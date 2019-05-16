package org.apache.pinot.thirdeye.cube.cost;

import com.google.common.base.Preconditions;
import java.util.Map;


public class OeRatioCostFunction implements CostFunction {
  // The threshold to the contribution to overall changes in percentage
  private double epsilon = 0.00001;

  public OeRatioCostFunction() {
  }

  public OeRatioCostFunction(Map<String, String> params) {
  }

  /**
   * Returns the cost that consider change difference, change changeRatio, and node size (contribution percentage of a node).
   *
   * In brief, this function uses this formula to compute the cost:
   *   change difference * log(contribution percentage * change changeRatio)
   *
   * In addition, if a node's contribution to overall changes is smaller than the threshold, which is defined when
   * constructing this class, then the cost is always zero.
   *
   * @param parentChangeRatio the changeRatio between baseline and current value of parent node.
   * @param baselineValue the baseline value of the current node.
   * @param currentValue the current value of the current node.
   * @param baselineSize
   * @param currentSize
   * @param topBaselineValue the baseline value of the top node.
   * @param topCurrentValue the current value of the top node.
   *
   * @param topBaselineSize
   * @param topCurrentSize
   * @return the cost that consider change difference, change changeRatio, and node size.
   */
  @Override
  public double computeCost(double parentChangeRatio, double baselineValue, double currentValue, double baselineSize,
      double currentSize, double topBaselineValue, double topCurrentValue, double topBaselineSize,
      double topCurrentSize) {

    // Contribution is the size of the node
    double contribution = (baselineSize + currentSize) / (topBaselineSize + topCurrentSize);
    Preconditions.checkState(Double.compare(contribution, 0) >= 0, "Contribution {} is smaller than 0.", contribution);
    Preconditions.checkState(Double.compare(contribution, 1) <= 0, "Contribution {} is larger than 1", contribution);
    // The cost function considers change difference, change changeRatio, and node size (i.e., contribution)
    return fillEmptyValuesAndGetError(baselineValue, currentValue, parentChangeRatio, contribution);
  }

  private static double error(double baselineValue, double currentValue, double parentRatio, double contribution) {
    double expectedBaselineValue = parentRatio * baselineValue;
    double expectedRatio = currentValue / expectedBaselineValue;
    double weightedExpectedRatio = (expectedRatio - 1) * contribution + 1;
    double logExpRatio = Math.log(weightedExpectedRatio);
    double cost = (currentValue - expectedBaselineValue) * logExpRatio;
//    double cost = Math.abs(logExpRatio);
    return cost;
  }

  private static double errorWithEmptyBaselineOrCurrent(double baseline, double currentValue, double parentRatio,
      double contribution) {
    if (Double.compare(parentRatio, 1) < 0) {
      parentRatio = 2 - parentRatio;
    }
    double logExpRatio = Math.log((parentRatio - 1) * contribution + 1);
    double cost = (currentValue - baseline) * logExpRatio;
//    double cost = Math.abs(logExpRatio);
    return cost;
  }

  /**
   * Auto fill in baselineValue and currentValue using parentRatio when one of them is zero.
   * If baselineValue and currentValue both are zero or parentRatio is not finite, this function returns 0.
   */
  private static double fillEmptyValuesAndGetError(double baselineValue, double currentValue, double parentRatio,
      double contribution) {
    if (Double.compare(0., parentRatio) == 0 || Double.isNaN(parentRatio)) {
      parentRatio = 1d;
    }
    if (Double.compare(0., baselineValue) != 0 && Double.compare(0., currentValue) != 0) {
      return error(baselineValue, currentValue, parentRatio, contribution);
    } else if (Double.compare(baselineValue, 0d) == 0 || Double.compare(currentValue, 0d) == 0) {
      if (Double.compare(0., baselineValue) == 0) {
        return errorWithEmptyBaselineOrCurrent(0d, currentValue, parentRatio, contribution);
      } else {
        return errorWithEmptyBaselineOrCurrent(baselineValue, 0d, parentRatio, contribution);
      }
    } else { // baselineValue and currentValue are zeros. Set cost to zero so the node will be naturally aggregated to its parent.
      return 0.;
    }
  }
}
