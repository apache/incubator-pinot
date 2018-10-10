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
package com.linkedin.pinot.core.crypt;

import org.testng.Assert;
import org.testng.annotations.Test;


public class PinotCrypterFactoryTest {
  @Test
  public void testDefaultPinotCrypter() {
    Assert.assertTrue(PinotCrypterFactory.create(null) instanceof NoOpPinotCrypter);
  }
  @Test
  public void testConfiguredPinotCrypter() {
    Assert.assertTrue(PinotCrypterFactory.create(NoOpPinotCrypter.class.getName()) instanceof NoOpPinotCrypter);
  }
}
