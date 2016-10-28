package com.linkedin.thirdeye.onboard.pinot.metrics;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.linkedin.pinot.common.data.MetricFieldSpec;
import com.linkedin.pinot.common.data.Schema;
import com.linkedin.thirdeye.client.DAORegistry;
import com.linkedin.thirdeye.common.ThirdEyeConfiguration;
import com.linkedin.thirdeye.dashboard.resources.CacheResource;
import com.linkedin.thirdeye.datalayer.bao.DashboardConfigManager;
import com.linkedin.thirdeye.datalayer.bao.DatasetConfigManager;
import com.linkedin.thirdeye.datalayer.bao.IngraphMetricConfigManager;
import com.linkedin.thirdeye.datalayer.bao.MetricConfigManager;
import com.linkedin.thirdeye.datalayer.dto.DashboardConfigDTO;
import com.linkedin.thirdeye.datalayer.dto.DatasetConfigDTO;
import com.linkedin.thirdeye.datalayer.dto.IngraphMetricConfigDTO;
import com.linkedin.thirdeye.datalayer.dto.MetricConfigDTO;
import com.linkedin.thirdeye.datalayer.pojo.DashboardConfigBean;

/**
 * This is a service to onboard datasets automatically to thirdeye from pinot
 * This service runs periodically and checks for new tables in pinot, to add to thirdeye
 * If the table is an ingraph table, it loads metrics from the ingraph table
 * It also looks for any changes in dimensions or metrics to the existing tables
 */
public class OnboardPinotMetricsService implements Runnable {
  private static final Logger LOG = LoggerFactory.getLogger(OnboardPinotMetricsService.class);


  private static final DAORegistry DAO_REGISTRY = DAORegistry.getInstance();

  private DatasetConfigManager datasetConfigDAO = DAO_REGISTRY.getDatasetConfigDAO();
  private MetricConfigManager metricConfigDAO = DAO_REGISTRY.getMetricConfigDAO();
  private DashboardConfigManager dashboardConfigDAO = DAO_REGISTRY.getDashboardConfigDAO();
  private IngraphMetricConfigManager ingraphMetricConfigDAO = DAO_REGISTRY.getIngraphMetricConfigDAO();


  private CacheResource cacheResource;

  private ScheduledExecutorService scheduledExecutorService;
  private OnboardPinotMetricsUtils onboardPinotMetricsUtils;

  private List<String> allDatasets = new ArrayList<>();
  private Map<String, Schema> allSchemas = new HashMap<>();

  public OnboardPinotMetricsService() {
  }

  public OnboardPinotMetricsService(ThirdEyeConfiguration config) {

    onboardPinotMetricsUtils = new OnboardPinotMetricsUtils(config);
    scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    cacheResource = new CacheResource();
  }

  public void start() {
    scheduledExecutorService.scheduleAtFixedRate(this, 0, 4, TimeUnit.HOURS);
  }

  public void shutdown() {
    scheduledExecutorService.shutdown();
  }

  public void run() {
    try {
      loadDatasets();

      for (String dataset : allDatasets) {
        LOG.info("Checking dataset {}", dataset);
        DatasetConfigDTO datasetConfig = datasetConfigDAO.findByDataset(dataset);
        Schema schema = allSchemas.get(dataset);

        if (isIngraphDataset(dataset)) {
          addIngraphDataset(dataset, schema, datasetConfig);
        } else {
          addPinotDataset(dataset, schema, datasetConfig);
        }
      }
      refreshCaches();

    } catch (IOException e) {
      LOG.error("Exception in loading datasets", e);
    }
  }

  public boolean isIngraphDataset(String dataset) {
    boolean isIngraphDataset = false;
    List<IngraphMetricConfigDTO> ingraphMetricConfigs = ingraphMetricConfigDAO.findByDataset(dataset);
    if (CollectionUtils.isNotEmpty(ingraphMetricConfigs)) {
      isIngraphDataset = true;
    }
    return isIngraphDataset;
  }

  /**
   * Adds ingraph dataset to the thirdeye database
   * @param dataset
   * @param schema
   * @param datasetConfig
   */
  public void addIngraphDataset(String dataset, Schema schema, DatasetConfigDTO datasetConfig) {
    LOG.info("Found ingraph dataset {}", dataset);
    if (datasetConfig == null) {
      DatasetConfigDTO datasetConfigDTO = ConfigGenerator.generateIngraphDatasetConfig(dataset, schema);
      LOG.info("Creating dataset for {}", dataset);
      datasetConfigDAO.save(datasetConfigDTO);
    }

    // find all ingraph metrics from the ingraph_metric_config
    List<IngraphMetricConfigDTO> ingraphMetricConfigs = ingraphMetricConfigDAO.findByDataset(dataset);
    Set<String> ingraphMetricNames = new HashSet<>();
    for (IngraphMetricConfigDTO ingraphMetricConfig : ingraphMetricConfigs) {
      ingraphMetricNames.add(ingraphMetricConfig.getMetricAlias());
    }

    // find all metric configs already in TE database
    List<MetricConfigDTO> metricConfigs = metricConfigDAO.findByDataset(dataset);
    Set<String> existingMetricNames = new HashSet<>();
    for (MetricConfigDTO metricConfig : metricConfigs) {
      existingMetricNames.add(metricConfig.getName());
    }

    // add ingraph metrics which are not yet in TE database
    for (IngraphMetricConfigDTO ingraphMetricConfig : ingraphMetricConfigs) {
      String metricAlias = ingraphMetricConfig.getMetricAlias();
      if (!existingMetricNames.contains(metricAlias)) {
        MetricConfigDTO metricConfigDTO = ConfigGenerator.generateIngraphMetricConfig(ingraphMetricConfig);
        LOG.info("Creating metric {} for {}", metricAlias, dataset);
        metricConfigDAO.save(metricConfigDTO);
      }
    }

    // update default dashboard
    List<Long> metricIds = ConfigGenerator.getMetricIdsFromMetricConfigs(metricConfigDAO.findByDataset(dataset));
    String dashboardName = DashboardConfigBean.DEFAULT_DASHBOARD_PREFIX + dataset;
    DashboardConfigDTO dashboardConfig = dashboardConfigDAO.findByName(dashboardName);
    if (dashboardConfig == null) {
      DashboardConfigDTO dashboardConfigDTO = ConfigGenerator.generateDefaultDashboardConfig(dataset, metricIds);
      LOG.info("Creating default dashboard for dataset {}", dataset);
      dashboardConfigDAO.save(dashboardConfigDTO);
    } else {
      dashboardConfig.setMetricIds(metricIds);
      dashboardConfigDAO.update(dashboardConfig);
    }
  }

  /**
   * Adds a non ingraph dataset to the thirdeye database
   * @param dataset
   * @param schema
   * @param datasetConfig
   */
  public void addPinotDataset(String dataset, Schema schema, DatasetConfigDTO datasetConfig) {
    if (datasetConfig == null) {
      LOG.info("Dataset {} is new, adding it to thirdeye", dataset);
      addNewDataset(dataset, schema);
    } else {
      LOG.info("Dataset {} already exists, checking for updates", dataset);
      refreshOldDataset(dataset, datasetConfig, schema);
    }
  }

  /**
   * Adds a new dataset to the thirdeye database
   * @param dataset
   * @param schema
   */
  private void addNewDataset(String dataset, Schema schema) {
    List<MetricFieldSpec> metricSpecs = schema.getMetricFieldSpecs();

    // Create DatasetConfig
    DatasetConfigDTO datasetConfigDTO = ConfigGenerator.generateDatasetConfig(dataset, schema);
    LOG.info("Creating dataset for {}", dataset);
    datasetConfigDAO.save(datasetConfigDTO);

    // Create MetricConfig
    for (MetricFieldSpec metricFieldSpec : metricSpecs) {
      MetricConfigDTO metricConfigDTO = ConfigGenerator.generateMetricConfig(metricFieldSpec, dataset);
      LOG.info("Creating metric {} for {}", metricConfigDTO.getName(), dataset);
      metricConfigDAO.save(metricConfigDTO);
    }

    // Create Default DashboardConfig
    List<Long> metricIds = ConfigGenerator.getMetricIdsFromMetricConfigs(metricConfigDAO.findByDataset(dataset));
    DashboardConfigDTO dashboardConfigDTO = ConfigGenerator.generateDefaultDashboardConfig(dataset, metricIds);
    LOG.info("Creating default dashboard for dataset {}", dataset);
    dashboardConfigDAO.save(dashboardConfigDTO);
  }

  /**
   * Refreshes an existing dataset in the thirdeye database
   * with any dimension/metric changes from pinot schema
   * @param dataset
   * @param datasetConfig
   * @param schema
   */
  private void refreshOldDataset(String dataset, DatasetConfigDTO datasetConfig, Schema schema) {

    if (datasetConfig.isMetricAsDimension()) {
      LOG.info("Skipping refresh for metricAsDimension dataset {}", dataset);
    } else {
      checkDimensionChanges(dataset, datasetConfig, schema);
      checkMetricChanges(dataset, datasetConfig, schema);
    }
  }

  private void checkDimensionChanges(String dataset, DatasetConfigDTO datasetConfig, Schema schema) {

    LOG.info("Checking for dimensions changes in {}", dataset);
    List<String> schemaDimensions = schema.getDimensionNames();
    List<String> datasetDimensions = datasetConfig.getDimensions();
    List<String> dimensionsToAdd = new ArrayList<>();
    List<String> dimensionsToRemove = new ArrayList<>();

    // dimensions which are new in the pinot schema
    for (String dimensionName : schemaDimensions) {
      if (!datasetDimensions.contains(dimensionName)) {
        dimensionsToAdd.add(dimensionName);
      }
    }
    // dimensions which are removed form pinot schema
    for (String dimensionName : datasetDimensions) {
      if (!schemaDimensions.contains(dimensionName)) {
        dimensionsToRemove.add(dimensionName);
      }
    }
    if (CollectionUtils.isNotEmpty(dimensionsToAdd) || CollectionUtils.isNotEmpty(dimensionsToRemove)) {
      datasetDimensions.addAll(dimensionsToAdd);
      datasetDimensions.removeAll(dimensionsToRemove);
      datasetConfig.setDimensions(datasetDimensions);

      if (!datasetConfig.isAdditive()
          && CollectionUtils.isNotEmpty(datasetConfig.getDimensionsHaveNoPreAggregation())) {
        List<String> dimensionsHaveNoPreAggregation = datasetConfig.getDimensionsHaveNoPreAggregation();
        dimensionsHaveNoPreAggregation.removeAll(dimensionsToRemove);
        datasetConfig.setDimensionsHaveNoPreAggregation(dimensionsHaveNoPreAggregation);
      }
      LOG.info("Added dimensions {}, removed {}", dimensionsToAdd, dimensionsToRemove);
      datasetConfigDAO.update(datasetConfig);
    }
  }

  private void checkMetricChanges(String dataset, DatasetConfigDTO datasetConfig, Schema schema) {

    LOG.info("Checking for metric changes in {}", dataset);
    List<MetricFieldSpec> schemaMetricSpecs = schema.getMetricFieldSpecs();
    List<MetricConfigDTO> datasetMetricConfigs = metricConfigDAO.findByDataset(dataset);
    List<String> datasetMetricNames = new ArrayList<>();
    for (MetricConfigDTO metricConfig : datasetMetricConfigs) {
      datasetMetricNames.add(metricConfig.getName());
    }
    List<Long> metricsToAdd = new ArrayList<>();

    for (MetricFieldSpec metricSpec : schemaMetricSpecs) {
      // metrics which are new in pinot schema, create them
      String metricName = metricSpec.getName();
      if (!datasetMetricNames.contains(metricName)) {
        MetricConfigDTO metricConfigDTO = ConfigGenerator.generateMetricConfig(metricSpec, dataset);
        LOG.info("Creating metric {} for {}", metricName, dataset);
        metricsToAdd.add(metricConfigDAO.save(metricConfigDTO));
      }
    }

    // add new metricIds to default dashboard
    if (CollectionUtils.isNotEmpty(metricsToAdd)) {
      LOG.info("Metrics to add {}", metricsToAdd);
      String dashboardName = DashboardConfigBean.DEFAULT_DASHBOARD_PREFIX + dataset;
      DashboardConfigDTO dashboardConfig = dashboardConfigDAO.findByName(dashboardName);
      List<Long> metricIds = dashboardConfig.getMetricIds();
      metricIds.addAll(metricsToAdd);
      dashboardConfigDAO.update(dashboardConfig);
    }

    // TODO: write a tool, which given a metric id, erases all traces of that metric from the database
    // This will include:
    // 1) delete the metric from metricConfigs
    // 2) remove any derived metrics which use the deleted metric
    // 3) remove the metric, and derived metrics from all dashboards
    // 4) remove any anomaly functions associated with the metric
    // 5) remove any alerts associated with these anomaly functions

  }

  /**
   * Refreshes thirdeye caches, for any changes we might have made in the refresh/load
   */
  private void refreshCaches() {
    LOG.info("Refresh all caches");
    cacheResource.refreshAllCaches();
  }

  /**
   * Reads all table names in pinot, and loads their schema
   * @throws IOException
   */
  private void loadDatasets() throws IOException {

    JsonNode tables = onboardPinotMetricsUtils.getAllTablesFromPinot();
    for (JsonNode table : tables) {
      String dataset = table.asText();
      Schema schema = onboardPinotMetricsUtils.getSchemaFromPinot(dataset);
      if (schema != null) {
        allDatasets.add(dataset);
        allSchemas.put(dataset, schema);
      }
    }
  }

}
