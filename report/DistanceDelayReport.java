/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package report;

import java.util.HashMap;

import core.Coord;
import core.DTNHost;
import core.Message;
import core.MessageListener;

/**
 * Report for how far apart the nodes were when the message
 * was sent and how long time & how many hops it took to deliver it.
 * Only messages created after the warm up period are counted.
 * If message is not delivered, its delivery time & hop count are reported as -1
 */
public class DistanceDelayReport extends Report implements MessageListener {
	/** Syntax of the report lines */
	public static final String SYNTAX = 
		"distance at msg send, delivery time, hop count, MSG_ID";
	private HashMap<String, InfoTuple> creationInfos;
	
	/**
	 * Constructor.
	 */
	public DistanceDelayReport() {
		init();
	}
	
	@Override
	protected void init() {
		super.init();
		this.creationInfos = new HashMap<String, InfoTuple>();
		printHeader();
	}

	/**
	 * This is called when a message is transferred between nodes
	 */
	@Override
	public void messageTransferred(Message m, DTNHost from, DTNHost to,
									boolean firstDelivery, boolean finalTarget) {
		if (isWarmupID(m.getID()) || !(firstDelivery && finalTarget)) {
			return; // report is only interested in first deliveries to target nodes  
		}
		
		InfoTuple info = creationInfos.remove(m.getID());
		if (info == null) {
			return; /* message was created before the warm up period */
		}
		
		report(m.getID(), info.getLoc1().distance(info.getLoc2()),
				getSimTime() - info.getTime(), m.getHops().size()-1);
	}

	/**
	 * This is called when a new message is created
	 */
	public void newMessage(Message m) {
		if (isWarmup()) {
			addWarmupID(m.getID());
			return;
		}
		
		creationInfos.put(m.getID(), new InfoTuple(getSimTime(),
							m.getFrom().getLocation().clone(),
							m.getTo().getLocation().clone()));
	}

	/**
	 * Writes an informative header in the beginning of the file
	 */
	private void printHeader() {
		write("# Scenario " + getScenarioName());
		write("# " + SYNTAX);
	}
	
	/**
	 * Writes a report line
	 * @param id Id of the message
	 * @param startDistance Distance of the nodes when the message was created
	 * @param time Time it took for the message to be delivered
	 * @param hopCount The amount of hops it took to deliver the message
	 */
	private void report(String id, double startDistance, double time, int hopCount) {
		write(format(startDistance) + " " + format(time) + " " + hopCount +	" " + id);
	}
	
	/* nothing to implement for the rest */

	@Override
	public void registerNode(DTNHost node) {}
	@Override
	public void messageDeleted(Message m, DTNHost where, boolean dropped, String cause) {}
	@Override
	public void messageTransferStarted(Message m, DTNHost from, DTNHost to) {}
	@Override
	public void messageTransferAborted(Message m, DTNHost from, DTNHost to, String cause) {}
	@Override
	public void messageTransmissionInterfered(Message m, DTNHost from, DTNHost to) {}

	@Override
	public void done() {
		// report rest of the messages as 'not delivered' (time == -1)
		for (String id : creationInfos.keySet()) {
			InfoTuple info = creationInfos.get(id);
			report(id, info.getLoc1().distance(info.getLoc2()), -1, -1);
		}
		
		super.done();
	}
	
 	/**
	 * Private class that encapsulates time and location related information
	 */
	private class InfoTuple {
		private double time;
		private Coord loc1;
		private Coord loc2;

		public InfoTuple(double time, Coord loc1, Coord loc2) {
			this.time = time;
			this.loc1 = loc1;
			this.loc2 = loc2;
		}

		public Coord getLoc1() {
			return loc1;
		}

		public Coord getLoc2() {
			return loc2;
		}

		public double getTime() {
			return time;
		}
	}

}
