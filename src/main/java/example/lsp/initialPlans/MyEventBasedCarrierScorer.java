package example.lsp.initialPlans;

import com.google.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.freight.carrier.Carrier;
import org.matsim.contrib.freight.carrier.ScheduledTour;
import org.matsim.contrib.freight.carrier.Tour;
import org.matsim.contrib.freight.controler.CarrierScoringFunctionFactory;
import org.matsim.contrib.freight.events.FreightTourEndEvent;
import org.matsim.contrib.freight.events.FreightTourStartEvent;
import org.matsim.core.scoring.ScoringFunction;
import org.matsim.core.scoring.SumScoringFunction;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Kai Martins-Turner (kturner)
 */
class MyEventBasedCarrierScorer implements CarrierScoringFunctionFactory {

	@Inject
	private Network network;

	@Inject
	private Scenario scenario;

	private double toll;

	public ScoringFunction createScoringFunction(Carrier carrier) {
		SumScoringFunction sf = new SumScoringFunction();
		sf.addScoringFunction(new EventBasedScoring(carrier));
		sf.addScoringFunction(new LinkBasedTollScoring(carrier, toll));
		return sf;
	}

	void setToll (double toll) {
		this.toll = toll;
	}

	/**
	 * Calculate the carrier's score based on Events.
	 * Currently, it includes:
	 * - fixed costs (using FreightTourEndEvent)
	 * - time-dependent costs (using FreightTourStart- and -EndEvent)
	 * - distance-dependent costs (using LinkEnterEvent)
	 */
	private class EventBasedScoring implements SumScoringFunction.ArbitraryEventScoring {

		Logger log = LogManager.getLogger(EventBasedScoring.class);
		private final Carrier carrier;
		private double score;

		private final Map<Id<Tour>, Double> tourStartTime = new LinkedHashMap<>();

		public EventBasedScoring(Carrier carrier) {
			super();
			this.carrier = carrier;
		}

		@Override public void finish() {
		}

		@Override public double getScore() {
			return score;
		}

		@Override public void handleEvent(Event event) {
			log.warn(event.toString());
			if (event instanceof FreightTourStartEvent freightTourStartEvent) {
				handleEvent(freightTourStartEvent);
			} else if (event instanceof FreightTourEndEvent freightTourEndEvent) {
				handleEvent(freightTourEndEvent);
			} else if (event instanceof LinkEnterEvent linkEnterEvent) {
				handleEvent(linkEnterEvent);
			}
		}

		private void handleEvent(FreightTourStartEvent event) {
			// Save time of freight tour start
			tourStartTime.put(event.getTourId(), event.getTime());
		}

		//Fix costs for vehicle usage
		private void handleEvent(FreightTourEndEvent event) {
			//Fix costs for vehicle usage
			//FIXME: Bei den FreightServiceEvents sind die MATSim vehicleIds drinnen. --> Gut
			final VehicleType vehicleType = (VehicleUtils.findVehicle(event.getVehicleId(), scenario)).getType();
//			final VehicleType vehicleType = carrier.getCarrierCapabilities().getCarrierVehicles().get(event.getVehicleId()).getType();
			score = score - vehicleType.getCostInformation().getFixedCosts();

			// variable costs per Time
			double tourDuration = event.getTime() - tourStartTime.get(event.getTourId());
			score = score - (tourDuration * vehicleType.getCostInformation().getCostsPerSecond());
		}

		private void handleEvent(LinkEnterEvent event) {
			final double distance = network.getLinks().get(event.getLinkId()).getLength();
			//FIXME: Bei den LinkEnterEvents sind die "Dummy" vehicle"Ids" des carriers drinnen.
			final double costPerMeter = carrier.getCarrierCapabilities().getCarrierVehicles().get(event.getVehicleId()).getType().getCostInformation().getCostsPerMeter();
			score = score - (distance * costPerMeter);
		}

	}

	/**
	 * Calculate some toll for drinving on a link
	 * This a lazy implementation of a cordon toll.
	 */
	class LinkBasedTollScoring implements SumScoringFunction.ArbitraryEventScoring {

		Logger log = LogManager.getLogger(EventBasedScoring.class);
		private final Carrier carrier;
		private final double toll;
		private double score;

		private double tollingCounter;
		private double maxNumberOfTollings; //Begrenze Anzahl der Bemautungen
		private final List<String> vehicleTypesToBeTolled = Arrays.asList("large50");

		public LinkBasedTollScoring(Carrier carrier, double toll) {
			super();
			this.carrier = carrier;
			this.toll = toll;
			for (ScheduledTour scheduledTour : carrier.getSelectedPlan().getScheduledTours()) {
				//Das ist noch nicht Perfekt, weil es besser wäre, dass nur jedes Fahrzeuig wirklich einmal bemautet wird.
				//Aber es begrenzt immerhin auf die Anzahl der Fahrzeuge des Types, die unterwegs sind.
				//Siehe auch untern bei der Bemautung selbst
				//kmt nov '22
				if (vehicleTypesToBeTolled.contains(scheduledTour.getVehicle().getType().getId().toString())) {
					this.maxNumberOfTollings++;
				}
			}
		}

		@Override public void finish() {}

		@Override public double getScore() {
			return score;
		}

		@Override public void handleEvent(Event event) {
			if (event instanceof LinkEnterEvent linkEnterEvent) {
				handleEvent(linkEnterEvent);
			}
		}

		private void handleEvent(LinkEnterEvent event) {
//			List<String> tolledLinkList = Arrays.asList("i(5,5)R");
			List<String> tolledLinkList = Arrays.asList("i(3,4)", "i(3,6)", "i(7,5)R", "i(7,7)R", "j(4,8)R", "j(6,8)R", "j(3,4)", "j(5,4)");

			//TODO: Leider ist hier nicht die (MATSim/mobsim) vehicleId verfügbar, sodass ein echter Ausschluss von Doppel-Bemautung nicht möglich ist.
			//Somit bleibt argumentativ nur, dass es echte Cordon-Maut ist, die bei JEDER Einfahrt bezahlt werden muss.
			final Id<VehicleType> vehicleTypeId = carrier.getCarrierCapabilities().getCarrierVehicles().get(event.getVehicleId()).getType().getId();

			if (tollingCounter < maxNumberOfTollings) {
				if (vehicleTypesToBeTolled.contains(vehicleTypeId.toString())) {
					if (tolledLinkList.contains(event.getLinkId().toString())) {
						log.info("Tolling caused by event: " + event.toString());
						tollingCounter++;
						score = score - toll;
					}
				}
			}
		}
	}
}
