package com.linkedin.thirdeye.tools;

import com.linkedin.thirdeye.anomaly.alert.AlertFilterHelper;
import com.linkedin.thirdeye.detector.email.filter.AlphaBetaAlertFilter;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linkedin.thirdeye.autoload.pinot.metrics.ConfigGenerator;
import com.linkedin.thirdeye.datalayer.bao.AnomalyFunctionManager;
import com.linkedin.thirdeye.datalayer.bao.DashboardConfigManager;
import com.linkedin.thirdeye.datalayer.bao.EmailConfigurationManager;
import com.linkedin.thirdeye.datalayer.bao.MergedAnomalyResultManager;
import com.linkedin.thirdeye.datalayer.bao.MetricConfigManager;
import com.linkedin.thirdeye.datalayer.bao.RawAnomalyResultManager;
import com.linkedin.thirdeye.datalayer.dto.AnomalyFunctionDTO;
import com.linkedin.thirdeye.datalayer.dto.DashboardConfigDTO;
import com.linkedin.thirdeye.datalayer.dto.EmailConfigurationDTO;
import com.linkedin.thirdeye.datalayer.dto.MergedAnomalyResultDTO;
import com.linkedin.thirdeye.datalayer.dto.MetricConfigDTO;
import com.linkedin.thirdeye.datalayer.util.DaoProviderUtil;
import com.linkedin.thirdeye.util.ThirdEyeUtils;

/**
 * Run adhoc queries to db
 */
public class RunAdhocDatabaseQueriesTool {

  private static final Logger LOG = LoggerFactory.getLogger(RunAdhocDatabaseQueriesTool.class);

  private AnomalyFunctionManager anomalyFunctionDAO;
  private EmailConfigurationManager emailConfigurationDAO;
  private RawAnomalyResultManager rawResultDAO;
  private MergedAnomalyResultManager mergedResultDAO;
  private MetricConfigManager metricConfigDAO;
  private DashboardConfigManager dashboardConfigDAO;

  public RunAdhocDatabaseQueriesTool(File persistenceFile)
      throws Exception {
    init(persistenceFile);
  }

  public void init(File persistenceFile) throws Exception {
    DaoProviderUtil.init(persistenceFile);
    anomalyFunctionDAO = DaoProviderUtil
        .getInstance(com.linkedin.thirdeye.datalayer.bao.jdbc.AnomalyFunctionManagerImpl.class);
    emailConfigurationDAO = DaoProviderUtil
        .getInstance(com.linkedin.thirdeye.datalayer.bao.jdbc.EmailConfigurationManagerImpl.class);
    rawResultDAO = DaoProviderUtil
        .getInstance(com.linkedin.thirdeye.datalayer.bao.jdbc.RawAnomalyResultManagerImpl.class);
    mergedResultDAO = DaoProviderUtil
        .getInstance(com.linkedin.thirdeye.datalayer.bao.jdbc.MergedAnomalyResultManagerImpl.class);
    metricConfigDAO = DaoProviderUtil
        .getInstance(com.linkedin.thirdeye.datalayer.bao.jdbc.MetricConfigManagerImpl.class);
    dashboardConfigDAO = DaoProviderUtil
        .getInstance(com.linkedin.thirdeye.datalayer.bao.jdbc.DashboardConfigManagerImpl.class);
  }

  private void toggleAnomalyFunction(Long id) {
    AnomalyFunctionDTO anomalyFunction = anomalyFunctionDAO.findById(id);
    anomalyFunction.setActive(true);
    anomalyFunctionDAO.update(anomalyFunction);
  }

  private void updateFields() {
    List<EmailConfigurationDTO> emailConfigs = emailConfigurationDAO.findAll();
    for (EmailConfigurationDTO emailConfig : emailConfigs) {
      LOG.info(emailConfig.getId() + " " + emailConfig.getToAddresses());
    }
  }

  private void updateField(Long id) {
    AnomalyFunctionDTO anomalyFunction = anomalyFunctionDAO.findById(id);
    //anomalyFunction.setCron("0/10 * * * * ?");
    anomalyFunction.setActive(true);
    anomalyFunctionDAO.update(anomalyFunction);
  }

  private void customFunction() {
    List<AnomalyFunctionDTO> anomalyFunctionDTOs = anomalyFunctionDAO.findAll();
    for (AnomalyFunctionDTO anomalyFunctionDTO : anomalyFunctionDTOs) {
      anomalyFunctionDTO.setActive(false);
      anomalyFunctionDAO.update(anomalyFunctionDTO);
    }
  }

  private void updateNotified() {
    List<MergedAnomalyResultDTO> mergedResults = mergedResultDAO.findAll();
    for (MergedAnomalyResultDTO mergedResult : mergedResults) {
      mergedResult.setNotified(true);
      mergedResultDAO.update(mergedResult);
    }
  }

  private void updateEmailConfigs() {
    List<EmailConfigurationDTO> emailConfigs = emailConfigurationDAO.findByCollection("login_additive");
    for (EmailConfigurationDTO emailConfig : emailConfigs) {
      emailConfig.setToAddresses("thirdeye-dev@linkedin.com,zilin@linkedin.com,ehuang@linkedin.com,login-alerts@linkedin.com");
      emailConfigurationDAO.update(emailConfig);
    }
  }

  private void createDashboard(String dataset) {

    String dashboardName = ThirdEyeUtils.getDefaultDashboardName(dataset);
    DashboardConfigDTO dashboardConfig = dashboardConfigDAO.findByName(dashboardName);
    dashboardConfig.setMetricIds(ConfigGenerator.getMetricIdsFromMetricConfigs(metricConfigDAO.findByDataset(dataset)));
    dashboardConfigDAO.update(dashboardConfig);
  }

  private void setAlertFilterForFunctionInCollection(String collection, List<String> metricList,
      Map<String, Map<String, String>> metricRuleMap, Map<String, String> defaultAlertFilter) {
    List<AnomalyFunctionDTO> anomalyFunctionDTOs =
        anomalyFunctionDAO.findAllByCollection(collection);
    for (AnomalyFunctionDTO anomalyFunctionDTO : anomalyFunctionDTOs) {
      String metricName = anomalyFunctionDTO.getMetric();
      if (metricList.contains(metricName)) {
        Map<String, String> alertFilter = defaultAlertFilter;
        if (metricRuleMap.containsKey(metricName)) {
          alertFilter = metricRuleMap.get(metricName);
        }
        anomalyFunctionDTO.setAlertFilter(alertFilter);
        anomalyFunctionDAO.update(anomalyFunctionDTO);
        LOG.info("Add alert filter {} to function {} (dataset: {}, metric: {})", alertFilter,
            anomalyFunctionDTO.getId(), collection, metricName);
      }
    }
  }

  public static void main(String[] args) throws Exception {

    File persistenceFile = new File(args[0]);
    if (!persistenceFile.exists()) {
      System.err.println("Missing file:" + persistenceFile);
      System.exit(1);
    }
    RunAdhocDatabaseQueriesTool dq = new RunAdhocDatabaseQueriesTool(persistenceFile);

  }

}
