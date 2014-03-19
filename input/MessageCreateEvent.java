/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package input;

import core.DTNHost;
import core.Message;
import core.World;
import core.disService.PublishSubscriber;
import core.disService.SubscriptionListManager;

/**
 * External event for creating a message.
 */
public class MessageCreateEvent extends MessageEvent {
	protected int size;
	protected int responseSize;
	protected int priority;
	
	/**
	 * Creates a message creation event with a optional response request
	 * @param from The creator of the message
	 * @param to Where the message is destined to
	 * @param id ID of the message
	 * @param size Size of the message
	 * @param responseSize Size of the requested response message or 0 if
	 * no response is requested
	 * @param time Time, when the message is created
	 */
	public MessageCreateEvent(int from, int to, String id, int priority, int size,
								int responseSize, double time) {
		super(from, to, id, time);
		this.size = size;
		this.responseSize = responseSize;
		this.priority = priority;
	}

	
	/**
	 * Creates the message this event represents. 
	 */
	@Override
	public void processEvent(World world) {
		DTNHost to = world.getNodeByAddress(this.toAddr);
		DTNHost from = world.getNodeByAddress(this.fromAddr);
		
		int subID = SubscriptionListManager.DEFAULT_SUB_ID;
		if (from.getRouter() instanceof PublishSubscriber) {	
			PublishSubscriber router = (PublishSubscriber) from.getRouter();
			subID = router.generateRandomSubID();
			if (subID == SubscriptionListManager.INVALID_SUB_ID) {
				// Node does not generate messages
				return;
			}
		}
	
		// No priority - Use the PrioritizedMessageEventGenerator to generate messages with priorities
		Message m = new Message(from, to, this.id, this.size, priority, subID);
		m.setResponseSize(this.responseSize);
		from.createNewMessage(m);
	}
	
	@Override
	public String toString() {
		return super.toString() + " [" + fromAddr + "->" + toAddr + "] " +
		"size:" + size + " CREATE";
	}
}
