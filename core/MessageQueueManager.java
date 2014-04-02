/**
 * 
 */
package core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import messageForwardingManager.MessageForwardingManager;

import strategies.MessageOrderingStrategy;

/**
 * This class models the queue of messages in memory.
 * It implements the logic that manages the insertion
 * and the deletion of messages from the buffer.
 * Also, it provides an interface to extract buffered
 * messages in accordance with the policy specified
 * in the configuration file.
 * 
 * @author Alex
 *
 */
public class MessageQueueManager {
	/** Message buffer size -setting id ({@value}). Integer value in bytes.*/
	public static final String B_SIZE_S = "bufferSize";
	/** string that identifies the queueing mode in the settings file */
	public static final String SEND_QUEUE_MODE_S = "sendQueueMode";
	/** string that identifies the forwarding manager in the settings file */
	public static final String MESSAGE_FORWARDING_MANAGER_S = "messageForwardingManager";

	/** size of the buffer */
	private final int bufferSize;
	/** The messages this router is carrying */
	private HashMap<String, Message> messages;

	/** The manager that implements the message forwarding policy */
	MessageForwardingManager messageForwardingManager;
	/** Strategy which implements the specified queueing mode */
	public MessageOrderingStrategy messageOrderingStrategy;

	public MessageQueueManager(Settings s) {
		// Default buffer size is large (2GB)
		this.bufferSize = s.contains(B_SIZE_S) ? s.getInt(B_SIZE_S) : Integer.MAX_VALUE;
		this.messages = new HashMap<String, Message>();
		
		int sendQueueMode = 0;
		if (s.contains(SEND_QUEUE_MODE_S)) {
			sendQueueMode = s.getInt(SEND_QUEUE_MODE_S);
			if ((sendQueueMode < 0) || (sendQueueMode >=
					MessageOrderingStrategy.QueueForwardingOrderMode.values().length)) {
				throw new SettingsError("Invalid value for " + s.getFullPropertyName(SEND_QUEUE_MODE_S));
			}
		}
		this.messageOrderingStrategy = MessageOrderingStrategy.MessageForwardingStrategyFactory(
				MessageOrderingStrategy.QueueForwardingOrderMode.values()[sendQueueMode]);

		int messageForwarderType = 0;
		if (s.contains(MESSAGE_FORWARDING_MANAGER_S)) {
			messageForwarderType = s.getInt(MESSAGE_FORWARDING_MANAGER_S);
			if ((messageForwarderType < 0) || (messageForwarderType >=
					MessageForwardingManager.MessageForwardingManagerImplementation.values().length)) {
				throw new SettingsError("Invalid value for " + s.getFullPropertyName(MESSAGE_FORWARDING_MANAGER_S));
			}
		}
		this.messageForwardingManager = MessageForwardingManager.messageForwardingManagerFactory(
				MessageForwardingManager.MessageForwardingManagerImplementation.values()[messageForwarderType],
				this, this.messageOrderingStrategy);
	}

	/** Copy constructor */
	public MessageQueueManager(MessageQueueManager mqm) {
		this.bufferSize = mqm.bufferSize;
		this.messages = new HashMap<String, Message>();
		
		// Create a new messageForwardingStrategy of the same type of the copied MessageQueueManager
		this.messageOrderingStrategy = MessageOrderingStrategy.MessageForwardingStrategyFactory(
											mqm.messageOrderingStrategy.getQueueForwardingMode());
	}
	
	public Message getMessage(String messageID) {
		return messages.get(messageID);
	}
	
	public boolean hasMessage(String messageID) {
		return messages.containsKey(messageID);
	}
	
	public boolean hasMessage(Message m) {
		return messages.containsKey(m.getID());
	}
	
	public int getNumberOfMessages() {
		return messages.size();
	}
	
	public void addMessageToQueue(Message m) {		
		setForwardedTimesToMinAmongMessages(m);
		messages.put(m.getID(), m);
	}
	
	public Message removeMessage(String messageID) {
		return messages.remove(messageID);
	}
	
	public Collection<Message> getMessageCollection() {
		return messages.values();
	}
	
	public List<Message> getMessageList() {
		return new ArrayList<Message>(messages.values());
	}
	
	public int getBufferSize() {
		return bufferSize;
	}

	public int getFreeBufferSize() {
		int occupancy = 0;		
		for (Message m : getMessageCollection()) {
			occupancy += m.getSize();
		}
		
		return getBufferSize() - occupancy;
	}

	public <T> List<T> sortByQueueMode(List<T> list) {
		return messageOrderingStrategy.messageProcessingOrder(list);
	}

	public <T> List<T> reverseOrderByQueueMode(List<T> list) {
		return messageOrderingStrategy.sortListInReverseOrder(list);
	}

	public int compareByQueueMode(Message m1, Message m2) {
		return messageOrderingStrategy.comparatorMethod(m1, m2);
	}
	
	
	private void setForwardedTimesToMinAmongMessages(Message m) {
		if (messages.size() > 0) {
			int min = Integer.MAX_VALUE;
			for (Message msg : messages.values()) {
				if (msg.getForwardTimes() < min) {
					min = msg.getForwardTimes();
				}
			}
			while (m.getForwardTimes() < min) {
					m.incrementForwardTimes();
			}
		}
	}
}
