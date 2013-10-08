package lw.sockets;

/**
  * Encapsulates information about an event arising from socket activity.
  * @author Liam Wade
  * @version 1.0 11/12/2008
  */
public class SocketEvent
{
  /**
    * Will create a new exception with the given reason.
    *
	* @param TID the unique Transaction ID for the message involved in this event
	* @param portNumber the port on which the socket server listens (aid to identifying actual problem)
    */
	public SocketEvent(String TID, int portNumber) {
		this.TID = TID;
		this.portNumber = portNumber;
	}

  /**
    * Will create a new exception with the given reason.
    *
	* @param TID the unique Transaction ID for the message involved in this event
	* @param portNumber the port on which the socket server listens (aid to identifying actual problem)
	* @param receivedMessage he message received over the socket
    */
	public SocketEvent(String TID, int portNumber, String receivedMessage) {
		this.TID = TID;
		this.portNumber = portNumber;
		this.receivedMessage = receivedMessage;
	}

	/**
	  *
	  * Get the port Number (useful as a unique id)
	  *
	  * @return the port Number
	  */
	public int getPortNumber() {
		return portNumber;
	}

	/**
	  *
	  * Get the Message received over the socket
	  *
	  * @return the received Message
	  */
	public String getReceivedMessage() {
		return receivedMessage;
	}

	/**
	  *
	  * Get the unique Transaction ID for the message involved in this event
	  *
	  * @return the TID
	  */
	public String getTID() {
		return TID;
	}

	private int portNumber = 0;				// aid to identifying actual problem
	private String receivedMessage = null;	// the message received over the socket
	private String TID = "";				// a unique transaction ID
}