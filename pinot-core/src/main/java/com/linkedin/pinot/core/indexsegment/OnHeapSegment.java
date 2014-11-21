package com.linkedin.pinot.core.indexsegment;

import java.util.Random;

import com.linkedin.pinot.common.segment.SegmentMetadata;
import com.linkedin.pinot.core.common.Predicate;
import com.linkedin.pinot.core.operator.DataSource;


public class OnHeapSegment implements IndexSegment {
  private static int[] intArray;
  private static final int BLOCK_SIZE = 100000;
  private static final int CARDINALITY = 10000;
  private static final int NUM_DOCS = 10000000;

  static {
    intArray = new int[NUM_DOCS];
    final Random r = new Random();
    for (int i = 0; i < intArray.length; i++) {
      intArray[i] = i % 10;
    }
  }

  @Override
  public DataSource getDataSource(String columnName) {
    return null;
  }

  @Override
  public DataSource getDataSource(String columnName, Predicate p) {
    return null;
  }

  @Override
  public IndexType getIndexType() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String getSegmentName() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String getAssociatedDirectory() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public SegmentMetadata getSegmentMetadata() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String[] getColumnNames() {
    // TODO Auto-generated method stub
    return null;
  }

}
