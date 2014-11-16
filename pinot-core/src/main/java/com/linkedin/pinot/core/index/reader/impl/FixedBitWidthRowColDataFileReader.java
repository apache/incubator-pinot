package com.linkedin.pinot.core.index.reader.impl;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.BitSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linkedin.pinot.core.indexsegment.utils.GenericRowColumnDataFileReader;

/**
 *
 * Generic utility class to read data from file. The data file consists of rows
 * and columns. The number of columns are fixed. Each column can have either
 * single value or multiple values. There are two basic types of methods to read
 * the data <br/>
 * 1. <TYPE> getType(int row, int col) this is used for single value column <br/>
 * 2. int getTYPEArray(int row, int col, TYPE[] array). The caller has to create
 * and initialize the array. The implementation will fill up the array. The
 * caller is responsible to ensure that the array is big enough to fit all the
 * values. The return value tells the number of values.<br/>
 *
 * @author kgopalak
 *
 */
public class FixedBitWidthRowColDataFileReader {
  private static Logger logger = LoggerFactory
      .getLogger(GenericRowColumnDataFileReader.class);

  RandomAccessFile file;
  private final int rows;
  private final int cols;
  private final int[] colBitOffSets;
  private int rowSizeInBits;
  private ByteBuffer byteBuffer;
  private final int[] columnSizesInBits;

  /**
   *
   * @param file
   * @param rows
   * @param cols
   * @param columnSizes
   * @return
   * @throws IOException
   */
  public static FixedBitWidthRowColDataFileReader forHeap(File file,
      int rows, int cols, int[] columnSizesInBits) throws IOException {
    return new FixedBitWidthRowColDataFileReader(file, rows, cols,
        columnSizesInBits, false);
  }

  /**
   *
   * @param file
   * @param rows
   * @param cols
   * @param columnSizes
   * @return
   * @throws IOException
   */
  public static FixedBitWidthRowColDataFileReader forMmap(File file,
      int rows, int cols, int[] columnSizesInBits) throws IOException {
    return new FixedBitWidthRowColDataFileReader(file, rows, cols,
        columnSizesInBits, true);
  }

  /**
   *
   * @param fileName
   * @param rows
   * @param cols
   * @param columnSizes
   *            in bytes
   * @throws IOException
   */
  public FixedBitWidthRowColDataFileReader(File dataFile, int rows, int cols,
      int[] columnSizesInBits, boolean isMmap) throws IOException {
    this.rows = rows;
    this.cols = cols;
    this.columnSizesInBits = columnSizesInBits;
    rowSizeInBits = 0;
    colBitOffSets = new int[columnSizesInBits.length];
    for (int i = 0; i < columnSizesInBits.length; i++) {
      colBitOffSets[i] = rowSizeInBits;
      rowSizeInBits += columnSizesInBits[i];
    }
    file = new RandomAccessFile(dataFile, "rw");
    final long totalSize = rowSizeInBits * rows;
    if (isMmap) {
      byteBuffer = file.getChannel().map(FileChannel.MapMode.READ_ONLY,
          0, totalSize);
    } else {
      byteBuffer = ByteBuffer.allocate((int) totalSize);
      file.getChannel().read(byteBuffer);
    }
  }
  /**
   *
   * @param fileName
   * @param rows
   * @param cols
   * @param columnSizes
   *            in bytes
   * @throws IOException
   */
  public FixedBitWidthRowColDataFileReader(ByteBuffer buffer, int rows, int cols,
      int[] columnSizesInBits) throws IOException {
    this.rows = rows;
    this.cols = cols;
    this.columnSizesInBits = columnSizesInBits;
    rowSizeInBits = 0;
    colBitOffSets = new int[columnSizesInBits.length];
    for (int i = 0; i < columnSizesInBits.length; i++) {
      colBitOffSets[i] = rowSizeInBits;
      rowSizeInBits += columnSizesInBits[i];
    }
    byteBuffer = buffer;
  }
  public FixedBitWidthRowColDataFileReader(String fileName, int rows,
      int cols, int[] columnSizes) throws IOException {
    this(new File(fileName), rows, cols, columnSizes, true);
  }

  /**
   * Computes the bit offset where the actual column data can be read
   *
   * @param row
   * @param col
   * @return
   */
  private int computeBitOffset(int row, int col) {
    if (row >= rows || col >= cols) {
      final String message = String.format(
          "Input (%d,%d) is not with in expected range (%d,%d)", row,
          col, rows, cols);
      throw new IndexOutOfBoundsException(message);
    }
    final int offset = row * rowSizeInBits + colBitOffSets[col];
    return offset;
  }

  /**
   *
   * @param row
   * @param col
   * @return
   */
  public int getInt(int row, int col) {

    final int startBitOffset = computeBitOffset(row, col);
    final int endBitOffset = startBitOffset + columnSizesInBits[col] - 1;
    final int startByteOffset = startBitOffset / 8;
    final int startBitPos = startBitOffset % 8;
    final int endByteOffset = (endBitOffset) / 8;
    final int endBitPos = (endBitOffset) % 8;
    return readInt(byteBuffer, startByteOffset, startBitPos, endByteOffset,
        endBitPos);
  }

  /*
   * byte[] bytes = s.toByteArray(); then bytes.length == (s.length()+7)/8 and
   * s.get(n) == ((bytes[n/8] & (1<<(n%8))) != 0) for all n < 8 *
   * bytes.length.
   */
  public static int readInt(ByteBuffer byteBuffer, int startByteOffset,
      int startBitPos, int endByteOffset, int endBitPos) {
    final byte[] dest = new byte[endByteOffset - startByteOffset + 1];
    byteBuffer.position(startByteOffset);
    try {
      byteBuffer.get(dest);
    } catch (final Exception e) {
      System.err.println("startByteOffset" +startByteOffset );
      System.err.println("startBitPos" + startBitPos);
      System.err.println("endByteOffset" + endByteOffset);
      System.err.println("endBitPos" + endBitPos);
      throw new RuntimeException(e);
    }
    final BitSet set = BitSet.valueOf(dest);
    final int numBits = 8 * dest.length - (startBitPos) - (7 - endBitPos);
    final BitSet project = set.get(startBitPos, startBitPos + numBits);
    int ret = 0;
    for (int i = 0; i < numBits; i++) {
      if (project.get(i)) {
        ret |= (1 << i);
      }
    }
    return ret;
  }

  public int getNumberOfRows() {
    return rows;
  }

  public int getNumberOfCols() {
    return rows;
  }

  public int[] getColumnSizes() {
    return columnSizesInBits;
  }

  public void close() {
    try {
      file.close();
    } catch (final IOException e) {
      logger.error(e.getMessage());
    }
  }

  public boolean open() {
    return true;
  }

}
