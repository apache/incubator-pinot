/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.pinot.common.utils.helix;

import org.apache.helix.AccessOption;
import org.apache.helix.BaseDataAccessor;
import org.apache.helix.HelixDataAccessor;
import org.apache.helix.HelixManager;
import org.apache.helix.PropertyKey;
import org.apache.helix.ZNRecord;
import org.apache.helix.model.ResourceConfig;
import org.apache.pinot.common.utils.CommonConstants.Helix;
import org.apache.pinot.common.utils.HashUtil;
import org.apache.pinot.common.utils.StringUtil;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class LeadControllerUtils {
  public static final Logger LOGGER = LoggerFactory.getLogger(LeadControllerUtils.class);

  /**
   * Given a raw table name and number of partitions, returns the partition id in lead controller resource.
   * Uses murmur2 function to get hashcode for table, ignores the most significant bit.
   * Note: This method CANNOT be changed when lead controller resource is enabled.
   * Otherwise it will assign different controller for the same table, which will mess up the controller periodic tasks and realtime segment completion.
   * @param rawTableName raw table name
   * @return partition id in lead controller resource.
   */
  public static int getPartitionIdForTable(String rawTableName) {
    return (HashUtil.murmur2(StringUtil.encodeUtf8(rawTableName)) & Integer.MAX_VALUE)
        % Helix.NUMBER_OF_PARTITIONS_IN_LEAD_CONTROLLER_RESOURCE;
  }

  /**
   * Generates participant instance id, e.g. returns Controller_localhost_9000 given localhost as hostname and 9000 as port.
   */
  public static String generateParticipantInstanceId(String controllerHost, int controllerPort) {
    return Helix.PREFIX_OF_CONTROLLER_INSTANCE + controllerHost + "_" + controllerPort;
  }

  /**
   * Generates partition name, e.g. returns leadControllerResource_0 given 0 as partition index.
   */
  public static String generatePartitionName(int partitionId) {
    return Helix.LEAD_CONTROLLER_RESOURCE_NAME + "_" + partitionId;
  }

  /**
   * Extracts partition index from partition name, e.g. returns 0 given leadControllerResource_0 as partition name.
   */
  public static int extractPartitionId(String partitionName) {
    return Integer.parseInt(partitionName.substring(partitionName.lastIndexOf('_') + 1));
  }

  /**
   * Checks from ZK if resource config of leadControllerResource is enabled.
   * @param helixManager helix manager
   */
  public static boolean isLeadControllerResourceEnabled(HelixManager helixManager)
      throws Exception {
    try {
      HelixDataAccessor helixDataAccessor = helixManager.getHelixDataAccessor();
      PropertyKey propertyKey = helixDataAccessor.keyBuilder().resourceConfig(Helix.LEAD_CONTROLLER_RESOURCE_NAME);
      ResourceConfig resourceConfig = helixDataAccessor.getProperty(propertyKey);
      String resourceEnabled = resourceConfig.getSimpleConfig(Helix.LEAD_CONTROLLER_RESOURCE_ENABLED_KEY);
      return Boolean.parseBoolean(resourceEnabled);
    } catch (Exception e) {
      LOGGER.warn("Could not get whether lead controller resource is enabled or not.", e);
      throw e;
    }
  }

  /**
   * Gets Helix leader in the cluster. Null if there is no leader.
   * @param helixManager helix manager
   * @return instance id of Helix cluster leader, e.g. localhost_9000.
   */
  public static String getHelixClusterLeader(HelixManager helixManager) {
    BaseDataAccessor<ZNRecord> dataAccessor = helixManager.getHelixDataAccessor().getBaseDataAccessor();
    Stat stat = new Stat();
    try {
      ZNRecord znRecord = dataAccessor.get("/" + helixManager.getClusterName() + "/CONTROLLER/LEADER", stat,
          AccessOption.THROW_EXCEPTION_IFNOTEXIST);
      String helixLeader = znRecord.getId();
      LOGGER.info("Getting Helix leader: {} as per znode version {}, mtime {}", helixLeader, stat.getVersion(),
          stat.getMtime());
      return helixLeader;
    } catch (Exception e) {
      LOGGER.warn("Could not locate Helix leader!", e);
      return null;
    }
  }
}
