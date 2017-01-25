package com.linkedin.thirdeye.completeness.checker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ListMultimap;
import com.linkedin.thirdeye.api.TimeSpec;
import com.linkedin.thirdeye.client.DAORegistry;
import com.linkedin.thirdeye.completeness.checker.DataCompletenessConstants.DataCompletenessAlgorithmName;
import com.linkedin.thirdeye.dashboard.resources.DataCompletenessResource;
import com.linkedin.thirdeye.datalayer.bao.DataCompletenessConfigManager;
import com.linkedin.thirdeye.datalayer.dto.DataCompletenessConfigDTO;

/**
 * This is the implementation of the WO4W Average function or checking data completeness of datasets
 */
public class Wo4WAvgDataCompletenessAlgorithm implements DataCompletenessAlgorithm {

  public static double DEFAULT_EXPECTED_COMPLETENESS = 80;
  private static double CONSIDER_COMPLETE_AFTER = 95;
  private static final DAORegistry DAO_REGISTRY = DAORegistry.getInstance();
  private static final Logger LOG = LoggerFactory.getLogger(Wo4WAvgDataCompletenessAlgorithm.class);

  private DataCompletenessResource dataCompletenessResource = null;
  private DataCompletenessConfigManager dataCompletenessConfigDAO = null;

  public Wo4WAvgDataCompletenessAlgorithm() {
    dataCompletenessResource = new DataCompletenessResource();
    dataCompletenessConfigDAO = DAO_REGISTRY.getDataCompletenessConfigDAO();
  }


  @Override
  public void computeBaselineCountsIfNotPresent(String dataset, Map<String, Long> bucketNameToBucketValueMS,
      DateTimeFormatter dateTimeFormatter, TimeSpec timeSpec) {

    long weekInMillis = TimeUnit.MILLISECONDS.convert(7, TimeUnit.DAYS);

    // look for the past 4 weeks
    for (int i = 0; i < 4; i ++) {
      long baselineOffset = weekInMillis * (i+1);
      LOG.info("Checking for {} week ago", (i+1));

      // check if baseline is present in database
      Map<String, Long> baselineBucketNameToBucketValueMS = new HashMap<>();
      for (Entry<String, Long> entry : bucketNameToBucketValueMS.entrySet()) {
        Long bucketValueMS = entry.getValue();
        Long baselineBucketValueMS = bucketValueMS - baselineOffset;
        String baselineBucketName = dateTimeFormatter.print(baselineBucketValueMS);
        DataCompletenessConfigDTO configDTO = dataCompletenessConfigDAO.findByDatasetAndDateSDF(dataset, baselineBucketName);
        if (configDTO == null) {
          baselineBucketNameToBucketValueMS.put(baselineBucketName, baselineBucketValueMS);
        }
      }
      // for all baseline values not present in database, fetch their counts, and update in database
      LOG.info("Missing baseline buckets {}", baselineBucketNameToBucketValueMS.keySet());
      if (!baselineBucketNameToBucketValueMS.isEmpty()) {

        ListMultimap<String, Long> baselineBucketNameToTimeValues =
            DataCompletenessTaskUtils.getBucketNameToTimeValuesMap(timeSpec, baselineBucketNameToBucketValueMS);
        Map<String, Long> baselineCountsForBuckets =
            DataCompletenessTaskUtils.getCountsForBucketsOfDataset(dataset, timeSpec, baselineBucketNameToTimeValues);
        LOG.info("Baseline bucket counts {}", baselineCountsForBuckets);

        for (Entry<String, Long> entry : baselineCountsForBuckets.entrySet()) {
          String baselineBucketName = entry.getKey();
          Long baselineBucketCount = entry.getValue();
          Long baselineBucketValueMS = baselineBucketNameToBucketValueMS.get(baselineBucketName);

          DataCompletenessConfigDTO createBaselineConfig = new DataCompletenessConfigDTO();
          createBaselineConfig.setDataset(dataset);
          createBaselineConfig.setDateToCheckInSDF(baselineBucketName);
          createBaselineConfig.setDateToCheckInMS(baselineBucketValueMS);
          createBaselineConfig.setCountStar(baselineBucketCount);
          dataCompletenessConfigDAO.save(createBaselineConfig);
        }
        LOG.info("Saved {} number of baseline counts in database", baselineCountsForBuckets.size());
      }
    }
  }

  @Override
  public List<Long> getBaselineCounts(String dataset, Long bucketValue) {
    long weekInMillis = TimeUnit.MILLISECONDS.convert(7, TimeUnit.DAYS);
    long baselineInMS = bucketValue;
    List<Long> baselineCounts = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      long count = 0;
      baselineInMS = baselineInMS - weekInMillis;
      DataCompletenessConfigDTO config = dataCompletenessConfigDAO.findByDatasetAndDateMS(dataset, baselineInMS);
      if (config != null) {
        count = config.getCountStar();
      }
      baselineCounts.add(count);
    }
    return baselineCounts;
  }

  @Override
  public double getPercentCompleteness(List<Long> baselineCounts, Long currentCount) {
    PercentCompletenessFunctionInput input = new PercentCompletenessFunctionInput();
    input.setAlgorithm(DataCompletenessAlgorithmName.WO4W_AVERAGE);
    input.setBaselineCounts(baselineCounts);
    input.setCurrentCount(currentCount);
    String jsonString = PercentCompletenessFunctionInput.toJson(input);
    double percentCompleteness = dataCompletenessResource.getPercentCompleteness(jsonString);
    return percentCompleteness;
  }

  @Override
  public boolean isDataComplete(Double percentComplete, Double expectedCompleteness) {
    boolean isDataComplete = false;
    if (expectedCompleteness == null) {
      expectedCompleteness = DEFAULT_EXPECTED_COMPLETENESS;
    }
    if (percentComplete >= expectedCompleteness) {
      isDataComplete = true;
    }
    return isDataComplete;
  }

  @Override
  public double getConsiderCompleteAfter() {
    return CONSIDER_COMPLETE_AFTER;
  }

}
