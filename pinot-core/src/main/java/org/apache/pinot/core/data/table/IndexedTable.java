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
package org.apache.pinot.core.data.table;

import java.util.List;
import javax.annotation.Nonnull;
import org.apache.commons.collections.CollectionUtils;
import org.apache.pinot.common.request.AggregationInfo;
import org.apache.pinot.common.request.SelectionSort;
import org.apache.pinot.common.utils.DataSchema;
import org.apache.pinot.core.query.aggregation.function.AggregationFunction;
import org.apache.pinot.core.query.aggregation.function.AggregationFunctionUtils;


/**
 * Base abstract implementation of Table for indexed lookup
 */
public abstract class IndexedTable implements Table {

  AggregationFunction[] _aggregationFunctions;
  int _numAggregations;
  DataSchema _dataSchema;

  int _capacity;
  int _bufferedCapacity;

  boolean _isOrderBy;
  IndexedTableResizer _indexedTableResizer;

  @Override
  public void init(@Nonnull DataSchema dataSchema, List<AggregationInfo> aggregationInfos, List<SelectionSort> orderBy,
      int capacity) {
    _dataSchema = dataSchema;

    _numAggregations = aggregationInfos.size();
    _aggregationFunctions = new AggregationFunction[_numAggregations];
    for (int i = 0; i < _numAggregations; i++) {
      _aggregationFunctions[i] =
          AggregationFunctionUtils.getAggregationFunctionContext(aggregationInfos.get(i)).getAggregationFunction();
    }

    _capacity = capacity;
    // TODO: tune these numbers
    if (capacity <= 100_000) { // Capacity is small, make a very large buffer. Make PQ of records to retain, during resize
      _bufferedCapacity = 1_000_000;
    } else { // Capacity is large, make buffer only slightly bigger. Make PQ of records to evict, during resize
      _bufferedCapacity = (int) (capacity * 1.2);
    }

    _isOrderBy = CollectionUtils.isNotEmpty(orderBy);
    if (_isOrderBy) {
      _indexedTableResizer = new OrderedIndexedTableResizer(dataSchema, aggregationInfos, orderBy);
    } else {
      _indexedTableResizer = new RandomIndexedTableResizer();
    }
  }
}
