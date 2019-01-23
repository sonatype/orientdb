package com.orientechnologies.orient.core.index.engine.v1;

import com.orientechnologies.common.exception.OException;
import com.orientechnologies.common.serialization.types.OBinarySerializer;
import com.orientechnologies.orient.core.db.record.OIdentifiable;
import com.orientechnologies.orient.core.encryption.OEncryption;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.index.OIndexAbstractCursor;
import com.orientechnologies.orient.core.index.OIndexCursor;
import com.orientechnologies.orient.core.index.OIndexDefinition;
import com.orientechnologies.orient.core.index.OIndexException;
import com.orientechnologies.orient.core.index.OIndexKeyCursor;
import com.orientechnologies.orient.core.index.engine.OMultiValueIndexEngine;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.storage.impl.local.OAbstractPaginatedStorage;
import com.orientechnologies.orient.core.storage.index.sbtree.multivalue.OCellBTreeMultiValue;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class OCellBTreeMultiValueIndexEngine implements OMultiValueIndexEngine {
  private static final String DATA_FILE_EXTENSION        = ".cbt";
  private static final String NULL_BUCKET_FILE_EXTENSION = ".nbt";

  private final OCellBTreeMultiValue<Object> sbTree;
  private final String                       name;

  public OCellBTreeMultiValueIndexEngine(String name, OAbstractPaginatedStorage storage) {
    this.name = name;
    this.sbTree = new OCellBTreeMultiValue<>(name, DATA_FILE_EXTENSION, NULL_BUCKET_FILE_EXTENSION, storage);
  }

  @Override
  public void init(String indexName, String indexType, OIndexDefinition indexDefinition, boolean isAutomatic, ODocument metadata) {
  }

  @Override
  public void flush() {
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public void create(OBinarySerializer valueSerializer, boolean isAutomatic, OType[] keyTypes, boolean nullPointerSupport,
      OBinarySerializer keySerializer, int keySize, Set<String> clustersToIndex, Map<String, String> engineProperties,
      ODocument metadata, OEncryption encryption) {
    try {
      //noinspection unchecked
      sbTree.create(keySerializer, keyTypes, keySize, encryption);
    } catch (IOException e) {
      throw OException.wrapException(new OIndexException("Error during creation of index " + name), e);
    }
  }

  @Override
  public void delete() {
    try {
      sbTree.delete();
    } catch (IOException e) {
      throw OException.wrapException(new OIndexException("Error during deletion of index " + name), e);
    }
  }

  @Override
  public void deleteWithoutLoad(String indexName) {
    try {
      sbTree.deleteWithoutLoad();
    } catch (IOException e) {
      throw OException.wrapException(new OIndexException("Error during deletion of index " + name), e);
    }
  }

  @Override
  public void load(String indexName, String encryptionKey) {
    sbTree.load(indexName, encryptionKey);
  }

  @Override
  public boolean contains(Object key) {
    return !sbTree.get(key).isEmpty();
  }

  @Override
  public boolean remove(Object key) {
    try {
      return sbTree.remove(key);
    } catch (IOException e) {
      throw OException.wrapException(new OIndexException("Error during removal of key " + key + " from index " + name), e);
    }
  }

  @Override
  public boolean remove(Object key, ORID value) {
    try {
      return sbTree.remove(key, value);
    } catch (IOException e) {
      throw OException.wrapException(
          new OIndexException("Error during removal of entry with key " + key + "and RID " + value + " from index " + name), e);
    }
  }

  @Override
  public int getVersion() {
    return 0;
  }

  @Override
  public void clear() {
    try {
      sbTree.clear();
    } catch (IOException e) {
      throw OException.wrapException(new OIndexException("Error during clearing of index " + name), e);
    }
  }

  @Override
  public void close() {
    sbTree.close();
  }

  @Override
  public List<ORID> get(Object key) {
    return sbTree.get(key);
  }

  @Override
  public OIndexCursor cursor(ValuesTransformer valuesTransformer) {
    final Object firstKey = sbTree.firstKey();
    if (firstKey == null) {
      return new NullCursor();
    }

    return new OSBTreeIndexCursor(sbTree.iterateEntriesMajor(firstKey, true, true));
  }

  @Override
  public OIndexCursor descCursor(ValuesTransformer valuesTransformer) {
    final Object lastKey = sbTree.lastKey();
    if (lastKey == null) {
      return new NullCursor();
    }

    return new OSBTreeIndexCursor(sbTree.iterateEntriesMinor(lastKey, true, false));
  }

  @Override
  public OIndexKeyCursor keyCursor() {
    return new OIndexKeyCursor() {
      private final OCellBTreeMultiValue.OSBTreeKeyCursor<Object> sbTreeKeyCursor = sbTree.keyCursor();

      @Override
      public Object next(int prefetchSize) {
        return sbTreeKeyCursor.next(prefetchSize);
      }
    };
  }

  @Override
  public void put(Object key, ORID value) {
    try {
      sbTree.put(key, value);
    } catch (IOException e) {
      throw OException
          .wrapException(new OIndexException("Error during insertion of key " + key + " and RID " + value + " to index " + name),
              e);
    }
  }

  @Override
  public Object getFirstKey() {
    return sbTree.firstKey();
  }

  @Override
  public Object getLastKey() {
    return sbTree.lastKey();
  }

  @Override
  public OIndexCursor iterateEntriesBetween(Object rangeFrom, boolean fromInclusive, Object rangeTo, boolean toInclusive,
      boolean ascSortOrder, ValuesTransformer transformer) {
    return new OSBTreeIndexCursor(sbTree.iterateEntriesBetween(rangeFrom, fromInclusive, rangeTo, toInclusive, ascSortOrder));
  }

  @Override
  public OIndexCursor iterateEntriesMajor(Object fromKey, boolean isInclusive, boolean ascSortOrder,
      ValuesTransformer transformer) {
    return new OSBTreeIndexCursor(sbTree.iterateEntriesMajor(fromKey, isInclusive, ascSortOrder));
  }

  @Override
  public OIndexCursor iterateEntriesMinor(Object toKey, boolean isInclusive, boolean ascSortOrder, ValuesTransformer transformer) {
    return new OSBTreeIndexCursor(sbTree.iterateEntriesMinor(toKey, isInclusive, ascSortOrder));
  }

  @Override
  public long size(final ValuesTransformer transformer) {
    if (transformer == null) {
      final Object firstKey = sbTree.firstKey();
      final Object lastKey = sbTree.lastKey();

      int counter = 0;

      if (!sbTree.get(null).isEmpty()) {
        counter++;
      }

      if (firstKey != null && lastKey != null) {
        final OCellBTreeMultiValue.OSBTreeCursor<Object, ORID> cursor = sbTree
            .iterateEntriesBetween(firstKey, true, lastKey, true, true);

        Object prevKey = new Object();
        while (true) {
          final Map.Entry<Object, ORID> entry = cursor.next(-1);
          if (entry == null) {
            break;
          }

          if (!prevKey.equals(entry.getKey())) {
            counter++;
          }

          prevKey = entry.getKey();
        }
      }

      return counter;
    }

    return sbTree.size();
  }

  @Override
  public boolean hasRangeQuerySupport() {
    return true;
  }

  @Override
  public boolean acquireAtomicExclusiveLock(Object key) {
    sbTree.acquireAtomicExclusiveLock();
    return true;
  }

  @Override
  public String getIndexNameByKey(Object key) {
    return name;
  }

  private static final class OSBTreeIndexCursor extends OIndexAbstractCursor {
    private final OCellBTreeMultiValue.OSBTreeCursor<Object, ORID> treeCursor;

    private OSBTreeIndexCursor(OCellBTreeMultiValue.OSBTreeCursor<Object, ORID> treeCursor) {
      this.treeCursor = treeCursor;
    }

    @Override
    public Map.Entry<Object, OIdentifiable> nextEntry() {
      final Object entry = treeCursor.next(getPrefetchSize());
      //noinspection unchecked
      return (Map.Entry<Object, OIdentifiable>) entry;
    }
  }

  private static class NullCursor extends OIndexAbstractCursor {
    @Override
    public Map.Entry<Object, OIdentifiable> nextEntry() {
      return null;
    }
  }
}
