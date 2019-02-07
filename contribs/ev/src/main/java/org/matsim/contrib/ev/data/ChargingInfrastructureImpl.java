/* *********************************************************************** *
 * project: org.matsim.*
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2018 by the members listed in the COPYING,        *
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

package org.matsim.contrib.ev.data;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.ev.charging.ChargingLogic;
import org.matsim.core.api.experimental.events.EventsManager;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author michalm
 */
public class ChargingInfrastructureImpl implements ChargingInfrastructure {
	private final Map<Id<Charger>, Charger> chargers = new LinkedHashMap<>();


	@Override
	public Map<Id<Charger>, Charger> getChargers() {
		return Collections.unmodifiableMap(chargers);
	}

    @Override
    public Map<Id<Charger>, Charger> getChargersAtLink(Id<Link> linkId) {
        return chargers.values().stream().filter(charger -> charger.getLink().getId().equals(linkId)).collect(Collectors.toMap(Charger::getId, charger -> charger));
    }

	public void addCharger(Charger charger) {
		chargers.put(charger.getId(), charger);
	}

	@Override
	public void initChargingLogics(ChargingLogic.Factory logicFactory, EventsManager eventsManager) {
		for (Charger c : chargers.values()) {
			ChargingLogic logic = logicFactory.create(c);
			logic.initEventsHandling(eventsManager);
			c.setLogic(logic);
		}
	}
}
