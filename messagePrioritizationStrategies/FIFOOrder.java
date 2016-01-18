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
public class FIFOOrder extends MessageCachingPrioritizationStrategy {

	static FIFOOrder singletonInstance = null;
	static Comparator<Message> comparator = new Comparator<Message>() {
		/** Compares two tuples by their messages' receiving time */
		@Override
		public int compare(Message m1, Message m2) {
			double diff = m1.getReceiveTime() - m2.getReceiveTime();
			
			if (diff == 0) {
				return 0;
			}
			return (diff < 0 ? -1 : 1);
		}
	};
	
	private FIFOOrder() {
		super(MessageCachingPrioritizationStrategy.CachingPrioritizationMode.FIFO);
	}
	
	/* (non-Javadoc)
	 * @see messagePrioritizationStrategies.MessageForwardingOrderStrategy#MessageProcessingOrder(java.util.List)
	 */
	static FIFOOrder getOrderingInstance() {
		if (singletonInstance == null) {
			singletonInstance = new FIFOOrder();
		}
		
		return singletonInstance;
	}
	
	@Override
	public void sortList(List<Message> inputList) {
		Collections.sort(inputList, FIFOOrder.comparator);
	}

	@Override
	public void sortListInReverseOrder(List<Message> inputList) {
		/* FIFO queueing strategy also deletes elements in FIFO,
		 * so this method does the same as the above one */
		sortList(inputList);
	}

	@Override
	public int comparatorMethod(Message m1, Message m2) {
		return FIFOOrder.comparator.compare(m1, m2);
	}

}
