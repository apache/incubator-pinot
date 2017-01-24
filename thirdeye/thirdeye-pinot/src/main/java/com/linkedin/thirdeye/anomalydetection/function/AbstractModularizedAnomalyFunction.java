package com.linkedin.thirdeye.anomalydetection.function;

import com.linkedin.pinot.pql.parsers.utils.Pair;
import com.linkedin.thirdeye.anomalydetection.context.AnomalyDetectionContext;
import com.linkedin.thirdeye.anomalydetection.context.TimeSeries;
import com.linkedin.thirdeye.anomalydetection.context.TimeSeriesKey;
import com.linkedin.thirdeye.anomalydetection.model.data.DataModel;
import com.linkedin.thirdeye.anomalydetection.model.data.NoopDataModel;
import com.linkedin.thirdeye.anomalydetection.model.detection.DetectionModel;
import com.linkedin.thirdeye.anomalydetection.model.detection.NoopDetectionModel;
import com.linkedin.thirdeye.anomalydetection.model.merge.MergeModel;
import com.linkedin.thirdeye.anomalydetection.model.merge.NoPredictionMergeModel;
import com.linkedin.thirdeye.anomalydetection.model.merge.NoopMergeModel;
import com.linkedin.thirdeye.anomalydetection.model.prediction.NoopPredictionModel;
import com.linkedin.thirdeye.anomalydetection.model.prediction.PredictionModel;
import com.linkedin.thirdeye.anomalydetection.model.transform.TransformationFunction;
import com.linkedin.thirdeye.api.DimensionMap;
import com.linkedin.thirdeye.api.MetricTimeSeries;
import com.linkedin.thirdeye.datalayer.dto.AnomalyFunctionDTO;
import com.linkedin.thirdeye.datalayer.dto.MergedAnomalyResultDTO;
import com.linkedin.thirdeye.datalayer.dto.RawAnomalyResultDTO;
import com.linkedin.thirdeye.detector.function.BaseAnomalyFunction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import org.apache.commons.collections.CollectionUtils;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class provides the default control logic to perform actions on an anomaly detection context
 * with the given anomaly detection module; the actions can be anomaly detection, information update
 * of merged anomalies, etc.
 */
public abstract class AbstractModularizedAnomalyFunction extends BaseAnomalyFunction implements AnomalyDetectionFunction {
  protected final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

  public static final String DEFAULT_MESSAGE_TEMPLATE = "weight: %.2f, score: %.2f";

  protected AnomalyFunctionDTO spec;
  protected Properties properties;

  protected DataModel dataModel = new NoopDataModel();
  protected List<TransformationFunction> currentTimeSeriesTransformationChain = new ArrayList<>();
  protected List<TransformationFunction> baselineTimeSeriesTransformationChain = new ArrayList<>();
  protected PredictionModel predictionModel = new NoopPredictionModel();
  protected DetectionModel detectionModel = new NoopDetectionModel();
  protected MergeModel mergeModel = new NoopMergeModel();

  @Override
  public void init(AnomalyFunctionDTO spec) throws Exception {
    this.spec = spec;
    this.properties = spec.toProperties();
  }

  @Override
  public AnomalyFunctionDTO getSpec() {
    return spec;
  }

  @Override
  public List<Interval> getTimeSeriesIntervals(long monitoringWindowStartTime,
      long monitoringWindowEndTime) {
    return dataModel.getTrainingDataIntervals(monitoringWindowStartTime, monitoringWindowEndTime);
  }

  @Override
  public List<RawAnomalyResultDTO> analyze(AnomalyDetectionContext anomalyDetectionContext)
      throws Exception {
    if (!checkPrecondition(anomalyDetectionContext)) {
      return Collections.emptyList();
    }

    // Transform current and baseline time series and train the prediction model
    preparePredictionModel(anomalyDetectionContext);

    // Detect anomalies
    List<RawAnomalyResultDTO> rawAnomalies = detectionModel.detect(anomalyDetectionContext);

    return rawAnomalies;
  }

  @Override
  public void updateMergedAnomalyInfo(AnomalyDetectionContext anomalyDetectionContext,
      MergedAnomalyResultDTO anomalyToUpdated) throws Exception {
    if (!(mergeModel instanceof NoPredictionMergeModel)) {
      if (checkPrecondition(anomalyDetectionContext)) {
        // Transform current and baseline time series and train the prediction model
        preparePredictionModel(anomalyDetectionContext);
      } else {
        return;
      }
    }
    mergeModel.update(anomalyDetectionContext, anomalyToUpdated);
    this.writeMergedAnomalyInfo(mergeModel, anomalyToUpdated);
  }

  /**
   * This method is invoked after updateMergedAnomalyInfo. The method should define the actions
   * to update the values in computed merge model to the anomaly to be updated.
   *
   * @param computedMergeModel a computed merge model.
   * @param anomalyToBeUpdated the anomaly to be updated.
   */
  protected void writeMergedAnomalyInfo(MergeModel computedMergeModel,
      MergedAnomalyResultDTO anomalyToBeUpdated) {
    double weight = computedMergeModel.getWeight();
    double score = computedMergeModel.getScore();
    anomalyToBeUpdated.setWeight(weight);
    anomalyToBeUpdated.setScore(score);
    anomalyToBeUpdated.setMessage(String.format(DEFAULT_MESSAGE_TEMPLATE, weight, score));
  }

  /**
   * Returns true if the following conditions hold:
   * 1. Anomaly detection context is not null.
   * 2. Anomaly detection function is not null.
   * 3. Current time series is not null.
   *
   * Note: Baseline time series could be null for MIN_MAX_FUNCTION.
   *
   * @param anomalyDetectionContext the context for anomaly detection.
   * @return true if the context satisfies the pre-condition of anomaly detection.
   */
  protected static boolean checkPrecondition(AnomalyDetectionContext anomalyDetectionContext) {
    return anomalyDetectionContext != null && anomalyDetectionContext.getCurrent() != null
        && anomalyDetectionContext.getAnomalyDetectionFunction() != null;
  }

  /**
   * Performs the following operations on the given an anomaly detection context.
   * 1. Transform current and baseline time series.
   * 2. Train prediction model using the baseline time series.
   *
   * At the end of this method, a transformed current time series, transformed baselines, and a
   * trained prediction model are appended to the given anomaly detection context.
   *
   * The processed anomaly detection context has multiple usages. For example, it could be used for
   * detecting anomalies, plotting UI, updating the information of anomalies, etc.
   *
   * @param anomalyDetectionContext anomaly detection context that contains the necessary time
   *                                series for preparing the prediction model
   */
  protected void preparePredictionModel(AnomalyDetectionContext anomalyDetectionContext) {
    // Transform the observed (current) time series
    if (anomalyDetectionContext.getTransformedCurrent() == null) {
      anomalyDetectionContext.setTransformedCurrent(anomalyDetectionContext.getCurrent());
    }
    if (CollectionUtils.isNotEmpty(currentTimeSeriesTransformationChain)) {
      for (TransformationFunction tf : currentTimeSeriesTransformationChain) {
        anomalyDetectionContext
            .setTransformedCurrent(tf.transform(anomalyDetectionContext.getTransformedCurrent(),
                anomalyDetectionContext));
      }
    }

    // Transform baseline time series
    if (anomalyDetectionContext.getTransformedBaselines() == null) {
      anomalyDetectionContext.setTransformedBaselines(anomalyDetectionContext.getBaselines());
    }
    if (CollectionUtils.isNotEmpty(baselineTimeSeriesTransformationChain)) {
      for (TransformationFunction tf : baselineTimeSeriesTransformationChain) {
        List<TimeSeries> transformedBaselines = new ArrayList<>();
        for (TimeSeries ts : anomalyDetectionContext.getTransformedBaselines()) {
          TimeSeries transformedTS = tf.transform(ts, anomalyDetectionContext);
          transformedBaselines.add(transformedTS);
        }
        anomalyDetectionContext.setTransformedBaselines(transformedBaselines);
      }
    }

    // Train Prediction Model
    predictionModel.train(anomalyDetectionContext.getTransformedBaselines());
    anomalyDetectionContext.setTrainedPredictionModel(predictionModel);
  }

  //////////////////// Methods for backward compatibility /////////////////////////
  @Override
  public List<Pair<Long, Long>> getDataRangeIntervals(Long monitoringWindowStartTime,
      Long monitoringWindowEndTime) {
    List<Interval> timeSeriesIntervals =
        this.getTimeSeriesIntervals(monitoringWindowStartTime, monitoringWindowEndTime);
    return BackwardAnomalyFunctionUtils.toBackwardCompatibleDataRanges(timeSeriesIntervals);
  }

  @Override
  public List<RawAnomalyResultDTO> analyze(DimensionMap exploredDimensions, MetricTimeSeries timeSeries,
      DateTime windowStart, DateTime windowEnd, List<MergedAnomalyResultDTO> knownAnomalies)
      throws Exception {
    // Create the anomaly detection context for the new modularized anomaly function
    AnomalyDetectionContext anomalyDetectionContext = new AnomalyDetectionContext();
    anomalyDetectionContext.setAnomalyDetectionFunction(this);

    TimeSeriesKey timeSeriesKey = new TimeSeriesKey();
    timeSeriesKey.setDimensionMap(exploredDimensions);
    timeSeriesKey.setMetricName(spec.getMetric());
    anomalyDetectionContext.setTimeSeriesKey(timeSeriesKey);

    List<Interval> intervals = this.getTimeSeriesIntervals(windowStart.getMillis(), windowEnd.getMillis());
    List<TimeSeries> timeSeriesList =
        BackwardAnomalyFunctionUtils.splitSetsOfTimeSeries(timeSeries, spec.getMetric(), intervals);

    anomalyDetectionContext.setCurrent(timeSeriesList.get(0));
    timeSeriesList.remove(0);
    anomalyDetectionContext.setBaselines(timeSeriesList);

    // Detect anomalies using the method from the new modularized anomaly function
    return this.analyze(anomalyDetectionContext);
  }

  @Override
  public void updateMergedAnomalyInfo(MergedAnomalyResultDTO anomalyToUpdated,
      MetricTimeSeries timeSeries, DateTime windowStart, DateTime windowEnd,
      List<MergedAnomalyResultDTO> knownAnomalies) throws Exception {

  }
}
