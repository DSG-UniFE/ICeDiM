/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package routing;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import strategies.MessageForwardingOrderStrategy;

import core.Application;
import core.Connection;
import core.DTNHost;
import core.InterferenceModel;
import core.Message;
import core.MessageListener;
import core.NetworkInterface;
import core.Settings;
import core.SettingsError;
import core.SimClock;
import core.SimError;

/**
 * Superclass for message routers.
 */
public abstract class MessageRouter {
	/** Message buffer size -setting id ({@value}). Integer value in bytes.*/
	public static final String B_SIZE_S = "bufferSize";
	/**
	 * Message TTL -setting id ({@value}). Value is in minutes and must be
	 * an integer. 
	 */ 
	public static final String MSG_TTL_S = "msgTtl";
	/**
	 * Message/fragment sending queue type -setting id ({@value}). 
	 * This setting affects the order the messages and fragments are sent if the
	 * routing protocol doesn't define any particular order (e.g, if more than 
	 * one message can be sent directly to the final recipient). 
	 * Valid values are<BR>
	 * <UL>
	 * <LI/> 0 : random (message order is randomized every time; default option)
	 * <LI/> 1 : FIFO (most recently received messages are sent last)
	 * <LI/> 2 : Prioritized_FIFO (FIFO with highest priority messages are sent first)
	 * <LI/> 3 : Prioritized_LFF_FIFO (Prioritized_FIFO with least forwarded messages sent first - attempt to be fairer)
	 * </UL>
	 */ 
	public static enum QueueForwardingOrderMode {Random, FIFO, Prioritized_FIFO, Prioritized_LFF_FIFO}
	
	public static final String SEND_QUEUE_MODE_S = "sendQueueMode";	

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
	/** Strategy which implements the specified queue mode */
	public MessageForwardingOrderStrategy messageForwardingStrategy;
	
	private List<MessageListener> mListeners;
	/** The messages this router is carrying */
	private HashMap<String, Message> messages; 
	/** The messages this router has received as the final recipient */
	private HashMap<String, Message> deliveredMessages;
	/** Host where this router belongs to */
	private DTNHost host;
	/** size of the buffer */
	private int bufferSize;
	/** TTL for all messages */
	protected int msgTtl;

	/** applications attached to the host */
	private HashMap<String, Collection<Application>> applications = null;
	
	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object. Size of the message buffer is read from
	 * {@link #B_SIZE_S} setting. Default value is Integer.MAX_VALUE.
	 * @param s The settings object
	 */
	public MessageRouter(Settings s) {
		this.bufferSize = Integer.MAX_VALUE; // defaults to rather large buffer	
		this.msgTtl = Message.INFINITE_TTL;
		this.applications = new HashMap<String, Collection<Application>>();
		
		if (s.contains(B_SIZE_S)) {
			this.bufferSize = s.getInt(B_SIZE_S);
		}
		if (s.contains(MSG_TTL_S)) {
			this.msgTtl = s.getInt(MSG_TTL_S);
		}
		
		int sendQueueMode = 0;
		if (s.contains(SEND_QUEUE_MODE_S)) {
			sendQueueMode = s.getInt(SEND_QUEUE_MODE_S);
			if (sendQueueMode < 0 || sendQueueMode >= QueueForwardingOrderMode.values().length) {
				throw new SettingsError("Invalid value for " + s.getFullPropertyName(SEND_QUEUE_MODE_S));
			}
		}
		messageForwardingStrategy = MessageForwardingOrderStrategy.MessageForwardingStrategyFactory
													(QueueForwardingOrderMode.values()[sendQueueMode]);
	}
	
	/**
	 * Initializes the router; i.e. sets the host this router is in and
	 * message listeners that need to be informed about message related
	 * events etc.
	 * @param host The host this router is in
	 * @param mListeners The message listeners
	 */
	public void init(DTNHost host, List<MessageListener> mListeners) {
		this.messages = new HashMap<String, Message>();
		this.deliveredMessages = new HashMap<String, Message>();
		this.mListeners = mListeners;
		this.host = host;
	}
	
	/**
	 * Copy-constructor.
	 * @param r Router to copy the settings from.
	 */
	protected MessageRouter(MessageRouter r) {
		this.bufferSize = r.bufferSize;
		this.msgTtl = r.msgTtl;
		this.messageForwardingStrategy = r.messageForwardingStrategy;

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
		for (Collection<Application> apps : this.applications.values()) {
			for (Application app : apps) {
				app.update(this.host);
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
	protected Message getMessage(String id) {
		return this.messages.get(id);
	}
	
	/**
	 * Checks if this router has a message with certain id buffered.
	 * @param id Identifier of the message
	 * @return True if the router has message with this id, false if not
	 */
	protected boolean hasMessage(String id) {
		return this.messages.containsKey(id);
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
	 * Returns true if a full message with same ID as the given message has been
	 * received by this host as the <strong>final</strong> recipient 
	 * (at least once).
	 * @param m message we're interested of
	 * @return true if a message with the same ID has been received by 
	 * this host as the final recipient.
	 */
	protected boolean isDeliveredMessage(Message m) {
		return (this.deliveredMessages.containsKey(m.getID()));
	}
	
	/**
	 * Returns a reference to the messages of this router in collection.
	 * <b>Note:</b> If there's a chance that some message(s) from the collection
	 * could be deleted (or added) while iterating through the collection, a
	 * copy of the collection should be made to avoid concurrent modification
	 * exceptions. 
	 * @return a reference to the messages of this router in collection
	 */
	public Collection<Message> getMessageCollection() {
		return this.messages.values();
	}
	
	/**
	 * Returns the number of messages this router has
	 * @return How many messages this router has
	 */
	public int getNrofMessages() {
		return this.messages.size();
	}
	
	/**
	 * Returns the size of the message buffer.
	 * @return The size or Integer.MAX_VALUE if the size isn't defined.
	 */
	public int getBufferSize() {
		return this.bufferSize;
	}
	
	/**
	 * Returns the amount of free space in the buffer. May return a negative
	 * value if there are more messages in the buffer than should fit there
	 * (because of creating new messages).
	 * @return The amount of free space (Integer.MAX_VALUE if the buffer
	 * size isn't defined)
	 */
	public int getFreeBufferSize() {
		int occupancy = 0;
		
		if (this.getBufferSize() == Integer.MAX_VALUE) {
			return Integer.MAX_VALUE;
		}
		
		for (Message m : getMessageCollection()) {
			occupancy += m.getSize();
		}
		
		return this.getBufferSize() - occupancy;
	}
	
	/**
	 * Returns the host this router is in
	 * @return The host object
	 */
	protected DTNHost getHost() {
		return this.host;
	}
	
	/**
	 * Start sending a message to another host.
	 * @param id Id of the message to send
	 * @param to The host to send the message to
	 */
	public void sendMessage(String id, DTNHost to) {
		Message m = getMessage(id);
		if (m == null) throw new SimError("no message for id " +
				id + " to send at " + this.host);
 
		Message m2 = m.replicate();	// send a replicate of the message
		for (Connection con : getHost().getConnections()) {
			if (con.getOtherNode(getHost()) == to) {
				to.receiveMessage(m2, con);
				return;
			}
		}
		
		throw new SimError("No connection to host " + to + " from host " + getHost());
	}
	
	/**
	 * Requests for deliverable message from this router to be sent trough a
	 * connection.
	 * @param con The connection to send the messages trough
	 * @return True if this router started a transfer, false if not
	 */
	public boolean requestDeliverableMessages(Connection con) {
		return false; // default behavior is to not start -- subclasses override
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
			for (MessageListener ml : this.mListeners) {
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
	 * @param m Message to put in the receiving buffer
	 * @param from Who the message is from
	 * @return Value zero if the node accepted the message (RCV_OK), value less
	 * than zero if node rejected the message (e.g. DENIED_OLD), value bigger
	 * than zero if the other node should try later (e.g. TRY_LATER_BUSY).
	 */
	public int receiveMessage(Message m, Connection con) {
		Message newMessage = m.replicate();
		NetworkInterface receivingInterface = con.getReceiverInterface();
		
		int receptionValue = receivingInterface.beginNewReception(newMessage, con);
		for (MessageListener ml : this.mListeners) {
			ml.messageTransferStarted(newMessage, con.getSenderNode(), getHost());
		}
		
		if (receptionValue == InterferenceModel.RECEPTION_DENIED_DUE_TO_SEND) {
			throw new SimError("Receive failed due to transmitting interface. " + 
								"CSMA/CA should avoid these situations");
		}
		if (receptionValue == InterferenceModel.RECEPTION_INTERFERENCE) {
			// The new reception failed, triggering an interference
			return DENIED_INTERFERENCE;
		}
		
		 // superclass accepts messages if the interference model accepts it
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
	public Message messageTransferred(String msgID, Connection con) {
		boolean isFinalRecipient;
		boolean isFirstDelivery; // is this first delivered instance of the msg
		
		NetworkInterface receivingInterface = con.getReceiverInterface();
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
				throw new SimError("A message was returned by NetworkInterface.retrieveTransferredMessage() " + 
									"method even if an interference was detected");
			}
			
			for (MessageListener ml : mListeners) {
				ml.messageTransmissionInterfered(con.getMessage(), con.getSenderNode(), con.getReceiverNode());
			}
			return null;
		}

		// receiveResult == InterferenceModel.RECEPTION_COMPLETED_CORRECTLY
		Message incoming = receivingInterface.retrieveTransferredMessage(msgID, con);
		if (incoming == null) {
			throw new SimError("Impossible to retrieve message with ID " + msgID + 
								" from the Interference Model");
		}
		incoming.setReceiveTime(SimClock.getTime());
		incoming.addNodeOnPath(this.host);	// Moved this instruction here from messageReceived()
		
		// Pass the message to the application (if any) and get outgoing message
		Message outgoing = incoming;
		for (Application app : getApplications(incoming.getAppID())) {
			// Note that the order of applications is significant
			// since the next one gets the output of the previous.
			outgoing = app.handle(outgoing, this.host);
			if (outgoing == null) break; // Some app wanted to drop the message
		}
		
		Message aMessage = (outgoing == null) ? (incoming) : (outgoing);
		// If the application re-targets the message (changes 'to')
		// then the message is not considered as 'delivered' to this host.
		isFinalRecipient = aMessage.getTo() == this.host;
		isFirstDelivery = isFinalRecipient && !isDeliveredMessage(aMessage);

		if (!isFinalRecipient && (outgoing != null)) {
			// not the final recipient and app doesn't want to drop the message
			// -> put it into the buffer
			addToMessages(aMessage, false);
		}
		else if (isFirstDelivery) {
			this.deliveredMessages.put(msgID, aMessage);
		}
		
		for (MessageListener ml : this.mListeners) {
			ml.messageTransferred(aMessage, con.getSenderNode(), this.host, isFirstDelivery);
		}
		
		return aMessage;
	}
	
	/**
	 * This method should be called (on the receiving host) when a message 
	 * transfer was aborted.
	 * @param id Id of the message that was being transferred
	 * @param from Host the message was from (previous hop)
	 * would have been ready; or -1 if the number of bytes is not known
	 */
	public void messageAborted(String msgID, Connection con) {
		// TODO: add support for out-of-synch received messages in the interference model
		NetworkInterface receivingInterface = con.getReceiverInterface();
		int receiveResult = receivingInterface.isMessageTransferredCorrectly(msgID, con);
		if (receiveResult == InterferenceModel.MESSAGE_ID_NOT_FOUND) {
			// Abortion is not necessary
			return;
		}
		else if (receiveResult != InterferenceModel.RECEPTION_INCOMPLETE) {
			throw new SimError("isMessageTransferredCorrectly() method invoked with message" +
								" with ID " + msgID + " returned reception incomplete;");
		}

		Message abortedMessage = receivingInterface.abortMessageReception(con);
		if (abortedMessage != null) {
			for (MessageListener ml : this.mListeners) {
				ml.messageTransferAborted(abortedMessage, con.getSenderNode(), this.host);
			}
		}
		else {
			throw new SimError("No incoming message for id " + msgID + " to abort in " + this.host);
		}
	}
	
	/**
	 * Puts a message to incoming messages buffer. Two messages with the
	 * same ID are distinguished by the from host.
	 * @param m The message to put
	 * @param from Who the message was from (previous hop).
	 */
//	protected void putToIncomingBuffer(Message m, Connection con) {
//		this.incomingMessages.put(m.getId() + "_i" + con.getSenderInterface().getAddress(),
//									new Tuple<Message, Connection>(m, con));
//	}
	
	/**
	 * Removes and returns a message with a certain ID from the incoming 
	 * messages buffer or null if such message wasn't found. 
	 * @param id ID of the message
	 * @param from The host that sent this message (previous hop)
	 * @return The found message or null if such message wasn't found
	 */
//	protected Tuple<Message, Connection> removeFromIncomingBuffer(String id, Connection con) {
//		return this.incomingMessages.remove(id + "_i" + con.getSenderInterface().getAddress());
//	}
	
	/**
	 * Returns true if a message with the given ID is one of the
	 * currently incoming messages, false if not
	 * @param id ID of the message
	 * @return True if such message is incoming right now
	 */
//	protected boolean isIncomingMessage(String id) {
//		return this.incomingMessages.containsKey(id);
//	}
	
	/**
	 * Adds a message to the message buffer and informs message listeners
	 * about new message (if requested).
	 * @param m The message to add
	 * @param newMessage If true, message listeners are informed about a new
	 * message, if false, nothing is informed.
	 */
	protected void addToMessages(Message m, boolean newMessage) {
		if (messages.containsKey(m.getID())) {
			// Message is already in queue
			return;
		}

		setForwardedTimesToMinAmongMessages(m);
		messages.put(m.getID(), m);
		
		if (newMessage) {
			for (MessageListener ml : mListeners) {
				ml.newMessage(m);
			}
		}
	}
	
	private void setForwardedTimesToMinAmongMessages(Message m) {
		if (messages.size() > 0) {
			int min = Integer.MAX_VALUE;
			for (Message msg : messages.values()) {
				if (msg.getForwardTimes() < min) {
					min = msg.getForwardTimes();
				}
			}
			while (m.getForwardTimes() < min) {
					m.incrementForwardTimes();
			}
		}
	}

	/**
	 * Removes and returns a message from the message buffer.
	 * @param id Identifier of the message to remove
	 * @return The removed message or null if message for the ID wasn't found
	 */
	protected Message removeFromMessages(String id) {
		return messages.remove(id);
	}
	
	/**
	 * Creates a new message to the router.
	 * @param m The message to create
	 * @return True if the creation succeeded, false if not (e.g.
	 * the message was too big for the buffer)
	 */
	public boolean createNewMessage(Message m) {
		m.setTtl(msgTtl);
		addToMessages(m, true);
		return true;
	}
	
	/**
	 * Deletes a message from the buffer and informs message listeners
	 * about the event
	 * @param id Identifier of the message to delete
	 * @param drop If the message is dropped (e.g. because of full buffer) this 
	 * should be set to true. False value indicates e.g. remove of message
	 * because it was delivered to final destination.  
	 */
	public void deleteMessage(String id, boolean drop) {
		Message removed = removeFromMessages(id); 
		if (removed == null) {
			throw new SimError("no message for id " + id + " to remove at " + host);
		}
		
		for (MessageListener ml : this.mListeners) {
			ml.messageDeleted(removed, this.host, drop);
		}
	}
	
	/**
	 * Deletes a message from the buffer without informing
	 * message listeners about the event
	 * @param id Identifier of the message to delete
	 * because it was delivered to final destination.  
	 */
	public void deleteMessageWithoutRaisingEvents(String id) {
		Message removed = removeFromMessages(id); 
		if (removed == null) throw new SimError("no message for id " +
				id + " to remove at " + this.host);
	}
	
	/**
	 * Informs message listeners about delete event
	 * @param id Identifier of the message to delete
	 * @param drop If the message is dropped (e.g. because of full buffer) this 
	 * should be set to true. False value indicates e.g. remove of message
	 * because it was delivered to final destination.  
	 */
	public void notifyListenersAboutMessageDelete(Message removed, boolean drop) {		
		for (MessageListener ml : this.mListeners) {
			ml.messageDeleted(removed, this.host, drop);
		}
	}
	
	
	/**
	 * Sorts/shuffles the given list according to the current sending queue 
	 * mode. The list can contain either Message or Tuple<Message, Connection> 
	 * objects. Other objects cause error. 
	 * @param list The list to sort or shuffle
	 * @return The sorted/shuffled list
	 */
	protected <T> List<T> sortByQueueMode(List<T> list) {
		return messageForwardingStrategy.MessageProcessingOrder(list);
	}

	/**
	 * Gives the order of the two given messages as defined by the current
	 * queue mode 
	 * @param m1 The first message
	 * @param m2 The second message
	 * @return -1 if the first message should come first, 1 if the second 
	 *          message should come first, or 0 if the ordering isn't defined
	 */
	protected int compareByQueueMode(Message m1, Message m2) {
		return messageForwardingStrategy.ComparatorMethod(m1, m2);
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
		
		RoutingInfo cons = new RoutingInfo(host.getConnections().size() + 
			" connection(s)");
				
		ri.addMoreInfo(incoming);
		ri.addMoreInfo(delivered);
		ri.addMoreInfo(cons);
		for (NetworkInterface ni : getHost().getInterfaces()) {
			for (Message m : ni.getIncomingMessages()) {
				incoming.addMoreInfo(new RoutingInfo(m));
			}
		}
		
		for (Message m : this.deliveredMessages.values()) {
			delivered.addMoreInfo(new RoutingInfo(m + " path:" + m.getHops()));
		}
		
		for (Connection c : host.getConnections()) {
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
		Collection<Application> tmp = this.applications.get(ID);
		if (tmp != null) {
			apps.addAll(tmp);
		}
		// Applications that want to look at all messages
		if (ID != null) {
			tmp = this.applications.get(null);
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
		return getClass().getSimpleName() + " of " + 
			this.getHost().toString() + " with " + getNrofMessages() 
			+ " messages";
	}
}
