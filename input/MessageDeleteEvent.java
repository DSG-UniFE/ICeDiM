/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package input;

import java.util.ArrayList;
import java.util.List;

import routing.MessageRouter.MessageDropMode;
import core.DTNHost;
import core.Message;
import core.World;

/**
 * External event for deleting a message.
 */

public class MessageDeleteEvent extends MessageEvent {
	/** is the delete caused by a drop (not "normal" removing) */
	private boolean drop;
	private String cause;
	
	/**
	 * Creates a message delete event
	 * @param host Where to delete the message
	 * @param id ID of the message
	 * @param time Time when the message is deleted
	 */
	public MessageDeleteEvent(int host, String id, double time, boolean drop, String cause) {
		super(host, host, id, time);
		this.drop = drop;
		this.cause = cause;
	}
	
	/**
	 * Deletes the message
	 */
	@Override
	public void processEvent(World world) {
		DTNHost host = world.getNodeByAddress(this.fromAddr);
		
		if (id.equals(StandardEventsReader.ALL_MESSAGES_ID)) {
			List<String> ids = new ArrayList<String>();
			for (Message m : host.getRouter().getMessageList()) {
				ids.add(m.getID());
			}
			for (String nextId : ids) {
				host.deleteMessage(nextId, drop ? MessageDropMode.DROPPED : MessageDropMode.REMOVED,
									cause);
			}
		} else {
			host.deleteMessage(id, drop ? MessageDropMode.DROPPED : MessageDropMode.REMOVED, cause);
		}
	}

	@Override
	public String toString() {
		return super.toString() + " [" + fromAddr + "] DELETE";
	}

}
