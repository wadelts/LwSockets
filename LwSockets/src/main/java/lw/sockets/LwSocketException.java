package lw.sockets;

/**
  * Encapsulates exceptions resulting from errors returned from socket activity.
  * @author Liam Wade
  * @version 1.0 11/12/2008
  */
public class LwSocketException extends Exception
{
  /**
	 * 
	 */
	private static final long serialVersionUID = 1L;

/**
    * Will create a new exception.
    */
	public LwSocketException() {
	}

  /**
    * Will create a new exception with the given message.
	* @param message the text explaining the error
    */
	public LwSocketException(String message) {
		super(message);
		errorCode = 0;
	}

  /**
    * Will create a new exception with the given message and remembering the causing exception.
	* @param message, the detail message (which is saved for later retrieval by the Throwable.getMessage() method).
	* @param cause - the cause (which is saved for later retrieval by the Throwable.getCause() method). (A null value is permitted, and indicates that the cause is nonexistent or unknown.)
    */
	public LwSocketException(String message, Throwable cause) {
		super(message, cause);
	}

	  /**
	    * Will create a new exception with the given message.
		* @param message the text explaining the error
		* @param errorCode the error code associated with the exception
	    */
		public LwSocketException(String message, int errorCode) {
			super(message);
			this.errorCode = errorCode;
		}

	/**
	  *
	  * Get the last error code
	  *
	  * @return the last error code
	  */
	public int getErrorCode() {
		return errorCode;
	}

	private int errorCode = 0;		// aid to identifying actual problem
}