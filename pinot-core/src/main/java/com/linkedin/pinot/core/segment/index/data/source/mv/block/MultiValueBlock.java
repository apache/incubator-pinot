package com.linkedin.pinot.core.segment.index.data.source.mv.block;

import org.roaringbitmap.buffer.ImmutableRoaringBitmap;

import com.linkedin.pinot.common.data.FieldSpec.DataType;
import com.linkedin.pinot.core.common.Block;
import com.linkedin.pinot.core.common.BlockDocIdIterator;
import com.linkedin.pinot.core.common.BlockDocIdSet;
import com.linkedin.pinot.core.common.BlockDocIdValueSet;
import com.linkedin.pinot.core.common.BlockId;
import com.linkedin.pinot.core.common.BlockMetadata;
import com.linkedin.pinot.core.common.BlockMultiValIterator;
import com.linkedin.pinot.core.common.BlockValIterator;
import com.linkedin.pinot.core.common.BlockValSet;
import com.linkedin.pinot.core.common.Constants;
import com.linkedin.pinot.core.common.Predicate;
import com.linkedin.pinot.core.segment.index.ColumnMetadata;
import com.linkedin.pinot.core.segment.index.readers.DictionaryReader;
import com.linkedin.pinot.core.segment.index.readers.FixedBitCompressedMVForwardIndexReader;


/**
 * @author Dhaval Patel<dpatel@linkedin.com>
 * Nov 15, 2014
 */

public class MultiValueBlock implements Block {

  private final FixedBitCompressedMVForwardIndexReader mVReader;
  private final ImmutableRoaringBitmap filteredDocIdsBitMap;
  private final BlockId id;
  private final DictionaryReader dictionary;
  private final ColumnMetadata columnMetadata;

  public MultiValueBlock(BlockId id, FixedBitCompressedMVForwardIndexReader singleValueReader, ImmutableRoaringBitmap filteredtBitmap,
      DictionaryReader dict, ColumnMetadata metadata) {
    filteredDocIdsBitMap = filteredtBitmap;
    mVReader = singleValueReader;
    this.id = id;
    dictionary = dict;
    columnMetadata = metadata;
  }

  public boolean hasDictionary() {
    return true;
  }

  public boolean hasInvertedIndex() {
    return columnMetadata.isHasInvertedIndex();
  }

  public boolean isSingleValued() {
    return columnMetadata.isSingleValue();
  }

  public int getMaxNumberOfMultiValues() {
    return columnMetadata.getMaxNumberOfMultiValues();
  }

  public DictionaryReader getDictionary() {
    return dictionary;
  }

  public DataType getDataType() {
    return columnMetadata.getDataType();
  }

  @Override
  public BlockId getId() {
    return id;
  }

  @Override
  public boolean applyPredicate(Predicate predicate) {
    return false;
  }

  @Override
  public BlockDocIdSet getBlockDocIdSet() {
    if (filteredDocIdsBitMap == null) {
      return null;
    }
    return new BlockDocIdSet() {
      @Override
      public BlockDocIdIterator iterator() {

        return new BlockDocIdIterator() {
          private final int[] docIds = filteredDocIdsBitMap.toArray();
          private int counter = 0;

          @Override
          public int skipTo(int targetDocId) {
            return -1;
          }

          @Override
          public int next() {
            if (counter >= docIds.length) {
              return Constants.EOF;
            }
            return docIds[counter++];
          }

          @Override
          public int currentDocId() {
            return docIds[counter];
          }
        };
      }

      @Override
      public Object getRaw() {
        return filteredDocIdsBitMap;
      }
    };
  }

  @Override
  public BlockValSet getBlockValueSet() {

    if (filteredDocIdsBitMap != null) {
      return null;
    }

    return new BlockValSet() {

      @Override
      public BlockValIterator iterator() {

        return new BlockMultiValIterator() {
          private int counter = 0;

          @Override
          public int nextIntVal(int[] intArray) {
            return mVReader.getIntArray(counter++, intArray);
          }

          @Override
          public boolean skipTo(int docId) {
            if (docId >= mVReader.length()) {
              return false;
            }
            counter = docId;
            return true;
          }

          @Override
          public int size() {
            return mVReader.length();
          }

          @Override
          public boolean reset() {
            counter = 0;
            return true;
          }

          @Override
          public boolean next() {
            return false;
          }

          @Override
          public boolean hasNext() {
            return (counter < mVReader.length());
          }

          @Override
          public DataType getValueType() {
            // TODO Auto-generated method stub
            return null;
          }

          @Override
          public int currentDocId() {
            return counter;
          }
        };
      }

      @Override
      public DataType getValueType() {
        // TODO Auto-generated method stub
        return null;
      }
    };
  }

  @Override
  public BlockDocIdValueSet getBlockDocIdValueSet() {
    return null;
  }

  @Override
  public BlockMetadata getMetadata() {
    return new BlockMetadata() {

      @Override
      public int maxNumberOfMultiValues() {
        return columnMetadata.getMaxNumberOfMultiValues();
      }

      @Override
      public boolean isSparse() {
        return false;
      }

      @Override
      public boolean isSorted() {
        return columnMetadata.isSorted();
      }

      @Override
      public boolean isSingleValue() {
        return columnMetadata.isSingleValue();
      }

      @Override
      public boolean hasInvertedIndex() {
        return columnMetadata.isHasInvertedIndex();
      }

      @Override
      public boolean hasDictionary() {
        return true;
      }

      @Override
      public int getStartDocId() {
        return 0;
      }

      @Override
      public int getSize() {
        return columnMetadata.getTotalDocs();
      }

      @Override
      public int getLength() {
        return columnMetadata.getTotalDocs();
      }

      @Override
      public int getEndDocId() {
        return columnMetadata.getTotalDocs() - 1;
      }

      @Override
      public DictionaryReader getDictionary() {
        return dictionary;
      }

      @Override
      public DataType getDataType() {
        return columnMetadata.getDataType();
      }
    };
  }
}
