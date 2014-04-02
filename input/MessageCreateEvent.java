/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package input;

import core.DTNHost;
import core.Message;
import core.World;
import core.disService.PublisherSubscriber;
import core.disService.SubscriptionListManager;

/**
 * External event for creating a message.
 */
public class MessageCreateEvent extends MessageEvent {
	
	private static final long serialVersionUID = 1L;
	
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
		DTNHost to = world.getNodeByAddress(toAddr);
		DTNHost from = world.getNodeByAddress(fromAddr);
		
		Integer subID = SubscriptionListManager.DEFAULT_SUB_ID;
		if (from.getRouter() instanceof PublisherSubscriber) {	
			PublisherSubscriber router = (PublisherSubscriber) from.getRouter();
			subID = router.generateRandomSubID();
			if (subID == SubscriptionListManager.INVALID_SUB_ID) {
				// Node does not generate messages
				return;
			}
		}
	
		// No priority - Use the PrioritizedMessageEventGenerator to generate messages with priorities
		Message m = new Message(from, to, id, size, priority);
		m.setResponseSize(responseSize);
		m.addProperty(PublisherSubscriber.SUBSCRIPTION_MESSAGE_PROPERTY_KEY, subID);
		from.createNewMessage(m);
	}
	
	@Override
	public String toString() {
		return super.toString() + " [" + fromAddr + "->" + toAddr + "] " +
		"size:" + size + " CREATE";
	}
}
