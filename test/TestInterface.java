/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package test;

import routing.MessageRouter;
import core.CBRConnection;
import core.Connection;
import core.DTNHost;
import core.Message;
import core.NetworkInterface;
import core.SimError;

public class TestInterface extends NetworkInterface {
	
	public TestInterface(double range, int speed) {
		transmitRange = range;
		transmitSpeed = speed;
	}
	
	public TestInterface(TestInterface ti) {
		super(ti);
	}
	
	/**
	 * Replication function
	 */
	public NetworkInterface replicate() {
		return new TestInterface(this);
	}
	
	/**
	 * Gives the currentTransmit Speed
	 */
	public int getTransmitSpeed() {
		return transmitSpeed;
	}

	/**
	 * Gives the currentTransmit Range
	 */
	public double getTransmitRange() {
		return transmitRange;
	}
	
	/**
	 * Connects the interface to another interface.
	 * 
	 * Overload this in a derived class.  Check the requirements for
	 * the connection to work in the derived class, then call 
	 * connect(Connection, NetworkInterface) for the actual connection.
	 * @param anotherInterface The host to connect to
	 * @return 
	 */
	public Connection connect(NetworkInterface anotherInterface) {
		Connection con = new CBRConnection(this.getHost(),this, anotherInterface.getHost(),
											anotherInterface, transmitSpeed);
		this.connect(con, anotherInterface);
		
		return con;
	}

	@Override
	public int sendUnicastMessageToHost(Message m, DTNHost host) {		
		for (Connection con : connections) {
			if (con.isConnectedToHost(host)) {
				return sendUnicastMessageViaConnection(m, con);
			}
		}
		
		return UNICAST_FAILED;
	}

	@Override
	public int sendUnicastMessageViaConnection(Message m, Connection con) {
		if ((getHost() == host) || !isReadyToBeginTransfer()) {
			return UNICAST_DENIED;
		}
		
		int retVal = con.startTransfer(getHost(), m);
		if (retVal == MessageRouter.RCV_OK || (retVal == MessageRouter.DENIED_INTERFERENCE)) {
			return UNICAST_OK;
		}

		return UNICAST_FAILED;
	}
	
	@Override
	public int sendBroadcastMessage(Message m) {
		if (isBusy() || !isReadyToBeginTransfer()) {
			return BROADCAST_DENIED;
		}

		int retVal;
		for (Connection con : this.connections) {
			retVal = con.startTransfer(getHost(), m);
			if ((retVal != MessageRouter.RCV_OK) && (retVal != MessageRouter.DENIED_INTERFERENCE)) {
				throw new SimError("Error on a connection which had resulted ready for transferring");
			}
		}
		
		return BROADCAST_OK;
	}

	/**
	 * Updates the state of current connections (ie tears down connections
	 * that are out of range, recalculates transmission speeds etc.).
	 */
	public void update() {
		for (int i = 0; i < connections.size(); ) {
			Connection con = connections.get(i);
			NetworkInterface anotherInterface = con.getOtherInterface(this);

			// all connections should be up at this stage
			assert con.isUp() : "Connection " + con + " was down!";

			if (!isWithinRange(anotherInterface)) {
				disconnect(con, anotherInterface, "node out of range");
				connections.remove(i);
			}
			else {
				i++;
			}
		}
	}

	/** 
	 * Creates a connection to another host. This method does not do any checks
	 * on whether the other node is in range or active 
	 * (cf. {@link #connect(DTNHost)}).
	 * @param anotherHost The host to create the connection to
	 * @return 
	 */
	public Connection createConnection(NetworkInterface anotherInterface) {
		return connect(anotherInterface);
	}

}
