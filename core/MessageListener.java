/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package core;

/**
 * Interface for classes that want to be informed about messages
 * between hosts
 *
 */
public interface MessageListener {
	/**
	 * Method is called when a new host wants to register itself
	 * @param node DTNHost that wants to register itself
	 */
	public void registerNode(DTNHost node);
	
	/**
	 * Method is called when a new message is created
	 * @param m Message that was created
	 */
	public void newMessage(Message m);
	
	/**
	 * Method is called when a message's transfer is started
	 * @param m The message that is going to be transferred
	 * @param from Node where the message is transferred from 
	 * @param to Node where the message is transferred to
	 */
	public void messageTransferStarted(Message m, DTNHost from, DTNHost to);
	
	/**
	 * Method is called when a message is deleted
	 * @param m The message that was deleted
	 * @param where The host where the message was deleted
	 * @param dropped True if the message was dropped, false if removed
	 * @param cause a String describing the reason for the deletion
	 */
	public void messageDeleted(Message m, DTNHost where, boolean dropped, String cause);
	
	/**
	 * Method is called when a message's transfer was aborted before 
	 * it finished
	 * @param m The message that was being transferred
	 * @param from Node where the message was being transferred from 
	 * @param to Node where the message was being transferred to
	 */
	public void messageTransferAborted(Message m, DTNHost from, DTNHost to);
	
	/**
	 * Method is called when a message is successfully transferred from
	 * a node to another.
	 * @param m The message that was transferred
	 * @param from Node where the message was transferred from
	 * @param to Node where the message was transferred to
	 * @param firstDelivery Was this the first time the node received the message?
	 * @param finalTarget Was the target node the final recipient of the message?
	 */
	public void messageTransferred(Message m, DTNHost from, DTNHost to,
									boolean firstDelivery, boolean finalTarget);

	/**
	 * Method is called when a message cannot be transferred successfully because
	 * it suffers from interference with other transmissions
	 * @param m The message that was transferred
	 * @param from Node where the message was transferred from
	 * @param to Node where the message was transferred to
	 */
	public void messageTransmissionInterfered(Message m, DTNHost from, DTNHost to);
}
