/* *********************************************************************** *
 * project: kai
 * KaiControler.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2012 by the members listed in the COPYING,        *
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

package playground.kai.usecases.simpleadaptivesignalengine;

import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.otfvis.OTFVis;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.groups.OTFVisConfigGroup.ColoringScheme;
import org.matsim.core.controler.Controler;
import org.matsim.core.mobsim.framework.MobsimFactory;
import org.matsim.core.mobsim.framework.Mobsim;
import org.matsim.ptproject.qsim.QSim;
import org.matsim.vis.otfvis.OTFClientLive;
import org.matsim.vis.otfvis.OnTheFlyServer;

public class Main {

	public static void main(String[] args) {
		final boolean useOTFVis = false ;
		
		Controler controler = new Controler( "examples/config/daganzo-config.xml" ) ;

		final SimpleAdaptiveSignal simpleAdaptiveSignalEngine = new SimpleAdaptiveSignal(controler) ;

		final MobsimFactory mobsimFactory = new MobsimFactory() {
			@Override
			public Mobsim createMobsim(Scenario sc, EventsManager events) {
				QSim qsim = QSim.createQSimAndAddAgentSource(sc, events ) ;
				qsim.addQueueSimulationListeners(simpleAdaptiveSignalEngine) ;
				events.addHandler(simpleAdaptiveSignalEngine) ;
				if ( useOTFVis ) {
					OnTheFlyServer server = OTFVis.startServerAndRegisterWithQSim(sc.getConfig(), sc, events, qsim);
					OTFClientLive.run(sc.getConfig(), server);
				}
				return qsim ;
			}
			
		} ;
		
		controler.setOverwriteFiles(true) ;
		controler.setMobsimFactory(mobsimFactory) ;
		if ( useOTFVis ) {
			controler.getConfig().otfVis().setColoringScheme( ColoringScheme.byId ) ;
		}
		controler.run();
	
	}

}
