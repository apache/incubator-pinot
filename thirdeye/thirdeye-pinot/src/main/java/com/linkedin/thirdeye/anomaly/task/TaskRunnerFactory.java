package com.linkedin.thirdeye.anomaly.task;

import com.linkedin.thirdeye.anomaly.alert.AlertTaskRunner;
import com.linkedin.thirdeye.anomaly.detection.DetectionTaskRunner;
import com.linkedin.thirdeye.anomaly.monitor.MonitorTaskRunner;
import com.linkedin.thirdeye.anomaly.task.TaskConstants.TaskType;

/**
 * This class returns an instance of the task runner depending on the task type
 */
public class TaskRunnerFactory {

  public static TaskRunner getTaskRunnerFromTaskType(TaskType taskType) {
    TaskRunner taskRunner = null;
    switch(taskType) {
      case ANOMALY_DETECTION:
        taskRunner = new DetectionTaskRunner();
        break;
      case MERGE:
        break;
      case MONITOR:
        taskRunner = new MonitorTaskRunner();
        break;
      case ALERT:
        taskRunner = new AlertTaskRunner();
        break;
      default:
        break;

    }
    return taskRunner;
  }

}
