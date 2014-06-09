/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package core;

import java.util.ArrayList;
import java.util.List;

import movement.MovementModel;
import movement.Path;
import routing.MessageRouter;
import routing.MessageRouter.MessageDropMode;
import routing.RoutingInfo;

/**
 * A DTN capable host.
 */
public class DTNHost implements Comparable<DTNHost> {
	private static int nextAddress = 0;
	private int address;

	private Coord location; 	// where is the host
	private Coord destination;	// where is it going

	private MessageRouter router;
	private MovementModel movement;
	private Path path;
	private double speed;
	private double nextTimeToMove;
	private String name;
	private List<MessageListener> msgListeners;
	private List<MovementListener> movListeners;
	private List<NetworkInterface> net;
	private ModuleCommunicationBus comBus;

	static {
		DTNSim.registerForReset(DTNHost.class.getCanonicalName());
		reset();
	}
	/**
	 * Creates a new DTNHost.
	 * @param msgLs Message listeners
	 * @param movLs Movement listeners
	 * @param groupId GroupID of this host
	 * @param interf List of NetworkInterfaces for the class
	 * @param comBus Module communication bus object
	 * @param mmProto Prototype of the movement model of this host
	 * @param mRouterProto Prototype of the message router of this host
	 */
	public DTNHost(List<MessageListener> msgLs, List<MovementListener> movLs, String groupId,
					List<NetworkInterface> interf, ModuleCommunicationBus comBus,
					MovementModel mmProto, MessageRouter mRouterProto) {
		this.comBus = comBus;
		this.location = new Coord(0,0);
		this.address = getNextAddress();
		this.name = groupId + address;
		this.net = new ArrayList<NetworkInterface>();

		for (NetworkInterface i : interf) {
			NetworkInterface ni = i.replicate();
			ni.setHost(this);
			net.add(ni);
		}	

		// TODO - think about the names of the interfaces and the nodes
		//this.name = groupId + ((NetworkInterface)net.get(1)).getAddress();

		this.msgListeners = msgLs;
		this.movListeners = movLs;

		// create instances by replicating the prototypes
		this.movement = mmProto.replicate();
		this.movement.setComBus(comBus);
		setRouter(mRouterProto.replicate());

		this.location = movement.getInitialLocation();
		this.nextTimeToMove = movement.nextPathAvailable();
		this.path = null;

		for (MessageListener ml : this.msgListeners) {
			// TODO check if passing this in the constructor might lead to problems
			ml.registerNode(this);
		}
		if (movLs != null) { // inform movement listeners about the location
			for (MovementListener l : movLs) {
				l.initialLocation(this, this.location);
			}
		}
	}
	
	/**
	 * Returns a new network interface address and increments the address for
	 * subsequent calls.
	 * @return The next address.
	 */
	final private synchronized static int getNextAddress() {
		return nextAddress++;	
	}

	/**
	 * Reset the host and its interfaces
	 */
	final public static void reset() {
		nextAddress = 0;
	}

	/**
	 * Returns true if this node is active (false if not)
	 * @return true if this node is active (false if not)
	 */
	final public boolean isActive() {
		return movement.isActive();
	}

	/**
	 * Set a router for this host
	 * @param router The router to set
	 */
	private void setRouter(MessageRouter router) {
		router.init(this, msgListeners);
		this.router = router;
	}

	/**
	 * Returns the router of this host
	 * @return the router of this host
	 */
	final public MessageRouter getRouter() {
		return router;
	}

	/**
	 * Returns the network-layer address of this host.
	 */
	final public int getAddress() {
		return address;
	}
	
	/**
	 * Returns this hosts's ModuleCommunicationBus
	 * @return this hosts's ModuleCommunicationBus
	 */
	final public ModuleCommunicationBus getComBus() {
		return comBus;
	}
	
    /**
	 * Informs the router of this host about state
	 * change in a {@link Connection} object.
	 * @param con  The {@link Connection} object
	 * whose state changed
	 */
	final public void connectionUp(Connection con) {
		router.changedConnection(con);
	}

	final public void connectionDown(Connection con) {
		router.changedConnection(con);
	}

	/**
	 * Returns a copy of the list of connections this host has with other hosts
	 * @return a copy of the list of connections this host has with other hosts
	 */
	final public List<Connection> getConnections() {
		List<Connection> lc = new ArrayList<Connection>();
		for (NetworkInterface i : net) {
			lc.addAll(i.getConnections());
		}

		return lc;
	}

	/**
	 * Returns the current location of this host. 
	 * @return The location
	 */
	final public Coord getLocation() {
		return location;
	}

	/**
	 * Returns the {@link Path} this node is currently
	 * traveling, or {@code null} if no path is in use
	 * at the moment.
	 * @return The path this node is traveling
	 */
	final public Path getPath() {
		return path;
	}


	/**
	 * Sets the Node's location overriding any location set by movement model
	 * @param location The location to set
	 */
	final public void setLocation(Coord location) {
		this.location = location.clone();
	}

	/**
	 * Sets the Node's name overriding the default name (groupId + netAddress)
	 * @param name The name to set
	 */
	final public void setName(String name) {
		this.name = name;
	}

	/**
	 * Returns the buffer occupancy percentage. Occupancy is 0 for empty
	 * buffer but can be over 100 if a created message is bigger than buffer 
	 * space that could be freed.
	 * @return Buffer occupancy percentage
	 */
	final public double getBufferOccupancy() {
		double bSize = router.getBufferSize();
		double freeBuffer = router.getFreeBufferSize();
		return 100*((bSize-freeBuffer)/bSize);
	}

	/**
	 * Returns routing info of this host's router.
	 * @return The routing info.
	 */
	final public RoutingInfo getRoutingInfo() {
		return router.getRoutingInfo();
	}

	/**
	 * Returns the interface objects of the node
	 */
	final public List<NetworkInterface> getInterfaces() {
		return net;
	}

	/**
	 * Find the {@link NetworkInterface} based on the index
	 * @param interfaceIndex The index of the {@link NetworkInterface}
	 * in the list of {@link NetworkInterface}s of this node.
	 * @return The {@link NetworkInterface} at the specified index.
	 */
	final protected NetworkInterface getInterface(int interfaceIndex) {
		NetworkInterface ni = null;
		try {
			ni = net.get(interfaceIndex - 1);
		} catch (IndexOutOfBoundsException ex) {
			throw new SimError("Interface with index " + interfaceIndex +
								" not found on node " + this);
		}
		
		return ni;
	}

	/**
	 * Find the first {@link NetworkInterface} based on the interface type.
	 * @param interfaceType A {@link String} identifying the type
	 * of the {@link NetworkInterface}.
	 * @return The first {@link NetworkInterface} with the specified
	 * type, if any, or {@code null} otherwise.
	 */
	final protected NetworkInterface getInterface(String interfaceType) {
		for (NetworkInterface ni : net) {
			if (ni.getInterfaceType().equals(interfaceType)) {
				return ni;
			}
		}
		
		return null;	
	}

	/**
	 * Force a connection event
	 */
	public void forceConnection(DTNHost anotherHost, String interfaceId, boolean up) {
		NetworkInterface ni;
		NetworkInterface no;

		if (interfaceId != null) {
			ni = getInterface(interfaceId);
			no = anotherHost.getInterface(interfaceId);

			assert (ni != null) : "Tried to use a nonexisting interfacetype " + interfaceId;
			assert (no != null) : "Tried to use a nonexisting interfacetype " + interfaceId;
		} else {
			ni = getInterface(1);
			no = anotherHost.getInterface(1);
			
			assert (ni.getInterfaceType().equals(no.getInterfaceType())) : 
				"Interface types do not match.  Please specify interface type explicitly";
		}
		
		if (up) {
			ni.createConnection(no);
		} else {
			ni.destroyConnection(no, "force disconnection between " + this + " and " + anotherHost);
		}
	}

	/**
	 * for tests only --- do not use!!!
	 */
	@Deprecated
	public void connect(DTNHost h) {
		System.err.println("WARNING: using deprecated DTNHost.connect(DTNHost)\n" +
							"Use DTNHost.forceConnection(DTNHost,null,true) instead");
		forceConnection(h, null, true);
	}

	/**
	 * for tests only --- do not use!!!
	 */
	@Deprecated
	public Connection getConnection(DTNHost to) {
		assert (this != to) : "Source and destination hosts are the same";
		for (Connection con : getConnections()) {
			if (((con.getSenderNode() == this) && (con.getReceiverNode() == to)) ||
			((con.getSenderNode() == to) && (con.getReceiverNode() == this))) {
				return con;
			}
		}
		
		return null;
	}

	/**
	 * Updates node's network layer and router.
	 * @param simulateConnections Should network layer be updated too
	 */
	public void update(boolean simulateConnections) {
		if (!isActive()) {
			return;
		}
		
		if (simulateConnections) {
			for (NetworkInterface i : net) {
				i.update();
			}
		}
		router.update();
	}

	/**
	 * Moves the node towards the next waypoint, or waits if
	 * it is not the time to move, yet.
	 * @param timeIncrement How long time the node moves.
	 */
	public void move(double timeIncrement) {		
		double possibleMovement;
		double distance;
		double dx, dy;

		if (!isActive() || SimClock.getTime() < this.nextTimeToMove) {
			return; 
		}
		if (destination == null) {
			if (!setNextWaypoint()) {
				return;
			}
		}

		possibleMovement = timeIncrement * speed;
		distance = location.distance(destination);
		while (possibleMovement >= distance) {
			// node can move past its next destination
			location.setLocation(destination); // snap to destination
			possibleMovement -= distance;
			if (!setNextWaypoint()) { // get a new waypoint
				return; // no more waypoints left
			}
			distance = location.distance(destination);
		}

		// move towards the point for possibleMovement amount
		dx = (possibleMovement/distance) * (destination.getX() - location.getX());
		dy = (possibleMovement/distance) * (destination.getY() - location.getY());
		location.translate(dx, dy);
	}	

	/**
	 * Sets the next destination and speed to correspond the next waypoint
	 * on the path.
	 * @return True if there was a next waypoint to set, false if node still
	 * should wait
	 */
	final private boolean setNextWaypoint() {
		if (path == null) {
			path = movement.getPath();
		}

		if (path == null || !path.hasNext()) {
			nextTimeToMove = movement.nextPathAvailable();
			path = null;
			return false;
		}

		destination = path.getNextWaypoint();
		speed = path.getSpeed();

		if (movListeners != null) {
			for (MovementListener l : movListeners) {
				l.newDestination(this, destination, speed);
			}
		}

		return true;
	}

	/**
	 * Start receiving a message from another host
	 * @param m The message
	 * @param from Who the message is from
	 * @return The value returned by 
	 * {@link MessageRouter#receiveMessage(Message, DTNHost)}
	 */
	public int receiveMessage(Message m, Connection con) {
		int retVal = router.receiveMessage(m, con);

		if (retVal == MessageRouter.RCV_OK) {
			m.addNodeOnPath(this);	// add this node on the messages path
		}

		return retVal;
	}

	/**
	 * Requests for deliverable message from this host to be
	 * sent through a connection.
	 * @param con The connection to send the messages through.
	 * @return The {@link Message} being transferred, if the
	 * request is successful, or {@code false} otherwise.
	 */
	public Message requestDeliverableMessages(Connection con) {
		return router.requestDeliverableMessages(con);
	}

	/**
	 * Informs the host that a message was successfully transferred.
	 * @param id Identifier of the message
	 * @param from From who the message was from
	 */
	public void messageTransferred(String id, Connection con) {
		router.messageTransferred(id, con);
	}

	/**
	 * Informs the host that a message transfer was aborted.
	 * @param id Identifier of the message
	 * @param from From who the message was from
	 * @param bytesRemaining Nrof bytes that were left before the transfer
	 * would have been ready; or -1 if the number of bytes is not known
	 */
	public void messageAborted(String id, Connection con, String motivation) {
		this.router.messageAborted(id, con, motivation);
	}

	/**
	 * Creates a new message to this host's router
	 * @param m The message to create
	 */
	public void createNewMessage(Message m) {
		router.createNewMessage(m);
	}

	/**
	 * Deletes a message from this host
	 * @param id Identifier of the message
	 * @param drop True if the message is deleted because of "dropping"
	 * (e.g. buffer is full) or false if it was deleted for some other reason
	 * (e.g. the message got delivered to final destination). This effects the
	 * way the removing is reported to the message listeners.
	 */
	public void deleteMessage(String id, MessageDropMode dropMode, String cause) {
		router.deleteMessage(id, dropMode, cause);
	}

	/**
	 * Notify the router that this connection has been interfered,
	 * so that it can react accordingly. Default actions are to remove
	 * the message from the incomingMessages list and notify listeners.
	 * @param con The connection which is transferring the message
	 */
	public void messageInterfered(String id, Connection con) {
		router.messageInterfered(id, con);
	}

	/**
	 * Returns a string presentation of the host.
	 * @return Host's name
	 */
	public String toString() {
		return name;
	}

	/**
	 * Checks if a host is the same as this host by comparing the object
	 * reference
	 * @param otherHost The other host
	 * @return True if the hosts objects are the same object
	 */
	public boolean equals(DTNHost otherHost) {
		return this == otherHost;
	}

	/**
	 * Compares two DTNHosts by their addresses.
	 * @see Comparable#compareTo(Object)
	 */
	public int compareTo(DTNHost h) {
		return getAddress() - h.getAddress();
	}

}
