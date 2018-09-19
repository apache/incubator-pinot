/*
 * Copyright (C) 2014-2018 LinkedIn Corp. (pinot-core@linkedin.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.linkedin.thirdeye.detection.algorithm.stage;

import com.linkedin.thirdeye.datalayer.dto.MergedAnomalyResultDTO;
import com.linkedin.thirdeye.detection.DataProvider;
import com.linkedin.thirdeye.detection.StaticDetectionPipelineData;
import com.linkedin.thirdeye.detection.StaticDetectionPipelineModel;

import static com.linkedin.thirdeye.detection.algorithm.stage.StageUtils.*;


/**
 * The anomaly filter stage
 */
public abstract class StaticAnomalyFilterStage implements AnomalyFilterStage {
  /**
   * Returns a data model describing all required data(time series, aggregates, existing anomalies) to perform a stage.
   * Data is retrieved in one pass and cached between executions if possible.
   * @return detection data model
   */
  abstract StaticDetectionPipelineModel getModel();

  /**
   * Check if an anomaly is qualified to pass the filter
   * @param anomaly the anomaly
   * @param data data(time series, anomalies, etc.) as described by data model
   * @return a boolean value to suggest if the anomaly should be filtered
   */
  abstract boolean isQualified(MergedAnomalyResultDTO anomaly, StaticDetectionPipelineData data);

  @Override
  public final boolean isQualified(MergedAnomalyResultDTO anomaly, DataProvider provider) {
    return isQualified(anomaly, getDataForModel(provider, this.getModel()));
  }
}
