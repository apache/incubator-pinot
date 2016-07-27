package com.linkedin.thirdeye.db.entity;
import java.util.Objects;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.Table;

import org.apache.commons.lang.ObjectUtils;
import org.joda.time.DateTime;

import com.google.common.base.MoreObjects;

@Entity
@Table(name = "anomaly_results")
public class AnomalyResult extends AbstractBaseEntity implements Comparable<AnomalyResult> {

  @Column(name = "start_time_utc", nullable = false)
  private Long startTimeUtc;

  @Column(name = "end_time_utc", nullable = true)
  private Long endTimeUtc;

  @Column(name = "dimensions", nullable = false)
  private String dimensions;

  @Column(name = "score", nullable = false)
  private double score;

  @Column(name = "weight", nullable = false)
  private double weight;

  @Column(name = "properties", nullable = true)
  private String properties;

  @Column(name = "message", nullable = true)
  private String message;

  @Column(name = "creation_time_utc", nullable = false)
  private Long creationTimeUtc;

  @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
  @JoinColumn(name="anomaly_feedback_id")
  private AnomalyFeedback feedback;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "function_id")
  private AnomalyFunctionSpec function;

  public AnomalyResult() {
    creationTimeUtc = DateTime.now().getMillis();
  }

  public AnomalyFunctionSpec getFunction() {
    return function;
  }

  public void setFunction(AnomalyFunctionSpec function) {
    this.function = function;
  }

  public String getDimensions() {
    return dimensions;
  }

  public void setDimensions(String dimensions) {
    this.dimensions = dimensions;
  }

  // --- TODO: remove methods above this comment ---
  public Long getFunctionId() {
    return function.getId();
  }

  public String getMetric() {
    return function.getMetric();
  }

  public String getCollection() {
    return function.getCollection();
  }

  public String getFilters() {
    return function.getFilters();
  }

  // --- remove methods above this comment ---

  public Long getStartTimeUtc() {
    return startTimeUtc;
  }

  public void setStartTimeUtc(Long startTimeUtc) {
    this.startTimeUtc = startTimeUtc;
  }

  public Long getEndTimeUtc() {
    return endTimeUtc;
  }

  public void setEndTimeUtc(Long endTimeUtc) {
    this.endTimeUtc = endTimeUtc;
  }

  public double getScore() {
    return score;
  }

  public void setScore(double score) {
    this.score = score;
  }

  public double getWeight() {
    return weight;
  }

  public void setWeight(double weight) {
    this.weight = weight;
  }

  public String getProperties() {
    return properties;
  }

  public void setProperties(String properties) {
    this.properties = properties;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public Long getCreationTimeUtc() {
    return creationTimeUtc;
  }

  public void setCreationTimeUtc(Long creationTimeUtc) {
    this.creationTimeUtc = creationTimeUtc;
  }

  public AnomalyFeedback getFeedback() {
    return feedback;
  }

  public void setFeedback(AnomalyFeedback feedback) {
    this.feedback = feedback;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("id", getId()).add("function", getFunction())
        .add("startTimeUtc", startTimeUtc).add("dimensions", dimensions)
        .add("endTimeUtc", endTimeUtc).add("score", score).add("weight", weight)
        .add("properties", properties).add("message", message)
        .add("creationTimeUtc", creationTimeUtc).add("feedback", feedback).toString();
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof AnomalyResult)) {
      return false;
    }
    AnomalyResult r = (AnomalyResult) o;
    return Objects.equals(function, r.getFunction())
        && Objects.equals(startTimeUtc, r.getStartTimeUtc())
        && Objects.equals(dimensions, r.getDimensions())
        && Objects.equals(endTimeUtc, r.getEndTimeUtc())
        && Objects.equals(score, r.getScore()) && Objects.equals(weight, r.getWeight())
        && Objects.equals(properties, r.getProperties()) && Objects.equals(message, r.getMessage());
    // Intentionally omit creationTimeUtc, since start/end are the truly significant dates for
    // anomalies
  }

  @Override
  public int hashCode() {
    return Objects.hash(getFunction(), dimensions, startTimeUtc,
        endTimeUtc, score, weight, properties, message);
    // Intentionally omit creationTimeUtc, since start/end are the truly significant dates for
    // anomalies
  }

  @Override
  public int compareTo(AnomalyResult o) {
    // compare by dimension, -startTime, functionId, id
    int diff = ObjectUtils.compare(getDimensions(), o.getDimensions());
    if (diff != 0) {
      return diff;
    }
    diff = -ObjectUtils.compare(startTimeUtc, o.getStartTimeUtc()); // inverted to sort by
    // decreasing time
    if (diff != 0) {
      return diff;
    }
    diff = ObjectUtils.compare(getFunctionId(), o.getFunctionId());
    if (diff != 0) {
      return diff;
    }
    return ObjectUtils.compare(getId(), o.getId());
  }
}
