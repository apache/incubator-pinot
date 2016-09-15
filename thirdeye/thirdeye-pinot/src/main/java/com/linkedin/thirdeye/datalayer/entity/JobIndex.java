package com.linkedin.thirdeye.datalayer.entity;

public class JobIndex extends AbstractIndexEntity {
  String name;
  String status;
  long scheduleStartTime;
  long scheduleEndTime;


  public long getScheduleStartTime() {
    return scheduleStartTime;
  }

  public void setScheduleStartTime(long scheduleStartTime) {
    this.scheduleStartTime = scheduleStartTime;
  }

  public long getScheduleEndTime() {
    return scheduleEndTime;
  }

  public void setScheduleEndTime(long scheduleEndTime) {
    this.scheduleEndTime = scheduleEndTime;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }



  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  @Override
  public String toString() {
    return "JobIndex [name=" + name + ", status=" + status + ", scheduleStartTime="
        + scheduleStartTime + ", scheduleEndTime=" + scheduleEndTime + "]";
  }

}
