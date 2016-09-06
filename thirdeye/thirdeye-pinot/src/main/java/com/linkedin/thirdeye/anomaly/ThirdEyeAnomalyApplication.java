package com.linkedin.thirdeye.anomaly;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import com.linkedin.thirdeye.anomaly.alert.AlertJobResource;
import com.linkedin.thirdeye.anomaly.alert.AlertJobScheduler;
import com.linkedin.thirdeye.anomaly.detection.DetectionJobResource;
import com.linkedin.thirdeye.anomaly.detection.DetectionJobScheduler;
import com.linkedin.thirdeye.anomaly.merge.AnomalyMergeExecutor;
import com.linkedin.thirdeye.anomaly.monitor.MonitorJobScheduler;
import com.linkedin.thirdeye.anomaly.task.TaskDriver;
import com.linkedin.thirdeye.client.ThirdEyeCacheRegistry;
import com.linkedin.thirdeye.common.BaseThirdEyeApplication;
import com.linkedin.thirdeye.detector.function.AnomalyFunctionFactory;

import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.lifecycle.Managed;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

public class ThirdEyeAnomalyApplication
    extends BaseThirdEyeApplication<ThirdEyeAnomalyConfiguration> {

  private DetectionJobScheduler detectionJobScheduler = null;
  private TaskDriver taskDriver = null;
  private MonitorJobScheduler monitorJobScheduler = null;
  private AlertJobScheduler alertJobScheduler = null;
  private AnomalyFunctionFactory anomalyFunctionFactory = null;
  private AnomalyMergeExecutor anomalyMergeExecutor = null;

  public static void main(final String[] args) throws Exception {
    List<String> argList = new ArrayList<>(Arrays.asList(args));
    if (argList.size() == 1) {
      argList.add(0, "server");
    }
    int lastIndex = argList.size() - 1;
    String thirdEyeConfigDir = argList.get(lastIndex);
    System.setProperty("dw.rootDir", thirdEyeConfigDir);
    String detectorApplicationConfigFile = thirdEyeConfigDir + "/" + "detector.yml";
    argList.set(lastIndex, detectorApplicationConfigFile); // replace config dir with the
                                                           // actual config file
    new ThirdEyeAnomalyApplication().run(argList.toArray(new String[argList.size()]));
  }

  @Override
  public String getName() {
    return "Thirdeye Controller";
  }

  @Override
  public void initialize(final Bootstrap<ThirdEyeAnomalyConfiguration> bootstrap) {
    bootstrap.addBundle(new AssetsBundle("/assets/", "/", "index.html"));
  }

  @Override
  public void run(final ThirdEyeAnomalyConfiguration config, final Environment environment)
      throws Exception {
    LOG.info("Starting ThirdeyeAnomalyApplication : Scheduler {} Worker {}", config.isScheduler(), config.isWorker());
    super.initDAOs();
    ThirdEyeCacheRegistry.initializeCaches(config, webappConfigDAO);
    environment.lifecycle().manage(new Managed() {
      @Override
      public void start() throws Exception {

        if (config.isWorker()) {
          anomalyFunctionFactory = new AnomalyFunctionFactory(config.getFunctionConfigPath());
          taskDriver = new TaskDriver(config, anomalyJobDAO, anomalyTaskDAO, anomalyResultDAO, anomalyMergedResultDAO, anomalyFunctionFactory);
          taskDriver.start();
        }
        if (config.isScheduler()) {
          detectionJobScheduler = new DetectionJobScheduler(anomalyJobDAO, anomalyTaskDAO, anomalyFunctionDAO);
          detectionJobScheduler.start();
          environment.jersey()
          .register(new DetectionJobResource(detectionJobScheduler, anomalyFunctionDAO));
        }
        if (config.isMonitor()) {
          monitorJobScheduler = new MonitorJobScheduler(anomalyJobDAO, anomalyTaskDAO, config.getMonitorConfiguration());
          monitorJobScheduler.start();
        }
        if (config.isAlert()) {
          alertJobScheduler = new AlertJobScheduler(anomalyJobDAO, anomalyTaskDAO, emailConfigurationDAO);
          alertJobScheduler.start();
          environment.jersey()
          .register(new AlertJobResource(alertJobScheduler, emailConfigurationDAO));
        }
        if (config.isMerger()) {
          ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
          anomalyMergeExecutor =
              new AnomalyMergeExecutor(anomalyMergedResultDAO, anomalyFunctionDAO, anomalyResultDAO,
                  executorService);
          anomalyMergeExecutor.start();
        }
      }

      @Override
      public void stop() throws Exception {
        if (config.isWorker()) {
          taskDriver.stop();
        }
        if (config.isScheduler()) {
          detectionJobScheduler.stop();
        }
        if (config.isMonitor()) {
          monitorJobScheduler.stop();
        }
        if (config.isAlert()) {
          alertJobScheduler.stop();
        }
        if (config.isMerger()) {
          anomalyMergeExecutor.stop();
        }
      }
    });
  }
}
