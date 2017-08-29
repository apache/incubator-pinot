package com.linkedin.thirdeye.api;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linkedin.thirdeye.util.NumberUtils;

/**
 * @author kgopalak
 */
public class MetricTimeSeries {
  private static final Logger LOGGER = LoggerFactory.getLogger(MetricTimeSeries.class);

  // Mapping from timestamp to the value of metrics. (One value per metric and multiple metrics per timestamp.)
  private Map<Long, ByteBuffer> metricsValue;

  private Map<Long, boolean[]> hasValue;

  private MetricSchema schema;

  /**
   * @param schema
   */
  public MetricTimeSeries(MetricSchema schema) {
    metricsValue = new HashMap<>();
    hasValue = new HashMap<>();
    this.schema = schema;
  }

  public MetricSchema getSchema() {
    return schema;
  }

  /**
   * @param timeWindow
   * @param value
   */
  public void set(long timeWindow, String name, Number value) {
    initBufferForTimeWindow(timeWindow);
    setTimeseries(timeWindow, name, value);
    setHasValue(timeWindow, name);
  }

  private void setTimeseries(long timeWindow, String name, Number value) {
    ByteBuffer buffer = metricsValue.get(timeWindow);
    buffer.position(schema.getOffset(name));
    MetricType metricType = schema.getMetricType(name);
    NumberUtils.addToBuffer(buffer, value, metricType);
  }

  private void setHasValue(long timeWindow, String name) {
    boolean[] buffer = hasValue.get(timeWindow);
    buffer[schema.getMetricIndex(name)] = true;
  }

  private void initBufferForTimeWindow(long timeWindow) {
    if (!metricsValue.containsKey(timeWindow)) {
      byte[] metricsValueBytes = new byte[schema.getRowSizeInBytes()];
      metricsValue.put(timeWindow, ByteBuffer.wrap(metricsValueBytes));
      boolean[] hasValueBytes = new boolean[schema.getNumMetrics()];
      hasValue.put(timeWindow, hasValueBytes);
    }
  }

  public Number get(long timeWindow, String name) {
    return getOrDefault(timeWindow, name, 0);
  }

  public Number getOrDefault(long timeWindow, String name, Number defaultNumber) {
    Number ret = null;

    boolean[] hasValueBuffer = hasValue.get(timeWindow);
    if (hasValueBuffer != null && hasValueBuffer[schema.getMetricIndex(name)]) {
      ByteBuffer buffer = metricsValue.get(timeWindow);
      if (buffer != null) {
        buffer = buffer.duplicate();
        MetricType metricType = schema.getMetricType(name);
        buffer.position(schema.getOffset(name));
        ret = NumberUtils.readFromBuffer(buffer, metricType);
      }
    }

    if (ret != null) {
      return ret;
    } else {
      return defaultNumber;
    }
  }

  public void increment(long timeWindow, String name, Number delta) {
    Number newValue = delta;
    Number oldValue = getOrDefault(timeWindow, name, null);

    if (oldValue != null) {
      MetricType metricType = schema.getMetricType(name);
      switch (metricType) {
      case SHORT:
        newValue = oldValue.shortValue() + delta.shortValue();
        break;
      case INT:
        newValue = oldValue.intValue() + delta.intValue();
        break;
      case LONG:
        newValue = oldValue.longValue() + delta.longValue();
        break;
      case FLOAT:
        newValue = oldValue.floatValue() + delta.floatValue();
        break;
      case DOUBLE:
        newValue = oldValue.doubleValue() + delta.doubleValue();
        break;
      default:
        throw new UnsupportedOperationException(
            "unknown metricType:" + metricType + " for column:" + name);
      }
    }

    set(timeWindow, name, newValue);
  }

  public void aggregate(MetricTimeSeries series) {
    for (long timeWindow : series.metricsValue.keySet()) {
      for (int i = 0; i < schema.getNumMetrics(); i++) {
        String metricName = schema.getMetricName(i);
        Number delta = series.getOrDefault(timeWindow, metricName, null);
        if (delta != null) {
          increment(timeWindow, metricName, delta);
        }
      }
    }
  }

  /**
   * @param series
   *          A time series whose values should be reflected in this time series
   * @param timeRange
   *          Only include values from series that are in this time range
   */
  public void aggregate(MetricTimeSeries series, TimeRange timeRange) {
    for (long timeWindow : series.metricsValue.keySet()) {
      if (timeRange.contains(timeWindow)) {
        for (int i = 0; i < schema.getNumMetrics(); i++) {
          String metricName = schema.getMetricName(i);
          Number delta = series.getOrDefault(timeWindow, metricName, null);
          if (delta != null) {
            increment(timeWindow, metricName, delta);
          }
        }
      }
    }
  }

  // TODO: Consider default null value; before that, this method is set to private
  private static MetricTimeSeries fromBytes(byte[] buf, MetricSchema schema) throws IOException {
    MetricTimeSeries series = new MetricTimeSeries(schema);
    DataInput in = new DataInputStream(new ByteArrayInputStream(buf));
    int numTimeWindows = in.readInt();
    int bufferSize = in.readInt();
    for (int i = 0; i < numTimeWindows; i++) {
      long timeWindow = in.readLong();
      byte[] bytes = new byte[bufferSize];
      in.readFully(bytes);
      series.metricsValue.put(timeWindow, ByteBuffer.wrap(bytes));
      boolean[] hasValues = new boolean[schema.getNumMetrics()];
      for (int numMetrics = 0; numMetrics < schema.getNumMetrics(); numMetrics++) {
        hasValues[numMetrics] = true;
      }
      series.hasValue.put(timeWindow, hasValues);
    }
    return series;
  }

  /**
   * @return
   */
  public Set<Long> getTimeWindowSet() {
    return metricsValue.keySet();
  }

  // TODO: Consider default null value; before that, this method is set to private
  private byte[] toBytes() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutput out = new DataOutputStream(baos);
    // write the number of timeWindows
    out.writeInt(metricsValue.size());
    // write the size of the metric buffer for each timeWindow
    out.writeInt(schema.getRowSizeInBytes());
    for (long time : metricsValue.keySet()) {
      out.writeLong(time);
      out.write(metricsValue.get(time).array());
    }
    return baos.toByteArray();
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("(");
    for (long timeWindow : metricsValue.keySet()) {
      sb.append("[");
      String delim = "";
      for (int i = 0; i < schema.getNumMetrics(); i++) {
        if (i > 0) {
          delim = ",";
        }
        sb.append(delim);
        Number number = getOrDefault(timeWindow, schema.getMetricName(i), null);
        if (number != null) {
          sb.append(number);
        } else {
          // TODO: read user specified null value from schema
          sb.append("null");
        }
      }
      sb.append("]");
      sb.append("@");
      sb.append(timeWindow);
      sb.append(" ");
    }
    sb.setLength(sb.length() - 1);
    sb.append(")");
    return sb.toString();
  }

  public Number[] getMetricSums() {
    Number[] result = new Number[schema.getNumMetrics()];

    for (int i = 0; i < schema.getNumMetrics(); i++) {
      result[i] = 0;
    }

    for (Long time : metricsValue.keySet()) {
      for (int i = 0; i < schema.getNumMetrics(); i++) {
        String metricName = schema.getMetricName(i);
        MetricType metricType = schema.getMetricType(i);
        Number metricValue = get(time, metricName);

        switch (metricType) {
        case INT:
          result[i] = result[i].intValue() + metricValue.intValue();
          break;
        case SHORT:
          result[i] = result[i].shortValue() + metricValue.shortValue();
          break;
        case LONG:
          result[i] = result[i].longValue() + metricValue.longValue();
          break;
        case FLOAT:
          result[i] = result[i].floatValue() + metricValue.floatValue();
          break;
        case DOUBLE:
          result[i] = result[i].doubleValue() + metricValue.doubleValue();
          break;
        default:
          throw new IllegalStateException();
        }
      }
    }

    return result;
  }

  private Integer[] getHasValueSums() {
    Integer[] result = new Integer[schema.getNumMetrics()];

    for (int i = 0; i < schema.getNumMetrics(); i++) {
      result[i] = 0;
    }

    for (Long time : hasValue.keySet()) {
      boolean[] booleans = hasValue.get(time);
      for (int i = 0; i < schema.getNumMetrics(); i++) {
        for (boolean aBoolean : booleans) {
          if (aBoolean) {
            result[i] = result[i] + 1;
          }
        }
      }
    }

    return result;
  }

  @Override
  public int hashCode() {
    return metricsValue.keySet().hashCode() + 13 * schema.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof MetricTimeSeries)) {
      return false;
    }

    MetricTimeSeries ts = (MetricTimeSeries) o;

    return getTimeWindowSet().equals(ts.getTimeWindowSet())
        && Arrays.equals(getMetricSums(), ts.getMetricSums())
        && Arrays.equals(getHasValueSums(), ts.getHasValueSums());
  }
}
