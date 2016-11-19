package com.linkedin.thirdeye.anomaly.alert;

import com.linkedin.thirdeye.api.DimensionMap;
import com.linkedin.thirdeye.datalayer.bao.MetricConfigManager;
import com.linkedin.thirdeye.datalayer.dto.MetricConfigDTO;
import com.linkedin.thirdeye.datalayer.pojo.AnomalyFunctionBean;
import com.linkedin.thirdeye.detector.email.filter.AlertFilter;
import com.linkedin.thirdeye.detector.email.filter.AlertFilterType;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linkedin.thirdeye.anomaly.ThirdEyeAnomalyConfiguration;
import com.linkedin.thirdeye.anomaly.task.TaskContext;
import com.linkedin.thirdeye.anomaly.task.TaskInfo;
import com.linkedin.thirdeye.anomaly.task.TaskResult;
import com.linkedin.thirdeye.anomaly.task.TaskRunner;
import com.linkedin.thirdeye.client.ThirdEyeCacheRegistry;
import com.linkedin.thirdeye.client.ThirdEyeClient;
import com.linkedin.thirdeye.client.cache.QueryCache;
import com.linkedin.thirdeye.datalayer.bao.MergedAnomalyResultManager;
import com.linkedin.thirdeye.datalayer.dto.EmailConfigurationDTO;
import com.linkedin.thirdeye.datalayer.dto.MergedAnomalyResultDTO;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import freemarker.template.TemplateMethodModelEx;
import freemarker.template.TemplateModelException;
import freemarker.template.TemplateNumberModel;

import static com.linkedin.thirdeye.anomaly.alert.AlertFilterHelper.FILTER_TYPE_KEY;

public class AlertTaskRunner implements TaskRunner {

  private static final Logger LOG = LoggerFactory.getLogger(AlertTaskRunner.class);
  private static final ThirdEyeCacheRegistry CACHE_REGISTRY_INSTANCE = ThirdEyeCacheRegistry.getInstance();
  private static final String DIMENSION_VALUE_SEPARATOR = ", ";
  private static final String EQUALS = "=";

  private MergedAnomalyResultManager anomalyMergedResultDAO;
  private MetricConfigManager metricConfigDAO;
  private EmailConfigurationDTO alertConfig;
  private DateTime windowStart;
  private DateTime windowEnd;
  private ThirdEyeAnomalyConfiguration thirdeyeConfig;

  private QueryCache queryCache;

  public static final TimeZone DEFAULT_TIME_ZONE = TimeZone.getTimeZone("America/Los_Angeles");
  public static final String CHARSET = "UTF-8";

  public AlertTaskRunner() {
    queryCache = CACHE_REGISTRY_INSTANCE.getQueryCache();
  }

  @Override
  public List<TaskResult> execute(TaskInfo taskInfo, TaskContext taskContext) throws Exception {
    AlertTaskInfo alertTaskInfo = (AlertTaskInfo) taskInfo;
    List<TaskResult> taskResult = new ArrayList<>();
    anomalyMergedResultDAO = taskContext.getMergedResultDAO();
    metricConfigDAO = taskContext.getMetricConfigDAO();
    alertConfig = alertTaskInfo.getAlertConfig();
    windowStart = alertTaskInfo.getWindowStartTime();
    windowEnd = alertTaskInfo.getWindowEndTime();
    thirdeyeConfig = taskContext.getThirdEyeAnomalyConfiguration();

    try {
      LOG.info("Begin executing task {}", taskInfo);
      runTask();
    } catch (Exception t) {
      LOG.error("Task failed with exception:", t);
      sendFailureEmail(t);
      // Let task driver mark this task failed
      throw t;
    }
    return taskResult;
  }

  private void runTask() throws Exception {
    LOG.info("Starting email report {}", alertConfig.getId());

    ThirdEyeClient client = queryCache.getClient();

    final String collection = alertConfig.getCollection();

    // Get the anomalies in that range
    final List<MergedAnomalyResultDTO> allResults = anomalyMergedResultDAO
        .getAllByTimeEmailIdAndNotifiedFalse(windowStart.getMillis(), windowEnd.getMillis(),
            alertConfig.getId());

    // apply filtration rule
    List<MergedAnomalyResultDTO> results = applyFiltrationRule(allResults);

    if (results.isEmpty() && !alertConfig.isSendZeroAnomalyEmail()) {
      LOG.info("Zero anomalies found, skipping sending email");
      return;
    }

    // Group by dimension key, then sort according to anomaly result compareTo method.
    Map<DimensionMap, List<MergedAnomalyResultDTO>> groupedResults = new TreeMap<>();
    for (MergedAnomalyResultDTO result : results) {
      DimensionMap dimensions = result.getDimensions();
      if (!groupedResults.containsKey(dimensions)) {
        groupedResults.put(dimensions, new ArrayList<>());
      }
      groupedResults.get(dimensions).add(result);
    }
    // sort each list of anomaly results afterwards and keep track of sequence number in a new list
    Map<MergedAnomalyResultDTO, String> anomaliesWithLabels = new LinkedHashMap<>();
    int counter = 1;
    for (List<MergedAnomalyResultDTO> resultsByExploredDimensions : groupedResults.values()) {
      Collections.sort(resultsByExploredDimensions);
      for (MergedAnomalyResultDTO result : resultsByExploredDimensions) {
        anomaliesWithLabels.put(result, String.valueOf(counter));
        counter++;
      }
    }
    sendAlertForAnomalies(collection, results, groupedResults);
    updateNotifiedStatus(results);
  }

  private List<MergedAnomalyResultDTO> applyFiltrationRule(List<MergedAnomalyResultDTO> results) {
    if (results.size() == 0) {
      return results;
    }

    // Function ID to Alert Filter
    Map<Long, AlertFilter> functionAlertFilter = new HashMap<>();

    List<MergedAnomalyResultDTO> qualifiedAnomalies = new ArrayList<>();
    for (MergedAnomalyResultDTO result : results) {
      // Lazy initiates alert filter for anomalies of the same anomaly function
      AnomalyFunctionBean anomalyFunctionSpec = result.getFunction();
      long functionId = anomalyFunctionSpec.getId();
      AlertFilter alertFilter = functionAlertFilter.get(functionId);
      if (alertFilter == null) {
        // Get filtration rule from anomaly function configuration
        alertFilter = initiateAlertFilter(anomalyFunctionSpec);
        functionAlertFilter.put(functionId, alertFilter);
        LOG.info("Using filter {} for anomaly function {} (dataset: {}, metric: {})", alertFilter,
            functionId, anomalyFunctionSpec.getCollection(), anomalyFunctionSpec.getMetric());
      }
      if (alertFilter.isQualified(result)) {
        qualifiedAnomalies.add(result);
      }
    }

    LOG.info(
        "Found [{}] anomalies qualified to alert after applying filtration rule on [{}] anomalies",
        qualifiedAnomalies.size(), results.size());
    return qualifiedAnomalies;
  }

  /**
   * Initiates an alert filter for the given anomaly function.
   *
   * @param anomalyFunctionSpec the anomaly function that contains the alert filter spec.
   *
   * @return the alert filter specified by the alert filter spec or a dummy filter if the function
   * does not have an alert filter spec or this method fails to initiates an alert filter from the
   * spec.
   */
  private AlertFilter initiateAlertFilter(AnomalyFunctionBean anomalyFunctionSpec) {
    Map<String, String> alertFilterInfo = anomalyFunctionSpec.getAlertFilter();
    if (alertFilterInfo == null) {
      alertFilterInfo = Collections.emptyMap();
    }
    AlertFilter alertFilter;
    if (alertFilterInfo.containsKey(FILTER_TYPE_KEY)) {
      AlertFilterType type = AlertFilterType.valueOf(alertFilterInfo.get(FILTER_TYPE_KEY).toUpperCase());
      alertFilter = AlertFilterHelper.getAlertFilter(type);
      alertFilter.setParameters(alertFilterInfo);
    } else {
      // Every anomaly triggers an alert by default
      alertFilter = AlertFilterHelper.getAlertFilter(AlertFilterType.DUMMY);
    }
    return alertFilter;
  }

  private void sendAlertForAnomalies(String collectionAlias, List<MergedAnomalyResultDTO> results,
      Map<DimensionMap, List<MergedAnomalyResultDTO>> groupedResults)
      throws JobExecutionException {

    long anomalyStartMillis = 0;
    long anomalyEndMillis = 0;
    int anomalyResultSize = 0;
    if (CollectionUtils.isNotEmpty(results)) {
      anomalyResultSize = results.size();
      anomalyStartMillis = results.get(0).getStartTime();
      anomalyEndMillis = results.get(0).getEndTime();
      for (MergedAnomalyResultDTO mergedAnomalyResultDTO : results) {
        if (mergedAnomalyResultDTO.getStartTime() < anomalyStartMillis) {
          anomalyStartMillis = mergedAnomalyResultDTO.getStartTime();
        }
        if (mergedAnomalyResultDTO.getEndTime() > anomalyEndMillis) {
          anomalyEndMillis = mergedAnomalyResultDTO.getEndTime();
        }
      }
    }

    DateTimeZone timeZone = DateTimeZone.forTimeZone(DEFAULT_TIME_ZONE);
    DateFormatMethod dateFormatMethod = new DateFormatMethod(timeZone);


    if (alertConfig.isReportEnabled()) {

    }

    HtmlEmail email = new HtmlEmail();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    try (Writer out = new OutputStreamWriter(baos, CHARSET)) {
      Configuration freemarkerConfig = new Configuration(Configuration.VERSION_2_3_21);
      Template template = freemarkerConfig.getTemplate("merged-anomaly-report.ftl");

      freemarkerConfig.setClassForTemplateLoading(getClass(), "/com/linkedin/thirdeye/detector/");
      freemarkerConfig.setDefaultEncoding(CHARSET);
      freemarkerConfig.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
      Map<String, Object> templateData = new HashMap<>();
      String metric = alertConfig.getMetric();
      String windowUnit = alertConfig.getWindowUnit().toString();
      templateData.put("groupedAnomalyResults", convertToStringKeyBasedMap(groupedResults));
      templateData.put("anomalyCount", anomalyResultSize);
      templateData.put("startTime", anomalyStartMillis);
      templateData.put("endTime", anomalyEndMillis);
      templateData.put("reportGenerationTimeMillis", System.currentTimeMillis());
      templateData.put("dateFormat", dateFormatMethod);
      templateData.put("timeZone", timeZone);
      templateData.put("collection", collectionAlias);
      templateData.put("metric", metric);
      templateData.put("windowUnit", windowUnit);
      templateData.put("dashboardHost", thirdeyeConfig.getDashboardHost());
      template.process(templateData, out);
    } catch (Exception e) {
      throw new JobExecutionException(e);
    }

    // Send email
    try {
      String alertEmailSubject = String
          .format("Anomaly Alert!: %d anomalies detected for %s:%s", results.size(),
              collectionAlias, alertConfig.getMetric());
      String alertEmailHtml = new String(baos.toByteArray(), CHARSET);
      EmailHelper.sendEmailWithHtml(email, thirdeyeConfig.getSmtpConfiguration(), alertEmailSubject,
          alertEmailHtml, alertConfig.getFromAddress(), alertConfig.getToAddresses());
    } catch (Exception e) {
      throw new JobExecutionException(e);
    }
    LOG.info("Sent email with {} anomalies! {}", results.size(), alertConfig);
  }

  /**
   * Convert a map of "dimension map to merged anomalies" to a map of "human readable dimension string to merged
   * anomalies".
   *
   * The dimension map is converted as follows. Assume that we have a dimension map (in Json string):
   * {"country"="US","page_name"="front_page'}, then it is converted to this String: "country=US, page_name=front_page".
   *
   * @param groupedResults a map of dimensionMap to a group of merged anomaly results
   * @return a map of "human readable dimension string to merged anomalies"
   */
  private Map<String, List<MergedAnomalyResultDTO>> convertToStringKeyBasedMap(
      Map<DimensionMap, List<MergedAnomalyResultDTO>> groupedResults) {
    // Sorted by dimension name and value pairs
    Map<String, List<MergedAnomalyResultDTO>> freemarkerGroupedResults = new TreeMap<>();

    if (MapUtils.isNotEmpty(groupedResults)) {
      for (Map.Entry<DimensionMap, List<MergedAnomalyResultDTO>> entry : groupedResults.entrySet()) {
        DimensionMap dimensionMap = entry.getKey();
        String dimensionMapString;
        if (MapUtils.isNotEmpty(dimensionMap)) {
          StringBuilder sb = new StringBuilder();
          String dimensionValueSeparator = "";
          for (Map.Entry<String, String> dimensionMapEntry : dimensionMap.entrySet()) {
            sb.append(dimensionValueSeparator).append(dimensionMapEntry.getKey());
            sb.append(EQUALS).append(dimensionMapEntry.getValue());
            dimensionValueSeparator = DIMENSION_VALUE_SEPARATOR;
          }
          dimensionMapString = sb.toString();
        } else {
          dimensionMapString = "ALL";
        }
        freemarkerGroupedResults.put(dimensionMapString, entry.getValue());
      }
    }

    return freemarkerGroupedResults;
  }

  private void updateNotifiedStatus(List<MergedAnomalyResultDTO> mergedResults) {
    for (MergedAnomalyResultDTO mergedResult : mergedResults) {
      mergedResult.setNotified(true);
      anomalyMergedResultDAO.update(mergedResult);
    }
  }

  private class DateFormatMethod implements TemplateMethodModelEx {
    private final DateTimeZone TZ;
    private static final String DATE_PATTERN = "yyyy-MM-dd HH:mm:ss";

    DateFormatMethod(DateTimeZone timeZone) {
      this.TZ = timeZone;
    }

    @Override
    public Object exec(@SuppressWarnings("rawtypes") List arguments) throws TemplateModelException {
      if (arguments.size() != 1) {
        throw new TemplateModelException("Wrong arguments, expected single millisSinceEpoch");
      }
      TemplateNumberModel tnm = (TemplateNumberModel) arguments.get(0);
      if (tnm == null) {
        return null;
      }

      Long millisSinceEpoch = tnm.getAsNumber().longValue();
      DateTime date = new DateTime(millisSinceEpoch, TZ);
      return date.toString(DATE_PATTERN);
    }
  }

  private void sendFailureEmail(Throwable t) throws JobExecutionException {
    HtmlEmail email = new HtmlEmail();
    String collection = alertConfig.getCollection();
    String metric = alertConfig.getMetric();

    String subject = String
        .format("[ThirdEye Anomaly Detector] FAILED ALERT ID=%d (%s:%s)", alertConfig.getId(),
            collection, metric);
    String textBody = String
        .format("%s%n%nException:%s", alertConfig.toString(), ExceptionUtils.getStackTrace(t));
    try {
      EmailHelper
          .sendEmailWithTextBody(email, thirdeyeConfig.getSmtpConfiguration(), subject, textBody,
              thirdeyeConfig.getFailureFromAddress(), thirdeyeConfig.getFailureToAddress());
    } catch (EmailException e) {
      throw new JobExecutionException(e);
    }
  }
}
