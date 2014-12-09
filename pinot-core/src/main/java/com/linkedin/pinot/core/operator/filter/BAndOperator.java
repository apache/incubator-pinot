package com.linkedin.pinot.core.operator.filter;

import java.util.List;

import org.roaringbitmap.buffer.ImmutableRoaringBitmap;
import org.roaringbitmap.buffer.MutableRoaringBitmap;

import com.linkedin.pinot.core.block.IntBlockDocIdSet;
import com.linkedin.pinot.core.common.Block;
import com.linkedin.pinot.core.common.BlockDocIdSet;
import com.linkedin.pinot.core.common.BlockDocIdValueSet;
import com.linkedin.pinot.core.common.BlockId;
import com.linkedin.pinot.core.common.BlockMetadata;
import com.linkedin.pinot.core.common.BlockValSet;
import com.linkedin.pinot.core.common.Operator;
import com.linkedin.pinot.core.common.Predicate;


public class BAndOperator implements Operator {

  private final Operator[] operators;

  public BAndOperator(Operator left, Operator right) {
    operators = new Operator[] { left, right };
  }

  public BAndOperator(Operator... operators) {
    this.operators = operators;
  }

  public BAndOperator(List<Operator> operators) {
    this.operators = new Operator[operators.size()];
    operators.toArray(this.operators);
  }

  @Override
  public boolean open() {
    for (final Operator operator : operators) {
      operator.open();
    }
    return true;
  }

  @Override
  public boolean close() {
    for (final Operator operator : operators) {
      operator.close();
    }
    return true;
  }

  @Override
  public Block nextBlock() {
    final Block[] blocks = new Block[operators.length];
    int i = 0;
    boolean isAnyBlockEmpty = false;
    for (final Operator operator : operators) {
      final Block nextBlock = operator.nextBlock();
      if (nextBlock == null) {
        isAnyBlockEmpty = true;
      }
      blocks[i++] = nextBlock;
    }
    if (isAnyBlockEmpty) {
      return null;
    }
    return new AndBlock(blocks);
  }

  @Override
  public Block nextBlock(BlockId BlockId) {

    return null;
  }

}

class AndBlock implements Block {

  private final Block[] blocks;

  int[] intersection;

  public AndBlock(Block[] blocks) {
    this.blocks = blocks;
  }

  @Override
  public boolean applyPredicate(Predicate predicate) {
    return false;
  }

  @Override
  public BlockId getId() {
    return null;
  }

  @Override
  public BlockMetadata getMetadata() {
    return null;
  }

  @Override
  public BlockValSet getBlockValueSet() {
    return null;
  }

  @Override
  public BlockDocIdValueSet getBlockDocIdValueSet() {

    return null;
  }

  @Override
  public BlockDocIdSet getBlockDocIdSet() {
    final MutableRoaringBitmap bit =
        ((ImmutableRoaringBitmap) blocks[0].getBlockDocIdSet().getRaw()).toMutableRoaringBitmap();
    for (int srcId = 1; srcId < blocks.length; srcId++) {
      final MutableRoaringBitmap bitToAndWith =
          ((ImmutableRoaringBitmap) blocks[srcId].getBlockDocIdSet().getRaw()).toMutableRoaringBitmap();
      bit.and(bitToAndWith);
    }
    return new IntBlockDocIdSet(bit);
  }
}
