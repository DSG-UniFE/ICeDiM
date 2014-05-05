/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package routing;

import java.util.ArrayList;
import java.util.List;

/**
 * Class for storing routing related information in a tree form for
 * user interface(s). 
 */
public class RoutingInfo {
	private String text;
	private List<RoutingInfo> moreInfo = null;
	
	/**
	 * Creates a routing info based on a text.
	 * @param infoText The text of the info
	 */
	public RoutingInfo(String infoText) {
		text = infoText;
	}
	
	/**
	 * Creates a routing info based on any object. Object's
	 * toString() method's output is used as the info text.
	 * @param o The object this info is based on
	 */
	public RoutingInfo(Object o) {
		text = o.toString();
	}
	
	/**
	 * Adds child info object for this routing info.
	 * @param info The info object to add.
	 */
	public void addMoreInfo(RoutingInfo info) {
		if (moreInfo == null) { // lazy creation
			moreInfo = new ArrayList<RoutingInfo>();
		}
		moreInfo.add(info);
	}
	
	/**
	 * Returns the child routing infos of this info.
	 * @return The children of this info or an empty list if this info
	 * doesn't have any children.
	 */
	public List<RoutingInfo> getMoreInfo() {
		if (moreInfo == null) {
			return new ArrayList<RoutingInfo>(0);
		}
		return moreInfo;
	}
	
	/**
	 * Returns the info text of this routing info.
	 * @return The info text
	 */
	public String toString() {
		return text;
	}
	
}
