package messageForwardingManager;

import java.util.ArrayList;
import java.util.List;

import core.Message;
import core.MessageQueueManager;
import strategies.MessageOrderingStrategy;

/**
 * {@link FIFOManager} offers messages in the order
 * specified by the MessageOrdering strategy
 * found in the MessageQueue manager passed
 * as parameter to the constructor of the class.
 * 
 * @author Alex
 *
 */
public class FIFOManager extends MessageForwardingManager {

	private int indexInList;
	List<Message> messageOrderedList;

	public FIFOManager(MessageQueueManager queueManager, MessageOrderingStrategy orderingStrategy) {
		super(MessageForwardingManagerImplementation.FIFO_MANAGER, queueManager, orderingStrategy);
		
		this.indexInList = 0;
		this.messageOrderedList = null;
	}


	@Override
	public Message getNextMessage() {
		checkConsistency();
		// If the value is still null, return null Message
		if ((messageOrderedList == null) || (messageOrderedList.size() == 0)) {
			return null;
		}
		
		return messageOrderedList.get(indexInList);
	}

	@Override
	public Message getNextMessageAndAdvanceQueue() {
		Message messageToReturn = getNextMessage();
		++indexInList;
		
		return messageToReturn;
	}

	@Override
	public void advanceQueue() {
		++indexInList;

		if (indexInList >= messageOrderedList.size()) {
			// reset ordered list
			resetMessageOrder();
		}
	}

	@Override
	public void resetMessageOrder() {
		indexInList = 0;
		messageOrderedList = messageOrderingStrategy.sortList(messageQueueManager.getMessageList());
	}

	@Override
	public List<Message> getOrderedMessageQueue() {
		checkConsistency();
		
		ArrayList<Message> result = new ArrayList<Message>(messageQueueManager.getMessageCollection().size());
		for (int i = indexInList; i < messageOrderedList.size(); i++) {
			result.add(messageOrderedList.get(i));
		}
		for (int i = 0; i < indexInList; i++) {
			result.add(messageOrderedList.get(i));
		}
		
		return result;
	}
	
	
	private void checkConsistency() {
		if ((messageOrderedList == null) || (messageOrderedList.size() == 0) ||
			indexInList >= messageOrderedList.size()) {
			// reset ordered list
			resetMessageOrder();
		}
	}

}