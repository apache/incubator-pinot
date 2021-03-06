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
package org.apache.pinot.core.segment.processing.genericrow;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.List;
import org.apache.pinot.segment.spi.memory.PinotDataBuffer;
import org.apache.pinot.spi.data.FieldSpec;
import org.apache.pinot.spi.data.readers.GenericRow;


/**
 * File reader for {@link GenericRow}. The input files should be generated by the {@link GenericRowFileWriter}.
 */
public class GenericRowFileReader implements Closeable {
  private final int _numRows;
  private final PinotDataBuffer _offsetBuffer;
  private final PinotDataBuffer _dataBuffer;
  private final GenericRowDeserializer _deserializer;
  private final int _numSortFields;

  public GenericRowFileReader(File offsetFile, File dataFile, List<FieldSpec> fieldSpecs, boolean includeNullFields,
      int numSortFields)
      throws IOException {
    long offsetFileLength = offsetFile.length();
    _numRows = (int) (offsetFileLength >>> 3); // offsetFileLength / Long.BYTES
    _offsetBuffer = PinotDataBuffer
        .mapFile(offsetFile, true, 0L, offsetFileLength, ByteOrder.BIG_ENDIAN, "GenericRow offset buffer");
    _dataBuffer = PinotDataBuffer
        .mapFile(dataFile, true, 0L, dataFile.length(), PinotDataBuffer.NATIVE_ORDER, "GenericRow data buffer");
    _deserializer = new GenericRowDeserializer(_dataBuffer, fieldSpecs, includeNullFields);
    _numSortFields = numSortFields;
  }

  /**
   * Returns the number of rows within the files.
   */
  public int getNumRows() {
    return _numRows;
  }

  /**
   * Returns the number of sort fields.
   */
  public int getNumSortFields() {
    return _numSortFields;
  }

  /**
   * Reads the data of the given row id into the given buffer row.
   */
  public void read(int rowId, GenericRow buffer) {
    long offset = _offsetBuffer.getLong((long) rowId << 3); // rowId * Long.BYTES
    _deserializer.deserialize(offset, buffer);
  }

  /**
   * Compares the rows at the given row ids. Only compare the values for the sort fields.
   */
  public int compare(int rowId1, int rowId2) {
    long offset1 = _offsetBuffer.getLong((long) rowId1 << 3); // rowId1 * Long.BYTES
    long offset2 = _offsetBuffer.getLong((long) rowId2 << 3); // rowId2 * Long.BYTES
    return _deserializer.compare(offset1, offset2, _numSortFields);
  }

  /**
   * Returns a record reader for the rows within the file. Records are sorted if sort order is configured.
   */
  public GenericRowFileRecordReader getRecordReader() {
    return new GenericRowFileRecordReader(this);
  }

  @Override
  public void close()
      throws IOException {
    _offsetBuffer.close();
    _dataBuffer.close();
  }
}
