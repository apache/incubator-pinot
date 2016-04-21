/**
 * Copyright (C) 2014-2015 LinkedIn Corp. (pinot-core@linkedin.com)
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
package com.linkedin.pinot.common.utils.time;

import java.util.concurrent.TimeUnit;

import org.joda.time.DateTime;

import com.linkedin.pinot.common.data.TimeGranularitySpec;

public class NoOpTimeConverter implements TimeConverter {
  TimeGranularitySpec incomingTimeGranularitySpec;
  TimeGranularitySpec outgoingTimeGranularitySpec;

  public NoOpTimeConverter(TimeGranularitySpec incoming) {
    this.incomingTimeGranularitySpec = incoming;
    this.outgoingTimeGranularitySpec = incoming;
  }

  @Override
  public Object convert(Object incoming) {
    return incoming;
  }

  @Override
  public DateTime getDateTimeFrom(Object o) {
    if (o == null) {
      return new DateTime(0);
    }
    long incoming = -1;
    if (o instanceof Number) {
      incoming = ((Number) o).longValue();
    } else {
      incoming = Long.valueOf(o.toString());
    }
    switch (incomingTimeGranularitySpec.getTimeType()) {
    case HOURS:
      long millisFromHours = TimeUnit.HOURS.toMillis(incoming);
      return new DateTime(millisFromHours);
    case DAYS:
      long millisFromDays = TimeUnit.DAYS.toMillis(incoming);
      return new DateTime(millisFromDays);
    case MILLISECONDS:
      return new DateTime(incoming);
    case MINUTES:
      long millisFromMinutes = TimeUnit.MINUTES.toMillis(incoming);
      return new DateTime(millisFromMinutes);
    default:
      return null;
    }
  }

  @Override
  public Object toValueFromDateTime(DateTime dt) {
    return outgoingTimeGranularitySpec.getTimeType().convert(dt.getMillis(), TimeUnit.MILLISECONDS);
  }
}
