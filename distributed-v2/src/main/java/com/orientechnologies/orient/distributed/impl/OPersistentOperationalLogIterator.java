package com.orientechnologies.orient.distributed.impl;

import com.orientechnologies.orient.distributed.impl.coordinator.OLogId;
import com.orientechnologies.orient.distributed.impl.coordinator.OOperationLogEntry;

import java.io.*;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class OPersistentOperationalLogIterator implements Iterator<OOperationLogEntry> {
  private final OLogId                      from;
  private final OLogId                      to;
  private final OPersistentOperationalLogV1 opLog;

  long nextIdToLoad;

  OOperationLogEntry nextEntry;
  private DataInputStream stream;

  public OPersistentOperationalLogIterator(OPersistentOperationalLogV1 opLog, OLogId from, OLogId to) {
    this.opLog = opLog;
    this.from = from;
    this.to = to;
    if (to == null) {
      throw new IllegalArgumentException("'to' value cannot be null");
    }
    if (from == null) {
      nextIdToLoad = 0L;
    } else {
      nextIdToLoad = from.getId();
    }
  }

  @Override
  public boolean hasNext() {
    if (nextEntry == null) {
      loadNext();
    }
    return nextEntry != null;
  }

  @Override
  public OOperationLogEntry next() {
    if (nextEntry == null) {
      loadNext();
    }
    if (nextEntry == null) {
      throw new NoSuchElementException();
    }
    OOperationLogEntry result = nextEntry;
    nextEntry = null;
    return result;
  }

  private void loadNext() {
    nextEntry = null;
    if (nextIdToLoad > to.getId()) {
      return;
    }
    if (stream == null || nextIdToLoad % OPersistentOperationalLogV1.LOG_ENTRIES_PER_FILE == 0) {
      initStream();
    }
    do {
      nextEntry = opLog.readRecord(stream);
    } while (nextEntry != null && nextEntry.getLogId().getId() < from.getId());

    nextIdToLoad++;

  }

  private void initStream() {
    if (stream != null) {
      try {
        stream.close();
      } catch (IOException e) {
      }
    }
    int fileSuffix = (int) (nextIdToLoad / OPersistentOperationalLogV1.LOG_ENTRIES_PER_FILE);
    File file = new File(opLog.calculateLogFileFullPath(fileSuffix));
    try {
      this.stream = new DataInputStream(new FileInputStream(file));
    } catch (FileNotFoundException e) {
      throw new IllegalStateException("Oplog file not found: " + file.getAbsolutePath());
    }
  }

}
