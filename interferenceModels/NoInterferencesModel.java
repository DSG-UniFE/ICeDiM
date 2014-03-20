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
public final class NoInterferencesModel implements InterferenceModel {

	NetworkInterface networkInterface;
	HashMap<String, MessageReception> receivingMessagesList;
	
	public NoInterferencesModel() {
		networkInterface = null;
		receivingMessagesList = new HashMap<String, MessageReception>();
	}
	
	public NoInterferencesModel(Settings s) {
		this();
	}

	@Override
	public InterferenceModel replicate() {
		return new NoInterferencesModel();
	}
	
	@Override
	public void setNetworkInterface(NetworkInterface networkInterface) {
		this.networkInterface = networkInterface;
	}

	@Override
	public int getIncomingMessageNumber() {
		return receivingMessagesList.size();
	}

	@Override
	public int beginNewReception(Message m, Connection con) {
		NetworkInterface ni = con.getReceiverInterface();
		assert (ni == networkInterface) : "The receiving interface of connection " +
				con + " and the one associated with this interference model differ";
		
		// Check for an interference
		MessageReception newMessageReception = new MessageReception(m, con);
		receivingMessagesList.put(m.getID() + "_i" + con.getSenderInterface().getAddress(),
									newMessageReception);
		
		return RECEPTION_OK;
	}

	@Override
	public int isMessageTransferredCorrectly(String msgID, Connection con) {
		MessageReception msgReception = findCorrectMessageInList(msgID, con);
		if (msgReception != null) {
			if (!msgReception.getConnection().isMessageTransferred()) {
				return RECEPTION_INCOMPLETE;
			}
			return RECEPTION_COMPLETED_CORRECTLY;
		}
		
		return MESSAGE_ID_NOT_FOUND;
	}

	@Override
	public Message forceInterference(String msgID, Connection con) {
		MessageReception msgReception = findCorrectMessageInList(msgID, con);
		if (msgReception != null) {
			msgReception.setInterfered(true);
			return msgReception.getMessage();
		}
		
		return null;
	}

	@Override
	public Message retrieveTransferredMessage(String msgID, Connection con) {
		MessageReception msgReception = findCorrectMessageInList(msgID, con);
		if (msgReception != null) {
			if (msgReception.getConnection().isMessageTransferred()) {
				removeMessageFromList(msgID, con);
				return msgReception.getMessage();
			}			
			// Message transfer incomplete
			return null;
		}
		
		// Message not found --> ERROR!!!
		throw new SimError("Message with ID " + msgID + " could not be found!");
	}
	
	@Override
	public List<Message> getListOfMessagesInTransfer() {
		ArrayList<Message> messages = new ArrayList<Message>(receivingMessagesList.size());
		for (MessageReception mr : receivingMessagesList.values()) {
			messages.add(mr.getMessage());
		}
		
		return messages;
	}

	@Override
	public List<Message> retrieveAllTransferredMessages() {
		ArrayList<Message> transferredMessages = new ArrayList<Message>();
		MessageReception[] messageReceptionArray = receivingMessagesList.values().
													toArray(new MessageReception[0]);
		for (MessageReception mr : messageReceptionArray) {
			if (mr.isTransferCompletedCorrectly()) {
				transferredMessages.add(mr.getMessage());
				removeMessageFromList(mr.getMessage().getID(), mr.getConnection());
			}
		}
		
		return transferredMessages;
	}

	@Override
	public Message abortMessageReception(String msgID, Connection con) {
		MessageReception msgReception = findCorrectMessageInList(msgID, con);
		if (msgReception != null) {
			if (null == removeMessageFromList(msgID, con)) {
				throw new SimError("Failed to remove MessageReception entry from interference model");
			}
			return msgReception.getMessage();
		}
		
		return null;
	}
	
	private MessageReception findCorrectMessageInList (String msgID, Connection con) {
		return receivingMessagesList.get(msgID + "_i" + con.getSenderInterface().getAddress());
	}
	
	private MessageReception removeMessageFromList (String msgID, Connection con) {
		return receivingMessagesList.remove(msgID + "_i" + con.getSenderInterface().getAddress());
	}
}
