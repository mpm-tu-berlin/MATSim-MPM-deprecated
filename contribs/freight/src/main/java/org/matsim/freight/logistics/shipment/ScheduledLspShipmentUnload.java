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

package org.matsim.freight.logistics.shipment;

import org.matsim.api.core.v01.Id;
import org.matsim.freight.logistics.LSPConstants;
import org.matsim.freight.logistics.LSPResource;
import org.matsim.freight.logistics.LogisticChainElement;

class ScheduledLspShipmentUnload implements LspShipmentPlanElement {

	private final double startTime;
  private final double endTime;
  private final LogisticChainElement element;
  private final Id<LSPResource> resourceId;

    ScheduledLspShipmentUnload(LspShipmentUtils.ScheduledShipmentUnloadBuilder builder) {
    this.startTime = builder.startTime;
    this.endTime = builder.endTime;
    this.element = builder.element;
    this.resourceId = builder.resourceId;
    }

  @Override
  public String getElementType() { return LSPConstants.UNLOAD; }

  @Override
  public double getStartTime() {
    return startTime;
  }

  @Override
  public double getEndTime() {
    return endTime;
  }

  @Override
  public LogisticChainElement getLogisticChainElement() {
    return element;
  }

  @Override
  public Id<LSPResource> getResourceId() {
    return resourceId;
  }

}
