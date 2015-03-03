/**
 * 
 */
package messageForwardingOrderManager;

import java.util.List;

import messagePrioritizationStrategies.MessagePrioritizationStrategy;
import core.Message;
import core.MessageQueueManager;
import core.Settings;
import core.SimError;

/**
 * Abstract class for all MessageForwardingManager types.
 * 
 * @author Alessandro Morelli
 *
 */
public abstract class MessageForwardingOrderManager {
	
	public enum MessageForwardingManagerImplementation {FIFO_MANAGER, EDP_MANAGER};
	
	private MessageForwardingManagerImplementation managerType;
	protected MessageQueueManager messageQueueManager;
	protected MessagePrioritizationStrategy messageOrderingStrategy;


	/**
	 * Creates the correct {@link MessageForwardingOrderManager} instance
	 * according to the parameters passed
	 * @param messageForwardingManagerImplementation the manager type to instantiate
	 * @param queueManager the queueing manager that buffers messages
	 * @param orderingStrategy the strategy to order queued messages
	 * @return the correct instance of a {@link MessageForwardingOrderManager}
	 */
	public static MessageForwardingOrderManager messageForwardingManagerFactory(Settings s,
		MessageForwardingManagerImplementation messageForwardingManagerImplementation,
		MessageQueueManager queueManager, MessagePrioritizationStrategy orderingStrategy) {
		switch (messageForwardingManagerImplementation) {
		case FIFO_MANAGER:
			return new FIFOManager(s, queueManager, orderingStrategy);
		case EDP_MANAGER:
			return new ExponentiallyDecayingManager(s, queueManager, orderingStrategy);
		default:
			throw new SimError("Wrong messageForwardingManager type");
		}
	}
	
	public MessageForwardingOrderManager(MessageForwardingManagerImplementation managerType,
											MessageQueueManager queueManager,
											MessagePrioritizationStrategy orderingStrategy) {
		this.managerType = managerType;
		this.messageQueueManager = queueManager;
		this.messageOrderingStrategy = orderingStrategy;
	}
	
	public MessageForwardingOrderManager(MessageForwardingOrderManager r) {
		this.managerType = r.managerType;
		this.messageQueueManager = r.messageQueueManager;
		this.messageOrderingStrategy = r.messageOrderingStrategy;
	}
	
	/**
	 * Returns a copy of this Message forwarding manager.
	 * @return a copy of this Message forwarding manager.
	 */
	public abstract MessageForwardingOrderManager replicate();

	/**
	 * Returns an instance of {@link MessageForwardingManagerImplementation}
	 * representing the type of {@link MessageForwardingOrderManager}.
	 * @return the instance of {@link MessageForwardingManagerImplementation}
	 * representing the type of the current {@link MessageForwardingOrderManager}
	 */
	MessageForwardingManagerImplementation getManagerType() {
		return managerType;
	}

	/**
	 * Returns a new list of Messages containing all the
	 * Messages belonging to the list passed as parameter
	 * and ordered accoding to the specified implementation.
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