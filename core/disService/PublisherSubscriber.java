/**
 * 
 */
package core.disService;

/**
 * Methods common to any router based on the Publish/Subscriber Model
 * @author Alex
 *
 */
public interface PublisherSubscriber {
	
	public static final String SUBSCRIPTION_MESSAGE_PROPERTY_KEY = "subID";
	
	public SubscriptionListManager getSubscriptionList();
	
	public int generateRandomSubID();
}
