/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package report;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;

import core.DTNHost;
import core.Message;
import core.MessageListener;

/**
 * Reports dropped messages. A new report entry is saved in a table
 * every time a message is dropped. Once the simulation is done,
 * a report line for every drop event is written to file in the
 * CSV format. The line contains a field to discern delivered messages
 * from those which never reached their destination.
 * Messages created during the warm up period are ignored.
 * For output syntax, see {@link #HEADER}.
 */
public class CSVPrioritizedMessageDropReport extends Report implements MessageListener {
	/** CSV file header */
	public final static String HEADER = "message_id,source,destination,dropping_node,priority," +
										"created_at,dropped_at,drop/delete,cause,delivered";
	/** Expected number of messages*/
	public final static int START_SIZE = 2000;

	private HashMap<String, ArrayList<String>> abortEventsMap;
	private ArrayList<String> deliveredMessages;
	
	public CSVPrioritizedMessageDropReport() {
		init();
	}
	
	@Override
	public void init() {
		super.init();
		
		write(HEADER);
		abortEventsMap = new HashMap<String, ArrayList<String>>();
		deliveredMessages = new ArrayList<String>();
	}
	
	@Override
	public void registerNode (DTNHost node) {}

	@Override
	public void newMessage (Message m) {
		if (isWarmup()) {
			addWarmupID(m.getID());
			return;
		}
		abortEventsMap.put(m.getID(), new ArrayList<String>());
	}

	@Override
	public void messageTransferred (Message m, DTNHost from, DTNHost to,
									boolean firstDelivery, boolean finalTarget) {
		if (isWarmupID(m.getID())) {
			// Ignore messages created during warmup
			return;
		}
		
		if (firstDelivery && finalTarget) {
			deliveredMessages.add(m.getID());
		}
	}
	
	@Override
	public void messageDeleted (Message m, DTNHost where, boolean dropped, String cause) {
		if (isWarmupID(m.getID())) {
			// Ignore messages created during warmup
			return;
		}
		abortEventsMap.get(m.getID()).add(makeDropEventString(m, where, dropped, cause));
	}

	// nothing to implement for the rest
	@Override
	public void messageTransferAborted (Message m, DTNHost from, DTNHost to, String cause) {}
	@Override
	public void messageTransferStarted (Message m, DTNHost from, DTNHost to) {}
	@Override
	public void messageTransmissionInterfered (Message m, DTNHost from, DTNHost to) {}
	@Override
	public void done() {
		// Print all drop events
		for (Entry<String, ArrayList<String>> entry : abortEventsMap.entrySet()) {
			if (deliveredMessages.contains(entry.getKey())) {
				for (String dropEventString : entry.getValue()) {
					write(dropEventString + "," + "YES");
				}
			}
			else {
				for (String dropEventString : entry.getValue()) {
					write(dropEventString + "," + "NO");
				}
			}
		}
		
		super.done();
	}


	private String makeDropEventString (Message m, DTNHost droppingNode, boolean dropped, String cause) {
		return m.getID() + "," + m.getFrom() + "," + m.getTo() + "," + droppingNode + "," +
				m.getPriority() + "," + format(m.getCreationTime()) + "," + format(getSimTime()) +
				"," + (dropped ? "DROP" : "DELETE") + "," + cause;
	}
	
}
