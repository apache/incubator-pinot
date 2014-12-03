package com.linkedin.pinot.broker.broker.helix;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.helix.HelixAdmin;
import org.apache.helix.HelixDataAccessor;
import org.apache.helix.HelixException;
import org.apache.helix.HelixManager;
import org.apache.helix.HelixManagerFactory;
import org.apache.helix.InstanceType;
import org.apache.helix.PropertyKey.Builder;
import org.apache.helix.ZNRecord;
import org.apache.helix.manager.zk.ZKHelixDataAccessor;
import org.apache.helix.manager.zk.ZKUtil;
import org.apache.helix.manager.zk.ZNRecordSerializer;
import org.apache.helix.manager.zk.ZkBaseDataAccessor;
import org.apache.helix.manager.zk.ZkClient;
import org.apache.helix.model.InstanceConfig;
import org.apache.helix.participant.StateMachineEngine;
import org.apache.helix.participant.statemachine.StateModelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linkedin.pinot.broker.broker.BrokerServerBuilder;
import com.linkedin.pinot.common.utils.CommonConstants;
import com.linkedin.pinot.common.utils.NetUtil;
import com.linkedin.pinot.routing.HelixExternalViewBasedRouting;
import com.linkedin.pinot.routing.builder.RoutingTableBuilder;
import com.linkedin.pinot.routing.builder.RoutingTableBuilderFactory;


/**
 * Helix Broker Startable
 *
 * @author xiafu
 *
 */
public class HelixBrokerStarter {

  private final HelixManager _helixManager;
  private final HelixAdmin _helixAdmin;
  private final Configuration _pinotHelixProperties;
  private final HelixBrokerRoutingTable _helixBrokerRoutingTable;
  // private final BrokerServerBuilder _brokerServerBuilder; 
  private final HelixExternalViewBasedRouting _helixExternalViewBasedRouting;
  private final BrokerServerBuilder _brokerServerBuilder;

  private static final Logger LOGGER = LoggerFactory.getLogger("HelixBrokerStarter");

  private final String DEFAULT_ROUTING_TABLE_BUILDER_KEY = "pinot.broker.routing.table.builder.default";
  private final String ROUTING_TABLE_BUILDER_KEY = "pinot.broker.routing.table.builder";

  public HelixBrokerStarter(String helixClusterName, String zkServer, Configuration pinotHelixProperties)
      throws Exception {
    _pinotHelixProperties = DefaultHelixBrokerConfig.getDefaultBrokerConf(pinotHelixProperties);
    final String brokerId =
        _pinotHelixProperties.getString(
            "instanceId",
            CommonConstants.Helix.PREFIX_OF_BROKER_INSTANCE
                + NetUtil.getHostAddress()
                + "_"
                + _pinotHelixProperties.getInt(CommonConstants.Helix.KEY_OF_BROKER_QUERY_PORT,
                    CommonConstants.Helix.DEFAULT_BROKER_QUERY_PORT));

    _pinotHelixProperties.addProperty("pinot.broker.id", brokerId);
    RoutingTableBuilder defaultRoutingTableBuilder =
        getRoutingTableBuilder(_pinotHelixProperties.subset(DEFAULT_ROUTING_TABLE_BUILDER_KEY));
    Map<String, RoutingTableBuilder> resourceToRoutingTableBuilderMap =
        getResourceToRoutingTableBuilderMap(_pinotHelixProperties.subset(ROUTING_TABLE_BUILDER_KEY));

    _helixExternalViewBasedRouting =
        new HelixExternalViewBasedRouting(defaultRoutingTableBuilder, resourceToRoutingTableBuilderMap);
    _helixBrokerRoutingTable = new HelixBrokerRoutingTable(_helixExternalViewBasedRouting, brokerId);
    // _brokerServerBuilder = startBroker();
    _brokerServerBuilder = startBroker(_pinotHelixProperties);
    _helixManager =
        HelixManagerFactory.getZKHelixManager(helixClusterName, brokerId, InstanceType.PARTICIPANT, zkServer);
    final StateMachineEngine stateMachineEngine = _helixManager.getStateMachineEngine();
    final StateModelFactory<?> stateModelFactory =
        new BrokerResourceOnlineOfflineStateModelFactory(_helixManager, _helixExternalViewBasedRouting);
    stateMachineEngine.registerStateModelFactory(BrokerResourceOnlineOfflineStateModelFactory.getStateModelDef(),
        stateModelFactory);
    _helixManager.connect();
    _helixAdmin = _helixManager.getClusterManagmentTool();

    addInstanceTagIfNeeded(helixClusterName, brokerId);
    _helixManager.addExternalViewChangeListener(_helixBrokerRoutingTable);

  }

  private void addInstanceTagIfNeeded(String clusterName, String instanceName) {
    InstanceConfig instanceConfig = _helixAdmin.getInstanceConfig(clusterName, instanceName);
    List<String> instanceTags = instanceConfig.getTags();
    if (instanceTags == null || instanceTags.size() == 0) {
      _helixAdmin.addInstanceTag(clusterName, instanceName, CommonConstants.Helix.UNTAGGED_BROKER_INSTANCE);
    }
  }

  private Map<String, RoutingTableBuilder> getResourceToRoutingTableBuilderMap(Configuration routingTableBuilderConfig) {
    String[] resources = routingTableBuilderConfig.getStringArray("resources");
    if ((resources != null) && (resources.length > 0)) {
      Map<String, RoutingTableBuilder> routingTableBuilderMap = new HashMap<String, RoutingTableBuilder>();
      for (String resource : resources) {
        RoutingTableBuilder routingTableBuilder = getRoutingTableBuilder(routingTableBuilderConfig.subset(resource));
        if (routingTableBuilder == null) {
          LOGGER.error("RoutingTableBuilder is null for resource : " + resource);
        } else {
          routingTableBuilderMap.put(resource, routingTableBuilder);
        }
      }
      return routingTableBuilderMap;
    } else {
      return null;
    }
  }

  private RoutingTableBuilder getRoutingTableBuilder(Configuration routingTableBuilderConfig) {
    String routingTableBuilderKey = routingTableBuilderConfig.getString("class", null);
    RoutingTableBuilder routingTableBuilder = RoutingTableBuilderFactory.get(routingTableBuilderKey);
    if (routingTableBuilder == null) {
      return null;
    }
    routingTableBuilder.init(routingTableBuilderConfig);
    return routingTableBuilder;
  }

  private BrokerServerBuilder startBroker(Configuration config) throws Exception {
    if (config == null) {
      config = DefaultHelixBrokerConfig.getDefaultBrokerConf();
    }
    final BrokerServerBuilder brokerServerBuilder = new BrokerServerBuilder(config, _helixExternalViewBasedRouting);
    brokerServerBuilder.buildNetwork();
    brokerServerBuilder.buildHTTP();
    brokerServerBuilder.start();

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        try {
          brokerServerBuilder.stop();
        } catch (final Exception e) {
          LOGGER.error(e.getMessage());
        }
      }
    });
    return brokerServerBuilder;
  }

  public HelixExternalViewBasedRouting getHelixExternalViewBasedRouting() {
    return _helixExternalViewBasedRouting;
  }

  public BrokerServerBuilder getBrokerServerBuilder() {
    return _brokerServerBuilder;
  }

  public static void main(String[] args) throws Exception {
    Configuration configuration = new PropertiesConfiguration();
    int port = 5001;
    configuration.addProperty(CommonConstants.Helix.KEY_OF_BROKER_QUERY_PORT, port);

    final HelixBrokerStarter pinotHelixBrokerStarter =
        new HelixBrokerStarter("sprintDemoCluster", "localhost:2181", configuration);
    Thread.sleep(1000);
  }
}
