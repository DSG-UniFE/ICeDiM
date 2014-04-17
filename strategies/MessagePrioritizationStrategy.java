/**
 * 
 */
package strategies;

import java.util.List;

import core.Message;
import core.SimError;

/**
 * @author Alex
 *
 */
public abstract class MessagePrioritizationStrategy {
	/**
	 * Message/fragment sending queue type -setting id ({@value}). 
	 * This setting affects the order the messages and fragments are sent if the
	 * routing protocol doesn't define any particular order (e.g, if more than 
	 * one message can be sent directly to the final recipient). 
	 * Valid values are<BR>
	 * <UL>
	 * <LI/> 0 : random (message order is randomized every time; default option)
	 * <LI/> 1 : FIFO (most recently received messages are sent last)
	 * <LI/> 2 : Prioritized_FIFO (FIFO with highest priority messages are sent first)
	 * <LI/> 3 : Prioritized_LFF_FIFO (Prioritized_FIFO with least forwarded messages sent first - attempt to be fairer)
	 * </UL>
	 */ 
	public static enum QueuePrioritizationMode {Random, FIFO, Prioritized_FIFO, Prioritized_LFF_FIFO}
	
	private final QueuePrioritizationMode queueForwardingMode;
	
	abstract public void sortList(List<Message> inputList);
	abstract public void sortListInReverseOrder(List<Message> inputList);
	abstract public int comparatorMethod(Message m1, Message m2);
	
	static public MessagePrioritizationStrategy MessageForwardingStrategyFactory
													(QueuePrioritizationMode qpm) {
		switch (qpm) {
		case Random:
			return RandomForwardingOrder.getForwardingOrderInstance();
		case FIFO:
			return FIFOForwardingOrder.getForwardingOrderInstance();
		case Prioritized_FIFO:
			return PrioritizedFIFOForwardingOrder.getForwardingOrderInstance();
		case Prioritized_LFF_FIFO:
			return LeastForwardedFirstForwardingOrder.getForwardingOrderInstance();
		/* add more queue modes here */
		default:
			throw new SimError("Undefined message forwarding order mode");
		}
	}
	
	protected MessagePrioritizationStrategy (QueuePrioritizationMode qfom) {
		this.queueForwardingMode = qfom;
	}
	
	public QueuePrioritizationMode getQueueForwardingMode() {
		return queueForwardingMode;
	}

}