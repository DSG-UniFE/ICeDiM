/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package routing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.text.ParseException;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.NetworkInterface;
import core.Settings;
import core.SimError;
import core.disService.PublisherSubscriber;
import core.disService.SubscriptionListManager;

/**
 * Epidemic message router with drop-oldest buffer and broadcast transmissions.
 */
public class EpidemicBroadcastRouterWithSubscriptions
	extends BroadcastEnabledRouter implements PublisherSubscriber {
	
	/** identifier for the sending probability ({@value})*/
	public static final String MESSAGE_DISSEMINATION_PROBABILITY_S = "msgDissProbability";
	/** identifier for the binary-mode setting ({@value})*/
	public static final String MESSAGE_ACCEPT_PROBABILITY_S = "msgAcceptProbability";

	private final SubscriptionBasedDisseminationMode pubSubDisseminationMode;
	private final SubscriptionListManager nodeSubscriptions;
	private HashMap<String, Boolean> sendMsgSemiPorousFilter;
	private HashMap<String, Boolean> receiveMsgSemiPorousFilter;
	
	private final double sendProbability;
	private final double receiveProbability;
	
	/**
	 * Constructor. Creates a new message router based on
	 * the settings in the given Settings object.
	 * @param s The settings object
	 */
	public EpidemicBroadcastRouterWithSubscriptions(Settings s) {
		super(s);

		try {
			this.nodeSubscriptions = new SubscriptionListManager(s);
		} catch (ParseException e) {
			throw new SimError("Error parsing configuration file");
		}
		
		int subpubDisMode = s.contains(PublisherSubscriber.SUBSCRIPTION_BASED_DISSEMINATION_MODE_S) ?
				s.getInt(PublisherSubscriber.SUBSCRIPTION_BASED_DISSEMINATION_MODE_S) :
				SubscriptionBasedDisseminationMode.FLEXIBLE.ordinal();
		if ((subpubDisMode < 0) || (subpubDisMode > SubscriptionBasedDisseminationMode.values().length)) {
			throw new SimError(PublisherSubscriber.SUBSCRIPTION_BASED_DISSEMINATION_MODE_S +
								" value " + "in the settings file is out of range");
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
	protected EpidemicBroadcastRouterWithSubscriptions(EpidemicBroadcastRouterWithSubscriptions r) {
		super(r);
		
		this.pubSubDisseminationMode = r.pubSubDisseminationMode;
		this.nodeSubscriptions = r.nodeSubscriptions.replicate();
		this.sendMsgSemiPorousFilter = new HashMap<String, Boolean>();
		this.receiveMsgSemiPorousFilter = new HashMap<String, Boolean>();
		
		this.sendProbability = r.sendProbability;
		this.receiveProbability = r.receiveProbability;
	}

	@Override
	public EpidemicBroadcastRouterWithSubscriptions replicate() {
		return new EpidemicBroadcastRouterWithSubscriptions(this);
	}

	/**
	 * It applies the chosen dissemination policy at the moment
	 * that the reception of a new message is complete.
	 */
	@Override
	public Message messageTransferred(String msgID, Connection con) {
		if (hasReceivedMessage(msgID)) {
			// Handle duplicate message
			return super.messageTransferred(msgID, con);
		}
		
		if (!isMessageDestination(con.getMessage())) {
			String message = null;
			switch (pubSubDisseminationMode) {
			case FLEXIBLE:
				return super.messageTransferred(msgID, con);
			case SEMI_POROUS:
				if (!receiveMsgSemiPorousFilter.containsKey(msgID)) {
					receiveMsgSemiPorousFilter.put(msgID, Boolean.valueOf(
							nextRandomDouble() <= receiveProbability));
				}
				
				if (receiveMsgSemiPorousFilter.get(msgID).booleanValue()) {
					return super.messageTransferred(msgID, con);
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
		
		return super.messageTransferred(msgID, con);
	}
	
	/**
	 * Router uses broadcast, so no line for having performed
	 * a new transmission should be logged. 
	 */
	@Override
	protected void transferDone(Connection con) {}

	@Override
	public void update() {
		super.update();
		 
		/* First, check if there are any new neighbors. */
		if ((pubSubDisseminationMode == SubscriptionBasedDisseminationMode.SEMI_POROUS) &&
			updateNeighborsList()) {
			// There are new neighbors: change send filter
			sendMsgSemiPorousFilter.clear();
		}
		
		 /* Then, try to send the messages that can be delivered to their
		  * final recipient; this is consistent with any dissemination policy. */
		while (canBeginNewTransfer() && (exchangeDeliverableMessages() != null));
		
		/* Then, try to send messages that cannot be delivered directly to
		 * their destinations. The chosen dissemination policy will affect
		 * the set of messages that can be sent this way. */
		if (canBeginNewTransfer()) {
			List<NetworkInterface> idleInterfaces = getIdleNetworkInterfaces();
			Collections.shuffle(idleInterfaces, RANDOM_GENERATOR);
			/* try to send those messages over all idle interfaces */
			for (NetworkInterface idleInterface : idleInterfaces) {
				List<Message> availableMessages = sortListOfMessagesForForwarding(
						getMessagesAccordingToDisseminationPolicy(idleInterface));
				for (Message m : availableMessages) {
					if (BROADCAST_OK == tryBroadcastOneMessage(m, idleInterface)) {
						// Move on to the remaining idle network interfaces.
						break;
					}
				}
			}
		}
	}

	@Override
	public SubscriptionListManager getSubscriptionList() {
		return nodeSubscriptions;
	}

	@Override
	public int generateRandomSubID() {
		return nodeSubscriptions.getRandomSubscriptionFromList();
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
		boolean receiveFilter = receiveMsgSemiPorousFilter.containsKey(m.getID()) ?
				receiveMsgSemiPorousFilter.get(m.getID()).booleanValue() : true;
		
		return !hasReceivedMessage(m.getID()) && (receiveFilter || isMessageDestination(m));
	}

	/**
	 * Returns whether a message can be delivered to the specified host
	 * or not, according to the EpidemicRouter policy. Said policy requires
	 * that Epidemic Routers keep a list of messages recently sent to each
	 * neighbor, and that they exchange the list of the messages they have
	 * with other nodes before they proceed with the dissemination phase.
	 * This phase is called Anti-entropy session in the literature.
	 * The algorithm hereby written is a simplification, as it does not
	 * require hosts to exchange the lists described above.
	 * @param m the {@link Message} to deliver.
	 * @param to the {@link DTNHost} to which deliver the Message m.
	 * @return {@code true} if the message can be delivered to the
	 * specified host, or {@code false} otherwise.
	 */
	@Override
	protected boolean shouldDeliverMessageToHost(Message m, DTNHost to) {
		boolean sendFilter = sendMsgSemiPorousFilter.containsKey(m.getID()) ?
					sendMsgSemiPorousFilter.get(m.getID()).booleanValue() : true;
		
		return (sendFilter || isMessageDestination(m, to)) &&
				super.shouldDeliverMessageToHost(m, to);
	}

	/**
	 * It selects all the messages available for transmission, according
	 * to the selected dissemination policy, which are not being sent.
	 * @param idleInterface The {@link NetworkInterface} onto which
	 * selected {@link Message}s will be sent out. It identifies
	 * the set of potential recipients for the messages.
	 * @return A {@link List} containing all the {@link Message}s
	 * that can be disseminated, according to the chosen strategy. 
	 */
	private List<Message> getMessagesAccordingToDisseminationPolicy(NetworkInterface idleInterface) {
		List<Message> availableMessages = new ArrayList<Message>();
		for (Message msg : getMessageList()) {
			boolean isBeingSent = false;
			for (NetworkInterface ni : getNetworkInterfaces()) {
				isBeingSent |= ni.isSendingMessage(msg.getID());
			}
			if (isBeingSent) {
				// Skip message
				continue;
			}
			
			/* If the message is not in the send filter yet, generate a new
			 * random value to associate with the present message and then
			 * add the result in the send filter. */
			if (!sendMsgSemiPorousFilter.containsKey(msg.getID())) {
				sendMsgSemiPorousFilter.put(msg.getID(), Boolean.valueOf(
											nextRandomDouble() <= sendProbability));
			}
			
			/* If no interface is sending the message and the dissemination
			 * policy chosen allows it, we add the present message to the
			 * list of messages available for sending. */
			if (shouldDeliverMessageToNeighbors(msg, idleInterface)) {
				availableMessages.add(msg);
			}
		}
		
		return availableMessages;
	}
}