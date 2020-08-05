/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

package org.apache.pinot.thirdeye.dashboard.resources;

import com.google.inject.Inject;
import javax.ws.rs.Path;
import org.apache.pinot.thirdeye.api.application.ApplicationResource;
import org.apache.pinot.thirdeye.dashboard.resources.v2.AnomaliesResource;
import org.apache.pinot.thirdeye.dashboard.resources.v2.ConfigResource;
import org.apache.pinot.thirdeye.dashboard.resources.v2.DataResource;
import org.apache.pinot.thirdeye.dashboard.resources.v2.alerts.AlertResource;

@Path("/")
public class RootResource {

  private final AlertResource alertResource;
  private final AdminResource adminResource;
  private final AnomaliesResource anomaliesResource;
  private final ApplicationResource applicationResource;
  private final AutoOnboardResource autoOnboardResource;
  private final CacheResource cacheResource;
  private final ConfigResource configResource;
  private final DashboardResource dashboardResource;
  private final DataResource dataResource;

  @Inject
  public RootResource(
      final AlertResource alertResource,
      final AdminResource adminResource,
      final AnomaliesResource anomaliesResource,
      final ApplicationResource applicationResource,
      final AutoOnboardResource autoOnboardResource,
      final CacheResource cacheResource,
      final ConfigResource configResource,
      final DashboardResource dashboardResource,
      final DataResource dataResource) {
    this.alertResource = alertResource;
    this.adminResource = adminResource;
    this.anomaliesResource = anomaliesResource;
    this.applicationResource = applicationResource;
    this.autoOnboardResource = autoOnboardResource;
    this.cacheResource = cacheResource;
    this.configResource = configResource;
    this.dashboardResource = dashboardResource;
    this.dataResource = dataResource;
  }

  @Path("alerts")
  public AlertResource getAlertResource() {
    return alertResource;
  }

  @Path("anomalies")
  public AnomaliesResource getAnomaliesResource() {
    return anomaliesResource;
  }

  @Path("application")
  public ApplicationResource getApplicationResource() {
    return applicationResource;
  }

  @Path(value = "autoOnboard")
  public AutoOnboardResource getAutoOnboardResource() {
    return autoOnboardResource;
  }

  @Path("cache")
  public CacheResource getCacheResource() {
    return cacheResource;
  }

  @Path("config")
  public ConfigResource getConfigResource() {
    return configResource;
  }

  @Path("dashboard")
  public DashboardResource getDashboardResource() {
    return dashboardResource;
  }

  @Path("data")
  public DataResource getDataResource() {
    return dataResource;
  }

  @Path("thirdeye-admin")
  public AdminResource getAdminResource() {
    return adminResource;
  }
}
