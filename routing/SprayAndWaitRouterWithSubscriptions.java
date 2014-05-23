/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package routing;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
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

	private final double sendProbability;
	private final double receiveProbability;
	
	private SubscriptionListManager nodeSubscriptions;
	private final SubscriptionBasedDisseminationMode pubSubDisseminationMode;

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
	}
	
	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected SprayAndWaitRouterWithSubscriptions(SprayAndWaitRouterWithSubscriptions r) {
		super(r);
		
		this.initialNrofCopies = r.initialNrofCopies;
		this.isBinary = r.isBinary;
		this.sendProbability = r.sendProbability;
		this.receiveProbability = r.receiveProbability;
		this.pubSubDisseminationMode = r.pubSubDisseminationMode;
		this.nodeSubscriptions = r.nodeSubscriptions.replicate();
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

	@Override
	public Message messageTransferred(String id, Connection con) {
		Integer subID = (Integer) con.getMessage().getProperty(SUBSCRIPTION_MESSAGE_PROPERTY_KEY);
		Integer nrofCopies = (Integer) con.getMessage().getProperty(MSG_COUNT_PROPERTY);
		if (!getSubscriptionList().getSubscriptionList().contains(subID)) {
			String message = null;
			switch (pubSubDisseminationMode) {
			case FLEXIBLE:
				return acceptMessage(id, con);
			case SEMI_POROUS:
				if (nrofCopies > 1) {
					/* The SnW Router is in its dissemination phase:
					 * we accept the message regardless of the porosity
					 * of the channel to avoid losing copies now. */
					return acceptMessage(id, con);
				}
				if (nextRandomDouble() <= receiveProbability) {
					// Randomly accept the message
					return acceptMessage(id, con);
				}
				// Discarding the message
				message = "semi-porous dissemination mode";
				break;
			case STRICT:
				message = "strict dissemination mode";
				break;
			}
			
			// remove message from receiving interface and refuse message
			Message incoming = retrieveTransferredMessageFromInterface(id, con);
			if (incoming == null) {
				// reception was interfered --> no need to log a discard
				return null;
			}
			notifyListenersAboutMessageDelete(incoming, MessageDropMode.DISCARDED, message);
			
			return null;
		}
	
		return acceptMessage(id, con);
	}
	
	/**
	 * Router uses broadcast, so no line for having performed
	 * a new transmission should be logged. 
	 */
	@Override
	protected void transferDone(Connection con) {}

	/**
	 * Called just before a transfer is finalized (by 
	 * {@link ActiveRouter#update()}).
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
				return;
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
		
		/* try messages that could be delivered to final recipient */
		while (canBeginNewTransfer() && (exchangeDeliverableMessages() != null)) { }
		
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
		return !to.getRouter().hasReceivedMessage(m.getID());
	}

	/**
	 * Returns a list of those messages whose recipient 
	 * is among the neighboring nodes.
	 * @return a List of messages to be delivered to the
	 * nodes under connection range.
	 */
	@Override
	protected List<Message> getDeliverableMessagesForNetworkInterface(NetworkInterface ni) {
		if (getNrofMessages() == 0) {
			/* no messages -> empty list */
			return new ArrayList<Message>(0);
		}
		
		// Create the list of subscriptions of the neighbors
		List<DTNHost> neighboringHosts = ni.getReachableHosts();
		List<Integer> neighborsSubscriptionIDs = new ArrayList<Integer>();
		for (DTNHost host : neighboringHosts) {
			if (!(host.getRouter() instanceof PublisherSubscriber)) {
				throw new SimError("Remote host " + host + " is not a Publisher/Subscriber router");
			}
			
			PublisherSubscriber remoteHost = (PublisherSubscriber) host.getRouter();
			for (Integer subID : remoteHost.getSubscriptionList().getSubscriptionList()) {
				if (!neighborsSubscriptionIDs.contains(subID)) {
					neighborsSubscriptionIDs.add(subID);
				}
			}
		}
		
		List<Message> messageList = new ArrayList<Message>();
		for (Message m : getMessageList()) {
			Integer subID = (Integer) m.getProperty(PublisherSubscriber.SUBSCRIPTION_MESSAGE_PROPERTY_KEY);
			if (neighborsSubscriptionIDs.contains(subID)) {
				// add all messages belonging to the subscriptions of a neighbor
				messageList.add(m);
			}
			else if (nextRandomDouble() <= sendProbability) {
				/* Add the message to the list. This will happen with
				 * probability 1 if the selected dissemination mode
				 * is FLEXIBLE, with the configured probability if the
				 * dissemination mode is SEMI-POROUS, or it won't
				 * happen at all if the dissemination mode set to STRICT.
				 */
				messageList.add(m);
			}
		}
		
		return messageList;
	}
	
	private List<Message> getMessagesAccordingToDisseminationPolicy(NetworkInterface idleInterface) {
		List<Message> availableMessages = new ArrayList<Message>();
		// SnW Router will consider only those messages with any copy left
		for (Message msg : getMessagesWithCopiesLeft()) {
			boolean isBeingSent = false;
			for (NetworkInterface ni : getNetworkInterfaces()) {
				isBeingSent |= ni.isSendingMessage(msg.getID());
			}
			
			/* If no interface is sending the message and the dissemination
			 * policy chosen allows it, we add it to the list of messages
			 * available for sending. */
			if (!isBeingSent && shouldDeliverMessageToNeighbors(msg, idleInterface) &&
				(nextRandomDouble() <= sendProbability)) {
				availableMessages.add(msg);
			}
		}
		
		return availableMessages;
	}
}
