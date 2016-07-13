package com.linkedin.thirdeye.anomaly;

import com.linkedin.thirdeye.common.ThirdEyeConfiguration;
import com.linkedin.thirdeye.detector.driver.FailureEmailConfiguration;

public class ThirdEyeAnomalyConfiguration extends ThirdEyeConfiguration {
  private boolean scheduler = false;
  private boolean worker = false;
  private long id;
  private String dashboardHost;
  private FailureEmailConfiguration failureEmailConfig;

  public String getFunctionConfigPath() {
    return getRootDir() + "/detector-config/anomaly-functions/functions.properties";
  }

  public String getDashboardHost() {
    return dashboardHost;
  }

  public void setDashboardHost(String dashboardHost) {
    this.dashboardHost = dashboardHost;
  }

  public boolean isScheduler() {
    return scheduler;
  }

  public void setScheduler(boolean scheduler) {
    this.scheduler = scheduler;
  }

  public boolean isWorker() {
    return worker;
  }

  public void setWorker(boolean worker) {
    this.worker = worker;
  }

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public FailureEmailConfiguration getFailureEmailConfig() {
    return failureEmailConfig;
  }

  public void setFailureEmailConfig(FailureEmailConfiguration failureEmailConfig) {
    this.failureEmailConfig = failureEmailConfig;
  }

}
