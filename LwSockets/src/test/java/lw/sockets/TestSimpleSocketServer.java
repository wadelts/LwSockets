package lw.sockets;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import lw.sockets.LwSocketComms.SocketType;
import lw.sockets.interfaces.LwIXMLSocketServerListener;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class TestSimpleSocketServer {
	// This is created BEFORE the class is instantiated
	static private  LwXMLSocketServer theServer;
	static private ExecutorService execPool;				// the pool of threads for this application

	private static final Logger logger = Logger.getLogger("gemha");
	
	@BeforeClass
	static public  void classSetup() {
		try {
			// Note: a org.junit.ComparisonFailure exception is thrown if these "static context" asserts fail
			// Setting server up with only 2 connections run at a time (albeit in its own thread, separate from the socket server itself)
			execPool = Executors.newFixedThreadPool(2);
			theServer = new LwXMLSocketServer(execPool, new LwIXMLSocketServerListener() {
				@Override
				public boolean messageReceived(LwSocketEvent event) {
					logger.info("[LISTENER-" + Thread.currentThread().getName() + "]: Mesage for TID " + event.getTID() + " received.");
					assertEquals(11819, event.getPortNumber());
					assertEquals("1", event.getTID());
					assertEquals("<APP_DEFINED_REQUEST><KEY>2345</KEY><STATUS>SUCCESS</STATUS></APP_DEFINED_REQUEST>", event.getReceivedMessage());
					return true;
				}

				@Override
				public String messageReceivedAndWantResponse(LwSocketEvent event) {
					logger.info("[LISTENER-" + Thread.currentThread().getName() + "]: Mesage for TID " + event.getTID() + " received.");
					assertEquals(11819, event.getPortNumber());
					return event.getReceivedMessage();
				}

				@Override
				public void handleError(LwSocketEvent event, LwSocketException exception) {
					logger.info("[LISTENER-" + Thread.currentThread().getName() + "]: Exception was: " + exception);
					assertEquals(11819, event.getPortNumber());
					assertEquals("3", event.getTID());
					assertNull(event.getReceivedMessage());
					assertEquals("XXXX", exception.getErrorCode());
				}

				@Override
				public boolean canCloseServerSocket(LwSocketEvent event) {
					logger.info("[LISTENER-" + Thread.currentThread().getName() + "]: Request to close received with TID " + event.getTID());
					assertEquals(11819, event.getPortNumber());
					assertEquals("4", event.getTID());
					assertNull(event.getReceivedMessage());
					return true;
				}

				@Override
				public boolean getConsumeMessage() {
					logger.info("[LISTENER-" + Thread.currentThread().getName() + "]: Request to Consume Message received.");
					return true;
				}
			}
			, 11819);
			
			new Thread(theServer).start();
		} catch (LwSocketException e) {
			fail("Could not create socket server. Exception: " + e);
		}
	}

	/**
	 * Ensure correct msg is received by server, when sent asynchronously.
	 */
	@Test
	public void testSendMsgAsynch() {
		///////////////////////////////////////////////
		// Connect to the socket on "this" machine.
		///////////////////////////////////////////////
		Socket s = null;
		try {
			s = new Socket("localhost", 11819);
		}
		catch (UnknownHostException e) {
			fail("Couldn't create new Client socket - UnknownHostException: " + e.getMessage());
		}
		catch (IOException e) {
			// May just fail because we're quicker than the server at setting up (try gain, if you want)
			fail("Couldn't create new Client socket - IOException: " + e.getMessage());
		}
		
		assertNotNull(s);

		LwSocketComms socketComms = null;
		int numAttempts = 1;
		while (socketComms == null && numAttempts <= 2) {
			// Open client socket
			socketComms = openClientComms(s);
			numAttempts++;
			if (socketComms == null)  try { Thread.sleep(1000);} catch (InterruptedException e) { /* do nothing, don't care */}
		}
		if (socketComms == null) fail("Couldn't create new Client socket (after " + (numAttempts-1) + " attempts) - IOException");
		
		// Perform test
		String wholeMessage = "<APP_DEFINED_REQUEST><KEY>2345</KEY><STATUS>SUCCESS</STATUS></APP_DEFINED_REQUEST>";
		
		try {
			socketComms.sendMessage(new LwSocketTransferMessage(new Integer(0), "1", LwSocketComms.SocketService.CONSUME, LwSocketComms.SocketFormat.XML, wholeMessage));
			// read confirmation msg, although not really needed (note: get a "java.net.socketexception software caused connection abort recv failed" on server if I don't read response - synch prob)
			socketComms.next();
		} catch (LwSocketException e) {
			fail("Could not send message: Exception: " + e);
		}

		// tell server am finished, otherwise he'll detect closure of socket and close accepted socket on server side
		try {
			socketComms.sendMessage(new LwSocketTransferMessage(new Integer(0), "1", LwSocketComms.SocketService.CLOSE, LwSocketComms.SocketFormat.XML, "Close me"));
		} catch (LwSocketException e) {
			fail("Could not explicitly close connection: Exception: " + e);
		}
		// Close socket
		if (s != null) {
			try {s.close();} catch (IOException e) { /* Ignore, cannot do anything anyway and we're finished */}
		}
	}

	/**
	 * Ensure correct msg is received by server, when sent asynchronously.
	 */
	@Test
	public void testSendMsgSynch() {
		///////////////////////////////////////////////
		// Connect to the socket on "this" machine.
		///////////////////////////////////////////////
		Socket s = null;
		try {
			s = new Socket("localhost", 11819);
		}
		catch (UnknownHostException e) {
			fail("Couldn't create new Client socket - UnknownHostException: " + e.getMessage());
		}
		catch (IOException e) {
			// May just fail because we're quicker than the server at setting up (try gain, if you want)
			fail("Couldn't create new Client socket - IOException: " + e.getMessage());
		}
		
		assertNotNull(s);

		// Open client socket
		LwSocketComms socketComms = openClientComms(s);
		
		String wholeMessage = "<APP_DEFINED_REQUEST><KEY>5432</KEY><STATUS>SUCCESS</STATUS></APP_DEFINED_REQUEST>";
		
		try {
			logger.info("testSendMsgSynch: Sending message for which want response.");
			socketComms.sendMessage(new LwSocketTransferMessage(new Integer(0), "2", LwSocketComms.SocketService.CONSUME_RESPOND, LwSocketComms.SocketFormat.XML, wholeMessage));
			// Get confirmation/error response from server ( just to say got request)
			logger.info("testSendMsgSynch: Waiting for confirmation msg (not response, that comes next).");
			socketComms.next();
			assertEquals(0, socketComms.getLastErrorNo());
			// Now get confirmation/error response from server for action requested
			logger.info("testSendMsgSynch: Waiting for response...");
			socketComms.next();
		} catch (LwSocketException e) {
			fail("Could not send/receive message: Exception: " + e);
		}
		
		// In this test, expect the server to send back same msg
		assertEquals(wholeMessage, socketComms.getLastMessageReceived());

		// tell server am finished, otherwise he'll detect closure of socket and close accepted socket on server side
		try {
			socketComms.sendMessage(new LwSocketTransferMessage(new Integer(0), "2", LwSocketComms.SocketService.CLOSE, LwSocketComms.SocketFormat.XML, "Close me"));
		} catch (LwSocketException e) {
			fail("Could not explicitly close connection: Exception: " + e);
		}
		// Close socket
		if (s != null) {
			try {s.close();} catch (IOException e) { /* Ignore, cannot do anything anyway and we're finished */}
		}
	}

	/**
	 * Ensure Server handles > 1 connection at a time.
	 */
	@Test
	public void testTwoConnections() {
		///////////////////////////////////////////////
		// Connect to the socket on "this" machine.
		///////////////////////////////////////////////
		Socket s1 = null;
		try {
			s1 = new Socket("localhost", 11819);
		}
		catch (UnknownHostException e) {
			fail("Couldn't create new Client socket - UnknownHostException: " + e.getMessage());
		}
		catch (IOException e) {
			// May just fail because we're quicker than the server at setting up (try gain, if you want)
			fail("Couldn't create new Client socket - IOException: " + e.getMessage());
		}
		
		assertNotNull(s1);

		///////////////////////////////////////////////
		// Connect to the socket on "this" machine.
		///////////////////////////////////////////////
		Socket s2 = null;
		try {
			s2 = new Socket("localhost", 11819);
		}
		catch (UnknownHostException e) {
			fail("Couldn't create new Client socket - UnknownHostException: " + e.getMessage());
		}
		catch (IOException e) {
			// May just fail because we're quicker than the server at setting up (try gain, if you want)
			fail("Couldn't create new Client socket - IOException: " + e.getMessage());
		}

		assertNotNull(s2);

		// Open client sockets
		LwSocketComms socketComms1 = openClientComms(s1);
		LwSocketComms socketComms2 = openClientComms(s2);
		
		///////////////////////////////////////////////
		// Send data request over s1...
		///////////////////////////////////////////////
		String wholeMessage1 = "<APP_DEFINED_REQUEST><KEY>5432</KEY><STATUS>SUCCESS</STATUS></APP_DEFINED_REQUEST>";
		
		try {
			logger.info("testSendMsgSynch: Sending message for which want response.");
			socketComms1.sendMessage(new LwSocketTransferMessage(new Integer(0), "5", LwSocketComms.SocketService.CONSUME_RESPOND, LwSocketComms.SocketFormat.XML, wholeMessage1));
			// Get confirmation/error response from server ( just to say got request)
			logger.info("testSendMsgSynch: Waiting for confirmation msg (not response, that comes next).");
			socketComms1.next();
			assertEquals(0, socketComms1.getLastErrorNo());
			// Now get confirmation/error response from server for action requested
			logger.info("testSendMsgSynch: Waiting for response...");
			socketComms1.next();
		} catch (LwSocketException e) {
			fail("Could not send/receive message: Exception: " + e);
		}
		
		// In this test, expect the server to send back same msg
		assertEquals(wholeMessage1, socketComms1.getLastMessageReceived());

		///////////////////////////////////////////////
		// Send data request over s2...
		///////////////////////////////////////////////
		String wholeMessage2 = "<APP_DEFINED_REQUEST><KEY>7623</KEY><STATUS>SUCCESS</STATUS></APP_DEFINED_REQUEST>";
		
		try {
			logger.info("testSendMsgSynch: Sending message for which want response.");
			socketComms2.sendMessage(new LwSocketTransferMessage(new Integer(0), "6", LwSocketComms.SocketService.CONSUME_RESPOND, LwSocketComms.SocketFormat.XML, wholeMessage2));
			// Get confirmation/error response from server ( just to say got request)
			logger.info("testSendMsgSynch: Waiting for confirmation msg (not response, that comes next).");
			socketComms2.next();
			assertEquals(0, socketComms1.getLastErrorNo());
			// Now get confirmation/error response from server for action requested
			logger.info("testSendMsgSynch: Waiting for response...");
			socketComms2.next();
		} catch (LwSocketException e) {
			fail("Could not send/receive message: Exception: " + e);
		}
		
		// In this test, expect the server to send back same msg
		assertEquals(wholeMessage2, socketComms2.getLastMessageReceived());

		// tell server am finished, otherwise he'll detect closure of socket and close accepted socket on server side
		try {
			socketComms1.sendMessage(new LwSocketTransferMessage(new Integer(0), "7", LwSocketComms.SocketService.CLOSE, LwSocketComms.SocketFormat.XML, "Close me"));
		} catch (LwSocketException e) {
			fail("Could not explicitly close connection: Exception: " + e);
		}
		// Close socket
		if (s1 != null) {
			try {s1.close();} catch (IOException e) { /* Ignore, cannot do anything anyway and we're finished */}
		}
		
		// tell server am finished, otherwise he'll detect closure of socket and close accepted socket on server side
		try {
			socketComms2.sendMessage(new LwSocketTransferMessage(new Integer(0), "8", LwSocketComms.SocketService.CLOSE, LwSocketComms.SocketFormat.XML, "Close me"));
		} catch (LwSocketException e) {
			fail("Could not explicitly close connection: Exception: " + e);
		}
		// Close socket
		if (s2 != null) {
			try {s2.close();} catch (IOException e) { /* Ignore, cannot do anything anyway and we're finished */}
		}
	}

	@AfterClass
	static public void classTearDown() {
		// Note: this method called regardless of success of BeforeClass (like a finally section)
		// Have to call this, as no test tells server to shut down - cannot do that way as tests run in any order.
		if (theServer != null) {
			theServer.terminateProcessing();
			execPool.shutdown();
		}
/*		
		// Use this to test normal shutdown from a client socket, as opposed to the server side.
		// Tell server am finished, and he should shut down
		try {
			socketComms.sendMessage(new LwSocketTransferMessage(new Integer(0), "4", LwSocketComms.SocketService.SHUTDOWN, LwSocketComms.SocketFormat.XML, "Close yourself"));
		} catch (LwSocketException e) {
			fail("Could not explicitly close connection: Exception: " + e);
		}
*/
	}

	//////////////////////////////////////////////////////////////////
	// Start: Helper methods ...
	//////////////////////////////////////////////////////////////////

	/**
	  * Open a socket for communications
	  *
	  */
	private LwSocketComms openClientComms(Socket s) {
		LwSocketComms socketComms = null;
		// May throw LwSocketException
		try {
			socketComms = new LwSocketComms(s, SocketType.CLIENT);
			// Read Server Ready message.
			socketComms.next();
		} catch (LwSocketException e) {
			fail("Could not create new LwSocketComms object. LwSocketException: " + e.getMessage());
		}
		
		return socketComms;
	}

}
