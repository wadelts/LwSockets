/**
 * 
 */
package lw.sockets;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.logging.Logger;

/**
 * @author wadel
 * 
 * This class handles all communications with a socket, either client or server.
 * Although it is fine to create an instance of this class in one thread and
 * call methods from another, the methods should only ever be called from a single thread.
 * 
 * Thread-safety: This class is NOT thread safe.
 *
 */
public class LwSocketComms {
	final private Socket incoming;
	final private InputStream is;
	final private OutputStream os;
	final private SocketType socketType;
	final private HashMap<String,StringBuilder> messageShelf = new HashMap<String,StringBuilder>();

	// Only set by called method(s) (==> always same thread), not in Constructor, so no need to make volatile
	private LwSocketTransferMessage lastMessageReceived;

	// The type of socket comunications to set up
	static public enum SocketType {
		SERVER,
		CLIENT;
	}
	
	// Note: Nested enum types are implicitly static! (I made explicitly static so could use fromNumber() statically
	static public enum SocketService {
		UNRECOGNISED(0),
		READY(1),
		CLOSE(2),
		SHUTDOWN(3),
		MORE(4),
		DISCARD(5),
		CONSUME(6),
		CONSUME_RESPOND(7),
		REFUSE(8);
		
		private int numVal;
		
		SocketService(int numVal) {
			this.numVal = numVal;
		}
		
		public int asNumber() {
			return numVal;
		}

		static public SocketService fromNumber(int numVal) {
			switch(numVal) {
				case 1 :
					 return SocketService.READY;
				case 2 :
					 return SocketService.CLOSE;
				case 3 :
					 return SocketService.SHUTDOWN;
				case 4 :
					 return SocketService.MORE;
				case 5 :
					 return SocketService.DISCARD;
				case 6 :
					 return SocketService.CONSUME;
				case 7 :
					 return SocketService.CONSUME_RESPOND;
				case 8 :
					 return SocketService.REFUSE;
				default :
					 return SocketService.UNRECOGNISED;
			}
		}
	}

	static public enum SocketFormat {
		UNRECOGNISED(0),
		XML(1);

		private int numVal;
		
		SocketFormat(int numVal) {
			this.numVal = numVal;
		}
		
		public int asNumber() {
			return numVal;
		}

		static public SocketFormat fromNumber(int numVal) {
			switch(numVal) {
				case 1 :
					 return SocketFormat.XML;
				default :
					 return SocketFormat.UNRECOGNISED;
			}
		}
	}

	///////////////////////////////////////////////
	// Total size of transmission message
	// Note: when put up to 4196, it pushed each send to a whole second duration!!!!
	//		 Leaving at 1024 means 4 or 5 transactions per second, if they are sub-1000.
	///////////////////////////////////////////////
	private static final int MESSAGE_SIZE =  1024;
	///////////////////////////////////////////////
	// Num bytes left for the message to be transferred, after the codes, control information are subtracted.
	///////////////////////////////////////////////
	private final int MAX_DATA_SIZE =  MESSAGE_SIZE - 18 - 255;

    private static final Logger logger = Logger.getLogger("gemha");

	/**
	  * Constructor.
	  * 
	  * @param incoming the socket with which communications is to be established
	  * @param socketType the side of the connection using this LwSocketComms object e.g. SERVER
	  *
	  */
    public LwSocketComms(Socket incoming, SocketType socketType) throws LwSocketException {
		checkNullArgument(incoming);

		this.incoming = incoming;
    	
		try {
			is = incoming.getInputStream();
		} catch (IOException e) {
			throw new LwSocketException("[" + socketType.toString() + "-" + Thread.currentThread().getName() + "]: Caught IOException creating new input stream: " + e, -1001);
		}
		try {
			os = incoming.getOutputStream();
		}
		catch(IOException e) {
			try { incoming.close(); } catch (IOException e1) { /* Ignore */}
			throw new LwSocketException("[" + socketType.toString() + "-" + Thread.currentThread().getName() + "]: Caught IOException creating new output stream: " + e, -1002);
		}
		
		this.socketType = socketType;
	}

	/**
	  * Block on the socket, waiting for a response.
	  * When a message arrives, it is parsed to extract the Error code, Service, Format, Payload and Transaction Id (TID),
	  * which are saved for subsequent retrieval by the caller.
	  *
	  * @return true if irrecoverable technical problem encountered (e.g. client rudely closes without sending CLOSE instr)
	  */
	public boolean next() throws LwSocketException {
		String str = readMsg();
		logger.info("[" + socketType.toString() + "-" + Thread.currentThread().getName() + "]: Msg received is:" + str.trim());

		String lastErrNo = str.substring(0, 3);

		// If things go wrong, 999 is to shut down server
		if ("999".equals(lastErrNo)) {
			logger.info("[" + socketType.toString() + "-" + Thread.currentThread().getName() + "]: Received Error code 999. Assuming is due to client closing without telling me. Shutting down accepted socket.");
			return true;
		}
		else {
			SocketService lastService = SocketService.fromNumber(Integer.parseInt(str.substring(4, 7)));
			SocketFormat lastFormat  = SocketFormat.fromNumber(Integer.parseInt(str.substring(8, 11)));
			int lastDataLength = Integer.parseInt(str.substring(12, 17));
			String lastTID = str.substring(18, 18 + 255).trim();
			String lastPayload = str.substring(18 + 255).trim();
			lastMessageReceived = new LwSocketTransferMessage(Integer.parseInt(lastErrNo), lastTID, lastService, lastFormat, lastPayload);
			logger.fine("[" + socketType.toString() + "-" + Thread.currentThread().getName() + "]: Service =" + lastService + " Object  =" + lastFormat + " DataLen =" + lastDataLength);
			
			if (lastTID.length() > 0) {
				addLastPayloadToShelf();
			}
			logger.info("[" + socketType.toString() + "-" + Thread.currentThread().getName() + "]: Socket received data on port for message " + lastTID);
		}

		return false;
	}
	
	/**
	  * Send a message over the socket, breaking into chunks, if necessary.
	  * 
	  * @param socketTransferMessage the information to be sent over the wire
	  * 
	  *
	  */
	public void sendMessage(LwSocketTransferMessage socketTransferMessage) throws LwSocketException {
		checkNullArgument(socketTransferMessage);
		
		// instrCode instruction for the consumer : 0: No more data for this Message; 1: more data to come for this Message; -1: discard all data for this Message
		// Now return the meat response, sending, in chunks if necessary
		StringBuffer wholeMessage = new StringBuffer(socketTransferMessage.getPayload());
		
		while (wholeMessage.length() > 0) {
			if (wholeMessage.length() <= MAX_DATA_SIZE) {
				sendPacket(socketTransferMessage.getErrNo(), socketTransferMessage.getTID(), socketTransferMessage.getService(), socketTransferMessage.getFormat(), wholeMessage.toString());
				wholeMessage.delete(0, MAX_DATA_SIZE);
				logger.info("[" + socketType.toString() + "-" + Thread.currentThread().getName() + "]: sent data for message " + socketTransferMessage.getTID() + ". Final chunk.");
			}
			else {
				sendPacket(socketTransferMessage.getErrNo(), socketTransferMessage.getTID(), SocketService.MORE, socketTransferMessage.getFormat(), wholeMessage.substring(0, MAX_DATA_SIZE));
				wholeMessage.delete(0, MAX_DATA_SIZE);
				logger.info("[" + socketType.toString() + "-" + Thread.currentThread().getName() + "]: sent data for message " + socketTransferMessage.getTID() + ". More to follow.");
			}
		}
		
	}
	

	/**
	  * Send a packet of data over the socket.
	  * 
	  * @param errNo the error number to be sent
	  * @param TID the Transaction ID to be sent
	  * @param lastService the Service requested to be sent
	  * @param lastFormat the format of the message being sent
	  * @param dataPart the actual data to be sent
	  * 
	  *
	  */
	private void sendPacket(Integer errNo, String TID, SocketService lastService, SocketFormat lastFormat, String dataPart) throws LwSocketException {
		checkNullArgument(TID);
		checkNullArgument(lastService);
		checkNullArgument(lastFormat);
		checkNullArgument(dataPart);

		// Make sure lastTID only 255 chars...
		if (TID.length() > 255) {
			TID = TID.substring(0, 255);
		}
		dataPart = padSpace(TID, 255) + dataPart;
		
		// Make sure data part has at least one chars - avoid stringoutofbounds errors on other side...
		if (dataPart == null || dataPart.length() == 0) {
			dataPart = " ";
		}

		Integer datalen  = new Integer(dataPart.length());
//		System.out.println(String.format("sendMsg: Formatted=[%03d_%03d_%03d_%05d_" + dataPart.trim() + "]", errNo, lastService.asNumber(), lastFormat.asNumber(), datalen));
		dataPart = String.format("%03d_%03d_%03d_%05d_" + dataPart, errNo, lastService.asNumber(), lastFormat.asNumber(), datalen);
//		dataPart = padZero(errNo) + "_" + padZero(lastService.asNumber()) + "_" + padZero(lastFormat.asNumber()) + "_" + padZero(datalen, 5) + "_" + dataPart;
		dataPart = fillString(dataPart);
		
		logger.info("[" + socketType.toString() + "-" + Thread.currentThread().getName() + "]: Sending Data Part=[" + dataPart.trim() + "]");
		
		byte[] rawData = dataPart.getBytes();
		
		try {
			os.write(rawData);
			os.flush();
		} catch (IOException e) {
			e.printStackTrace();
			throw new LwSocketException("[" + socketType.toString() + "-" + Thread.currentThread().getName() + "]: failed to write/flush socket! Exception:" + e);
		}
	}


	/**
	  * Retrieve a (partial) message, previously stored
	  *
	  * @param lastTID the key under which data is stored
	  * 
	  * @return the stored message
	  *
	  */
	public StringBuilder getMessageForTID(String lastTID) {
		checkNullArgument(lastTID);

		return messageShelf.get(lastTID);
	}

	/**
	  * Removed a stored message
	  *
	  * @param lastTID the key under which data is stored
	  *
	  *@return the previous value associated with lastTID, or null if there was no message for lastTID.
	  */
	public StringBuilder removeMessageForTID(String lastTID) {
		checkNullArgument(lastTID);

		return messageShelf.remove(lastTID);
	}

	
	/**
	  * Close the socket
	  *
	  */
	public void closeConnection() {
		if (incoming != null) {
			try {
				incoming.close();
			}
			catch(IOException e) {
				logger.warning("[" + socketType.toString() + "-" + Thread.currentThread().getName() + "]: Caught (and muffled) IOException trying to close current socket: " + e);
			}
		}
	}

	public int getLastErrorNo() {
		return (lastMessageReceived == null ? 0 : lastMessageReceived.getErrNo());
	}

	public String getTID() {
		return (lastMessageReceived == null ? null : lastMessageReceived.getTID());
	}

	public SocketService getLastService() {
		return (lastMessageReceived == null ? null : lastMessageReceived.getService());
	}

	public SocketFormat getLastFormat() {
		return (lastMessageReceived == null ? null : lastMessageReceived.getFormat());
	}

	public String getLastMessageReceived() {
		return (lastMessageReceived == null ? null : lastMessageReceived.getPayload());
	}

	/**
	  * Store a partial message, or add a new piece to one already received
	  *
	  */
	private void addLastPayloadToShelf() {
		checkNullArgument(lastMessageReceived);
		
		if (messageShelf.containsKey(lastMessageReceived.getTID())) {
			StringBuilder storedMessage = messageShelf.get(lastMessageReceived.getTID());
			storedMessage.append(new StringBuilder(lastMessageReceived.getPayload()));
		}
		else {
			messageShelf.put(lastMessageReceived.getTID(), new StringBuilder(lastMessageReceived.getPayload()));
		}
	}

	/**
	  * Pad a String with trailing blanks, up to a given size.
	  * @param s the String to be padded
	  * @param size the size to which the String should be increased, with this padding.
	  * @return a String containing the padding.
	  */
	static private String padSpace(String s, int size)
	// This method pads a string number with leading zeros, up to the specified size.
	{
		while (s.length() < size)
			s = s + " ";

		return s;
	}

	/**
	  * Right-Fill a String with nulls.
	  * @param str the String to be filled
	  * 
	  * @return a String containing the padding.
	  */
	static private String fillString(String str)
	//	This method nullChar-fills the string to MESSAGE_SIZE characters.
	{
		int len = MESSAGE_SIZE-str.length();
		for (int i = len; i > 0 ; i--)
			str = str + '\0';
	
		return str;
	}

	/**
	  * Read a message from the input stream.
	  */
	private String readMsg() throws LwSocketException
	// Get a response from the the connected socket.
	{
		byte[] response = new byte[MESSAGE_SIZE];
		int numBytesTransferred = 0;     // Bytes received so far.
		int numTries = 0;

		// Need while because read can be interrupted before getting any/all data.
		// Combination of numTries<6 and socket timeout of 10 seconds will mean try for one minute
		while (numBytesTransferred < response.length && numTries < 6) {
			try {
				numBytesTransferred += is.read(response, numBytesTransferred, (response.length - numBytesTransferred));
				numTries++; // Need this for when other socket severs connection - would keep reading no bytes.
			}
			catch(InterruptedIOException e) {
				numTries++;
				numBytesTransferred += e.bytesTransferred;
				if (numBytesTransferred >= response.length)
					return new String(response);
			}
			catch(IOException e) {
				throw new LwSocketException("[" + socketType.toString() + "-" + Thread.currentThread().getName() + "]: Error: " + e);
			}
		}

		// For when connection is severed.
		if (numBytesTransferred <= 0)
			return new String("999_999_999_00027_Connection no longer valid.");

		return new String(response);
	}

	/**
	 * @param o the object to be checked for null.
	 * 
	 * @throws IllegalArgumentException if o is null
	 */
	private void checkNullArgument(Object o) {
		if ((o == null)) throw new IllegalArgumentException("[" + Thread.currentThread().getName() + "]: Null value received.");
	}
}
