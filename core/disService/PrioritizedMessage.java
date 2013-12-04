package core.disService;

import core.DTNHost;
import core.Message;


public class PrioritizedMessage extends Message {
	
	/** Valid message priority level limits */
	public static final int MIN_PRIORITY = 1;
	public static final int MAX_PRIORITY = 5;
	
	private int priority;
	private final int subscriptionID;

	public PrioritizedMessage(DTNHost from, DTNHost to, String id, int size,
								int priority, int subscriptionID) {
		super(from, to, id, size);
		
		this.setPriority(priority);
		this.subscriptionID = subscriptionID;
	}

	/**
	 * Returns the message priority
	 * @return the message priority
	 */
	public int getPriority() {
		return priority;
	}

	/**
	 * Returns the message subscription
	 * @return the message subscription
	 */
	public int getSubscriptionID() {
		return subscriptionID;
	}

	/**
	 * Sets the message priority level
	 * @param priority the message priority level to set
	 */
	public void setPriority(int priority) {
		if (priority < MIN_PRIORITY) {
			this.priority = MIN_PRIORITY;
		}
		else if (priority > MAX_PRIORITY) {
			this.priority = MAX_PRIORITY;
		}
		else {		
			this.priority = priority;
		}
	}
	

	/**
	 * Deep copies message data from other message. If new fields are
	 * introduced to this class, most likely they should be copied here too
	 * (unless done in constructor).
	 * @param m The PrioritizedMessage from where the data is copied
	 */
	protected void copyFrom(PrioritizedMessage m) {
		super.copyFrom(m);
		
		this.setPriority(m.getPriority());
	}

	
	/**
	 * Returns a replicate of this message (identical except for the unique id)
	 * @return A replicate of the message
	 */
	@Override
	public PrioritizedMessage replicate() {
		PrioritizedMessage  m = new PrioritizedMessage (this.getFrom(), this.getTo(), this.getId(),
														this.getSize(), this.getPriority(),
														this.getSubscriptionID());
		m.copyFrom(this);
		return m;
	}

}
