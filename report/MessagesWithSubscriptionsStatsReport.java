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
public class MessagesWithSubscriptionsStatsReport extends Report implements MessageListener {
	private Map<String, Double> creationTimes;
	
	private int nrofDropped;
	private int nrofRemoved;
	private int nrofStarted;
	private int nrofAborted;
	private int nrofInterfered;
	private int nrofRelayed;
	private int nrofDuplicates;
	private int nrofCreated;
	private int nrofResponseReqCreated;
	private int nrofResponseDelivered;
	private int nrofDelivered;
	
	private int nrofTotalDueDeliveries;
	
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
	
	private HashMap<String, Integer> firstDeliveriesPerMessage;
	
	private HashMap<Integer, ArrayList<Double>> latenciesPerSubscription;
	private HashMap<Integer, ArrayList<Integer>> hopCountsPerSubscription;
	private HashMap<Integer, ArrayList<Double>> msgBufferTimePerSubscription;
	private HashMap<Integer, ArrayList<Double>> msgRTTPerSubscription;
	
	
	/**
	 * Constructor.
	 */
	public MessagesWithSubscriptionsStatsReport() {
		init();
	}

	@Override
	protected void init() {
		super.init();
		int subscriptionsArraySize = SubscriptionListManager.MAX_SUB_ID_OF_SIMULATION + 1;
		
		this.creationTimes = new HashMap<String, Double>();
		
		this.nrofHelloMessagesStarted = 0;
		this.nrofHelloMessagesDelivered = 0;
		this.nrofHelloMessagesAborted = 0;
		this.nrofHelloMessagesInterfered = 0;

		this.nrofDropped = 0;
		this.nrofRemoved = 0;
		this.nrofStarted = 0;
		this.nrofAborted = 0;
		this.nrofInterfered = 0;
		this.nrofRelayed = 0;
		this.nrofDuplicates = 0;
		this.nrofCreated = 0;
		this.nrofResponseReqCreated = 0;
		this.nrofResponseDelivered = 0;
		this.nrofDelivered = 0;
		
		this.nrofTotalDueDeliveries = 0;

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
		
		this.firstDeliveriesPerMessage = new HashMap<String, Integer>();
		
		this.latenciesPerSubscription = new HashMap<Integer, ArrayList<Double>>();
		this.hopCountsPerSubscription = new HashMap<Integer, ArrayList<Integer>>();
		this.msgBufferTimePerSubscription = new HashMap<Integer, ArrayList<Double>>();
		this.msgRTTPerSubscription = new HashMap<Integer, ArrayList<Double>>();

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
			
			this.latenciesPerSubscription.put(i, new ArrayList<Double>());
			this.hopCountsPerSubscription.put(i, new ArrayList<Integer>());
			this.msgBufferTimePerSubscription.put(i, new ArrayList<Double>());
			this.msgRTTPerSubscription.put(i, new ArrayList<Double>());
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
			
			this.latenciesPerSubscription.put(0, new ArrayList<Double>());
			this.hopCountsPerSubscription.put(0, new ArrayList<Integer>());
			this.msgBufferTimePerSubscription.put(0, new ArrayList<Double>());
			this.msgRTTPerSubscription.put(0, new ArrayList<Double>());
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

	@Override
	public void messageDeleted(Message m, DTNHost where, boolean dropped, String cause) {
		if (isWarmupID(m.getID())) {
			return;
		}

		Integer subID = (Integer) m.getProperty(PublisherSubscriber.SUBSCRIPTION_MESSAGE_PROPERTY_KEY);		
		if (dropped) {
			nrofDropped++;
			messageDroppedPerSubscription.put(subID, Integer.valueOf(
					messageDroppedPerSubscription.get(subID).intValue() + 1));
		}
		else {
			nrofRemoved++;
			messageRemovedPerSubscription.put(subID, Integer.valueOf(
					messageRemovedPerSubscription.get(subID).intValue() + 1));
		}
		msgBufferTimePerSubscription.get(subID).add(getSimTime() - m.getReceiveTime());
	}

	@Override
	public void messageTransferAborted(Message m, DTNHost from, DTNHost to, String cause) {
		if (isWarmupID(m.getID())) {
			return;
		}
		
		if (m instanceof DisServiceHelloMessage) {
			nrofHelloMessagesAborted++;
		}
		else {
			nrofAborted++;
			
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
			nrofInterfered++;
			
			Integer subID = (Integer) m.getProperty(PublisherSubscriber.SUBSCRIPTION_MESSAGE_PROPERTY_KEY);
			messageInterferedPerSubscription.put(subID, Integer.valueOf(
					messageInterferedPerSubscription.get(subID).intValue() + 1));
		}
	}

	@Override
	public void messageTransferred(Message m, DTNHost from, DTNHost to,
									boolean firstDelivery, boolean finalTarget) {
		if (isWarmupID(m.getID())) {
			return;
		}

		if (m instanceof DisServiceHelloMessage) {
			nrofHelloMessagesDelivered++;
		}
		else if (firstDelivery) {
			nrofRelayed++;
			
			Integer subID = (Integer) m.getProperty(PublisherSubscriber.SUBSCRIPTION_MESSAGE_PROPERTY_KEY);
			messageRelayedPerSubscription.put(subID,
			Integer.valueOf(messageRelayedPerSubscription.get(subID).intValue() + 1));
			
			if (finalTarget) {
				nrofDelivered++;
				firstDeliveriesPerMessage.put(m.getID(), firstDeliveriesPerMessage.get(m.getID()) + 1);
				latenciesPerSubscription.get(subID).add(getSimTime() - creationTimes.get(m.getID()));
				hopCountsPerSubscription.get(subID).add(m.getHops().size() - 1);
				
				if (m.isResponse()) {
					nrofResponseDelivered++;
					msgRTTPerSubscription.get(subID).add(getSimTime() -	m.getRequest().getCreationTime());
				}

				if (to.getRouter() instanceof PublisherSubscriber) {
					if (subID <= SubscriptionListManager.INVALID_SUB_ID) {
						throw new SimError("Message subscription ID (" + subID + ") is invalid");
					}

					PublisherSubscriber destNode = (PublisherSubscriber) to.getRouter();
					SubscriptionListManager sl = destNode.getSubscriptionList();
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
			nrofDuplicates++;
			
			Integer subID = (Integer) m.getProperty(PublisherSubscriber.SUBSCRIPTION_MESSAGE_PROPERTY_KEY);
			messageDuplicatesPerSubscription.put(subID, Integer.valueOf(
					messageDuplicatesPerSubscription.get(subID).intValue() + 1));
		}
	}

	@Override
	public void newMessage(Message m) {
		if (isWarmup()) {
			addWarmupID(m.getID());
			return;
		}

		nrofCreated++;
		creationTimes.put(m.getID(), getSimTime());
		firstDeliveriesPerMessage.put(m.getID(), 0);

		Integer subID = (Integer) m.getProperty(PublisherSubscriber.SUBSCRIPTION_MESSAGE_PROPERTY_KEY);
		nrofTotalDueDeliveries += nodesPerSubscription.get(subID);
		messageCreatedPerSubscription.put(subID, Integer.valueOf(
				messageCreatedPerSubscription.get(subID).intValue() + 1));
		
		if (m.getResponseSize() > 0) {
			nrofResponseReqCreated++;
			messageResponseCreatedPerSubscription.put(subID, Integer.valueOf(
					messageResponseCreatedPerSubscription.get(subID).intValue() + 1));
		}
	}

	@Override
	public void messageTransferStarted(Message m, DTNHost from, DTNHost to) {
		if (isWarmupID(m.getID())) {
			return;
		}
		
		if (m instanceof DisServiceHelloMessage) {
			nrofHelloMessagesStarted++;
		}
		else {
			nrofStarted++;

			Integer subID = (Integer) m.getProperty(PublisherSubscriber.SUBSCRIPTION_MESSAGE_PROPERTY_KEY);
			messageStartedPerSubscription.put(subID, Integer.valueOf(
					messageStartedPerSubscription.get(subID).intValue() + 1));
		}
	}

	@Override
	public void done() {
		double deliveryProb = 0.0;	// delivery probability
		double responseProb = 0.0;	// request-response success probability
		double overHead = 0.0;		// overhead ratio
		
		deliveryProb = (1.0 * nrofDelivered) / nrofTotalDueDeliveries;
		if (nrofResponseReqCreated > 0) {
			responseProb = (1.0 * nrofResponseDelivered) / nrofResponseReqCreated;
		}
		overHead = (1.0 * (nrofDuplicates + nrofRelayed - nrofDelivered)) / nrofDelivered;
		
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
		
		List<Double> latencies = new ArrayList<Double>();
		for(List<Double> latenciesPerSub : latenciesPerSubscription.values()) {
			latencies.addAll(latenciesPerSub);
		}
		List<Integer> hopCounts = new ArrayList<Integer>();
		for(List<Integer> hopsPerSub : hopCountsPerSubscription.values()) {
			hopCounts.addAll(hopsPerSub);
		}
		List<Double> msgBufferTime = new ArrayList<Double>();
		for(List<Double> msgBufTimePerSub : msgBufferTimePerSubscription.values()) {
			msgBufferTime.addAll(msgBufTimePerSub);
		}
		List<Double> totalRTTs = new ArrayList<Double>();
		for(List<Double> msgRTTPerSub : msgRTTPerSubscription.values()) {
			totalRTTs.addAll(msgRTTPerSub);
		}		
		
		statsText = "General results:" +
					"\ncreated: " + nrofCreated +
					"\nstarted: " + nrofStarted +
					"\nrelayed: " + nrofRelayed +
					"\nduplicates: " + nrofDuplicates +
					"\naborted: " + nrofAborted +
					"\nInterfered: " + nrofInterfered +
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
					"\nrtt_avg: " + getAverage(totalRTTs) +
					"\nrtt_med: " + getMedian(totalRTTs) + "\n\n";
		write(statsText);

		if (!nodesPerSubscription.isEmpty()) {
			int statsSize = SubscriptionListManager.MAX_SUB_ID_OF_SIMULATION;
			double deliveryProbPerSub[] = new double[statsSize];		// delivery probability
			double responseProbPerSub[] = new double[statsSize];		// request-response success probability
			double overHeadPerSub[] = new double[statsSize];			// overhead ratio
			
			for (int i = 1; i <= statsSize; i++) {
				if (messageCreatedPerSubscription.get(i).intValue() > 0) {
					deliveryProbPerSub[i - 1] = (1.0 * messageDeliveredPerSubscription.get(i).intValue()) /
													(messageCreatedPerSubscription.get(i).intValue() *
													nodesPerSubscription.get(i));
				}
				else {
					deliveryProbPerSub[i - 1] = 0;
				}
			}
			for (int i = 1; i <= statsSize; i++) {
				if (messageDeliveredPerSubscription.get(i).intValue() > 0) {
					overHeadPerSub[i - 1] = (1.0 * (messageDuplicatesPerSubscription.get(i) +
												messageRelayedPerSubscription.get(i).intValue() -
												messageDeliveredPerSubscription.get(i).intValue())) /
												messageDeliveredPerSubscription.get(i).intValue();
				}
				else {
					overHeadPerSub[i - 1] = Double.NaN;
				}
			}
			for (int i = 1; i <= statsSize; i++) {
				if (messageResponseCreatedPerSubscription.get(i).intValue() > 0) {
					responseProbPerSub[i - 1] = (1.0 * messageResponseDeliveredPerSubscription.get(i).intValue()) /
													(messageResponseCreatedPerSubscription.get(i).intValue() *
													nodesPerSubscription.get(i));
				}
			}
			write("Statistics per subscription ID:\n");
			for (int i = 1; i <= statsSize; i++) {
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
							"\ndelivery_prob: " + format(deliveryProbPerSub[i - 1]) +
							"\nresponse_prob: " + format(responseProbPerSub[i - 1]) +
							"\noverhead_ratio: " + format(overHeadPerSub[i - 1]) +
							"\n\n";
				write(statsText);
			}
		}
		
		super.done();
	}
	
}