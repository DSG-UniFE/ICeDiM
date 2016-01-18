/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package routing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import org.uncommons.maths.random.MersenneTwisterRNG;

import core.Application;
import core.Connection;
import core.DTNHost;
import core.DTNSim;
import core.InterferenceModel;
import core.Message;
import core.MessageListener;
import core.MessageCacheManager;
import core.NetworkInterface;
import core.SeedGeneratorHelper;
import core.Settings;
import core.SimClock;
import core.SimError;

/**
 * Superclass for message routers.
 */
public abstract class MessageRouter {
	/** Enum that discerns the reasons underlying a {@link Message} drop. <br/>
	 * REMOVED stands for messages explicitly removed from the cache by the
	 * Router; <br/> DROPPED stands for messages removed for reasons of memory
	 * limits; <br/> DISCARDED stands for messages correctly received, but never
	 * stored in the Router's cache; <br/> TTL_EXPIRATION stands for messages
	 * deleted because their TTL expired while on the node. */
	public static enum MessageDropMode {REMOVED, DROPPED, DISCARDED, TTL_EXPIRATION};
	
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
	/** Receive return value for not enough space in cache for the msg */
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


	/** TTL for all messages */
	protected final int msgTTL;
	/** List of current neighbors */
	private HashSet<DTNHost> neighborsList;
	/** Message cache manager */
	private MessageCacheManager messageCacheManager;
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
	
	
	static {
		DTNSim.registerForReset(MessageRouter.class.getCanonicalName());
		reset();
	}
	
	static protected int nextRandomInt() {
		return RANDOM_GENERATOR.nextInt();
	}
	
	static protected int nextRandomInt(int n) {
		return RANDOM_GENERATOR.nextInt(n);
	}
	
	static protected double nextRandomDouble() {
		return RANDOM_GENERATOR.nextDouble();
	}
	
	static public void reset() {
		RANDOM_GENERATOR = null;
	}
	
	/**
	 * Constructor. Creates a new message router based
	 * on the settings in the given Settings object.
	 * @param s The settings object
	 */
	public MessageRouter(Settings s) {
		this.msgTTL = s.contains(MSG_TTL_S) ? s.getInt(MSG_TTL_S) : Message.INFINITE_TTL;
		this.messageCacheManager = new MessageCacheManager(s);
		this.applications = new HashMap<String, Collection<Application>>();
		
		if (RANDOM_GENERATOR == null) {
			// Singleton
			RANDOM_GENERATOR = new MersenneTwisterRNG(
					SeedGeneratorHelper.get16BytesSeedFromValue(RANDOM_GENERATOR_SEED));
			RANDOM_GENERATOR.setSeed(RANDOM_GENERATOR_SEED);
		}
	}
	
	/**
	 * Copy-constructor.
	 * @param r Router to copy the settings from.
	 */
	protected MessageRouter(MessageRouter r) {
		this.msgTTL = r.msgTTL;
		this.messageCacheManager = new MessageCacheManager(r.messageCacheManager);
		
		this.applications = new HashMap<String, Collection<Application>>();		
		for (Collection<Application> apps : r.applications.values()) {
			for (Application app : apps) {
				addApplication(app.replicate());
			}
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
		this.neighborsList = new HashSet<DTNHost>();
		this.deliveredMessages = new HashMap<String, Message>();
		this.receivedMessages = new HashMap<String, Message>();
		this.mListeners = mListeners;
		this.host = host;
	}
	
	/**
	 * Creates a replicate of this router. The replicate has the same
	 * settings as this router but empty cache and routing tables.
	 * @return The replicate
	 */
	public abstract MessageRouter replicate();

	/**
	 * Returns the host this router is in
	 * @return The host object
	 */
	final protected DTNHost getHost() {
		return host;
	}

	/**
	 * Returns whether the specified host is a neighbor or not
	 * @param neighborHost the {@link DTNHost} we want to check
	 * @return {@code true} if the specified host is a neighbor,
	 * or {@code false} otherwise.
	 */
	final protected boolean isNeighboringHost(DTNHost neighborHost) {
		for (NetworkInterface ni : getNetworkInterfaces()) {
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
	final protected boolean isNeighboringHost(NetworkInterface ni, DTNHost neighborHost) {
		for (Connection con : ni.getConnections()) {
			if (con.getOtherNode(getHost()) == neighborHost) {
				return true;
			}
		}
		
		return false;
	}
	
	/**
	 * This method updates the old neighbors list with the present
	 * list of neighboring nodes and it returns an information
	 * about whether there are any new neighbors nearby since
	 * the last time the method was called.
	 * @return {@code true} if there are new neighbors that fell
	 * under connection range of a {@link NetworkInterface},
	 * or {@code false} otherwise.
	 */
	protected final boolean updateNeighborsList() {
		boolean newNeighbors = false;
		HashSet<DTNHost> presentList = new HashSet<DTNHost>();
		
		for (NetworkInterface ni : getNetworkInterfaces()) {
			for (Connection con : ni.getConnections()) {
				DTNHost neighbor = con.getOtherNode(getHost()); 
				presentList.add(neighbor);
				newNeighbors |= !neighborsList.contains(neighbor);
			}
		}
		// TODO: Update neighborsList according to received HELLO messages
		//neighborsList = presentList;
		
		return newNeighbors;
	}

	/**
	 * Returns the {@link RoutingInfo} about this Router.
	 * @return The {@link RoutingInfo} about this Router.
	 */
	public RoutingInfo getRoutingInfo() {
		RoutingInfo ri = new RoutingInfo(this);
		
		int incomingMessages = 0;
		for (NetworkInterface ni : getNetworkInterfaces()) {
			incomingMessages += ni.getNumberOfIncomingMessages();
		}
		RoutingInfo incoming = new RoutingInfo(incomingMessages + " incoming message(s)");
		RoutingInfo delivered = new RoutingInfo(deliveredMessages.size() + " delivered message(s)");
		RoutingInfo cons = new RoutingInfo(getConnections().size() + " connection(s)");
		ri.addMoreInfo(incoming);
		ri.addMoreInfo(delivered);
		ri.addMoreInfo(cons);
		
		for (NetworkInterface ni : getNetworkInterfaces()) {
			for (Message m : ni.getIncomingMessages()) {
				incoming.addMoreInfo(new RoutingInfo(m));
			}
		}
		for (Message m : deliveredMessages.values()) {
			delivered.addMoreInfo(new RoutingInfo(m + " path:" + m.getHops()));
		}
		for (Connection c : getConnections()) {
			cons.addMoreInfo(new RoutingInfo(c));
		}
		
		return ri;
	}

	/** 
	 * Adds an {@link Application} to the {@link List}
	 * of Applications installed on this Router. 
	 * @param app The {@link Application} to attach
	 * to this router.
	 */
	final public void addApplication(Application app) {
		if (!applications.containsKey(app.getAppID())) {
			applications.put(app.getAppID(), new LinkedList<Application>());
		}
		applications.get(app.getAppID()).add(app);
	}

	/** 
	 * Returns all the {@link Application}s that want to receive
	 * {@link Message}s for the given application ID. 
	 * @param ID A {@link String} representing the ID of an
	 * {@link Application}, or {@code null} for all apps.
	 * @return A list of all {@link Application}s that
	 * want to receive {@link Message}s for the Application
	 * ID passed as parameter.
	 */
	final public Collection<Application> getApplications(String ID) {
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
	 * Returns a {@link List} of {@link Connection}s
	 * this host currently has with other hosts.
	 * @return a {@link List} of {@link Connection}s
	 * this host currently has with other hosts.
	 */
	@Deprecated
	public Connection getConnectionTo(DTNHost dest) {
		assert dest != getHost() : "Source and destination nodes are the same node";
		
		for (Connection con : getConnections()) {
			if (con.getOtherNode(getHost()) == dest) {
				return con;
			}
		}
		
		return null;
	}

	/**
	 * Returns a {@link List} of {@link Connection}s
	 * this host currently has with other hosts.
	 * @return a {@link List} of {@link Connection}s
	 * this host currently has with other hosts.
	 */
	final protected List<Connection> getConnections() {
		return getHost().getConnections();
	}

	/**
	 * Returns a {@link List} of idle {@link Connection}s
	 * this host currently has with other hosts.
	 * @return a {@link List} of idle {@link Connection}s
	 * this host currently has with other hosts.
	 */
	final protected List<Connection> getIdleConnections() {
		List<Connection> idleConnections = new ArrayList<Connection>();
		for (Connection con : getConnections()) {
			if (con.isIdle()) {
				idleConnections.add(con);
			}
		}
		
		return idleConnections;
	}

	/**
	 * Returns a {@link List} containing all
	 * {@link NetworkInterface}s of this host.
	 * @return a {@link List} containing all
	 * {@link NetworkInterface}s of this host.
	 */
	final protected List<NetworkInterface> getNetworkInterfaces() {
		return getHost().getInterfaces();
	}

	/**
	 * Returns the first idle {@link NetworkInterface}s available.
	 * @return the first {@link NetworkInterface} ready to begin
	 * a new transfer.
	 */
	final protected NetworkInterface getNextIdleInterface() {
		for (NetworkInterface ni : getNetworkInterfaces()) {
			if (ni.isReadyToBeginTransfer()) {
				return ni;
			}
		}
		
		return null;
	}

	/**
	 * Returns a {@link List} of all {@link NetworkInterface}
	 * ready to begin a new transfer.
	 * @return The {@link List} of all idle {@link NetworkInterface} 
	 */
	final protected List<NetworkInterface> getIdleNetworkInterfaces() {
		List<NetworkInterface> idleInterfaces = new ArrayList<NetworkInterface>();
		for (NetworkInterface ni : getNetworkInterfaces()) {
			if (ni.isReadyToBeginTransfer()) {
				idleInterfaces.add(ni);
			}
		}
		
		return idleInterfaces;
	}

	/**
	 * Check if there is any traffic from/to this node.
	 * @return {@code true} if a {@link NetworkInterface} is
	 * sending any data, {@code false} otherwise.
	 */	
	final protected boolean isTransferring() {
		for (NetworkInterface ni : getNetworkInterfaces()) {
			if (ni.isSendingData() || ni.isReceivingData()) {
				return true;
			}
		}
		
		return false;
	}

	/**
	 * Returns true if this router is currently sending the
	 * message with ID {@code msgID}.
	 * @param msgID The ID of the message.
	 * @return {@code true} if the message is being sent,
	 * {@code false} otherwise.
	 */
	final protected boolean isSendingMessage(String msgID) {
		for (NetworkInterface ni : getNetworkInterfaces()) {
			if (ni.isSendingMessage(msgID)) {
				return true;
			}
		}
		
		return false;
	}

	/**
	 * Returns the number of messages this router has
	 * @return How many messages this router has
	 */
	final public int getNrofMessages() {
		return messageCacheManager.getNumberOfMessages();
	}

	/**
	 * Creates a new {@link Message} to store in cache.
	 * By default, newly created messages have higher
	 * priority than any other message in cache.
	 * @param m The {@link Message} just created.
	 * @return True if the creation succeeded, false if not
	 * (e.g., the message was too big for the cache).
	 */
	public boolean createNewMessage(Message m) {
		if (makeRoomForNewMessage(m.getSize(), Message.MAX_PRIORITY_LEVEL)) {
			m.setTtl(msgTTL);
			addToMessages(m);
			
			for (MessageListener ml : mListeners) {
				ml.newMessage(m);
			}
			
			return true;
		}
		
		return false;
	}

	/**
	 * Adds a message to the cache and informs message listeners
	 * about new message (if requested).
	 * @param m The message to add
	 * @param newMessage If true, message listeners are informed about a new
	 * message, if false, nothing is informed.
	 */
	final protected void addToMessages(Message m) {
		if (messageCacheManager.hasMessage(m)) {
			return;
		}
		
		messageCacheManager.addMessageToQueue(m);
	}

	/**
	 * Returns a {@link Message} by ID.
	 * @param id ID of the message
	 * @return The message
	 */
	final protected Message getMessage(String msgID) {
		return messageCacheManager.getMessage(msgID);
	}

	/**
	 * Returns a shallow copy of the list of messages in cache.
	 * Being a shallow copy, items in this list point to the
	 * actual messages in cache. However, deletion of elements
	 * from the list does not delete messages from cache.
	 * @return a shallow copy of the list of messages in cache.
	 */
	final public List<Message> getMessageList() {
		return new ArrayList<Message>(messageCacheManager.getMessageCollection());
	}

	/**
	 * Checks if this router has any cached message with a specific id.
	 * @param id Identifier of the message
	 * @return True if the router has message with
	 * the specified id, false if not
	 */
	final protected boolean hasMessage(String msgID) {
		return messageCacheManager.hasMessage(msgID);
	}

	/**
	 * Checks if this router has received a {@link Message}
	 * with the specified identifier in the past.
	 * @param msgID Identifier of the message.
	 * @return {@code true} if the router has message
	 * with this id, {@code false} otherwise.
	 */
	final protected boolean hasReceivedMessage(String msgID) {
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
	final protected boolean isIncomingMessage(String msgID) {
		for (NetworkInterface ni : getNetworkInterfaces()) {
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
	final protected boolean isDeliveredMessage(Message m) {
		return deliveredMessages.containsKey(m.getID());
	}

	/**
	 * Deletes a message from cache and informs message listeners about the event
	 * @param id Identifier of the message to delete
	 * @param drop If the message is dropped (e.g. because of full cache) this 
	 * should be set to true. False value indicates e.g. remove of message
	 * because it was delivered to final destination.  
	 */
	final public void deleteMessage(String id, MessageDropMode dropMode, String cause) {
		Message removed = removeFromMessages(id); 
		if (removed == null) {
			throw new SimError("No message for id " + id + " to remove at " + getHost());
		}
		
		notifyListenersAboutMessageDelete(removed, dropMode, cause);
	}

	/**
	 * Deletes a message from cache without informing
	 * message listeners about the event
	 * @param msgID Identifier of the message to delete
	 * because it was delivered to final destination.  
	 */
	final protected void deleteMessageWithoutRaisingEvents(String msgID) {
		if (null == removeFromMessages(msgID)) {
			throw new SimError("No message for id " + msgID +
								" to remove at " + getHost());
		}
	}

	/**
	 * Removes and returns a message from the cache.
	 * @param id Identifier of the message to remove
	 * @return The removed message or null if message for the ID wasn't found
	 */
	private Message removeFromMessages(String msgID) {
		return messageCacheManager.removeMessage(msgID);
	}

	/**
	 * Drops messages whose TTL is less than zero.
	 */
	protected void removeExpiredMessagesFromCache() {
		for (Message m : getMessageList()) {
			if (m.getTtl() <= 0) {				
				deleteMessage(m.getID(), MessageDropMode.TTL_EXPIRATION, "TTL expired");
			}
		}
	}

	/**
	 * Informs message listeners about the transmission completed event.
	 * @param m The {@link Message} deleted.  
	 */
	final protected void notifyListenersAboutTransmissionCompleted(Message m) {
		if (m == null) {
			return;
		}
		
		for (MessageListener ml : mListeners) {
			ml.transmissionPerformed(m, getHost());
		}
	}

	/**
	 * Informs message listeners about the {@link Message} transfer event.
	 * @param aMessage The transferred message.
	 * @param con The {@link Connection} transferring the message.
	 * @param isFirstDelivery True if the message reached the host for the first time.
	 * @param isFinalTarget True if the host was the destination of the message.
	 */
	protected final void notifyListenersAboutMessageTransferred(Message aMessage, Connection con,
																boolean isFirstDelivery,
																boolean isFinalTarget) {
		for (MessageListener ml : mListeners) {
			ml.messageTransferred(aMessage, con.getSenderNode(), getHost(),
									isFirstDelivery, isFinalTarget);
		}
	}

	/**
	 * Informs message listeners about the delete event.
	 * @param removedMessage The {@link Message} deleted.
	 * @param dropMode A {@link MessageDropMode} value to
	 * describe the reason of the deletion.
	 * @param cause The cause of the deletion.
	 */
	final protected void notifyListenersAboutMessageDelete(Message removedMessage,
															MessageDropMode dropMode,
															String cause) {
		if (removedMessage == null) {
			return;
		}
		
		for (MessageListener ml : mListeners) {
			ml.messageDeleted(removedMessage, getHost(), dropMode, cause);
		}
	}

	/**
	 * Returns the total size of the caching memory.
	 * @return The size or Integer.MAX_VALUE if the size isn't defined.
	 */
	final public int getCacheSize() {
		return messageCacheManager.getCacheSize();
	}

	/**
	 * Returns the amount of free space in cache. May return a negative
	 * value if there are more cached messages than they could fit
	 * because new messages are being created.
	 * @return The amount of free space (Integer.MAX_VALUE if
	 * cache size isn't defined)
	 */
	final public int getFreeCacheSize() {
		return messageCacheManager.getFreeCacheSize();
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
		return messageCacheManager.compareByPrioritizationMode(m1, m2);
	}

	/**
	 * Sorts a copy of the list containing all messages received by the
	 * router, according to the current message prioritization strategy.
	 * @return A copy of the list of the received messages, sorted
	 * according to the current message prioritization strategy.
	 */
	protected List<Message> sortAllReceivedMessagesForForwarding() {
		return messageCacheManager.sortCachedMessagesForForwarding();
	}

	/**
	 * Sorts the given list according to the current sending queue strategy.
	 * @param inputList The list to sort
	 * @return The sorted list
	 */
	protected List<Message> sortListOfMessagesForForwarding(List<Message> inputList) {
		return messageCacheManager.sortMessageListForForwarding(inputList);
	}

	/**
	 * Sorts the given list in reverse order, according to the current
	 * sending queue strategy. The list can contain either Message or
	 * Tuple<Message, Connection> objects. Other objects cause error.
	 * @param list The list to sort
	 * @return The list sorted in reverse order
	 */
	protected List<Message> getListOfMessagesInReversePriorityOrder(List<Message> inputList) {
		messageCacheManager.sortByReversedPrioritizationMode(inputList);
		return inputList;
	}

	/**
	 * Returns the oldest (by receive time) message in cache 
	 * (that is not being sent if excludeMsgBeingSent is true).
	 * @param excludeMsgBeingSent If true, excludes message(s) that are
	 * being sent from the oldest message check (i.e. if oldest message is
	 * being sent, the second oldest message is returned)
	 * @return The oldest message or null if no message could be returned
	 * (no cached messages or all cached messages are being sent and
	 * exludeMsgBeingSent is true)
	 */
	protected Message getLeastImportantMessageInCache(boolean excludeMsgBeingSent) {		
		List<Message> sortedList = getListOfMessagesInReversePriorityOrder(getMessageList());
		
		// Traverse the list in order and return the first available message
		for (Message m : sortedList) {
			if (excludeMsgBeingSent && isSendingMessage(m.getID())) {
				// skip the message(s) that router is sending
				continue;
			}
			return m;
		}
		
		return null;
	}

	/**
	 * Returns {@code true} if the router has any free
	 * {@link NetworkInterface}, or {@code false} otherwise.
	 * @return a boolean value to determine if any
	 * {@link NetworkInterface} is available to
	 * start a new transfer.
	 */
	protected boolean canBeginNewTransfer() {
		return (getNrofMessages() > 0) && (getIdleNetworkInterfaces().size() > 0);
	}

	/**
	 * Checks if router can start receiving the {@link Message}
	 * (i.e. router isn't transferring and there are no interferences).
	 * @param m The {@link Message} to check
	 * @param con The {@link Connection} transferring the {@link Message}
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
	 * Informs the router about change in connections state.
	 * @param con The connection that changed
	 */
	public abstract void changedConnection(Connection con);

	/**
	 * This method should be called (on the receiving host) when a
	 * {@link Message} transfer was aborted.
	 * @param msgID {@link String} ID of the {@link Message}
	 * that was being transferred.
	 * @param con The {@link Connection} that was aborted.
	 * @param motivation A {@link String} describing the issue
	 * that caused the abortion. 
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
	 * Notify the router that this connection has been interfered,
	 * so that it can react accordingly. The Default action is to
	 * notify the interference models and all the listeners.
	 * @param con The connection which is transferring the message
	 */
	final public void messageInterfered(String msgID, Connection con) {
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
	 * Try to start receiving a message from another host.
	 * @param m {@link Message} to put in cache.
	 * @param con The {@link Connection} transferring the message.
	 * @return Value zero if the node accepted the message (RCV_OK),
	 * value less than zero if node rejected the message (e.g.
	 * {@code DENIED_OLD}), value bigger than zero if the other
	 * node should try later (e.g. {@code TRY_LATER_BUSY}).
	 */
	public int receiveMessage(Message m, Connection con) {
		Message newMessage = m.replicate();
		NetworkInterface receivingInterface = con.getReceiverInterface();
		if (!getNetworkInterfaces().contains(receivingInterface)) {
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
	 * was successfully transferred. The transferred message is put in
	 * cache unless this host is the final recipient of the message.
	 * @param id Id of the transferred message
	 * @param from Host the message was from (previous hop)
	 * @return The message that this host received
	 */
	public Message messageTransferred(String msgID, Connection con) throws SimError {
		boolean isFinalTarget;
		boolean isFirstDelivery;	// is this the first delivered instance of the msg?
		
		Message incoming = retrieveTransferredMessageFromInterface(msgID, con);
		if (incoming == null) {
			// Message interfered or reception out-of-synch --> nothing to do
			return null;
		}
		incoming.setReceiveTime(SimClock.getTime());
		incoming.addNodeOnPath(getHost());
		
		// Pass the message to the application (if any) and get outgoing message
		// TODO: Fix the logic of application-related issues to 
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
		isFinalTarget = isMessageDestination(aMessage);
		isFirstDelivery = !hasReceivedMessage(aMessage.getID());
	
		/* Messages are stored in cache regardless they are addressed
		 * to this node or not, unless any application wants to drop it. */
		if (isFirstDelivery && (outgoing != null)) {
			/* No app wants to drop it and it is the first time
			 * the message was received -> cache it */
			if (!makeRoomForMessage(aMessage.getSize(), aMessage.getPriority())) {
				// Drop message due to insufficient space in cache
				notifyListenersAboutMessageDelete(aMessage, MessageDropMode.DROPPED,
						"Impossible to free enough space from cache");
			}
			addToMessages(aMessage);
			receivedMessages.put(msgID, aMessage);
			if (isFinalTarget) {
				// This node is the message destination
				deliveredMessages.put(msgID, aMessage);
			}
		}
		
		notifyListenersAboutMessageTransferred(aMessage, con, isFirstDelivery, isFinalTarget);
		return aMessage;
	}

	/** 
	 * Removes messages from cache (oldest and lowest priority first)
	 * until there's enough space for the new message.
	 * If admissible message removals are not enough to free required space,
	 * all performed deletes are rolled back.
	 * @param size Size of the new message transferred, the
	 * transfer is aborted before message is removed
	 * @param priority Priority level of the new message
	 * @return True if enough space could be freed, false if not
	 */
	protected boolean makeRoomForMessage(int size, int priority) {
		if (size > getCacheSize()) {
			// message too big for the cache
			return false;
		}
		
		int freeCache = getFreeCacheSize();
		ArrayList<Message> deletedMessages = new ArrayList<Message>();
		// delete messages from cache until there's enough space
		while (freeCache < size) {
			// can't remove messages being sent --> use true as parameter
			Message m = getLeastImportantMessageInCache(true);
			if ((m == null) || (m.getPriority() > priority)) {
				// can't remove any more messages
				break;
			}
			
			deleteMessageWithoutRaisingEvents(m.getID());
			deletedMessages.add(m);
			freeCache += m.getSize();
		}
		
		/* notify message drops only if necessary amount of space was freed */
		if (freeCache < size) {
			// rollback deletes and return false
			for (Message m : deletedMessages) {
				addToMessages(m);
			}
			return false;
		}
	
		// commit deletes by notifying event listeners about the deletes
		for (Message m : deletedMessages) {
			// delete message from cache as "removed" (false)
			notifyListenersAboutMessageDelete(m, MessageDropMode.DROPPED,
												"Cache size exceeded");
		}
		
		return true;
	}

	/**
	 * Tries to make room for a new message. Current implementation simply
	 * calls {@link #makeRoomForMessage(int, int)} and ignores the return value.
	 * Therefore, if the message can't fit in cache, the cache is only 
	 * cleared from messages that are not being sent.
	 * @param size Size of the new message
	 * @param priority Priority level of the new message
	 */
	protected boolean makeRoomForNewMessage(int size, int priority) {
		return makeRoomForMessage(size, priority);
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
	final protected Message retrieveTransferredMessageFromInterface(String msgID, Connection con)
																throws SimError {
		NetworkInterface receivingInterface = con.getReceiverInterface();
		if (!getNetworkInterfaces().contains(receivingInterface)) {
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
	 * Check if this {@link DTNHost} is the final destination of the
	 * specified {@link Message}. Subclasses can redefine the method
	 * to change its logic; e.g., to use subscriptions. 
	 * @param aMessage The received {@link Message}.
	 * @return {@code true} if this {@link MessageRouter} is the final
	 * destination (or one of them) of the received {@link Message},
	 * or {@code false} otherwise.
	 */
	protected boolean isMessageDestination(Message aMessage) {
		return (aMessage == null) ? false : aMessage.getTo() == getHost();
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
	 * Hook method that returns whether the specified {@link Message}
	 * coming from the specified {@link DTNHost} should be delivered
	 * to this host. This method returns {@code true} as its default
	 * behavior. Subclasses should overwrite it.
	 * @param m The Message that might need to be delivered.
	 * @param to The host that might need the Message.
	 * @return {@code true} if the specified host needs the
	 * Message, {@code false} otherwise.
	 */
	protected boolean shouldBeDeliveredMessageFromHost(Message m, DTNHost from) {
		return true;
	}

	/**
	 * Hook method that returns whether the specified {@link Message}
	 * needs to be delivered to the selected {@link DTNHost}.
	 * This method returns the value of a call to
	 * {@link MessageRouter#shouldBeDeliveredMessageFromHost(Message, DTNHost)}
	 * as its default behavior. Subclasses can overwrite it.
	 * @param m The {@link Message} that might need to be delivered.
	 * @param to The {@link DTNHost} that might need the Message.
	 * @return {@code true} if the specified host needs the
	 * Message, {@code false} otherwise.
	 */
	protected boolean shouldDeliverMessageToHost(Message m, DTNHost to) {
		return to.getRouter().shouldBeDeliveredMessageFromHost(m, getHost());
	}

	/**
	 * Start sending a message to another host.
	 * @param id Id of the message to send
	 * @param to The host to send the message to
	 */
	@Deprecated
	public void sendMessage(String id, DTNHost to) {
		Message m = getMessage(id);
		if (m == null) throw new SimError("no message for id " + id + " to send at " + getHost());
	
		// send a replication of the message
		Message m2 = m.replicate();
		for (Connection con : getConnections()) {
			if (con.getOtherNode(getHost()) == to) {
				to.receiveMessage(m2, con);
				return;
			}
		}
		
		throw new SimError("No connection to host " + to + " from host " + getHost());
	}

	/**
	 * Returns a String presentation of this router
	 * @return A String presentation of this router
	 */
	public String toString() {
		return getClass().getSimpleName() + " of " + getHost() + " with " +
				getNrofMessages() + " messages";
	}
	
}
