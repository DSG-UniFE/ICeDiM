/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package report;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import core.DTNHost;
import core.Message;
import core.MessageListener;
import core.SimError;
import core.disService.DisServiceHelloMessage;
import core.disService.PublishSubscriber;
import core.disService.SubscriptionListManager;

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
	private ArrayList<Double> msgBufferTime[];
	private ArrayList<Double> rtt[]; // round trip times
	
	private int nrofDropped[];
	private int nrofRemoved[];
	private int nrofStarted[];
	private int nrofAborted[];
	private int nrofInterfered[];
	private int nrofRelayed[];
	private int nrofCreated[];
	private int nrofResponseReqCreated[];
	private int nrofResponseDelivered[];
	private int nrofDelivered[];
	
	private int nrofHelloMessagesStarted;
	private int nrofHelloMessagesDelivered;
	private int nrofHelloMessagesAborted;
	private int nrofHelloMessagesInterfered;
	
	private HashMap<Integer, Integer> nodesPerSubscription;
	private HashMap<Integer, Integer> messageCreatedPerSubscription;
	private HashMap<Integer, Integer> messageResponseCreatedPerSubscription;
	private HashMap<Integer, Integer> messageStartedPerSubscription;
	private HashMap<Integer, Integer> messageRelayedPerSubscription;
	private HashMap<Integer, Integer> messageDeliveredPerSubscription;
	private HashMap<Integer, Integer> messageResponseDeliveredPerSubscription;
	private HashMap<Integer, Integer> messageAbortedPerSubscription;
	private HashMap<Integer, Integer> messageDroppedPerSubscription;
	private HashMap<Integer, Integer> messageRemovedPerSubscription;
	private HashMap<Integer, Integer> messageInterferredPerSubscription;
	
	
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
		int arraySize = Message.PRIORITY_LEVEL.values().length;
		
		this.nrofHelloMessagesStarted = 0;
		this.nrofHelloMessagesDelivered = 0;
		this.nrofHelloMessagesAborted = 0;
		this.nrofHelloMessagesInterfered = 0;
		
		this.creationTimes = new HashMap<String, Double>();
		this.latencies = new ArrayList[arraySize];
		for (int i = 0; i < arraySize; i++) {
			this.latencies[i] = new ArrayList<Double>();
		}
		this.msgBufferTime = new ArrayList[arraySize];
		for (int i = 0; i < arraySize; i++) {
			this.msgBufferTime[i] = new ArrayList<Double>();
		}
		this.hopCounts = new ArrayList[arraySize];
		for (int i = 0; i < arraySize; i++) {
			this.hopCounts[i] = new ArrayList<Integer>();
		}
		this.rtt = new ArrayList[arraySize];
		for (int i = 0; i < arraySize; i++) {
			this.rtt[i] = new ArrayList<Double>();
		}

		this.nrofDropped = new int[arraySize];
		for (int i = 0; i < arraySize; i++) {
			this.nrofDropped[i] = 0;
		}

		this.nrofRemoved = new int[arraySize];
		for (int i = 0; i < arraySize; i++) {
			this.nrofRemoved[i] = 0;
		}

		this.nrofStarted = new int[arraySize];
		for (int i = 0; i < arraySize; i++) {
			this.nrofStarted[i] = 0;
		}
		
		this.nrofAborted = new int[arraySize];
		for (int i = 0; i < arraySize; i++) {
			this.nrofAborted[i] = 0;
		}
		
		this.nrofInterfered = new int[arraySize];
		for (int i = 0; i < arraySize; i++) {
			this.nrofInterfered[i] = 0;
		}
		
		this.nrofRelayed = new int[arraySize];
		for (int i = 0; i < arraySize; i++) {
			this.nrofRelayed[i] = 0;
		}
		
		this.nrofCreated = new int[arraySize];
		for (int i = 0; i < arraySize; i++) {
			this.nrofCreated[i] = 0;
		}
		
		this.nrofResponseReqCreated = new int[arraySize];
		for (int i = 0; i < arraySize; i++) {
			this.nrofResponseReqCreated[i] = 0;
		}
		
		this.nrofResponseDelivered = new int[arraySize];
		for (int i = 0; i < arraySize; i++) {
			this.nrofResponseDelivered[i] = 0;
		}
		
		this.nrofDelivered = new int[arraySize];
		for (int i = 0; i < arraySize; i++) {
			this.nrofDelivered[i] = 0;
		}

		this.nodesPerSubscription = new HashMap<Integer, Integer>();
		this.messageCreatedPerSubscription = new HashMap<Integer, Integer>();
		this.messageResponseCreatedPerSubscription = new HashMap<Integer, Integer>();
		this.messageStartedPerSubscription = new HashMap<Integer, Integer>();
		this.messageRelayedPerSubscription = new HashMap<Integer, Integer>();
		this.messageDeliveredPerSubscription = new HashMap<Integer, Integer>();
		this.messageResponseDeliveredPerSubscription = new HashMap<Integer, Integer>();
		this.messageAbortedPerSubscription = new HashMap<Integer, Integer>();
		this.messageDroppedPerSubscription = new HashMap<Integer, Integer>();
		this.messageRemovedPerSubscription = new HashMap<Integer, Integer>();
		this.messageInterferredPerSubscription = new HashMap<Integer, Integer>();
	}

	
	@Override
	public void registerNode(DTNHost node) {
		if (node instanceof PublishSubscriber) {
			PublishSubscriber destNode = (PublishSubscriber) node;
			
			SubscriptionListManager sl = destNode.getSubscriptionList();
			for (int subID : sl.getSubscriptionList()) {
				if (nodesPerSubscription.containsKey(subID)) {
					nodesPerSubscription.put(subID,
						Integer.valueOf(nodesPerSubscription.get(subID).intValue() + 1));
				}
				else {
					nodesPerSubscription.put(subID, 1);
				}
			}
		}
		else {
			int subID = SubscriptionListManager.DEFAULT_SUB_ID;
			if (nodesPerSubscription.containsKey(subID)) {
				nodesPerSubscription.put(subID,
					Integer.valueOf(nodesPerSubscription.get(subID).intValue() + 1));
			}
			else {
				nodesPerSubscription.put(subID, 1);
			}
		}
	}
	
	public void messageDeleted(Message m, DTNHost where, boolean dropped) {
		if (isWarmupID(m.getID())) {
			return;
		}
		
		if (dropped) {
			nrofDropped[m.getPriority().ordinal()]++;
			
			Integer subID = Integer.valueOf(m.getSubscriptionID());
			if (messageDroppedPerSubscription.containsKey(subID)) {
				messageDroppedPerSubscription.put(subID,
					Integer.valueOf(messageDroppedPerSubscription.get(subID).intValue() + 1));
			}
			else {
				messageDroppedPerSubscription.put(subID, 1);
			}
		}
		else {
			nrofRemoved[m.getPriority().ordinal()]++;
			
			Integer subID = Integer.valueOf(m.getSubscriptionID());
			if (messageRemovedPerSubscription.containsKey(subID)) {
				messageRemovedPerSubscription.put(subID,
					Integer.valueOf(messageRemovedPerSubscription.get(subID).intValue() + 1));
			}
			else {
				messageRemovedPerSubscription.put(subID, 1);
			}
		}

		msgBufferTime[m.getPriority().ordinal()].add(getSimTime() - m.getReceiveTime());
	}

	public void messageTransferAborted(Message m, DTNHost from, DTNHost to) {
		if (isWarmupID(m.getID())) {
			return;
		}
		
		if (m instanceof DisServiceHelloMessage) {
			nrofHelloMessagesAborted++;
		}
		else {		
			nrofAborted[m.getPriority().ordinal()]++;
			
			Integer subID = Integer.valueOf(m.getSubscriptionID());
			if (messageAbortedPerSubscription.containsKey(subID)) {
				messageAbortedPerSubscription.put(subID,
					Integer.valueOf(messageAbortedPerSubscription.get(subID).intValue() + 1));
			}
			else {
				messageAbortedPerSubscription.put(subID, 1);
			}
		}
	}

	@Override
	public void messageTransmissionInterfered(Message m, DTNHost from, DTNHost to) {
		if (m instanceof DisServiceHelloMessage) {
			nrofHelloMessagesInterfered++;
		}
		else {
			nrofInterfered[m.getPriority().ordinal()]++;
			
			Integer subID = Integer.valueOf(m.getSubscriptionID());
			if (messageInterferredPerSubscription.containsKey(subID)) {
				messageInterferredPerSubscription.put(subID,
					Integer.valueOf(messageInterferredPerSubscription.get(subID).intValue() + 1));
			}
			else {
				messageInterferredPerSubscription.put(subID, 1);
			}
		}
	}

	public void messageTransferred(Message m, DTNHost from, DTNHost to, boolean finalTarget) {
		if (isWarmupID(m.getID())) {
			return;
		}

		if (m instanceof DisServiceHelloMessage) {
			nrofHelloMessagesDelivered++;
		}
		else {
			nrofRelayed[m.getPriority().ordinal()]++;

			Integer subID = Integer.valueOf(m.getSubscriptionID());
			if (messageRelayedPerSubscription.containsKey(subID)) {
				messageRelayedPerSubscription.put(subID,
					Integer.valueOf(messageRelayedPerSubscription.get(subID).intValue() + 1));
			}
			else {
				messageRelayedPerSubscription.put(subID, 1);
			}
			
			if (finalTarget) {
				latencies[m.getPriority().ordinal()].add(getSimTime() - this.creationTimes.get(m.getID()));
				nrofDelivered[m.getPriority().ordinal()]++;
				hopCounts[m.getPriority().ordinal()].add(m.getHops().size() - 1);
				
				if (m.isResponse()) {
					rtt[m.getPriority().ordinal()].add(getSimTime() -	m.getRequest().getCreationTime());
					nrofResponseDelivered[m.getPriority().ordinal()]++;
					
					if (messageResponseDeliveredPerSubscription.containsKey(subID)) {
						messageResponseDeliveredPerSubscription.put(subID,
							Integer.valueOf(messageResponseDeliveredPerSubscription.get(subID).intValue() + 1));
					}
					else {
						messageResponseDeliveredPerSubscription.put(subID, 1);
					}
				}
			}

			if (to.getRouter() instanceof PublishSubscriber) {
				PublishSubscriber destNode = (PublishSubscriber) to.getRouter();
				if (subID <= SubscriptionListManager.INVALID_SUB_ID) {
					throw new SimError("Message subscription ID (" + subID + ") is invalid");
				}
				SubscriptionListManager sl = destNode.getSubscriptionList();
				if (sl.containsSubscriptionID(subID)) {
					if (messageDeliveredPerSubscription.containsKey(subID)) {
						messageDeliveredPerSubscription.put(subID,
							Integer.valueOf(messageDeliveredPerSubscription.get(subID).intValue() + 1));
					}
					else {
						messageDeliveredPerSubscription.put(subID, 1);
					}
				}
			}
			else if (finalTarget) {
				if (messageDeliveredPerSubscription.containsKey(subID)) {
					messageDeliveredPerSubscription.put(subID,
						Integer.valueOf(messageDeliveredPerSubscription.get(subID).intValue() + 1));
				}
				else {
					messageDeliveredPerSubscription.put(subID, 1);
				}
			}
		}
	}

	public void newMessage(Message m) {
		if (isWarmup()) {
			addWarmupID(m.getID());
			return;
		}
		
		creationTimes.put(m.getID(), getSimTime());
		nrofCreated[m.getPriority().ordinal()]++;

		Integer subID = Integer.valueOf(m.getSubscriptionID());
		if (messageCreatedPerSubscription.containsKey(subID)) {
			messageCreatedPerSubscription.put(subID,
				Integer.valueOf(messageCreatedPerSubscription.get(subID).intValue() + 1));
		}
		else {
			messageCreatedPerSubscription.put(subID, 1);
		}
		
		if (m.getResponseSize() > 0) {
			nrofResponseReqCreated[m.getPriority().ordinal()]++;
			
			if (messageResponseCreatedPerSubscription.containsKey(subID)) {
				messageResponseCreatedPerSubscription.put(subID,
					Integer.valueOf(messageResponseCreatedPerSubscription.get(subID).intValue() + 1));
			}
			else {
				messageResponseCreatedPerSubscription.put(subID, 1);
			}
		}
	}

	public void messageTransferStarted(Message m, DTNHost from, DTNHost to) {
		if (isWarmupID(m.getID())) {
			return;
		}
		
		if (m instanceof DisServiceHelloMessage) {
			nrofHelloMessagesStarted++;
		}
		else {
			nrofStarted[m.getPriority().ordinal()]++;

			Integer subID = Integer.valueOf(m.getSubscriptionID());
			if (messageStartedPerSubscription.containsKey(subID)) {
				messageStartedPerSubscription.put(subID,
					Integer.valueOf(messageStartedPerSubscription.get(subID).intValue() + 1));
			}
			else {
				messageStartedPerSubscription.put(subID, 1);
			}
		}
	}

	@Override
	public void done() {
		int arraySize = Message.PRIORITY_LEVEL.values().length;
		
		double deliveryProb[] = new double[arraySize];	// delivery probability
		double responseProb[] = new double[arraySize];	// request-response success probability
		double overHead[] = new double[arraySize];		// overhead ratio
		
		for (int i = 0; i < arraySize; i++) {
			if (nrofCreated[i] > 0) {
				deliveryProb[i] = (1.0 * nrofDelivered[i]) / nrofCreated[i];
			}
			else {
				deliveryProb[i] = 0;
			}
		}
		for (int i = 0; i < arraySize; i++) {
			if (nrofDelivered[i] > 0) {
				overHead[i] = (1.0 * (nrofRelayed[i] - nrofDelivered[i])) / nrofDelivered[i];
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
		write("Message stats for scenario " + getScenarioName() + "\nsim_time: " + format(getSimTime()));
		String statsText = "";
		if (nrofHelloMessagesStarted > 0) {
			statsText = "\nHMSent: " + nrofHelloMessagesStarted + 
						"\nHMDelivered : " + nrofHelloMessagesDelivered + 
						"\nHMAborted: " + nrofHelloMessagesAborted + 
						"\nHMInterfered: " + nrofHelloMessagesInterfered;
			write(statsText);
		}
		
		for (int i = 0; i < arraySize; i++) {
			statsText = "Priority Level: " + (i) +
						"\ncreated: " + nrofCreated[i] +
						"\nstarted: " + nrofStarted[i] +
						"\nrelayed: " + nrofRelayed[i] +
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
						"\nbuffertime_avg: " + getAverage(msgBufferTime[i]) +
						"\nbuffertime_med: " + getMedian(msgBufferTime[i]) +
						"\nrtt_avg: " + getAverage(rtt[i]) +
						"\nrtt_med: " + getMedian(rtt[i]) + "\n\n";
			write(statsText);
		}

		if (!nodesPerSubscription.isEmpty()) {
			int statsSize = SubscriptionListManager.MAX_SUB_ID_FOR_SIMULATION;
			deliveryProb = new double[statsSize];		// delivery probability
			responseProb = new double[statsSize];		// request-response success probability
			overHead = new double[statsSize];			// overhead ratio
			
			for (int i = 0; i < statsSize; i++) {
				if (messageCreatedPerSubscription.get(i).intValue() > 0) {
					deliveryProb[i] = (1.0 * messageDeliveredPerSubscription.get(i).intValue()) /
										messageCreatedPerSubscription.get(i).intValue();
				}
				else {
					deliveryProb[i] = 0;
				}
			}
			for (int i = 0; i < statsSize; i++) {
				if (messageDeliveredPerSubscription.get(i).intValue() > 0) {
					overHead[i] = (1.0 * (messageRelayedPerSubscription.get(i).intValue() -
											messageDeliveredPerSubscription.get(i).intValue())) /
											messageDeliveredPerSubscription.get(i).intValue();
				}
				else {
					overHead[i] = Double.NaN;
				}
			}
			for (int i = 0; i < statsSize; i++) {
				if (messageResponseCreatedPerSubscription.get(i).intValue() > 0) {
					responseProb[i] = (1.0 * messageResponseDeliveredPerSubscription.get(i).intValue()) /
										messageResponseCreatedPerSubscription.get(i).intValue();
				}
			}
			write("Statistics per subscription ID:\n");
			for (int i = 0; i < statsSize; i++) {
				statsText = "Subscription ID: " + i +
							"\nregistered: " + nodesPerSubscription.get(i).intValue() +
							"\ncreated: " + messageCreatedPerSubscription.get(i).intValue() +
							"\nstarted: " + messageStartedPerSubscription.get(i).intValue() +
							"\nrelayed: " + messageRelayedPerSubscription.get(i).intValue() +
							"\naborted: " + messageAbortedPerSubscription.get(i).intValue() +
							"\nInterfered: " + messageInterferredPerSubscription.get(i).intValue() +
							"\ndropped: " + messageDroppedPerSubscription.get(i).intValue() +
							"\nremoved: " + messageRemovedPerSubscription.get(i).intValue() +
							"\ndelivered: " + messageDeliveredPerSubscription.get(i).intValue() +
							"\ndelivery_prob: " + format(deliveryProb[i]) +
							"\nresponse_prob: " + format(responseProb[i]) +
							"\noverhead_ratio: " + format(overHead[i]) +
							"\n\n";
			}
		}
		
		super.done();
	}
	
}