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
package org.apache.pinot.ingestion.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import java.io.File;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;
import org.apache.pinot.common.config.SegmentsValidationAndRetentionConfig;
import org.apache.pinot.common.config.TableConfig;
import org.apache.pinot.core.indexsegment.generator.SegmentGeneratorConfig;
import org.apache.pinot.core.segment.creator.impl.SegmentIndexCreationDriverImpl;
import org.apache.pinot.core.segment.name.NormalizedDateSegmentNameGenerator;
import org.apache.pinot.core.segment.name.SegmentNameGenerator;
import org.apache.pinot.core.segment.name.SimpleSegmentNameGenerator;
import org.apache.pinot.spi.data.Schema;
import org.apache.pinot.spi.data.TimeFieldSpec;
import org.apache.pinot.spi.data.readers.RecordReader;
import org.apache.pinot.spi.data.readers.RecordReaderConfig;
import org.apache.pinot.spi.utils.JsonUtils;


public class SegmentGenerationTaskRunner {

  public static final String SEGMENT_NAME_GENERATOR_TYPE = "segment.name.generator.type";
  public static final String SIMPLE_SEGMENT_NAME_GENERATOR = "simple";
  public static final String NORMALIZED_DATE_SEGMENT_NAME_GENERATOR = "normalizedDate";
  public static final String DEFAULT_SEGMENT_NAME_GENERATOR = SIMPLE_SEGMENT_NAME_GENERATOR;

  // For SimpleSegmentNameGenerator
  public static final String SEGMENT_NAME_POSTFIX = "segment.name.postfix";

  // For NormalizedDateSegmentNameGenerator
  public static final String SEGMENT_NAME_PREFIX = "segment.name.prefix";
  public static final String EXCLUDE_SEQUENCE_ID = "exclude.sequence.id";

  private SegmentGenerationTaskSpec _taskSpec;

  public SegmentGenerationTaskRunner(SegmentGenerationTaskSpec taskSpec) {
    _taskSpec = taskSpec;
  }

  public String run()
      throws Exception {

    String tableName = _taskSpec.getTableConfig().getTableName();
    TableConfig tableConfig = _taskSpec._tableConfig;
    Schema schema = _taskSpec.getSchema();

    //init record reader config
    String readerConfigClassName = _taskSpec.getRecordReaderSpec().getConfigClassName();
    RecordReaderConfig recordReaderConfig = null;

    if (readerConfigClassName != null) {
      Map<String, String> configs = _taskSpec.getRecordReaderSpec().getConfigs();
      if(configs == null) {
        configs = new HashMap<>();
      }
      JsonNode jsonNode = new ObjectMapper().valueToTree(configs);
      JsonUtils.jsonNodeToObject(jsonNode, Class.forName(readerConfigClassName));
      recordReaderConfig = (RecordReaderConfig) Class.forName(readerConfigClassName).newInstance();
    }

    //init record reader
    String readerClassName = _taskSpec.getRecordReaderSpec().getClassName();
    RecordReader recordReader = (RecordReader) Class.forName(readerClassName).newInstance();
    recordReader.init(new File(_taskSpec.getInputFilePath()), schema, recordReaderConfig);

    //init segmentName Generator
    String segmentNameGeneratorType = _taskSpec.getSegmentNameGeneratorSpec().getType();
    if (segmentNameGeneratorType == null) {
      segmentNameGeneratorType = SIMPLE_SEGMENT_NAME_GENERATOR;
    }
    Map<String, String> segmentNameGeneratorConfigs = _taskSpec.getSegmentNameGeneratorSpec().getConfigs();
    SegmentNameGenerator segmentNameGenerator;
    switch (segmentNameGeneratorType) {
      case SIMPLE_SEGMENT_NAME_GENERATOR:
        segmentNameGenerator =
            new SimpleSegmentNameGenerator(tableName, segmentNameGeneratorConfigs.get(SEGMENT_NAME_POSTFIX));
        break;
      case NORMALIZED_DATE_SEGMENT_NAME_GENERATOR:
        Preconditions.checkState(tableConfig != null,
            "In order to use NormalizedDateSegmentNameGenerator, table config must be provided");
        SegmentsValidationAndRetentionConfig validationConfig = tableConfig.getValidationConfig();
        String timeFormat = null;
        TimeFieldSpec timeFieldSpec = schema.getTimeFieldSpec();
        if (timeFieldSpec != null) {
          timeFormat = timeFieldSpec.getOutgoingGranularitySpec().getTimeFormat();
        }
        segmentNameGenerator =
            new NormalizedDateSegmentNameGenerator(tableName, segmentNameGeneratorConfigs.get(SEGMENT_NAME_PREFIX),
                Boolean.valueOf(segmentNameGeneratorConfigs.get(EXCLUDE_SEQUENCE_ID)),
                validationConfig.getSegmentPushType(), validationConfig.getSegmentPushFrequency(),
                validationConfig.getTimeType(), timeFormat);
        break;
      default:
        throw new UnsupportedOperationException("Unsupported segment name generator type: " + segmentNameGeneratorType);
    }

    //init segment generation config
    SegmentGeneratorConfig segmentGeneratorConfig = new SegmentGeneratorConfig(tableConfig, schema);
    segmentGeneratorConfig.setTableName(tableName);
    segmentGeneratorConfig.setOutDir(_taskSpec.getOutputDirectoryPath());
    segmentGeneratorConfig.setSegmentNameGenerator(segmentNameGenerator);
    segmentGeneratorConfig.setSequenceId(_taskSpec.getSequenceId());

    //build segment
    SegmentIndexCreationDriverImpl segmentIndexCreationDriver = new SegmentIndexCreationDriverImpl();
    segmentIndexCreationDriver.init(segmentGeneratorConfig, recordReader);
    segmentIndexCreationDriver.build();
    return segmentIndexCreationDriver.getSegmentName();
  }
}
