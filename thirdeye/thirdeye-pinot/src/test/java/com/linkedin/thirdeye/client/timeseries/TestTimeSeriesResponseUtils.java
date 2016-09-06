package com.linkedin.thirdeye.client.timeseries;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.joda.time.DateTime;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.linkedin.thirdeye.api.DimensionKey;
import com.linkedin.thirdeye.api.MetricSchema;
import com.linkedin.thirdeye.api.MetricTimeSeries;
import com.linkedin.thirdeye.api.MetricType;
import com.linkedin.thirdeye.client.MetricFunction;
import com.linkedin.thirdeye.client.timeseries.TimeSeriesRow.Builder;
import com.linkedin.thirdeye.client.timeseries.TimeSeriesRow.TimeSeriesMetric;
import com.linkedin.thirdeye.constant.MetricAggFunction;

public class TestTimeSeriesResponseUtils {
  private static final TimeSeriesResponseConverter converter =
      TimeSeriesResponseConverter.getInstance();

  @Test(dataProvider = "toMapProvider")
  public void toMap(String testName, TimeSeriesResponse response, List<String> schemaDimensions,
      Map<DimensionKey, MetricTimeSeries> expected) {
    Map<DimensionKey, MetricTimeSeries> actual = converter.toMap(response, schemaDimensions);
    Assert.assertEquals(actual, expected);
  }

  @DataProvider(name = "toMapProvider")
  public Object[][] toMapProvider() {
    Object[] noGroupByArgsOneMetricPerRow =
        createMapProviderArgs("noGroupByArgsOneMetricPerRow", false, false); // no dimension
    // grouping, each
    // MetricFunction
    // appears on a
    // separate row
    // from others in
    // the same time +
    // dimension key
    Object[] noGroupByArgsAllMetricsInRow =
        createMapProviderArgs("noGroupByArgsAllMetricsInRow", false, true); // no dimension
    // grouping, metric
    // functions for the
    // same time +
    // dimension key
    // will appear in
    // the same row.
    Object[] dimensionGroupByArgsOneMetricPerRow =
        createMapProviderArgs("dimensionGroupByArgsOneMetricPerRow", true, false); // dimension
    // grouping,
    // each
    // metric function appears
    // on a separate row from
    // others in the same time +
    // dimension key.

    Object[] dimensionGroupByWithAllMetricsInRow =
        createMapProviderArgs("dimensionGroupByWithAllMetricsInRow", true, true); // dimension
    // grouping,
    // metric
    // functions
    // for the
    // same time +
    // dimension
    // key appear
    // in one row.
    return new Object[][] {
        noGroupByArgsOneMetricPerRow, noGroupByArgsAllMetricsInRow,
        dimensionGroupByArgsOneMetricPerRow, dimensionGroupByWithAllMetricsInRow
    };
  }

  /**
   * return type: String testName, TimeSeriesResponse, List<String> dimensions, Map<DimensionKey,
   * MetricTimeSeries>.
   */
  public Object[] createMapProviderArgs(String testName, boolean groupByDimension,
      boolean groupTimeSeriesMetricsIntoRow) {
    List<String> dimensions = Arrays.asList("dim1", "dim2", "dim3", "all");
    List<String> dimensionValueSuffixes = Arrays.asList("_a", "_b", "_c"); // appended to each
                                                                           // dimension
    List<MetricFunction> metricFunctions = createSumFunctions("m1", "m2", "m3");
    ConversionDataGenerator dataGenerator =
        new ConversionDataGenerator(dimensions, metricFunctions);
    for (long hoursSinceEpoch = 0; hoursSinceEpoch < 50; hoursSinceEpoch++) {
      DateTime start = new DateTime(hoursSinceEpoch);
      DateTime end = start.plusHours(1);
      for (String dimension : (groupByDimension ? dimensions
          : Collections.<String> singleton("all"))) {
        for (String dimensionValueSuffix : (groupByDimension ? dimensionValueSuffixes
            : Collections.<String> singleton("all"))) {
          String dimensionValue;
          if (groupByDimension) {
            dimensionValue = dimension + dimensionValueSuffix;
          } else {
            dimensionValue = "all";
          }
          List<TimeSeriesMetric> timeSeriesMetrics = new ArrayList<>();
          for (MetricFunction metricFunction : metricFunctions) {
            Double value = (double) (Objects.hash(start, end, dimension, dimensionValue,
                metricFunction.toString()) % 1000); // doesn't matter, the test is that values are
                                                    // consistent between data
                                                    // structures.
            TimeSeriesMetric timeSeriesMetric =
                new TimeSeriesMetric(metricFunction.getMetricName(), value);
            timeSeriesMetrics.add(timeSeriesMetric);
          }
          if (groupTimeSeriesMetricsIntoRow) {
            // add them all at once
            dataGenerator.addEntry(dimension, dimensionValue, start, end,
                timeSeriesMetrics.toArray(new TimeSeriesMetric[timeSeriesMetrics.size()]));
          } else {
            // add them individually (one metric per row)
            for (TimeSeriesMetric timeSeriesMetric : timeSeriesMetrics) {
              dataGenerator.addEntry(dimension, dimensionValue, start, end, timeSeriesMetric);
            }
          }
        }
      }
    }
    Object[] dimensionGroupByArgs = new Object[] {
        testName, dataGenerator.getResponse(), dimensions, dataGenerator.getMap()
    };
    return dimensionGroupByArgs;
  }

  private List<MetricFunction> createSumFunctions(String... metricNames) {
    List<MetricFunction> result = new ArrayList<>();
    for (String metricName : metricNames) {
      result.add(new MetricFunction(MetricAggFunction.SUM, metricName));
    }
    return result;
  }

  /**
   * Helper class to test converting from TimeSeriesResponse to Map<DK,MTS>. This class will
   * simultaneously build corresponding TimeSeriesRow objects and populate a Map<DimensionKey,
   * MetricTimeSeries> with the provided addEntry method.
   */
  private class ConversionDataGenerator {
    private final List<TimeSeriesRow> timeSeriesRows = new ArrayList<>();
    private final Map<DimensionKey, MetricTimeSeries> map = new HashMap<>();
    private final List<String> dimensions;
    private final List<MetricFunction> metricFunctions;
    private final List<String> metricNames;
    private final MetricSchema metricSchema;

    ConversionDataGenerator(List<String> dimensions, List<MetricFunction> metricFunctions) {
      this.dimensions = dimensions;
      this.metricFunctions = metricFunctions;
      List<String> metricNames = new ArrayList<>();
      for (MetricFunction metricFunction : metricFunctions) {
        metricNames.add(metricFunction.getMetricName());
      }
      this.metricNames = metricNames;
      this.metricSchema =
          new MetricSchema(metricNames, Collections.nCopies(metricNames.size(), MetricType.DOUBLE));
    }

    void addEntry(String dimensionName, String dimensionValue, DateTime start, DateTime end,
        TimeSeriesMetric... timeSeriesMetrics) {
      validateArgs(dimensionName, dimensionValue, start, end, timeSeriesMetrics);
      timeSeriesRows.add(createRow(dimensionName, dimensionValue, start, end, timeSeriesMetrics));
      String[] dimensionKeyArr = new String[dimensions.size()];
      Arrays.fill(dimensionKeyArr, "*");
      if (dimensionName != null) {
        dimensionKeyArr[dimensions.indexOf(dimensionName)] = dimensionValue;
      }
      DimensionKey dimensionKey = new DimensionKey(dimensionKeyArr);
      if (!map.containsKey(dimensionKey)) {
        map.put(dimensionKey, new MetricTimeSeries(metricSchema));
      }
      incrementMetricData(map.get(dimensionKey), start, end, timeSeriesMetrics);
    }

    private void validateArgs(String dimensionName, String dimensionValue, DateTime start,
        DateTime end, TimeSeriesMetric[] timeSeriesMetrics) {
      if (dimensionName != null) {
        Assert.assertTrue(dimensions.contains(dimensionName));
      }
      for (TimeSeriesMetric metric : timeSeriesMetrics) {
        Assert.assertTrue(metricNames.contains(metric.getMetricName()));
      }

    }

    private TimeSeriesRow createRow(String dimensionName, String dimensionValue, DateTime start,
        DateTime end, TimeSeriesMetric... timeSeriesMetrics) {
      Builder builder = new TimeSeriesRow.Builder();
      builder.setDimensionName(dimensionName);
      builder.setDimensionValue(dimensionValue);
      builder.setStart(start);
      builder.setEnd(end);
      builder.addMetrics(timeSeriesMetrics);
      return builder.build();
    }

    private void incrementMetricData(MetricTimeSeries metricTimeSeries, DateTime start,
        DateTime end, TimeSeriesMetric... timeSeriesMetrics) {
      long timeWindow = start.getMillis();
      for (TimeSeriesMetric metric : timeSeriesMetrics) {
        metricTimeSeries.increment(timeWindow, metric.getMetricName(), metric.getValue());
      }
    }

    private TimeSeriesResponse getResponse() {
      return new TimeSeriesResponse(timeSeriesRows);
    }

    private Map<DimensionKey, MetricTimeSeries> getMap() {
      return map;
    }

  }

}
