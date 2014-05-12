/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package core;

import input.EventQueue;
import input.ExternalEvent;
import input.ScheduledUpdatesQueue;
import interfaces.ConnectivityGrid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * World contains all the nodes and is responsible for updating their
 * location and connections.
 */
public class World {
	/** namespace of optimization settings ({@value})*/
	public static final String SETTINGS_NS = "Optimization";
	/**
	 * Cell based optimization cell size multiplier -setting id ({@value}).
	 * Single ConnectivityCell's size is the biggest radio range times this.
	 * Larger values save memory and decrease startup time but may result in
	 * slower simulation.
	 * Default value is {@link #DEF_CON_CELL_SIZE_MULT}.
	 * Smallest accepted value is 2.
	 * @see ConnectivityGrid
	 */
	public static final String CELL_SIZE_MULT_S = "cellSizeMult";
	/**
	 * Should the order of node updates be different (random) within every 
	 * update step -setting id ({@value}). Boolean (true/false) variable. 
	 * Default is @link {@link #DEF_RANDOMIZE_UPDATES}.
	 */
	public static final String RANDOMIZE_UPDATES_S = "randomizeUpdateOrder";
	/**
	 * The seed value for the randomizer of the update order -setting id
	 * ({@value}). Integer variable. Default is @link {@link #RANDOM_UPDATE_ORDER_SEED}.
	 */
	public static final String RANDOMIZE_UPDATES_SEED_S = "randomizeUpdateOrderSeed";
	/** default value for cell size multiplier ({@value}) */
	public static final int DEF_CON_CELL_SIZE_MULT = 5;
	/** should the update order of nodes be randomized -setting's default value
	 * ({@value}) */
	public static final boolean DEF_RANDOMIZE_UPDATES = true;
	/** the seed value for the update order randomizer -setting's default value
	 * ({@value}) */
	public static long RANDOM_UPDATE_ORDER_SEED = 1;
	public static Random UPDATE_ORDER_RANDOMIZER = null;

	private int sizeX;
	private int sizeY;
	private List<EventQueue> eventQueues;
	private double updateInterval;
	private SimClock simClock;
	private double nextQueueEventTime;
	private EventQueue nextEventQueue;
	/** list of nodes; nodes are indexed by their network address */
	private List<DTNHost> hosts;
	private boolean simulateConnections;
	/** nodes in the order they should be updated (if the order should be 
	 * randomized; null value means that the order should not be randomized) */
	private ArrayList<DTNHost> updateOrder;
	/** is cancellation of simulation requested from UI */
	private boolean isCancelled;
	private List<UpdateListener> updateListeners;
	/** Queue of scheduled update requests */
	private ScheduledUpdatesQueue scheduledUpdates;

	/** single ConnectivityCell's size is biggest radio range times this */
	private int conCellSizeMult;

	/**
	 * Constructor.
	 */
	public World(List<DTNHost> hosts, int sizeX, int sizeY, double updateInterval,
			List<UpdateListener> updateListeners, boolean simulateConnections,
			List<EventQueue> eventQueues) {
		this.hosts = hosts;
		this.sizeX = sizeX;
		this.sizeY = sizeY;
		this.updateInterval = updateInterval;
		this.updateListeners = updateListeners;
		this.simulateConnections = simulateConnections;
		this.eventQueues = eventQueues;
		
		this.simClock = SimClock.getInstance();
		this.scheduledUpdates = new ScheduledUpdatesQueue();
		this.isCancelled = false;		

		setNextEventQueue();
		initSettings();
	}

	/**
	 * Initializes settings fields that can be configured using Settings class
	 */
	private void initSettings() {
		Settings s = new Settings(SETTINGS_NS);
		boolean randomizeUpdates = DEF_RANDOMIZE_UPDATES;

		if (s.contains(RANDOMIZE_UPDATES_S)) {
			randomizeUpdates = s.getBoolean(RANDOMIZE_UPDATES_S);
		}
		if (randomizeUpdates) {
			// creates the update order array that can be shuffled
			updateOrder = new ArrayList<DTNHost>(hosts);
			if (s.contains(RANDOMIZE_UPDATES_SEED_S)) {
				RANDOM_UPDATE_ORDER_SEED = s.getInt(RANDOMIZE_UPDATES_SEED_S);
			}
			UPDATE_ORDER_RANDOMIZER = new Random(RANDOM_UPDATE_ORDER_SEED);
		}
		else { // null pointer means "don't randomize"
			updateOrder = null;
		}

		if (s.contains(CELL_SIZE_MULT_S)) {
			conCellSizeMult = s.getInt(CELL_SIZE_MULT_S);
		}
		else {
			conCellSizeMult = DEF_CON_CELL_SIZE_MULT;
		}

		// check that values are within limits
		if (conCellSizeMult < 2) {
			throw new SettingsError("Too small value (" + conCellSizeMult +
									") for " + SETTINGS_NS + "." + CELL_SIZE_MULT_S);
		}
	}

	/**
	 * Moves hosts in the world for the time given time initialize host 
	 * positions properly. SimClock must be set to <CODE>-time</CODE>
	 * before calling this method.
	 * @param time The total time (seconds) to move
	 */
	public void warmupMovementModel(double time) {
		if (time <= 0) {
			return;
		}

		while(SimClock.getTime() < -updateInterval) {
			moveHosts(updateInterval);
			simClock.advance(updateInterval);
		}

		double finalStep = -SimClock.getTime();

		moveHosts(finalStep);
		simClock.setTime(0);
	}

	/**
	 * Goes through all event Queues and sets the 
	 * event queue that has the next event.
	 */
	public void setNextEventQueue() {
		EventQueue nextQueue = scheduledUpdates;
		double earliest = nextQueue.nextEventsTime();

		/* find the queue that has the next event */
		for (EventQueue eq : eventQueues) {
			if (eq.nextEventsTime() < earliest){
				nextQueue = eq;	
				earliest = eq.nextEventsTime();
			}
		}

		nextEventQueue = nextQueue;
		nextQueueEventTime = earliest;
	}

	/** 
	 * Update (move, connect, disconnect etc.) all hosts in the world.
	 * Runs all external events that are due between the time when
	 * this method is called and after one update interval.
	 */
	public void update() {
		double runUntil = SimClock.getTime() + updateInterval;

		setNextEventQueue();

		/* process all events that are due until next interval update */
		while (nextQueueEventTime <= runUntil) {
			simClock.setTime(nextQueueEventTime);
			ExternalEvent ee = nextEventQueue.nextEvent();
			ee.processEvent(this);
			updateHosts(); // update all hosts after every event
			setNextEventQueue();
		}

		moveHosts(updateInterval);
		simClock.setTime(runUntil);

		updateHosts();

		/* inform all update listeners */
		for (UpdateListener ul : updateListeners) {
			ul.updated(hosts);
		}
	}

	/**
	 * Updates all hosts (calls update for every one of them). If update
	 * order randomizing is on (updateOrder array is defined), the calls
	 * are made in random order.
	 */
	private void updateHosts() {
		if (updateOrder == null) { // randomizing is off
			for (int i = 0, n = hosts.size(); i < n; i++) {
				if (isCancelled) {
					break;
				}
				hosts.get(i).update(simulateConnections);
			}
		}
		else { // update order randomizing is on
			assert updateOrder.size() == hosts.size() : "Nrof hosts has changed unexpectedly";
			Collections.shuffle(updateOrder, UPDATE_ORDER_RANDOMIZER);
			for (int i = 0, n = updateOrder.size(); i < n; i++) {
				if (isCancelled) {
					break;
				}
				updateOrder.get(i).update(simulateConnections);
			}
		}
	}

	/**
	 * Moves all hosts in the world for a given amount of time
	 * @param timeIncrement The time how long all nodes should move
	 */
	private void moveHosts(double timeIncrement) {
		for (int i = 0, n = hosts.size(); i < n; i++) {
			DTNHost host = hosts.get(i);
			host.move(timeIncrement);			
		}
	}

	/**
	 * Asynchronously cancels the currently running simulation
	 */
	public void cancelSim() {
		isCancelled = true;
	}

	/**
	 * Returns the hosts in a list
	 * @return the hosts in a list
	 */
	public List<DTNHost> getHosts() {
		return hosts;
	}

	/**
	 * Returns the x-size (width) of the world 
	 * @return the x-size (width) of the world 
	 */
	public int getSizeX() {
		return sizeX;
	}

	/**
	 * Returns the y-size (height) of the world 
	 * @return the y-size (height) of the world 
	 */
	public int getSizeY() {
		return sizeY;
	}

	/**
	 * Returns a node from the world by its address
	 * @param address The address of the node
	 * @return The requested node or null if it wasn't found
	 */
	public DTNHost getNodeByAddress(int address) {
		if (address < 0 || address >= hosts.size()) {
			throw new SimError("No host for address " + address + ". Address " +
								"range of 0-" + (hosts.size()-1) + " is valid");
		}

		DTNHost node = this.hosts.get(address);
		assert node.getAddress() == address : "Node indexing failed. " + "Node " +
												node + " in index " + address;

		return node;
	}

	/**
	 * Schedules an update request to all nodes to happen at the specified 
	 * simulation time.
	 * @param simTime The time of the update
	 */
	public void scheduleUpdate(double simTime) {
		scheduledUpdates.addUpdate(simTime);
	}
	
}
