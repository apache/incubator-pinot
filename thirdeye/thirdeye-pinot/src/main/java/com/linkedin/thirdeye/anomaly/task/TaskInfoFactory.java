package com.linkedin.thirdeye.anomaly.task;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkedin.thirdeye.anomaly.detection.DetectionTaskInfo;
import com.linkedin.thirdeye.anomaly.monitor.MonitorTaskInfo;
import com.linkedin.thirdeye.anomaly.task.TaskConstants.TaskType;

/**
 * This class returns deserializes the task info json and returns the TaskInfo,
 * depending on the task type
 */
public class TaskInfoFactory {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final Logger LOG = LoggerFactory.getLogger(TaskInfoFactory.class);

  public static TaskInfo getTaskInfoFromTaskType(TaskType taskType, String taskInfoString)
      throws JsonParseException, JsonMappingException, IOException {
    TaskInfo taskInfo = null;
    try {
      switch(taskType) {
        case ANOMALY_DETECTION:
          taskInfo = OBJECT_MAPPER.readValue(taskInfoString, DetectionTaskInfo.class);
          break;
        case MERGE:
          break;
        case MONITOR:
          taskInfo = OBJECT_MAPPER.readValue(taskInfoString, MonitorTaskInfo.class);
          break;
        case REPORTER:
          break;
        default:
          break;
      }
    } catch (Exception e) {
      LOG.error("Exception in converting taskInfoString {} to taskType {}", taskInfoString, taskType, e);
    }
    return taskInfo;
  }

}
