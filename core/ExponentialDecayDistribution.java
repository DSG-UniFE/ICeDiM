package core;

public class ExponentialDecayDistribution {
	
	public static double extractValueFromDistribution(double seed, double decayingRate) {
		if ((seed < 0.0) || (seed > 1.0)) {
			throw new SimError("Seed value is not in the range [0, 1]");
		}
		
		return -Math.log(1 - ((1 - Math.exp(-decayingRate)) * seed)) / decayingRate;
	}

}
