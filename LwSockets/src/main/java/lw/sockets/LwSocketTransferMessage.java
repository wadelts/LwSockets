package lw.sockets;

import lw.sockets.LwSocketComms.SocketFormat;
import lw.sockets.LwSocketComms.SocketService;

/**
  * Encapsulates information about an event arising from socket activity.
  * 
 * Thread-safety: This class is thread safe.
  * 
  * @author Liam Wade
  * @version 1.0 20/09/2013
  */
public class LwSocketTransferMessage {
	final private int errNo;			// data length for message
	final private String TID;				// a unique transaction ID
	final private SocketService service;	// Service requested in messqge
	final private SocketFormat format ;		// Format for message
	final private String payload;			// message payload
	
  /**
    * Will create a new exception with the given reason.
    *
	* @param TID the unique Transaction ID for the message involved in this event
	* @param portNumber the port on which the socket server listens (aid to identifying actual problem)
	* @param payload he message received over the socket
    */

	public LwSocketTransferMessage(int errNo, String TID, SocketService service, SocketFormat format, String payload) {
		super();
		this.errNo = errNo;
		this.TID = TID;
		this.service = service;
		this.format = format;
		this.payload = payload;
	}

	
	public int getErrNo() {
		return errNo;
	}
	
	public String getTID() {
		return TID;
	}

	public SocketService getService() {
		return service;
	}
	
	public SocketFormat getFormat() {
		return format;
	}
	
	public String getPayload() {
		return payload;
	}

}