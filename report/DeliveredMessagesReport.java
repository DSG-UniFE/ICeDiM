/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package report;

import java.util.List;

import core.DTNHost;
import core.Message;
import core.MessageListener;

/**
 * Report information about all delivered messages. Messages created during
 * the warm up period are ignored.
 * For output syntax, see {@link #HEADER}.
 */
public class DeliveredMessagesReport extends Report implements MessageListener {
	public static String HEADER = "# time  ID  size  hopcount  deliveryTime  " +
		"fromHost  toHost  remainingTtl  isResponse  path";

	/**
	 * Constructor.
	 */
	public DeliveredMessagesReport() {
		init();
	}
	
	@Override
	public void init() {
		super.init();
		write(HEADER);
	}

	/** 
	 * Returns the given messages hop path as a string
	 * @param m The message
	 * @return hop path as a string
	 */
	private String getPathString(Message m) {
		List<DTNHost> hops = m.getHops();
		String str = m.getFrom().toString();
		
		for (int i=1; i<hops.size(); i++) {
			str += "->" + hops.get(i); 
		}
		
		return str;
	}
	
	public void messageTransferred(Message m, DTNHost from, DTNHost to, 
									boolean firstDelivery, boolean finalTarget) {
		if (isWarmupID(m.getID())) {
			return;
		}
		
		if (firstDelivery && finalTarget) {
			write(format(getSimTime()) + " " + m.getID() + " " + m.getSize() + " " +
							m.getHopCount() + " " + format(getSimTime() - m.getCreationTime()) +
							" " + m.getFrom() + " " + m.getTo() + " " +
							(m.getTtl() != Integer.MAX_VALUE ? m.getTtl() : "n/a") +
							(m.isResponse() ? " Y " : " N ") + getPathString(m));
		}
	}

	public void newMessage(Message m) {
		if (isWarmup()) {
			addWarmupID(m.getID());
		}
	}
	
	// nothing to implement for the rest
	@Override
	public void registerNode(DTNHost node) {}
	@Override
	public void messageDeleted(Message m, DTNHost where, boolean dropped, String cause) {}
	@Override
	public void messageTransferAborted(Message m, DTNHost from, DTNHost to, String cause) {}
	@Override
	public void messageTransferStarted(Message m, DTNHost from, DTNHost to) {}
	@Override
	public void messageTransmissionInterfered(Message m, DTNHost from, DTNHost to) {}

	@Override
	public void done() {
		super.done();
	}
}
