package com.linkedin.thirdeye.dashboard.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkedin.thirdeye.client.DAORegistry;
import com.linkedin.thirdeye.datalayer.bao.AnomalyFunctionManager;
import com.linkedin.thirdeye.datalayer.bao.DashboardConfigManager;
import com.linkedin.thirdeye.datalayer.bao.DatasetConfigManager;
import com.linkedin.thirdeye.datalayer.bao.EmailConfigurationManager;
import com.linkedin.thirdeye.datalayer.bao.MetricConfigManager;
import com.linkedin.thirdeye.datalayer.dto.AbstractDTO;
import com.linkedin.thirdeye.datalayer.dto.AnomalyFunctionDTO;
import com.linkedin.thirdeye.datalayer.dto.DashboardConfigDTO;
import com.linkedin.thirdeye.datalayer.dto.DatasetConfigDTO;
import com.linkedin.thirdeye.datalayer.dto.EmailConfigurationDTO;

import com.linkedin.thirdeye.datalayer.dto.MetricConfigDTO;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("thirdeye/entity")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EntityManagerResource {
  private final AnomalyFunctionManager anomalyFunctionManager;
  private final EmailConfigurationManager emailConfigurationManager;
  private final DashboardConfigManager dashboardConfigManager;
  private final MetricConfigManager metricConfigManager;
  private final DatasetConfigManager datasetConfigManager;

  private static final DAORegistry DAO_REGISTRY = DAORegistry.getInstance();

  public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final Logger LOG = LoggerFactory.getLogger(EntityManagerResource.class);

  public EntityManagerResource() {
    this.emailConfigurationManager = DAO_REGISTRY.getEmailConfigurationDAO();
    this.anomalyFunctionManager = DAO_REGISTRY.getAnomalyFunctionDAO();
    this.dashboardConfigManager = DAO_REGISTRY.getDashboardConfigDAO();
    this.metricConfigManager = DAO_REGISTRY.getMetricConfigDAO();
    this.datasetConfigManager = DAO_REGISTRY.getDatasetConfigDAO();
  }

  private enum EntityType {
    ANOMALY_FUNCTION, EMAIL_CONFIGURATION, DASHBOARD_CONFIG, DATASET_CONFIG, METRIC_CONFIG
  }

  @GET
  public List<EntityType> getAllEntityTypes() {
    return Arrays.asList(EntityType.values());
  }

  @GET
  @Path("{entityType}")
  public Response getAllEntitiesForType(@PathParam("entityType") String entityTypeStr) {
    EntityType entityType = EntityType.valueOf(entityTypeStr);
    List<AbstractDTO> results = new ArrayList<>();
    switch (entityType) {
    case ANOMALY_FUNCTION:
      results.addAll(anomalyFunctionManager.findAllActiveFunctions());
      break;
    case EMAIL_CONFIGURATION:
      results.addAll(emailConfigurationManager.findAll());
      break;
    case DASHBOARD_CONFIG:
      results.addAll(dashboardConfigManager.findAll());
      break;
    case DATASET_CONFIG:
      results.addAll(datasetConfigManager.findAll());
      break;
    case METRIC_CONFIG:
      results.addAll(metricConfigManager.findAll());
      break;
    default:
      throw new WebApplicationException("Unknown entity type : " + entityType);
    }
    return Response.ok(results).build();
  }

  @POST
  public Response updateEntity(@QueryParam("entityType") String entityTypeStr, String jsonPayload) {
    EntityType entityType = EntityType.valueOf(entityTypeStr);
    try {
      switch (entityType) {
      case ANOMALY_FUNCTION:
        AnomalyFunctionDTO anomalyFunctionDTO =
            OBJECT_MAPPER.readValue(jsonPayload, AnomalyFunctionDTO.class);
        anomalyFunctionManager.update(anomalyFunctionDTO);
        break;
      case EMAIL_CONFIGURATION:
        EmailConfigurationDTO emailConfigurationDTO =
            OBJECT_MAPPER.readValue(jsonPayload, EmailConfigurationDTO.class);
        emailConfigurationManager.update(emailConfigurationDTO);
        break;
      case DASHBOARD_CONFIG:
        DashboardConfigDTO dashboardConfigDTO = OBJECT_MAPPER.readValue(jsonPayload, DashboardConfigDTO.class);
        dashboardConfigManager.update(dashboardConfigDTO);
        break;
      case DATASET_CONFIG:
        DatasetConfigDTO datasetConfigDTO = OBJECT_MAPPER.readValue(jsonPayload, DatasetConfigDTO.class);
        datasetConfigManager.update(datasetConfigDTO);
        break;
      case METRIC_CONFIG:
        MetricConfigDTO metricConfigDTO = OBJECT_MAPPER.readValue(jsonPayload, MetricConfigDTO.class);
        metricConfigManager.update(metricConfigDTO);
        break;
      }
    } catch (IOException e) {
      LOG.error("Error saving the entity with payload : " + jsonPayload, e);
      throw new WebApplicationException(e);
    }
    return Response.ok().build();
  }
}

