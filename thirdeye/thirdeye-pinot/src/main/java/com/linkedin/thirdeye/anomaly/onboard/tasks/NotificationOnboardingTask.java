package com.linkedin.thirdeye.anomaly.onboard.tasks;

import com.google.common.base.Preconditions;
import com.linkedin.thirdeye.anomaly.SmtpConfiguration;
import com.linkedin.thirdeye.anomaly.onboard.BaseDetectionOnboardTask;
import com.linkedin.thirdeye.anomaly.onboard.DetectionOnboardExecutionContext;
import com.linkedin.thirdeye.dashboard.resources.EmailResource;
import com.linkedin.thirdeye.datalayer.dto.AlertConfigDTO;
import com.linkedin.thirdeye.datasource.DAORegistry;
import org.apache.commons.configuration.Configuration;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Send out email notifications
 */
public class NotificationOnboardingTask extends BaseDetectionOnboardTask{
  private static final Logger LOG = LoggerFactory.getLogger(NotificationOnboardingTask.class);

  public static final String TASK_NAME = "Notification";

  public static final String ANOMALY_FUNCTION = DefaultDetectionOnboardJob.ANOMALY_FUNCTION;
  public static final String NOTIFICATION_START = DefaultDetectionOnboardJob.START;
  public static final String NOTIFICATION_END = DefaultDetectionOnboardJob.END;
  public static final String SMTP_HOST = DefaultDetectionOnboardJob.SMTP_HOST;
  public static final String SMTP_PORT = DefaultDetectionOnboardJob.SMTP_PORT;
  public static final String THIRDEYE_HOST = DefaultDetectionOnboardJob.THIRDEYE_HOST;
  public static final String DEFAULT_ALERT_SENDER = DefaultDetectionOnboardJob.DEFAULT_ALERT_SENDER;
  public static final String DEFAULT_ALERT_RECEIVER = DefaultDetectionOnboardJob.DEFAULT_ALERT_RECEIVER;
  public static final String PHANTON_JS_PATH = DefaultDetectionOnboardJob.PHANTON_JS_PATH;
  public static final String ROOT_DIR = DefaultDetectionOnboardJob.ROOT_DIR;

  public NotificationOnboardingTask(){
    super(TASK_NAME);
  }

  /**
   * Executes the task. To fail this task, throw exceptions. The job executor will catch the exception and store
   * it in the message in the execution status of this task.
   */
  @Override
  public void run() {
    Configuration taskConfigs = taskContext.getConfiguration();
    DetectionOnboardExecutionContext executionContext = taskContext.getExecutionContext();

    Preconditions.checkNotNull(executionContext.getExecutionResult(ANOMALY_FUNCTION));
    Preconditions.checkNotNull(executionContext.getExecutionResult(NOTIFICATION_START));
    Preconditions.checkNotNull(executionContext.getExecutionResult(NOTIFICATION_END));
    Preconditions.checkNotNull(taskConfigs.getString(SMTP_HOST));
    Preconditions.checkNotNull(taskConfigs.getString(SMTP_PORT));
    Preconditions.checkNotNull(taskConfigs.getString(DEFAULT_ALERT_RECEIVER));
    Preconditions.checkNotNull(taskConfigs.getString(DEFAULT_ALERT_SENDER));
    Preconditions.checkNotNull(taskConfigs.getString(THIRDEYE_HOST));
    Preconditions.checkNotNull(taskConfigs.getString(PHANTON_JS_PATH));
    Preconditions.checkNotNull(taskConfigs.getString(ROOT_DIR));

    Long functionId = (Long) executionContext.getExecutionResult(ANOMALY_FUNCTION);
    DateTime start = (DateTime) executionContext.getExecutionResult(NOTIFICATION_START);
    DateTime end = (DateTime) executionContext.getExecutionResult(NOTIFICATION_END);


    AlertConfigDTO alertConfig = (AlertConfigDTO) executionContext.getExecutionResult(DefaultDetectionOnboardJob.ALERT_CONFIG);

    SmtpConfiguration smtpConfiguration = new SmtpConfiguration();
    smtpConfiguration.setSmtpHost(taskConfigs.getString(SMTP_HOST));
    smtpConfiguration.setSmtpPort(taskConfigs.getInt(SMTP_PORT));
    EmailResource emailResource = new EmailResource(smtpConfiguration, taskContext.getAlertFilterFactory(),
        taskConfigs.getString(DEFAULT_ALERT_SENDER), taskConfigs.getString(DEFAULT_ALERT_RECEIVER),
        taskConfigs.getString(THIRDEYE_HOST), taskConfigs.getString(PHANTON_JS_PATH), taskConfigs.getString(ROOT_DIR));
    String subject = new StringBuilder(
        "Replay results for " +
            DAORegistry.getInstance().getAnomalyFunctionDAO().findById(functionId).getFunctionName() +
            " is ready for review!").toString();
    emailResource.generateAndSendAlertForFunctions(start.getMillis(), end.getMillis(), String.valueOf(functionId),
        alertConfig.getFromAddress(), alertConfig.getRecipients(), subject, false, true,
        taskConfigs.getString(THIRDEYE_HOST), smtpConfiguration.getSmtpHost(), smtpConfiguration.getSmtpPort(),
        taskConfigs.getString(PHANTON_JS_PATH));
  }
}
