package com.linkedin.thirdeye.anomaly.alert;

import java.util.Objects;

import org.joda.time.DateTime;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.MoreObjects;
import com.linkedin.thirdeye.anomaly.task.TaskInfo;
import com.linkedin.thirdeye.db.entity.EmailConfiguration;
import com.linkedin.thirdeye.util.CustomDateDeserializer;
import com.linkedin.thirdeye.util.CustomDateSerializer;

public class AlertTaskInfo implements TaskInfo {

  private long jobExecutionId;

  @JsonSerialize(using = CustomDateSerializer.class)
  @JsonDeserialize(using = CustomDateDeserializer.class)
  private DateTime windowStartTime;

  @JsonSerialize(using = CustomDateSerializer.class)
  @JsonDeserialize(using = CustomDateDeserializer.class)
  private DateTime windowEndTime;
  private EmailConfiguration alertConfig;

  public AlertTaskInfo(long jobExecutionId, DateTime windowStartTime,
      DateTime windowEndTime, EmailConfiguration alertConfig) {
    this.jobExecutionId = jobExecutionId;
    this.windowStartTime = windowStartTime;
    this.windowEndTime = windowEndTime;
    this.alertConfig = alertConfig;
  }

  public AlertTaskInfo() {

  }

  public long getJobExecutionId() {
    return jobExecutionId;
  }

  public void setJobExecutionId(long jobExecutionId) {
    this.jobExecutionId = jobExecutionId;
  }

  public DateTime getWindowStartTime() {
    return windowStartTime;
  }

  public void setWindowStartTime(DateTime windowStartTime) {
    this.windowStartTime = windowStartTime;
  }

  public DateTime getWindowEndTime() {
    return windowEndTime;
  }

  public void setWindowEndTime(DateTime windowEndTime) {
    this.windowEndTime = windowEndTime;
  }

  public EmailConfiguration getAlertConfig() {
    return alertConfig;
  }

  public void setAlertConfig(EmailConfiguration alertConfig) {
    this.alertConfig = alertConfig;
  }


  @Override
  public boolean equals(Object o) {
    if (!(o instanceof AlertTaskInfo)) {
      return false;
    }
    AlertTaskInfo at = (AlertTaskInfo) o;
    return Objects.equals(jobExecutionId, at.getJobExecutionId())
        && Objects.equals(windowStartTime, at.getWindowStartTime())
        && Objects.equals(windowEndTime, at.getWindowEndTime())
        && Objects.equals(alertConfig, at.getAlertConfig());
  }

  @Override
  public int hashCode() {
    return Objects.hash(jobExecutionId, windowStartTime, windowEndTime, alertConfig);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("jobExecutionId", jobExecutionId)
        .add("windowStartTime", windowStartTime).add("windowEndTime", windowEndTime)
        .add("alertConfig", alertConfig).toString();
  }
}
