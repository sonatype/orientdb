package com.orientechnologies.orient.core.db.document;

import com.orientechnologies.common.concur.ONeedRetryException;
import com.orientechnologies.orient.core.Orient;
import com.orientechnologies.orient.core.cache.OLocalRecordCache;
import com.orientechnologies.orient.core.command.OCommandOutputListener;
import com.orientechnologies.orient.core.command.OCommandRequest;
import com.orientechnologies.orient.core.config.OContextConfiguration;
import com.orientechnologies.orient.core.config.OGlobalConfiguration;
import com.orientechnologies.orient.core.conflict.ORecordConflictStrategy;
import com.orientechnologies.orient.core.db.*;
import com.orientechnologies.orient.core.db.record.OCurrentStorageComponentsFactory;
import com.orientechnologies.orient.core.db.record.OIdentifiable;
import com.orientechnologies.orient.core.db.record.ridbag.sbtree.OSBTreeCollectionManager;
import com.orientechnologies.orient.core.dictionary.ODictionary;
import com.orientechnologies.orient.core.exception.*;
import com.orientechnologies.orient.core.hook.ORecordHook;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.id.ORecordId;
import com.orientechnologies.orient.core.intent.OIntent;
import com.orientechnologies.orient.core.iterator.ORecordIteratorClass;
import com.orientechnologies.orient.core.iterator.ORecordIteratorCluster;
import com.orientechnologies.orient.core.metadata.OMetadataInternal;
import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.metadata.schema.OSchema;
import com.orientechnologies.orient.core.metadata.security.ORule;
import com.orientechnologies.orient.core.metadata.security.OSecurityUser;
import com.orientechnologies.orient.core.metadata.security.OToken;
import com.orientechnologies.orient.core.query.OQuery;
import com.orientechnologies.orient.core.record.*;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.record.impl.ODocumentInternal;
import com.orientechnologies.orient.core.record.impl.OVertexDelegate;
import com.orientechnologies.orient.core.serialization.serializer.binary.OBinarySerializerFactory;
import com.orientechnologies.orient.core.serialization.serializer.record.ORecordSerializer;
import com.orientechnologies.orient.core.serialization.serializer.record.ORecordSerializerFactory;
import com.orientechnologies.orient.core.shutdown.OShutdownHandler;
import com.orientechnologies.orient.core.sql.OCommandSQLParsingException;
import com.orientechnologies.orient.core.sql.executor.OTodoResultSet;
import com.orientechnologies.orient.core.storage.ORecordCallback;
import com.orientechnologies.orient.core.storage.ORecordMetadata;
import com.orientechnologies.orient.core.storage.OStorage;
import com.orientechnologies.orient.core.tx.OTransaction;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by tglman on 20/07/16.
 */
public class ODatabaseDocumentTx implements ODatabaseDocumentInternal {

  private static ConcurrentMap<String, OrientDBFactory> embedded          = new ConcurrentHashMap<>();
  private static ConcurrentMap<String, OrientDBFactory> remote            = new ConcurrentHashMap<>();

  protected ODatabaseDocumentInternal                   internal;
  private final String                                  url;
  private OrientDBFactory                               factory;
  private final String                                  type;
  private final String                                  dbName;
  private final String                                  baseUrl;
  private final Map<String, Object>                     preopenProperties = new HashMap<>();
  private final Map<ATTRIBUTES, Object>                 preopenAttributes = new HashMap<>();
  // TODO review for the case of browseListener before open.
  private final Set<ODatabaseListener>                  preopenListener   = new HashSet<>();
  private ODatabaseInternal<?>                          databaseOwner;
  private OIntent                                       intent;
  private OStorage                                      delegateStorage;
  private ORecordConflictStrategy                       conflictStrategy;
  private ORecordSerializer                             serializer;
  protected final AtomicReference<Thread>               owner             = new AtomicReference<Thread>();
  private final boolean                                 ownerProtection;

  private static OShutdownHandler                       shutdownHandler   = new OShutdownHandler() {
                                                                            @Override
                                                                            public void shutdown() throws Exception {
                                                                              closeAll();
                                                                            }

                                                                            @Override
                                                                            public int getPriority() {
                                                                              return 10000;
                                                                            }
                                                                          };

  static {
    Orient.instance().registerOrientStartupListener(() -> Orient.instance().addShutdownHandler(shutdownHandler));
    Orient.instance().addShutdownHandler(shutdownHandler);
  }

  public static void closeAll() {
    for (OrientDBFactory factory : embedded.values()) {
      factory.close();
    }
    embedded.clear();
    for (OrientDBFactory factory : remote.values()) {
      factory.close();
    }
    remote.clear();
  }

  protected OrientDBFactory getOrCreateRemoteFactory(String baseUrl) {
    OrientDBFactory factory;
    synchronized (remote) {
      factory = remote.get(baseUrl);
      if (factory == null) {
        factory = OrientDBFactory.fromUrl("remote:" + baseUrl, null);
        remote.put(baseUrl, factory);
      }
    }
    return factory;
  }

  protected static OEmbeddedDBFactory getOrCreateEmbeddedFactory(String baseUrl, OrientDBConfig config) {
    if (!baseUrl.endsWith("/"))
      baseUrl += "/";
    OEmbeddedDBFactory factory;
    synchronized (embedded) {
      factory = (OEmbeddedDBFactory) embedded.get(baseUrl);
      if (factory == null) {
        factory = (OEmbeddedDBFactory) OrientDBFactory.fromUrl("embedded:" + baseUrl, config);
        embedded.put(baseUrl, factory);
      }
    }
    return factory;
  }

  public ODatabaseDocumentTx(String url) {
    this(url, true);
  }

  protected ODatabaseDocumentTx(String url, boolean ownerProtection) {
    if (url.endsWith("/"))
      url = url.substring(0, url.length() - 1);
    url = url.replace('\\', '/');
    this.url = url;

    int typeIndex = url.indexOf(':');
    if (typeIndex <= 0)
      throw new OConfigurationException(
          "Error in database URL: the engine was not specified. Syntax is: " + Orient.URL_SYNTAX + ". URL was: " + url);

    String databaseReference = url.substring(typeIndex + 1);
    type = url.substring(0, typeIndex);
    if (!"remote".equals(type) && !"plocal".equals(type) && !"memory".equals(type))
      throw new OConfigurationException("Error on opening database: the engine '" + type + "' was not found. URL was: " + url
          + ". Registered engines are: [\"memory\",\"remote\",\"plocal\"]");

    int index = databaseReference.lastIndexOf('/');
    String path;
    if (index > 0) {
      path = databaseReference.substring(0, index);
      dbName = databaseReference.substring(index + 1);
    } else {
      path = "./";
      dbName = databaseReference;
    }
    if ("plocal".equals(type) || "memory".equals(type)) {
      baseUrl = new File(path).getAbsolutePath();
    } else {
      baseUrl = path;
    }
    this.ownerProtection = ownerProtection;

  }

  protected ODatabaseDocumentTx(ODatabaseDocumentInternal ref, String baseUrl) {
    url = ref.getURL();
    type = ref.getType();
    this.baseUrl = baseUrl;
    dbName = ref.getName();
    internal = ref;
    this.ownerProtection = true;
  }

  public static ORecordSerializer getDefaultSerializer() {
    return ODatabaseDocumentAbstract.getDefaultSerializer();
  }

  public static void setDefaultSerializer(ORecordSerializer defaultSerializer) {
    ODatabaseDocumentAbstract.setDefaultSerializer(defaultSerializer);
  }

  @Override
  public OCurrentStorageComponentsFactory getStorageVersions() {
    if (internal == null)
      return null;
    return internal.getStorageVersions();
  }

  @Override
  public OSBTreeCollectionManager getSbTreeCollectionManager() {
    if (internal == null)
      return null;
    return internal.getSbTreeCollectionManager();
  }

  @Override
  public OBinarySerializerFactory getSerializerFactory() {
    checkOpeness();
    return internal.getSerializerFactory();
  }

  @Override
  public ORecordSerializer getSerializer() {
    if (internal == null) {
      if (serializer != null)
        return serializer;
      return ORecordSerializerFactory.instance().getDefaultRecordSerializer();
    }
    return internal.getSerializer();
  }

  @Override
  public int assignAndCheckCluster(ORecord record, String iClusterName) {
    return internal.assignAndCheckCluster(record, iClusterName);
  }

  @Override
  public <RET extends ORecord> RET loadIfVersionIsNotLatest(ORID rid, int recordVersion, String fetchPlan, boolean ignoreCache)
      throws ORecordNotFoundException {
    checkOpeness();
    return (RET) internal.loadIfVersionIsNotLatest(rid, recordVersion, fetchPlan, ignoreCache);
  }

  @Override
  public void reloadUser() {
    checkOpeness();
    internal.reloadUser();
  }

  @Override
  public ORecordHook.RESULT callbackHooks(ORecordHook.TYPE type, OIdentifiable id) {
    checkOpeness();
    return internal.callbackHooks(type, id);
  }

  @Override
  public <RET extends ORecord> RET executeReadRecord(ORecordId rid, ORecord iRecord, int recordVersion, String fetchPlan,
      boolean ignoreCache, boolean iUpdateCache, boolean loadTombstones, OStorage.LOCKING_STRATEGY lockingStrategy,
      RecordReader recordReader) {
    checkOpeness();
    return internal.executeReadRecord(rid, iRecord, recordVersion, fetchPlan, ignoreCache, iUpdateCache, loadTombstones,
        lockingStrategy, recordReader);
  }

  @Override
  public <RET extends ORecord> RET executeSaveRecord(ORecord record, String clusterName, int ver, OPERATION_MODE mode,
      boolean forceCreate, ORecordCallback<? extends Number> recordCreatedCallback,
      ORecordCallback<Integer> recordUpdatedCallback) {
    checkOpeness();
    return internal.executeSaveRecord(record, clusterName, ver, mode, forceCreate, recordCreatedCallback, recordUpdatedCallback);
  }

  @Override
  public void executeDeleteRecord(OIdentifiable record, int iVersion, boolean iRequired, OPERATION_MODE iMode,
      boolean prohibitTombstones) {
    checkOpeness();
    internal.executeDeleteRecord(record, iVersion, iRequired, iMode, prohibitTombstones);
  }

  @Override
  public <RET extends ORecord> RET executeSaveEmptyRecord(ORecord record, String clusterName) {
    checkOpeness();
    return internal.executeSaveEmptyRecord(record, clusterName);
  }

  @Override
  public void setDefaultTransactionMode() {
    checkOpeness();
    internal.setDefaultTransactionMode();
  }

  @Override
  public OMetadataInternal getMetadata() {
    checkOpeness();
    return internal.getMetadata();
  }

  @Override
  public <DB extends ODatabase<?>> DB registerHook(ORecordHook iHookImpl) {
    checkOpeness();
    internal.registerHook(iHookImpl);
    return (DB) this;
  }

  @Override
  public <DB extends ODatabase<?>> DB registerHook(ORecordHook iHookImpl, ORecordHook.HOOK_POSITION iPosition) {
    checkOpeness();
    internal.registerHook(iHookImpl, iPosition);
    return (DB) this;
  }

  @Override
  public Map<ORecordHook, ORecordHook.HOOK_POSITION> getHooks() {
    checkOpeness();
    return internal.getHooks();
  }

  @Override
  public <DB extends ODatabase<?>> DB unregisterHook(ORecordHook iHookImpl) {
    checkOpeness();
    internal.unregisterHook(iHookImpl);
    return (DB) this;
  }

  @Override
  public boolean isMVCC() {
    return false;
  }

  @Override
  public Iterable<ODatabaseListener> getListeners() {
    return internal.getListeners();
  }

  @Override
  public <DB extends ODatabase<?>> DB setMVCC(boolean iValue) {
    return null;
  }

  @Override
  public String getType() {
    return this.type;
  }

  @Override
  public ORecordConflictStrategy getConflictStrategy() {
    return internal.getConflictStrategy();
  }

  @Override
  public <DB extends ODatabase<?>> DB setConflictStrategy(String iStrategyName) {
    if (internal != null) {
      internal.setConflictStrategy(iStrategyName);
    } else {
      conflictStrategy = Orient.instance().getRecordConflictStrategy().getStrategy(iStrategyName);
    }
    return (DB) this;
  }

  @Override
  public <DB extends ODatabase<?>> DB setConflictStrategy(ORecordConflictStrategy iResolver) {
    if (internal != null) {
      internal.setConflictStrategy(iResolver);
    } else {
      conflictStrategy = iResolver;
    }
    return (DB) this;
  }

  @Override
  public String incrementalBackup(String path) {
    checkOpeness();
    return internal.incrementalBackup(path);
  }

  @Override
  public ODatabaseDocumentTx copy() {
    checkOpeness();
    return new ODatabaseDocumentTx(this.internal.copy(), this.baseUrl);
  }

  @Override
  public Set<ORecord> executeReadRecords(Set<ORecordId> iRids, boolean ignoreCache) {
    checkOpeness();
    return internal.executeReadRecords(iRids, ignoreCache);
  }

  @Override
  public void checkIfActive() {
    internal.checkIfActive();
  }

  protected void checkOpeness() {
    if (internal == null)
      throw new ODatabaseException("Database '" + getURL() + "' is closed");
  }

  @Override
  public void callOnOpenListeners() {
    checkOpeness();
    internal.callOnOpenListeners();
  }

  @Override
  public void callOnCloseListeners() {
    checkOpeness();
    internal.callOnCloseListeners();
  }

  @Override
  public OStorage getStorage() {
    if (internal == null)
      return delegateStorage;
    return internal.getStorage();
  }

  @Override
  public void setUser(OSecurityUser user) {
    internal.setUser(user);
  }

  @Override
  public void replaceStorage(OStorage iNewStorage) {
    internal.replaceStorage(iNewStorage);
  }

  @Override
  public <V> V callInLock(Callable<V> iCallable, boolean iExclusiveLock) {
    return internal.callInLock(iCallable, iExclusiveLock);
  }

  @Override
  public void resetInitialization() {
    if (internal != null)
      internal.resetInitialization();
  }

  @Override
  public ODatabaseInternal<?> getDatabaseOwner() {
    ODatabaseInternal<?> current = databaseOwner;

    while (current != null && current != this && current.getDatabaseOwner() != current)
      current = current.getDatabaseOwner();
    if (current == null)
      return this;
    return current;
  }

  @Override
  public ODatabaseInternal<?> setDatabaseOwner(ODatabaseInternal<?> iOwner) {
    databaseOwner = iOwner;
    if (internal != null)
      internal.setDatabaseOwner(iOwner);
    return this;
  }

  @Override
  public <DB extends ODatabase> DB getUnderlying() {
    return internal.getUnderlying();
  }

  @Override
  public void setInternal(ATTRIBUTES attribute, Object iValue) {
    checkOpeness();
    internal.setInternal(attribute, iValue);
  }

  @Override
  public <DB extends ODatabase> DB open(OToken iToken) {
    throw new UnsupportedOperationException();
  }

  @Override
  public OSharedContext getSharedContext() {
    return internal.getSharedContext();
  }

  @Override
  public ORecordIteratorClass<ODocument> browseClass(String iClassName) {
    checkOpeness();
    return internal.browseClass(iClassName);
  }

  @Override
  public ORecordIteratorClass<ODocument> browseClass(String iClassName, boolean iPolymorphic) {
    checkOpeness();
    return internal.browseClass(iClassName, iPolymorphic);
  }

  @Override
  public void freeze() {
    checkOpeness();
    internal.freeze();
  }

  @Override
  public void release() {
    checkOpeness();
    internal.release();
  }

  @Override
  public void freeze(boolean throwException) {
    checkOpeness();
    internal.freeze(throwException);
  }

  public OVertex newVertex(final String iClassName) {
    ODocument doc = newInstance(iClassName);
    if (!doc.isVertex()) {
      throw new IllegalArgumentException("" + iClassName + " is not a vertex class");
    }
    return doc.asVertex().get();
  }

  @Override public OVertex newVertex(OClass type) {
    if (type == null) {
      return newVertex("E");
    }
    return newVertex(type.getName());
  }

  @Override public OEdge newEdge(OVertex from, OVertex to, String type) {
    ODocument doc = newInstance(type);
    if (!doc.isEdge()) {
      throw new IllegalArgumentException("" + type + " is not an edge class");
    }

    return addEdgeInternal(from, to, type);
  }

  @Override public OEdge newEdge(OVertex from, OVertex to, OClass type) {
    if (type == null) {
      return newEdge(from, to, "E");
    }
    return newEdge(from, to, type.getName());
  }

  @Override public OElement newElement() {
    return newInstance();
  }

  @Override public OElement newElement(String className) {
    return newInstance(className);
  }

  public OElement newElement(OClass clazz) {
    return newInstance(clazz.getName());
  }


  private OEdge addEdgeInternal(final OVertex currentVertex, final OVertex inVertex, String iClassName, final Object... fields) {

    OEdge edge = null;
    ODocument outDocument = null;
    ODocument inDocument = null;
    boolean outDocumentModified = false;

    final int maxRetries = 1;//TODO
    for (int retry = 0; retry < maxRetries; ++retry) {
      try {
        // TEMPORARY STATIC LOCK TO AVOID MT PROBLEMS AGAINST OMVRBTreeRID
        if (outDocument == null) {
          outDocument = currentVertex.getRecord();
          if (outDocument == null)
            throw new IllegalArgumentException("source vertex is invalid (rid=" + currentVertex.getIdentity() + ")");
        }

        if (inDocument == null) {
          inDocument = inVertex.getRecord();
          if (inDocument == null)
            throw new IllegalArgumentException("source vertex is invalid (rid=" + inVertex.getIdentity() + ")");
        }

        if (!ODocumentInternal.getImmutableSchemaClass(outDocument).isVertexType())
          throw new IllegalArgumentException("source record is not a vertex");

        if (!ODocumentInternal.getImmutableSchemaClass(outDocument).isVertexType())
          throw new IllegalArgumentException("destination record is not a vertex");

        OVertex to = inVertex;
        OVertex from = currentVertex;

        OSchema schema = getMetadata().getSchema();
        final OClass edgeType = schema.getClass(iClassName);
        if (edgeType == null)
          // AUTO CREATE CLASS
          schema.createClass(iClassName);
        else
          // OVERWRITE CLASS NAME BECAUSE ATTRIBUTES ARE CASE SENSITIVE
          iClassName = edgeType.getName();

        final String outFieldName = getConnectionFieldName(ODirection.OUT, iClassName);
        final String inFieldName = getConnectionFieldName(ODirection.IN, iClassName);

        // since the label for the edge can potentially get re-assigned
        // before being pushed into the OrientEdge, the
        // null check has to go here.
        if (iClassName == null)
          throw new IllegalArgumentException("Class " + iClassName + " cannot be found");

        // CREATE THE EDGE DOCUMENT TO STORE FIELDS TOO

        edge = newInstance(iClassName).asEdge().get();
        if (fields != null) {
          for (int i = 0; i < fields.length; i += 2) {
            String fieldName = "" + fields[i];
            if (fields.length <= i + 1) {
              break;
            }
            Object fieldValue = fields[i + 1];
            edge.setProperty(fieldName, fieldValue);

          }
        }

        edge.setProperty("out", currentVertex.getRecord());
        edge.setProperty("in", inDocument.getRecord());

        if (!outDocumentModified) {
          // OUT-VERTEX ---> IN-VERTEX/EDGE
          OVertexDelegate.createLink(outDocument, to.getRecord(), outFieldName);

        }

        // IN-VERTEX ---> OUT-VERTEX/EDGE
        OVertexDelegate.createLink(inDocument, from.getRecord(), inFieldName);

        // OK
        break;

      } catch (ONeedRetryException e) {
        // RETRY
        if (!outDocumentModified)
          outDocument.reload();
        else if (inDocument != null)
          inDocument.reload();
      } catch (RuntimeException e) {
        // REVERT CHANGES. EDGE.REMOVE() TAKES CARE TO UPDATE ALSO BOTH VERTICES IN CASE
        try {
          edge.delete();
        } catch (Exception ex) {
        }
        throw e;
      } catch (Throwable e) {
        // REVERT CHANGES. EDGE.REMOVE() TAKES CARE TO UPDATE ALSO BOTH VERTICES IN CASE
        try {
          edge.delete();
        } catch (Exception ex) {
        }
        throw new IllegalStateException("Error on addEdge in non tx environment", e);
      }
    }
    return edge;
  }

  private static String getConnectionFieldName(final ODirection iDirection, final String iClassName) {
    if (iDirection == null || iDirection == ODirection.BOTH)
      throw new IllegalArgumentException("Direction not valid");

    // PREFIX "out_" or "in_" TO THE FIELD NAME
    final String prefix = iDirection == ODirection.OUT ? "out_" : "in_";
    if (iClassName == null || iClassName.isEmpty() || iClassName.equals("E"))
      return prefix;

    return prefix + iClassName;
  }

  @Override
  public ODocument newInstance() {
    checkOpeness();
    return internal.newInstance();
  }

  @Override
  public ODictionary<ORecord> getDictionary() {
    checkOpeness();
    return internal.getDictionary();
  }

  @Override
  public OSecurityUser getUser() {
    if (internal != null)
      return internal.getUser();
    return null;
  }

  @Override
  public <RET extends ORecord> RET load(ORecord iObject) {
    checkOpeness();
    return internal.load(iObject);
  }

  @Override
  public <RET extends ORecord> RET load(ORecord iObject, String iFetchPlan) {
    checkOpeness();
    return internal.load(iObject, iFetchPlan);
  }

  @Override
  public <RET extends ORecord> RET load(ORecord iObject, String iFetchPlan, boolean iIgnoreCache, boolean loadTombstone,
      OStorage.LOCKING_STRATEGY iLockingStrategy) {
    checkOpeness();
    return internal.load(iObject, iFetchPlan, iIgnoreCache, loadTombstone, iLockingStrategy);
  }

  @Override
  public <RET extends ORecord> RET load(ORecord iObject, String iFetchPlan, boolean iIgnoreCache, boolean iUpdateCache,
      boolean loadTombstone, OStorage.LOCKING_STRATEGY iLockingStrategy) {
    checkOpeness();
    return internal.load(iObject, iFetchPlan, iIgnoreCache, iUpdateCache, loadTombstone, iLockingStrategy);
  }

  @Override
  public <RET extends ORecord> RET load(ORecord iObject, String iFetchPlan, boolean iIgnoreCache) {
    checkOpeness();
    return internal.load(iObject, iFetchPlan, iIgnoreCache);
  }

  @Override
  public <RET extends ORecord> RET reload(ORecord iObject, String iFetchPlan, boolean iIgnoreCache) {
    checkOpeness();
    return internal.reload(iObject, iFetchPlan, iIgnoreCache);
  }

  @Override
  public <RET extends ORecord> RET reload(ORecord iObject, String iFetchPlan, boolean iIgnoreCache, boolean force) {
    checkOpeness();
    return internal.reload(iObject, iFetchPlan, iIgnoreCache, force);
  }

  @Override
  public <RET extends ORecord> RET load(ORID recordId) {
    checkOpeness();
    return internal.load(recordId);
  }

  @Override
  public <RET extends ORecord> RET load(ORID iRecordId, String iFetchPlan) {
    checkOpeness();
    return internal.load(iRecordId, iFetchPlan);
  }

  @Override
  public <RET extends ORecord> RET load(ORID iRecordId, String iFetchPlan, boolean iIgnoreCache) {
    checkOpeness();
    return internal.load(iRecordId, iFetchPlan, iIgnoreCache);
  }

  @Override
  public <RET extends ORecord> RET load(ORID iRecordId, String iFetchPlan, boolean iIgnoreCache, boolean loadTombstone,
      OStorage.LOCKING_STRATEGY iLockingStrategy) {
    checkOpeness();
    return internal.load(iRecordId, iFetchPlan, iIgnoreCache, loadTombstone, iLockingStrategy);
  }

  @Override
  public <RET extends ORecord> RET load(ORID iRecordId, String iFetchPlan, boolean iIgnoreCache, boolean iUpdateCache,
      boolean loadTombstone, OStorage.LOCKING_STRATEGY iLockingStrategy) {
    checkOpeness();
    return internal.load(iRecordId, iFetchPlan, iIgnoreCache, iUpdateCache, loadTombstone, iLockingStrategy);
  }

  @Override
  public <RET extends ORecord> RET save(ORecord iObject) {
    checkOpeness();
    return internal.save(iObject);
  }

  @Override
  public <RET extends ORecord> RET save(ORecord iObject, OPERATION_MODE iMode, boolean iForceCreate,
      ORecordCallback<? extends Number> iRecordCreatedCallback, ORecordCallback<Integer> iRecordUpdatedCallback) {
    checkOpeness();
    return internal.save(iObject, iMode, iForceCreate, iRecordCreatedCallback, iRecordUpdatedCallback);
  }

  @Override
  public <RET extends ORecord> RET save(ORecord iObject, String iClusterName) {
    checkOpeness();
    return internal.save(iObject, iClusterName);
  }

  @Override
  public <RET extends ORecord> RET save(ORecord iObject, String iClusterName, OPERATION_MODE iMode, boolean iForceCreate,
      ORecordCallback<? extends Number> iRecordCreatedCallback, ORecordCallback<Integer> iRecordUpdatedCallback) {
    checkOpeness();
    return internal.save(iObject, iClusterName, iMode, iForceCreate, iRecordCreatedCallback, iRecordUpdatedCallback);
  }

  @Override
  public ODatabase<ORecord> delete(ORecord iObject) {
    checkOpeness();
    internal.delete(iObject);
    return this;
  }

  @Override
  public ODatabase<ORecord> delete(ORID iRID) {
    checkOpeness();
    internal.delete(iRID);
    return this;
  }

  @Override
  public ODatabase<ORecord> delete(ORID iRID, int iVersion) {
    checkOpeness();
    internal.delete(iRID, iVersion);
    return this;
  }

  @Override
  public boolean hide(ORID rid) {
    checkOpeness();
    return internal.hide(rid);
  }

  @Override
  public ODatabase<ORecord> cleanOutRecord(ORID rid, int version) {
    checkOpeness();
    internal.cleanOutRecord(rid, version);
    return this;
  }

  @Override
  public OTransaction getTransaction() {
    checkOpeness();
    return internal.getTransaction();
  }

  @Override
  public ODatabase<ORecord> begin() {
    checkOpeness();
    internal.begin();
    return this;
  }

  @Override
  public ODatabase<ORecord> begin(OTransaction.TXTYPE iStatus) {
    checkOpeness();
    internal.begin(iStatus);
    return this;
  }

  @Override
  public ODatabase<ORecord> begin(OTransaction iTx) throws OTransactionException {
    checkOpeness();
    internal.begin(iTx);
    return this;
  }

  @Override
  public ODatabase<ORecord> commit() throws OTransactionException {
    checkOpeness();
    internal.commit();
    return this;
  }

  @Override
  public ODatabase<ORecord> commit(boolean force) throws OTransactionException {
    checkOpeness();
    internal.commit(force);
    return this;
  }

  @Override
  public ODatabase<ORecord> rollback() throws OTransactionException {
    checkOpeness();
    internal.rollback();
    return this;
  }

  @Override
  public ODatabase<ORecord> rollback(boolean force) throws OTransactionException {
    checkOpeness();
    internal.rollback(force);
    return this;
  }

  @Override
  public <RET extends List<?>> RET query(OQuery<?> iCommand, Object... iArgs) {
    checkOpeness();
    return internal.query(iCommand, iArgs);
  }

  @Override
  public <RET extends OCommandRequest> RET command(OCommandRequest iCommand) {
    checkOpeness();
    return internal.command(iCommand);
  }

  @Override
  public ORecordIteratorCluster<ODocument> browseCluster(String iClusterName) {
    checkOpeness();
    return internal.browseCluster(iClusterName);
  }

  @Override
  public ORecordIteratorCluster<ODocument> browseCluster(String iClusterName, long startClusterPosition, long endClusterPosition,
      boolean loadTombstones) {
    checkOpeness();
    return internal.browseCluster(iClusterName, startClusterPosition, endClusterPosition, loadTombstones);
  }

  @Override
  public <REC extends ORecord> ORecordIteratorCluster<REC> browseCluster(String iClusterName, Class<REC> iRecordClass) {
    checkOpeness();
    return internal.browseCluster(iClusterName, iRecordClass);
  }

  @Override
  public <REC extends ORecord> ORecordIteratorCluster<REC> browseCluster(String iClusterName, Class<REC> iRecordClass,
      long startClusterPosition, long endClusterPosition) {
    checkOpeness();
    return internal.browseCluster(iClusterName, iRecordClass, startClusterPosition, endClusterPosition);
  }

  @Override
  public <REC extends ORecord> ORecordIteratorCluster<REC> browseCluster(String iClusterName, Class<REC> iRecordClass,
      long startClusterPosition, long endClusterPosition, boolean loadTombstones) {
    checkOpeness();
    return internal.browseCluster(iClusterName, iRecordClass, startClusterPosition, endClusterPosition, loadTombstones);
  }

  @Override
  public <RET extends ORecord> RET getRecord(OIdentifiable iIdentifiable) {
    checkOpeness();
    return internal.getRecord(iIdentifiable);
  }

  @Override
  public byte getRecordType() {
    checkOpeness();
    return internal.getRecordType();
  }

  @Override
  public boolean isRetainRecords() {
    checkOpeness();
    return internal.isRetainRecords();
  }

  @Override
  public ODatabaseDocument setRetainRecords(boolean iValue) {
    checkOpeness();
    return internal.setRetainRecords(iValue);
  }

  @Override
  public <DB extends ODatabaseDocument> DB checkSecurity(ORule.ResourceGeneric resourceGeneric, String resourceSpecific,
      int iOperation) {
    checkOpeness();
    internal.checkSecurity(resourceGeneric, resourceSpecific, iOperation);
    return (DB) this;
  }

  @Override
  public <DB extends ODatabaseDocument> DB checkSecurity(ORule.ResourceGeneric iResourceGeneric, int iOperation,
      Object iResourceSpecific) {
    checkOpeness();
    internal.checkSecurity(iResourceGeneric, iOperation, iResourceSpecific);
    return (DB) this;
  }

  @Override
  public <DB extends ODatabaseDocument> DB checkSecurity(ORule.ResourceGeneric iResourceGeneric, int iOperation,
      Object... iResourcesSpecific) {
    checkOpeness();
    internal.checkSecurity(iResourceGeneric, iOperation, iResourcesSpecific);
    return (DB) this;
  }

  @Override
  public boolean isValidationEnabled() {
    checkOpeness();
    return internal.isValidationEnabled();
  }

  @Override
  public <DB extends ODatabaseDocument> DB setValidationEnabled(boolean iEnabled) {
    checkOpeness();
    internal.setValidationEnabled(iEnabled);
    return (DB) this;
  }

  @Override
  public <DB extends ODatabaseDocument> DB checkSecurity(String iResource, int iOperation) {
    checkOpeness();
    internal.checkSecurity(iResource, iOperation);
    return (DB) this;
  }

  @Override
  public <DB extends ODatabaseDocument> DB checkSecurity(String iResourceGeneric, int iOperation, Object iResourceSpecific) {
    checkOpeness();
    internal.checkSecurity(iResourceGeneric, iOperation, iResourceSpecific);
    return (DB) this;
  }

  @Override
  public <DB extends ODatabaseDocument> DB checkSecurity(String iResourceGeneric, int iOperation, Object... iResourcesSpecific) {
    checkOpeness();
    internal.checkSecurity(iResourceGeneric, iOperation, iResourcesSpecific);
    return (DB) this;
  }

  @Override
  public boolean isPooled() {
    return false;
  }

  @Override
  public <DB extends ODatabase> DB open(String iUserName, String iUserPassword) {
    setupThreadOwner();
    try {
      if ("remote".equals(type)) {
        factory = getOrCreateRemoteFactory(baseUrl);
        OrientDBConfig config = buildConfig(null);
        internal = (ODatabaseDocumentInternal) factory.open(dbName, iUserName, iUserPassword, config);

      } else {
        factory = getOrCreateEmbeddedFactory(baseUrl, null);
        OrientDBConfig config = buildConfig(null);
        internal = (ODatabaseDocumentInternal) factory.open(dbName, iUserName, iUserPassword, config);
      }
      if (databaseOwner != null)
        internal.setDatabaseOwner(databaseOwner);
      if (intent != null)
        internal.declareIntent(intent);
      if (conflictStrategy != null)
        internal.setConflictStrategy(conflictStrategy);
      if (serializer != null)
        internal.setSerializer(serializer);
      for (Entry<String, Object> pro : preopenProperties.entrySet())
        internal.setProperty(pro.getKey(), pro.getValue());
    } catch (RuntimeException e) {
      clearOwner();
      throw e;
    }
    return (DB) this;
  }

  protected void setupThreadOwner() {
    if (!ownerProtection)
      return;

    final Thread current = Thread.currentThread();
    final Thread o = owner.get();

    if (o != null || !owner.compareAndSet(null, current)) {
      throw new IllegalStateException("Current instance is owned by other thread" + (o != null ? " : '" + o.getName() + "'" : ""));
    }
  }
  
  protected void clearOwner() {
    if (!ownerProtection)
      return;
    owner.set(null);
  }

  @Override
  public <DB extends ODatabase> DB create() {
    // TODO
    return create((Map<OGlobalConfiguration, Object>) null);
  }

  @Override
  public <DB extends ODatabase> DB create(String incrementalBackupPath) {
    return null;
  }

  @Override
  public <DB extends ODatabase> DB create(Map<OGlobalConfiguration, Object> iInitialSettings) {
    setupThreadOwner();
    try {
      OrientDBConfig config = buildConfig(iInitialSettings);
      if ("remote".equals(type)) {
        throw new UnsupportedOperationException();
      } else if ("memory".equals(type)) {
        factory = getOrCreateEmbeddedFactory(baseUrl, null);
        factory.create(dbName, null, null, OrientDBFactory.DatabaseType.MEMORY, config);
        OrientDBConfig openConfig = OrientDBConfig.builder().fromContext(config.getConfigurations()).build();
        internal = (ODatabaseDocumentInternal) factory.open(dbName, "admin", "admin", openConfig);
        for (Map.Entry<ATTRIBUTES, Object> attr : preopenAttributes.entrySet()) {
          internal.set(attr.getKey(), attr.getValue());
        }

        for (ODatabaseListener oDatabaseListener : preopenListener) {
          internal.registerListener(oDatabaseListener);
        }

      } else {
        factory = getOrCreateEmbeddedFactory(baseUrl, null);
        factory.create(dbName, null, null, OrientDBFactory.DatabaseType.PLOCAL, config);
        OrientDBConfig openConfig = OrientDBConfig.builder().fromContext(config.getConfigurations()).build();
        internal = (ODatabaseDocumentInternal) factory.open(dbName, "admin", "admin", openConfig);
        for (Map.Entry<ATTRIBUTES, Object> attr : preopenAttributes.entrySet()) {
          internal.set(attr.getKey(), attr.getValue());
        }

        for (ODatabaseListener oDatabaseListener : preopenListener) {
          internal.registerListener(oDatabaseListener);
        }

      }
      if (databaseOwner != null)
        internal.setDatabaseOwner(databaseOwner);
      if (intent != null)
        internal.declareIntent(intent);
      if (conflictStrategy != null)
        internal.setConflictStrategy(conflictStrategy);
      if (serializer != null)
        internal.setSerializer(serializer);
      for (Entry<String, Object> pro : preopenProperties.entrySet())
        internal.setProperty(pro.getKey(), pro.getValue());
    } catch (RuntimeException e) {
      clearOwner();
      throw e;
    }
    return (DB) this;
  }

  @Override
  public ODatabase activateOnCurrentThread() {
    if (internal != null)
      internal.activateOnCurrentThread();
    return this;
  }

  @Override
  public boolean isActiveOnCurrentThread() {
    if (internal != null)
      return internal.isActiveOnCurrentThread();
    return false;
  }

  @Override
  public void reload() {
    checkOpeness();
    internal.reload();
  }

  @Override
  public void drop() {
    checkOpeness();
    this.internal.callOnDropListeners();
    ODatabaseRecordThreadLocal.INSTANCE.remove();
    factory.drop(this.getName(), null, null);
    this.internal = null;
    clearOwner();
  }

  @Override
  public OContextConfiguration getConfiguration() {
    checkOpeness();
    return internal.getConfiguration();
  }

  @Override
  public boolean declareIntent(OIntent iIntent) {
    if (internal != null)
      return internal.declareIntent(iIntent);
    else {
      intent = iIntent;
      return true;
    }
  }

  @Override
  public boolean exists() {
    if (internal != null)
      return true;
    if ("remote".equals(type)) {
      throw new UnsupportedOperationException();
    } else {
      factory = getOrCreateEmbeddedFactory(baseUrl, null);
      return factory.exists(dbName, null, null);
    }
  }

  @Override
  public void close() {
    clearOwner();
    if (internal != null) {
      delegateStorage = internal.getStorage();
      internal.close();
      internal = null;
    }
  }

  @Override
  public STATUS getStatus() {
    return internal.getStatus();
  }

  @Override
  public <DB extends ODatabase> DB setStatus(STATUS iStatus) {
    checkOpeness();
    internal.setStatus(iStatus);
    return (DB) this;
  }

  @Override
  public long getSize() {
    checkOpeness();
    return internal.getSize();
  }

  @Override
  public String getName() {
    return dbName;
  }

  @Override
  public String getURL() {
    return url;
  }

  @Override
  public OLocalRecordCache getLocalCache() {
    checkOpeness();
    return internal.getLocalCache();
  }

  @Override
  public int getDefaultClusterId() {
    checkOpeness();
    return internal.getDefaultClusterId();
  }

  @Override
  public int getClusters() {
    checkOpeness();
    return internal.getClusters();
  }

  @Override
  public boolean existsCluster(String iClusterName) {
    checkOpeness();
    return internal.existsCluster(iClusterName);
  }

  @Override
  public Collection<String> getClusterNames() {
    checkOpeness();
    return internal.getClusterNames();
  }

  @Override
  public int getClusterIdByName(String iClusterName) {
    checkOpeness();
    return internal.getClusterIdByName(iClusterName);
  }

  @Override
  public String getClusterNameById(int iClusterId) {
    checkOpeness();
    return internal.getClusterNameById(iClusterId);
  }

  @Override
  public long getClusterRecordSizeByName(String iClusterName) {
    checkOpeness();
    return internal.getClusterRecordSizeByName(iClusterName);
  }

  @Override
  public long getClusterRecordSizeById(int iClusterId) {
    checkOpeness();
    return internal.getClusterRecordSizeById(iClusterId);
  }

  @Override
  public boolean isClosed() {
    return internal == null || internal.isClosed();
  }

  @Override
  public void truncateCluster(String clusterName) {
    checkOpeness();
    internal.truncateCluster(clusterName);
  }

  @Override
  public long countClusterElements(int iCurrentClusterId) {
    checkOpeness();
    return internal.countClusterElements(iCurrentClusterId);
  }

  @Override
  public long countClusterElements(int iCurrentClusterId, boolean countTombstones) {
    checkOpeness();
    return internal.countClusterElements(iCurrentClusterId, countTombstones);
  }

  @Override
  public long countClusterElements(int[] iClusterIds) {
    checkOpeness();
    return internal.countClusterElements(iClusterIds);
  }

  @Override
  public long countClusterElements(int[] iClusterIds, boolean countTombstones) {
    checkOpeness();
    return internal.countClusterElements(iClusterIds, countTombstones);
  }

  @Override
  public long countClusterElements(String iClusterName) {
    checkOpeness();
    return internal.countClusterElements(iClusterName);
  }

  @Override
  public int addCluster(String iClusterName, Object... iParameters) {
    checkOpeness();
    return internal.addCluster(iClusterName, iParameters);
  }

  @Override
  public int addBlobCluster(String iClusterName, Object... iParameters) {
    checkOpeness();
    return internal.addBlobCluster(iClusterName, iParameters);
  }

  @Override
  public Set<Integer> getBlobClusterIds() {
    checkOpeness();
    return internal.getBlobClusterIds();
  }

  @Override
  public int addCluster(String iClusterName, int iRequestedId, Object... iParameters) {
    checkOpeness();
    return internal.addCluster(iClusterName, iRequestedId, iParameters);
  }

  @Override
  public boolean dropCluster(String iClusterName, boolean iTruncate) {
    checkOpeness();
    return internal.dropCluster(iClusterName, iTruncate);
  }

  @Override
  public boolean dropCluster(int iClusterId, boolean iTruncate) {
    checkOpeness();
    return internal.dropCluster(iClusterId, iTruncate);
  }

  @Override
  public Object setProperty(String iName, Object iValue) {
    if (internal != null)
      return internal.setProperty(iName, iValue);
    else
      return preopenProperties.put(iName, iValue);
  }

  @Override
  public Object getProperty(String iName) {
    if (internal != null)
      return internal.getProperty(iName);
    else
      return preopenProperties.get(iName);
  }

  @Override
  public Iterator<Map.Entry<String, Object>> getProperties() {
    checkOpeness();
    return internal.getProperties();
  }

  @Override
  public Object get(ATTRIBUTES iAttribute) {
    if (internal != null) {
      return internal.get(iAttribute);
    } else {
      return preopenAttributes.get(iAttribute);
    }
  }

  @Override
  public <DB extends ODatabase> DB set(ATTRIBUTES iAttribute, Object iValue) {
    if (internal != null)
      internal.set(iAttribute, iValue);
    else
      preopenAttributes.put(iAttribute, iValue);
    return (DB) this;
  }

  @Override
  public void registerListener(ODatabaseListener iListener) {
    if (internal != null) {
      internal.registerListener(iListener);
    } else {
      preopenListener.add(iListener);
    }
  }

  @Override
  public void unregisterListener(ODatabaseListener iListener) {
    checkOpeness();
    internal.unregisterListener(iListener);
  }

  @Override
  public ORecordMetadata getRecordMetadata(ORID rid) {
    checkOpeness();
    return internal.getRecordMetadata(rid);
  }

  @Override
  public ODocument newInstance(String iClassName) {
    checkOpeness();
    return internal.newInstance(iClassName);
  }

  @Override
  public long countClass(String iClassName) {
    checkOpeness();
    return internal.countClass(iClassName);
  }

  @Override
  public long countClass(String iClassName, boolean iPolymorphic) {
    checkOpeness();
    return internal.countClass(iClassName, iPolymorphic);
  }

  @Override
  public List<String> backup(OutputStream out, Map<String, Object> options, Callable<Object> callable,
      OCommandOutputListener iListener, int compressionLevel, int bufferSize) throws IOException {
    checkOpeness();
    return internal.backup(out, options, callable, iListener, compressionLevel, bufferSize);
  }

  @Override
  public void restore(InputStream in, Map<String, Object> options, Callable<Object> callable, OCommandOutputListener iListener)
      throws IOException {
    checkOpeness();
    internal.restore(in, options, callable, iListener);
  }

  public void setSerializer(ORecordSerializer serializer) {
    if (internal != null) {
      ((ODatabaseDocumentAbstract) internal).setSerializer(serializer);
    } else {
      this.serializer = serializer;
    }
  }

  @Override
  public OTodoResultSet query(String query, Object... args) {
    checkOpeness();
    return internal.query(query, args);
  }

  @Override
  public OTodoResultSet query(String query, Map args) throws OCommandSQLParsingException, OCommandExecutionException {
    checkOpeness();
    return internal.query(query, args);
  }

  private OrientDBConfig buildConfig(final Map<OGlobalConfiguration, Object> iProperties) {
    Map<String, Object> pars = new HashMap<>(preopenProperties);
    if (iProperties != null) {
      for (Map.Entry<OGlobalConfiguration, Object> par : iProperties.entrySet()) {
        pars.put(par.getKey().getKey(), par.getValue());
      }
    }
    OrientDBConfigBuilder builder = OrientDBConfig.builder();
    final String connectionStrategy = pars != null ? (String) pars.get("connectionStrategy") : null;
    if (connectionStrategy != null)
      builder.addConfig(OGlobalConfiguration.CLIENT_CONNECTION_STRATEGY, connectionStrategy);

    final String compressionMethod = pars != null ? (String) pars.get(OGlobalConfiguration.STORAGE_COMPRESSION_METHOD.getKey())
        : null;
    if (compressionMethod != null)
      // SAVE COMPRESSION METHOD IN CONFIGURATION
      builder.addConfig(OGlobalConfiguration.STORAGE_COMPRESSION_METHOD, compressionMethod);

    final String encryptionMethod = pars != null ? (String) pars.get(OGlobalConfiguration.STORAGE_ENCRYPTION_METHOD.getKey())
        : null;
    if (encryptionMethod != null)
      // SAVE ENCRYPTION METHOD IN CONFIGURATION
      builder.addConfig(OGlobalConfiguration.STORAGE_ENCRYPTION_METHOD, encryptionMethod);

    final String encryptionKey = pars != null ? (String) pars.get(OGlobalConfiguration.STORAGE_ENCRYPTION_KEY.getKey()) : null;
    if (encryptionKey != null)
      // SAVE ENCRYPTION KEY IN CONFIGURATION
      builder.addConfig(OGlobalConfiguration.STORAGE_ENCRYPTION_KEY, encryptionKey);

    for (Map.Entry<ATTRIBUTES, Object> attr : preopenAttributes.entrySet()) {
      builder.addAttribute(attr.getKey(), attr.getValue());
    }

    for (ODatabaseListener oDatabaseListener : preopenListener) {
      builder.addListener(oDatabaseListener);
    }

    return builder.build();
  }

  @Override
  public OTodoResultSet command(String query, Object... args) throws OCommandSQLParsingException, OCommandExecutionException {
    checkOpeness();
    return internal.command(query, args);
  }

  public OTodoResultSet command(String query, Map args) throws OCommandSQLParsingException, OCommandExecutionException {
    checkOpeness();
    return internal.command(query, args);
  }

  @Override
  public <DB extends ODatabase> DB setCustom(String name, Object iValue) {
    return internal.setCustom(name, iValue);
  }

  @Override
  public void callOnDropListeners() {
    checkOpeness();
    internal.callOnDropListeners();
  }

  @Override
  public boolean isPrefetchRecords() {
    checkOpeness();
    return internal.isPrefetchRecords();
  }

  public void setPrefetchRecords(boolean prefetchRecords) {
    checkOpeness();
    internal.setPrefetchRecords(prefetchRecords);
  }
  
  public void checkForClusterPermissions(String name) {
    checkOpeness();
    internal.checkForClusterPermissions(name);
  }
  
}
