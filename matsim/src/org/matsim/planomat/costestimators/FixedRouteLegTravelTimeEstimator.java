/* *********************************************************************** *
 * project: org.matsim.*
 * FixedRouteLegTravelTimeEstimator.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007 by the members listed in the COPYING,        *
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

package org.matsim.planomat.costestimators;

import java.util.HashMap;
import java.util.List;

import org.matsim.api.basic.v01.Id;
import org.matsim.api.basic.v01.TransportMode;
import org.matsim.api.basic.v01.population.BasicLeg;
import org.matsim.api.basic.v01.population.PlanElement;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.config.groups.PlanomatConfigGroup;
import org.matsim.core.network.LinkImpl;
import org.matsim.core.population.ActivityImpl;
import org.matsim.core.population.LegImpl;
import org.matsim.core.population.PlanImpl;
import org.matsim.core.population.routes.NetworkRouteWRefs;
import org.matsim.core.router.PlansCalcRoute;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.router.util.LeastCostPathCalculator.Path;

/**
 * Implementation of <code>LegTravelTimeEstimator</code>
 * which estimates the travel time of a fixed route.
 *
 * @author meisterk
 *
 */
public class FixedRouteLegTravelTimeEstimator implements LegTravelTimeEstimator {

	protected final TravelTime linkTravelTimeEstimator;
	protected final DepartureDelayAverageCalculator tDepDelayCalc;
	private final PlansCalcRoute plansCalcRoute;
	private final PlanomatConfigGroup.SimLegInterpretation simLegInterpretation;

	private HashMap<Integer, HashMap<TransportMode, Double>> travelTimeCache = new HashMap<Integer, HashMap<TransportMode, Double>>();

	protected FixedRouteLegTravelTimeEstimator(
			TravelTime linkTravelTimeEstimator,
			DepartureDelayAverageCalculator depDelayCalc,
			PlansCalcRoute plansCalcRoute,
			PlanomatConfigGroup.SimLegInterpretation simLegInterpretation) {

		this.linkTravelTimeEstimator = linkTravelTimeEstimator;
		this.tDepDelayCalc = depDelayCalc;
		this.plansCalcRoute = plansCalcRoute;
		this.simLegInterpretation = simLegInterpretation;

	}

	public double getLegTravelTimeEstimation(Id personId, double departureTime,
			ActivityImpl actOrigin, ActivityImpl actDestination,
			LegImpl legIntermediate, boolean doModifyLeg) {

		double legTravelTimeEstimation = 0.0;

		int legIndex = this.currentPlan.getActLegIndex(legIntermediate);

		if (legIntermediate.getMode().equals(TransportMode.car)) {
			
			// if no fixed route is given, generate free speed route for that leg in a lazy manner
			if (!this.fixedRoutes.containsKey(legIndex)) {
				
				// calculate free speed route and cache it
				Path path = this.plansCalcRoute.getPtFreeflowLeastCostPathCalculator().calcLeastCostPath(
						actOrigin.getLink().getToNode(), 
						actDestination.getLink().getFromNode(), 
						0.0);
				this.fixedRoutes.put(legIndex, path.links);
				
			}
			
			double now = departureTime;
			now = this.processDeparture(actOrigin.getLink(), now);

			if (simLegInterpretation.equals(PlanomatConfigGroup.SimLegInterpretation.CetinCompatible)) {
				now = this.processRouteTravelTime(this.fixedRoutes.get(legIndex), now);
				now = this.processLink(actDestination.getLink(), now);
			} else if (simLegInterpretation.equals(PlanomatConfigGroup.SimLegInterpretation.CharyparEtAlCompatible)) {
				now = this.processLink(actOrigin.getLink(), now);
				now = this.processRouteTravelTime(this.fixedRoutes.get(legIndex), now);
			}

			if (doModifyLeg) {
				NetworkRouteWRefs networkRoute = (NetworkRouteWRefs) this.plansCalcRoute.getRouteFactory().createRoute(
						TransportMode.car, 
						actOrigin.getLink(), 
						actDestination.getLink());
				networkRoute.setLinks(actOrigin.getLink(), this.fixedRoutes.get(legIndex), actDestination.getLink());
				legIntermediate.setRoute(networkRoute);
			}
			
			legTravelTimeEstimation = now - departureTime;
			
		} else {

			HashMap<TransportMode, Double> legInformation = null; 
			if (this.travelTimeCache.containsKey(legIndex)) {
				legInformation = this.travelTimeCache.get(legIndex);
			} else {
				legInformation = new HashMap<TransportMode, Double>();
				this.travelTimeCache.put(legIndex, legInformation);
			}
			if (legInformation.containsKey(legIntermediate.getMode())) {
				legTravelTimeEstimation = legInformation.get(legIntermediate.getMode()).doubleValue();
			} else {
				legTravelTimeEstimation = this.plansCalcRoute.handleLeg(legIntermediate, actOrigin, actDestination, departureTime);
				legInformation.put(legIntermediate.getMode(), legTravelTimeEstimation);
			}
			
		}

		return legTravelTimeEstimation;
		
	}
	
	protected double processDeparture(final LinkImpl link, final double start) {

		double departureDelayEnd = start + this.tDepDelayCalc.getLinkDepartureDelay(link, start);
		return departureDelayEnd;

	}

	protected double processRouteTravelTime(final List<Link> route, final double start) {

		double now = start;

		for (Link link : route) {
			now = this.processLink(link, now);
		}
		return now;

	}

	protected double processLink(final Link link, final double start) {

		double linkEnd = start + this.linkTravelTimeEstimator.getLinkTravelTime(link, start);
		return linkEnd;

	}

	@Override
	public String toString() {
		return this.getClass().getSimpleName();
	}

	public void resetPlanSpecificInformation() {
		this.travelTimeCache.clear();
		this.fixedRoutes.clear();
		this.currentPlan = null;
	}

	private HashMap<Integer, List<Link>> fixedRoutes = new HashMap<Integer, List<Link>>();
	private PlanImpl currentPlan;
	
	public void initPlanSpecificInformation(PlanImpl plan) {

		this.currentPlan = plan;
		
		for (PlanElement planElement : plan.getPlanElements()) {
			
			if (planElement instanceof BasicLeg) {
				
				LegImpl leg = (LegImpl) planElement;
				if (leg.getRoute() instanceof NetworkRouteWRefs) {
					this.fixedRoutes.put(
							this.currentPlan.getActLegIndex(leg), 
							((NetworkRouteWRefs) leg.getRoute()).getLinks());
				}
				
			}
			
		}
		
	}

}
