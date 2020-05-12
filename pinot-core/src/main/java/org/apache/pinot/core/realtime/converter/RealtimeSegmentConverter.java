/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.pinot.core.realtime.converter;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.pinot.common.metrics.ServerGauge;
import org.apache.pinot.common.metrics.ServerMetrics;
import org.apache.pinot.core.data.recordtransformer.CompositeTransformer;
import org.apache.pinot.core.indexsegment.generator.SegmentGeneratorConfig;
import org.apache.pinot.core.indexsegment.generator.SegmentVersion;
import org.apache.pinot.core.indexsegment.mutable.MutableSegmentImpl;
import org.apache.pinot.core.io.compression.ChunkCompressorFactory;
import org.apache.pinot.core.realtime.converter.stats.RealtimeSegmentSegmentCreationDataSource;
import org.apache.pinot.core.segment.creator.impl.SegmentIndexCreationDriverImpl;
import org.apache.pinot.spi.config.table.ColumnPartitionConfig;
import org.apache.pinot.spi.config.table.SegmentPartitionConfig;
import org.apache.pinot.spi.config.table.TableConfig;
import org.apache.pinot.spi.data.FieldSpec;
import org.apache.pinot.spi.data.Schema;
import org.apache.pinot.spi.data.TimeFieldSpec;
import org.apache.pinot.spi.data.TimeGranularitySpec;


public class RealtimeSegmentConverter {
  private MutableSegmentImpl _realtimeSegmentImpl;
  private final String _outputPath;
  private final Schema _dataSchema;
  private final String _tableName;
  private final TableConfig _tableConfig;
  private final String _segmentName;
  private final String _sortedColumn;
  private final List<String> _invertedIndexColumns;
  private final List<String> _textIndexColumns;
  private final List<String> _noDictionaryColumns;
  private final List<String> _varLengthDictionaryColumns;
  private final boolean _nullHandlingEnabled;

  public RealtimeSegmentConverter(MutableSegmentImpl realtimeSegment, String outputPath, Schema schema,
      String tableName, TableConfig tableConfig, String segmentName, String sortedColumn,
      List<String> invertedIndexColumns, List<String> textIndexColumns, List<String> noDictionaryColumns,
      List<String> varLengthDictionaryColumns, boolean nullHandlingEnabled) {
    _realtimeSegmentImpl = realtimeSegment;
    _outputPath = outputPath;
    _invertedIndexColumns = new ArrayList<>(invertedIndexColumns);
    if (sortedColumn != null) {
      _invertedIndexColumns.remove(sortedColumn);
    }
    _dataSchema = getUpdatedSchema(schema);
    _sortedColumn = sortedColumn;
    _tableName = tableName;
    _tableConfig = tableConfig;
    _segmentName = segmentName;
    _noDictionaryColumns = noDictionaryColumns;
    _varLengthDictionaryColumns = varLengthDictionaryColumns;
    _nullHandlingEnabled = nullHandlingEnabled;
    _textIndexColumns = textIndexColumns;
  }

  public RealtimeSegmentConverter(MutableSegmentImpl realtimeSegment, String outputPath, Schema schema,
      String tableName, TableConfig tableConfig, String segmentName, String sortedColumn,
      List<String> invertedIndexColumns, List<String> noDictionaryColumns, List<String> varLengthDictionaryColumns,
      boolean nullHandlingEnabled) {
    this(realtimeSegment, outputPath, schema, tableName, tableConfig, segmentName, sortedColumn,
        invertedIndexColumns, new ArrayList<>(), noDictionaryColumns, varLengthDictionaryColumns, nullHandlingEnabled);
  }

  // Used in RealtimeSegmentConverterTest
  public RealtimeSegmentConverter(MutableSegmentImpl realtimeSegment, String outputPath, Schema schema,
      String tableName, TableConfig tableConfig, String segmentName, String sortedColumn) {
    this(realtimeSegment, outputPath, schema, tableName, tableConfig, segmentName, sortedColumn, new ArrayList<>(),
        new ArrayList<>(), new ArrayList<>(), null/*StarTreeIndexSpec*/, false/*nullHandlingEnabled*/);
  }

  public void build(@Nullable SegmentVersion segmentVersion, ServerMetrics serverMetrics)
      throws Exception {
    // lets create a record reader
    RealtimeSegmentRecordReader reader = new RealtimeSegmentRecordReader(_realtimeSegmentImpl, _sortedColumn);
    SegmentGeneratorConfig genConfig = new SegmentGeneratorConfig(_tableConfig, _dataSchema);
    // The segment generation code in SegmentColumnarIndexCreator will throw
    // exception if start and end time in time column are not in acceptable
    // range. We don't want the realtime consumption to stop (if an exception
    // is thrown) and thus the time validity check is explicitly disabled for
    // realtime segment generation
    genConfig.setSkipTimeValueCheck(true);
    if (_invertedIndexColumns != null && !_invertedIndexColumns.isEmpty()) {
      for (String column : _invertedIndexColumns) {
        genConfig.createInvertedIndexForColumn(column);
      }
    }
    if (_noDictionaryColumns != null) {
      genConfig.setRawIndexCreationColumns(_noDictionaryColumns);
      Map<String, ChunkCompressorFactory.CompressionType> columnToCompressionType = new HashMap<>();
      for (String column : _noDictionaryColumns) {
        FieldSpec fieldSpec = _dataSchema.getFieldSpecFor(column);
        if (fieldSpec.getFieldType().equals(FieldSpec.FieldType.METRIC)) {
          columnToCompressionType.put(column, ChunkCompressorFactory.CompressionType.PASS_THROUGH);
        }
      }
      genConfig.setRawIndexCompressionType(columnToCompressionType);
    }

    if (_varLengthDictionaryColumns != null) {
      genConfig.setVarLengthDictionaryColumns(_varLengthDictionaryColumns);
    }

    if (segmentVersion != null) {
      genConfig.setSegmentVersion(segmentVersion);
    }
    genConfig.setTableName(_tableName);
    genConfig.setOutDir(_outputPath);
    genConfig.setSegmentName(_segmentName);
    genConfig.setTextIndexCreationColumns(_textIndexColumns);
    SegmentPartitionConfig segmentPartitionConfig = _realtimeSegmentImpl.getSegmentPartitionConfig();
    genConfig.setSegmentPartitionConfig(segmentPartitionConfig);
    genConfig.setNullHandlingEnabled(_nullHandlingEnabled);
    final SegmentIndexCreationDriverImpl driver = new SegmentIndexCreationDriverImpl();
    RealtimeSegmentSegmentCreationDataSource dataSource =
        new RealtimeSegmentSegmentCreationDataSource(_realtimeSegmentImpl, reader, _dataSchema);
    driver.init(genConfig, dataSource, CompositeTransformer.getPassThroughTransformer());
    driver.build();

    if (segmentPartitionConfig != null) {
      Map<String, ColumnPartitionConfig> columnPartitionMap = segmentPartitionConfig.getColumnPartitionMap();
      for (String columnName : columnPartitionMap.keySet()) {
        int numPartitions = driver.getSegmentStats().getColumnProfileFor(columnName).getPartitions().size();
        serverMetrics.addValueToTableGauge(_tableName, ServerGauge.REALTIME_SEGMENT_NUM_PARTITIONS, numPartitions);
      }
    }
  }

  /**
   * Returns a new schema based on the original one. The new schema removes columns as needed (for ex, virtual cols)
   * and adds the new timespec to the schema.
   */
  @VisibleForTesting
  public Schema getUpdatedSchema(Schema original) {
    Schema newSchema = new Schema();
    for (String col : original.getPhysicalColumnNames()) {
      newSchema.addField(original.getFieldSpecFor(col));
    }
    return newSchema;
  }
}
