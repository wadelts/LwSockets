package lw.sockets;

import java.util.logging.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.io.*;
import java.net.*;

import lw.sockets.LwSocketComms.SocketType;
import lw.sockets.interfaces.LwIXMLSocketServerListener;
import lw.utils.LwLogger;

/**
  * This class encapsulates a socket server for accepting XML messages (in parts, if required).
  * Note it runs in it's own thread, so x.start() must be called to start (which will call run() below).
  * NOTE:
  * 	We'll get a "java.net.socketexception software caused connection abort recv failed" on server if cient doesn't read response - synch problem.
  *
  * @author Liam Wade
  * @version 1.0 11/12/2008
  */
public class LwXMLSocketServer implements Runnable {

	private static final Logger logger = Logger.getLogger("gemha");

	volatile private boolean shutDownRequested = false;	// If set to true, will shut down server. Can be set by calling terminateProcessing() or through the socket SERV_SHUTDOWN command

	final private LwIXMLSocketServerListener app; // The object that will be implementing interface LwIXMLSocketServerListener
	final private int portNumber;

	final private ServerSocket servSocket;
	final private SynchronousQueue<String> synchQueue;	// synchronize messages between receiving and getting processed/consumed (SynchronousQueue has no space, so blocks on put)
														// this is required to allow us await instruction to consume or not.
	final private ExecutorService execPool;				// the pool of threads for handling Accepted Connections
	



	public LwXMLSocketServer(ExecutorService execPool, LwIXMLSocketServerListener app, int portNumber) throws LwSocketException {
		checkNullArgument(execPool);
		checkNullArgument(app);
		
		this.execPool = execPool;
		this.app = app;
		this.portNumber = portNumber;
		this.synchQueue = null;

		try {
			servSocket = new ServerSocket(portNumber);
			logger.info("[SERVER-" + Thread.currentThread().getName() + "]: Socket Server created.");
		}
		catch(IOException e) {
			throw new LwSocketException("LwXMLSocketServer.constructor: Error creating new Server Socket", e);
		}
	}

	public LwXMLSocketServer(ExecutorService execPool, LwIXMLSocketServerListener app, int portNumber, SynchronousQueue<String> synchQueue) throws LwSocketException {
		checkNullArgument(execPool);
		checkNullArgument(app);

		this.execPool = execPool;
		this.app = app;
		this.synchQueue = synchQueue;
		this.portNumber = portNumber;

		try {
			servSocket = new ServerSocket(portNumber);
			logger.info("[SERVER-" + Thread.currentThread().getName() + "]: Socket Server created.");
		}
		catch(IOException e) {
			throw new LwSocketException("LwXMLSocketServer.constructor: Error creating new Server Socket", e);
		}
	}

	/**
	  * Start the Thread
	  *
	  */
	public void run() {
		try {
			accept();
		}
		catch(LwSocketException e) {
			app.handleError(new LwSocketEvent("TID Unavailable", portNumber), e);
		}

	}

	/**
	  * Block on the socket, waiting for a new connection.
	  *
	  */
	public void accept() throws LwSocketException {
		// Note, only accepts one connection at a time...
		while (!shutDownRequested) {
			Socket incoming;
			try {
				incoming = servSocket.accept();
			} catch(IOException e) {
				if (shutDownRequested) {
					break; // ...out of while (!shutDownRequested)
				} else {
					throw new LwSocketException("LwXMLSocketServer.accept(): Caught IOException accepting new socket connection: ", e);
				}
			}
	
			// Check for interruption (e.g. by an ExecutorService)
			if (Thread.interrupted()) {
				shutDownRequested = true;
				// Re-set the interrupted flag, in case others within this thread need it
				Thread.currentThread().interrupt();
				break;
			}
			
			logger.info("[SERVER-" + Thread.currentThread().getName() + "]: New client connection accepted.");
			
			// May throw LwSocketException
			LwAcceptedSocket acceptedSocketConnection = new LwAcceptedSocket(this, app, incoming, SocketType.SERVER, portNumber);
			execPool.execute(acceptedSocketConnection);
			logger.info("[SERVER-" + Thread.currentThread().getName() + "]: New LwAcceptedSocket object created and executed.");
		}
		
		close(null);
	}
	
	/**
	  * Close the connection
	  *
     * @param out the Logger to use to report events etc
     *
	  * @throws LwSocketException when any error is encountered
	  */
	public void close(LwLogger out) throws LwSocketException {
		// LwLogger used when close() is called by
		// a VM shutdown hook, in which case the logger may be dead (it's shutdown hook may be
		// executed before ours), so a FileWriter object is used instead.
		try {
			servSocket.close();
			if (out != null) {
				out.appendln("Closed Server socket on port " + portNumber);
			}
			else {
				logger.info("[SERVER-" + Thread.currentThread().getName() + "]: Closed Server socket on port " + portNumber);
			}
		}
		catch(IOException e) {
			if (out != null) {
				try {
					out.appendln("LwXMLSocketServer.close(): Caught (and muffled) IOException trying to close Server socket: " + e);
				}
				catch(IOException e2) {
					System.out.println("LwXMLSocketServer.close(): While writing exception to LwLogger, caught IOException: " + e2.getMessage());
				}
			}
			else {
				logger.warning("[SERVER-" + Thread.currentThread().getName() + "]: Caught (and muffled) IOException trying to close Server socket: " + e);
			}
		}
	}

	/**
	  * Stop the Server
	  *
	  */
	public void terminateProcessing() {
		logger.info("[SERVER-" + Thread.currentThread().getName() + "]: Request to Terminate processing received.");
		shutDownRequested = true;
		try {
			close(null); // Only way to interrupt accept(), as it doesn't check Thread.interrupted()
		} catch (LwSocketException e) {
			// Ignore any prob here
			logger.info("[SERVER-" + Thread.currentThread().getName() + "]: Ignored LwSocketException closing server socket on port " + portNumber + ": " + e);
		}
	}

	/**
	 * @param o the object to be checked for null.
	 * 
	 * @throws IllegalArgumentException if o is null
	 */
	private void checkNullArgument(Object o) {
		if ((o == null)) throw new IllegalArgumentException("[SERVER-" + Thread.currentThread().getName() + "]: Null value received.");
	}
}
