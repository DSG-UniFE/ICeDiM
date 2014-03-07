/**
 * 
 */
package core.disService;

/**
 * Methods common to any router based on the Publish/Subscriber Model
 * @author Alex
 *
 */
public interface PublishSubscriber {
	
	public SubscriptionListManager getSubscriptionList();
	
	public int generateRandomSubID();
}
