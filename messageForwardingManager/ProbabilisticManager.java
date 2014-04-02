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

	/** random number generator */
	static MersenneTwisterRNG randomGenerator = null;
	static final long SEED = 1;
	
	List<Message> orderedMessageList;
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
		return drawMessageFromOrderedList();
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
		orderedMessageList = messageOrderingStrategy.messageProcessingOrder(
				messageQueueManager.getMessageList());
		computeProbabilityVector();
	}

	@Override
	public List<Message> getOrderedMessageQueue() {
		checkOrderedListConsistency();
		List<Message> result = new ArrayList<Message>(orderedMessageList.size());
		for (int i = 0; i < orderedMessageList.size(); ++i) {
			/* Calling drawMessageFromOrderedList() avoids unnecessary
			 * calls to the checkOrderedListConsistency() method */
			result.set(i, drawMessageFromOrderedList());
		}
		
		return result;
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

	private void computeProbabilityVector() {
		if ((orderedMessageList == null) || (orderedMessageList.size() == 0)) {
			return;
		}
		probabilityVector = new ArrayList<Double>(orderedMessageList.size());
		
		double totalWeight = 0.0;
		for (Message m : orderedMessageList) {
			totalWeight += m.getPriority() + 1.0;
		}
		totalWeight = Math.max(totalWeight, 1.0);

		// Assign probabilities to the vector
		final double probabilityStep = 1.0 / totalWeight;
		double probabilityAccumulator = 0.0;
		for (Message m : orderedMessageList) {
			probabilityAccumulator += (m.getPriority() + 1.0) * probabilityStep;
			probabilityVector.add(probabilityAccumulator);
		}

		// Increase probability of the first element, if necessary
		final double probabilityDifference = 1.0 - probabilityAccumulator;
		if (probabilityDifference > 0.0) {
			for (int i = 1; i < probabilityVector.size(); ++i) {
				probabilityVector.set(i, probabilityVector.get(i) + probabilityDifference);
			}
		}
	}

	/**
	 * Randomly extracts a message from the ordered queue.
	 * Higher priority messages has higher probability.
	 * @return a Message randomly extracted from the queue.
	 */
	private Message drawMessageFromOrderedList() {
		return orderedMessageList.get(findMessageIndex(randomGenerator.nextDouble()));
	}

	/**
	 * Finds the index of the Message in the ordered queue
	 * that corresponds to the value passed as parameter.
	 * @param a double value in the range 0-1.
	 * @return the index of a Message in the ordered queue.
	 */
	private int findMessageIndex(double randomVal) {
		if ((randomVal < 0.0) || (randomVal > 1.0)) {
			throw new SimError("Random value " + randomVal + " does not belong to the range 0-1");
		}

		for (int i = 1; i < probabilityVector.size(); ++i) {
			if (randomVal <= probabilityVector.get(i)) {
				return i;
			}
		}
		
		throw new SimError("Unable to find an index that matches the value " + randomVal);
	}

}
 