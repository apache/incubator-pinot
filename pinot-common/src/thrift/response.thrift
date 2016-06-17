/**
 * Copyright (C) 2014-2016 LinkedIn Corp. (pinot-core@linkedin.com)
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
namespace java com.linkedin.pinot.common.response

/**
 * Aggregation result.
 * Interesting Notes: Tags upto 15 takes only one byte but after that takes 2  * or more bytes. So, keep the ones most frequently used with lower tags
 **/
union AggregationResult {
  1: optional i64 longVal;
  2: optional string stringVal;
  3: optional double doubleVal;
  4: optional set<i64> longSet;
  5: optional set<string> stringSet;
  6: optional set<double> doubleSet;
  7: optional map<string, string> stringStringMap;
  8: optional map<string, i64> stringLongMap;
  9: optional map<string, double> stringDoubleMap;
  10: optional map<string, set<string>> stringStringSetMap;
  11: optional map<string, set<i64>> stringLongSetMap;
  12: optional map<string, set<double>> stringDoubleSetMap;
}

union RowEventVal {
  1: optional i64 longVal;
  2: optional string stringVal;
  3: optional double doubleVal;
  4: optional set<i64> longSet;
  5: optional set<string> stringSet;
  6: optional set<double> doubleSet;
  7: optional list<i64> longList;
  8: optional list<string> stringList;
  9: optional list<double> doubleList;
}

/**
 * A single row event keyed by field name
 **/
struct RowEvent {
  1: optional map<string,RowEventVal> stringRowEventMap;
}

/**
 * Response Statistics
 **/
struct ResponseStatistics {
 1: optional string segmentId;
 2: optional i32 numDocsScanned;
 3: optional i64 timeUsedMs;
}

/**
 * Processing exception
 **/
exception ProcessingException {
  1: required i32 errorCode;
  2: optional string message;
}

/**
 * InstanceResponse
 **/
struct InstanceResponse {
  1: required i64 requestId;
  2: optional i64 totalDocs;
  3: optional i64 numDocsScanned;
  4: optional i64 timeUsedMs;
  5: optional list<AggregationResult> aggregationResults;
  6: optional list<RowEvent> rowEvents;
  7: optional list<ResponseStatistics> segmentStatistics;
  8: optional list<ProcessingException> exceptions;
  9: optional map<string,string> traceInfo;
}

