package com.linkedin.thirdeye.db.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import org.hibernate.validator.constraints.Email;

import com.google.common.base.MoreObjects;
import com.google.common.collect.Multimap;
import com.linkedin.thirdeye.util.ThirdEyeUtils;

@Entity
@Table(name = "email_configurations")
@NamedQueries({
    @NamedQuery(name = "com.linkedin.thirdeye.api.EmailConfiguration#findAll", query = "SELECT c FROM EmailConfiguration c"),
    @NamedQuery(name = "com.linkedin.thirdeye.api.EmailConfiguration#toggleActive", query = "UPDATE EmailConfiguration set isActive = :isActive WHERE id = :id")
})
public class EmailConfiguration extends AbstractBaseEntity {

  @Valid
  @NotNull
  @Column(name = "collection", nullable = false)
  private String collection;

  @Valid
  @NotNull
  @Column(name = "metric", nullable = false)
  private String metric;

  @Valid
  @NotNull
  @Email
  @Column(name = "from_address", nullable = false)
  private String fromAddress;

  @Valid
  @NotNull
  @Column(name = "to_addresses", nullable = false)
  private String toAddresses;

  @Valid
  @NotNull
  @Column(name = "cron", nullable = false)
  private String cron;

  @Valid
  @NotNull
  @Column(name = "smtp_host", nullable = false)
  private String smtpHost;

  @Valid
  @Column(name = "smtp_port", nullable = false)
  private int smtpPort = 25;

  @Column(name = "smtp_user", nullable = true)
  private String smtpUser;

  @Column(name = "smtp_password", nullable = true)
  private String smtpPassword;

  @Valid
  @NotNull
  @Column(name = "window_size", nullable = false)
  private int windowSize = 7;

  @Valid
  @NotNull
  @Column(name = "window_unit", nullable = false)
  private TimeUnit windowUnit = TimeUnit.DAYS;

  @Column(name = "is_active", nullable = false)
  private boolean isActive;

  @Column(name = "send_zero_anomaly_email", nullable = false)
  private boolean sendZeroAnomalyEmail;

  @Column(name = "filters", nullable = true)
  private String filters;

  @Column(name = "window_delay", nullable = false)
  private Integer windowDelay;

  @Column(name = "window_delay_unit", nullable = false)
  private TimeUnit windowDelayUnit;

  @ManyToMany(cascade = {
      CascadeType.ALL
  }, fetch = FetchType.LAZY)
  @JoinTable(name = "email_function_dependencies", joinColumns = {
      @JoinColumn(name = "email_id", referencedColumnName = "id")
  }, inverseJoinColumns = {
      @JoinColumn(name = "function_id", referencedColumnName = "id")
  })
  private List<AnomalyFunctionSpec> functions = new ArrayList<>();

  public String getCollection() {
    return collection;
  }

  public void setCollection(String collection) {
    this.collection = collection;
  }

  public String getMetric() {
    return metric;
  }

  public void setMetric(String metric) {
    this.metric = metric;
  }

  public String getFromAddress() {
    return fromAddress;
  }

  public void setFromAddress(String fromAddress) {
    this.fromAddress = fromAddress;
  }

  public String getToAddresses() {
    return toAddresses;
  }

  public void setToAddresses(String toAddresses) {
    this.toAddresses = toAddresses;
  }

  public String getCron() {
    return cron;
  }

  public void setCron(String cron) {
    this.cron = cron;
  }

  public String getSmtpHost() {
    return smtpHost;
  }

  public void setSmtpHost(String smtpHost) {
    this.smtpHost = smtpHost;
  }

  public int getSmtpPort() {
    return smtpPort;
  }

  public void setSmtpPort(int smtpPort) {
    this.smtpPort = smtpPort;
  }

  public String getSmtpUser() {
    return smtpUser;
  }

  public void setSmtpUser(String smtpUser) {
    this.smtpUser = smtpUser;
  }

  public String getSmtpPassword() {
    return smtpPassword;
  }

  public void setSmtpPassword(String smtpPassword) {
    this.smtpPassword = smtpPassword;
  }

  public int getWindowSize() {
    return windowSize;
  }

  public void setWindowSize(int windowSize) {
    this.windowSize = windowSize;
  }

  public TimeUnit getWindowUnit() {
    return windowUnit;
  }

  public void setWindowUnit(TimeUnit windowUnit) {
    this.windowUnit = windowUnit;
  }

  public boolean getIsActive() {
    return isActive;
  }

  public void setIsActive(boolean isActive) {
    this.isActive = isActive;
  }

  public boolean getSendZeroAnomalyEmail() {
    return sendZeroAnomalyEmail;
  }

  public void setSendZeroAnomalyEmail(boolean sendZeroAnomalyEmail) {
    this.sendZeroAnomalyEmail = sendZeroAnomalyEmail;
  }

  public String getFilters() {
    return filters;
  }

  public Multimap<String, String> getFilterSet() {
    return ThirdEyeUtils.getFilterSet(filters);
  }

  public void setFilters(String filters) {
    String sortedFilters = ThirdEyeUtils.getSortedFilters(filters);
    this.filters = sortedFilters;
  }

  public Integer getWindowDelay() {
    return windowDelay;
  }

  public void setWindowDelay(Integer windowDelay) {
    this.windowDelay = windowDelay;
  }

  public TimeUnit getWindowDelayUnit() {
    return windowDelayUnit;
  }

  public void setWindowDelayUnit(TimeUnit windowDelayUnit) {
    this.windowDelayUnit = windowDelayUnit;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("collection", collection).add("metric", metric)
        .add("fromAddress", fromAddress).add("toAddresses", toAddresses).add("cron", cron)
        .add("smtpHost", smtpHost).add("smtpPort", smtpPort).add("smtpUser", smtpUser)
        .add("windowSize", windowSize).add("windowUnit", windowUnit).add("isActive", isActive)
        .add("sendZeroAnomalyEmail", sendZeroAnomalyEmail).add("filters", filters)
        .add("windowDelay", windowDelay).add("windowDelayUnit", windowDelayUnit)
        .add("functions", functions).toString();
  }

  public List<AnomalyFunctionSpec> getFunctions() {
    return functions;
  }

  public void setFunctions(List<AnomalyFunctionSpec> functions) {
    this.functions = functions;
  }
}
