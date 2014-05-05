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
import core.disService.PublisherSubscriber;
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
	private int nrofDuplicates[];
	private int nrofCreated[];
	private int nrofResponseReqCreated[];
	private int nrofResponseDelivered[];
	private int nrofDelivered[];
	
	private int nrofTotalDueDeliveries[];
	
	private int nrofHelloMessagesStarted;
	private int nrofHelloMessagesDelivered;
	private int nrofHelloMessagesAborted;
	private int nrofHelloMessagesInterfered;
	
	private HashMap<Integer, Integer> nodesPerSubscription;
	private HashMap<Integer, Integer> messageCreatedPerSubscription;
	private HashMap<Integer, Integer> messageResponseCreatedPerSubscription;
	private HashMap<Integer, Integer> messageStartedPerSubscription;
	private HashMap<Integer, Integer> messageRelayedPerSubscription;
	private HashMap<Integer, Integer> messageDuplicatesPerSubscription;
	private HashMap<Integer, Integer> messageDeliveredPerSubscription;
	private HashMap<Integer, Integer> messageResponseDeliveredPerSubscription;
	private HashMap<Integer, Integer> messageAbortedPerSubscription;
	private HashMap<Integer, Integer> messageDroppedPerSubscription;
	private HashMap<Integer, Integer> messageRemovedPerSubscription;
	private HashMap<Integer, Integer> messageInterferedPerSubscription;
	
	
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
		int subscriptionsArraySize = SubscriptionListManager.MAX_SUB_ID_OF_SIMULATION + 1;
		
		this.nrofHelloMessagesStarted = 0;
		this.nrofHelloMessagesDelivered = 0;
		this.nrofHelloMessagesAborted = 0;
		this.nrofHelloMessagesInterfered = 0;
		
		this.creationTimes = new HashMap<String, Double>();
		this.latencies = new ArrayList[priorityArraySize];
		for (int i = 0; i < priorityArraySize; i++) {
			this.latencies[i] = new ArrayList<Double>();
		}
		this.msgBufferTime = new ArrayList[priorityArraySize];
		for (int i = 0; i < priorityArraySize; i++) {
			this.msgBufferTime[i] = new ArrayList<Double>();
		}
		this.hopCounts = new ArrayList[priorityArraySize];
		for (int i = 0; i < priorityArraySize; i++) {
			this.hopCounts[i] = new ArrayList<Integer>();
		}
		this.rtt = new ArrayList[priorityArraySize];
		for (int i = 0; i < priorityArraySize; i++) {
			this.rtt[i] = new ArrayList<Double>();
		}

		this.nrofDropped = new int[priorityArraySize];
		for (int i = 0; i < priorityArraySize; i++) {
			this.nrofDropped[i] = 0;
		}

		this.nrofRemoved = new int[priorityArraySize];
		for (int i = 0; i < priorityArraySize; i++) {
			this.nrofRemoved[i] = 0;
		}

		this.nrofStarted = new int[priorityArraySize];
		for (int i = 0; i < priorityArraySize; i++) {
			this.nrofStarted[i] = 0;
		}
		
		this.nrofAborted = new int[priorityArraySize];
		for (int i = 0; i < priorityArraySize; i++) {
			this.nrofAborted[i] = 0;
		}
		
		this.nrofInterfered = new int[priorityArraySize];
		for (int i = 0; i < priorityArraySize; i++) {
			this.nrofInterfered[i] = 0;
		}
		
		this.nrofRelayed = new int[priorityArraySize];
		for (int i = 0; i < priorityArraySize; i++) {
			this.nrofRelayed[i] = 0;
		}

		this.nrofDuplicates = new int[priorityArraySize];
		for (int i = 0; i < priorityArraySize; i++) {
			this.nrofDuplicates[i] = 0;
		}
		
		this.nrofCreated = new int[priorityArraySize];
		for (int i = 0; i < priorityArraySize; i++) {
			this.nrofCreated[i] = 0;
		}
		
		this.nrofResponseReqCreated = new int[priorityArraySize];
		for (int i = 0; i < priorityArraySize; i++) {
			this.nrofResponseReqCreated[i] = 0;
		}
		
		this.nrofResponseDelivered = new int[priorityArraySize];
		for (int i = 0; i < priorityArraySize; i++) {
			this.nrofResponseDelivered[i] = 0;
		}
		
		this.nrofDelivered = new int[priorityArraySize];
		for (int i = 0; i < priorityArraySize; i++) {
			this.nrofDelivered[i] = 0;
		}
		
		this.nrofTotalDueDeliveries = new int[priorityArraySize];
		for (int i = 0; i < priorityArraySize; i++) {
			this.nrofTotalDueDeliveries[i] = 0;
		}

		this.nodesPerSubscription = new HashMap<Integer, Integer>();
		this.messageCreatedPerSubscription = new HashMap<Integer, Integer>();
		this.messageResponseCreatedPerSubscription = new HashMap<Integer, Integer>();
		this.messageStartedPerSubscription = new HashMap<Integer, Integer>();
		this.messageRelayedPerSubscription = new HashMap<Integer, Integer>();
		this.messageDuplicatesPerSubscription = new HashMap<Integer, Integer>();
		this.messageDeliveredPerSubscription = new HashMap<Integer, Integer>();
		this.messageResponseDeliveredPerSubscription = new HashMap<Integer, Integer>();
		this.messageAbortedPerSubscription = new HashMap<Integer, Integer>();
		this.messageDroppedPerSubscription = new HashMap<Integer, Integer>();
		this.messageRemovedPerSubscription = new HashMap<Integer, Integer>();
		this.messageInterferedPerSubscription = new HashMap<Integer, Integer>();

		for (int i = 1; i < subscriptionsArraySize; ++i) {
			this.nodesPerSubscription.put(i, 0);
			this.messageCreatedPerSubscription.put(i, 0);
			this.messageResponseCreatedPerSubscription.put(i, 0);
			this.messageStartedPerSubscription.put(i, 0);
			this.messageRelayedPerSubscription.put(i, 0);
			this.messageDuplicatesPerSubscription.put(i, 0);
			this.messageDeliveredPerSubscription.put(i, 0);
			this.messageResponseDeliveredPerSubscription.put(i, 0);
			this.messageAbortedPerSubscription.put(i, 0);
			this.messageDroppedPerSubscription.put(i, 0);
			this.messageRemovedPerSubscription.put(i, 0);
			this.messageInterferedPerSubscription.put(i, 0);
		}

		if (subscriptionsArraySize <= 1) {
			this.nodesPerSubscription.put(0, 0);
			this.messageCreatedPerSubscription.put(0, 0);
			this.messageResponseCreatedPerSubscription.put(0, 0);
			this.messageStartedPerSubscription.put(0, 0);
			this.messageRelayedPerSubscription.put(0, 0);
			this.messageDuplicatesPerSubscription.put(0, 0);
			this.messageDeliveredPerSubscription.put(0, 0);
			this.messageResponseDeliveredPerSubscription.put(0, 0);
			this.messageAbortedPerSubscription.put(0, 0);
			this.messageDroppedPerSubscription.put(0, 0);
			this.messageRemovedPerSubscription.put(0, 0);
			this.messageInterferedPerSubscription.put(0, 0);
		}
	}

	
	@Override
	public void registerNode(DTNHost node) {
		if (node.getRouter() instanceof PublisherSubscriber) {
			PublisherSubscriber destNode = (PublisherSubscriber) node.getRouter();
			
			SubscriptionListManager sl = destNode.getSubscriptionList();
			for (int subID : sl.getSubscriptionList()) {
				nodesPerSubscription.put(subID, Integer.valueOf(
						nodesPerSubscription.get(subID).intValue() + 1));
			}
		}
		else {
			int subID = SubscriptionListManager.DEFAULT_SUB_ID;
			nodesPerSubscription.put(subID, Integer.valueOf(
					nodesPerSubscription.get(subID).intValue() + 1));
		}
	}
	
	public void messageDeleted(Message m, DTNHost where, boolean dropped, String cause) {
		if (isWarmupID(m.getID())) {
			return;
		}

		Integer subID = (Integer) m.getProperty(PublisherSubscriber.SUBSCRIPTION_MESSAGE_PROPERTY_KEY);		
		if (dropped) {
			nrofDropped[m.getPriority()]++;
			messageDroppedPerSubscription.put(subID, Integer.valueOf(
					messageDroppedPerSubscription.get(subID).intValue() + 1));
		}
		else {
			nrofRemoved[m.getPriority()]++;
			messageRemovedPerSubscription.put(subID, Integer.valueOf(
					messageRemovedPerSubscription.get(subID).intValue() + 1));
		}

		msgBufferTime[m.getPriority()].add(getSimTime() - m.getReceiveTime());
	}

	public void messageTransferAborted(Message m, DTNHost from, DTNHost to) {
		if (isWarmupID(m.getID())) {
			return;
		}
		
		if (m instanceof DisServiceHelloMessage) {
			nrofHelloMessagesAborted++;
		}
		else {
			nrofAborted[m.getPriority()]++;

			Integer subID = (Integer) m.getProperty(PublisherSubscriber.SUBSCRIPTION_MESSAGE_PROPERTY_KEY);
			messageAbortedPerSubscription.put(subID, Integer.valueOf(
					messageAbortedPerSubscription.get(subID).intValue() + 1));
		}
	}

	@Override
	public void messageTransmissionInterfered(Message m, DTNHost from, DTNHost to) {
		if (m instanceof DisServiceHelloMessage) {
			nrofHelloMessagesInterfered++;
		}
		else {
			nrofInterfered[m.getPriority()]++;

			Integer subID = (Integer) m.getProperty(PublisherSubscriber.SUBSCRIPTION_MESSAGE_PROPERTY_KEY);
			messageInterferedPerSubscription.put(subID, Integer.valueOf(
					messageInterferedPerSubscription.get(subID).intValue() + 1));
		}
	}

	public void messageTransferred(Message m, DTNHost from, DTNHost to,
									boolean firstDelivery, boolean finalTarget) {
		if (isWarmupID(m.getID())) {
			return;
		}

		if (m instanceof DisServiceHelloMessage) {
			nrofHelloMessagesDelivered++;
		}
		else if (firstDelivery) {
			nrofRelayed[m.getPriority()]++;

			Integer subID = (Integer) m.getProperty(PublisherSubscriber.SUBSCRIPTION_MESSAGE_PROPERTY_KEY);
			messageRelayedPerSubscription.put(subID, Integer.valueOf(
					messageRelayedPerSubscription.get(subID).intValue() + 1));
			
			if (finalTarget) {
				latencies[m.getPriority()].add(getSimTime() - creationTimes.get(m.getID()));
				nrofDelivered[m.getPriority()]++;
				hopCounts[m.getPriority()].add(m.getHops().size() - 1);
				
				if (m.isResponse()) {
					rtt[m.getPriority()].add(getSimTime() -	m.getRequest().getCreationTime());
					nrofResponseDelivered[m.getPriority()]++;
					messageResponseDeliveredPerSubscription.put(subID, Integer.valueOf(
							messageResponseDeliveredPerSubscription.get(subID).intValue() + 1));
				}
				
				if (to.getRouter() instanceof PublisherSubscriber) {
					if (subID <= SubscriptionListManager.INVALID_SUB_ID) {
						throw new SimError("Message subscription ID (" + subID + ") is invalid");
					}

					PublisherSubscriber destRouter = (PublisherSubscriber) to.getRouter();
					SubscriptionListManager sl = destRouter.getSubscriptionList();
					if (sl.containsSubscriptionID(subID)) {
						messageDeliveredPerSubscription.put(subID, Integer.valueOf(
								messageDeliveredPerSubscription.get(subID).intValue() + 1));
					}
					if (m.isResponse() && sl.containsSubscriptionID(subID)) {
						messageResponseDeliveredPerSubscription.put(subID, Integer.valueOf(
								messageResponseDeliveredPerSubscription.get(subID).intValue() + 1));
					}
				}
			}
		}
		else {
			// Duplicate message
			nrofDuplicates[m.getPriority()]++;
			
			Integer subID = (Integer) m.getProperty(PublisherSubscriber.SUBSCRIPTION_MESSAGE_PROPERTY_KEY);
			messageDuplicatesPerSubscription.put(subID, Integer.valueOf(
					messageDuplicatesPerSubscription.get(subID).intValue() + 1));
		}
	}

	public void newMessage(Message m) {
		if (isWarmup()) {
			addWarmupID(m.getID());
			return;
		}

		nrofCreated[m.getPriority()]++;
		creationTimes.put(m.getID(), getSimTime());

		Integer subID = (Integer) m.getProperty(PublisherSubscriber.SUBSCRIPTION_MESSAGE_PROPERTY_KEY);
		if ((m.getTo() == null) && (subID > SubscriptionListManager.DEFAULT_SUB_ID)) {
			nrofTotalDueDeliveries[m.getPriority()] += nodesPerSubscription.get(subID);
		}
		else if ((m.getTo() != null) && (subID == SubscriptionListManager.DEFAULT_SUB_ID)) {
			nrofTotalDueDeliveries[m.getPriority()]++;
		}
		else {
			throw new SimError("Impossible to discriminate the delivery mechanism");
		}
		messageCreatedPerSubscription.put(subID, Integer.valueOf(
				messageCreatedPerSubscription.get(subID).intValue() + 1));
		
		if (m.getResponseSize() > 0) {
			nrofResponseReqCreated[m.getPriority()]++;
			messageResponseCreatedPerSubscription.put(subID, Integer.valueOf(
					messageResponseCreatedPerSubscription.get(subID).intValue() + 1));
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
			nrofStarted[m.getPriority()]++;

			Integer subID = (Integer) m.getProperty(PublisherSubscriber.SUBSCRIPTION_MESSAGE_PROPERTY_KEY);
			messageStartedPerSubscription.put(subID, Integer.valueOf(
					messageStartedPerSubscription.get(subID).intValue() + 1));
		}
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
						"\nbuffertime_avg: " + getAverage(msgBufferTime[i]) +
						"\nbuffertime_med: " + getMedian(msgBufferTime[i]) +
						"\nrtt_avg: " + getAverage(rtt[i]) +
						"\nrtt_med: " + getMedian(rtt[i]) + "\n\n";
			write(statsText);
		}

		if (!nodesPerSubscription.isEmpty()) {
			int statsSize = SubscriptionListManager.MAX_SUB_ID_OF_SIMULATION;
			deliveryProb = new double[statsSize];		// delivery probability
			responseProb = new double[statsSize];		// request-response success probability
			overHead = new double[statsSize];			// overhead ratio
			
			for (int i = 1; i <= statsSize; ++i) {
				if (messageCreatedPerSubscription.get(i).intValue() > 0) {
					deliveryProb[i - 1] = (1.0 * messageDeliveredPerSubscription.get(i).intValue()) /
											(messageCreatedPerSubscription.get(i).intValue() *
											nodesPerSubscription.get(i).intValue());
				}
				else {
					deliveryProb[i - 1] = 0;
				}
			}
			for (int i = 1; i <= statsSize; ++i) {
				if (messageDeliveredPerSubscription.get(i).intValue() > 0) {
					overHead[i - 1] = (1.0 * (messageDuplicatesPerSubscription.get(i).intValue() +
										messageRelayedPerSubscription.get(i).intValue() -
										messageDeliveredPerSubscription.get(i).intValue())) /
										messageDeliveredPerSubscription.get(i).intValue();
				}
				else {
					overHead[i - 1] = Double.NaN;
				}
			}
			for (int i = 1; i <= statsSize; ++i) {
				if (messageResponseCreatedPerSubscription.get(i).intValue() > 0) {
					responseProb[i - 1] = (1.0 * messageResponseDeliveredPerSubscription.get(i).intValue()) /
											messageResponseCreatedPerSubscription.get(i).intValue();
				}
			}
			
			write("Statistics per subscription ID:\n");
			for (int i = 1; i <= statsSize; ++i) {
				statsText = "Subscription ID: " + i +
							"\nregistered: " + nodesPerSubscription.get(i).intValue() +
							"\ncreated: " + messageCreatedPerSubscription.get(i).intValue() +
							"\nstarted: " + messageStartedPerSubscription.get(i).intValue() +
							"\nrelayed: " + messageRelayedPerSubscription.get(i).intValue() +
							"\nduplicates: " + messageDuplicatesPerSubscription.get(i).intValue() +
							"\naborted: " + messageAbortedPerSubscription.get(i).intValue() +
							"\nInterfered: " + messageInterferedPerSubscription.get(i).intValue() +
							"\ndropped: " + messageDroppedPerSubscription.get(i).intValue() +
							"\nremoved: " + messageRemovedPerSubscription.get(i).intValue() +
							"\ndelivered: " + messageDeliveredPerSubscription.get(i).intValue() +
							"\ndelivery_prob: " + format(deliveryProb[i - 1]) +
							"\nresponse_prob: " + format(responseProb[i - 1]) +
							"\noverhead_ratio: " + format(overHead[i - 1]) +
							"\n\n";
				write(statsText);
			}
		}
		
		super.done();
	}
	
}