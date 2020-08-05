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
import org.apache.pinot.thirdeye.api.user.dashboard.UserDashboardResource;
import org.apache.pinot.thirdeye.dashboard.resources.v2.AnomaliesResource;
import org.apache.pinot.thirdeye.dashboard.resources.v2.ConfigResource;
import org.apache.pinot.thirdeye.dashboard.resources.v2.DataResource;
import org.apache.pinot.thirdeye.dashboard.resources.v2.DetectionAlertResource;
import org.apache.pinot.thirdeye.dashboard.resources.v2.alerts.AlertResource;
import org.apache.pinot.thirdeye.dataset.DatasetAutoOnboardResource;
import org.apache.pinot.thirdeye.detection.DetectionResource;
import org.apache.pinot.thirdeye.detection.yaml.YamlResource;

@Path("/")
public class RootResource {

  private final AlertResource alertResource;
  private final AdminResource adminResource;
  private final AnomaliesResource anomaliesResource;
  private final ApplicationResource applicationResource;
  private final AutoOnboardResource autoOnboardResource;
  private final CacheResource cacheResource;
  private final ConfigResource configResource;
  private final CustomizedEventResource customizedEventResource;
  private final DashboardResource dashboardResource;
  private final DataResource dataResource;
  private final DatasetAutoOnboardResource datasetAutoOnboardResource;
  private final DetectionResource detectionResource;
  private final DetectionAlertResource detectionAlertResource;
  private final ThirdEyeResource thirdEyeResource;
  private final UserDashboardResource userDashboardResource;
  private final YamlResource yamlResource;

  @Inject
  public RootResource(
      final AlertResource alertResource,
      final AdminResource adminResource,
      final AnomaliesResource anomaliesResource,
      final ApplicationResource applicationResource,
      final AutoOnboardResource autoOnboardResource,
      final CacheResource cacheResource,
      final ConfigResource configResource,
      final CustomizedEventResource customizedEventResource,
      final DashboardResource dashboardResource,
      final DataResource dataResource,
      final DatasetAutoOnboardResource datasetAutoOnboardResource,
      final DetectionResource detectionResource,
      final DetectionAlertResource detectionAlertResource,
      final ThirdEyeResource thirdEyeResource,
      final UserDashboardResource userDashboardResource,
      final YamlResource yamlResource) {
    this.alertResource = alertResource;
    this.adminResource = adminResource;
    this.anomaliesResource = anomaliesResource;
    this.applicationResource = applicationResource;
    this.autoOnboardResource = autoOnboardResource;
    this.cacheResource = cacheResource;
    this.configResource = configResource;
    this.customizedEventResource = customizedEventResource;
    this.dashboardResource = dashboardResource;
    this.dataResource = dataResource;
    this.datasetAutoOnboardResource = datasetAutoOnboardResource;
    this.detectionResource = detectionResource;
    this.detectionAlertResource = detectionAlertResource;
    this.thirdEyeResource = thirdEyeResource;
    this.userDashboardResource = userDashboardResource;
    this.yamlResource = yamlResource;
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

  @Path("events")
  public CustomizedEventResource getCustomizedEventResource() {
    return customizedEventResource;
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

  @Path("dataset-auto-onboard")
  public DatasetAutoOnboardResource getDatasetAutoOnboardResource() {
    return datasetAutoOnboardResource;
  }

  @Path("detection")
  public DetectionResource getDetectionResource() {
    return detectionResource;
  }

  @Path("groups")
  public DetectionAlertResource getDetectionAlertResource() {
    return detectionAlertResource;
  }

  @Path("thirdeye-admin")
  public AdminResource getAdminResource() {
    return adminResource;
  }

  @Path("thirdeye")
  public ThirdEyeResource getThirdEyeResource() {
    return thirdEyeResource;
  }

  @Path("userdashboard")
  public UserDashboardResource getUserDashboardResource() {
    return userDashboardResource;
  }

  @Path("yaml")
  public YamlResource getYamlResource() {
    return yamlResource;
  }
}
