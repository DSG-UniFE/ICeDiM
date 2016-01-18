/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */

package routing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import core.NetworkInterface;
import core.Connection;
import core.DTNHost;
import core.Message;
import core.MessageListener;
import core.Settings;
import core.SimClock;
import core.SimError;
import core.Tuple;

/**
 * Superclass of active routers. Contains convenience methods (e.g. 
 * {@link #getOldestMessage(boolean)}) and watching of sending connections (see
 * {@link #update()}).
 */
public abstract class ActiveRouter extends MessageRouter {
	/** Delete delivered messages -setting id ({@value}). Boolean valued.
	 * If set to true and the final recipient of a message rejects it because
	 * it already has it, the message is deleted from buffer. Default=false. */
	public static final String DELETE_DELIVERED_S = "deleteDelivered";
	/** should messages that final recipient marks as delivered be deleted
	 * from message buffer */
	protected boolean deleteDelivered;
	
	/** prefix of all response message IDs */
	public static final String RESPONSE_PREFIX = "R_";
	/** how often TTL check (discarding old messages) is performed */
	public static int TTL_CHECK_INTERVAL = 60;
	/** connection(s) that are currently used for sending */
	protected ArrayList<Connection> sendingConnections;
	/** simulation time when the last TTL check was done */
	private double lastTtlCheck;


	/**
	 * Constructor. Creates a new message router based on
	 * the settings in the given Settings object.
	 * @param s The settings object
	 */
	public ActiveRouter(Settings s) {
		super(s);
		
		if (s.contains(DELETE_DELIVERED_S)) {
			this.deleteDelivered = s.getBoolean(DELETE_DELIVERED_S);
		}
		else {
			this.deleteDelivered = false;
		}
	}
	
	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected ActiveRouter(ActiveRouter r) {
		super(r);
		
		this.deleteDelivered = r.deleteDelivered;
	}
	
	@Override
	public void init(DTNHost host, List<MessageListener> mListeners) {
		super.init(host, mListeners);
		
		sendingConnections = new ArrayList<Connection>(1);
		lastTtlCheck = 0;
	}
	
	
	/**
	 * Called when a connection's state changes. This version doesn't do 
	 * anything but subclasses may want to override this.
	 */
	@Override
	public void changedConnection(Connection con) { }

	/**
	 * Returns a list of those messages whose recipient is the
	 * host reachable through the specified Connection.
	 * @param con the Connection to some host.
	 * @return a List of messages to be delivered to the host
	 * reachable through the specified Connection.
	 */
	protected List<Message> getDeliverableMessagesForConnection(Connection con) {
		if (getNrofMessages() == 0) {
			/* no messages -> empty list */
			return new ArrayList<Message>(0); 
		}
	
		final DTNHost to = con.getOtherNode(getHost());
		List<Message> messageList = new ArrayList<Message>();
		for (Message m : getMessageList()) {
			if (isMessageDestination(m, to)) {
				messageList.add(m);
			}
		}
		
		return messageList;
	}
	
	@Override
	public Message requestDeliverableMessages(Connection con) {
		// Get a reference to the local NetworkInterface and check if it is busy
		NetworkInterface transferringInterface = con.getInterfaceForNode(getHost());
		if (transferringInterface == null) {
			throw new SimError("Connection " + con + " does not involve local host " + getHost());
		}
		if (!transferringInterface.isReadyToBeginTransfer()) {
			return null;
		}
		
		DTNHost otherNode = con.getOtherNode(getHost());
		/* The call sortAllReceivedMessagesForForwarding returns a copy of
		 * received messages, in order to avoid concurrent modification
		 * exceptions (startTransfer may remove messages). */
		List<Message> temp = sortAllReceivedMessagesForForwarding();
		for (Message m : temp) {
			if (shouldDeliverMessageToHost(m, otherNode) &&
				isMessageDestination(m, otherNode)) {
				if (startTransfer(m, con) == RCV_OK) {
					return m;
				}
			}
		}
		
		return null;
	}
	
	@Override
	public int receiveMessage(Message m, Connection con) {
		int recvCheck = checkReceiving(m, con); 
		if (recvCheck != RCV_OK) {
			return recvCheck;
		}

		// seems OK, start receiving the message
		return super.receiveMessage(m, con);
	}
	
	@Override
	public Message messageTransferred(String id, Connection con) {
		Message m = super.messageTransferred(id, con);

		/**
		 *  N.B. With application support the following if-block
		 *  becomes obsolete, and the response size should be configured 
		 *  to zero.
		 */
		// check if the Message was for this host and a response was requested
		if (isMessageDestination(m) && (m.getResponseSize() > 0)) {
			// generate a response message with the same priority level
			Message res = new Message(this.getHost(),m.getFrom(), RESPONSE_PREFIX + m.getID(),
										m.getResponseSize(), m.getPriority());
			res.copyPropertiesFrom(m);
			createNewMessage(res);
			getMessage(RESPONSE_PREFIX + m.getID()).setRequest(m);
		}
		
		return m;
	}
	
	/**
	 * Tries to start a transfer of message using a connection. Is starting
	 * succeeds, the connection is added to the watch list of active connections
	 * @param m The message to transfer
	 * @param con The connection to use
	 * @return the value returned by 
	 * {@link Connection#startTransfer(DTNHost, Message)}
	 */
	protected int startTransfer(Message m, Connection con) {
		NetworkInterface ni = con.getInterfaceForNode(getHost());
		
		if (!ni.isReadyToBeginTransfer()) {
			return TRY_LATER_BUSY;
		}
		
		int retVal = ni.sendUnicastMessageViaConnection(m, con);
		if (retVal == NetworkInterface.UNICAST_OK) {
			// started transfer and return RCV_OK
			addToSendingConnections(con);
			return RCV_OK;
		}
		else if (deleteDelivered && (retVal == DENIED_OLD) &&
			(m.getTo() == con.getOtherNode(getHost()))) {
			/* final recipient has already received the msg -> delete it */
			deleteMessage(m.getID(), MessageDropMode.REMOVED,
							"message had already been delivered");
		}
		
		return retVal;
	}
	
	/**
	 * Checks if router "wants" to start receiving message (i.e. router 
	 * isn't transferring, doesn't have the message and has room for it).
	 * @param m The message to check
	 * @return A return code similar to 
	 * {@link MessageRouter#receiveMessage(Message, DTNHost)}, i.e. 
	 * {@link MessageRouter#RCV_OK} if receiving seems to be OK, 
	 * TRY_LATER_BUSY if router is transferring, DENIED_OLD if the router
	 * is already carrying the message or it has been delivered to
	 * this router (as final recipient), or DENIED_NO_SPACE if the message
	 * does not fit into buffer
	 */
	protected int checkReceiving(Message m, Connection con) {	
		if (hasMessage(m.getID()) || isDeliveredMessage(m)){
			return DENIED_OLD; // already seen this message -> reject it
		}
		
		if (m.getTtl() <= 0 && m.getTo() != getHost()) {
			/* TTL has expired and this host is not the final recipient */
			return DENIED_TTL; 
		}
		
		/* remove oldest messages but not the ones being sent */
		if (!makeRoomForMessage(m.getSize(), m.getPriority())) {
			return DENIED_NO_SPACE; // couldn't fit into buffer -> reject
		}
		
		return RCV_OK;
	}
	
	/**
	 * Tries to send messages for the connection specified in the order
	 * they are in the list, until one of the connections starts
	 * transferring or all messages have been tried.
	 * @param messageList the list of Messages to try.
	 * @param con the Connection through which send a Message.
	 * @return The tuple whose connection accepted the message or null if
	 * the connection could not send the message.
	 */
	protected Tuple<Message, Connection> tryMessagesForConnection(
				List<Message> messageList, Connection con) {
		if ((messageList == null) || (con == null) ||
			(messageList.size() == 0)) {
			// Nothing to do
			return null;
		}
		
		for (Message m : messageList) {
			DTNHost destinationNode = con.getOtherNode(getHost());
			if (shouldDeliverMessageToHost(m, destinationNode) &&
				(startTransfer(m, con) == RCV_OK)) {
				return new Tuple<Message, Connection>(m, con);
			}
		}
		
		return null;
	}
	
	/**
	 * Tries to send messages for the connections that are mentioned
	 * in the Tuples in the order they are in the list until one of
	 * the connections starts transferring or all tuples have been tried.
	 * @param messageConnectionList The tuples to try.
	 * @return The tuple whose connection accepted the message or null if
	 * none of the connections accepted the message that was meant for them.
	 */
	protected Tuple<Message, Connection> tryMessagesForConnection(
				List<Tuple<Message, Connection>> messageConnectionList) {
		if ((messageConnectionList == null) || (messageConnectionList.size() == 0)) {
			// Nothing to do
			return null;
		}
		
		for (Tuple<Message, Connection> tuple : messageConnectionList) {
			DTNHost destinationNode = tuple.getValue().getOtherNode(getHost());
			if (shouldDeliverMessageToHost(tuple.getKey(), destinationNode) &&
				(startTransfer(tuple.getKey(), tuple.getValue()) == RCV_OK)) {
				return tuple;
			}
		}
		
		return null;
	}

	 /**
	  * Goes through all messages until the other node accepts one
	  * for receiving (or doesn't accept any). If a transfer is started, the
	  * connection is included in the list of sending connections.
	  * @param con Connection trough which the messages are sent
	  * @param messages A list of messages to try
	  * @return The message whose transfer was started or null if no 
	  * transfer was started. 
	  */
	protected Message tryAllMessages(Connection con, List<Message> messages) {
		DTNHost destinationNode = con.getOtherNode(getHost());
		for (Message m : messages) {
			if ((m.getSenderNode() == destinationNode) ||
				!shouldDeliverMessageToHost(m, destinationNode)) {
				// Avoid to send a message right back to the sender
				continue;
			}
			
			int retVal = startTransfer(m, con);
			if (retVal == RCV_OK) {
				return m;	// accepted a message, don't try others
			}
			else {
				return null; // should try later -> don't bother trying others
			}
		}
		
		return null; // no message was accepted
	}

	/**
	 * Tries to send all given messages to all given connections. Connections
	 * are first iterated in the order they are in the list and for every
	 * connection, the messages are tried in the order they are in the list.
	 * Once an accepting connection is found, no other connections or messages
	 * are tried.
	 * @param messages The list of Messages to try.
	 * @param connections The list of Connections to try.
	 * @return The connections that started a transfer or {@code null}
	 * if no connection accepted a message.
	 */
	protected Connection tryMessagesToConnections(List<Message> messages,
													List<Connection> connections) {
		// Randomize order to improve fairness
		Collections.shuffle(connections, RANDOM_GENERATOR);
		for (Connection con : connections) {
			Message started = tryAllMessages(con, messages); 
			if (started != null) { 
				return con;
			}
		}
		
		return null;
	}
	
	/**
	 * Tries to send all messages that this router is carrying to all
	 * connections this node has. Messages are ordered using the 
	 * {@link MessageRouter#sortByCachingPrioritizationStrategy(List)}. See 
	 * {@link #tryMessagesToConnections(List, List)} for sending details.
	 * @return The connections that started a transfer or null if no connection
	 * accepted a message.
	 */
	protected Connection tryAllMessagesToAllConnections() {
		List<NetworkInterface> networkInterfaces = getIdleNetworkInterfaces();
		if ((networkInterfaces.size() == 0) || (getNrofMessages() == 0)) {
			return null;
		}
		// Randomize order to improve fairness
		Collections.shuffle(networkInterfaces, RANDOM_GENERATOR);

		List<Message> messages = sortListOfMessagesForForwarding(getMessageList());
		for (NetworkInterface ni : networkInterfaces) {
			// Randomize order to improve fairness
			List<Connection> connections = ni.getConnections();
			Collections.shuffle(connections, RANDOM_GENERATOR);
			
			Connection con = tryMessagesToConnections(messages, connections);
			if (con != null) {
				return con;
			}
		}
		
		return null;
	}
		
	/**
	 * Exchanges deliverable (to final recipient) messages between this host
	 * and all hosts this host is currently connected to. First all messages
	 * from this host are checked and then all other hosts are asked for
	 * messages to this host. If a transfer is started, the search ends.
	 * @return A connection that started a transfer or null if no transfer
	 * was started
	 */
	protected Connection exchangeDeliverableMessages() {
		List<NetworkInterface> networkInterfaces = getIdleNetworkInterfaces();
		if (networkInterfaces.size() == 0) {
			return null;
		}
		// Randomize order to improve fairness
		Collections.shuffle(networkInterfaces, RANDOM_GENERATOR);
		
		for (NetworkInterface ni : networkInterfaces) {
			// Randomize order to improve fairness
			List<Connection> connections = ni.getConnections();
			Collections.shuffle(connections, RANDOM_GENERATOR);
			
			Tuple<Message, Connection> t = null;
			for (Connection con : connections) {
				t = tryMessagesForConnection(
						sortListOfMessagesForForwarding(getDeliverableMessagesForConnection(con)), con);
				if (t != null) {
					// started transfer
					return t.getValue();
				}
			}
			
			// didn't start transfer to any node -> ask messages from connected
			for (Connection con : connections) {
				if (con.getOtherNode(getHost()).requestDeliverableMessages(con) != null) {
					return con;
				}
			}
		}
		
		return null;
	}
	
	/**
	 * Adds a connections to sending connections which are monitored in
	 * the update.
	 * @see #update()
	 * @param con The connection to add
	 */
	protected void addToSendingConnections(Connection con) {
		sendingConnections.add(con);
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
		
		/* in theory we can have multiple sending connections even though
		  currently all routers allow only one concurrent sending connection */
		for (int i = 0; i < sendingConnections.size();) {
			boolean removeCurrent = false;
			Connection con = sendingConnections.get(i);
			
			/* finalize ready transfers */
			if (con.isMessageTransferred()) {
				if (con.getMessage() != null) {
					transferDone(con);
					con.finalizeTransfer();
				} /* else: some other entity aborted transfer */
				removeCurrent = true;
			}
			/* remove connections that have gone down */
			else if (!con.isUp()) {
				if (con.getMessage() != null) {
					transferAborted(con);
					con.abortTransfer("connection went down");
				}
				removeCurrent = true;
			} 
			
			if (removeCurrent) {
				// if the message being sent was holding excess buffer, free it
				if (getFreeBufferSize() < 0) {
					makeRoomForMessage(0, Message.MAX_PRIORITY_LEVEL);
				}
				sendingConnections.remove(i);
			}
			else {
				/* index increase needed only if nothing was removed */
				i++;
			}
		}
		
		/* time to do a TTL check and drop old messages? Only if not sending */
		if ((SimClock.getTime() - lastTtlCheck >= TTL_CHECK_INTERVAL) &&
			(sendingConnections.size() == 0)) {
			removeExpiredMessagesFromBuffer();
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
	protected void transferDone(Connection con) {
		// Notify listeners of the completed message transmission
		notifyListenersAboutTransmissionCompleted(con.getMessage());
	}
	
}
