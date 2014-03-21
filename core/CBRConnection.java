/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package core;

import routing.MessageRouter;

/**
 * A constant bit-rate connection between two DTN nodes.
 */
public class CBRConnection extends Connection {
	private int speed;
	private double transferDoneTime;

	/**
	 * Creates a new connection between nodes and sets the connection
	 * state to "up".
	 * @param fromNode The node that initiated the connection
	 * @param fromInterface The interface that initiated the connection
	 * @param toNode The node in the other side of the connection
	 * @param toInterface The interface in the other side of the connection
	 * @param connectionSpeed Transfer speed of the connection (Bps) when 
	 *  the connection is initiated
	 */
	public CBRConnection(DTNHost fromNode, NetworkInterface fromInterface, 
			DTNHost toNode,	NetworkInterface toInterface, int connectionSpeed) {
		super(fromNode, fromInterface, toNode, toInterface);
		this.speed = connectionSpeed;
		this.transferDoneTime = -1.0;
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
		assert (underwayTransfer == null) : "Already transferring " + underwayTransfer.getMsgOnFly() +
				" from " + underwayTransfer.getSender() + " to " +	underwayTransfer.getReceiver() +
				". Can't start transfer of " + m + " from " + from;
		assert isUp() : "Connection is down!";

		underwayTransfer = new Transfer(this, from, m);
		Message newMessage = m.replicate();
		int retVal = underwayTransfer.getReceiver().receiveMessage(newMessage, this);

		if ((retVal == MessageRouter.RCV_OK) ||
			(retVal == MessageRouter.DENIED_INTERFERENCE)) {
			transferDoneTime = SimClock.getTime() + (1.0 * m.getSize()) / this.speed;
		}
		else {
			// The receiver is itself sending. Avoid beginning a new transfer (CSMA/CA)
			abortTransfer();
		}

		return retVal;
	}
	
	public void copyMessageTransfer(DTNHost from, Connection c) {
		if (!c.isTransferOngoing()) {
			return;
		}
		if (from != c.getSenderNode()) {
			throw new SimError("The present node and specified connection's" +
								" sender node are different");
		}
		
		if (!(c instanceof CBRConnection)) {
			throw new SimError("Present and remote connections are of different types");
		}
		CBRConnection cbrc = (CBRConnection) c;
		
		if (underwayTransfer != null) {
			if (underwayTransfer.getMsgOnFly().getSize() == underwayTransfer.getBytesToTransfer()) {
				throw new SimError("Trying to copy an out-of-synch transfer over a synchronized one");
			}
			if (getRemainingByteCount() >= cbrc.getRemainingByteCount()) {
				// The transmission currently set lasts longer than the other one --> do nothing
				return;
			}
			
			// Remove current transfer from the receiving interface (which will then turn into the sender one)
			getReceiverInterface().removeOutOfSynchTransfer(getMessage().getID(), this);
		}
		
		// Copying a transfer is done for out-of-synch transfers only --> it never transfers all data
		underwayTransfer = (cbrc.getRemainingByteCount() == cbrc.getMessage().getSize()) ?
							new Transfer (this, from, cbrc.getMessage(), cbrc.getRemainingByteCount() - 1) :
							new Transfer (this, from, cbrc.getMessage(), cbrc.getRemainingByteCount());
		speed = (int) cbrc.getSpeed();
		transferDoneTime = cbrc.transferDoneTime;
		
		getReceiverInterface().beginNewOutOfSynchTransfer(getMessage(), this);
	}

	/**
	 * Aborts the transfer of the currently transferred message.
	 */
	@Override
	public void abortTransfer() {
		super.abortTransfer();
		transferDoneTime = -1.0;
	}

	/**
	 * Gets the transferdonetime
	 */
	public double getTransferDoneTime() {
		return transferDoneTime;
	}
	
	/**
	 * Returns true if the current message transfer is done.
	 * @return True if the transfer is done, false if not
	 */
	public boolean isMessageTransferred() {
		return getRemainingByteCount() == 0;
	}

	/**
	 * returns the current speed of the connection
	 */
	public double getSpeed() {
		return this.speed;
	}

	/**
	 * Returns the amount of bytes to be transferred before ongoing transfer
	 * is ready or 0 if there's no ongoing transfer or it has finished
	 * already
	 * @return the amount of bytes to be transferred
	 */
	public int getRemainingByteCount() {
		if (underwayTransfer == null) {
			return 0;
		}
		if (transferDoneTime < 0.0) {
			return underwayTransfer.getMsgOnFly().getSize();
		}

		int remaining = (int)((transferDoneTime - SimClock.getTime()) * speed);

		return (remaining > 0 ? remaining : 0);
	}
	
	@Override
	public void finalizeTransfer() {
		super.finalizeTransfer();
		transferDoneTime = -1.0;
	}

	/**
	 * Returns a String presentation of the connection.
	 */
	public String toString() {
		return fromNode + "<->" + toNode + " (" + speed + "Bps) is " + (isUp() ? "up":"down")
				+ (underwayTransfer != null ? " transferring " + underwayTransfer.getMsgOnFly() +
				" from " + underwayTransfer.getSender() + " until " + transferDoneTime : "");
	}

}
