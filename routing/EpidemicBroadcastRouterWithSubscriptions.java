/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package routing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.text.ParseException;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.NetworkInterface;
import core.Settings;
import core.SimError;
import core.iceDim.PublisherSubscriber;
import core.iceDim.SubscriptionListManager;

/**
 * Epidemic message router with broadcast transmissions.
 */
public class EpidemicBroadcastRouterWithSubscriptions
	extends BroadcastEnabledRouter implements PublisherSubscriber {
	
	/** identifier for the sending probability ({@value})*/
	public static final String MESSAGE_DISSEMINATION_PROBABILITY_S = "msgDissProbability";
	/** identifier for the binary-mode setting ({@value})*/
	public static final String MESSAGE_ACCEPT_PROBABILITY_S = "msgAcceptProbability";

	private final ADCMode pubSubDisseminationMode;
	private final SubscriptionListManager nodeSubscriptions;
	private HashMap<String, Boolean> sendMsgSemiPermeableFilter;
	private HashMap<String, Boolean> receiveMsgSemiPermeableFilter;
	
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
		
		int subpubDisMode = s.contains(PublisherSubscriber.ADC_MODE_S) ?
				s.getInt(PublisherSubscriber.ADC_MODE_S) :
				ADCMode.UNCONSTRAINED.ordinal();
		if ((subpubDisMode < 0) || (subpubDisMode > ADCMode.values().length)) {
			throw new SimError(PublisherSubscriber.ADC_MODE_S +
								" value " + "in the settings file is out of range");
		}
		
		this.pubSubDisseminationMode = ADCMode.values()[subpubDisMode];
		if (this.pubSubDisseminationMode == ADCMode.SEMI_PERMEABLE) {
			this.sendProbability = s.contains(MESSAGE_DISSEMINATION_PROBABILITY_S) ? s.getDouble(MESSAGE_DISSEMINATION_PROBABILITY_S) : 0.5;
			this.receiveProbability = s.contains(MESSAGE_ACCEPT_PROBABILITY_S) ? s.getDouble(MESSAGE_ACCEPT_PROBABILITY_S) : 0.5;
		}
		else {
			this.sendProbability = (this.pubSubDisseminationMode ==
									ADCMode.UNCONSTRAINED) ? 1.0 : 0.0;
			this.receiveProbability = (this.pubSubDisseminationMode ==
										ADCMode.UNCONSTRAINED) ? 1.0 : 0.0;
		}
		this.sendMsgSemiPermeableFilter = new HashMap<String, Boolean>();
		this.receiveMsgSemiPermeableFilter = new HashMap<String, Boolean>();
	}
	
	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected EpidemicBroadcastRouterWithSubscriptions(EpidemicBroadcastRouterWithSubscriptions r) {
		super(r);
		
		this.pubSubDisseminationMode = r.pubSubDisseminationMode;
		this.nodeSubscriptions = r.nodeSubscriptions.replicate();
		this.sendMsgSemiPermeableFilter = new HashMap<String, Boolean>();
		this.receiveMsgSemiPermeableFilter = new HashMap<String, Boolean>();
		
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
			case UNCONSTRAINED:
				return super.messageTransferred(msgID, con);
			case SEMI_PERMEABLE:
				if (!receiveMsgSemiPermeableFilter.containsKey(msgID)) {
					receiveMsgSemiPermeableFilter.put(msgID, Boolean.valueOf(
							nextRandomDouble() <= receiveProbability));
				}
				
				if (receiveMsgSemiPermeableFilter.get(msgID).booleanValue()) {
					return super.messageTransferred(msgID, con);
				}
				message = "semi-permeable dissemination mode";
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
	
	/**
	 * The Router only checks that one message only was sent out.
	 * This is a post-condition check on the Router, given traditional
	 * wireless network interfaces such as WiFi or Bluetooth.
	 */
	@Override
	protected void transferDone(Set<Message> transferredMessages,
								Set<Connection> transferringConnections) {
		if (transferredMessages.size() > 1) {
			throw new SimError("NetworkInterface sent more than one message at the same time");
		}
		
		super.transferDone(transferredMessages, transferringConnections);
	}

	@Override
	public void update() {
		super.update();
		 
		/* First, check if there are any new neighbors. */
		if ((pubSubDisseminationMode == ADCMode.SEMI_PERMEABLE) &&
			updateNeighborsList()) {
			// There are new neighbors: change send filter
			sendMsgSemiPermeableFilter.clear();
		}
		
		 /* Then, try to send the messages that can be delivered to their
		  * final recipient; this is consistent with any dissemination policy. */
		while (canBeginNewTransfer() && (exchangeDeliverableMessages() != null));
		
		/* If the chosen dissemination mode is strict, then messages can not be
		 * disseminated to nodes which are not a destination. */
		if (pubSubDisseminationMode == ADCMode.STRICT) {
			return;
		}
		
		/* Then, try to send messages that cannot be delivered directly to
		 * their destinations. The chosen dissemination policy will affect
		 * the set of messages that can be sent this way. */
		if (canBeginNewTransfer()) {
			List<NetworkInterface> idleInterfaces = getIdleNetworkInterfaces();
			Collections.shuffle(idleInterfaces, RANDOM_GENERATOR);
			/* try to send those messages over all idle interfaces */
			for (NetworkInterface idleInterface : idleInterfaces) {
				List<Message> messagesToDisseminate = sortListOfMessagesForForwarding(
						getMessagesAccordingToDisseminationPolicy(idleInterface));
				for (Message m : messagesToDisseminate) {
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
		return !hasReceivedMessage(m.getID()) &&
				(shouldReceiveAccordingToDisseminationPolicy(m, from) || isMessageDestination(m));
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
		return (shouldSendAccordingToDisseminationPolicy(m, to) ||
				isMessageDestination(m, to)) && super.shouldDeliverMessageToHost(m, to);
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
			
			/* If no interface is sending the message and the dissemination
			 * policy chosen allows it, we add the present message to the
			 * list of messages available for sending. */
			if (shouldDeliverMessageToNeighbors(msg, idleInterface)) {
				availableMessages.add(msg);
			}
		}
		
		return availableMessages;
	}
	
	private boolean shouldSendAccordingToDisseminationPolicy(Message m, DTNHost to) {
		switch(pubSubDisseminationMode) {
		case STRICT:
			return false;
		case UNCONSTRAINED:
			return true;
		case SEMI_PERMEABLE:
			if (!sendMsgSemiPermeableFilter.containsKey(m.getID())) {
				sendMsgSemiPermeableFilter.put(m.getID(), Boolean.valueOf(
											nextRandomDouble() <= sendProbability));
			}
			
			return sendMsgSemiPermeableFilter.get(m.getID()).booleanValue();
		}
		
		return false;
	}
	
	private boolean shouldReceiveAccordingToDisseminationPolicy(Message m, DTNHost from) {
		switch(pubSubDisseminationMode) {
		case STRICT:
			return false;
		case UNCONSTRAINED:
			return true;
		case SEMI_PERMEABLE:
			if (!receiveMsgSemiPermeableFilter.containsKey(m.getID())) {
				receiveMsgSemiPermeableFilter.put(m.getID(), Boolean.valueOf(
											nextRandomDouble() <= receiveProbability));
			}
			
			return receiveMsgSemiPermeableFilter.get(m.getID()).booleanValue();
		}
		
		return false;
	}
	
}