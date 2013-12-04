/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package core;

import routing.MessageRouter;

/**
 * A connection between two DTN nodes.
 */
public abstract class Connection {
	protected DTNHost toNode;
	protected NetworkInterface toInterface;
	protected DTNHost fromNode;
	protected NetworkInterface fromInterface;

	private boolean isUp;

	/** bytes transferred correctly that belong to complete messages */
	protected int bytesTransferredForGoodput;
	/** bytes transferred correctly */
	protected int bytesTransferredForThroughput;
	/** total bytes this connection has transferred */
	protected int bytesTransferred;
	
	protected Transfer underwayTransfer;

	/**
	 * Creates a new connection between nodes and sets the connection
	 * state to "up".
	 * @param fromNode The node that initiated the connection
	 * @param fromInterface The interface that initiated the connection
	 * @param toNode The node in the other side of the connection
	 * @param toInterface The interface in the other side of the connection
	 */
	public Connection(DTNHost fromNode, NetworkInterface fromInterface, 
						DTNHost toNode, NetworkInterface toInterface) {
		this.fromNode = fromNode;
		this.fromInterface = fromInterface;
		this.toNode = toNode;
		this.toInterface = toInterface;
		this.isUp = true;
		this.bytesTransferred = 0;
		this.underwayTransfer = null;
	}


	/**
	 * Returns true if the connection is up
	 * @return state of the connection
	 */
	public boolean isUp() {
		return isUp;
	}

	/**
	 * Returns true if the connection is not being used
	 * @return true if the connection is not being used
	 */
	public boolean isIdle() {
		return underwayTransfer == null;
	}

	/**
	 * Returns true if there is data flowing on the connection
	 * @return true if the given node is the initiator of the connection
	 */
	public boolean isTransferOngoing() {
		return getRemainingByteCount() > 0;
	}

	/**
	 * Returns true if the given Network Interface is the sender
	 * @return true if the given Network Interface is the sender
	 */
	public boolean isSenderInterface(NetworkInterface ni) {
		return (underwayTransfer != null) && (underwayTransfer.getSenderInterface() == ni);
	}

	/**
	 * Returns true if the given Network Interface is the sender
	 * @return true if the given Network Interface is the sender
	 */
	public boolean isReceiverInterface(NetworkInterface ni) {
		return (underwayTransfer != null) && (underwayTransfer.getReceiverInterface() == ni);
	}

	/**
	 * Returns true if the given node is the initiator of the connection, false
	 * otherwise
	 * @param node The node to check
	 * @return true if the given node is the initiator of the connection
	 */
	public boolean isInitiator(DTNHost node) {
		return node == fromNode;
	}

	/**
	 * Sets the state of the connection.
	 * @param state True if the connection is up, false if not
	 */
	public void setUpState(boolean state) {
		isUp = state;
	}
	
	/**
	 * Returns true if connected to the specified host
	 * @param host The host of which to verify reachability
	 * @return {@code true} if the specified host is reachable
	 * through this Connection, {@code false} otherwise
	 */
	public boolean isConnectedToHost(DTNHost host) {
		if ((fromNode == host) || (toNode == host)) {
			return true;
		}
		
		return false;
	}

	/**
	 * Sets a message that this connection is currently transferring. If message
	 * passing is controlled by external events, this method is not needed
	 * (but then e.g. {@link #finalizeTransfer()} and 
	 * {@link #isMessageTransferred()} will not work either). Only a one message
	 * at a time can be transferred using one connection.
	 * @param m The message
	 * @return The value returned by 
	 * {@link MessageRouter#receiveMessage(Message, DTNHost)}
	 */
	public abstract int startTransfer(DTNHost from, Message m);

	/**
	 * Calculate the current transmission speed from the information
	 * given by the interfaces, and calculate the missing data amount.
	 */
	public void update() {};

	/**
     * Aborts the transfer of the currently transferred message.
     */
	public void abortTransfer() {
		assert ((underwayTransfer != null) && (underwayTransfer.getMsgOnFly() != null)) :
				"No message to abort on the connection " + fromNode + " - " + toNode;	
		int remainingBytes = getRemainingByteCount();

		bytesTransferred += underwayTransfer.getMsgOnFly().getSize() - remainingBytes;
		bytesTransferredForThroughput += underwayTransfer.getMsgOnFly().getSize() - remainingBytes;

		underwayTransfer.getReceiver().messageAborted(underwayTransfer.getMsgOnFly().getId(),
														this, remainingBytes);
		//underwayTransfer.getReceiverInterface().abortMessageReception(this);
		clearMsgOnFly();
	}

	/**
	 * Returns the amount of bytes to be transferred before ongoing transfer
	 * is ready or 0 if there's no ongoing transfer or it has finished
	 * already
	 * @return the amount of bytes to be transferred
	 */
	public abstract int getRemainingByteCount();

	/**
	 * Clears the message that is currently being transferred.
	 * Calls to {@link #getMessage()} will return null after this.
	 */
	protected void clearMsgOnFly() {
		assert underwayTransfer != null;
		underwayTransfer = null;
	}

	/**
	 * Finalizes the transfer of the currently transferred message.
	 * The message that was being transferred can <STRONG>not</STRONG> be
	 * retrieved from this connections after calling this method (using
	 * {@link #getMessage()}).
	 */
	public void finalizeTransfer() {
		assert underwayTransfer != null : "Nothing to finalize in " + this;
		assert underwayTransfer.getSender() != null : "msgFromNode is not set";
		
		bytesTransferred += underwayTransfer.getMsgOnFly().getSize();
		bytesTransferredForThroughput += underwayTransfer.getMsgOnFly().getSize();
		bytesTransferredForGoodput += underwayTransfer.getMsgOnFly().getSize();

		underwayTransfer.getReceiver().messageTransferred(underwayTransfer.getMsgOnFly().getId(), this);
		clearMsgOnFly();
	}

	/**
	 * Returns true if the current message transfer is done 
	 * @return True if the transfer is done, false if not
	 */
	public abstract boolean isMessageTransferred();

	/**
	 * Returns true if the connection is ready to transfer a message (connection
	 * is up and there is no message being transferred).
	 * @return true if the connection is ready to transfer a message
	 */
	public boolean isReadyForTransfer() {
		return isUp && (underwayTransfer == null); 
	}

	/**
	 * Gets the message that this connection is currently transferring.
	 * @return The message or null if no message is being transferred
	 */
	public Message getMessage() {
		if (underwayTransfer != null) {
			return underwayTransfer.getMsgOnFly();
		}
		
		return null;
	}

	/** 
	 * Gets the current connection speed
	 */
	public abstract double getSpeed();	

	/**
	 * Returns the total amount of bytes this connection has transferred so far
	 * (including all transfers).
	 */
	public int getTotalBytesTransferred() {
		if (underwayTransfer == null) {
			return bytesTransferred;
		}
		else {
			if (isMessageTransferred()) {
				return bytesTransferred + underwayTransfer.getMsgOnFly().getSize();
			}
			else {
				return bytesTransferred + 
						(underwayTransfer.getMsgOnFly().getSize() - getRemainingByteCount());
			}
		}
	}

	/**
	 * Returns the node in the other end of the connection
	 * @param node The node in this end of the connection
	 * @return The requested node, or null if the specified
	 * node is not involved in this connection
	 */
	public DTNHost getOtherNode(DTNHost node) {
		if (node == fromNode) {
			return toNode;
		}
		else if (node == toNode) {
			return fromNode;
		}
		
		return null;
	}	

	/**
	 * Returns the node which is transmitting in the connection
	 * @return The requested node, or null if the connection
	 * is not transferring any data
	 */
	public DTNHost getSenderNode() {
		if (underwayTransfer != null) {
			return underwayTransfer.getSender();
		}
		
		return null;
	}	

	/**
	 * Returns the interface which is transmitting in the connection
	 * @return The requested interface, or null if the connection
	 * is not transferring any data
	 */
	public NetworkInterface getSenderInterface() {
		if (underwayTransfer != null) {
			return underwayTransfer.getSenderInterface();
		}
		
		return null;
	}	

	/**
	 * Returns the node which is receiving the message in the connection
	 * @return The requested node, or null if the connection
	 * is not transferring any data
	 */
	public DTNHost getReceiverNode() {
		if (underwayTransfer != null) {
			return underwayTransfer.getReceiver();
		}
		
		return null;
	}
	
	/**
	 * Returns the interface which is transmitting in the connection
	 * @return The requested interface, or null if the connection
	 * is not transferring any data
	 */
	public NetworkInterface getReceiverInterface() {
		if (underwayTransfer != null) {
			return underwayTransfer.getReceiverInterface();
		}
		
		return null;
	}

	/**
	 * Returns true if the given node is sending a message with the
	 * specified String as ID through this connection.
	 * @param senderNode The node sending the message
	 * @param msgID The string which identifies the message being sent
	 * @return True, if the connection is sending a message with the
	 * given msgID, or false otherwise
	 */
	public boolean isSendingMessage(DTNHost senderNode, String msgID) {
		if (underwayTransfer != null) {
			return (underwayTransfer.getSender() == senderNode) &&
					(underwayTransfer.getMsgOnFly().getId() == msgID);
		}
		
		return false;
	}

	/**
	 * Returns true if the given node is receiving a message with the
	 * specified String as ID through this connection.
	 * @param receiverNode The node receiving the message
	 * @param msgID The string which identifies the message being sent
	 * @return True, if the connection is sending a message with the
	 * given msgID, or false otherwise
	 */
	public boolean isReceivingMessage(DTNHost receiverNode, String msgID) {
		if (underwayTransfer != null) {
			return (underwayTransfer.getReceiver() == receiverNode) &&
					(underwayTransfer.getMsgOnFly().getId() == msgID);
		}
		
		return false;
	}

	/**
	 * Returns the interface that the node is using
	 * to manage this connection
	 * @param node The node in this end of the connection
	 * @return The requested interface, or null if node is not
	 * involved in this connection
	 */
	public NetworkInterface getInterfaceForNode(DTNHost node) {
		if (node == fromNode) {
			return fromInterface;
		}
		else if (node == toNode) {
			return toInterface;
		}
		
		return null;
	}

	/**
	 * Returns the interface in the other end of the connection
	 * @param i The interface in this end of the connection
	 * @return The requested interface, or null if i is not
	 * involved in this connection
	 */
	public NetworkInterface getOtherInterface(NetworkInterface i) {
		if (i == fromInterface) {
			return toInterface;
		}
		else if (i == toInterface) {
			return fromInterface;
		}
		
		return null;
	}

	/**
	 * Returns a String presentation of the connection.
	 */
	public String toString() {
		return fromNode + "<->" + toNode + " (" + getSpeed() + "Bps) is " +
				(isUp() ? "up":"down") + (this.underwayTransfer != null ? " transferring " +
				underwayTransfer.getMsgOnFly()  + " from " + underwayTransfer.getSender() : "");
	}

}


class Transfer {
	private Connection transferringConnection;
	private DTNHost msgFromNode;
	private Message msgOnFly;
	
	public Transfer(Connection transferringConnection, DTNHost senderNode, Message m) {
		this.transferringConnection = transferringConnection;
		this.msgFromNode = senderNode;
		this.msgOnFly = m;
	}
	
	public DTNHost getSender() {
		return msgFromNode;
	}
	
	public NetworkInterface getSenderInterface() {
		return transferringConnection.getInterfaceForNode(msgFromNode);
	}
	
	public DTNHost getReceiver() {
		return transferringConnection.getOtherNode(msgFromNode);
	}
	
	public NetworkInterface getReceiverInterface() {
		return transferringConnection.getInterfaceForNode(getReceiver());
	}
	
	public Message getMsgOnFly() {
		return msgOnFly;
	}
}