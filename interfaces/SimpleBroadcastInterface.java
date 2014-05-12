/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package interfaces;

import java.util.ArrayList;
import java.util.Collection;

import routing.MessageRouter;

import core.CBRConnection;
import core.Connection;
import core.DTNHost;
import core.InterferenceModel;
import core.Message;
import core.NetworkInterface;
import core.Settings;
import core.SimError;

/**
 * A simple Network Interface that provides a constant bit-rate service, where
 * one transmission can be on at a time.
 */
public class SimpleBroadcastInterface extends NetworkInterface {
	/**
	 * Reads the interface settings from the Settings file
	 *  
	 */
	public SimpleBroadcastInterface(Settings s)	{
		super(s);
	}
		
	/**
	 * Copy constructor
	 * @param ni the copied network interface object
	 */
	public SimpleBroadcastInterface(SimpleBroadcastInterface ni) {
		super(ni);
	}

	public NetworkInterface replicate()	{
		return new SimpleBroadcastInterface(this);
	}

	/**
	 * Tries to connect this host to another host. The other host must be
	 * active and within range of this host for the connection to succeed. 
	 * @param anotherInterface The interface to connect to
	 */
	public Connection connect(NetworkInterface anotherInterface) {
		if (isScanning() && anotherInterface.getHost().isActive() &&
			isWithinRange(anotherInterface)) {
			return createConnection(anotherInterface);
		}
		
		return null;
	}

	/** 
	 * Creates a connection to another host. This method does not do any checks
	 * on whether the other node is in range or active 
	 * @param anotherInterface The interface to create the connection to
	 */
	public Connection createConnection(NetworkInterface anotherInterface) {
		if (!isConnected(anotherInterface) && (this != anotherInterface)) {    			
			// connection speed is the lower one of the two speeds 
			int conSpeed = anotherInterface.getTransmitSpeed();
			if (conSpeed > this.transmitSpeed) {
				conSpeed = this.transmitSpeed; 
			}

			Connection con = new CBRConnection(this.host, this, anotherInterface.getHost(),
												anotherInterface, conSpeed);
			connect(con, anotherInterface);
			
			return con;
		}
		
		return null;
	}
	
	/**
	 * Informs the client if this interface can begin a new transfer.
	 * @return true if the interface can begin a new transfer, false otherwise
	 */
	@Override
	public boolean isReadyToBeginTransfer() {
		if (isSendingData() || !super.isReadyToBeginTransfer()) {
			return false;
		}
		
		for (Connection con : connections) {
			if (!con.isReadyForTransfer() || con.getOtherInterface(this).isSendingData()) {
				/* We can safely assume that any connected interface is of the same kind.
				 * If a neighbor node is transmitting, deny the transfer.
				 * Note that this might cause the exposed terminal problem.
				 */
				return false;
			}
		}
		
		return true;
	}

	@Override
	public int beginNewReception(Message m, Connection con) {		
		if (isSendingData()) {
			return InterferenceModel.RECEPTION_DENIED_DUE_TO_SEND;
		}
		
		return super.beginNewReception(m, con);
	}

	@Override
	public int sendUnicastMessageToHost(Message m, DTNHost host) {
		if (getHost() == host) {
			return UNICAST_DENIED;
		}
		
		for (Connection con : connections) {
			if (con.isConnectedToHost(host)) {
				return sendUnicastMessageViaConnection(m, con);
			}
		}
		
		return UNICAST_FAILED;
	}

	@Override
	public int sendUnicastMessageViaConnection(Message m, Connection con) {
		if (!isReadyToBeginTransfer()) {
			return UNICAST_DENIED;
		}
		
		int retVal = con.startTransfer(getHost(), m);
		if (retVal == MessageRouter.RCV_OK || (retVal == MessageRouter.DENIED_INTERFERENCE)) {
			return UNICAST_OK;
		}

		return retVal;
	}
	
	@Override
	public int sendBroadcastMessage(Message m) {
		if (isBusy() || !isReadyToBeginTransfer()) {
			return BROADCAST_DENIED;
		}

		int retVal;
		for (Connection con : connections) {
			retVal = con.startTransfer(getHost(), m);
			if ((retVal != MessageRouter.RCV_OK) && (retVal != MessageRouter.DENIED_INTERFERENCE)) {
				throw new SimError("Error on a connection which resulted ready for transferring");
			}
		}
		
		return BROADCAST_OK;
	}

	/**
	 * Updates the state of current connections (i.e. tears
	 * down connections that are out of range).
	 */
	public void update() {
		// First break the old ones
		optimizer.updateLocation(this);
		for (int i = 0; i < connections.size();) {
			Connection con = connections.get(i);
			NetworkInterface anotherInterface = con.getOtherInterface(this);

			// all connections should be up at this stage
			assert con.isUp() : "Connection " + con + " was down!";

			if (!isWithinRange(anotherInterface)) {
				disconnect(con, anotherInterface);
				connections.remove(i);
			}
			else {
				i++;
			}
		}
		
		// Then find new possible connections
		Collection<NetworkInterface> interfaces = optimizer.getNearInterfaces(this);
		ArrayList<Connection> newConnections = new ArrayList<Connection>();
		for (NetworkInterface ni : interfaces) {
			if (!ni.isConnectedTo(this)) {
				Connection newConnection = connect(ni);
				if (newConnection != null) {
					newConnections.add(newConnection);
				}
			}
		}
		
		// Finally check if new connections could result in interferences
		if (isSendingData()) {
			// transmit remaining data also onto new connections
			for (Connection con : newConnections) {
				duplicateTransfer(con);
			}
		}
		for (Connection con : newConnections) {
			if (con.getOtherInterface(this).isSendingData()) {
				con.getOtherInterface(this).duplicateTransfer(con);
			}
		}
	}

	/**
	 * Returns a string representation of the object.
	 * @return a string representation of the object.
	 */
	public String toString() {
		return "SimpleBroadcastInterface " + super.toString();
	}

}
