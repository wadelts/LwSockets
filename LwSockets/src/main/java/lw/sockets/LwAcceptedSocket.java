/**
 * 
 */
package lw.sockets;

import java.net.Socket;
import java.util.logging.Logger;

import lw.sockets.interfaces.LwIXMLSocketServerListener;

/**
 * @author wadel
 *
 * Package-private class for handling requests over an accepted server socket.
 * 
 * Thread-safety: This class is thread safe.
 * 
 */
class LwAcceptedSocket extends LwSocketComms implements Runnable {
	private static final Logger logger = Logger.getLogger("gemha");

	volatile private boolean shutDown = false;	// If set to true, will shut down server. Can be set through the socket SERV_SHUTDOWN command
												// Needs to be volatile as object may be instantiated by one thread and run() in another

	final private LwXMLSocketServer parent;			// the parent socket server - need this to tell him to shut down, if allowed
	final private LwIXMLSocketServerListener app; // The object that will be implementing interface LwIXMLSocketServerListener
	final private int portNumber;
	final private SocketType socketType;

	public LwAcceptedSocket(LwXMLSocketServer parent, LwIXMLSocketServerListener app, Socket incoming, SocketType socketType, int portNumber) throws LwSocketException {
		super(incoming, socketType);
		
		assert parent != null;
		assert app != null;
		assert socketType != null;
		
		this.parent = parent;
		this.app = app;
		this.socketType = socketType;
		this.portNumber = portNumber;
	}

	@Override
	public void run() {
		try {
			acceptMessages();
		}
		catch(LwSocketException e) {
			String TID = (getTID() == null ? "Unknown TID" : getTID());
			app.handleError(new LwSocketEvent(TID, portNumber), e);
		}
	}

	/**
	  * Commence accepting messages
	  * Calls receiveMessage() when a message is ready.
	  *
	  * @throws LwSocketException when any error is encountered
	  */
	private void acceptMessages() throws LwSocketException {

		// First send Server-ready message to client...
		logger.info("[" + socketType.toString() + "-" + Thread.currentThread().getName() + "]:Connection accepted. Going to send SERV_READY instruction...");
		Integer errNo = new Integer(0);
		sendMessage(new LwSocketTransferMessage(errNo, "1", SocketService.READY, SocketFormat.XML, "Server Ready"));

		boolean closeConnection = false;
		while (!closeConnection && !shutDown) {
			closeConnection = next(); // Returns true if irrecoverable technical problem encountered (e.g. client rudely closes without sending CLOSE instr)
			
			switch(getLastService()) {
				case CLOSE :
					logger.info("[" + socketType.toString() + "-" + Thread.currentThread().getName() + "]: Request to CLOSE current socket received.");
					closeConnection = true;
					break;
				case SHUTDOWN :
					logger.info("[" + socketType.toString() + "-" + Thread.currentThread().getName() + "]: Request to SHUTDOWN received.");

					if ( app.canCloseServerSocket(new LwSocketEvent(getTID(), portNumber)) ) {
						logger.info("[" + socketType.toString() + "-" + Thread.currentThread().getName() + "]: Controling application granted request to close Socket Server. Setting shutDown to true.");
						shutDown = true;
					}
					else {
						logger.info("[" + socketType.toString() + "-" + Thread.currentThread().getName() + "]: Controling application REFUSED request to close Socket Server. Request ignored.");
					}

					closeConnection = true;
					break;
				case CONSUME :
				case CONSUME_RESPOND :
					switch(getLastFormat()) {
						case XML:
							consumeXMLMsg();
							break;
						case UNRECOGNISED:
							break;
						default:
							break;
					}
					break;
				case MORE :
					switch(getLastFormat()) {
						case XML :
							logger.fine("[" + socketType.toString() + "-" + Thread.currentThread().getName() + "]: Request to SERV_MORE OBJ_XML received.");

							// Now Respond
							errNo   = new Integer(0);
							
							sendMessage(new LwSocketTransferMessage(errNo, getTID(), LwSocketComms.SocketService.MORE, LwSocketComms.SocketFormat.XML, "Message part saved. Awaiting more"));
							logger.fine("[" + socketType.toString() + "-" + Thread.currentThread().getName() + "]: Response returned to socket client.");
							break;
						case UNRECOGNISED:
							break;
						default:
							break;
					}
					break;
				case DISCARD :
					switch(getLastFormat()) {
						case XML :
							logger.fine("[" + socketType.toString() + "-" + Thread.currentThread().getName() + "]: Request to SERV_DISCARD OBJ_XML received.");

							removeMessageForTID(getTID());

							// Now Respond
							errNo   = new Integer(0);
							sendMessage(new LwSocketTransferMessage(errNo, getTID(), LwSocketComms.SocketService.DISCARD, LwSocketComms.SocketFormat.XML, "Message discarded"));
							logger.fine("Response returned to socket client.");
							break;
						case UNRECOGNISED:
							break;
						default:
							break;
					}
					break;
			} // end switch(serv)
			
			// Check for interruption (e.g. by an ExecutorService)
			if (Thread.interrupted()) {
				closeConnection = true;
				// Re-set the interrupted flag, in case others within this thread need it
				Thread.currentThread().interrupt();
			}
		} // end while (!closeConnection && !shutDown)
		
		closeConnection();
		if (shutDown) {
			parent.terminateProcessing();
		}
	}

	/**
	 * Process the incoming mesage
	 */
	private void consumeXMLMsg() throws LwSocketException {
		String TID = getTID();
		Integer errNo;
		LwSocketComms.SocketService service = getLastService();
		logger.fine("Request to " + service.toString() + " OBJ_XML received.");

		StringBuilder payLoad = getMessageForTID(TID);
		boolean consumeMessage = false;
		if (service == LwSocketComms.SocketService.CONSUME) { // deliver it to the implementing application
			// Give the implementor of this interface the opportunity to process the message...
			consumeMessage = app.messageReceived(new LwSocketEvent(TID, portNumber, payLoad.toString()));
			// TODO: ...
/* NEED TO IMPLEMENT THIS FOR MULTIPLE THREADS !!!!!
			// Is OK to use this synchQueue method, if take() is used on other side (it also blocks)
			if (synchQueue != null) { // the consumeMessage was not really known when we returned from app.messageReceived(), so wait
				// Now just awaiting processing of message and setting of consumeMessage flag
				try { synchQueue.put("waiting");
				} catch(InterruptedException e) { 
					shutDown = true; // Want to terminate this Thread.
					return;
				}
				consumeMessage = app.getConsumeMessage();
			}
*/
			// Now Respond
			errNo   = new Integer(consumeMessage ? 0 : 1);
			sendMessage(new LwSocketTransferMessage(errNo, TID, SocketService.CONSUME, LwSocketComms.SocketFormat.XML, "Message " + (consumeMessage ? "" : "not ") + "consumed"));
			logger.fine("[" + socketType.toString() + "-" + Thread.currentThread().getName() + "]: Response returned to socket client.");
		}
		else { // is SERV_CONSUME_RESPOND
			consumeMessage = true;
			logger.info("[" + socketType.toString() + "-" + Thread.currentThread().getName() + "]: XML Response Message is expected to be returned to socket client.");
			// Just send confirmation of receipt of msg
			errNo   = new Integer(consumeMessage ? 0 : 1);
			
			sendMessage(new LwSocketTransferMessage(errNo, TID, SocketService.CONSUME, LwSocketComms.SocketFormat.XML, "Message " + (consumeMessage ? "" : "not ") + "consumed"));
			logger.fine("[" + socketType.toString() + "-" + Thread.currentThread().getName() + "]: Receipt Response returned to socket client.");

			// Give the implementor of this interface the opportunity to consume the message...
			String responseMessage = app.messageReceivedAndWantResponse(new LwSocketEvent(TID, portNumber, payLoad.toString()));
			if (responseMessage != null) {
				errNo   = new Integer(0);
				// Now return the meat response...
				sendMessage(new LwSocketTransferMessage(errNo, TID, SocketService.CONSUME, LwSocketComms.SocketFormat.XML, responseMessage));
				
				responseMessage = null;
			}
		}

		// Then get rid of the message...
		removeMessageForTID(getTID());
	}
}
