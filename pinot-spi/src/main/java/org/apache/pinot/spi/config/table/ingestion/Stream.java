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
package org.apache.pinot.spi.config.table.ingestion;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;


/**
 * Contains all the configs related to the streams for ingestion
 */
public class Stream {

  @JsonPropertyDescription("All configs for the streams from which to ingest")
  private final List<Map<String, String>> _streamConfigs;

  @JsonCreator
  public Stream(@JsonProperty("stream") @Nullable List<Map<String, String>> streamConfigs) {
    _streamConfigs = streamConfigs;
  }

  @Nullable
  public List<Map<String, String>> getStreamConfigs() {
    return _streamConfigs;
  }
}