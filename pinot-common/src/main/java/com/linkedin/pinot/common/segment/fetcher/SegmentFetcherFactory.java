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
package com.linkedin.pinot.common.segment.fetcher;

import com.google.common.base.Preconditions;
import com.linkedin.pinot.filesystem.LocalPinotFS;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.configuration.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class SegmentFetcherFactory {
  private static final Logger LOGGER = LoggerFactory.getLogger(SegmentFetcherFactory.class);
  private static final SegmentFetcherFactory INSTANCE = new SegmentFetcherFactory();

  private SegmentFetcherFactory() {
  }

  public static SegmentFetcherFactory getInstance() {
    return INSTANCE;
  }

  public static final String PROTOCOLS_KEY = "protocols";
  public static final List<String> DEFAULT_PROTOCOLS = Collections.unmodifiableList(Arrays.asList("file", "http"));
  public static final Map<String, String> DEFAULT_FETCHER_CLASS_MAP =
      Collections.unmodifiableMap(new HashMap<String, String>(5) {{
        put("http", HttpSegmentFetcher.class.getName());
        put("https", HttpsSegmentFetcher.class.getName());
        put("hdfs", GenericSegmentFetcher.class.getName());
        put("adl", GenericSegmentFetcher.class.getName());
        put("file", GenericSegmentFetcher.class.getName());
      }});
  public static final String FETCHER_CLASS_KEY_SUFFIX = ".class";

  private final Map<String, SegmentFetcher> _segmentFetcherMap = new HashMap<>();
  private Configuration _pinotFSConfig;

  /**
   * Initiate the segment fetcher factory. This method should only be called once.
   * @param segmentFetcherClassConfig Segment fetcher factory config
   * @param pinotFSConfig
   *
   */
  public void init(Configuration segmentFetcherClassConfig, Configuration pinotFSConfig) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
    @SuppressWarnings("unchecked")
    List<String> protocols = segmentFetcherClassConfig.getList(PROTOCOLS_KEY, DEFAULT_PROTOCOLS);
    for (String protocol : protocols) {
      String fetcherClass =
          segmentFetcherClassConfig.getString(protocol + FETCHER_CLASS_KEY_SUFFIX, DEFAULT_FETCHER_CLASS_MAP.get(protocol));
      Preconditions.checkNotNull(fetcherClass, "No fetcher class defined for protocol: " + protocol);
      LOGGER.info("Creating a new segment fetcher for protocol: {} with class: {}", protocol, fetcherClass);
      SegmentFetcher segmentFetcher = (SegmentFetcher) Class.forName(fetcherClass).newInstance();
      LOGGER.info("Initializing segment fetcher for protocol: {}", protocol);
      Configuration segmentFetcherConfig = segmentFetcherClassConfig.subset(protocol);
      logFetcherInitConfig(segmentFetcher, protocol, segmentFetcherConfig);
      segmentFetcher.init(segmentFetcherConfig);
      _segmentFetcherMap.put(protocol, segmentFetcher);
    }
    _pinotFSConfig = pinotFSConfig;
  }

  public boolean containsProtocol(String protocol) {
    return _segmentFetcherMap.containsKey(protocol);
  }

  public SegmentFetcher getSegmentFetcherBasedOnURI(String uri) throws Exception {
    String protocol = new URI(uri).getScheme();
    SegmentFetcher segmentFetcher = _segmentFetcherMap.get(protocol);
    if (segmentFetcher instanceof GenericSegmentFetcher) {
      LOGGER.info("Initializing pinotFS for segment fetcher with uri {}", uri);
      if (_pinotFSConfig.getProperty("file") == null) {
        _pinotFSConfig.addProperty("file", LocalPinotFS.class.getName());
      }
      segmentFetcher = new GenericSegmentFetcher(new URI(uri), _pinotFSConfig);
    }
    return segmentFetcher;
  }

  private static void logFetcherInitConfig(SegmentFetcher fetcher, String protocol, Configuration conf) {
    LOGGER.info("Initializing protocol [{}] with the following configs:", protocol);
    Iterator iter = conf.getKeys();
    Set<String> secretKeys = fetcher.getProtectedConfigKeys();
    while (iter.hasNext()) {
      String key = (String) iter.next();
      if (secretKeys.contains(key)) {
        LOGGER.info("{}: {}", key, "********");
      } else {
        LOGGER.info("{}: {}", key, conf.getString(key));
      }
    }
    LOGGER.info("");
  }
}
