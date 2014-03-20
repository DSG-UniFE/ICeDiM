/**
 * 
 */
package input;

import org.uncommons.maths.random.MersenneTwisterRNG;

import core.Message;
import core.Settings;
import core.SimError;

/**
 * @author Alex
 *
 */
public class MessagePriorityGenerator {
	
	/** Message priority slots probability -setting id ({@value}). Defines the
	 *  probability that a message is created with a certain priority. By default,
	 *  one and only priority level is defined with 0 and 1 as extremes. */
	public static final String MESSAGE_PRIORITY_SLOT_S = "prioritySlots";
	
	private static MersenneTwisterRNG randomGenerator = null;
	private static final double defaultPrioritySlotRanges[] = {0.0, 1.0};
	
	private final double prioritySlotRanges[];
	
	public MessagePriorityGenerator(Settings s) {
		if (randomGenerator == null) {
			randomGenerator = new MersenneTwisterRNG();
		}
		
		if (!s.contains(MESSAGE_PRIORITY_SLOT_S)) {
			prioritySlotRanges = new double[defaultPrioritySlotRanges.length];
			System.arraycopy(defaultPrioritySlotRanges, 0, prioritySlotRanges, 0,
								defaultPrioritySlotRanges.length);
		}
		else {
			double tempVals[] = s.getCsvDoubles(MESSAGE_PRIORITY_SLOT_S);
			// Check consistency of the array of doubles containing the priority ranges
			for (int i = 0; i < tempVals.length; i++) {
				if ((tempVals[i] <= 0.0) || (tempVals[i] >= 1.0)) {
					throw new SimError("Found invalid value in the " + MESSAGE_PRIORITY_SLOT_S +
										" entry of the settings file");
				}
				else if ((i != 0) && (tempVals[i] <= tempVals[i-1])) {
					throw new SimError("Found an out of order value in the " + MESSAGE_PRIORITY_SLOT_S +
										" entry of the settings file");
				}
			}
			
			// OK! Proceed with copying it
			prioritySlotRanges = new double[tempVals.length + 2];
			prioritySlotRanges[0] = 0.0;
			prioritySlotRanges[prioritySlotRanges.length - 1] = 1.0;
			System.arraycopy(tempVals, 0, prioritySlotRanges, 1, tempVals.length);
			
			Message.MAX_PRIORITY_LEVEL = Math.max(Message.MAX_PRIORITY_LEVEL,
													prioritySlotRanges.length - 2);
		}
	}
	
	public int randomlyGenerateNextPriority() {
		double randD = randomGenerator.nextDouble();

		for (int i = 0; i < prioritySlotRanges.length - 1; i++) {
			if ((randD >= prioritySlotRanges[i]) && (randD < prioritySlotRanges[i + 1])) {
				return i;
			}
		}
		
		throw new SimError("Impossible to find the correct priority slot");
	}

}
