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
package com.linkedin.pinot.transport.scattergather;

import java.util.Map;
import com.linkedin.pinot.common.request.BrokerRequest;
import com.linkedin.pinot.common.response.ServerInstance;
import com.linkedin.pinot.transport.common.BucketingSelection;
import com.linkedin.pinot.transport.common.ReplicaSelection;
import com.linkedin.pinot.transport.common.ReplicaSelectionGranularity;
import com.linkedin.pinot.transport.common.SegmentIdSet;


/**
 *
 * All clients of Request-Routing layer must implement and pass the RoutingRequest instance
 * for request-routing
 */
public interface ScatterGatherRequest {

  /**
   * Return the candidate set of servers that hosts each segment-set.
   * The List of services are expected to be ordered so that replica-selection strategy can be
   * applied to them to select one Service among the list for each segment.
   *
   * @return SegmentSet to Request map.
   */
  public Map<ServerInstance, SegmentIdSet> getSegmentsServicesMap();

  /**
   * Return the requests that will be sent to the service which is hosting a group of interested segments
   * @param service Service to which segments will be sent.
   * @param querySegments Segments to be queried in the service identified by Service.
   * @return byte[] request to be sent
   */
  public byte[] getRequestForService(ServerInstance service, SegmentIdSet querySegments);

  /**
   * Replica Selection Policy to follow when a segmentIdSet has more than one candidate nodes
   * @return Replica selection strategy for this request.
   **/
  public ReplicaSelection getReplicaSelection();

  /**
   * Replica Selection granularity to follow for applying the replica selection policy.
   * @return Replica selection granularity for this request.
   **/
  public ReplicaSelectionGranularity getReplicaSelectionGranularity();

  /**
   * Used by Hash based selection strategy. Hash-based routing allows groups of request
   * to be sent to the same node. This is called bucketing. Useful for A/B testing.
   * This is an optional field and is only used if hash-based selection is requested.
   * If hash-based routing is requested but the bucket-key is not present, then round-robin
   * selection is used.
   */
  public Object getHashKey();

  /**
   * Return the number of speculative (duplicate) requests ( to different server) that needs
   * to be sent foe each scattered request. To turn off speculative requests, this method should
   * return 0.
   *
   * TODO: Currently Not implemented
   */
  public int getNumSpeculativeRequests();

  /**
   * Used for diagnostics, A predefined selection of service can be chosen for each segments
   * and sent to the Scatter-Gather. Scatter-Gather will honor such selection and do not override them.
   * @return
   */
  public BucketingSelection getPredefinedSelection();

  /**
   * Request Id for tracing purpose
   * @return
   */
  public long getRequestId();

  /**
   * Return timeout in MS for the request. If the timeout gets elapsed, the request will be cancelled.  Timeout
   * with negative values are considered infinite timeout.
   * @return
   */
  public long getRequestTimeoutMS();

  /**
   * @return the BrokerRequest object used for this scatterGather
   */
  public BrokerRequest getBrokerRequest();
}
