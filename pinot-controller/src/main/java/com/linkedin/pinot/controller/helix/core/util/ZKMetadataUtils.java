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
package com.linkedin.pinot.controller.helix.core.util;

import java.util.concurrent.TimeUnit;

import org.joda.time.Duration;

import com.linkedin.pinot.common.metadata.segment.OfflineSegmentZKMetadata;
import com.linkedin.pinot.common.segment.SegmentMetadata;
import com.linkedin.pinot.common.utils.CommonConstants.Segment.SegmentType;


public class ZKMetadataUtils {

  public static OfflineSegmentZKMetadata updateSegmentMetadata(OfflineSegmentZKMetadata offlineSegmentZKMetadata, SegmentMetadata segmentMetadata) {
    offlineSegmentZKMetadata.setSegmentName(segmentMetadata.getName());
    offlineSegmentZKMetadata.setTableName(segmentMetadata.getTableName());
    offlineSegmentZKMetadata.setIndexVersion(segmentMetadata.getVersion());
    offlineSegmentZKMetadata.setSegmentType(SegmentType.OFFLINE);

    offlineSegmentZKMetadata.setTimeUnit(extractTimeUnitFromDuration(segmentMetadata.getTimeGranularity()));
    if (segmentMetadata.getTimeInterval() == null) {
      offlineSegmentZKMetadata.setStartTime(-1);
      offlineSegmentZKMetadata.setEndTime(-1);
    } else {
      offlineSegmentZKMetadata.setStartTime(
          offlineSegmentZKMetadata.getTimeUnit().convert(segmentMetadata.getTimeInterval().getStartMillis(), TimeUnit.MILLISECONDS));
      offlineSegmentZKMetadata.setEndTime(
          offlineSegmentZKMetadata.getTimeUnit().convert(segmentMetadata.getTimeInterval().getEndMillis(), TimeUnit.MILLISECONDS));
    }
    offlineSegmentZKMetadata.setTotalRawDocs(segmentMetadata.getTotalRawDocs());
    offlineSegmentZKMetadata.setCreationTime(segmentMetadata.getIndexCreationTime());
    offlineSegmentZKMetadata.setCrc(Long.parseLong(segmentMetadata.getCrc()));
    return offlineSegmentZKMetadata;
  }

  private static TimeUnit extractTimeUnitFromDuration(Duration timeGranularity) {
    if (timeGranularity == null) {
      return null;
    }
    long timeUnitInMills = timeGranularity.getMillis();
    for (TimeUnit timeUnit : TimeUnit.values()) {
      if (timeUnit.toMillis(1) == timeUnitInMills) {
        return timeUnit;
      }
    }
    return null;
  }
}
