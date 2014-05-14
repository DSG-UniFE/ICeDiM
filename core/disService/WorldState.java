/**
 * 
 */
package core.disService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import core.DTNHost;
import core.NetworkInterface;
import core.Settings;
import core.disService.NeighborInfo;

/**
 * Class that keeps track of all the information
 * shared by DisService nodes
 * 
 * @author Alex
 *
 */

public class WorldState {
	/** Inactivity interval identifier in the options */
	protected static final String INACTIVITY_INTERVAL_OPTION = "inactivityInterval";
	
	/** Default seconds before a peer is qualified as inactive */
	protected static final double DEFAULT_INACTIVITY_INTERVAL = 16.0;
	
	private final DTNHost node;
	private final double inactivityInterval;
	private int genHelloMessagesCount;
	private int totalHelloMessagesReceived;
	private HashMap<DTNHost, NeighborInfo> neighborsInfo;
	
	// PredictionManager predictionManager;
	// NodeDiversityManager nodeDiversityCalculator;
	
	public WorldState(DTNHost node, Settings s) {
		this.node = node;
		if (s.contains(INACTIVITY_INTERVAL_OPTION)) {
			this.inactivityInterval = s.getDouble(INACTIVITY_INTERVAL_OPTION);
		}
		else {
			this.inactivityInterval = DEFAULT_INACTIVITY_INTERVAL;
		}
		this.genHelloMessagesCount = 0;
		this.totalHelloMessagesReceived = 0;
		neighborsInfo = new HashMap<>();
	}	
	
	public WorldState(DTNHost node, WorldState ws) {
		this.node = node;
		this.inactivityInterval = ws.inactivityInterval;
		this.genHelloMessagesCount = 0;
		this.totalHelloMessagesReceived = 0;
		neighborsInfo = new HashMap<>();
	}
	
	public DTNHost getNode() {
		return node;
	}
	
	public int getGeneratedHelloMessagesSentCount() {
		return genHelloMessagesCount;
	}
	
	public int getReceivedHelloMessagesSentCount() {
		return totalHelloMessagesReceived;
	}

	public List<NeighborInfo> getActiveNeighborInfos() {
		ArrayList<NeighborInfo> nearbyNeighbors = new ArrayList<>();
		for (NeighborInfo ni : neighborsInfo.values()) {
			if (ni.isNearby()) {
				nearbyNeighbors.add(ni);
			}
		}
		
		return nearbyNeighbors;
	}

	public List<NeighborInfo> getActiveNeighborInfosByNetworkInterface(NetworkInterface ni) {
		List<DTNHost> reachableNodes = ni.getReachableHosts();
		List<NeighborInfo> nearbyNeighbors = getActiveNeighborInfos();
		for (int i = 0; i < nearbyNeighbors.size(); ) {
			NeighborInfo neighborInfo = nearbyNeighbors.get(i);
			if (!reachableNodes.contains(neighborInfo.getNode())) {
				nearbyNeighbors.remove(i);
			}
			else {
				++i;
			}
		}
		
		return nearbyNeighbors;
	}
	
	public NeighborInfo getNeighborInfo(DTNHost neighborNode) {
		NeighborInfo neighborInfo =  neighborsInfo.get(neighborNode);
		if (neighborInfo != null) {
			return neighborInfo;
		}
		neighborInfo = new NeighborInfo(neighborNode, inactivityInterval);
		neighborsInfo.put(neighborNode, neighborInfo);
		return neighborInfo;
	}
	
	public void incrementGeneratedHelloMessagesCount() {
		++genHelloMessagesCount;
	}
	
	public void processHelloMessage(DisServiceHelloMessage helloMessage) {
		++totalHelloMessagesReceived;
		NeighborInfo neighborInfo = getNeighborInfo(helloMessage.getFrom());
		neighborInfo.processHelloMessage(helloMessage);
	}

	/**
	 * Updates router.
	 * This method should be called (at least once) on every simulation
	 * interval to update the status of transfer(s). 
	 */
	public void update() {
		Collection<NeighborInfo> neighborsInfoCollection = neighborsInfo.values();
		for (NeighborInfo ni : neighborsInfoCollection) {
			ni.update();
		}
	}

}