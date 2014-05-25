/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package routing;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.NetworkInterface;
import core.Settings;
import core.SimError;
import core.disService.PublisherSubscriber;
import core.disService.SubscriptionListManager;

/**
 * Implementation of Spray and Wait Router as depicted in 
 * <I>Spray and Wait: An Efficient Routing Scheme for Intermittently
 * Connected Mobile Networks</I> by Thrasyvoulos Spyropoulus et al.
 *
 */
public class SprayAndWaitRouterWithSubscriptions extends BroadcastEnabledRouter
													implements PublisherSubscriber {
	/** identifier for the initial number of copies setting ({@value})*/ 
	public static final String NROF_COPIES_S = "nrofCopies";
	/** identifier for the binary-mode setting ({@value})*/
	public static final String BINARY_MODE_S = "binaryMode";
	/** identifier for the sending probability ({@value})*/
	public static final String MESSAGE_DISSEMINATION_PROBABILITY_S = "msgDissProbability";
	/** identifier for the binary-mode setting ({@value})*/
	public static final String MESSAGE_ACCEPT_PROBABILITY_S = "msgAcceptProbability";
	/** SprayAndWaitRouterWithSubscriptions' settings name space ({@value})*/
	public static final String SPRAYANDWAIT_NS = "SprayAndWaitRouterWithSubscriptions";
	/** Message property key */
	public static final String MSG_COUNT_PROPERTY = SPRAYANDWAIT_NS + ".copies";
	
	protected int initialNrofCopies;
	protected boolean isBinary;
	
	private SubscriptionListManager nodeSubscriptions;
	private final SubscriptionBasedDisseminationMode pubSubDisseminationMode;
	private HashMap<String, Boolean> sendMsgSemiPorousFilter;
	private HashMap<String, Boolean> receiveMsgSemiPorousFilter;

	private final double sendProbability;
	private final double receiveProbability;

	public SprayAndWaitRouterWithSubscriptions(Settings s) {
		super(s);
		
		try {
			this.nodeSubscriptions = new SubscriptionListManager(s);
		} catch (ParseException e) {
			throw new SimError("Error parsing configuration file");
		}

		Settings snwSettings = new Settings(SPRAYANDWAIT_NS);
		this.initialNrofCopies = snwSettings.getInt(NROF_COPIES_S);
		this.isBinary = snwSettings.getBoolean(BINARY_MODE_S);
		
		int subpubDisMode = s.contains(PublisherSubscriber.SUBSCRIPTION_BASED_DISSEMINATION_MODE_S) ?
							s.getInt(PublisherSubscriber.SUBSCRIPTION_BASED_DISSEMINATION_MODE_S) :
							SubscriptionBasedDisseminationMode.FLEXIBLE.ordinal();
		if ((subpubDisMode < 0) || (subpubDisMode > SubscriptionBasedDisseminationMode.values().length)) {
			throw new SimError(PublisherSubscriber.SUBSCRIPTION_BASED_DISSEMINATION_MODE_S + " value " +
								"in the settings file is out of range");
		}
		this.pubSubDisseminationMode = SubscriptionBasedDisseminationMode.values()[subpubDisMode];
		
		if (this.pubSubDisseminationMode == SubscriptionBasedDisseminationMode.SEMI_POROUS) {
			this.sendProbability = s.contains(MESSAGE_DISSEMINATION_PROBABILITY_S) ? s.getDouble(MESSAGE_DISSEMINATION_PROBABILITY_S) : 0.5;
			this.receiveProbability = s.contains(MESSAGE_ACCEPT_PROBABILITY_S) ? s.getDouble(MESSAGE_ACCEPT_PROBABILITY_S) : 0.5;
		}
		else {
			this.sendProbability = (this.pubSubDisseminationMode ==
									SubscriptionBasedDisseminationMode.FLEXIBLE) ? 1.0 : 0.0;
			this.receiveProbability = (this.pubSubDisseminationMode ==
										SubscriptionBasedDisseminationMode.FLEXIBLE) ? 1.0 : 0.0;
		}
		this.sendMsgSemiPorousFilter = new HashMap<String, Boolean>();
		this.receiveMsgSemiPorousFilter = new HashMap<String, Boolean>();
	}
	
	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected SprayAndWaitRouterWithSubscriptions(SprayAndWaitRouterWithSubscriptions r) {
		super(r);
		
		this.initialNrofCopies = r.initialNrofCopies;
		this.isBinary = r.isBinary;
		
		this.pubSubDisseminationMode = r.pubSubDisseminationMode;
		this.nodeSubscriptions = r.nodeSubscriptions.replicate();
		this.sendMsgSemiPorousFilter = new HashMap<String, Boolean>();
		this.receiveMsgSemiPorousFilter = new HashMap<String, Boolean>();
		
		this.sendProbability = r.sendProbability;
		this.receiveProbability = r.receiveProbability;
	}
	
	@Override
	public SprayAndWaitRouterWithSubscriptions replicate() {
		return new SprayAndWaitRouterWithSubscriptions(this);
	}

	@Override 
	public boolean createNewMessage(Message m) {
		m.addProperty(MSG_COUNT_PROPERTY, new Integer(initialNrofCopies));
		
		return super.createNewMessage(m);
	}
	
	@Override
	public int receiveMessage(Message m, Connection con) {
		return super.receiveMessage(m, con);
	}

	@Override
	public Message messageTransferred(String msgID, Connection con) {
		if (hasReceivedMessage(msgID)) {
			// Handle duplicate message
			return acceptMessage(msgID, con);
		}
		
		Integer nrofCopies = (Integer) con.getMessage().getProperty(MSG_COUNT_PROPERTY);
		if (!isMessageDestination(con.getMessage())) {
			String message = null;
			switch (pubSubDisseminationMode) {
			case FLEXIBLE:
				return acceptMessage(msgID, con);
			case SEMI_POROUS:
				if (nrofCopies > 1) {
					//TODO: Check if this really makes sense
					/* The SnW Router is in its dissemination phase:
					 * we accept the message regardless of the porosity
					 * of the channel to avoid losing copies now. */
					return acceptMessage(msgID, con);
				}

				if (!receiveMsgSemiPorousFilter.containsKey(msgID)) {
					receiveMsgSemiPorousFilter.put(msgID, Boolean.valueOf(
							nextRandomDouble() <= receiveProbability));
				}
				
				if (receiveMsgSemiPorousFilter.get(msgID)) {
					return acceptMessage(msgID, con);
				}
				message = "semi-porous dissemination mode";
				break;
			case STRICT:
				message = "strict dissemination mode";
				break;
			}
			
			// remove message from receiving interface and refuse message
			Message incoming = retrieveTransferredMessageFromInterface(msgID, con);
			if (incoming == null) {
				// reception was interfered --> no need to log a discard
				return null;
			}
			notifyListenersAboutMessageDelete(incoming, MessageDropMode.DISCARDED, message);
			return null;
		}
		
		return acceptMessage(msgID, con);
	}
	
	/**
	 * Router uses broadcast, so no line for having performed
	 * a new transmission should be logged. 
	 */
	@Override
	protected void transferDone(Connection con) {}

	/**
	 * Called just before a transfer is finalized (by 
	 * {@link BroadcastEnabledRouter#update()}).
	 * Reduces the number of copies we have left for a message. 
	 * In binary Spray and Wait, sending host is left with floor(n/2) copies,
	 * but in standard mode, nrofCopies left is reduced by one. 
	 */
	@Override
	protected void transferDone(Set<Message> transferredMessages,
								Set<Connection> transferringConnections) {
		if (transferredMessages.size() > 1) {
			throw new SimError("NetworkInterface sent more than one message at the same time");
		}
		super.transferDone(transferredMessages, transferringConnections);
		
		for (Message m : transferredMessages) {
			// get this router's copy of the message
			Message msg = getMessage(m.getID());
			if (msg == null) {
				/* message was dropped from the buffer after the transfer
				 * started -> no need to reduce amount of copies left. */
				continue;
			}
			
			Integer nrofCopies = (Integer) msg.getProperty(MSG_COUNT_PROPERTY);
			if (nrofCopies > 1) {
				/* reduce the amount of copies left */
				if (isBinary) {
					nrofCopies = (int) Math.ceil(nrofCopies / 2.0);
				}
				else {
					nrofCopies--;
				}
				msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
			}
		}
	}

	@Override
	public void update() {
		super.update();
		 
		/* First, check if there are any new neighbors. */
		if ((pubSubDisseminationMode == SubscriptionBasedDisseminationMode.SEMI_POROUS) &&
			updateNeighborsList()) {
			// There are new neighbors: change send filter
			sendMsgSemiPorousFilter.clear();
		}
		
		/* Then, try messages that could be delivered to their final recipient */
		while (canBeginNewTransfer() && (exchangeDeliverableMessages() != null));
		
		/* If the chosen dissemination mode is strict, then messages can not be
		 * disseminated to nodes which are not a destination. */
		if (pubSubDisseminationMode == SubscriptionBasedDisseminationMode.STRICT) {
			return;
		}
		
		/* If the node is still able to transfer messages, it considers the
		 * list of SnWMessages that have copies left to distribute and tries
		 * to send them over all idle network interfaces. */
		if (canBeginNewTransfer()) {
			List<NetworkInterface> idleInterfaces = getIdleNetworkInterfaces();
			Collections.shuffle(idleInterfaces, RANDOM_GENERATOR);
			/* try to send those messages over all idle interfaces */
			for (NetworkInterface idleInterface : idleInterfaces) {
				List<Message> messagesToDisseminate = sortListOfMessagesForForwarding(
						getMessagesAccordingToDisseminationPolicy(idleInterface));
				for (Message m : messagesToDisseminate) {
					if (BROADCAST_OK == tryBroadcastOneMessage(m, idleInterface)) {
						// Moves on to the next Network Interface in the list
						break;
					}
				}
			}
		}
	}

	@Override
	public int generateRandomSubID() {
		return nodeSubscriptions.getRandomSubscriptionFromList();
	}

	@Override
	public SubscriptionListManager getSubscriptionList() {
		return nodeSubscriptions;
	}

	/**
	 * Creates and returns a list of messages this router is currently
	 * carrying and still has copies left to distribute (nrofCopies > 1).
	 * @return A list of messages that have copies left
	 */
	protected List<Message> getMessagesWithCopiesLeft() {
		List<Message> list = new ArrayList<Message>();

		for (Message m : getMessageList()) {
			Integer nrofCopies = (Integer)m.getProperty(MSG_COUNT_PROPERTY);
			if (nrofCopies == null) {
				throw new SimError("SnW message " + m + " didn't have the nrofcopies property!");
			}
			
			if (nrofCopies > 1) {
				list.add(m);
			}
		}
		
		return list;
	}
	
	@Override
	protected boolean isMessageDestination(Message aMessage) {
		if (aMessage == null) {
			return false;
		}
		
		Integer messageSubID = (Integer) aMessage.getProperty(SUBSCRIPTION_MESSAGE_PROPERTY_KEY);
		return getSubscriptionList().containsSubscriptionID(messageSubID);
	}

	@Override
	protected boolean isMessageDestination(Message aMessage, DTNHost dest) {
		return dest.getRouter().isMessageDestination(aMessage);
	}
	
	@Override
	protected boolean shouldBeDeliveredMessageFromHost(Message m, DTNHost from) {
		return !hasReceivedMessage(m.getID()) &&
				(shouldReceiveAccordingToDisseminationPolicy(m, from) || isMessageDestination(m));
	}

	/**
	 * Returns whether a message can be delivered to the specified host
	 * or not, according to the Spray and Wait (SnW) policy. Said policy
	 * requires that SnW Routers, in the spray phase, perform a message
	 * dissemination in a way similar to Epidemic Routers, thereby also
	 * carrying out the Anti-entropy session before proceeding with
	 * message spraying. The algorithm hereby written is a simplification,
	 * as it does not require hosts to exchange the lists produced for
	 * the Anti-entropy session.
	 * @param m the {@link Message} to deliver.
	 * @param to the {@link DTNHost} to which deliver the Message m.
	 * @return {@code true} if the message can be delivered to the
	 * specified host, or {@code false} otherwise.
	 */
	@Override
	protected boolean shouldDeliverMessageToHost(Message m, DTNHost to) {
		return (shouldSendAccordingToDisseminationPolicy(m, to) ||
				isMessageDestination(m, to)) && super.shouldDeliverMessageToHost(m, to);
	}
	
	private List<Message> getMessagesAccordingToDisseminationPolicy(NetworkInterface idleInterface) {
		List<Message> availableMessages = new ArrayList<Message>();
		// SnW Router will consider only those messages with any copy left
		for (Message msg : getMessagesWithCopiesLeft()) {
			boolean isBeingSent = false;
			for (NetworkInterface ni : getNetworkInterfaces()) {
				isBeingSent |= ni.isSendingMessage(msg.getID());
			}
			if (isBeingSent) {
				// Skip message
				continue;
			}
			
			/* If no interface is sending the message and the dissemination
			 * policy chosen allows it, we add it to the list of messages
			 * available for sending. */
			if (shouldDeliverMessageToNeighbors(msg, idleInterface)) {
				availableMessages.add(msg);
			}
		}
		
		return availableMessages;
	}
	
	/**
	 * This method groups the actions a SnW Router performs
	 * when it correctly receives a {@link Message}.
	 * @param msgID A {@link String} representing the ID
	 * of the transferred {@link Message}.
	 * @param con The {@link Connection} transferring
	 * the {@link Message}.
	 * @return The {@link Message} received if the reception
	 * concluded correctly, or {@code null} otherwise.
	 * @throws SimError
	 */
	private Message acceptMessage(String msgID, Connection con) throws SimError {
		Message msg = super.messageTransferred(msgID, con);
		// Check if message is null (interference, or out-of-synch) 
		if (msg != null) {
			Integer nrofCopies = (Integer) msg.getProperty(MSG_COUNT_PROPERTY);
			assert nrofCopies != null : "Not a SnW message: " + msg;
			
			if (isBinary) {
				/* in binary S'n'W the receiving node gets ceil(n/2) copies */
				nrofCopies = (int) Math.ceil(nrofCopies/2.0);
			}
			else {
				/* in standard S'n'W the receiving node gets only single copy */
				nrofCopies = 1;
			}
			
			msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
		}
		
		return msg;
	}
	
	private boolean shouldSendAccordingToDisseminationPolicy(Message m, DTNHost to) {
		switch(pubSubDisseminationMode) {
		case STRICT:
			return false;
		case FLEXIBLE:
			return true;
		case SEMI_POROUS:
			if (!sendMsgSemiPorousFilter.containsKey(m.getID())) {
				sendMsgSemiPorousFilter.put(m.getID(), Boolean.valueOf(
											nextRandomDouble() <= sendProbability));
			}
			
			return sendMsgSemiPorousFilter.get(m.getID()).booleanValue();
		}
		
		return false;
	}
	
	private boolean shouldReceiveAccordingToDisseminationPolicy(Message m, DTNHost from) {
		switch(pubSubDisseminationMode) {
		case STRICT:
			return false;
		case FLEXIBLE:
			return true;
		case SEMI_POROUS:
			if (!receiveMsgSemiPorousFilter.containsKey(m.getID())) {
				receiveMsgSemiPorousFilter.put(m.getID(), Boolean.valueOf(
											nextRandomDouble() <= receiveProbability));
			}
			
			return receiveMsgSemiPorousFilter.get(m.getID()).booleanValue();
		}
		
		return false;
	}
	
}
