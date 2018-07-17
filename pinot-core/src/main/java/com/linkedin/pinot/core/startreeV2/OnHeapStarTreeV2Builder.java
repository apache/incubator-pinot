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

package com.linkedin.pinot.core.startreeV2;

import java.io.File;
import java.util.Set;
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.io.IOException;
import xerial.larray.mmap.MMapMode;
import xerial.larray.mmap.MMapBuffer;
import com.linkedin.pinot.common.utils.Pairs;
import com.linkedin.pinot.common.data.FieldSpec;
import com.linkedin.pinot.common.segment.ReadMode;
import com.linkedin.pinot.common.data.MetricFieldSpec;
import com.linkedin.pinot.common.segment.SegmentMetadata;
import com.linkedin.pinot.common.data.DimensionFieldSpec;
import com.linkedin.pinot.core.startree.OffHeapStarTreeNode;
import org.apache.commons.configuration.PropertiesConfiguration;
import com.linkedin.pinot.core.segment.creator.impl.V1Constants;
import com.linkedin.pinot.core.segment.creator.ForwardIndexCreator;
import com.linkedin.pinot.core.data.readers.PinotSegmentColumnReader;
import com.linkedin.pinot.core.io.compression.ChunkCompressorFactory;
import com.linkedin.pinot.core.indexsegment.generator.SegmentVersion;
import com.linkedin.pinot.core.segment.index.loader.IndexLoadingConfig;
import com.linkedin.pinot.core.indexsegment.immutable.ImmutableSegment;
import com.linkedin.pinot.core.segment.creator.SingleValueRawIndexCreator;
import com.linkedin.pinot.core.indexsegment.immutable.ImmutableSegmentLoader;
import com.linkedin.pinot.core.segment.creator.SingleValueForwardIndexCreator;
import com.linkedin.pinot.core.segment.index.readers.ImmutableDictionaryReader;
import com.linkedin.pinot.core.segment.creator.impl.SegmentColumnarIndexCreator;
import com.linkedin.pinot.core.segment.creator.impl.fwd.SingleValueUnsortedForwardIndexCreator;


public class OnHeapStarTreeV2Builder implements StarTreeV2Builder {

  // Segment
  private File _outDir;
  private SegmentMetadata _segmentMetadata;
  private ImmutableSegment _immutableSegment;
  private PropertiesConfiguration _properties;
  private IndexLoadingConfig _v3IndexLoadingConfig;

  // Dimensions
  private int _dimensionsCount;
  private List<String> _dimensionsName;
  private String _dimensionSplitOrderString;
  private List<Integer> _dimensionsCardinalty;
  private List<Integer> _dimensionsSplitOrder;
  private List<Integer> _dimensionsWithoutStarNode;
  private Map<String, DimensionFieldSpec> _dimensionsSpecMap;

  // Metrics
  private int _metricsCount;
  private Set<String> _metricsName;
  private int _met2aggfuncPairsCount;
  private String _met2aggfuncPairsString;
  private List<Met2AggfuncPair> _met2aggfuncPairs;
  private Map<String, MetricFieldSpec> _metricsSpecMap;

  // General
  private int _nodesCount;
  private int _rawDocsCount;
  private TreeNode _rootNode;
  private int _maxNumLeafRecords;
  private AggregationFunctionFactory _aggregationFunctionFactory;

  // Star Tree
  private int _starTreeCount = 0;
  private String _starTreeId = null;
  private List<Record> _starTreeData = new ArrayList<>();
  private List<Record> _rawStarTreeData = new ArrayList<>();
  private List<ForwardIndexCreator> _metricForwardIndexCreatorList;
  private List<ForwardIndexCreator> _dimensionForwardIndexCreatorList;

  @Override
  public void init(File indexDir, StarTreeV2Config config) throws Exception {

    // segment
    _v3IndexLoadingConfig = new IndexLoadingConfig();
    _v3IndexLoadingConfig.setReadMode(ReadMode.mmap);
    _v3IndexLoadingConfig.setSegmentVersion(SegmentVersion.v3);

    _immutableSegment = ImmutableSegmentLoader.load(indexDir, _v3IndexLoadingConfig);
    _segmentMetadata = _immutableSegment.getSegmentMetadata();
    _rawDocsCount = _segmentMetadata.getTotalRawDocs();

    // dimension
    _dimensionsSpecMap = new HashMap<>();
    _dimensionsName = config.getDimensions();
    _dimensionsCount = _dimensionsName.size();

    List<DimensionFieldSpec> _dimensionsSpecList = _segmentMetadata.getSchema().getDimensionFieldSpecs();
    for (DimensionFieldSpec dimension : _dimensionsSpecList) {
      if (_dimensionsName.contains(dimension.getName())) {
        _dimensionsSpecMap.put(dimension.getName(), dimension);
      }
    }

    // dimension split order.
    List<String> dimensionsSplitOrder = config.getDimensionsSplitOrder();
    _dimensionsSplitOrder = OnHeapStarTreeV2BuilderHelper.enumerateDimensions(_dimensionsName, dimensionsSplitOrder);
    List<String> dimensionsWithoutStarNode = config.getDimensionsWithoutStarNode();
    _dimensionsWithoutStarNode =
        OnHeapStarTreeV2BuilderHelper.enumerateDimensions(_dimensionsName, dimensionsWithoutStarNode);

    // metric
    _met2aggfuncPairsString = "";
    _metricsName = new HashSet<>();
    _metricsSpecMap = new HashMap<>();
    _met2aggfuncPairs = config.getMetric2aggFuncPairs();
    _met2aggfuncPairsCount = _met2aggfuncPairs.size();
    for (Met2AggfuncPair pair : _met2aggfuncPairs) {
      _metricsName.add(pair.getMetricName());
      _met2aggfuncPairsString += pair.getMetricName() + '_' + pair.getAggregatefunction() + ',';
    }
    _metricsCount = _metricsName.size();

    List<MetricFieldSpec> _metricsSpecList = _segmentMetadata.getSchema().getMetricFieldSpecs();
    for (MetricFieldSpec metric : _metricsSpecList) {
      if (_metricsName.contains(metric.getName())) {
          _metricsSpecMap.put(metric.getName(), metric);
      }
    }

    // other initialisation
    _starTreeId = StarTreeV2Constant.STAR_TREE + '_' + Integer.toString(_starTreeCount);
    _outDir = config.getOutDir();
    _maxNumLeafRecords = config.getMaxNumLeafRecords();
    _rootNode = new TreeNode();
    _rootNode._dimensionId = StarTreeV2Constant.STAR_NODE;
    _nodesCount++;
    _starTreeCount++;
    _aggregationFunctionFactory = new AggregationFunctionFactory();

    File metadataFile = new File(new File(indexDir, "v3"), V1Constants.MetadataKeys.METADATA_FILE_NAME);
    _properties = new PropertiesConfiguration(metadataFile);
  }

  @Override
  public void build() throws IOException {

    // storing dimension cardinality for calculating default sorting order.
    _dimensionsCardinalty = new ArrayList<>();
    for (int i = 0; i < _dimensionsCount; i++) {
      String dimensionName = _dimensionsName.get(i);
      ImmutableDictionaryReader dictionary = _immutableSegment.getDictionary(dimensionName);
      _dimensionsCardinalty.add(dictionary.length());
    }

    // gathering dimensions column reader
    List<PinotSegmentColumnReader> dimensionColumnReaders = new ArrayList<>();
    for (String name : _dimensionsName) {
      PinotSegmentColumnReader columnReader = new PinotSegmentColumnReader(_immutableSegment, name);
      dimensionColumnReaders.add(columnReader);
    }

    // gathering dimensions data ( dictionary encoded id )
    for (int i = 0; i < _rawDocsCount; i++) {
      Record record = new Record();
      int[] dimensionValues = new int[_dimensionsCount];
      for (int j = 0; j < dimensionColumnReaders.size(); j++) {
        Integer dictId = dimensionColumnReaders.get(j).getDictionaryId(i);
        dimensionValues[j] = dictId;
      }
      record.setDimensionValues(dimensionValues);
      _rawStarTreeData.add(record);
    }

    // gathering metric column reader
    List<PinotSegmentColumnReader> metricColumnReaders = new ArrayList<>();
    for (int i = 0; i < _met2aggfuncPairsCount; i++) {
      String metricName = _met2aggfuncPairs.get(i).getMetricName();
      PinotSegmentColumnReader columnReader = new PinotSegmentColumnReader(_immutableSegment, metricName);
      metricColumnReaders.add(columnReader);
    }

    // gathering metric data ( raw data )
    for (int i = 0; i < _rawDocsCount; i++) {
      Record record = _rawStarTreeData.get(i);
      List<Object> metricRawValues = new ArrayList<>();
      for (int j = 0; j < _met2aggfuncPairsCount; j++) {
        String metricName = _met2aggfuncPairs.get(j).getMetricName();
        MetricFieldSpec metricFieldSpec = _metricsSpecMap.get(metricName);
        Object val = readHelper(metricColumnReaders.get(j), metricFieldSpec.getDataType(), i);
        metricRawValues.add(val);
      }
      metricRawValues.add(1L);   // for the count, initially '1' for single document.
      record.setMetricValues(metricRawValues);
    }

    // calculating default split order in case null provided.
    if (_dimensionsSplitOrder.isEmpty() || _dimensionsSplitOrder == null) {
      _dimensionsSplitOrder =
          OnHeapStarTreeV2BuilderHelper.computeDefaultSplitOrder(_dimensionsCount, _dimensionsCardinalty);

      List<String> dimensionSplitOrderStringList = new ArrayList<>();
      for (int i = 0; i < _dimensionsSplitOrder.size(); i++) {
        dimensionSplitOrderStringList.add(_dimensionsName.get(_dimensionsSplitOrder.get(i)));
      }

      _dimensionSplitOrderString = String.join(",", dimensionSplitOrderStringList);
    }

    // sorting the data as per the sort order.
    List<Record> rawSortedStarTreeData =
        OnHeapStarTreeV2BuilderHelper.sortStarTreeData(0, _rawDocsCount, _dimensionsSplitOrder, _rawStarTreeData);
    _starTreeData = OnHeapStarTreeV2BuilderHelper.condenseData(rawSortedStarTreeData, _met2aggfuncPairs,
        StarTreeV2Constant.RAW_DATA);

    // Recursively construct the star tree
    _rootNode._startDocId = 0;
    _rootNode._endDocId = _starTreeData.size();
    constructStarTree(_rootNode, 0, _starTreeData.size(), 0);

    // create aggregated doc for all nodes.
    createAggregatedDocForAllNodes(_rootNode);

    return;
  }

  @Override
  public void serialize() throws Exception {
    createIndexes();
    serializeTree(new File(_outDir, _starTreeId));

    // updating segment metadata by adding star tree meta data.
    Map<String, String> metadata = getMetaData();
    for (String key : metadata.keySet()) {
      String value = metadata.get(key);
      _properties.setProperty(key, value);
    }
    _properties.setProperty(StarTreeV2Constant.STAR_TREE_V2_COUNT, _starTreeCount);
    _properties.save();

    combineIndexesFiles(_starTreeCount - 1);

    return;
  }

  @Override
  public Map<String, String> getMetaData() {
    Map<String, String> metadata = new HashMap<>();

    String starTreeDocsCount = _starTreeId + "_" + StarTreeV2Constant.StarTreeMetadata.STAR_TREE_DOCS_COUNT;
    metadata.put(starTreeDocsCount, Integer.toString(_starTreeData.size()));

    String startTreeSplitOrder = _starTreeId + "_" + StarTreeV2Constant.StarTreeMetadata.STAR_TREE_SPLIT_ORDER;
    metadata.put(startTreeSplitOrder, _dimensionSplitOrderString);

    String startTreeMet2aggfuncPairs = _starTreeId + "_" + StarTreeV2Constant.StarTreeMetadata.STAR_TREE_MAT2FUNC_MAP;
    metadata.put(startTreeMet2aggfuncPairs, _met2aggfuncPairsString + "count");

    // TODO: have to add logic for SKIP_STAR_NODE_CREATION_FOR_DIMENSIONS

    return metadata;
  }

  /**
   * Helper method to combine all the files to one
   *
   * @return void.
   */
  private void combineIndexesFiles(int starTreeId) throws Exception {
    StarTreeIndexesConverter converter = new StarTreeIndexesConverter();
    converter.convert(_outDir, starTreeId);

    return;
  }

  /**
   * Helper method to serialize the start tree into a file.
   *
   * @return void.
   */
  private void serializeTree(File starTreeFile) throws IOException {
    int headerSizeInBytes = OnHeapStarTreeV2BuilderHelper.computeHeaderSizeInBytes(_dimensionsName);
    long totalSizeInBytes = headerSizeInBytes + _nodesCount * OffHeapStarTreeNode.SERIALIZABLE_SIZE_IN_BYTES;

    MMapBuffer dataBuffer = new MMapBuffer(starTreeFile, 0, totalSizeInBytes, MMapMode.READ_WRITE);

    try {
      long offset =
          OnHeapStarTreeV2BuilderHelper.writeHeader(dataBuffer, headerSizeInBytes, _dimensionsCount, _dimensionsName,
              _nodesCount);
      OnHeapStarTreeV2BuilderHelper.writeNodes(dataBuffer, offset, _rootNode);
    } finally {
      dataBuffer.flush();
      dataBuffer.close();
    }
  }

  /**
   * Helper function to create indexes and index values.
   *
   * @return void.
   */
  private void createIndexes() throws Exception {

    _dimensionForwardIndexCreatorList = new ArrayList<>();
    _metricForwardIndexCreatorList = new ArrayList<>();

    // 'SingleValueForwardIndexCreator' for dimensions.
    for (String dimensionName : _dimensionsName) {
      DimensionFieldSpec spec = _dimensionsSpecMap.get(dimensionName);
      int cardinality = _immutableSegment.getDictionary(dimensionName).length();

      _dimensionForwardIndexCreatorList.add(
          new SingleValueUnsortedForwardIndexCreator(spec, _outDir, cardinality, _starTreeData.size(),
              _starTreeData.size(), false));
    }

    // 'SingleValueRawIndexCreator' for metrics
    for (Met2AggfuncPair pair : _met2aggfuncPairs) {
      String columnName = pair._metricName + '_' + pair._aggregatefunction;
      AggregationFunction function = _aggregationFunctionFactory.getAggregationFunction(pair._aggregatefunction);

      SingleValueRawIndexCreator rawIndexCreator = SegmentColumnarIndexCreator.getRawIndexCreatorForColumn(_outDir,
          ChunkCompressorFactory.CompressionType.PASS_THROUGH, columnName, function.getDatatype(), _starTreeData.size(),
          V1Constants.Numbers.DOUBLE_SIZE);

      _metricForwardIndexCreatorList.add(rawIndexCreator);
    }

    // 'SingleValueRawIndexCreator' for count(*)
    String columnName = "count";
    AggregationFunction function =
        _aggregationFunctionFactory.getAggregationFunction(StarTreeV2Constant.AggregateFunctions.COUNT);
    SingleValueRawIndexCreator rawIndexCreator = SegmentColumnarIndexCreator.getRawIndexCreatorForColumn(_outDir,
        ChunkCompressorFactory.CompressionType.PASS_THROUGH, columnName, function.getDatatype(), _starTreeData.size(),
        V1Constants.Numbers.DOUBLE_SIZE);

    _metricForwardIndexCreatorList.add(rawIndexCreator);

    // indexing each record.
    for (int i = 0; i < _starTreeData.size(); i++) {
      Record row = _starTreeData.get(i);
      int[] dimension = row.getDimensionValues();
      List<Object> metric = row.getMetricValues();

      for (int j = 0; j < dimension.length; j++) {
        int val = dimension[j];

        // if condition to skip -1 value for star nodes.
        if (dimension[j] == StarTreeV2Constant.SKIP_VALUE) {
          val = StarTreeV2Constant.VALID_INDEX_VALUE;
        }
        ((SingleValueForwardIndexCreator) _dimensionForwardIndexCreatorList.get(j)).index(i, val);
      }

      for (int j = 0; j < metric.size(); j++) {
        ((SingleValueRawIndexCreator) _metricForwardIndexCreatorList.get(j)).index(i, metric.get(j));
      }
    }

    // closing all the opened index creator.
    for (int i = 0; i < _dimensionForwardIndexCreatorList.size(); i++) {
      _dimensionForwardIndexCreatorList.get(i).close();
    }

    for (int i = 0; i < _metricForwardIndexCreatorList.size(); i++) {
      _metricForwardIndexCreatorList.get(i).close();
    }

    return;
  }

  /**
   * Helper function to construct a star tree.
   *
   * @return void.
   */
  private void constructStarTree(TreeNode node, int startDocId, int endDocId, int level) throws IOException {
    if (level == _dimensionsSplitOrder.size()) {
      return;
    }

    int numDocs = endDocId - startDocId;
    int splitDimensionId = _dimensionsSplitOrder.get(level);
    Map<Integer, Pairs.IntPair> dimensionRangeMap = groupOnDimension(startDocId, endDocId, splitDimensionId);

    node._childDimensionId = splitDimensionId;

    // reserve one space for star node
    Map<Integer, TreeNode> children = new HashMap<>(dimensionRangeMap.size() + 1);

    node._children = children;
    for (Integer key : dimensionRangeMap.keySet()) {
      int childDimensionValue = key;
      Pairs.IntPair range = dimensionRangeMap.get(childDimensionValue);

      TreeNode child = new TreeNode();
      child._dimensionValue = key;
      child._dimensionId = splitDimensionId;
      int childStartDocId = range.getLeft();
      child._startDocId = childStartDocId;
      int childEndDocId = range.getRight();
      child._endDocId = childEndDocId;
      children.put(childDimensionValue, child);
      if (childEndDocId - childStartDocId > _maxNumLeafRecords) {
        constructStarTree(child, childStartDocId, childEndDocId, level + 1);
      }
      _nodesCount++;
    }

    // directly return if we don't need to create star-node
    if (_dimensionsWithoutStarNode != null && _dimensionsWithoutStarNode.contains(splitDimensionId)) {
      return;
    }

    // create a star node
    TreeNode starChild = new TreeNode();
    starChild._dimensionId = splitDimensionId;
    int starChildStartDocId = _starTreeData.size();
    starChild._startDocId = starChildStartDocId;
    starChild._dimensionValue = StarTreeV2Constant.STAR_NODE;

    children.put(StarTreeV2Constant.STAR_NODE, starChild);
    _nodesCount++;

    List<Record> sortedFilteredData =
        OnHeapStarTreeV2BuilderHelper.filterData(startDocId, endDocId, splitDimensionId, _dimensionsSplitOrder,
            _starTreeData);
    List<Record> condensedData = OnHeapStarTreeV2BuilderHelper.condenseData(sortedFilteredData, _met2aggfuncPairs,
        StarTreeV2Constant.AGGREGATED_DATA);
    _starTreeData.addAll(condensedData);

    int starChildEndDocId = _starTreeData.size();
    starChild._endDocId = starChildEndDocId;

    if (starChildEndDocId - starChildStartDocId > _maxNumLeafRecords) {
      constructStarTree(starChild, starChildStartDocId, starChildEndDocId, level + 1);
    }
  }

  /**
   * Group all documents based on a dimension's value.
   *
   * @return Map from dimension value to a pair of start docId and end docId (exclusive)
   */
  private Map<Integer, Pairs.IntPair> groupOnDimension(int startDocId, int endDocId, Integer dimensionId) {
    Map<Integer, Pairs.IntPair> rangeMap = new HashMap<>();
    int[] rowDimensions = _starTreeData.get(startDocId).getDimensionValues();
    int currentValue = rowDimensions[dimensionId];

    int groupStartDocId = startDocId;

    for (int i = startDocId + 1; i < endDocId; i++) {
      rowDimensions = _starTreeData.get(i).getDimensionValues();
      int value = rowDimensions[dimensionId];
      if (value != currentValue) {
        int groupEndDocId = i;
        rangeMap.put(currentValue, new Pairs.IntPair(groupStartDocId, groupEndDocId));
        currentValue = value;
        groupStartDocId = groupEndDocId;
      }
    }
    rangeMap.put(currentValue, new Pairs.IntPair(groupStartDocId, endDocId));

    return rangeMap;
  }

  /**
   * Helper function to read value of a doc in a column
   *
   * @return Object
   */
  private Object readHelper(PinotSegmentColumnReader reader, FieldSpec.DataType dataType, int docId) {
    switch (dataType) {
      case INT:
        return reader.readInt(docId);
      case FLOAT:
        return reader.readFloat(docId);
      case LONG:
        return reader.readLong(docId);
      case DOUBLE:
        return reader.readDouble(docId);
      case STRING:
        return reader.readString(docId);
    }

    return null;
  }

  /**
   * Helper function to create aggregated document for all nodes
   *
   * @return void.
   */
  private void createAggregatedDocForAllNodes(TreeNode node) {

    if (node._children == null) {
      List<Object> aggregatedValues = getAggregatedDocument(node._startDocId, node._endDocId);
      int aggDocId = appendAggregatedDocuments(aggregatedValues, node);
      node._aggDataDocumentId = aggDocId;
      return;
    }

    Map<Integer, TreeNode> children = node._children;
    for (int key : children.keySet()) {
      TreeNode child = children.get(key);
      createAggregatedDocForAllNodes(child);
    }

    int aggDocId = calculateAggregatedDocumentFromChildren(node);
    node._aggDataDocumentId = aggDocId;

    return;
  }

  /**
   * Helper function to create aggregated document from children for a parent node.
   *
   * @return aggregated document id.
   */
  private Integer calculateAggregatedDocumentFromChildren(TreeNode parent) {
    Map<Integer, TreeNode> children = parent._children;
    List<Record> chilAggRecordsList = new ArrayList<>();

    for (int key : children.keySet()) {
      TreeNode child = children.get(key);
      child._dimensionValue = key;
      if (child._dimensionValue == StarTreeV2Constant.STAR_NODE) {
        return child._aggDataDocumentId;
      } else {
        chilAggRecordsList.add(_starTreeData.get(child._aggDataDocumentId));
      }
    }
    List<Object> aggregatedValues =
        OnHeapStarTreeV2BuilderHelper.aggregateMetrics(0, chilAggRecordsList.size(), chilAggRecordsList,
            _met2aggfuncPairs, StarTreeV2Constant.AGGREGATED_DATA);
    int aggDocId = appendAggregatedDocuments(aggregatedValues, parent);

    return aggDocId;
  }

  /**
   * Create a aggregated document for this range.
   *
   * @return list of all metric2aggfunc value.
   */
  private List<Object> getAggregatedDocument(int startDocId, int endDocId) {

    List<Object> aggregatedValues =
        OnHeapStarTreeV2BuilderHelper.aggregateMetrics(startDocId, endDocId, _starTreeData, _met2aggfuncPairs,
            StarTreeV2Constant.AGGREGATED_DATA);

    return aggregatedValues;
  }

  /**
   * Append a aggregated document to star tree data.
   *
   * @return aggregated document id.
   */
  private int appendAggregatedDocuments(List<Object> aggregatedValues, TreeNode node) {
    int size = _starTreeData.size();

    Record aggRecord = new Record();
    int[] dimensionsValue = new int[_dimensionsCount];
    for (int i = 0; i < _dimensionsCount; i++) {
      dimensionsValue[i] = StarTreeV2Constant.SKIP_VALUE;
    }
    dimensionsValue[node._dimensionId] = node._dimensionValue;

    aggRecord.setDimensionValues(dimensionsValue);
    aggRecord.setMetricValues(aggregatedValues);
    _starTreeData.add(aggRecord);

    return size;
  }
}
