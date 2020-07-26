/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.pinot.controller.api.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.BiMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionService;
import java.util.concurrent.Executor;
import org.apache.commons.httpclient.HttpConnectionManager;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.pinot.common.http.MultiGetRequest;
import org.apache.pinot.common.restlet.resources.SegmentStatus;
import org.apache.pinot.spi.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerSegmentMetadataReader {
  private static final Logger LOGGER = LoggerFactory.getLogger(ServerSegmentMetadataReader.class);

  private final Executor _executor;
  private final HttpConnectionManager _connectionManager;

  public ServerSegmentMetadataReader(Executor executor, HttpConnectionManager connectionManager) {
    _executor = executor;
    _connectionManager = connectionManager;
  }

  public List<String> getSegmentMetadataFromServer(String tableNameWithType,
                                                   Map<String, List<String>> serversToSegmentsMap,
                                                   BiMap<String, String> endpoints, int timeoutMs) {
    LOGGER.info("Reading segment metadata from servers for table {}.", tableNameWithType);
    List<String> serverURLs = new ArrayList<>();
    for (Map.Entry<String, List<String>> serverToSegments : serversToSegmentsMap.entrySet()) {
      List<String> segments = serverToSegments.getValue();
      for (String segment : segments) {
        serverURLs.add(generateSegmentMetadataServerURL(tableNameWithType, segment, endpoints.get(serverToSegments.getKey())));
      }
    }
    CompletionService<GetMethod> completionService =
            new MultiGetRequest(_executor, _connectionManager).execute(serverURLs, timeoutMs);
    List<String> segmentsMetadata = new ArrayList<>();

    BiMap<String, String> endpointsToServers = endpoints.inverse();
    for (int i = 0; i < serverURLs.size(); i++) {
      GetMethod getMethod = null;
      try {
        getMethod = completionService.take().get();
        URI uri = getMethod.getURI();
        String instance = endpointsToServers.get(uri.getHost() + ":" + uri.getPort());
        if (getMethod.getStatusCode() >= 300) {
          LOGGER.error("Server {} returned error: code: {}, message: {}", instance, getMethod.getStatusCode(),
              getMethod.getResponseBodyAsString());
          continue;
        }
        JsonNode segmentMetadata =
                JsonUtils.inputStreamToJsonNode(getMethod.getResponseBodyAsStream());
        segmentsMetadata.add(JsonUtils.objectToString(segmentMetadata));
      } catch (Exception e) {
        // Ignore individual exceptions because the exception has been logged in MultiGetRequest
        // Log the number of failed servers after gathering all responses
      } finally {
        if (Objects.nonNull(getMethod)) {
          getMethod.releaseConnection();
        }
      }
    }
    LOGGER.info("Retrieved segment metadata from servers.");
    return segmentsMetadata;
  }

  private String generateSegmentMetadataServerURL(String tableNameWithType, String segmentName, String endpoint) {
    return String.format("http://%s/tables/%s/segments/%s/metadata", endpoint, tableNameWithType, segmentName);
  }

  private String generateReloadStatusServerURL(String tableNameWithType, String segmentName, String endpoint) {
    return String.format("http://%s/tables/%s/segments/%s/reload-status", endpoint, tableNameWithType, segmentName);
  }

  public List<SegmentStatus> getSegmentReloadTime(String tableNameWithType,
                                                  Map<String, List<String>> serverToSegments,
                                                  BiMap<String, String> serverToEndpoint, int timeoutMs) {
    LOGGER.info("Reading segment reload status from servers for table {}.", tableNameWithType);
    List<String> serverURLs = new ArrayList<>();
    for (Map.Entry<String, List<String>> serverToSegmentsEntry : serverToSegments.entrySet()) {
      List<String> segments = serverToSegmentsEntry.getValue();
      for (String segment : segments) {
        serverURLs.add(generateReloadStatusServerURL(tableNameWithType, segment, serverToEndpoint.get(serverToSegmentsEntry.getKey())));
      }
    }
    CompletionService<GetMethod> completionService =
        new MultiGetRequest(_executor, _connectionManager).execute(serverURLs, timeoutMs);
    BiMap<String, String> endpointsToServers = serverToEndpoint.inverse();
    List<SegmentStatus> segmentsStatus = new ArrayList<>();

    for (int i = 0; i < serverURLs.size(); i++) {
      GetMethod getMethod = null;
      try {
        getMethod = completionService.take().get();
        URI uri = getMethod.getURI();
        String instance = endpointsToServers.get(uri.getHost() + ":" + uri.getPort());
        if (getMethod.getStatusCode() >= 300) {
          LOGGER.error("Server {} returned error: code: {}, message: {}", instance, getMethod.getStatusCode(),
              getMethod.getResponseBodyAsString());
          continue;
        }
        SegmentStatus segmentStatus = JsonUtils.inputStreamToObject(getMethod.getResponseBodyAsStream(), SegmentStatus.class);
        segmentsStatus.add(segmentStatus);
      } catch (Exception e) {
        // Ignore individual exceptions because the exception has been logged in MultiGetRequest
        // Log the number of failed servers after gathering all responses
      } finally {
        if (Objects.nonNull(getMethod)) {
          getMethod.releaseConnection();
        }
      }
    }
    return segmentsStatus;
  }

}
