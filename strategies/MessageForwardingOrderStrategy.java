/**
 * 
 */
package strategies;

import java.util.List;

import routing.MessageRouter;
import core.Message;
import core.SimError;

/**
 * @author Alex
 *
 */
public abstract class MessageForwardingOrderStrategy {
	
	abstract public <T> List<T> MessageProcessingOrder(List<T> inputList);
	abstract public int ComparatorMethod(Message m1, Message m2);
	
	static public MessageForwardingOrderStrategy MessageForwardingStrategyFactory
									(MessageRouter.QueueForwardingOrderMode qfom) {
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

}
