package com.linkedin.thirdeye.detector.functionex.impl;

import com.linkedin.thirdeye.detector.functionex.AnomalyFunctionEx;
import com.linkedin.thirdeye.detector.functionex.AnomalyFunctionExResult;
import com.linkedin.thirdeye.detector.functionex.dataframe.DataFrame;
import com.linkedin.thirdeye.detector.functionex.dataframe.DoubleSeries;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RulesFunction extends AnomalyFunctionEx {
  private static final Logger LOG = LoggerFactory.getLogger(RulesFunction.class);

  private static final String COLUMN_TIMESTAMP = "timestamp";
  private static final String COLUMN_METRIC = "metric";

  @Override
  public AnomalyFunctionExResult apply() throws Exception {
    Map<String, String> variables = getSubConfig(getContext().getConfig(), "variables.");
    Map<String, String> rules = getSubConfig(getContext().getConfig(), "rules.");

    LOG.info("Using {} variables: {}", variables.size(), variables.keySet());
    LOG.info("Using {} rules: {}", rules.size(), rules.keySet());

    Map<String, DataFrame> dataFrames = new HashMap<>();

    LOG.info("Populating variables:");
    for (Map.Entry<String, String> e : variables.entrySet()) {
      URI uri = URI.create(e.getValue());
      LOG.info("Fetching '{}': '{}'", e.getKey(), uri);
      DataFrame queryResult = queryDataSource(uri.getScheme(), uri.toString());
      dataFrames.put(e.getKey(), queryResult);
    }

    DataFrame data = mergeDataFrames(dataFrames);

    long timestepSize = estimateStepSize(data);

    AnomalyFunctionExResult anomalyResult = new AnomalyFunctionExResult();
    anomalyResult.setContext(getContext());

    LOG.info("Applying rules:");
    for (Map.Entry<String, String> e : rules.entrySet()) {
      String rule = e.getValue();
      DoubleSeries ruleResults = data.map(rule);
      DataFrame violations = data.filter(ruleResults.toBooleans());
      LOG.info("Rule '{}' violated at {} / {} timestamps", e.getKey(), violations.size(), ruleResults.size());

      long[] timestamps = violations.toLongs(COLUMN_TIMESTAMP).values();
      for (int i = 0; i < timestamps.length; i++) {
        DataFrame slice = violations.sliceRows(i, i+1);

        String numeric = extractNumericPortion(rule);
        String threshold = extractThresholdPortion(rule);

        DataFrame debug = new DataFrame(1);
        debug.addSeries("current", slice.map(numeric).first());
        debug.addSeries("baseline", slice.map(threshold).first());

        anomalyResult.addAnomaly(timestamps[i] - timestepSize, timestamps[i], String.format("Rule '%s' violated", e.getKey()), debug);
      }
    }

    return anomalyResult;
  }

  static DataFrame mergeDataFrames(Map<String, DataFrame> dataFrames) {
    // TODO: move to data frame, check indices and timestamps

    if (dataFrames.isEmpty()) {
      return new DataFrame(0);
    }

    DataFrame first = dataFrames.values().iterator().next();
    DataFrame df = new DataFrame();
    df.addSeries(COLUMN_TIMESTAMP, first.toLongs(COLUMN_TIMESTAMP));

    for (Map.Entry<String, DataFrame> e : dataFrames.entrySet()) {
      DataFrame candidate = e.getValue();
      if(!first.toLongs(COLUMN_TIMESTAMP).equals(candidate.toLongs(COLUMN_TIMESTAMP)))
        throw new IllegalStateException("series timestamps do not align");
      df.addSeries(e.getKey(), candidate.get(COLUMN_METRIC));
    }

    return df;
  }

  static Map<String, String> getSubConfig(Map<String, String> map, String prefix) {
    Map<String, String> output = new TreeMap<>();
    for (Map.Entry<String, String> e : map.entrySet()) {
      if (e.getKey().startsWith(prefix)) {
        String subKey = e.getKey().substring(prefix.length());
        output.put(subKey, e.getValue());
      }
    }
    return output;
  }

  static long estimateStepSize(DataFrame df) {
    if(df.size() <= 1)
      return 0;
    long[] index = df.toLongs(COLUMN_TIMESTAMP).values();
    return index[1] - index[0];
  }

  static String extractNumericPortion(String rule) {
    Pattern p = Pattern.compile(">=|<=|>|<|!=|==|=");
    Matcher m = p.matcher(rule);
    if(!m.find())
      return "0";
    return rule.substring(0, m.start()).trim();
  }

  static String extractThresholdPortion(String rule) {
    Pattern p = Pattern.compile(">=|<=|>|<|!=|==|=");
    Matcher m = p.matcher(rule);
    if(!m.find())
      return "0";
    return rule.substring(m.end()).trim();
  }
}
