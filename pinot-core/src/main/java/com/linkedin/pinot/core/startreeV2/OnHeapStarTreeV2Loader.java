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

package com.linkedin.pinot.core.startreeV2;

import java.io.File;
import java.io.FileInputStream;
import java.nio.channels.FileChannel;
import java.util.Map;
import java.util.List;
import java.io.IOException;
import java.util.ArrayList;
import java.io.FileOutputStream;
import com.linkedin.pinot.core.startree.StarTree;
import com.linkedin.pinot.core.common.DataSource;
import com.linkedin.pinot.common.segment.ReadMode;
import com.linkedin.pinot.core.startree.OffHeapStarTree;
import com.linkedin.pinot.common.segment.StarTreeV2Metadata;
import com.linkedin.pinot.core.segment.index.SegmentMetadataImpl;
import com.linkedin.pinot.core.indexsegment.generator.SegmentVersion;
import com.linkedin.pinot.core.segment.index.loader.IndexLoadingConfig;
import com.linkedin.pinot.core.indexsegment.immutable.ImmutableSegment;
import com.linkedin.pinot.core.indexsegment.immutable.ImmutableSegmentLoader;


public class OnHeapStarTreeV2Loader implements StarTreeV2Loader {

  // segment
  private ImmutableSegment _immutableSegment;
  private IndexLoadingConfig _v3IndexLoadingConfig;
  private SegmentMetadataImpl _segmentMetadataImpl;

  // star tree
  private File _indexDir;
  private File _starTreeFile;
  private File _starTreeIndexDataFile;
  private String _starTreeIndexMapFile;
  private Map<String, Integer> _starTreeIndexMetadata;
  private List<StarTreeV2Metadata> _starTreeV2MetadataList;
  private List<StarTreeV2DataSource> _starTreeV2DataSources;

  @Override
  public void init(File indexDir) throws Exception {
    // segment
    _indexDir = indexDir;
    _v3IndexLoadingConfig = new IndexLoadingConfig();
    _v3IndexLoadingConfig.setReadMode(ReadMode.mmap);
    _v3IndexLoadingConfig.setSegmentVersion(SegmentVersion.v3);
    _immutableSegment = ImmutableSegmentLoader.load(indexDir, _v3IndexLoadingConfig);
    _segmentMetadataImpl = new SegmentMetadataImpl(indexDir);

    // star tree
    _starTreeV2MetadataList = _segmentMetadataImpl.getStarTreeV2Metadata();
    _starTreeIndexDataFile = new File(indexDir, StarTreeV2Constant.STAR_TREE_V2_COlUMN_FILE);
    _starTreeIndexMapFile = new File(indexDir, StarTreeV2Constant.STAR_TREE_V2_INDEX_MAP_FILE).getPath();
    _starTreeFile = new File(indexDir, StarTreeV2Constant.STAR_TREE_V2_TEMP_FILE);
  }

  @Override
  public void load() throws IOException {
    _starTreeIndexMetadata = OnHeapStarTreeV2LoaderHelper.readMetaData(_starTreeIndexMapFile);
    _starTreeV2DataSources = new ArrayList<>();

    int starTreeId = 0;
    for (StarTreeV2Metadata metaData : _starTreeV2MetadataList) {
      StarTreeV2DataSource a =
          new StarTreeV2DataSource(_immutableSegment, _segmentMetadataImpl, metaData, _starTreeIndexMetadata,
              _starTreeIndexDataFile);
      a.loadDataSource(starTreeId);
      _starTreeV2DataSources.add(a);
      starTreeId += 1;
    }

    return;
  }

  @Override
  public StarTree getStarTree(int starTreeId) throws IOException {

    String sa = "startree" + starTreeId + ".root.start";
    String sb = "startree" + starTreeId + ".root.size";

    int start = _starTreeIndexMetadata.get(sa);
    int size = _starTreeIndexMetadata.get(sb);


    FileChannel src = new FileInputStream(_starTreeIndexDataFile).getChannel();
    FileChannel dest = new FileOutputStream(_starTreeFile).getChannel();
    src.transferTo(start, size, dest);

    src.close();
    dest.close();

    StarTree s = new OffHeapStarTree(_starTreeFile, ReadMode.mmap);

    return s;
  }

  @Override
  public DataSource getDimensionDataSource(int starTreeId, String column) throws Exception {
    return _starTreeV2DataSources.get(starTreeId).getDimensionForwardIndexReader(column);
  }

  @Override
  public DataSource getMetricAggPairDataSource(int starTreeId, String column) throws Exception {
    return _starTreeV2DataSources.get(starTreeId).getMetricRawIndexReader(column);
  }
}
