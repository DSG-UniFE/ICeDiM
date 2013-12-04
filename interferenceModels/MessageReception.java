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
 * @author Alex
 */
public class MessageReception {

	private Message message;
	private Connection connection;
	private boolean isInterfered;
	
	MessageReception(Message m, Connection con) {
		this (m, con, false);
	}

	MessageReception(Message m, Connection con, boolean interference) {
		this.message = m;
		this.connection = con;
		this.isInterfered = interference;
	}
	

	public Message getMessage() {
		return message;
	}

	public Connection getConnection() {
		return connection;
	}	

	public boolean isInterfered() {
		return isInterfered;
	}

	public void setInterfered(boolean isInterfered) {
		this.isInterfered = isInterfered;
	}
	
	public boolean isTransferCompletedCorrectly() {
		return getConnection().isMessageTransferred() && !isInterfered();
	}

}
