package com.linkedin.thirdeye.anomaly.classification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.linkedin.thirdeye.anomaly.job.JobConstants;
import com.linkedin.thirdeye.anomaly.job.JobRunner;
import com.linkedin.thirdeye.anomaly.task.TaskConstants;
import com.linkedin.thirdeye.anomaly.task.TaskGenerator;
import com.linkedin.thirdeye.client.DAORegistry;
import com.linkedin.thirdeye.datalayer.bao.JobManager;
import com.linkedin.thirdeye.datalayer.dto.JobDTO;
import com.linkedin.thirdeye.datalayer.dto.TaskDTO;
import java.util.ArrayList;
import java.util.List;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.linkedin.thirdeye.dashboard.resources.EntityManagerResource.OBJECT_MAPPER;

public class ClassificationJobRunner implements JobRunner {
  private static final Logger LOG = LoggerFactory.getLogger(ClassificationJobRunner.class);
  private static final DAORegistry DAO_REGISTRY = DAORegistry.getInstance();
  private static final JobManager jobDAO = DAO_REGISTRY.getJobDAO();
  private ClassificationJobContext jobContext;
  private TaskGenerator taskGenerator = new TaskGenerator();

  public ClassificationJobRunner(ClassificationJobContext classificationJobContext) {
    this.jobContext = classificationJobContext;
  }

  @Override
  public Long createJob() {
    try {
      JobDTO classificationJobSpec = new JobDTO();
      String jobName = createJobName(jobContext);
      classificationJobSpec.setJobName(jobName);
      classificationJobSpec.setWindowStartTime(jobContext.getWindowStartTime());
      classificationJobSpec.setWindowEndTime(jobContext.getWindowEndTime());
      classificationJobSpec.setScheduleStartTime(System.currentTimeMillis());
      classificationJobSpec.setStatus(JobConstants.JobStatus.SCHEDULED);
      Long jobExecutionId = jobDAO.save(classificationJobSpec);
      jobContext.setJobName(jobName);
      jobContext.setJobExecutionId(jobExecutionId);
      LOG.info("Created anomalyJobSpec {} with jobExecutionId {}", classificationJobSpec, jobExecutionId);
      return jobExecutionId;
    } catch (Exception e) {
      LOG.error("Exception in creating detection job", e);
    }
    return null;
  }

  @Override
  public List<Long> createTasks() {
    List<Long> taskIds = new ArrayList<>();

    try {
      LOG.info("Creating classification tasks");
      List<ClassificationTaskInfo> taskInfos = taskGenerator
          .createClassificationTasks(jobContext, new DateTime(jobContext.getWindowStartTime()),
              new DateTime(jobContext.getWindowEndTime()));
      LOG.info("Classification tasks {}", taskInfos);
      for (ClassificationTaskInfo taskInfo : taskInfos) {
        String taskInfoJson = null;
        try {
          taskInfoJson = OBJECT_MAPPER.writeValueAsString(taskInfo);
        } catch (JsonProcessingException e) {
          LOG.error("Exception when converting ClassificationTaskInfo {} to jsonString", taskInfo, e);
        }

        TaskDTO taskSpec = new TaskDTO();
        taskSpec.setTaskType(TaskConstants.TaskType.CLASSIFICATION);
        taskSpec.setJobName(jobContext.getJobName());
        taskSpec.setStatus(TaskConstants.TaskStatus.WAITING);
        taskSpec.setStartTime(System.currentTimeMillis());
        taskSpec.setTaskInfo(taskInfoJson);
        taskSpec.setJobId(jobContext.getJobExecutionId());
        long taskId = DAO_REGISTRY.getTaskDAO().save(taskSpec);

        taskIds.add(taskId);
        LOG.info("Created classification task {} with taskId {}", taskSpec, taskId);
      }
    } catch (Exception e) {
      LOG.error("Exception in creating classification tasks", e);
    }

    return taskIds;
  }

  @Override
  public void run() {
    Long jobExecutionId = createJob();
    if (jobExecutionId != null) {
      List<Long> taskIds = createTasks();
    }
  }

  private static String createJobName(ClassificationJobContext jobContext) {
    long configId = jobContext.getConfigDTO().getId();
    String configName = jobContext.getConfigDTO().getName();
    long startTimes = jobContext.getWindowStartTime();
    long endTimes = jobContext.getWindowEndTime();

    return String.format("%s-%s-%s-%s", configId, configName, startTimes, endTimes);
  }
}
