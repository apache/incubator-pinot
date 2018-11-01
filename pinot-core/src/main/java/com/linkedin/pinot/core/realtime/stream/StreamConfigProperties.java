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
package com.linkedin.pinot.core.realtime.stream;

import com.google.common.base.Joiner;


public class StreamConfigProperties {
  public static final String DOT_SEPARATOR = ".";
  public static final String STREAM_PREFIX = "stream";
  // TODO: this can be removed, check all properties before doing so
  public static final String LLC_SUFFIX = ".llc";

  /**
   * Generic properties
   */
  public static final String STREAM_TYPE = "streamType";
  public static final String STREAM_TOPIC_NAME = "topic.name";
  public static final String STREAM_CONSUMER_TYPES = "consumer.type";
  public static final String STREAM_CONSUMER_FACTORY_CLASS = "consumer.factory.class.name";
  public static final String STREAM_CONSUMER_OFFSET_CRITERIA = "consumer.prop.auto.offset.reset";
  public static final String STREAM_FETCH_TIMEOUT_MILLIS = "fetch.timeout.millis";
  public static final String STREAM_CONNECTION_TIMEOUT_MILLIS = "connection.timeout.millis";
  public static final String STREAM_DECODER_CLASS = "decoder.class.name";
  public static final String DECODER_PROPS_PREFIX = "decoder.prop";

  /**
   * Time threshold that will keep the realtime segment open for before we complete the segment
   */
  public static final String SEGMENT_FLUSH_THRESHOLD_TIME = "realtime.segment.flush.threshold.time";

  /**
   * Row count flush threshold for realtime segments. This behaves in a similar way for HLC and LLC. For HLC,
   * since there is only one consumer per server, this size is used as the size of the consumption buffer and
   * determines after how many rows we flush to disk. For example, if this threshold is set to two million rows,
   * then a high level consumer would have a buffer size of two million.
   *
   * For LLC, this size is divided across all the segments assigned to a given server and is set on a per segment
   * basis. Assuming a low level consumer server is assigned four stream partitions to consume from and a flush
   * size of two million, then each consuming segment would have a flush size of five hundred thousand rows, for a
   * total of two million rows in memory.
   *
   * Keep in mind that this NOT a hard threshold, as other tables can also be assigned to this server, and that in
   * certain conditions (eg. if the number of servers, replicas of partitions changes) where partition
   * to server assignment changes, it's possible to end up with more (or less) than this number of rows in memory.
   *
   * If this value is set to 0, then the consumers adjust the number of rows consumed by a partition such that
   * the size of the completed segment is the desired size (see REALTIME_DESIRED_SEGMENT_SIZE), unless
   * REALTIME_SEGMENT_FLUSH_TIME is reached first)
   */
  public static final String SEGMENT_FLUSH_THRESHOLD_ROWS = "realtime.segment.flush.threshold.size";

  /*
   * The desired size of a completed realtime segment.
   * This config is used only if REALTIME_SEGMENT_FLUSH_SIZE is set
   * to 0. Default value of REALTIME_SEGMENT_FLUSH_SIZE is "200M". Values are parsed using DataSize class.
   *
   * The value for this configuration should be chosen based on the amount of memory available on consuming
   * machines, the number of completed segments that are expected to be resident on the machine and the amount
   * of memory used by consuming machines. In other words:
   *
   *    numPartitionsInMachine * (consumingPartitionMemory + numPartitionsRetained * REALTIME_DESIRED_SEGMENT_SIZE)
   *
   * must be less than or equal to the total memory available to store pinot data.
   *
   * Note that consumingPartitionMemory will vary depending on the rows that are consumed.
   *
   * Not included here is any heap memory used (currently inverted index uses heap memory for consuming partitions).
   */
  public static final String SEGMENT_FLUSH_DESIRED_SIZE = "realtime.segment.flush.desired.size";

  // Time threshold that controller will wait for the segment to be built by the server
  public static final String SEGMENT_COMMIT_TIMEOUT_SECONDS = "realtime.segment.commit.timeoutSeconds";


  /**
   * Helper method to create a stream specific property
   * @param streamType
   * @param property
   * @return
   */
  public static String constructStreamProperty(String streamType, String property) {
    return Joiner.on(DOT_SEPARATOR).join(STREAM_PREFIX, streamType, property);
  }

  public static String getPropertySuffix(String incoming, String propertyPrefix) {
    return incoming.split(propertyPrefix + ".")[1];
  }

}

