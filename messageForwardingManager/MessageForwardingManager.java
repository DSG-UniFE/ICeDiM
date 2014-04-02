/**
 * 
 */
package messageForwardingManager;

import java.util.List;

import strategies.MessageOrderingStrategy;
import core.Message;
import core.MessageQueueManager;
import core.SimError;

/**
 * Abstract class for all MessageForwardingManager types.
 * 
 * @author Alex
 *
 */
public abstract class MessageForwardingManager {
	
	public enum MessageForwardingManagerImplementation {FIFO_MANAGER, PROBABILISTIC_MANAGER};
	
	private MessageForwardingManagerImplementation managerType;
	protected MessageQueueManager messageQueueManager;
	protected MessageOrderingStrategy messageOrderingStrategy;


	/**
	 * Creates the correct {@link MessageForwardingManager} instance
	 * according to the parameters passed
	 * @param messageForwardingManagerImplementation the manager type to instantiate
	 * @param queueManager the queueing manager that buffers messages
	 * @param orderingStrategy the strategy to order queued messages
	 * @return the correct instance of a {@link MessageForwardingManager}
	 */
	public static MessageForwardingManager messageForwardingManagerFactory(
		MessageForwardingManagerImplementation messageForwardingManagerImplementation,
		MessageQueueManager queueManager, MessageOrderingStrategy orderingStrategy) {
		switch (messageForwardingManagerImplementation) {
		case FIFO_MANAGER:
			return new FIFOManager(queueManager, orderingStrategy);
		case PROBABILISTIC_MANAGER:
			return new ProbabilisticManager(queueManager, orderingStrategy);
		default:
			throw new SimError("Wrong messageForwardingManager type");
		}
	}
	
	public MessageForwardingManager(MessageForwardingManagerImplementation managerType,
									MessageQueueManager queueManager,
									MessageOrderingStrategy orderingStrategy) {
		this.managerType = managerType;
		this.messageQueueManager = queueManager;
		this.messageOrderingStrategy = orderingStrategy;
	}
	
	/**
	 * Returns a copy of this Message forwarding manager.
	 * @return a copy of this Message forwarding manager.
	 */
	public MessageForwardingManager replicate() {
		return messageForwardingManagerFactory(getManagerType(), messageQueueManager,
												messageOrderingStrategy);
	}
	
	MessageForwardingManagerImplementation getManagerType() {
		return managerType;
	}

	/**
	 * Returns the next message to be forwarded
	 * to neighboring nodes.
	 * @return the Message to be forwarded, or
	 * {@code null} if no messages are available.
	 */
	abstract public Message getNextMessage();
	
	/**
	 * Returns the next message to be forwarded
	 * to neighboring nodes and advance the pointer
	 * to the messages in the queue in a single call.
	 * @return the Message to be forwarded, or
	 * {@code null} if no messages are available.
	 */
	abstract public Message getNextMessageAndAdvanceQueue();

	/**
	 * Advances the pointer to the messages in the
	 * queue (that is, it skips a message).
	 */
	abstract public void advanceQueue();
	
	/** Resets the order of messages. */
	abstract public void resetMessageOrder();

	/**
	 * Returns a new list of Messages containing all the
	 * Messages belonging to the list passed as parameter
	 * and ordered accoding to the specified implementation.
	 * @param inputList the {@code List<Message>} to order. 
	 * @return the ordered List of Messages.
	 */
	abstract public List<Message> sortMessageList(List<Message> inputList);

	/**
	 * Returns a list of Messages ordered accoding
	 * to the specified implementation and to the
	 * current position in the queue.
	 * @return the ordered List of Messages available.
	 */
	abstract public List<Message> getOrderedMessageQueue();

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