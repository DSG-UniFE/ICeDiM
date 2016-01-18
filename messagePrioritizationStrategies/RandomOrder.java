/**
 * 
 */
package messagePrioritizationStrategies;

import java.util.Collections;
import java.util.List;

import org.uncommons.maths.random.MersenneTwisterRNG;

import core.DTNSim;
import core.Message;
import core.SeedGeneratorHelper;

/**
 * @author Alessandro Morelli
 *
 */
public class RandomOrder extends MessageCachingPrioritizationStrategy {

	static private RandomOrder singletonInstance = null;
	static private MersenneTwisterRNG RandomGenerator = null;
	static private final int RandomGeneratorSeed = 1043;

	
	static {
		DTNSim.registerForReset(RandomOrder.class.getCanonicalName());
		reset();
	}
	
	/**
	 * Resets the static fields of the class
	 */
	public static void reset() {
		singletonInstance = null;
		RandomGenerator = null;
	}
	
	static RandomOrder getOrderingInstance() {
		if (singletonInstance == null) {
			singletonInstance = new RandomOrder();
			RandomGenerator = new MersenneTwisterRNG(
					SeedGeneratorHelper.get16BytesSeedFromValue(RandomGeneratorSeed));
		}
		
		return singletonInstance;
	}
	
	private RandomOrder() {
		super(MessageCachingPrioritizationStrategy.CachingPrioritizationMode.Random);
	}
	
	@Override
	public void sortList(List<Message> inputList) {
		Collections.shuffle(inputList, RandomGenerator);
	}

	@Override
	public void sortListInReverseOrder(List<Message> inputList) {
		/* Random queueing strategy also deletes elements randomly,
		 * so this method does the same as the above one */
		sortList(inputList);
	}
	
	@Override
	public int comparatorMethod(Message m1, Message m2) {
		return RandomGenerator.nextInt(3) - 1;
	}

}
