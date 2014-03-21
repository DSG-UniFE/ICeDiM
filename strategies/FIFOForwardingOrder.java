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
public class FIFOForwardingOrder extends MessageForwardingOrderStrategy {

	static FIFOForwardingOrder singletonInstance = null;
	static Comparator<Object> comparator = new Comparator<Object>() {
		/** Compares two tuples by their messages' receiving time */
		@Override
		@SuppressWarnings("unchecked")
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
			if (diff == 0) {
				return 0;
			}
			return (diff < 0 ? -1 : 1);
		}
	};
	
	private FIFOForwardingOrder() {
		super(MessageForwardingOrderStrategy.QueueForwardingOrderMode.FIFO);
	}
	
	/* (non-Javadoc)
	 * @see strategies.MessageForwardingOrderStrategy#MessageProcessingOrder(java.util.List)
	 */
	static FIFOForwardingOrder getForwardingOrderInstance() {
		if (singletonInstance == null) {
			singletonInstance = new FIFOForwardingOrder();
		}
		
		return singletonInstance;
	}
	
	@Override
	public <T> List<T> messageProcessingOrder(List<T> inputList) {
		Collections.sort(inputList, FIFOForwardingOrder.comparator);
		
		return inputList;
	}

	@Override
	public <T> List<T> reverseProcessingOrder(List<T> inputList) {
		/* FIFO queueing strategy also deletes elements in FIFO,
		 * so this method does the same as the above one */
		return messageProcessingOrder(inputList);
	}

	@Override
	public int comparatorMethod(Message m1, Message m2) {
		return FIFOForwardingOrder.comparator.compare(m1, m2);
	}

}
