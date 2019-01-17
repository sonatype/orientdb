package com.orientechnologies.orient.server.distributed.impl.structural;

import com.orientechnologies.orient.server.distributed.impl.coordinator.*;
import com.orientechnologies.orient.server.distributed.impl.coordinator.transaction.OSessionOperationId;

public class OStructuralLoopBackDistributeDistributedMember extends OStructuralDistributedMember {
  private       OStructuralSubmitContext       submitContext;
  private       OStructuralCoordinator         coordinator;
  private final OStructuralDistributedExecutor executor;

  public OStructuralLoopBackDistributeDistributedMember(String name, OStructuralSubmitContext submitContext, OStructuralCoordinator coordinator,
      OStructuralDistributedExecutor executor) {
    super(name, null);
    this.submitContext = submitContext;
    this.coordinator = coordinator;
    this.executor = executor;
  }

  public void sendRequest(OLogId id, OStructuralNodeRequest nodeRequest) {
    executor.receive(this, id, nodeRequest);
  }

  public void reply(OSessionOperationId operationId, OStructuralSubmitResponse response) {
    submitContext.receive(operationId, response);
  }

  public void sendResponse(OLogId opId, OStructuralNodeResponse response) {
    coordinator.receive(this, opId, response);
  }

  public void submit(OSessionOperationId operationId, OStructuralSubmitRequest request) {
    coordinator.submit(this, operationId, request);
  }
}
