package messageForwardingManager;

import java.util.ArrayList;
import java.util.List;
import org.uncommons.maths.random.MersenneTwisterRNG;

import core.Message;
import core.MessageQueueManager;
import core.SimError;
import strategies.MessageOrderingStrategy;

/**
 * {@link ProbabilisticManager} offers first messages,
 * as ordered by the MessageOrdering strategy, with
 * a higher probability.
 * Therefore, the ProbabilisticManager fosters dissemination
 * of messages with the highest priority, while it tries to
 * prevent starvation of lower priority messages.
 * 
 * @author Alex
 *
 */
public class ProbabilisticManager extends MessageForwardingManager {

	/** Random number generator */
	static MersenneTwisterRNG randomGenerator = null;
	/** Random number generator's seed */
	static final long SEED = 1;
	
	/** The list of messages ordered according to their priority value */
	List<Message> orderedMessageList;
	/** The list of ranges to establish the probability for each message
	 * to be randomly picked from the {@code orderedMessageList}*/
	List<Double> probabilityVector;
	
	
	public ProbabilisticManager(MessageQueueManager queueManager,
								MessageOrderingStrategy orderingStrategy) {
		super(MessageForwardingManagerImplementation.PROBABILISTIC_MANAGER,
				queueManager, orderingStrategy);
		
		this.orderedMessageList = null;
		this.probabilityVector = null;
		if (ProbabilisticManager.randomGenerator == null) {
			// Singleton
			ProbabilisticManager.randomGenerator = new MersenneTwisterRNG();
			ProbabilisticManager.randomGenerator.setSeed(SEED);
		}
	}

	@Override
	public Message getNextMessage() {
		checkOrderedListConsistency();
		return drawMessageFromOrderedList(orderedMessageList, probabilityVector);
	}

	@Override
	public Message getNextMessageAndAdvanceQueue() {
		// Advancing has no meaning in the probabilistic manager
		return getNextMessage();
	}

	@Override
	public void advanceQueue() { }

	@Override
	public void resetMessageOrder() {
		// Reset and order the orderedMessageList attribute 
		orderedMessageList = messageQueueManager.getMessageList();
		messageOrderingStrategy.sortList(orderedMessageList);
		
		probabilityVector = computeProbabilityVector(orderedMessageList);
	}

	@Override
	public List<Message> getOrderedMessageQueue() {
		checkOrderedListConsistency();
		return createRandomlyOrderedMessageList(orderedMessageList, probabilityVector);
	}

	@Override
	public List<Message> sortMessageList(List<Message> inputList) {
		if (inputList == null) {
			return new ArrayList<Message>(0);
		}
		if (inputList.size() <= 1) {
			return new ArrayList<Message>(inputList);
		}
		
		// Compute probability vector and then a new randomly ordered list from it
		return createRandomlyOrderedMessageList(inputList, computeProbabilityVector(inputList));
	}
	
	private void checkOrderedListConsistency() {
		if ((orderedMessageList == null) || (orderedMessageList.size() != probabilityVector.size()) ||
			(orderedMessageList.size() != messageQueueManager.getNumberOfMessages())) {
			resetMessageOrder();
		}
		else if (!isPermutationOf(orderedMessageList, messageQueueManager.getMessageList())) {
			resetMessageOrder();
		}
	}

	/**
	 * Computes a new probability vector according to the
	 * priority of the Messages in the list passed as parameter.
	 * @param messageList the list of Messages used by the
	 * method to build a new probability vector.
	 * @return a {@code List<Double>} which represents the
	 * computed probability vector of the Message list in input.
	 */
	private List<Double> computeProbabilityVector(List<Message> messageList) {
		if ((messageList == null) || (messageList.size() == 0)) {
			return new ArrayList<Double>(0);
		}
		ArrayList<Double> probVector = new ArrayList<Double>(orderedMessageList.size());
		
		double totalWeight = 0.0;
		for (Message m : messageList) {
			totalWeight += m.getPriority() + 1.0;
		}
		totalWeight = Math.max(totalWeight, 1.0);

		// Assign probabilities to the vector
		final double probabilityStep = 1.0 / totalWeight;
		double probabilityAccumulator = 0.0;
		for (Message m : messageList) {
			probabilityAccumulator += (m.getPriority() + 1.0) * probabilityStep;
			probVector.add(probabilityAccumulator);
		}

		/* Increase probability of the first element, in case of a strictly
		 * positive remainder. This is done by adding the remainder to each
		 * range extreme, with the exception of the first one (0.0). */
		final double probabilityDifference = 1.0 - probabilityAccumulator;
		if (probabilityDifference > 0.0) {
			for (int i = 1; i < probVector.size(); ++i) {
				probVector.set(i, probVector.get(i) + probabilityDifference);
			}
		}
		
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
		return messageList.get(findMessageIndex(randomGenerator.nextDouble(), probVector));
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

		for (int i = 1; i < probVector.size(); ++i) {
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
			int index = inputList.indexOf(m);
			while (result.contains(m)) {
				// Avoid to add duplicates to the result list
				m = inputList.get(++index % inputList.size());
			}
			result.set(i, m);
		}
		
		return result;
	}

}