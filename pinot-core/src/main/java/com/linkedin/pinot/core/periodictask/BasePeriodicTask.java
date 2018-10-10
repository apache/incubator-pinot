/**
 * Copyright (C) 2014-2018 LinkedIn Corp. (pinot-core@linkedin.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.linkedin.pinot.core.periodictask;


public abstract class BasePeriodicTask implements PeriodicTask {
  private final String _taskName;
  private long _intervalInSeconds;
  private long _initialDelayInSeconds;

  private static final long DEFAULT_INITIAL_DELAY_IN_SECOND = 120L;

  public BasePeriodicTask(String taskName, long runFrequencyInSeconds) {
    this(taskName, runFrequencyInSeconds, DEFAULT_INITIAL_DELAY_IN_SECOND);
  }

  public BasePeriodicTask(String taskName, long runFrequencyInSeconds, long initialDelaySeconds) {
    _taskName = taskName;
    _intervalInSeconds = runFrequencyInSeconds;
    _initialDelayInSeconds = initialDelaySeconds;
  }

  public abstract void run();

  @Override
  public void init() {
  }

  @Override
  public long getIntervalInSeconds() {
    return _intervalInSeconds;
  }

  @Override
  public long getInitialDelayInSeconds() {
    return _initialDelayInSeconds;
  }

  @Override
  public String getTaskName() {
    return _taskName;
  }
}
