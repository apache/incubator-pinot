package com.linkedin.thirdeye.db;

import com.linkedin.thirdeye.api.dto.GroupByKey;
import com.linkedin.thirdeye.api.dto.GroupByRow;
import com.linkedin.thirdeye.constant.AnomalyFeedbackType;
import com.linkedin.thirdeye.constant.FeedbackStatus;
import com.linkedin.thirdeye.db.dao.AbstractDbTestBase;
import com.linkedin.thirdeye.db.entity.AnomalyFeedback;
import com.linkedin.thirdeye.db.entity.AnomalyFunctionSpec;
import com.linkedin.thirdeye.db.entity.AnomalyResult;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestAnomalyResultDAO extends AbstractDbTestBase {

  Long anomalyResultId;
  AnomalyFunctionSpec spec = getTestFunctionSpec("metric", "dataset");

  @Test
  public void testAnomalyResultCRUD() {
    anomalyFunctionDAO.save(spec);
    Assert.assertNotNull(spec);

    // create anomaly result
    AnomalyResult result = getAnomalyResult();
    result.setFunction(spec);
    anomalyResultDAO.save(result);

    AnomalyResult resultRet = anomalyResultDAO.findById(result.getId());
    Assert.assertEquals(resultRet.getFunction(), spec);

    anomalyResultId = result.getId();
  }

  @Test(dependsOnMethods = {"testAnomalyResultCRUD"})
  public void testGetCountByFunction() {
    List<GroupByRow<GroupByKey, Long>> groupByRows =
        anomalyResultDAO.getCountByFunction(0l, System.currentTimeMillis());
    Assert.assertEquals(groupByRows.size(), 1);
    Assert.assertEquals(groupByRows.get(0).getGroupBy().getFunctionId(), spec.getId());
    Assert.assertEquals(groupByRows.get(0).getValue().longValue(), 1);
  }

  @Test(dependsOnMethods = {"testGetCountByFunction"})
  public void testResultFeedback() {
    AnomalyResult result = anomalyResultDAO.findById(anomalyResultId);
    Assert.assertNotNull(result);
    Assert.assertNull(result.getFeedback());

    AnomalyFeedback feedback = new AnomalyFeedback();
    feedback.setComment("this is a good find");
    feedback.setFeedbackType(AnomalyFeedbackType.ANOMALY);
    feedback.setStatus(FeedbackStatus.NEW);
    result.setFeedback(feedback);
    anomalyResultDAO.save(result);

    AnomalyResult resultRet = anomalyResultDAO.findById(anomalyResultId);
    Assert.assertEquals(resultRet.getId(), result.getId());
    Assert.assertNotNull(resultRet.getFeedback());

    AnomalyFunctionSpec functionSpec = result.getFunction();

    anomalyResultDAO.deleteById(anomalyResultId);
    anomalyFunctionDAO.delete(functionSpec);
  }

}
