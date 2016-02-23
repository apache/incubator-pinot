package com.linkedin.thirdeye.client;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.linkedin.pinot.client.Connection;
import com.linkedin.pinot.client.ConnectionFactory;
import com.linkedin.pinot.client.PinotClientException;
import com.linkedin.pinot.client.ResultSet;
import com.linkedin.pinot.client.ResultSetGroup;
import com.linkedin.pinot.common.data.DimensionFieldSpec;
import com.linkedin.pinot.common.data.FieldSpec.DataType;
import com.linkedin.pinot.common.data.MetricFieldSpec;
import com.linkedin.pinot.common.data.Schema;
import com.linkedin.pinot.common.data.TimeFieldSpec;
import com.linkedin.thirdeye.api.DimensionKey;
import com.linkedin.thirdeye.api.DimensionSpec;
import com.linkedin.thirdeye.api.MetricSpec;
import com.linkedin.thirdeye.api.MetricTimeSeries;
import com.linkedin.thirdeye.api.MetricType;
import com.linkedin.thirdeye.api.SegmentDescriptor;
import com.linkedin.thirdeye.api.StarTreeConfig;
import com.linkedin.thirdeye.api.TimeGranularity;
import com.linkedin.thirdeye.api.TimeSpec;
import com.linkedin.thirdeye.client.ThirdEyeMetricFunction.Expression;
import com.linkedin.thirdeye.client.factory.PinotThirdEyeClientFactory;
import com.linkedin.thirdeye.client.util.PqlUtils;
import com.linkedin.thirdeye.query.ThirdEyeRatioFunction;

/**
 * ThirdEyeClient that uses {@link Connection} to query data, and the Pinot Controller REST
 * endpoints for querying tables and schemas. Because of the controller dependency, schemas must be
 * provided to the cluster controller even if all cluster data is offline. <br/>
 * While this class does provide some caching on PQL queries, it is recommended to use
 * {@link CachedThirdEyeClient} or instantiate this class via {@link PinotThirdEyeClientFactory}
 * to improve performance. Instances of this class can be created from the static factory methods
 * (from*).
 * @author jteoh
 */
public class PinotThirdEyeClient implements ThirdEyeClient {
  public static final String CONTROLLER_HOST_PROPERTY_KEY = "controllerHost";
  public static final String CONTROLLER_PORT_PROPERTY_KEY = "controllerPort";
  public static final String FIXED_COLLECTIONS_PROPERTY_KEY = "fixedCollections";

  private static final Logger LOG = LoggerFactory.getLogger(PinotThirdEyeClient.class);

  private static final String UTF_8 = "UTF-8";
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String TABLES_ENDPOINT = "tables/";

  // No way to determine data retention from pinot schema
  private static final TimeGranularity DEFAULT_TIME_RETENTION = null;
  // Pinot TimeFieldSpec always assumes granularity size of 1.
  private static final int GRANULARITY_SIZE = 1;

  private final Connection connection;
  private final LoadingCache<String, ResultSetGroup> resultSetGroupCache;
  private final LoadingCache<String, Schema> schemaCache;
  private final HttpHost controllerHost;
  private final CloseableHttpClient controllerClient;
  private List<String> fixedCollections = null;

  protected PinotThirdEyeClient(Connection connection, String controllerHostName,
      int controllerPort) {
    this.connection = connection;
    // TODO make this more configurable? (leverage cache config)
    this.resultSetGroupCache = CacheBuilder.newBuilder().expireAfterAccess(5, TimeUnit.MINUTES)
        .build(new ResultSetGroupCacheLoader());
    this.schemaCache = CacheBuilder.newBuilder().expireAfterAccess(5, TimeUnit.MINUTES)
        .build(new SchemaCacheLoader());
    this.controllerHost = new HttpHost(controllerHostName, controllerPort);
    // TODO currently no way to configure the CloseableHttpClient
    this.controllerClient = HttpClients.createDefault();
    LOG.info("Created PinotThirdEyeClient to {} with controller {}", connection, controllerHost);
  }

  /* Static builder methods to mirror Pinot Java API (ConnectionFactory) */
  public static PinotThirdEyeClient fromHostList(String controllerHost, int controllerPort,
      String... brokers) {
    return fromHostList(new CachedThirdEyeClientConfig(), controllerHost, controllerPort, brokers);
  }

  public static PinotThirdEyeClient fromHostList(CachedThirdEyeClientConfig config,
      String controllerHost, int controllerPort, String... brokers) {
    if (brokers == null || brokers.length == 0) {
      throw new IllegalArgumentException("Please specify at least one broker.");
    }
    Connection connection = ConnectionFactory.fromHostList(brokers);
    LOG.info("Created PinotThirdEyeClient to hosts: {}", (Object[]) brokers);
    return new PinotThirdEyeClient(connection, controllerHost, controllerPort);
  }

  public static PinotThirdEyeClient fromZookeeper(String controllerHost, int controllerPort,
      String zkUrl) {
    return fromZookeeper(new CachedThirdEyeClientConfig(), controllerHost, controllerPort, zkUrl);
  }

  public static PinotThirdEyeClient fromZookeeper(CachedThirdEyeClientConfig config,
      String controllerHost, int controllerPort, String zkUrl) {
    Connection connection = ConnectionFactory.fromZookeeper(zkUrl);
    LOG.info("Created PinotThirdEyeClient to zookeeper: {}", zkUrl);
    return new PinotThirdEyeClient(connection, controllerHost, controllerPort);
  }

  public static PinotThirdEyeClient fromProperties(Properties properties) {
    return fromProperties(new CachedThirdEyeClientConfig(), properties);
  }

  public static PinotThirdEyeClient fromProperties(CachedThirdEyeClientConfig config,
      Properties properties) {
    Connection connection = ConnectionFactory.fromProperties(properties);
    LOG.info("Created PinotThirdEyeClient from properties {}", properties);
    if (!properties.containsKey(CONTROLLER_HOST_PROPERTY_KEY)
        || !properties.containsKey(CONTROLLER_PORT_PROPERTY_KEY)) {
      throw new IllegalArgumentException("Properties file must contain controller mappings for "
          + CONTROLLER_HOST_PROPERTY_KEY + " and " + CONTROLLER_PORT_PROPERTY_KEY);
    }
    return new PinotThirdEyeClient(connection, properties.getProperty(CONTROLLER_HOST_PROPERTY_KEY),
        Integer.valueOf(properties.getProperty(CONTROLLER_PORT_PROPERTY_KEY)));
  }

  @Override
  public Map<DimensionKey, MetricTimeSeries> execute(ThirdEyeRequest request) throws Exception {
    LOG.info("execute: {}", request);
    ThirdEyeRawResponse rawResponse = getRawResponse(request);
    // Figure out the metric types of the projection
    StarTreeConfig starTreeConfig = getStarTreeConfig(request.getCollection());
    Map<String, MetricType> metricTypes = new HashMap<>();
    for (MetricSpec metricSpec : starTreeConfig.getMetrics()) {
      String metricName = metricSpec.getName();
      MetricType metricType = metricSpec.getType();
      metricTypes.put(metricName, metricType);
    }
    List<MetricType> projectionTypes = new ArrayList<>();
    for (String metricName : rawResponse.getMetrics()) {
      MetricType metricType = metricTypes.get(metricName);
      projectionTypes.add(metricType);
    }
    return rawResponse.convert(projectionTypes);
  }

  @Override
  public ThirdEyeRawResponse getRawResponse(ThirdEyeRequest request) throws Exception {
    StarTreeConfig starTreeConfig = getStarTreeConfig(request.getCollection());
    TimeSpec dataTimeSpec = starTreeConfig.getTime();
    List<String> rawMetrics = request.getRawMetricNames();
    List<String> dimensionNames = starTreeConfig.getDimensionNames();

    String sql = PqlUtils.getPql(request, dataTimeSpec);
    LOG.info("getRawResponse: {}", sql);
    ResultSetGroup result = resultSetGroupCache.get(sql);

    Map<String, Map<String, Number[]>> data =
        parseResultSetGroup(request, result, rawMetrics, starTreeConfig, dimensionNames);

    // now we have raw metric data aggregated by the appropriate time bucket, calculate derived and
    // update for each timestamp.
    data = calculateDerivedMetrics(request, data, rawMetrics);

    ThirdEyeRawResponse resp = new ThirdEyeRawResponse();
    resp.setData(data);
    resp.setDimensions(dimensionNames);
    resp.setMetrics(request.getMetricNames());
    return resp;
  }

  private Map<String, Map<String, Number[]>> parseResultSetGroup(ThirdEyeRequest request,
      ResultSetGroup result, List<String> rawMetrics, StarTreeConfig starTreeConfig,
      List<String> dimensionNames)
          throws JsonProcessingException, RuntimeException, NumberFormatException {

    // Key: dimensionKey -> timestamp
    HashMap<String, Map<String, Number[]>> data = new HashMap<String, Map<String, Number[]>>();

    String baseDimensionKeyFormatter =
        calculateBaseDimensionKey(dimensionNames, request.getGroupBy());

    TimeGranularity bucketGranularity = starTreeConfig.getTime().getBucket();
    TimeGranularity aggGranularity = request.getTimeGranularity();
    DateTime startTime = request.getStartTime();

    int columnOffset = 0; // number of observed columns from previous result sets.
    String dimensionKey;
    String timestamp = null;
    List<String> dimensionKeyList = new LinkedList<String>();
    Double[] rowData;
    for (int groupIdx = 0; groupIdx < result.getResultSetCount(); groupIdx++) {
      ResultSet resultSet = result.getResultSet(groupIdx);
      int columnCount = resultSet.getColumnCount();
      for (int row = 0; row < resultSet.getRowCount(); row++) {
        // determine timestamp + dimensionKey
        if (resultSet.getGroupKeyLength() > 0) {
          for (int group = 0; group < resultSet.getGroupKeyLength(); group++) {
            String timeKey = resultSet.getGroupKeyString(row, group);
            if (group == 0) {
              timestamp = calculateTimeStamp(timeKey, bucketGranularity, aggGranularity, startTime);
            } else {
              dimensionKeyList.add(timeKey);
            }
          }
          dimensionKey = String.format(baseDimensionKeyFormatter, dimensionKeyList.toArray());
          dimensionKey = MAPPER.writeValueAsString(dimensionKey.split(","));
        } else {
          throw new RuntimeException("Error: no dimension key can be derived from the results.");
        }

        // Populate column values for current result set
        rowData = getRowData(data, rawMetrics.size(), dimensionKey, timestamp);
        for (int col = 0; col < columnCount; col++) {
          String colValStr = resultSet.getString(row, col);
          int metricIdx = col + columnOffset;
          // we're assuming all values are doubles (from SUM)
          rowData[metricIdx] += Double.valueOf(colValStr);
        }
      }
      // increment columnOffset to account for the result sets observed so far.
      columnOffset += columnCount;
      dimensionKeyList.clear();
      timestamp = null;
    }
    return data;
  }

  /**
   * Calculates derived metrics specified by the request object and updates the data in place with
   * the new values.
   * @param request
   * @param data
   * @param rawMetrics
   * @return
   */
  private Map<String, Map<String, Number[]>> calculateDerivedMetrics(ThirdEyeRequest request,
      Map<String, Map<String, Number[]>> data, List<String> rawMetrics) {
    List<Expression> metrics = request.getMetricFunction().getMetricExpressions();
    Map<String, Integer> rawMetricIndexMap = computeIndexMap(rawMetrics);
    for (Map<String, Number[]> row : data.values()) {
      for (String timestamp : row.keySet()) {
        Number[] rawData = row.get(timestamp);
        Number[] metricData = computeMetricData(metrics, rawMetricIndexMap, rawData);
        row.put(timestamp, metricData);
      }
    }
    return data;
  }

  private Double[] getRowData(HashMap<String, Map<String, Number[]>> data, int columnCount,
      String dimensionKey, String timeStamp) {
    if (!data.containsKey(dimensionKey)) {
      data.put(dimensionKey, new TreeMap<String, Number[]>());
    }
    Map<String, Number[]> dimKeyData = data.get(dimensionKey);
    if (!dimKeyData.containsKey(timeStamp)) {
      Double[] row = new Double[columnCount];
      Arrays.fill(row, Double.valueOf(0));
      dimKeyData.put(timeStamp, row);
    }
    Double[] rowData = (Double[]) dimKeyData.get(timeStamp);
    return rowData;
  }

  private String calculateBaseDimensionKey(List<String> dimensions, Set<String> groupBy) {
    List<String> placeholders = new LinkedList<>();
    for (String dimension : dimensions) {
      if (groupBy.contains(dimension)) {
        placeholders.add("%s");
      } else {
        placeholders.add("*");
      }
    }
    return StringUtils.join(placeholders, ",");
  }

  /**
   * Logic mostly taken from {@link ThirdEyeRatioFunction}. The implementation does not explicitly
   * provide a default value for NaN ratios, so null is used.
   */
  private Number[] computeMetricData(List<Expression> metrics,
      Map<String, Integer> rawMetricIndexMap, Number[] rawData) {
    Number[] metricData = new Number[metrics.size()];
    for (int i = 0; i < metrics.size(); i++) {
      Expression expression = metrics.get(i);
      Number expressionValue;
      if (expression.isAtomic()) {
        int idx = rawMetricIndexMap.get(expression.getAtomicValue());
        expressionValue = rawData[idx];
      } else {
        // ratio
        List<String> arguments = expression.getArguments();
        int numeratorIdx = rawMetricIndexMap.get(arguments.get(0));
        int denominatorIdx = rawMetricIndexMap.get(arguments.get(1));

        double numeratorVal = rawData[numeratorIdx].doubleValue();
        double denominatorVal = rawData[denominatorIdx].doubleValue();
        if (denominatorVal == 0) {
          expressionValue = null;
        } else {
          expressionValue = numeratorVal / denominatorVal;
        }
      }

      metricData[i] = expressionValue;
    }
    return metricData;
  }

  /** Return a map of entry->list index. */
  private Map<String, Integer> computeIndexMap(List<String> entries) {
    HashMap<String, Integer> map = new HashMap<>(entries.size());
    for (int i = 0; i < entries.size(); i++) {
      String metric = entries.get(i);
      map.put(metric, i);
    }
    return map;
  }

  @Override
  public StarTreeConfig getStarTreeConfig(String collection) throws Exception {
    Schema schema = getSchema(collection);
    List<DimensionSpec> dimSpecs = fromDimensionFieldSpecs(schema.getDimensionFieldSpecs());
    List<MetricSpec> metricSpecs = fromMetricFieldSpecs(schema.getMetricFieldSpecs());
    TimeSpec timeSpec = fromTimeFieldSpec(schema.getTimeFieldSpec());
    StarTreeConfig config = new StarTreeConfig.Builder().setCollection(collection)
        .setDimensions(dimSpecs).setMetrics(metricSpecs).setTime(timeSpec).build();
    return config;
  }

  /**
   * Hardcodes a set of collections. Note that this method assumes the schemas for
   * these collections already exist.
   */
  public void setFixedCollections(List<String> collections) {
    LOG.info("Setting fixed collections: {}", collections);
    this.fixedCollections = collections;
  }

  @Override
  public List<String> getCollections() throws Exception {
    if (this.fixedCollections != null) {
      // assume the fixed collections are correct.
      return fixedCollections;
    }
    HttpGet req = new HttpGet(TABLES_ENDPOINT);
    LOG.info("Retrieving collections: {}", req);
    CloseableHttpResponse res = controllerClient.execute(controllerHost, req);
    try {
      if (res.getStatusLine().getStatusCode() != 200) {
        throw new IllegalStateException(res.getStatusLine().toString());
      }
      InputStream content = res.getEntity().getContent();
      JsonNode tables = new ObjectMapper().readTree(content).get("tables");
      ArrayList<String> collections = new ArrayList<>(tables.size());
      ArrayList<String> skippedCollections = new ArrayList<>();
      for (JsonNode table : tables) {
        String collection = table.asText();
        // TODO Since Pinot does not strictly require a schema to be provided for each offline data
        // set, filter out those for which a schema cannot be retrieved.
        try {
          Schema schema = getSchema(collection);
          if (schema == null) {
            LOG.debug("Skipping collection {} due to null schema", collection);
            skippedCollections.add(collection);
            continue;
          }
        } catch (Exception e) {
          LOG.debug("Skipping collection {} due to schema retrieval exception", collection, e);
          skippedCollections.add(collection);
          continue;
        }
        collections.add(collection);
      }
      if (!skippedCollections.isEmpty()) {
        LOG.info(
            "{} collections were not included because their schemas could not be retrieved: {}",
            skippedCollections.size(), skippedCollections);
      }

      return collections;
    } finally {
      if (res.getEntity() != null) {
        EntityUtils.consume(res.getEntity());
      }
      res.close();
    }
  }

  /**
   * Returns the min and max time values available for the provided collection in a single
   * descriptor.
   */
  @Override
  public List<SegmentDescriptor> getSegmentDescriptors(String collection) throws Exception {
    TimeSpec timeSpec = getStarTreeConfig(collection).getTime();
    String timeColumnName = timeSpec.getColumnName();
    String sql = PqlUtils.getDataTimeRangeSql(collection, timeColumnName);
    LOG.info("Retrieving segment: {}", sql);
    ResultSetGroup result = resultSetGroupCache.get(sql);
    double minTime = result.getResultSet(0).getDouble(0);
    double maxTime = result.getResultSet(1).getDouble(0);
    TimeUnit dataUnit = timeSpec.getBucket().getUnit();
    long minTimeMillis = dataUnit.toMillis((long) minTime);
    long maxTimeMillis = dataUnit.toMillis((long) maxTime);
    SegmentDescriptor singletonDescriptor =
        new SegmentDescriptor(null, null, null, new DateTime(minTimeMillis, DateTimeZone.UTC),
            new DateTime(maxTimeMillis, DateTimeZone.UTC));
    return Collections.singletonList(singletonDescriptor);
  }

  @Override
  public void clear() throws Exception {
    resultSetGroupCache.invalidateAll();
    schemaCache.invalidateAll();
  }

  @Override
  public void close() throws Exception {
    controllerClient.close();
  }

  private Schema getSchema(String collection) throws ClientProtocolException, IOException,
      InterruptedException, ExecutionException, TimeoutException {
    return schemaCache.get(collection);
  }

  private List<DimensionSpec> fromDimensionFieldSpecs(List<DimensionFieldSpec> specs) {
    List<DimensionSpec> results = new ArrayList<>(specs.size());
    for (DimensionFieldSpec dimensionFieldSpec : specs) {
      DimensionSpec dimensionSpec = new DimensionSpec(dimensionFieldSpec.getName());
      results.add(dimensionSpec);
    }
    return results;
  }

  private List<MetricSpec> fromMetricFieldSpecs(List<MetricFieldSpec> specs) {
    ArrayList<MetricSpec> results = new ArrayList<>(specs.size());
    for (MetricFieldSpec metricFieldSpec : specs) {
      MetricSpec metricSpec = getMetricType(metricFieldSpec);
      results.add(metricSpec);
    }
    return results;
  }

  private MetricSpec getMetricType(MetricFieldSpec metricFieldSpec) {
    DataType dataType = metricFieldSpec.getDataType();
    MetricType metricType;
    switch (dataType) {
    case BOOLEAN:
    case BYTE:
    case BYTE_ARRAY:
    case CHAR:
    case CHAR_ARRAY:
    case DOUBLE_ARRAY:
    case FLOAT_ARRAY:
    case INT_ARRAY:
    case LONG_ARRAY:
    case OBJECT:
    case SHORT_ARRAY:
    case STRING:
    case STRING_ARRAY:
    default:
      throw new UnsupportedOperationException(dataType + " is not a supported metric type");
    case DOUBLE:
      metricType = MetricType.DOUBLE;
      break;
    case FLOAT:
      metricType = MetricType.FLOAT;
      break;
    case INT:
      metricType = MetricType.INT;
      break;
    case LONG:
      metricType = MetricType.LONG;
      break;
    case SHORT:
      metricType = MetricType.SHORT;
      break;

    }
    MetricSpec metricSpec = new MetricSpec(metricFieldSpec.getName(), metricType);
    return metricSpec;
  }

  private TimeSpec fromTimeFieldSpec(TimeFieldSpec timeFieldSpec) {
    TimeGranularity inputGranularity = new TimeGranularity(GRANULARITY_SIZE,
        timeFieldSpec.getIncomingGranularitySpec().getTimeType());
    TimeGranularity outputGranularity = new TimeGranularity(GRANULARITY_SIZE,
        timeFieldSpec.getOutgoingGranularitySpec().getTimeType());
    TimeSpec spec = new TimeSpec(timeFieldSpec.getOutGoingTimeColumnName(), inputGranularity,
        outputGranularity, DEFAULT_TIME_RETENTION);
    return spec;
  }

  /**
   * Converts a timestamp in the provided data granularity to millisSinceEpoch, aligned to the given
   * aggregation granularity. This assumes:
   * <ol>
   * <li><tt>timeKey</tt> will have a value aligned to
   * unitsSinceEpoch, based on the data granularity unit + size (eg hoursSinceEpoch).</li>
   * <li>The input date (<tt>start</tt>) is also aligned to unitsSinceEpoch as described above.</li>
   * </ol>
   */
  private String calculateTimeStamp(String timeKey, TimeGranularity dataGranularity,
      TimeGranularity aggGranularity, DateTime start) {
    long startMillis = start.getMillis();
    long millisSinceEpoch =
        dataGranularity.getUnit().toMillis(Long.valueOf(timeKey) * dataGranularity.getSize());
    // align to start of time range (ie offset based on start range rather than epoch)
    long millisSinceStart = millisSinceEpoch - startMillis;
    // round down to nearest factor of agg bucket
    long bucketMillis = aggGranularity.getUnit().toMillis(aggGranularity.getSize());
    long alignedMillisSinceStart = (millisSinceStart / bucketMillis) * bucketMillis;
    // re-align to epoch
    millisSinceEpoch = startMillis + alignedMillisSinceStart;
    return String.valueOf(millisSinceEpoch);
  }

  private class ResultSetGroupCacheLoader extends CacheLoader<String, ResultSetGroup> {
    @Override
    public ResultSetGroup load(String sql) throws Exception {
      try {
        return connection.execute(sql);
      } catch (PinotClientException cause) {
        throw new PinotClientException("Error when running sql:" + sql, cause);
      }
    }
  }

  private class SchemaCacheLoader extends CacheLoader<String, Schema> {
    @Override
    public Schema load(String collection) throws Exception {
      HttpGet req = new HttpGet(TABLES_ENDPOINT + URLEncoder.encode(collection, UTF_8) + "/schema");
      LOG.info("Retrieving schema: {}", req);
      CloseableHttpResponse res = controllerClient.execute(controllerHost, req);
      try {
        if (res.getStatusLine().getStatusCode() != 200) {
          throw new IllegalStateException(res.getStatusLine().toString());
        }
        InputStream content = res.getEntity().getContent();
        Schema schema = new ObjectMapper().readValue(content, Schema.class);
        return schema;
      } finally {
        if (res.getEntity() != null) {
          EntityUtils.consume(res.getEntity());
        }
        res.close();
      }
    }
  }

}
