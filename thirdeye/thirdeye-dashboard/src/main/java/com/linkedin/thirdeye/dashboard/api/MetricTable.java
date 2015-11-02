package com.linkedin.thirdeye.dashboard.api;

import java.util.List;
import java.util.Map;


public class MetricTable {
  private final Map<String, String> dimensionValues;
  private final List<MetricDataRow> rows;
  private final List<MetricDataRow> cumulativeRows;

  public MetricTable(Map<String, String> dimensionValues, List<MetricDataRow> rows,
      List<MetricDataRow> cumulativeRows) {
    this.dimensionValues = dimensionValues;
    this.rows = rows;
    this.cumulativeRows = cumulativeRows;
  }

  public Map<String, String> getDimensionValues() {
    return dimensionValues;
  }

  public List<MetricDataRow> getRows() {
    return rows;
  }

  public List<MetricDataRow> getCumulativeRows() {
    return cumulativeRows;
  }
}
