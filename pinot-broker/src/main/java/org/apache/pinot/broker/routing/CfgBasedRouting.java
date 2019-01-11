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
package org.apache.pinot.broker.routing;

import java.util.List;
import java.util.Map;
import org.apache.pinot.transport.config.PerTableRoutingConfig;
import org.apache.pinot.transport.config.RoutingTableConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class CfgBasedRouting implements RoutingTable {
  private static final Logger LOGGER = LoggerFactory.getLogger(CfgBasedRouting.class);

  private RoutingTableConfig _cfg;

  public CfgBasedRouting() {
  }

  public void init(RoutingTableConfig cfg) {
    _cfg = cfg;
  }

  @Override
  public Map<String, List<String>> getRoutingTable(RoutingTableLookupRequest request) {
    String tableName = request.getTableName();
    PerTableRoutingConfig cfg = _cfg.getPerTableRoutingCfg().get(tableName);

    if (cfg == null) {
      LOGGER.warn("Unable to find routing setting for table: {}", tableName);
      return null;
    }

    return cfg.buildRequestRoutingMap();
  }

  @Override
  public boolean routingTableExists(String tableName) {
    return _cfg.getPerTableRoutingCfg().containsKey(tableName);
  }

  @Override
  public String dumpSnapshot(String tableName) throws Exception {
    return null;
  }
}
