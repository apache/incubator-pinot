package com.linkedin.thirdeye.anomaly.task;

import com.linkedin.thirdeye.db.dao.AnomalyJobDAO;
import com.linkedin.thirdeye.db.dao.AnomalyMergedResultDAO;
import com.linkedin.thirdeye.db.dao.AnomalyResultDAO;
import com.linkedin.thirdeye.db.dao.AnomalyTaskDAO;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.persistence.OptimisticLockException;
import javax.persistence.RollbackException;

import org.hibernate.StaleObjectStateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linkedin.thirdeye.anomaly.ThirdEyeAnomalyConfiguration;
import com.linkedin.thirdeye.anomaly.task.TaskConstants.TaskStatus;
import com.linkedin.thirdeye.anomaly.task.TaskConstants.TaskType;
import com.linkedin.thirdeye.db.entity.AnomalyTaskSpec;
import com.linkedin.thirdeye.detector.function.AnomalyFunctionFactory;

public class TaskDriver {

  private static final Logger LOG = LoggerFactory.getLogger(TaskDriver.class);

  private ExecutorService taskExecutorService;

  private final AnomalyTaskDAO anomalyTaskDAO;
  private TaskContext taskContext;
  private long workerId;

  private volatile boolean shutdown = false;
  private static final int MAX_PARALLEL_TASK = 3;
  private static final int NO_TASK_IDLE_DELAY_MILLIS = 15_000; // 15 seconds
  private static final int TASK_FAILURE_DELAY_MILLIS = 5 * 60_000; // 5 minutes
  private static final int TASK_FETCH_SIZE = 10;
  private static final Random RANDOM = new Random();

  public TaskDriver(ThirdEyeAnomalyConfiguration thirdEyeAnomalyConfiguration, AnomalyJobDAO anomalyJobDAO,
      AnomalyTaskDAO anomalyTaskDAO, AnomalyResultDAO anomalyResultDAO, AnomalyMergedResultDAO mergedResultDAO,
      AnomalyFunctionFactory anomalyFunctionFactory) {
    this.workerId = thirdEyeAnomalyConfiguration.getId();
    this.anomalyTaskDAO = anomalyTaskDAO;
    taskExecutorService = Executors.newFixedThreadPool(MAX_PARALLEL_TASK);

    taskContext = new TaskContext();
    taskContext.setAnomalyJobDAO(anomalyJobDAO);
    taskContext.setAnomalyTaskDAO(anomalyTaskDAO);
    taskContext.setResultDAO(anomalyResultDAO);
    taskContext.setAnomalyFunctionFactory(anomalyFunctionFactory);
    taskContext.setMergedResultDAO(mergedResultDAO);
    taskContext.setThirdEyeAnomalyConfiguration(thirdEyeAnomalyConfiguration);
  }

  public void start() throws Exception {
    List<Callable<Void>> callables = new ArrayList<>();
    for (int i = 0; i < MAX_PARALLEL_TASK; i++) {
      Callable<Void> callable = () -> {
        while (!shutdown) {
          LOG.info(Thread.currentThread().getId() + " : Finding next task to execute for threadId:{}",
              Thread.currentThread().getId());
          try {
            // select a task to execute, and update it to RUNNING
            AnomalyTaskSpec anomalyTaskSpec = selectAndUpdate();
            LOG.info(Thread.currentThread().getId() + " : Executing task: {} {}", anomalyTaskSpec.getId(),
                anomalyTaskSpec.getTaskInfo());

            // execute the selected task
            TaskType taskType = anomalyTaskSpec.getTaskType();
            TaskRunner taskRunner = TaskRunnerFactory.getTaskRunnerFromTaskType(taskType);
            TaskInfo taskInfo = TaskInfoFactory.getTaskInfoFromTaskType(taskType, anomalyTaskSpec.getTaskInfo());
            LOG.info(Thread.currentThread().getId() + " : Task Info {}", taskInfo);
            List<TaskResult> taskResults = taskRunner.execute(taskInfo, taskContext);
            LOG.info(Thread.currentThread().getId() + " : DONE Executing task: {}", anomalyTaskSpec.getId());

            // update status to COMPLETED
            updateStatusAndTaskEndTime(anomalyTaskSpec.getId(), TaskStatus.RUNNING, TaskStatus.COMPLETED);
          } catch (Exception e) {
            LOG.error("Exception in electing and executing task", e);
          }
        }
        return null;
      };
      callables.add(callable);
    }
    for (Callable<Void> callable : callables) {
      taskExecutorService.submit(callable);
    }
    LOG.info(Thread.currentThread().getId() + " : Started task driver");
  }

  public void stop() {
    taskExecutorService.shutdown();
  }

  private AnomalyTaskSpec selectAndUpdate() throws Exception {
    LOG.info(Thread.currentThread().getId() + " : Starting selectAndUpdate {}", Thread.currentThread().getId());
    AnomalyTaskSpec acquiredTask = null;
    LOG.info(Thread.currentThread().getId() + " : Trying to find a task to execute");
    do {
      List<AnomalyTaskSpec> anomalyTasks = new ArrayList<>();
      try {
        anomalyTasks = anomalyTaskDAO.findByStatusOrderByCreateTimeAsc(TaskStatus.WAITING, TASK_FETCH_SIZE);
      } catch (Exception e) {
        LOG.error("Exception found in fetching new tasks, sleeping for few seconds", e);
        // TODO : Add better wait / clear call
        Thread.sleep(TASK_FAILURE_DELAY_MILLIS);
      }
      if (anomalyTasks.size() > 0) {
        LOG.info(Thread.currentThread().getId() + " : Found {} tasks in waiting state", anomalyTasks.size());
      } else {
        // sleep for few seconds if not tasks found - avoid cpu thrashing
        // also add some extra random number of milli seconds to allow threads to start at different times
        // TODO : Add better wait / clear call
        int delay = NO_TASK_IDLE_DELAY_MILLIS + RANDOM.nextInt(NO_TASK_IDLE_DELAY_MILLIS);
        LOG.debug("No tasks found to execute, sleeping for {} MS", delay);
        Thread.sleep(delay);
      }

      for (AnomalyTaskSpec anomalyTaskSpec : anomalyTasks) {
        LOG.info(Thread.currentThread().getId() + " : Trying to acquire task : {}", anomalyTaskSpec.getId());

        boolean success = false;
        try {
          success = anomalyTaskDAO.updateStatusAndWorkerId(workerId, anomalyTaskSpec.getId(),TaskStatus.WAITING,
              TaskStatus.RUNNING);
          LOG.info(Thread.currentThread().getId() + " : Task acquired success: {}", success);
        } catch (OptimisticLockException | RollbackException | StaleObjectStateException e) {
          LOG.warn("[{}] in acquiring task by threadId {} and workerId {}", e.getClass().getSimpleName(),
              Thread.currentThread().getId(), workerId);
        }
        if (success) {
          acquiredTask = anomalyTaskSpec;
          break;
        }
      }
    } while (acquiredTask == null);
    LOG.info(Thread.currentThread().getId() + " : Acquired task ======" + acquiredTask);
    return acquiredTask;
  }

  private void updateStatusAndTaskEndTime(long taskId, TaskStatus oldStatus, TaskStatus newStatus) throws Exception {
    LOG.info("{} : Starting updateStatus {}", Thread.currentThread().getId(), Thread.currentThread().getId());

    try {
      anomalyTaskDAO.updateStatusAndTaskEndTime(taskId, oldStatus, newStatus, System.currentTimeMillis());
      LOG.info("{} : updated status {}", Thread.currentThread().getId(), newStatus);
    } catch (Exception e) {
      LOG.error("Exception in updating status and task end time", e);
    }
  }

}
