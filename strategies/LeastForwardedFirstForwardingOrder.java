/**
 * 
 */
package strategies;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import core.Connection;
import core.Message;
import core.SimError;
import core.Tuple;
import core.disService.PrioritizedMessage;

/**
 * @author Alex
 *
 */
public class LeastForwardedFirstForwardingOrder extends MessageForwardingOrderStrategy {
	
	static LeastForwardedFirstForwardingOrder singletonInstance = null;
	static Comparator<Object> comparator = new Comparator<Object>() {
		/** Compares two tuples by their messages' receiving time, priority,
		 *  and the number of times they were forwarded */
		@SuppressWarnings("unchecked")
		@Override
		public int compare(Object o1, Object o2) {
			double diff;
			Message m1, m2;
			
			if (o1 instanceof Tuple) {
				m1 = ((Tuple<Message, Connection>)o1).getKey();
				m2 = ((Tuple<Message, Connection>)o2).getKey();
			}
			else if (o1 instanceof Message) {
				m1 = (Message) o1;
				m2 = (Message) o2;
			}
			else {
				throw new SimError("Invalid type of objects in the list");
			}

			diff = m1.getReceiveTime() - m2.getReceiveTime();
			if ((m1 instanceof PrioritizedMessage) && (m2 instanceof PrioritizedMessage)) {
				int pDiff, timesForwardedDiff;
				PrioritizedMessage pm1 = (PrioritizedMessage) m1;
				PrioritizedMessage pm2 = (PrioritizedMessage) m2; 
				
				timesForwardedDiff = pm1.getForwardTimes() - pm2.getForwardTimes();
				pDiff = pm1.getPriority() - pm2.getPriority();
				if ((timesForwardedDiff == 0) && (pDiff == 0) && (diff == 0)) {
					return 0;
				}
				if (timesForwardedDiff == 0) {
					// Same as Q_MODE_FIFO_WITH_PRIORITY
					return (pDiff < 0 ? 1 : (pDiff > 0 ? -1 : (diff < 0 ? -1 : 1)));
				}
				else {
					// Less forwarded messages go first 
					return timesForwardedDiff < 0 ? -1 : 1;
				}
			}
			else {
				throw new SimError("Message objects in the list are not instances of PrioritizedMessage");
			}
		}
	};
	
	private LeastForwardedFirstForwardingOrder() {}
	/* (non-Javadoc)
	 * @see strategies.MessageForwardingOrderStrategy#MessageProcessingOrder(java.util.List)
	 */
	static LeastForwardedFirstForwardingOrder getForwardingOrderInstance() {
		if (singletonInstance == null) {
			singletonInstance = new LeastForwardedFirstForwardingOrder();
		}
		
		return singletonInstance;
	}
	
	@Override
	public <T> List<T> MessageProcessingOrder(List<T> inputList) {
		Collections.sort(inputList, LeastForwardedFirstForwardingOrder.comparator);
		
		return inputList;
	}
	@Override
	public int ComparatorMethod(Message m1, Message m2) {
		return LeastForwardedFirstForwardingOrder.comparator.compare(m1, m2);
	}
}
