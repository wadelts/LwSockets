package lw.sockets.interfaces;

import lw.sockets.LwSocketEvent;
import lw.sockets.LwSocketException;;
/**
  * Encapsulates receiving XML messages over a socket (server) connection.
  * The LwXMLMesssageOverSocket object call this interface.
  * @author Liam Wade
  * @version 1.0 11/12/2008
  */
public interface LwIXMLSocketServerListener {


//////////////////////////////////////////////////////////////////////////
//				Start: Interface Methods
//////////////////////////////////////////////////////////////////////////
/**
  * Will be called by the supporting object when a complete message is available for delivery
  *
  * @param event holds information on the event
  *
  * @return true if the response is to be consumed, otherwise false
  */
boolean messageReceived(LwSocketEvent event);

/**
  * Will be called by the supporting object when a complete message is available for delivery
  * and a response is expected.
  *
  * @param event holds information on the event
  *
  * @return the response to be sent back over the socket
  */
String messageReceivedAndWantResponse(LwSocketEvent event);

/**
  * Will be called by the supporting object when an error is encountered
  *
  * @param event holds information on the event
  * @param exception LwSocketException explaining the problem
  *
  */
void handleError(LwSocketEvent event, LwSocketException exception);

/**
  * Will be called by the supporting object when a request has been received over the connection to close down.
  * If true is returned, the socket server will be closed for good, otherwise the request will be ignored,
  * (only the current socket will be closed).
  *
  * @param event holds information on the event
  *
  * @return true if the Socket Server shold close down, false if it should remain open
  */
boolean canCloseServerSocket(LwSocketEvent event);

/**
  * Will be called by the supporting object to find out if a message should be consumed.
  *
  * @return true if a message should be consumed, otherwise false
  */
boolean getConsumeMessage();

}
