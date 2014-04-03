/**
 * 
 */
package strategies;

import java.util.Collections;
import java.util.List;
import java.util.Random;

import core.Message;
import core.SimClock;

/**
 * @author Alex
 *
 */
public class RandomForwardingOrder extends MessageOrderingStrategy {

	static RandomForwardingOrder singletonInstance = null;
	static Random randomGenerator = null;
	
	private RandomForwardingOrder() {
		super(MessageOrderingStrategy.QueueForwardingOrderMode.Random);
	}
	
	/* (non-Javadoc)
	 * @see strategies.MessageForwardingOrderStrategy#MessageProcessingOrder(java.util.List)
	 */
	static RandomForwardingOrder getForwardingOrderInstance() {
		if (singletonInstance == null) {
			singletonInstance = new RandomForwardingOrder();
			randomGenerator = new Random(System.currentTimeMillis());
		}
		
		return singletonInstance;
	}
	
	@Override
	public void sortList(List<Message> inputList) {
		Collections.shuffle(inputList, new Random(SimClock.getIntTime()));
	}

	@Override
	public void sortListInReverseOrder(List<Message> inputList) {
		/* Random queueing strategy also deletes elements randomly,
		 * so this method does the same as the above one */
		sortList(inputList);
	}
	
	@Override
	public int comparatorMethod(Message m1, Message m2) {
		return RandomForwardingOrder.randomGenerator.nextInt(3) - 1;
	}

}
