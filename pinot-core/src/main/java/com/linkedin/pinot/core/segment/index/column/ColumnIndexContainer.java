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
package com.linkedin.pinot.core.segment.index.column;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linkedin.pinot.common.metadata.segment.IndexLoadingConfigMetadata;
import com.linkedin.pinot.common.segment.ReadMode;
import com.linkedin.pinot.core.index.reader.DataFileReader;
import com.linkedin.pinot.core.index.reader.SingleColumnMultiValueReader;
import com.linkedin.pinot.core.index.reader.impl.FixedBitSkipListSCMVReader;
import com.linkedin.pinot.core.index.reader.impl.FixedByteWidthRowColDataFileReader;
import com.linkedin.pinot.core.segment.creator.InvertedIndexCreator;
import com.linkedin.pinot.core.segment.creator.impl.V1Constants;
import com.linkedin.pinot.core.segment.creator.impl.inv.BitmapInvertedIndexCreator;
import com.linkedin.pinot.core.segment.index.BitmapInvertedIndexReader;
import com.linkedin.pinot.core.segment.index.ColumnMetadata;
import com.linkedin.pinot.core.segment.index.InvertedIndexReader;
import com.linkedin.pinot.core.segment.index.readers.DoubleDictionary;
import com.linkedin.pinot.core.segment.index.readers.FixedBitCompressedSVForwardIndexReader;
import com.linkedin.pinot.core.segment.index.readers.FloatDictionary;
import com.linkedin.pinot.core.segment.index.readers.ImmutableDictionaryReader;
import com.linkedin.pinot.core.segment.index.readers.IntDictionary;
import com.linkedin.pinot.core.segment.index.readers.LongDictionary;
import com.linkedin.pinot.core.segment.index.readers.StringDictionary;


public abstract class ColumnIndexContainer {
  private static final Logger LOGGER = LoggerFactory.getLogger(ColumnIndexContainer.class);

  public static ColumnIndexContainer init(String column, File indexDir, ColumnMetadata metadata,
      IndexLoadingConfigMetadata indexLoadingConfigMetadata, ReadMode mode) throws Exception {

    boolean loadInverted = false;
    if (indexLoadingConfigMetadata != null) {
      if (indexLoadingConfigMetadata.getLoadingInvertedIndexColumns() != null) {
        loadInverted = indexLoadingConfigMetadata.getLoadingInvertedIndexColumns().contains(column);
      }
    }

    File dictionaryFile = new File(indexDir, column + V1Constants.Dict.FILE_EXTENTION);
    ImmutableDictionaryReader dictionary = load(metadata, dictionaryFile, mode);

    if (metadata.isSorted() && metadata.isSingleValue()) {
      return loadSorted(column, indexDir, metadata, dictionary, mode);
    }

    if (metadata.isSingleValue()) {
      return loadUnsorted(column, indexDir, metadata, dictionary, mode, loadInverted);
    }
    return loadMultiValue(column, indexDir, metadata, dictionary, mode, loadInverted);
  }

  private static ColumnIndexContainer loadSorted(String column, File indexDir, ColumnMetadata metadata,
      ImmutableDictionaryReader dictionary, ReadMode mode) throws IOException {
    File fwdIndexFile = new File(indexDir, column + V1Constants.Indexes.SORTED_FWD_IDX_FILE_EXTENTION);

    FixedByteWidthRowColDataFileReader indexReader =
        new FixedByteWidthRowColDataFileReader(fwdIndexFile, metadata.getCardinality(), 2, new int[] { 4, 4 },
            mode == ReadMode.mmap);
    return new SortedSVColumnIndexContainer(column, metadata, indexReader, dictionary);
  }

  private static ColumnIndexContainer loadUnsorted(String column, File indexDir, ColumnMetadata metadata,
      ImmutableDictionaryReader dictionary, ReadMode mode, boolean loadInverted) throws IOException {
    File fwdIndexFile = new File(indexDir, column + V1Constants.Indexes.UN_SORTED_SV_FWD_IDX_FILE_EXTENTION);
    File invertedIndexFile = new File(indexDir, column + V1Constants.Indexes.BITMAP_INVERTED_INDEX_FILE_EXTENSION);

    FixedBitCompressedSVForwardIndexReader fwdIndexReader =
        new FixedBitCompressedSVForwardIndexReader(fwdIndexFile, metadata.getTotalDocs(), metadata.getBitsPerElement(),
            mode == ReadMode.mmap, metadata.hasNulls());

    BitmapInvertedIndexReader invertedIndex = null;

    if (loadInverted) {
      invertedIndex =
          createAndLoadInvertedIndexFor(column, fwdIndexReader, metadata, invertedIndexFile, mode, indexDir);
    }

    return new UnsortedSVColumnIndexContainer(column, metadata, fwdIndexReader, dictionary, invertedIndex);
  }

  private static ColumnIndexContainer loadMultiValue(String column, File indexDir, ColumnMetadata metadata,
      ImmutableDictionaryReader dictionary, ReadMode mode, boolean loadInverted) throws Exception {
    File fwdIndexFile = new File(indexDir, column + V1Constants.Indexes.UN_SORTED_MV_FWD_IDX_FILE_EXTENTION);
    File invertedIndexFile = new File(indexDir, column + V1Constants.Indexes.BITMAP_INVERTED_INDEX_FILE_EXTENSION);

    FixedBitSkipListSCMVReader fwdIndexReader =
        new FixedBitSkipListSCMVReader(fwdIndexFile, metadata.getTotalDocs(), metadata.getTotalNumberOfEntries(),
            metadata.getBitsPerElement(), false, mode == ReadMode.mmap);

    BitmapInvertedIndexReader invertedIndex = null;

    if (loadInverted) {
      invertedIndex =
          createAndLoadInvertedIndexFor(column, fwdIndexReader, metadata, invertedIndexFile, mode, indexDir);
    }

    return new UnSortedMVColumnIndexContainer(column, metadata, fwdIndexReader, dictionary, invertedIndex);
  }

  private static BitmapInvertedIndexReader createAndLoadInvertedIndexFor(String column, DataFileReader fwdIndex,
      ColumnMetadata metadata, File invertedIndexFile, ReadMode mode, File indexDir) throws IOException {

    File inProgress = new File(column + "_inv.inprogress");

    // returning inverted index from file only when marker file does not exist and inverted file exist
    if (!inProgress.exists() && invertedIndexFile.exists()) {
      LOGGER.warn("found inverted index for colummn {}, loading it", column);
      return new BitmapInvertedIndexReader(invertedIndexFile, metadata.getCardinality(), mode == ReadMode.mmap);
    }

    // creating the marker file
    FileUtils.touch(inProgress);

    if (invertedIndexFile.exists()) {
      // if inverted index file exists, it means that we got an exception while creating inverted index last time
      // deleting it
      FileUtils.deleteQuietly(invertedIndexFile);
    }

    LOGGER.warn("did not find inverted index for colummn {}, creating it", column);

    // creating inverted index for the column now
    InvertedIndexCreator creator =
        new BitmapInvertedIndexCreator(indexDir, metadata.getCardinality(), metadata.toFieldSpec());
    if (!metadata.isSingleValue()) {
      SingleColumnMultiValueReader mvFwdIndex = (SingleColumnMultiValueReader) fwdIndex;
      int[] container = new int[metadata.getMaxNumberOfMultiValues()];
      for (int i = 0; i < metadata.getTotalDocs(); i++) {
        int len = mvFwdIndex.getIntArray(i, container);
        Integer[] dicIds = new Integer[len];
        for (int j = 0; j < len; j++) {
          dicIds[j] = container[j];
        }
        creator.add(i, dicIds);
      }
    } else {
      FixedBitCompressedSVForwardIndexReader svFwdIndex = (FixedBitCompressedSVForwardIndexReader) fwdIndex;
      for (int i = 0; i < metadata.getTotalDocs(); i++) {
        creator.add(i, svFwdIndex.getInt(i));
      }
    }
    creator.seal();

    // delete the marker file
    FileUtils.deleteQuietly(inProgress);

    LOGGER.warn("created inverted index for colummn {}, loading it", column);
    return new BitmapInvertedIndexReader(invertedIndexFile, metadata.getCardinality(), mode == ReadMode.mmap);
  }

  @SuppressWarnings("incomplete-switch")
  private static ImmutableDictionaryReader load(ColumnMetadata metadata, File dictionaryFile, ReadMode loadMode)
      throws IOException {
    switch (metadata.getDataType()) {
      case INT:
        return new IntDictionary(dictionaryFile, metadata, loadMode);
      case LONG:
        return new LongDictionary(dictionaryFile, metadata, loadMode);
      case FLOAT:
        return new FloatDictionary(dictionaryFile, metadata, loadMode);
      case DOUBLE:
        return new DoubleDictionary(dictionaryFile, metadata, loadMode);
      case STRING:
      case BOOLEAN:
        return new StringDictionary(dictionaryFile, metadata, loadMode);
    }

    throw new UnsupportedOperationException("unsupported data type : " + metadata.getDataType());
  }

  /**
   *
   * @return
   */
  public abstract InvertedIndexReader getInvertedIndex();

  /**
   *
   * @return
   */
  public abstract DataFileReader getForwardIndex();

  /**
   *
   * @return
   */
  public abstract ImmutableDictionaryReader getDictionary();

  /**
   *
   * @return
   */
  public abstract ColumnMetadata getColumnMetadata();

  /**
   *
   * @return
   * @throws Exception
   */
  public abstract boolean unload() throws Exception;
}
