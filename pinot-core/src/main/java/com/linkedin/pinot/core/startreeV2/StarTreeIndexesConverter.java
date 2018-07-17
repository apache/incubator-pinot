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
import java.util.List;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.nio.channels.FileChannel;
import org.apache.commons.io.FileUtils;
import com.linkedin.pinot.common.segment.StarTreeV2Metadata;
import com.linkedin.pinot.core.segment.index.SegmentMetadataImpl;


public class StarTreeIndexesConverter implements StarTreeFormatConverter {

  private int _docsCount = 0;
  private List<StarTreeV2Metadata> _starTreeV2MetadataList;

  @Override
  public void convert(File indexStarTreeDir, int starTreeId) throws Exception {

    SegmentMetadataImpl v2Metadata = new SegmentMetadataImpl(indexStarTreeDir);
    createFilesIfNotExist(indexStarTreeDir);
    copyIndexData(indexStarTreeDir, v2Metadata, starTreeId);
    copyStarTreeData(indexStarTreeDir, starTreeId);

    return;
  }

  /**
   * Helper function to create files if do not exist to hold star tree information.
   *
   * @return void.
   */
  private void createFilesIfNotExist(File indexStarTreeDir) throws IOException {

    File index_file = new File(indexStarTreeDir, StarTreeV2Constant.STAR_TREE_V2_INDEX_MAP_FILE);
    if (!index_file.exists()) {
      index_file.createNewFile();
    }

    File data_file = new File(indexStarTreeDir, StarTreeV2Constant.STAR_TREE_V2_COlUMN_FILE);
    if (!data_file.exists()) {
      data_file.createNewFile();
    }

    return;
  }

  /**
   * Helper function to copy indexes to single file.
   *
   * @return void.
   */
  private void copyIndexData(File v2Directory, SegmentMetadataImpl v2Metadata, int starTreeId) throws Exception {

    _starTreeV2MetadataList = v2Metadata.getStarTreeV2Metadata();
    StarTreeV2Metadata metaData = _starTreeV2MetadataList.get(starTreeId);

    List<String> dimensionsSplitOrder = metaData.getDimensionsSplitOrder();
    List<String> met2aggfuncPairs = metaData.getMet2AggfuncPairs();
    _docsCount = metaData.getDocsCount();

    for (String dimension : dimensionsSplitOrder) {
      appendToFile(v2Directory, dimension, "DIMENSION", starTreeId);
    }

    for (String pair : met2aggfuncPairs) {
      appendToFile(v2Directory, pair, "METRIC", starTreeId);
    }

    return;
  }

  /**
   * Helper function to copy star tree to a single file.
   *
   * @return void.
   */
  private void copyStarTreeData(File v2Directory, int starTreeId) throws Exception {
    appendToFile(v2Directory, "root", "STARTREE", starTreeId);
    return;
  }

  /**
   * Helper function to append data from one file to another.
   *
   * @return void.
   */
  private void appendToFile(File path, String column, String type, int starTreeId) throws IOException {
    File readerFile = null;
    String readerFilePath = "";
    if (type.equals("DIMENSION")) {
      readerFile = new File(path, column + StarTreeV2Constant.DIMENSION_FWD_INDEX_SUFFIX);
      readerFilePath = readerFile.getPath();
    } else if (type.equals("METRIC")) {
      readerFile = new File(path, column + StarTreeV2Constant.METRIC_RAW_INDEX_SUFFIX);
      readerFilePath = readerFile.getPath();
    } else if (type.equals("STARTREE")) {
      readerFile = new File(path, StarTreeV2Constant.STAR_TREE + "_" + Integer.toString(starTreeId));
      readerFilePath = readerFile.getPath();
    } else {
      return;
    }

    String indexWriterFile = new File(path, StarTreeV2Constant.STAR_TREE_V2_INDEX_MAP_FILE).getPath();
    String columnWriterFile = new File(path, StarTreeV2Constant.STAR_TREE_V2_COlUMN_FILE).getPath();

    try {
      int b;
      File inFile = new File(readerFilePath);
      ByteBuffer buf = ByteBuffer.allocateDirect((int) inFile.length());
      InputStream is = new FileInputStream(inFile);
      while ((b = is.read()) != -1) {
        buf.put((byte) b);
      }

      File file = new File(columnWriterFile);
      boolean append = true;
      FileChannel channel = new FileOutputStream(file, append).getChannel();
      long start = channel.size();
      buf.flip();
      long end = buf.remaining();
      channel.write(buf);
      channel.close();

      PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(indexWriterFile, true)));
      out.println("startree" + starTreeId + "." + column + ".start:" + start);
      out.println("startree" + starTreeId + "." + column + ".size:" + end);
      out.close();

      FileUtils.deleteQuietly(readerFile);
    } catch (FileNotFoundException ex) {
      System.out.println("Unable to open file '" + indexWriterFile + "'");
    } catch (IOException ex) {
      System.out.println("Error reading file '" + readerFilePath + "'");
    }
  }
}
