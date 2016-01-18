package messageForwardingOrderManager;

import java.util.ArrayList;
import java.util.List;

import messagePrioritizationStrategies.MessageCachingPrioritizationStrategy;
import core.Message;
import core.MessageCacheManager;
import core.Settings;

/**
 * {@link UnchangedForwardingOrder} does not modify the message lists orders,
 * thereby returning the messages in the same order in which they
 * are passed to the orderMessageListForForwarding() method.
 * 
 * @author Alessandro Morelli
 *
 */
public class UnchangedForwardingOrder extends MessageForwardingOrderManager {

	public UnchangedForwardingOrder(Settings s, MessageCacheManager cacheManager,
						MessageCachingPrioritizationStrategy cachingPrioritizationStrategy) {
		super(MessageForwardingOrderStrategy.ORDER_UNCHANGED, cacheManager,
				cachingPrioritizationStrategy);
	}

	public UnchangedForwardingOrder(UnchangedForwardingOrder fifoManager) {
		super(fifoManager);
	}

	@Override
	public List<Message> orderMessageListForForwarding(List<Message> inputList) {
		if (inputList == null) {
			return new ArrayList<Message>(0);
		}
		
		return new ArrayList<Message>(inputList);
	}

	@Override
	public MessageForwardingOrderManager replicate() {
		return new UnchangedForwardingOrder(this);
	}

}