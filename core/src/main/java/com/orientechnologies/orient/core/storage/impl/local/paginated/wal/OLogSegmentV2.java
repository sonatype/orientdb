package com.orientechnologies.orient.core.storage.impl.local.paginated.wal;

import com.orientechnologies.common.exception.OException;
import com.orientechnologies.common.io.OFileUtils;
import com.orientechnologies.common.log.OLogManager;
import com.orientechnologies.common.serialization.types.OIntegerSerializer;
import com.orientechnologies.common.util.OPair;
import com.orientechnologies.orient.core.config.OGlobalConfiguration;
import com.orientechnologies.orient.core.exception.OStorageException;
import com.orientechnologies.orient.core.storage.impl.local.statistic.OPerformanceStatisticManager;
import com.orientechnologies.orient.core.storage.impl.local.statistic.OSessionStoragePerformanceStatistic;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

import static com.orientechnologies.common.io.OIOUtils.readByteBuffer;
import static com.orientechnologies.common.io.OIOUtils.writeByteBuffer;

final class OLogSegmentV2 implements OLogSegment {
  private final ODiskWriteAheadLog writeAheadLog;

  /**
   * File which contains WAL segment data. It is <code>null</code> by default and initialized on request.
   * <p>
   * When file is requested and if this file is not file of active WAL segment then timer which will close file if it is not
   * accessed any more in {@link #fileTTL} seconds will be started.
   * <p>
   * This field is not supposed to be accessed directly please use {@link #getRndFile()} instead.
   *
   * @see #closer
   * @see #fileTTL
   */
  private RandomAccessFile rndFile;

  /**
   * Lock which protects {@link #rndFile} access. Any time you call {@link #getRndFile()} you should also acquire this lock.
   */
  private final Lock fileLock = new ReentrantLock();

  /**
   * Flag which indicates if auto close timer is started. This flag is used to guarantee that one and only one instance of auto
   * close timer is active at any moment.
   *
   * @see #rndFile
   */
  private final AtomicBoolean autoCloseInProgress = new AtomicBoolean();

  /**
   * Flag which when is set will prevent auto close timer to close of file. But timer itself will not be stopped.
   *
   * @see #rndFile
   */
  private volatile boolean preventAutoClose = false;

  private final File file;

  /**
   * Flag which indicates that file was accessed inside of {@link #fileTTL} which means that file will not be accessed at least
   * inside of next {@link #fileTTL} interval.
   */
  private volatile boolean closeNextTime;

  /**
   * If {@link #rndFile} will not be accessed inside of this interval (in seconds) it will be closed by timer.
   *
   * @see #rndFile
   */
  private final int fileTTL;

  /**
   * Scheduler which will be used to start timer which will close file if last one will not be accessed inside of {@link #fileTTL}
   * in seconds.
   *
   * @see #rndFile
   */
  private final ScheduledExecutorService closer;

  private final long                         order;
  private final int                          maxPagesCacheSize;
  private final OPerformanceStatisticManager performanceStatisticManager;
  private final    Lock             cacheLock = new ReentrantLock();
  private volatile List<OLogRecord> logCache  = new ArrayList<OLogRecord>();

  private final ScheduledExecutorService commitExecutor;

  private volatile long filledUpTo;
  private volatile long writtenUpTo;

  private boolean closed;
  private OLogSequenceNumber last = null;

  private volatile boolean flushNewData = true;

  private WeakReference<OPair<OLogSequenceNumber, byte[]>> lastReadRecord = new WeakReference<OPair<OLogSequenceNumber, byte[]>>(
      null);

  private final class FlushTask implements Runnable {
    private FlushTask() {
    }

    @Override
    public void run() {
      try {
        try {
          commitLog();
        } catch (Exception e) {
          OLogManager.instance().error(this, "Error during WAL background flush", e);
        }
      } finally {
        writeAheadLog.checkFreeSpace();
      }
    }

    private void commitLog() throws IOException {
      if (!flushNewData)
        return;

      final OSessionStoragePerformanceStatistic statistic = performanceStatisticManager.getSessionPerformanceStatistic();
      if (statistic != null)
        statistic.startWALFlushTimer();
      try {
        flushNewData = false;
        List<OLogRecord> toFlush;
        try {
          cacheLock.lock();
          if (logCache.isEmpty())
            return;

          toFlush = logCache;
          logCache = new ArrayList<OLogRecord>();
        } finally {
          cacheLock.unlock();
        }
        if (toFlush.isEmpty())
          return;

        final ByteBuffer pageContent = ByteBuffer.allocate(OWALPage.PAGE_SIZE).order(ByteOrder.nativeOrder());

        //Position of the last record end of which is stored inside of the page
        //We use it to find the last log record which is flushed for sure at the end of
        //the WAL when we open WAL after storage was closed or crashed.
        long lastRecordPosition = -1;

        //Position of the end of last record inside of the page
        int endOfLastRecord = -1;
        pageContent.putLong(OWALPageV2.LAST_STORED_LSN, lastRecordPosition);
        pageContent.putInt(OWALPageV2.END_LAST_RECORD, endOfLastRecord);

        OLogRecord first = toFlush.get(0);

        long curIndex = first.writeFrom / OWALPage.PAGE_SIZE;

        //used together with lastRecordPosition and endOfLastRecord and indicates index of page which value is contained in lastRecordPosition
        //and endOfLastRecord variables
        long lastRecordPositionPageIndex = curIndex;

        fileLock.lock();
        try {
          final RandomAccessFile rndFile = getRndFile();

          long pagesCount = rndFile.length() / OWALPage.PAGE_SIZE;
          if (pagesCount > curIndex) {
            final FileChannel channel = rndFile.getChannel();

            pageContent.position(0);
            readByteBuffer(pageContent, channel, curIndex * OWALPage.PAGE_SIZE, true);

            //reread the value of last updated page otherwise it will be overwritten
            //by -1 later
            lastRecordPosition = pageContent.getLong(OWALPageV2.LAST_STORED_LSN);
            endOfLastRecord = pageContent.getInt(OWALPageV2.END_LAST_RECORD);
          }
        } finally {
          fileLock.unlock();
        }

        OLogSequenceNumber lsn = null;
        long pageIndex = 0;

        int pos;
        boolean lastToFlush = false;

        long lastPos = 0;

        for (OLogRecord log : toFlush) {
          lsn = new OLogSequenceNumber(order, log.writeFrom);
          pos = (int) (log.writeFrom % OWALPage.PAGE_SIZE);

          pageIndex = log.writeFrom / OWALPage.PAGE_SIZE;
          //if page index is changed to new one, then we do not have any written records
          //in page yet
          if (pageIndex != lastRecordPositionPageIndex) {
            lastRecordPosition = -1;
            endOfLastRecord = -1;
            lastRecordPositionPageIndex = pageIndex;
          }

          int written = 0;

          while (written < log.record.length) {
            lastToFlush = true;
            int pageFreeSpace = OWALPageV2.calculateRecordSize(OWALPage.PAGE_SIZE - pos);
            int contentLength = Math.min(pageFreeSpace, (log.record.length - written));
            int fromRecord = written;
            written += contentLength;

            //end of the record is reached in this page
            //lets remember that and write to the page
            if (written == log.record.length) {
              lastRecordPosition = lsn.getPosition();
              endOfLastRecord = pos + OWALPageV2.calculateSerializedSize(contentLength);
            }

            pos = writeContentInPage(pageContent, pos, log.record, written == log.record.length, fromRecord, contentLength,
                lastRecordPosition, endOfLastRecord);

            if (OWALPage.PAGE_SIZE - pos < OWALPage.MIN_RECORD_SIZE) {
              fileLock.lock();
              try {
                final RandomAccessFile rndFile = getRndFile();
                final FileChannel channel = rndFile.getChannel();
                flushPage(pageContent, channel, pageIndex * OWALPage.PAGE_SIZE);
              } finally {
                fileLock.unlock();
              }

              writtenUpTo = (pageIndex + 1) * OWALPage.PAGE_SIZE - 1;

              lastToFlush = false;
              pageIndex++;

              //new page for sure nothing is written there yet
              lastRecordPosition = -1;
              endOfLastRecord = -1;
              lastRecordPositionPageIndex = pageIndex;

              pos = OWALPageV2.RECORDS_OFFSET;
            }
          }

          lastPos = log.writeTo;
        }
        if (lastToFlush) {
          fileLock.lock();
          try {
            RandomAccessFile rndFile = getRndFile();
            final FileChannel channel = rndFile.getChannel();
            flushPage(pageContent, channel, pageIndex * OWALPage.PAGE_SIZE);
          } finally {
            fileLock.unlock();
          }

          writtenUpTo = lastPos;
        }
        if (OGlobalConfiguration.WAL_SYNC_ON_PAGE_FLUSH.getValueAsBoolean()) {
          fileLock.lock();
          try {
            final RandomAccessFile rndFile = getRndFile();
            rndFile.getFD().sync();
          } finally {
            fileLock.unlock();
          }
        }

        writeAheadLog.setFlushedLsn(lsn);
      } finally {
        if (statistic != null)
          statistic.stopWALFlushTimer();
      }
    }

  }

  /**
   * Write the content in the page and return the new page cursor position.
   *
   * @param pageContent     buffer of the page to be filled
   * @param posInPage       position in the page where to write
   * @param log             content to write to the page
   * @param isLast          flag to mark if is last portion of the record
   * @param fromRecord      the start of the portion of the record to write in this page
   * @param contentLength   the length of the portion of the record to write in this page
   * @param position        Position part of LSN of last stored record
   * @param endOfLastRecord End position of last record end of which is written in this page
   *
   * @return the new page cursor  position after this write.
   */
  private int writeContentInPage(ByteBuffer pageContent, int posInPage, byte[] log, boolean isLast, int fromRecord,
      int contentLength, long position, int endOfLastRecord) {
    pageContent.put(posInPage, !isLast ? (byte) 1 : 0);
    pageContent.put(posInPage + 1, isLast ? (byte) 1 : 0);
    pageContent.putInt(posInPage + 2, contentLength);
    pageContent.position(posInPage + OIntegerSerializer.INT_SIZE + 2);
    pageContent.put(log, fromRecord, contentLength);
    posInPage += OWALPageV2.calculateSerializedSize(contentLength);
    pageContent.putInt(OWALPage.FREE_SPACE_OFFSET, OWALPage.PAGE_SIZE - posInPage);

    pageContent.putLong(OWALPageV2.LAST_STORED_LSN, position);
    pageContent.putInt(OWALPageV2.END_LAST_RECORD, endOfLastRecord);

    return posInPage;
  }

  private void flushPage(ByteBuffer content, FileChannel channel, long position) throws IOException {
    content.putLong(OWALPage.MAGIC_NUMBER_OFFSET, OWALPageV2.MAGIC_NUMBER);
    CRC32 crc32 = new CRC32();
    byte[] data = new byte[OWALPage.PAGE_SIZE - OIntegerSerializer.INT_SIZE];
    content.position(OWALPage.MAGIC_NUMBER_OFFSET);
    content.get(data, 0, data.length);

    crc32.update(data);
    content.putInt(0, (int) crc32.getValue());

    content.position(0);
    writeByteBuffer(content, channel, position);
  }

  OLogSegmentV2(ODiskWriteAheadLog writeAheadLog, File file, int fileTTL, int maxPagesCacheSize,
      OPerformanceStatisticManager performanceStatisticManager, ScheduledExecutorService closer,
      ScheduledExecutorService commitExecutor) {
    this.writeAheadLog = writeAheadLog;
    this.file = file;
    this.fileTTL = fileTTL;
    this.maxPagesCacheSize = maxPagesCacheSize;
    this.performanceStatisticManager = performanceStatisticManager;
    this.closer = closer;
    this.commitExecutor = commitExecutor;

    order = extractOrder(file.getName());
    closed = false;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void startBackgroundWrite() {
    if (writeAheadLog.getCommitDelay() > 0) {
      commitExecutor.scheduleAtFixedRate(new FlushTask(), writeAheadLog.getCommitDelay(), writeAheadLog.getCommitDelay(),
          TimeUnit.MILLISECONDS);

      //if WAL segment is active (all content is written in this segment) we should not try to close it after TTL.
      preventAutoClose = true;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void stopBackgroundWrite(boolean flush) {
    if (flush)
      flush();

    if (!commitExecutor.isShutdown()) {
      commitExecutor.shutdown();
      try {
        if (!commitExecutor.awaitTermination(OGlobalConfiguration.WAL_SHUTDOWN_TIMEOUT.getValueAsInteger(), TimeUnit.MILLISECONDS))
          throw new OStorageException("WAL flush task for '" + getPath() + "' segment cannot be stopped");

      } catch (InterruptedException e) {
        OLogManager.instance().error(this, "Cannot shutdown background WAL commit thread", e);
      }
    }

    //segment is not active any more we should start file auto close
    preventAutoClose = false;
  }

  /**
   * Returns active instance of file which is associated with given WAL segment Call of this method should always be protected by
   * {@link #fileLock}.
   *
   * @return Active instance of file which is associated with given WAL segment
   */
  private RandomAccessFile getRndFile() throws IOException {
    if (rndFile == null) {
      rndFile = new RandomAccessFile(file, "rw");
      scheduleFileAutoClose();
    } else {
      closeNextTime = false;
    }

    return rndFile;
  }

  /**
   * Start timer thread which will auto close file if it is not accesses during {@link #fileTTL} seconds. If file is already closed
   * timer thread will be terminate itself till it will not be started again by {@link #getRndFile()} call.
   */
  private void scheduleFileAutoClose() {
    if (!autoCloseInProgress.get() && autoCloseInProgress.compareAndSet(false, true)) {
      closeNextTime = true;
      final FileCloser task = new FileCloser();
      task.self = closer.scheduleWithFixedDelay(task, fileTTL, fileTTL, TimeUnit.SECONDS);

    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long getOrder() {
    return order;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void init() throws IOException {
    long currentPage;

    //position of LSN of last record which is fully written to the WAL
    long lastPosition;

    final ByteBuffer buffer = ByteBuffer.allocate(OWALPage.PAGE_SIZE).order(ByteOrder.nativeOrder());

    fileLock.lock();
    try {
      final RandomAccessFile rndFile = getRndFile();
      final FileChannel channel = rndFile.getChannel();

      final long pages = rndFile.length() / OWALPage.PAGE_SIZE;

      //WAL write is crashed in the middle of page
      if (rndFile.length() % OWALPage.PAGE_SIZE > 0) {
        OLogManager.instance().error(this, "Last WAL page was written partially, auto fix", null);
        channel.truncate(pages * OWALPage.PAGE_SIZE);
      }

      if (pages == 0) {
        last = null;
        filledUpTo = 0;

        return;
      }

      currentPage = pages;

      //find first not broken page starting from the end which contains
      //the last fully written WAL record, sure there may be some broken pages in the middle
      //but in such way we will truncate all broken pages at the end
      while (true) {
        currentPage--;

        if (currentPage < 0) {
          last = null;
          filledUpTo = 0;

          OLogManager.instance()
              .error(this, "%d pages in WAL segment %s are broken and will be truncated, some data will be lost after restore.",
                  null, pages, file.getName());

          channel.truncate(0);
          channel.force(true);

          return;
        }

        buffer.position(0);
        readByteBuffer(buffer, channel, currentPage * OWALPage.PAGE_SIZE, false);

        if (pageIsBroken(buffer)) {
          OLogManager.instance()
              .warn(this, "Page %d is broken in WAL segment %d and will be truncated", currentPage, currentPage, order);
        } else {
          lastPosition = buffer.getLong(OWALPageV2.LAST_STORED_LSN);
          if (lastPosition >= 0) {
            break;
          }
        }
      }

      if (currentPage + 1 < pages) {
        OLogManager.instance()
            .error(this, "Last %d pages in WAL segment %s are broken and will be truncate, some data will be lost after restore.",
                null, pages - currentPage - 1, file.getName());

        channel.truncate((currentPage + 1) * OWALPage.PAGE_SIZE);
        channel.force(true);
      }

      final int lastRecordEnd = buffer.getInt(OWALPageV2.END_LAST_RECORD);
      final int freeSpaceOffset = buffer.getInt(OWALPage.FREE_SPACE_OFFSET);

      //write to the WAL is truncated right after we write first page and going to write
      //second page all pages will be not broken but WAL itself is broken we can detect it by the fact that
      //last page is completely written but end of last record does not match the amount of free space
      if (OWALPage.PAGE_SIZE - lastRecordEnd != freeSpaceOffset) {
        OLogManager.instance().error(this, "For the page '%d' of WAL segment '%s' amount of free space '%d' does not match"
                + " the end of last record in page '%d' it will be fixed automatically but may lead to data loss during recovery after crash",
            null, currentPage, file.getName(), freeSpaceOffset, lastRecordEnd);
        buffer.putInt(OWALPage.FREE_SPACE_OFFSET, OWALPage.PAGE_SIZE - lastRecordEnd);
        buffer.position(0);

        flushPage(buffer, channel, currentPage * OWALPage.PAGE_SIZE);
        channel.force(true);
      }

    } finally {
      fileLock.unlock();
    }

    last = new OLogSequenceNumber(order, lastPosition);
    final int freeSpace = buffer.getInt(OWALPage.FREE_SPACE_OFFSET);
    filledUpTo = currentPage * OWALPage.PAGE_SIZE + (OWALPage.PAGE_SIZE - freeSpace);
  }

  @Override
  public int compareTo(@SuppressWarnings("NullableProblems") final OLogSegment other) {
    final long otherOrder = other.getOrder();

    if (order > otherOrder)
      return 1;
    else if (order < otherOrder)
      return -1;

    return 0;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    final OLogSegmentV2 that = (OLogSegmentV2) o;

    return order == that.order;

  }

  @Override
  public int hashCode() {
    return (int) (order ^ (order >>> 32));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long filledUpTo() {
    return filledUpTo;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public OLogSequenceNumber begin() throws IOException {
    if (!logCache.isEmpty())
      return new OLogSequenceNumber(order, OWALPageV2.RECORDS_OFFSET);

    fileLock.lock();
    try {
      final RandomAccessFile rndFile = getRndFile();

      if (rndFile.length() > 0)
        return new OLogSequenceNumber(order, OWALPageV2.RECORDS_OFFSET);

    } finally {
      fileLock.unlock();
    }

    return null;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public OLogSequenceNumber end() {
    return last;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void delete() throws IOException {
    close(false);

    boolean deleted = OFileUtils.delete(file);
    int retryCount = 0;

    while (!deleted) {
      deleted = OFileUtils.delete(file);
      retryCount++;

      if (retryCount > 10)
        throw new IOException("Cannot delete file. Retry limit exceeded. (" + retryCount + ")");
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getPath() {
    return file.getAbsolutePath();
  }

  static class OLogRecord {
    final byte[] record;
    final long   writeFrom;
    final long   writeTo;

    OLogRecord(byte[] record, long writeFrom, long writeTo) {
      this.record = record;
      this.writeFrom = writeFrom;
      this.writeTo = writeTo;
    }
  }

  static OLogRecord generateLogRecord(final long starting, final byte[] record) {
    long from = starting;
    long length = record.length;
    long resultSize;
    int freePageSpace = OWALPage.PAGE_SIZE - (int) Math.max(starting % OWALPage.PAGE_SIZE, OWALPageV2.RECORDS_OFFSET);
    int inPage = OWALPageV2.calculateRecordSize(freePageSpace);
    //the record fit in the current page
    if (inPage >= length) {
      resultSize = OWALPageV2.calculateSerializedSize((int) length);
      if (from % OWALPage.PAGE_SIZE == 0)
        from += OWALPageV2.RECORDS_OFFSET;
      return new OLogRecord(record, from, from + resultSize);
    } else {
      if (inPage > 0) {
        //space left in the current page, take it
        length -= inPage;
        resultSize = freePageSpace;
        if (from % OWALPage.PAGE_SIZE == 0)
          from += OWALPageV2.RECORDS_OFFSET;
      } else {
        //no space left, start from a new one.
        from = starting + freePageSpace + OWALPageV2.RECORDS_OFFSET;
        resultSize = -OWALPageV2.RECORDS_OFFSET;
      }

      //calculate spare page
      //add all the full pages
      resultSize += length / OWALPageV2.calculateRecordSize(OWALPageV2.MAX_ENTRY_SIZE) * OWALPage.PAGE_SIZE;

      int leftSize = (int) length % OWALPageV2.calculateRecordSize(OWALPageV2.MAX_ENTRY_SIZE);
      if (leftSize > 0) {
        //add the spare bytes at the last page
        resultSize += OWALPageV2.RECORDS_OFFSET + OWALPageV2.calculateSerializedSize(leftSize);
      }

      return new OLogRecord(record, from, from + resultSize);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public OLogSequenceNumber logRecord(byte[] record) {
    flushNewData = true;

    OLogRecord rec = generateLogRecord(filledUpTo, record);
    filledUpTo = rec.writeTo;
    last = new OLogSequenceNumber(order, rec.writeFrom);
    try {
      cacheLock.lock();
      logCache.add(rec);
    } finally {
      cacheLock.unlock();

    }

    long pagesInCache = (filledUpTo - writtenUpTo) / OWALPage.PAGE_SIZE;
    if (pagesInCache > maxPagesCacheSize) {
      OLogManager.instance()
          .info(this, "Max cache limit is reached (%d vs. %d), sync flush is performed", maxPagesCacheSize, pagesInCache);

      writeAheadLog.incrementCacheOverflowCount();

      flush();
    }
    return last;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @SuppressFBWarnings(value = "PZLA_PREFER_ZERO_LENGTH_ARRAYS")
  public byte[] readRecord(OLogSequenceNumber lsn) throws IOException {
    final OPair<OLogSequenceNumber, byte[]> lastRecord = lastReadRecord.get();
    if (lastRecord != null && lastRecord.getKey().equals(lsn))
      return lastRecord.getValue();

    assert lsn.getSegment() == order;
    if (lsn.getPosition() >= filledUpTo)
      return null;

    if (!logCache.isEmpty())
      flush();

    long pageIndex = lsn.getPosition() / OWALPage.PAGE_SIZE;

    byte[] record = null;
    int pageOffset = (int) (lsn.getPosition() % OWALPage.PAGE_SIZE);

    long pageCount = (filledUpTo + OWALPage.PAGE_SIZE - 1) / OWALPage.PAGE_SIZE;

    final ByteBuffer buffer = ByteBuffer.allocate(OWALPage.PAGE_SIZE).order(ByteOrder.nativeOrder());
    while (pageIndex < pageCount) {
      fileLock.lock();
      try {
        final RandomAccessFile rndFile = getRndFile();
        final FileChannel channel = rndFile.getChannel();

        buffer.position(0);
        readByteBuffer(buffer, channel, pageIndex * OWALPage.PAGE_SIZE, false);
      } finally {
        fileLock.unlock();
      }

      if (pageIsBroken(buffer))
        throw new OWALPageBrokenException("WAL page with index " + pageIndex + " is broken");

      OWALPage page = new OWALPageV2(buffer, false);

      byte[] content = page.getRecord(pageOffset);
      if (record == null)
        record = content;
      else {
        byte[] oldRecord = record;

        record = new byte[record.length + content.length];
        System.arraycopy(oldRecord, 0, record, 0, oldRecord.length);
        System.arraycopy(content, 0, record, oldRecord.length, record.length - oldRecord.length);
      }

      if (page.mergeWithNextPage(pageOffset)) {
        pageOffset = OWALPageV2.RECORDS_OFFSET;
        pageIndex++;
        if (pageIndex >= pageCount)
          throw new OWALPageBrokenException("WAL page with index " + pageIndex + " is broken");
      } else {
        if (page.getFreeSpace() >= OWALPage.MIN_RECORD_SIZE && pageIndex < pageCount - 1)
          throw new OWALPageBrokenException("WAL page with index " + pageIndex + " is broken");

        break;
      }
    }

    lastReadRecord = new WeakReference<OPair<OLogSequenceNumber, byte[]>>(new OPair<OLogSequenceNumber, byte[]>(lsn, record));
    return record;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public OLogSequenceNumber getNextLSN(OLogSequenceNumber lsn) throws IOException {
    final byte[] record = readRecord(lsn);
    if (record == null)
      return null;

    long pos = lsn.getPosition();
    long pageIndex = pos / OWALPage.PAGE_SIZE;
    int pageOffset = (int) (pos - pageIndex * OWALPage.PAGE_SIZE);

    int restOfRecord = record.length;
    while (restOfRecord > 0) {
      int entrySize = OWALPageV2.calculateSerializedSize(restOfRecord);
      if (entrySize + pageOffset < OWALPage.PAGE_SIZE) {
        if (entrySize + pageOffset <= OWALPage.PAGE_SIZE - OWALPage.MIN_RECORD_SIZE)
          pos += entrySize;
        else
          pos += OWALPage.PAGE_SIZE - pageOffset + OWALPageV2.RECORDS_OFFSET;
        break;
      } else if (entrySize + pageOffset == OWALPage.PAGE_SIZE) {
        pos += entrySize + OWALPageV2.RECORDS_OFFSET;
        break;
      } else {
        long chunkSize = OWALPageV2.calculateRecordSize(OWALPage.PAGE_SIZE - pageOffset);
        restOfRecord -= chunkSize;

        pos += OWALPage.PAGE_SIZE - pageOffset + OWALPageV2.RECORDS_OFFSET;
        pageOffset = OWALPageV2.RECORDS_OFFSET;
      }
    }

    if (pos >= filledUpTo)
      return null;

    return new OLogSequenceNumber(order, pos);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void close(boolean flush) throws IOException {
    if (!closed) {
      lastReadRecord.clear();

      stopBackgroundWrite(flush);

      if (!closer.isShutdown()) {
        closer.shutdown();
        try {
          if (!closer.awaitTermination(OGlobalConfiguration.WAL_SHUTDOWN_TIMEOUT.getValueAsInteger(), TimeUnit.MILLISECONDS))
            throw new OStorageException("WAL file auto close task '" + getPath() + "' cannot be stopped");

        } catch (InterruptedException e) {
          OLogManager.instance().error(this, "Shutdown of file auto close thread was interrupted", e);
        }
      }

      fileLock.lock();
      try {
        if (rndFile != null) {
          rndFile.close();
          rndFile = null;
        }
      } finally {
        fileLock.unlock();
      }

      closed = true;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void flush() {
    if (!commitExecutor.isShutdown()) {
      try {
        commitExecutor.submit(new FlushTask()).get();
      } catch (RejectedExecutionException e) {
        if (!commitExecutor.isShutdown()) {
          throw OException.wrapException(new OStorageException("Unable to flush data"), e);
        } else {
          new FlushTask().run();
        }
      } catch (InterruptedException e) {
        throw OException.wrapException(new OStorageException("Thread was interrupted during flush"), e);
      } catch (ExecutionException e) {
        throw OException.wrapException(new OStorageException("Error during WAL segment '" + getPath() + "' flush"), e);
      }
    } else {
      new FlushTask().run();
    }
  }

  private long extractOrder(String name) {
    final Matcher matcher = Pattern.compile("^.*\\.(\\d+)\\.wal$").matcher(name);

    final boolean matches = matcher.find();
    assert matches;

    final String order = matcher.group(1);
    try {
      return Long.parseLong(order);
    } catch (NumberFormatException e) {
      // never happen
      throw new IllegalStateException(e);
    }
  }

  private boolean pageIsBroken(ByteBuffer content) {
    final long magicNumber = content.getLong(OWALPage.MAGIC_NUMBER_OFFSET);
    if (magicNumber != OWALPageV2.MAGIC_NUMBER)
      return true;

    byte[] data = new byte[OWALPage.PAGE_SIZE - OIntegerSerializer.INT_SIZE];
    content.position(OWALPage.MAGIC_NUMBER_OFFSET);
    content.get(data, 0, data.length);

    final CRC32 crc32 = new CRC32();
    crc32.update(data);

    return ((int) crc32.getValue()) != content.getInt(0);
  }

  /**
   * Timer task which is used to close file if it is not accessed during {@link #fileTTL} interval.
   */
  class FileCloser implements Runnable {
    private          boolean            stopped = false;
    private volatile ScheduledFuture<?> self    = null;

    @Override
    public void run() {
      if (stopped) {
        //this task is finished we should stop its execution
        if (self != null) {
          self.cancel(false);
        }

        return;
      }

      if (preventAutoClose) {
        return;
      }

      fileLock.lock();
      try {
        if (closeNextTime) {
          try {
            if (rndFile != null) {
              rndFile.close();
              rndFile = null;
            }
          } catch (IOException e) {
            OLogManager.instance().error(this, "Can not auto close file in WAL", e);
          }

          autoCloseInProgress.set(false);
          stopped = true;

          if (self != null)
            self.cancel(false);

        } else {
          //reschedule himself
          closeNextTime = true;
        }
      } finally {
        fileLock.unlock();
      }
    }
  }
}
