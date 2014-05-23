/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package report;

import java.util.ArrayList;
import java.util.List;

import routing.MessageRouter.MessageDropMode;
import core.DTNHost;
import core.Message;
import core.MessageListener;

/**
 * Reports delivered messages' delays (one line per delivered message)
 * and cumulative delivery probability sorted by message delays.
 * Ignores the messages that were created during the warm up period.
 */
public class MessageDelayReport extends Report implements MessageListener {
	/** Description of the format */
	public static final String HEADER = "# messageDelay  cumulativeProbability";
	
	/** all message delays */
	private List<Double> delays;
	private int nrofCreated;
	
	/**
	 * Constructor.
	 */
	public MessageDelayReport() {
		init();
	}
	
	@Override
	public void init() {
		super.init();
		
		delays = new ArrayList<Double>();
		nrofCreated = 0;
		
		write(HEADER);
	}
	
	@Override
	public void registerNode(DTNHost node) {}

	@Override
	public void newMessage(Message m) {
		if (isWarmup()) {
			addWarmupID(m.getID());
		}
		else {
			nrofCreated++;
		}
	}

	@Override
	public void messageTransferred(Message m, DTNHost from, DTNHost to, 
									boolean firstDelivery, boolean finalTarget) {
		if (isWarmupID(m.getID())) {
			return;
		}
		
		if (firstDelivery && finalTarget) {
			delays.add(getSimTime() - m.getCreationTime());
		}
		
	}

	@Override
	public void done() {
		if (delays.size() == 0) {
			write("# no messages delivered in sim time "+format(getSimTime()));
			super.done();
			
			return;
		}
		
		double cumProb = 0; // cumulative probability
		java.util.Collections.sort(delays);
		for (int i=0; i < delays.size(); i++) {
			cumProb += 1.0/nrofCreated;
			write(format(delays.get(i)) + " " + format(cumProb));
		}
		
		super.done();
	}
	
	// nothing to implement for the rest
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

}
