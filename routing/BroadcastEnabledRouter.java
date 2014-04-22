package routing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import core.Connection;
import core.DTNHost;
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
	/** sim time when the last TTL check was done */
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
	public void init(DTNHost host, List<MessageListener> mListeners) {
		super.init(host, mListeners);
		this.lastTtlCheck = 0;
	}

	@Override
	public MessageRouter replicate() {
		return new BroadcastEnabledRouter(this);
	}	
	
	/**
	 * Called when a connection's state changes. This version doesn't do 
	 * anything but subclasses may want to override this.
	 */
	@Override
	public void changedConnection(Connection con) { }


	/**
	 * Requests for deliverable message from this host to be sent
	 * through a connection. This method is called by a remote host.
	 * @param con The connection to send the messages through
	 * @return True if this host started a transfer, false if not
	 */
	@Override
	public boolean requestDeliverableMessages(Connection con) {
		NetworkInterface requestingInterface = con.getInterfaceForNode(getHost());
		if (requestingInterface == null) {
			throw new SimError("Connection " + con + " does not involve local host " + getHost());
		}
		if (!requestingInterface.isReadyToBeginTransfer()) {
			return false;
		}

		/* getDeliverableMessages() returns a copy of only those messages
		 * deliverable to their final recipient */
		List<Message> sortedMessageList = sortListOfMessagesForForwarding(getDeliverableMessages());
		for (Message m : sortedMessageList) {
			if (tryBroadcastOneMessage(m, requestingInterface) == RCV_OK) {
				return true;
			}
		}
		
		return false;
	}
	
	public boolean createNewMessage(Message m) {
		if (makeRoomForNewMessage(m.getSize(), m.getPriority())) {
			return super.createNewMessage(m);
		}
		else {
			return false;
		}
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
			
		/**
		 *  N.B. With application support the following if-block
		 *  becomes obsolete, and the response size should be configured 
		 *  to zero.
		 */
		// check if msg was for this host and a response was requested
		if ((m != null) && (m.getTo() == getHost()) &&
			(m.getResponseSize() > 0)) {
			// generate a response message with same priority and suscription as the request
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
	 * Removes messages from the buffer (oldest and lowest priority first)
	 * until there's enough space for the new message.
	 * If admissible message removals are not enough to free required space,
	 * all performed deletes are rolled back.
	 * @param size Size of the new message transferred, the
	 * transfer is aborted before message is removed
	 * @param priority Priority level of the new message
	 * @return True if enough space could be freed, false if not
	 */
	protected boolean makeRoomForMessage(int size, int priority) {
		if (size > getBufferSize()) {
			// message too big for the buffer
			return false;
		}
		
		int freeBuffer = this.getFreeBufferSize();
		ArrayList<Message> deletedMessages = new ArrayList<Message>();
		/* delete messages from the buffer until there's enough space */
		while (freeBuffer < size) {
			// can't remove messages being sent --> use true as parameter
			Message m = getLeastImportantMessageInQueue(true);
			if ((m == null) || (m.getPriority() > priority)) {
				// can't remove any more messages
				break;
			}
			
			// delete message from the buffer as "drop"
			deleteMessageWithoutRaisingEvents(m.getID());
			deletedMessages.add(m);
			freeBuffer += m.getSize();
		}
		
		/* notify message drops only if necessary amount of space was freed */
		if (freeBuffer < size) {
			// rollback deletes and return false
			for (Message m : deletedMessages) {
				addToMessages(m, false);
			}
			return false;
		}

		// commit deletes by notifying event listeners about the deletes
		for (Message m : deletedMessages) {
			// true identifies dropped messages
			notifyListenersAboutMessageDelete(m, true, "Buffer size exceeded");
		}
		return true;
	}
	
	/**
	 * Tries to make room for a new message. Current implementation simply
	 * calls {@link #makeRoomForMessage(int)} and ignores the return value.
	 * Therefore, if the message can't fit into buffer, the buffer is only 
	 * cleared from messages that are not being sent.
	 * @param size Size of the new message
	 * @param priority Priority level of the new message
	 */
	protected boolean makeRoomForNewMessage(int size, int priority) {
		return makeRoomForMessage(size, priority);
	}
	
	/**
	 * Returns the oldest (by receive time) message in the message buffer 
	 * (that is not being sent if excludeMsgBeingSent is true).
	 * @param excludeMsgBeingSent If true, excludes message(s) that are
	 * being sent from the oldest message check (i.e. if oldest message is
	 * being sent, the second oldest message is returned)
	 * @return The oldest message or null if no message could be returned
	 * (no messages in buffer or all messages in buffer are being sent and
	 * exludeMsgBeingSent is true)
	 */
	protected Message getLeastImportantMessageInQueue(boolean excludeMsgBeingSent) {		
		List<Message> sortedList = getListOfMessagesInReversePriorityOrder(
									new ArrayList<Message>(getMessageCollection()));
		
		// Traverse the list in order and return the first available message
		for (Message m : sortedList) {
			if (excludeMsgBeingSent && isSending(m.getID())) {
				// skip the message(s) that router is sending
				continue;
			}
			return m;
		}
		
		return null;
	}
	
	/**
	 * Returns a list of connections this host currently has with other hosts.
	 * @return a list of connections this host currently has with other hosts
	 */
	protected List<Connection> getConnections() {
		return getHost().getConnections();
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
		if (!con.isReadyForTransfer() ||
			!con.getInterfaceForNode(getHost()).isReadyToBeginTransfer()) {
			return TRY_LATER_BUSY;
		}
		
		int retVal = con.startTransfer(getHost(), m);
		if (deleteDelivered && (retVal == DENIED_OLD) && 
			(m.getTo() == con.getOtherNode(getHost()))) {
			/* final recipient has already received the msg -> delete it */
			deleteMessage(m.getID(), false, "message already delivered");
		}
		
		return retVal;
	}
	
	/**
	 * Makes rudimentary checks (that we have at least one message and one
	 * connection) about can this router start transfer.
	 * @return True if router can start transfer, false if not
	 */
	protected boolean canStartTransfer() {
		if (getNrofMessages() == 0) {
			return false;
		}
		if (getConnections().size() == 0) {
			return false;
		}
		
		// Check if at least one interface can do a broadcast
		for (NetworkInterface ni : getHost().getInterfaces()) {
			if (ni.isReadyToBeginTransfer()) {
				return true;
			}
		}
		
		return false;
	}
	
	/**
	 * Returns a list of those messages whose recipient 
	 * is among the neighboring nodes.
	 * @return a List of messages to be delivered to the
	 * nodes under connection range.
	 */
	protected List<Message> getDeliverableMessages() {
		if (getNrofMessages() == 0) {
			/* no messages -> empty list */
			return new ArrayList<Message>(0); 
		}

		List<Message> messageList = new ArrayList<Message>();
		for (Message m : getMessageCollection()) {
			if (isNeighbouringHost(m.getTo())) {
				messageList.add(m);
			}
		}
		
		return messageList;
	}
	
	/**
	 * Returns a list of those messages whose recipient 
	 * is among the neighboring nodes.
	 * @return a List of messages to be delivered to the
	 * nodes under connection range.
	 */
	protected List<Message> getDeliverableMessagesForNetworkInterface(NetworkInterface ni) {
		if (getNrofMessages() == 0) {
			/* no messages -> empty list */
			return new ArrayList<Message>(0);
		}

		List<Message> messageList = new ArrayList<Message>();
		for (Message m : getMessageCollection()) {
			if (isNeighbouringHost(ni, m.getTo())) {
				messageList.add(m);
			}
		}
		
		return messageList;
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

	/**
	 * Returns the first idle interface available.
	 * @return a {@link NetworkInterface} ready to begin
	 * a new transfer.
	 */
	protected NetworkInterface getIdleInterface() {
		for (NetworkInterface ni : getHost().getInterfaces()) {
			if (ni.isReadyToBeginTransfer()) {
				return ni;
			}
		}
		
		return null;
	}
	
	/**
	 * Returns true if this router is currently sending the
	 * message with ID {@code msgID}.
	 * @param msgID The ID of the message.
	 * @return {@code true} if the message is being sent,
	 * {@code false} otherwise.
	 */
	protected boolean isSending(String msgID) {
		for (Connection con : getConnections()) {
			if (con.isSendingMessage(getHost(), msgID)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Check if there is outgoing traffic from this node.
	 * @return {@code true} if a {@link NetworkInterface} is
	 * sending any data, {@code false} otherwise.
	 */	
	public boolean isTransferring() {
		for (NetworkInterface ni : getHost().getInterfaces()) {
			if (ni.isSendingData()) {
				return true;
			}
		}
		
		return false;
	}
	
	/**
	 * Returns true if the specified {@link NetworkInterface} is
	 * transferring something at the moment or some transfer
	 * has not been finalized, yet.
	 * @param a {@link NetworkInterface} belonging to this host
	 * @return true if this router is transferring something
	 */
	public boolean isTransferring(NetworkInterface ni) {
		if (!getHost().getInterfaces().contains(ni)) {
			throw new SimError("The NetworkInterface " + ni + " does not " +
								"belong to the local host " + getHost());
		}
		
		if (ni.isSendingData()) {
			return true;
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
		List<NetworkInterface> networkInterfaces = getHost().getInterfaces();
		Collections.shuffle(networkInterfaces);
		
		for (NetworkInterface ni : networkInterfaces) {
			if (!ni.isReadyToBeginTransfer()) {
				// Skip busy interfaces
				continue;
			}
			List<Message> deliverableMessages = sortListOfMessagesForForwarding(
												getDeliverableMessagesForNetworkInterface(ni));
			for (Message m : deliverableMessages) {
				if (tryBroadcastOneMessage(m, ni) == BROADCAST_OK) {
					// Transfer using broadcast started
					return ni;
				}
			}
		}
		
		// Didn't start transfer to any node -> ask for messages to connected peers
		for (NetworkInterface ni : networkInterfaces) {
			List<Connection> connections = ni.getConnections();
			Collections.shuffle(connections);
			for (Connection con : connections) {
				if (con.getOtherNode(getHost()).requestDeliverableMessages(con)) {
					return ni;
				}
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
		 * ones which terminated the transfer from the sendingConnections list.
		 * If no connection is still sending, and the buffer is holding an excessive amount
		 * of bytes, the method proceeds deleting some cached messages to free up some space.
		 * If all connections completed a message transfer, then the number of times that
		 * message has been forwarded is incremented.
		 */
		boolean freeBuffer = true;
		for (NetworkInterface ni : this.getHost().getInterfaces()) {
			for (int i=0; i < ni.getConnections().size(); ++i) {
				Connection con = ni.getConnections().get(i);
				if (con.isIdle()) {
					// Not sending anything on this connection..
					continue;
				}
				
				/* finalize ready transfers */
				if (con.isMessageTransferred()) {
					transferDone(con);
					con.finalizeTransfer();
				}
				/* remove connections that have gone down */
				else if (!con.isUp()) {
					transferAborted(con);
					con.abortTransfer();
				}
				else {
					/* one transfer is not done, yet. Do not remove any messages */
					freeBuffer = false;
				}
			}
		}
		
		if (freeBuffer) {
			// if the message being sent was holding excessive buffer, free it
			if (getFreeBufferSize() < 0) {
				makeRoomForMessage(0, Message.MAX_PRIORITY_LEVEL);
			}
		}

		/* time to do a TTL check and drop old messages? Only if not sending */
		for (NetworkInterface ni : getHost().getInterfaces()) {
			if (ni.isSendingData()) {
				return;
			}
		}
		if ((SimClock.getTime() - lastTtlCheck) >= TTL_CHECK_INTERVAL) {
			dropExpiredMessages();
			lastTtlCheck = SimClock.getTime();
		}
	}
	
	/**
	 * Method is called just before a transfer is aborted at {@link #update()} 
	 * due connection going down. This happens on the sending host. 
	 * Subclasses that are interested of the event may want to override this. 
	 * @param con The connection whose transfer was aborted
	 */
	protected void transferAborted(Connection con) { }
	
	/**
	 * Method is called just before a transfer is finalized 
	 * at {@link #update()}.
	 * Subclasses that are interested of the event may want to override this.
	 * @param con The connection whose transfer was finalized
	 */
	protected void transferDone(Connection con) { }
}
