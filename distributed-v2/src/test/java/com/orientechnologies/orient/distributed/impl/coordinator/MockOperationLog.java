package com.orientechnologies.orient.distributed.impl.coordinator;

import java.util.concurrent.atomic.AtomicLong;

public class MockOperationLog implements OOperationLog {
  private AtomicLong sequence = new AtomicLong(0);

  @Override
  public OLogId log(OLogRequest request) {
    return new OLogId(sequence.incrementAndGet());
  }

  @Override
  public void logReceived(OLogId logId, OLogRequest request) {

  }
}
