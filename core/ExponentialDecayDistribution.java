package core;

/**
 * A random number generator that returns values that belongs to
 * an Exponentially Decaying Distribution.
 * 
 * @author Alessandro Morelli
 *
 */

public class ExponentialDecayDistribution {
	
	public static double extractValueFromDistribution(double seed, double decayingRate) {
		if ((seed < 0.0) || (seed > 1.0)) {
			throw new SimError("Seed value is not in the range [0, 1]");
		}
		
		return -Math.log(1 - ((1 - Math.exp(-decayingRate)) * seed)) / decayingRate;
	}

}
