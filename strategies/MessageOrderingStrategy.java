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
public abstract class MessageOrderingStrategy {
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
	public static enum QueueForwardingOrderMode {Random, FIFO, Prioritized_FIFO, Prioritized_LFF_FIFO}
	
	private final QueueForwardingOrderMode queueForwardingMode;
	
	abstract public <T> void sortList(List<T> inputList);
	abstract public <T> void sortListInReverseOrder(List<T> inputList);
	abstract public int comparatorMethod(Message m1, Message m2);
	
	static public MessageOrderingStrategy MessageForwardingStrategyFactory
									(QueueForwardingOrderMode qfom) {
		switch (qfom) {
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
	
	protected MessageOrderingStrategy (QueueForwardingOrderMode qfom) {
		this.queueForwardingMode = qfom;
	}
	
	public QueueForwardingOrderMode getQueueForwardingMode() {
		return queueForwardingMode;
	}

}