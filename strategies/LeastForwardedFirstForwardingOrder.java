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

/**
 * @author Alex
 *
 */

public class LeastForwardedFirstForwardingOrder extends MessageOrderingStrategy {
	
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
			int pDiff, timesForwardedDiff;
			
			timesForwardedDiff = m1.getForwardTimes() - m2.getForwardTimes();
			pDiff = m1.getPriority() - m2.getPriority();
			if ((timesForwardedDiff == 0) && (pDiff == 0) && (diff == 0)) {
				return 0;
			}
			if (timesForwardedDiff == 0) {
				// Same as Q_MODE_FIFO_WITH_PRIORITY
				return (pDiff < 0 ? 1 : (pDiff > 0 ? -1 : (diff < 0 ? -1 : 1)));
			}
			else {
				// Least forwarded messages go first
				return timesForwardedDiff < 0 ? -1 : 1;
			}
		}
	};
	
	static Comparator<Object> reverseOrderComparator = new Comparator<Object>() {
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
			int pDiff, timesForwardedDiff;
			
			timesForwardedDiff = m1.getForwardTimes() - m2.getForwardTimes();
			pDiff = m1.getPriority() - m2.getPriority();
			if ((timesForwardedDiff == 0) && (pDiff == 0) && (diff == 0)) {
				return 0;
			}
			if (timesForwardedDiff == 0) {
				// Lowest priority, first-received messages go first
				return (pDiff < 0 ? -1 : (pDiff > 0 ? 1 : (diff < 0 ? -1 : 1)));
			}
			else {
				// Most forwarded messages go first
				return timesForwardedDiff < 0 ? 1 : -1;
			}
		}
	};

	static LeastForwardedFirstForwardingOrder getForwardingOrderInstance() {
		if (singletonInstance == null) {
			singletonInstance = new LeastForwardedFirstForwardingOrder();
		}
		
		return singletonInstance;
	}
	
	private LeastForwardedFirstForwardingOrder() {
		super(MessageOrderingStrategy.QueueForwardingOrderMode.Prioritized_LFF_FIFO);
	}
	
	@Override
	public <T> List<T> messageProcessingOrder(List<T> inputList) {
		Collections.sort(inputList, LeastForwardedFirstForwardingOrder.comparator);
		
		return inputList;
	}

	@Override
	public <T> List<T> sortListInReverseOrder(List<T> inputList) {
		Collections.sort(inputList, LeastForwardedFirstForwardingOrder.reverseOrderComparator);
		
		return inputList;
	}
	
	@Override
	public int comparatorMethod(Message m1, Message m2) {
		return LeastForwardedFirstForwardingOrder.comparator.compare(m1, m2);
	}
	
}
