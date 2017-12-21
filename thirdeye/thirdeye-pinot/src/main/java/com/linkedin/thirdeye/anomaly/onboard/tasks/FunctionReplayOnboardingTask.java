package com.linkedin.thirdeye.anomaly.onboard.tasks;

import com.google.common.base.Preconditions;
import com.linkedin.thirdeye.anomaly.detection.DetectionJobScheduler;
import com.linkedin.thirdeye.anomaly.onboard.BaseDetectionOnboardTask;
import com.linkedin.thirdeye.anomaly.onboard.DetectionOnboardExecutionContext;
import com.linkedin.thirdeye.anomalydetection.alertFilterAutotune.AlertFilterAutotuneFactory;
import com.linkedin.thirdeye.dashboard.resources.DetectionJobResource;
import com.linkedin.thirdeye.datalayer.dto.AnomalyFunctionDTO;
import com.linkedin.thirdeye.detector.email.filter.AlertFilterFactory;
import org.apache.commons.configuration.Configuration;
import org.joda.time.DateTime;
import org.joda.time.Period;


/**
 * This task performs replay on anomaly functions
 */
public class FunctionReplayOnboardingTask extends BaseDetectionOnboardTask {
  public static final String TASK_NAME = "FunctionReplay";

  public static final String ALERT_FILTER_FACTORY = DefaultDetectionOnboardJob.ALERT_FILTER_FACTORY;
  public static final String ALERT_FILTER_AUTOTUNE_FACTORY = DefaultDetectionOnboardJob.ALERT_FILTER_AUTOTUNE_FACTORY;
  public static final String ANOMALY_FUNCTION = DefaultDetectionOnboardJob.ANOMALY_FUNCTION;
  public static final String BACKFILL_PERIOD = DefaultDetectionOnboardJob.PERIOD;
  public static final String BACKFILL_START = DefaultDetectionOnboardJob.START;
  public static final String BACKFILL_END = DefaultDetectionOnboardJob.END;
  public static final String BACKFILL_FORCE = DefaultDetectionOnboardJob.FORCE;
  public static final String BACKFILL_SPEEDUP = DefaultDetectionOnboardJob.SPEEDUP;
  public static final String BACKFILL_REMOVE_ANOMALY_IN_WINDOW = DefaultDetectionOnboardJob.REMOVE_ANOMALY_IN_WINDOW;

  public static final String DEFAULT_BACKFILL_PERIOD = "P30D";
  public static final Boolean DEFAULT_BACKFILL_FORCE = true;
  public static final Boolean DEFAULT_BACKFILL_SPEEDUP = false;
  public static final Boolean DEFAULT_BACKFILL_REMOVE_ANOMALY_IN_WINDOW = false;

  public FunctionReplayOnboardingTask() {
    super(TASK_NAME);
  }

  /**
   * Executes the task. To fail this task, throw exceptions. The job executor will catch the exception and store
   * it in the message in the execution status of this task.
   */
  @Override
  public void run() {
    Configuration taskConfiguration = taskContext.getConfiguration();
    DetectionOnboardExecutionContext executionContext = taskContext.getExecutionContext();

    Preconditions.checkNotNull(executionContext.getExecutionResult(ALERT_FILTER_FACTORY));
    Preconditions.checkNotNull(executionContext.getExecutionResult(ALERT_FILTER_AUTOTUNE_FACTORY));

    AlertFilterFactory alertFilterFactory = (AlertFilterFactory) executionContext.getExecutionResult(ALERT_FILTER_FACTORY);
    AlertFilterAutotuneFactory alertFilterAutotuneFactory = (AlertFilterAutotuneFactory)
        executionContext.getExecutionResult(ALERT_FILTER_AUTOTUNE_FACTORY);

    Preconditions.checkNotNull(alertFilterFactory);
    Preconditions.checkNotNull(alertFilterAutotuneFactory);

    DetectionJobResource detectionJobResource = new DetectionJobResource(new DetectionJobScheduler(),
        alertFilterFactory, alertFilterAutotuneFactory);
    AnomalyFunctionDTO anomalyFunction = (AnomalyFunctionDTO) executionContext.getExecutionResult(ANOMALY_FUNCTION);
    long functionId = anomalyFunction.getId();
    Period backfillPeriod = Period.parse(taskConfiguration.getString(BACKFILL_PERIOD, DEFAULT_BACKFILL_PERIOD));
    DateTime start = DateTime.parse(taskConfiguration.getString(BACKFILL_START, DateTime.now().toString()));
    DateTime end = DateTime.parse(taskConfiguration.getString(BACKFILL_END, DateTime.now().minus(backfillPeriod).toString()));
    executionContext.setExecutionResult(BACKFILL_START, start);
    executionContext.setExecutionResult(BACKFILL_END, end);

    try {
      detectionJobResource.generateAnomaliesInRange(functionId, start.toString(), end.toString(),
          Boolean.toString(taskConfiguration.getBoolean(BACKFILL_FORCE, DEFAULT_BACKFILL_FORCE)),
          taskConfiguration.getBoolean(BACKFILL_SPEEDUP, DEFAULT_BACKFILL_SPEEDUP),
          taskConfiguration.getBoolean(BACKFILL_REMOVE_ANOMALY_IN_WINDOW, DEFAULT_BACKFILL_REMOVE_ANOMALY_IN_WINDOW));
    } catch (Exception e) {
      throw new UnsupportedOperationException(String.format("Unable to create detection job for %d from %s to %s",
          functionId, start.toString(), end.toString()));
    }
  }
}
