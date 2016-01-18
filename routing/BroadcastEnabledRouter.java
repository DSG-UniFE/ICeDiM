package routing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import core.Connection;
import core.DTNHost;
import core.InterferenceModel;
import core.Message;
import core.MessageListener;
import core.NetworkInterface;
import core.Settings;
import core.SimClock;
import core.SimError;

public class BroadcastEnabledRouter extends MessageRouter {
	
	/** Delete delivered messages -setting id ({@value}). Boolean valued.
	 * If set to true and final recipient of a message rejects it because it
	 * already has it, the message is deleted from buffer. Default=false. */
	public static final String DELETE_DELIVERED_S = "deleteDelivered";
	
	/** should messages that final recipient marks as delivered be deleted
	 * from message buffer */
	protected boolean deleteDelivered;
	
	/** prefix of all response message IDs */
	public static final String RESPONSE_PREFIX = "R_";
	/** how often TTL check (discarding old messages) is performed */
	public static int TTL_CHECK_INTERVAL = 60;
	/** simulation time when the last TTL check was done */
	private double lastTtlCheck;


	public BroadcastEnabledRouter(Settings s) {
		super(s);
		
		if (s.contains(DELETE_DELIVERED_S))	{
			this.deleteDelivered = s.getBoolean(DELETE_DELIVERED_S);
		}
		else {
			this.deleteDelivered = false;
		}
	}

	public BroadcastEnabledRouter(BroadcastEnabledRouter r) {
		super(r);
		
		this.deleteDelivered = r.deleteDelivered;
	}
	
	@Override
	public MessageRouter replicate() {
		return new BroadcastEnabledRouter(this);
	}

	@Override
	public void init(DTNHost host, List<MessageListener> mListeners) {
		super.init(host, mListeners);
		this.lastTtlCheck = 0;
	}

	/**
	 * Called when a connection's state changes. This version doesn't do 
	 * anything but subclasses may want to override this.
	 */
	@Override
	public void changedConnection(Connection con) { }


	/**
	 * Returns a list of those messages whose recipient(s)
	 * is/are among the neighboring nodes.
	 * @return a List of messages to be delivered to the
	 * nodes under connection range.
	 */
	protected List<Message> getDeliverableMessages() {
		if (getNrofMessages() == 0) {
			/* no messages -> empty list */
			return new ArrayList<Message>(0); 
		}

		List<Message> messageList = new ArrayList<Message>();
		for (Message m : getMessageList()) {
			if (isMessageDestinationReachable(m)) {
				messageList.add(m);
			}
		}
		
		return messageList;
	}

	/**
	 * Returns a list of those messages whose recipient(s)
	 * is/are among the neighboring nodes.
	 * @return a List of messages to be delivered to the
	 * nodes under connection range.
	 */
	protected List<Message> getDeliverableMessagesForNetworkInterface(NetworkInterface ni) {
		if (getNrofMessages() == 0) {
			/* no messages -> empty list */
			return new ArrayList<Message>(0);
		}

		List<Message> messageList = new ArrayList<Message>();
		for (Message m : getMessageList()) {
			if (isMessageDestinationReachableThroughInterface(m, ni)) {
				messageList.add(m);
			}
		}
		
		return messageList;
	}

	/**
	 * Checks if the destination of the specified {@link Message}
	 * is among this node's neighbors.
	 * @param m the {@link Message} to check.
	 * @return {@code true} if the destination belongs to the set
	 * of this node's neighbors, or {@code false} otherwise.
	 */
	protected boolean isMessageDestinationReachable(Message m) {
		for (NetworkInterface ni : getHost().getInterfaces()) {
			if (isMessageDestinationReachableThroughInterface(m, ni)) {
				return true;
			}
		}
		
		return false;
	}


	/**
	 * Checks if the destination of the specified {@link Message}
	 * is among the neighbors reachable through the specified
	 * {@link NetworkInterface}.
	 * @param m the {@link Message} to check.
	 * @param ni the {@link NetworkInterface} that determines the
	 * neighbors to consider as possible message destinations.
	 * @return {@code true} if the destination belongs to the set
	 * of neighbors reachable through the specified
	 * {@link NetworkInterface}, or {@code false} otherwise.
	 */
	protected boolean isMessageDestinationReachableThroughInterface(Message m,
																	NetworkInterface ni) {
		for (Connection con : ni.getConnections()) {
			if (isMessageDestination(m, con.getOtherNode(getHost()))) {
				return true;
			}
		}
		
		return false;
	}

	/**
	 * The method is called just before a transfer is finalized 
	 * at {@link #update()}. Subclasses that are interested in
	 * the event may want to override this.
	 * @param con The connection whose transfer was finalized
	 */
	protected void transferDone(Connection con) {
		// Notify listeners of the completed message transmission
		notifyListenersAboutTransmissionCompleted(con.getMessage());
	}
	
	/**
	 * The method is called after a {@link NetworkInterface} completed
	 * its transfers. Subclasses that are interested in the event may
	 * want to override this.
	 * @param transferredMessages A {@link Set} containing all
	 * {@link Message}s transferred successfully.
	 * @param transferringConnections A {@link Set} containing all
	 * {@link Connection}s that completed their transfer successfully.
	 */
	protected void transferDone(Set<Message> transferredMessages,
								Set<Connection> transferringConnections) {
		// Notify listeners of completed message transmissions
		for (Message m : transferredMessages) {
			notifyListenersAboutTransmissionCompleted(m);
		}
	}

	/**
	 * The method is called just before a transfer is aborted at {@link #update()} 
	 * due connection going down. This happens on the sending host. 
	 * Subclasses that are interested in the event may want to override this. 
	 * @param con The connection whose transfer was aborted
	 */
	protected void transferAborted(Connection con) { }

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
			
		/**
		 *  N.B. With application support the following if-block
		 *  becomes obsolete, and the response size should be configured 
		 *  to zero.
		 */
		// Check if the Message was for this host and a response was requested
		if (isMessageDestination(m) && (m.getResponseSize() > 0)) {
			// Generate a response message with same priority as the request
			Message res = new Message(getHost(),m.getFrom(), RESPONSE_PREFIX + m.getID(),
										m.getResponseSize(), m.getPriority());
			res.copyPropertiesFrom(m);
			if (createNewMessage(res)) {
				getMessage(RESPONSE_PREFIX + m.getID()).setRequest(m);
			}
		}
		
		return m;
	}

	/**
	 * Requests for deliverable message from this host to be sent
	 * through a connection. This method is called by a remote host.
	 * @param con The connection to send the messages through
	 * @return True if this host started a transfer, false if not
	 */
	@Override
	public Message requestDeliverableMessages(Connection con) {
		// Get a reference to the local NetworkInterface and check if it is busy
		NetworkInterface requestingInterface = con.getInterfaceForNode(getHost());
		if (requestingInterface == null) {
			throw new SimError("Connection " + con + " does not involve local host " + getHost());
		}
		if (!requestingInterface.isReadyToBeginTransfer()) {
			return null;
		}
	
		/* getDeliverableMessagesForNetworkInterface() returns a copy of all
		 * and only those messages which can be delivered to their final
		 * recipients through the specified NetworkInterface */
		List<Message> sortedMessageList = sortListOfMessagesForForwarding(
				getDeliverableMessagesForNetworkInterface(requestingInterface));
		for (Message m : sortedMessageList) {
			if (shouldDeliverMessageToNeighbors(m, requestingInterface) &&
				tryBroadcastOneMessage(m, requestingInterface) == BROADCAST_OK) {
				return m;
			}
		}
		
		return null;
	}

	/**
	 * Checks out all sending connections to finalize the ready ones 
	 * and abort those whose connection went down. Also drops messages
	 * whose TTL <= 0 (checking every one simulated minute).
	 * @see #addToSendingConnections(Connection)
	 */
	@Override
	public void update() {
		super.update();
	
		/*
		 * The method cycles through all the connections for each interface, and removes the
		 * ones that terminated the transfer from the sendingConnections list.
		 * If no connection is still sending, and the buffer is holding an excessive amount
		 * of bytes, the method proceeds deleting some cached messages to free up some space.
		 * If all connections completed a message transfer, then the number of times that
		 * message has been forwarded is incremented.
		 */
		boolean freeBuffer = true;
		for (NetworkInterface ni : getHost().getInterfaces()) {
			if (ni.isSendingData()) {
				HashSet<Message> transferredMessages = new HashSet<Message>();
				HashSet<Connection> transferringConnections = new HashSet<Connection>();
				for (int i = 0; i < ni.getConnections().size(); ++i) {
					Connection con = ni.getConnections().get(i);
					if (con.isIdle()) {
						// Not sending anything on this connection..
						continue;
					}
					
					/* finalize ready transfers */
					if (con.isMessageTransferred()) {
						if (con.getSenderNode() == getHost()) {
							// We are senders
							transferredMessages.add(con.getMessage());
							transferringConnections.add(con);
						}
						else {
							// We are receivers --> verify interference since we are also sending!!
							String msgID = con.getMessage().getID();
							int retVal = con.getReceiverInterface().
												isMessageTransferredCorrectly(msgID, con);
							if (retVal != InterferenceModel.RECEPTION_OUT_OF_SYNCH) {
								throw new SimError("Out-of-synch is the only condition available " +
													"here, but " + retVal + " was detected instead");
							}
							// Leave connection cleanup to the senders
						}
					}
					/* remove connections that have gone down */
					else if (!con.isUp()) {
						transferAborted(con);
						con.abortTransfer("connection went down");
					}
					else {
						/* one transfer is not done, yet. Do not remove any messages */
						freeBuffer = false;
					}
				}
				
				// Check if the network interface finished transferring message(s)
				if (transferredMessages.size() > 0) {
					transferDone(transferredMessages, transferringConnections);
					
					// Finalize all transfers and signal the event to the Router
					for (Connection con : transferringConnections) { 
						transferDone(con);
						con.finalizeTransfer();
					}
				}
			}
		}
		
		if (freeBuffer && (getFreeBufferSize() < 0)) {
			// if the message being sent was holding excessive buffer, free it
			makeRoomForMessage(0, Message.MAX_PRIORITY_LEVEL);
		}
	
		// time to do a TTL check to drop old messages, if nothing is being sent out
		for (NetworkInterface ni : getHost().getInterfaces()) {
			if (ni.isSendingData()) {
				return;
			}
		}
		if ((SimClock.getTime() - lastTtlCheck) >= TTL_CHECK_INTERVAL) {
			removeExpiredMessagesFromBuffer();
			lastTtlCheck = SimClock.getTime();
		}
	}

	/**
	 * Returns whether the specified {@link Message} needs to be delivered
	 * to at least one node in the set of neighbors of this {@link DTNHost}.
	 * Subclasses can overwrite this method, if necessary.
	 * @param m The {@link Message} that needs to be delivered.
	 * @return {@code true} if at least one node among the neighbors
	 * needs the {@link Message}, or {@code false} otherwise.
	 */
	protected boolean shouldDeliverMessageToNeighbors(Message m) {
		for (NetworkInterface ni : getHost().getInterfaces()) {
			if (shouldDeliverMessageToNeighbors(m, ni)) {
				return true;
			}
		}
		
		return false;
	}

	/**
	 * Returns whether the specified {@link Message} needs to be
	 * delivered to at least one node in the neighbors reachable
	 * through the selected {@link NetworkInterface}. Subclasses
	 * can overwrite this method, if necessary.
	 * @param m The {@link Message} that needs to be delivered.
	 * @param to The {@link NetworkInterface} that identifies the set
	 * of neighbors to which the specified Message might be delivered.
	 * @return {@code true} if at least one node among the neighbors
	 * reachable through the specified {@link NetworkInterface}
	 * needs the {@link Message}, or {@code false} otherwise.
	 */
	protected boolean shouldDeliverMessageToNeighbors(Message m, NetworkInterface ni) {
		DTNHost thisHost = getHost();
		for (Connection con : ni.getConnections()) {
			if (shouldDeliverMessageToHost(m, con.getOtherNode(thisHost))) {
				return true;
			}
		}
		
		return false;
	}

	/**
	 * Tries to deliver messages to their final recipient if they belong
	 * to the set of hosts this node is currently connected to.
	 * First all messages from this host are checked and then all other
	 * hosts are asked for messages to this host. If a transfer is
	 * started, the search ends.
	 * @return the {@link NetworkInterface} that started the transfer, if
	 * one was started, or {@code null} otherwise.
	 */
	protected NetworkInterface exchangeDeliverableMessages() {
		List<NetworkInterface> networkInterfaces = getIdleNetworkInterfaces();
		Collections.shuffle(networkInterfaces, RANDOM_GENERATOR);
		
		for (NetworkInterface ni : networkInterfaces) {
			List<Message> deliverableMessages = sortListOfMessagesForForwarding(
												getDeliverableMessagesForNetworkInterface(ni));
			for (Message m : deliverableMessages) {
				if (shouldDeliverMessageToNeighbors(m, ni) &&
					tryBroadcastOneMessage(m, ni) == BROADCAST_OK) {
					// Transfer using broadcast started
					return ni;
				}
			}
		}
		
		// Didn't start transfer to any node -> ask for messages to connected peers
		for (NetworkInterface ni : networkInterfaces) {
			List<Connection> connections = ni.getConnections();
			Collections.shuffle(connections, RANDOM_GENERATOR);
			for (Connection con : connections) {
				if (con.getOtherNode(getHost()).requestDeliverableMessages(con) != null) {
					return ni;
				}
			}
		}
		
		return null;
	}

	/**
	 * Tries to start a transfer of message using a connection. If starting
	 * succeeds, the connection is added to the watch list of active connections
	 * @param m The message to transfer
	 * @param con The connection to use
	 * @return the value returned by 
	 * {@link Connection#startTransfer(DTNHost, Message)}
	 */
	protected int startUnicastTransfer(Message m, Connection con) {
		if (!con.getInterfaceForNode(getHost()).isReadyToBeginTransfer()) {
			return TRY_LATER_BUSY;
		}
		
		int retVal = con.startTransfer(getHost(), m);
		if (deleteDelivered && (retVal == DENIED_OLD) && 
			(m.getTo() == con.getOtherNode(getHost()))) {
			/* final recipient has already received the msg -> delete it */
			deleteMessage(m.getID(), MessageDropMode.REMOVED,
							"message had already been delivered");
		}
		
		return retVal;
	}

	/**
	 * Tries to send the given message to all connections,
	 * to perform a broadcast.
	 * @param m The message to broadcast.
	 * @param connections The list of Connections to try.
	 * @return {@code BROADCAST_OK} if the broadcast attempt
	 * went fine, an error otherwise.
	 */
	protected int tryBroadcastOneMessage(Message m, NetworkInterface ni) {
		int retVal = ni.sendBroadcastMessage(m);
		if (retVal == NetworkInterface.BROADCAST_OK) {
			m.incrementForwardTimes();
		}
		
		return retVal;
	}
}
