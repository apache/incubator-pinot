package com.linkedin.pinot.core.query.selection;

import java.io.Serializable;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.linkedin.pinot.common.data.FieldSpec.DataType;
import com.linkedin.pinot.common.request.Selection;
import com.linkedin.pinot.common.request.SelectionSort;
import com.linkedin.pinot.common.response.ServerInstance;
import com.linkedin.pinot.common.utils.DataTable;
import com.linkedin.pinot.common.utils.DataTableBuilder;
import com.linkedin.pinot.common.utils.DataTableBuilder.DataSchema;
import com.linkedin.pinot.core.common.Block;
import com.linkedin.pinot.core.common.BlockDocIdIterator;
import com.linkedin.pinot.core.common.BlockId;
import com.linkedin.pinot.core.common.BlockMultiValIterator;
import com.linkedin.pinot.core.common.BlockSingleValIterator;
import com.linkedin.pinot.core.common.Constants;
import com.linkedin.pinot.core.indexsegment.IndexSegment;
import com.linkedin.pinot.core.segment.index.readers.DictionaryReader;
import com.linkedin.pinot.core.segment.index.readers.DoubleDictionary;
import com.linkedin.pinot.core.segment.index.readers.FloatDictionary;
import com.linkedin.pinot.core.segment.index.readers.IntDictionary;
import com.linkedin.pinot.core.segment.index.readers.LongDictionary;
import com.linkedin.pinot.core.segment.index.readers.StringDictionary;


/**
 * SelectionOperator provides the apis for selection query.
 *
 * @author xiafu
 *
 */
public class SelectionOperatorService {

  private int _numDocsScanned = 0;
  private final List<SelectionSort> _sortSequence;
  private final List<String> _selectionColumns;
  private final int _selectionSize;
  private final int _selectionOffset;
  private final int _maxRowSize;
  private final DataSchema _dataSchema;
  private final Comparator<Serializable[]> _rowComparator;
  private final PriorityQueue<Serializable[]> _rowEventsSet;

  private Comparator<Integer> _rowDocIdComparator;
  private PriorityQueue<Integer> _rowDocIdSet;

  private final IndexSegment _indexSegment;
  private final boolean _doOrdering;

  public static final Map<DataType, DecimalFormat> DEFAULT_FORMAT_STRING_MAP = new HashMap<DataType, DecimalFormat>();
  static {
    DEFAULT_FORMAT_STRING_MAP.put(DataType.INT, new DecimalFormat("##########"));
    DEFAULT_FORMAT_STRING_MAP.put(DataType.LONG, new DecimalFormat("####################"));
    DEFAULT_FORMAT_STRING_MAP.put(DataType.FLOAT, new DecimalFormat("##########.#####"));
    DEFAULT_FORMAT_STRING_MAP.put(DataType.DOUBLE, new DecimalFormat("####################.##########"));
    DEFAULT_FORMAT_STRING_MAP.put(DataType.INT_ARRAY, new DecimalFormat("##########"));
    DEFAULT_FORMAT_STRING_MAP.put(DataType.LONG_ARRAY, new DecimalFormat("####################"));
    DEFAULT_FORMAT_STRING_MAP.put(DataType.FLOAT_ARRAY, new DecimalFormat("##########.#####"));
    DEFAULT_FORMAT_STRING_MAP.put(DataType.DOUBLE_ARRAY, new DecimalFormat("####################.##########"));
  }

  public SelectionOperatorService(Selection selections, IndexSegment indexSegment) {
    _indexSegment = indexSegment;
    if ((selections.getSelectionSortSequence() == null) || selections.getSelectionSortSequence().isEmpty()) {
      _doOrdering = false;
    } else {
      _doOrdering = true;
    }
    _sortSequence = appendNatureOrdering(selections.getSelectionSortSequence());
    _selectionColumns = getSelectionColumns(selections.getSelectionColumns());
    _selectionSize = selections.getSize();
    _selectionOffset = selections.getOffset();
    _maxRowSize = _selectionOffset + _selectionSize;
    _dataSchema = getDataSchema(_sortSequence, _selectionColumns, _indexSegment);
    _rowComparator = getComparator(_sortSequence, _dataSchema);
    _rowEventsSet = new PriorityQueue<Serializable[]>(_maxRowSize, _rowComparator);
    _rowDocIdComparator = null;
    _rowDocIdSet = null;
  }

  private List<String> getSelectionColumns(List<String> selectionColumns) {
    if ((selectionColumns.size() == 1) && selectionColumns.get(0).equals("*")) {
      final List<String> newSelectionColumns = new ArrayList<String>();
      for (final String columnName : _indexSegment.getSegmentMetadata().getSchema().getColumnNames()) {
        newSelectionColumns.add(columnName);
      }
      return newSelectionColumns;
    }
    return selectionColumns;
  }

  public SelectionOperatorService(Selection selections, DataSchema dataSchema) {
    _indexSegment = null;
    if ((selections.getSelectionSortSequence() == null) || selections.getSelectionSortSequence().isEmpty()) {
      _doOrdering = false;
    } else {
      _doOrdering = true;
    }
    _sortSequence = appendNatureOrdering(selections.getSelectionSortSequence());
    _selectionColumns = getSelectionColumns(selections.getSelectionColumns(), dataSchema);
    _selectionSize = selections.getSize();
    _selectionOffset = selections.getOffset();
    _maxRowSize = _selectionOffset + _selectionSize;
    _dataSchema = dataSchema;
    _rowComparator = getComparator(_sortSequence, _dataSchema);
    _rowEventsSet = new PriorityQueue<Serializable[]>(_maxRowSize, _rowComparator);
    _rowDocIdComparator = null;
    _rowDocIdSet = null;
  }

  private List<String> getSelectionColumns(List<String> selectionColumns, DataSchema dataSchema) {
    if ((selectionColumns.size() == 1) && selectionColumns.get(0).equals("*")) {
      final List<String> newSelectionColumns = new ArrayList<String>();
      for (int i = 0; i < dataSchema.size(); ++i) {
        newSelectionColumns.add(dataSchema.getColumnName(i));
      }
      return newSelectionColumns;
    }
    return selectionColumns;
  }

  public PriorityQueue<Serializable[]> merge(PriorityQueue<Serializable[]> rowEventsSet1,
      PriorityQueue<Serializable[]> rowEventsSet2) {
    final Iterator<Serializable[]> iterator = rowEventsSet2.iterator();
    while (iterator.hasNext()) {
      final Serializable[] row = iterator.next();
      if (rowEventsSet1.size() < _maxRowSize) {
        rowEventsSet1.add(row);
      } else {
        if (_rowComparator.compare(rowEventsSet1.peek(), row) < 0) {
          rowEventsSet1.add(row);
          rowEventsSet1.poll();
        }
      }
    }
    return rowEventsSet1;
  }

  public PriorityQueue<Serializable[]> reduce(Map<ServerInstance, DataTable> selectionResults) {
    _rowEventsSet.clear();
    for (final DataTable dt : selectionResults.values()) {
      for (int rowId = 0; rowId < dt.getNumberOfRows(); ++rowId) {
        final Serializable[] row = getRowFromDataTable(dt, rowId);
        if (_rowEventsSet.size() < _maxRowSize) {
          _rowEventsSet.add(row);
        } else {
          if (_rowComparator.compare(_rowEventsSet.peek(), row) < 0) {
            _rowEventsSet.add(row);
            _rowEventsSet.poll();
          }
        }
      }
    }
    return _rowEventsSet;
  }

  public JSONObject render(PriorityQueue<Serializable[]> finalResults, DataSchema dataSchema, int offset)
      throws Exception {
    final List<JSONArray> rowEventsJSonList = new LinkedList<JSONArray>();
    while (finalResults.size() > offset) {
      ((LinkedList<JSONArray>) rowEventsJSonList).addFirst(getJSonArrayFromRow(finalResults.poll(), dataSchema));
    }
    final JSONObject resultJsonObject = new JSONObject();
    resultJsonObject.put("results", new JSONArray(rowEventsJSonList));
    resultJsonObject.put("columns", getSelectionColumnsFromDataSchema(dataSchema));
    return resultJsonObject;
  }

  private JSONArray getSelectionColumnsFromDataSchema(DataSchema dataSchema) {
    final JSONArray jsonArray = new JSONArray();
    for (int idx = 0; idx < dataSchema.size(); ++idx) {
      if (_selectionColumns.contains(dataSchema.getColumnName(idx))) {
        jsonArray.put(dataSchema.getColumnName(idx));
      }
    }
    return jsonArray;
  }

  public PriorityQueue<Serializable[]> getRowEventsSet() {
    return _rowEventsSet;
  }

  public DataSchema getDataSchema() {
    return _dataSchema;
  }

  public long getNumDocsScanned() {
    return _numDocsScanned;
  }

  private Comparator<Serializable[]> getComparator(final List<SelectionSort> sortSequence, final DataSchema dataSchema) {
    return new Comparator<Serializable[]>() {
      @Override
      public int compare(Serializable[] o1, Serializable[] o2) {
        for (int i = 0; i < sortSequence.size(); ++i) {
          switch (dataSchema.getColumnType(i)) {
            case INT:
              if (((Integer) o1[i]) > ((Integer) o2[i])) {
                if (!sortSequence.get(i).isIsAsc()) {
                  return 1;
                } else {
                  return -1;
                }
              }
              if (((Integer) o1[i]) < ((Integer) o2[i])) {
                if (!sortSequence.get(i).isIsAsc()) {
                  return -1;
                } else {
                  return 1;
                }
              }
              break;
            case SHORT:
              if (((Short) o1[i]) > ((Short) o2[i])) {
                if (!sortSequence.get(i).isIsAsc()) {
                  return 1;
                } else {
                  return -1;
                }
              }
              if (((Short) o1[i]) < ((Short) o2[i])) {
                if (!sortSequence.get(i).isIsAsc()) {
                  return -1;
                } else {
                  return 1;
                }
              }
              break;
            case LONG:
              if (((Long) o1[i]) > ((Long) o2[i])) {
                if (!sortSequence.get(i).isIsAsc()) {
                  return 1;
                } else {
                  return -1;
                }
              }
              if (((Long) o1[i]) < ((Long) o2[i])) {
                if (!sortSequence.get(i).isIsAsc()) {
                  return -1;
                } else {
                  return 1;
                }
              }
              break;
            case FLOAT:
              if (((Float) o1[i]) > ((Float) o2[i])) {
                if (!sortSequence.get(i).isIsAsc()) {
                  return 1;
                } else {
                  return -1;
                }
              }
              if (((Float) o1[i]) < ((Float) o2[i])) {
                if (!sortSequence.get(i).isIsAsc()) {
                  return -1;
                } else {
                  return 1;
                }
              }
              break;
            case DOUBLE:
              if (((Double) o1[i]) > ((Double) o2[i])) {
                if (!sortSequence.get(i).isIsAsc()) {
                  return 1;
                } else {
                  return -1;
                }
              }
              if (((Double) o1[i]) < ((Double) o2[i])) {
                if (!sortSequence.get(i).isIsAsc()) {
                  return -1;
                } else {
                  return 1;
                }
              }
              break;
            case STRING:
              if (((String) o1[i]).compareTo(((String) o2[i])) > 0) {
                if (!sortSequence.get(i).isIsAsc()) {
                  return 1;
                } else {
                  return -1;
                }
              }
              if (((String) o1[i]).compareTo(((String) o2[i])) < 0) {
                if (!sortSequence.get(i).isIsAsc()) {
                  return -1;
                } else {
                  return 1;
                }
              }
              break;
            default:
              break;
          }
        }
        return 0;
      };
    };
  }

  public JSONObject render(PriorityQueue<Serializable[]> reduceResults) throws Exception {
    return render(reduceResults, _dataSchema, _selectionOffset);
  }

  private List<SelectionSort> appendNatureOrdering(final List<SelectionSort> selectionSorts) {
    final List<SelectionSort> newSelectionSorts = new ArrayList<SelectionSort>();
    if (selectionSorts != null) {
      newSelectionSorts.addAll(selectionSorts);
    }

    final SelectionSort selectionSort0 = new SelectionSort();
    selectionSort0.setColumn("_segmentId");
    selectionSort0.setIsAsc(true);
    newSelectionSorts.add(selectionSort0);
    final SelectionSort selectionSort1 = new SelectionSort();
    selectionSort1.setColumn("_docId");
    selectionSort1.setIsAsc(true);
    newSelectionSorts.add(selectionSort1);
    return newSelectionSorts;
  }

  private DataSchema getDataSchema(List<SelectionSort> sortSequence, List<String> selectionColumns,
      IndexSegment indexSegment) {
    final List<String> columns = new ArrayList<String>();

    for (final SelectionSort selectionSort : sortSequence) {
      columns.add(selectionSort.getColumn());
    }
    for (final String selectionColumn : selectionColumns) {
      if (!columns.contains(selectionColumn)) {
        columns.add(selectionColumn);
      }
    }
    final DataType[] dataTypes = new DataType[columns.size()];
    for (int i = 0; i < dataTypes.length; ++i) {
      if (columns.get(i).equals("_segmentId") || (columns.get(i).equals("_docId"))) {
        dataTypes[i] = DataType.INT;
      } else {
        dataTypes[i] = indexSegment.getDataSource(columns.get(i)).nextBlock(new BlockId(0)).getMetadata().getDataType();
        if (!indexSegment.getDataSource(columns.get(i)).nextBlock().getMetadata().isSingleValue()) {
          dataTypes[i] = DataType.valueOf(dataTypes[i] + "_ARRAY");
        }
      }
    }
    return new DataSchema(columns.toArray(new String[0]), dataTypes);
  }

  public void iterateOnBlock(BlockDocIdIterator blockDocIdIterator, Block[] blocks) throws Exception {
    int docId = 0;
    _rowDocIdComparator = getDocIdComparator(_sortSequence, _dataSchema, blocks);
    _rowDocIdSet = new PriorityQueue<Integer>(_maxRowSize, _rowDocIdComparator);
    while ((docId = blockDocIdIterator.next()) != Constants.EOF) {
      _numDocsScanned++;
      if (_rowDocIdSet.size() < _maxRowSize) {
        _rowDocIdSet.add(docId);
      } else {
        if (_doOrdering && (_rowDocIdComparator.compare(docId, _rowDocIdSet.peek()) > 0)) {
          _rowDocIdSet.add(docId);
          _rowDocIdSet.poll();
        }
      }
    }
    mergeToRowEventsSet(blocks);
  }

  public PriorityQueue<Serializable[]> mergeToRowEventsSet(Block[] blocks) throws Exception {
    final PriorityQueue<Serializable[]> rowEventsPriorityQueue =
        new PriorityQueue<Serializable[]>(_maxRowSize, _rowComparator);
    while (!_rowDocIdSet.isEmpty()) {
      rowEventsPriorityQueue.add(getRowFromBlockValSets(_rowDocIdSet.poll(), blocks));
    }
    merge(_rowEventsSet, rowEventsPriorityQueue);
    return _rowEventsSet;
  }

  private Comparator<Integer> getDocIdComparator(final List<SelectionSort> sortSequence, final DataSchema dataSchema,
      final Block[] blocks) {

    return new Comparator<Integer>() {
      @Override
      public int compare(Integer o1, Integer o2) {
        for (int i = 0; i < sortSequence.size(); ++i) {
          if ((sortSequence.get(i).getColumn().equals("_segmentId"))
              || (sortSequence.get(i).getColumn().equals("_docId"))) {
            return (o2 - o1);
          }

          final BlockSingleValIterator blockValSetIterator =
              (BlockSingleValIterator) blocks[i].getBlockValueSet().iterator();
          blockValSetIterator.skipTo(o1);
          int v1 = blockValSetIterator.nextIntVal();
          blockValSetIterator.skipTo(o2);
          int v2 = blockValSetIterator.nextIntVal();
          if (v1 > v2) {
            if (!sortSequence.get(i).isIsAsc()) {
              return 1;
            } else {
              return -1;
            }
          }
          if (v1 < v2) {
            if (!sortSequence.get(i).isIsAsc()) {
              return -1;
            } else {
              return 1;
            }
          }
        }
        return 0;
      };
    };
  }

  private Serializable[] getRowFromBlockValSets(int docId, Block[] blocks) throws Exception {

    final Serializable[] row = new Serializable[_dataSchema.size()];
    int j = 0;
    for (int i = 0; i < _dataSchema.size(); ++i) {
      if (_dataSchema.getColumnName(i).equals("_segmentId")) {
        row[i] = _indexSegment.getSegmentName().hashCode();
        continue;
      }
      if (_dataSchema.getColumnName(i).equals("_docId")) {
        row[i] = docId;
        continue;
      }

      if (blocks[j].getMetadata().isSingleValue()) {
        DictionaryReader dictionaryReader = blocks[j].getMetadata().getDictionary();
        BlockSingleValIterator bvIter = (BlockSingleValIterator) blocks[j].getBlockValueSet().iterator();
        bvIter.skipTo(docId);
        switch (_dataSchema.getColumnType(i)) {
          case INT:
            row[i] = ((IntDictionary) dictionaryReader).get(bvIter.nextIntVal());
            break;
          case FLOAT:
            row[i] = ((FloatDictionary) dictionaryReader).get(bvIter.nextIntVal());
            break;
          case LONG:
            row[i] = ((LongDictionary) dictionaryReader).get(bvIter.nextIntVal());
            break;
          case DOUBLE:
            row[i] = ((DoubleDictionary) dictionaryReader).get(bvIter.nextIntVal());
            break;
          case STRING:
            row[i] = ((StringDictionary) dictionaryReader).get(bvIter.nextIntVal());
            break;
          default:
            break;
        }
      } else {
        DictionaryReader dictionaryReader = blocks[j].getMetadata().getDictionary();
        BlockMultiValIterator bvIter = (BlockMultiValIterator) blocks[j].getBlockValueSet().iterator();
        bvIter.skipTo(docId);
        int[] dictIds = new int[blocks[j].getMetadata().maxNumberOfMultiValues()];
        int dictSize;
        switch (_dataSchema.getColumnType(i)) {
          case INT_ARRAY:
            dictSize = bvIter.nextIntVal(dictIds);
            int[] rawIntRow = new int[dictSize];
            for (int dictIdx = 0; dictIdx < dictSize; ++dictIdx) {
              rawIntRow[dictIdx] = ((IntDictionary) dictionaryReader).get(dictIds[dictIdx]);
            }
            row[i] = rawIntRow;
            break;
          case FLOAT_ARRAY:
            dictSize = bvIter.nextIntVal(dictIds);
            Float[] rawFloatRow = new Float[dictSize];
            for (int dictIdx = 0; dictIdx < dictSize; ++dictIdx) {
              rawFloatRow[dictIdx] = ((FloatDictionary) dictionaryReader).get(dictIds[dictIdx]);
            }
            row[i] = rawFloatRow;
            break;
          case LONG_ARRAY:
            dictSize = bvIter.nextIntVal(dictIds);
            Long[] rawLongRow = new Long[dictSize];
            for (int dictIdx = 0; dictIdx < dictSize; ++dictIdx) {
              rawLongRow[dictIdx] = ((LongDictionary) dictionaryReader).get(dictIds[dictIdx]);
            }
            row[i] = rawLongRow;
            break;
          case DOUBLE_ARRAY:
            dictSize = bvIter.nextIntVal(dictIds);
            Double[] rawDoubleRow = new Double[dictSize];
            for (int dictIdx = 0; dictIdx < dictSize; ++dictIdx) {
              rawDoubleRow[dictIdx] = ((DoubleDictionary) dictionaryReader).get(dictIds[dictIdx]);
            }
            row[i] = rawDoubleRow;
            break;
          case STRING_ARRAY:
            dictSize = bvIter.nextIntVal(dictIds);
            String[] rawStringRow = new String[dictSize];
            for (int dictIdx = 0; dictIdx < dictSize; ++dictIdx) {
              rawStringRow[dictIdx] = (((StringDictionary) dictionaryReader).get(dictIds[dictIdx]));
            }
            row[i] = rawStringRow;
            break;
          default:
            break;
        }
      }
      j++;
    }
    return row;
  }

  private JSONArray getJSonArrayFromRow(Serializable[] poll, DataSchema dataSchema) throws JSONException {

    final JSONArray jsonArray = new JSONArray();
    for (int i = 0; i < dataSchema.size(); ++i) {
      if (_selectionColumns.contains(dataSchema.getColumnName(i))) {
        if (dataSchema.getColumnType(i).isSingleValue()) {
          if (dataSchema.getColumnType(i) == DataType.STRING) {
            jsonArray.put(poll[i]);
          } else {
            jsonArray.put(DEFAULT_FORMAT_STRING_MAP.get(dataSchema.getColumnType(i)).format(poll[i]));
          }
        } else {
          // Multi-value;

          JSONArray stringJsonArray = new JSONArray();
          //          stringJsonArray.put(poll[i]);
          switch (dataSchema.getColumnType(i)) {
            case STRING_ARRAY:
              String[] stringValues = (String[]) poll[i];
              for (String s : stringValues) {
                stringJsonArray.put(s);
              }
              break;
            case INT_ARRAY:
              int[] intValues = (int[]) poll[i];
              for (int s : intValues) {
                stringJsonArray.put(DEFAULT_FORMAT_STRING_MAP.get(dataSchema.getColumnType(i)).format(s));
              }
              break;
            case FLOAT_ARRAY:
              float[] floatValues = (float[]) poll[i];
              for (float s : floatValues) {
                stringJsonArray.put(DEFAULT_FORMAT_STRING_MAP.get(dataSchema.getColumnType(i)).format(s));
              }
              break;
            case LONG_ARRAY:
              long[] longValues = (long[]) poll[i];
              for (long s : longValues) {
                stringJsonArray.put(DEFAULT_FORMAT_STRING_MAP.get(dataSchema.getColumnType(i)).format(s));
              }
              break;
            case DOUBLE_ARRAY:
              double[] doubleValues = (double[]) poll[i];
              for (double s : doubleValues) {
                stringJsonArray.put(DEFAULT_FORMAT_STRING_MAP.get(dataSchema.getColumnType(i)).format(s));
              }
              break;
            default:
              break;
          }
          jsonArray.put(stringJsonArray);

        }
      }
    }
    return jsonArray;
  }

  private static Serializable[] getRowFromDataTable(DataTable dt, int rowId) {
    final Serializable[] row = new Serializable[dt.getDataSchema().size()];
    for (int i = 0; i < dt.getDataSchema().size(); ++i) {
      if (dt.getDataSchema().getColumnType(i).isSingleValue()) {
        switch (dt.getDataSchema().getColumnType(i)) {
          case INT:
            row[i] = dt.getInt(rowId, i);
            break;
          case LONG:
            row[i] = dt.getLong(rowId, i);
            break;
          case DOUBLE:
            row[i] = dt.getDouble(rowId, i);
            break;
          case FLOAT:
            row[i] = dt.getFloat(rowId, i);
            break;
          case STRING:
            row[i] = dt.getString(rowId, i);
            break;
          case SHORT:
            row[i] = dt.getShort(rowId, i);
            break;
          case CHAR:
            row[i] = dt.getChar(rowId, i);
            break;
          case BYTE:
            row[i] = dt.getByte(rowId, i);
            break;
          default:
            row[i] = dt.getObject(rowId, i);
            break;
        }
      } else {
        switch (dt.getDataSchema().getColumnType(i)) {
          case INT_ARRAY:
            row[i] = dt.getIntArray(rowId, i);
            break;
          case LONG_ARRAY:
            row[i] = dt.getLongArray(rowId, i);
            break;
          case DOUBLE_ARRAY:
            row[i] = dt.getDoubleArray(rowId, i);
            break;
          case FLOAT_ARRAY:
            row[i] = dt.getFloatArray(rowId, i);
            break;
          case STRING_ARRAY:
            row[i] = dt.getStringArray(rowId, i);
            break;
          case CHAR_ARRAY:
            row[i] = dt.getCharArray(rowId, i);
            break;
          case BYTE_ARRAY:
            row[i] = dt.getByteArray(rowId, i);
            break;
          default:
            row[i] = dt.getObject(rowId, i);
            break;
        }
      }
    }
    return row;
  }

  public static DataTable getDataTableFromRowSet(PriorityQueue<Serializable[]> rowEventsSet1, DataSchema dataSchema)
      throws Exception {
    final DataTableBuilder dataTableBuilder = new DataTableBuilder(dataSchema);
    dataTableBuilder.open();
    final Iterator<Serializable[]> iterator = rowEventsSet1.iterator();
    while (iterator.hasNext()) {
      final Serializable[] row = iterator.next();
      // System.out.println(getRowStringFromSerializable(row, dataSchema));
      dataTableBuilder.startRow();
      for (int i = 0; i < dataSchema.size(); ++i) {
        if (dataSchema.getColumnType(i).isSingleValue()) {
          switch (dataSchema.getColumnType(i)) {
            case INT:
              dataTableBuilder.setColumn(i, ((Integer) row[i]).intValue());
              break;
            case LONG:
              dataTableBuilder.setColumn(i, ((Long) row[i]).longValue());
              break;
            case DOUBLE:
              dataTableBuilder.setColumn(i, ((Double) row[i]).doubleValue());
              break;
            case FLOAT:
              dataTableBuilder.setColumn(i, ((Float) row[i]).floatValue());
              break;
            case STRING:
              dataTableBuilder.setColumn(i, ((String) row[i]));
              break;
            default:
              dataTableBuilder.setColumn(i, row[i]);
              break;
          }
        } else {
          switch (dataSchema.getColumnType(i)) {
            case INT_ARRAY:
              dataTableBuilder.setColumn(i, (int[]) row[i]);
              break;
            case LONG_ARRAY:
              dataTableBuilder.setColumn(i, (long[]) row[i]);
              break;
            case DOUBLE_ARRAY:
              dataTableBuilder.setColumn(i, (double[]) row[i]);
              break;
            case FLOAT_ARRAY:
              dataTableBuilder.setColumn(i, (float[]) row[i]);
              break;
            case STRING_ARRAY:
              dataTableBuilder.setColumn(i, (String[]) row[i]);
              break;
            default:
              dataTableBuilder.setColumn(i, row[i]);
              break;
          }
        }
      }
      dataTableBuilder.finishRow();
    }
    dataTableBuilder.seal();
    return dataTableBuilder.build();
  }

  private static String getStringFromMultiValue(Serializable serializable, DataType dataType) {

    switch (dataType) {
      case INT_ARRAY:
        int[] intValues = (int[]) serializable;
        if ((intValues == null) || (intValues.length == 0)) {
          return "";
        }
        StringBuilder sbBuilder = new StringBuilder();
        sbBuilder.append(intValues[0]);
        for (int i = 1; i < intValues.length; ++i) {
          sbBuilder.append("\t\t");
          sbBuilder.append(intValues[i]);
        }
        return sbBuilder.toString();
      case LONG_ARRAY:
        long[] longValues = (long[]) serializable;
        if ((longValues == null) || (longValues.length == 0)) {
          return "";
        }
        sbBuilder = new StringBuilder();
        sbBuilder.append(longValues[0]);
        for (int i = 1; i < longValues.length; ++i) {
          sbBuilder.append("\t\t");
          sbBuilder.append(longValues[i]);
        }
        return sbBuilder.toString();
      case DOUBLE_ARRAY:
        double[] doubleValues = (double[]) serializable;
        if ((doubleValues == null) || (doubleValues.length == 0)) {
          return "";
        }
        sbBuilder = new StringBuilder();
        sbBuilder.append(doubleValues[0]);
        for (int i = 1; i < doubleValues.length; ++i) {
          sbBuilder.append("\t\t");
          sbBuilder.append(doubleValues[i]);
        }
        return sbBuilder.toString();
      case FLOAT_ARRAY:
        float[] floatValues = (float[]) serializable;
        if ((floatValues == null) || (floatValues.length == 0)) {
          return "";
        }
        sbBuilder = new StringBuilder();
        sbBuilder.append(floatValues[0]);
        for (int i = 1; i < floatValues.length; ++i) {
          sbBuilder.append("\t\t");
          sbBuilder.append(floatValues[i]);
        }
        return sbBuilder.toString();
      case STRING:
        String[] stringValues = (String[]) serializable;
        if ((stringValues == null) || (stringValues.length == 0)) {
          return "";
        }
        sbBuilder = new StringBuilder();
        sbBuilder.append(stringValues[0]);
        for (int i = 1; i < stringValues.length; ++i) {
          sbBuilder.append("\t\t");
          sbBuilder.append(stringValues[i]);
        }
        return sbBuilder.toString();
      default:
        break;
    }
    return "";

  }

  public static String getRowStringFromSerializable(Serializable[] row, DataSchema dataSchema) {
    String rowString = "";
    if (dataSchema.getColumnType(0).isSingleValue()) {
      if (dataSchema.getColumnType(0) == DataType.STRING) {
        rowString += (String) row[0];
      } else {
        rowString += row[0];
      }
    } else {
      rowString += "[ ";
      if (dataSchema.getColumnType(0) == DataType.STRING) {
        String[] values = (String[]) row[0];
        for (int i = 0; i < values.length; ++i) {
          rowString += values[i];
        }
      } else {
        Serializable[] values = (Serializable[]) row[0];
        for (int i = 0; i < values.length; ++i) {
          rowString += values[i];
        }
      }
      rowString += " ]";
    }
    for (int i = 1; i < row.length; ++i) {
      if (dataSchema.getColumnType(i).isSingleValue()) {
        if (dataSchema.getColumnType(i) == DataType.STRING) {
          rowString += " : " + (String) row[i];
        } else {
          rowString += " : " + row[i];
        }
      } else {

        rowString += " : [ ";
        switch (dataSchema.getColumnType(i)) {
          case STRING_ARRAY:
            String[] stringValues = (String[]) row[i];
            for (int j = 0; j < stringValues.length; ++j) {
              rowString += stringValues[j] + " ";
            }
            break;
          case INT_ARRAY:
            int[] intValues = (int[]) row[i];
            for (int j = 0; j < intValues.length; ++j) {
              rowString += intValues[j] + " ";
            }
            break;
          case FLOAT_ARRAY:
            float[] floatValues = (float[]) row[i];
            for (int j = 0; j < floatValues.length; ++j) {
              rowString += floatValues[j] + " ";
            }
            break;
          case LONG_ARRAY:
            long[] longValues = (long[]) row[i];
            for (int j = 0; j < longValues.length; ++j) {
              rowString += longValues[j] + " ";
            }
            break;
          case DOUBLE_ARRAY:
            double[] doubleValues = (double[]) row[i];
            for (int j = 0; j < doubleValues.length; ++j) {
              rowString += doubleValues[j] + " ";
            }
            break;

          default:
            break;
        }
        rowString += "]";

      }
    }
    return rowString;
  }
}
