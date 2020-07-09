package org.apache.pinot.client.controller;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.pinot.client.PinotClientException;
import org.apache.pinot.client.controller.response.SchemaResponse;
import org.apache.pinot.client.controller.response.SchemaResponse.SchemaResponseFuture;
import org.apache.pinot.client.controller.response.TableResponse;
import org.apache.pinot.client.controller.response.TableResponse.TableResponseFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class PinotControllerTransport {
  private static final Logger LOGGER = LoggerFactory.getLogger(PinotControllerTransport.class);

  AsyncHttpClient _httpClient = new AsyncHttpClient();
  Map<String, String> _headers;

  public PinotControllerTransport() {
  }

  public PinotControllerTransport(Map<String, String> headers) {
    _headers = headers;
  }

  public TableResponse getAllTables(String controllerAddress) {
    try {
      String url = "http://" + controllerAddress + "/tables";
      AsyncHttpClient.BoundRequestBuilder requestBuilder = _httpClient.prepareGet(url);
      if (_headers != null) {
        _headers.forEach((k, v) -> requestBuilder.addHeader(k, v));
      }

      final Future<Response> response =
          requestBuilder.addHeader("Content-Type", "application/json; charset=utf-8").execute();

      TableResponseFuture tableResponseFuture = new TableResponseFuture(response, url);
      return tableResponseFuture.get();
    } catch (ExecutionException e) {
      throw new PinotClientException(e);
    }
  }

  public SchemaResponse getTableSchema(String table, String controllerAddress) {
    try {
      String url = "http://" + controllerAddress + "/tables/" + table + "/schema";
      AsyncHttpClient.BoundRequestBuilder requestBuilder = _httpClient.prepareGet(url);
      if (_headers != null) {
        _headers.forEach((k, v) -> requestBuilder.addHeader(k, v));
      }

      final Future<Response> response =
          requestBuilder.addHeader("Content-Type", "application/json; charset=utf-8").execute();

      SchemaResponseFuture schemaResponseFuture = new SchemaResponseFuture(response, url);
      return schemaResponseFuture.get();
    } catch (ExecutionException e) {
      throw new PinotClientException(e);
    }
  }

  public void close()
      throws PinotClientException {
    if (_httpClient.isClosed()) {
      throw new PinotClientException("Connection is already closed!");
    }
    _httpClient.close();
  }

}
