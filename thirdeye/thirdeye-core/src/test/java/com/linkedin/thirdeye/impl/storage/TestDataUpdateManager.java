package com.linkedin.thirdeye.impl.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkedin.thirdeye.api.*;
import com.linkedin.thirdeye.impl.*;

import junit.framework.Assert;

import org.apache.commons.io.FileUtils;
import org.joda.time.DateTime;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class TestDataUpdateManager {
  private File rootDir;
  private DataUpdateManager dataUpdateManager;
  private String collection;
  private String schedule;
  private String treeId;
  private DateTime minTime;
  private DateTime maxTime;
  private StarTreeConfig baseConfig;
  private StarTreeConfig config;
  private MetricSchema metricSchema;

  @BeforeClass
  public void beforeClass() throws Exception {
    rootDir = new File(System.getProperty("java.io.tmpdir"),
        TestDataUpdateManager.class.getCanonicalName());
    dataUpdateManager = new DataUpdateManager(rootDir, false);
    schedule = "TEST";
    treeId = UUID.randomUUID().toString();
    minTime = new DateTime(0);
    maxTime = new DateTime(1000);
    baseConfig = StarTreeConfig.decode(ClassLoader.getSystemResourceAsStream("sample-config.yml"));
    collection = baseConfig.getCollection();

    SplitSpec split = new SplitSpec(5, baseConfig.getSplit().getOrder());

    // TODO: We should really just have setters in config - immutability doesn't really get us
    // anything here
    config = new StarTreeConfig(baseConfig.getCollection(),
        StarTreeRecordStoreFactoryLogBufferImpl.class.getCanonicalName(), new Properties(),
        baseConfig.getAnomalyDetectionFunctionClass(),
        baseConfig.getAnomalyDetectionFunctionConfig(), baseConfig.getAnomalyHandlerClass(),
        baseConfig.getAnomalyHandlerConfig(), baseConfig.getAnomalyDetectionMode(),
        baseConfig.getDimensions(), baseConfig.getMetrics(), baseConfig.getTime(),
        baseConfig.getJoinSpec(), baseConfig.getRollup(), baseConfig.getTopKRollup(), split, false);

    metricSchema = MetricSchema.fromMetricSpecs(config.getMetrics());
  }

  @AfterClass
  public void afterClass() throws Exception {
    if (rootDir.exists()) {
      FileUtils.forceDelete(rootDir);
    }
  }

  @Test(enabled = false)
  public void testUpdateData() throws Exception {
    // TODO (requires using an actual data archive, so we need to generate that via some test
    // framework)
  }

  @Test
  public void testDeleteData() throws Exception {
    // Create some collection data dir
    File collectionDir = new File(rootDir, collection);
    File dataDir =
        new File(collectionDir, StorageUtils.getDataDirName(treeId, schedule, minTime, maxTime));
    FileUtils.forceMkdir(dataDir);
    Assert.assertTrue(dataDir.exists());

    // Delete it
    dataUpdateManager.deleteData(collection, schedule, minTime, maxTime);
    Assert.assertFalse(dataDir.exists());
  }

  @Test
  public void testDeleteCollection() throws Exception {
    // Create some collection dir
    File collectionDir = new File(rootDir, collection);
    FileUtils.forceMkdir(collectionDir);
    Assert.assertTrue(collectionDir.exists());

    // Delete it
    dataUpdateManager.deleteCollection(collection);
    Assert.assertFalse(collectionDir.exists());
  }

  @Test
  public void testPersistTree() throws Exception {
    // Write base config
    File collectionDir = new File(rootDir, collection);
    File configFile = new File(collectionDir, StarTreeConstants.CONFIG_FILE_NAME);
    FileUtils.forceMkdir(collectionDir);
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.writeValue(configFile, baseConfig);

    // Create a tree
    StarTree starTree = new StarTreeImpl(config);
    starTree.open();

    // Add some data
    for (int i = 0; i < 10000; i++) {
      String[] combination = new String[] {
          "A" + (i % 2), "B" + (i % 4), "C" + (i % 8)
      };

      MetricTimeSeries timeSeries = new MetricTimeSeries(metricSchema);
      for (int j = 0; j < 4; j++) {
        timeSeries.increment(j, "M", 1);
      }

      StarTreeRecord record =
          new StarTreeRecordImpl(config, new DimensionKey(combination), timeSeries);
      starTree.add(record);
    }

    // Query to make sure data was added
    StarTreeQuery query = new StarTreeQueryImpl(config, new DimensionKey(new String[] {
        "*", "*", "*"
    }), new TimeRange(0L, 0L));
    MetricTimeSeries result = starTree.getTimeSeries(query);
    Assert.assertEquals(result.get(0, "M"), 10000);

    // Persist to tmp dir
    String collection = config.getCollection();
    String schedule = "ONCE";
    DateTime minTime = new DateTime(0);
    DateTime maxTime = new DateTime(4);
    String treeId = starTree.getRoot().getId().toString();
    dataUpdateManager.persistTree(collection, schedule, minTime, maxTime, starTree);

    // Check that the directory was created
    File dataDir =
        new File(collectionDir, StorageUtils.getDataDirName(treeId, schedule, minTime, maxTime));
    Assert.assertTrue(dataDir.exists());

    // Create a star tree rooted at that directory
    StarTreeManager manager = new StarTreeManagerImpl();
    manager.restore(rootDir, collection);
    Map<File, StarTree> starTrees = manager.getStarTrees(collection);
    Assert.assertEquals(starTrees.size(), 1);
    StarTree restoredTree = starTrees.values().iterator().next();
    result = restoredTree.getTimeSeries(query);
    Assert.assertEquals(result.get(0, "M"), 10000);
  }

  @Test
  public void testExpireSegments() throws Exception {
    dataUpdateManager = new DataUpdateManager(rootDir, true);
    File collectionDir = new File(rootDir, collection);
    FileUtils.forceMkdir(collectionDir);

    DateTime[][] dataDirs = new DateTime[6][2];
    dataDirs[0] = new DateTime[] {
        StarTreeConstants.DATE_TIME_FORMATTER.parseDateTime("2015-06-01-000000"),
        StarTreeConstants.DATE_TIME_FORMATTER.parseDateTime("2015-06-01-010000")
    };
    dataDirs[1] = new DateTime[] {
        StarTreeConstants.DATE_TIME_FORMATTER.parseDateTime("2015-05-31-000000"),
        StarTreeConstants.DATE_TIME_FORMATTER.parseDateTime("2015-05-31-010000")
    };
    dataDirs[2] = new DateTime[] {
        StarTreeConstants.DATE_TIME_FORMATTER.parseDateTime("2015-05-31-230000"),
        StarTreeConstants.DATE_TIME_FORMATTER.parseDateTime("2015-05-31-000000")
    };
    dataDirs[3] = new DateTime[] {
        StarTreeConstants.DATE_TIME_FORMATTER.parseDateTime("2015-05-31-000000"),
        StarTreeConstants.DATE_TIME_FORMATTER.parseDateTime("2015-06-01-000000")
    };
    dataDirs[4] = new DateTime[] {
        StarTreeConstants.DATE_TIME_FORMATTER.parseDateTime("2015-06-01-000000"),
        StarTreeConstants.DATE_TIME_FORMATTER.parseDateTime("2015-06-02-000000")
    };
    dataDirs[5] = new DateTime[] {
        StarTreeConstants.DATE_TIME_FORMATTER.parseDateTime("2015-05-01-000000"),
        StarTreeConstants.DATE_TIME_FORMATTER.parseDateTime("2015-06-01-000000")
    };

    // Create hourly folders for 2015-05-31, 2015-06-01
    File dataDirHourly1 = new File(collectionDir, StorageUtils.getDataDirName(treeId,
        StarTreeConstants.Schedule.HOURLY.name(), dataDirs[0][0], dataDirs[0][1]));
    FileUtils.forceMkdir(dataDirHourly1);
    File dataDirHourly2 = new File(collectionDir, StorageUtils.getDataDirName(treeId,
        StarTreeConstants.Schedule.HOURLY.name(), dataDirs[1][0], dataDirs[1][1]));
    FileUtils.forceMkdir(dataDirHourly2);
    File dataDirHourly3 = new File(collectionDir, StorageUtils.getDataDirName(treeId,
        StarTreeConstants.Schedule.HOURLY.name(), dataDirs[2][0], dataDirs[2][1]));
    FileUtils.forceMkdir(dataDirHourly3);

    // Check that they exist before daily comes along
    Assert.assertTrue(dataDirHourly1.exists());
    Assert.assertTrue(dataDirHourly2.exists());
    Assert.assertTrue(dataDirHourly3.exists());
    // Create daily folder for 2015-05-31
    File dataDirDaily1 = new File(collectionDir, StorageUtils.getDataDirName(treeId,
        StarTreeConstants.Schedule.DAILY.name(), dataDirs[3][0], dataDirs[3][1]));
    FileUtils.forceMkdir(dataDirDaily1);
    dataUpdateManager.expireSegments(collectionDir, dataDirs[3][0], dataDirs[3][1],
        StarTreeConstants.Schedule.DAILY.name());
    // All folders should exist as they are not older than QUIESCENCE_TIME
    Assert.assertTrue(dataDirHourly1.exists());
    Assert.assertTrue(dataDirHourly2.exists());
    Assert.assertTrue(dataDirHourly3.exists());
    Assert.assertTrue(dataDirDaily1.exists());

    // Update lastModified
    dataDirHourly1.setLastModified(
        dataDirHourly1.lastModified() - TimeUnit.MILLISECONDS.convert(6, TimeUnit.MINUTES));
    dataDirHourly2.setLastModified(
        dataDirHourly2.lastModified() - TimeUnit.MILLISECONDS.convert(6, TimeUnit.MINUTES));
    dataUpdateManager.expireSegments(collectionDir, dataDirs[3][0], dataDirs[3][1],
        StarTreeConstants.Schedule.DAILY.name());
    // Hourly folders for 2015-05-31 older than QUIESCENCE_TIME should be deleted
    Assert.assertTrue(dataDirHourly1.exists());
    Assert.assertFalse(dataDirHourly2.exists());
    Assert.assertTrue(dataDirHourly3.exists());
    Assert.assertTrue(dataDirDaily1.exists());

    // Create daily folder for 2015-06-01
    File dataDirDaily2 = new File(collectionDir, StorageUtils.getDataDirName(treeId,
        StarTreeConstants.Schedule.DAILY.name(), dataDirs[4][0], dataDirs[4][1]));
    FileUtils.forceMkdir(dataDirDaily2);
    // Create monthly folder for 2015-05
    File dataDirMonthly = new File(collectionDir, StorageUtils.getDataDirName(treeId,
        StarTreeConstants.Schedule.MONTHLY.name(), dataDirs[5][0], dataDirs[5][1]));
    FileUtils.forceMkdir(dataDirMonthly);
    dataUpdateManager.expireSegments(collectionDir, dataDirs[5][0], dataDirs[5][1],
        StarTreeConstants.Schedule.MONTHLY.name());
    // No hourly should be deleted.
    // No daily should be deleted as they are not older than QUIESCENCE_TIME
    Assert.assertTrue(dataDirHourly1.exists());
    Assert.assertTrue(dataDirHourly3.exists());
    Assert.assertTrue(dataDirDaily1.exists());
    Assert.assertTrue(dataDirDaily2.exists());
    Assert.assertTrue(dataDirMonthly.exists());

    // update last modified time of daily folders
    dataDirDaily1.setLastModified(
        dataDirDaily1.lastModified() - TimeUnit.MILLISECONDS.convert(6, TimeUnit.MINUTES));
    dataDirDaily2.setLastModified(
        dataDirDaily2.lastModified() - TimeUnit.MILLISECONDS.convert(6, TimeUnit.MINUTES));
    dataUpdateManager.expireSegments(collectionDir, dataDirs[5][0], dataDirs[5][1],
        StarTreeConstants.Schedule.MONTHLY.name());
    // No hourly should be deleted
    // Daily folders only in 2015-05 should be deleted
    Assert.assertTrue(dataDirHourly1.exists());
    Assert.assertTrue(dataDirHourly3.exists());
    Assert.assertFalse(dataDirDaily1.exists());
    Assert.assertTrue(dataDirDaily2.exists());
    Assert.assertTrue(dataDirMonthly.exists());

    FileUtils.forceDelete(dataDirHourly1);
    FileUtils.forceDelete(dataDirHourly3);
    FileUtils.forceDelete(dataDirDaily2);
    FileUtils.forceDelete(dataDirMonthly);
  }
}
