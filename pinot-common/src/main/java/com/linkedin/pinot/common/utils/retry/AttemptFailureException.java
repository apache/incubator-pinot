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
package com.linkedin.pinot.common.utils.retry;

import java.util.concurrent.Callable;


/**
 * The <code>AttemptFailureException</code> indicates that the {@link RetryPolicy#attempt(Callable)} failed because of
 * either operation throwing an exception or running out of attempts.
 */
public class AttemptFailureException extends Exception {

  public AttemptFailureException(String message) {
    super(message);
  }

  public AttemptFailureException(Throwable cause) {
    super(cause);
  }
}
