/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package routing;

import java.util.ArrayList;
import java.util.List;

import core.Connection;
import core.Message;
import core.Settings;
import core.SimError;

/**
 * Implementation of Spray and wait router as depicted in 
 * <I>Spray and Wait: An Efficient Routing Scheme for Intermittently
 * Connected Mobile Networks</I> by Thrasyvoulos Spyropoulus et al.
 *
 */
public class SprayAndWaitRouter extends ActiveRouter {
	/** identifier for the initial number of copies setting ({@value})*/ 
	public static final String NROF_COPIES = "nrofCopies";
	/** identifier for the binary-mode setting ({@value})*/ 
	public static final String BINARY_MODE = "binaryMode";
	/** SprayAndWait router's settings name space ({@value})*/ 
	public static final String SPRAYANDWAIT_NS = "SprayAndWaitRouter";
	/** Message property key */
	public static final String MSG_COUNT_PROPERTY = SPRAYANDWAIT_NS + ".copies";
	
	protected int initialNrofCopies;
	protected boolean isBinary;

	public SprayAndWaitRouter(Settings s) {
		super(s);
		
		Settings snwSettings = new Settings(SPRAYANDWAIT_NS);
		initialNrofCopies = snwSettings.getInt(NROF_COPIES);
		isBinary = snwSettings.getBoolean(BINARY_MODE);
	}
	
	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected SprayAndWaitRouter(SprayAndWaitRouter r) {
		super(r);
		
		this.initialNrofCopies = r.initialNrofCopies;
		this.isBinary = r.isBinary;
	}
	
	@Override
	public int receiveMessage(Message m, Connection con) {
		return super.receiveMessage(m, con);
	}
	
	@Override
	public Message messageTransferred(String id, Connection con) {
		Message msg = super.messageTransferred(id, con);
		// Check if message is null (interference, or out-of-synch) 
		if (msg != null) {
			Integer nrofCopies = (Integer)msg.getProperty(MSG_COUNT_PROPERTY);
			
			assert nrofCopies != null : "Not a SnW message: " + msg;
			
			if (isBinary) {
				/* in binary S'n'W the receiving node gets ceil(n/2) copies */
				nrofCopies = (int)Math.ceil(nrofCopies/2.0);
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
		m.addProperty(MSG_COUNT_PROPERTY, new Integer(initialNrofCopies));
		
		return super.createNewMessage(m);
	}
	
	@Override
	public void update() {
		super.update();
		if (!canBeginNewTransfer() || isTransferring()) {
			return; // nothing to transfer or is currently transferring 
		}

		/* try messages that could be delivered to final recipient */
		if (exchangeDeliverableMessages() != null) {
			return;
		}
		
		/* create a list of SAWMessages that have copies left to distribute */
		List<Message> copiesLeft = sortListOfMessagesForForwarding(getMessagesWithCopiesLeft());
		
		if (copiesLeft.size() > 0) {
			/* try to send those messages */
			tryMessagesToConnections(copiesLeft, getConnections());
		}
	}
	
	/**
	 * Creates and returns a list of messages this router is currently
	 * carrying and still has copies left to distribute (nrof copies > 1).
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

		if (msg == null) { // message has been dropped from cache after transfer
			return; // ..has begun -> no need to reduce the number of copies
		}
		
		/* reduce the number of copies left */
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
	public SprayAndWaitRouter replicate() {
		return new SprayAndWaitRouter(this);
	}
}
