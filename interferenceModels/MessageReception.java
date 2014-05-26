/**
 * 
 */
package interferenceModels;

import core.Connection;
import core.Message;

/**
 * This class maps the act of receiving a message
 * by a network interface
 * 
 * @author Alessandro Morelli
 */
public class MessageReception {

	private final Message message;
	private final Connection connection;
	private final boolean isTransferInSynch;
	private boolean isInterfered;
	
	MessageReception(Message m, Connection con, boolean synch) {
		this (m, con, synch, false);
	}

	MessageReception(Message m, Connection con, boolean synch, boolean interference) {
		this.message = m;
		this.connection = con;
		this.isInterfered = interference;
		this.isTransferInSynch = synch;
	}


	public Message getMessage() {
		return message;
	}

	public Connection getConnection() {
		return connection;
	}

	public boolean isTransferInSynch() {
		return isTransferInSynch;
	}

	public boolean isInterfered() {
		return isInterfered;
	}

	public void setInterfered(boolean isInterfered) {
		this.isInterfered = isInterfered;
	}
	
	public boolean isTransferCompleted() {
		return getConnection().isMessageTransferred();
	}
	
	public boolean isTransferCompletedCorrectly() {
		return getConnection().isMessageTransferred() && isTransferInSynch() && !isInterfered();
	}

}
