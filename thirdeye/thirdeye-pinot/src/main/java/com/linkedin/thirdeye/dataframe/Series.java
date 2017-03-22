package com.linkedin.thirdeye.dataframe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang.ArrayUtils;


/**
 * Container for a one-dimensional series of elements with a common primitive type.
 * Supports transparent conversion between different primitive types and implements
 * common logic for element management, transformation and aggregation.
 *
 * Series are designed to be immutable (albeit with some limitations due to Java's
 * primitive array model). Operations return new Series instances without modifying
 * the underlying data structures.
 */
public abstract class Series {
  public static final String COLUMN_KEY = "key";
  public static final String COLUMN_VALUE = "value";

  public enum SeriesType {
    DOUBLE,
    LONG,
    STRING,
    BOOLEAN
  }

  enum JoinType {
    INNER,
    OUTER,
    LEFT,
    RIGHT
  }

//  @FunctionalInterface
  public interface DoubleConditional {
    boolean apply(double value);
  }

//  @FunctionalInterface
  public interface LongConditional {
    boolean apply(long value);
  }

//  @FunctionalInterface
  public interface StringConditional {
    boolean apply(String value);
  }

//  @FunctionalInterface
  public interface DoubleFunction {
    double apply(double... values);
  }

//  @FunctionalInterface
  public interface LongFunction {
    long apply(long... values);
  }

//  @FunctionalInterface
  public interface StringFunction {
    String apply(String... values);
  }

//  @FunctionalInterface
  public interface BooleanFunction {
    byte apply(byte... values);
  }

  /**
   * Helper container for references generated by grouping
   */
  public static final class Bucket {
    final int[] fromIndex;

    Bucket(int[] fromIndex) {
      this.fromIndex = fromIndex;
    }

    public int size() {
      return this.fromIndex.length;
    }
  }

  /**
   * Grouping container referencing a single series. Holds group keys and the indices of group
   * elements in the source series. Enables aggregation with custom user functions.
   */
  public static final class SeriesGrouping {
    final Series keys;
    final Series source;
    final List<Bucket> buckets;

    SeriesGrouping(Series keys, Series source, List<Bucket> buckets) {
      if(keys.size() != buckets.size())
        throw new IllegalArgumentException("key series and bucket count must be equal");
      this.keys = keys;
      this.source = source;
      this.buckets = buckets;
    }

    SeriesGrouping(Series source) {
      this.keys = LongSeries.buildFrom();
      this.source = source;
      this.buckets = Collections.emptyList();
    }

    /**
     * Applies index-based groups to a different series. Used by DataFrame for grouping across
     * multiple series.
     *
     * @param s other series
     * @return SeriesGrouping with different size
     */
    SeriesGrouping applyTo(Series s) {
      return new SeriesGrouping(this.keys, s, this.buckets);
    }

    /**
     * Returns the number of groups
     *
     * @return group count
     */
    public int size() {
      return this.keys.size();
    }

    /**
     * Returns the keys of each group in the container as series.
     *
     * @return key series
     */
    public Series keys() {
      return this.keys;
    }

    /**
     * Returns the source series this grouping applies to.
     *
     * @return source series
     */
    public Series source() {
      return this.source;
    }

    /**
     * Returns {@code true} if the grouping container does not hold any groups.
     *
     * @return {@code true} is empty, {@code false} otherwise.
     */
    public boolean isEmpty() {
      return this.keys.isEmpty();
    }

    /**
     * Applies {@code function} as aggregation function to all values per group and
     * returns the result as a new series with the number of elements equal to the size
     * of the key series.
     * If the series' native types do not match the required input type of {@code function},
     * the series are converted transparently. The native type of the returned series is
     * determined by {@code function}'s output type.
     *
     * @param function aggregation function to apply to each grouped series
     * @return grouped aggregation series
     */
    public DataFrame aggregate(DoubleFunction function) {
      DoubleSeries.Builder builder = DoubleSeries.builder();
      for(Bucket b : this.buckets)
        builder.add(this.source.project(b.fromIndex).aggregate(function));
      return makeAggregate(this.keys, builder.build());
    }

    /**
     * Applies {@code function} as aggregation function to all values per group and
     * returns the result as a new series with the number of elements equal to the size
     * of the key series.
     * If the series' native types do not match the required input type of {@code function},
     * the series are converted transparently. The native type of the returned series is
     * determined by {@code function}'s output type.
     *
     * @param function aggregation function to apply to each grouped series
     * @return grouped aggregation series
     */
    public DataFrame aggregate(LongFunction function) {
      LongSeries.Builder builder = LongSeries.builder();
      for(Bucket b : this.buckets)
        builder.add(this.source.project(b.fromIndex).aggregate(function));
      return makeAggregate(this.keys, builder.build());
    }

    /**
     * Applies {@code function} as aggregation function to all values per group and
     * returns the result as a new series with the number of elements equal to the size
     * of the key series.
     * If the series' native types do not match the required input type of {@code function},
     * the series are converted transparently. The native type of the returned series is
     * determined by {@code function}'s output type.
     *
     * @param function aggregation function to apply to each grouped series
     * @return grouped aggregation series
     */
    public DataFrame aggregate(StringFunction function) {
      StringSeries.Builder builder = StringSeries.builder();
      for(Bucket b : this.buckets)
        builder.add(this.source.project(b.fromIndex).aggregate(function));
      return makeAggregate(this.keys, builder.build());
    }

    /**
     * Applies {@code function} as aggregation function to all values per group and
     * returns the result as a new series with the number of elements equal to the size
     * of the key series.
     * If the series' native types do not match the required input type of {@code function},
     * the series are converted transparently. The native type of the returned series is
     * determined by {@code function}'s output type.
     *
     * @param function aggregation function to apply to each grouped series
     * @return grouped aggregation series
     */
    public DataFrame aggregate(BooleanFunction function) {
      BooleanSeries.Builder builder = BooleanSeries.builder();
      for(Bucket b : this.buckets)
        builder.add(this.source.project(b.fromIndex).aggregate(function));
      return makeAggregate(this.keys, builder.build());
    }

    static DataFrame makeAggregate(Series keys, Series values) {
      DataFrame df = new DataFrame();
      df.addSeries(COLUMN_KEY, keys);
      df.addSeries(COLUMN_VALUE, values);
      return df;
    }
  }

  /**
   * Helper container for index-pairs generated by join logic
   */
  static final class JoinPair {
    final int left;
    final int right;

    public JoinPair(int left, int right) {
      this.left = left;
      this.right = right;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      JoinPair joinPair = (JoinPair) o;

      return (left == joinPair.left) && (right == joinPair.right);
    }

    @Override
    public int hashCode() {
      int result = left;
      result = 31 * result + right;
      return result;
    }
  }

  /* *************************************************************************
   * Public abstract interface
   * *************************************************************************/

  /**
   * Returns the number of elements contained in the series.
   *
   * <b>NOTE:</b> {@code null} values count as elements.
   *
   * @return series size
   */
  public abstract int size();

  /**
   * Returns a the series as DoubleSeries. The underlying series is converted
   * transparently if the series' native type is different.
   *
   * @return DoubleSeries equivalent
   */
  public abstract DoubleSeries getDoubles();

  /**
   * Returns the series as LongSeries. The underlying series is converted
   * transparently if the series' native type is different.
   *
   * @return LongSeries equivalent
   */
  public abstract LongSeries getLongs();

  /**
   * Returns the series as BooleanSeries. The underlying series is converted
   * transparently if the series' native type is different.
   *
   * @return BooleanSeries equivalent
   */
  public abstract BooleanSeries getBooleans();

  /**
   * Returns the series as StringSeries. The underlying series is converted
   * transparently if the series' native type is different.
   *
   * @return StringSeries equivalent
   */
  public abstract StringSeries getStrings();

  /**
   * Returns the series' native type.
   *
   * @return series type
   */
  public abstract SeriesType type();

  /**
   * Slices the series from index {@code from} (inclusive) to index {@code to}
   * (exclusive) and returns the result as a series of the same native type.
   *
   * @param from start index (inclusive), must be >= 0
   * @param to end index (exclusive), must be <= size
   * @return sliced series copy
   */
  public abstract Series slice(int from, int to);

  /**
   * Returns as copy of the series with the same native type.
   *
   * @return series copy
   */
  public abstract Series copy();

  /**
   * Returns a copy of the series with all values' indices
   * shifted by {@code offset} positions while
   * leaving the series size unchanged. Values shifted outside to upper (or lower)
   * bounds of the series are dropped. Vacated positions are padded with {@code null}.
   *
   * <b>NOTE:</b> for each value, newIndex = oldIndex + offset
   *
   * @param offset offset to shift values by. Can be positive or negative.
   * @return shifted series copy
   */
  public abstract Series shift(int offset);

  /**
   * Returns {@code true} if the series contains at least one {@code null}. Otherwise
   * returns {@code false}.
   *
   * @return {@code true} if empty, {@code false} otherwise
   */
  public abstract boolean hasNull();

  /**
   * Returns a copy of the series with values from {@code other}
   * appended at the end. If {@code other} has a different type it is converted transparently.
   *
   * <b>NOTE:</b> newSize = oldSize + otherSize
   *
   * @param other other series to append at the end
   * @return concatenated series
   */
  public abstract Series append(Series other);

  /* *************************************************************************
   * Internal abstract interface
   * *************************************************************************/

  /**
   * Returns projection of the series.
   *
   * <b>NOTE:</b> fromIndex <= -1 is filled with {@code null}.
   * <b>NOTE:</b> array with length 0 returns empty series.
   *
   * @param fromIndex array with indices to project from (must be <= series size)
   * @return series projection
   */
  abstract Series project(int[] fromIndex);

  /**
   * Returns an array of indices with a size equal to the series size, such that the values
   * references by the indices are sorted in ascending order.
   *
   * <b>NOTE:</b> output can be used directly by {@code project()} to create a sorted copy of the series.
   *
   * @return indices of sorted values
   */
  abstract int[] sortedIndex();

  /**
   * Returns an array of indices with size less than or equal to the series size, such that
   * each index references a null value in the original series.
   *
   * @return indices of null values
   */
  abstract int[] nullIndex();

  /**
   * Compares values across two series with the {@code same} native type based on index. The
   * semantics follow {@code Long.compare()} (and similar) in Java.
   *
   * @param that other series with same native type (may reference itself)
   * @param indexThis index in this series
   * @param indexThat index in the other series
   * @return 0 if the referenced values are equal, -1 if {@code this} is less than {@code that}, 1 otherwise
   */
  abstract int compare(Series that, int indexThis, int indexThat);

  /* *************************************************************************
   * Public interface
   * *************************************************************************/

  /**
   * Returns {@code true} is there are no values in the series. Otherwise returns {@code false}.
   *
   * <b>NOTE:</b> {@code null} values count as elements.
   *
   * @return {@code true} if empty, {@code false} otherwise
   */
  public boolean isEmpty() {
    return this.size() <= 0;
  }

  /**
   * Returns a copy of the series containing at maximum the first {@code n} elements of the series.
   * If {@code n} is larger than the series size, the entire series is returned. Additional values
   * to make up the difference between {@code n} and the size are not padded.
   *
   * @param n number of elements
   * @return series copy with at most the first {@code n} elements
   */
  public Series head(int n) {
    return this.slice(0, Math.min(n, this.size()));
  }

  /**
   * Returns a copy of the series containing at maximum the last {@code n} elements of the series.
   * If {@code n} is larger than the series size, the entire series is returned. Additional values
   * to make up the difference between {@code n} and the size are not padded.
   *
   * @param n number of elements
   * @return series copy with at most the last {@code n} elements
   */
  public Series tail(int n) {
    int len = this.size();
    return this.slice(len - Math.min(n, len), len);
  }

  /**
   * Returns a copy of the series omitting any elements before index {@code n}.
   * If {@code n} is {@code 0}, the entire series is returned. If {@code n} is greater than
   * the series size, an empty series is returned.
   *
   * @param from start index of copy (inclusive)
   * @return series copy with elements from index {@code from}.
   */
  public Series sliceFrom(int from) {
    return this.slice(Math.max(from, 0), this.size());
  }

  /**
   * Returns a copy of the series omitting any elements equal to or after index {@code n}.
   * If {@code n} is equal or greater than the series size, the entire series is returned.
   * If {@code n} is {@code 0}, an empty series is returned.
   *
   * @param to end index of copy (exclusive)
   * @return series copy with elements before from index {@code from}.
   */
  public Series sliceTo(int to) {
    return this.slice(0, Math.min(to, this.size()));
  }

  /**
   * Returns a copy of the series with values ordered
   * in ascending order.
   *
   * <b>NOTE:</b> BooleanSeries interprets {@code false} as smaller than {@code true}.
   *
   * @return sorted series copy
   */
  public Series sorted() {
    return this.project(this.sortedIndex());
  }

  /**
   * Returns a copy of the series with elements in reverse order from the original series.
   *
   * @return reversed series
   */
  public Series reverse() {
    int[] fromIndex = new int[this.size()];
    for (int i = 0; i < fromIndex.length; i++) {
      fromIndex[i] = fromIndex.length - i - 1;
    }
    return this.project(fromIndex);
  }

  /**
   * Returns a copy of the series with each distinct value of the
   * source series appearing exactly once. The values are further sorted in ascending order.
   *
   * @return sorted series copy with distinct unique values
   */
  public Series unique() {
    if(this.size() <= 1)
      return this;

    Series sorted = this.sorted();
    List<Integer> indices = new ArrayList<>();

    indices.add(0);
    for(int i=1; i<this.size(); i++) {
      if(sorted.compare(sorted, i-1, i) != 0)
        indices.add(i);
    }

    int[] fromIndex = ArrayUtils.toPrimitive(indices.toArray(new Integer[indices.size()]));
    return sorted.project(fromIndex);
  }

  /**
   * Applies {@code conditional} to the series row by row and returns the results as a BooleanSeries.
   * If the series' native type does not match the required input type of {@code conditional},
   * the series is converted transparently.
   *
   * @param conditional condition to apply to each element in the series
   * @return BooleanSeries with evaluation results
   */
  public BooleanSeries map(DoubleConditional conditional) {
    return this.getDoubles().map(conditional);
  }

  /**
   * Applies a {@code conditional} to the series row by row and returns the results as a BooleanSeries.
   * If the series' native type does not match the required input type of {@code conditional},
   * the series is converted transparently.
   *
   * @param conditional condition to apply to each element in the series
   * @return BooleanSeries with evaluation results
   */
  public BooleanSeries map(LongConditional conditional) {
    return this.getLongs().map(conditional);
  }

  /**
   * Applies a {@code conditional} to the series row by row and returns the results as a BooleanSeries.
   * If the series' native type does not match the required input type of {@code conditional},
   * the series is converted transparently.
   *
   * @param conditional condition to apply to each element in the series
   * @return BooleanSeries with evaluation results
   */
  public BooleanSeries map(StringConditional conditional) {
    return this.getStrings().map(conditional);
  }

  /**
   * Applies {@code function} to the series row by row and returns the results as a new series.
   * If the series' native type does not match the required input type of {@code function},
   * the series is converted transparently. The native type of the returned series is
   * determined by {@code function}'s output type.
   *
   * @param function function to apply to each element in the series
   * @return series with evaluation results
   */
  public DoubleSeries map(DoubleFunction function) {
    return this.getDoubles().map(function);
  }

  /**
   * Applies {@code function} to the series row by row and returns the results as a new series.
   * If the series' native type does not match the required input type of {@code function},
   * the series is converted transparently. The native type of the returned series is
   * determined by {@code function}'s output type.
   *
   * @param function function to apply to each element in the series
   * @return series with evaluation results
   */
  public LongSeries map(LongFunction function) {
    return this.getLongs().map(function);
  }

  /**
   * Applies {@code function} to the series row by row and returns the results as a new series.
   * If the series' native type does not match the required input type of {@code function},
   * the series is converted transparently. The native type of the returned series is
   * determined by {@code function}'s output type.
   *
   * @param function function to apply to each element in the series
   * @return series with evaluation results
   */
  public StringSeries map(StringFunction function) {
    return this.getStrings().map(function);
  }

  /**
   * Applies {@code function} to the series row by row and returns the results as a new series.
   * If the series' native type does not match the required input type of {@code function},
   * the series is converted transparently. The native type of the returned series is
   * determined by {@code function}'s output type.
   *
   * @param function function to apply to each element in the series
   * @return series with evaluation results
   */
  public BooleanSeries map(BooleanFunction function) {
    return this.getBooleans().map(function);
  }

  /**
   * Applies {@code function} as aggregation function to all values in the series at once and
   * returns the result as a new series with a single element.
   * If the series' native type does not match the required input type of {@code function},
   * the series is converted transparently. The native type of the returned series is
   * determined by {@code function}'s output type.
   *
   * @param function aggregation function to apply to the series
   * @return single element series
   */
  public DoubleSeries aggregate(DoubleFunction function) {
    return this.getDoubles().aggregate(function);
  }

  /**
   * Applies {@code function} as aggregation function to all values in the series at once and
   * returns the result as a new series with a single element.
   * If the series' native type does not match the required input type of {@code function},
   * the series is converted transparently. The native type of the returned series is
   * determined by {@code function}'s output type.
   *
   * @param function aggregation function to apply to the series
   * @return single element series
   */
  public LongSeries aggregate(LongFunction function) {
    return this.getLongs().aggregate(function);
  }

  /**
   * Applies {@code function} as aggregation function to all values in the series at once and
   * returns the result as a new series with a single element.
   * If the series' native type does not match the required input type of {@code function},
   * the series is converted transparently. The native type of the returned series is
   * determined by {@code function}'s output type.
   *
   * @param function aggregation function to apply to the series
   * @return single element series
   */
  public StringSeries aggregate(StringFunction function) {
    return this.getStrings().aggregate(function);
  }

  /**
   * Applies {@code function} as aggregation function to all values in the series at once and
   * returns the result as a new series with a single element.
   * If the series' native type does not match the required input type of {@code function},
   * the series is converted transparently. The native type of the returned series is
   * determined by {@code function}'s output type.
   *
   * @param function aggregation function to apply to the series
   * @return single element series
   */
  public BooleanSeries aggregate(BooleanFunction function) {
    return this.getBooleans().aggregate(function);
  }

  /**
   * Returns a copy of the series with a native type corresponding to {@code type}.
   *
   * @param type series copy native type
   * @return series copy with native type {@code type}
   */
  public Series toType(SeriesType type) {
    return DataFrame.asType(this, type);
  }

  /**
   * Returns a SeriesGrouping based on value. Elements are grouped into separate buckets for each
   * distinct value in the series.
   *
   * <b>NOTE:</b> the resulting keys are equivalent to calling {@code unique()} on the series.
   *
   * @return grouping by value
   */
  public SeriesGrouping groupByValue() {
    if(this.isEmpty())
      return new SeriesGrouping(this);

    List<Bucket> buckets = new ArrayList<>();
    int[] sref = this.sortedIndex();

    int bucketOffset = 0;
    for(int i=1; i<sref.length; i++) {
      if(this.compare(this, sref[i-1], sref[i]) != 0) {
        int[] fromIndex = Arrays.copyOfRange(sref, bucketOffset, i);
        buckets.add(new Bucket(fromIndex));
        bucketOffset = i;
      }
    }

    int[] fromIndex = Arrays.copyOfRange(sref, bucketOffset, sref.length);
    buckets.add(new Bucket(fromIndex));

    return new SeriesGrouping(this.unique(), this, buckets);
  }

  /**
   * Returns a SeriesGrouping based on element count per buckets. Elements are grouped into buckets
   * based on a greedy algorithm with fixed bucket size. The size of all buckets (except for the
   * last) is guaranteed to be equal to {@code bucketSize}.
   *
   * <b>NOTE:</b> the series is not sorted before grouping.
   *
   * @param bucketSize maximum number of elements per bucket
   * @return grouping by element count
   */
  public SeriesGrouping groupByCount(int bucketSize) {
    if(bucketSize <= 0)
      throw new IllegalArgumentException("bucketSize must be greater than 0");
    if(this.isEmpty())
      return new SeriesGrouping(this);

    bucketSize = Math.min(bucketSize, this.size());

    int numBuckets = (this.size() - 1) / bucketSize + 1;
    long[] keys = new long[numBuckets];
    List<Bucket> buckets = new ArrayList<>();
    for(int i=0; i<numBuckets; i++) {
      int from = i*bucketSize;
      int to = Math.min((i+1)*bucketSize, this.size());
      int[] fromIndex = new int[to-from];
      for(int j=0; j<fromIndex.length; j++) {
        fromIndex[j] = j + from;
      }
      buckets.add(new Bucket(fromIndex));
      keys[i] = i;
    }
    return new SeriesGrouping(DataFrame.toSeries(keys), this, buckets);
  }

  /**
   * Returns a SeriesGrouping based on a fixed number of buckets. Elements are grouped into buckets
   * based on a greedy algorithm to approximately evenly fill buckets. The number of buckets
   * is guaranteed to be equal to {@code partitionCount} even if some remain empty.
   *
   * <b>NOTE:</b> the series is not sorted before grouping.
   *
   * @param partitionCount number of buckets
   * @return grouping by bucket count
   */
  public SeriesGrouping groupByPartitions(int partitionCount) {
    if(partitionCount <= 0)
      throw new IllegalArgumentException("partitionCount must be greater than 0");
    if(this.isEmpty())
      return new SeriesGrouping(this);

    double perPartition = this.size() /  (double)partitionCount;

    long[] keys = new long[partitionCount];
    List<Bucket> buckets = new ArrayList<>();
    for(int i=0; i<partitionCount; i++) {
      int from = (int)Math.round(i * perPartition);
      int to = (int)Math.round((i+1) * perPartition);
      int[] fromIndex = new int[to-from];
      for(int j=0; j<fromIndex.length; j++) {
        fromIndex[j] = j + from;
      }
      buckets.add(new Bucket(fromIndex));
      keys[i] = i;
    }
    return new SeriesGrouping(DataFrame.toSeries(keys), this, buckets);
  }

  /* *************************************************************************
   * Internal interface
   * *************************************************************************/

  /**
   * Returns index tuples (pairs) for a join performed based on value.
   *
   * <b>NOTE:</b> the implementation uses merge join. Thus, the index pairs reference
   * values in ascending order.
   *
   * @param other series to match values against
   * @param type type of join to perform
   * @return list of index pairs for join
   */
  List<JoinPair> join(Series other, JoinType type) {
    // NOTE: merge join
    Series that = other.toType(this.type());

    int[] lref = this.sortedIndex();
    int[] rref = that.sortedIndex();

    List<JoinPair> pairs = new ArrayList<>();
    int i = 0;
    int j = 0;
    while(i < this.size() || j < that.size()) {
      if(j >= that.size() || (i < this.size() && this.compare(that, lref[i], rref[j]) < 0)) {
        switch(type) {
          case LEFT:
          case OUTER:
            pairs.add(new JoinPair(lref[i], -1));
          default:
        }
        i++;
      } else if(i >= this.size() || (j < that.size() && this.compare(that, lref[i], rref[j]) > 0)) {
        switch(type) {
          case RIGHT:
          case OUTER:
            pairs.add(new JoinPair(-1, rref[j]));
          default:
        }
        j++;
      } else if(i < this.size() && j < that.size()) {
        // generate cross product

        // count similar values on the left
        int lcount = 1;
        while(i + lcount < this.size() && this.compare(this, lref[i + lcount], lref[i + lcount - 1]) == 0) {
          lcount++;
        }

        // count similar values on the right
        int rcount = 1;
        while(j + rcount < that.size() && that.compare(that, rref[j + rcount], rref[j + rcount - 1]) == 0) {
          rcount++;
        }

        for(int l=0; l<lcount; l++) {
          for(int r=0; r<rcount; r++) {
            pairs.add(new JoinPair(lref[i + l], rref[j + r]));
          }
        }

        i += lcount;
        j += rcount;
      }
    }

    return pairs;
  }

}
