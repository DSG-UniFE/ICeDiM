/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package report;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import com.sun.org.apache.xml.internal.security.utils.HelperNodeList;

import junit.framework.Assert;

import core.DTNHost;
import core.Message;
import core.MessageListener;
import core.disService.DisServiceHelloMessage;
import core.disService.PrioritizedMessage;
import core.disService.PublishSubscriber;
import core.disService.SubscriptionList;

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
	private List<Double> latencies[];
	private List<Integer> hopCounts[];
	private List<Double> msgBufferTime[];
	private List<Double> rtt[]; // round trip times
	
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
	private HashMap<Integer, Integer> messageDeliveredPerSubscription;
	private HashMap<Integer, Integer> messageAbortedPerSubscription;
	private HashMap<Integer, Integer> messageInterferredPerSubscription;
	
	
	/**
	 * Constructor.
	 */
	public PrioritizedMessageStatsReport() {
		init();
	}

	@Override
	protected void init() {
		super.init();
		int arraySize = PrioritizedMessage.MAX_PRIORITY - PrioritizedMessage.MIN_PRIORITY + 1;
		
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
		this.messageDeliveredPerSubscription = new HashMap<Integer, Integer>();
		this.messageAbortedPerSubscription = new HashMap<Integer, Integer>();
		this.messageInterferredPerSubscription = new HashMap<Integer, Integer>();
	}

	@Override
	public void registerNode(DTNHost node) {
		Assert.assertTrue(node instanceof PublishSubscriber);
		PublishSubscriber destNode = (PublishSubscriber) node;
		
		SubscriptionList sl = destNode.getSubscriptionList();
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
	
	public void messageDeleted(Message m, DTNHost where, boolean dropped) {
		if (isWarmupID(m.getID())) {
			return;
		}
		
		Assert.assertTrue(m instanceof PrioritizedMessage);
		PrioritizedMessage pm = (PrioritizedMessage) m;
		
		if (dropped) {
			this.nrofDropped[pm.getPriority() - PrioritizedMessage.MIN_PRIORITY]++;
		}
		else {
			this.nrofRemoved[pm.getPriority() - PrioritizedMessage.MIN_PRIORITY]++;
		}
		
		this.msgBufferTime[pm.getPriority() - PrioritizedMessage.MIN_PRIORITY].add(getSimTime() - pm.getReceiveTime());
	}

	
	public void messageTransferAborted(Message m, DTNHost from, DTNHost to) {
		if (isWarmupID(m.getID())) {
			return;
		}
		
		if (m instanceof PrioritizedMessage) {
			PrioritizedMessage pm = (PrioritizedMessage) m;
			
			nrofAborted[pm.getPriority() - PrioritizedMessage.MIN_PRIORITY]++;
			
			Integer subID = Integer.valueOf(pm.getSubscriptionID());
			if (messageAbortedPerSubscription.containsKey(subID)) {
				messageAbortedPerSubscription.put(subID,
					Integer.valueOf(messageAbortedPerSubscription.get(subID).intValue() + 1));
			}
			else {
				messageAbortedPerSubscription.put(subID, 1);
			}
		}
		else if (m instanceof DisServiceHelloMessage) {
			nrofHelloMessagesAborted++;
		}
	}

	@Override
	public void messageTransmissionInterfered(Message m, DTNHost from, DTNHost to) {
		if (m instanceof PrioritizedMessage) {
			PrioritizedMessage pm = (PrioritizedMessage) m;
		
			this.nrofInterfered[pm.getPriority() - PrioritizedMessage.MIN_PRIORITY]++;
			
			Integer subID = Integer.valueOf(pm.getSubscriptionID());
			if (messageInterferredPerSubscription.containsKey(subID)) {
				messageInterferredPerSubscription.put(subID,
					Integer.valueOf(messageInterferredPerSubscription.get(subID).intValue() + 1));
			}
			else {
				messageInterferredPerSubscription.put(subID, 1);
			}
		}
		else if (m instanceof DisServiceHelloMessage) {
			nrofHelloMessagesInterfered++;
		}
	}

	
	public void messageTransferred(Message m, DTNHost from, DTNHost to, boolean finalTarget) {
		if (isWarmupID(m.getID())) {
			return;
		}

		Assert.assertTrue(to.getRouter() instanceof PublishSubscriber);
		PublishSubscriber destNode = (PublishSubscriber) to.getRouter();
		
		if (m instanceof PrioritizedMessage) {
			PrioritizedMessage pm = (PrioritizedMessage) m;
	
			this.nrofRelayed[pm.getPriority() - PrioritizedMessage.MIN_PRIORITY]++;
			if (finalTarget) {
				this.latencies[pm.getPriority() - PrioritizedMessage.MIN_PRIORITY].add(getSimTime() - this.creationTimes.get(pm.getID()));
				this.nrofDelivered[pm.getPriority() - PrioritizedMessage.MIN_PRIORITY]++;
				this.hopCounts[pm.getPriority() - PrioritizedMessage.MIN_PRIORITY].add(pm.getHops().size() - 1);
				
				if (pm.isResponse()) {
					this.rtt[pm.getPriority() - PrioritizedMessage.MIN_PRIORITY].add(getSimTime() -	pm.getRequest().getCreationTime());
					this.nrofResponseDelivered[pm.getPriority() - PrioritizedMessage.MIN_PRIORITY]++;
				}
			}
	
			Integer subID = Integer.valueOf(pm.getSubscriptionID());
			SubscriptionList sl = destNode.getSubscriptionList();
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
		else if (m instanceof DisServiceHelloMessage) {
			nrofHelloMessagesDelivered++;
		}
	}


	public void newMessage(Message m) {
		if (isWarmup()) {
			addWarmupID(m.getID());
			return;
		}
		
		Assert.assertTrue(m instanceof PrioritizedMessage);
		PrioritizedMessage pm = (PrioritizedMessage) m;
		
		this.creationTimes.put(pm.getID(), getSimTime());
		this.nrofCreated[pm.getPriority() - PrioritizedMessage.MIN_PRIORITY]++;
		if (pm.getResponseSize() > 0) {
			this.nrofResponseReqCreated[pm.getPriority() - PrioritizedMessage.MIN_PRIORITY]++;
		}

		Integer subID = Integer.valueOf(pm.getSubscriptionID());
		if (messageCreatedPerSubscription.containsKey(subID)) {
			messageDeliveredPerSubscription.put(subID,
				Integer.valueOf(messageDeliveredPerSubscription.get(subID).intValue() + 1));
		}
		else {
			messageDeliveredPerSubscription.put(subID, 1);
		}
	}
	
	
	public void messageTransferStarted(Message m, DTNHost from, DTNHost to) {
		if (isWarmupID(m.getID())) {
			return;
		}
		
		if (m instanceof PrioritizedMessage) {
			PrioritizedMessage pm = (PrioritizedMessage) m;
			this.nrofStarted[pm.getPriority() - PrioritizedMessage.MIN_PRIORITY]++;	
		}
		else if (m instanceof DisServiceHelloMessage) {
			nrofHelloMessagesStarted++;
		}
	}
	

	@Override
	public void done() {
		int arraySize = PrioritizedMessage.MAX_PRIORITY - PrioritizedMessage.MIN_PRIORITY + 1;
		
		double deliveryProb[] = new double[arraySize];	// delivery probability
		double responseProb[] = new double[arraySize];	// request-response success probability
		double overHead[] = new double[arraySize];		// overhead ratio
		
		for (int i = 0; i < arraySize; i++) {
			if (this.nrofCreated[i] > 0) {
				deliveryProb[i] = (1.0 * this.nrofDelivered[i]) / this.nrofCreated[i];
			}
			else {
				deliveryProb[i] = 0;
			}
		}

		for (int i = 0; i < arraySize; i++) {
			if (this.nrofDelivered[i] > 0) {
				overHead[i] = (1.0 * (this.nrofRelayed[i] - this.nrofDelivered[i])) / this.nrofDelivered[i];
			}
			else {
				overHead[i] = Double.NaN;
			}
		}

		for (int i = 0; i < arraySize; i++) {
			if (this.nrofResponseReqCreated[i] > 0) {
				responseProb[i] = (1.0 * this.nrofResponseDelivered[i]) / this.nrofResponseReqCreated[i];
			}
		}
		
		/* printing stats */
		write("Message stats for scenario " + getScenarioName() + "\nsim_time: " + format(getSimTime()));
		String statsText = "\nHMSent: " + this.nrofHelloMessagesStarted + 
				"\nHMDelivered : " + this.nrofHelloMessagesDelivered + 
				"\nHMAborted: " + this.nrofHelloMessagesAborted + 
				"\nHMInterfered: " + this.nrofHelloMessagesInterfered;
		write(statsText);
		
		for (int i = 0; i < arraySize; i++) {
			statsText = "Priority Level: " + (i + PrioritizedMessage.MIN_PRIORITY) +
				"\ncreated: " + this.nrofCreated[i] + 
				"\nstarted: " + this.nrofStarted[i] + 
				"\nrelayed: " + this.nrofRelayed[i] +
				"\naborted: " + this.nrofAborted[i] +
				"\nInterfered: " + this.nrofInterfered[i] +
				"\ndropped: " + this.nrofDropped[i] +
				"\nremoved: " + this.nrofRemoved[i] +
				"\ndelivered: " + this.nrofDelivered[i] +
				"\ndelivery_prob: " + format(deliveryProb[i]) +
				"\nresponse_prob: " + format(responseProb[i]) + 
				"\noverhead_ratio: " + format(overHead[i]) + 
				"\nlatency_avg: " + getAverage(this.latencies[i]) +
				"\nlatency_med: " + getMedian(this.latencies[i]) + 
				"\nhopcount_avg: " + getIntAverage(this.hopCounts[i]) +
				"\nhopcount_med: " + getIntMedian(this.hopCounts[i]) + 
				"\nbuffertime_avg: " + getAverage(this.msgBufferTime[i]) +
				"\nbuffertime_med: " + getMedian(this.msgBufferTime[i]) +
				"\nrtt_avg: " + getAverage(this.rtt[i]) +
				"\nrtt_med: " + getMedian(this.rtt[i]) + "\n\n";			
			write(statsText);
		}
		super.done();
	}
	
}
