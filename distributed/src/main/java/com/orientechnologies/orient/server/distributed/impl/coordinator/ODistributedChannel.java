package com.orientechnologies.orient.server.distributed.impl.coordinator;

import com.orientechnologies.orient.server.distributed.impl.coordinator.transaction.OSessionOperationId;
import com.orientechnologies.orient.server.distributed.impl.structural.OStructuralNodeRequest;
import com.orientechnologies.orient.server.distributed.impl.structural.OStructuralNodeResponse;
import com.orientechnologies.orient.server.distributed.impl.structural.OStructuralSubmitRequest;
import com.orientechnologies.orient.server.distributed.impl.structural.OStructuralSubmitResponse;

public interface ODistributedChannel {
  /**
   * Send submit request, that should go only to coordinator.
   *  @param database
   * @param operationId
   * @param request the request
   */
  void submit(String database, OSessionOperationId operationId, OSubmitRequest request);

  /**
   * Send submit response, this is sent from the coordinator to the node that sent a submit request.
   *  @param database
   * @param operationId
   * @param response
   */
  void reply(String database, OSessionOperationId operationId, OSubmitResponse response);

  /**
   * Send an operation to the node this is used by the coordinator to send operations of the distributed flow.
   *
   * @param database
   * @param id
   * @param nodeRequest
   */
  void sendRequest(String database, OLogId id, ONodeRequest nodeRequest);

  /**
   * Send the response back to the coordinator, this is used by nodes to send a reply to a node request.
   *
   * @param database
   * @param id
   * @param nodeResponse
   */
  void sendResponse(String database, OLogId id, ONodeResponse nodeResponse);

  void sendResponse(OLogId opId, OStructuralNodeResponse response);

  void sendRequest(OLogId id, OStructuralNodeRequest request);

  void reply(OSessionOperationId operationId, OStructuralSubmitResponse response);

  void submit(OSessionOperationId operationId, OStructuralSubmitRequest request);
}
