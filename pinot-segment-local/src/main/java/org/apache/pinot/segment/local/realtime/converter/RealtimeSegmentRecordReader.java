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
package org.apache.pinot.segment.local.realtime.converter;

import java.io.File;
import java.util.Set;
import javax.annotation.Nullable;
import org.apache.pinot.segment.local.indexsegment.mutable.MutableSegmentImpl;
import org.apache.pinot.spi.data.readers.GenericRow;
import org.apache.pinot.spi.data.readers.RecordReader;
import org.apache.pinot.spi.data.readers.RecordReaderConfig;


/**
 * Record reader for Pinot realtime segment.
 */
public class RealtimeSegmentRecordReader implements RecordReader {
  private final MutableSegmentImpl _realtimeSegment;
  private final int _numDocs;
  private final int[] _sortedDocIdIterationOrder;

  private int _nextDocId = 0;

  public RealtimeSegmentRecordReader(MutableSegmentImpl realtimeSegment, @Nullable String sortedColumn) {
    _realtimeSegment = realtimeSegment;
    _numDocs = realtimeSegment.getNumDocsIndexed();
    _sortedDocIdIterationOrder = sortedColumn != null && realtimeSegment.getNumDocsIndexed() > 0
        ? realtimeSegment.getSortedDocIdIterationOrderWithSortedColumn(sortedColumn) : null;
  }

  public int[] getSortedDocIdIterationOrder() {
    return _sortedDocIdIterationOrder;
  }

  @Override
  public void init(File dataFile, Set<String> fieldsToRead, @Nullable RecordReaderConfig recordReaderConfig) {
  }

  @Override
  public boolean hasNext() {
    return _nextDocId < _numDocs;
  }

  @Override
  public GenericRow next() {
    return next(new GenericRow());
  }

  @Override
  public GenericRow next(GenericRow reuse) {
    if (_sortedDocIdIterationOrder == null) {
      return _realtimeSegment.getRecord(_nextDocId++, reuse);
    } else {
      return _realtimeSegment.getRecord(_sortedDocIdIterationOrder[_nextDocId++], reuse);
    }
  }

  @Override
  public void rewind() {
    _nextDocId = 0;
  }

  @Override
  public void close() {
  }
}
