/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package report;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import routing.MessageRouter.MessageDropMode;
import core.DTNHost;
import core.Message;
import core.MessageListener;

/**
 * Report for generating different kind of total statistics about message
 * relaying performance, differentiated based on the message priority.
 * Messages that were created during the warm up period are ignored.
 * <P><strong>Note:</strong> if some statistics could not be created (e.g.
 * overhead ratio if no messages were delivered) "NaN" is reported for
 * double values and zero for integer median(s).
 */
public class PrioritizedMessageStatsReport extends Report implements MessageListener {
	private Map<String, Double> creationTimes;
	private ArrayList<Double> latencies[];
	private ArrayList<Integer> hopCounts[];
	private ArrayList<Double> msgCacheTime[];
	private ArrayList<Double> rtt[]; // round trip times

	private int nrofCreated[];
	private int nrofTransmissions[];
	private int nrofStarted[];
	private int nrofAborted[];
	private int nrofInterfered[];
	private int nrofRelayed[];
	private int nrofDuplicates[];
	private int nrofRemoved[];
	private int nrofDropped[];
	private int nrofDiscarded[];
	private int nrofExpired[];
	private int nrofDelivered[];
	private int nrofTotalDueDeliveries[];
	private int nrofResponseReqCreated[];
	private int nrofResponseDelivered[];
	
	
	/**
	 * Constructor.
	 */
	public PrioritizedMessageStatsReport() {
		init();
	}

	@Override
	@SuppressWarnings("unchecked")
	protected void init() {
		super.init();
		int priorityArraySize = Message.MAX_PRIORITY_LEVEL + 1;
		
		creationTimes = new HashMap<String, Double>();
		latencies = new ArrayList[priorityArraySize];
		for (int i = 0; i < priorityArraySize; i++) {
			latencies[i] = new ArrayList<Double>();
		}
		hopCounts = new ArrayList[priorityArraySize];
		for (int i = 0; i < priorityArraySize; i++) {
			hopCounts[i] = new ArrayList<Integer>();
		}		
		msgCacheTime = new ArrayList[priorityArraySize];
		for (int i = 0; i < priorityArraySize; i++) {
			msgCacheTime[i] = new ArrayList<Double>();
		}
		rtt = new ArrayList[priorityArraySize];
		for (int i = 0; i < priorityArraySize; i++) {
			rtt[i] = new ArrayList<Double>();
		}

		nrofCreated = new int[priorityArraySize];
		for (int i = 0; i < priorityArraySize; i++) {
			nrofCreated[i] = 0;
		}
		nrofStarted = new int[priorityArraySize];
		for (int i = 0; i < priorityArraySize; i++) {
			nrofStarted[i] = 0;
		}
		nrofRelayed = new int[priorityArraySize];
		for (int i = 0; i < priorityArraySize; i++) {
			nrofRelayed[i] = 0;
		}
		nrofDuplicates = new int[priorityArraySize];
		for (int i = 0; i < priorityArraySize; i++) {
			nrofDuplicates[i] = 0;
		}
		nrofDelivered = new int[priorityArraySize];
		for (int i = 0; i < priorityArraySize; i++) {
			nrofDelivered[i] = 0;
		}
		nrofAborted = new int[priorityArraySize];
		for (int i = 0; i < priorityArraySize; i++) {
			nrofAborted[i] = 0;
		}
		nrofInterfered = new int[priorityArraySize];
		for (int i = 0; i < priorityArraySize; i++) {
			nrofInterfered[i] = 0;
		}
		nrofRemoved = new int[priorityArraySize];
		for (int i = 0; i < priorityArraySize; i++) {
			nrofRemoved[i] = 0;
		}
		nrofDropped = new int[priorityArraySize];
		for (int i = 0; i < priorityArraySize; i++) {
			nrofDropped[i] = 0;
		}
		nrofDiscarded = new int[priorityArraySize];
		for (int i = 0; i < priorityArraySize; i++) {
			nrofDiscarded[i] = 0;
		}
		nrofExpired = new int[priorityArraySize];
		for (int i = 0; i < priorityArraySize; i++) {
			nrofExpired[i] = 0;
		}
		
		nrofResponseReqCreated = new int[priorityArraySize];
		for (int i = 0; i < priorityArraySize; i++) {
			nrofResponseReqCreated[i] = 0;
		}
		nrofResponseDelivered = new int[priorityArraySize];
		for (int i = 0; i < priorityArraySize; i++) {
			nrofResponseDelivered[i] = 0;
		}
		
		nrofTotalDueDeliveries = new int[priorityArraySize];
		for (int i = 0; i < priorityArraySize; i++) {
			nrofTotalDueDeliveries[i] = 0;
		}
	}
	
	@Override
	public void registerNode(DTNHost node) {}

	@Override
	public void newMessage(Message m) {
		if (isWarmup()) {
			addWarmupID(m.getID());
			return;
		}
	
		nrofCreated[m.getPriority()]++;
		creationTimes.put(m.getID(), getSimTime());
		if (m.getTo() != null) {
			nrofTotalDueDeliveries[m.getPriority()]++;
		}
		if (m.getResponseSize() > 0) {
			nrofResponseReqCreated[m.getPriority()]++;
		}
	}

	@Override
	public void transmissionPerformed(Message m, DTNHost source) {
		if (isWarmupID(m.getID())) {
			// Ignore messages created during warmup
			return;
		}
		
		nrofTransmissions[m.getPriority()]++;
	}

	@Override
	public void messageTransferred(Message m, DTNHost from, DTNHost to,
									boolean firstDelivery, boolean finalTarget) {
		if (isWarmupID(m.getID())) {
			return;
		}
		
		if (firstDelivery) {
			nrofRelayed[m.getPriority()]++;
			
			if (finalTarget) {
				latencies[m.getPriority()].add(getSimTime() - creationTimes.get(m.getID()));
				nrofDelivered[m.getPriority()]++;
				hopCounts[m.getPriority()].add(m.getHops().size() - 1);
				
				if (m.isResponse()) {
					rtt[m.getPriority()].add(getSimTime() -	m.getRequest().getCreationTime());
					nrofResponseDelivered[m.getPriority()]++;
				}
			}
		}
		else {
			// Duplicate message
			nrofDuplicates[m.getPriority()]++;
		}
	}

	@Override
	public void messageTransferStarted(Message m, DTNHost from, DTNHost to) {
		if (isWarmupID(m.getID())) {
			return;
		}

		nrofStarted[m.getPriority()]++;
	}

	@Override
	public void messageTransferAborted(Message m, DTNHost from, DTNHost to, String cause) {
		if (isWarmupID(m.getID())) {
			return;
		}
		
		nrofAborted[m.getPriority()]++;
	}

	@Override
	public void messageTransmissionInterfered(Message m, DTNHost from, DTNHost to) {
		nrofInterfered[m.getPriority()]++;
	}

	@Override
	public void messageDeleted(Message m, DTNHost where, MessageDropMode dropMode, String cause) {
		if (isWarmupID(m.getID())) {
			return;
		}
	
		switch (dropMode) {
		case REMOVED:
			nrofRemoved[m.getPriority()]++;
			break;
		case DROPPED:
			nrofDropped[m.getPriority()]++;
			break;
		case DISCARDED:
			nrofDiscarded[m.getPriority()]++;
			break;
		case TTL_EXPIRATION:
			nrofExpired[m.getPriority()]++;
			break;
		}
	
		msgCacheTime[m.getPriority()].add(getSimTime() - m.getReceiveTime());
	}

	@Override
	public void done() {
		int arraySize = Message.MAX_PRIORITY_LEVEL + 1;
		double deliveryProb[] = new double[arraySize];	// delivery probability
		double responseProb[] = new double[arraySize];	// request-response success probability
		double overHead[] = new double[arraySize];		// overhead ratio
		
		for (int i = 0; i < arraySize; i++) {
			if (nrofCreated[i] > 0) {
				deliveryProb[i] = (1.0 * nrofDelivered[i]) / nrofTotalDueDeliveries[i];
			}
			else {
				deliveryProb[i] = 0;
			}
		}
		for (int i = 0; i < arraySize; i++) {
			if (nrofDelivered[i] > 0) {
				overHead[i] = (1.0 * (nrofDuplicates[i] + nrofRelayed[i] - nrofDelivered[i]))
								/ nrofDelivered[i];
			}
			else {
				overHead[i] = Double.NaN;
			}
		}
		for (int i = 0; i < arraySize; i++) {
			if (nrofResponseReqCreated[i] > 0) {
				responseProb[i] = (1.0 * nrofResponseDelivered[i]) / nrofResponseReqCreated[i];
			}
		}
		
		/* printing stats */
		String statsText = "";
		write("Message stats for scenario " + getScenarioName() + "\nsim_time: " + format(getSimTime()));
		for (int i = 0; i < arraySize; i++) {
			statsText = "Priority Level: " + (i) +
						"\ncreated: " + nrofCreated[i] +
						"\nstarted: " + nrofStarted[i] +
						"\nrelayed: " + nrofRelayed[i] +
						"\nduplicates: " + nrofDuplicates[i] +
						"\naborted: " + nrofAborted[i] +
						"\nInterfered: " + nrofInterfered[i] +
						"\ndropped: " + nrofDropped[i] +
						"\nremoved: " + nrofRemoved[i] +
						"\ndelivered: " + nrofDelivered[i] +
						"\ndelivery_prob: " + format(deliveryProb[i]) +
						"\nresponse_prob: " + format(responseProb[i]) +
						"\noverhead_ratio: " + format(overHead[i]) +
						"\nlatency_avg: " + getAverage(latencies[i]) +
						"\nlatency_med: " + getMedian(latencies[i]) +
						"\nhopcount_avg: " + getIntAverage(hopCounts[i]) +
						"\nhopcount_med: " + getIntMedian(hopCounts[i]) +
						"\ncachetime_avg: " + getAverage(msgCacheTime[i]) +
						"\ncachetime_med: " + getMedian(msgCacheTime[i]) +
						"\nrtt_avg: " + getAverage(rtt[i]) +
						"\nrtt_med: " + getMedian(rtt[i]) + "\n\n";
			write(statsText);
		}
		
		super.done();
	}
	
}
