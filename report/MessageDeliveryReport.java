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
public class MessageDeliveryReport extends Report implements MessageListener {

	/** Description of the format */
	public static String HEADER="# time  created  delivered  delivered/created";
	
	private int created;
	private int delivered;

	/**
	 * Constructor.
	 */
	public MessageDeliveryReport() {
		init();
	}
	
	@Override
	public void init() {
		super.init();
		created = 0;
		delivered = 0;
		write(HEADER);
	}
	
	@Override
	public void registerNode(DTNHost node) {}

	@Override
	public void messageTransferred(Message m, DTNHost from, DTNHost to,
									boolean firstDelivery, boolean finalTarget) {
		if (isWarmupID(m.getID())) {
			return;
		}
		
		if (firstDelivery && finalTarget) {
			delivered++;
			reportValues();
		}
	}

	@Override
	public void newMessage(Message m) {
		if (isWarmup()) {
			addWarmupID(m.getID());
			
			return;
		}
		created++;
		reportValues();
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
	public void done() {
		super.done();
	}
	
	/**
	 * Writes the current values to report file
	 */
	private void reportValues() {
		double prob = (1.0 * delivered) / created;
		
		write(format(getSimTime()) + " " + created + " " + delivered + " " + format(prob));
	}
}