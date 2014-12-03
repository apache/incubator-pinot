package com.linkedin.pinot.core.query.pruner;

import org.apache.commons.configuration.Configuration;

import com.linkedin.pinot.common.request.BrokerRequest;
import com.linkedin.pinot.core.indexsegment.IndexSegment;


/**
 * An implementation of SegmentPruner.
 * Querying tables not appearing in the given segment will be pruned.
 * If table name in the brokerRequest is null or empty string or "default",
 * it will always match all the segments for a specific resource.
 * 
 * @author xiafu
 *
 */
public class TableNameSegmentPruner implements SegmentPruner {

  @Override
  public boolean prune(IndexSegment segment, BrokerRequest brokerRequest) {
    // Prune all the query without QuerySource.
    if (brokerRequest.getQuerySource() == null) {
      return true;
    }
    // Prune all the mismatched resourceName.
    if (brokerRequest.getQuerySource().getResourceName() == null
        || !brokerRequest.getQuerySource().getResourceName().equals(segment.getSegmentMetadata().getResourceName())) {
      return true;
    }
    // For matched resourceName queries, if tableName is null or empty string, will default it as resourceName.
    if (brokerRequest.getQuerySource().getTableName() == null
        || brokerRequest.getQuerySource().getTableName().equals("")) {
      if (brokerRequest.getQuerySource().getResourceName().equals(segment.getSegmentMetadata().getTableName())) {
        return false;
      } else {
        return true;
      }
    }

    // If tableName is *, select all the segments
    if (brokerRequest.getQuerySource().getTableName().equals("*")) {
      return false;
    }

    // Get list of tableNames, and select segments within those table names.
    String[] tableNames = brokerRequest.getQuerySource().getTableName().split(",");
    for (String tableName : tableNames) {
      if (tableName.equals(segment.getSegmentMetadata().getTableName())) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void init(Configuration config) {

  }

}
