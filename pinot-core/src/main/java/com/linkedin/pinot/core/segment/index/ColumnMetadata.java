/**
 * Copyright (C) 2014-2018 LinkedIn Corp. (pinot-core@linkedin.com)
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
package com.linkedin.pinot.core.segment.index;

import com.linkedin.pinot.common.config.ColumnPartitionConfig;
import com.linkedin.pinot.common.data.DateTimeFieldSpec;
import com.linkedin.pinot.common.data.DimensionFieldSpec;
import com.linkedin.pinot.common.data.FieldSpec;
import com.linkedin.pinot.common.data.FieldSpec.DataType;
import com.linkedin.pinot.common.data.FieldSpec.FieldType;
import com.linkedin.pinot.common.data.MetricFieldSpec;
import com.linkedin.pinot.common.data.MetricFieldSpec.DerivedMetricType;
import com.linkedin.pinot.common.data.TimeFieldSpec;
import com.linkedin.pinot.core.data.partition.PartitionFunction;
import com.linkedin.pinot.core.data.partition.PartitionFunctionFactory;
import com.linkedin.pinot.core.segment.creator.impl.V1Constants;
import com.linkedin.pinot.startree.hll.HllSizeUtils;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.math.IntRange;
import org.codehaus.jackson.annotate.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.linkedin.pinot.core.segment.creator.impl.V1Constants.MetadataKeys.Column.*;
import static com.linkedin.pinot.core.segment.creator.impl.V1Constants.MetadataKeys.Segment.SEGMENT_PADDING_CHARACTER;
import static com.linkedin.pinot.core.segment.creator.impl.V1Constants.MetadataKeys.Segment.TIME_UNIT;


public class ColumnMetadata {
  private static final Logger LOGGER = LoggerFactory.getLogger(ColumnMetadata.class);

  private final FieldSpec fieldSpec;
  private final String columnName;
  private final int cardinality;
  private final int totalDocs;
  private final int totalRawDocs;
  private final int totalAggDocs;
  private final DataType dataType;
  private final int bitsPerElement;
  private final int columnMaxLength;
  private final FieldType fieldType;
  private final boolean isSorted;
  @JsonProperty
  private final boolean containsNulls;
  @JsonProperty
  private final boolean hasDictionary;
  @JsonProperty
  private final boolean hasInvertedIndex;
  private final boolean isSingleValue;
  private final boolean isVirtual;
  private final int maxNumberOfMultiValues;
  private final int totalNumberOfEntries;
  private final boolean isAutoGenerated;
  private final String defaultNullValueString;
  private final TimeUnit timeUnit;
  private final char paddingCharacter;
  private final DerivedMetricType derivedMetricType;
  private final int fieldSize;
  private final String originColumnName;
  private final Comparable minValue;
  private final Comparable maxValue;
  private final PartitionFunction partitionFunction;
  private final int numPartitions;
  private final List<IntRange> partitionRanges;
  private final String dateTimeFormat;
  private final String dateTimeGranularity;

  public static ColumnMetadata fromPropertiesConfiguration(String column, PropertiesConfiguration config) {
    Builder builder = new Builder();

    builder.setColumnName(column);
    builder.setCardinality(config.getInt(getKeyFor(column, CARDINALITY)));
    int totalDocs = config.getInt(getKeyFor(column, TOTAL_DOCS));
    builder.setTotalDocs(totalDocs);
    builder.setTotalRawDocs(config.getInt(getKeyFor(column, TOTAL_RAW_DOCS), totalDocs));
    builder.setTotalAggDocs(config.getInt(getKeyFor(column, TOTAL_AGG_DOCS), 0));
    DataType dataType = DataType.valueOf(config.getString(getKeyFor(column, DATA_TYPE)).toUpperCase());
    builder.setDataType(dataType);
    builder.setBitsPerElement(config.getInt(getKeyFor(column, BITS_PER_ELEMENT)));
    builder.setColumnMaxLength(config.getInt(getKeyFor(column, DICTIONARY_ELEMENT_SIZE)));
    builder.setFieldType(FieldType.valueOf(config.getString(getKeyFor(column, COLUMN_TYPE)).toUpperCase()));
    builder.setIsSorted(config.getBoolean(getKeyFor(column, IS_SORTED)));
    builder.setContainsNulls(config.getBoolean(getKeyFor(column, HAS_NULL_VALUE)));
    builder.setHasDictionary(config.getBoolean(getKeyFor(column, HAS_DICTIONARY), true));
    builder.setHasInvertedIndex(config.getBoolean(getKeyFor(column, HAS_INVERTED_INDEX)));
    builder.setSingleValue(config.getBoolean(getKeyFor(column, IS_SINGLE_VALUED)));
    builder.setMaxNumberOfMultiValues(config.getInt(getKeyFor(column, MAX_MULTI_VALUE_ELEMTS)));
    builder.setTotalNumberOfEntries(config.getInt(getKeyFor(column, TOTAL_NUMBER_OF_ENTRIES)));
    builder.setAutoGenerated(config.getBoolean(getKeyFor(column, IS_AUTO_GENERATED), false));
    builder.setDefaultNullValueString(config.getString(getKeyFor(column, DEFAULT_NULL_VALUE), null));
    builder.setTimeUnit(TimeUnit.valueOf(config.getString(TIME_UNIT, "DAYS").toUpperCase()));
    char paddingCharacter = V1Constants.Str.LEGACY_STRING_PAD_CHAR;
    if (config.containsKey(SEGMENT_PADDING_CHARACTER)) {
      String padding = config.getString(SEGMENT_PADDING_CHARACTER);
      paddingCharacter = StringEscapeUtils.unescapeJava(padding).charAt(0);
    }
    builder.setPaddingCharacter(paddingCharacter);

    String dateTimeFormat = config.getString(getKeyFor(column, DATETIME_FORMAT), null);
    if (dateTimeFormat != null) {
      builder.setDateTimeFormat(dateTimeFormat);
    }

    String dateTimeGranularity = config.getString(getKeyFor(column, DATETIME_GRANULARITY), null);
    if (dateTimeGranularity != null) {
      builder.setDateTimeGranularity(dateTimeGranularity);
    }

    // DERIVED_METRIC_TYPE property is used to check whether this field is derived or not
    // ORIGIN_COLUMN property is used to indicate the origin field of this derived metric
    String typeStr = config.getString(getKeyFor(column, DERIVED_METRIC_TYPE), null);
    DerivedMetricType derivedMetricType = (typeStr == null) ? null : DerivedMetricType.valueOf(typeStr.toUpperCase());

    if (derivedMetricType != null) {
      switch (derivedMetricType) {
        case HLL:
          try {
            final int hllLog2m = config.getInt(V1Constants.MetadataKeys.Segment.SEGMENT_HLL_LOG2M);
            builder.setFieldSize(HllSizeUtils.getHllFieldSizeFromLog2m(hllLog2m));
            final String originColumnName = config.getString(getKeyFor(column, ORIGIN_COLUMN));
            builder.setOriginColumnName(originColumnName);
          } catch (RuntimeException e) {
            LOGGER.error(
                "Column: " + column + " is HLL derived column, but missing log2m, fieldSize or originColumnName.");
            throw e;
          }
          break;
        default:
          throw new IllegalArgumentException("Column: " + column + " with derived metric Type: " + derivedMetricType
              + " is not supported in building column metadata.");
      }
      builder.setDerivedMetricType(derivedMetricType);
    }

    // Set min/max value if available.
    String minString = config.getString(getKeyFor(column, MIN_VALUE), null);
    String maxString = config.getString(getKeyFor(column, MAX_VALUE), null);
    if ((minString != null) && (maxString != null)) {
      switch (dataType) {
        case INT:
          builder.setMinValue(Integer.valueOf(minString));
          builder.setMaxValue(Integer.valueOf(maxString));
          break;
        case LONG:
          builder.setMinValue(Long.valueOf(minString));
          builder.setMaxValue(Long.valueOf(maxString));
          break;
        case FLOAT:
          builder.setMinValue(Float.valueOf(minString));
          builder.setMaxValue(Float.valueOf(maxString));
          break;
        case DOUBLE:
          builder.setMinValue(Double.valueOf(minString));
          builder.setMaxValue(Double.valueOf(maxString));
          break;
        case STRING:
          builder.setMinValue(minString);
          builder.setMaxValue(maxString);
          break;
        default:
          throw new IllegalStateException("Unsupported data type: " + dataType + " for column: " + column);
      }
    }

    String partitionFunctionName =
        config.getString(getKeyFor(column, V1Constants.MetadataKeys.Column.PARTITION_FUNCTION));
    if (partitionFunctionName != null) {
      int numPartitions = config.getInt(getKeyFor(column, V1Constants.MetadataKeys.Column.NUM_PARTITIONS));
      PartitionFunction partitionFunction =
          PartitionFunctionFactory.getPartitionFunction(partitionFunctionName, numPartitions);
      builder.setPartitionFunction(partitionFunction);
      builder.setNumPartitions(numPartitions);

      String[] valueString = config.getStringArray(getKeyFor(column, V1Constants.MetadataKeys.Column.PARTITION_VALUES));
      builder.setPartitionValues(ColumnPartitionConfig.rangesFromString(valueString));
    }

    return builder.build();
  }

  public PartitionFunction getPartitionFunction() {
    return partitionFunction;
  }

  public int getNumPartitions() {
    return numPartitions;
  }

  public List<IntRange> getPartitionRanges() {
    return partitionRanges;
  }

  public static class Builder {
    private String columnName;
    private int cardinality;
    private int totalDocs;
    private int totalRawDocs;
    private int totalAggDocs;
    private DataType dataType;
    private int bitsPerElement;
    private int columnMaxLength;
    private FieldType fieldType;
    private boolean isSorted;
    private boolean containsNulls;
    private boolean hasDictionary;
    private boolean hasInvertedIndex;
    private boolean isSingleValue;
    private boolean isVirtual;
    private int maxNumberOfMultiValues;
    private int totalNumberOfEntries;
    private boolean isAutoGenerated;
    private String defaultNullValueString;
    private TimeUnit timeUnit;
    private char paddingCharacter;
    private DerivedMetricType derivedMetricType;
    private int fieldSize;
    private String originColumnName;
    private Comparable minValue;
    private Comparable maxValue;
    private PartitionFunction partitionFunction;
    private List<IntRange> partitionValues = null;
    private int numPartitions;
    private String dateTimeFormat;
    private String dateTimeGranularity;

    public Builder setColumnName(String columnName) {
      this.columnName = columnName;
      return this;
    }

    public Builder setCardinality(int cardinality) {
      this.cardinality = cardinality;
      return this;
    }

    public Builder setTotalDocs(int totalDocs) {
      this.totalDocs = totalDocs;
      return this;
    }

    public Builder setTotalRawDocs(int totalRawDocs) {
      this.totalRawDocs = totalRawDocs;
      return this;
    }

    public Builder setTotalAggDocs(int totalAggDocs) {
      this.totalAggDocs = totalAggDocs;
      return this;
    }

    public Builder setDataType(DataType dataType) {
      this.dataType = dataType.getStoredType();
      return this;
    }

    public Builder setBitsPerElement(int bitsPerElement) {
      this.bitsPerElement = bitsPerElement;
      return this;
    }

    public Builder setColumnMaxLength(int columnMaxLength) {
      this.columnMaxLength = columnMaxLength;
      return this;
    }

    public Builder setFieldType(FieldType fieldType) {
      this.fieldType = fieldType;
      return this;
    }

    public Builder setIsSorted(boolean isSorted) {
      this.isSorted = isSorted;
      return this;
    }

    public Builder setContainsNulls(boolean containsNulls) {
      this.containsNulls = containsNulls;
      return this;
    }

    public Builder setHasDictionary(boolean hasDictionary) {
      this.hasDictionary = hasDictionary;
      return this;
    }

    public Builder setHasInvertedIndex(boolean hasInvertedIndex) {
      this.hasInvertedIndex = hasInvertedIndex;
      return this;
    }

    public Builder setSingleValue(boolean singleValue) {
      this.isSingleValue = singleValue;
      return this;
    }

    public Builder setMaxNumberOfMultiValues(int maxNumberOfMultiValues) {
      this.maxNumberOfMultiValues = maxNumberOfMultiValues;
      return this;
    }

    public Builder setTotalNumberOfEntries(int totalNumberOfEntries) {
      this.totalNumberOfEntries = totalNumberOfEntries;
      return this;
    }

    public Builder setAutoGenerated(boolean isAutoGenerated) {
      this.isAutoGenerated = isAutoGenerated;
      return this;
    }

    public Builder setVirtual(boolean isVirtual) {
      this.isVirtual = isVirtual;
      return this;
    }

    public Builder setDefaultNullValueString(String defaultNullValueString) {
      this.defaultNullValueString = defaultNullValueString;
      return this;
    }

    public Builder setTimeUnit(TimeUnit timeUnit) {
      this.timeUnit = timeUnit;
      return this;
    }

    public Builder setPaddingCharacter(char paddingCharacter) {
      this.paddingCharacter = paddingCharacter;
      return this;
    }

    public Builder setDerivedMetricType(DerivedMetricType derivedMetricType) {
      this.derivedMetricType = derivedMetricType;
      return this;
    }

    public Builder setFieldSize(int fieldSize) {
      this.fieldSize = fieldSize;
      return this;
    }

    public Builder setOriginColumnName(String originColumnName) {
      this.originColumnName = originColumnName;
      return this;
    }

    public Builder setMinValue(Comparable minValue) {
      this.minValue = minValue;
      return this;
    }

    public Builder setMaxValue(Comparable maxValue) {
      this.maxValue = maxValue;
      return this;
    }

    public Builder setPartitionFunction(PartitionFunction partitionFunction) {
      this.partitionFunction = partitionFunction;
      return this;
    }

    public void setNumPartitions(int numPartitions) {
      this.numPartitions = numPartitions;
    }

    public Builder setPartitionValues(List<IntRange> partitionValues) {
      this.partitionValues = partitionValues;
      return this;
    }

    public Builder setDateTimeFormat(String dateTimeFormat) {
      this.dateTimeFormat = dateTimeFormat;
      return this;
    }

    public Builder setDateTimeGranularity(String dateTimeGranularity) {
      this.dateTimeGranularity = dateTimeGranularity;
      return this;
    }

    public ColumnMetadata build() {
      return new ColumnMetadata(columnName, cardinality, totalDocs, totalRawDocs, totalAggDocs, dataType,
          bitsPerElement, columnMaxLength, fieldType, isSorted, containsNulls, hasDictionary, hasInvertedIndex,
          isSingleValue, maxNumberOfMultiValues, totalNumberOfEntries, isAutoGenerated, isVirtual, defaultNullValueString,
          timeUnit, paddingCharacter, derivedMetricType, fieldSize, originColumnName, minValue, maxValue,
          partitionFunction, numPartitions, partitionValues, dateTimeFormat, dateTimeGranularity);
    }
  }

  private ColumnMetadata(String columnName, int cardinality, int totalDocs, int totalRawDocs, int totalAggDocs,
      DataType dataType, int bitsPerElement, int columnMaxLength, FieldType fieldType, boolean isSorted,
      boolean hasNulls, boolean hasDictionary, boolean hasInvertedIndex, boolean isSingleValue,
      int maxNumberOfMultiValues, int totalNumberOfEntries, boolean isAutoGenerated, boolean isVirtual,
      String defaultNullValueString, TimeUnit timeUnit, char paddingCharacter, DerivedMetricType derivedMetricType,
      int fieldSize, String originColumnName, Comparable minValue, Comparable maxValue,
      PartitionFunction partitionFunction, int numPartitions, List<IntRange> partitionRanges, String dateTimeFormat,
      String dateTimeGranularity) {
    this.columnName = columnName;
    this.cardinality = cardinality;
    this.totalDocs = totalDocs;
    this.totalRawDocs = totalRawDocs;
    this.totalAggDocs = totalAggDocs;
    this.dataType = dataType;
    this.bitsPerElement = bitsPerElement;
    this.columnMaxLength = columnMaxLength;
    this.fieldType = fieldType;
    this.isSorted = isSorted;
    this.containsNulls = hasNulls;
    this.hasDictionary = hasDictionary;
    this.hasInvertedIndex = hasInvertedIndex;
    this.isSingleValue = isSingleValue;
    this.maxNumberOfMultiValues = maxNumberOfMultiValues;
    this.totalNumberOfEntries = totalNumberOfEntries;
    this.isAutoGenerated = isAutoGenerated;
    this.isVirtual = isVirtual;
    this.defaultNullValueString = defaultNullValueString;
    this.timeUnit = timeUnit;
    this.paddingCharacter = paddingCharacter;
    this.derivedMetricType = derivedMetricType;
    this.fieldSize = fieldSize;
    this.originColumnName = originColumnName;
    this.minValue = minValue;
    this.maxValue = maxValue;
    this.partitionFunction = partitionFunction;
    this.numPartitions = numPartitions;
    this.partitionRanges = partitionRanges;
    this.dateTimeFormat = dateTimeFormat;
    this.dateTimeGranularity = dateTimeGranularity;

    switch (fieldType) {
      case DIMENSION:
        this.fieldSpec = new DimensionFieldSpec(columnName, dataType, isSingleValue);
        break;
      case METRIC:
        if (derivedMetricType == null) {
          this.fieldSpec = new MetricFieldSpec(columnName, dataType);
        } else {
          this.fieldSpec = new MetricFieldSpec(columnName, dataType, fieldSize, derivedMetricType);
        }
        break;
      case TIME:
        this.fieldSpec = new TimeFieldSpec(columnName, dataType, timeUnit);
        break;
      case DATE_TIME:
        this.fieldSpec = new DateTimeFieldSpec(columnName, dataType, dateTimeFormat, dateTimeGranularity);
        break;
      default:
        throw new RuntimeException("Unsupported field type: " + fieldType);
    }
  }

  public String getColumnName() {
    return columnName;
  }

  /**
   * When a realtime segment has no-dictionary columns, the cardinality for those columns will be
   * set to Constants.UNKNOWN_CARDINALITY
   *
   * @return The cardinality of the column.
   */
  public int getCardinality() {
    return cardinality;
  }

  public int getTotalDocs() {
    return totalDocs;
  }

  public int getTotalRawDocs() {
    return totalRawDocs;
  }

  public int getTotalAggDocs() {
    return totalAggDocs;
  }

  public DataType getDataType() {
    return dataType;
  }

  public int getBitsPerElement() {
    return bitsPerElement;
  }

  public int getColumnMaxLength() {
    return columnMaxLength;
  }

  public FieldType getFieldType() {
    return fieldType;
  }

  public boolean isSorted() {
    return isSorted;
  }

  public boolean hasNulls() {
    return containsNulls;
  }

  public boolean hasDictionary() {
    return hasDictionary;
  }

  public boolean hasInvertedIndex() {
    return hasInvertedIndex;
  }

  public boolean isSingleValue() {
    return isSingleValue;
  }

  public int getMaxNumberOfMultiValues() {
    return maxNumberOfMultiValues;
  }

  public int getTotalNumberOfEntries() {
    return totalNumberOfEntries;
  }

  public boolean isAutoGenerated() {
    return isAutoGenerated;
  }

  public boolean isVirtual() {
    return isVirtual;
  }

  public String getDefaultNullValueString() {
    return defaultNullValueString;
  }

  public TimeUnit getTimeUnit() {
    return timeUnit;
  }

  public char getPaddingCharacter() {
    return paddingCharacter;
  }

  public DerivedMetricType getDerivedMetricType() {
    return derivedMetricType;
  }

  public String getOriginColumnName() {
    return originColumnName;
  }

  public FieldSpec getFieldSpec() {
    return fieldSpec;
  }

  public Comparable getMinValue() {
    return minValue;
  }

  public Comparable getMaxValue() {
    return maxValue;
  }

  public String getDateTimeFormat() {
    return dateTimeFormat;
  }

  public String getDateTimeGranularity() {
    return dateTimeGranularity;
  }

  @Override
  public String toString() {
    final StringBuilder result = new StringBuilder();
    final String newLine = System.getProperty("line.separator");

    result.append(this.getClass().getName());
    result.append(" Object {");
    result.append(newLine);

    //determine fields declared in this class only (no fields of superclass)
    final Field[] fields = this.getClass().getDeclaredFields();

    //print field names paired with their values
    for (final Field field : fields) {
      result.append("  ");
      try {
        result.append(field.getName());
        result.append(": ");
        //requires access to private field:
        result.append(field.get(this));
      } catch (final IllegalAccessException ex) {
        if (LOGGER.isErrorEnabled()) {
          LOGGER.error("Unable to access field " + field, ex);
        }
        result.append("[ERROR]");
      }
      result.append(newLine);
    }
    result.append("}");

    return result.toString();
  }
}
