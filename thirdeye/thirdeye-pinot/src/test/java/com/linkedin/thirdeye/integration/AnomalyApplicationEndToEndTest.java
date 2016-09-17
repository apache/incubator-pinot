package com.linkedin.thirdeye.integration;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.joda.time.DateTime;
import org.mockito.Matchers;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.quartz.SchedulerException;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.cache.LoadingCache;
import com.linkedin.thirdeye.anomaly.ThirdEyeAnomalyConfiguration;
import com.linkedin.thirdeye.anomaly.alert.AlertJobScheduler;
import com.linkedin.thirdeye.anomaly.detection.DetectionJobScheduler;
import com.linkedin.thirdeye.anomaly.job.JobConstants.JobStatus;
import com.linkedin.thirdeye.anomaly.merge.AnomalyMergeExecutor;
import com.linkedin.thirdeye.anomaly.monitor.MonitorConfiguration;
import com.linkedin.thirdeye.anomaly.monitor.MonitorJobScheduler;
import com.linkedin.thirdeye.anomaly.task.TaskConstants.TaskStatus;
import com.linkedin.thirdeye.anomaly.task.TaskConstants.TaskType;
import com.linkedin.thirdeye.anomaly.task.TaskDriver;
import com.linkedin.thirdeye.api.CollectionSchema;
import com.linkedin.thirdeye.api.TimeGranularity;
import com.linkedin.thirdeye.api.TimeSpec;
import com.linkedin.thirdeye.client.ThirdEyeCacheRegistry;
import com.linkedin.thirdeye.client.ThirdEyeClient;
import com.linkedin.thirdeye.client.ThirdEyeRequest;
import com.linkedin.thirdeye.client.ThirdEyeResponse;
import com.linkedin.thirdeye.client.cache.QueryCache;
import com.linkedin.thirdeye.client.pinot.PinotThirdEyeResponse;
import com.linkedin.thirdeye.dashboard.configs.CollectionConfig;
import com.linkedin.thirdeye.datalayer.bao.AbstractManagerTestBase;
import com.linkedin.thirdeye.datalayer.dto.JobDTO;
import com.linkedin.thirdeye.datalayer.dto.MergedAnomalyResultDTO;
import com.linkedin.thirdeye.datalayer.dto.RawAnomalyResultDTO;
import com.linkedin.thirdeye.datalayer.dto.TaskDTO;
import com.linkedin.thirdeye.detector.function.AnomalyFunctionFactory;


public class AnomalyApplicationEndToEndTest extends AbstractManagerTestBase {

  private DetectionJobScheduler detectionJobScheduler = null;
  private TaskDriver taskDriver = null;
  private MonitorJobScheduler monitorJobScheduler = null;
  private AlertJobScheduler alertJobScheduler = null;
  private AnomalyMergeExecutor anomalyMergeExecutor = null;
  private AnomalyFunctionFactory anomalyFunctionFactory = null;
  private ThirdEyeCacheRegistry cacheRegistry = ThirdEyeCacheRegistry.getInstance();
  private CollectionSchema testCollectionSchema;
  private CollectionConfig testCollectionConfig;
  private ThirdEyeAnomalyConfiguration thirdeyeAnomalyConfig;
  private List<TaskDTO> tasks;
  private List<JobDTO> jobs;
  private long functionId;

  private int id = 0;
  private String dashboardHost = "http://localhost:8080/dashboard";
  private String functionPropertiesFile = "/sample-config-dir/detector-config/anomaly-functions/functions.properties";
  private String thirdeyeCollectionSchema = "/sample-config-dir/webapp-config/CollectionSchema/test-collection.json";
  private String metric = "cost";
  private String collection = "test-collection";

  private void setup() throws Exception {

    // Mock query cache
    ThirdEyeClient mockThirdeyeClient = Mockito.mock(ThirdEyeClient.class);
    Mockito.when(mockThirdeyeClient.execute(Matchers.any(ThirdEyeRequest.class)))
    .thenAnswer(new Answer<ThirdEyeResponse>() {

      @Override
      public ThirdEyeResponse answer(InvocationOnMock invocation) throws Throwable {
        Object[] args = invocation.getArguments();
        ThirdEyeRequest request = (ThirdEyeRequest) args[0];
        ThirdEyeResponse response = getMockResponse(request);
        return response;
      }
    });

    QueryCache mockQueryCache = new QueryCache(mockThirdeyeClient, Executors.newFixedThreadPool(10));
    cacheRegistry.registerQueryCache(mockQueryCache);

    // Mock collection schema cache
    testCollectionSchema = CollectionSchema.decode(AnomalyApplicationEndToEndTest.class.getResourceAsStream(thirdeyeCollectionSchema));
    LoadingCache<String, CollectionSchema> mockCollectionSchemaCache = Mockito.mock(LoadingCache.class);
    Mockito.when(mockCollectionSchemaCache.get(collection)).thenReturn(testCollectionSchema);
    cacheRegistry.registerCollectionSchemaCache(mockCollectionSchemaCache);

    // Mock collection config cache
    testCollectionConfig = new CollectionConfig();
    testCollectionConfig.setCollectionName(collection);
    LoadingCache<String, CollectionConfig> mockCollectionConfigCache = Mockito.mock(LoadingCache.class);
    Mockito.when(mockCollectionConfigCache.get(collection)).thenReturn(testCollectionConfig);
    cacheRegistry.registerCollectionConfigCache(mockCollectionConfigCache);

    // Application config
    thirdeyeAnomalyConfig = new ThirdEyeAnomalyConfiguration();
    thirdeyeAnomalyConfig.setId(id);
    thirdeyeAnomalyConfig.setDashboardHost(dashboardHost);
    MonitorConfiguration monitorConfiguration = new MonitorConfiguration();
    monitorConfiguration.setMonitorFrequency(new TimeGranularity(30, TimeUnit.SECONDS));
    thirdeyeAnomalyConfig.setMonitorConfiguration(monitorConfiguration);

    // create test anomaly function
    functionId = anomalyFunctionDAO.save(getTestFunctionSpec(metric, collection));

    // create test email configuration
    emailConfigurationDAO.save(getTestEmailConfiguration(metric, collection));
  }


  private ThirdEyeResponse getMockResponse(ThirdEyeRequest request) {
    ThirdEyeResponse response = null;
    Random rand = new Random();
    TimeSpec dataTimeSpec = testCollectionSchema.getTime();
    List<String[]> rows = new ArrayList<>();
    DateTime start = request.getStartTimeInclusive();
    DateTime end = request.getEndTimeExclusive();
    List<String> metrics = request.getMetricNames();
    int bucket = 0;
    while (start.isBefore(end)) {
      String[] row = new String[metrics.size() + 1];
      row[0] = String.valueOf(bucket);
      bucket++;
      for (int i = 0; i < metrics.size(); i++) {
        row[i+1] = String.valueOf(rand.nextInt(1000));
      }
      rows.add(row);
      start = start.plusHours(1);
    }
    response = new PinotThirdEyeResponse(request, rows, dataTimeSpec);
    return response;
  }

  @Test(enabled=true)
  public void testThirdeyeAnomalyApplication() throws Exception {

    // setup caches and config
    setup();

    // start detection scheduler
    startDetectionScheduler();

    // start alert scheduler
    startAlertScheduler();

    // check for number of entries in tasks and jobs
    Thread.sleep(1000);
    Assert.assertEquals(jobDAO.findAll().size(), 2);
    Assert.assertEquals(taskDAO.findAll().size(), 2);
    Thread.sleep(10000);
    Assert.assertEquals(jobDAO.findAll().size(), 4);
    Assert.assertEquals(taskDAO.findAll().size(), 4);

    tasks = taskDAO.findAll();

    // check for task type
    int detectionCount = 0;
    int alertCount = 0;
    for (TaskDTO task : tasks) {
      if (task.getTaskType().equals(TaskType.ANOMALY_DETECTION)) {
        detectionCount ++;
      } else if (task.getTaskType().equals(TaskType.ALERT)) {
        alertCount ++;
      }
    }
    Assert.assertEquals(detectionCount, 2);
    Assert.assertEquals(alertCount, 2);

    // check for task status
    tasks = taskDAO.findAll();
    for (TaskDTO task : tasks) {
      Assert.assertEquals(task.getStatus(), TaskStatus.WAITING);
    }

    // start monitor
    startMonitor();

    // check for monitor tasks
    Thread.sleep(5000);
    tasks = taskDAO.findAll();
    int monitorCount = 0;
    for (TaskDTO task : tasks) {
      if (task.getTaskType().equals(TaskType.MONITOR)) {
        monitorCount++;
      }
    }
    Assert.assertTrue(monitorCount > 0);

    // check for job status
    jobs = jobDAO.findAll();
    for (JobDTO job : jobs) {
      Assert.assertEquals(job.getStatus(), JobStatus.SCHEDULED);
    }

    // start task drivers
    startWorker();
//    startWorker();
//    startWorker();

    // check for change in task status to COMPLETED
    Thread.sleep(30000);
    tasks = taskDAO.findAll();
    int completedCount = 0;
    for (TaskDTO task : tasks) {
      if (task.getStatus().equals(TaskStatus.COMPLETED)) {
        completedCount++;
      }
    }
    Assert.assertTrue(completedCount > 0);

    // check if anomalies present
    List<RawAnomalyResultDTO> rawAnomalies = rawResultDAO.findUnmergedByFunctionId(functionId);
    Assert.assertTrue(rawAnomalies.size() > 0);

    // start merge
    startMerger();

    // check merged anomalies
    Thread.sleep(10000);
    List<MergedAnomalyResultDTO> mergedAnomalies = mergedResultDAO.findByFunctionId(functionId);
    Assert.assertTrue(mergedAnomalies.size() > 0);

    // check for job status COMPLETED
    jobs = jobDAO.findAll();
    int completedJobCount = 0;
    for (JobDTO job : jobs) {
      if (job.getStatus().equals(JobStatus.COMPLETED)) {
        completedJobCount ++;
      }
    }
    Assert.assertTrue(completedJobCount > 0);

    // stop schedulers
    cleanup();
  }


  private void startMerger() {
    ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    anomalyMergeExecutor =
        new AnomalyMergeExecutor(mergedResultDAO, anomalyFunctionDAO, rawResultDAO, executorService);
    executorService.scheduleWithFixedDelay(anomalyMergeExecutor, 0, 1, TimeUnit.MINUTES);
  }


  private void startMonitor() {
    monitorJobScheduler = new MonitorJobScheduler(jobDAO, taskDAO, thirdeyeAnomalyConfig.getMonitorConfiguration());
    monitorJobScheduler.start();
  }


  private void startWorker() throws Exception {
    InputStream factoryStream = AnomalyApplicationEndToEndTest.class.getResourceAsStream(functionPropertiesFile);
    anomalyFunctionFactory = new AnomalyFunctionFactory(factoryStream);
    taskDriver = new TaskDriver(thirdeyeAnomalyConfig, jobDAO, taskDAO, rawResultDAO, mergedResultDAO,
        anomalyFunctionFactory);
    taskDriver.start();
  }


  private void startAlertScheduler() throws SchedulerException {
    alertJobScheduler = new AlertJobScheduler(jobDAO, taskDAO, emailConfigurationDAO);
    alertJobScheduler.start();
  }


  private void startDetectionScheduler() throws SchedulerException {
    detectionJobScheduler = new DetectionJobScheduler(jobDAO, taskDAO, anomalyFunctionDAO);
    detectionJobScheduler.start();
  }


  private void cleanup() throws SchedulerException {
    if (detectionJobScheduler != null) {
      detectionJobScheduler.stop();
    }
    if (alertJobScheduler != null) {
      alertJobScheduler.stop();
    }
    if (monitorJobScheduler != null) {
      monitorJobScheduler.stop();
    }
    if (anomalyMergeExecutor != null) {
      anomalyMergeExecutor.stop();
    }
    if (taskDriver != null) {
      taskDriver.stop();
    }
  }

}
