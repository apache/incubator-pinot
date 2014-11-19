package com.linkedin.pinot.core.chunk.index.data.source.sv.block;

import org.roaringbitmap.buffer.ImmutableRoaringBitmap;

import com.linkedin.pinot.common.data.FieldSpec.DataType;
import com.linkedin.pinot.core.chunk.index.readers.FixedBitCompressedSVForwardIndexReader;
import com.linkedin.pinot.core.common.Block;
import com.linkedin.pinot.core.common.BlockDocIdIterator;
import com.linkedin.pinot.core.common.BlockDocIdSet;
import com.linkedin.pinot.core.common.BlockDocIdValueSet;
import com.linkedin.pinot.core.common.BlockId;
import com.linkedin.pinot.core.common.BlockMetadata;
import com.linkedin.pinot.core.common.BlockSingleValIterator;
import com.linkedin.pinot.core.common.BlockValIterator;
import com.linkedin.pinot.core.common.BlockValSet;
import com.linkedin.pinot.core.common.Constants;
import com.linkedin.pinot.core.common.Predicate;


/**
 * @author Dhaval Patel<dpatel@linkedin.com>
 * Nov 15, 2014
 */

public class SingleValueBlock implements Block {

  private final FixedBitCompressedSVForwardIndexReader sVReader;
  private final ImmutableRoaringBitmap filteredDocIdsBitMap;
  private final BlockId id;

  public SingleValueBlock(BlockId id, FixedBitCompressedSVForwardIndexReader singleValueReader, ImmutableRoaringBitmap filteredtBitmap) {
    filteredDocIdsBitMap = filteredtBitmap;
    sVReader = singleValueReader;
    this.id = id;
  }

  public SingleValueBlock(BlockId id, FixedBitCompressedSVForwardIndexReader singleValueReader) {
    filteredDocIdsBitMap = null;
    sVReader = singleValueReader;
    this.id = id;
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
    return new BlockDocIdSet() {

      @Override
      public BlockDocIdIterator iterator() {
        return new BlockDocIdIterator() {
          final int[] docIds = filteredDocIdsBitMap.toArray();
          int counter = 0;

          @Override
          public int skipTo(int targetDocId) {
            if (targetDocId < docIds.length) {
              counter = targetDocId;
              return counter;
            }
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
    };
  }

  private BlockValSet returnBlockValueSetBackedByFwdIndex() {
    return new BlockValSet() {
      @Override
      public BlockValIterator iterator() {


        return new BlockSingleValIterator() {
          private int counter = 0;

          @Override
          public boolean skipTo(int docId) {
            if (docId >= sVReader.getLength()) {
              return false;
            }

            counter = docId;

            return true;
          }

          @Override
          public int size() {
            return sVReader.getLength();
          }

          @Override
          public int nextIntVal(){
            if (counter >= sVReader.getLength()) {
              return Constants.EOF;
            }
            return sVReader.getInt(counter++);
          }

          @Override
          public boolean reset() {
            counter = 0;
            return true;
          }

          @Override
          public boolean next() {
            // TODO Auto-generated method stub
            return false;
          }

          @Override
          public boolean hasNext() {
            return (counter < sVReader.getLength());
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
  public BlockValSet getBlockValueSet() {
    if (filteredDocIdsBitMap == null) {
      return returnBlockValueSetBackedByFwdIndex();
    }

    return new BlockValSet() {

      @Override
      public BlockValIterator iterator() {
        return new BlockSingleValIterator() {
          private final int[] docIds = filteredDocIdsBitMap.toArray();
          private int counter = 0;

          @Override
          public boolean skipTo(int docId) {
            if (docId >= docIds.length) {
              return false;
            }

            counter = docId;

            return true;
          }

          @Override
          public int size() {
            return docIds.length;
          }

          @Override
          public int nextIntVal(){
            if (counter >= docIds.length) {
              return Constants.EOF;
            }
            return sVReader.getInt(docIds[counter++]);
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
            return (counter < docIds.length);
          }

          @Override
          public DataType getValueType() {
            return null;
          }

          @Override
          public int currentDocId() {
            return docIds[counter];
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
    return null;
  }
}
