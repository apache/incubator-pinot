package com.linkedin.pinot.query.aggregation;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import junit.framework.Assert;

import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.linkedin.pinot.common.request.AggregationInfo;
import com.linkedin.pinot.common.request.BrokerRequest;
import com.linkedin.pinot.common.request.FilterOperator;
import com.linkedin.pinot.common.request.GroupBy;
import com.linkedin.pinot.common.response.BrokerResponse;
import com.linkedin.pinot.common.response.ServerInstance;
import com.linkedin.pinot.common.segment.ReadMode;
import com.linkedin.pinot.common.utils.DataTable;
import com.linkedin.pinot.common.utils.NamedThreadFactory;
import com.linkedin.pinot.common.utils.request.FilterQueryTree;
import com.linkedin.pinot.common.utils.request.RequestUtils;
import com.linkedin.pinot.core.block.query.IntermediateResultsBlock;
import com.linkedin.pinot.core.indexsegment.IndexSegment;
import com.linkedin.pinot.core.indexsegment.columnar.ColumnarSegmentLoader;
import com.linkedin.pinot.core.indexsegment.generator.SegmentGeneratorConfig;
import com.linkedin.pinot.core.operator.BDocIdSetOperator;
import com.linkedin.pinot.core.operator.DataSource;
import com.linkedin.pinot.core.operator.MProjectionOperator;
import com.linkedin.pinot.core.operator.UReplicatedProjectionOperator;
import com.linkedin.pinot.core.operator.query.AggregationFunctionGroupByOperator;
import com.linkedin.pinot.core.operator.query.MAggregationFunctionGroupByWithDictionaryAndTrieTreeOperator;
import com.linkedin.pinot.core.operator.query.MAggregationGroupByOperator;
import com.linkedin.pinot.core.plan.Plan;
import com.linkedin.pinot.core.plan.PlanNode;
import com.linkedin.pinot.core.plan.maker.InstancePlanMakerImplV1;
import com.linkedin.pinot.core.plan.maker.PlanMaker;
import com.linkedin.pinot.core.query.aggregation.CombineService;
import com.linkedin.pinot.core.query.aggregation.groupby.AggregationGroupByOperatorService;
import com.linkedin.pinot.core.query.reduce.DefaultReduceService;
import com.linkedin.pinot.core.segment.creator.SegmentIndexCreationDriver;
import com.linkedin.pinot.core.segment.creator.impl.SegmentCreationDriverFactory;
import com.linkedin.pinot.core.segment.index.IndexSegmentImpl;
import com.linkedin.pinot.core.segment.index.SegmentMetadataImpl;
import com.linkedin.pinot.core.segment.index.ColumnMetadata;
import com.linkedin.pinot.core.segment.index.readers.DictionaryReader;
import com.linkedin.pinot.core.time.SegmentTimeUnit;
import com.linkedin.pinot.segments.v1.creator.SegmentTestUtils;


public class TestAggregationGroupByWithDictionaryAndTrieTreeOperatorMultiValue {

  private final String AVRO_DATA = "data/mirror-mv.avro";
  private static File INDEX_DIR = new File("TestAggGroupByWithDictTrieOpMultiVal");
  private static File INDEXES_DIR = new File("TestAggGroupByWithDictTrieOpMultiValList");
  private static String SEGMENT_ID = "test_testTable_16381_16381_";

  public static IndexSegment _indexSegment;
  private static List<IndexSegment> _indexSegmentList;

  public static AggregationInfo _paramsInfo;
  public static List<AggregationInfo> _aggregationInfos;
  public static int _numAggregations = 6;

  public Map<String, DictionaryReader> _dictionaryMap;
  public Map<String, ColumnMetadata> _medataMap;
  public static GroupBy _groupBy;

  @BeforeClass
  public void setup() throws Exception {
    setupSegment();
    setupQuery();
    _indexSegmentList = new ArrayList<IndexSegment>();
  }

  @AfterClass
  public void tearDown() {
    if (INDEX_DIR.exists()) {
      FileUtils.deleteQuietly(INDEX_DIR);
    }
    if (INDEXES_DIR.exists()) {
      FileUtils.deleteQuietly(INDEXES_DIR);
    }
  }

  private void setupSegmentList(int numberOfSegments) throws Exception {
    final String filePath = getClass().getClassLoader().getResource(AVRO_DATA).getFile();

    if (INDEXES_DIR.exists()) {
      FileUtils.deleteQuietly(INDEXES_DIR);
    }
    INDEXES_DIR.mkdir();

    for (int i = 0; i < numberOfSegments; ++i) {
      final File segmentDir = new File(INDEXES_DIR, "segment_" + i);

      final SegmentGeneratorConfig config =
          SegmentTestUtils.getSegmentGenSpecWithSchemAndProjectedColumns(new File(filePath), segmentDir,
              "daysSinceEpoch", SegmentTimeUnit.days, "test", "testTable");

      final SegmentIndexCreationDriver driver = SegmentCreationDriverFactory.get(null);
      driver.init(config);
      driver.build();

      System.out.println("built at : " + segmentDir.getAbsolutePath());
      _indexSegmentList.add(ColumnarSegmentLoader.load(new File(segmentDir, SEGMENT_ID), ReadMode.heap));
    }
  }

  private void setupSegment() throws Exception {
    final String filePath = getClass().getClassLoader().getResource(AVRO_DATA).getFile();

    if (INDEX_DIR.exists()) {
      FileUtils.deleteQuietly(INDEX_DIR);
    }

    final SegmentGeneratorConfig config =
        SegmentTestUtils.getSegmentGenSpecWithSchemAndProjectedColumns(new File(filePath), INDEX_DIR, "daysSinceEpoch",
            SegmentTimeUnit.days, "test", "testTable");

    final SegmentIndexCreationDriver driver = SegmentCreationDriverFactory.get(null);
    driver.init(config);
    driver.build();

    System.out.println("built at : " + INDEX_DIR.getAbsolutePath());
    File indexSegmentDir = new File(INDEX_DIR, SEGMENT_ID);
    _indexSegment = ColumnarSegmentLoader.load(indexSegmentDir, ReadMode.heap);
    _dictionaryMap = ((IndexSegmentImpl) _indexSegment).getDictionaryMap();
    _medataMap =
        ((SegmentMetadataImpl) ((IndexSegmentImpl) _indexSegment).getSegmentMetadata()).getColumnMetadataMap();
  }

  private void setupQuery() {
    _aggregationInfos = getAggregationsInfo();
    final List<String> groupbyColumns = new ArrayList<String>();
    groupbyColumns.add("viewerCompanies");
    _groupBy = new GroupBy();
    _groupBy.setColumns(groupbyColumns);
    _groupBy.setTopN(10);
  }

  @Test
  public void testAggregationGroupBys() {
    final List<AggregationFunctionGroupByOperator> aggregationFunctionGroupByOperatorList =
        new ArrayList<AggregationFunctionGroupByOperator>();
    final BDocIdSetOperator docIdSetOperator = new BDocIdSetOperator(null, _indexSegment, 5000);
    final Map<String, DataSource> dataSourceMap = getDataSourceMap();
    final MProjectionOperator projectionOperator = new MProjectionOperator(dataSourceMap, docIdSetOperator);

    for (int i = 0; i < _numAggregations; ++i) {
      final MAggregationFunctionGroupByWithDictionaryAndTrieTreeOperator aggregationFunctionGroupByOperator =
          new MAggregationFunctionGroupByWithDictionaryAndTrieTreeOperator(_aggregationInfos.get(i), _groupBy,
              new UReplicatedProjectionOperator(projectionOperator));
      aggregationFunctionGroupByOperatorList.add(aggregationFunctionGroupByOperator);
    }

    final MAggregationGroupByOperator aggregationGroupByOperator =
        new MAggregationGroupByOperator(_indexSegment, _aggregationInfos, _groupBy, projectionOperator,
            aggregationFunctionGroupByOperatorList);

    System.out.println("running query: ");
    final IntermediateResultsBlock block = (IntermediateResultsBlock) aggregationGroupByOperator.nextBlock();
    for (int i = 0; i < _numAggregations; ++i) {
      System.out.println(block.getAggregationGroupByOperatorResult().get(i));
    }

  }

  @Test
  public void testAggregationGroupBysWithCombine() {
    final List<AggregationFunctionGroupByOperator> aggregationFunctionGroupByOperatorList =
        new ArrayList<AggregationFunctionGroupByOperator>();
    final BDocIdSetOperator docIdSetOperator = new BDocIdSetOperator(null, _indexSegment, 5000);
    final Map<String, DataSource> dataSourceMap = getDataSourceMap();
    final MProjectionOperator projectionOperator = new MProjectionOperator(dataSourceMap, docIdSetOperator);

    for (int i = 0; i < _numAggregations; ++i) {
      final MAggregationFunctionGroupByWithDictionaryAndTrieTreeOperator aggregationFunctionGroupByOperator =
          new MAggregationFunctionGroupByWithDictionaryAndTrieTreeOperator(_aggregationInfos.get(i), _groupBy,
              new UReplicatedProjectionOperator(projectionOperator));
      aggregationFunctionGroupByOperatorList.add(aggregationFunctionGroupByOperator);
    }

    final MAggregationGroupByOperator aggregationGroupByOperator =
        new MAggregationGroupByOperator(_indexSegment, _aggregationInfos, _groupBy, projectionOperator,
            aggregationFunctionGroupByOperatorList);

    final IntermediateResultsBlock block = (IntermediateResultsBlock) aggregationGroupByOperator.nextBlock();

    System.out.println("Result 1: ");
    for (int i = 0; i < _numAggregations; ++i) {
      System.out.println(block.getAggregationGroupByOperatorResult().get(i));
    }

    ////////////////////////////////////////////////////////////////////////////////
    final List<AggregationFunctionGroupByOperator> aggregationFunctionGroupByOperatorList1 =
        new ArrayList<AggregationFunctionGroupByOperator>();
    final BDocIdSetOperator docIdSetOperator1 = new BDocIdSetOperator(null, _indexSegment, 5000);
    final Map<String, DataSource> dataSourceMap1 = getDataSourceMap();
    final MProjectionOperator projectionOperator1 = new MProjectionOperator(dataSourceMap1, docIdSetOperator1);

    for (int i = 0; i < _numAggregations; ++i) {
      final MAggregationFunctionGroupByWithDictionaryAndTrieTreeOperator aggregationFunctionGroupByOperator1 =
          new MAggregationFunctionGroupByWithDictionaryAndTrieTreeOperator(_aggregationInfos.get(i), _groupBy,
              new UReplicatedProjectionOperator(projectionOperator1));
      aggregationFunctionGroupByOperatorList1.add(aggregationFunctionGroupByOperator1);
    }

    final MAggregationGroupByOperator aggregationGroupByOperator1 =
        new MAggregationGroupByOperator(_indexSegment, _aggregationInfos, _groupBy, projectionOperator1,
            aggregationFunctionGroupByOperatorList1);

    final IntermediateResultsBlock block1 = (IntermediateResultsBlock) aggregationGroupByOperator1.nextBlock();

    System.out.println("Result 2: ");
    for (int i = 0; i < _numAggregations; ++i) {
      System.out.println(block1.getAggregationGroupByOperatorResult().get(i));
    }

    CombineService.mergeTwoBlocks(getAggregationGroupByNoFilterBrokerRequest(), block, block1);

    System.out.println("Combined Result : ");
    for (int i = 0; i < _numAggregations; ++i) {
      System.out.println(block.getAggregationGroupByOperatorResult().get(i));
    }

  }

  @Test
  public void testAggregationGroupBysWithDataTableEncodeAndDecode() throws Exception {
    final List<AggregationFunctionGroupByOperator> aggregationFunctionGroupByOperatorList =
        new ArrayList<AggregationFunctionGroupByOperator>();
    final BDocIdSetOperator docIdSetOperator = new BDocIdSetOperator(null, _indexSegment, 5000);
    final Map<String, DataSource> dataSourceMap = getDataSourceMap();
    final MProjectionOperator projectionOperator = new MProjectionOperator(dataSourceMap, docIdSetOperator);

    for (int i = 0; i < _numAggregations; ++i) {
      final MAggregationFunctionGroupByWithDictionaryAndTrieTreeOperator aggregationFunctionGroupByOperator =
          new MAggregationFunctionGroupByWithDictionaryAndTrieTreeOperator(_aggregationInfos.get(i), _groupBy,
              new UReplicatedProjectionOperator(projectionOperator));
      aggregationFunctionGroupByOperatorList.add(aggregationFunctionGroupByOperator);
    }

    final MAggregationGroupByOperator aggregationGroupByOperator =
        new MAggregationGroupByOperator(_indexSegment, _aggregationInfos, _groupBy, projectionOperator,
            aggregationFunctionGroupByOperatorList);

    final IntermediateResultsBlock block = (IntermediateResultsBlock) aggregationGroupByOperator.nextBlock();

    System.out.println("Result 1: ");
    for (int i = 0; i < _numAggregations; ++i) {
      System.out.println(block.getAggregationGroupByOperatorResult().get(i));
    }

    ////////////////////////////////////////////////////////////////////////////////
    final List<AggregationFunctionGroupByOperator> aggregationFunctionGroupByOperatorList1 =
        new ArrayList<AggregationFunctionGroupByOperator>();
    final BDocIdSetOperator docIdSetOperator1 = new BDocIdSetOperator(null, _indexSegment, 5000);
    final Map<String, DataSource> dataSourceMap1 = getDataSourceMap();
    final MProjectionOperator projectionOperator1 = new MProjectionOperator(dataSourceMap1, docIdSetOperator1);

    for (int i = 0; i < _numAggregations; ++i) {
      final MAggregationFunctionGroupByWithDictionaryAndTrieTreeOperator aggregationFunctionGroupByOperator1 =
          new MAggregationFunctionGroupByWithDictionaryAndTrieTreeOperator(_aggregationInfos.get(i), _groupBy,
              new UReplicatedProjectionOperator(projectionOperator1));
      aggregationFunctionGroupByOperatorList1.add(aggregationFunctionGroupByOperator1);
    }

    final MAggregationGroupByOperator aggregationGroupByOperator1 =
        new MAggregationGroupByOperator(_indexSegment, _aggregationInfos, _groupBy, projectionOperator1,
            aggregationFunctionGroupByOperatorList1);

    final IntermediateResultsBlock block1 = (IntermediateResultsBlock) aggregationGroupByOperator1.nextBlock();

    System.out.println("Result 2: ");
    for (int i = 0; i < _numAggregations; ++i) {
      System.out.println(block1.getAggregationGroupByOperatorResult().get(i));
    }

    CombineService.mergeTwoBlocks(getAggregationGroupByNoFilterBrokerRequest(), block, block1);

    System.out.println("Combined Result : ");
    for (int i = 0; i < _numAggregations; ++i) {
      System.out.println(block.getAggregationGroupByOperatorResult().get(i));
    }

    final DataTable dataTable = block.getAggregationGroupByResultDataTable();

    final List<Map<String, Serializable>> results =
        AggregationGroupByOperatorService.transformDataTableToGroupByResult(dataTable);
    System.out.println("Decode AggregationResult from DataTable: ");
    for (int i = 0; i < _numAggregations; ++i) {
      System.out.println(results.get(i));
    }
  }

  @Test
  public void testInnerSegmentPlanMakerForAggregationGroupByOperatorNoFilter() throws Exception {
    final BrokerRequest brokerRequest = getAggregationGroupByNoFilterBrokerRequest();
    final PlanMaker instancePlanMaker = new InstancePlanMakerImplV1();
    final PlanNode rootPlanNode = instancePlanMaker.makeInnerSegmentPlan(_indexSegment, brokerRequest);
    rootPlanNode.showTree("");
    final MAggregationGroupByOperator operator = (MAggregationGroupByOperator) rootPlanNode.run();
    final IntermediateResultsBlock resultBlock = (IntermediateResultsBlock) operator.nextBlock();
    System.out.println("RunningTime : " + resultBlock.getTimeUsedMs());
    System.out.println("NumDocsScanned : " + resultBlock.getNumDocsScanned());
    System.out.println("TotalDocs : " + resultBlock.getTotalDocs());
    System.out.println(resultBlock.getAggregationGroupByResultDataTable());
    final AggregationGroupByOperatorService aggregationGroupByOperatorService =
        new AggregationGroupByOperatorService(_aggregationInfos, brokerRequest.getGroupBy());

    final Map<ServerInstance, DataTable> instanceResponseMap = new HashMap<ServerInstance, DataTable>();
    instanceResponseMap.put(new ServerInstance("localhost:0000"), resultBlock.getAggregationGroupByResultDataTable());
    instanceResponseMap.put(new ServerInstance("localhost:1111"), resultBlock.getAggregationGroupByResultDataTable());
    instanceResponseMap.put(new ServerInstance("localhost:2222"), resultBlock.getAggregationGroupByResultDataTable());
    instanceResponseMap.put(new ServerInstance("localhost:3333"), resultBlock.getAggregationGroupByResultDataTable());
    instanceResponseMap.put(new ServerInstance("localhost:4444"), resultBlock.getAggregationGroupByResultDataTable());
    instanceResponseMap.put(new ServerInstance("localhost:5555"), resultBlock.getAggregationGroupByResultDataTable());
    instanceResponseMap.put(new ServerInstance("localhost:6666"), resultBlock.getAggregationGroupByResultDataTable());
    instanceResponseMap.put(new ServerInstance("localhost:7777"), resultBlock.getAggregationGroupByResultDataTable());
    instanceResponseMap.put(new ServerInstance("localhost:8888"), resultBlock.getAggregationGroupByResultDataTable());
    instanceResponseMap.put(new ServerInstance("localhost:9999"), resultBlock.getAggregationGroupByResultDataTable());
    final List<Map<String, Serializable>> reducedResults =
        aggregationGroupByOperatorService.reduceGroupByOperators(instanceResponseMap);
    //    System.out.println("********************************");
    //    for (int i = 0; i < reducedResults.size(); ++i) {
    //      Map<String, Serializable> groupByResult = reducedResults.get(i);
    //      System.out.println(groupByResult);
    //    }
    //    System.out.println("********************************");
    final List<JSONObject> jsonResult = aggregationGroupByOperatorService.renderGroupByOperators(reducedResults);
    System.out.println(jsonResult);
    //    System.out.println("********************************");
  }

  @Test
  public void testInnerSegmentPlanMakerForAggregationGroupByOperatorWithFilter() throws Exception {
    final BrokerRequest brokerRequest = getAggregationGroupByWithFilterBrokerRequest();
    final PlanMaker instancePlanMaker = new InstancePlanMakerImplV1();
    final PlanNode rootPlanNode = instancePlanMaker.makeInnerSegmentPlan(_indexSegment, brokerRequest);
    rootPlanNode.showTree("");
    // UAggregationGroupByOperator operator = (UAggregationGroupByOperator) rootPlanNode.run();
    final MAggregationGroupByOperator operator = (MAggregationGroupByOperator) rootPlanNode.run();
    final IntermediateResultsBlock resultBlock = (IntermediateResultsBlock) operator.nextBlock();
    System.out.println("RunningTime : " + resultBlock.getTimeUsedMs());
    System.out.println("NumDocsScanned : " + resultBlock.getNumDocsScanned());
    System.out.println("TotalDocs : " + resultBlock.getTotalDocs());
    Assert.assertEquals(resultBlock.getNumDocsScanned(), 10);
    Assert.assertEquals(resultBlock.getTotalDocs(), 100000);

    final AggregationGroupByOperatorService aggregationGroupByOperatorService =
        new AggregationGroupByOperatorService(_aggregationInfos, brokerRequest.getGroupBy());

    final Map<ServerInstance, DataTable> instanceResponseMap = new HashMap<ServerInstance, DataTable>();
    instanceResponseMap.put(new ServerInstance("localhost:0000"), resultBlock.getAggregationGroupByResultDataTable());
    instanceResponseMap.put(new ServerInstance("localhost:1111"), resultBlock.getAggregationGroupByResultDataTable());
    instanceResponseMap.put(new ServerInstance("localhost:2222"), resultBlock.getAggregationGroupByResultDataTable());
    instanceResponseMap.put(new ServerInstance("localhost:3333"), resultBlock.getAggregationGroupByResultDataTable());
    instanceResponseMap.put(new ServerInstance("localhost:4444"), resultBlock.getAggregationGroupByResultDataTable());
    instanceResponseMap.put(new ServerInstance("localhost:5555"), resultBlock.getAggregationGroupByResultDataTable());
    instanceResponseMap.put(new ServerInstance("localhost:6666"), resultBlock.getAggregationGroupByResultDataTable());
    instanceResponseMap.put(new ServerInstance("localhost:7777"), resultBlock.getAggregationGroupByResultDataTable());
    instanceResponseMap.put(new ServerInstance("localhost:8888"), resultBlock.getAggregationGroupByResultDataTable());
    instanceResponseMap.put(new ServerInstance("localhost:9999"), resultBlock.getAggregationGroupByResultDataTable());
    final List<Map<String, Serializable>> reducedResults =
        aggregationGroupByOperatorService.reduceGroupByOperators(instanceResponseMap);
    final List<JSONObject> jsonResult = aggregationGroupByOperatorService.renderGroupByOperators(reducedResults);
    System.out.println(jsonResult);
  }

  @Test
  public void testInterSegmentAggregationGroupByPlanMakerAndRun() throws Exception {
    final int numSegments = 5;
    setupSegmentList(numSegments);
    final PlanMaker instancePlanMaker = new InstancePlanMakerImplV1();
    final BrokerRequest brokerRequest = getAggregationGroupByNoFilterBrokerRequest();
    final ExecutorService executorService = Executors.newCachedThreadPool(new NamedThreadFactory("test-plan-maker"));
    final Plan globalPlan = instancePlanMaker.makeInterSegmentPlan(_indexSegmentList, brokerRequest, executorService);
    globalPlan.print();
    globalPlan.execute();
    final DataTable instanceResponse = globalPlan.getInstanceResponse();
    System.out.println(instanceResponse);

    final DefaultReduceService defaultReduceService = new DefaultReduceService();
    final Map<ServerInstance, DataTable> instanceResponseMap = new HashMap<ServerInstance, DataTable>();
    instanceResponseMap.put(new ServerInstance("localhost:0000"), instanceResponse);
    final BrokerResponse brokerResponse = defaultReduceService.reduceOnDataTable(brokerRequest, instanceResponseMap);
    System.out.println(new JSONArray(brokerResponse.getAggregationResults()));
    System.out.println("Time used : " + brokerResponse.getTimeUsedMs());
    assertBrokerResponse(numSegments, brokerResponse);
  }

  private void assertBrokerResponse(int numSegments, BrokerResponse brokerResponse) throws JSONException {
    Assert.assertEquals(100000 * numSegments, brokerResponse.getNumDocsScanned());
    Assert.assertEquals(_numAggregations, brokerResponse.getAggregationResults().size());
    for (int i = 0; i < _numAggregations; ++i) {
      Assert.assertEquals("[\"viewerOccupations\",\"viewerCompanies\"]", brokerResponse.getAggregationResults().get(i)
          .getJSONArray("groupByColumns").toString());
      Assert.assertEquals(15, brokerResponse.getAggregationResults().get(i).getJSONArray("groupByResult").length());
    }

    // Assertion on Count
    Assert.assertEquals("count_star", brokerResponse.getAggregationResults().get(0).getString("function").toString());
    Assert.assertEquals("sum_count", brokerResponse.getAggregationResults().get(1).getString("function").toString());
    Assert.assertEquals("max_count", brokerResponse.getAggregationResults().get(2).getString("function").toString());
    Assert.assertEquals("min_count", brokerResponse.getAggregationResults().get(3).getString("function").toString());
    Assert.assertEquals("avg_count", brokerResponse.getAggregationResults().get(4).getString("function").toString());
    Assert.assertEquals("distinctCount_vieweeId", brokerResponse.getAggregationResults().get(5).getString("function")
        .toString());

    // Assertion on Aggregation Results
    final List<double[]> aggregationResult = getAggregationResult(numSegments);
    final List<String[]> groupByResult = getGroupResult();
    for (int j = 0; j < _numAggregations; ++j) {
      final double[] aggResult = aggregationResult.get(j);
      final String[] groupResult = groupByResult.get(j);
      for (int i = 0; i < 15; ++i) {
        Assert.assertEquals(aggResult[i], brokerResponse.getAggregationResults().get(j).getJSONArray("groupByResult")
            .getJSONObject(i).getDouble("value"));
        if (groupResult.length < 2) {
          continue;
        }
        Assert.assertEquals(groupResult[i], brokerResponse.getAggregationResults().get(j).getJSONArray("groupByResult")
            .getJSONObject(i).getString("group"));
      }
    }

  }

  private static List<double[]> getAggregationResult(int numSegments) {
    final List<double[]> aggregationResultList = new ArrayList<double[]>();
    aggregationResultList.add(getCountResult(numSegments));
    aggregationResultList.add(getSumResult(numSegments));
    aggregationResultList.add(getMaxResult());
    aggregationResultList.add(getMinResult());
    aggregationResultList.add(getAvgResult());
    aggregationResultList.add(getDistinctCountResult());
    return aggregationResultList;
  }

  private static List<String[]> getGroupResult() {
    final List<String[]> groupResults = new ArrayList<String[]>();
    groupResults.add(getCountGroupResult());
    groupResults.add(getSumGroupResult());
    groupResults.add(getMaxGroupResult());
    groupResults.add(getMinGroupResult());
    groupResults.add(getAvgGroupResult());
    groupResults.add(getDistinctCountGroupResult());
    return groupResults;
  }

  private static double[] getCountResult(int numSegments) {
    return new double[] { 45806 * numSegments, 1621 * numSegments, 1182 * numSegments, 1080 * numSegments, 1041 * numSegments, 712 * numSegments, 622 * numSegments, 545 * numSegments, 418 * numSegments, 418 * numSegments, 418 * numSegments, 418 * numSegments, 295 * numSegments, 256 * numSegments, 254 * numSegments };
  }

  private static String[] getCountGroupResult() {
    return new String[] { "[\"2147483647\",\"2147483647\"]", "[\"363\",\"2147483647\"]", "[\"523\",\"2147483647\"]", "[\"469\",\"2147483647\"]", "[\"564\",\"2147483647\"]", "[\"288\",\"2147483647\"]", "[\"246\",\"2147483647\"]", "[\"225\",\"2147483647\"]", "[\"523\",\"3311739\"]", "[\"314\",\"3311739\"]", "[\"246\",\"3311739\"]", "[\"478\",\"3311739\"]", "[\"211\",\"2147483647\"]", "[\"332\",\"2147483647\"]", "[\"496\",\"2147483647\"]" };
  }

  private static double[] getSumResult(int numSegments) {
    return new double[] { 45806 * numSegments, 1621 * numSegments, 1182 * numSegments, 1080 * numSegments, 1041 * numSegments, 712 * numSegments, 622 * numSegments, 545 * numSegments, 418 * numSegments, 418 * numSegments, 418 * numSegments, 418 * numSegments, 295 * numSegments, 256 * numSegments, 254 * numSegments };
  }

  private static String[] getSumGroupResult() {
    return new String[] { "[\"2147483647\",\"2147483647\"]", "[\"363\",\"2147483647\"]", "[\"523\",\"2147483647\"]", "[\"469\",\"2147483647\"]", "[\"564\",\"2147483647\"]", "[\"288\",\"2147483647\"]", "[\"246\",\"2147483647\"]", "[\"225\",\"2147483647\"]", "[\"523\",\"3311739\"]", "[\"314\",\"3311739\"]", "[\"246\",\"3311739\"]", "[\"478\",\"3311739\"]", "[\"211\",\"2147483647\"]", "[\"332\",\"2147483647\"]", "[\"496\",\"2147483647\"]" };
  }

  private static double[] getMaxResult() {
    return new double[] { 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 };
  }

  private static String[] getMaxGroupResult() {
    return new String[1];
  }

  private static double[] getMinResult() {
    return new double[] { 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 };
  }

  private static String[] getMinGroupResult() {
    return new String[1];
  }

  private static double[] getAvgResult() {
    return new double[] { 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 };
  }

  private static String[] getAvgGroupResult() {
    return new String[1];
  }

  private static double[] getDistinctCountResult() {
    return new double[] { 22056, 1146, 838, 755, 732, 534, 458, 418, 418, 418, 418, 396, 216, 194, 190 };
  }

  private static String[] getDistinctCountGroupResult() {
    return new String[] { "[\"2147483647\",\"2147483647\"]", "[\"363\",\"2147483647\"]", "[\"523\",\"2147483647\"]", "[\"469\",\"2147483647\"]", "[\"564\",\"2147483647\"]", "[\"288\",\"2147483647\"]", "[\"246\",\"2147483647\"]", "[\"523\",\"3311739\"]", "[\"314\",\"3311739\"]", "[\"246\",\"3311739\"]", "[\"478\",\"3311739\"]", "[\"225\",\"2147483647\"]", "[\"211\",\"2147483647\"]", "[\"332\",\"2147483647\"]", "[\"496\",\"2147483647\"]" };
  }

  private static BrokerRequest getAggregationGroupByNoFilterBrokerRequest() {
    final BrokerRequest brokerRequest = new BrokerRequest();
    final List<AggregationInfo> aggregationsInfo = getAggregationsInfo();
    brokerRequest.setAggregationsInfo(aggregationsInfo);
    brokerRequest.setGroupBy(getGroupBy());
    return brokerRequest;
  }

  private static List<AggregationInfo> getAggregationsInfo() {
    final List<AggregationInfo> aggregationsInfo = new ArrayList<AggregationInfo>();
    aggregationsInfo.add(getCountAggregationInfo());
    aggregationsInfo.add(getSumAggregationInfo());
    aggregationsInfo.add(getMaxAggregationInfo());
    aggregationsInfo.add(getMinAggregationInfo());
    aggregationsInfo.add(getAvgAggregationInfo());
    aggregationsInfo.add(getDistinctCountAggregationInfo("vieweeId"));
    return aggregationsInfo;
  }

  private static Map<String, DataSource> getDataSourceMap() {
    final Map<String, DataSource> dataSourceMap = new HashMap<String, DataSource>();
    dataSourceMap.put("viewerOccupations", _indexSegment.getDataSource("viewerOccupations"));
    dataSourceMap.put("viewerCompanies", _indexSegment.getDataSource("viewerCompanies"));
    dataSourceMap.put("count", _indexSegment.getDataSource("count"));
    dataSourceMap.put("vieweeId", _indexSegment.getDataSource("vieweeId"));

    return dataSourceMap;
  }

  private static AggregationInfo getCountAggregationInfo() {
    final String type = "count";
    final Map<String, String> params = new HashMap<String, String>();
    params.put("column", "*");
    final AggregationInfo aggregationInfo = new AggregationInfo();
    aggregationInfo.setAggregationType(type);
    aggregationInfo.setAggregationParams(params);
    return aggregationInfo;
  }

  private static AggregationInfo getSumAggregationInfo() {
    final String type = "sum";
    final Map<String, String> params = new HashMap<String, String>();
    params.put("column", "count");
    final AggregationInfo aggregationInfo = new AggregationInfo();
    aggregationInfo.setAggregationType(type);
    aggregationInfo.setAggregationParams(params);
    return aggregationInfo;
  }

  private static AggregationInfo getMaxAggregationInfo() {
    final String type = "max";
    final Map<String, String> params = new HashMap<String, String>();
    params.put("column", "count");
    final AggregationInfo aggregationInfo = new AggregationInfo();
    aggregationInfo.setAggregationType(type);
    aggregationInfo.setAggregationParams(params);
    return aggregationInfo;
  }

  private static AggregationInfo getMinAggregationInfo() {
    final String type = "min";
    final Map<String, String> params = new HashMap<String, String>();
    params.put("column", "count");
    final AggregationInfo aggregationInfo = new AggregationInfo();
    aggregationInfo.setAggregationType(type);
    aggregationInfo.setAggregationParams(params);
    return aggregationInfo;
  }

  private static AggregationInfo getAvgAggregationInfo() {
    final String type = "avg";
    final Map<String, String> params = new HashMap<String, String>();
    params.put("column", "count");
    final AggregationInfo aggregationInfo = new AggregationInfo();
    aggregationInfo.setAggregationType(type);
    aggregationInfo.setAggregationParams(params);
    return aggregationInfo;
  }

  private static AggregationInfo getDistinctCountAggregationInfo(String dim) {
    final String type = "distinctCount";
    final Map<String, String> params = new HashMap<String, String>();
    params.put("column", dim);

    final AggregationInfo aggregationInfo = new AggregationInfo();
    aggregationInfo.setAggregationType(type);
    aggregationInfo.setAggregationParams(params);
    return aggregationInfo;
  }

  private static GroupBy getGroupBy() {
    final GroupBy groupBy = new GroupBy();
    final List<String> columns = new ArrayList<String>();
    columns.add("viewerOccupations");
    columns.add("viewerCompanies");
    groupBy.setColumns(columns);
    groupBy.setTopN(15);
    return groupBy;
  }

  private static BrokerRequest getAggregationGroupByWithFilterBrokerRequest() {
    final BrokerRequest brokerRequest = new BrokerRequest();
    final List<AggregationInfo> aggregationsInfo = getAggregationsInfo();
    brokerRequest.setAggregationsInfo(aggregationsInfo);
    brokerRequest.setGroupBy(getGroupBy());
    setFilterQuery(brokerRequest);
    return brokerRequest;
  }

  private static BrokerRequest setFilterQuery(BrokerRequest brokerRequest) {
    FilterQueryTree filterQueryTree;
    String filterColumn = "vieweeId";
    String filterVal = "356899";
    if (filterColumn.contains(",")) {
      String[] filterColumns = filterColumn.split(",");
      String[] filterValues = filterVal.split(",");
      List<FilterQueryTree> nested = new ArrayList<FilterQueryTree>();
      for (int i = 0; i < filterColumns.length; i++) {

        List<String> vals = new ArrayList<String>();
        vals.add(filterValues[i]);
        FilterQueryTree d = new FilterQueryTree(i + 1, filterColumns[i], vals, FilterOperator.EQUALITY, null);
        nested.add(d);
      }
      filterQueryTree = new FilterQueryTree(0, null, null, FilterOperator.AND, nested);
    } else {
      List<String> vals = new ArrayList<String>();
      vals.add(filterVal);
      filterQueryTree = new FilterQueryTree(0, filterColumn, vals, FilterOperator.EQUALITY, null);
    }
    RequestUtils.generateFilterFromTree(filterQueryTree, brokerRequest);
    return brokerRequest;
  }
}
