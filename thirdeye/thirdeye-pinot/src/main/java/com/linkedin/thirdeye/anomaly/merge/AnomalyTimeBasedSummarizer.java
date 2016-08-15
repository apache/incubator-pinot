package com.linkedin.thirdeye.anomaly.merge;

import com.linkedin.thirdeye.db.entity.AnomalyMergedResult;
import com.linkedin.thirdeye.db.entity.AnomalyResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Given list of {@link AnomalyResult} and merge parameters, this utility performs time based merge
 */
public abstract class AnomalyTimeBasedSummarizer {
  private final static Logger LOG = LoggerFactory.getLogger(AnomalyTimeBasedSummarizer.class);

  private AnomalyTimeBasedSummarizer() {

  }

  /**
   * @param anomalies   : list of raw anomalies to be merged with last mergedAnomaly
   * @param mergeDuration   : length of a merged anomaly
   * @param sequentialAllowedGap : allowed gap between two raw anomalies in order to merge
   *
   * @return
   */
  public static List<AnomalyMergedResult> mergeAnomalies(List<AnomalyResult> anomalies,
      long mergeDuration, long sequentialAllowedGap) {
    return mergeAnomalies(null, anomalies, mergeDuration, sequentialAllowedGap);
  }

  /**
   * @param mergedAnomaly : last merged anomaly
   * @param anomalies     : list of raw anomalies to be merged with last mergedAnomaly
   * @param mergeDuration   : length of a merged anomaly
   * @param sequentialAllowedGap : allowed gap between two raw anomalies in order to merge
   * @return
   */
  public static List<AnomalyMergedResult> mergeAnomalies(AnomalyMergedResult mergedAnomaly,
      List<AnomalyResult> anomalies, long mergeDuration, long sequentialAllowedGap) {

    // sort anomalies in natural order of start time
    Collections
        .sort(anomalies, (o1, o2) -> (int) ((o1.getStartTimeUtc() - o2.getStartTimeUtc()) / 1000));

    boolean applySequentialGapBasedSplit = false;
    boolean applyMaxDurationBasedSplit = false;

    if (mergeDuration > 0) {
      applyMaxDurationBasedSplit = true;
    }

    if (sequentialAllowedGap > 0) {
      applySequentialGapBasedSplit = true;
    }

    List<AnomalyMergedResult> mergedAnomalies = new ArrayList<>();

    for (int i = 0; i < anomalies.size(); i++) {
      AnomalyResult currentResult = anomalies.get(i);
      if (mergedAnomaly == null) {
        mergedAnomaly = new AnomalyMergedResult();
        populateMergedResult(mergedAnomaly, currentResult);
      } else {
        // compare current with merged and decide whether to merge the current result or create a new one
        if (applySequentialGapBasedSplit
            && (currentResult.getStartTimeUtc() - mergedAnomaly.getEndTime()) > sequentialAllowedGap) {

          // Split here
          // add previous merged result
          mergedAnomalies.add(mergedAnomaly);

          //set current raw result
          mergedAnomaly = new AnomalyMergedResult();
          populateMergedResult(mergedAnomaly, currentResult);
        } else {
          // add the current raw result into mergedResult
          if (currentResult.getStartTimeUtc() < mergedAnomaly.getStartTime()) {
            mergedAnomaly.setStartTime(currentResult.getStartTimeUtc());
          }
          if (currentResult.getEndTimeUtc() > mergedAnomaly.getEndTime()) {
            mergedAnomaly.setEndTime(currentResult.getEndTimeUtc());
          }
          if (!mergedAnomaly.getAnomalyResults().contains(currentResult)) {
            mergedAnomaly.getAnomalyResults().add(currentResult);
            currentResult.setMerged(true);
          }
        }
      }

      // till this point merged result contains current raw result
      if (applyMaxDurationBasedSplit
          // check if Max Duration for merged has passed, if so, create new one
          && mergedAnomaly.getEndTime() - mergedAnomaly.getStartTime() >= mergeDuration) {
        // check if next anomaly has same start time as current one, that should be merged with current one too
        if (i < (anomalies.size() - 1) && anomalies.get(i + 1).getStartTimeUtc()
            .equals(currentResult.getStartTimeUtc())) {
          // no need to split as we want to include the next raw anomaly into the current one
        } else {
          // Split here
          mergedAnomalies.add(mergedAnomaly);
          mergedAnomaly = null;
        }
      }

      if (i == (anomalies.size() - 1) && mergedAnomaly != null) {
        mergedAnomalies.add(mergedAnomaly);
      }
    }
    LOG.info("merging [{}] raw anomalies", anomalies.size());
    return mergedAnomalies;
  }


  private static void populateMergedResult(AnomalyMergedResult mergedAnomaly,
      AnomalyResult currentResult) {
    if (!mergedAnomaly.getAnomalyResults().contains(currentResult)) {
      mergedAnomaly.getAnomalyResults().add(currentResult);
      currentResult.setMerged(true);
    }
    // only set collection, keep metric, dimensions and function null
    mergedAnomaly.setCollection(currentResult.getCollection());
    mergedAnomaly.setMetric(currentResult.getMetric());
    mergedAnomaly.setStartTime(currentResult.getStartTimeUtc());
    mergedAnomaly.setEndTime(currentResult.getEndTimeUtc());
    mergedAnomaly.setCreatedTime(currentResult.getCreationTimeUtc());
  }
}
