package com.linkedin.thirdeye.anomaly.detection;

import com.google.common.collect.ArrayListMultimap;
import com.linkedin.pinot.pql.parsers.utils.Pair;
import com.linkedin.thirdeye.anomaly.merge.AnomalyMergeExecutor;
import com.linkedin.thirdeye.anomaly.utils.AnomalyUtils;
import com.linkedin.thirdeye.api.DimensionMap;
import com.linkedin.thirdeye.datalayer.bao.MergedAnomalyResultManager;
import com.linkedin.thirdeye.datalayer.dto.MergedAnomalyResultDTO;
import com.linkedin.thirdeye.detector.function.BaseAnomalyFunction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.CollectionUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linkedin.thirdeye.anomaly.task.TaskContext;
import com.linkedin.thirdeye.anomaly.task.TaskInfo;
import com.linkedin.thirdeye.anomaly.task.TaskResult;
import com.linkedin.thirdeye.anomaly.task.TaskRunner;
import com.linkedin.thirdeye.api.DimensionKey;
import com.linkedin.thirdeye.api.MetricTimeSeries;
import com.linkedin.thirdeye.datalayer.bao.DatasetConfigManager;
import com.linkedin.thirdeye.datalayer.bao.RawAnomalyResultManager;
import com.linkedin.thirdeye.datalayer.dto.AnomalyFunctionDTO;
import com.linkedin.thirdeye.datalayer.dto.RawAnomalyResultDTO;
import com.linkedin.thirdeye.detector.function.AnomalyFunctionFactory;

public class DetectionTaskRunner implements TaskRunner {

  private static final Logger LOG = LoggerFactory.getLogger(DetectionTaskRunner.class);
  public static final String BACKFILL_PREFIX = "adhoc_";

  private MergedAnomalyResultManager mergedResultDAO;
  private RawAnomalyResultManager rawAnomalyDAO;

  private List<String> collectionDimensions;
  private DateTime windowStart;
  private DateTime windowEnd;
  private List<MergedAnomalyResultDTO> knownMergedAnomalies;
  private List<RawAnomalyResultDTO> existingRawAnomalies;
  private BaseAnomalyFunction anomalyFunction;
  private DatasetConfigManager datasetConfigDAO;

  public DetectionTaskRunner() {
  }

  public List<TaskResult> execute(TaskInfo taskInfo, TaskContext taskContext) throws Exception {
    DetectionTaskInfo detectionTaskInfo = (DetectionTaskInfo) taskInfo;
    List<TaskResult> taskResult = new ArrayList<>();
    LOG.info("Begin executing task {}", taskInfo);
    mergedResultDAO = taskContext.getMergedResultDAO();
    rawAnomalyDAO = taskContext.getResultDAO();
    datasetConfigDAO = taskContext.getDatasetConfigDAO();
    AnomalyFunctionFactory anomalyFunctionFactory = taskContext.getAnomalyFunctionFactory();
    AnomalyFunctionDTO anomalyFunctionSpec = detectionTaskInfo.getAnomalyFunctionSpec();
    anomalyFunction = anomalyFunctionFactory.fromSpec(anomalyFunctionSpec);
    windowStart = detectionTaskInfo.getWindowStartTime();
    windowEnd = detectionTaskInfo.getWindowEndTime();

    LOG.info(
        "Running anomaly detection job with metricFunction: [{}], metric [{}], collection: [{}]",
        anomalyFunctionSpec.getFunctionName(), anomalyFunctionSpec.getMetric(),
        anomalyFunctionSpec.getCollection());

    collectionDimensions = datasetConfigDAO.findByDataset(anomalyFunctionSpec.getCollection()).getDimensions();

    // Get existing anomalies for this time range and this function id for all combinations of dimensions
    if (anomalyFunction.useHistoryAnomaly()) {
      // if this anomaly function uses history data, then we get all time ranges
      knownMergedAnomalies = getKnownMergedAnomalies(anomalyFunctionSpec.getId(),
          anomalyFunction.getDataRangeIntervals(windowStart.getMillis(), windowEnd.getMillis()));
    } else {
      // otherwise, we only get the merge anomaly for current window in order to remove duplicate raw anomalies
      List<Pair<Long, Long>> currentTimeRange = new ArrayList<>();
      currentTimeRange.add(new Pair<>(windowStart.getMillis(), windowEnd.getMillis()));
      knownMergedAnomalies = getKnownMergedAnomalies(anomalyFunctionSpec.getId(), currentTimeRange);
    }
    // We always find existing raw anomalies to prevent duplicate raw anomalies are generated
    existingRawAnomalies =
        getExistingRawAnomalies(anomalyFunctionSpec.getId(), windowStart.getMillis(), windowEnd.getMillis());

    List<Pair<Long, Long>> startEndTimeRanges =
        anomalyFunction.getDataRangeIntervals(windowStart.getMillis(), windowEnd.getMillis());
    Map<DimensionKey, MetricTimeSeries> dimensionKeyMetricTimeSeriesMap =
        TimeSeriesUtil.getTimeSeriesForAnomalyDetection(anomalyFunctionSpec, startEndTimeRanges);

    exploreDimensionsAndAnalyze(dimensionKeyMetricTimeSeriesMap);

    boolean isBackfill = false;
    // If the current job is a backfill (adhoc) detection job, set notified flag to true so the merged anomalies do not
    // induce alerts and emails.
    String jobName = taskContext.getJobDAO().getJobNameByJobId(detectionTaskInfo.getJobExecutionId());
    if (jobName != null && jobName.toLowerCase().startsWith(BACKFILL_PREFIX)) {
      isBackfill = true;
    }

    // TODO: Create AnomalyMergeExecutor in class level in order to reuse the resource
    // syncAnomalyMergeExecutor is supposed to perform lightweight merges (i.e., anomalies that have the same function
    // id and at the same dimensions) after each detection task. Consequently, a null thread pool is passed the merge
    // executor on purpose in order to prevent an undesired asynchronous merge happens.
    AnomalyMergeExecutor syncAnomalyMergeExecutor = new AnomalyMergeExecutor(null, anomalyFunctionFactory);
    syncAnomalyMergeExecutor.synchronousMergeBasedOnFunctionIdAndDimension(anomalyFunctionSpec, isBackfill);

    return taskResult;
  }

  private void exploreDimensionsAndAnalyze(Map<DimensionKey, MetricTimeSeries> dimensionKeyMetricTimeSeriesMap) {
    int anomalyCounter = 0;

    // Sort the known merged and raw anomalies by their dimension names
    ArrayListMultimap<DimensionMap, MergedAnomalyResultDTO> dimensionNamesToKnownMergedAnomalies = ArrayListMultimap.create();
    for (MergedAnomalyResultDTO knownMergedAnomaly : knownMergedAnomalies) {
      dimensionNamesToKnownMergedAnomalies.put(knownMergedAnomaly.getDimensions(), knownMergedAnomaly);
    }
    ArrayListMultimap<DimensionMap, RawAnomalyResultDTO> dimensionNamesToKnownRawAnomalies = ArrayListMultimap.create();
    for (RawAnomalyResultDTO existingRawAnomaly : existingRawAnomalies) {
      dimensionNamesToKnownRawAnomalies.put(existingRawAnomaly.getDimensions(), existingRawAnomaly);
    }

    for (Map.Entry<DimensionKey, MetricTimeSeries> entry : dimensionKeyMetricTimeSeriesMap.entrySet()) {
      DimensionKey dimensionKey = entry.getKey();
      DimensionMap exploredDimensions = DimensionMap.fromDimensionKey(dimensionKey, collectionDimensions);

      if (entry.getValue().getTimeWindowSet().size() < 1) {
        LOG.warn("Insufficient data for {} to run anomaly detection function", exploredDimensions);
        continue;
      }

      // Get current entry's knownMergedAnomalies, which should have the same explored dimensions
      List<MergedAnomalyResultDTO> knownMergedAnomaliesOfAnEntry = dimensionNamesToKnownMergedAnomalies.get(exploredDimensions);

      try {
        // Run algorithm
        MetricTimeSeries metricTimeSeries = entry.getValue();
        LOG.info("Analyzing anomaly function with explored dimensions: {}, windowStart: {}, windowEnd: {}",
            exploredDimensions, windowStart, windowEnd);

        List<MergedAnomalyResultDTO> historyMergedAnomalies;
        if (anomalyFunction.useHistoryAnomaly()) {
          historyMergedAnomalies = retainHistoryMergedAnomalies(windowStart.getMillis(), knownMergedAnomaliesOfAnEntry);
        } else {
          historyMergedAnomalies = Collections.emptyList();
        }

        LOG.info("Checking if any known anomalies overlap with the monitoring window of anomaly detection, which could result in unwanted holes in current values.");
        AnomalyUtils.logAnomaliesOverlapWithWindow(windowStart, windowEnd, historyMergedAnomalies);

        List<RawAnomalyResultDTO> resultsOfAnEntry = anomalyFunction
            .analyze(exploredDimensions, metricTimeSeries, windowStart, windowEnd, historyMergedAnomalies);

        // Remove detected anomalies that have existed in database
        if (CollectionUtils.isNotEmpty(resultsOfAnEntry)) {
          List<RawAnomalyResultDTO> existingRawAnomaliesOfAnEntry =
              dimensionNamesToKnownRawAnomalies.get(exploredDimensions);
          resultsOfAnEntry = removeFromExistingRawAnomalies(resultsOfAnEntry, existingRawAnomaliesOfAnEntry);
        }
        if (CollectionUtils.isNotEmpty(resultsOfAnEntry)) {
          List<MergedAnomalyResultDTO> existingMergedAnomalies =
              retainExistingMergedAnomalies(windowStart.getMillis(), windowEnd.getMillis(), knownMergedAnomaliesOfAnEntry);
          resultsOfAnEntry = removeFromExistingMergedAnomalies(resultsOfAnEntry, existingMergedAnomalies);
        }

        // Handle results
        handleResults(resultsOfAnEntry);

        LOG.info("Dimension {} has {} anomalies in window {} to {}", exploredDimensions, resultsOfAnEntry.size(),
            windowStart, windowEnd);
        anomalyCounter += resultsOfAnEntry.size();
      } catch (Exception e) {
        LOG.error("Could not compute for {}", exploredDimensions, e);
      }
    }
    LOG.info("{} anomalies found in total", anomalyCounter);
  }

  /**
   * Returns existing raw anomalies in the given monitoring window
   *
   * @param functionId the id of the anomaly function
   * @param monitoringWindowStart inclusive
   * @param monitoringWindowEnd inclusive but it doesn't matter
   *
   * @return known raw anomalies in the given window
   */
  private List<RawAnomalyResultDTO> getExistingRawAnomalies(long functionId, long monitoringWindowStart,
      long monitoringWindowEnd) {
    List<RawAnomalyResultDTO> results = new ArrayList<>();
    try {
      results.addAll(rawAnomalyDAO.findAllByTimeAndFunctionId(monitoringWindowStart, monitoringWindowEnd, functionId));
    } catch (Exception e) {
      LOG.error("Exception in getting existing anomalies", e);
    }
    return results;
  }

  /**
   * Returns all known merged anomalies of the function id that are needed for anomaly detection, i.e., the merged
   * anomalies that overlap with the monitoring window and baseline windows.
   *
   * @param functionId the id of the anomaly function
   * @param startEndTimeRanges the time ranges for retrieving the known merge anomalies

   * @return known merged anomalies of the function id that are needed for anomaly detection
   */
  public List<MergedAnomalyResultDTO> getKnownMergedAnomalies(long functionId, List<Pair<Long, Long>> startEndTimeRanges) {

    List<MergedAnomalyResultDTO> results = new ArrayList<>();
    for (Pair<Long, Long> startEndTimeRange : startEndTimeRanges) {
      try {
        results.addAll(
            mergedResultDAO.findAllConflictByFunctionId(functionId, startEndTimeRange.getFirst(),
                startEndTimeRange.getSecond()));
      } catch (Exception e) {
        LOG.error("Exception in getting merged anomalies", e);
      }
    }

    return results;
  }

  /**
   * Returns history anomalies of the monitoring window from the given known anomalies.
   *
   * Definition of history anomaly: An anomaly that starts before the monitoring window starts.
   *
   * @param monitoringWindowStart the start of the monitoring window
   * @param knownAnomalies the list of known anomalies
   *
   * @return all history anomalies of the monitoring window
   */
  private List<MergedAnomalyResultDTO> retainHistoryMergedAnomalies(long monitoringWindowStart,
      List<MergedAnomalyResultDTO> knownAnomalies) {
    List<MergedAnomalyResultDTO> historyAnomalies = new ArrayList<>();
    for (MergedAnomalyResultDTO knownAnomaly : knownAnomalies) {
      if (knownAnomaly.getStartTime() < monitoringWindowStart) {
        historyAnomalies.add(knownAnomaly);
      }
    }
    return historyAnomalies;
  }

  /**
   * Returns anomalies that overlap with the monitoring window from the given known anomalies
   *
   * Definition of existing anomaly: An anomaly that happens in the monitoring window
   *
   * @param monitoringWindowStart the start of the monitoring window
   * @param monitoringWindowEnd the end of the monitoring window
   * @param knownAnomalies the list of known anomalies
   *
   * @return anomalies that happen in the monitoring window from the given known anomalies
   */
  private List<MergedAnomalyResultDTO> retainExistingMergedAnomalies(long monitoringWindowStart, long monitoringWindowEnd,
      List<MergedAnomalyResultDTO> knownAnomalies) {
    List<MergedAnomalyResultDTO> existingAnomalies = new ArrayList<>();
    for (MergedAnomalyResultDTO knownAnomaly : knownAnomalies) {
      if (knownAnomaly.getStartTime() <= monitoringWindowEnd && knownAnomaly.getEndTime() >= monitoringWindowStart) {
        existingAnomalies.add(knownAnomaly);
      }
    }
    return existingAnomalies;
  }

  /**
   * Given a list of raw anomalies, this method returns a list of raw anomalies that are not contained in any existing
   * merged anomalies.
   *
   * @param rawAnomalies
   * @param existingAnomalies
   * @return
   */
  private List<RawAnomalyResultDTO> removeFromExistingMergedAnomalies(List<RawAnomalyResultDTO> rawAnomalies,
      List<MergedAnomalyResultDTO> existingAnomalies) {
    if (CollectionUtils.isEmpty(rawAnomalies) || CollectionUtils.isEmpty(existingAnomalies)) {
      return rawAnomalies;
    }
    List<RawAnomalyResultDTO> newRawAnomalies = new ArrayList<>();

    for (RawAnomalyResultDTO rawAnomaly : rawAnomalies) {
      boolean isContained = false;
      for (MergedAnomalyResultDTO existingAnomaly : existingAnomalies) {
        if (existingAnomaly.getStartTime().compareTo(rawAnomaly.getStartTime()) <= 0
            && rawAnomaly.getEndTime().compareTo(existingAnomaly.getEndTime()) <= 0) {
          isContained = true;
          break;
        }
      }
      if (!isContained) {
        newRawAnomalies.add(rawAnomaly);
      }
    }

    return newRawAnomalies;
  }

  /**
   * Given a list of raw anomalies, this method returns a list of raw anomalies that are not contained in any existing
   * raw anomalies.
   *
   * @param rawAnomalies
   * @param existingRawAnomalies
   * @return
   */
  private List<RawAnomalyResultDTO> removeFromExistingRawAnomalies(List<RawAnomalyResultDTO> rawAnomalies,
      List<RawAnomalyResultDTO> existingRawAnomalies) {
    List<RawAnomalyResultDTO> newRawAnomalies = new ArrayList<>();

    for (RawAnomalyResultDTO rawAnomaly : rawAnomalies) {
      boolean matched = false;
      for (RawAnomalyResultDTO existingAnomaly : existingRawAnomalies) {
        if (existingAnomaly.getStartTime().compareTo(rawAnomaly.getStartTime()) <= 0
            && rawAnomaly.getEndTime().compareTo(existingAnomaly.getEndTime()) <= 0) {
          matched = true;
          break;
        }
      }
      if (!matched) {
        newRawAnomalies.add(rawAnomaly);
      }
    }

    return newRawAnomalies;
  }

  private void handleResults(List<RawAnomalyResultDTO> results) {
    for (RawAnomalyResultDTO result : results) {
      try {
        // Properties that always come from the function spec
        AnomalyFunctionDTO spec = anomalyFunction.getSpec();
        // make sure score and weight are valid numbers
        result.setScore(normalize(result.getScore()));
        result.setWeight(normalize(result.getWeight()));
        result.setFunction(spec);
        rawAnomalyDAO.save(result);
      } catch (Exception e) {
        LOG.error("Exception in saving anomaly result : " + result.toString(), e);
      }
    }
  }

  /**
   * Handle any infinite or NaN values by replacing them with +/- max value or 0
   */
  private double normalize(double value) {
    if (Double.isInfinite(value)) {
      return (value > 0.0 ? 1 : -1) * Double.MAX_VALUE;
    } else if (Double.isNaN(value)) {
      return 0.0; // default?
    } else {
      return value;
    }
  }
}
