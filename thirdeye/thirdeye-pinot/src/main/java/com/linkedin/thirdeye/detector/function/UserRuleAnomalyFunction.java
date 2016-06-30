package com.linkedin.thirdeye.detector.function;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.linkedin.thirdeye.api.DimensionKey;
import com.linkedin.thirdeye.api.MetricTimeSeries;
import com.linkedin.thirdeye.detector.api.AnomalyResult;

/**
 * See params for property configuration.
 * @param baseline baseline comparison period. Value should be one of 'w/w', 'w/2w',
 *          'w/3w'. The anomaly function spec should also be configured to provide a sufficient
 *          window size + additional time buckets for comparison. For example, monitoring one hour
 *          of w/w data would require a window size of 168 (1 week) + an additional 1 (for the
 *          monitoring window), for a total of 169 hours window size.
 * @param changeThreshold detection threshold for percent change relative to baseline, defined as
 *          (current - baseline) / baseline. Positive values detect an increase, while negative
 *          values detect a decrease. This value should be a decimal, eg a 50% increase would have a
 *          threshold of 0.50. Any triggered anomalies must strictly exceed this threshold (no
 *          equality).
 * @param averageVolumeThreshold minimum average threshold across the entire input window. If the
 *          average value does not meet or exceed the threshold, no anomaly results will be
 *          generated. This value should be a double.
 */
public class UserRuleAnomalyFunction extends BaseAnomalyFunction {
  private static final Logger LOGGER = LoggerFactory.getLogger(UserRuleAnomalyFunction.class);
  public static final String BASELINE = "baseline";
  public static final String CHANGE_THRESHOLD = "changeThreshold";
  public static final String AVERAGE_VOLUME_THRESHOLD = "averageVolumeThreshold";
  public static final String MIN_CONSECUTIVE_SIZE = "minConsecutiveSize";
  public static final String DEFAULT_MESSAGE_TEMPLATE = "threshold=%s, %s value is %s / %s (%s)";
  private static final Joiner CSV = Joiner.on(",");
  private static final Joiner ANOMALY_RESULT_MESSAGE_JOINER = Joiner.on("...");
  private static final NumberFormat PERCENT_FORMATTER = NumberFormat.getPercentInstance();
  static {
    // limit number of decimal points shown, for readability
    PERCENT_FORMATTER.setMaximumFractionDigits(2);
  }

  private AnomalyResult getMergedAnomalyResults(List<AnomalyResult> anomalyResults) {
    int n = anomalyResults.size();
    AnomalyResult firstAnomalyResult = anomalyResults.get(0);
    AnomalyResult lastAnomalyResult = anomalyResults.get(n - 1);
    AnomalyResult mergedAnomalyResult = new AnomalyResult();
    mergedAnomalyResult.setCollection(firstAnomalyResult.getCollection());
    mergedAnomalyResult.setMetric(firstAnomalyResult.getMetric());
    mergedAnomalyResult.setDimensions(firstAnomalyResult.getDimensions());
    mergedAnomalyResult.setFunctionId(firstAnomalyResult.getFunctionId());
    mergedAnomalyResult.setProperties(firstAnomalyResult.getProperties());
    mergedAnomalyResult.setStartTimeUtc(firstAnomalyResult.getStartTimeUtc());
    mergedAnomalyResult.setEndTimeUtc(lastAnomalyResult.getEndTimeUtc());
    mergedAnomalyResult.setWeight(firstAnomalyResult.getWeight());
    mergedAnomalyResult.setFilters(firstAnomalyResult.getFilters());

    double summedScore = 0;
    for (AnomalyResult anomalyResult : anomalyResults) {
      summedScore += anomalyResult.getScore();
    }
    mergedAnomalyResult.setScore(summedScore / n);

    List<String> messages = new ArrayList<>();
    String firstMessage = anomalyResults.get(0).getMessage();
    String lastMessage = anomalyResults.get(anomalyResults.size() - 1).getMessage();
    messages.add(firstMessage);
    messages.add(lastMessage);

    mergedAnomalyResult.setMessage(ANOMALY_RESULT_MESSAGE_JOINER.join(messages));

    return mergedAnomalyResult;
  }

  @Override
  public List<AnomalyResult> analyze(DimensionKey dimensionKey, MetricTimeSeries timeSeries,
      DateTime windowStart, DateTime windowEnd, List<AnomalyResult> knownAnomalies)
      throws Exception {
    List<AnomalyResult> anomalyResults = new ArrayList<>();

    // Parse function properties
    Properties props = getProperties();

    // Metric
    String metric = getSpec().getMetric();
    // Get thresholds
    double changeThreshold = Double.valueOf(props.getProperty(CHANGE_THRESHOLD));
    double volumeThreshold = 0;
    if (props.containsKey(AVERAGE_VOLUME_THRESHOLD)) {
      volumeThreshold = Double.valueOf(props.getProperty(AVERAGE_VOLUME_THRESHOLD));
    }

    // Compute baseline for comparison
    String baselineProp = props.getProperty(BASELINE);
    long baselineMillis = getBaselineMillis(baselineProp);

    // Compute the bucket size, so we can iterate in those steps
    long bucketMillis =
        TimeUnit.MILLISECONDS.convert(getSpec().getBucketSize(), getSpec().getBucketUnit());

    // Compute the weight of this time series (average across whole)
    double averageValue = 0;
    for (Long time : timeSeries.getTimeWindowSet()) {
      averageValue += timeSeries.get(time, metric).doubleValue();
    }
    long numBuckets = (windowEnd.getMillis() - windowStart.getMillis()) / bucketMillis;
    averageValue /= numBuckets;

    // Check if this time series even meets our volume threshold
    if (averageValue < volumeThreshold) {
      LOGGER.info("{} does not meet volume threshold {}: {}", dimensionKey, volumeThreshold,
          averageValue);
      return anomalyResults; // empty list
    }
    // iterate through baseline keys
    Set<Long> filteredBaselineTimes =
        filterTimeWindowSet(timeSeries.getTimeWindowSet(), windowStart.getMillis(), windowEnd
            .minus(baselineMillis).getMillis());
    Long min = Collections.min(timeSeries.getTimeWindowSet());
    Long max = Collections.max(timeSeries.getTimeWindowSet());
    for (Long baselineKey : filteredBaselineTimes) {
      Long currentKey = baselineKey + baselineMillis;
      double currentValue = timeSeries.get(currentKey, metric).doubleValue();
      double baselineValue = timeSeries.get(baselineKey, metric).doubleValue();
      if (isAnomaly(currentValue, baselineValue, changeThreshold)) {
        AnomalyResult anomalyResult = new AnomalyResult();
        anomalyResult.setCollection(getSpec().getCollection());
        anomalyResult.setMetric(metric);
        anomalyResult.setDimensions(CSV.join(dimensionKey.getDimensionValues()));
        anomalyResult.setFunctionId(getSpec().getId());
        anomalyResult.setProperties(getSpec().getProperties());
        anomalyResult.setStartTimeUtc(currentKey);
        anomalyResult.setEndTimeUtc(currentKey + bucketMillis); // point-in-time
        anomalyResult.setScore(calculatePercentChange(currentValue, baselineValue));
        anomalyResult.setWeight(averageValue);
        String message =
            getAnomalyResultMessage(changeThreshold, baselineProp, currentValue, baselineValue);
        anomalyResult.setMessage(message);
        anomalyResult.setFilters(getSpec().getFilters());
        anomalyResults.add(anomalyResult);

      }
    }

    String minConsecutiveSizeStr = props.getProperty(MIN_CONSECUTIVE_SIZE);
    int minConsecutiveSize = 1;
    if (StringUtils.isNotBlank(minConsecutiveSizeStr)) {
      minConsecutiveSize = Integer.valueOf(minConsecutiveSizeStr);
    }

    return getFilteredAndMergedAnomalyResults(anomalyResults, minConsecutiveSize, bucketMillis);

  }

  /**
   * Merge consecutive anomaly results. If anomaly results are not consecutive they are
   * rejected.
   * @param anomalyResults
   * @param minConsecutiveSize
   * @param bucketMillis
   * @return
   */
  List<AnomalyResult> getFilteredAndMergedAnomalyResults(List<AnomalyResult> anomalyResults,
      int minConsecutiveSize, long bucketMillis) {

    int anomalyResultsSize = anomalyResults.size();
    if (minConsecutiveSize > 1) {
      List<AnomalyResult> anomalyResultsAggregated = new ArrayList<>();

      if (anomalyResultsSize >= minConsecutiveSize) {
        int remainingSize = anomalyResultsSize;

        List<AnomalyResult> currentConsecutiveResults = new ArrayList<>();
        for (AnomalyResult anomalyResult : anomalyResults) {
          if (currentConsecutiveResults.isEmpty()) {
            currentConsecutiveResults.add(anomalyResult);
            remainingSize--;
          } else {
            AnomalyResult lastConsecutiveAnomalyResult =
                currentConsecutiveResults.get(currentConsecutiveResults.size() - 1);
            long lastStartTime = lastConsecutiveAnomalyResult.getStartTimeUtc();
            long currentStarTime = anomalyResult.getStartTimeUtc();

            if ((lastStartTime + bucketMillis) == currentStarTime) {
              currentConsecutiveResults.add(anomalyResult);
              remainingSize--;

              // End of loop. Last element.
              if (remainingSize == 0) {
                anomalyResultsAggregated.add(getMergedAnomalyResults(currentConsecutiveResults));
              }

            } else {

              if (currentConsecutiveResults.size() >= minConsecutiveSize) {
                // Current consecutives have been all added.
                anomalyResultsAggregated.add(getMergedAnomalyResults(currentConsecutiveResults));
              }

              // Condition for start collecting new consecutives.
              if (remainingSize >= minConsecutiveSize) {
                // Reset current consecutive results. Start collecting new consecutives.
                currentConsecutiveResults.clear();
                currentConsecutiveResults.add(anomalyResult);
                remainingSize--;
              } else {
                break;

              }
            }

          }
        }
      }

      return anomalyResultsAggregated;

    } else {
      return anomalyResults;
    }
  }

  private String getAnomalyResultMessage(double threshold, String baselineProp,
      double currentValue, double baselineValue) {
    double change = calculatePercentChange(currentValue, baselineValue);
    NumberFormat percentInstance = NumberFormat.getPercentInstance();
    percentInstance.setMaximumFractionDigits(2);
    String thresholdPercent = percentInstance.format(threshold);
    String changePercent = percentInstance.format(change);
    String message =
        String.format(DEFAULT_MESSAGE_TEMPLATE, thresholdPercent, baselineProp, currentValue,
            baselineValue, changePercent);
    return message;
  }

  /**
   * Returns a new ordered set of all timestamps in the provided set that fit within
   * [windowStart, windowEnd)
   */
  SortedSet<Long> filterTimeWindowSet(Set<Long> timeWindowSet, long windowStart, long windowEnd) {
    TreeSet<Long> treeSet = new TreeSet<Long>(timeWindowSet);
    SortedSet<Long> subSet = treeSet.subSet(windowStart, windowEnd);
    return subSet;
  }

  long getBaselineMillis(String baselineProp) throws IllegalArgumentException {
    // TODO: Expand options here and use regex to extract numeric parameter
    long baselineMillis;
    if ("w/w".equals(baselineProp)) {
      baselineMillis = TimeUnit.MILLISECONDS.convert(7, TimeUnit.DAYS);
    } else if ("w/2w".equals(baselineProp)) {
      baselineMillis = TimeUnit.MILLISECONDS.convert(14, TimeUnit.DAYS);
    } else if ("w/3w".equals(baselineProp)) {
      baselineMillis = TimeUnit.MILLISECONDS.convert(21, TimeUnit.DAYS);
    } else {
      throw new IllegalArgumentException("Unsupported baseline " + baselineProp);
    }
    return baselineMillis;
  }

  boolean isAnomaly(double currentValue, double baselineValue, double changeThreshold) {
    if (baselineValue > 0) {
      double percentChange = calculatePercentChange(currentValue, baselineValue);
      if (changeThreshold > 0 && percentChange > changeThreshold || changeThreshold < 0
          && percentChange < changeThreshold) {
        return true;
      }
    }
    return false;
  }

  private double calculatePercentChange(double currentValue, double baselineValue) {
    return (currentValue - baselineValue) / baselineValue;
  }
}
