/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
 
package report;

import routing.MessageRouter.MessageDropMode;
import core.DTN2Manager;
import core.DTNHost;
import core.Message;
import core.MessageListener;
import fi.tkk.netlab.dtn.ecla.CLAParser;

/**
 * The DTN2Reporter class is responsible for delivering bundles from
 * The ONE to dtnd. To enable DTN2 connectivity, the class must be
 * specified in the configuration file as a report class.
 * @author teemuk
 */
public class DTN2Reporter extends Report implements MessageListener {
	/**
	 * Creates a new reporter object.
	 */
	public DTN2Reporter() {
		super.init();
		DTN2Manager.setReporter(this);
	}


	@Override
	public void registerNode(DTNHost node) {}


	@Override
	public void transmissionPerformed(Message m, DTNHost source) {}
	
	/**
	 * Method is called when a new message is created
	 * @param m Message that was created
	 */
	@Override
	public void newMessage(Message m) {}
	
	/**
	 * Method is called when a message's transfer is started
	 * @param m The message that is going to be transferred
	 * @param from Node where the message is transferred from 
	 * @param to Node where the message is transferred to
	 */
	@Override
	public void messageTransferStarted(Message m, DTNHost from, DTNHost to) {}
	
	/**
	 * Method is called when a message is deleted
	 * @param m The message that was deleted
	 * @param where The host where the message was deleted
	 * @param dropped True if the message was dropped, false if removed
	 */
	@Override
	public void messageDeleted(Message m, DTNHost where, MessageDropMode dropMode, String cause) {}
	
	/**
	 * Method is called when a message's transfer was aborted before 
	 * it finished
	 * @param m The message that was being transferred
	 * @param from Node where the message was being transferred from 
	 * @param to Node where the message was being transferred to
	 */
	@Override
	public void messageTransferAborted(Message m, DTNHost from, DTNHost to, String cause) {}

	/**
	 * Method is called when a message's transfer was interfered before 
	 * it finished
	 * @param m The message that was being transferred
	 * @param from Node where the message was being transferred from 
	 * @param to Node where the message was being transferred to
	 */
	@Override
	public void messageTransmissionInterfered(Message m, DTNHost from, DTNHost to) {}
	
	/**
	 * Method is called when a message is successfully transferred from
	 * a node to another.
	 * @param m The message that was transferred
	 * @param from Node where the message was transferred from
	 * @param to Node where the message was transferred to
	 * @param firstDelivery Was the target node final destination of the message
	 * and received this message for the first time.
	 */
	@Override
	public void messageTransferred(Message m, DTNHost from, DTNHost to,
									boolean firstDelivery, boolean finalTarget) {
		if (firstDelivery && finalTarget) {
			// We received a BundleMessage that should be passed to dtnd
			CLAParser p = DTN2Manager.getParser(to);
			if (p != null) { // Check that there's a CLA connected to this node
				p.sendBundle((DTN2Manager.getBundle(m.getID())).file);
			}
		}
	}
	
}
