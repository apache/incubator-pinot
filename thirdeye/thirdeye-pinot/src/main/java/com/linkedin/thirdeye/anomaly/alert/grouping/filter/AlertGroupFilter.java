package com.linkedin.thirdeye.anomaly.alert.grouping.filter;

import com.linkedin.thirdeye.datalayer.dto.GroupedAnomalyResultsDTO;
import java.util.Map;

public interface AlertGroupFilter {

  /**
   * Sets the properties of this grouper.
   *
   * @param props the properties for this grouper.
   */
  void setParameters(Map<String, String> props);

  /**
   * Returns if the given grouped anomaly is qualified for passing through the filter.
   *
   * @param groupedAnomaly the given grouped anomaly.
   *
   * @return true if the given grouped anomaly passes through the filter.
   */
  boolean isQualified(GroupedAnomalyResultsDTO groupedAnomaly);
}
