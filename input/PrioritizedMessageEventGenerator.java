package input;

import org.uncommons.maths.random.GaussianGenerator;
import org.uncommons.maths.random.MersenneTwisterRNG;

import core.Settings;
import core.disService.PrioritizedMessage;

public class PrioritizedMessageEventGenerator extends MessageEventGenerator {
	
	private MersenneTwisterRNG randomPriority;
	private GaussianGenerator gGen;

	public PrioritizedMessageEventGenerator(Settings s) {
		super(s);
		
		randomPriority = new MersenneTwisterRNG();
		gGen = new GaussianGenerator(0, 1.5, randomPriority);
	}
	
	
	/** 
	 * Returns the next message creation event
	 * @see input.EventQueue#nextEvent()
	 */
	@Override
	public ExternalEvent nextEvent() {
		int responseSize = 0; /* zero stands for one way messages */
		int msgSize;
		int interval;
		int from;
		int to;
		
		/* Get two *different* nodes randomly from the host ranges */
		from = drawHostAddress(this.hostRange);
		to = drawToAddress(hostRange, from);
		
		msgSize = drawMessageSize();
		interval = drawNextEventTimeDiff();
		
		// Discard values < 0
		int pVal = (int) Math.round(gGen.nextValue());
		while (pVal < 0) {
			pVal = (int) Math.round(gGen.nextValue());
		}
		
		/* Create event and advance to next event */
		PrioritizedMessageCreateEvent mce = new PrioritizedMessageCreateEvent(from, to, this.getID(), 
				msgSize, responseSize, this.nextEventsTime, PrioritizedMessage.MIN_PRIORITY +
				Math.min(pVal, PrioritizedMessage.MAX_PRIORITY));
		this.nextEventsTime += interval;
		
		if (this.msgTime != null && this.nextEventsTime > this.msgTime[1]) {
			/* next event would be later than the end time */
			this.nextEventsTime = Double.MAX_VALUE;
		}
		
		return mce;
	}
}
