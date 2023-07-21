/*
 *  *********************************************************************** *
 *  * project: org.matsim.*
 *  * *********************************************************************** *
 *  *                                                                         *
 *  * copyright       : (C) 2022 by the members listed in the COPYING,        *
 *  *                   LICENSE and WARRANTY file.                            *
 *  * email           : info at matsim dot org                                *
 *  *                                                                         *
 *  * *********************************************************************** *
 *  *                                                                         *
 *  *   This program is free software; you can redistribute it and/or modify  *
 *  *   it under the terms of the GNU General Public License as published by  *
 *  *   the Free Software Foundation; either version 2 of the License, or     *
 *  *   (at your option) any later version.                                   *
 *  *   See also COPYING, LICENSE and WARRANTY file                           *
 *  *                                                                         *
 *  * ***********************************************************************
 */

package lsp.resourceImplementations.transshipmentHub;

import lsp.LSPResource;
import lsp.LSPSimulationTracker;
import lsp.LogisticChainElement;
import lsp.events.HandlingInHubStartsEvent;
import lsp.shipment.*;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.freight.carrier.Carrier;
import org.matsim.contrib.freight.carrier.CarrierService;
import org.matsim.contrib.freight.carrier.ScheduledTour;
import org.matsim.contrib.freight.carrier.Tour;
import org.matsim.contrib.freight.carrier.Tour.ServiceActivity;
import org.matsim.contrib.freight.carrier.Tour.TourElement;
import org.matsim.contrib.freight.controler.FreightUtils;
import org.matsim.contrib.freight.events.CarrierTourEndEvent;
import org.matsim.contrib.freight.events.eventhandler.FreightTourEndEventHandler;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.controler.events.AfterMobsimEvent;
import org.matsim.core.controler.listener.AfterMobsimListener;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class TransshipmentHubTourEndEventHandler implements AfterMobsimListener, LSPSimulationTracker<LSPResource>, FreightTourEndEventHandler {
// Todo: I have made it (temporarily) public because of junit tests :( -- need to find another way to do the junit testing. kmt jun'23

	private final Scenario scenario;
	private final HashMap<CarrierService, TransshipmentHubEventHandlerPair> servicesWaitedFor;
	private final TransshipmentHubResource transshipmentHubResource;
	private final Id<LSPResource> resourceId;
	private final Id<Link> linkId;
	private EventsManager eventsManager;

	/**
	 * This is a TourEndEvent-Handler, doing some stuff regarding the {@link TransshipmentHubResource}.
	 *
	 * @param transshipmentHubResource hub
	 * @param scenario The scenario. Is used to get the Carrier(s).
	 */
	TransshipmentHubTourEndEventHandler(TransshipmentHubResource transshipmentHubResource, Scenario scenario) {
		this.transshipmentHubResource = transshipmentHubResource;
		this.linkId = transshipmentHubResource.getEndLinkId();
		this.resourceId = transshipmentHubResource.getId();
		this.scenario = scenario;
		this.servicesWaitedFor = new HashMap<>();
		this.transshipmentHubResource.addSimulationTracker(this);
	}

	@Override
	public void setEmbeddingContainer(LSPResource pointer) {
	}

	@Override public void setEventsManager( EventsManager eventsManager ) {
		this.eventsManager = eventsManager;
	}

	@Override
	public void notifyAfterMobsim(AfterMobsimEvent event) {
		servicesWaitedFor.clear(); // cleanup after Mobsim ends (instead of doing it in reset() = before Mobsim starts.) kmt oct'22
	}

	@Override
	public void reset(int iteration) {
		// not implemented; cleanup is done after Mobsim ends, because the internal state is (re)set before Mobsim starts.
		// --> cleaning up here is too late.
		// This is maybe not ideal, but works; kmt oct'22
	}

	public void addShipment(LSPShipment shipment, LogisticChainElement logisticChainElement, ShipmentPlan shipmentPlan) {
		TransshipmentHubEventHandlerPair pair = new TransshipmentHubEventHandlerPair(shipment, logisticChainElement);

		for (ShipmentPlanElement planElement : shipmentPlan.getPlanElements().values()) {
			if (planElement instanceof ShipmentLeg transport) {
				if (transport.getLogisticChainElement().getNextElement() == logisticChainElement) {
					servicesWaitedFor.put(transport.getCarrierService(), pair);
				}
			}
		}
	}

	@Override
	public void handleEvent(CarrierTourEndEvent event) {
		Tour tour = null;
		Carrier carrier = FreightUtils.getCarriers(scenario).getCarriers().get(event.getCarrierId());
		Collection<ScheduledTour> scheduledTours = carrier.getSelectedPlan().getScheduledTours();
		for (ScheduledTour scheduledTour : scheduledTours) {
			if (scheduledTour.getTour().getId() == event.getTourId()) {
				tour = scheduledTour.getTour();
				break;
			}
		}
		if ((event.getLinkId() == this.linkId)) {
			assert tour != null;
			if (allShipmentsOfTourEndInOnePoint(tour)) {
				for (TourElement tourElement : tour.getTourElements()) {
					if (tourElement instanceof ServiceActivity serviceActivity) {
						if (serviceActivity.getLocation() == transshipmentHubResource.getStartLinkId()
								&& allServicesAreInOnePoint(tour)
								&& (tour.getStartLinkId() != transshipmentHubResource.getStartLinkId())) {
							logHandlingInHub(serviceActivity.getService(), event.getTime());
						} else {
//							logHandlingInHub(serviceActivity.getService(), event.getTime() + getUnloadEndTime(tour)); //TODO: Das liefert eine RunTimeException, weil es Events gibt, die in der Zwischenzeit stattfinden -> Eventsstream ist nicht chronologisch...
						}
					}
				}
			}
		}
	}

	private boolean allShipmentsOfTourEndInOnePoint(Tour tour) {
		boolean allShipmentsOfTourEndInOnePoint = true;
		for (TourElement tourElement : tour.getTourElements()) {
			if (tourElement instanceof ServiceActivity serviceActivity) {
				if (!servicesWaitedFor.containsKey(serviceActivity.getService())) {
					return false;
				}
			}
		}
		return allShipmentsOfTourEndInOnePoint;
	}

	private void logHandlingInHub(CarrierService carrierService, double startTime) {
		LSPShipment lspShipment = servicesWaitedFor.get(carrierService).shipment;

		double handlingTime = transshipmentHubResource.getCapacityNeedFixed() + transshipmentHubResource.getCapacityNeedLinear() * lspShipment.getSize();
		double endTime = startTime + handlingTime;

		{ //Old logging approach - will be removed at some point in time
			ShipmentPlanElement handle = ShipmentUtils.LoggedShipmentHandleBuilder.newInstance()
					.setLinkId(linkId)
					.setResourceId(resourceId)
					.setStartTime(startTime)
					.setEndTime(endTime)
					.setLogisticsChainElement(servicesWaitedFor.get(carrierService).element)
					.build();
			Id<ShipmentPlanElement> loadId = Id.create(handle.getResourceId() + String.valueOf(handle.getLogisticChainElement().getId()) + handle.getElementType(), ShipmentPlanElement.class);
			if (!lspShipment.getShipmentLog().getPlanElements().containsKey(loadId)) {
				lspShipment.getShipmentLog().addPlanElement(loadId, handle);
			}
		}
		{ // New event-based approach
			//  Könnte sein, dass das jetzt funktioniert.  kai, may'23
			eventsManager.processEvent(new HandlingInHubStartsEvent(startTime, linkId, lspShipment.getId(), resourceId));
		}

	}

	private double getUnloadEndTime(Tour tour) {
		double unloadEndTime = 0;
		for (TourElement element : tour.getTourElements()) {
			if (element instanceof ServiceActivity serviceActivity) {
				unloadEndTime = unloadEndTime + serviceActivity.getDuration();
			}
		}
		return unloadEndTime;
	}

	private boolean allServicesAreInOnePoint(Tour tour) {
		for (TourElement element : tour.getTourElements()) {
			if (element instanceof ServiceActivity activity) {
				if (activity.getLocation() != tour.getEndLinkId()) {
					return false;
				}
			}
		}
		return true;
	}

	public Map<CarrierService, TransshipmentHubEventHandlerPair> getServicesWaitedFor() {
		return servicesWaitedFor;
	}

	public TransshipmentHubResource getTranshipmentHub() {
		return transshipmentHubResource;
	}

	public Id<LSPResource> getResourceId() {
		return resourceId;
	}

	public Id<Link> getLinkId() {
		return linkId;
	}

	public static class TransshipmentHubEventHandlerPair {
		public final LSPShipment shipment;
		public final LogisticChainElement element;

		public TransshipmentHubEventHandlerPair(LSPShipment shipment, LogisticChainElement element) {
			this.shipment = shipment;
			this.element = element;
		}
	}


}
