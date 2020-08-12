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
package org.apache.pinot.core.data.function;

import com.google.common.collect.Lists;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.pinot.spi.data.readers.GenericRow;
import org.apache.pinot.spi.utils.JsonUtils;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;


/**
 * Tests the Pinot inbuilt transform functions
 */
public class InbuiltFunctionsTest {

  private void testFunction(String functionExpression, List<String> expectedArguments, GenericRow row,
      Object expectedResult) {
    InbuiltFunctionEvaluator evaluator = new InbuiltFunctionEvaluator(functionExpression);
    Assert.assertEquals(evaluator.getArguments(), expectedArguments);
    Assert.assertEquals(evaluator.evaluate(row), expectedResult);
  }

  @Test(dataProvider = "dateTimeFunctionsDataProvider")
  public void testDateTimeFunctions(String functionExpression, List<String> expectedArguments, GenericRow row,
      Object expectedResult) {
    testFunction(functionExpression, expectedArguments, row, expectedResult);
  }

  @DataProvider(name = "dateTimeFunctionsDataProvider")
  public Object[][] dateTimeFunctionsDataProvider() {
    List<Object[]> inputs = new ArrayList<>();

    // round epoch millis to nearest 15 minutes
    GenericRow row0_0 = new GenericRow();
    row0_0.putValue("timestamp", 1578685189000L);
    // round to 15 minutes, but keep in milliseconds: Fri Jan 10 2020 19:39:49 becomes Fri Jan 10 2020 19:30:00
    inputs.add(new Object[]{"round(timestamp, 900000)", Lists.newArrayList("timestamp"), row0_0, 1578684600000L});

    // toEpochSeconds (with type conversion)
    GenericRow row1_0 = new GenericRow();
    row1_0.putValue("timestamp", 1578685189000.0);
    inputs.add(new Object[]{"toEpochSeconds(timestamp)", Lists.newArrayList("timestamp"), row1_0, 1578685189L});

    // toEpochSeconds w/ rounding (with type conversion)
    GenericRow row1_1 = new GenericRow();
    row1_1.putValue("timestamp", "1578685189000");
    inputs.add(
        new Object[]{"toEpochSecondsRounded(timestamp, 10)", Lists.newArrayList("timestamp"), row1_1, 1578685180L});

    // toEpochSeconds w/ bucketing (with underscore in function name)
    GenericRow row1_2 = new GenericRow();
    row1_2.putValue("timestamp", 1578685189000L);
    inputs.add(
        new Object[]{"to_epoch_seconds_bucket(timestamp, 10)", Lists.newArrayList("timestamp"), row1_2, 157868518L});

    // toEpochMinutes
    GenericRow row2_0 = new GenericRow();
    row2_0.putValue("timestamp", 1578685189000L);
    inputs.add(new Object[]{"toEpochMinutes(timestamp)", Lists.newArrayList("timestamp"), row2_0, 26311419L});

    // toEpochMinutes w/ rounding
    GenericRow row2_1 = new GenericRow();
    row2_1.putValue("timestamp", 1578685189000L);
    inputs
        .add(new Object[]{"toEpochMinutesRounded(timestamp, 15)", Lists.newArrayList("timestamp"), row2_1, 26311410L});

    // toEpochMinutes w/ bucketing
    GenericRow row2_2 = new GenericRow();
    row2_2.putValue("timestamp", 1578685189000L);
    inputs.add(new Object[]{"toEpochMinutesBucket(timestamp, 15)", Lists.newArrayList("timestamp"), row2_2, 1754094L});

    // toEpochHours
    GenericRow row3_0 = new GenericRow();
    row3_0.putValue("timestamp", 1578685189000L);
    inputs.add(new Object[]{"toEpochHours(timestamp)", Lists.newArrayList("timestamp"), row3_0, 438523L});

    // toEpochHours w/ rounding
    GenericRow row3_1 = new GenericRow();
    row3_1.putValue("timestamp", 1578685189000L);
    inputs.add(new Object[]{"toEpochHoursRounded(timestamp, 2)", Lists.newArrayList("timestamp"), row3_1, 438522L});

    // toEpochHours w/ bucketing
    GenericRow row3_2 = new GenericRow();
    row3_2.putValue("timestamp", 1578685189000L);
    inputs.add(new Object[]{"toEpochHoursBucket(timestamp, 2)", Lists.newArrayList("timestamp"), row3_2, 219261L});

    // toEpochDays
    GenericRow row4_0 = new GenericRow();
    row4_0.putValue("timestamp", 1578685189000L);
    inputs.add(new Object[]{"toEpochDays(timestamp)", Lists.newArrayList("timestamp"), row4_0, 18271L});

    // toEpochDays w/ rounding
    GenericRow row4_1 = new GenericRow();
    row4_1.putValue("timestamp", 1578685189000L);
    inputs.add(new Object[]{"toEpochDaysRounded(timestamp, 7)", Lists.newArrayList("timestamp"), row4_1, 18270L});

    // toEpochDays w/ bucketing
    GenericRow row4_2 = new GenericRow();
    row4_2.putValue("timestamp", 1578685189000L);
    inputs.add(new Object[]{"toEpochDaysBucket(timestamp, 7)", Lists.newArrayList("timestamp"), row4_2, 2610L});

    // fromEpochDays
    GenericRow row5_0 = new GenericRow();
    row5_0.putValue("daysSinceEpoch", 14000);
    inputs.add(
        new Object[]{"fromEpochDays(daysSinceEpoch)", Lists.newArrayList("daysSinceEpoch"), row5_0, 1209600000000L});

    // fromEpochDays w/ bucketing
    GenericRow row5_1 = new GenericRow();
    row5_1.putValue("sevenDaysSinceEpoch", 2000);
    inputs.add(new Object[]{"fromEpochDaysBucket(sevenDaysSinceEpoch, 7)", Lists.newArrayList(
        "sevenDaysSinceEpoch"), row5_1, 1209600000000L});

    // fromEpochHours
    GenericRow row6_0 = new GenericRow();
    row6_0.putValue("hoursSinceEpoch", 336000);
    inputs.add(
        new Object[]{"fromEpochHours(hoursSinceEpoch)", Lists.newArrayList("hoursSinceEpoch"), row6_0, 1209600000000L});

    // fromEpochHours w/ bucketing
    GenericRow row6_1 = new GenericRow();
    row6_1.putValue("twoHoursSinceEpoch", 168000);
    inputs.add(new Object[]{"fromEpochHoursBucket(twoHoursSinceEpoch, 2)", Lists.newArrayList(
        "twoHoursSinceEpoch"), row6_1, 1209600000000L});

    // fromEpochMinutes
    GenericRow row7_0 = new GenericRow();
    row7_0.putValue("minutesSinceEpoch", 20160000);
    inputs.add(new Object[]{"fromEpochMinutes(minutesSinceEpoch)", Lists.newArrayList(
        "minutesSinceEpoch"), row7_0, 1209600000000L});

    // fromEpochMinutes w/ bucketing
    GenericRow row7_1 = new GenericRow();
    row7_1.putValue("fifteenMinutesSinceEpoch", 1344000);
    inputs.add(new Object[]{"fromEpochMinutesBucket(fifteenMinutesSinceEpoch, 15)", Lists.newArrayList(
        "fifteenMinutesSinceEpoch"), row7_1, 1209600000000L});

    // fromEpochSeconds
    GenericRow row8_0 = new GenericRow();
    row8_0.putValue("secondsSinceEpoch", 1209600000L);
    inputs.add(new Object[]{"fromEpochSeconds(secondsSinceEpoch)", Lists.newArrayList(
        "secondsSinceEpoch"), row8_0, 1209600000000L});

    // fromEpochSeconds w/ bucketing
    GenericRow row8_1 = new GenericRow();
    row8_1.putValue("tenSecondsSinceEpoch", 120960000L);
    inputs.add(new Object[]{"fromEpochSecondsBucket(tenSecondsSinceEpoch, 10)", Lists.newArrayList(
        "tenSecondsSinceEpoch"), row8_1, 1209600000000L});

    // nested
    GenericRow row9_0 = new GenericRow();
    row9_0.putValue("hoursSinceEpoch", 336000);
    inputs.add(new Object[]{"toEpochDays(fromEpochHours(hoursSinceEpoch))", Lists.newArrayList(
        "hoursSinceEpoch"), row9_0, 14000L});

    GenericRow row9_1 = new GenericRow();
    row9_1.putValue("fifteenSecondsSinceEpoch", 80640000L);
    inputs.add(
        new Object[]{"toEpochMinutesBucket(fromEpochSecondsBucket(fifteenSecondsSinceEpoch, 15), 10)", Lists.newArrayList(
            "fifteenSecondsSinceEpoch"), row9_1, 2016000L});

    // toDateTime simple
    GenericRow row10_0 = new GenericRow();
    row10_0.putValue("dateTime", 98697600000L);
    inputs.add(new Object[]{"toDateTime(dateTime, 'yyyyMMdd')", Lists.newArrayList("dateTime"), row10_0, "19730216"});

    // toDateTime complex
    GenericRow row10_1 = new GenericRow();
    row10_1.putValue("dateTime", 1234567890000L);
    inputs.add(new Object[]{"toDateTime(dateTime, 'MM/yyyy/dd HH:mm:ss')", Lists.newArrayList(
        "dateTime"), row10_1, "02/2009/13 23:31:30"});

    // toDateTime with timezone
    GenericRow row10_2 = new GenericRow();
    row10_2.putValue("dateTime", 7897897890000L);
    inputs.add(new Object[]{"toDateTime(dateTime, 'EEE MMM dd HH:mm:ss ZZZ yyyy')", Lists.newArrayList(
        "dateTime"), row10_2, "Mon Apr 10 20:31:30 UTC 2220"});

    // fromDateTime simple
    GenericRow row11_0 = new GenericRow();
    row11_0.putValue("dateTime", "19730216");
    inputs
        .add(new Object[]{"fromDateTime(dateTime, 'yyyyMMdd')", Lists.newArrayList("dateTime"), row11_0, 98668800000L});

    // fromDateTime complex
    GenericRow row11_1 = new GenericRow();
    row11_1.putValue("dateTime", "02/2009/13 15:31:30");
    inputs.add(new Object[]{"fromDateTime(dateTime, 'MM/yyyy/dd HH:mm:ss')", Lists.newArrayList(
        "dateTime"), row11_1, 1234539090000L});

    // fromDateTime with timezone
    GenericRow row11_2 = new GenericRow();
    row11_2.putValue("dateTime", "Mon Aug 24 12:36:46 America/Los_Angeles 2009");
    inputs.add(new Object[]{"fromDateTime(dateTime, \"EEE MMM dd HH:mm:ss ZZZ yyyy\")", Lists.newArrayList(
        "dateTime"), row11_2, 1251142606000L});

    return inputs.toArray(new Object[0][]);
  }

  @Test(dataProvider = "jsonFunctionsDataProvider")
  public void testJsonFunctions(String functionExpression, List<String> expectedArguments, GenericRow row,
      Object expectedResult) {
    testFunction(functionExpression, expectedArguments, row, expectedResult);
  }

  @DataProvider(name = "jsonFunctionsDataProvider")
  public Object[][] jsonFunctionsDataProvider()
      throws IOException {
    List<Object[]> inputs = new ArrayList<>();

    // toJsonMapStr
    GenericRow row0 = new GenericRow();
    String jsonStr = "{\"k1\":\"foo\",\"k2\":\"bar\"}";
    row0.putValue("jsonMap", JsonUtils.stringToObject(jsonStr, Map.class));
    inputs.add(new Object[]{"toJsonMapStr(jsonMap)", Lists.newArrayList("jsonMap"), row0, jsonStr});

    GenericRow row1 = new GenericRow();
    jsonStr = "{\"k3\":{\"sub1\":10,\"sub2\":1.0},\"k4\":\"baz\",\"k5\":[1,2,3]}";
    row1.putValue("jsonMap", JsonUtils.stringToObject(jsonStr, Map.class));
    inputs.add(new Object[]{"toJsonMapStr(jsonMap)", Lists.newArrayList("jsonMap"), row1, jsonStr});

    GenericRow row2 = new GenericRow();
    jsonStr = "{\"k1\":\"foo\",\"k2\":\"bar\"}";
    row2.putValue("jsonMap", JsonUtils.stringToObject(jsonStr, Map.class));
    inputs.add(new Object[]{"json_format(jsonMap)", Lists.newArrayList("jsonMap"), row2, jsonStr});

    GenericRow row3 = new GenericRow();
    jsonStr = "{\"k3\":{\"sub1\":10,\"sub2\":1.0},\"k4\":\"baz\",\"k5\":[1,2,3]}";
    row3.putValue("jsonMap", JsonUtils.stringToObject(jsonStr, Map.class));
    inputs.add(new Object[]{"json_format(jsonMap)", Lists.newArrayList("jsonMap"), row3, jsonStr});

    GenericRow row4 = new GenericRow();
    jsonStr = "[{\"one\":1,\"two\":\"too\"},{\"one\":11,\"two\":\"roo\"}]";
    row4.putValue("jsonMap", JsonUtils.stringToObject(jsonStr, List.class));
    inputs.add(new Object[]{"json_format(jsonMap)", Lists.newArrayList("jsonMap"), row4, jsonStr});

    GenericRow row5 = new GenericRow();
    jsonStr =
        "[{\"one\":1,\"two\":{\"sub1\":1.1,\"sub2\":1.2},\"three\":[\"a\",\"b\"]},{\"one\":11,\"two\":{\"sub1\":11.1,\"sub2\":11.2},\"three\":[\"aa\",\"bb\"]}]";
    row5.putValue("jsonMap", JsonUtils.stringToObject(jsonStr, List.class));
    inputs.add(new Object[]{"json_format(jsonMap)", Lists.newArrayList("jsonMap"), row5, jsonStr});

    return inputs.toArray(new Object[0][]);
  }

  @Test(dataProvider = "arithmeticFunctionsDataProvider")
  public void testArithmeticFunctions(String functionExpression, List<String> expectedArguments, GenericRow row,
      Object expectedResult) {
    testFunction(functionExpression, expectedArguments, row, expectedResult);
  }

  @DataProvider(name = "arithmeticFunctionsDataProvider")
  public Object[][] arithmeticFunctionsDataProvider() {
    List<Object[]> inputs = new ArrayList<>();

    GenericRow row0 = new GenericRow();
    row0.putValue("a", (byte) 1);
    row0.putValue("b", (char) 2);
    inputs.add(new Object[]{"plus(a, b)", Lists.newArrayList("a", "b"), row0, 3.0});

    GenericRow row1 = new GenericRow();
    row1.putValue("a", (short) 3);
    row1.putValue("b", 4);
    inputs.add(new Object[]{"minus(a, b)", Lists.newArrayList("a", "b"), row1, -1.0});

    GenericRow row2 = new GenericRow();
    row2.putValue("a", 5L);
    row2.putValue("b", 6f);
    inputs.add(new Object[]{"times(a, b)", Lists.newArrayList("a", "b"), row2, 30.0});

    GenericRow row3 = new GenericRow();
    row3.putValue("a", 7.0);
    row3.putValue("b", "8");
    inputs.add(new Object[]{"divide(a, b)", Lists.newArrayList("a", "b"), row3, 0.875});

    return inputs.toArray(new Object[0][]);
  }
}
