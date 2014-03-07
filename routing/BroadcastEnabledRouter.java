package routing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;


import core.Connection;
import core.DTNHost;
import core.Message;
import core.MessageListener;
import core.NetworkInterface;
import core.Settings;
import core.SimClock;
import core.Tuple;

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
	
	@Override
	public boolean requestDeliverableMessages(Connection con) {
		NetworkInterface transferringInterface = con.getInterfaceForNode(getHost());
		if (!transferringInterface.isReadyToBeginTransfer()) {
			return false;
		}
		
		DTNHost other = con.getOtherNode(getHost());
		/* do a copy to avoid concurrent modification exceptions 
		 * (startTransfer may remove messages) */
		//ArrayList<Message> temp = new ArrayList<Message>(this.getMessageCollection());
		Collection<Message> temp = this.getMessageCollection();
		List<Message> messagesList = new ArrayList<Message>(temp);
		this.sortByQueueMode(messagesList);
		
		//for (Message m : temp) {
		for (Message m : messagesList) {
			if (other == m.getTo()) {
				if (startTransfer(m, con) == RCV_OK) {
					// A deliverable message is found and will be delivered via con
					return true;
				}
			}
		}
		return false;
	}
	
	public boolean createNewMessage(Message m) {
		if (makeRoomForNewMessage(m.getSize(), m.getPriority().ordinal())) {
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
			Message res = new Message(this.getHost(),m.getFrom(), RESPONSE_PREFIX + m.getID(),
										m.getResponseSize(), m.getPriority(), m.getSubscriptionID());
			if (this.createNewMessage(res)) {
				this.getMessage(RESPONSE_PREFIX + m.getID()).setRequest(m);
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
		if (size > this.getBufferSize()) {
			return false; // message too big for the buffer
		}
			
		int freeBuffer = this.getFreeBufferSize();
		ArrayList<Message> deletedMessages = new ArrayList<Message>();
		/* delete messages from the buffer until there's enough space */
		while (freeBuffer < size) {
			Message m = getOldestMessageWithLowestPriority(true); // don't remove msgs being sent

			if ((m == null) || (m.getPriority().ordinal() > priority)) {
				//return false
				break; // couldn't remove any more messages
			}
			
			/* delete message from the buffer as "drop" */
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
			notifyListenersAboutMessageDelete(m, true);	// true identifies dropped messages
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
	 * Returns the oldest (by receive time) message in the message buffer (that is
	 * not being sent if excludeMsgBeingSent is true) with the lowest Priority.
	 * @param excludeMsgBeingSent If true, excludes message(s) that are
	 * being sent from the oldest message check (i.e. if oldest message is
	 * being sent, the second oldest message is returned)
	 * @return The oldest message or null if no message could be returned
	 * (no messages in buffer or all messages in buffer are being sent and
	 * exludeMsgBeingSent is true)
	 */
	protected Message getOldestMessageWithLowestPriority(boolean excludeMsgBeingSent) {
		Collection<Message> messages = this.getMessageCollection();
		Message oldestWithLowestPriority = null;
		for (Message m : messages) {
			if (excludeMsgBeingSent && isSending(m.getID())){
				continue; // skip the message(s) that router is sending
			}
			
			if (oldestWithLowestPriority == null) {
				oldestWithLowestPriority = m;
			}
			else if (oldestWithLowestPriority.getPriority().ordinal() >= m.getPriority().ordinal()) {
				if ((oldestWithLowestPriority.getPriority().ordinal() > m.getPriority().ordinal()) ||
					(oldestWithLowestPriority.getReceiveTime() > m.getReceiveTime())) {
					oldestWithLowestPriority = m;
				}
			}
		}
		
		return oldestWithLowestPriority;
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
	protected int startTransfer(Message m, Connection con) {
		if (!con.isReadyForTransfer() ||
			!con.getInterfaceForNode(getHost()).isReadyToBeginTransfer()) {
			return TRY_LATER_BUSY;
		}
		
		int retVal = con.startTransfer(getHost(), m);
		if (deleteDelivered && retVal == DENIED_OLD && 
			m.getTo() == con.getOtherNode(this.getHost())) {
			/* final recipient has already received the msg -> delete it */
			this.deleteMessage(m.getID(), false);
		}
		
		return retVal;
	}
	
	/**
	 * Makes rudimentary checks (that we have at least one message and one
	 * connection) about can this router start transfer.
	 * @return True if router can start transfer, false if not
	 */
	protected boolean canStartTransfer() {
		if (this.getNrofMessages() == 0) {
			return false;
		}
		if (this.getConnections().size() == 0) {
			return false;
		}
		
		// Check if at least one interface can do a broadcast
		for (NetworkInterface ni : this.getHost().getInterfaces()) {
			if (ni.isReadyToBeginTransfer()) {
				return true;
			}
		}
		
		return false;
	}
	
	/**
	 * Drops messages whose TTL is less than zero.
	 */
	protected void dropExpiredMessages() {
		Message[] messages = getMessageCollection().toArray(new Message[0]);
		for (int i=0; i<messages.length; i++) {
			int ttl = messages[i].getTtl(); 
			if (ttl <= 0) {
				deleteMessage(messages[i].getID(), true);
				//Assert.assertTrue("Impossible to find ", receivedMsgIDs.remove(messages[i].getId()));
			}
		}
	}
	
	
	/**
	 * Returns a list of message-connections tuples of the messages whose
	 * recipient is some host that we're connected to at the moment.
	 * @return a list of message-connections tuples
	 */
	protected List<Tuple<Message, Connection>> getMessagesForConnected() {
		if (getNrofMessages() == 0 || getConnections().size() == 0) {
			/* no messages -> empty list */
			return new ArrayList<Tuple<Message, Connection>>(0); 
		}

		List<Tuple<Message, Connection>> forTuples = new ArrayList<Tuple<Message, Connection>>();
		for (Message m : getMessageCollection()) {
			for (Connection con : getConnections()) {
				DTNHost to = con.getOtherNode(getHost());
				if (m.getTo() == to) {
					forTuples.add(new Tuple<Message, Connection>(m, con));
				}
			}
		}
		
		return forTuples;
	}

	/**
	 * Tries to send the given message to all connections, performing a broadcast.
	 * If all connections result able to send, then the broadcast is performed
	 * by sending the same message via all the connections.
	 * @param m The message to broadcast
	 * @param connections The list of Connections to try
	 * @return BROADCAST_OK if the broadcast attempt went fine, an error otherwise
	 */
	protected int tryBroadcastOneMessage(Message m, NetworkInterface ni) {		
		int retVal = ni.sendBroadcastMessage(m);
		if (retVal == NetworkInterface.BROADCAST_OK) {
			m.incrementForwardTimes();
		}
		
		return retVal;
	}

	/**
	 * Shuffles a messages list so the messages are in random order.
	 * @param messages The list to sort and shuffle
	 */
	protected void shuffleMessages(List<Message> messages) {
		if (messages.size() <= 1) {
			return; // nothing to shuffle
		}
		
		Random rng = new Random(SimClock.getIntTime());
		Collections.shuffle(messages, rng);	
	}

	/**
	 * Shuffles a messages list so the messages are in random order.
	 * @param messages The list to sort and shuffle
	 */	
	protected NetworkInterface getIdleInterface() {
		for (NetworkInterface ni : getHost().getInterfaces()) {
			if (ni.isReadyToBeginTransfer()) {
				return ni;
			}
		}
		
		return null;
	}
	
	protected boolean isSending() {
		for (NetworkInterface ni : getHost().getInterfaces()) {
			if (ni.isSendingData()) {
				return true;
			}
		}
		
		return false;		
	}
	
	/**
	 * Returns true if this router is currently sending a message with 
	 * <CODE>msgId</CODE>.
	 * @param msgId The ID of the message
	 * @return True if the message is being sent false if not
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
			if (this.getFreeBufferSize() < 0) {
				this.makeRoomForMessage(0, Message.PRIORITY_LEVEL.HIGHEST_P.ordinal());
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

	List<Message> getOrderedMessageList() {
		List<Message> messagesList = new ArrayList<Message>(this.getMessageCollection());
		
		return sortByQueueMode(messagesList);
	}
}
