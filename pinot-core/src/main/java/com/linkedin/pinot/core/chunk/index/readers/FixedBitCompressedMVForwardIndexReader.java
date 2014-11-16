package com.linkedin.pinot.core.chunk.index.readers;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linkedin.pinot.core.index.reader.DataFileMetadata;
import com.linkedin.pinot.core.index.reader.SingleColumnMultiValueReader;
import com.linkedin.pinot.core.index.reader.impl.FixedBitWidthRowColDataFileReader;
import com.linkedin.pinot.core.index.reader.impl.FixedByteWidthRowColDataFileReader;
import com.linkedin.pinot.core.indexsegment.utils.GenericRowColumnDataFileReader;

/**
 * Reads a column where each row can have multiple values. Lets say we want to
 * represent (1,2,4) <br/>
 * (3,4) <br/>
 * (5,6)<br/>
 * Overall there are (3+2+2) = 7 values that we need to store. We will store
 * them sequentially but by storing sequentially we dont know the boundary
 * between each row. We store additional header section that tells the offset
 * and length for each row.
 * 
 * The file can be represented as two sections <br/>
 * 
 * <pre>
 * {@code
 * Header <br/>
 * (rowOffset, number of values) 
 * 0 3 
 * 3 2 
 * 5 2 
 * Data 
 * 1
 * 2
 * 4
 * 3
 * 4
 * 5
 * 6
 * }
 * </pre>
 * 
 * so if want to read the values for docId=0. We first read the header values
 * (offset and length) at offset 0, in this case we get 6,3. Now we read, 6
 * integer
 * 
 * @author kgopalak
 * 
 */
public class FixedBitCompressedMVForwardIndexReader implements
		SingleColumnMultiValueReader {
	private static Logger logger = LoggerFactory
			.getLogger(FixedBitCompressedMVForwardIndexReader.class);
	private static int SIZE_OF_INT = 4;
	private static int NUM_COLS_IN_HEADER = 2;
	private RandomAccessFile raf;
	private FixedByteWidthRowColDataFileReader headerSectionReader;
	private FixedBitWidthRowColDataFileReader dataSectionReader;

	/**
	 * 
	 * @param file
	 * @param numDocs
	 * @param columnSizeInBytes
	 * @param isHeap
	 */
	public FixedBitCompressedMVForwardIndexReader(File file, int numDocs,
			int columnSizeInBits, boolean isMMap) throws Exception {
		// compute the header size= numDocs * size of int * 2
		int headerSize = numDocs * SIZE_OF_INT * NUM_COLS_IN_HEADER;
		ByteBuffer byteBuffer;
		raf = new RandomAccessFile(file, "rw");
		if (isMMap) {
			byteBuffer = raf.getChannel().map(FileChannel.MapMode.READ_ONLY, 0,
					headerSize);

		} else {
			byteBuffer = ByteBuffer.allocate((int) headerSize);
			raf.getChannel().read(byteBuffer);

		}
		headerSectionReader = new FixedByteWidthRowColDataFileReader(
				byteBuffer, numDocs, NUM_COLS_IN_HEADER, new int[] {
						SIZE_OF_INT, SIZE_OF_INT });

		// to calculate total number of values across all docs, we need to read
		// the last two entries in the header (start index, length)
		int startIndex = headerSectionReader.getInt(numDocs - 1, 0);
		int length = headerSectionReader.getInt(numDocs - 1, 1);
		int totalNumValues = startIndex + length;
		int dataSize = totalNumValues * columnSizeInBits;
		ByteBuffer dataBuffer;

		if (isMMap) {
			dataBuffer = raf.getChannel().map(FileChannel.MapMode.READ_ONLY,
					headerSize, dataSize);
		} else {
			dataBuffer = ByteBuffer.allocate((int) dataSize);
			raf.getChannel().read(dataBuffer, headerSize);
		}
		dataSectionReader = new FixedBitWidthRowColDataFileReader(dataBuffer,
				totalNumValues, 1, new int[] { columnSizeInBits });

	}

	@Override
	public boolean open() {
		return true;
	}

	@Override
	public DataFileMetadata getMetadata() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean close() {
		try {
			if (raf != null) {
				raf.close();
			}
		} catch (IOException e) {
			logger.error(e.getMessage());
			return false;
		}
		return true;
	}

	@Override
	public int getCharArray(int row, char[] charArray) {
		throw new UnsupportedOperationException(
				"Only int data types are allowed in multivalue fixed bit format");
	}

	@Override
	public int getShortArray(int row, short[] shortArray) {
		throw new UnsupportedOperationException(
				"Only int data types are allowed in multivalue fixed bit format");

	}

	@Override
	public int getIntArray(int row, int[] intArray) {
		int startIndex = headerSectionReader.getInt(row, 0);
		int length = headerSectionReader.getInt(row, 1);
		for (int i = 0; i < length; i++) {
			intArray[i] = dataSectionReader.getInt(startIndex + i, 0);
		}
		return length;
	}

	@Override
	public int getLongArray(int row, long[] longArray) {
		throw new UnsupportedOperationException(
				"Only int data types are allowed in multivalue fixed bit format");

	}

	@Override
	public int getFloatArray(int row, float[] floatArray) {
		throw new UnsupportedOperationException(
				"Only int data types are allowed in multivalue fixed bit format");

	}

	@Override
	public int getDoubleArray(int row, double[] doubleArray) {
		throw new UnsupportedOperationException(
				"Only int data types are allowed in multivalue fixed bit format");

	}

	@Override
	public int getStringArray(int row, String[] stringArray) {
		throw new UnsupportedOperationException(
				"Only int data types are allowed in multivalue fixed bit format");

	}

	@Override
	public int getBytesArray(int row, byte[][] bytesArray) {
		throw new UnsupportedOperationException(
				"Only int data types are allowed in multivalue fixed bit format");

	}

}
