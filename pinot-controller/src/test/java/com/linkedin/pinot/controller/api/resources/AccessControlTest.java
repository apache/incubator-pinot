/**
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
package com.linkedin.pinot.controller.api.resources;

import com.linkedin.pinot.controller.ControllerConf;
import com.linkedin.pinot.controller.api.access.AccessControl;
import com.linkedin.pinot.controller.api.access.AccessControlFactory;
import com.linkedin.pinot.controller.helix.ControllerTest;
import java.io.IOException;
import java.util.Enumeration;
import javax.ws.rs.core.HttpHeaders;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;


public class AccessControlTest extends ControllerTest {

  @BeforeClass
  public void setUp() {
    Enumeration<Logger> loggers = Logger.getRootLogger().getLoggerRepository().getCurrentLoggers();
    while (loggers.hasMoreElements()) {
      Logger logger = loggers.nextElement();
      System.out.println("Log name: " + logger.getName());
      if (logger.getName().startsWith("com.linkedin.pinot.common.utils") ||
          logger.getName().startsWith("org.I0Itec.zkclient") ||
          logger.getName().startsWith("org.apache.zookeeper.server")) {
        logger.setLevel(Level.INFO);
      }
    }
    startZk();
    ControllerConf config = getDefaultControllerConfiguration();
    config.setAccessControlFactoryClass(DenyAllAccessFactory.class.getName());
    startController(config);
  }

  @Test
  public void testAccessDenied() throws Exception {
    try {
      sendGetRequest(_controllerRequestURLBuilder.forSegmentDownload("testTable", "testSegment"));
    } catch (IOException e) {
      Assert.assertTrue(e.getMessage().startsWith("Server returned HTTP response code: 403"));
      return;
    }
    Assert.fail("Access not denied");
  }

  @AfterClass
  public void tearDown() {
    stopController();
    stopZk();
  }

  public static class DenyAllAccessFactory implements AccessControlFactory {
    private static final AccessControl DENY_ALL_ACCESS = new AccessControl() {
      @Override
      public boolean hasDataAccess(HttpHeaders httpHeaders, String tableName) {
        return false;
      }
    };

    @Override
    public AccessControl create() {
      return DENY_ALL_ACCESS;
    }
  }
}
