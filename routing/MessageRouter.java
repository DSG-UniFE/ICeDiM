/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package routing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.uncommons.maths.random.MersenneTwisterRNG;

import core.Application;
import core.Connection;
import core.DTNHost;
import core.InterferenceModel;
import core.Message;
import core.MessageListener;
import core.MessageQueueManager;
import core.NetworkInterface;
import core.SeedGeneratorHelper;
import core.Settings;
import core.SimClock;
import core.SimError;

/**
 * Superclass for message routers.
 */
public abstract class MessageRouter {
	/** Receive return value for a successful broadcast */
	public static final int BROADCAST_OK = 0;
	/** Receive return value for an unsuccessful broadcast */
	public static final int BROADCAST_DENIED = 1;
	/** Receive return value for OK */
	public static final int RCV_OK = 0;
	/** Receive return value for busy receiver */
	public static final int TRY_LATER_BUSY = 1;
	/** Receive return value for an old (already received) message */
	public static final int DENIED_OLD = 2;
	/** Receive return value for not enough space in the buffer for the msg */
	public static final int DENIED_NO_SPACE = 3;
	/** Receive return value for messages whose TTL has expired */
	public static final int DENIED_TTL = 4;
	/** Receive return value for messages whose TTL has expired */
	public static final int DENIED_INTERFERENCE = 5;
	/** Receive return value for unspecified reason */
	public static final int DENIED_UNSPECIFIED = 99;
	
	/** Message TTL -setting id ({@value}). Integer expressed in minutes */
	public static final String MSG_TTL_S = "msgTTL";
	/** Random generator seed value -setting id ({@value}). Integer */
	public static final String ROUTER_RANDOM_SEED_S = "routerRndSeed";

	/** Random number generator for the purposes of routing */
	protected static MersenneTwisterRNG RANDOM_GENERATOR = null;
	/** Random number generator's seed value */
	protected static long RANDOM_GENERATOR_SEED = 13;

	
	/** Message queueing manager */
	private MessageQueueManager messageQueueManager;
	/** TTL for all messages */
	protected final int msgTTL;
	/** List of listeners for logging purposes */
	protected List<MessageListener> mListeners;
	/** The messages this router has received as the final recipient */
	private HashMap<String, Message> deliveredMessages;
	/** All the messages this router has received in the past */
	private HashMap<String, Message> receivedMessages;
	/** Host where this router belongs to */
	private DTNHost host;

	/** applications attached to the host */
	private HashMap<String, Collection<Application>> applications = null;
	
	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object. Size of the message buffer is read from
	 * {@link #B_SIZE_S} setting. Default value is Integer.MAX_VALUE.
	 * @param s The settings object
	 */
	public MessageRouter(Settings s) {
		this.msgTTL = s.contains(MSG_TTL_S) ? s.getInt(MSG_TTL_S) : Message.INFINITE_TTL;
		this.messageQueueManager = new MessageQueueManager(s);
		this.applications = new HashMap<String, Collection<Application>>();
		
		if (RANDOM_GENERATOR == null) {
			// Singleton
			RANDOM_GENERATOR = new MersenneTwisterRNG(
					SeedGeneratorHelper.get16BytesSeedFromValue(RANDOM_GENERATOR_SEED));
			RANDOM_GENERATOR.setSeed(RANDOM_GENERATOR_SEED);
		}
	}
	
	/**
	 * Initializes the router; i.e. sets the host this router is in and
	 * message listeners that need to be informed about message related
	 * events etc.
	 * @param host The host this router is in
	 * @param mListeners The message listeners
	 */
	public void init(DTNHost host, List<MessageListener> mListeners) {
		this.deliveredMessages = new HashMap<String, Message>();
		this.receivedMessages = new HashMap<String, Message>();
		this.mListeners = mListeners;
		this.host = host;
	}
	
	/**
	 * Copy-constructor.
	 * @param r Router to copy the settings from.
	 */
	protected MessageRouter(MessageRouter r) {
		this.msgTTL = r.msgTTL;
		this.messageQueueManager = new MessageQueueManager(r.messageQueueManager);
		this.applications = new HashMap<String, Collection<Application>>();		
		for (Collection<Application> apps : r.applications.values()) {
			for (Application app : apps) {
				addApplication(app.replicate());
			}
		}
	}
	
	/**
	 * Updates router.
	 * This method should be called (at least once) on every simulation
	 * interval to update the status of transfer(s). 
	 */
	public void update() {
		for (Collection<Application> apps : applications.values()) {
			for (Application app : apps) {
				app.update(getHost());
			}
		}
	}
	
	/**
	 * Informs the router about change in connections state.
	 * @param con The connection that changed
	 */
	public abstract void changedConnection(Connection con);	
	
	/**
	 * Returns a message by ID.
	 * @param id ID of the message
	 * @return The message
	 */
	protected Message getMessage(String msgID) {
		return messageQueueManager.getMessage(msgID);
	}
	
	/**
	 * Checks if this router has a message with certain id buffered.
	 * @param id Identifier of the message
	 * @return True if the router has message with this id, false if not
	 */
	protected boolean hasMessage(String msgID) {
		return messageQueueManager.hasMessage(msgID);
	}
	
	/**
	 * Checks if this router has received a {@link Message}
	 * with the specified identifier in the past.
	 * @param msgID Identifier of the message.
	 * @return {@code true} if the router has message
	 * with this id, {@code false} otherwise.
	 */
	protected boolean hasReceivedMessage(String msgID) {
		return receivedMessages.containsKey(msgID);
	}
	
	/**
	 * Returns true if a full message with same ID as the given message has been
	 * received by this host as the <strong>final</strong> recipient 
	 * (at least once).
	 * @param m message we're interested of
	 * @return true if a message with the same ID has been received by 
	 * this host as the final recipient.
	 */
	protected boolean isIncomingMessage(String msgID) {
		for (NetworkInterface ni : getHost().getInterfaces()) {
			if (ni.isReceivingMessage(msgID)) {
				return true;
			}
		}
		
		return false;
	}
	
	/**
	 * Returns true if a full message with same ID as the given message
	 * has been received by this host as the <strong>final</strong>
	 * recipient (at least once).
	 * @param m message we're interested of
	 * @return true if a message with the same ID has been received by 
	 * this host as the final recipient.
	 */
	protected boolean isDeliveredMessage(Message m) {
		return deliveredMessages.containsKey(m.getID());
	}
	
	/**
	 * Returns the number of messages this router has
	 * @return How many messages this router has
	 */
	public int getNrofMessages() {
		return messageQueueManager.getNumberOfMessages();
	}
	
	/**
	 * Returns the size of the message buffer.
	 * @return The size or Integer.MAX_VALUE if the size isn't defined.
	 */
	public int getBufferSize() {
		return messageQueueManager.getBufferSize();
	}
	
	/**
	 * Returns the amount of free space in the buffer. May return a negative
	 * value if there are more messages in the buffer than should fit there
	 * (because of creating new messages).
	 * @return The amount of free space (Integer.MAX_VALUE if the buffer
	 * size isn't defined)
	 */
	public int getFreeBufferSize() {
		return messageQueueManager.getFreeBufferSize();
	}
	
	/**
	 * Returns a list of those messages whose recipient is the
	 * host reachable through the specified Connection.
	 * @param con the Connection to some host.
	 * @return a List of messages to be delivered to the host
	 * reachable through the specified Connection.
	 */
	protected List<Message> getMessagesForConnection(Connection con) {
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
	
	/**
	 * Adds a message to the message buffer and informs message listeners
	 * about new message (if requested).
	 * @param m The message to add
	 * @param newMessage If true, message listeners are informed about a new
	 * message, if false, nothing is informed.
	 */
	protected void addToMessages(Message m, boolean newMessage) {
		if (messageQueueManager.hasMessage(m)) {
			return;
		}
		messageQueueManager.addMessageToQueue(m);

		if (newMessage) {
			for (MessageListener ml : mListeners) {
				ml.newMessage(m);
			}
		}
	}
	
	/**
	 * Creates a new message to the router.
	 * @param m The message to create
	 * @return True if the creation succeeded, false if not (e.g.
	 * the message was too big for the buffer)
	 */
	public boolean createNewMessage(Message m) {
		m.setTtl(msgTTL);
		addToMessages(m, true);
		
		return true;
	}

	/**
	 * Removes and returns a message from the message buffer.
	 * @param id Identifier of the message to remove
	 * @return The removed message or null if message for the ID wasn't found
	 */
	protected Message removeFromMessages(String msgID) {
		return messageQueueManager.removeMessage(msgID);
	}
	
	/**
	 * Deletes a message from the buffer and informs message listeners
	 * about the event
	 * @param id Identifier of the message to delete
	 * @param drop If the message is dropped (e.g. because of full buffer) this 
	 * should be set to true. False value indicates e.g. remove of message
	 * because it was delivered to final destination.  
	 */
	public void deleteMessage(String id, boolean drop, String cause) {
		Message removed = removeFromMessages(id); 
		if (removed == null) {
			throw new SimError("No message for id " + id + " to remove at " + getHost());
		}

		for (MessageListener ml : mListeners) {
			ml.messageDeleted(removed, getHost(), drop, cause);
		}
	}
	
	/**
	 * Deletes a message from the buffer without informing
	 * message listeners about the event
	 * @param id Identifier of the message to delete
	 * because it was delivered to final destination.  
	 */
	public void deleteMessageWithoutRaisingEvents(String id) {
		if (null == removeFromMessages(id)) {
			throw new SimError("No message for id " + id + " to remove at " + getHost());
		}
	}
	
	/**
	 * Informs message listeners about delete event
	 * @param id Identifier of the message to delete
	 * @param drop If the message is dropped (e.g. because of full buffer) this 
	 * should be set to true. False value indicates e.g. remove of message
	 * because it was delivered to final destination.  
	 */
	public void notifyListenersAboutMessageDelete(Message removed, boolean drop, String cause) {		
		for (MessageListener ml : mListeners) {
			ml.messageDeleted(removed, getHost(), drop, cause);
		}
	}
	
	/**
	 * Drops messages whose TTL is less than zero.
	 */
	protected void dropExpiredMessages() {
		for (Message m : getMessageList()) { 
			if (m.getTtl() <= 0) {
				deleteMessage(m.getID(), true, "TTL expired");
			}
		}
	}
	
	/**
	 * Returns a reference to the messages of this router in collection.
	 * <b>Note:</b> If there's a chance that some message(s) from the collection
	 * could be deleted (or added) while iterating through the collection, a
	 * copy of the collection should be made to avoid concurrent modification
	 * exceptions. 
	 * @return a reference to the messages of this router in collection
	 */
	public List<Message> getMessageList() {
		return new ArrayList<Message>(messageQueueManager.getMessageCollection());
	}
	
	/**
	 * Sorts a copy of the list containing all messages received by the
	 * router, according to the current message prioritization strategy.
	 * @return A copy of the list of the received messages, sorted
	 * according to the current message prioritization strategy.
	 */
	protected List<Message> sortAllReceivedMessagesForForwarding() {
		return messageQueueManager.sortBufferedMessagesForForwarding();
	}
	
	/**
	 * Sorts the given list according to the current sending queue strategy.
	 * @param inputList The list to sort
	 * @return The sorted list
	 */
	protected List<Message> sortListOfMessagesForForwarding(List<Message> inputList) {
		return messageQueueManager.sortMessageListForForwarding(inputList);
	}
	
	/**
	 * Sorts the given list in reverse order, according to the current
	 * sending queue strategy. The list can contain either Message or
	 * Tuple<Message, Connection> objects. Other objects cause error.
	 * @param list The list to sort
	 * @return The list sorted in reverse order
	 */
	protected List<Message> getListOfMessagesInReversePriorityOrder(List<Message> inputList) {
		messageQueueManager.sortByReversedPrioritizationMode(inputList);
		return inputList;
	}

	/**
	 * Gives the order of the two given messages as defined by the current
	 * queue mode 
	 * @param m1 The first message
	 * @param m2 The second message
	 * @return -1 if the first message should come first, 1 if the second 
	 *          message should come first, or 0 if the ordering isn't defined
	 */
	protected int compareMessagesByQueueMode(Message m1, Message m2) {
		return messageQueueManager.compareByPrioritizationMode(m1, m2);
	}
	
	
	/**
	 * Returns the host this router is in
	 * @return The host object
	 */
	protected DTNHost getHost() {
		return host;
	}
	
	/**
	 * Returns whether the specified host is a neighbor or not
	 * @param neighborHost the {@link DTNHost} we want to check
	 * @return {@code true} if the specified host is a neighbor,
	 * or {@code false} otherwise.
	 */
	protected boolean isNeighboringHost(DTNHost neighborHost) {
		for (NetworkInterface ni : getHost().getInterfaces()) {
			if (isNeighboringHost(ni, neighborHost)) {
				return true;
			}
		}
		
		return false;
	}
	
	/**
	 * Returns whether the specified host is a neighbor reachable
	 * through the specified {@link NetworkInterface} or not.
	 * @param ni the {@link NetworkInterface} considered.
	 * @param neighborHost the {@link DTNHost} we want to check.
	 * @return {@code true} if the specified host is a neighbor
	 * reachable through the selected {@link NetworkInterface},
	 * or {@code false} otherwise.
	 */
	protected boolean isNeighboringHost(NetworkInterface ni, DTNHost neighborHost) {
		for (Connection con : ni.getConnections()) {
			if (con.getOtherNode(getHost()) == neighborHost) {
				return true;
			}
		}
		
		return false;
	}

	/**
	 * Returns whether the specified {@link Message} needs to be
	 * delivered to the selected {@link DTNHost}. This method is
	 * a hook that returns {@code true} as its default behavior.
	 * Subclasses should overwrite it.
	 * @param m The Message that might need to be delivered.
	 * @param to The host that might need the Message.
	 * @return {@code true} if the specified host needs the
	 * Message, {@code false} otherwise.
	 */
	protected boolean shouldDeliverMessageToHost(Message m, DTNHost to) {
		return true;
	}
	
	/**
	 * Returns a {@link List} of all {@link NetworkInterface}
	 * ready to begin a new transfer.
	 * @return The {@link List} of all idle {@link NetworkInterface} 
	 */
	protected List<NetworkInterface> getIdleNetworkInterfaces() {
		List<NetworkInterface> idleInterfaces = new ArrayList<NetworkInterface>();
		for (NetworkInterface ni : getHost().getInterfaces()) {
			if (ni.isReadyToBeginTransfer()) {
				idleInterfaces.add(ni);
			}
		}
		
		return idleInterfaces;
	}
	
	/**
	 * Returns {@code true} if the router has any free
	 * {@link NetworkInterface}, or {@code false} otherwise.
	 * @return a boolean value to determine if any
	 * {@link NetworkInterface} is available to
	 * start a new transfer.
	 */
	protected boolean canBeginNewTransfer() {
		if (getNrofMessages() == 0) {
			return false;
		}
		
		return getIdleNetworkInterfaces().size() > 0;
	}
	
	/**
	 * Start sending a message to another host.
	 * @param id Id of the message to send
	 * @param to The host to send the message to
	 */
	public void sendMessage(String id, DTNHost to) {
		Message m = getMessage(id);
		if (m == null) throw new SimError("no message for id " + id + " to send at " + getHost());
 
		// send a replication of the message
		Message m2 = m.replicate();
		for (Connection con : getHost().getConnections()) {
			if (con.getOtherNode(getHost()) == to) {
				to.receiveMessage(m2, con);
				return;
			}
		}
		
		throw new SimError("No connection to host " + to + " from host " + getHost());
	}
	
	/**
	 * Requests for deliverable message from this router to be
	 * sent trough the specified connection.
	 * @param con The connection to send the messages trough.
	 * @return True if this router started a transfer,
	 * false otherwise.
	 */
	public Message requestDeliverableMessages(Connection con) {
		return null; // default behavior is to not start -- subclasses override
	}
	
	/**
	 * Notify the router that this connection has been interfered,
	 * so that it can react accordingly. The Default action is to
	 * notify the interference models and all the listeners.
	 * @param con The connection which is transferring the message
	 */
	public void messageInterfered(String msgID, Connection con) {
		if ((con.getReceiverNode() == getHost()) && (msgID != null) &&
			con.isTransferOngoing()) {
			Message interferedMessage = con.getReceiverInterface().
											forceInterference(msgID, con);
			for (MessageListener ml : mListeners) {
				ml.messageTransmissionInterfered(interferedMessage,
												con.getSenderNode(), getHost());
			}
		}
	}
	
	/**
	 * Checks if router can start receiving the message (i.e. router 
	 * isn't transferring and there are no interferences).
	 * @param m The message to check
	 * @param con The Connection transferring the Message
	 * @return A return code similar to 
	 * {@link InterferenceModel#beginNewReception(Message, Connection)}, i.e. 
	 * {@link InterferenceModel#RECEPTION_OK} if receiving seems to be OK, 
	 * {@link InterferenceModel#RECEPTION_DENIED_DUE_TO_SEND} if the interface
	 * is transferring, or {@link InterferenceModel#RECEPTION_INTERFERENCE}
	 * in case of interference with other messages. 
	 */
	protected int checkReceiving(Message m, Connection con) {
		/* Base router always accepts messages
		 * Derived classes can override this method to provide
		 * more complex message acceptance politics
		 */
		return RCV_OK;
	}
	
	/**
	 * Try to start receiving a message from another host.
	 * @param m {@link Message} to put in the receiving buffer.
	 * @param con The {@link Connection} transferring the message.
	 * @return Value zero if the node accepted the message (RCV_OK),
	 * value less than zero if node rejected the message (e.g.
	 * {@code DENIED_OLD}), value bigger than zero if the other
	 * node should try later (e.g. {@code TRY_LATER_BUSY}).
	 */
	public int receiveMessage(Message m, Connection con) {
		Message newMessage = m.replicate();
		NetworkInterface receivingInterface = con.getReceiverInterface();
		if (!getHost().getInterfaces().contains(receivingInterface)) {
			throw new SimError("receiveMessage method invoked on the wrong host!");
		}
		
		int receptionValue = receivingInterface.beginNewReception(newMessage, con);		
		if (receptionValue == InterferenceModel.RECEPTION_DENIED_DUE_TO_SEND) {
			throw new SimError("Receive failed due to transmitting interface. " + 
								"CSMA/CA should avoid such situations");
		}
		
		for (MessageListener ml : mListeners) {
			ml.messageTransferStarted(newMessage, con.getSenderNode(), getHost());
		}
		if (receptionValue == InterferenceModel.RECEPTION_INTERFERENCE) {
			// The new reception failed, triggering an interference
			return DENIED_INTERFERENCE;
		}
		
		 // Superclass accepts messages if the interference model accepts it
		return RCV_OK;
	}
	
	/**
	 * This method should be called (on the receiving host) after a message
	 * was successfully transferred. The transferred message is put to the
	 * message buffer unless this host is the final recipient of the message.
	 * @param id Id of the transferred message
	 * @param from Host the message was from (previous hop)
	 * @return The message that this host received
	 */
	public Message messageTransferred(String msgID, Connection con) throws SimError {
		boolean isFinalRecipient;
		boolean isFirstDelivery;	// is this the first delivered instance of the msg?
		
		Message incoming = retrieveTransferredMessageFromInterface(msgID, con);
		if (incoming == null) {
			// Message interfered or reception out-of-synch --> nothing to do
			return null;
		}
		incoming.setReceiveTime(SimClock.getTime());
		incoming.addNodeOnPath(getHost());
		
		// Pass the message to the application (if any) and get outgoing message
		Message outgoing = incoming;
		for (Application app : getApplications(incoming.getAppID())) {
			// Note that the order of applications is significant
			// since the next one gets the output of the previous.
			outgoing = app.handle(outgoing, getHost());
			if (outgoing == null) break; // Some app wanted to drop the message
		}
		
		Message aMessage = (outgoing == null) ? (incoming) : (outgoing);
		// If the application re-targets the message (changes 'to')
		// then the message is not considered as 'delivered' to this host.
		isFinalRecipient = isMessageDestination(aMessage);
		isFirstDelivery = !hasReceivedMessage(aMessage.getID());

		if (!isFinalRecipient && (outgoing != null)) {
			/* received message is not the final recipient and
			 * no app wants to drop it -> put it into the buffer */
			addToMessages(aMessage, false);
		}
		else if (isFirstDelivery && isFinalRecipient) {
			deliveredMessages.put(msgID, aMessage);
		}
		if (isFirstDelivery) {
			/* if it is the first time the router receives
			 * the message, add it to received messages */
			receivedMessages.put(msgID, aMessage);
		}
		
		for (MessageListener ml : mListeners) {
			ml.messageTransferred(aMessage, con.getSenderNode(), getHost(),
									isFirstDelivery, isFinalRecipient);
		}
		
		return aMessage;
	}

	/**
	 * The method checks that the receiving interface of the specified
	 * {@link Connection} belongs to this node and it verifies that the
	 * code returned by the {@link InterferenceModel} is consistent with
	 * the status of the Router and with the availability of the transferred
	 * {@link Message}. In addition, it performs the actual message reception. 
	 * @param msgID the ID of the {@link Message} being received. 
	 * @param con the {@link Connection} transferring the Message. 
	 * @return The received Message in case of successful reception, or
	 * {@code null} if the message could not be received due to interferences.
	 * @throws SimError An error signaling any detected inconsistency.
	 */
	protected Message retrieveTransferredMessageFromInterface(String msgID, Connection con)
																throws SimError {
		NetworkInterface receivingInterface = con.getReceiverInterface();
		if (!getHost().getInterfaces().contains(receivingInterface)) {
			throw new SimError("messageTransferred() method called on the wrong host");
		}
		int receiveResult = receivingInterface.isMessageTransferredCorrectly(msgID, con);
		
		if (receiveResult == InterferenceModel.MESSAGE_ID_NOT_FOUND) {
			throw new SimError("Message with messageID " + msgID + " could not" +
								" be found within the interference model");
		}
		else if (receiveResult == InterferenceModel.RECEPTION_INCOMPLETE) {
			throw new SimError("messageTransferred method invoked when message with ID " +
								msgID + " was not completely transferred yet");
		}
		else if (receiveResult == InterferenceModel.RECEPTION_INTERFERENCE) {
			if (receivingInterface.retrieveTransferredMessage(msgID, con) != null) {
				throw new SimError("NetworkInterface.retrieveTransferredMessage() method " +
									"returned a message even if an interference was detected");
			}
			
			for (MessageListener ml : mListeners) {
				ml.messageTransmissionInterfered(con.getMessage(), con.getSenderNode(),
													con.getReceiverNode());
			}
			return null;
		}
		else if (receiveResult == InterferenceModel.RECEPTION_OUT_OF_SYNCH) {
			if (receivingInterface.retrieveTransferredMessage(msgID, con) != null) {
				throw new SimError("NetworkInterface.retrieveTransferredMessage() method " +
									"returned a message even if reception was out-of-synch");
			}
			
			// No need to notify listeners: removing transfer from the InterferenceModel is enough
			return null;
		}

		// receiveResult == InterferenceModel.RECEPTION_COMPLETED_CORRECTLY
		Message incoming = receivingInterface.retrieveTransferredMessage(msgID, con);
		if (incoming == null) {
			throw new SimError("Impossible to retrieve message with ID " + msgID + 
								" from the InterferenceModel");
		}
		
		return incoming;
	}
	
	/**
	 * This method should be called (on the receiving host) when a message 
	 * transfer was aborted.
	 * @param id Id of the message that was being transferred
	 * @param from Host the message was from (previous hop)
	 * would have been ready; or -1 if the number of bytes is not known
	 */
	public void messageAborted(String msgID, Connection con, String motivation) {
		NetworkInterface receivingInterface = con.getReceiverInterface();
		int receiveResult = receivingInterface.isMessageTransferredCorrectly(msgID, con);
		if (receiveResult == InterferenceModel.MESSAGE_ID_NOT_FOUND) {
			// Abortion is not necessary
			return;
		}
		else if (receiveResult == InterferenceModel.RECEPTION_OUT_OF_SYNCH) {
			if (null == receivingInterface.abortMessageReception(con)) {
				throw new SimError("abortMessageReception() method could not abort " +
									"any transfer on specified connection");
			}
			// No notifications to listeners are necessary (transfer not synched)
			return;
		}
		else if (receiveResult != InterferenceModel.RECEPTION_INCOMPLETE) {
			throw new SimError("isMessageTransferredCorrectly() method invoked with message" +
								" with ID " + msgID + " returned reception incomplete;");
		}

		Message abortedMessage = receivingInterface.abortMessageReception(con);
		if (abortedMessage != null) {
			for (MessageListener ml : this.mListeners) {
				ml.messageTransferAborted(abortedMessage, con.getSenderNode(),
											getHost(), motivation);
			}
		}
		else {
			throw new SimError("No incoming message for id " + msgID + " to abort in " + getHost());
		}
	}

	/**
	 * Check if this {@link DTNHost} is the final destination of the
	 * specified {@link Message}. Subclasses can redefine the method
	 * to change its logic; e.g., to use subscriptions. 
	 * @param aMessage The received {@link Message}.
	 * @return {@code true} if this {@link MessageRouter} is the final
	 * destination (or one of them) of the received {@link Message},
	 * or {@code false} otherwise.
	 */
	protected boolean isMessageDestination(Message aMessage) {
		return aMessage.getTo() == getHost();
	}

	/**
	 * Check if the specified {@link DTNHost} is the final destination
	 * of the specified {@link Message}. Subclasses can redefine the
	 * method to change its logic; e.g., to use subscriptions. 
	 * @param aMessage The received {@link Message}. 
	 * @param dest The host we want to check if it's the message destination.
	 * @return {@code true} if this {@link MessageRouter} is the final
	 * destination (or one of them) of the received {@link Message},
	 * or {@code false} otherwise.
	 */
	protected boolean isMessageDestination(Message aMessage, DTNHost dest) {
		return aMessage.getTo() == dest;
	}
	
	/**
	 * Returns routing information about this router.
	 * @return The routing information.
	 */
	public RoutingInfo getRoutingInfo() {
		RoutingInfo ri = new RoutingInfo(this);
		
		int incomingMessages = 0;
		for (NetworkInterface ni : getHost().getInterfaces()) {
			incomingMessages += ni.getNumberOfIncomingMessages();
		}		
		RoutingInfo incoming = new RoutingInfo(incomingMessages + " incoming message(s)");
		
		RoutingInfo delivered = new RoutingInfo(this.deliveredMessages.size() +
				" delivered message(s)");
		
		RoutingInfo cons = new RoutingInfo(getHost().getConnections().size() + 
			" connection(s)");
				
		ri.addMoreInfo(incoming);
		ri.addMoreInfo(delivered);
		ri.addMoreInfo(cons);
		for (NetworkInterface ni : getHost().getInterfaces()) {
			for (Message m : ni.getIncomingMessages()) {
				incoming.addMoreInfo(new RoutingInfo(m));
			}
		}
		
		for (Message m : deliveredMessages.values()) {
			delivered.addMoreInfo(new RoutingInfo(m + " path:" + m.getHops()));
		}
		
		for (Connection c : getHost().getConnections()) {
			cons.addMoreInfo(new RoutingInfo(c));
		}

		return ri;
	}
	
	/** 
	 * Adds an application to the attached applications list.
	 * 
	 * @param app	The application to attach to this router.
	 */
	public void addApplication(Application app) {
		if (!this.applications.containsKey(app.getAppID())) {
			this.applications.put(app.getAppID(),
					new LinkedList<Application>());
		}
		this.applications.get(app.getAppID()).add(app);
	}
	
	/** 
	 * Returns all the applications that want to receive messages for the given
	 * application ID.
	 * 
	 * @param ID	The application ID or <code>null</code> for all apps.
	 * @return		A list of all applications that want to receive the message.
	 */
	public Collection<Application> getApplications(String ID) {
		LinkedList<Application>	apps = new LinkedList<Application>();
		// Applications that match
		Collection<Application> tmp = applications.get(ID);
		if (tmp != null) {
			apps.addAll(tmp);
		}
		// Applications that want to look at all messages
		if (ID != null) {
			tmp = applications.get(null);
			if (tmp != null) {
				apps.addAll(tmp);
			}
		}
		return apps;
	}

	/**
	 * Creates a replicate of this router. The replicate has the same
	 * settings as this router but empty buffers and routing tables.
	 * @return The replicate
	 */
	public abstract MessageRouter replicate();
	
	/**
	 * Returns a String presentation of this router
	 * @return A String presentation of this router
	 */
	public String toString() {
		return getClass().getSimpleName() + " of " + getHost() + " with " +
				getNrofMessages() + " messages";
	}
	
}
