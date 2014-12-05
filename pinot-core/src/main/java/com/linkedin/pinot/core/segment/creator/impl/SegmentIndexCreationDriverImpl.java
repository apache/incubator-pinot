package com.linkedin.pinot.core.segment.creator.impl;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;

import com.linkedin.pinot.common.data.FieldSpec;
import com.linkedin.pinot.common.data.Schema;
import com.linkedin.pinot.common.utils.SegmentNameBuilder;
import com.linkedin.pinot.core.data.extractors.FieldExtractorFactory;
import com.linkedin.pinot.core.data.readers.RecordReader;
import com.linkedin.pinot.core.data.readers.RecordReaderFactory;
import com.linkedin.pinot.core.indexsegment.generator.SegmentGeneratorConfig;
import com.linkedin.pinot.core.segment.creator.ColumnIndexCreationInfo;
import com.linkedin.pinot.core.segment.creator.ForwardIndexType;
import com.linkedin.pinot.core.segment.creator.InvertedIndexType;
import com.linkedin.pinot.core.segment.creator.SegmentIndexCreationDriver;
import com.linkedin.pinot.core.segment.creator.SegmentPreIndexStatsCollector;
import com.linkedin.pinot.core.util.CrcUtils;


/**
 * @author Dhaval Patel<dpatel@linkedin.com> Nov 6, 2014
 */

public class SegmentIndexCreationDriverImpl implements SegmentIndexCreationDriver {

  SegmentGeneratorConfig config;
  RecordReader recordReader;
  SegmentPreIndexStatsCollector statsCollector;
  Map<String, ColumnIndexCreationInfo> indexCreationInfoMap;
  SegmentColumnarIndexCreator indexCreator;
  Schema dataSchema;
  int totalDocs = 0;
  File tempIndexDir;
  String segmentName;

  @Override
  public void init(SegmentGeneratorConfig config) throws Exception {
    this.config = config;
    recordReader = RecordReaderFactory.get(config.getInputFileFormat(), config.getInputFilePath(), FieldExtractorFactory.get(config));
    recordReader.init();
    dataSchema = recordReader.getSchema();

    statsCollector = new SegmentPreIndexStatsCollectorImpl(recordReader.getSchema());
    statsCollector.init();
    indexCreationInfoMap = new HashMap<String, ColumnIndexCreationInfo>();
    indexCreator = new SegmentColumnarIndexCreator();
    final File indexDir = new File(config.getIndexOutputDir());
    if (!indexDir.exists()) {
      indexDir.mkdir();
    }
    tempIndexDir = new File(indexDir, com.linkedin.pinot.common.utils.FileUtils.getRandomFileName());
  }

  public void init(SegmentGeneratorConfig config, RecordReader reader) throws Exception {
    this.config = config;
    recordReader = reader;
    recordReader.init();
    dataSchema = recordReader.getSchema();

    statsCollector = new SegmentPreIndexStatsCollectorImpl(recordReader.getSchema());
    statsCollector.init();
    indexCreationInfoMap = new HashMap<String, ColumnIndexCreationInfo>();
    indexCreator = new SegmentColumnarIndexCreator();
    final File indexDir = new File(config.getIndexOutputDir());
    if (!indexDir.exists()) {
      indexDir.mkdir();
    }
    tempIndexDir = new File(indexDir, com.linkedin.pinot.common.utils.FileUtils.getRandomFileName());
  }

  @Override
  public void build() throws Exception {

    while (recordReader.hasNext()) {
      totalDocs++;
      statsCollector.collectRow(recordReader.next());
    }

    buildIndexCreationInfo();

    indexCreator.init(config, indexCreationInfoMap, dataSchema, totalDocs, tempIndexDir);

    recordReader.rewind();

    while (recordReader.hasNext()) {
      indexCreator.index(recordReader.next());
    }

    recordReader.close();

    final File outputDir = new File(config.getIndexOutputDir());

    final String timeColumn = config.getTimeColumnName();

    if (timeColumn != null && timeColumn.length() > 0) {
      final Object minTimeValue = statsCollector.getColumnProfileFor(timeColumn).getMinValue();
      final Object maxTimeValue = statsCollector.getColumnProfileFor(timeColumn).getMaxValue();

      segmentName =
          SegmentNameBuilder.buildBasic(config.getResourceName(), config.getTableName(), minTimeValue, maxTimeValue,
              config.getSegmentNamePostfix());
    } else {
      segmentName = SegmentNameBuilder.buildBasic(config.getResourceName(), config.getTableName(), config.getSegmentNamePostfix());
    }

    indexCreator.setSegmentName(segmentName);
    indexCreator.seal();
    final File segmentOutputDir = new File(outputDir, segmentName);
    if (segmentOutputDir.exists()) {
      FileUtils.deleteDirectory(segmentOutputDir);
    }

    FileUtils.moveDirectory(tempIndexDir, new File(outputDir, segmentName));

    FileUtils.deleteQuietly(tempIndexDir);

    final long crc = CrcUtils.forAllFilesInFolder(new File(outputDir, segmentName)).computeCrc();

    persistCreationMeta(new File(outputDir, segmentName), crc);
  }

  void persistCreationMeta(File outputDir, long crc) throws IOException {
    final File crcFile = new File(outputDir, V1Constants.SEGMENT_CREATION_META);
    final DataOutputStream out = new DataOutputStream(new FileOutputStream(crcFile));
    out.writeLong(crc);
    out.writeLong(System.currentTimeMillis());
    out.close();
  }

  void buildIndexCreationInfo() throws Exception {
    statsCollector.build();
    for (final FieldSpec spec : dataSchema.getAllFieldSpecs()) {
      final String column = spec.getName();
      indexCreationInfoMap.put(spec.getName(), new ColumnIndexCreationInfo(true, statsCollector.getColumnProfileFor(column).getMinValue(),
          statsCollector.getColumnProfileFor(column).getMaxValue(), statsCollector.getColumnProfileFor(column).getUniqueValuesSet(),
          ForwardIndexType.fixed_bit_compressed, InvertedIndexType.p4_delta, statsCollector.getColumnProfileFor(column).isSorted(),
          statsCollector.getColumnProfileFor(column).hasNull(), statsCollector.getColumnProfileFor(column).getTotalNumberOfEntries(),
          statsCollector.getColumnProfileFor(column).getMaxNumberOfMultiValues()));
    }
  }

  @Override
  public String getSegmentName() {
    return segmentName;
  }

}
