package com.linkedin.thirdeye.detector.driver;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;
import org.quartz.Scheduler;
import org.quartz.SchedulerFactory;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linkedin.thirdeye.api.TimeGranularity;
import com.linkedin.thirdeye.client.ThirdEyeClient;
import com.linkedin.thirdeye.client.cache.QueryCache;
import com.linkedin.thirdeye.client.comparison.TimeOnTimeComparisonHandler;
import com.linkedin.thirdeye.client.pinot.PinotThirdEyeClient;
import com.linkedin.thirdeye.client.timeseries.TimeSeriesHandler;
import com.linkedin.thirdeye.client.timeseries.TimeSeriesResponseConverter;
import com.linkedin.thirdeye.detector.ThirdEyeDetectorConfiguration;
import com.linkedin.thirdeye.detector.api.AnomalyFunctionRelation;
import com.linkedin.thirdeye.detector.api.AnomalyFunctionSpec;
import com.linkedin.thirdeye.detector.api.AnomalyResult;
import com.linkedin.thirdeye.detector.api.ContextualEvent;
import com.linkedin.thirdeye.detector.api.EmailConfiguration;
import com.linkedin.thirdeye.detector.db.AnomalyFunctionRelationDAO;
import com.linkedin.thirdeye.detector.db.AnomalyFunctionSpecDAO;
import com.linkedin.thirdeye.detector.db.AnomalyResultDAO;
import com.linkedin.thirdeye.detector.db.EmailConfigurationDAO;
import com.linkedin.thirdeye.detector.email.EmailReportJobManager;
import com.linkedin.thirdeye.detector.function.AnomalyFunctionFactory;

import io.dropwizard.Application;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.hibernate.HibernateBundle;
import io.dropwizard.lifecycle.Managed;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

/**
 * Mostly grabbed from ThirdEyeDetectorApplication, used to run an anomaly function or email from
 * file without
 * storing it. Not meant to be used as a fullproof test, as it's pretty hacky.
 * TODO currently OOO. Pinot client needs to be properly set up...
 */
public class TestAnomalyApplication extends Application<ThirdEyeDetectorConfiguration> {
  private static final Logger LOG = LoggerFactory.getLogger(TestAnomalyApplication.class);

  public enum TestType {
    FUNCTION,
    EMAIL;
  }

  private final HibernateBundle<ThirdEyeDetectorConfiguration> hibernate =
      new HibernateBundle<ThirdEyeDetectorConfiguration>(AnomalyFunctionSpec.class,
          AnomalyFunctionRelation.class, AnomalyResult.class, ContextualEvent.class,
          EmailConfiguration.class) {
        @Override
        public DataSourceFactory getDataSourceFactory(ThirdEyeDetectorConfiguration config) {
          return config.getDatabase();
        }
      };
  private final String filePath, startISO, endISO;
  private final TestType testType;
  private final int impersonatedId;

  public TestAnomalyApplication(String filePath, String startISO, String endISO, TestType testType,
      int impersonatedId) throws Exception {
    this.filePath = filePath;
    TimeGranularity timeGranularity = new TimeGranularity(1, TimeUnit.HOURS);
    this.startISO = truncateBy(startISO, timeGranularity);
    this.endISO = truncateBy(endISO, timeGranularity);
    this.testType = testType;
    this.impersonatedId = impersonatedId;
  }

  @Override
  public void initialize(Bootstrap<ThirdEyeDetectorConfiguration> bootstrap) {
    bootstrap.addBundle(hibernate);
  }

  @Override
  public void run(ThirdEyeDetectorConfiguration config, Environment environment) throws Exception {
    // DAO
    final AnomalyResultDAO anomalyResultDAO = new AnomalyResultDAO(hibernate.getSessionFactory());

    // Quartz Scheduler
    SchedulerFactory schedulerFactory = new StdSchedulerFactory();
    final Scheduler quartzScheduler = schedulerFactory.getScheduler();
    quartzScheduler.start();

    // TODO fix this up to be more standardized
    // ThirdEye client
    final ThirdEyeClient thirdEyeClient =
        // CollectionMapThirdEyeClient.fromFolder(config.getClientConfigRoot());
        PinotThirdEyeClient.getDefaultTestClient(); // TODO make this configurable

    environment.lifecycle().manage(new Managed() {
      @Override
      public void start() throws Exception {
        // NOP
      }

      @Override
      public void stop() throws Exception {
        thirdEyeClient.close();
      }
    });
    FailureEmailConfiguration failureEmailConfig = null; // TODO configure this for testing

    if (testType == TestType.FUNCTION) {
      int existingFunctionId = this.impersonatedId;
      // function
      final AnomalyFunctionSpecDAO anomalyFunctionSpecDAO =
          new AnomalyFunctionSpecDAO(hibernate.getSessionFactory());
      final AnomalyFunctionRelationDAO anomalyFunctionRelationDAO =
          new AnomalyFunctionRelationDAO(hibernate.getSessionFactory());

      final AnomalyFunctionFactory anomalyFunctionFactory =
          new AnomalyFunctionFactory(config.getFunctionConfigPath());

      final AnomalyDetectionJobManager jobManager = new AnomalyDetectionJobManager(quartzScheduler,
           anomalyFunctionSpecDAO, anomalyFunctionRelationDAO, anomalyResultDAO, hibernate.getSessionFactory(),
          environment.metrics(), anomalyFunctionFactory, failureEmailConfig);

      jobManager.runAdhocFile(filePath, existingFunctionId, startISO, endISO);
    } else if (testType == TestType.EMAIL) {

      // email
      final EmailConfigurationDAO emailConfigurationDAO =
          new EmailConfigurationDAO(hibernate.getSessionFactory());

      final EmailReportJobManager emailReportJobManager =
          new EmailReportJobManager(quartzScheduler, emailConfigurationDAO, anomalyResultDAO,
              hibernate.getSessionFactory(), new AtomicInteger(-1),
              config.getDashboardHost(), failureEmailConfig);
      emailReportJobManager.runAdhocFile(filePath);
    } else {
      throw new IllegalArgumentException("Unknown test type: " + testType);
    }

    // call stop after ready - quartz scheduler should know to wait for the current job.
    Thread.sleep(1000); // give it a bit of time to start up...
    quartzScheduler.shutdown(true);
    Thread.sleep(1000); // give time for logs to print.
    System.exit(0);
  }

  /**
   * Based off thirdeye-anomaly (author??)
   */
  private static String truncateBy(String ISODate, TimeGranularity tg) {
    if (ISODate == null)
      return null;
    DateTime parsedTime = ISODateTimeFormat.dateTimeParser().parseDateTime(ISODate);
    DateTime truncatedTime = truncateBy(parsedTime, tg);
    return truncatedTime.toString();
  }

  private static DateTime truncateBy(DateTime date, TimeGranularity tg) {
    if (date == null)
      return null;
    int millisOfDay = date.getMillisOfDay();
    millisOfDay = (int) ((millisOfDay / tg.toMillis()) * tg.toMillis());
    return new DateTime(date.withMillisOfDay(millisOfDay).getMillis());
  }
}
