/**
 * Copyright (C) 2014-2015 LinkedIn Corp. (pinot-core@linkedin.com)
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
package com.linkedin.pinot.client;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


/**
 * A Pinot query result set for group by results, of which there is one of per aggregation function in the query.
 */
class GroupByResultSet extends AbstractResultSet {
  private final JSONObject _jsonObject;
  private final JSONArray _groupByResults;

  public GroupByResultSet(JSONObject jsonObject) {
    _jsonObject = jsonObject;
    try {
      _groupByResults = jsonObject.getJSONArray("groupByResult");
    } catch (JSONException e) {
      throw new PinotClientException(e);
    }
  }

  /**
   * Returns the number of rows in this result group.
   *
   * @return The number of rows in this result group
   */
  @Override
  public int getRowCount() {
    return _groupByResults.length();
  }

  @Override
  public int getColumnCount() {
    return 1;
  }

  @Override
  public String getColumnName(int columnIndex) {
    try {
      return _jsonObject.getString("function");
    } catch (JSONException e) {
      throw new PinotClientException(e);
    }
  }

  @Override
  public String getString(int rowIndex, int columnIndex) {
    if (columnIndex != 0) {
      throw new IllegalArgumentException("Column index must always be 0 for aggregation result sets");
    }

    try {
      return _groupByResults.getJSONObject(rowIndex).getString("value");
    } catch (Exception e) {
      throw new PinotClientException(e);
    }
  }

  @Override
  public int getGroupKeyLength() {
    try {
      return _groupByResults.getJSONObject(0).getJSONArray("group").length();
    } catch (JSONException e) {
      throw new PinotClientException(e);
    }
  }

  @Override
  public String getGroupKeyString(int rowIndex, int groupKeyColumnIndex) {
    try {
      return _groupByResults.getJSONObject(rowIndex).getJSONArray("group").getString(groupKeyColumnIndex);
    } catch (Exception e) {
      throw new PinotClientException(e);
    }
  }
}
