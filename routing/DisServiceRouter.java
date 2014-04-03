package routing;

import java.util.ArrayList;
import java.util.List;

import com.sun.xml.internal.messaging.saaj.packaging.mime.internet.ParseException;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.MessageListener;
import core.NetworkInterface;
import core.Settings;
import core.SimClock;
import core.SimError;
import core.disService.DisServiceHelloMessage;
import core.disService.NeighborInfo;
import core.disService.PublisherSubscriber;
import core.disService.SubscriptionListManager;
import core.disService.WorldState;

public class DisServiceRouter extends BroadcastEnabledRouter implements PublisherSubscriber {
	/** Seconds between the broadcast of two subsequent HELLO Messages. */
	public static final String PING_INTERVAL_PERIOD = "pingInterval";
	
	/** The default interval (in seconds) between two HELLO Messages */
	private static final double DEFAULT_PING_INTERVAL = 5.0;
	
	private DisServiceHelloMessage helloMessage;
	private final double pingInterval;
	protected double lastPingSentTime[];
	
	protected ArrayList<String> receivedMsgIDs;
	protected SubscriptionListManager nodeSubscriptions;
	protected WorldState worldState;
	
	protected HelloMessageGen hmGenerator;

	public DisServiceRouter(Settings s) {
		super(s);

		this.helloMessage = null;
		if (s.contains(PING_INTERVAL_PERIOD)) {
			this.pingInterval = s.getDouble(PING_INTERVAL_PERIOD);
		}
		else {
			this.pingInterval = DEFAULT_PING_INTERVAL;
		}
		this.lastPingSentTime = null;	// It will be allocated in the init() method
		
		this.receivedMsgIDs = new ArrayList<String>();
		try {
			this.nodeSubscriptions = new SubscriptionListManager(s);
		} catch (ParseException e) {
			throw new SimError("Error parsing configuration file");
		}
		
		this.worldState = new WorldState(this.getHost(), s);
		this.hmGenerator = new HelloMessageGen(this.receivedMsgIDs, this.nodeSubscriptions);
	}

	public DisServiceRouter(DisServiceRouter r) {
		super(r);

		this.helloMessage = null;
		this.pingInterval = r.pingInterval;
		this.lastPingSentTime = null;	// It will be allocated in the init() method

		this.receivedMsgIDs = new ArrayList<String>();
		this.nodeSubscriptions = r.nodeSubscriptions.replicate();
		
		this.worldState = new WorldState(r.getHost(), r.worldState);
		this.hmGenerator = new HelloMessageGen(this.receivedMsgIDs, this.nodeSubscriptions);
	}
	
	@Override
	public void init(DTNHost host, List<MessageListener> mListeners) {
		super.init(host, mListeners);
		
		this.lastPingSentTime = new double [this.getHost().getInterfaces().size()];
		for (int i = 0; i < this.lastPingSentTime.length; ++i) {
			this.lastPingSentTime[i] = 0.0;
		}
		
		this.hmGenerator.init(getHost());
	}

	@Override
	public void update() {
		super.update();
		
		// Send a ping via all the interfaces which need to send it
		broadcastHelloMessageToNeighbors();

		// Forward data messages to neighbors
		forwardDataMessagesToNeighbors();
	}

	/**
	 * Tries to broadcast the data messages in the configured order
	 * considering active neighbor nodes and their subscriptions.
	 * @throws SimError
	 */
	private void forwardDataMessagesToNeighbors() throws SimError {
		NetworkInterface ni = getIdleInterface();
		
		while (ni != null) {
			/* Retrieve reachable nodes
			 * Retrieve subscriptions and messages for these nodes
			 * Check if we have some messages which should be transferred
			 * Transfer most requested message or the one less forwarded
			 */
			List<Message> sortedMessageList = sortListOfMessages(
												new ArrayList<Message>(getMessageCollection()));
			List<NeighborInfo> nearbyNodes = worldState.getActiveNeighborInfosByNetworkInterface(ni);
			
			if ((nearbyNodes.size() == 0) || (sortedMessageList.size() == 0)) {
				// Nothing to do
				return;
			}
			
			/* Check if any reachable neighbor is interested in any message
			 * If yes, we try to broadcast the message through the selected interface
			 */
		nextSearch:
			for (Message pm : sortedMessageList) {
				for (NeighborInfo neighborInfo : nearbyNodes) {
					Integer subID = (Integer) pm.getProperty(SUBSCRIPTION_MESSAGE_PROPERTY_KEY);
					if (neighborInfo.getSubscriptionList().containsSubscriptionID(subID) &&
						!neighborInfo.getReceivedMessagesList().contains(pm.getID())) {
						if (BROADCAST_OK != tryBroadcastOneMessage (pm, ni)) {
							throw new SimError("Impossible transmit message " + pm +
												" via Network Interface" + ni);
						}
						break nextSearch;
					}
				}
			}
			
			NetworkInterface newNI = getIdleInterface();
			if (ni != newNI) {
				ni = newNI;
			}
			else {
				// then try broadcasting messages in queue order
				Message pm = sortedMessageList.get(0);
				if (BROADCAST_OK != tryBroadcastOneMessage (pm, ni)) {
					throw new SimError("Impossible transmit message " + pm +
										" via Network Interface" + ni);
				}
				ni = getIdleInterface();
			}
		}
	}

	/**
	 * Sends HelloMessages through all the Network Interfaces
	 * which can transmit and which sent the last message
	 * more than pingInterval seconds ago
	 * @throws SimError
	 */
	private void broadcastHelloMessageToNeighbors() throws SimError {
		for (int i = 0; i < getHost().getInterfaces().size(); ++i) {
			NetworkInterface ni = getHost().getInterfaces().get(i);
			if (ni.isReadyToBeginTransfer()) {
				if ((SimClock.getTime() - lastPingSentTime[i]) >= pingInterval) {
					if (BROADCAST_OK == broadcastHelloMessage(ni)) {
						lastPingSentTime[i] = SimClock.getTime();
					}
					else {
						throw new SimError("Failed to send HELLO Message");
					}
				}
			}
		}
	}
	
	/**
	 * Tries to send all messages that this router is carrying to all
	 * connections this node has. Messages are ordered using the 
	 * {@link MessageRouter#sortByQueueMode(List)}. See 
	 * {@link #tryBroadcastOneMessage(List, List)} for sending details.
	 * @return The connections that started a transfer or null if no connection
	 * accepted a message.
	 */
	protected int broadcastHelloMessage(NetworkInterface ni) {
		if ((ni == null) || (ni.getConnections().size() == 0)) {
			return BROADCAST_DENIED;
		}
		
		if (helloMessage == null) {
			helloMessage = hmGenerator.buildHelloMsg();
		}
		if (BROADCAST_OK == tryBroadcastOneMessage(helloMessage, ni)) {
			helloMessage = null;
		}
		
		return BROADCAST_OK;
	}

	@Override
	public boolean createNewMessage(Message m) {
		receivedMsgIDs.add(m.getID());
		return super.createNewMessage(m);
	}
	
	@Override
	public int receiveMessage(Message m, Connection con) {
		int recvCheck = checkReceiving(m, con);
		if (recvCheck != RCV_OK) {
			return recvCheck;
		}
		
		return super.receiveMessage(m, con);
	}
	
	/**
	 * This method should be called (on the receiving host) after a message
	 * was successfully transferred. The transferred message is put to the
	 * message buffer unless this host is the final recipient of the message.
	 * @param id Id of the transferred message
	 * @param from Host the message was from (previous hop)
	 * @return The message that this host received
	 */
	@Override
	public Message messageTransferred(String id, Connection con) {
		Message m = super.messageTransferred(id, con);
		
		if (m instanceof DisServiceHelloMessage) {
			// Process DisService HELLO message
			DisServiceHelloMessage receivedHelloMessage = (DisServiceHelloMessage) m;
			worldState.processHelloMessage(receivedHelloMessage);
			removeFromMessages(id);
		}
		else if (m != null) {
			receivedMsgIDs.add(m.getID());
		}
		
		return m;
	}

	/**
	 * Returns a list of subscriptions this host has.
	 * @return a list of subscription IDs this host has
	 */
	protected List<Integer> getSubscriptionIDs() {
		return this.nodeSubscriptions.getSubscriptionList();
	}
	
	/**
	 * Checks if router can start receiving message (i.e. router 
	 * isn't transferring, doesn't have the message and has room for it).
	 * Note that the result of this method should not be stop the transmission
	 * (that is, the decision of sending is made at sender side), but
	 * different router implementations could still decide what to do
	 * with the data which is being transmitted.
	 * @param m The message to check
	 * @return A return code similar to 
	 * {@link MessageRouter#receiveMessage(Message, DTNHost)}, i.e. 
	 * {@link MessageRouter#RCV_OK} if receiving seems to be OK, 
	 * TRY_LATER_BUSY if router is transferring, DENIED_OLD if the router
	 * is already carrying the message or it has been delivered to
	 * this router (as final recipient), or DENIED_NO_SPACE if the message
	 * does not fit into buffer
	 */
	@Override
	protected int checkReceiving(Message m, Connection con) {
		int retVal = super.checkReceiving(m, con);
		if (retVal != RCV_OK) {
			return retVal;
		}

		return RCV_OK;
	}
	
	/**
	 * Drops messages whose TTL is less than zero.
	 */
	@Override
	protected void dropExpiredMessages() {
		for (Message m : getMessageCollection()) {
			if (m.getTtl() <= 0) {
				if (!receivedMsgIDs.remove(m.getID())) {
					throw new SimError("Impossible to find message " +
							m.getID() + " among receivedMsgIDs");
				}
			}
		}
		super.dropExpiredMessages();
	}
	
	@Override
	public DisServiceRouter replicate() {
		return new DisServiceRouter(this);
	}

	public double getPingInterval() {
		return pingInterval;
	}

	@Override
	public SubscriptionListManager getSubscriptionList() {
		return nodeSubscriptions;
	}

	@Override
	public int generateRandomSubID() {
		return nodeSubscriptions.getRandomSubscriptionFromList();
	}

}


class HelloMessageGen {

	private DTNHost node;
	private ArrayList<String> receivedMsgIDs;
	private SubscriptionListManager nodeSubscriptions;
	
	private int helloMsgIDCounter;
	
	static final int SourceAndIDSize = 8; // 8 bytes --> 2 * 32bit integers	
	
	public HelloMessageGen(ArrayList<String> receivedMsgIDs, SubscriptionListManager nodeSubscriptions) {
		this.node = null;
		this.receivedMsgIDs = receivedMsgIDs;
		this.nodeSubscriptions = nodeSubscriptions;
		
		this.helloMsgIDCounter = 0;
	}
	
	public void init(DTNHost node) {
		this.node = node;
	}
	
	public DisServiceHelloMessage buildHelloMsg() {
		return new DisServiceHelloMessage (node, getHelloMsgID(), getHelloMsgSize(), receivedMsgIDs,
											nodeSubscriptions.getSubscriptionList());
	}
	
	private int getHelloMsgSize() {
		int subscriptionListSize = 4 * nodeSubscriptions.getSubscriptionList().size();
		int receivedMsgListSize = 4 * receivedMsgIDs.size();
		
		// 2 bytes to represent message length
		return SourceAndIDSize + subscriptionListSize + receivedMsgListSize + 2;
	}
	
	private String getHelloMsgID() {
		return "HM_" + node + "_" +  String.format("%04d", helloMsgIDCounter++);
	}
}
