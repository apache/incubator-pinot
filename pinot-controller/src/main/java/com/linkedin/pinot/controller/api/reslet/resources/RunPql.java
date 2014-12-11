package com.linkedin.pinot.controller.api.reslet.resources;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.antlr.runtime.RecognitionException;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import org.restlet.representation.Representation;
import org.restlet.representation.StringRepresentation;
import org.restlet.resource.ServerResource;

import com.linkedin.pinot.controller.ControllerConf;
import com.linkedin.pinot.controller.helix.core.PinotHelixResourceManager;
import com.linkedin.pinot.pql.parsers.PQLCompiler;


/**
 * @author Dhaval Patel<dpatel@linkedin.com>
 * Dec 8, 2014
 */

public class RunPql extends ServerResource {

  private static final Logger logger = Logger.getLogger(RunPql.class);
  private final ControllerConf conf;
  private final PinotHelixResourceManager manager;
  private static final PQLCompiler compiler = new PQLCompiler(new HashMap<String, String[]>());;

  public RunPql() {
    conf = (ControllerConf) getApplication().getContext().getAttributes().get(ControllerConf.class.toString());
    manager = (PinotHelixResourceManager) getApplication().getContext().getAttributes().get(PinotHelixResourceManager.class.toString());
  }

  @Override
  public Representation get() {
    final String pqlString = getQuery().getValues("pql");
    logger.info("*** found pql : " + pqlString);
    JSONObject compiledJSON;

    try {
      compiledJSON = compiler.compile(pqlString);
    } catch (final RecognitionException e) {
      logger.error(e);
      return new StringRepresentation(e.getMessage());
    }

    if (!compiledJSON.has("collection")) {
      return new StringRepresentation("your request does not contain the collection information");
    }

    String resource;

    try {
      final String collection = compiledJSON.getString("collection");
      resource = collection.substring(0, collection.indexOf("."));
    } catch (final JSONException e) {
      logger.error(e);
      return new StringRepresentation(e.getMessage());
    }

    String instanceId;
    try {
      instanceId = manager.getBrokerInstanceFor(resource);
    } catch (final Exception e) {
      logger.error(e);
      return new StringRepresentation(e.getMessage());
    }

    if (instanceId == null) {
      return new StringRepresentation("could not find a broker for data resource : " + resource);
    }

    final String[] splStrings = instanceId.split("_");

    final String host = "http://" + splStrings[1];
    final String port = splStrings[2];

    final String resp = sendPQLRaw(host + ":" + port + "/query", pqlString);

    return new StringRepresentation(resp);
  }

  public String sendPostRaw(String urlStr, String requestStr, Map<String, String> headers) {
    HttpURLConnection conn = null;
    try {
      /*if (LOG.isInfoEnabled()){
        LOG.info("Sending a post request to the server - " + urlStr);
      }

      if (LOG.isDebugEnabled()){
        LOG.debug("The request is - " + requestStr);
      }*/

      logger.info("url string passed is : " + urlStr);
      final URL url = new URL(urlStr);
      conn = (HttpURLConnection) url.openConnection();
      conn.setDoOutput(true);
      conn.setRequestMethod("POST");
      // conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");

      conn.setRequestProperty("Accept-Encoding", "gzip");

      final String string = requestStr;
      final byte[] requestBytes = string.getBytes("UTF-8");
      conn.setRequestProperty("Content-Length", String.valueOf(requestBytes.length));
      conn.setRequestProperty("http.keepAlive", String.valueOf(true));
      conn.setRequestProperty("default", String.valueOf(true));

      if (headers != null && headers.size() > 0) {
        final Set<Entry<String, String>> entries = headers.entrySet();
        for (final Entry<String, String> entry : entries) {
          conn.setRequestProperty(entry.getKey(), entry.getValue());
        }
      }

      //GZIPOutputStream zippedOutputStream = new GZIPOutputStream(conn.getOutputStream());
      final OutputStream os = new BufferedOutputStream(conn.getOutputStream());
      os.write(requestBytes);
      os.flush();
      os.close();
      final int responseCode = conn.getResponseCode();

      /*if (LOG.isInfoEnabled()){
        LOG.info("The http response code is " + responseCode);
      }*/
      if (responseCode != HttpURLConnection.HTTP_OK) {
        throw new IOException("Failed : HTTP error code : " + responseCode);
      }
      final byte[] bytes = drain(new BufferedInputStream(conn.getInputStream()));

      final String output = new String(bytes, "UTF-8");
      /*if (LOG.isDebugEnabled()){
        LOG.debug("The response from the server is - " + output);
      }*/
      return output;
    } catch (final Exception ex) {
      throw new RuntimeException(ex);
    } finally {
      if (conn != null) {
        conn.disconnect();
      }
    }
  }

  byte[] drain(InputStream inputStream) throws IOException {
    try {
      final byte[] buf = new byte[1024];
      int len;
      final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      while ((len = inputStream.read(buf)) > 0) {
        byteArrayOutputStream.write(buf, 0, len);
      }
      return byteArrayOutputStream.toByteArray();
    } finally {
      inputStream.close();
    }
  }

  public String sendPQLRaw(String url, String pqlRequest) {
    try {
      final long startTime = System.currentTimeMillis();
      final JSONObject bqlJson = new JSONObject().put("pql", pqlRequest);

      final String pinotResultString = sendPostRaw(url, bqlJson.toString(), null);

      final long bqlQueryTime = System.currentTimeMillis() - startTime;
      logger.info("BQL: " + pqlRequest + " Time: " + bqlQueryTime);

      return pinotResultString;
    } catch (final Exception ex) {
      throw new RuntimeException(ex);
    }
  }
}
