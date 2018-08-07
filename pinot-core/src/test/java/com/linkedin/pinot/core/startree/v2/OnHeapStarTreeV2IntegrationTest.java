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
package com.linkedin.pinot.core.startree.v2;

import java.io.File;
import java.util.List;
import java.util.ArrayList;
import com.google.common.io.Files;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeTest;
import com.linkedin.pinot.common.data.Schema;
import com.linkedin.pinot.core.data.GenericRow;
import com.linkedin.pinot.core.startree.StarTree;
import com.linkedin.pinot.core.common.DataSource;
import com.linkedin.pinot.common.segment.ReadMode;
import com.linkedin.pinot.core.data.readers.RecordReader;
import com.linkedin.pinot.core.data.readers.GenericRowRecordReader;
import com.linkedin.pinot.core.indexsegment.generator.SegmentVersion;
import com.linkedin.pinot.core.indexsegment.immutable.ImmutableSegment;
import com.linkedin.pinot.core.segment.index.loader.IndexLoadingConfig;
import com.linkedin.pinot.core.indexsegment.immutable.ImmutableSegmentLoader;
import com.linkedin.pinot.core.query.aggregation.function.AggregationFunctionType;


public class OnHeapStarTreeV2IntegrationTest {

  private File _filepath;
  private File _indexDir;
  private List<StarTreeV2Config> _starTreeV2ConfigList = new ArrayList<>();

  @BeforeTest
  void setUp() throws Exception {

    Schema schema = StarTreeV2SegmentHelper.createSegmentSchema();
    String _segmentName = "starTreeV2BuilderTest";
    String _segmentOutputDir = Files.createTempDir().toString();

    List<GenericRow> _rows = StarTreeV2SegmentHelper.createSegmentSmallData(schema);

    //List<GenericRow> _rows = StarTreeV2SegmentHelper.createSegmentLargeData(schema);

    RecordReader _recordReader = new GenericRowRecordReader(_rows, schema);
    _indexDir = StarTreeV2SegmentHelper.createSegment(schema, _segmentName, _segmentOutputDir, _recordReader);
    _filepath = new File(_indexDir, "v3");

    List<AggregationFunctionColumnPair> metric2aggFuncPairs1 = new ArrayList<>();
    List<AggregationFunctionColumnPair> metric2aggFuncPairs2 = new ArrayList<>();


    AggregationFunctionColumnPair pair1 = new AggregationFunctionColumnPair(AggregationFunctionType.SUM, "salary");
    AggregationFunctionColumnPair pair2 = new AggregationFunctionColumnPair(AggregationFunctionType.MAX, "salary");
    AggregationFunctionColumnPair pair3 = new AggregationFunctionColumnPair(AggregationFunctionType.MIN, "salary");
    AggregationFunctionColumnPair pair4 = new AggregationFunctionColumnPair(AggregationFunctionType.DISTINCTCOUNTHLL, "salary");
    AggregationFunctionColumnPair pair5 = new AggregationFunctionColumnPair(AggregationFunctionType.PERCENTILETDIGEST, "salary");
    AggregationFunctionColumnPair pair6 = new AggregationFunctionColumnPair(AggregationFunctionType.PERCENTILEEST, "salary");
    AggregationFunctionColumnPair pair7 = new AggregationFunctionColumnPair(AggregationFunctionType.COUNT, "star");

    metric2aggFuncPairs1.add(pair1);
    metric2aggFuncPairs1.add(pair2);
    metric2aggFuncPairs1.add(pair4);
    metric2aggFuncPairs1.add(pair5);
    metric2aggFuncPairs1.add(pair6);
    metric2aggFuncPairs1.add(pair7);

    metric2aggFuncPairs2.add(pair3);
    metric2aggFuncPairs2.add(pair1);

    StarTreeV2Config _starTreeV2Config1 = new StarTreeV2Config();
    _starTreeV2Config1.setOutDir(_filepath);
    _starTreeV2Config1.setMaxNumLeafRecords(1);
    _starTreeV2Config1.setDimensions(schema.getDimensionNames());
    _starTreeV2Config1.setMetric2aggFuncPairs(metric2aggFuncPairs1);
    _starTreeV2ConfigList.add(_starTreeV2Config1);

    StarTreeV2Config _starTreeV2Config2 = new StarTreeV2Config();
    _starTreeV2Config2.setOutDir(_filepath);
    _starTreeV2Config2.setMaxNumLeafRecords(1);
    _starTreeV2Config2.setDimensions(schema.getDimensionNames());
    _starTreeV2Config2.setMetric2aggFuncPairs(metric2aggFuncPairs2);
    _starTreeV2ConfigList.add(_starTreeV2Config2);

    return;
  }

  @Test
  public void testBuilder() throws Exception {
    OnHeapStarTreeV2Builder buildTest = new OnHeapStarTreeV2Builder();
    try {
      for (int i = 0; i < _starTreeV2ConfigList.size(); i++) {
        buildTest.init(_indexDir, _starTreeV2ConfigList.get(i));
        buildTest.build();
        buildTest.serialize();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Test
  public void testLoader() throws Exception {
    OnHeapStarTreeV2Loader loadTest = new OnHeapStarTreeV2Loader();
    IndexLoadingConfig v3IndexLoadingConfig = new IndexLoadingConfig();
    v3IndexLoadingConfig.setReadMode(ReadMode.mmap);
    v3IndexLoadingConfig.setSegmentVersion(SegmentVersion.v3);

    ImmutableSegment immutableSegment = ImmutableSegmentLoader.load(_indexDir, v3IndexLoadingConfig);

    try {
      List<StarTreeV2> starTreeImplList = loadTest.load(_filepath, immutableSegment);

      int starTreeId = 0;
      for (StarTreeV2 impl : starTreeImplList) {
        System.out.println("Working on star tree " + Integer.toString(starTreeId + 1));

        StarTree s = impl.getStarTree();
        StarTreeV2LoaderHelper.printStarTree(s);

        for (String dimension : _starTreeV2ConfigList.get(starTreeId).getDimensions()) {
          DataSource source = impl.getDataSource(dimension);
          System.out.println("Printing for dimension : " + dimension);
          StarTreeV2LoaderHelper.printDimensionDataFromDataSource(source);
        }

        for (AggregationFunctionColumnPair pair : _starTreeV2ConfigList.get(starTreeId).getMetric2aggFuncPairs()) {
          String metPair = pair.toColumnName();
          DataSource source = impl.getDataSource(metPair);
          System.out.println("Printing for Met2AggPair : " + metPair);
          StarTreeV2LoaderHelper.printMetricAggfuncDataFromDataSource(source, pair.getFunctionType().getName());
        }
        starTreeId += 1;
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}