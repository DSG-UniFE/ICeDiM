/**
 * 
 */
package core.iceDim;

/**
 * Methods common to any router based on the Publisher/Subscriber Model
 * @author Alessandro Morelli
 *
 */
public interface PublisherSubscriber {
	
	/** All the possible ways a Publisher/Subscriber enabled router can
	 * change its Dissemination strategy due to the subscriptionIDs of
	 * messages and the subscriptions of its neighboring nodes */
	public enum SubscriptionBasedDisseminationMode {STRICT, SEMI_PERMEABLE, FLEXIBLE};
	/** The default value for the Publisher/Subscriber dissemination mode */
	public static final SubscriptionBasedDisseminationMode DEFAULT_DISSEMINATION_MODE = 
															SubscriptionBasedDisseminationMode.FLEXIBLE;
	
	/** The property key of messages generated by publisher/subscriber compliant
	 * routers to specify the SubscriptionID the messages belong to.
	 * Returned value is of {@link Integer} type. */
	public static final String SUBSCRIPTION_MESSAGE_PROPERTY_KEY = "subID";
	/** The string that identifies the {@link SubscriptionBasedDisseminationMode} 
	 * option in the settings file */
	public static final String SUBSCRIPTION_BASED_DISSEMINATION_MODE_S = "subDisMode";

	/** Returns the The SubscriptionList manager of the router 
	 * @return the The SubscriptionList manager of the router */
	public SubscriptionListManager getSubscriptionList();

	/** Generates a random subscriptionID among those the node is subscribed to
	 * @return an int representing a subscriptionID */
	public int generateRandomSubID();
}