package com.orientechnologies.orient.core.storage.index.hashindex.local.cache;

import com.orientechnologies.common.collection.closabledictionary.OClosableLinkedContainer;
import com.orientechnologies.common.directmemory.OByteBufferPool;
import com.orientechnologies.common.serialization.types.OIntegerSerializer;
import com.orientechnologies.common.serialization.types.OLongSerializer;
import com.orientechnologies.common.serialization.types.OStringSerializer;
import com.orientechnologies.common.types.OModifiableBoolean;
import com.orientechnologies.orient.core.config.OGlobalConfiguration;
import com.orientechnologies.orient.core.exception.OStorageException;
import com.orientechnologies.orient.core.storage.OChecksumMode;
import com.orientechnologies.orient.core.storage.cache.OCachePointer;
import com.orientechnologies.orient.core.storage.cache.local.OWOWCache;
import com.orientechnologies.orient.core.storage.fs.OFileClassic;
import com.orientechnologies.orient.core.storage.impl.local.paginated.wal.OAbstractWALRecord;
import com.orientechnologies.orient.core.storage.impl.local.paginated.wal.OLogSequenceNumber;
import com.orientechnologies.orient.core.storage.impl.local.paginated.wal.OWALRecordsFactory;
import com.orientechnologies.orient.core.storage.impl.local.paginated.wal.cas.OCASDiskWriteAheadLog;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Random;
import java.util.TreeMap;

/**
 * @author Andrey Lomakin (a.lomakin-at-orientdb.com)
 * @since 26.07.13
 */
public class WOWCacheTestIT {
  private static final int systemOffset = 2 * (OIntegerSerializer.INT_SIZE + OLongSerializer.LONG_SIZE);
  private static final int pageSize     = systemOffset + 8;

  private static String fileName;

  private static       OCASDiskWriteAheadLog writeAheadLog;
  private static final OByteBufferPool       bufferPool = new OByteBufferPool(pageSize);
  private static       Path                  storagePath;
  private static       OWOWCache             wowCache;
  private static       String                storageName;

  private final OClosableLinkedContainer<Long, OFileClassic> files = new OClosableLinkedContainer<>(1024);

  @BeforeClass
  public static void beforeClass() {
    OGlobalConfiguration.STORAGE_EXCLUSIVE_FILE_ACCESS.setValue(Boolean.FALSE);
    OGlobalConfiguration.FILE_LOCK.setValue(Boolean.FALSE);
    String buildDirectory = System.getProperty("buildDirectory", ".");

    fileName = "wowCacheTest.tst";
    storageName = "WOWCacheTest";
    storagePath = Paths.get(buildDirectory).resolve(storageName);

    OWALRecordsFactory.INSTANCE.registerNewRecord((byte) 128, TestRecord.class);
  }

  @Before
  public void beforeMethod() throws Exception {
    deleteCacheAndDeleteFile();

    initBuffer();
  }

  private static void deleteCacheAndDeleteFile() throws IOException {
    String nativeFileName = null;

    if (wowCache != null) {
      long fileId = wowCache.fileIdByName(fileName);
      nativeFileName = wowCache.nativeFileNameById(fileId);

      wowCache.delete();
      wowCache = null;
    }

    if (writeAheadLog != null) {
      writeAheadLog.delete();
      writeAheadLog = null;
    }

    if (nativeFileName != null) {
      File testFile = storagePath.resolve(nativeFileName).toFile();

      if (testFile.exists()) {
        Assert.assertTrue(testFile.delete());
      }
    }

    File nameIdMapFile = storagePath.resolve("name_id_map.cm").toFile();
    if (nameIdMapFile.exists()) {
      Assert.assertTrue(nameIdMapFile.delete());
    }

    nameIdMapFile = storagePath.resolve("name_id_map_v2.cm").toFile();
    if (nameIdMapFile.exists()) {
      Assert.assertTrue(nameIdMapFile.delete());
    }
  }

  @AfterClass
  public static void afterClass() throws IOException {
    deleteCacheAndDeleteFile();

    Files.deleteIfExists(storagePath);
    bufferPool.clear();

    OGlobalConfiguration.STORAGE_EXCLUSIVE_FILE_ACCESS.setValue(Boolean.TRUE);
    OGlobalConfiguration.FILE_LOCK.setValue(Boolean.TRUE);
  }

  private void initBuffer() throws IOException, InterruptedException {
    Files.createDirectories(storagePath);

    writeAheadLog = new OCASDiskWriteAheadLog(storageName, storagePath, storagePath, 12_000, 128, Integer.MAX_VALUE,
        Integer.MAX_VALUE, 25, true, Locale.US, -1, 1024L * 1024 * 1024, 1000, true, false, true, 10);
    wowCache = new OWOWCache(pageSize, bufferPool, writeAheadLog, 10, 10, 100, storagePath, storageName, OStringSerializer.INSTANCE,
        files, 1, OChecksumMode.StoreAndVerify, false, true, 10);

    wowCache.loadRegisteredFiles();
  }

  @Test
  public void testLoadStore() throws IOException {
    Random random = new Random();

    byte[][] pageData = new byte[200][];
    long fileId = wowCache.addFile(fileName);
    final String nativeFileName = wowCache.nativeFileNameById(fileId);

    for (int i = 0; i < pageData.length; i++) {
      byte[] data = new byte[8];
      random.nextBytes(data);

      pageData[i] = data;

      final int pageIndex = wowCache.allocateNewPage(fileId);
      Assert.assertEquals(i, pageIndex);
      final OCachePointer cachePointer = wowCache.load(fileId, i, 1, new OModifiableBoolean(), false)[0];
      cachePointer.acquireExclusiveLock();

      ByteBuffer buffer = cachePointer.getBufferDuplicate();
      buffer.position(systemOffset);
      buffer.put(data);
      cachePointer.releaseExclusiveLock();

      wowCache.store(fileId, i, cachePointer);
      cachePointer.decrementReadersReferrer();
    }

    for (int i = 0; i < pageData.length; i++) {
      byte[] dataOne = pageData[i];

      OCachePointer cachePointer = wowCache.load(fileId, i, 1, new OModifiableBoolean(), true)[0];
      byte[] dataTwo = new byte[8];
      ByteBuffer buffer = cachePointer.getBufferDuplicate();
      buffer.position(systemOffset);
      buffer.get(dataTwo);
      cachePointer.decrementReadersReferrer();

      Assert.assertArrayEquals(dataTwo, dataOne);
    }

    wowCache.flush();

    for (int i = 0; i < pageData.length; i++) {
      byte[] dataContent = pageData[i];
      assertFile(i, dataContent, new OLogSequenceNumber(0, 0), nativeFileName);
    }
  }

  @Test
  public void testDataUpdate() throws Exception {
    final NavigableMap<Long, byte[]> pageIndexDataMap = new TreeMap<>();
    long fileId = wowCache.addFile(fileName);
    final String nativeFileName = wowCache.nativeFileNameById(fileId);

    Random random = new Random();

    for (int i = 0; i < 2048; i++) {
      wowCache.allocateNewPage(fileId);
    }

    for (int i = 0; i < 600; i++) {
      long pageIndex = random.nextInt(2048);

      byte[] data = new byte[8];
      random.nextBytes(data);

      pageIndexDataMap.put(pageIndex, data);

      final OCachePointer cachePointer = wowCache.load(fileId, pageIndex, 1, new OModifiableBoolean(), false)[0];
      cachePointer.acquireExclusiveLock();
      ByteBuffer buffer = cachePointer.getBufferDuplicate();
      buffer.position(systemOffset);
      buffer.put(data);
      cachePointer.releaseExclusiveLock();

      wowCache.store(fileId, pageIndex, cachePointer);
      cachePointer.decrementReadersReferrer();
    }

    for (Map.Entry<Long, byte[]> entry : pageIndexDataMap.entrySet()) {
      long pageIndex = entry.getKey();
      byte[] dataOne = entry.getValue();

      OCachePointer cachePointer = wowCache.load(fileId, pageIndex, 1, new OModifiableBoolean(), true)[0];
      byte[] dataTwo = new byte[8];
      ByteBuffer buffer = cachePointer.getBufferDuplicate();
      buffer.position(systemOffset);
      buffer.get(dataTwo);

      cachePointer.decrementReadersReferrer();
      Assert.assertArrayEquals(dataTwo, dataOne);
    }

    for (int i = 0; i < 300; i++) {
      long desiredIndex = random.nextInt(2048);

      Long pageIndex = pageIndexDataMap.ceilingKey(desiredIndex);
      if (pageIndex == null)
        pageIndex = pageIndexDataMap.floorKey(desiredIndex);

      byte[] data = new byte[8];
      random.nextBytes(data);
      pageIndexDataMap.put(pageIndex, data);

      final OCachePointer cachePointer = wowCache.load(fileId, pageIndex, 1, new OModifiableBoolean(), true)[0];

      cachePointer.acquireExclusiveLock();
      ByteBuffer buffer = cachePointer.getBufferDuplicate();
      buffer.position(systemOffset);
      buffer.put(data);
      cachePointer.releaseExclusiveLock();

      wowCache.store(fileId, pageIndex, cachePointer);
      cachePointer.decrementReadersReferrer();
    }

    for (Map.Entry<Long, byte[]> entry : pageIndexDataMap.entrySet()) {
      long pageIndex = entry.getKey();
      byte[] dataOne = entry.getValue();
      OCachePointer cachePointer = wowCache.load(fileId, pageIndex, 1, new OModifiableBoolean(), true)[0];
      byte[] dataTwo = new byte[8];
      ByteBuffer buffer = cachePointer.getBufferDuplicate();
      buffer.position(systemOffset);
      buffer.get(dataTwo);
      cachePointer.decrementReadersReferrer();

      Assert.assertArrayEquals(dataTwo, dataOne);
    }

    wowCache.flush();

    for (Map.Entry<Long, byte[]> entry : pageIndexDataMap.entrySet()) {
      assertFile(entry.getKey(), entry.getValue(), new OLogSequenceNumber(0, 0), nativeFileName);
    }

  }

  @Test
  public void testFileRestore() throws IOException {
    final long nonDelFileId = wowCache.addFile(fileName);
    final long fileId = wowCache.addFile("removedFile.del");

    final String removedNativeFileName = wowCache.nativeFileNameById(fileId);

    wowCache.deleteFile(fileId);
    File deletedFile = storagePath.resolve(removedNativeFileName).toFile();
    Assert.assertTrue(!deletedFile.exists());

    String fileName = wowCache.restoreFileById(fileId);
    Assert.assertEquals(fileName, "removedFile.del");

    fileName = wowCache.restoreFileById(nonDelFileId);
    Assert.assertNull(fileName);
    Assert.assertTrue(deletedFile.exists());

    fileName = wowCache.restoreFileById(1525454L);
    Assert.assertNull(fileName);

    wowCache.deleteFile(fileId);
    Assert.assertTrue(!deletedFile.exists());
  }

  @Test
  public void testFileRestoreAfterClose() throws Exception {
    final long nonDelFileId = wowCache.addFile(fileName);
    final long fileId = wowCache.addFile("removedFile.del");

    final String removedNativeFileName = wowCache.nativeFileNameById(fileId);

    wowCache.deleteFile(fileId);
    File deletedFile = storagePath.resolve(removedNativeFileName).toFile();

    Assert.assertTrue(!deletedFile.exists());

    wowCache.close();
    writeAheadLog.close();

    initBuffer();

    String fileName = wowCache.restoreFileById(fileId);
    Assert.assertEquals(fileName, "removedFile.del");

    fileName = wowCache.restoreFileById(nonDelFileId);
    Assert.assertNull(fileName);
    Assert.assertTrue(deletedFile.exists());

    fileName = wowCache.restoreFileById(1525454L);
    Assert.assertNull(fileName);

    wowCache.deleteFile(fileId);
    Assert.assertTrue(!deletedFile.exists());
  }

  @Test
  public void testChecksumFailure() throws IOException {
    wowCache.setChecksumMode(OChecksumMode.StoreAndThrow);

    final long fileId = wowCache.addFile(fileName);
    Assert.assertEquals(0, wowCache.allocateNewPage(fileId));
    final OCachePointer cachePointer = wowCache.load(fileId, 0, 1, new OModifiableBoolean(), false)[0];

    cachePointer.acquireExclusiveLock();
    final ByteBuffer buffer = cachePointer.getBufferDuplicate();
    buffer.position(systemOffset);
    buffer.put(new byte[buffer.remaining()]);
    cachePointer.releaseExclusiveLock();

    wowCache.store(fileId, 0, cachePointer);
    cachePointer.decrementReadersReferrer();

    wowCache.flush();

    final Path path = storagePath.resolve(wowCache.nativeFileNameById(fileId));
    final OFileClassic file = new OFileClassic(path);
    file.open();
    file.writeByte(systemOffset, (byte) 1);
    file.close();

    try {
      wowCache.load(fileId, 0, 1, new OModifiableBoolean(), true);
      Assert.fail();
    } catch (OStorageException e) {
      // ok
    }
  }

  @Test
  public void testMagicFailure() throws IOException {
    wowCache.setChecksumMode(OChecksumMode.StoreAndThrow);

    final long fileId = wowCache.addFile(fileName);
    Assert.assertEquals(0, wowCache.allocateNewPage(fileId));
    final OCachePointer cachePointer = wowCache.load(fileId, 0, 1, new OModifiableBoolean(), false)[0];

    cachePointer.acquireExclusiveLock();
    final ByteBuffer buffer = cachePointer.getBufferDuplicate();
    buffer.position(systemOffset);
    buffer.put(new byte[buffer.remaining()]);
    cachePointer.releaseExclusiveLock();

    wowCache.store(fileId, 0, cachePointer);
    cachePointer.decrementReadersReferrer();

    wowCache.flush();

    final Path path = storagePath.resolve(wowCache.nativeFileNameById(fileId));
    final OFileClassic file = new OFileClassic(path);
    file.open();
    file.writeByte(0, (byte) 1);
    file.close();

    try {
      wowCache.load(fileId, 0, 1, new OModifiableBoolean(), true);
      Assert.fail();
    } catch (OStorageException e) {
      // ok
    }
  }

  @Test
  public void testNoChecksumVerificationIfNotRequested() throws IOException {
    wowCache.setChecksumMode(OChecksumMode.StoreAndThrow);

    final long fileId = wowCache.addFile(fileName);
    Assert.assertEquals(0, wowCache.allocateNewPage(fileId));
    final OCachePointer cachePointer = wowCache.load(fileId, 0, 1, new OModifiableBoolean(), false)[0];

    cachePointer.acquireExclusiveLock();
    final ByteBuffer buffer = cachePointer.getBufferDuplicate();
    buffer.position(systemOffset);
    buffer.put(new byte[buffer.remaining()]);
    cachePointer.releaseExclusiveLock();

    wowCache.store(fileId, 0, cachePointer);
    cachePointer.decrementReadersReferrer();

    wowCache.flush();

    final Path path = storagePath.resolve(wowCache.nativeFileNameById(fileId));
    final OFileClassic file = new OFileClassic(path);
    file.open();
    file.writeByte(systemOffset, (byte) 1);
    file.close();

    wowCache.load(fileId, 0, 1, new OModifiableBoolean(), false)[0].decrementReadersReferrer();
  }

  @Test
  public void testNoChecksumFailureIfVerificationTurnedOff() throws IOException {
    wowCache.setChecksumMode(OChecksumMode.Off);

    final long fileId = wowCache.addFile(fileName);
    Assert.assertEquals(0, wowCache.allocateNewPage(fileId));
    final OCachePointer cachePointer = wowCache.load(fileId, 0, 1, new OModifiableBoolean(), true)[0];

    cachePointer.acquireExclusiveLock();
    final ByteBuffer buffer = cachePointer.getBufferDuplicate();
    buffer.position(systemOffset);
    buffer.put(new byte[buffer.remaining()]);
    cachePointer.releaseExclusiveLock();

    wowCache.store(fileId, 0, cachePointer);
    cachePointer.decrementReadersReferrer();

    wowCache.flush();

    final Path path = storagePath.resolve(wowCache.nativeFileNameById(fileId));
    final OFileClassic file = new OFileClassic(path);
    file.open();
    file.writeByte(systemOffset, (byte) 1);
    file.close();

    wowCache.load(fileId, 0, 1, new OModifiableBoolean(), true)[0].decrementReadersReferrer();
  }

  @Test
  public void testNoChecksumFailureIfVerificationTurnedOffOnLoad() throws IOException {
    wowCache.setChecksumMode(OChecksumMode.Store);

    final long fileId = wowCache.addFile(fileName);
    Assert.assertEquals(0, wowCache.allocateNewPage(fileId));
    final OCachePointer cachePointer = wowCache.load(fileId, 0, 1, new OModifiableBoolean(), true)[0];

    cachePointer.acquireExclusiveLock();
    final ByteBuffer buffer = cachePointer.getBufferDuplicate();
    buffer.position(systemOffset);
    buffer.put(new byte[buffer.remaining()]);
    cachePointer.releaseExclusiveLock();

    wowCache.store(fileId, 0, cachePointer);
    cachePointer.decrementReadersReferrer();

    wowCache.flush();

    final Path path = storagePath.resolve(wowCache.nativeFileNameById(fileId));
    final OFileClassic file = new OFileClassic(path);
    file.open();
    file.writeByte(systemOffset, (byte) 1);
    file.close();

    wowCache.load(fileId, 0, 1, new OModifiableBoolean(), true)[0].decrementReadersReferrer();
  }

  @Test
  public void testNoChecksumFailureIfNoChecksumProvided() throws IOException {
    wowCache.setChecksumMode(OChecksumMode.Off);

    final long fileId = wowCache.addFile(fileName);
    Assert.assertEquals(0, wowCache.allocateNewPage(fileId));
    final OCachePointer cachePointer = wowCache.load(fileId, 0, 1, new OModifiableBoolean(), true)[0];

    cachePointer.acquireExclusiveLock();
    final ByteBuffer buffer = cachePointer.getBufferDuplicate();
    buffer.position(systemOffset);
    buffer.put(new byte[buffer.remaining()]);
    cachePointer.releaseExclusiveLock();

    wowCache.store(fileId, 0, cachePointer);
    cachePointer.decrementReadersReferrer();

    wowCache.flush();

    final Path path = storagePath.resolve(wowCache.nativeFileNameById(fileId));
    final OFileClassic file = new OFileClassic(path);
    file.open();
    file.writeByte(systemOffset, (byte) 1);
    file.close();

    wowCache.setChecksumMode(OChecksumMode.StoreAndThrow);
    wowCache.load(fileId, 0, 1, new OModifiableBoolean(), true)[0].decrementReadersReferrer();
  }

  private void assertFile(long pageIndex, byte[] value, OLogSequenceNumber lsn, String fileName) throws IOException {
    OFileClassic fileClassic = new OFileClassic(storagePath.resolve(fileName));
    fileClassic.open();
    byte[] content = new byte[8 + systemOffset];
    fileClassic.read(pageIndex * (8 + systemOffset), content, 8 + systemOffset);

    Assert.assertArrayEquals(Arrays.copyOfRange(content, systemOffset, 8 + systemOffset), value);

    long magicNumber = OLongSerializer.INSTANCE.deserializeNative(content, 0);
    Assert.assertEquals(magicNumber, OWOWCache.MAGIC_NUMBER_WITH_CHECKSUM);

    int segment = OIntegerSerializer.INSTANCE.deserializeNative(content, OLongSerializer.LONG_SIZE + OIntegerSerializer.INT_SIZE);
    long position = OLongSerializer.INSTANCE
        .deserializeNative(content, OLongSerializer.LONG_SIZE + 2 * OIntegerSerializer.INT_SIZE);

    OLogSequenceNumber readLsn = new OLogSequenceNumber(segment, position);

    Assert.assertEquals(readLsn, lsn);

    fileClassic.close();
  }

  public static final class TestRecord extends OAbstractWALRecord {
    private byte[] data;

    @SuppressWarnings("unused")
    public TestRecord() {
    }

    @SuppressWarnings("unused")
    public TestRecord(byte[] data) {
      this.data = data;
    }

    @Override
    public int toStream(byte[] content, int offset) {
      OIntegerSerializer.INSTANCE.serializeNative(data.length, content, offset);
      offset += OIntegerSerializer.INT_SIZE;

      System.arraycopy(data, 0, content, offset, data.length);
      offset += data.length;

      return offset;
    }

    @Override
    public void toStream(ByteBuffer buffer) {
      buffer.putInt(data.length);
      buffer.put(data);
    }

    @Override
    public int fromStream(byte[] content, int offset) {
      int len = OIntegerSerializer.INSTANCE.deserializeNative(content, offset);
      offset += OIntegerSerializer.INT_SIZE;

      data = new byte[len];
      System.arraycopy(content, offset, data, 0, len);
      offset += len;

      return offset;
    }

    @Override
    public int serializedSize() {
      return data.length + OIntegerSerializer.INT_SIZE;
    }

    @Override
    public boolean isUpdateMasterRecord() {
      return false;
    }

    @Override
    public byte getId() {
      return (byte) 128;
    }
  }

}
