package messageForwardingOrderManager;

import java.util.ArrayList;
import java.util.List;

import org.uncommons.maths.random.MersenneTwisterRNG;

import core.DTNSim;
import core.Message;
import core.MessageQueueManager;
import core.SeedGeneratorHelper;
import core.Settings;
import core.SimError;
import strategies.MessagePrioritizationStrategy;

/**
 * {@link ExponentiallyDecayingManager} offers first messages,
 * as ordered by the MessageOrdering strategy, with a higher
 * probability.
 * Therefore, the ProbabilisticManager fosters dissemination
 * of messages with the highest priority, while it tries to
 * prevent starvation of lower priority messages.
 * 
 * @author Alessandro Morelli
 *
 */
public class ExponentiallyDecayingManager extends MessageForwardingOrderManager {

	/** Random number generator */
	static MersenneTwisterRNG RANDOM_GENERATOR = null;
	/** Random number generator's seed */
	static final long RANDOM_GENERATOR_SEED = 105;
	
	
	static {
		DTNSim.registerForReset(ExponentiallyDecayingManager.class.getCanonicalName());
		reset();
	}
	
	static private double nextDouble() {
		return RANDOM_GENERATOR.nextDouble();
	}
	
	static public void reset() {
		RANDOM_GENERATOR = null;
	}
	
	public ExponentiallyDecayingManager(Settings s, MessageQueueManager queueManager,
										MessagePrioritizationStrategy orderingStrategy) {
		super(MessageForwardingManagerImplementation.EDP_MANAGER, queueManager, orderingStrategy);
		
		if (RANDOM_GENERATOR == null) {
			// Singleton
			RANDOM_GENERATOR = new MersenneTwisterRNG(
					SeedGeneratorHelper.get16BytesSeedFromValue(RANDOM_GENERATOR_SEED));
		}
	}

	public ExponentiallyDecayingManager(ExponentiallyDecayingManager
										exponentiallyDecayingManager) {
		super(exponentiallyDecayingManager);
	}

	@Override
	public List<Message> orderMessageListForForwarding(List<Message> inputList) {
		if (inputList == null) {
			return new ArrayList<Message>(0);
		}
		if (inputList.size() <= 1) {
			// No need to order lists of 1 or 0 messages
			return new ArrayList<Message>(inputList);
		}
		
		// Compute probability vector and then a new randomly ordered list from it
		return createRandomlyOrderedMessageList(inputList, computeProbabilityVector(inputList.size()));
	}

	/**
	 * Computes a new probability vector according to the order and
	 * the number of Messages in the list passed as parameter.
	 * @param messageList the list of Messages used by the
	 * method to build a new probability vector.
	 * @return a {@code List<Double>} which represents the
	 * computed probability vector of the Message list in input.
	 */
	private List<Double> computeProbabilityVector(int messageListSize) {
		ArrayList<Double> probVector = new ArrayList<Double>(messageListSize);
		
		// Determine decaying rate and define useful variables
		final double decayingRate = 1.0 / messageListSize;
		double decayedValue = 1.0 - decayingRate;
		double probabilityAccumulator = 0.0;
		
		// Compute values along an exponentially decaying distribution 
		for (int i = 0; i < messageListSize; ++i) {
			probabilityAccumulator += decayedValue;
			probVector.add(probabilityAccumulator);
			decayedValue *= decayingRate;	// decay step
		}
		
		// Normalize probability vector values
		for (int i = 0; i < probVector.size() - 1; ++i) {
			probVector.set(i, probVector.get(i) / probabilityAccumulator);
		}
		probVector.set(probVector.size() - 1, 1.0);
		
		return probVector;
	}

	/**
	 * Randomly extracts a message from the list passed as
	 * the first parameter and ordered in accordance with
	 * the probability vector passed as the second parameter.
	 * @param messageList the list of Messages from which
	 * the method will randomly extract one.
	 * @param probVector a {@code List<Double>} containing the
	 * probability ranges to establish message priority values.
	 * @return a Message randomly extracted from the queue.
	 */
	private Message drawMessageFromOrderedList(List<Message> messageList, List<Double> probVector) {
		return messageList.get(findMessageIndex(nextDouble(), probVector));
	}

	/**
	 * Finds the index of the Message in the ordered queue
	 * that corresponds to the value passed as parameter.
	 * @param a double value in the range 0-1.
	 * @param probVector a {@code List<Double>} containing
	 * the probability ranges to establish message priority values.
	 * @return the index the range which contains the value
	 * passed as the first parameter.
	 */
	private int findMessageIndex(double randomVal, List<Double> probVector) {
		if ((randomVal < 0.0) || (randomVal > 1.0)) {
			throw new SimError("Random value " + randomVal + " does not belong to the range 0-1");
		}

		for (int i = 0; i < probVector.size(); ++i) {
			if (randomVal <= probVector.get(i)) {
				return i;
			}
		}
		
		throw new SimError("Unable to find an index that matches the value " + randomVal);
	}

	/**
	 * Randomly orders the list passed as parameter.
	 * @param inputList the List to sort randomly.
	 * @param probVector a {@code List<Double>} containing
	 * the probability ranges to establish message priority values.
	 * @return the List passed as input parameter sorted randomly.
	 */
	private List<Message> createRandomlyOrderedMessageList(List<Message> inputList,
															List<Double> probVector) {
		List<Message> result = new ArrayList<Message>(inputList.size());
		for (int i = 0; i < inputList.size(); ++i) {
			/* Calling drawMessageFromOrderedList() avoids unnecessary
			 * calls to the checkOrderedListConsistency() method */
			Message m = drawMessageFromOrderedList(inputList, probVector);
			
			int drawnIndex = inputList.indexOf(m);
			int index = drawnIndex;
			// Avoid to add duplicates to the result list
			while (result.contains(m) && (index > 0)) {
				m = inputList.get(--index);
			}
			index = drawnIndex;
			while (result.contains(m) && (index < inputList.size())) {
				m = inputList.get(++index);
			}
			
			if (!result.contains(m)) {
				// Add message to result vector
				result.add(m);
			}
			else {
				throw new SimError("An unexpected error occurred while creating the ordered list");
			}
		}
		
		return result;
	}

	@Override
	public MessageForwardingOrderManager replicate() {
		return new ExponentiallyDecayingManager(this);
	}

}