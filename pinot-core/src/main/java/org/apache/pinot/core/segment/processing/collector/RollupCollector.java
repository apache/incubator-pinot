/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.pinot.core.segment.processing.collector;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.apache.pinot.core.data.table.Record;
import org.apache.pinot.spi.data.FieldSpec;
import org.apache.pinot.spi.data.MetricFieldSpec;
import org.apache.pinot.spi.data.Schema;
import org.apache.pinot.spi.data.readers.GenericRow;


/**
 * A Collector that rolls up the incoming records on unique dimensions + time columns, based on provided aggregation types for metrics.
 * By default will use the SUM aggregation on metrics.
 */
public class RollupCollector implements Collector {

  private final Map<Record, GenericRow> _collection = new HashMap<>();

  private final int _keySize;
  private final int _valueSize;
  private final String[] _keyColumns;
  private final String[] _valueColumns;
  private final ValueAggregator[] _valueAggregators;
  private final MetricFieldSpec[] _metricFieldSpecs;

  public RollupCollector(CollectorConfig collectorConfig, Schema schema) {
    _keySize = schema.getColumnNames().size() - schema.getMetricNames().size();
    _valueSize = schema.getMetricNames().size();
    _keyColumns = new String[_keySize];
    _valueColumns = new String[_valueSize];
    _valueAggregators = new ValueAggregator[_valueSize];
    _metricFieldSpecs = new MetricFieldSpec[_valueSize];

    Map<String, ValueAggregatorFactory.ValueAggregatorType> aggregatorTypeMap = collectorConfig.getAggregatorTypeMap();
    if (aggregatorTypeMap == null) {
      aggregatorTypeMap = Collections.emptyMap();
    }
    int valIdx = 0;
    int keyIdx = 0;
    for (FieldSpec fieldSpec : schema.getAllFieldSpecs()) {
      if (!fieldSpec.isVirtualColumn()) {
        String name = fieldSpec.getName();
        if (fieldSpec.getFieldType().equals(FieldSpec.FieldType.METRIC)) {
          _metricFieldSpecs[valIdx] = (MetricFieldSpec) fieldSpec;
          _valueColumns[valIdx] = name;
          _valueAggregators[valIdx] = ValueAggregatorFactory.getValueAggregator(
              aggregatorTypeMap.getOrDefault(name, ValueAggregatorFactory.ValueAggregatorType.SUM).toString());
          valIdx++;
        } else {
          _keyColumns[keyIdx++] = name;
        }
      }
    }
  }

  /**
   * If a row already exists in the collection (based on dimension + time columns), rollup the metric values, else add the row
   */
  @Override
  public void collect(GenericRow genericRow) {
    Object[] key = new Object[_keySize];
    for (int i = 0; i < _keySize; i++) {
      key[i] = genericRow.getValue(_keyColumns[i]);
    }
    Record keyRecord = new Record(key);
    GenericRow prev = _collection.get(keyRecord);
    if (prev == null) {
      _collection.put(keyRecord, genericRow);
    } else {
      for (int i = 0; i < _valueSize; i++) {
        String valueColumn = _valueColumns[i];
        Object aggregate = _valueAggregators[i]
            .aggregate(prev.getValue(valueColumn), genericRow.getValue(valueColumn), _metricFieldSpecs[i]);
        prev.putValue(valueColumn, aggregate);
      }
    }
  }

  @Override
  public Iterator<GenericRow> iterator() {
    return _collection.values().iterator();
  }

  @Override
  public int size() {
    return _collection.size();
  }

  @Override
  public void reset() {
    _collection.clear();
  }
}
