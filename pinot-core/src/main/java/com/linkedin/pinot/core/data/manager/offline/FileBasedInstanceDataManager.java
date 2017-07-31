/**
 * Copyright (C) 2014-2016 LinkedIn Corp. (pinot-core@linkedin.com)
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
package com.linkedin.pinot.core.data.manager.offline;

import com.google.common.base.Preconditions;
import com.linkedin.pinot.common.Utils;
import com.linkedin.pinot.common.config.TableConfig;
import com.linkedin.pinot.common.data.Schema;
import com.linkedin.pinot.core.data.manager.config.FileBasedInstanceDataManagerConfig;
import com.linkedin.pinot.core.data.manager.config.TableDataManagerConfig;
import com.linkedin.pinot.core.metadata.instance.InstanceZKMetadata;
import com.linkedin.pinot.core.metadata.segment.SegmentMetadata;
import com.linkedin.pinot.core.metadata.segment.SegmentMetadataLoader;
import com.linkedin.pinot.core.metadata.segment.SegmentZKMetadata;
import com.linkedin.pinot.core.segment.index.loader.IndexLoadingConfig;
import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.commons.configuration.Configuration;
import org.apache.helix.ZNRecord;
import org.apache.helix.store.zk.ZkHelixPropertyStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class FileBasedInstanceDataManager implements InstanceDataManager {
  public static final Logger LOGGER = LoggerFactory.getLogger(FileBasedInstanceDataManager.class);

  private FileBasedInstanceDataManagerConfig _instanceDataManagerConfig;
  private Map<String, TableDataManager> _tableDataManagerMap = new HashMap<String, TableDataManager>();
  private boolean _isStarted = false;

  @Override
  public synchronized void init(@Nonnull Configuration instanceDataManagerConfig) {
    try {
      _instanceDataManagerConfig = new FileBasedInstanceDataManagerConfig(instanceDataManagerConfig);
    } catch (Exception e) {
      _instanceDataManagerConfig = null;
      LOGGER.error("Error during InstanceDataManager initialization", e);
      Utils.rethrowException(e);
    }
    for (String tableName : _instanceDataManagerConfig.getTableNames()) {
      TableDataManagerConfig tableDataManagerConfig =
          _instanceDataManagerConfig.getTableDataManagerConfig(tableName);
      TableDataManager tableDataManager = TableDataManagerProvider.getTableDataManager(tableDataManagerConfig, null);
      _tableDataManagerMap.put(tableName, tableDataManager);
    }
  }

  public void addTable(TableDataManagerConfig tableConfig) {
    TableDataManager tableDataManager = TableDataManagerProvider.getTableDataManager(tableConfig, null);
    _tableDataManagerMap.put(tableConfig.getTableName(), tableDataManager);
  }

  @Override
  public synchronized void start() {
    for (TableDataManager tableDataManager : _tableDataManagerMap.values()) {
      tableDataManager.start();
    }
    try {
      bootstrapSegmentsFromSegmentDir();
    } catch (Exception e) {
      LOGGER.error(
          "Error in bootstrap segment from dir : " + _instanceDataManagerConfig.getInstanceBootstrapSegmentDir(), e);
    }

    _isStarted = true;
    LOGGER.info("InstanceDataManager is started!");
  }

  private void bootstrapSegmentsFromSegmentDir() throws Exception {
    if (_instanceDataManagerConfig.getInstanceBootstrapSegmentDir() != null) {
      File bootstrapSegmentDir = new File(_instanceDataManagerConfig.getInstanceBootstrapSegmentDir());
      if (bootstrapSegmentDir.exists()) {
        for (File segment : bootstrapSegmentDir.listFiles()) {
          addSegment(SegmentMetadataLoader.load(segment), null, null);
          LOGGER.info("Bootstrapped segment from directory : " + segment.getAbsolutePath());
        }
      } else {
        LOGGER.info("Bootstrap segment directory : " + _instanceDataManagerConfig.getInstanceBootstrapSegmentDir()
            + " doesn't exist.");
      }
    } else {
      LOGGER.info("Config of bootstrap segment directory hasn't been set.");
    }

  }

  @Override
  public boolean isStarted() {
    return _isStarted;
  }

  @Nonnull
  @Override
  public Collection<TableDataManager> getTableDataManagers() {
    return _tableDataManagerMap.values();
  }

  @Nullable
  @Override
  public TableDataManager getTableDataManager(String tableNameWithType) {
    return _tableDataManagerMap.get(tableNameWithType);
  }

  @Override
  public synchronized void shutDown() {
    if (isStarted()) {
      for (TableDataManager tableDataManager : getTableDataManagers()) {
        tableDataManager.shutDown();
      }
      _isStarted = false;
      LOGGER.info("InstanceDataManager is shutDown!");
    } else {
      LOGGER.warn("InstanceDataManager is already shutDown, won't do anything!");
    }
  }

  @Override
  public synchronized void addSegment(@Nonnull SegmentMetadata segmentMetadata, @Nullable TableConfig tableConfig,
      @Nullable Schema schema)
      throws Exception {
    String segmentName = segmentMetadata.getName();
    String tableName = segmentMetadata.getTableName();
    LOGGER.info("Trying to add segment: {} to OFFLINE table: {}", segmentName, tableName);
    Preconditions.checkState(_tableDataManagerMap.containsKey(tableName),
        "InstanceDataManager does not contain OFFLINE table: " + tableName + " for segment: " + segmentName);
    _tableDataManagerMap.get(tableName)
        .addSegment(segmentMetadata, new IndexLoadingConfig(_instanceDataManagerConfig, tableConfig), schema);
    LOGGER.info("Added segment: {} to OFFLINE table: {}", segmentName, tableName);
  }

  @Override
  public synchronized void removeSegment(@Nonnull String segmentName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void reloadSegment(@Nonnull String tableNameWithType, @Nonnull SegmentMetadata segmentMetadata,
      @Nullable TableConfig tableConfig, @Nullable Schema schema) {
    throw new UnsupportedOperationException("Unsupported reloading segment in FileBasedInstanceDataManager");
  }

  @Nonnull
  @Override
  public String getInstanceDataDir() {
    return _instanceDataManagerConfig.getInstanceDataDir();
  }

  @Nonnull
  @Override
  public String getInstanceSegmentTarDir() {
    return _instanceDataManagerConfig.getInstanceSegmentTarDir();
  }

  @Nonnull
  @Override
  public List<SegmentMetadata> getSegmentsMetadata(@Nonnull String tableNameWithType) {
    throw new UnsupportedOperationException(
        "Unsupported getting all segments' metadata in FileBasedInstanceDataManager");
  }

  @Nullable
  @Override
  public SegmentMetadata getSegmentMetadata(@Nonnull String tableNameWithType, @Nonnull String segmentName) {
    throw new UnsupportedOperationException("Unsupported getting segment metadata in FileBasedInstanceDataManager");
  }

  @Override
  public void addSegment(@Nonnull ZkHelixPropertyStore<ZNRecord> propertyStore, @Nonnull TableConfig tableConfig,
      @Nullable InstanceZKMetadata instanceZKMetadata, @Nonnull SegmentZKMetadata segmentZKMetadata,
      @Nonnull String serverInstance)
      throws Exception {
    throw new UnsupportedOperationException(
        "Unsupported adding segment to REALTIME table in FileBasedInstanceDataManager");
  }
}
