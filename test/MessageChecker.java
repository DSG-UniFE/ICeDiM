/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package test;

import java.util.ArrayList;

import core.DTNHost;
import core.Message;
import core.MessageListener;

/**
 * Message event checker for tests.
 */
public class MessageChecker implements MessageListener {
	private Message lastMsg;
	private DTNHost lastFrom;
	private DTNHost lastTo;
	private Boolean lastDropped;
	private Boolean lastFirstDelivery;
	private String lastType;
	private ArrayList<MsgCheckerEvent> queue;
	
	public final String TYPE_NONE = "none";
	public final String TYPE_DELETE = "delete";
	public final String TYPE_ABORT = "abort";
	public final String TYPE_INTERFERED = "interfered";
	public final String TYPE_RELAY = "relay";
	public final String TYPE_CREATE = "create";
	public final String TYPE_START = "start";
	
	public MessageChecker() {
		reset();
	}
	
	public void reset() {
		this.queue = new ArrayList<MsgCheckerEvent>();
		this.lastType = TYPE_NONE;
		this.lastMsg = null;
		this.lastFrom = null;
		this.lastTo = null;
		this.lastDropped = null;
		this.lastFirstDelivery = null;
	}
	
	@Override
	public void registerNode(DTNHost node) {}

	@Override
	public void messageDeleted(Message m, DTNHost where, boolean dropped, String cause) {
		add(m, where, null, TYPE_DELETE, dropped, null);
	}

	@Override
	public void messageTransferAborted(Message m, DTNHost from, DTNHost to) {
		add(m, from, to, TYPE_ABORT, null, null);
	}

	@Override
	public void messageTransmissionInterfered(Message m, DTNHost from, DTNHost to) {
		add(m, from, to, TYPE_INTERFERED, null, null);
	}

	@Override
	public void messageTransferred(Message m, DTNHost from, DTNHost to,
									boolean firstDelivery, boolean finalTarget) {
		add(m, from, to, TYPE_RELAY, null, firstDelivery && finalTarget);
	}

	@Override
	public void newMessage(Message m) {
		add(m, m.getFrom(), m.getTo(), TYPE_CREATE, null, null);
	}
	
	@Override
	public void messageTransferStarted(Message m, DTNHost from, DTNHost to) {
		add(m, from, to, TYPE_START, null, null);
	}

	public boolean next() {
		if (queue.size() == 0) {
			return false;
		}
		
		MsgCheckerEvent e = queue.remove(0);
		lastMsg = e.msg;
		lastFrom = e.from;
		lastTo = e.to;		
		lastType = e.type;
		lastFirstDelivery = e.delivered;
		lastDropped = e.dropped;
		
		return true;

	}
	
	private void add(Message m, DTNHost from, DTNHost to, String type,
						Boolean dropped, Boolean delivered) {
		queue.add(new MsgCheckerEvent(m,from,to,type,dropped,delivered));
	}

	/**
	 * @return the lastFirstDelivery
	 */
	public Boolean getLastFirstDelivery() {
		return lastFirstDelivery;
	}

	/**
	 * @return the lastDropped
	 */
	public Boolean getLastDropped() {
		return lastDropped;
	}

	/**
	 * @return the lastFrom
	 */
	public DTNHost getLastFrom() {
		return lastFrom;
	}

	/**
	 * @return the lastMsg
	 */
	public Message getLastMsg() {
		return lastMsg;
	}

	/**
	 * @return the lastTo
	 */
	public DTNHost getLastTo() {
		return lastTo;
	}

	/**
	 * @return the lastType
	 */
	public String getLastType() {
		return lastType;
	}
	
	public String toString() {
		return queue.size() + " event(s) : " + queue;
	}

	private class MsgCheckerEvent {
		private Message msg;
		private DTNHost from;
		private DTNHost to;
		private Boolean dropped;
		private Boolean delivered;
		private String type;
		
		public MsgCheckerEvent(Message m, DTNHost from, DTNHost to,
								String type, Boolean dropped, Boolean delivered) {
			this.msg = m;
			this.from = from;
			this.to = to;
			this.type = type;
			this.dropped = dropped;
			this.delivered = delivered;
		}

		public String toString() {
			return type + " (" + from + "->" + to+") " + msg;
		}
	}
}
