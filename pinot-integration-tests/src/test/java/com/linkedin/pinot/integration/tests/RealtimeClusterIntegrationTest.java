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
package com.linkedin.pinot.integration.tests;

import java.io.File;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import kafka.server.KafkaServerStartable;

import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.linkedin.pinot.common.data.Schema;
import com.linkedin.pinot.common.utils.KafkaStarterUtils;
import com.linkedin.pinot.common.utils.TarGzCompressionUtils;
import com.linkedin.pinot.util.TestUtils;


/**
 * Integration test that creates a Kafka broker, creates a Pinot cluster that consumes from Kafka and queries Pinot.
 *
 */
public class RealtimeClusterIntegrationTest extends BaseClusterIntegrationTest {
  private static final Logger LOGGER = LoggerFactory.getLogger(RealtimeClusterIntegrationTest.class);
  private final File _tmpDir = new File("/tmp/RealtimeClusterIntegrationTest");
  private static final String KAFKA_TOPIC = "realtime-integration-test";

  private static final int SEGMENT_COUNT = 12;
  public static final int QUERY_COUNT = 1000;
  private KafkaServerStartable kafkaStarter;

  protected void setUpTable(String tableName, String timeColumnName, String timeColumnType, String kafkaZkUrl,
      String kafkaTopic, File schemaFile, File avroFile) throws Exception {
    Schema schema = Schema.fromFile(schemaFile);
    addSchema(schemaFile, schema.getSchemaName());
    addRealtimeTable(tableName, timeColumnName, timeColumnType, 900, "Days", kafkaZkUrl, kafkaTopic, schema.getSchemaName(),
        null, null, avroFile);
  }

  @BeforeClass
  public void setUp() throws Exception {
    // Start ZK and Kafka
    startZk();
    kafkaStarter =
        KafkaStarterUtils.startServer(KafkaStarterUtils.DEFAULT_KAFKA_PORT, KafkaStarterUtils.DEFAULT_BROKER_ID,
            KafkaStarterUtils.DEFAULT_ZK_STR, KafkaStarterUtils.getDefaultKafkaConfiguration());

    // Create Kafka topic
    KafkaStarterUtils.createTopic(KAFKA_TOPIC, KafkaStarterUtils.DEFAULT_ZK_STR);

    // Start the Pinot cluster
    startController();
    startBroker();
    startServer();

    // Unpack data
    TarGzCompressionUtils.unTar(
        new File(TestUtils.getFileFromResourceUrl(RealtimeClusterIntegrationTest.class.getClassLoader().getResource(
            "On_Time_On_Time_Performance_2014_100k_subset_nonulls.tar.gz"))), _tmpDir);

    _tmpDir.mkdirs();
    final List<File> avroFiles = new ArrayList<File>(SEGMENT_COUNT);
    for (int segmentNumber = 1; segmentNumber <= SEGMENT_COUNT; ++segmentNumber) {
      avroFiles.add(new File(_tmpDir.getPath() + "/On_Time_On_Time_Performance_2014_" + segmentNumber + ".avro"));
    }

    File schemaFile =
        new File(OfflineClusterIntegrationTest.class.getClassLoader()
            .getResource("On_Time_On_Time_Performance_2014_100k_subset_nonulls.schema").getFile());

    // Create Pinot table
    setUpTable("mytable", "DaysSinceEpoch", "daysSinceEpoch", KafkaStarterUtils.DEFAULT_ZK_STR, KAFKA_TOPIC,
        schemaFile, avroFiles.get(0));

    // Load data into H2
    ExecutorService executor = Executors.newCachedThreadPool();
    Class.forName("org.h2.Driver");
    _connection = DriverManager.getConnection("jdbc:h2:mem:");
    executor.execute(new Runnable() {
      @Override
      public void run() {
        createH2SchemaAndInsertAvroFiles(avroFiles, _connection);
      }
    });

    // Initialize query generator
    executor.execute(new Runnable() {
      @Override
      public void run() {
        _queryGenerator = new QueryGenerator(avroFiles, "'mytable'", "mytable");
      }
    });

    // Push data into the Kafka topic
    executor.execute(new Runnable() {
      @Override
      public void run() {
        pushAvroIntoKafka(avroFiles, KafkaStarterUtils.DEFAULT_KAFKA_BROKER, KAFKA_TOPIC);
      }
    });

    // Wait for data push, query generator initialization and H2 load to complete
    executor.shutdown();
    executor.awaitTermination(10, TimeUnit.MINUTES);

    // Wait until the Pinot event count matches with the number of events in the Avro files
    int pinotRecordCount, h2RecordCount;
    long timeInTwoMinutes = System.currentTimeMillis() + 2 * 60 * 1000L;
    Statement statement = _connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
    do {
      Thread.sleep(5000L);

      // Run the query
      JSONObject response = postQuery("select count(*) from 'mytable'");
      JSONArray aggregationResultsArray = response.getJSONArray("aggregationResults");
      JSONObject firstAggregationResult = aggregationResultsArray.getJSONObject(0);
      String pinotValue = firstAggregationResult.getString("value");
      pinotRecordCount = Integer.parseInt(pinotValue);

      statement.execute("select count(*) from mytable");
      ResultSet rs = statement.getResultSet();
      rs.first();
      h2RecordCount = rs.getInt(1);
      rs.close();
      LOGGER.info("H2 record count: " + h2RecordCount + "\tPinot record count: " + pinotRecordCount);
      Assert.assertTrue(System.currentTimeMillis() < timeInTwoMinutes, "Failed to read all records within two minutes");
      TOTAL_DOCS = response.getLong("totalDocs");
    } while (h2RecordCount != pinotRecordCount);
  }

  @Override
  protected String getHelixClusterName() {
    return "RealtimeClusterIntegrationTest";
  }

  @Override
  @Test
  public void testHardcodedQuerySet() throws Exception {
    super.testHardcodedQuerySet();
  }

  @Override
  @Test
  public void testGeneratedQueries() throws Exception {
    super.testGeneratedQueries();
  }

  @Test
  public void testSingleQuery() throws Exception {
    String query;
    query = "select count(*) from 'mytable' where DaysSinceEpoch >= 16312";
    super.runQuery(query, Collections.singletonList(query.replace("'mytable'", "mytable")));
    query = "select count(*) from 'mytable' where DaysSinceEpoch < 16312";
    super.runQuery(query, Collections.singletonList(query.replace("'mytable'", "mytable")));
    query = "select count(*) from 'mytable' where DaysSinceEpoch <= 16312";
    super.runQuery(query, Collections.singletonList(query.replace("'mytable'", "mytable")));
    query = "select count(*) from 'mytable' where DaysSinceEpoch > 16312";
    super.runQuery(query, Collections.singletonList(query.replace("'mytable'", "mytable")));

  }

  @Override
  @Test
  public void testMultipleQueries() throws Exception {
    super.testMultipleQueries();
  }

  @AfterClass
  public void tearDown() throws Exception {
    stopBroker();
    stopController();
    stopServer();
    KafkaStarterUtils.stopServer(kafkaStarter);
    try {
      stopZk();
    } catch (Exception e) {
      // Swallow ZK Exceptions.
    }
    FileUtils.deleteDirectory(_tmpDir);
  }

  @Override
  protected int getGeneratedQueryCount() {
    return QUERY_COUNT;
  }
}
