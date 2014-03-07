package core.disService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import core.DTNHost;
import core.SimClock;

public class NeighborInfo {
	private final DTNHost node;
    private double firstActivity;				// First time of a new contact
    private double lastActivity;				// Last contact time
    private final double inactivityInterval;	// Seconds before qualifying the neighbor as inactive
    private int pingCount;						// Number of HELLO messages received
    private int contactsCount;					// Incremented any time the other node gets back into this node's range
    private boolean isNearby;					// true if a HELLO message was recently received

    SubscriptionListManager subscriptionList;			// The list of the node's subscriptions
    HashSet<String> receivedMessages;			// The list of message IDs received by this neighbor
    ArrayList<Contact> contactsList;			// The list of past contacts with this neighbor
    ArrayList<Double> contactDurationList;    	// maintains the lengths of the contacts with the neighbor
    ArrayList<Double> intercontactTimes;		// maintains the lengths of intercontact times with this neighbor

    /* TODO: yet to implement */
    //short periodicity;
    //double predictionAccuracy;
    //double nodeDiversity;    
    //QualityStatistics qualityStats;
    
	public NeighborInfo(DTNHost node, double inactivityInterval) {
		this.node = node;
		this.firstActivity = -1.0;
		this.lastActivity = -1.0;
		this.inactivityInterval = inactivityInterval;
		this.pingCount = 0;
		this.contactsCount = 0;
		this.isNearby = false;
		
		this.subscriptionList = new SubscriptionListManager();
		this.receivedMessages = new HashSet<String>();
		this.contactsList = new ArrayList<Contact>();
		this.contactDurationList = new ArrayList<Double>();
		this.intercontactTimes = new ArrayList<Double>();
	}
	
	public void processHelloMessage(DisServiceHelloMessage helloMessage) {
		incrementPingCount();
		if (firstActivity < 0.0) {
			firstActivity = SimClock.getTime();
		}
		if (!isNearby) {
			incrementContactsCount();
			contactsList.add(new Contact(SimClock.getTime()));
			// TODO: Add a limit to the size of contactsList
			if (lastActivity >= 0.0) {
				intercontactTimes.add(new Double(SimClock.getTime() - lastActivity));
			}
			receivedMessages.clear();
		}
		lastActivity = SimClock.getTime();
		isNearby = true;
		
		subscriptionList.updateSubscriptionList(helloMessage.getNodeSubscriptionsList());
		updateReceivedMessages(helloMessage.getMsgIDs());
	}
	
	public void update() {
		// TODO update method
		if (isNearby && ((SimClock.getTime() - lastActivity) > inactivityInterval)) {
			isNearby = false;
			if (contactsList.size() > 0) {
				contactsList.get(contactsList.size() - 1);
			}
		}
	}

	public DTNHost getNode() {
		return node;
	}

	public double getFirstActivity() {
		return firstActivity;
	}

	public double getLastActivity() {
		return lastActivity;
	}

	public double geInactivityInterval() {
		return inactivityInterval;
	}

	public int getPingCount() {
		return pingCount;
	}

	public int getContactsCount() {
		return contactsCount;
	}

	public boolean isNearby() {
		return isNearby;
	}

	public SubscriptionListManager getSubscriptionList() {
		return subscriptionList;
	}

	public List<String> getReceivedMessagesList() {
		return new ArrayList<String> (receivedMessages);
	}

	public ArrayList<Contact> getContactsList() {
		return contactsList;
	}

	public ArrayList<Double> getContactDurationList() {
		return contactDurationList;
	}

	public ArrayList<Double> getIntercontactTimes() {
		return intercontactTimes;
	}

	public void setFirstActivity(double firstActivity) {
		this.firstActivity = firstActivity;
	}

	public void setLastActivity(double lastActivity) {
		this.lastActivity = lastActivity;
	}

	private void incrementPingCount () {
		++this.pingCount;
	}

	private void incrementContactsCount() {
		++this.contactsCount;
	}

	public void updateReceivedMessages(List<String> receivedMessageList) {
		for (String msgID : receivedMessageList) {
			receivedMessages.add(msgID);
		}
	}

	public void addNewContact(Contact contact) {
		this.contactsList.add(contact);
	}

	public void addNewContactDuration(Double contactDuration) {
		this.contactDurationList.add(contactDuration);
	}

	public void addNewIntercontactTime(Double intercontactTime) {
		this.intercontactTimes.add(intercontactTime);
	}
}

class Contact {
	private double start;
	private double stop;
	private double durPrediction;      // contact duration prediction
	private double ncPrediction;       // next contact prediction
	
	public Contact(double start) {
		this.start = start;
	}
	
	public double getStart() {
		return start;
	}
	
	public double getStop() {
		return stop;
	}
	
	public double getDurPrediction() {
		return durPrediction;
	}
	
	public double getNcPrediction() {
		return ncPrediction;
	}
	
	public void setStop(double stop) {
		this.stop = stop;
	}
	
	public void setDurPrediction(double durPrediction) {
		this.durPrediction = durPrediction;
	}
	
	public void setNcPrediction(double ncPrediction) {
		this.ncPrediction = ncPrediction;
	}
	
}
