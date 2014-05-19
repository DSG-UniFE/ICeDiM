/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package test;

import java.util.List;

import routing.PassiveRouter;
import core.Connection;
import core.Coord;
import core.DTNHost;
import core.Message;
import core.ModuleCommunicationBus;
import core.NetworkInterface;
import core.SimClock;

/**
 * A test stub of DTNHost for testing. All fields are public so they can be
 * easily read from test cases.
 */
public class TestDTNHost extends DTNHost {
	public double lastUpdate = 0;
	public int nrofConnect = 0;
	public int nrofUpdate = 0;
	public Message recvMessage;
	public DTNHost recvFrom;
	public String abortedId;
	public DTNHost abortedFrom;
	public int abortedBytesRemaining;
	
	public String transferredId;
	public DTNHost transferredFrom;

	
	public TestDTNHost(List<NetworkInterface> li, ModuleCommunicationBus comBus) {
		super(null, null, "TST", li, comBus, new StationaryMovement(new Coord(0,0)), 
				new PassiveRouter(new TestSettings()));
	}
	
	@Override
	public void connect(DTNHost anotherHost) {
		nrofConnect++;
	}
	
	@Override
	public void update(boolean up) {
		nrofUpdate++;
		lastUpdate = SimClock.getTime();
	}
	
	@Override
	public int receiveMessage(Message m, Connection con) {
		recvMessage = m;
		recvFrom = con.getSenderNode();
		return routing.MessageRouter.RCV_OK;
	}
	
	@Override
	public void messageAborted(String id, Connection con, String cause) {
		abortedId = id;
		abortedFrom = con.getSenderNode();
		abortedBytesRemaining = con.getRemainingByteCount();
	}
	
	@Override
	public void messageTransferred(String id, Connection con) {
		transferredId = id;
		transferredFrom = con.getSenderNode();
	}
}
