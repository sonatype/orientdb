/*
 *
 *  *  Copyright 2014 Orient Technologies LTD (info(at)orientechnologies.com)
 *  *
 *  *  Licensed under the Apache License, Version 2.0 (the "License");
 *  *  you may not use this file except in compliance with the License.
 *  *  You may obtain a copy of the License at
 *  *
 *  *       http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  *  Unless required by applicable law or agreed to in writing, software
 *  *  distributed under the License is distributed on an "AS IS" BASIS,
 *  *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *  See the License for the specific language governing permissions and
 *  *  limitations under the License.
 *  *
 *  * For more information: http://www.orientechnologies.com
 *
 */

package com.orientechnologies.common.concur.lock;

import com.orientechnologies.common.types.OModifiableInteger;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.AbstractOwnableSynchronizer;
import java.util.concurrent.locks.LockSupport;

/**
 * @author Andrey Lomakin (a.lomakin-at-orientechnologies.com)
 * @since 8/18/14
 */
@SuppressFBWarnings("SE_TRANSIENT_FIELD_NOT_RESTORED")
public class OReadersWriterSpinLock extends AbstractOwnableSynchronizer implements OReadWriteLock {
  private static final long serialVersionUID = 7975120282194559960L;

  private final transient ODistributedCounter distributedCounter;
  private final transient AtomicReference<WNode>          tail      = new AtomicReference<WNode>();
  private final transient ThreadLocal<OModifiableInteger> lockHolds = new InitOModifiableInteger();

  private final transient ThreadLocal<WNode> myNode   = new InitWNode();
  private final transient ThreadLocal<WNode> predNode = new ThreadLocal<WNode>();

  public OReadersWriterSpinLock() {
    final WNode wNode = new WNode();
    wNode.locked = false;

    tail.set(wNode);

    distributedCounter = new ODistributedCounter();
  }

  public OReadersWriterSpinLock(int concurrencyLevel) {
    final WNode wNode = new WNode();
    wNode.locked = false;

    tail.set(wNode);
    distributedCounter = new ODistributedCounter(concurrencyLevel);
  }

  public void acquireReadLock() {
    final OModifiableInteger lHolds = lockHolds.get();

    final int holds = lHolds.intValue();
    if (holds > 0) {
      // we have already acquire read lock
      lHolds.increment();
      return;
    } else if (holds < 0) {
      // write lock is acquired before, do nothing
      return;
    }

    distributedCounter.increment();

    WNode wNode = tail.get();
    while (wNode.locked) {
      distributedCounter.decrement();

      while (wNode.locked && wNode == tail.get()) {
        wNode.waitingReaders.putIfAbsent(Thread.currentThread(), Boolean.TRUE);

        if (wNode.locked && wNode == tail.get())
          LockSupport.park(this);

        wNode = tail.get();
      }

      distributedCounter.increment();

      wNode = tail.get();
    }

    lHolds.increment();
    assert lHolds.intValue() == 1;
  }

  public void releaseReadLock() {
    final OModifiableInteger lHolds = lockHolds.get();
    final int holds = lHolds.intValue();
    if (holds > 1) {
      lHolds.decrement();
      return;
    } else if (holds < 0) {
      // write lock was acquired before, do nothing
      return;
    }

    distributedCounter.decrement();

    lHolds.decrement();
    assert lHolds.intValue() == 0;
  }

  public void acquireWriteLock() {
    final OModifiableInteger lHolds = lockHolds.get();

    if (lHolds.intValue() < 0) {
      lHolds.decrement();
      return;
    }

    final WNode node = myNode.get();
    node.locked = true;

    final WNode pNode = tail.getAndSet(myNode.get());
    while (pNode.locked) {
      pNode.waitingWriter = Thread.currentThread();

      if (pNode.locked)
        LockSupport.park(this);
    }

    pNode.waitingWriter = null;

    final long beginTime = System.currentTimeMillis();
    while (!distributedCounter.isEmpty()) {
      // IN THE WORST CASE CPU CAN BE 100% FOR MAXIMUM 1 SECOND
      if (System.currentTimeMillis() - beginTime > 1000)
        try {
          Thread.sleep(1);
        } catch (InterruptedException ignore) {
          Thread.currentThread().interrupt();
          break;
        }
    }

    setExclusiveOwnerThread(Thread.currentThread());

    lHolds.decrement();
    assert lHolds.intValue() == -1;
  }

  public void releaseWriteLock() {
    final OModifiableInteger lHolds = lockHolds.get();

    if (lHolds.intValue() < -1) {
      lHolds.increment();
      return;
    }

    setExclusiveOwnerThread(null);

    final WNode node = myNode.get();
    myNode.set(new WNode());

    node.locked = false;

    final Thread waitingWriter = node.waitingWriter;
    if (waitingWriter != null)
      LockSupport.unpark(waitingWriter);

    while (!node.waitingReaders.isEmpty()) {
      final Set<Thread> readers = node.waitingReaders.keySet();
      final Iterator<Thread> threadIterator = readers.iterator();

      while (threadIterator.hasNext()) {
        final Thread reader = threadIterator.next();
        threadIterator.remove();

        LockSupport.unpark(reader);
      }
    }

    lHolds.increment();
    assert lHolds.intValue() == 0;
  }

  private static final class InitWNode extends ThreadLocal<WNode> {
    @Override
    protected WNode initialValue() {
      return new WNode();
    }
  }

  private static final class InitOModifiableInteger extends ThreadLocal<OModifiableInteger> {
    @Override
    protected OModifiableInteger initialValue() {
      return new OModifiableInteger();
    }
  }

  private final static class WNode {
    private final ConcurrentHashMap<Thread, Boolean> waitingReaders = new ConcurrentHashMap<Thread, Boolean>();

    private volatile boolean locked = true;
    private volatile Thread waitingWriter;
  }
}
