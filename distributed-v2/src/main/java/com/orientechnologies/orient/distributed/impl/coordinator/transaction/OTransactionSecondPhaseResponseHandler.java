package com.orientechnologies.orient.distributed.impl.coordinator.transaction;

import com.orientechnologies.orient.distributed.impl.coordinator.*;
import com.orientechnologies.orient.distributed.impl.coordinator.lock.OLockGuard;

import java.util.List;

public class OTransactionSecondPhaseResponseHandler implements OResponseHandler {

  private       OTransactionSubmit  request;
  private       ODistributedMember  requester;
  private final boolean             success;
  private       int                 responseCount = 0;
  private final List<OLockGuard>    guards;
  private       OSessionOperationId operationId;
  private       boolean             replySent     = false;

  public OTransactionSecondPhaseResponseHandler(boolean success, OTransactionSubmit request, ODistributedMember requester,
      List<OLockGuard> guards, OSessionOperationId operationId) {
    this.success = success;
    this.request = request;
    this.requester = requester;
    this.guards = guards;
    this.operationId = operationId;
  }

  @Override
  public boolean receive(ODistributedCoordinator coordinator, ORequestContext context, ODistributedMember member,
      ONodeResponse response) {
    responseCount++;
    if (responseCount >= context.getQuorum()) {
      OTransactionSecondPhaseResponse values = (OTransactionSecondPhaseResponse) response;
      if (success) {
        if (!replySent) {
          if (guards != null) {
            coordinator.getLockManager().unlock(guards);
          }
          coordinator.reply(requester, operationId,
              new OTransactionResponse(true, values.getCreatedRecords(), values.getUpdatedRecords(), values.getDeletedRecords()));
          replySent = true;
        }
      }
    }
    return responseCount == context.getInvolvedMembers().size();
  }

  @Override
  public boolean timeout(ODistributedCoordinator coordinator, ORequestContext context) {
    return false;
  }
}
