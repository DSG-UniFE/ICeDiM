/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package routing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.uncommons.maths.random.MersenneTwisterRNG;

import com.sun.xml.internal.messaging.saaj.packaging.mime.internet.ParseException;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.MessageListener;
import core.NetworkInterface;
import core.Settings;
import core.SimError;
import core.disService.PublisherSubscriber;
import core.disService.SubscriptionListManager;

/**
 * Implementation of Spray and wait router as depicted in 
 * <I>Spray and Wait: An Efficient Routing Scheme for Intermittently
 * Connected Mobile Networks</I> by Thrasyvoulos Spyropoulus et al.
 *
 */
public class SprayAndWaitRouterWithSubscriptions extends BroadcastEnabledRouter implements PublisherSubscriber {
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
	
	private static MersenneTwisterRNG randomGenerator = null;
	private static final long SEED = 1;
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
			if (randomGenerator == null) {
				// Singleton is instanciated only if needed
				randomGenerator = new MersenneTwisterRNG();
				randomGenerator.setSeed(SEED);
			}
			
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
	public int receiveMessage(Message m, Connection con) {
		return super.receiveMessage(m, con);
	}
	
	@Override
	public Message messageTransferred(String id, Connection con) {
		Integer subID = (Integer) con.getMessage().getProperty(SUBSCRIPTION_MESSAGE_PROPERTY_KEY);
		if (!getSubscriptionList().getSubscriptionList().contains(subID)) {
			if (randomGenerator.nextDouble() > receiveProbability) {
				// remove message from receiving interface and refuse message
				Message incoming = con.getReceiverInterface().retrieveTransferredMessage(id, con);
				String message = null; 
				switch (pubSubDisseminationMode) {
				case FLEXIBLE:
					throw new SimError("message refuse despite FLEXIBLE strategy was set");
				case STRICT:
					message = "strict dissemination mode";
					break;
				case SEMI_POROUS:
					message = "message discaded due to a semi-porous strategy";
					break;
				}
				for (MessageListener ml : mListeners) {
					ml.messageDeleted(incoming, getHost(), true, message);
				}
				
				return null;
			}
		}
		
		// Accept message
		Message msg = super.messageTransferred(id, con);
		
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
	public boolean createNewMessage(Message m) {
		makeRoomForNewMessage(m.getSize(), m.getPriority());
		
		m.setTtl(msgTTL);
		m.addProperty(MSG_COUNT_PROPERTY, new Integer(initialNrofCopies));
		addToMessages(m, true);
		
		return true;
	}
	
	@Override
	public void update() {
		super.update();
		
		/* try messages that could be delivered to final recipient */
		while (canStartTransfer() && (exchangeDeliverableMessages() != null)) { }
		
		/* If the node is still able to transfer messages, it considers the
		 * list of SAWMessages that have copies left to distribute and tries
		 * to send them over all idle network interfaces. */
		List<Message> copiesLeft = sortListOfMessagesForForwarding(getMessagesWithCopiesLeft());
		if (canStartTransfer() && (copiesLeft.size() > 0)) {
			List<NetworkInterface> idleInterfaces = getIdleNetworkInterfaces();
			Collections.shuffle(idleInterfaces);
			/* try to send those messages over all idle interfaces */
			for (NetworkInterface idleInterface : idleInterfaces) {
				for (Message m : copiesLeft) {
					if (BROADCAST_OK == tryBroadcastOneMessage(m, idleInterface)) {
						/* Removes transferring message from the list and moves
						 * on to the remaining idle network interfaces */
						copiesLeft.remove(m);
						break;
					}
				}
			}
		}
	}
	
	/**
	 * Creates and returns a list of messages this router is currently
	 * carrying and still has copies left to distribute (nrof copies > 1).
	 * @return A list of messages that have copies left
	 */
	protected List<Message> getMessagesWithCopiesLeft() {
		List<Message> list = new ArrayList<Message>();

		for (Message m : getMessageCollection()) {
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
		List<DTNHost> neighbouringHosts = ni.getReachableHosts();
		List<Integer> neighboursSubscriptionIDs = new ArrayList<Integer>();
		for (DTNHost host : neighbouringHosts) {
			if (!(host.getRouter() instanceof PublisherSubscriber)) {
				throw new SimError("Remote host " + host + " is not a Publisher/Subscriber router");
			}
			
			PublisherSubscriber remoteHost = (PublisherSubscriber) host.getRouter();
			for (Integer subID : remoteHost.getSubscriptionList().getSubscriptionList()) {
				if (!neighboursSubscriptionIDs.contains(subID)) {
					neighboursSubscriptionIDs.add(subID);
				}
			}
		}
		
		List<Message> messageList = new ArrayList<Message>();
		for (Message m : getMessageCollection()) {
			Integer subID = (Integer) m.getProperty(PublisherSubscriber.SUBSCRIPTION_MESSAGE_PROPERTY_KEY);
			if (neighboursSubscriptionIDs.contains(subID)) {
				// add all messages belonging to the subscriptions of a neighbour
				messageList.add(m);
			}
			else if (randomGenerator.nextDouble() <= sendProbability) {
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

	/**
	 * Called just before a transfer is finalized (by 
	 * {@link ActiveRouter#update()}).
	 * Reduces the number of copies we have left for a message. 
	 * In binary Spray and Wait, sending host is left with floor(n/2) copies,
	 * but in standard mode, nrof copies left is reduced by one. 
	 */
	@Override
	protected void transferDone(Connection con) {
		Integer nrofCopies;
		String msgId = con.getMessage().getID();
		/* get this router's copy of the message */
		Message msg = getMessage(msgId);

		if (msg == null) { // message has been dropped from the buffer after..
			return; // ..start of transfer -> no need to reduce amount of copies
		}
		
		/* reduce the amount of copies left */
		nrofCopies = (Integer) msg.getProperty(MSG_COUNT_PROPERTY);
		if (isBinary) { 
			nrofCopies /= 2;
		}
		else {
			nrofCopies--;
		}
		msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
	}
	
	@Override
	public SprayAndWaitRouterWithSubscriptions replicate() {
		return new SprayAndWaitRouterWithSubscriptions(this);
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
		Integer messageSubID = (Integer) aMessage.getProperty(SUBSCRIPTION_MESSAGE_PROPERTY_KEY);
		if (getSubscriptionList().containsSubscriptionID(messageSubID)) {
			return true;
		}
		
		return false;
	}
}