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
package com.linkedin.pinot.integration.tests;

import com.linkedin.pinot.common.metrics.ServerMetrics;
import com.linkedin.pinot.core.data.GenericRow;
import com.linkedin.pinot.core.realtime.impl.kafka.KafkaStreamLevelConsumer;
import com.linkedin.pinot.core.realtime.stream.StreamLevelConsumer;
import com.linkedin.pinot.core.realtime.StreamProviderConfig;
import java.util.Random;
import org.testng.annotations.BeforeClass;


/**
 * Integration test that simulates a flaky Kafka consumer.
 */
public class FlakyConsumerRealtimeClusterIntegrationTest extends RealtimeClusterIntegrationTest {
  private static final Class<? extends StreamLevelConsumer> ORIGINAL_STREAM_LEVEL_CONSUMER =
      KafkaStreamLevelConsumer.class;

  @BeforeClass
  @Override
  public void setUp() throws Exception {
    super.setUp();
  }

  public static class FlakyStreamLevelConsumer implements StreamLevelConsumer {
    private StreamLevelConsumer _streamLevelConsumer;
    private Random _random = new Random();

    public FlakyStreamLevelConsumer() throws Exception {
      _streamLevelConsumer = ORIGINAL_STREAM_LEVEL_CONSUMER.newInstance();
    }

    @Override
    public void init(StreamProviderConfig streamProviderConfig, String tableName, ServerMetrics serverMetrics)
        throws Exception {
      _streamLevelConsumer.init(streamProviderConfig, tableName, serverMetrics);
    }

    @Override
    public void start()
        throws Exception {
      _streamLevelConsumer.start();
    }

    @Override
    public void setOffset(long offset) {
      _streamLevelConsumer.setOffset(offset);
    }

    @Override
    public GenericRow next(GenericRow destination) {
      // Return a null row every ~1/1000 rows and an exception every ~1/1000 rows
      int randomValue = _random.nextInt(1000);

      if (randomValue == 0) {
        return null;
      } else if (randomValue == 1) {
        throw new RuntimeException("Flaky stream level consumer exception");
      } else {
        return _streamLevelConsumer.next(destination);
      }
    }

    @Override
    public GenericRow next(long offset) {
      return _streamLevelConsumer.next(offset);
    }

    @Override
    public long currentOffset() {
      return _streamLevelConsumer.currentOffset();
    }

    @Override
    public void commit() {
      // Fail to commit 50% of the time
      boolean failToCommit = _random.nextBoolean();

      if (failToCommit) {
        throw new RuntimeException("Flaky stream level consumer exception");
      } else {
        _streamLevelConsumer.commit();
      }
    }

    @Override
    public void commit(long offset) {
      _streamLevelConsumer.commit(offset);
    }

    @Override
    public void shutdown()
        throws Exception {
      _streamLevelConsumer.shutdown();
    }
  }
}
