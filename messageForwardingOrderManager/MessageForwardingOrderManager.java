/**
 * 
 */
package messageForwardingOrderManager;

import java.util.List;

import messagePrioritizationStrategies.MessageCachingPrioritizationStrategy;
import core.Message;
import core.MessageCacheManager;
import core.Settings;
import core.SimError;

/**
 * Abstract class for all MessageForwardingManager types.
 * 
 * @author Alessandro Morelli
 *
 */
public abstract class MessageForwardingOrderManager {
	
	public enum MessageForwardingOrderStrategy {ORDER_UNCHANGED, EXP_DEC_ORDER};
	
	private MessageForwardingOrderStrategy forwardingOrderStrategy;
	protected MessageCacheManager messageCacheManager;
	protected MessageCachingPrioritizationStrategy messageCachingPrioritizationStrategy;


	/**
	 * Creates the correct {@link MessageForwardingOrderManager} instance
	 * according to the parameters passed
	 * @param messageforwardingOrderStrategy the ordering algorithm to use
	 * @param cacheManager the caching manager that stores messages
	 * @param cachingPrioritizationStrategy the strategy to prioritize cached messages
	 * @return the correct instance of a {@link MessageForwardingOrderManager}
	 */
	public static MessageForwardingOrderManager messageForwardingManagerFactory(Settings s,
		MessageForwardingOrderStrategy messageforwardingOrderStrategy,
		MessageCacheManager cacheManager,
		MessageCachingPrioritizationStrategy cachingPrioritizationStrategy) {
		switch (messageforwardingOrderStrategy) {
		case ORDER_UNCHANGED:
			return new UnchangedForwardingOrder(s, cacheManager, cachingPrioritizationStrategy);
		case EXP_DEC_ORDER:
			return new ExponentiallyDecayingForwardingOrder(s, cacheManager, cachingPrioritizationStrategy);
		default:
			throw new SimError("Wrong messageForwardingManager type");
		}
	}
	
	public MessageForwardingOrderManager(MessageForwardingOrderStrategy managerType,
			MessageCacheManager cacheManager,
			MessageCachingPrioritizationStrategy cachingPrioritizationStrategy) {
		this.forwardingOrderStrategy = managerType;
		this.messageCacheManager = cacheManager;
		this.messageCachingPrioritizationStrategy = cachingPrioritizationStrategy;
	}
	
	public MessageForwardingOrderManager(MessageForwardingOrderManager r) {
		this.forwardingOrderStrategy = r.forwardingOrderStrategy;
		this.messageCacheManager = r.messageCacheManager;
		this.messageCachingPrioritizationStrategy = r.messageCachingPrioritizationStrategy;
	}
	
	/**
	 * Returns a copy of this Message forwarding manager.
	 * @return a copy of this Message forwarding manager.
	 */
	public abstract MessageForwardingOrderManager replicate();

	/**
	 * Returns an instance of {@link MessageForwardingOrderStrategy}
	 * representing the type of {@link MessageForwardingOrderManager}.
	 * @return the instance of {@link MessageForwardingOrderStrategy}
	 * representing the type of the current {@link MessageForwardingOrderManager}
	 */
	MessageForwardingOrderStrategy getManagerType() {
		return forwardingOrderStrategy;
	}

	/**
	 * Returns a new list of Messages containing all the
	 * Messages belonging to the list passed as parameter
	 * and ordered according to the specified implementation.
	 * @param inputList the {@code List<Message>} to order. 
	 * @return the ordered List of Messages.
	 */
	abstract public List<Message> orderMessageListForForwarding(List<Message> inputList);

	/**
	 * Returns true if the two lists passed as parameters
	 * are one the permutation of the other.
	 * @param l1 first List of messages.
	 * @param l2 second List of messages.
	 * @return true if l1 and l2 are permutations
	 * of the same list.
	 */
	public boolean isPermutationOf(List<Message> l1, List<Message> l2) {
		if (l1.size() != l2.size()) {
			return false;
		}
		
		for (Message m : l1) {
			if (!l2.contains(m)) {
				return false;
			}
		}
		
		return true;
	}
}