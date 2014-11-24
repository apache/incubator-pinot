package com.linkedin.pinot.core.query.aggregation;

import java.io.Serializable;
import java.util.List;

import org.json.JSONObject;

import com.linkedin.pinot.common.data.FieldSpec.DataType;
import com.linkedin.pinot.common.request.AggregationInfo;
import com.linkedin.pinot.core.common.Block;
import com.linkedin.pinot.core.common.BlockValIterator;


/**
 * By extending this interface, one can access the index segment data, produce intermediate results for a given
 * segment, then aggregate those results on instance and router level.
 * 
 */
public interface AggregationFunction<AggregateResult extends Serializable, ReduceResult extends Serializable> extends
    Serializable {

  /**
   * Initialized the aggregation funtion from aggregation info
   * @param aggregationInfo
   */
  public void init(AggregationInfo aggregationInfo);

  /**
   * Aggregate function used by AggregationFunctionOperator. 
   * It gets multiple blocks and do aggregations.
   * @param docIdSetBlock
   * @param block
   * @return
   */
  AggregateResult aggregate(Block docIdSetBlock, Block[] block);

  /**
   * Aggregate function used by AggregationFunctionOperator. 
   * It gets multiple dataSourceIterators and do aggregations.
   * 
   * @param blockValIterator
   * @return
   */
  AggregateResult aggregate(BlockValIterator[] blockValIterator);

  /**
   * Aggregate function used by AggregationFunctionGroupByOperator. 
   * It gets multiple dataSourceIterators and only call next to get one result.
   * Then merge this result to mergedResult.
   * 
   * @param serializable
   * @param _blockValIterators
   * @return
   */
  AggregateResult aggregate(AggregateResult mergedResult, BlockValIterator[] blockValIterators);

  /**
   * Aggregate function used by AggregationFunctionGroupByOperator. 
   * It gets multiple blocks and only call next to get one result.
   * Then merge this result to mergedResult.
   * 
   * @param mergedResult
   * @param docId 
   * @param block
   * @return
   */
  AggregateResult aggregate(AggregateResult mergedResult, int docId, Block[] block);

  /**
   * Take a list of intermediate results and do intermediate merge.
   * 
   * @param aggregationResultList
   * @param combineLevel
   * @return intermediate merge results
   */
  List<AggregateResult> combine(List<AggregateResult> aggregationResultList, CombineLevel combineLevel);

  /**
   * Take two intermediate results and do merge.
   * 
   * @param aggregationResult0
   * @param aggregationResult1
   * @return intermediate merge results
   */
  AggregateResult combineTwoValues(AggregateResult aggregationResult0, AggregateResult aggregationResult1);

  /**
   * Take a list of intermediate results and merge them.
   * 
   * @param aggregationResultList
   * @return final merged results
   */
  ReduceResult reduce(List<AggregateResult> combinedResultList);

  /**
   * Return a JsonObject representation for the final aggregation result.
   * 
   * @param finalAggregationResult
   * @return final results in Json format
   */
  JSONObject render(ReduceResult finalAggregationResult);

  /**
   * Return data type of aggregateResult.
   * 
   * @return DataType
   */
  DataType aggregateResultDataType();

  /**
   * Return function name + column name. Should be unique in one query.
   * 
   * @return functionName
   */
  String getFunctionName();

}
