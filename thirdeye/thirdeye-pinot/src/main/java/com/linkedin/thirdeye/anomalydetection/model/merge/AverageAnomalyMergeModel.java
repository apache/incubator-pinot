package com.linkedin.thirdeye.anomalydetection.model.merge;

import com.linkedin.thirdeye.anomalydetection.context.AnomalyDetectionContext;
import com.linkedin.thirdeye.datalayer.dto.MergedAnomalyResultDTO;
import com.linkedin.thirdeye.datalayer.dto.RawAnomalyResultDTO;
import java.util.List;
import org.apache.commons.collections.CollectionUtils;

public class AverageAnomalyMergeModel extends AbstractMergeModel {
  /**
   * The weight and score is the average weight and score, respectively, of the raw anomalies of
   * the given merged anomaly. If the merged anomaly could not provides the list of its raw
   * anomalies, then weight and score are set to 0d.
   *
   * @param anomalyDetectionContext a context that would not be used.
   *
   * @param anomalyToUpdated the anomaly of which the information is updated.
   */
  @Override
  public void update(AnomalyDetectionContext anomalyDetectionContext,
      MergedAnomalyResultDTO anomalyToUpdated) {
    if (CollectionUtils.isEmpty(anomalyToUpdated.getAnomalyResults())) {
      return;
    }
    List<RawAnomalyResultDTO> rawAnomalyResultDTOs = anomalyToUpdated.getAnomalyResults();

    weight = 0d;
    score = 0d;
    for (RawAnomalyResultDTO rawAnomaly : rawAnomalyResultDTOs) {
      weight += rawAnomaly.getWeight();
      score += rawAnomaly.getScore();
    }
    if (rawAnomalyResultDTOs.size() != 0) {
      double size = rawAnomalyResultDTOs.size();
      weight /= size;
      score /= size;
    }
  }
}
