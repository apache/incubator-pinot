/**
 * Copyright (C) 2014-2016 LinkedIn Corp. (pinot-core@linkedin.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.linkedin.pinot.common.config;

import java.io.IOException;
import org.json.JSONException;
import org.testng.Assert;
import org.testng.annotations.Test;



public class AbstractTableConfigTest {

  @Test
  public void testInit()
      throws IOException, JSONException {
    {
      // with quota
      String tableConfigStr =
          "{\"tableName\":\"myTable\",\"quota\":{\"storage\":\"30G\"},\"segmentsConfig\":{\"retentionTimeUnit\":\"DAYS\",\"retentionTimeValue\":\"60\",\"segmentPushFrequency\":\"daily\",\"segmentPushType\":\"APPEND\",\"replication\":\"2\",\"schemaName\":\"\",\"timeColumnName\":\"\",\"timeType\":\"\",\"segmentAssignmentStrategy\":\"BalanceNumSegmentAssignmentStrategy\"},\"tableIndexConfig\":{\"invertedIndexColumns\":[],\"loadMode\":\"HEAP\",\"lazyLoad\":\"false\"},\"tenants\":{\"broker\":\"colocated\",\"server\":\"myServer\"},\"tableType\":\"OFFLINE\",\"metadata\":{\"customConfigs\":{\"loadBalancer\":\"myLoadBalancer\"}}}";
      AbstractTableConfig tableConfig = AbstractTableConfig.init(tableConfigStr);

      Assert.assertEquals(tableConfig.getTableName(), "myTable_OFFLINE");
      Assert.assertEquals(tableConfig.getTableType().toLowerCase(), "offline");
      Assert.assertEquals(tableConfig.getIndexingConfig().getLoadMode(), "HEAP");
      Assert.assertNotNull(tableConfig.getQuotaConfig());
      Assert.assertEquals(tableConfig.getQuotaConfig().getStorage(), "30G");
      String tcFromJson = tableConfig.toJSON().toString();
      AbstractTableConfig validator = AbstractTableConfig.init(tcFromJson);
      Assert.assertEquals(validator.getQuotaConfig().getStorage(), tableConfig.getQuotaConfig().getStorage());
      Assert.assertEquals(validator.getTableName(), tableConfig.getTableName());
    }
    {
      // no quota
      String tableConfigStr =
          "{\"tableName\":\"myTable\",\"segmentsConfig\":{\"retentionTimeUnit\":\"DAYS\",\"retentionTimeValue\":\"60\",\"segmentPushFrequency\":\"daily\",\"segmentPushType\":\"APPEND\",\"replication\":\"2\",\"schemaName\":\"\",\"timeColumnName\":\"\",\"timeType\":\"\",\"segmentAssignmentStrategy\":\"BalanceNumSegmentAssignmentStrategy\"},\"tableIndexConfig\":{\"invertedIndexColumns\":[],\"loadMode\":\"HEAP\",\"lazyLoad\":\"false\"},\"tenants\":{\"broker\":\"colocated\",\"server\":\"myServer\"},\"tableType\":\"OFFLINE\",\"metadata\":{\"customConfigs\":{\"loadBalancer\":\"myLoadBalancer\"}}}";
      AbstractTableConfig tableConfig = AbstractTableConfig.init(tableConfigStr);

      Assert.assertEquals(tableConfig.getTableName(), "myTable_OFFLINE");
      Assert.assertEquals(tableConfig.getTableType().toLowerCase(), "offline");
      Assert.assertEquals(tableConfig.getIndexingConfig().getLoadMode(), "HEAP");
      Assert.assertNull(tableConfig.getQuotaConfig());
      String tcFromJson = tableConfig.toJSON().toString();
      AbstractTableConfig validator = AbstractTableConfig.init(tcFromJson);
      Assert.assertNull(validator.getQuotaConfig());
      Assert.assertEquals(validator.getTableName(), tableConfig.getTableName());

    }
  }

}
