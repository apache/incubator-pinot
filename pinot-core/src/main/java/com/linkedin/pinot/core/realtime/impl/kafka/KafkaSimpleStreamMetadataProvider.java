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
package com.linkedin.pinot.core.realtime.impl.kafka;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.Uninterruptibles;
import com.linkedin.pinot.core.realtime.stream.StreamMetadataProvider;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import kafka.api.PartitionOffsetRequestInfo;
import kafka.common.TopicAndPartition;
import kafka.javaapi.OffsetRequest;
import kafka.javaapi.OffsetResponse;
import kafka.javaapi.TopicMetadata;
import kafka.javaapi.TopicMetadataRequest;
import kafka.javaapi.TopicMetadataResponse;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.protocol.Errors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Implementation of a stream metadata provider for a kafka simple stream
 */
public class KafkaSimpleStreamMetadataProvider extends KafkaConnectionHandler implements StreamMetadataProvider {
  private static final Logger LOGGER = LoggerFactory.getLogger(KafkaSimpleStreamMetadataProvider.class);

  public KafkaSimpleStreamMetadataProvider(KafkaSimpleConsumerFactory simpleConsumerFactory, String bootstrapNodes,
      String clientId, String topic, int partition, long connectTimeoutMillis) {
    super(simpleConsumerFactory, bootstrapNodes, clientId, topic, partition, connectTimeoutMillis);
  }

  public KafkaSimpleStreamMetadataProvider(KafkaSimpleConsumerFactory simpleConsumerFactory, String bootstrapNodes,
      String clientId, String topic, long connectTimeoutMillis) {
    super(simpleConsumerFactory, bootstrapNodes, clientId, topic, connectTimeoutMillis);
  }

  // TODO: this will replace metadata related methods called from SimpleConsumerWrapper
  // TODO: make sure this code is equivalent to the methods in SimpleConsumerWrapper before starting to use this
  @Override
  public synchronized int fetchPartitionCount(String topic, long timeoutMillis) {
    int unknownTopicReplyCount = 0;
    final int MAX_UNKNOWN_TOPIC_REPLY_COUNT = 10;
    int kafkaErrorCount = 0;
    final int MAX_KAFKA_ERROR_COUNT = 10;

    final long endTime = System.currentTimeMillis() + timeoutMillis;

    while (System.currentTimeMillis() < endTime) {
      // Try to get into a state where we're connected to Kafka
      while (!_currentState.isConnectedToKafkaBroker() && System.currentTimeMillis() < endTime) {
        _currentState.process();
      }

      if (endTime <= System.currentTimeMillis() && !_currentState.isConnectedToKafkaBroker()) {
        throw new TimeoutException(
            "Failed to get the partition count for topic " + topic + " within " + timeoutMillis + " ms");
      }

      // Send the metadata request to Kafka
      TopicMetadataResponse topicMetadataResponse = null;
      try {
        topicMetadataResponse = _simpleConsumer.send(new TopicMetadataRequest(Collections.singletonList(topic)));
      } catch (Exception e) {
        _currentState.handleConsumerException(e);
        continue;
      }

      final TopicMetadata topicMetadata = topicMetadataResponse.topicsMetadata().get(0);
      final short errorCode = topicMetadata.errorCode();

      if (errorCode == Errors.NONE.code()) {
        return topicMetadata.partitionsMetadata().size();
      } else if (errorCode == Errors.LEADER_NOT_AVAILABLE.code()) {
        // If there is no leader, it'll take some time for a new leader to be elected, wait 100 ms before retrying
        Uninterruptibles.sleepUninterruptibly(100, TimeUnit.MILLISECONDS);
      } else if (errorCode == Errors.INVALID_TOPIC_EXCEPTION.code()) {
        throw new RuntimeException("Invalid topic name " + topic);
      } else if (errorCode == Errors.UNKNOWN_TOPIC_OR_PARTITION.code()) {
        if (MAX_UNKNOWN_TOPIC_REPLY_COUNT < unknownTopicReplyCount) {
          throw new RuntimeException("Topic " + topic + " does not exist");
        } else {
          // Kafka topic creation can sometimes take some time, so we'll retry after a little bit
          unknownTopicReplyCount++;
          Uninterruptibles.sleepUninterruptibly(100, TimeUnit.MILLISECONDS);
        }
      } else {
        // Retry after a short delay
        kafkaErrorCount++;

        if (MAX_KAFKA_ERROR_COUNT < kafkaErrorCount) {
          throw exceptionForKafkaErrorCode(errorCode);
        }

        Uninterruptibles.sleepUninterruptibly(100, TimeUnit.MILLISECONDS);
      }
    }

    throw new TimeoutException();
  }

  /**
   * Fetches the numeric Kafka offset for this partition for a symbolic name ("largest" or "smallest").
   *
   * @param requestedOffset Either "largest" or "smallest"
   * @param timeoutMillis Timeout in milliseconds
   * @throws java.util.concurrent.TimeoutException If the operation could not be completed within {@code timeoutMillis}
   * milliseconds
   * @return An offset
   */
  @Override
  public synchronized long fetchPartitionOffset(String requestedOffset, int timeoutMillis)
      throws java.util.concurrent.TimeoutException {
    Preconditions.checkState(_isPartitionMetadata,
        "Cannot fetch messages from a non partition specific SimpleConsumerWrapper");
    Preconditions.checkNotNull(requestedOffset);

    final long offsetRequestTime;
    if (requestedOffset.equalsIgnoreCase("largest")) {
      offsetRequestTime = kafka.api.OffsetRequest.LatestTime();
    } else if (requestedOffset.equalsIgnoreCase("smallest")) {
      offsetRequestTime = kafka.api.OffsetRequest.EarliestTime();
    } else if (requestedOffset.equalsIgnoreCase("testDummy")) {
      return -1L;
    } else {
      throw new IllegalArgumentException("Unknown initial offset value " + requestedOffset);
    }

    int kafkaErrorCount = 0;
    final int MAX_KAFKA_ERROR_COUNT = 10;

    final long endTime = System.currentTimeMillis() + timeoutMillis;

    while (System.currentTimeMillis() < endTime) {
      // Try to get into a state where we're connected to Kafka
      while (_currentState.getStateValue() != KafkaConnectionHandler.ConsumerState.CONNECTED_TO_PARTITION_LEADER
          && System.currentTimeMillis() < endTime) {
        _currentState.process();
      }

      if (_currentState.getStateValue() != KafkaConnectionHandler.ConsumerState.CONNECTED_TO_PARTITION_LEADER
          && endTime <= System.currentTimeMillis()) {
        throw new TimeoutException();
      }

      // Send the offset request to Kafka
      OffsetRequest request = new OffsetRequest(Collections.singletonMap(new TopicAndPartition(_topic, _partition),
          new PartitionOffsetRequestInfo(offsetRequestTime, 1)), kafka.api.OffsetRequest.CurrentVersion(), _clientId);
      OffsetResponse offsetResponse;
      try {
        offsetResponse = _simpleConsumer.getOffsetsBefore(request);
      } catch (Exception e) {
        _currentState.handleConsumerException(e);
        continue;
      }

      final short errorCode = offsetResponse.errorCode(_topic, _partition);

      if (errorCode == Errors.NONE.code()) {
        long offset = offsetResponse.offsets(_topic, _partition)[0];
        if (offset == 0L) {
          LOGGER.warn("Fetched offset of 0 for topic {} and partition {}, is this a newly created topic?", _topic,
              _partition);
        }
        return offset;
      } else if (errorCode == Errors.LEADER_NOT_AVAILABLE.code()) {
        // If there is no leader, it'll take some time for a new leader to be elected, wait 100 ms before retrying
        Uninterruptibles.sleepUninterruptibly(100, TimeUnit.MILLISECONDS);
      } else {
        // Retry after a short delay
        kafkaErrorCount++;

        if (MAX_KAFKA_ERROR_COUNT < kafkaErrorCount) {
          throw exceptionForKafkaErrorCode(errorCode);
        }

        Uninterruptibles.sleepUninterruptibly(100, TimeUnit.MILLISECONDS);
      }
    }

    throw new TimeoutException();
  }

  @Override
  public void close() throws IOException {
    super.close();
  }
}
