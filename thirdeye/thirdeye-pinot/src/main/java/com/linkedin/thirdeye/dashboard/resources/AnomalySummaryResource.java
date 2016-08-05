package com.linkedin.thirdeye.dashboard.resources;

import com.linkedin.thirdeye.anomaly.merge.AnomalyMergeConfig;
import com.linkedin.thirdeye.anomaly.merge.AnomalySummaryGenerator;
import com.linkedin.thirdeye.api.dto.GroupByKey;
import com.linkedin.thirdeye.api.dto.GroupByRow;
import com.linkedin.thirdeye.api.dto.MergedAnomalyResult;
import com.linkedin.thirdeye.db.dao.AnomalyResultDAO;
import com.linkedin.thirdeye.db.entity.AnomalyResult;
import java.util.List;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;

@Path("thirdeye/anomaly")
@Produces(MediaType.APPLICATION_JSON)
public class AnomalySummaryResource {

  AnomalyResultDAO resultDAO;

  public AnomalySummaryResource(AnomalyResultDAO resultDAO) {
    this.resultDAO = resultDAO;
  }

  @POST
  @Path("summary/function/{functionId}")
  public List<MergedAnomalyResult> getAnomalySummaryForFunction(
      @PathParam("functionId") Long functionId, AnomalyMergeConfig mergeConfig) {
    if (mergeConfig == null) {
      mergeConfig = new AnomalyMergeConfig();
    }
    if (functionId == null) {
      throw new IllegalArgumentException("Function id can't be null");
    }
    DateTime startTimeUtc = new DateTime(mergeConfig.getStartTime());
    DateTime endTimeUtc = new DateTime(mergeConfig.getEndTime());

    List<AnomalyResult> anomalies =
        resultDAO.findAllByTimeAndFunctionId(startTimeUtc, endTimeUtc, functionId);
    return AnomalySummaryGenerator.mergeAnomalies(anomalies, mergeConfig);
  }

  @POST
  @Path("summary/{collection}")
  public List<MergedAnomalyResult> getAnomalySummaryForCollectionMeric(
      @PathParam("collection") String collection, @QueryParam("metric") String metric,
      AnomalyMergeConfig mergeConfig) {
    if (StringUtils.isEmpty(collection)) {
      throw new IllegalArgumentException("Collection can't be empty");
    }
    if (mergeConfig == null) {
      mergeConfig = new AnomalyMergeConfig();
    }
    DateTime startTimeUtc = new DateTime(mergeConfig.getStartTime());
    DateTime endTimeUtc = new DateTime(mergeConfig.getEndTime());

    List<AnomalyResult> anomalies;
    if (!StringUtils.isEmpty(metric)) {
      anomalies =
          resultDAO.findAllByCollectionTimeAndMetric(collection, metric, startTimeUtc, endTimeUtc);
    } else {
      anomalies = resultDAO.findAllByCollectionAndTime(collection, startTimeUtc, endTimeUtc);
    }
    return AnomalySummaryGenerator.mergeAnomalies(anomalies, mergeConfig);
  }

  @POST
  @Path("summary/groupBy")
  public List<GroupByRow<GroupByKey, Long>> getAnomalyResultsByMergeGroup(
      AnomalyMergeConfig mergeConfig) {
    if (mergeConfig == null) {
      mergeConfig = new AnomalyMergeConfig();
    }
    switch (mergeConfig.getMergeStrategy()) {
    case FUNCTION:
      return resultDAO.getCountByFunction(mergeConfig.getStartTime(), mergeConfig.getEndTime());
    case COLLECTION_METRIC:
      return resultDAO.getCountByCollectionMetric(mergeConfig.getStartTime(), mergeConfig.getEndTime());
    case COLLECTION:
      return resultDAO.getCountByCollection(mergeConfig.getStartTime(), mergeConfig.getEndTime());
    default:
      throw new IllegalArgumentException(
          "Unknown merge strategy : " + mergeConfig.getMergeStrategy());
    }
  }
}
