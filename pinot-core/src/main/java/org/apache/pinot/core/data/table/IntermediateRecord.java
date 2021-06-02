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
package org.apache.pinot.core.data.table;

/**
 * Helper class to store a subset of Record fields
 * IntermediateRecord is derived from a Record
 * Some of the main properties of an IntermediateRecord are:
 *
 * 1. Key in IntermediateRecord is expected to be identical to the one in the Record
 * 2. For values, IntermediateRecord should only have the columns needed for order by
 * 3. Inside the values, the columns should be ordered by the order by sequence
 * 4. For order by on aggregations, final results should extracted if the intermediate result is non-comparable
 * 5. There is an optional field to store the original record. Each segment can keep the intermediate result in this
 * form to prevent constructing IntermediateRecord again in the server.
 */
public class IntermediateRecord {
  public final Key _key;
  public final Comparable[] _values;
  public final Record _record;

  IntermediateRecord(Key key, Comparable[] values, Record record) {
    _key = key;
    _values = values;
    _record = record;
  }

}
