package com.linkedin.thirdeye.anomaly.utils;

import com.linkedin.thirdeye.anomalydetection.context.AnomalyFeedback;
import com.linkedin.thirdeye.datalayer.dto.MergedAnomalyResultDTO;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.commons.collections.CollectionUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class AnomalyUtils {
  private static final Logger LOG = LoggerFactory.getLogger(AnomalyUtils.class);

  /**
   * Logs the known anomalies whose window overlaps with the given window, whose range is defined by windowStart
   * and windowEnd.
   *
   * Reason to log the overlapped anomalies: During anomaly detection, the know anomalies are supposedly used to remove
   * abnormal baseline values but not current values. This method provides a check before sending the known anomalies to
   * anomaly detection functions.
   *
   * @param windowStart the inclusive start time of the window
   * @param windowEnd the exclusive end time of the window
   * @param knownAnomalies the known anomalies
   */
  public static void logAnomaliesOverlapWithWindow(DateTime windowStart, DateTime windowEnd,
      List<MergedAnomalyResultDTO> knownAnomalies) {
    if (CollectionUtils.isEmpty(knownAnomalies) || windowEnd.compareTo(windowStart) <= 0) {
      return;
    }

    List<MergedAnomalyResultDTO> overlappedAnomalies = new ArrayList<>();
    for (MergedAnomalyResultDTO knownAnomaly : knownAnomalies) {
      if (knownAnomaly.getStartTime() <= windowEnd.getMillis() && knownAnomaly.getEndTime() >= windowStart.getMillis()) {
        overlappedAnomalies.add(knownAnomaly);
      }
    }

    if (overlappedAnomalies.size() > 0) {
      StringBuffer sb = new StringBuffer();
      String separator = "";
      for (MergedAnomalyResultDTO overlappedAnomaly : overlappedAnomalies) {
        sb.append(separator).append(overlappedAnomaly.getStartTime()).append("--").append(overlappedAnomaly.getEndTime());
        separator = ", ";
      }
      LOG.warn("{} merged anomalies overlap with this window {} -- {}. Anomalies: {}", overlappedAnomalies.size(),
          windowStart, windowEnd, sb.toString());
    }
  }

  /**
   * This function checks if the input list of merged anomalies has at least one positive label.
   * It is a helper for alert filter auto tuning
   * @param mergedAnomalyResultDTOS
   * @return true if the list of merged anomalies has at least one positive label, false otherwise
   */
  public static Boolean checkHasLabels(List<MergedAnomalyResultDTO> mergedAnomalyResultDTOS){
    for(MergedAnomalyResultDTO anomaly: mergedAnomalyResultDTOS){
      AnomalyFeedback feedback = anomaly.getFeedback();
      if (feedback != null){
        return true;
      }
    }
    return false;
  }

  /**
   * Safely and quitely shutdown executor service.
   *
   * @param executorService the executor service to be shutdown.
   * @param ownerClass the class that owns the executor service.
   */
  public static void safelyShutdownExecutionService(ExecutorService executorService, Class ownerClass) {
    if (executorService == null) {
      return;
    }
    executorService.shutdown(); // Prevent new tasks from being submitted
    try {
      // Wait a while for existing tasks to terminate
      if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
        executorService.shutdownNow(); // Force terminate all currently executing tasks
        // Wait for tasks to be cancelled
        if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
          if (ownerClass != null) {
            LOG.error("Failed to terminate thread pool for class {}", ownerClass.getSimpleName());
          } else {
            LOG.error("Failed to terminate thread pool: {}", executorService);
          }
        }
      }
    } catch (InterruptedException e) { // If current thread is interrupted
      executorService.shutdownNow(); // Interrupt all currently executing tasks for the last time
      Thread.currentThread().interrupt();
    }
  }
  public static class metaDataNode{
    public double windowSize;
    public double severity;
    public String startTimeISO;
    public String endTimeISO;
    public String functionName;
    public AnomalyFeedback feedback;

    public metaDataNode(MergedAnomalyResultDTO anomaly){
      this.windowSize = 1. * (anomaly.getEndTime() - anomaly.getStartTime()) / 3600000L;
      this.severity = Math.abs(anomaly.getWeight());
      this.startTimeISO = new Timestamp(anomaly.getStartTime()).toString();
      this.endTimeISO = new Timestamp(anomaly.getEndTime()).toString();
      this.functionName = anomaly.getFunction().getFunctionName();
      this.feedback = anomaly.getFeedback();
    }
  }
}
