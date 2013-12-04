/**
 * 
 */
package core;

import java.util.List;

/**
 * Provides the interface for interference managers
 * @author Alex 
 */
public interface InterferenceModel {
	
	/** Return code for successful reception */
	public static final int RECEPTION_OK = 0;
	/** Return code for a reception completed correctly */
	public static final int RECEPTION_COMPLETED_CORRECTLY = 0;

	/** Return code for a message not found error */
	public static final int MESSAGE_ID_NOT_FOUND = 1;

	/** Return code for a message interfered error */
	public static final int RECEPTION_INTERFERENCE = -1;
	/** Return code for a message reception denied due to
	 * the Network Interface that was busy sending a frame */
	public static final int RECEPTION_DENIED_DUE_TO_SEND = -2;
	/** Return code for a message interfered error */
	public static final int RECEPTION_INCOMPLETE = -3;


	
	/**
	 * Returns a copy of this interference model.
	 * @return a copy of this interference model.
	 */
	InterferenceModel replicate();
	
	/**
	 * This method has to be called when the model is instanced
	 * the first time.
	 * @param networkInterface NetworkInterface associated to
	 * the interference model
	 */
	void setNetworkInterface (NetworkInterface networkInterface);
	
	/**
	 * Returns the number of incoming messages
	 * @return an int that represents the number of incoming messages.
	 */
	int getIncomingMessageNumber();
	
	/**
	 * This method has to be called whenever the reception
	 * of a new message begins. It informs the caller whether
	 * the reception can continue successfully.
	 * @param m Message to be received
	 * @param con Connection transferring the message {@code m}
	 * @return an int among {@code RECEPTION_OK}, if successful,
	 * {@code RECEPTION_DENIED_DUE_TO_SEND} if the interface
	 * is busy, and {@code RECEPTION_INTERFERENCE} if the new
	 * reception triggers an interference
	 */
	int beginNewReception (Message m, Connection con);
	
	/**
	 * This method informs the caller whether the transfer has
	 * been completed successfully, with an interference, or if
	 * the transfer is not complete, yet.
	 * @param msgID String representing the messageID
	 * @return an int among {@code RECEPTION_OK}, if successful,
	 * {@code RECEPTION_INCOMPLETE}, if the transfer has not been
	 * completed yet, and {@code RECEPTION_INTERFERENCE} if the
	 * transfer completed with an interference.
	 */
	int isMessageTransferredCorrectly (String msgID, Connection con);
	
	/**
	 * This method force an interference event to occur. The status
	 * of interfered is set for the specified message.
	 * @param msgID String representing the messageID
	 * @return an int among {@code RECEPTION_OK}, if successful,
	 * {@code RECEPTION_INCOMPLETE}, if the transfer has not been
	 * completed yet, and {@code RECEPTION_INTERFERENCE} if the
	 * transfer completed with an interference.
	 */
	Message forceInterference (String msgID, Connection con);
	
	/**
	 * This method allows the caller to retrieve the message with
	 * the messageID specified as a parameter. If the transfer is
	 * not yet completed, or if it completed with an interference,
	 * the method will return {@code null}.
	 * @param msgID String representing the messageID
	 * @return the requested Message, if the transfer completed
	 * without interferences, or {@code null} if the transfer is
	 * not completed or an interference has occurred.
	 */
	Message retrieveTransferredMessage (String msgID, Connection con);
	
	/**
	 * This method allows the caller to retrieve all the
	 * messages which have are being transferred.
	 * It can be useful for statistics purposes.
	 * @return a List containing all Messages being transferred.
	 */
	List<Message> getListOfMessagesInTransfer();
	
	/**
	 * This method allows the caller to retrieve all the messages
	 * which have been transferred completely without interferences.
	 * @return a List containing all transferred Messages.
	 */
	List<Message> retrieveAllTransferredMessages();
	
	/**
	 * This method notifies the InterferenceModel that 
	 * @param msgID String representing the messageID
	 * @return the requested Message, if the transfer completed
	 * without interferences, or {@code null} if the transfer is
	 * not completed or an interference has occurred.
	 */
	Message abortMessageReception (String msgID, Connection con);

}
