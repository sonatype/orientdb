package com.orientechnologies.orient.core.storage.cache;

import com.orientechnologies.orient.core.storage.cache.chm.LRUList;
import com.orientechnologies.orient.core.storage.impl.local.paginated.wal.OLogSequenceNumber;
import com.orientechnologies.orient.core.storage.impl.local.paginated.wal.OWALChanges;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by tglman on 23/06/16.
 */
public class OCacheEntryImpl implements OCacheEntry {
  private static final int FROZEN = -1;
  private static final int DEAD   = -2;

  private       OCachePointer dataPointer;
  private final long          fileId;
  private final long          pageIndex;

  private       boolean       dirty;
  private final AtomicInteger usagesCount = new AtomicInteger();
  private final AtomicInteger state       = new AtomicInteger();

  private OCacheEntry next;
  private OCacheEntry prev;

  private LRUList container;

  public OCacheEntryImpl(final long fileId, final long pageIndex, final OCachePointer dataPointer) {
    this.fileId = fileId;
    this.pageIndex = pageIndex;

    this.dataPointer = dataPointer;
  }

  @Override
  public OCachePointer getCachePointer() {
    return dataPointer;
  }

  @Override
  public void clearCachePointer() {
    dataPointer = null;
  }

  @Override
  public void setCachePointer(final OCachePointer cachePointer) {
    this.dataPointer = cachePointer;
  }

  @Override
  public long getFileId() {
    return fileId;
  }

  @Override
  public long getPageIndex() {
    return pageIndex;
  }

  @Override
  public void acquireExclusiveLock() {
    dataPointer.acquireExclusiveLock();
  }

  @Override
  public void releaseExclusiveLock() {
    dataPointer.releaseExclusiveLock();
  }

  @Override
  public void acquireSharedLock() {
    dataPointer.acquireSharedLock();
  }

  @Override
  public void releaseSharedLock() {
    dataPointer.releaseSharedLock();
  }

  @Override
  public int getUsagesCount() {
    return usagesCount.get();
  }

  @Override
  public void incrementUsages() {
    usagesCount.incrementAndGet();
  }

  /**
   * DEBUG only !!
   *
   * @return Whether lock acquired on current entry
   */
  @Override
  public boolean isLockAcquiredByCurrentThread() {
    return dataPointer.isLockAcquiredByCurrentThread();
  }

  @Override
  public void decrementUsages() {
    usagesCount.decrementAndGet();
  }

  @Override
  public OWALChanges getChanges() {
    return null;
  }

  @Override
  public OLogSequenceNumber getEndLSN() {
    return dataPointer.getEndLSN();
  }

  @Override
  public void setEndLSN(final OLogSequenceNumber endLSN) {
    dataPointer.setEndLSN(endLSN);
  }

  @Override
  public void markDirty() {
    this.dirty = true;
  }

  @Override
  public void clearDirty() {
    this.dirty = false;
  }

  @Override
  public boolean isDirty() {
    return dirty;
  }

  @Override
  public boolean acquireEntry() {
    int state = this.state.get();

    while (state >= 0) {
      if (this.state.compareAndSet(state, state + 1)) {
        return true;
      }

      state = this.state.get();
    }

    return false;
  }

  @Override
  public void releaseEntry() {
    int state = this.state.get();

    while (true) {
      if (state <= 0) {
        throw new IllegalStateException("Cache entry " + fileId + ":" + pageIndex + " has invalid state " + state);
      }

      if (this.state.compareAndSet(state, state - 1)) {
        return;
      }

      state = this.state.get();
    }
  }

  @Override
  public boolean isReleased() {
    return state.get() == 0;
  }

  @Override
  public boolean isAlive() {
    return state.get() >= 0;
  }

  @Override
  public boolean freeze() {
    int state = this.state.get();
    while (state == 0) {
      if (this.state.compareAndSet(state, FROZEN)) {
        return true;
      }

      state = this.state.get();
    }

    return false;
  }

  @Override
  public boolean isFrozen() {
    return this.state.get() == FROZEN;
  }

  @Override
  public void makeDead() {
    int state = this.state.get();

    while (state == FROZEN) {
      if (this.state.compareAndSet(state, DEAD)) {
        return;
      }

      state = this.state.get();
    }

    throw new IllegalStateException("Cache entry " + fileId + ":" + pageIndex + " has invalid state " + state);
  }

  @Override
  public boolean isDead() {
    return this.state.get() == DEAD;
  }

  @Override
  public OCacheEntry getNext() {
    return next;
  }

  @Override
  public OCacheEntry getPrev() {
    return prev;
  }

  @Override
  public void setPrev(final OCacheEntry prev) {
    this.prev = prev;
  }

  @Override
  public void setNext(final OCacheEntry next) {
    this.next = next;
  }

  @Override
  public void setContainer(final LRUList lruList) {
    this.container = lruList;
  }

  @Override
  public LRUList getContainer() {
    return container;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    final OCacheEntryImpl that = (OCacheEntryImpl) o;

    if (fileId != that.fileId)
      return false;
    return pageIndex == that.pageIndex;
  }

  @Override
  public int hashCode() {
    int result = (int) (fileId ^ (fileId >>> 32));
    result = 31 * result + (int) (pageIndex ^ (pageIndex >>> 32));
    return result;
  }

  @Override
  public String toString() {
    return "OCacheEntryImpl{" + "dataPointer=" + dataPointer + ", fileId=" + fileId + ", pageIndex=" + pageIndex + ", usagesCount="
        + usagesCount + '}';
  }
}
