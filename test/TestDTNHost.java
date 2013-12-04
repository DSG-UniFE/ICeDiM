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

	
	public TestDTNHost(List<NetworkInterface> li, 
			ModuleCommunicationBus comBus) {
		super(null,null,"TST", li, comBus, 
				new StationaryMovement(new Coord(0,0)), 
				new PassiveRouter(new TestSettings()));
	}
	
	@Override
	public void connect(DTNHost anotherHost) {
		this.nrofConnect++;
	}
	
	@Override
	public void update(boolean up) {
		this.nrofUpdate++;
		this.lastUpdate = SimClock.getTime();
	}
	
	@Override
	public int receiveMessage(Message m, Connection con) {
		this.recvMessage = m;
		this.recvFrom = con.getSenderNode();
		return routing.MessageRouter.RCV_OK;
	}
	
	@Override
	public void messageAborted(String id, Connection con) {
		this.abortedId = id;
		this.abortedFrom = con.getSenderNode();
		this.abortedBytesRemaining = con.getRemainingByteCount();
	}
	
	@Override
	public void messageTransferred(String id, Connection con) {
		this.transferredId = id;
		this.transferredFrom = con.getSenderNode();
	}
}
