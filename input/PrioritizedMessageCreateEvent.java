package input;

import core.DTNHost;
import core.Message;
import core.World;
import core.disService.PublishSubscriber;
import core.disService.SubscriptionListManager;

public class PrioritizedMessageCreateEvent extends MessageCreateEvent {
	
	private int msgPriority;

	public PrioritizedMessageCreateEvent(int from, int to, String id, int size,
											int responseSize, double time, int msgPriority) {
		super(from, to, id, size, responseSize, time);

		this.msgPriority = msgPriority;
	}
	
	/**
	 * Creates the message this event represents. 
	 */
	@Override
	public void processEvent(World world) {
		DTNHost from = world.getNodeByAddress(this.fromAddr);
		DTNHost to = world.getNodeByAddress(this.toAddr);
		
		assert from.getRouter() instanceof PublishSubscriber : "The router installed on node " +
			from + " does not implement the PublishSubscriber interface";
		
		PublishSubscriber router = (PublishSubscriber) from.getRouter();
		int subID = router.generateRandomSubID();
		if (subID == SubscriptionListManager.INVALID_SUB_ID) {
			// Node does not generate messages
			return;
		}
		
		Message m = new Message(from, to, this.id, this.size,
								Message.PRIORITY_LEVEL.values()[msgPriority], subID);
		m.setResponseSize(this.responseSize);
		from.createNewMessage(m);
	}
	
}
