/*
 * Copyright 2014 Orient Technologies.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.orientechnologies.lucene;

import com.orientechnologies.common.log.OLogManager;
import com.orientechnologies.lucene.index.OLuceneFullTextIndex;
import com.orientechnologies.lucene.index.OLuceneSpatialIndex;
import com.orientechnologies.lucene.manager.OLuceneFullTextIndexManager;
import com.orientechnologies.lucene.manager.OLuceneSpatialIndexManager;
import com.orientechnologies.lucene.shape.OShapeFactoryImpl;
import com.orientechnologies.orient.core.OOrientListener;
import com.orientechnologies.orient.core.Orient;
import com.orientechnologies.orient.core.db.*;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.db.record.OIdentifiable;
import com.orientechnologies.orient.core.exception.OConfigurationException;
import com.orientechnologies.orient.core.index.OIndex;
import com.orientechnologies.orient.core.index.OIndexFactory;
import com.orientechnologies.orient.core.index.OIndexInternal;
import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.metadata.security.OSecurityNull;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.storage.OStorage;
import com.orientechnologies.orient.core.storage.impl.local.OAbstractPaginatedStorage;
import com.orientechnologies.orient.core.storage.impl.local.OFullCheckpointListener;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class OLuceneIndexFactory implements OIndexFactory, ODatabaseLifecycleListener, OOrientListener, OFullCheckpointListener {

  public static final String LUCENE_ALGORITHM = "LUCENE";
  private static final Set<String> TYPES;
  private static final Set<String> ALGORITHMS;

  static {
    final Set<String> types = new HashSet<String>();
    types.add(OClass.INDEX_TYPE.FULLTEXT.toString());
    types.add(OClass.INDEX_TYPE.SPATIAL.toString());
    TYPES = Collections.unmodifiableSet(types);
  }

  static {
    final Set<String> algorithms = new HashSet<String>();
    algorithms.add(LUCENE_ALGORITHM);
    ALGORITHMS = Collections.unmodifiableSet(algorithms);
  }

  public OLuceneIndexFactory() {
    this(false);
  }

  public OLuceneIndexFactory(boolean manual) {
    if (!manual) {

      Orient.instance().registerListener(this);
      Orient.instance().addDbLifecycleListener(this);
    }
  }

  @Override
  public int getLastVersion() {
    return 0;
  }

  @Override
  public Set<String> getTypes() {
    return TYPES;
  }

  @Override
  public Set<String> getAlgorithms() {
    return ALGORITHMS;
  }

  @Override
  public OIndexInternal<?> createIndex(String name, ODatabaseDocumentInternal database, String indexType, String algorithm,
      String valueContainerAlgorithm, ODocument metadata, int version) throws OConfigurationException {
    return createIndex(name, database, indexType, algorithm, valueContainerAlgorithm, metadata);
  }

  @Override
  public PRIORITY getPriority() {
    return PRIORITY.REGULAR;
  }

  @Override
  public void onCreate(ODatabaseInternal iDatabase) {

    OStorage storage = iDatabase.getStorage();

    if (storage instanceof OAbstractPaginatedStorage) {
      ((OAbstractPaginatedStorage) storage).addFullCheckpointListener(this);
    }

  }

  @Override
  public void onOpen(ODatabaseInternal iDatabase) {

  }

  @Override
  public void onClose(ODatabaseInternal iDatabase) {

  }

  @Override
  public void onDrop(final ODatabaseInternal iDatabase) {
    try {
      OLogManager.instance().debug(this, "Dropping Lucene indexes...");
      for (OIndex idx : iDatabase.getMetadata().getIndexManager().getIndexes()) {
        if (idx.getInternal() instanceof OLuceneIndex) {
          OLogManager.instance().debug(this, "- index '%s'", idx.getName());
          idx.delete();
        }
      }
    } catch (Exception e) {
      OLogManager.instance().warn(this, "Error on dropping Lucene indexes", e);
    }
  }

  @Override
  public void onCreateClass(ODatabaseInternal iDatabase, OClass iClass) {

  }

  @Override
  public void onDropClass(ODatabaseInternal iDatabase, OClass iClass) {

  }

  protected OIndexInternal<?> createIndex(String name, ODatabaseDocumentInternal oDatabaseRecord, String indexType,
      String algorithm, String valueContainerAlgorithm, ODocument metadata) throws OConfigurationException {
    return createLuceneIndex(name, oDatabaseRecord, indexType, valueContainerAlgorithm, metadata);
  }

  private OIndexInternal<?> createLuceneIndex(String name, ODatabaseDocumentInternal database, String indexType,
      String valueContainerAlgorithm, ODocument metadata) {
    final OStorage storage = database.getStorage().getUnderlying();

    if (metadata == null)
      metadata = new ODocument();

    if (OClass.INDEX_TYPE.FULLTEXT.toString().equals(indexType)) {
      return new OLuceneFullTextIndex(name, indexType, LUCENE_ALGORITHM,
          new OLuceneIndexEngine<Set<OIdentifiable>>(new OLuceneFullTextIndexManager(), indexType), valueContainerAlgorithm,
          metadata, storage);
    } else if (OClass.INDEX_TYPE.SPATIAL.toString().equals(indexType)) {
      return new OLuceneSpatialIndex(name, indexType, LUCENE_ALGORITHM,
          new OLuceneIndexEngine<Set<OIdentifiable>>(new OLuceneSpatialIndexManager(OShapeFactoryImpl.INSTANCE), indexType),
          valueContainerAlgorithm, metadata, storage);
    }
    throw new OConfigurationException("Unsupported type : " + indexType);
  }

  @Override
  public void onShutdown() {

  }

  @Override
  public void onStorageRegistered(OStorage storage) {

    if (storage instanceof OAbstractPaginatedStorage) {
      ((OAbstractPaginatedStorage) storage).removeFullCheckpointListener(this);
      ((OAbstractPaginatedStorage) storage).addFullCheckpointListener(this);
    }
  }

  @Override
  public void onStorageUnregistered(OStorage storage) {
    if (storage instanceof OAbstractPaginatedStorage) {
      ((OAbstractPaginatedStorage) storage).removeFullCheckpointListener(this);
    }
  }

  @Override
  public void fullCheckpointMade(OAbstractPaginatedStorage storage) {

    if (storage.isClosed()) {
      return;
    }

    ODatabaseInternal db = ODatabaseRecordThreadLocal.INSTANCE.getIfDefined();
    boolean opened = false;

    if (db == null) {
      db = openDatabase(storage.getURL());
      opened = true;
    }
    try {

      if (!db.isClosed()) {
        for (OIndex idx : db.getMetadata().getIndexManager().getIndexes()) {
          if (idx.getInternal() instanceof OLuceneIndex) {
            idx.flush();
          }
        }
      }
    } catch (Exception e) {
      OLogManager.instance().warn(this, "Error on flushing Lucene indexes", e);
    } finally {
      if (opened) {
        db.close();
      }
    }
  }

  private ODatabaseInternal openDatabase(String url) {
    ODatabaseDocumentTx db = new ODatabaseDocumentTx(url);
    db.setProperty(ODatabase.OPTIONS.SECURITY.toString(), OSecurityNull.class);
    db.open("admin", "aaa");
    return db;
  }
}
