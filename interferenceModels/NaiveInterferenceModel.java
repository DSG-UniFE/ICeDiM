/**
 * 
 */
package interferenceModels;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import core.Connection;
import core.InterferenceModel;
import core.Message;
import core.NetworkInterface;
import core.Settings;
import core.SimError;

/**
 * This interference model always receives correctly any frame
 * with which the receiving interface could synchronize (that
 * is, any frame received when the interface was idle).
 * @author Alex
 */
public final class NaiveInterferenceModel implements InterferenceModel {

	NetworkInterface networkInterface;
	HashMap<String, MessageReception> receivingMessagesList;
	
	public NaiveInterferenceModel() {
		networkInterface = null;
		receivingMessagesList = new HashMap<String, MessageReception>();
	}
	
	public NaiveInterferenceModel(Settings s) {
		this();
	}

	@Override
	public InterferenceModel replicate() {
		return new NaiveInterferenceModel();
	}
	
	@Override
	public void setNetworkInterface(NetworkInterface networkInterface) {
		this.networkInterface = networkInterface;
	}

	@Override
	public int beginNewReception(Message m, Connection con) {
		NetworkInterface ni = con.getReceiverInterface();
		assert (ni == networkInterface) : "The receiving interface of connection " +
				con + " and the one associated with this interference model differ";
		
		// Check for an interference
		MessageReception newMessageReception = new MessageReception(m, con);
		for (MessageReception msgReception : receivingMessagesList.values()) {
			if (!msgReception.getConnection().isMessageTransferred()) {
				newMessageReception.setInterfered(true);
				break;
			}
		}
		receivingMessagesList.put(m.getId() + "_i" + con.getSenderInterface().getAddress(),
									newMessageReception);
		
		return newMessageReception.isInterfered() ? RECEPTION_INTERFERENCE : RECEPTION_OK;
	}

	@Override
	public int isMessageTransferredCorrectly(String msgID, Connection con) {
		MessageReception msgReception = findCorrectMessageInList(msgID, con);
		if (msgReception != null) {
			if (!msgReception.getConnection().isMessageTransferred()) {
				return RECEPTION_INCOMPLETE;
			}
			else if (msgReception.isInterfered()) {
				return RECEPTION_INTERFERENCE;
			}
			return RECEPTION_COMPLETED_CORRECTLY;
		}
		
		return MESSAGE_ID_NOT_FOUND;
	}

	@Override
	public Message retrieveTransferredMessage(String msgID, Connection con) {
		MessageReception msgReception = findCorrectMessageInList(msgID, con);
		if (msgReception != null) {
			if (msgReception.getConnection().isMessageTransferred() &&
				!msgReception.isInterfered()) {
				removeMessageFromList(msgID, con);
				return msgReception.getMessage();
			}
			else if (msgReception.getConnection().isMessageTransferred()) {
				// Interference: remove message and return null
				if (null == removeMessageFromList(msgID, con)) {
					throw new SimError("Failed to remove MessageReception entry from interference model");
				}
				return null;
			}
			
			// Message transfer incomplete
			return null;
		}
		
		// Message not found --> ERROR!!!
		assert false : "Message with ID " + msgID + " could not be found!";
		return null;
	}

	@Override
	public List<Message> retrieveAllTransferredMessages() {
		ArrayList<Message> transferredMessages = new ArrayList<Message>(1);
		MessageReception[] messageReceptionArray = receivingMessagesList.values().toArray(new MessageReception[0]);
		for (int i = 0; i < messageReceptionArray.length; ++i) {
			if (messageReceptionArray[i].isTransferCompletedCorrectly()) {
				transferredMessages.add(messageReceptionArray[i].getMessage());
				removeMessageFromList(messageReceptionArray[i].getMessage().getId(),
										messageReceptionArray[i].getConnection());
			}
		}
		
		return transferredMessages;
	}

	@Override
	public void abortMessageReception(String msgID, Connection con) {
		MessageReception msgReception = findCorrectMessageInList(msgID, con);
		if (msgReception != null) {
			if (null == removeMessageFromList(msgID, con)) {
				throw new SimError("Failed to remove MessageReception entry from interference model");
			}
			return;
		}
	}
	
	private MessageReception findCorrectMessageInList (String msgID, Connection con) {
		return receivingMessagesList.get(msgID + "_i" + con.getSenderInterface().getAddress());
	}
	
	private MessageReception removeMessageFromList (String msgID, Connection con) {
		return receivingMessagesList.remove(msgID + "_i" + con.getSenderInterface().getAddress());
	}
}
