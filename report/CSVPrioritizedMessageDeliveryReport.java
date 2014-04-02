/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package report;

import core.DTNHost;
import core.Message;
import core.MessageListener;

/**
 * Report for of amount of messages delivered vs. time. A new report line
 * is created every time when either a message is created or delivered.
 * Messages created during the warm up period are ignored.
 * For output syntax, see {@link #HEADER}.
 */
public class CSVPrioritizedMessageDeliveryReport extends Report implements MessageListener {
	/** CSV file header */
	public final static String HEADER="message_id,from,to,source,destination,priority," +
										"created_at,transferred_at,delivery_type";

	public CSVPrioritizedMessageDeliveryReport() {
		init();
	}
	
	@Override
	public void init() {
		super.init();
		write(HEADER);
	}
	
	@Override
	public void registerNode(DTNHost node) {}

	@Override
	public void newMessage(Message m) {
		if (isWarmup()) {
			addWarmupID(m.getID());
			return;
		}
	}

	@Override
	public void messageTransferred(Message m, DTNHost from, DTNHost to, boolean firstDelivery) {
		if (isWarmupID(m.getID())) {
			// Ignore messages created during warmup
			return;
		}
		if (firstDelivery && to.equals(m.getTo())) {
			reportValues(m, from, to, "first_delivery");
		}
		else if (to.equals(m.getTo())) {
			reportValues(m, from, to, "duplicate_delivery");
		}
		else {
			reportValues(m, from, to, "relay");
		}
	}

	// nothing to implement for the rest
	@Override
	public void messageDeleted(Message m, DTNHost where, boolean dropped, String cause) {}
	@Override
	public void messageTransferAborted(Message m, DTNHost from, DTNHost to) {}
	@Override
	public void messageTransferStarted(Message m, DTNHost from, DTNHost to) {}
	@Override
	public void messageTransmissionInterfered(Message m, DTNHost from, DTNHost to) {}
	@Override
	public void done() { super.done(); }


	private void reportValues(Message m, DTNHost from, DTNHost to, String deliveryType) {
		write(m.getID() + "," + from + "," + to + "," + m.getFrom() + "," + m.getTo() + "," +
				m.getPriority() + "," + format(m.getCreationTime()) + "," +
				format(getSimTime()) + "," + deliveryType);
	}
	
}