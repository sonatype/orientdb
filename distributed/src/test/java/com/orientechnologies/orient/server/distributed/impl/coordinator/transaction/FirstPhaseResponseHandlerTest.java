package com.orientechnologies.orient.server.distributed.impl.coordinator.transaction;

import com.orientechnologies.orient.core.id.ORecordId;
import com.orientechnologies.orient.server.distributed.impl.coordinator.*;
import com.orientechnologies.orient.server.distributed.impl.coordinator.transaction.OTransactionFirstPhaseResult.ConcurrentModification;
import com.orientechnologies.orient.server.distributed.impl.coordinator.transaction.OTransactionFirstPhaseResult.Success;
import com.orientechnologies.orient.server.distributed.impl.coordinator.transaction.OTransactionFirstPhaseResult.Type;
import com.orientechnologies.orient.server.distributed.impl.coordinator.transaction.OTransactionFirstPhaseResult.UniqueKeyViolation;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.times;

public class FirstPhaseResponseHandlerTest {

  @Mock
  private ODistributedCoordinator coordinator;

  @Before
  public void before() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testFirstPhaseQuorumSuccess() {
    OSessionOperationId operationId = new OSessionOperationId();
    ODistributedMember member1 = new ODistributedMember("one", null);
    ODistributedMember member2 = new ODistributedMember("two", null);
    ODistributedMember member3 = new ODistributedMember("three", null);
    List<ODistributedMember> members = new ArrayList<>();
    members.add(member1);
    members.add(member2);
    members.add(member3);
    OTransactionFirstPhaseResponseHandler handler = new OTransactionFirstPhaseResponseHandler(operationId, null, member1, null);
    OLogId id = new OLogId(1);
    ORequestContext context = new ORequestContext(null, null, null, members, handler, id);

    handler.receive(coordinator, context, member1, new OTransactionFirstPhaseResult(Type.SUCCESS, new Success(new ArrayList<>())));
    handler.receive(coordinator, context, member2, new OTransactionFirstPhaseResult(Type.SUCCESS, new Success(new ArrayList<>())));
    handler.receive(coordinator, context, member3, new OTransactionFirstPhaseResult(Type.SUCCESS, new Success(new ArrayList<>())));

    Mockito.verify(coordinator, times(1))
        .sendOperation(any(OSubmitRequest.class), eq(new OTransactionSecondPhaseOperation(operationId, true, new ArrayList<>())),
            any(OTransactionSecondPhaseResponseHandler.class));
    Mockito.verify(coordinator, times(0)).reply(same(member1), any(OTransactionResponse.class));
  }

  @Test
  public void testFirstPhaseQuorumCME() {
    OSessionOperationId operationId = new OSessionOperationId();
    ODistributedMember member1 = new ODistributedMember("one", null);
    ODistributedMember member2 = new ODistributedMember("two", null);
    ODistributedMember member3 = new ODistributedMember("three", null);
    List<ODistributedMember> members = new ArrayList<>();
    members.add(member1);
    members.add(member2);
    members.add(member3);
    OTransactionFirstPhaseResponseHandler handler = new OTransactionFirstPhaseResponseHandler(operationId, null, member1, null);
    OLogId id = new OLogId(1);
    ORequestContext context = new ORequestContext(null, null, null, members, handler, id);

    handler.receive(coordinator, context, member1, new OTransactionFirstPhaseResult(Type.CONCURRENT_MODIFICATION_EXCEPTION,
        new ConcurrentModification(new ORecordId(10, 10), 0, 1)));
    handler.receive(coordinator, context, member2, new OTransactionFirstPhaseResult(Type.SUCCESS, null));
    handler.receive(coordinator, context, member3, new OTransactionFirstPhaseResult(Type.CONCURRENT_MODIFICATION_EXCEPTION,
        new ConcurrentModification(new ORecordId(10, 10), 0, 1)));

    Mockito.verify(coordinator, times(1))
        .sendOperation(any(OSubmitRequest.class), eq(new OTransactionSecondPhaseOperation(operationId, false, new ArrayList<>())),
            any(OTransactionSecondPhaseResponseHandler.class));

    Mockito.verify(coordinator, times(1)).reply(same(member1), any(OTransactionResponse.class));
  }

  @Test
  public void testFirstPhaseQuorumUnique() {
    OSessionOperationId operationId = new OSessionOperationId();
    ODistributedMember member1 = new ODistributedMember("one", null);
    ODistributedMember member2 = new ODistributedMember("two", null);
    ODistributedMember member3 = new ODistributedMember("three", null);
    List<ODistributedMember> members = new ArrayList<>();
    members.add(member1);
    members.add(member2);
    members.add(member3);
    OTransactionFirstPhaseResponseHandler handler = new OTransactionFirstPhaseResponseHandler(operationId, null, member1, null);
    OLogId id = new OLogId(1);
    ORequestContext context = new ORequestContext(null, null, null, members, handler, id);

    handler.receive(coordinator, context, member1, new OTransactionFirstPhaseResult(Type.UNIQUE_KEY_VIOLATION,
        new UniqueKeyViolation("Key", new ORecordId(10, 10), new ORecordId(10, 11), "Class.property")));
    handler.receive(coordinator, context, member2, new OTransactionFirstPhaseResult(Type.SUCCESS, null));
    handler.receive(coordinator, context, member3, new OTransactionFirstPhaseResult(Type.UNIQUE_KEY_VIOLATION,
        new UniqueKeyViolation("Key", new ORecordId(10, 10), new ORecordId(10, 11), "Class.property")));

    Mockito.verify(coordinator, times(1))
        .sendOperation(any(OSubmitRequest.class), eq(new OTransactionSecondPhaseOperation(operationId, false, new ArrayList<>())),
            any(OTransactionSecondPhaseResponseHandler.class));

    Mockito.verify(coordinator, times(1)).reply(same(member1), any(OTransactionResponse.class));

  }

}
