package com.linkedin.thirdeye.dashboard;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.linkedin.thirdeye.api.TimeGranularity;
import com.linkedin.thirdeye.client.MetricExpression;
import com.linkedin.thirdeye.client.MetricFunction;
import com.linkedin.thirdeye.client.ThirdEyeCacheRegistry;
import com.linkedin.thirdeye.client.ThirdEyeRequest;
import com.linkedin.thirdeye.client.ThirdEyeRequest.ThirdEyeRequestBuilder;
import com.linkedin.thirdeye.client.ThirdEyeResponse;
import com.linkedin.thirdeye.client.cache.QueryCache;
import com.linkedin.thirdeye.constant.MetricAggFunction;
import com.linkedin.thirdeye.datalayer.bao.DashboardConfigManager;
import com.linkedin.thirdeye.datalayer.dto.DashboardConfigDTO;
import com.linkedin.thirdeye.datalayer.dto.DatasetConfigDTO;
import com.linkedin.thirdeye.util.ThirdEyeUtils;

public class Utils {
  private static final Logger LOG = LoggerFactory.getLogger(Utils.class);

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static ThirdEyeCacheRegistry CACHE_REGISTRY = ThirdEyeCacheRegistry.getInstance();

  public static List<ThirdEyeRequest> generateRequests(String collection, String requestReference,
      MetricFunction metricFunction, List<String> dimensions, DateTime start, DateTime end) {

    List<ThirdEyeRequest> requests = new ArrayList<>();

    for (String dimension : dimensions) {
      ThirdEyeRequestBuilder requestBuilder = new ThirdEyeRequestBuilder();
      requestBuilder.setCollection(collection);
      List<MetricFunction> metricFunctions = Arrays.asList(metricFunction);
      requestBuilder.setMetricFunctions(metricFunctions);

      requestBuilder.setStartTimeInclusive(start);
      requestBuilder.setEndTimeExclusive(end);
      requestBuilder.setGroupBy(dimension);

      ThirdEyeRequest request = requestBuilder.build(requestReference);
      requests.add(request);
    }

    return requests;
  }

  public static Map<String, List<String>> getFilters(QueryCache queryCache, String collection,
      String requestReference, List<String> dimensions, DateTime start,
      DateTime end) throws Exception {

    MetricFunction metricFunction = new MetricFunction(MetricAggFunction.COUNT, "*");

    List<ThirdEyeRequest> requests =
        generateRequests(collection, requestReference, metricFunction, dimensions, start, end);

    Map<ThirdEyeRequest, Future<ThirdEyeResponse>> queryResultMap =
        queryCache.getQueryResultsAsync(requests);

    Map<String, List<String>> result = new HashMap<>();
    for (Map.Entry<ThirdEyeRequest, Future<ThirdEyeResponse>> entry : queryResultMap.entrySet()) {
      ThirdEyeRequest request = entry.getKey();
      String dimension = request.getGroupBy().get(0);
      ThirdEyeResponse thirdEyeResponse = entry.getValue().get();
      int n = thirdEyeResponse.getNumRowsFor(metricFunction);

      List<String> values = new ArrayList<>();
      for (int i = 0; i < n; i++) {
        Map<String, String> row = thirdEyeResponse.getRow(metricFunction, i);
        String dimensionValue = row.get(dimension);
        values.add(dimensionValue);
      }
      result.put(dimension, values);
    }
    return result;
  }

  public static List<String> getSortedDimensionNames(String collection)
      throws Exception {
    List<String> dimensions = new ArrayList<>(getSchemaDimensionNames(collection));
    Collections.sort(dimensions);
    return dimensions;
  }

  public static List<String> getSchemaDimensionNames(String collection) throws Exception {
    DatasetConfigDTO datasetConfig = CACHE_REGISTRY.getDatasetConfigCache().get(collection);
    return datasetConfig.getDimensions();
  }

  public static List<String> getDimensionsToGroupBy(String collection, Multimap<String, String> filters)
      throws Exception {
    List<String> dimensions = Utils.getSortedDimensionNames(collection);

    List<String> dimensionsToGroupBy = new ArrayList<>();
    if (filters != null) {
      Set<String> filterDimenions = filters.keySet();
      for (String dimension : dimensions) {
        if (!filterDimenions.contains(dimension)) {
          // dimensions.remove(dimension);
          dimensionsToGroupBy.add(dimension);
        }
      }
    } else {
      return dimensions;
    }
    return dimensionsToGroupBy;
  }

  public static List<String> getDashboards(DashboardConfigManager dashboardConfigDAO, String collection) throws Exception {
    List<DashboardConfigDTO> dashboardConfigs = dashboardConfigDAO.findActiveByDataset(collection);

    List<String> dashboards = new ArrayList<>();
    for (DashboardConfigDTO dashboardConfig : dashboardConfigs) {
      dashboards.add(dashboardConfig.getName());
    }
    return dashboards;
  }

  public static List<MetricExpression> convertToMetricExpressions(String metricsJson,
      MetricAggFunction aggFunction, String collection) throws ExecutionException {

    List<MetricExpression> metricExpressions = new ArrayList<>();
    if (metricsJson == null) {
      return metricExpressions;
    }
    ArrayList<String> metricExpressionNames;
    try {
      TypeReference<ArrayList<String>> valueTypeRef = new TypeReference<ArrayList<String>>() {
      };

      metricExpressionNames = OBJECT_MAPPER.readValue(metricsJson, valueTypeRef);
    } catch (Exception e) {
      LOG.warn("Expected json expression for metric [{}], adding as it is. Error in json parsing : [{}]", metricsJson, e.getMessage());
      metricExpressionNames = new ArrayList<>();
      String[] metrics = metricsJson.split(",");
      for (String metric : metrics) {
        metricExpressionNames.add(metric.trim());
      }
    }
    for (String metricExpressionName : metricExpressionNames) {
      String derivedMetricExpression = ThirdEyeUtils.getDerivedMetricExpression(metricExpressionName, collection);
      MetricExpression metricExpression = new MetricExpression(metricExpressionName, derivedMetricExpression,
           aggFunction);
      metricExpressions.add(metricExpression);
    }
    return metricExpressions;
  }


  public static List<MetricFunction> computeMetricFunctionsFromExpressions(
      List<MetricExpression> metricExpressions) {
    Set<MetricFunction> metricFunctions = new HashSet<>();

    for (MetricExpression expression : metricExpressions) {
      metricFunctions.addAll(expression.computeMetricFunctions());
    }
    return Lists.newArrayList(metricFunctions);
  }

  public static TimeGranularity getAggregationTimeGranularity(String aggTimeGranularity, String collection) {
    TimeGranularity timeGranularity = getNonAdditiveTimeGranularity(collection);

    if (timeGranularity == null) { // Data is additive and hence use the given time granularity -- aggTimeGranularity
      if (aggTimeGranularity.indexOf("_") > -1) {
        String[] split = aggTimeGranularity.split("_");
        timeGranularity = new TimeGranularity(Integer.parseInt(split[0]), TimeUnit.valueOf(split[1]));
      } else {
        timeGranularity = new TimeGranularity(1, TimeUnit.valueOf(aggTimeGranularity));
      }
    }
    return timeGranularity;
  }

  public static TimeGranularity getNonAdditiveTimeGranularity(String collection) {
    DatasetConfigDTO datasetConfig;
    try {
      datasetConfig = CACHE_REGISTRY.getDatasetConfigCache().get(collection);
      Integer nonAdditiveBucketSize = datasetConfig.getNonAdditiveBucketSize();
      String nonAdditiveBucketUnit = datasetConfig.getNonAdditiveBucketUnit();
      if (nonAdditiveBucketSize != null && nonAdditiveBucketUnit != null) {
        TimeGranularity timeGranularity = new TimeGranularity(datasetConfig.getNonAdditiveBucketSize(),
            TimeUnit.valueOf(datasetConfig.getNonAdditiveBucketUnit()));
        return timeGranularity;
      }
    } catch (ExecutionException e) {
      LOG.info("Exception in fetching non additive time granularity");
    }
    return null;
  }

  public static List<MetricExpression> convertToMetricExpressions(
      List<MetricFunction> metricFunctions) {
    List<MetricExpression> metricExpressions = new ArrayList<>();
    for (MetricFunction function : metricFunctions) {
      metricExpressions.add(new MetricExpression(function.getMetricName()));
    }
    return metricExpressions;
  }

  /*
   * This method returns the time zone of the data in this collection
   */
  public static DateTimeZone getDataTimeZone(String collection) throws ExecutionException {
    DatasetConfigDTO datasetConfig = CACHE_REGISTRY.getDatasetConfigCache().get(collection);
    String timezone = datasetConfig.getTimezone();
    return DateTimeZone.forID(timezone);
  }

  public static String getJsonFromObject(Object obj) throws JsonProcessingException {
    return OBJECT_MAPPER.writeValueAsString(obj);
  }

  public static Map<String, Object> getMapFromJson(String json) throws IOException {
    TypeReference<HashMap<String, Object>> typeRef = new TypeReference<HashMap<String, Object>>() {
    };
    return OBJECT_MAPPER.readValue(json, typeRef);
  }

  public static Map<String, Object> getMapFromObject(Object object) throws IOException {
    return getMapFromJson(getJsonFromObject(object));
  }
  
  public static <T extends Object> List<T> sublist(List<T> input, int startIndex, int length) {
    startIndex = Math.min(startIndex, input.size());
    int endIndex = Math.min(startIndex + length, input.size());
    List<T> subList = Lists.newArrayList(input).subList(startIndex, endIndex);
    return subList;
  }
}
