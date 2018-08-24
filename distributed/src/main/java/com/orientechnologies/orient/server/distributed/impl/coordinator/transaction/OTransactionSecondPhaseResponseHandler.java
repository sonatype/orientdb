package com.orientechnologies.orient.server.distributed.impl.coordinator.transaction;

import com.orientechnologies.orient.server.distributed.impl.coordinator.*;

import java.util.List;

public class OTransactionSecondPhaseResponseHandler implements OResponseHandler {

  private       OTransactionSubmit request;
  private       ODistributedMember requester;
  private final boolean            success;
  private       int                responseCount = 0;
  private final List<OLockGuard>   guards;

  public OTransactionSecondPhaseResponseHandler(boolean success, OTransactionSubmit request, ODistributedMember requester,
      List<OLockGuard> guards) {
    this.success = success;
    this.request = request;
    this.requester = requester;
    this.guards = guards;
  }

  @Override
  public boolean receive(ODistributedCoordinator coordinator, ORequestContext context, ODistributedMember member,
      ONodeResponse response) {
    responseCount++;
    if (responseCount >= context.getQuorum()) {
      if (success) {
        if (guards != null) {
          for (OLockGuard guard : guards) {
            guard.release();
          }
        }
        coordinator.reply(requester, new OTransactionResponse());
      }
    }
    return responseCount == context.getInvolvedMembers().size();
  }

  @Override
  public boolean timeout(ODistributedCoordinator coordinator, ORequestContext context) {
    return false;
  }
}
