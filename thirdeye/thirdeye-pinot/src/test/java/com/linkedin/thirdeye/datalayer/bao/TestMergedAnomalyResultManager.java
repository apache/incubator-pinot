package com.linkedin.thirdeye.datalayer.bao;

import java.util.ArrayList;
import java.util.List;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.linkedin.thirdeye.anomaly.merge.AnomalyMergeConfig;
import com.linkedin.thirdeye.anomaly.merge.AnomalyTimeBasedSummarizer;
import com.linkedin.thirdeye.constant.AnomalyFeedbackType;
import com.linkedin.thirdeye.constant.FeedbackStatus;
import com.linkedin.thirdeye.datalayer.dto.AnomalyFeedbackDTO;
import com.linkedin.thirdeye.datalayer.dto.AnomalyFunctionDTO;
import com.linkedin.thirdeye.datalayer.dto.MergedAnomalyResultDTO;
import com.linkedin.thirdeye.datalayer.dto.RawAnomalyResultDTO;

public class TestMergedAnomalyResultManager extends AbstractManagerTestBase {
  MergedAnomalyResultDTO mergedResult = null;
  Long anomalyResultId;
  AnomalyFunctionDTO function = getTestFunctionSpec("metric", "dataset");

  @Test
  public void testMergedResultCRUD() {
    anomalyFunctionDAO.save(function);
    Assert.assertNotNull(function.getId());

    // create anomaly result
    RawAnomalyResultDTO result = getAnomalyResult();
    result.setFunction(function);
    rawResultDAO.save(result);

    RawAnomalyResultDTO resultRet = rawResultDAO.findById(result.getId());
    Assert.assertEquals(resultRet.getFunction(), function);

    anomalyResultId = result.getId();

    // Let's create merged result
    List<RawAnomalyResultDTO> rawResults = new ArrayList<>();
    rawResults.add(result);

    AnomalyMergeConfig mergeConfig = new AnomalyMergeConfig();

    List<MergedAnomalyResultDTO> mergedResults = AnomalyTimeBasedSummarizer
        .mergeAnomalies(rawResults, mergeConfig.getMaxMergeDurationLength(),
            mergeConfig.getSequentialAllowedGap());
    Assert.assertEquals(mergedResults.get(0).getStartTime(),result.getStartTime());
    Assert.assertEquals(mergedResults.get(0).getEndTime(),result.getEndTime());
    Assert.assertEquals(mergedResults.get(0).getAnomalyResults().get(0), result);

    // Let's persist the merged result
    mergedResults.get(0).setDimensions(result.getDimensions());

    mergedResultDAO.save(mergedResults.get(0));
    mergedResult = mergedResults.get(0);
    Assert.assertNotNull(mergedResult.getId());

    // verify the merged result
    MergedAnomalyResultDTO mergedResultById = mergedResultDAO.findById(mergedResult.getId());
    Assert.assertEquals(mergedResultById.getAnomalyResults(), rawResults);
    Assert.assertEquals(mergedResultById.getAnomalyResults().get(0).getId(), anomalyResultId);

    List<MergedAnomalyResultDTO> mergedResultsByMetricDimensionsTime = mergedResultDAO
        .findByCollectionMetricDimensionsTime(mergedResult.getCollection(), mergedResult.getMetric(),
            mergedResult.getDimensions().toString(), 0, System.currentTimeMillis(), true);

    Assert.assertEquals(mergedResultsByMetricDimensionsTime.get(0), mergedResult);
  }

  @Test(dependsOnMethods = {"testMergedResultCRUD"})
  public void testFeedback() {
    MergedAnomalyResultDTO anomalyMergedResult = mergedResultDAO.findById(mergedResult.getId());
    AnomalyFeedbackDTO feedback = new AnomalyFeedbackDTO();
    feedback.setComment("this is a good find");
    feedback.setFeedbackType(AnomalyFeedbackType.ANOMALY);
    feedback.setStatus(FeedbackStatus.NEW);
    anomalyMergedResult.setFeedback(feedback);
    mergedResultDAO.save(anomalyMergedResult);

    //verify feedback
    MergedAnomalyResultDTO mergedResult1 = mergedResultDAO.findById(mergedResult.getId());
    Assert.assertEquals(mergedResult1.getAnomalyResults().get(0).getId(), anomalyResultId);
    Assert.assertEquals(mergedResult1.getFeedback().getFeedbackType(), AnomalyFeedbackType.ANOMALY);
  }
}
