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
package com.linkedin.pinot.core.realtime.stream;

import com.linkedin.pinot.common.Utils;


/**
 * Constructs the {@link StreamConsumerFactory} from the {@link StreamMetadata::getConsumerFactoryName()} property and initializes it
 */
public abstract class StreamConsumerFactoryProvider {

  public static StreamConsumerFactory create(StreamMetadata streamMetadata) {
    StreamConsumerFactory factory = null;
    try {
      factory = (StreamConsumerFactory) Class.forName(streamMetadata.getConsumerFactoryName()).newInstance();
    } catch (Exception e) {
      Utils.rethrowException(e);
    }
    factory.init(streamMetadata);
    return factory;
  }

}
