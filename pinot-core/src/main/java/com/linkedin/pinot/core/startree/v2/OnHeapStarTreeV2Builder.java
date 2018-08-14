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

import com.google.common.base.Preconditions;
import com.linkedin.pinot.common.data.DimensionFieldSpec;
import com.linkedin.pinot.common.data.FieldSpec;
import com.linkedin.pinot.common.data.MetricFieldSpec;
import com.linkedin.pinot.common.segment.ReadMode;
import com.linkedin.pinot.common.utils.Pairs;
import com.linkedin.pinot.core.data.readers.PinotSegmentColumnReader;
import com.linkedin.pinot.core.indexsegment.generator.SegmentVersion;
import com.linkedin.pinot.core.indexsegment.immutable.ImmutableSegmentLoader;
import com.linkedin.pinot.core.io.compression.ChunkCompressorFactory;
import com.linkedin.pinot.core.query.aggregation.function.AggregationFunctionType;
import com.linkedin.pinot.core.segment.creator.ForwardIndexCreator;
import com.linkedin.pinot.core.segment.creator.SingleValueForwardIndexCreator;
import com.linkedin.pinot.core.segment.creator.SingleValueRawIndexCreator;
import com.linkedin.pinot.core.segment.creator.impl.SegmentColumnarIndexCreator;
import com.linkedin.pinot.core.segment.creator.impl.V1Constants;
import com.linkedin.pinot.core.segment.creator.impl.fwd.SingleValueUnsortedForwardIndexCreator;
import com.linkedin.pinot.core.segment.index.loader.IndexLoadingConfig;
import com.linkedin.pinot.core.segment.index.readers.Dictionary;
import com.linkedin.pinot.core.segment.index.readers.ImmutableDictionaryReader;
import com.linkedin.pinot.core.segment.memory.PinotDataBuffer;
import com.linkedin.pinot.core.startree.OffHeapStarTreeNode;
import java.io.File;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class OnHeapStarTreeV2Builder extends StarTreeV2BaseClass implements StarTreeV2Builder {

  // Star Tree
  private int _starTreeCount = 0;
  private String _starTreeId = null;
  private List<Record> _starTreeData;
  private List<AggregationFunction> _aggregationFunctions;

  Map<String, Dictionary> _dictionary = new HashMap<>();

  private static final Logger LOGGER = LoggerFactory.getLogger(OnHeapStarTreeV2Builder.class);

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
    _dimensionsSplitOrder = enumerateDimensions(_dimensionsName, dimensionsSplitOrder);
    List<String> dimensionsWithoutStarNode = config.getDimensionsWithoutStarNode();
    _dimensionsWithoutStarNode = enumerateDimensions(_dimensionsName, dimensionsWithoutStarNode);

    LOGGER.info("Dimensions Split Order: {}", _dimensionsSplitOrder);
    LOGGER.info("Dimensions without star node: {}", dimensionsWithoutStarNode);

    // metric
    _aggFunColumnPairsString = "";
    _metricsName = new HashSet<>();
    _metricsSpecMap = new HashMap<>();
    _aggFunColumnPairs = config.getMetric2aggFuncPairs();
    _aggFunColumnPairsCount = _aggFunColumnPairs.size();
    List<String> aggFunColumnPairsStringList = new ArrayList<>();
    for (AggregationFunctionColumnPair pair : _aggFunColumnPairs) {
      _metricsName.add(pair.getColumn());
      aggFunColumnPairsStringList.add(pair.toColumnName());
    }
    _aggFunColumnPairsString = String.join(", ", aggFunColumnPairsStringList);
    _metricsCount = _metricsName.size();

    List<MetricFieldSpec> _metricsSpecList = _segmentMetadata.getSchema().getMetricFieldSpecs();
    for (MetricFieldSpec metric : _metricsSpecList) {
      if (_metricsName.contains(metric.getName())) {
        _metricsSpecMap.put(metric.getName(), metric);
      }
    }

    LOGGER.info("Dimensions Name: {}", _dimensionsName);
    LOGGER.info("AggFun Column Pairs String: {}", _aggFunColumnPairsString);

    // other initialisation
    _starTreeId = StarTreeV2Constant.STAR_TREE + '_' + Integer.toString(_starTreeCount);
    _outDir = config.getOutDir();
    _maxNumLeafRecords = config.getMaxNumLeafRecords();
    _rootNode = new TreeNode();
    _rootNode._dimensionValue = StarTreeV2Constant.STAR_NODE;
    _rootNode._dimensionId = StarTreeV2Constant.STAR_NODE;
    _nodesCount++;
    _starTreeCount++;
    _aggregationFunctions = new ArrayList<>();

    File metadataFile = StarTreeV2BaseClass.findFormatFile(indexDir, V1Constants.MetadataKeys.METADATA_FILE_NAME);
    _properties = new PropertiesConfiguration(metadataFile);
  }

  @Override
  public void build() throws Exception {

    _starTreeData = new ArrayList<>();
    _dimensionsCardinality = new ArrayList<>();
    List<Record> _rawStarTreeData = new ArrayList<>();

    // storing dimension cardinality for calculating default sorting order.
    for (int i = 0; i < _dimensionsCount; i++) {
      String dimensionName = _dimensionsName.get(i);
      ImmutableDictionaryReader dictionary = _immutableSegment.getDictionary(dimensionName);
      _dictionary.put(dimensionName, dictionary);
      _dimensionsCardinality.add(dictionary.length());
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

    LOGGER.info("Number of raw documents in the segment: {}", _rawStarTreeData.size());

    // gathering metric column reader
    Map<String, PinotSegmentColumnReader> metricColumnReaders = new HashMap<>();
    for (String metricName : _metricsSpecMap.keySet()) {
      PinotSegmentColumnReader columnReader = new PinotSegmentColumnReader(_immutableSegment, metricName);
      metricColumnReaders.put(metricName, columnReader);
    }

    // collecting all aggFunColObjects
    for (AggregationFunctionColumnPair pair : _aggFunColumnPairs) {
      AggregationFunction function =
          AggregationFunctionFactory.getAggregationFunction(pair.getFunctionType().getName());

      _aggregationFunctions.add(function);
    }

    // gathering metric data ( raw data )
    for (int i = 0; i < _rawDocsCount; i++) {
      Record record = _rawStarTreeData.get(i);
      List<Object> metricRawValues = new ArrayList<>();
      for (int j = 0; j < _aggFunColumnPairsCount; j++) {
        String metricName = _aggFunColumnPairs.get(j).getColumn();
        String aggFunc = _aggFunColumnPairs.get(j).getFunctionType().getName();
        AggregationFunction function = _aggregationFunctions.get(j);
        if (aggFunc.equals(AggregationFunctionType.COUNT.getName())) {
          metricRawValues.add(function.convert(1));
        } else {
          MetricFieldSpec metricFieldSpec = _metricsSpecMap.get(metricName);
          Object val = readHelper(metricColumnReaders.get(metricName), metricFieldSpec.getDataType(), i);
          metricRawValues.add(function.convert(val));
        }
      }
      record.setAggregatedValues(metricRawValues);
    }

    computeDefaultSplitOrder(_dimensionsCardinality);

    long start = System.currentTimeMillis();

    // sorting the data as per the sort order.
    List<Record> rawSortedStarTreeData = sortStarTreeData(0, _rawDocsCount, _dimensionsSplitOrder, _rawStarTreeData);
    _starTreeData = condenseData(rawSortedStarTreeData, _aggFunColumnPairs, StarTreeV2Constant.IS_RAW_DATA);

    // recursively construct the star tree
    _rootNode._startDocId = 0;
    _rootNode._endDocId = _starTreeData.size();
    constructStarTree(_rootNode, 0, _starTreeData.size(), 0);

    // create aggregated doc for all nodes.
    createAggregatedDocForAllNodes();

    long end = System.currentTimeMillis();
    LOGGER.info("Took {}ms to build star tree index with {} aggregated documents", (end - start), _starTreeData.size());

    // serialize all the files and tree.
    serialize();
  }

  /**
   * Helper function to construct a star tree.
   *
   * @param node TreeNode to work on
   * @param startDocId int start index in star tree data.
   * @param endDocId int end index in star tree data.
   * @param level int dimension split order level.
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

    // directly return if we don't need to create star-node or there is just one child node.
    if ((_dimensionsWithoutStarNode != null && _dimensionsWithoutStarNode.contains(splitDimensionId))
        || dimensionRangeMap.size() == 1) {
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
        filterData(startDocId, endDocId, splitDimensionId, _dimensionsSplitOrder, _starTreeData);
    List<Record> condensedData = condenseData(sortedFilteredData, _aggFunColumnPairs, !StarTreeV2Constant.IS_RAW_DATA);

    _starTreeData.addAll(condensedData);

    int starChildEndDocId = _starTreeData.size();
    starChild._endDocId = starChildEndDocId;

    if (starChildEndDocId - starChildStartDocId > _maxNumLeafRecords) {
      constructStarTree(starChild, starChildStartDocId, starChildEndDocId, level + 1);
    }
  }

  /**
   * Helper function to serialize tree.
   */
  private void serialize() throws Exception {
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

    // combining all the indexes and star tree in one file.
    combineIndexesFiles(_starTreeCount - 1);
  }

  /**
   * Helper method to create star-tree meta data for saving to segment meta data
   */
  private Map<String, String> getMetaData() {
    Map<String, String> metadata = new HashMap<>();

    String starTreeDocsCount = _starTreeId + "_" + StarTreeV2Constant.StarTreeMetadata.STAR_TREE_DOCS_COUNT;
    metadata.put(starTreeDocsCount, Integer.toString(_starTreeData.size()));

    String startTreeSplitOrder = _starTreeId + "_" + StarTreeV2Constant.StarTreeMetadata.STAR_TREE_SPLIT_ORDER;
    metadata.put(startTreeSplitOrder, _dimensionSplitOrderString);

    String withoutStarNode =
        _starTreeId + "_" + StarTreeV2Constant.StarTreeMetadata.STAR_TREE_SKIP_STAR_NODE_CREATION_FOR_DIMENSIONS;
    metadata.put(withoutStarNode, _dimensionWithoutStarNodeString);

    String startTreeMet2aggfuncPairs =
        _starTreeId + "_" + StarTreeV2Constant.StarTreeMetadata.STAR_TREE_AGG_FUN_COL_PAIR;
    metadata.put(startTreeMet2aggfuncPairs, _aggFunColumnPairsString);

    String maxNumLeafRecords = _starTreeId + "_" + StarTreeV2Constant.StarTreeMetadata.STAR_TREE_MAX_LEAF_RECORD;
    metadata.put(maxNumLeafRecords, Integer.toString(_maxNumLeafRecords));

    return metadata;
  }

  /**
   * Helper function to group all documents based on a dimension's value.
   *
   * @param startDocId int start index in star tree data.
   * @param endDocId int end index in star tree data.
   * @param dimensionId dimension id to group on.
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
        rangeMap.put(currentValue, new Pairs.IntPair(groupStartDocId, i));
        currentValue = value;
        groupStartDocId = i;
      }
    }
    rangeMap.put(currentValue, new Pairs.IntPair(groupStartDocId, endDocId));

    return rangeMap;
  }

  /**
   * Helper function to create aggregated document for all nodes
   */
  private void createAggregatedDocForAllNodes() {

    int[] dimensions = new int[_dimensionsCount];
    for (int i = 0; i < _dimensionsCount; i++) {
      dimensions[i] = StarTreeV2Constant.STAR_NODE;
    }
    createAggregatedDocForAllNodesHelper(_rootNode, dimensions);
  }

  /**
   * Helper function to create aggregated document for all nodes
   * @param node 'TreeNode' to create aggregate documents for.
   * @param dimension 'Array' int array for keeping dimensions value.
   *
   *
   */
  private void createAggregatedDocForAllNodesHelper(TreeNode node, int[] dimension) {
    List<Object> aggregatedValues;
    List<Record> childAggRecordsList = new ArrayList<>();

    if (node._children == null) {
      aggregatedValues = aggregateMetrics(node._startDocId, node._endDocId, _starTreeData, _aggFunColumnPairs, !StarTreeV2Constant.IS_RAW_DATA);
      node._aggDataDocumentId = appendAggregatedDocument(aggregatedValues, dimension);

    } else {
      boolean hasStarChild = false;
      TreeNode starChild = null;
      int childDimensionId = node._childDimensionId;
      Map<Integer, TreeNode> children = node._children;
      for (int key : children.keySet()) {
        int childDimensionValue = key;
        TreeNode child = children.get(key);
        dimension[childDimensionId] = childDimensionValue;
        createAggregatedDocForAllNodesHelper(child, dimension);
        if (childDimensionValue == StarTreeV2Constant.STAR_NODE) {
          hasStarChild = true;
          starChild = child;
        } else {
          childAggRecordsList.add(_starTreeData.get(child._aggDataDocumentId));
        }
      }
      dimension[childDimensionId] = StarTreeV2Constant.STAR_NODE;

      if (!hasStarChild) {
        aggregatedValues = aggregateMetrics(0, childAggRecordsList.size(), childAggRecordsList, _aggFunColumnPairs, !StarTreeV2Constant.IS_RAW_DATA);
        node._aggDataDocumentId = appendAggregatedDocument(aggregatedValues, dimension);
      } else {
        node._aggDataDocumentId = starChild._aggDataDocumentId;
      }
    }
  }

  /**
   * Helper function to append a aggregated document to star tree data.
   *
   * @param aggregatedValues 'List' of all aggregated values
   * @param dimension 'int[]' dimension value to be added.
   *
   * @return aggregated document id.
   */
  private int appendAggregatedDocument(List<Object> aggregatedValues, int[] dimension) {

    int size = _starTreeData.size();
    int[] d = new int[_dimensionsCount];
    for (int i = 0; i < _dimensionsCount; i++) {
      d[i] = dimension[i];
    }

    Record aggRecord = new Record();
    aggRecord.setDimensionValues(d);
    aggRecord.setAggregatedValues(aggregatedValues);
    _starTreeData.add(aggRecord);

    return size;
  }

  /**
   * Helper method to combine all the files to one
   */
  private void combineIndexesFiles(int starTreeId) throws Exception {
    StarTreeIndexesConverter converter = new StarTreeIndexesConverter();
    converter.convert(_outDir, starTreeId);
  }

  /**
   * Helper method to serialize the start tree into a file.
   */

  private void serializeTree(File starTreeFile) throws IOException {
    int headerSizeInBytes = computeHeaderSizeInBytes(_dimensionsName);
    long totalSizeInBytes = headerSizeInBytes + _nodesCount * OffHeapStarTreeNode.SERIALIZABLE_SIZE_IN_BYTES;

    try (PinotDataBuffer buffer = PinotDataBuffer.mapFile(starTreeFile, false, 0, totalSizeInBytes,
        ByteOrder.LITTLE_ENDIAN, "OffHeapStarTreeBuilder#serializeTree: star-tree buffer")) {
      long offset = writeHeader(buffer, headerSizeInBytes, _dimensionsCount, _dimensionsName, _nodesCount);
      Preconditions.checkState(offset == headerSizeInBytes, "Error writing Star Tree file, header size mis-match");
      writeNodes(buffer, offset, _rootNode);
    }
  }

  /**
   * Helper function to create indexes and index values.
   */
  private void createIndexes() throws Exception {

    List<ForwardIndexCreator> _dimensionForwardIndexCreatorList = new ArrayList<>();
    List<ForwardIndexCreator> _aggFunColumnPairForwardIndexCreatorList = new ArrayList<>();

    // 'SingleValueForwardIndexCreator' for dimensions.
    for (String dimensionName : _dimensionsName) {
      DimensionFieldSpec spec = _dimensionsSpecMap.get(dimensionName);
      int cardinality = _immutableSegment.getDictionary(dimensionName).length();

      _dimensionForwardIndexCreatorList.add(
          new SingleValueUnsortedForwardIndexCreator(spec, _outDir, cardinality, _starTreeData.size(),
              _starTreeData.size(), false));
    }

    // 'SingleValueRawIndexCreator' for metrics
    int index = 0;
    for (AggregationFunctionColumnPair pair : _aggFunColumnPairs) {
      String columnName = pair.toColumnName();
      AggregationFunction function = _aggregationFunctions.get(index);
      index++;
      SingleValueRawIndexCreator rawIndexCreator = SegmentColumnarIndexCreator.getRawIndexCreatorForColumn(_outDir,
          ChunkCompressorFactory.CompressionType.PASS_THROUGH, columnName, function.getResultDataType(),
          _starTreeData.size(), function.getResultMaxByteSize());
      _aggFunColumnPairForwardIndexCreatorList.add(rawIndexCreator);
    }

    // indexing each record.
    for (int i = 0; i < _starTreeData.size(); i++) {
      Record row = _starTreeData.get(i);
      int[] dimension = row.getDimensionValues();
      List<Object> metric = row.getAggregatedValues();

      // indexing dimension data.
      for (int j = 0; j < dimension.length; j++) {
        int val = (dimension[j] == StarTreeV2Constant.SKIP_VALUE) ? StarTreeV2Constant.VALID_INDEX_VALUE : dimension[j];
        ((SingleValueForwardIndexCreator) _dimensionForwardIndexCreatorList.get(j)).index(i, val);
      }

      // indexing AggfunColumn Pair data.
      for (int j = 0; j < metric.size(); j++) {
        AggregationFunction function = _aggregationFunctions.get(j);
        if (function.getResultDataType().equals(FieldSpec.DataType.BYTES)) {
          ((SingleValueRawIndexCreator) _aggFunColumnPairForwardIndexCreatorList.get(j)).index(i,
              function.serialize(metric.get(j)));
        } else {
          ((SingleValueRawIndexCreator) _aggFunColumnPairForwardIndexCreatorList.get(j)).index(i, metric.get(j));
        }
      }
    }

    // closing all the opened index creator.
    for (int i = 0; i < _dimensionForwardIndexCreatorList.size(); i++) {
      _dimensionForwardIndexCreatorList.get(i).close();
    }

    for (int i = 0; i < _aggFunColumnPairForwardIndexCreatorList.size(); i++) {
      _aggFunColumnPairForwardIndexCreatorList.get(i).close();
    }

    return;
  }

  /**
   * sort the star tree data.
   */
  List<Record> sortStarTreeData(int startDocId, int endDocId, List<Integer> sortOrder, List<Record> starTreeData) {

    List<Record> newData = new ArrayList<>();
    for (int i = startDocId; i < endDocId; i++) {
      newData.add(starTreeData.get(i));
    }

    Collections.sort(newData, new Comparator<Record>() {
      @Override
      public int compare(Record o1, Record o2) {
        int compare = 0;
        for (int index : sortOrder) {
          compare = o1.getDimensionValues()[index] - o2.getDimensionValues()[index];
          if (compare != 0) {
            return compare;
          }
        }
        return compare;
      }
    });

    return newData;
  }

  /**
   * Filter data by removing the dimension we don't need.
   */
  List<Record> filterData(int startDocId, int endDocId, int dimensionIdToRemove, List<Integer> sortOrder,
      List<Record> starTreeData) {

    List<Record> newData = new ArrayList<>();

    for (int i = startDocId; i < endDocId; i++) {
      Record record = starTreeData.get(i);
      int[] dimension = record.getDimensionValues().clone();
      List<Object> metric = record.getAggregatedValues();
      dimension[dimensionIdToRemove] = StarTreeV2Constant.SKIP_VALUE;

      Record newRecord = new Record();
      newRecord.setDimensionValues(dimension);
      newRecord.setAggregatedValues(metric);

      newData.add(newRecord);
    }
    return sortStarTreeData(0, newData.size(), sortOrder, newData);
  }

  /**
   * function to condense documents according to sorted order.
   */
  List<Record> condenseData(List<Record> starTreeData, List<AggregationFunctionColumnPair> aggfunColumnPairs,
      boolean isRawData) {
    int start = 0;
    List<Record> newData = new ArrayList<>();
    Record prevRecord = starTreeData.get(0);

    for (int i = 1; i < starTreeData.size(); i++) {
      Record nextRecord = starTreeData.get(i);
      int[] prevDimensions = prevRecord.getDimensionValues();
      int[] nextDimensions = nextRecord.getDimensionValues();

      if (!Record.compareDimensions(prevDimensions, nextDimensions)) {
        List<Object> aggregatedMetricsValue = aggregateMetrics(start, i, starTreeData, aggfunColumnPairs, isRawData);
        Record newRecord = new Record();
        newRecord.setAggregatedValues(aggregatedMetricsValue);
        newRecord.setDimensionValues(prevRecord.getDimensionValues());
        newData.add(newRecord);
        prevRecord = nextRecord;
        start = i;
      }
    }
    Record record = new Record();
    record.setDimensionValues(starTreeData.get(start).getDimensionValues());
    List<Object> aggregatedMetricsValue =
        aggregateMetrics(start, starTreeData.size(), starTreeData, aggfunColumnPairs, isRawData);
    record.setAggregatedValues(aggregatedMetricsValue);
    newData.add(record);

    return newData;
  }

  /**
   * aggregate metric values ( raw or aggregated )
   */
  private List<Object> aggregateMetrics(int start, int end, List<Record> starTreeData,
      List<AggregationFunctionColumnPair> aggfunColumnPairs, boolean isRawData) {

    List<Object> aggregatedMetricsValue = new ArrayList<>();
    for (int i = 0; i < aggfunColumnPairs.size(); i++) {
      AggregationFunction function = _aggregationFunctions.get(i);
      Object obj1 = starTreeData.get(start).getAggregatedValues().get(i);
      for (int j = start + 1; j < end; j++) {
        Object obj2 = starTreeData.get(j).getAggregatedValues().get(i);
        obj1 = function.aggregate(obj1, obj2);
      }
      aggregatedMetricsValue.add(obj1);
    }

    return aggregatedMetricsValue;
  }
}
