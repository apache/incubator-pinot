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
package org.apache.pinot.common.config;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.apache.pinot.common.data.StarTreeIndexSpec;
import org.apache.pinot.common.utils.JsonUtils;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;


public class IndexingConfigTest {

  @Test
  public void testSerDe()
      throws IOException {
    IndexingConfig indexingConfig = new IndexingConfig();
    indexingConfig.setLoadMode("MMAP");
    indexingConfig.setAggregateMetrics(true);
    List<String> invertedIndexColumns = Arrays.asList("a", "b", "c");
    indexingConfig.setInvertedIndexColumns(invertedIndexColumns);
    List<String> sortedColumn = Arrays.asList("d", "e", "f");
    indexingConfig.setSortedColumn(sortedColumn);
    List<String> onHeapDictionaryColumns = Arrays.asList("x", "y", "z");
    indexingConfig.setOnHeapDictionaryColumns(onHeapDictionaryColumns);
    List<String> bloomFilterColumns = Arrays.asList("a", "b");
    indexingConfig.setBloomFilterColumns(bloomFilterColumns);
    Map<String, String> noDictionaryConfig = new HashMap<>();
    noDictionaryConfig.put("a", "SNAPPY");
    noDictionaryConfig.put("b", "PASS_THROUGH");
    indexingConfig.setnoDictionaryConfig(noDictionaryConfig);
    List<String> varLengthDictionaryColumns = Arrays.asList("a", "x", "z");
    indexingConfig.setVarLengthDictionaryColumns(varLengthDictionaryColumns);

    indexingConfig = JsonUtils.stringToObject(JsonUtils.objectToString(indexingConfig), IndexingConfig.class);

    assertEquals(indexingConfig.getLoadMode(), "MMAP");
    assertTrue(indexingConfig.isAggregateMetrics());
    assertEquals(indexingConfig.getInvertedIndexColumns(), invertedIndexColumns);
    assertEquals(indexingConfig.getSortedColumn(), sortedColumn);
    assertEquals(indexingConfig.getOnHeapDictionaryColumns(), onHeapDictionaryColumns);
    assertEquals(indexingConfig.getBloomFilterColumns(), bloomFilterColumns);
    assertEquals(indexingConfig.getNoDictionaryConfig(), noDictionaryConfig);
    assertEquals(indexingConfig.getVarLengthDictionaryColumns(), varLengthDictionaryColumns);
  }

  @Test
  public void testSegmentPartitionConfig()
      throws IOException {
    int numColumns = 5;
    Map<String, ColumnPartitionConfig> expectedColumnPartitionMap = new HashMap<>(5);
    for (int i = 0; i < numColumns; i++) {
      expectedColumnPartitionMap.put("column_" + i, new ColumnPartitionConfig("function_" + i, i + 1));
    }

    SegmentPartitionConfig expectedPartitionConfig = new SegmentPartitionConfig(expectedColumnPartitionMap);
    IndexingConfig expectedIndexingConfig = new IndexingConfig();
    expectedIndexingConfig.setSegmentPartitionConfig(expectedPartitionConfig);

    IndexingConfig actualIndexingConfig =
        JsonUtils.stringToObject(JsonUtils.objectToString(expectedIndexingConfig), IndexingConfig.class);

    SegmentPartitionConfig actualPartitionConfig = actualIndexingConfig.getSegmentPartitionConfig();
    Map<String, ColumnPartitionConfig> actualColumnPartitionMap = actualPartitionConfig.getColumnPartitionMap();
    assertEquals(actualColumnPartitionMap.size(), expectedColumnPartitionMap.size());

    for (String column : expectedColumnPartitionMap.keySet()) {
      assertEquals(actualPartitionConfig.getFunctionName(column), expectedPartitionConfig.getFunctionName(column));
      assertEquals(actualPartitionConfig.getNumPartitions(column), expectedPartitionConfig.getNumPartitions(column));
    }
  }

  /**
   * Unit test to check get and set of star tree index spec on IndexingConfig.
   * <ul>
   *   <li> Creates a StarTreeIndexSpec and sets it into the IndexingConfig. </li>
   *   <li> Indexing config is first serialized into a string, and then read back from string. </li>
   *   <li> Test to ensure star tree index spec values are correct after serialization and de-serialization. </li>
   * </ul>
   */
  @Test
  public void testStarTreeSpec()
      throws IOException {
    Random random = new Random(System.nanoTime());
    StarTreeIndexSpec expectedStarTreeSpec = new StarTreeIndexSpec();

    List<String> expectedDimensionSplitOrder = Arrays.asList("col1", "col2", "col3");
    expectedStarTreeSpec.setDimensionsSplitOrder(expectedDimensionSplitOrder);

    int expectedMaxLeafRecords = random.nextInt();
    expectedStarTreeSpec.setMaxLeafRecords(expectedMaxLeafRecords);

    int expectedSkipMaterializationThreshold = random.nextInt();
    expectedStarTreeSpec.setSkipMaterializationCardinalityThreshold(expectedSkipMaterializationThreshold);

    Set<String> expectedSkipMaterializationDimensions = new HashSet<>(Arrays.asList("col4", "col5"));
    expectedStarTreeSpec.setSkipMaterializationForDimensions(expectedSkipMaterializationDimensions);

    Set<String> expectedSkipStarNodeCreationForDimension = new HashSet<>(Arrays.asList("col6", "col7"));
    expectedStarTreeSpec.setSkipStarNodeCreationForDimensions(expectedSkipStarNodeCreationForDimension);

    IndexingConfig expectedIndexingConfig = new IndexingConfig();
    expectedIndexingConfig.setStarTreeIndexSpec(expectedStarTreeSpec);

    IndexingConfig actualIndexingConfig =
        JsonUtils.stringToObject(JsonUtils.objectToString(expectedIndexingConfig), IndexingConfig.class);
    StarTreeIndexSpec actualStarTreeSpec = actualIndexingConfig.getStarTreeIndexSpec();

    assertEquals(actualStarTreeSpec.getDimensionsSplitOrder(), expectedDimensionSplitOrder);
    assertEquals(actualStarTreeSpec.getMaxLeafRecords(), expectedMaxLeafRecords);

    assertEquals(actualStarTreeSpec.getSkipMaterializationCardinalityThreshold(), expectedSkipMaterializationThreshold);
    assertEquals(actualStarTreeSpec.getSkipMaterializationForDimensions(), expectedSkipMaterializationDimensions);
    assertEquals(actualStarTreeSpec.getSkipStarNodeCreationForDimensions(), expectedSkipStarNodeCreationForDimension);
  }
}
