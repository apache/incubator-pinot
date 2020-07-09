package org.apache.pinot.client.controller.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.ning.http.client.Response;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;


public class SchemaResponse {
  private String _schemaName;
  private JsonNode _dimensions;
  private JsonNode _metrics;

  private SchemaResponse() {
  }

  private SchemaResponse(JsonNode schemaResponse) {
    _schemaName = schemaResponse.get("schemaName").textValue();
    _dimensions = schemaResponse.get("dimensionFieldSpecs");
    _metrics = schemaResponse.get("metricFieldSpecs");
  }

  public static SchemaResponse fromJson(JsonNode schemaResponse) {
    return new SchemaResponse(schemaResponse);
  }

  public static SchemaResponse empty() {
    return new SchemaResponse();
  }

  public String getSchemaName(){
    return _schemaName;
  }

  public JsonNode getDimensions(){
    return _dimensions;
  }

  public JsonNode getMetrics() {
    return _metrics;
  }


  public static class SchemaResponseFuture extends ControllerResponseFuture<SchemaResponse> {
    private final ObjectReader OBJECT_READER = new ObjectMapper().reader();

    public SchemaResponseFuture(Future<Response> response, String url) {
      super(response, url);
    }

    @Override
    public SchemaResponse get(long timeout, TimeUnit unit)
        throws ExecutionException {
      String response = getStringResponse(timeout, unit);
      try {
        JsonNode jsonResponse = OBJECT_READER.readTree(response);
        return SchemaResponse.fromJson(jsonResponse);
      } catch (IOException e) {
        new ExecutionException(e);
      }

      return null;
    }
  }


}
