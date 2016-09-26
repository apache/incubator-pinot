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
package com.linkedin.pinot.common.metrics;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import com.yammer.metrics.core.Gauge;
import com.yammer.metrics.core.MetricName;
import com.yammer.metrics.core.MetricsRegistry;


/**
 * Validation metrics utility class, which contains the glue code to publish metrics.
 *
 */
public class ValidationMetrics {
  private final MetricsRegistry _metricsRegistry;

  private final Map<String, Long> gaugeValues = new HashMap<String, Long>();

  /**
   * A simple gauge that returns whatever last value was stored in the gaugeValues hash map.
   */
  private class StoredValueGauge extends Gauge<Long> {
    private final String key;

    public StoredValueGauge(String key) {
      this.key = key;
    }

    @Override
    public Long value() {
      return gaugeValues.get(key);
    }
  }

  /**
   * A simple gauge that returns the difference between the current system time in millis and the value stored in the
   * gaugeValues hash map.
   */
  private class CurrentTimeMillisDeltaGauge extends Gauge<Long> {
    private final String key;

    public CurrentTimeMillisDeltaGauge(String key) {
      this.key = key;
    }

    @Override
    public Long value() {
      Long gaugeValue = gaugeValues.get(key);

      if (gaugeValue != null && gaugeValue != Long.MIN_VALUE)
        return System.currentTimeMillis() - gaugeValue;
      else
        return Long.MIN_VALUE;
    }
  }

  /**
   * A simple gauge that returns the difference in hours between the current system time and the value stored in the
   * gaugeValues hash map.
   */
  private class CurrentTimeMillisDeltaGaugeHours extends Gauge<Double> {
    private final String key;

    private final double MILLIS_PER_HOUR = TimeUnit.MILLISECONDS.convert(1, TimeUnit.HOURS);

    public CurrentTimeMillisDeltaGaugeHours(String key) {
      this.key = key;
    }

    @Override
    public Double value() {
      Long gaugeValue = gaugeValues.get(key);

      if (gaugeValue != null && gaugeValue != Long.MIN_VALUE)
        return (System.currentTimeMillis() - gaugeValue) / MILLIS_PER_HOUR;
      else
        return Double.MIN_VALUE;
    }
  }

  private interface GaugeFactory<T> {
    Gauge<T> buildGauge(final String key);
  }

  private class StoredValueGaugeFactory implements GaugeFactory<Long> {
    @Override
    public Gauge<Long> buildGauge(final String key) {
      return new StoredValueGauge(key);
    }
  }

  private class CurrentTimeMillisDeltaGaugeFactory implements GaugeFactory<Long> {
    @Override
    public Gauge<Long> buildGauge(final String key) {
      return new CurrentTimeMillisDeltaGauge(key);
    }
  }

  private class CurrentTimeMillisDeltaGaugeHoursFactory implements GaugeFactory<Double> {
    @Override
    public Gauge<Double> buildGauge(final String key) {
      return new CurrentTimeMillisDeltaGaugeHours(key);
    }
  }

  private final StoredValueGaugeFactory _storedValueGaugeFactory = new StoredValueGaugeFactory();
  private final CurrentTimeMillisDeltaGaugeFactory _currentTimeMillisDeltaGaugeFactory = new CurrentTimeMillisDeltaGaugeFactory();
  private final CurrentTimeMillisDeltaGaugeHoursFactory _currentTimeMillisDeltaGaugeHoursFactory = new CurrentTimeMillisDeltaGaugeHoursFactory();

  /**
   * Builds the validation metrics.
   *
   * @param metricsRegistry The metrics registry used to store all the gauges.
   */
  public ValidationMetrics(MetricsRegistry metricsRegistry) {
    _metricsRegistry = metricsRegistry;
  }

  /**
   * Updates the gauge for the number of missing segments.
   *
   * @param resource The resource for which the gauge is updated
   * @param missingSegmentCount The number of missing segments
   */
  public void updateMissingSegmentsGauge(final String resource, final int missingSegmentCount) {
    final String fullGaugeName = makeGaugeName(resource, "missingSegmentCount");
    makeGauge(fullGaugeName, makeMetricName(fullGaugeName), _storedValueGaugeFactory, missingSegmentCount);
  }

  /**
   * Updates the gauge for the offline segment delay.
   *
   * @param resource The resource for which the gauge is updated
   * @param lastOfflineSegmentTime The last offline segment end time, in milliseconds since the epoch, or Long.MIN_VALUE
   *                               if there is no such time.
   */
  public void updateOfflineSegmentDelayGauge(final String resource, final long lastOfflineSegmentTime) {
    final String fullGaugeName = makeGaugeName(resource, "offlineSegmentDelayMillis");
    makeGauge(fullGaugeName, makeMetricName(fullGaugeName), _currentTimeMillisDeltaGaugeFactory, lastOfflineSegmentTime);
    final String fullGaugeNameHours = makeGaugeName(resource, "offlineSegmentDelayHours");
    makeGauge(fullGaugeNameHours, makeMetricName(fullGaugeNameHours), _currentTimeMillisDeltaGaugeHoursFactory, lastOfflineSegmentTime);
  }

  /**
   * Updates the gauge for the last push time.
   *
   * @param resource The resource for which the gauge is updated
   * @param lastPushTimeMillis The last push time, in milliseconds since the epoch, or Long.MIN_VALUE if there is no
   *                           such time.
   */
  public void updateLastPushTimeGauge(final String resource, final long lastPushTimeMillis) {
    final String fullGaugeName = makeGaugeName(resource, "lastPushTimeDelayMillis");
    makeGauge(fullGaugeName, makeMetricName(fullGaugeName), _currentTimeMillisDeltaGaugeFactory, lastPushTimeMillis);
    final String fullGaugeNameHours = makeGaugeName(resource, "lastPushTimeDelayHours");
    makeGauge(fullGaugeNameHours, makeMetricName(fullGaugeNameHours), _currentTimeMillisDeltaGaugeHoursFactory, lastPushTimeMillis);
  }

  /**
   * Updates the gauge for the Total Document Count
   *
   * @param resource The resource for which the gauge is updated
   * @param documentCount Total document count for the give resource name / tablename
   */
  public void updateTotalDocumentsGauge(final String resource, final long documentCount)
  {
    final String fullGaugeName = makeGaugeName(resource, "TotalDocumentCount");
    makeGauge(fullGaugeName, makeMetricName(fullGaugeName), _storedValueGaugeFactory, documentCount);
  }

  /**
   *
   * @param resource The resource for which the guage is updated
   * @param partitionCount Number of kafka partitions that do not have any segment in CONSUMING state.
   */
  public void updateNumNonConsumingPartitionsMetric(final String resource, final int partitionCount) {
    final String fullGaugeName = makeGaugeName(resource, "NonConsumingPartitionCount");
    makeGauge(fullGaugeName, makeMetricName(fullGaugeName), _storedValueGaugeFactory, partitionCount);

  }

  /**
   * Updates the gauge for the Total segment count
   *
   * @param resource The resource for which the gauge is updated
   * @param segmentCount Total segment count for the give resource name / tablename
   */
  public void updateSegmentCountGauge(final String resource, final long segmentCount)
  {
    final String fullGaugeName = makeGaugeName(resource, "SegmentCount");
    makeGauge(fullGaugeName, makeMetricName(fullGaugeName), _storedValueGaugeFactory, segmentCount);
  }

  private String makeGaugeName(final String resource, final String gaugeName) {
    return "pinot.controller." + resource + "." + gaugeName;
  }

  private MetricName makeMetricName(final String gaugeName) {
    return new MetricName(ValidationMetrics.class, gaugeName);
  }

  private void makeGauge(final String gaugeName, final MetricName metricName, final GaugeFactory<?> gaugeFactory, final long value) {
    if (!gaugeValues.containsKey(gaugeName)) {
      gaugeValues.put(gaugeName, value);
      MetricsHelper.newGauge(_metricsRegistry, metricName, gaugeFactory.buildGauge(gaugeName));
    } else {
      gaugeValues.put(gaugeName, value);
    }
  }
}
