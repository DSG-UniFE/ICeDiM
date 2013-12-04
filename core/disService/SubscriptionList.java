/**
 * 
 */
package core.disService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.uncommons.maths.random.MersenneTwisterRNG;

import com.sun.xml.internal.messaging.saaj.packaging.mime.internet.ParseException;

import core.Settings;
import core.SimError;

/**
 * Class that implements the concept of a list of subscriptions
 * @author Alex
 *
 */
public class SubscriptionList {
	
	private ArrayList<Integer> subscriptionList;

	private int MAX_SUB_ID;
	private int MAX_SUBSCRIPTIONS_PER_NODE;
	private boolean areSubscriptionsRandom;
	
	private static MersenneTwisterRNG RandomIDGenerator = null;
	
	public static final int INVALID_SUB_ID = -1;

	/** Namespace for subscriptions in the setting file -setting id ({@value}). String. */
	public static final String NAMESPACE_ID = "subscriptions";
	/** Max number of subscriptions -setting id ({@value}). int.
	 * If not specified, the value is computed from the number of subIDs specified. */
	public static final String MAX_SUB_SIZE = "maxSize";
	/** List of subscriptions the node performed -setting id ({@value}). Array of int.
	 * If not specified, the subscriptions are randomly generated. */
	public static final String GROUP_SUBSCRIPTION = "subIDs";
	
	/** The max number of subscriptions randomly generated a node can subscribe to */
	private static final int DEFAULT_MAX_SUBSCRIPTIONS_PER_NODE = 5;
	
	
	public SubscriptionList() {
		MAX_SUBSCRIPTIONS_PER_NODE = DEFAULT_MAX_SUBSCRIPTIONS_PER_NODE;
		MAX_SUB_ID = MAX_SUBSCRIPTIONS_PER_NODE - 1;
		areSubscriptionsRandom = false;
		
		this.subscriptionList = new ArrayList<Integer>();
	}
	
	public SubscriptionList(Settings s) throws ParseException {
		this.subscriptionList = new ArrayList<Integer>();
		
		if (s.contains(GROUP_SUBSCRIPTION)) {
			int ids[] = s.getCsvInts(GROUP_SUBSCRIPTION);
			if (ids.length > 0) {
				Arrays.sort(ids);
				if ((ids.length > 1) || (ids[0] != -1)) {
					for (int id : ids) {
						if (id < 0) {
							throw new SimError("Invalid value for a subscription ID");
						}
						addSubscriptionToList(id);
					}
					
					MAX_SUBSCRIPTIONS_PER_NODE = ids.length;
					MAX_SUB_ID = ids[ids.length - 1];
					areSubscriptionsRandom = false;
				}
			}
		}
		
		if (this.subscriptionList.size() == 0) {
			// Parsing was unsuccessful
			if (s.contains(MAX_SUB_SIZE)) {
				MAX_SUBSCRIPTIONS_PER_NODE = s.getInt(MAX_SUB_SIZE);
			}
			else {
				MAX_SUBSCRIPTIONS_PER_NODE = DEFAULT_MAX_SUBSCRIPTIONS_PER_NODE;
			}
			MAX_SUB_ID = (MAX_SUBSCRIPTIONS_PER_NODE == 0) ?
						INVALID_SUB_ID : MAX_SUBSCRIPTIONS_PER_NODE - 1;
			areSubscriptionsRandom = true;
			
			randomizeSubscriptions();
		}
	}
	
	private SubscriptionList(SubscriptionList sl) {
		this.MAX_SUB_ID = sl.MAX_SUB_ID;
		this.MAX_SUBSCRIPTIONS_PER_NODE = sl.MAX_SUBSCRIPTIONS_PER_NODE;
		areSubscriptionsRandom = sl.areSubscriptionsRandom;
		
		this.subscriptionList = new ArrayList<Integer>();
		if (areSubscriptionsRandom) {
			randomizeSubscriptions();
		}
		else {
			for (Integer subID : sl.subscriptionList) {
				addSubscriptionToList(subID);
			}
		}
	}
	
	public SubscriptionList replicate() {
		return new SubscriptionList(this);
	}
	
	public boolean containsSubscriptionID(Integer subID) {
		return subscriptionList.contains(subID);
	}
	
	public void updateSubscriptionList (ArrayList<Integer> newSubscriptionList) {
		subscriptionList = newSubscriptionList;
	}
	
	public void addSubscriptionToList(Integer subID) {
		if (!containsSubscriptionID(subID)) {
			subscriptionList.add(subID);
		}
	}

	private void randomizeSubscriptions() {
		if (MAX_SUBSCRIPTIONS_PER_NODE <= 0) {
			return;
		}
		
		MersenneTwisterRNG r = new MersenneTwisterRNG();
		r.setSeed(System.currentTimeMillis());
		
		int subscriptions = r.nextInt(MAX_SUBSCRIPTIONS_PER_NODE) + 1;
		for (int i = 0; i < subscriptions; ++i) {
			if (addRandomSubscriptionToList() == SubscriptionList.INVALID_SUB_ID) {
				break;
			}
		}
	}
	
	public int addRandomSubscriptionToList() {
		if (subscriptionList.size() >= (MAX_SUB_ID + 1)) {
			return INVALID_SUB_ID;
		}
		
		int subID = getRandomID(MAX_SUB_ID);
		while (containsSubscriptionID(subID)) {
			subID = getRandomID(MAX_SUB_ID);
		}
		addSubscriptionToList(subID);
		
		return subID;
	}
	
	public List<Integer> getSubscriptionList() {
		return subscriptionList;
	}
		
	public int getRandomSubscriptionFromList() {
		if (subscriptionList.size() == 0) {
			return INVALID_SUB_ID;
		}
		return subscriptionList.get(SubscriptionList.getRandomID(MAX_SUB_ID) % subscriptionList.size());
	}
	
	static private int getRandomID(int maxSubID) {
		if (RandomIDGenerator == null) {
			RandomIDGenerator = new MersenneTwisterRNG();
			RandomIDGenerator.setSeed(System.currentTimeMillis());
		}
		
		return RandomIDGenerator.nextInt(maxSubID + 1);
	}
}
