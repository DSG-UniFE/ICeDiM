/**
 * 
 */
package core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import messageForwardingOrderManager.MessageForwardingOrderManager;
import messagePrioritizationStrategies.MessageCachingPrioritizationStrategy;

/**
 * This class models the cache manager for the node.
 * It implements the logic that manages the insertion and the deletion
 * of messages from cache. Also, it provides an interface to extract
 * cached messages in accordance with the policy specified in the
 * configuration file.
 * 
 * @author Alessandro Morelli
 *
 */
public class MessageCacheManager {
	/** Cache size -setting id ({@value}). Integer value in bytes.*/
	public static final String CACHE_SIZE_S = "cacheSize";
	/** string that identifies the cache prioritization strategy in the settings file */
	public static final String CACHING_PRIORITIZATION_STRATEGY_S = "cachingPrioritizationStrategy";
	/** string that identifies the forwarding order strategy in the settings file */
	public static final String MESSAGE_FORWARDING_ORDER_STRATEGY_S = "messageForwardingOrderStrategy";

	/** size of the cache */
	private final int cacheSize;
	/** The messages this router is carrying */
	private HashMap<String, Message> messages;

	/** Manager that implements the message forwarding policy */
	private MessageForwardingOrderManager messageForwardingOrderManager;
	/** Strategy that implements the specified caching prioritization strategy */
	public MessageCachingPrioritizationStrategy messageCachingPrioritizationStrategy;

	public MessageCacheManager(Settings s) {
		// Default cache size is large (~2GB)
		this.cacheSize = s.contains(CACHE_SIZE_S) ? s.getInt(CACHE_SIZE_S) : Integer.MAX_VALUE;
		this.messages = new HashMap<String, Message>();
		
		int sendQueueMode = 0;
		if (s.contains(CACHING_PRIORITIZATION_STRATEGY_S)) {
			sendQueueMode = s.getInt(CACHING_PRIORITIZATION_STRATEGY_S);
			if ((sendQueueMode < 0) || (sendQueueMode >=
					MessageCachingPrioritizationStrategy.CachingPrioritizationMode.values().length)) {
				throw new SettingsError("Invalid value for " + s.getFullPropertyName(CACHING_PRIORITIZATION_STRATEGY_S));
			}
		}
		this.messageCachingPrioritizationStrategy = MessageCachingPrioritizationStrategy.messageCachingPrioritizationStrategyFactory(
				MessageCachingPrioritizationStrategy.CachingPrioritizationMode.values()[sendQueueMode]);

		int messageForwarderType = 0;
		if (s.contains(MESSAGE_FORWARDING_ORDER_STRATEGY_S)) {
			messageForwarderType = s.getInt(MESSAGE_FORWARDING_ORDER_STRATEGY_S);
			if ((messageForwarderType < 0) || (messageForwarderType >=
					MessageForwardingOrderManager.MessageForwardingOrderStrategy.values().length)) {
				throw new SettingsError("Invalid value for " + s.getFullPropertyName(MESSAGE_FORWARDING_ORDER_STRATEGY_S));
			}
		}
		this.messageForwardingOrderManager = MessageForwardingOrderManager.messageForwardingManagerFactory(s,
			MessageForwardingOrderManager.MessageForwardingOrderStrategy.values()[messageForwarderType],
			this, this.messageCachingPrioritizationStrategy);
	}

	/** Copy constructor */
	public MessageCacheManager(MessageCacheManager mqm) {
		this.cacheSize = mqm.cacheSize;
		this.messages = new HashMap<String, Message>();
		
		// Create a new messageForwardingOrderStrategy of the same type of the copied MessageCacheManager
		this.messageCachingPrioritizationStrategy = MessageCachingPrioritizationStrategy.messageCachingPrioritizationStrategyFactory(
										mqm.messageCachingPrioritizationStrategy.getCachingPrioritizationMode());
		this.messageForwardingOrderManager = mqm.messageForwardingOrderManager.replicate();
	}
	
	public Message getMessage(String messageID) {
		return messages.get(messageID);
	}
	
	public Collection<Message> getMessageCollection() {
		return messages.values();
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
	
	public List<Message> sortCachedMessagesForForwarding() {
		return sortMessageListForForwarding(getMessageList());
	}
	
	public List<Message> sortMessageListForForwarding(List<Message> inputList) {
		// First, sort the list according to the configured priority strategy...
		sortByCachingPrioritizationStrategy(inputList);
		
		/* ...and second, return the list reordered according to the forwarding
		 * strategy. The idea here is that the MessageForwardingManager has the
		 * last word over the order of the message list that will be returned.
		 * If the manager does not change the message order, this method will
		 * return the messages in the order set by the above call to
		 * sortByPrioritizationMode(inputList). */
		return sortByMessageForwardingStrategy(inputList);
	}
	
	public int getCacheSize() {
		return cacheSize;
	}

	public int getFreeCacheSize() {
		int occupancy = 0;		
		for (Message m : getMessageCollection()) {
			occupancy += m.getSize();
		}
		
		return getCacheSize() - occupancy;
	}

	public void sortByCachingPrioritizationStrategy(List<Message> inputList) {
		messageCachingPrioritizationStrategy.sortList(inputList);
	}

	public void sortByReversedPrioritizationMode(List<Message> inputList) {
		messageCachingPrioritizationStrategy.sortListInReverseOrder(inputList);
	}

	public int compareByPrioritizationMode(Message m1, Message m2) {
		return messageCachingPrioritizationStrategy.comparatorMethod(m1, m2);
	}

	
	private List<Message> getMessageList() {
		return new ArrayList<Message>(messages.values());
	}

	private List<Message> sortByMessageForwardingStrategy(List<Message> inputList) {
		return messageForwardingOrderManager.orderMessageListForForwarding(inputList);
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