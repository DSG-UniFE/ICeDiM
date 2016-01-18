/**
 * 
 */
package messagePrioritizationStrategies;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import core.Message;

/**
 * @author Alessandro Morelli
 *
 */
public class LeastForwardedFirstFIFOOrder extends MessageCachingPrioritizationStrategy {
	
	static LeastForwardedFirstFIFOOrder singletonInstance = null;
	static Comparator<Message> comparator = new Comparator<Message>() {
		/** Compares two tuples by their messages' receiving time, priority,
		 *  and the number of times they were forwarded */
		@Override
		public int compare(Message m1, Message m2) {
			double diff = m1.getReceiveTime() - m2.getReceiveTime();
			int pDiff = m1.getPriority() - m2.getPriority();
			int timesForwardedDiff = m1.getForwardTimes() - m2.getForwardTimes();
			
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
	
	static Comparator<Message> reverseOrderComparator = new Comparator<Message>() {
		/** Compares two tuples by their messages' receiving time, priority,
		 *  and the number of times they were forwarded */
		@Override
		public int compare(Message m1, Message m2) {
			double diff = m1.getReceiveTime() - m2.getReceiveTime();
			int pDiff = m1.getPriority() - m2.getPriority();
			int timesForwardedDiff = m1.getForwardTimes() - m2.getForwardTimes();
			
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

	static LeastForwardedFirstFIFOOrder getOrderingInstance() {
		if (singletonInstance == null) {
			singletonInstance = new LeastForwardedFirstFIFOOrder();
		}
		
		return singletonInstance;
	}
	
	private LeastForwardedFirstFIFOOrder() {
		super(MessageCachingPrioritizationStrategy.CachingPrioritizationMode.Prioritized_LFF_FIFO);
	}
	
	@Override
	public void sortList(List<Message> inputList) {
		Collections.sort(inputList, LeastForwardedFirstFIFOOrder.comparator);
	}

	@Override
	public void sortListInReverseOrder(List<Message> inputList) {
		Collections.sort(inputList, LeastForwardedFirstFIFOOrder.reverseOrderComparator);
	}
	
	@Override
	public int comparatorMethod(Message m1, Message m2) {
		return LeastForwardedFirstFIFOOrder.comparator.compare(m1, m2);
	}
	
}
