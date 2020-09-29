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
package org.apache.pinot.core.query.aggregation.function;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Map;
import org.apache.pinot.common.function.AggregationFunctionType;
import org.apache.pinot.common.function.scalar.DataTypeConversionFunctions;
import org.apache.pinot.common.utils.DataSchema;
import org.apache.pinot.core.common.BlockValSet;
import org.apache.pinot.core.query.aggregation.AggregationResultHolder;
import org.apache.pinot.core.query.aggregation.ObjectAggregationResultHolder;
import org.apache.pinot.core.query.aggregation.groupby.GroupByResultHolder;
import org.apache.pinot.core.query.aggregation.groupby.ObjectGroupByResultHolder;
import org.apache.pinot.core.query.request.context.ExpressionContext;


public class SumWithPrecisionAggregationFunction extends BaseSingleInputAggregationFunction<BigDecimal, BigDecimal> {
  MathContext _mathContext = new MathContext(0);
  Integer _scale = null;

  public SumWithPrecisionAggregationFunction(ExpressionContext expression, List<ExpressionContext> arguments) {
    super(expression);
    int numArguments = arguments.size();

    if (numArguments == 3) {
      Integer precision = Integer.parseInt(arguments.get(1).getLiteral());
      _scale = Integer.parseInt(arguments.get(2).getLiteral());
      _mathContext = new MathContext(precision);
    } else if (numArguments == 2) {
      Integer precision = Integer.parseInt(arguments.get(1).getLiteral());
      _mathContext = new MathContext(precision);
    }
  }

  @Override
  public AggregationFunctionType getType() {
    return AggregationFunctionType.SUMPRECISION;
  }

  @Override
  public AggregationResultHolder createAggregationResultHolder() {
    return new ObjectAggregationResultHolder();
  }

  @Override
  public GroupByResultHolder createGroupByResultHolder(int initialCapacity, int maxCapacity) {
    return new ObjectGroupByResultHolder(initialCapacity, maxCapacity);
  }

  @Override
  public void aggregate(int length, AggregationResultHolder aggregationResultHolder,
      Map<ExpressionContext, BlockValSet> blockValSetMap) {
    byte[][] valueArray = blockValSetMap.get(_expression).getBytesValuesSV();
    BigDecimal sumValue = getDefaultResult(aggregationResultHolder);
    for (int i = 0; i < length; i++) {
      BigDecimal value = new BigDecimal(DataTypeConversionFunctions.bytesToBigDecimal(valueArray[i]));
      sumValue = sumValue.add(value, _mathContext);
    }
    aggregationResultHolder.setValue(setScale(sumValue));
  }

  @Override
  public void aggregateGroupBySV(int length, int[] groupKeyArray, GroupByResultHolder groupByResultHolder,
      Map<ExpressionContext, BlockValSet> blockValSetMap) {
    byte[][] valueArray = blockValSetMap.get(_expression).getBytesValuesSV();
    for (int i = 0; i < length; i++) {
      int groupKey = groupKeyArray[i];
      BigDecimal groupByResultValue = getDefaultResult(groupByResultHolder, groupKey);
      BigDecimal value = new BigDecimal(DataTypeConversionFunctions.bytesToBigDecimal(valueArray[i]));
      groupByResultValue = groupByResultValue.add(value, _mathContext);
      groupByResultHolder.setValueForKey(groupKey, setScale(groupByResultValue));
    }
  }

  @Override
  public void aggregateGroupByMV(int length, int[][] groupKeysArray, GroupByResultHolder groupByResultHolder,
      Map<ExpressionContext, BlockValSet> blockValSetMap) {
    byte[][] valueArray = blockValSetMap.get(_expression).getBytesValuesSV();
    for (int i = 0; i < length; i++) {
      byte[] value = valueArray[i];
      for (int groupKey : groupKeysArray[i]) {
        BigDecimal groupByResultValue = getDefaultResult(groupByResultHolder, groupKey);
        BigDecimal valueBigDecimal = new BigDecimal(DataTypeConversionFunctions.bytesToBigDecimal(value));
        groupByResultValue = groupByResultValue.add(valueBigDecimal, _mathContext);
        groupByResultHolder.setValueForKey(groupKey, setScale(groupByResultValue));
      }
    }
  }

  @Override
  public BigDecimal extractAggregationResult(AggregationResultHolder aggregationResultHolder) {
    return getDefaultResult(aggregationResultHolder);
  }

  @Override
  public BigDecimal extractGroupByResult(GroupByResultHolder groupByResultHolder, int groupKey) {
    return getDefaultResult(groupByResultHolder, groupKey);
  }

  @Override
  public BigDecimal merge(BigDecimal intermediateResult1, BigDecimal intermediateResult2) {
    try {
      return setScale(intermediateResult1.add(intermediateResult2, _mathContext));
    } catch (Exception e) {
      throw new RuntimeException("Caught Exception while merging results in sum with precision function", e);
    }
  }

  @Override
  public boolean isIntermediateResultComparable() {
    return true;
  }

  @Override
  public DataSchema.ColumnDataType getIntermediateResultColumnType() {
    return DataSchema.ColumnDataType.OBJECT;
  }

  @Override
  public DataSchema.ColumnDataType getFinalResultColumnType() {
    return DataSchema.ColumnDataType.STRING;
  }

  @Override
  public BigDecimal extractFinalResult(BigDecimal intermediateResult) {
    return intermediateResult;
  }

  public BigDecimal getDefaultResult(AggregationResultHolder aggregationResultHolder) {
    BigDecimal result = aggregationResultHolder.getResult();
    if (result == null) {
      result = new BigDecimal(0, _mathContext);
      aggregationResultHolder.setValue(result);
    }
    result = setScale(result);
    return result;
  }

  public BigDecimal getDefaultResult(GroupByResultHolder groupByResultHolder, int groupKey) {
    BigDecimal result = groupByResultHolder.getResult(groupKey);
    if (result == null) {
      result = new BigDecimal(0, _mathContext);
      groupByResultHolder.setValueForKey(groupKey, result);
    }
    result = setScale(result);
    return result;
  }

  private BigDecimal setScale(BigDecimal value) {
    if (_scale != null) {
      value = value.setScale(_scale, BigDecimal.ROUND_HALF_EVEN);
    }
    return value;
  }
}
