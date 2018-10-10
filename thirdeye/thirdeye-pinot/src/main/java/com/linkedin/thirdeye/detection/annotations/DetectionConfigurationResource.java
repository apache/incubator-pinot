/*
 * Copyright (C) 2014-2018 LinkedIn Corp. (pinot-core@linkedin.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.linkedin.thirdeye.detection.annotations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.reflect.ClassPath;
import com.linkedin.thirdeye.detection.algorithm.stage.AnomalyDetectionStage;
import com.wordnik.swagger.annotations.ApiParam;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;


@Path("/detection/annotation")
public class DetectionConfigurationResource {
  private static ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @GET
  public Response getConfigurations(@ApiParam("tag") String tag) throws IOException, ClassNotFoundException {
    Set<ClassPath.ClassInfo> classInfos = ClassPath.from(Thread.currentThread().getContextClassLoader())
        .getTopLevelClasses(AnomalyDetectionStage.class.getPackage().getName());
    List<Map> annotations = new ArrayList<>();
    for (ClassPath.ClassInfo classInfo : classInfos) {
      // stage annotations
      Class clazz = Class.forName(classInfo.getName());
      Map<String, Object> stageAnnotation = new HashMap<>();
      for (Annotation annotation : clazz.getAnnotations()) {
        if (annotation instanceof Detection) {
          stageAnnotation.put("stage", annotation);
        }
      }

      //parameter annotations
      List<Annotation> parameterAnnotations = new ArrayList<>();
      for (Field field : clazz.getDeclaredFields()) {
        for (Annotation annotation : field.getAnnotations()) {
          if (annotation instanceof DetectionParam) {
            parameterAnnotations.add(annotation);
          }
        }
      }
      if (!parameterAnnotations.isEmpty()) {
        stageAnnotation.put("params", parameterAnnotations);
      }

      if (!stageAnnotation.isEmpty()) {
        annotations.add(stageAnnotation);
      }
    }
    return Response.ok(OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(annotations)).build();
  }
}
