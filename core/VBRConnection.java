/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package core;

import routing.MessageRouter;

/**
 * A connection between two DTN nodes.  The transmission speed
 * is updated every round from the end point transmission speeds
 */
public class VBRConnection extends Connection {
	private int msgsize;
	private int msgsent;
	private int currentspeed = 0;
	
	/**
	 * Creates a new connection between nodes and sets the connection
	 * state to "up".
	 * @param fromNode The node that initiated the connection
	 * @param fromInterface The interface that initiated the connection
	 * @param toNode The node in the other side of the connection
	 * @param toInterface The interface in the other side of the connection
	 */
   public VBRConnection(DTNHost fromNode, NetworkInterface fromInterface, 
		   DTNHost toNode, NetworkInterface toInterface) {
	    super(fromNode, fromInterface, toNode, toInterface);
		this.msgsize = 0;
		this.msgsent = 0;
	}
	
	/**
	 * Sets a message that this connection is currently transferring. If message
	 * passing is controlled by external events, this method is not needed
	 * (but then e.g. {@link #finalizeTransfer()} and 
	 * {@link #isMessageTransferred()} will not work either). Only a one message
	 * at a time can be transferred using one connection.
	 * @param from The host sending the message
	 * @param m The message
	 * @return The value returned by 
	 * {@link MessageRouter#receiveMessage(Message, DTNHost)}
	 */
	public int startTransfer(DTNHost from, Message m) {
		assert underwayTransfer == null : "Already transferring " + underwayTransfer.getMsgOnFly() +
				" from " + underwayTransfer.getSender() + " to " + underwayTransfer.getReceiver() +
				". Can't " + "start transfer of " + m + " from " + from;
		
		underwayTransfer = new Transfer(this, from, m);
		Message newMessage = m.replicate();
		int retVal = getOtherNode(from).receiveMessage(newMessage, this);
		
		if ((retVal == MessageRouter.RCV_OK) ||
			(retVal == MessageRouter.DENIED_INTERFERENCE)) {
			msgsize = m.getSize();
			msgsent = 0;
		}
		else {
			abortTransfer();
		}

		return retVal;
	}

	@Override
	public void copyMessageTransfer(DTNHost from, Connection c) {
		if (!c.isTransferOngoing()) {
			return;
		}
		if (from != c.getSenderNode()) {
			throw new SimError("The present node and specified connection's" +
								" sender node are different");
		}
		
		if (!(c instanceof VBRConnection)) {
			throw new SimError("Present and remote connections are of different types");
		}
		VBRConnection vbrc = (VBRConnection) c;
		
		if (underwayTransfer != null) {
			if (getRemainingByteCount() >= vbrc.getRemainingByteCount()) {
				// The transmission currently set lasts longer than the other one --> do nothing
				return;
			}
			
			// Remove current transfer from the receiving interface
			getSenderInterface().removeOutOfSynchTransfer(getMessage().getID(), this);
		}
		
		underwayTransfer = (vbrc.getRemainingByteCount() == vbrc.getMessage().getSize()) ?
							new Transfer (this, from, vbrc.getMessage(), vbrc.getRemainingByteCount() - 1) :
							new Transfer (this, from, vbrc.getMessage(), vbrc.getRemainingByteCount());
		msgsize = underwayTransfer.getBytesToTransfer();
		msgsent = 0;
		currentspeed = vbrc.currentspeed;
		
		getReceiverInterface().beginNewOutOfSynchTransfer(getMessage(), this);
	}

	/**
	 * Calculate the current transmission speed from the information
	 * given by the interfaces, and calculate the missing data amount.
	 *
	 */
	public void update() {
		currentspeed =  this.fromInterface.getTransmitSpeed();
		int othspeed =  this.toInterface.getTransmitSpeed();
		
		if (othspeed < currentspeed) {
			currentspeed = othspeed;
		}
		
		msgsent += currentspeed;
	}
	
	/**
	 * returns the current speed of the connection
	 */
	public double getSpeed() {
		return this.currentspeed;
	}

    /**
     * Returns the amount of bytes to be transferred before ongoing transfer
     * is ready or 0 if there's no ongoing transfer or it has finished
     * already
     * @return the amount of bytes to be transferred
     */
    public int getRemainingByteCount() {
    	int bytesLeft = msgsize - msgsent; 
    	return (bytesLeft > 0 ? bytesLeft : 0);
    }
    
	/**
	 * Returns true if the current message transfer is done.
	 * @return True if the transfer is done, false if not
	 */
	public boolean isMessageTransferred() {
		if (msgsent >= msgsize) {
			return true;
		} else {
			return false;
		}
	}

	@Override
	public void abortTransfer() {
		super.abortTransfer();
		msgsize = 0;
		msgsent = 0;
	}
	
	@Override
	public void finalizeTransfer() {
		super.finalizeTransfer();
		msgsize = 0;
		msgsent = 0;
	}
	
	/**
	 * Returns a String presentation of the connection.
	 */
	public String toString() {
		return fromNode + "<->" + toNode + " (" + currentspeed + "Bps) is " + (isUp() ? "up" : "down") + 
				(underwayTransfer.getMsgOnFly() != null ? " transferring " +
				underwayTransfer.getMsgOnFly() + " from " + underwayTransfer.getSender() : "");
	}
}
