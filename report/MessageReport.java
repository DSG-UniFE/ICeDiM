/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package report;

import routing.MessageRouter.MessageDropMode;
import core.DTNHost;
import core.Message;
import core.MessageListener;

/**
 * Reports delivered messages
 * report:
 * 			message_id creation_time deliver_time (duplicate)
 */
public class MessageReport extends Report implements MessageListener {
	public static final String HEADER = "# messages: ID, start time, end time";
	
	/**
	 * Constructor.
	 */
	public MessageReport() {
		init();
	}
	
	@Override
	public void init() {
		super.init();
		write(HEADER);
	}

	@Override
	public void messageTransferred(Message m, DTNHost from, DTNHost to,
									boolean firstDelivery, boolean finalTarget) {
		if (firstDelivery && finalTarget) {
			write(m.getID() + " " + format(m.getCreationTime()) + " " + format(getSimTime()));
		}
		else if (finalTarget) {
			write(m.getID() + " " + format(m.getCreationTime()) +
					" " + format(getSimTime()) + " duplicate");
		}
	}


	// nothing to implement for the rest
	@Override
	public void registerNode(DTNHost node) {}
	@Override
	public void newMessage(Message m) {}
	@Override
	public void transmissionPerformed(Message m, DTNHost source) {}
	@Override
	public void messageTransferStarted(Message m, DTNHost from, DTNHost to) {}
	@Override
	public void messageTransferAborted(Message m, DTNHost from, DTNHost to, String cause) {}
	@Override
	public void messageTransmissionInterfered(Message m, DTNHost from, DTNHost to) {}
	@Override
	public void messageDeleted(Message m, DTNHost where, MessageDropMode dropMode, String cause) {}

	@Override
	public void done() {
		super.done();
	}

}