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
 */

package org.apache.pinot.thirdeye.api.user.dashboard;

import com.google.common.base.Preconditions;
import com.google.common.collect.Collections2;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import org.apache.commons.lang.StringUtils;
import org.apache.pinot.thirdeye.api.Constants;
import org.apache.pinot.thirdeye.constant.AnomalyFeedbackType;
import org.apache.pinot.thirdeye.constant.AnomalyResultSource;
import org.apache.pinot.thirdeye.dashboard.resources.v2.ResourceUtils;
import org.apache.pinot.thirdeye.dashboard.resources.v2.pojo.AnomalySummary;
import org.apache.pinot.thirdeye.datalayer.bao.AlertConfigManager;
import org.apache.pinot.thirdeye.datalayer.bao.AnomalyFunctionManager;
import org.apache.pinot.thirdeye.datalayer.bao.DatasetConfigManager;
import org.apache.pinot.thirdeye.datalayer.bao.DetectionAlertConfigManager;
import org.apache.pinot.thirdeye.datalayer.bao.DetectionConfigManager;
import org.apache.pinot.thirdeye.datalayer.bao.MergedAnomalyResultManager;
import org.apache.pinot.thirdeye.datalayer.bao.MetricConfigManager;
import org.apache.pinot.thirdeye.datalayer.dto.AlertConfigDTO;
import org.apache.pinot.thirdeye.datalayer.dto.AnomalyFunctionDTO;
import org.apache.pinot.thirdeye.datalayer.dto.DetectionAlertConfigDTO;
import org.apache.pinot.thirdeye.datalayer.dto.DetectionConfigDTO;
import org.apache.pinot.thirdeye.datalayer.dto.MergedAnomalyResultDTO;
import org.apache.pinot.thirdeye.datalayer.util.Predicate;
import org.apache.pinot.thirdeye.datasource.ThirdEyeCacheRegistry;
import org.apache.pinot.thirdeye.datasource.loader.AggregationLoader;
import org.apache.pinot.thirdeye.datasource.loader.DefaultAggregationLoader;
import org.apache.pinot.thirdeye.detection.ConfigUtils;
import org.apache.pinot.thirdeye.detection.CurrentAndBaselineLoader;
import org.apache.pinot.thirdeye.rootcause.impl.MetricEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.pinot.thirdeye.detection.yaml.YamlDetectionAlertConfigTranslator.*;


/**
 * Endpoints for user-customized dashboards (currently alerts only)
 */
@Api(tags = {Constants.DASHBOARD_TAG})
@Path(value = "/userdashboard")
@Produces(MediaType.APPLICATION_JSON)
public class UserDashboardResource {
  protected static final Logger LOG = LoggerFactory.getLogger(UserDashboardResource.class);

  private static final int ANOMALIES_LIMIT_DEFAULT = 500;

  private final MergedAnomalyResultManager anomalyDAO;
  private final AnomalyFunctionManager functionDAO;
  private final AlertConfigManager alertDAO;
  private final MetricConfigManager metricDAO;
  private final DatasetConfigManager datasetDAO;
  private final DetectionConfigManager detectionDAO;
  private final DetectionAlertConfigManager detectionAlertDAO;
  private final AggregationLoader aggregationLoader;
  private final CurrentAndBaselineLoader currentAndBaselineLoader;


  public UserDashboardResource(MergedAnomalyResultManager anomalyDAO, AnomalyFunctionManager functionDAO,
      MetricConfigManager metricDAO, DatasetConfigManager datasetDAO, AlertConfigManager alertDAO,
      DetectionConfigManager detectionDAO, DetectionAlertConfigManager detectionAlertDAO) {
    this.anomalyDAO = anomalyDAO;
    this.functionDAO = functionDAO;
    this.metricDAO = metricDAO;
    this.datasetDAO = datasetDAO;
    this.alertDAO = alertDAO;
    this.detectionDAO = detectionDAO;
    this.detectionAlertDAO = detectionAlertDAO;

    this.aggregationLoader =
        new DefaultAggregationLoader(this.metricDAO, this.datasetDAO, ThirdEyeCacheRegistry.getInstance().getQueryCache(),
            ThirdEyeCacheRegistry.getInstance().getDatasetMaxDataTimeCache());
    this.currentAndBaselineLoader = new CurrentAndBaselineLoader(this.metricDAO, this.datasetDAO, this.aggregationLoader);
  }

  /**
   * Returns a list of AnomalySummary for a set of query parameters. Anomalies are
   * sorted by start time (descending).
   *
   * <br/><b>Example:</b>
   * <pre>
   *   [ {
   *     "id" : 12345,
   *     "start" : 1517000000000,
   *     "end" : 1517100000000,
   *     "dimensions" : { "country": "us" },
   *     "severity" : 0.517,
   *     "current" : 1213.0,
   *     "baseline" : 550.0,
   *     "feedback" : "NO_FEEDBACK",
   *     "comment": "",
   *     "metricId" : 12346
   *     "metric" : "page_views",
   *     "metricUrn" : "thirdeye:metric:12345:country%3Dus"
   *     "functionId" : 12347
   *     "functionName" : "page_views_monitoring"
   *     },
   *     ...
   *   ]
   * </pre>
   *
   * @see AnomalySummary
   *
   * @param start window start time
   * @param end window end time (optional)
   * @param owner anomaly function owner only (optional)
   * @param application anomaly function for application alert groups only (optional)
   *
   * @return List of AnomalySummary
   */
  @GET
  @Path("/anomalies")
  @ApiOperation(value = "Query anomalies")
  public List<AnomalySummary> queryAnomalies(
      @ApiParam(value = "start time of anomaly retrieval window")
      @QueryParam("start") Long start,
      @ApiParam(value = "end time of anomaly retrieval window")
      @QueryParam("end") Long end,
      @ApiParam(value = "alert owner")
      @QueryParam("owner") String owner,
      @ApiParam(value = "alert application/product/team")
      @QueryParam("application") String application,
      @ApiParam(value = "subscription group")
      @QueryParam("group") String group,
      @ApiParam(value = "The name of the metric to fetch anomalies from")
      @QueryParam("metric") String metric,
      @ApiParam(value = "The name of the pinot table to which this metric belongs")
      @QueryParam("dataset") String dataset,
      @ApiParam(value = "Specify if you want to only fetch true anomalies")
      @QueryParam("fetchTrueAnomaly") @DefaultValue("false") boolean fetchTrueAnomaly,
      @ApiParam(value = "max number of results")
      @QueryParam("limit") Integer limit) throws Exception {
    LOG.info("[USER DASHBOARD] Fetching anomalies with filters. Start: " + start + " end: " + end + " metric: "
        + metric + " dataset: " + dataset + " owner: " + owner + " application: " + application + " group: " + group
        + " fetchTrueAnomaly: " + fetchTrueAnomaly + " limit: " + limit);

    // Safety conditions
    if (limit == null) {
      LOG.warn("No upper limit specified while fetching anomalies. Defaulting to " + ANOMALIES_LIMIT_DEFAULT);
      limit = ANOMALIES_LIMIT_DEFAULT;
    }
    Preconditions.checkNotNull(start, "Please specify the start time of the anomaly retrieval window");

    // TODO support index select on user-reported anomalies
//    predicates.add(Predicate.OR(
//        Predicate.EQ("notified", true),
//        Predicate.EQ("anomalyResultSource", AnomalyResultSource.USER_LABELED_ANOMALY)));

    // TODO: Prefer to have intersection of anomalies rather than union
    List<MergedAnomalyResultDTO> anomalies = new ArrayList<>();
    anomalies.addAll(fetchLegacyAnomaliesByFunctionId(start, end, group, application, owner));
    // Fetch anomalies by group
    anomalies.addAll(fetchAnomaliesBySubsGroup(start, end, group));
    // Fetch anomalies by application
    anomalies.addAll(fetchAnomaliesByApplication(start, end, application));
    // Fetch anomalies by metric and/or dataset
    anomalies.addAll(fetchAnomaliesByMetricDataset(start, end, metric, dataset));

    // sort descending by start time
    Collections.sort(anomalies, new Comparator<MergedAnomalyResultDTO>() {
      @Override
      public int compare(MergedAnomalyResultDTO o1, MergedAnomalyResultDTO o2) {
        return -1 * Long.compare(o1.getStartTime(), o2.getStartTime());
      }
    });

    if (fetchTrueAnomaly) {
      // Filter and retain only true anomalies
      List<MergedAnomalyResultDTO> trueAnomalies = new ArrayList<>();
      for (MergedAnomalyResultDTO anomaly : anomalies) {
        if (anomaly.getFeedback() != null && anomaly.getFeedback().getFeedbackType().isAnomaly()) {
          trueAnomalies.add(anomaly);
        }
      }
      anomalies = trueAnomalies;
    }
    // filter child anomalies
    anomalies = anomalies.stream().filter(anomaly -> !anomaly.isChild()).collect(Collectors.toList());
    // limit result size
    anomalies = anomalies.subList(0, Math.min(anomalies.size(), limit));

    List<AnomalySummary> output = getAnomalyFormattedOutput(anomalies);

    LOG.info("Successfully returned " + output.size() + " anomalies.");
    return output;
  }

  private List<AnomalySummary> getAnomalyFormattedOutput(List<MergedAnomalyResultDTO> anomalies) {
    List<AnomalySummary> output = new ArrayList<>();

    // fetch functions & build function id to function object mapping
    Set<Long> anomalyFunctionIds = new HashSet<>();
    for (MergedAnomalyResultDTO anomaly : anomalies) {
      if (anomaly.getFunctionId() != null) {
        anomalyFunctionIds.add(anomaly.getFunctionId());
      }
    }
    List<AnomalyFunctionDTO> functions = this.functionDAO.findByPredicate(Predicate.IN("baseId", anomalyFunctionIds.toArray()));
    Map<Long, AnomalyFunctionDTO> id2function = new HashMap<>();
    for (AnomalyFunctionDTO function : functions) {
      id2function.put(function.getId(), function);
    }

    for (MergedAnomalyResultDTO anomaly : anomalies) {
      long metricId = this.getMetricId(anomaly);

      AnomalySummary summary = new AnomalySummary();
      summary.setId(anomaly.getId());
      summary.setStart(anomaly.getStartTime());
      summary.setEnd(anomaly.getEndTime());
      summary.setCurrent(anomaly.getAvgCurrentVal());
      summary.setBaseline(anomaly.getAvgBaselineVal());

      summary.setFunctionId(-1);
      if (anomaly.getFunctionId() != null) {
        summary.setFunctionId(anomaly.getFunctionId());
        if (id2function.get(anomaly.getFunctionId()) != null) {
          summary.setFunctionName(id2function.get(anomaly.getFunctionId()).getFunctionName());
        }
      }

      summary.setDetectionConfigId(-1);
      if (anomaly.getDetectionConfigId() != null) {
        long detectionConfigId = anomaly.getDetectionConfigId();
        DetectionConfigDTO detectionDTO = this.detectionDAO.findById(detectionConfigId);
        summary.setFunctionName(detectionDTO.getName());
        summary.setDetectionConfigId(detectionConfigId);
      }

      summary.setMetricName(anomaly.getMetric());
      summary.setDimensions(anomaly.getDimensions());
      summary.setDataset(anomaly.getCollection());
      summary.setMetricId(metricId);

      if (metricId > 0) {
        summary.setMetricUrn(this.getMetricUrn(anomaly));
      }

      // TODO use alert filter if necessary
      summary.setSeverity(Math.abs(anomaly.getWeight()));


      summary.setFeedback(AnomalyFeedbackType.NO_FEEDBACK);
      summary.setComment("");
      if (anomaly.getFeedback() != null) {
        summary.setFeedback(anomaly.getFeedback().getFeedbackType());
        summary.setComment(anomaly.getFeedback().getComment());
      }

      summary.setClassification(ResourceUtils.getStatusClassification(anomaly));
      summary.setSource(anomaly.getAnomalyResultSource());

      output.add(summary);
    }

    return output;
  }

  @Deprecated
  private Collection<MergedAnomalyResultDTO> fetchLegacyAnomaliesByFunctionId(Long start, Long end, String group, String application, String owner) {
    // Find functionIds which belong to application, subscription group and owner.
    List<Predicate> predicates = new ArrayList<>();
    Set<Long> functionIds = new HashSet<>();

    // application (indirect)
    if (StringUtils.isNotBlank(application)) {
      List<AnomalyFunctionDTO> functions = this.functionDAO.findAllByApplication(application);
      for (AnomalyFunctionDTO function : functions) {
        if (function.getIsActive()) {
          functionIds.add(function.getId());
        }
      }
    }
    // Support for partially migrated alerts.
    List<DetectionAlertConfigDTO> notifications = detectionAlertDAO.findByPredicate(Predicate.EQ("application", application));
    for (DetectionAlertConfigDTO notification : notifications) {
      for (long id : ConfigUtils.getLongs(notification.getProperties().get(PROP_DETECTION_CONFIG_IDS))) {
        AnomalyFunctionDTO function = this.functionDAO.findById(id);
        if (function != null && function.getIsActive()) {
          functionIds.add(id);
        }
      }
    }

    // group (indirect)
    Set<Long> groupFunctionIds = new HashSet<>();
    if (StringUtils.isNotBlank(group)) {
      AlertConfigDTO alert = this.alertDAO.findWhereNameEquals(group);
      if (alert != null) {
        for (long id : alert.getEmailConfig().getFunctionIds()) {
          AnomalyFunctionDTO function = this.functionDAO.findById(id);
          if (function != null && function.getIsActive()) {
            groupFunctionIds.add(id);
          }
        }
      }
    }
    if (!groupFunctionIds.isEmpty()) {
      if (functionIds.isEmpty()) {
        functionIds = groupFunctionIds;
      } else {
        functionIds.retainAll(groupFunctionIds);
      }
    }

    // owner (indirect)
    Set<Long> ownerFunctionIds = new HashSet<>();
    if (StringUtils.isNotBlank(owner)) {
      // TODO: replace database scan with targeted select
      List<AnomalyFunctionDTO> functions = this.functionDAO.findAll();
      for (AnomalyFunctionDTO function : functions) {
        if (function.getIsActive() && Objects.equals(function.getCreatedBy(), owner)) {
          ownerFunctionIds.add(function.getId());
        }
      }
    }
    if (!ownerFunctionIds.isEmpty()) {
      if (functionIds.isEmpty()) {
        functionIds = ownerFunctionIds;
      } else {
        functionIds.retainAll(ownerFunctionIds);
      }
    }

    // Predicate on start time end time and function Id.
    predicates.add(Predicate.IN("functionId", functionIds.toArray()));
    predicates.add(Predicate.GE("endTime", start));
    if (end != null) {
      predicates.add(Predicate.LT("startTime", end));
    }

    // Fetch legacy anomalies via predicates
    List<MergedAnomalyResultDTO> anomalies = this.anomalyDAO.findByPredicate(Predicate.AND(predicates.toArray(new Predicate[predicates.size()])));
    // filter (un-notified && non-user-reported) anomalies
    // TODO remove once index select on user-reported anomalies available
    Iterator<MergedAnomalyResultDTO> itAnomaly = anomalies.iterator();
    while (itAnomaly.hasNext()) {
      MergedAnomalyResultDTO anomaly = itAnomaly.next();
      if (!anomaly.isNotified() &&
          !AnomalyResultSource.USER_LABELED_ANOMALY.equals(anomaly.getAnomalyResultSource())) {
        itAnomaly.remove();
      }
    }

    return anomalies;
  }

  private Collection<MergedAnomalyResultDTO> fetchAnomaliesByMetricDataset(Long start, Long end, String metric, String dataset) {
    if (StringUtils.isBlank(metric) && StringUtils.isBlank(dataset)) {
      return Collections.emptyList();
    }

    List<Predicate> predicates = new ArrayList<>();
    predicates.add(Predicate.GE("endTime", start));
    if (end != null) {
      predicates.add(Predicate.LT("startTime", end));
    }

    // Filter by metric and dataset
    if (metric != null) {
      predicates.add(Predicate.EQ("metric", metric));
    }
    if (dataset != null) {
      predicates.add(Predicate.EQ("collection", dataset));
    }

    return this.anomalyDAO.findByPredicate(Predicate.AND(predicates.toArray(new Predicate[predicates.size()])));
  }

  private Collection<MergedAnomalyResultDTO> fetchAnomaliesByApplication(Long start, Long end, String application) throws Exception {
    if (StringUtils.isBlank(application)) {
      return Collections.emptyList();
    }

    List<DetectionAlertConfigDTO> alerts =
        this.detectionAlertDAO.findByPredicate(Predicate.EQ("application", application));

    Set<Long> detectionConfigIds = new HashSet<>();
    for (DetectionAlertConfigDTO alertConfigDTO : alerts) {
      detectionConfigIds.addAll(alertConfigDTO.getVectorClocks().keySet());
    }

    return fetchAnomaliesByConfigIds(start, end, detectionConfigIds);
  }

  private Collection<MergedAnomalyResultDTO> fetchAnomaliesBySubsGroup(Long start, Long end, String group) throws Exception {
    if (StringUtils.isBlank(group)) {
      return Collections.emptyList();
    }

    List<DetectionAlertConfigDTO> alerts =
        this.detectionAlertDAO.findByPredicate(Predicate.EQ("name", group));

    Set<Long> detectionConfigIds = new HashSet<>();
    for (DetectionAlertConfigDTO alertConfigDTO : alerts) {
      detectionConfigIds.addAll(alertConfigDTO.getVectorClocks().keySet());
    }

    return fetchAnomaliesByConfigIds(start, end, detectionConfigIds);
  }

  private Collection<MergedAnomalyResultDTO> fetchAnomaliesByConfigIds(Long start, Long end, Set<Long> detectionConfigIds) throws Exception {
    if (detectionConfigIds.isEmpty()) {
      return Collections.emptyList();
    }

    List<Predicate> predicates = new ArrayList<>();

    // anomaly window start
    if (start != null) {
      predicates.add(Predicate.GT("endTime", start));
    }

    // anomaly window end
    if (end != null) {
      predicates.add(Predicate.LT("startTime", end));
    }

    predicates.add(Predicate.IN("detectionConfigId", detectionConfigIds.toArray()));

    Collection<MergedAnomalyResultDTO> anomalies = this.anomalyDAO.findByPredicate(Predicate.AND(predicates.toArray(new Predicate[predicates.size()])));

    anomalies = Collections2.filter(anomalies, new com.google.common.base.Predicate<MergedAnomalyResultDTO>() {
      @Override
      public boolean apply(@Nullable MergedAnomalyResultDTO mergedAnomalyResultDTO) {
        return !mergedAnomalyResultDTO.isChild();
      }
    });

    this.currentAndBaselineLoader.fillInCurrentAndBaselineValue(anomalies);

    return anomalies;
  }

  /**
   * Helper to work around for anomaly function not setting metric id
   *
   * @param anomaly anomaly dto
   * @return metric id, or {@code -1} if the metric/dataset cannot be resolved
   */
  private long getMetricId(MergedAnomalyResultDTO anomaly) {
    if (anomaly.getFunction() != null && anomaly.getFunction().getMetricId() > 0) {
      return anomaly.getFunction().getMetricId();
    }
    try {
      return this.metricDAO.findByMetricAndDataset(anomaly.getMetric(), anomaly.getCollection()).getId();
    } catch (Exception e) {
      return -1;
    }
  }

  /**
   * Returns an URN matching the anomalies associated metric (and dimensions)
   *
   * @param anomaly anomaly dto
   * @return metric urn
   */
  private String getMetricUrn(MergedAnomalyResultDTO anomaly) {
    return MetricEntity.fromMetric(1.0, this.getMetricId(anomaly), ResourceUtils.getAnomalyFilters(anomaly, this.datasetDAO)).getUrn();
  }
}
