package com.orientechnologies.orient.server.distributed.impl.coordinator;

import com.orientechnologies.orient.core.db.ODatabaseDocumentInternal;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ODistributedCoordinatorTest {

  @Test
  public void simpleOperationTest() throws InterruptedException {
    CountDownLatch responseReceived = new CountDownLatch(1);
    OOperationLog operationLog = new MockOperationLog();

    ODistributedCoordinator coordinator = new ODistributedCoordinator(Executors.newSingleThreadExecutor(), operationLog, null);
    MockChannel channel = new MockChannel();
    channel.coordinator = coordinator;
    ODistributedMember one = new ODistributedMember("one", channel);
    channel.member = one;
    coordinator.join(one);

    coordinator.submit(one, new OSubmitRequest() {
      @Override
      public void begin(ODistributedMember member, ODistributedCoordinator coordinator) {
        MockNodeRequest nodeRequest = new MockNodeRequest();
        coordinator.sendOperation(this, nodeRequest, new OResponseHandler() {
          @Override
          public boolean receive(ODistributedCoordinator coordinator, ORequestContext context, ODistributedMember member,
              ONodeResponse response) {
            if (context.getResponses().size() == 1) {
              responseReceived.countDown();
            }
            return context.getResponses().size() == context.getInvolvedMembers().size();
          }

          @Override
          public boolean timeout(ODistributedCoordinator coordinator, ORequestContext context) {
            return true;
          }
        });
      }

    });
    assertTrue(responseReceived.await(1, TimeUnit.SECONDS));
    coordinator.close();
    assertEquals(0, coordinator.getContexts().size());
  }

  @Test
  public void testTwoPhase() throws InterruptedException {
    CountDownLatch responseReceived = new CountDownLatch(1);
    OOperationLog operationLog = new MockOperationLog();

    ODistributedCoordinator coordinator = new ODistributedCoordinator(Executors.newSingleThreadExecutor(), operationLog, null);
    MockChannel channel = new MockChannel();
    channel.coordinator = coordinator;
    channel.reply = responseReceived;
    ODistributedMember one = new ODistributedMember("one", channel);
    channel.member = one;
    coordinator.join(one);

    coordinator.submit(one, new OSubmitRequest() {
      @Override
      public void begin(ODistributedMember member, ODistributedCoordinator coordinator) {
        MockNodeRequest nodeRequest = new MockNodeRequest();
        coordinator.sendOperation(this, nodeRequest, new OResponseHandler() {
          @Override
          public boolean receive(ODistributedCoordinator coordinator, ORequestContext context, ODistributedMember member,
              ONodeResponse response) {
            if (context.getResponses().size() == 1) {
              coordinator.sendOperation(null, new ONodeRequest() {
                @Override
                public ONodeResponse execute(ODistributedMember nodeFrom, OLogId opId, ODistributedExecutor executor,
                    ODatabaseDocumentInternal session) {
                  return null;
                }
              }, new OResponseHandler() {
                @Override
                public boolean receive(ODistributedCoordinator coordinator, ORequestContext context, ODistributedMember member,
                    ONodeResponse response) {
                  if (context.getResponses().size() == 1) {
                    member.reply(new OSubmitResponse() {
                    });
                  }
                  return context.getResponses().size() == context.getInvolvedMembers().size();
                }

                @Override
                public boolean timeout(ODistributedCoordinator coordinator, ORequestContext context) {
                  return true;
                }
              });
            }
            return context.getResponses().size() == context.getInvolvedMembers().size();
          }

          @Override
          public boolean timeout(ODistributedCoordinator coordinator, ORequestContext context) {
            return true;
          }
        });
      }
    });

    assertTrue(responseReceived.await(1, TimeUnit.SECONDS));
    coordinator.close();
    assertEquals(0, coordinator.getContexts().size());
  }

  @Test
  public void simpleTimeoutTest() throws InterruptedException {
    CountDownLatch timedOut = new CountDownLatch(1);
    OOperationLog operationLog = new MockOperationLog();

    ODistributedCoordinator coordinator = new ODistributedCoordinator(Executors.newSingleThreadExecutor(), operationLog, null);
    MockChannel channel = new MockChannel();
    channel.coordinator = coordinator;
    ODistributedMember one = new ODistributedMember("one", channel);
    channel.member = one;
    coordinator.join(one);

    coordinator.submit(one, new OSubmitRequest() {
      @Override
      public void begin(ODistributedMember member, ODistributedCoordinator coordinator) {
        MockNodeRequest nodeRequest = new MockNodeRequest();
        coordinator.sendOperation(this, nodeRequest, new OResponseHandler() {
          @Override
          public boolean receive(ODistributedCoordinator coordinator, ORequestContext context, ODistributedMember member,
              ONodeResponse response) {
            return false;
          }

          @Override
          public boolean timeout(ODistributedCoordinator coordinator, ORequestContext context) {
            timedOut.countDown();
            return true;
          }
        });
      }
    });

    //This is 2 seconds because timeout is hard coded with 1 sec now
    assertTrue(timedOut.await(2, TimeUnit.SECONDS));
    coordinator.close();
    assertEquals(0, coordinator.getContexts().size());
  }

  private static class MockChannel implements ODistributedChannel {
    public ODistributedCoordinator coordinator;
    public CountDownLatch          reply;
    public ODistributedMember      member;

    @Override
    public void sendRequest(OLogId id, ONodeRequest request) {
      coordinator.receive(member, id, new ONodeResponse() {
      });
    }

    @Override
    public void sendResponse(OLogId id, ONodeResponse nodeResponse) {
      assertTrue(false);
    }

    @Override
    public void reply(OSubmitResponse response) {
      reply.countDown();
    }
  }

  private static class MockNodeRequest implements ONodeRequest {

    @Override
    public ONodeResponse execute(ODistributedMember nodeFrom, OLogId opId, ODistributedExecutor executor,
        ODatabaseDocumentInternal session) {
      return null;
    }
  }
}
