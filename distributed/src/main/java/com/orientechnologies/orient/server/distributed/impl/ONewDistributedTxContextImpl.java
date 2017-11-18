package com.orientechnologies.orient.server.distributed.impl;

import com.orientechnologies.common.concur.lock.OInterruptedException;
import com.orientechnologies.orient.core.db.ODatabaseDocumentInternal;
import com.orientechnologies.orient.core.exception.OConcurrentCreateException;
import com.orientechnologies.orient.core.exception.OConcurrentModificationException;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.id.ORecordId;
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException;
import com.orientechnologies.orient.core.tx.OTransactionInternal;
import com.orientechnologies.orient.server.distributed.ODistributedRequestId;
import com.orientechnologies.orient.server.distributed.ODistributedServerManager;
import com.orientechnologies.orient.server.distributed.ODistributedTxContext;
import com.orientechnologies.orient.server.distributed.task.ORemoteTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ONewDistributedTxContextImpl implements ODistributedTxContext {

  private final ODistributedDatabaseImpl shared;
  private final ODistributedRequestId    id;
  private final OTransactionInternal     tx;
  private final long                     startedOn;
  private final    List<ORID>   lockedRids = new ArrayList<>();
  private final    List<Object> lockedKeys = new ArrayList<>();
  private volatile boolean      began      = true;

  public ONewDistributedTxContextImpl(ODistributedDatabaseImpl shared, ODistributedRequestId reqId, OTransactionInternal tx) {
    this.shared = shared;
    this.id = reqId;
    this.tx = tx;
    this.startedOn = System.currentTimeMillis();

  }

  @Override
  public void lockIndexKey(Object key) {
    shared.getIndexKeyLockManager().lock(key);
    lockedKeys.add(key);
  }

  @Override
  public void lock(ORID rid) {
    shared.getRecordLockManager().lock(rid);
    lockedRids.add(rid);
  }

  @Override
  public void lock(ORID rid, long timeout) {
    //TODO: the timeout is only in the lock manager, this implementation may need evolution
    shared.getRecordLockManager().lock(rid);
    lockedRids.add(rid);
  }

  @Override
  public void addUndoTask(ORemoteTask undoTask) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ODistributedRequestId getReqId() {
    return id;
  }

  @Override
  public synchronized void begin(ODatabaseDocumentInternal database, boolean local) {
    try {
      ((ODatabaseDocumentDistributed) database).internalBegin2pc(this, local);
      began = true;
    } catch (RuntimeException e) {
      began = false;
      unlock();
      throw e;
    }
  }

  @Override
  public synchronized void commit(ODatabaseDocumentInternal database) {
    if (!began) {
      begin(database, false);
    }
    ((ODatabaseDocumentDistributed) database).internalCommit2pc(this);
  }

  @Override
  public void fix(ODatabaseDocumentInternal database, List<ORemoteTask> fixTasks) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Set<ORecordId> rollback(ODatabaseDocumentInternal database) {
    return new HashSet<>();
  }

  @Override
  public void destroy() {
    unlock();
  }

  @Override
  public void clearUndo() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void unlock() {
    for (ORID lockedRid : lockedRids) {
      shared.getRecordLockManager().unlock(lockedRid);
    }
    lockedRids.clear();
    for (Object lockedKey : lockedKeys) {
      shared.getIndexKeyLockManager().unlock(lockedKey);
    }
    lockedKeys.clear();
  }

  @Override
  public long getStartedOn() {
    return startedOn;
  }

  @Override
  public Set<ORecordId> cancel(ODistributedServerManager current, ODatabaseDocumentInternal database) {
    return null;
  }

  @Override
  public boolean isCanceled() {
    return false;
  }

  @Override
  public OTransactionInternal getTransaction() {
    return tx;
  }
}
