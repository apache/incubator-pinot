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
package com.linkedin.pinot.core.query.aggregation;

import com.linkedin.pinot.common.Utils;
import java.util.ArrayList;
import java.util.List;

import com.linkedin.pinot.common.request.AggregationInfo;
import com.linkedin.pinot.common.request.BrokerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class AggregationFunctionFactory {
  private static final Logger LOGGER = LoggerFactory.getLogger(AggregationFunctionFactory.class);

  public static AggregationFunction get(AggregationInfo aggregationInfo, boolean hasDictionary) {
    try {
      String aggregationKey = aggregationInfo.getAggregationType();
      if (hasDictionary) {
        AggregationFunction aggregationFunction = AggregationFunctionRegistry.get(aggregationKey);
        aggregationFunction.init(aggregationInfo);
        return aggregationFunction;
      } else {
        AggregationFunction aggregationFunction = AggregationFunctionRegistry.getAggregationNoDictionaryFunction(aggregationKey);
        aggregationFunction.init(aggregationInfo);
        return aggregationFunction;
      }
    } catch (Exception ex) {
      LOGGER.error("Caught exception while building aggregation function", ex);
      Utils.rethrowException(ex);
      throw new AssertionError("Should not reach here");
    }
  }

  public static List<AggregationFunction> getAggregationFunction(BrokerRequest query) {
    List<AggregationFunction> aggregationFunctions = new ArrayList<AggregationFunction>();
    for (com.linkedin.pinot.common.request.AggregationInfo agg : query.getAggregationsInfo()) {
      AggregationFunction agg1 = AggregationFunctionFactory.get(agg, true);
      aggregationFunctions.add(agg1);
    }
    return aggregationFunctions;
  }

  public static List<AggregationFunction> getAggregationFunction(List<AggregationInfo> aggregationInfos) {
    List<AggregationFunction> aggregationFunctions = new ArrayList<AggregationFunction>();
    for (AggregationInfo agg : aggregationInfos) {
      AggregationFunction agg1 = AggregationFunctionFactory.get(agg, true);
      aggregationFunctions.add(agg1);
    }
    return aggregationFunctions;
  }

}
