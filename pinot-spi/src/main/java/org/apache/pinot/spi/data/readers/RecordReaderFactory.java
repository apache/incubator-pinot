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
package org.apache.pinot.spi.data.readers;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.apache.pinot.spi.data.Schema;
import org.apache.pinot.spi.utils.JsonUtils;


public class RecordReaderFactory {

  private static final Map<String, String> DEFAULT_RECORD_READER_CLASS_MAP = new HashMap<>();
  private static final Map<String, String> DEFAULT_RECORD_READER_CONFIG_CLASS_MAP = new HashMap<>();

  // TODO: This could be removed once we have dynamic loading plugins supports.
  private static final String DEFAULT_AVRO_RECORD_READER_CLASS = "org.apache.pinot.avro.data.readers.AvroRecordReader";
  private static final String DEFAULT_CSV_RECORD_READER_CLASS = "org.apache.pinot.csv.data.readers.CSVRecordReader";
  private static final String DEFAULT_CSV_RECORD_READER_CONFIG_CLASS =
      "org.apache.pinot.csv.data.readers.CSVRecordReaderConfig";
  private static final String DEFAULT_JSON_RECORD_READER_CLASS = "org.apache.pinot.json.data.readers.JSONRecordReader";
  private static final String DEFAULT_THRIFT_RECORD_READER_CLASS =
      "org.apache.pinot.thrift.data.readers.ThriftRecordReader";
  private static final String DEFAULT_THRIFT_RECORD_READER_CONFIG_CLASS =
      "org.apache.pinot.thrift.data.readers.ThriftRecordReaderConfig";
  private static final String DEFAULT_ORC_RECORD_READER_CLASS = "org.apache.pinot.orc.data.readers.ORCRecordReader";
  private static final String DEFAULT_PARQUET_RECORD_READER_CLASS =
      "org.apache.pinot.parquet.data.readers.ParquetRecordReader";

  private RecordReaderFactory() {
  }

  public static void register(String fileFormat, String recordReaderClassName, String recordReaderConfigClassName) {
    DEFAULT_RECORD_READER_CLASS_MAP.put(fileFormat.toUpperCase(), recordReaderClassName);
    DEFAULT_RECORD_READER_CONFIG_CLASS_MAP.put(fileFormat.toUpperCase(), recordReaderConfigClassName);
  }

  public static void register(FileFormat fileFormat, String recordReaderClassName, String recordReaderConfigClassName) {
    register(fileFormat.name(), recordReaderClassName, recordReaderConfigClassName);
  }

  /**
   * Construct a RecordReaderConfig instance from a given file path.
   *
   * @param recordReaderConfigClassName
   * @param readerConfigFile
   * @return
   * @throws IOException
   * @throws ClassNotFoundException
   */
  public static RecordReaderConfig getRecordReaderConfigByClassName(String recordReaderConfigClassName, String readerConfigFile)
      throws IOException, ClassNotFoundException {
    return getRecordReaderConfigByClassName(recordReaderConfigClassName, new File(readerConfigFile));
  }

  /**
   * Construct a RecordReaderConfig instance from a given file.
   *
   * @param recordReaderConfigClassName
   * @param readerConfigFile
   * @return
   * @throws IOException
   * @throws ClassNotFoundException
   */
  public static RecordReaderConfig getRecordReaderConfigByClassName(String recordReaderConfigClassName, File readerConfigFile)
      throws IOException, ClassNotFoundException {
    Class recordReaderConfigClass = Class.forName(recordReaderConfigClassName);
    RecordReaderConfig recordReaderConfig =
        (RecordReaderConfig) JsonUtils.fileToObject(readerConfigFile, recordReaderConfigClass);
    return recordReaderConfig;
  }

  /**
   * Construct a RecordReaderConfig instance from a given file.
   *
   * @param fileFormat
   * @param readerConfigFile
   * @return a RecordReaderConfig instance
   * @throws IOException
   * @throws ClassNotFoundException
   */
  public static RecordReaderConfig getRecordReaderConfig(FileFormat fileFormat, String readerConfigFile)
      throws IOException, ClassNotFoundException {
    String fileFormatKey = fileFormat.name().toUpperCase();
    if (DEFAULT_RECORD_READER_CONFIG_CLASS_MAP.containsKey(fileFormatKey)) {
      return getRecordReaderConfigByClassName(DEFAULT_RECORD_READER_CONFIG_CLASS_MAP.get(fileFormatKey), readerConfigFile);
    }
    throw new UnsupportedOperationException("No supported RecordReader found for file format - '" + fileFormat + "'");
  }

  /**
   * Construct a RecordReader instance given RecordReader class name, then initialize it with config.
   *
   * @param recordReaderClassName
   * @param dataFile
   * @param schema
   * @param recordReaderConfig
   * @return an initialized RecordReader instance.
   * @throws IOException
   * @throws ClassNotFoundException
   * @throws IllegalAccessException
   * @throws InstantiationException
   */
  public static RecordReader getRecordReaderByClass(String recordReaderClassName, File dataFile, Schema schema,
      RecordReaderConfig recordReaderConfig)
      throws IOException, ClassNotFoundException, IllegalAccessException, InstantiationException {
    RecordReader recordReader = (RecordReader) Class.forName(recordReaderClassName).newInstance();
    recordReader.init(dataFile, schema, recordReaderConfig);
    return recordReader;
  }

  /**
   * Get an initialized RecordReader.
   *
   * @param fileFormat
   * @param dataFile
   * @param schema
   * @param recordReaderConfig
   * @return an initialized RecordReader instance.
   * @throws ClassNotFoundException
   * @throws IOException
   * @throws InstantiationException
   * @throws IllegalAccessException
   */
  public static RecordReader getRecordReader(FileFormat fileFormat, File dataFile, Schema schema,
      RecordReaderConfig recordReaderConfig)
      throws ClassNotFoundException, IOException, InstantiationException, IllegalAccessException {
    return getRecordReader(fileFormat.name(), dataFile, schema, recordReaderConfig);
  }

  /**
   * Get a RecordReader instance of file format initialized with recordReaderConfig which is ready to read from dataFile given schema.
   *
   * @param fileFormatStr
   * @param dataFile
   * @param schema
   * @param recordReaderConfig
   * @return an initialized RecordReader instance.
   * @throws ClassNotFoundException
   * @throws InstantiationException
   * @throws IllegalAccessException
   * @throws IOException
   */
  public static RecordReader getRecordReader(String fileFormatStr, File dataFile, Schema schema,
      RecordReaderConfig recordReaderConfig)
      throws ClassNotFoundException, InstantiationException, IllegalAccessException, IOException {
    String fileFormatKey = fileFormatStr.toUpperCase();
    if (DEFAULT_RECORD_READER_CLASS_MAP.containsKey(fileFormatKey)) {
      return getRecordReaderByClass(DEFAULT_RECORD_READER_CLASS_MAP.get(fileFormatKey), dataFile, schema,
          recordReaderConfig);
    }
    throw new UnsupportedOperationException(
        "No supported RecordReader found for file format - '" + fileFormatStr + "'");
  }

  /**
   * Get registered RecordReader class name given a file format.
   *
   * @param fileFormatStr
   * @return recordReaderClassName
   */
  public static String getRecordReaderClassName(String fileFormatStr) {
    return DEFAULT_RECORD_READER_CLASS_MAP.get(fileFormatStr.toUpperCase());
  }

  /**
   * Get registered RecordReaderConfig class name given a file format.
   *
   * @param fileFormatStr
   * @return recordReaderConfigClassName
   */
  public static String getRecordReaderConfigClassName(String fileFormatStr) {
    return DEFAULT_RECORD_READER_CONFIG_CLASS_MAP.get(fileFormatStr.toUpperCase());
  }

  static {
    register(FileFormat.AVRO, DEFAULT_AVRO_RECORD_READER_CLASS, null);
    register(FileFormat.GZIPPED_AVRO, DEFAULT_AVRO_RECORD_READER_CLASS, null);
    register(FileFormat.CSV, DEFAULT_CSV_RECORD_READER_CLASS, DEFAULT_CSV_RECORD_READER_CONFIG_CLASS);
    register(FileFormat.JSON, DEFAULT_JSON_RECORD_READER_CLASS, null);
    register(FileFormat.THRIFT, DEFAULT_THRIFT_RECORD_READER_CLASS, DEFAULT_THRIFT_RECORD_READER_CONFIG_CLASS);
    register(FileFormat.ORC, DEFAULT_ORC_RECORD_READER_CLASS, null);
    register(FileFormat.PARQUET, DEFAULT_PARQUET_RECORD_READER_CLASS, null);
  }
}
