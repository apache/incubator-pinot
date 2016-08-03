package com.linkedin.thirdeye.common.persistence;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.persist.PersistService;
import com.google.inject.persist.jpa.JpaPersistModule;

import io.dropwizard.configuration.ConfigurationFactory;
import io.dropwizard.jackson.Jackson;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.validation.Validation;

import org.apache.commons.lang3.StringUtils;

public abstract class PersistenceUtil {

  public static final String JPA_UNIT = "te";
  private static Injector injector;

  private PersistenceUtil() {
  }

  // Used for unit testing, provides injector
  public static void init(File localConfigFile) {
    PersistenceConfig configuration = createConfiguration(localConfigFile);
    Properties properties = createDbPropertiesFromConfiguration(configuration);
    JpaPersistModule jpaPersistModule = new JpaPersistModule(JPA_UNIT);
    jpaPersistModule.properties(properties);
    injector = Guice.createInjector(jpaPersistModule, new PersistenceModule());
    injector.getInstance(PersistService.class).start();
  }

  public static PersistenceConfig createConfiguration(File configFile) {
    ConfigurationFactory<PersistenceConfig> factory =
        new ConfigurationFactory<>(PersistenceConfig.class,
            Validation.buildDefaultValidatorFactory().getValidator(), Jackson.newObjectMapper(),
            "");
    PersistenceConfig configuration;
    try {
      configuration = factory.build(configFile);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return configuration;
  }

  public static Properties createDbPropertiesFromConfiguration(PersistenceConfig localConfiguration) {
    PersistenceConfig.DatabaseConfiguration databaseConfiguration =
        localConfiguration.getDatabaseConfiguration();
    List<String> propertiesList = new ArrayList<>();
    propertiesList.add("hibernate.dialect");
    propertiesList.add("hibernate.show_sql");
    propertiesList.add("hibernate.hbm2ddl.auto");
    propertiesList.add("hibernate.archive.autodetection");
    propertiesList.add("hibernate.connection.driver_class");

    Properties properties = new Properties();
    properties.setProperty("javax.persistence.jdbc.url", databaseConfiguration.getUrl());
    properties.setProperty("javax.persistence.jdbc.user", databaseConfiguration.getUser());
    properties.setProperty("javax.persistence.jdbc.password", databaseConfiguration.getPassword());
    for (String p : propertiesList) {
      String val = databaseConfiguration.getProperties().get(p);
      if (val != null) {
        properties.setProperty(p, val);
      }
    }
    Map<String, String> defaultDbcpProperties = new HashMap<>();
    defaultDbcpProperties.put("hibernate.dbcp.initialSize", "8");
    defaultDbcpProperties.put("hibernate.dbcp.maxTotal", "50");
    defaultDbcpProperties.put("hibernate.dbcp.maxIdle", "50");
    defaultDbcpProperties.put("hibernate.dbcp.minIdle", "0");
    for (String key : defaultDbcpProperties.keySet()) {
      String val = databaseConfiguration.getProperties().get(key);
      if (StringUtils.isBlank(val)) {
        val = defaultDbcpProperties.get(key);
      }
      properties.setProperty(key, val);
    }
    return properties;
  }

  public static Injector getInjector() {
    if (injector == null) {
      throw new RuntimeException("call init() first!");
    }
    return injector;
  }

  public static <T> T getInstance(Class<T> c) {
    return getInjector().getInstance(c);
  }
}
