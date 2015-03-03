/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package report;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import routing.MessageRouter.MessageDropMode;
import core.DTNHost;
import core.Message;
import core.MessageListener;

/**
 * Report for generating different kind of total statistics about message
 * relaying performance. Messages that were created during the warm up period
 * are ignored.
 * <P><strong>Note:</strong> if some statistics could not be created (e.g.
 * overhead ratio if no messages were delivered) "NaN" is reported for
 * double values and zero for integer median(s).
 */
public class MessageStatsReport extends Report implements MessageListener {
	private Map<String, Double> creationTimes;
	private List<Double> latencies;
	private List<Integer> hopCounts;
	private List<Double> msgBufferTime;
	private List<Double> rtt; // round trip times

	private int nrofCreated;
	private int nrofStarted;
	private int nrofAborted;
	private int nrofInterfered;
	private int nrofRelayed;
	private int nrofDuplicated;
	private int nrofDelivered;
	private int nrofRemoved;
	private int nrofDropped;
	private int nrofDiscarded;
	private int nrofExpired;
	private int nrofResponseReqCreated;
	private int nrofResponseDelivered;

	private int nrofTransmissions;
	
	/**
	 * Constructor.
	 */
	public MessageStatsReport() {
		init();
	}

	@Override
	protected void init() {
		super.init();
		
		this.creationTimes = new HashMap<String, Double>();
		this.latencies = new ArrayList<Double>();
		this.msgBufferTime = new ArrayList<Double>();
		this.hopCounts = new ArrayList<Integer>();
		this.rtt = new ArrayList<Double>();

		this.nrofCreated = 0;
		this.nrofStarted = 0;
		this.nrofAborted = 0;
		this.nrofInterfered = 0;
		this.nrofRelayed = 0;
		this.nrofDuplicated = 0;
		this.nrofDelivered = 0;
		this.nrofRemoved = 0;
		this.nrofDropped = 0;
		this.nrofDiscarded = 0;
		this.nrofExpired = 0;
		this.nrofResponseReqCreated = 0;
		this.nrofResponseDelivered = 0;
		
		this.nrofTransmissions = 0;
	}
	
	@Override
	public void registerNode(DTNHost node) {}

	@Override
	public void newMessage(Message m) {
		if (isWarmup()) {
			addWarmupID(m.getID());
			return;
		}
		
		creationTimes.put(m.getID(), getSimTime());
		nrofCreated++;
		if (m.getResponseSize() > 0) {
			nrofResponseReqCreated++;
		}
	}

	@Override
	public void transmissionPerformed(Message m, DTNHost source) {
		if (isWarmupID(m.getID())) {
			return;
		}
		
		nrofTransmissions++;
	}
	
	@Override
	public void messageTransferred(Message m, DTNHost from, DTNHost to,
									boolean firstDelivery, boolean finalTarget) {
		if (isWarmupID(m.getID())) {
			return;
		}
	
		if (firstDelivery) {
			nrofRelayed++;
			if (finalTarget) {
				nrofDelivered++;
				latencies.add(getSimTime() - creationTimes.get(m.getID()));
				hopCounts.add(m.getHops().size() - 1);
				
				if (m.isResponse()) {
					rtt.add(getSimTime() -	m.getRequest().getCreationTime());
					nrofResponseDelivered++;
				}
			}
		}
		else {
			nrofDuplicated++;
		}
	}

	@Override
	public void messageTransferStarted(Message m, DTNHost from, DTNHost to) {
		if (isWarmupID(m.getID())) {
			return;
		}
	
		nrofStarted++;
	}

	@Override
	public void messageTransferAborted(Message m, DTNHost from, DTNHost to, String cause) {
		if (isWarmupID(m.getID())) {
			return;
		}
		
		nrofAborted++;
	}

	@Override
	public void messageTransmissionInterfered(Message m, DTNHost from, DTNHost to) {
		if (isWarmupID(m.getID())) {
			return;
		}
		
		nrofInterfered++;
	}

	@Override
	public void messageDeleted(Message m, DTNHost where, MessageDropMode dropMode, String cause) {
		if (isWarmupID(m.getID())) {
			return;
		}
		
		switch (dropMode) {
		case REMOVED:
			nrofRemoved++;
			break;
		case DROPPED:
			nrofDropped++;
			break;
		case DISCARDED:
			nrofDiscarded++;
			break;
		case TTL_EXPIRATION:
			nrofExpired++;
			break;
		}
		
		msgBufferTime.add(getSimTime() - m.getReceiveTime());
	}

	@Override
	public void done() {
		double deliveryProb = 0; // delivery probability
		double responseProb = 0; // request-response success probability
		double overHead = Double.NaN;	// overhead ratio
		
		write("Message stats for scenario " + getScenarioName() + 
				"\nsim_time: " + format(getSimTime()));
		
		if (nrofCreated > 0) {
			deliveryProb = (1.0 * nrofDelivered) / nrofCreated;
		}
		if (nrofDelivered > 0) {
			overHead = (1.0 * (nrofDuplicated + nrofRelayed - nrofDelivered)) / nrofDelivered;
		}
		if (nrofResponseReqCreated > 0) {
			responseProb = (1.0* nrofResponseDelivered) / nrofResponseReqCreated;
		}
		
		String statsText = "created: " + nrofCreated + 
							"\ntransmissions: " + nrofTransmissions + 
							"\nstarted: " + nrofStarted + 
							"\nrelayed: " + nrofRelayed + 
							"\nduplicated: " + nrofDuplicated +
							"\naborted: " + nrofAborted +
							"\ninterfered: " + nrofInterfered +
							"\nremoved: " + nrofRemoved +
							"\ndropped: " + nrofDropped +
							"\ndiscarded: " + nrofDiscarded +
							"\nexpired: " + nrofInterfered +
							"\ndelivered: " + nrofExpired +
							"\ndelivery_prob: " + format(deliveryProb) +
							"\nresponse_prob: " + format(responseProb) + 
							"\noverhead_ratio: " + format(overHead) + 
							"\nlatency_avg: " + getAverage(latencies) +
							"\nlatency_med: " + getMedian(latencies) + 
							"\nhopcount_avg: " + getIntAverage(hopCounts) +
							"\nhopcount_med: " + getIntMedian(hopCounts) + 
							"\nbuffertime_avg: " + getAverage(msgBufferTime) +
							"\nbuffertime_med: " + getMedian(msgBufferTime) +
							"\nrtt_avg: " + getAverage(rtt) +
							"\nrtt_med: " + getMedian(rtt);
		write(statsText);
		
		super.done();
	}
	
}