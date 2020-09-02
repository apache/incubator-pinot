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
package org.apache.pinot.core.segment.processing.transformer;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.util.Map;
import javax.annotation.Nullable;


/**
 * Config for record transformer
 */
@JsonDeserialize(builder = RecordTransformerConfig.Builder.class)
public class RecordTransformerConfig {

  private final Map<String, String> _transformFunctionsMap;

  private RecordTransformerConfig(@Nullable Map<String, String> transformFunctionsMap) {
    _transformFunctionsMap = transformFunctionsMap;
  }

  /**
   * Map containing transform functions for column transformation of a record
   */
  @Nullable
  public Map<String, String> getTransformFunctionsMap() {
    return _transformFunctionsMap;
  }

  /**
   * Builder for Record Transformer Config
   */
  @JsonPOJOBuilder(withPrefix = "set")
  public static class Builder {
    private Map<String, String> transformFunctionsMap;

    public Builder setTransformFunctionsMap(Map<String, String> transformFunctionsMap) {
      this.transformFunctionsMap = transformFunctionsMap;
      return this;
    }

    public RecordTransformerConfig build() {
      return new RecordTransformerConfig(transformFunctionsMap);
    }
  }

  @Override
  public String toString() {
    return "RecordTransformerConfig{" + "_transformFunctionsMap=" + _transformFunctionsMap + '}';
  }
}