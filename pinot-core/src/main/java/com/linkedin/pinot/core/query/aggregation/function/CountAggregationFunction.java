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
package com.linkedin.pinot.core.query.aggregation.function;

import com.linkedin.pinot.common.utils.primitive.MutableLongValue;
import java.io.Serializable;
import com.linkedin.pinot.common.Utils;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

import com.linkedin.pinot.common.data.FieldSpec.DataType;
import com.linkedin.pinot.common.request.AggregationInfo;
import com.linkedin.pinot.core.common.Block;
import com.linkedin.pinot.core.operator.blocks.DocIdSetBlock;
import com.linkedin.pinot.core.query.aggregation.AggregationFunction;
import com.linkedin.pinot.core.query.aggregation.CombineLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This function will take a column and do sum on that.
 *
 */
public class CountAggregationFunction implements AggregationFunction<Number, Number> {
  private static final Logger LOGGER = LoggerFactory.getLogger(CountAggregationFunction.class);

  public CountAggregationFunction() {

  }

  @Override
  public void init(AggregationInfo aggregationInfo) {

  }

  @Override
  public MutableLongValue aggregate(Block docIdSetBlock, Block[] block) {
    return new MutableLongValue(((DocIdSetBlock) docIdSetBlock).getSearchableLength());
  }

  @Override
  public Number aggregate(Number mergedResult, int docId, Block[] block) {
    if (mergedResult == null) {
      return new MutableLongValue(1L);
    } else {
      ((MutableLongValue) mergedResult).addToValue(1L);
      return mergedResult;
    }
  }

  @Override
  public List<Number> combine(List<Number> aggregationResultList, CombineLevel combineLevel) {
    long combinedValue = 0;
    for (Number value : aggregationResultList) {
      combinedValue += value.longValue();
    }
    aggregationResultList.clear();
    aggregationResultList.add(new MutableLongValue(combinedValue));
    return aggregationResultList;
  }

  @Override
  public Number combineTwoValues(Number aggregationResult0, Number aggregationResult1) {
    if (aggregationResult0 == null) {
      return aggregationResult1;
    }
    if (aggregationResult1 == null) {
      return aggregationResult0;
    }
    return new MutableLongValue(aggregationResult0.longValue() + aggregationResult1.longValue());
  }

  @Override
  public Number reduce(List<Number> combinedResultList) {
    long reducedValue = 0;
    for (Number value : combinedResultList) {
      reducedValue += value.longValue();
    }
    return new MutableLongValue(reducedValue);
  }

  @Override
  public JSONObject render(Number reduceResult) {
    try {
      if (reduceResult == null) {
        reduceResult = new MutableLongValue(0L);
      }
      return new JSONObject().put("value", reduceResult.toString());
    } catch (JSONException e) {
      LOGGER.error("Caught exception while rendering to JSON", e);
      Utils.rethrowException(e);
      throw new AssertionError("Should not reach this");
    }
  }

  @Override
  public DataType aggregateResultDataType() {
    return DataType.LONG;
  }

  @Override
  public String getFunctionName() {
    return "count_star";
  }

  @Override
  public Serializable getDefaultValue() {
    return new MutableLongValue(0L);
  }

}
