package com.orientechnologies.orient.server.distributed.impl.coordinator.mocktx;

import com.orientechnologies.orient.server.distributed.impl.coordinator.*;

public class SecondPhaseResponseHandler implements OResponseHandler {
  private final OSubmitTx          submitTx;
  private final ODistributedMember member;
  boolean done = false;

  public SecondPhaseResponseHandler(OSubmitTx submitTx, ODistributedMember member) {
    this.member = member;
    this.submitTx = submitTx;
  }

  @Override
  public boolean receive(ODistributedCoordinator coordinator, ORequestContext context1, ODistributedMember member,
      ONodeResponse response) {
    if (context1.getResponses().size() >= context1.getQuorum() && !done) {
      done = true;
      submitTx.secondPhase = true;
      this.member.reply(new OSubmitResponse() {
      });
    }
    return context1.getResponses().size() == context1.getInvolvedMembers().size();
  }

  @Override
  public boolean timeout(ODistributedCoordinator coordinator, ORequestContext context) {
    return true;
  }
}
