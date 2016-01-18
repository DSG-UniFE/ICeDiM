/**
 * 
 */
package messagePrioritizationStrategies;

import java.util.List;

import core.Message;
import core.SimError;

/**
 * @author Alessandro Morelli
 *
 */
public abstract class MessageCachingPrioritizationStrategy {
	/**
	 * Message/fragment caching prioritization mode -setting id ({@value}). 
	 * This setting affects the priority that governs messages and fragments
	 * when added to the cache. Also, it affects message forwarding order if
	 * the routing protocol doesn't define any particular order.
	 * Valid values are<BR>
	 * <UL>
	 * <LI/> 0 : random (caching priority is randomized every time; default option)
	 * <LI/> 1 : FIFO (least recently received messages have higher priority)
	 * <LI/> 2 : Prioritized_FIFO (FIFO with higher priority messages first)
	 * <LI/> 3 : Prioritized_LFF_FIFO (Prioritized_FIFO where least forwarded 
	 * 			 messages have higher caching priority - attempt to be fairer)
	 * </UL>
	 */
	public static enum CachingPrioritizationMode {Random, FIFO, Prioritized_FIFO, Prioritized_LFF_FIFO}
	
	private final CachingPrioritizationMode cachingPrioritizationMode;
	
	abstract public void sortList(List<Message> inputList);
	abstract public void sortListInReverseOrder(List<Message> inputList);
	abstract public int comparatorMethod(Message m1, Message m2);
	
	static public MessageCachingPrioritizationStrategy messageCachingPrioritizationStrategyFactory
													(CachingPrioritizationMode cpm) {
		switch (cpm) {
		case Random:
			return RandomOrder.getOrderingInstance();
		case FIFO:
			return FIFOOrder.getOrderingInstance();
		case Prioritized_FIFO:
			return PrioritizedFIFOOrder.getOrderingInstance();
		case Prioritized_LFF_FIFO:
			return LeastForwardedFirstFIFOOrder.getOrderingInstance();
		/* add more queue modes here */
		default:
			throw new SimError("Undefined message forwarding order mode");
		}
	}
	
	protected MessageCachingPrioritizationStrategy (CachingPrioritizationMode qfom) {
		this.cachingPrioritizationMode = qfom;
	}
	
	public CachingPrioritizationMode getCachingPrioritizationMode() {
		return cachingPrioritizationMode;
	}

}
