package com.linkedin.thirdeye.detector.email;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.hibernate.SessionFactory;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.linkedin.thirdeye.client.ThirdEyeCacheRegistry;
import com.linkedin.thirdeye.client.cache.QueryCache;
import com.linkedin.thirdeye.client.comparison.TimeOnTimeComparisonHandler;
import com.linkedin.thirdeye.detector.api.EmailConfiguration;
import com.linkedin.thirdeye.detector.db.AnomalyResultDAO;
import com.linkedin.thirdeye.detector.db.EmailConfigurationDAO;
import com.linkedin.thirdeye.detector.driver.FailureEmailConfiguration;
import com.linkedin.thirdeye.detector.driver.TestAnomalyApplication;
import com.linkedin.thirdeye.detector.driver.TestAnomalyApplication.TestType;

import io.dropwizard.lifecycle.Managed;

public class EmailReportJobManager implements Managed {
  private static final Logger LOG = LoggerFactory.getLogger(EmailReportJobManager.class);
  private static final ThirdEyeCacheRegistry CACHE_REGISTRY_INSTANCE = ThirdEyeCacheRegistry.getInstance();

  private final Scheduler quartzScheduler;
  private final EmailConfigurationDAO configurationDAO;
  private final Map<String, EmailConfiguration> jobKeys;
  private final AnomalyResultDAO resultDAO;
  private final SessionFactory sessionFactory;
  private final AtomicInteger applicationPort;
  private final TimeOnTimeComparisonHandler timeOnTimeComparisonHandler;
  private final Object sync;
  private final String dashboardHost;
  private final FailureEmailConfiguration failureEmailConfig;
  private static final ObjectMapper reader = new ObjectMapper(new YAMLFactory());
  private final QueryCache queryCache;

  public EmailReportJobManager(Scheduler quartzScheduler, EmailConfigurationDAO configurationDAO,
      AnomalyResultDAO resultDAO, SessionFactory sessionFactory, AtomicInteger applicationPort,
      String dashboardHost, FailureEmailConfiguration failureEmailConfig) {

    this.queryCache = CACHE_REGISTRY_INSTANCE.getQueryCache();
    this.quartzScheduler = quartzScheduler;
    this.configurationDAO = configurationDAO;
    this.sessionFactory = sessionFactory;
    this.resultDAO = resultDAO;
    this.applicationPort = applicationPort;
    this.timeOnTimeComparisonHandler = new TimeOnTimeComparisonHandler(queryCache);
    this.failureEmailConfig = failureEmailConfig;
    this.jobKeys = new HashMap<>();
    this.sync = new Object();
    this.dashboardHost = dashboardHost;
  }

  public void sendAdHoc(Long id) throws Exception {
    synchronized (sync) {
      EmailConfiguration emailConfig = configurationDAO.findById(id);

      String triggerKey = String.format("ad_hoc_email_trigger_%d", emailConfig.getId());
      Trigger trigger = TriggerBuilder.newTrigger().withIdentity(triggerKey).startNow().build();

      String jobKey = String.format("ad_hoc_email_job_%d", emailConfig.getId());
      buildAndScheduleJob(emailConfig, trigger, jobKey);
    }
  }

  @Override
  public void start() throws Exception {
    synchronized (sync) {
      List<EmailConfiguration> emailConfigs = configurationDAO.findAll();
      for (EmailConfiguration emailConfig : emailConfigs) {
        if (emailConfig.getIsActive()) {
          String triggerKey = String.format("email_trigger_%d", emailConfig.getId());
          CronTrigger trigger = TriggerBuilder.newTrigger().withIdentity(triggerKey)
              .withSchedule(CronScheduleBuilder.cronSchedule(emailConfig.getCron())).build();

          String jobKey = String.format("email_job_%d", emailConfig.getId());
          jobKeys.put(jobKey, emailConfig);
          buildAndScheduleJob(emailConfig, trigger, jobKey);
        }
      }
    }
  }

  @Override
  public void stop() throws Exception {
    synchronized (sync) {
      for (Map.Entry<String, EmailConfiguration> entry : jobKeys.entrySet()) {
        quartzScheduler.deleteJob(JobKey.jobKey(entry.getKey()));
        LOG.info("Stopped {}: {}", entry.getKey(), entry.getValue());
      }
    }
  }

  public void reset() throws Exception {
    synchronized (sync) {
      stop();
      start();
    }
  }

  public void runAdhocFile(String filePath) throws Exception {
    synchronized (sync) {
      File file = new File(filePath);
      if (!file.exists() || file.isDirectory()) {
        throw new IllegalArgumentException("File does not exist or is a directory: " + file);
      }
      EmailConfiguration emailConfig = reader.readValue(file, EmailConfiguration.class);
      emailConfig.setId(-1);
      String triggerKey = String.format("file-based_email_trigger_%s", filePath);
      Trigger trigger = TriggerBuilder.newTrigger().withIdentity(triggerKey).startNow().build();

      String jobKey = String.format("file-based_email_job_%s", filePath);
      buildAndScheduleJob(emailConfig, trigger, jobKey);
    }
  }

  public void runAdhocConfig(EmailConfiguration emailConfig, String executionName)
      throws Exception {
    synchronized (sync) {
      String triggerKey = String.format("adhoc_config-based_email_trigger_%s", executionName);
      Trigger trigger = TriggerBuilder.newTrigger().withIdentity(triggerKey).startNow().build();

      String jobKey = String.format("adhoc_config-based_email_job_%s", executionName);
      buildAndScheduleJob(emailConfig, trigger, jobKey);
    }
  }

  private void buildAndScheduleJob(EmailConfiguration emailConfig, Trigger trigger, String jobKey)
      throws SchedulerException {
    JobDetail job = JobBuilder.newJob(EmailReportJob.class).withIdentity(jobKey).build();

    job.getJobDataMap().put(EmailReportJob.RESULT_DAO, resultDAO);
    job.getJobDataMap().put(EmailReportJob.CONFIG, emailConfig);
    job.getJobDataMap().put(EmailReportJob.SESSION_FACTORY, sessionFactory);
    job.getJobDataMap().put(EmailReportJob.APPLICATION_PORT, applicationPort.get());
    job.getJobDataMap().put(EmailReportJob.TIME_ON_TIME_COMPARISON_HANDLER,
        timeOnTimeComparisonHandler);
    job.getJobDataMap().put(EmailReportJob.DASHBOARD_HOST, dashboardHost);
    job.getJobDataMap().put(FailureEmailConfiguration.FAILURE_EMAIL_CONFIG_KEY, failureEmailConfig);
    quartzScheduler.scheduleJob(job, trigger);
    LOG.info("Started {}: {}", jobKey, emailConfig);
  }

  public static void main(String[] args) throws Exception {
    if (args.length != 2) {
      System.err.println("Arguments must be configYml emailConfigPath");
      System.exit(1);
    }
    String thirdEyeConfigDir = args[0];
    System.setProperty("dw.rootDir", thirdEyeConfigDir);
    String detectorApplicationConfigFile = thirdEyeConfigDir + "/" + "detector.yml";
    String filePath = args[1];

    new TestAnomalyApplication(filePath, null, null, TestType.EMAIL, -1).run(new String[] {
        "server", detectorApplicationConfigFile
    });
  }
}
