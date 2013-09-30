package lw.sockets;

import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.*;

import lw.sockets.interfaces.LwIXMLSocketServerListener;
import lw.utils.*;

public class ExampleSocketServer implements LwIXMLSocketServerListener
											,IApp
{

	// Add this to JVM command line to have logger print lines on single line (gods bless Stackoverflow!)...
	// -Djava.util.logging.SimpleFormatter.format="%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS %4$s %2$s %5$s%6$s%n"    private static final Logger logger = Logger.getLogger("gemha");
    private static final Logger logger = Logger.getLogger("gemha");

    private FileHandler fh = null;
    private final String logFileName = "logs/ExampleSocketServer.log";
    private final String shutDownLogFileName = "logs/ExampleSocketServer_shutdown.log";
    private LwXMLSocketServer sockServer = null;
	private ExecutorService execPool;				// the pool of threads for this application
	
	volatile private boolean stayAlive = true;

	/**
	  *
	  * Default constructor
	  */
	public ExampleSocketServer() {
	}

	/**
	  *
	  * Start the application
	  */
	public void start() {
		logger.info("Starting up");

		try {
			fh = new FileHandler(logFileName);
			// Change default format from XML records (XMLFormatter) to "easy-read"
			fh.setFormatter(new SimpleFormatter());

		}
		catch (IOException e) {
            logger.log(Level.SEVERE, "Couldn't open log file " + logFileName + ": ", e);
            System.exit(-1);
        }

        // Send logger output to our FileHandler.
        logger.addHandler(fh);

        // Use this to get startup level from props file
        try {
			logger.setLevel(Level.parse("FINER"));
			Level logLevel = logger.getLevel();
			logger.config("Log Level initialised to " + logLevel.getName());
		}
		catch(Exception e) {
			System.out.println("Invalid LogLevel in props file! " + e);
			System.exit(-2);
		}


		//////////////////////////////////////////////////////////////////////////////////
		// All OK, Start testing....
		// The interface methods below will handle messages
		//////////////////////////////////////////////////////////////////////////////////
		try {
			// Setting server up with only 1 connection thread allowed run at a time (albeit in its own thread, separate from the socket server itself)
			execPool = Executors.newFixedThreadPool(1);
			sockServer = new LwXMLSocketServer(execPool, this, 11819);
			new Thread(sockServer).start(); // LwXMLSocketServer implements Thread, so can have any number of servers!
			logger.info("sockServer started.");
		}
		catch(LwSocketException e) {
			logger.severe("Couldn't create new sockServer: " + e.getMessage());
			System.exit(-1);
		}

		logger.info("Going into main loop now, doing nothing.");
		while (stayAlive) {
			// Sleep for 10 seconds
			try {
				Thread.sleep(10000);
			}
			catch(InterruptedException e) {
				// do nothing
			}
		}
		
		execPool.shutdown();
	}

	//////////////////////////////////////////////////////////////////////////
	//				Start: Interface LwIXMLSocketServer Methods
	//
	//	NOTE:	These methods may be called by another thread, so make sure any
	//			instance variables are updated in a thread-safe manner. 
	//
	//////////////////////////////////////////////////////////////////////////
	/**
	  * Will be called by the supporting object when a complete message is available for delivery
	  *
	  * @param event holds information on the event
	  *
	  * @return true if the response is to be consumed, otherwise false
	  */
	@Override
	public boolean messageReceived(LwSocketEvent event) {
		logger.info("Got message from port " + event.getPortNumber() + " for TID " + event.getTID() + ": " + event.getReceivedMessage());
		return true;
	}

	/**
	  * Will be called by the supporting object when a complete message is available for delivery
	  * and a response is expected.
	  *
	  * @param event holds information on the event
	  *
	  * @return the response to be sent back over the socket
	  */
	@Override
	public String messageReceivedAndWantResponse(LwSocketEvent event) {
		logger.info("Got message from port " + event.getPortNumber() + " for TID " + event.getTID() + ": " + event.getReceivedMessage());
		return "<APP_DEFINED_RESPONSE><KEY>" + event.getTID() + "</KEY><STATUS>SUCCESS</STATUS></APP_DEFINED_RESPONSE>";
	}

	/**
	  * Will be called by the supporting object when an error is encountered
	  *
	  * @param event holds information on the event
	  * @param exception LwSocketException explaining the problem
	  *
	  */
	@Override
	public void handleError(LwSocketEvent event, LwSocketException exception) {
		logger.info("Handled error from Socket Server on port " + event.getPortNumber() + " for TID " + event.getTID() + " LwSocketException: " + exception.getMessage());
	}

	/**
	  * Will be called by the supporting object when a request has been received over the connection to close down.
	  * If true is returned, the socket server will be closed for good, otherwise the request will be ignored,
	  * (only the current socket will be closed).
	  *
	  * @param event holds information on the event
	  *
	  * @return true if the Socket Server shold close down, false if it should remain open
	  */
	@Override
	public boolean canCloseServerSocket(LwSocketEvent event) {
		logger.info("Got request to close Server Socket on port " + event.getPortNumber() + " for TID " + event.getTID());
		stayAlive = false;
		return true;
	}

	/**
	  * Will be called by the supporting object to find out if a message should be consumed.
	  *
	  * @return true if a message should be consumed, otherwise false
	  */
	@Override
	public boolean getConsumeMessage() {
		return true;
	}
	//////////////////////////////////////////////////////////////////////////
	//				End: Interface LwIXMLSocketServer Methods
	//////////////////////////////////////////////////////////////////////////

	/**
	  *
	  * Shut down the application gracefully - will be called by the Java VM when closing down
	  *
	  * NOTE : NEVER call System.exit() from this method, or you'll go into a continuous loop!
	  *
	  */
	public void shutDown() {
		// Do a graceful shutdown here
		// NOTE: Do not use the java.util.Logger here, use LwLogger.
		// The Logger has its own shutdown hook so it can be shut down by the
		// virtual machine before our shutdown hook calls this method.

		LwLogger shutdownLogger = null;

		try {
			shutdownLogger = new LwLogger(shutDownLogFileName, true);
		}
		catch(IOException e) {
			System.out.println("ExampleSocketServer: could not open file " + shutDownLogFileName + " to record shutdown.");
		}

		try {
			sockServer.close(shutdownLogger);
		}
		catch(LwSocketException e) {
			System.out.println("ExampleSocketServer.shutDown(): caught LwSocketException trying to shut down.");
		}

		try {
			shutdownLogger.appendln("ExampleSocketServer.shutDown(): Shutdown completed successfully");
			shutdownLogger.close();
		}
		catch(IOException e) {
			System.out.println("ExampleSocketServer.shutDown(): could not write to file " + shutDownLogFileName + " to record shutdown completed successfully.");
		}

	}

	public static void main(String argv[]) {

		IApp app = new ExampleSocketServer();

		ShutdownInterceptor shutdownInterceptor = new ShutdownInterceptor(app);

		// Register the thread to be called when the VM is shut down...
		Runtime.getRuntime().addShutdownHook(shutdownInterceptor);

		// Let's go...
		app.start();
	}
}
