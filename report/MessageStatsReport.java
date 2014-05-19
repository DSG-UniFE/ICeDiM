/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package report;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
	
	private int nrofDropped;
	private int nrofRemoved;
	private int nrofStarted;
	private int nrofAborted;
	private int nrofInterfered;
	private int nrofRelayed;
	private int nrofDuplicated;
	private int nrofCreated;
	private int nrofResponseReqCreated;
	private int nrofResponseDelivered;
	private int nrofDelivered;
	
	/**
	 * Constructor.
	 */
	public MessageStatsReport() {
		init();
	}

	@Override
	protected void init() {
		super.init();
		
		creationTimes = new HashMap<String, Double>();
		latencies = new ArrayList<Double>();
		msgBufferTime = new ArrayList<Double>();
		hopCounts = new ArrayList<Integer>();
		rtt = new ArrayList<Double>();
		
		nrofDropped = 0;
		nrofRemoved = 0;
		nrofStarted = 0;
		nrofAborted = 0;
		nrofInterfered = 0;
		nrofRelayed = 0;
		nrofDuplicated = 0;
		nrofCreated = 0;
		nrofResponseReqCreated = 0;
		nrofResponseDelivered = 0;
		nrofDelivered = 0;
	}
	
	@Override
	public void registerNode(DTNHost node) {}
	
	@Override
	public void messageDeleted(Message m, DTNHost where, boolean dropped, String cause) {
		if (isWarmupID(m.getID())) {
			return;
		}
		
		if (dropped) {
			nrofDropped++;
		}
		else {
			nrofRemoved++;
		}
		
		msgBufferTime.add(getSimTime() - m.getReceiveTime());
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
	public void messageTransferStarted(Message m, DTNHost from, DTNHost to) {
		if (isWarmupID(m.getID())) {
			return;
		}

		nrofStarted++;
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
							"\nstarted: " + nrofStarted + 
							"\nrelayed: " + nrofRelayed + 
							"\nduplicated: " + nrofRelayed +
							"\naborted: " + nrofAborted +
							"\ninterfered: " + nrofInterfered +
							"\ndropped: " + nrofDropped +
							"\nremoved: " + nrofRemoved +
							"\ndelivered: " + nrofDelivered +
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