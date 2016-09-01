package com.linkedin.thirdeye.datalayer.dao;

import com.linkedin.thirdeye.datalayer.entity.Job;
import com.linkedin.thirdeye.db.entity.AnomalyFeedback;

public class JobDAO extends AbstractBaseDAO<Job> {

  public JobDAO() {
    super(Job.class);
  }
}
