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
 * Reports information about all created messages. Messages created during
 * the warm up period are ignored.
 * For output syntax, see {@link #HEADER}.
 */
public class CreatedMessagesReport extends Report implements MessageListener {
	public static String HEADER = "# time  ID  size  fromHost  toHost  TTL  isResponse";

	/**
	 * Constructor.
	 */
	public CreatedMessagesReport() {
		init();
	}
	
	@Override
	public void init() {
		super.init();
		write(HEADER);
	}

	public void newMessage(Message m) {
		if (isWarmup()) {
			return;
		}
		
		int ttl = m.getTtl();
		write(format(getSimTime()) + " " + m.getID() + " " + m.getSize() + " " + m.getFrom() +
						" " + m.getTo() + " " + (ttl != Integer.MAX_VALUE ? ttl : "n/a") +  
						(m.isResponse() ? " Y " : " N "));
	}
	
	// nothing to implement for the rest

	@Override
	public void registerNode(DTNHost node) {}
	@Override
	public void messageTransferStarted(Message m, DTNHost from, DTNHost to) {}
	@Override
	public void transmissionPerformed(Message m, DTNHost source) {}
	@Override
	public void messageTransferred(Message m, DTNHost f, DTNHost t,
									boolean firstDelivery, boolean finalTarget) {}
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
