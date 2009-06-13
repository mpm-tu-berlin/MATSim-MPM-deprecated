/* *********************************************************************** *
 * project: org.matsim.*
 * CarRoute.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2008 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.core.api.population;

import java.util.List;

import org.matsim.api.basic.v01.Id;
import org.matsim.core.api.network.Link;
import org.matsim.core.api.network.Node;


/**
 * A route that describes a path in a (road) network. Thus, the route is described usually
 * as a series of links or nodes.
 *
 * @author mrieser
 */
public interface NetworkRoute extends Route {

	public List<Node> getNodes();

	public void setLinks(final Link startLink, final List<Link> srcRoute, final Link endLink);

	/**
	 * @param srcRoute
	 * @deprecated please use method {@link #setNodes(Link, List, Link)} which also specifies start and end Link
	 */
	@Deprecated
	public void setNodes(final List<Node> srcRoute);

	public void setNodes(final Link startLink, final List<Node> srcRoute, final Link endLink);

	public void setTravelCost(final double travelCost);

	public double getTravelCost();

	/**
	 * Returns the list of links that build the route. The links where the route
	 * starts and ends (the links where the activities are on) are <b>not</b>
	 * included in the list.
	 * @return a list containing the links the agents plans to travel along
	 */
	public List<Link> getLinks();

	public List<Id> getLinkIds();
	
	/**
	 * This method returns a new Route object with the subroute of this beginning at fromNode
	 * till toNode. If from or twoNode are not found in this, an IllegalArgumentException is thrown.
	 * @param fromNode
	 * @param toNode
	 * @return A flat copy of the original Route
	 */
	public NetworkRoute getSubRoute(final Node fromNode, final Node toNode);

}
