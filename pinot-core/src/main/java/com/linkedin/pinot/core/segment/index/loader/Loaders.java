/**
 * Copyright (C) 2014-2015 LinkedIn Corp. (pinot-core@linkedin.com)
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
package com.linkedin.pinot.core.segment.index.loader;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;

import com.linkedin.pinot.common.metadata.segment.IndexLoadingConfigMetadata;
import com.linkedin.pinot.common.segment.ReadMode;
import com.linkedin.pinot.core.segment.creator.impl.V1Constants;
import com.linkedin.pinot.core.segment.index.IndexSegmentImpl;
import com.linkedin.pinot.core.segment.index.SegmentMetadataImpl;
import com.linkedin.pinot.core.segment.index.column.ColumnIndexContainer;
import com.linkedin.pinot.core.startree.StarTreeIndexNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Nov 13, 2014
 */

public class Loaders {
  private static final Logger LOGGER = LoggerFactory.getLogger(Loaders.class);

  public static class IndexSegment {
    public static com.linkedin.pinot.core.indexsegment.IndexSegment load(File indexDir, ReadMode mode) throws Exception {
      return load(indexDir, mode, null);
    }

    public static com.linkedin.pinot.core.indexsegment.IndexSegment load(File indexDir, ReadMode readMode,
        IndexLoadingConfigMetadata indexLoadingConfigMetadata) throws Exception {
      SegmentMetadataImpl metadata = new SegmentMetadataImpl(indexDir);

      Map<String, ColumnIndexContainer> indexContainerMap = new HashMap<String, ColumnIndexContainer>();

      for (String column : metadata.getColumnMetadataMap().keySet()) {
        indexContainerMap.put(column, ColumnIndexContainer.init(column, indexDir,
            metadata.getColumnMetadataFor(column), indexLoadingConfigMetadata, readMode));
      }

      // The star tree index (if available)
      StarTreeIndexNode starTreeRoot = null;
      if (metadata.hasStarTree()) {
        File starTreeFile = new File(indexDir, V1Constants.STARTREE_FILE);
        LOGGER.debug("Loading star tree index file {}", starTreeFile);
        starTreeRoot = StarTreeIndexNode.fromBytes(new FileInputStream(starTreeFile));
      }

      return new IndexSegmentImpl(indexDir, metadata, indexContainerMap, starTreeRoot);
    }
  }
}
