package com.orientechnologies.orient.distributed.impl.coordinator.lock;

import com.orientechnologies.common.util.OPair;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.distributed.impl.coordinator.ODistributedLockManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ODistributedLockManagerImpl implements ODistributedLockManager {

  private Map<OLockKey, Queue<OWaitingTracker>> locks = new ConcurrentHashMap<>();

  private void lock(OLockKey key, OWaitingTracker waitingTracker) {
    Queue<OWaitingTracker> queue = locks.get(locks);
    if (queue == null) {
      locks.put(key, new LinkedList<>());
    } else {
      queue.add(waitingTracker);
      waitingTracker.waitOne();
    }
  }

  private void unlock(OLockGuard guard) {
    Queue<OWaitingTracker> result = locks.get(guard.getKey());
    assert result != null : guard.getKey().toString();
    OWaitingTracker waiting = result.poll();
    if (waiting != null) {
      waiting.unlockOne();
    } else {
      locks.remove(guard.getKey());
    }
  }

  public synchronized void lock(SortedSet<ORID> rids, SortedSet<OPair<String, String>> indexKeys, OnLocksAcquired acquired) {
    List<OLockGuard> guards = new ArrayList<>();
    OWaitingTracker waitingTracker = new OWaitingTracker(acquired);
    for (ORID rid : rids) {
      ORIDLockKey key = new ORIDLockKey(rid);
      lock(key, waitingTracker);
      guards.add(new OLockGuard(key));
    }

    for (OPair<String, String> indexKey : indexKeys) {
      OIndexKeyLockKey key = new OIndexKeyLockKey(indexKey);
      lock(key, waitingTracker);
      guards.add(new OLockGuard(key));
    }
    waitingTracker.setGuards(guards);
    waitingTracker.acquireIfNoWaiting();
  }

  public synchronized void unlock(List<OLockGuard> guards) {
    for (OLockGuard guard : guards) {
      unlock(guard);
    }
  }
}
