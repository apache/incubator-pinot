package com.linkedin.thirdeye.anomaly.utils;

import java.io.IOException;

import org.apache.http.HttpHost;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpPost;

/**
 * Utility classes for calling detector endpoints to execute/schedule jobs
 */
public class DetectionResourceHttpUtils extends AbstractResourceHttpUtils {

  private static String DETECTION_JOB_ENDPOINT = "/api/detection-job/";
  private static String ADHOC = "/ad-hoc";
  private static String BACKFILL = "/backfill";

  public DetectionResourceHttpUtils(String detectionHost, int detectionPort) {
    super(new HttpHost(detectionHost, detectionPort));
  }

  public String enableAnomalyFunction(String id) throws ClientProtocolException, IOException {
    HttpPost req = new HttpPost(DETECTION_JOB_ENDPOINT + id);
    return callJobEndpoint(req);
  }

  public String disableAnomalyFunction(String id) throws ClientProtocolException, IOException {
    HttpDelete req = new HttpDelete(DETECTION_JOB_ENDPOINT + id);
    return callJobEndpoint(req);
  }

  public String runAdhocAnomalyFunction(String id, String startTimeIso, String endTimeIso)
      throws ClientProtocolException, IOException {
    HttpPost req = new HttpPost(
        DETECTION_JOB_ENDPOINT + id + ADHOC + "?start=" + startTimeIso + "&end=" + endTimeIso);
    return callJobEndpoint(req);
  }

  public String runBackfillAnomalyFunction(String id, String startTimeIso, String endTimeIso, boolean forceBackfill)
      throws ClientProtocolException, IOException {
    HttpPost req = new HttpPost(
        DETECTION_JOB_ENDPOINT + id + BACKFILL + "?start=" + startTimeIso + "&end=" + endTimeIso + "&force=" + forceBackfill);
    return callJobEndpoint(req);
  }
}
