/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package core;

import interfaces.ConnectivityGrid;
import interfaces.ConnectivityOptimizer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Network interface of a DTNHost. Takes care of connectivity among hosts.
 */
abstract public class NetworkInterface implements ModuleCommunicationListener {
	/** transmit range -setting id ({@value})*/
	public static final String TRANSMIT_RANGE_S = "transmitRange";
	/** transmit speed -setting id ({@value})*/
	public static final String TRANSMIT_SPEED_S = "transmitSpeed";
	/** scanning interval -setting id ({@value})*/
	public static final String SCAN_INTERVAL_S = "scanInterval";
	/** scanning interval -setting id ({@value})*/
	public static final String INTERFERENCE_MODEL = "interferenceModel";
	
	/** {@link ModuleCommunicationBus} identifier for the "scanning interval" 
    variable. */
	public static final String SCAN_INTERVAL_ID = "Network.scanInterval";
	/** {@link ModuleCommunicationBus} identifier for the "radio range" 
	variable. Value type: double */
	public static final String RANGE_ID = "Network.radioRange";
	/** {@link ModuleCommunicationBus} identifier for the "transmission speed" 
    variable. Value type: integer */
	public static final String SPEED_ID = "Network.speed";
	
	private static final int CON_UP = 1;
	private static final int CON_DOWN = 2;

	public static final int UNICAST_OK = 0;
	public static final int UNICAST_DENIED = -1;
	public static final int UNICAST_FAILED = -2;
	public static final int BROADCAST_OK = 0;
	public static final int BROADCAST_DENIED = -1;
	
	private static int nextAddress = 0;
	private static Random rng;
	protected DTNHost host = null;

	protected String interfacetype;
	protected List<Connection> connections; // connected hosts
	private List<ConnectionListener> cListeners = null; // list of listeners
	private int address; // network interface address
	protected double transmitRange;
	protected int transmitSpeed;
	
	protected ConnectivityOptimizer optimizer = null;
	protected InterferenceModel interferenceModel;
	
	/** scanning interval, or 0.0 if n/a */
	private double scanInterval;
	private double lastScanTime;


	static {
		DTNSim.registerForReset(NetworkInterface.class.getCanonicalName());
		reset();
	}
	
	/**
	 * Resets the static fields of the class
	 */
	public static void reset() {
		nextAddress = 0;
		rng = new Random(0);
	}
	
	/**
	 * For creating an empty class of a specific type
	 */
	public NetworkInterface(Settings s) {
		this.interfacetype = s.getNameSpace();
		this.connections = new ArrayList<Connection>();
		this.address = getNextNetAddress();
		this.interferenceModel = null;

		this.transmitRange = s.getDouble(TRANSMIT_RANGE_S);
		this.transmitSpeed = s.getInt(TRANSMIT_SPEED_S);
		ensurePositiveValue(transmitRange, TRANSMIT_RANGE_S);
		ensurePositiveValue(transmitSpeed, TRANSMIT_SPEED_S);
		if (s.contains(SCAN_INTERVAL_S)) {
			scanInterval =  s.getDouble(SCAN_INTERVAL_S);
		} else {
			this.scanInterval = 0;
		}
	}
	
	/**
	 * For creating an empty class of a specific type
	 */
	public NetworkInterface() {
		this.interfacetype = "Default";
		this.connections = new ArrayList<Connection>();
		this.address = getNextNetAddress();
	}
	
	/**
	 * copy constructor
	 */
	public NetworkInterface(NetworkInterface ni) {
		this.interfacetype = ni.interfacetype;
		this.connections = new ArrayList<Connection>();
		this.address = getNextNetAddress();
		this.host = ni.host;
		this.interferenceModel = ni.interferenceModel.replicate();
		this.interferenceModel.setNetworkInterface(this);
		
		this.cListeners = ni.cListeners;
		this.transmitRange = ni.transmitRange;
		this.transmitSpeed = ni.transmitSpeed;
		
		/* draw lastScanTime of [0 -- scanInterval] */
		this.lastScanTime = rng.nextDouble() * scanInterval;
	}

	/**
	 * Replication function
	 */
	abstract public NetworkInterface replicate();

	/**
	 * For setting the host - needed when a prototype is copied for several
	 * hosts
	 * @param host The host where the network interface is
	 */
	public void setHost(DTNHost host) {
		this.host = host;
		ModuleCommunicationBus comBus = host.getComBus();
		comBus.subscribe(SCAN_INTERVAL_ID, this);
		comBus.subscribe(RANGE_ID, this);
		comBus.subscribe(SPEED_ID, this);
		
		optimizer = ConnectivityGrid.ConnectivityGridFactory(
					interfacetype.hashCode(), transmitRange);
		optimizer.addInterface(this);
	}

	/**
	 * For checking what interface type this interface is
	 */
	public String getInterfaceType() {
		return interfacetype;
	}
	
	/**
	 * For setting the connectionListeners
	 * @param cListeners List of connection listeners
	 */
	public void setClisteners(List<ConnectionListener> cListeners) {
		this.cListeners = cListeners;
	}
	
	/**
	 * For setting the interference model
	 * @param iModel InterferenceModel instance
	 */
	public void setInterferenceModel(InterferenceModel iModel) {
		interferenceModel = iModel;
	}

	/**
	 * Returns a new network interface address and increments the address for
	 * subsequent calls.
	 * @return The next address.
	 */
	private synchronized static int getNextNetAddress() {
		return nextAddress++;	
	}

	/**
	 * Returns the network interface address.
	 * @return The address (integer)
	 */
	public int getAddress() {
		return address;
	}

	/**
	 * Returns the transmit range of this network layer
	 * @return the transmit range
	 */
	public double getTransmitRange() {
		return transmitRange;
	}

	/**
	 * Returns the transmit speed of this network layer
	 * @return the transmit speed
	 */
	public int getTransmitSpeed() {
		return transmitSpeed;
	}

	/**
	 * Returns a list of currently connected connections
	 * @return a list of currently connected connections
	 */
	public List<Connection> getConnections() {
		return connections;
	}
	
	public boolean isConnectedTo(NetworkInterface ni) {
		assert this != ni : "Error: references to remote and local NetworkInterface are the same!";
		
		List<Connection> connections = getConnections();
		for (Connection con : connections) {
			if (con.isConnectedToHost(ni.getHost())) {
				return true;
			}
		}
		
		return false;
	}

	/**
	 * Returns the list of nodes currently reachable through this interface
	 * @return the list of nodes currently reachable through this interface
	 */
	public List<DTNHost> getReachableHosts() {
		ArrayList<DTNHost> reachableHosts = new ArrayList<DTNHost>();
		for (Connection con : connections) {
			assert con.isUp() : "Error: Connection in active connections list is down!";
			reachableHosts.add(con.getOtherNode(host));
		}
		
		return reachableHosts;
	}
	
	/**
	 * Returns the number of Messages being received by this interface
	 * @return the number of Messages being received by this interface
	 */
	public int getNumberOfIncomingMessages() {
		return interferenceModel.getIncomingMessageNumber();
	}
	
	/**
	 * Returns all the Messages being received by this interface
	 * @return all the Messages being received by this interface
	 */
	public List<Message> getIncomingMessages() {
		return interferenceModel.getListOfMessagesInTransfer();
	}
	
	/**
	 * Returns the list of Messages being exchanged through this interface
	 * @return the list of nodes currently reachable through this interface
	 */
	public Set<Message> getMessagesInTransfer() {
		HashSet<Message> messagesInTransfer = new HashSet<Message>();
		for (Connection con : connections) {
			if (con.isSenderInterface(this)) {
				messagesInTransfer.add(con.getMessage());
			}
		}
		
		return messagesInTransfer;
	}
	
	/**
	 * Returns true if the Connection passed as a parameter
	 * belongs to the list of connections of this Network Interface
	 * @param con The connection that clients need to verify if
	 * it belongs to this Network Interface
	 * @return true if the Connection passed as a parameter
	 * belongs to the list of connections of this Network Interface
	 */
	public boolean handlesConnection(Connection con) {
		for (Connection c : connections) {
			if (c == con) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Checks if this interface is currently in the scanning mode
	 * @return True if the interface is scanning; false if not
	 */
	public boolean isScanning() {
		double simTime = SimClock.getTime();
		
		if (scanInterval > 0.0) {
			if (simTime < lastScanTime) {
				return false; /* not time for the first scan */
			}
			else if (simTime > lastScanTime + scanInterval) {
				lastScanTime = simTime; /* time to start the next scan round */
				return true;
			}
			else if (simTime != lastScanTime) {
				return false; /* not in the scan round */
			}
		}
		/* interval == 0 or still in the last scan round */
		return true;
	}

	/**
	 * Connects the interface to another interface.
	 * 
	 * Overload this in a derived class.  Check the requirements for
	 * the connection to work in the derived class, then call 
	 * connect(Connection, NetworkInterface) for the actual connection.
	 * @param anotherInterface The interface to connect to
	 * @return 
	 */
	public abstract Connection connect(NetworkInterface anotherInterface);

	/**
	 * Connects this host to another host. The derived class should check 
	 * that all pre-requisites for making a connection are satisfied before 
	 * actually connecting.
	 * @param con The new connection object
	 * @param anotherInterface The interface to connect to
	 */
	protected void connect(Connection con, NetworkInterface anotherInterface) {

		connections.add(con);
		notifyConnectionListeners(CON_UP, anotherInterface.getHost());

		// set up bidirectional connection
		anotherInterface.getConnections().add(con);

		// inform routers about the connection
		host.connectionUp(con);
		anotherInterface.getHost().connectionUp(con);
	}

	/**
	 * Disconnects this host from another host.  The derived class should
	 * make the decision whether to disconnect or not
	 * @param con The connection to tear down
	 */
	protected void disconnect(Connection con, NetworkInterface anotherInterface) {
		con.setUpState(false);
		notifyConnectionListeners(CON_DOWN, anotherInterface.getHost());
		finalizeBeforeDisconnection(con);

		// tear down bidirectional connection
		if (!anotherInterface.getConnections().remove(con)) {
			throw new SimError("No connection " + con + " found in " +
					anotherInterface);	
		}

		// Notify the hosts that the connection is down
		host.connectionDown(con);
		anotherInterface.getHost().connectionDown(con);
	}

	/**
	 * Returns true if another interface is within radio range of this interface
	 * and this interface is also within radio range of the another interface.
	 * @param anotherInterface The another interface
	 * @return True if the interface is within range, false if not
	 */
	protected boolean isWithinRange(NetworkInterface anotherInterface) {
		double smallerRange = anotherInterface.getTransmitRange();
		double myRange = getTransmitRange();
		if (myRange < smallerRange) {
			smallerRange = myRange;
		}

		return host.getLocation().distance(anotherInterface.getHost().getLocation()) <= smallerRange;
	}
	
	/**
	 * Returns true if the given NetworkInterface is connected to this host. 
	 * @param netinterface The other NetworkInterface to check 
	 * @return True if the two hosts are connected
	 */
	protected boolean isConnected(NetworkInterface netInterface) {
		for (int i = 0; i < connections.size(); i++) {
			if (connections.get(i).getOtherInterface(this) == netInterface) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Makes sure that a value is positive
	 * @param value Value to check
	 * @param settingName Name of the setting (for error's message)
	 * @throws SettingsError if the value was not positive
	 */
	protected void ensurePositiveValue(double value, String settingName) {
		if (value < 0) {
			throw new SettingsError("Negative value (" + value +
									") not accepted for setting " + settingName);
		}
	}
	
	/**
	 * Updates the state of current connections (ie tears down connections
	 * that are out of range, recalculates transmission speeds etc.).
	 */
	abstract public void update();
	
	/**
	 * Informs the client wheter this interface has any connection
	 * which is transferring data. If so, the interface is busy and
	 * no other activity should be performed on any connection of
	 * this interface.
	 * This method can implement a very simple form of CSMA/CA control.
	 * @return true if the interface is busy, false otherwise
	 */
	public boolean isBusy() {
		for (Connection con : this.connections) {
			if (con.isUp() && !con.isIdle()) {
				return true;
			}
		}
		
		return false;
	}

	/**
	 * Informs the client wheter this interface is sending data onto
	 * any connections. If so, the interface is busy and no other activity
	 * should be performed on any connection of this interface.
	 * @return true if the interface is busy, false otherwise
	 */
	public boolean isSendingData() {
		for (Connection con : connections) {
			if (con.isSenderInterface(this)) {
				return true;
			}
		}
		
		return false;
	}

	/**
	 * Informs the client wheter this interface is sending
	 * the {@link Message} with the specified ID onto
	 * any of its connections.
	 * @param msgID A {@link String} specifying the ID
	 * of the {@link Message} to check.
	 * @return {@code true} if the {@link Message} with the ID
	 * passed as parameter is being sent, {@code false} otherwise.
	 */
	public boolean isSendingMessage(String msgID) {
		for (Connection con : connections) {
			if (con.isSendingMessage(getHost(), msgID)) {
				return true;
			}
		}
		
		return false;
	}
	
	/**
	 * Informs the client wheter this interface is
	 * receiving data from any connections.
	 * @return true if the interface is receiving data,
	 * false otherwise
	 */
	public boolean isReceivingData() {
		for (Connection con : connections) {
			if (con.isReceiverInterface(this)) {
				return true;
			}
		}
		
		return false;
	}

	/**
	 * Informs the client wheter this interface is
	 * receiving the specified message.
	 * @return true if the interface is receiving the
	 * message, false otherwise
	 */
	public boolean isReceivingMessage(String msgID) {
		for (Message msg : interferenceModel.getListOfMessagesInTransfer()) {
			if (msg.getID().equals(msgID)) {
				return true;
			}
		}
		
		return false;
	}
	
	/**
	 * Informs the client if this interface can begin a new transfer.
	 * @return true if the interface can begin a new transfer, false otherwise
	 */
	public boolean isReadyToBeginTransfer() {
		if (connections.size() <= 0) {
			return false;
		}
		
		// We need at least one connection up & idle
		for (Connection con : connections) {
			if (con.isUp() && con.isIdle()) {
				return true;
			}
		}
		
		return false;
	}
	
	/**
	 * Informs the client if the specified message has been transferred correctly.
	 * @param a String which identifies the message
	 * @return an int among {@code InterferenceModel.RECEPTION_OK}, if successful,
	 * {@code InterferenceModel.RECEPTION_INCOMPLETE}, if the transfer has not been
	 * completed yet, and {@code InterferenceModel.RECEPTION_INTERFERENCE} if the
	 * transfer completed with an interference.
	 */
	public int isMessageTransferredCorrectly(String msgID, Connection con) {
		return interferenceModel.isMessageTransferredCorrectly(msgID, con);
	}
	
	/**
	 * Retrieve the specified message, considering the interference model.
	 * @param a String which identifies the message
	 * @return the specified Message, or {@code null} if the Message was
	 * interfered or no match for the specified {@code msgID} was found. 
	 */
	public Message retrieveTransferredMessage(String msgID, Connection con) {
		return interferenceModel.retrieveTransferredMessage(msgID, con);
	}

	/**
	 * Informs the receiving NetworkInterface that the sender aborted the transfer.
	 * @param msgID the String that identifies the transferring message
	 * @param con the Connection that is transferring the message
	 */
	public Message abortMessageReception(Connection con) {
		if (!handlesConnection(con) || (con.getMessage() == null)) {
			throw new SimError("specified connection is not transferring any message, " +
								"or it does not belong to this network interface");
		}
		
		return interferenceModel.abortMessageReception(con.getMessage().getID(), con);
	}
	
	/**
	 * Send a message using unicast to the specified host
	 * @param m The Message to send
	 * @param host The destination host
	 * @return an int among {@code UNICAST_OK}, if successful,
	 * and {@code UNICAST_DENIED} in case of failure
	 */
	abstract public int sendUnicastMessageToHost(Message m, DTNHost host);
	
	/**
	 * Send a message using unicast to the specified host
	 * @param m The Message to send
	 * @param con The Connection to use
	 * @return an int among {@code UNICAST_OK}, if successful,
	 * and {@code UNICAST_DENIED} in case of failure
	 */
	abstract public int sendUnicastMessageViaConnection(Message m, Connection con);
	
	/**
	 * Send a message in broadcast onto all the connections
	 * of this interface
	 * @param m The message to send
	 * @return an int among {@code BCAST_OK}, if successful,
	 * and {@code BCAST_DENIED} in case of failure
	 */
	abstract public int sendBroadcastMessage(Message m);

	/**
	 * Receive a message on the specified connection
	 * @param m Message to be received
	 * @param con Connection to be used for the trasfer
	 * @return an int among {@code RECEPTION_OK}, if successful,
	 * {@code RECEPTION_DENIED_DUE_TO_SEND}, in the interface is
	 * busy, and {@code RECEPTION_INTERFERENCE} if the new
	 * reception triggers an interference
	 */
	public int beginNewReception(Message m, Connection con) {
		assert con.isTransferOngoing() : "There is no traffic ongoing on this connection";
		assert con.getMessage().getID().equals(m.getID()) : "Message and connection passed " +
															"as parameters do not match!";
		
		return interferenceModel.beginNewReception(m, con);
	}

	/**
	 * Hands off to the interference model the information
	 * about a new, transmitting network interface that
	 * has just fallen into the connection range.
	 * @param m the Message under transfer
	 * @param con the Connection transferring the Message
	 */
	public void beginNewOutOfSynchTransfer(Message m, Connection con) {
		assert con.isTransferOngoing() : "There is no traffic ongoing on this connection";
		assert con.getMessage().getID().equals(m.getID()) : "Message and connection passed " +
															"as parameters do not match!";
		interferenceModel.beginNewOutOfSynchTransfer(m, con);
	}

	/**
	 * Removes an out-of-synch transfer from the interference
	 * model. This method should only be called when both the
	 * interfaces involved in a connection are transmitting, and
	 * we need to remove the 
	 */
	public void removeOutOfSynchTransfer(String msgID, Connection con) {
		if (null == interferenceModel.removeOutOfSynchTransfer(msgID, con)) {
			throw new SimError("Impossible to remove the specified out-of-synch" +
								" transfer from the interference model");
		}
	}

	/**
	 * Force an interference to the specified message in the model.
	 * @param msgID String the ID of the message
	 * @param con the Connection which has been interfered
	 * @return the interfered message
	 */
	public Message forceInterference(String msgID, Connection con) {
		return interferenceModel.forceInterference(msgID, con);
	}

	/**
	 * Notifies all the connection listeners about a change in connections.
	 * @param type Type of the change (e.g. {@link #CON_DOWN} )
	 * @param otherHost The other host on the other end of the connection
	 */
	private void notifyConnectionListeners(int type, DTNHost otherHost) {
		if (cListeners == null) {
			return;
		}
		for (ConnectionListener cl : cListeners) {
			switch (type) {
			case CON_UP:
				cl.hostsConnected(host, otherHost);
				break;
			case CON_DOWN:
				cl.hostsDisconnected(host, otherHost);
				break;
			default:
				assert false : type;	// invalid type code
			}
		}
	}
	
	/**
	 * This method is called by the {@link ModuleCommunicationBus} when/if
	 * someone changes the scanning interval, transmit speed, or range
	 * @param key Identifier of the changed value
	 * @param newValue New value for the variable
	 */
	public void moduleValueChanged(String key, Object newValue) {
		if (key.equals(SCAN_INTERVAL_ID)) {
			scanInterval = (Double)newValue;	
		}
		else if (key.equals(SPEED_ID)) {
			transmitSpeed = (Integer)newValue;	
		}
		else if (key.equals(RANGE_ID)) {
			transmitRange = (Double)newValue;	
		}
		else {
			throw new SimError("Unexpected combus ID " + key);
		}
	}

	/** 
	 * Creates a connection to another host. This method does not do any checks
	 * on whether the other node is in range or active 
	 * (cf. {@link #connect(NetworkInterface)}).
	 * @param anotherInterface The interface to create the connection to
	 * @return 
	 */
	public abstract Connection createConnection(NetworkInterface anotherInterface);
	
	/**
	 * Disconnect a connection between this and another host.
	 * @param anotherInterface The other host's network interface to disconnect 
	 * from this host
	 */
	public void destroyConnection(NetworkInterface anotherInterface) {
		DTNHost anotherHost = anotherInterface.getHost();
		for (int i=0; i < connections.size(); i++) {
			if (connections.get(i).getOtherNode(host) == anotherHost){
				removeConnectionByIndex(i, anotherInterface);
			}
		}
		// the connection didn't exist, do nothing
	}

	/**
	 * Removes a connection by its position (index) in the connections array
	 * of the interface
	 * @param index The array index of the connection to be removed
	 * @param anotherInterface The interface of the other host
	 */
	private void removeConnectionByIndex(int index, NetworkInterface anotherInterface) {
		Connection con = connections.get(index);
		DTNHost anotherNode = anotherInterface.getHost();
		con.setUpState(false);
		notifyConnectionListeners(CON_DOWN, anotherNode);
		finalizeBeforeDisconnection(con);

		// tear down bidirectional connection
		if (!anotherInterface.getConnections().remove(con)) {
			throw new SimError("No connection " + con + " found in " +
					anotherNode);   
		}

		host.connectionDown(con);
		anotherNode.connectionDown(con);

		connections.remove(index);
	}
	
	/**
	 * Perform last actions before disconnecting hosts.
	 * @param con Connection which will be disconnected.
	 */
	protected void finalizeBeforeDisconnection(Connection con) {
		// Note: changedConnection will be called later by the invoking method
		if (con.isTransferOngoing()) {
			// Message transfer has not been completed
			//getHost().getRouter().changedConnection(con);
			con.abortTransfer();
		}
		else if (!con.isIdle()) {
			// Transfer was completed --> finalize transfer before connection goes down
			//getHost().getRouter().changedConnection(con);
			con.finalizeTransfer();
		}
	}

	/**
	 * Start sending data also on the specified Connection.
	 * @param con Connection that will transfer data.
	 */
	public void duplicateTransfer(Connection con) {
		if (!this.isSendingData()) {
			return;
		}
		
		for (Connection c : connections) {
			if ((c != con) && c.isTransferOngoing() &&
				(c.getSenderNode() == getHost())) {
				con.copyMessageTransfer(getHost(), c);
				return;
			}
		}
	}

	/**
	 * Returns the DTNHost of this interface
	 */
	public DTNHost getHost() {
		return host;
	}

	/**
	 * Returns the current location of the host of this interface. 
	 * @return The location
	 */
	public Coord getLocation() {
		return host.getLocation();
	}

	/**
	 * Returns a string representation of the object.
	 * @return a string representation of the object.
	 */
	public String toString() {
		return "Network interface " + address + " of " + host +
				". Connections: " + connections;
	}

}