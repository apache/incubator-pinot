package com.linkedin.thirdeye.datalayer.pojo;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.base.MoreObjects;
import com.linkedin.thirdeye.api.MetricType;

@JsonIgnoreProperties(ignoreUnknown=true)
public class MetricConfigBean extends AbstractBean {

  public static double DEFAULT_THRESHOLD = 0.01;
  public static String DERIVED_METRIC_ID_PREFIX = "id";
  public static final String ALIAS_JOINER = "::";

  private String name;

  private String dataset;

  private String alias;

  private MetricType datatype;

  private boolean derived = false;

  private String derivedMetricExpression;

  private Double rollupThreshold = DEFAULT_THRESHOLD;

  private boolean inverseMetric = false;

  private String cellSizeExpression;

  private boolean active = true;

  private String externalDashboardURL;


  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDataset() {
    return dataset;
  }

  public void setDataset(String dataset) {
    this.dataset = dataset;
  }

  public String getAlias() {
    return alias;
  }

  public void setAlias(String alias) {
    this.alias = alias;
  }

  public MetricType getDatatype() {
    return datatype;
  }

  public void setDatatype(MetricType datatype) {
    this.datatype = datatype;
  }

  public boolean isDerived() {
    return derived;
  }

  public void setDerived(boolean derived) {
    this.derived = derived;
  }

  public String getDerivedMetricExpression() {
    return derivedMetricExpression;
  }

  public void setDerivedMetricExpression(String derivedMetricExpression) {
    this.derivedMetricExpression = derivedMetricExpression;
  }

  public Double getRollupThreshold() {
    return rollupThreshold;
  }

  public void setRollupThreshold(Double rollupThreshold) {
    this.rollupThreshold = rollupThreshold;
  }

  public boolean isInverseMetric() {
    return inverseMetric;
  }

  public void setInverseMetric(boolean inverseMetric) {
    this.inverseMetric = inverseMetric;
  }

  public String getCellSizeExpression() {
    return cellSizeExpression;
  }

  public void setCellSizeExpression(String cellSizeExpression) {
    this.cellSizeExpression = cellSizeExpression;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }


  public String getExternalDashboardURL() {
    return externalDashboardURL;
  }

  public void setExternalDashboardURL(String externalDashboardURL) {
    this.externalDashboardURL = externalDashboardURL;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof MetricConfigBean)) {
      return false;
    }
    MetricConfigBean mc = (MetricConfigBean) o;
    return Objects.equals(getId(), mc.getId())
        && Objects.equals(name, mc.getName())
        && Objects.equals(dataset, mc.getDataset())
        && Objects.equals(alias, mc.getAlias())
        && Objects.equals(derived, mc.isDerived())
        && Objects.equals(derivedMetricExpression, mc.getDerivedMetricExpression())
        && Objects.equals(rollupThreshold, mc.getRollupThreshold())
        && Objects.equals(inverseMetric, mc.isInverseMetric())
        && Objects.equals(cellSizeExpression, mc.getCellSizeExpression())
        && Objects.equals(active, mc.isActive())
        && Objects.equals(externalDashboardURL, mc.getExternalDashboardURL());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getId(), dataset, alias, derived, derivedMetricExpression, rollupThreshold,
        inverseMetric, cellSizeExpression, active, externalDashboardURL);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("id", getId()).add("name", name).add("dataset", dataset)
        .add("alias", alias).add("derived", derived).add("derivedMetricExpression", derivedMetricExpression)
        .add("rollupThreshold", rollupThreshold).add("cellSizeExpression", cellSizeExpression)
        .add("active", active).add("externalDashboardURL", externalDashboardURL).toString();
  }
}
