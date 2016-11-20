package com.orientechnologies.orient.server;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;

import com.orientechnologies.common.exception.OException;
import com.orientechnologies.common.log.OLogManager;
import com.orientechnologies.common.serialization.types.OBinarySerializer;
import com.orientechnologies.common.serialization.types.OByteSerializer;
import com.orientechnologies.common.serialization.types.ONullSerializer;
import com.orientechnologies.common.util.OCommonConst;
import com.orientechnologies.orient.client.binary.OBinaryRequestExecutor;
import com.orientechnologies.orient.client.remote.OBinaryResponse;
import com.orientechnologies.orient.client.remote.message.OAddClusterRequest;
import com.orientechnologies.orient.client.remote.message.OAddClusterResponse;
import com.orientechnologies.orient.client.remote.message.OCeilingPhysicalPositionsRequest;
import com.orientechnologies.orient.client.remote.message.OCeilingPhysicalPositionsResponse;
import com.orientechnologies.orient.client.remote.message.OCleanOutRecordRequest;
import com.orientechnologies.orient.client.remote.message.OCleanOutRecordResponse;
import com.orientechnologies.orient.client.remote.message.OCloseRequest;
import com.orientechnologies.orient.client.remote.message.OCommandRequest;
import com.orientechnologies.orient.client.remote.message.OCommandResponse;
import com.orientechnologies.orient.client.remote.message.OCommitRequest;
import com.orientechnologies.orient.client.remote.message.OCommitResponse;
import com.orientechnologies.orient.client.remote.message.OCommitResponse.OCreatedRecordResponse;
import com.orientechnologies.orient.client.remote.message.OCommitResponse.OUpdatedRecordResponse;
import com.orientechnologies.orient.client.remote.message.OConnectRequest;
import com.orientechnologies.orient.client.remote.message.OConnectResponse;
import com.orientechnologies.orient.client.remote.message.OCountRecordsRequest;
import com.orientechnologies.orient.client.remote.message.OCountRecordsResponse;
import com.orientechnologies.orient.client.remote.message.OCountRequest;
import com.orientechnologies.orient.client.remote.message.OCountResponse;
import com.orientechnologies.orient.client.remote.message.OCreateDatabaseRequest;
import com.orientechnologies.orient.client.remote.message.OCreateDatabaseResponse;
import com.orientechnologies.orient.client.remote.message.OCreateRecordRequest;
import com.orientechnologies.orient.client.remote.message.OCreateRecordResponse;
import com.orientechnologies.orient.client.remote.message.ODeleteRecordRequest;
import com.orientechnologies.orient.client.remote.message.ODeleteRecordResponse;
import com.orientechnologies.orient.client.remote.message.ODistributedStatusRequest;
import com.orientechnologies.orient.client.remote.message.ODistributedStatusResponse;
import com.orientechnologies.orient.client.remote.message.ODropClusterRequest;
import com.orientechnologies.orient.client.remote.message.ODropClusterResponse;
import com.orientechnologies.orient.client.remote.message.ODropDatabaseRequest;
import com.orientechnologies.orient.client.remote.message.ODropDatabaseResponse;
import com.orientechnologies.orient.client.remote.message.OExistsDatabaseRequest;
import com.orientechnologies.orient.client.remote.message.OExistsDatabaseResponse;
import com.orientechnologies.orient.client.remote.message.OFloorPhysicalPositionsRequest;
import com.orientechnologies.orient.client.remote.message.OFloorPhysicalPositionsResponse;
import com.orientechnologies.orient.client.remote.message.OFreezeDatabaseRequest;
import com.orientechnologies.orient.client.remote.message.OFreezeDatabaseResponse;
import com.orientechnologies.orient.client.remote.message.OGetClusterDataRangeRequest;
import com.orientechnologies.orient.client.remote.message.OGetClusterDataRangeResponse;
import com.orientechnologies.orient.client.remote.message.OGetGlobalConfigurationRequest;
import com.orientechnologies.orient.client.remote.message.OGetGlobalConfigurationResponse;
import com.orientechnologies.orient.client.remote.message.OGetRecordMetadataRequest;
import com.orientechnologies.orient.client.remote.message.OGetRecordMetadataResponse;
import com.orientechnologies.orient.client.remote.message.OGetSizeRequest;
import com.orientechnologies.orient.client.remote.message.OGetSizeResponse;
import com.orientechnologies.orient.client.remote.message.OHideRecordRequest;
import com.orientechnologies.orient.client.remote.message.OHideRecordResponse;
import com.orientechnologies.orient.client.remote.message.OHigherPhysicalPositionsRequest;
import com.orientechnologies.orient.client.remote.message.OHigherPhysicalPositionsResponse;
import com.orientechnologies.orient.client.remote.message.OImportRequest;
import com.orientechnologies.orient.client.remote.message.OImportResponse;
import com.orientechnologies.orient.client.remote.message.OIncrementalBackupRequest;
import com.orientechnologies.orient.client.remote.message.OIncrementalBackupResponse;
import com.orientechnologies.orient.client.remote.message.OListDatabasesRequest;
import com.orientechnologies.orient.client.remote.message.OListDatabasesResponse;
import com.orientechnologies.orient.client.remote.message.OListGlobalConfigurationsRequest;
import com.orientechnologies.orient.client.remote.message.OListGlobalConfigurationsResponse;
import com.orientechnologies.orient.client.remote.message.OLowerPhysicalPositionsRequest;
import com.orientechnologies.orient.client.remote.message.OLowerPhysicalPositionsResponse;
import com.orientechnologies.orient.client.remote.message.OOpenRequest;
import com.orientechnologies.orient.client.remote.message.OOpenResponse;
import com.orientechnologies.orient.client.remote.message.OReadRecordIfVersionIsNotLatestRequest;
import com.orientechnologies.orient.client.remote.message.OReadRecordIfVersionIsNotLatestResponse;
import com.orientechnologies.orient.client.remote.message.OReadRecordRequest;
import com.orientechnologies.orient.client.remote.message.OReadRecordResponse;
import com.orientechnologies.orient.client.remote.message.OReleaseDatabaseRequest;
import com.orientechnologies.orient.client.remote.message.OReleaseDatabaseResponse;
import com.orientechnologies.orient.client.remote.message.OReloadRequest;
import com.orientechnologies.orient.client.remote.message.OReloadResponse;
import com.orientechnologies.orient.client.remote.message.OReopenRequest;
import com.orientechnologies.orient.client.remote.message.OReopenResponse;
import com.orientechnologies.orient.client.remote.message.OSBTCreateTreeRequest;
import com.orientechnologies.orient.client.remote.message.OSBTCreateTreeResponse;
import com.orientechnologies.orient.client.remote.message.OSBTFetchEntriesMajorRequest;
import com.orientechnologies.orient.client.remote.message.OSBTFetchEntriesMajorResponse;
import com.orientechnologies.orient.client.remote.message.OSBTFirstKeyRequest;
import com.orientechnologies.orient.client.remote.message.OSBTFirstKeyResponse;
import com.orientechnologies.orient.client.remote.message.OSBTGetRealBagSizeRequest;
import com.orientechnologies.orient.client.remote.message.OSBTGetRealBagSizeResponse;
import com.orientechnologies.orient.client.remote.message.OSBTGetRequest;
import com.orientechnologies.orient.client.remote.message.OSBTGetResponse;
import com.orientechnologies.orient.client.remote.message.OServerInfoRequest;
import com.orientechnologies.orient.client.remote.message.OServerInfoResponse;
import com.orientechnologies.orient.client.remote.message.OSetGlobalConfigurationRequest;
import com.orientechnologies.orient.client.remote.message.OSetGlobalConfigurationResponse;
import com.orientechnologies.orient.client.remote.message.OShutdownRequest;
import com.orientechnologies.orient.client.remote.message.OShutdownResponse;
import com.orientechnologies.orient.client.remote.message.OUpdateRecordRequest;
import com.orientechnologies.orient.client.remote.message.OUpdateRecordResponse;
import com.orientechnologies.orient.core.OConstants;
import com.orientechnologies.orient.core.Orient;
import com.orientechnologies.orient.core.cache.OCommandCache;
import com.orientechnologies.orient.core.command.OCommandOutputListener;
import com.orientechnologies.orient.core.command.OCommandRequestText;
import com.orientechnologies.orient.core.command.OCommandResultListener;
import com.orientechnologies.orient.core.config.OGlobalConfiguration;
import com.orientechnologies.orient.core.db.ODatabase;
import com.orientechnologies.orient.core.db.ODatabaseDocumentInternal;
import com.orientechnologies.orient.core.db.ODatabaseRecordThreadLocal;
import com.orientechnologies.orient.core.db.OrientDBFactory.DatabaseType;
import com.orientechnologies.orient.core.db.record.OIdentifiable;
import com.orientechnologies.orient.core.db.record.ridbag.sbtree.OBonsaiCollectionPointer;
import com.orientechnologies.orient.core.db.record.ridbag.sbtree.OSBTreeCollectionManager;
import com.orientechnologies.orient.core.db.tool.ODatabaseImport;
import com.orientechnologies.orient.core.exception.OConfigurationException;
import com.orientechnologies.orient.core.exception.ODatabaseException;
import com.orientechnologies.orient.core.exception.ORecordNotFoundException;
import com.orientechnologies.orient.core.exception.OSecurityAccessException;
import com.orientechnologies.orient.core.fetch.OFetchContext;
import com.orientechnologies.orient.core.fetch.OFetchHelper;
import com.orientechnologies.orient.core.fetch.OFetchListener;
import com.orientechnologies.orient.core.fetch.OFetchPlan;
import com.orientechnologies.orient.core.fetch.remote.ORemoteFetchContext;
import com.orientechnologies.orient.core.fetch.remote.ORemoteFetchListener;
import com.orientechnologies.orient.core.id.ORecordId;
import com.orientechnologies.orient.core.index.sbtree.OTreeInternal;
import com.orientechnologies.orient.core.index.sbtreebonsai.local.OSBTreeBonsai;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.ORecord;
import com.orientechnologies.orient.core.record.ORecordInternal;
import com.orientechnologies.orient.core.record.impl.OBlob;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.record.impl.ODocumentInternal;
import com.orientechnologies.orient.core.serialization.serializer.record.ORecordSerializer;
import com.orientechnologies.orient.core.serialization.serializer.record.ORecordSerializerFactory;
import com.orientechnologies.orient.core.sql.query.OSQLAsynchQuery;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;
import com.orientechnologies.orient.core.storage.OCluster;
import com.orientechnologies.orient.core.storage.OPhysicalPosition;
import com.orientechnologies.orient.core.storage.ORecordMetadata;
import com.orientechnologies.orient.core.storage.OStorage;
import com.orientechnologies.orient.core.storage.OStorageProxy;
import com.orientechnologies.orient.core.storage.impl.local.paginated.OOfflineClusterException;
import com.orientechnologies.orient.enterprise.channel.binary.OChannelBinaryProtocol;
import com.orientechnologies.orient.server.distributed.ODistributedConfiguration;
import com.orientechnologies.orient.server.distributed.ODistributedServerManager;
import com.orientechnologies.orient.server.network.protocol.binary.OAbstractCommandResultListener;
import com.orientechnologies.orient.server.network.protocol.binary.OAsyncCommandResultListener;
import com.orientechnologies.orient.server.network.protocol.binary.OCommandCacheRemoteResultListener;
import com.orientechnologies.orient.server.network.protocol.binary.OLiveCommandResultListener;
import com.orientechnologies.orient.server.network.protocol.binary.ONetworkProtocolBinary;
import com.orientechnologies.orient.server.network.protocol.binary.OSyncCommandResultListener;
import com.orientechnologies.orient.server.plugin.OServerPlugin;
import com.orientechnologies.orient.server.tx.OTransactionOptimisticProxy;

final class OConnectionBinaryExecutor implements OBinaryRequestExecutor {

  private final OClientConnection connection;
  private final OServer           server;

  public OConnectionBinaryExecutor(OClientConnection connection, OServer server) {
    this.connection = connection;
    this.server = server;
  }

  @Override
  public OListDatabasesResponse executeListDatabases(OListDatabasesRequest request) {

    Set<String> dbs = server.listDatabases();
    String listener = server.getListenerByProtocol(ONetworkProtocolBinary.class).getInboundAddr().toString();
    Map<String, String> toSend = new HashMap<String, String>();
    for (String dbName : dbs) {
      toSend.put(dbName, "remote:" + listener + "/" + dbName);
    }
    return new OListDatabasesResponse(toSend);
  }

  @Override
  public OBinaryResponse executeServerInfo(OServerInfoRequest request) {
    try {
      return new OServerInfoResponse(OServerInfo.getServerInfo(server));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public OBinaryResponse executeDBReload(OReloadRequest request) {
    final Collection<? extends OCluster> clusters = connection.getDatabase().getStorage().getClusterInstances();
    return new OReloadResponse(clusters.toArray(new OCluster[clusters.size()]));
  }

  @Override
  public OBinaryResponse executeCreateDatabase(OCreateDatabaseRequest request) {

    if (server.existsDatabase(request.getDatabaseName()))
      throw new ODatabaseException("Database named '" + request.getDatabaseName() + "' already exists");
    if (request.getBackupPath() != null && !"".equals(request.getBackupPath().trim())) {
      server.restore(request.getDatabaseName(), request.getBackupPath());
    } else {
      server.createDatabase(request.getDatabaseName(), DatabaseType.valueOf(request.getStorageMode().toUpperCase()), null);
    }
    OLogManager.instance().info(this, "Created database '%s' of type '%s'", request.getDatabaseName(), request.getStorageMode());

    // TODO: it should be here an additional check for open with the right user
    connection.setDatabase(
        server.openDatabase(request.getDatabaseName(), connection.getData().serverUsername, null, connection.getData(), true));

    return new OCreateDatabaseResponse();
  }

  @Override
  public OBinaryResponse executeClose(OCloseRequest request) {
    server.getClientConnectionManager().disconnect(connection);
    return null;
  }

  @Override
  public OBinaryResponse executeExistDatabase(OExistsDatabaseRequest request) {
    boolean result = server.existsDatabase(request.getDatabaseName());
    return new OExistsDatabaseResponse(result);
  }

  @Override
  public OBinaryResponse executeDropDatabase(ODropDatabaseRequest request) {

    server.dropDatabase(request.getDatabaseName());
    OLogManager.instance().info(this, "Dropped database '%s'", request.getDatabaseName());
    connection.close();
    return new ODropDatabaseResponse();
  }

  @Override
  public OBinaryResponse executeGetSize(OGetSizeRequest request) {
    return new OGetSizeResponse(connection.getDatabase().getStorage().getSize());
  }

  @Override
  public OBinaryResponse executeCountRecords(OCountRecordsRequest request) {
    return new OCountRecordsResponse(connection.getDatabase().getStorage().countRecords());
  }

  @Override
  public OBinaryResponse executeDistributedStatus(ODistributedStatusRequest request) {
    final ODocument req = request.getStatus();
    ODocument clusterConfig = null;

    final String operation = req.field("operation");
    if (operation == null)
      throw new IllegalArgumentException("Cluster operation is null");

    if (operation.equals("status")) {
      final OServerPlugin plugin = server.getPlugin("cluster");
      if (plugin != null && plugin instanceof ODistributedServerManager)
        clusterConfig = ((ODistributedServerManager) plugin).getClusterConfiguration();
    } else
      throw new IllegalArgumentException("Cluster operation '" + operation + "' is not supported");

    return new ODistributedStatusResponse(clusterConfig);
  }

  @Override
  public OBinaryResponse executeCountCluster(OCountRequest request) {
    final long count = connection.getDatabase().countClusterElements(request.getClusterIds(), request.isCountTombstones());
    return new OCountResponse(count);
  }

  @Override
  public OBinaryResponse executeClusterDataRange(OGetClusterDataRangeRequest request) {
    final long[] pos = connection.getDatabase().getStorage().getClusterDataRange(request.getClusterId());
    return new OGetClusterDataRangeResponse(pos);
  }

  @Override
  public OBinaryResponse executeAddCluster(OAddClusterRequest request) {
    final int num;
    if (request.getRequestedId() < 0)
      num = connection.getDatabase().addCluster(request.getClusterName());
    else
      num = connection.getDatabase().addCluster(request.getClusterName(), request.getRequestedId(), null);

    return new OAddClusterResponse(num);
  }

  @Override
  public OBinaryResponse executeDropCluster(ODropClusterRequest request) {
    final String clusterName = connection.getDatabase().getClusterNameById(request.getClusterId());
    if (clusterName == null)
      throw new IllegalArgumentException("Cluster " + request.getClusterId()
          + " does not exist anymore. Refresh the db structure or just reconnect to the database");

    boolean result = connection.getDatabase().dropCluster(clusterName, false);
    return new ODropClusterResponse(result);
  }

  @Override
  public OBinaryResponse executeGetRecordMetadata(OGetRecordMetadataRequest request) {
    final ORecordMetadata metadata = connection.getDatabase().getRecordMetadata(request.getRid());
    if (metadata != null) {
      return new OGetRecordMetadataResponse(metadata);
    } else {
      throw new ODatabaseException(String.format("Record metadata for RID: %s, Not found", request.getRid()));
    }
  }

  @Override
  public OBinaryResponse executeReadRecord(OReadRecordRequest request) {
    final ORecordId rid = request.getRid();
    final String fetchPlanString = request.getFetchPlan();
    boolean ignoreCache = false;
    ignoreCache = request.isIgnoreCache();

    boolean loadTombstones = false;
    loadTombstones = request.isLoadTumbstone();
    OReadRecordResponse response;
    if (rid.getClusterId() == 0 && rid.getClusterPosition() == 0) {
      // @COMPATIBILITY 0.9.25
      // SEND THE DB CONFIGURATION INSTEAD SINCE IT WAS ON RECORD 0:0
      OFetchHelper.checkFetchPlanValid(fetchPlanString);

      final byte[] record = connection.getDatabase().getStorage().callInLock(new Callable<byte[]>() {
        @Override
        public byte[] call() throws Exception {
          return connection.getDatabase().getStorage().getConfiguration().toStream(connection.getData().protocolVersion);
        }
      }, false);

      response = new OReadRecordResponse(OBlob.RECORD_TYPE, 0, record, new HashSet<>());

    } else {
      final ORecord record = connection.getDatabase().load(rid, fetchPlanString, ignoreCache, loadTombstones,
          OStorage.LOCKING_STRATEGY.NONE);
      if (record != null) {
        byte[] bytes = getRecordBytes(connection, record);
        final Set<ORecord> recordsToSend = new HashSet<ORecord>();
        if (record != null) {
          if (fetchPlanString.length() > 0) {
            // BUILD THE SERVER SIDE RECORD TO ACCES TO THE FETCH
            // PLAN
            if (record instanceof ODocument) {
              final OFetchPlan fetchPlan = OFetchHelper.buildFetchPlan(fetchPlanString);

              final ODocument doc = (ODocument) record;
              final OFetchListener listener = new ORemoteFetchListener() {
                @Override
                protected void sendRecord(ORecord iLinked) {
                  recordsToSend.add(iLinked);
                }
              };
              final OFetchContext context = new ORemoteFetchContext();
              OFetchHelper.fetch(doc, doc, fetchPlan, listener, context, "");

            }
          }
        }
        response = new OReadRecordResponse(ORecordInternal.getRecordType(record), record.getVersion(), bytes, recordsToSend);
      } else {
        // No Record to send
        response = new OReadRecordResponse((byte) 0, 0, null, null);
      }

    }
    return response;
  }

  @Override
  public OBinaryResponse executeReadRecordIfNotLastest(OReadRecordIfVersionIsNotLatestRequest request) {

    final ORecordId rid = request.getRid();
    final int recordVersion = request.getRecordVersion();
    final String fetchPlanString = request.getFetchPlan();

    boolean ignoreCache = request.isIgnoreCache();

    OReadRecordIfVersionIsNotLatestResponse response;
    if (rid.getClusterId() == 0 && rid.getClusterPosition() == 0) {
      // @COMPATIBILITY 0.9.25
      // SEND THE DB CONFIGURATION INSTEAD SINCE IT WAS ON RECORD 0:0
      OFetchHelper.checkFetchPlanValid(fetchPlanString);

      final byte[] record = connection.getDatabase().getStorage().callInLock(new Callable<byte[]>() {
        @Override
        public byte[] call() throws Exception {
          return connection.getDatabase().getStorage().getConfiguration().toStream(connection.getData().protocolVersion);
        }
      }, false);

      response = new OReadRecordIfVersionIsNotLatestResponse(OBlob.RECORD_TYPE, 0, record, new HashSet<>());

    } else {
      final ORecord record = connection.getDatabase().loadIfVersionIsNotLatest(rid, recordVersion, fetchPlanString, ignoreCache);

      if (record != null) {
        byte[] bytes = getRecordBytes(connection, record);
        final Set<ORecord> recordsToSend = new HashSet<ORecord>();
        if (fetchPlanString.length() > 0) {
          // BUILD THE SERVER SIDE RECORD TO ACCES TO THE FETCH
          // PLAN
          if (record instanceof ODocument) {
            final OFetchPlan fetchPlan = OFetchHelper.buildFetchPlan(fetchPlanString);

            final ODocument doc = (ODocument) record;
            final OFetchListener listener = new ORemoteFetchListener() {
              @Override
              protected void sendRecord(ORecord iLinked) {
                recordsToSend.add(iLinked);
              }
            };
            final OFetchContext context = new ORemoteFetchContext();
            OFetchHelper.fetch(doc, doc, fetchPlan, listener, context, "");
          }
        }
        response = new OReadRecordIfVersionIsNotLatestResponse(ORecordInternal.getRecordType(record), record.getVersion(), bytes,
            recordsToSend);
      } else {
        response = new OReadRecordIfVersionIsNotLatestResponse((byte) 0, 0, null, null);
      }
    }
    return response;
  }

  @Override
  public OBinaryResponse executeCreateRecord(OCreateRecordRequest request) {

    final ORecord record = Orient.instance().getRecordFactoryManager().newInstance(request.getRecordType());
    fillRecord(connection, request.getRid(), request.getContent(), 0, record);
    if (record instanceof ODocument) {
      // Force conversion of value to class for trigger default values.
      ODocumentInternal.autoConvertValueToClass(connection.getDatabase(), (ODocument) record);
    }
    connection.getDatabase().save(record);

    if (request.getMode() < 2) {
      Map<UUID, OBonsaiCollectionPointer> changedIds;
      OSBTreeCollectionManager collectionManager = connection.getDatabase().getSbTreeCollectionManager();
      if (collectionManager != null) {
        changedIds = new HashMap<>(collectionManager.changedIds());
        collectionManager.clearChangedIds();
      } else
        changedIds = new HashMap<>();

      return new OCreateRecordResponse((ORecordId) record.getIdentity(), record.getVersion(), changedIds);
    }
    return null;
  }

  @Override
  public OBinaryResponse executeUpdateRecord(OUpdateRecordRequest request) {

    ODatabaseDocumentInternal database = connection.getDatabase();
    final ORecord newRecord = Orient.instance().getRecordFactoryManager().newInstance(request.getRecordType());
    fillRecord(connection, request.getRid(), request.getContent(), request.getVersion(), newRecord);

    ORecordInternal.setContentChanged(newRecord, request.isUpdateContent());
    ORecordInternal.getDirtyManager(newRecord).clearForSave();
    ORecord currentRecord = null;
    if (newRecord instanceof ODocument) {
      try {
        currentRecord = database.load(request.getRid());
      } catch (ORecordNotFoundException e) {
        // MAINTAIN COHERENT THE BEHAVIOR FOR ALL THE STORAGE TYPES
        if (e.getCause() instanceof OOfflineClusterException)
          throw (OOfflineClusterException) e.getCause();
      }

      if (currentRecord == null)
        throw new ORecordNotFoundException(request.getRid());

      ((ODocument) currentRecord).merge((ODocument) newRecord, false, false);

    } else
      currentRecord = newRecord;

    ORecordInternal.setVersion(currentRecord, request.getVersion());

    database.save(currentRecord);

    if (currentRecord.getIdentity().toString().equals(database.getStorage().getConfiguration().indexMgrRecordId)
        && !database.getStatus().equals(ODatabase.STATUS.IMPORTING)) {
      // FORCE INDEX MANAGER UPDATE. THIS HAPPENS FOR DIRECT CHANGES FROM REMOTE LIKE IN GRAPH
      database.getMetadata().getIndexManager().reload();
    }
    final int newVersion = currentRecord.getVersion();

    if (request.getMode() < 2) {
      Map<UUID, OBonsaiCollectionPointer> changedIds;
      OSBTreeCollectionManager collectionManager = connection.getDatabase().getSbTreeCollectionManager();
      if (collectionManager != null) {
        changedIds = new HashMap<>(collectionManager.changedIds());
        collectionManager.clearChangedIds();
      } else
        changedIds = new HashMap<>();

      return new OUpdateRecordResponse(newVersion, changedIds);
    }
    return null;
  }

  @Override
  public OBinaryResponse executeDeleteRecord(ODeleteRecordRequest request) {

    int result;
    ODatabaseDocumentInternal database = connection.getDatabase();
    try {
      ORecord record = database.load(request.getRid());
      if (record != null) {
        database.delete(request.getRid(), request.getVersion());
        result = 1;
      } else
        result = 0;
    } catch (ORecordNotFoundException e) {
      // MAINTAIN COHERENT THE BEHAVIOR FOR ALL THE STORAGE TYPES
      if (e.getCause() instanceof OOfflineClusterException)
        throw (OOfflineClusterException) e.getCause();
      result = 0;
    }

    if (request.getMode() < 2) {
      return new ODeleteRecordResponse(result == 1);
    }
    return null;
  }

  @Override
  public OBinaryResponse executeHideRecord(OHideRecordRequest request) {

    int result;
    try {
      connection.getDatabase().hide(request.getRecordId());
      result = 1;
    } catch (ORecordNotFoundException e) {
      result = 0;
    }

    if (request.getMode() < 2) {
      return new OHideRecordResponse(result == 1);

    }
    return null;
  }

  @Override
  public OBinaryResponse executeHigherPosition(OHigherPhysicalPositionsRequest request) {
    OPhysicalPosition[] nextPositions = connection.getDatabase().getStorage().higherPhysicalPositions(request.getClusterId(),
        request.getClusterPosition());
    return new OHigherPhysicalPositionsResponse(nextPositions);
  }

  @Override
  public OBinaryResponse executeCeilingPosition(OCeilingPhysicalPositionsRequest request) {
    final OPhysicalPosition[] previousPositions = connection.getDatabase().getStorage()
        .ceilingPhysicalPositions(request.getClusterId(), request.getPhysicalPosition());
    return new OCeilingPhysicalPositionsResponse(previousPositions);
  }

  @Override
  public OBinaryResponse executeLowerPosition(OLowerPhysicalPositionsRequest request) {
    final OPhysicalPosition[] previousPositions = connection.getDatabase().getStorage()
        .lowerPhysicalPositions(request.getiClusterId(), request.getPhysicalPosition());
    return new OLowerPhysicalPositionsResponse(previousPositions);
  }

  @Override
  public OBinaryResponse executeFloorPosition(OFloorPhysicalPositionsRequest request) {
    final OPhysicalPosition[] previousPositions = connection.getDatabase().getStorage()
        .floorPhysicalPositions(request.getClusterId(), request.getPhysicalPosition());
    return new OFloorPhysicalPositionsResponse(previousPositions);
  }

  @Override
  public OBinaryResponse executeCommand(OCommandRequest request) {

    final boolean live = request.isLive();
    final boolean asynch = request.isAsynch();

    String dbSerializerName = connection.getDatabase().getSerializer().toString();
    String name = connection.getData().serializationImpl;
    if (name == null)
      name = dbSerializerName;
    ORecordSerializer ser = ORecordSerializerFactory.instance().getFormat(name);

    OCommandRequestText command = request.getQuery();

    final Map<Object, Object> params = command.getParameters();

    if (asynch && command instanceof OSQLSynchQuery) {
      // CONVERT IT IN ASYNCHRONOUS QUERY
      final OSQLAsynchQuery asynchQuery = new OSQLAsynchQuery(command.getText());
      asynchQuery.setFetchPlan(command.getFetchPlan());
      asynchQuery.setLimit(command.getLimit());
      asynchQuery.setTimeout(command.getTimeoutTime(), command.getTimeoutStrategy());
      asynchQuery.setUseCache(((OSQLSynchQuery) command).isUseCache());
      command = asynchQuery;
    }

    connection.getData().commandDetail = command.getText();

    connection.getData().command = command;
    OAbstractCommandResultListener listener = null;
    OLiveCommandResultListener liveListener = null;

    OCommandResultListener cmdResultListener = command.getResultListener();

    if (live) {
      liveListener = new OLiveCommandResultListener(server, connection, cmdResultListener);
      listener = new OSyncCommandResultListener(null);
      command.setResultListener(liveListener);
    } else if (asynch) {
      // IF COMMAND CACHE IS ENABLED, RESULT MUST BE COLLECTED
      final OCommandCache cmdCache = connection.getDatabase().getMetadata().getCommandCache();
      if (cmdCache.isEnabled())
        // CREATE E COLLECTOR OF RESULT IN RAM TO CACHE THE RESULT
        cmdResultListener = new OCommandCacheRemoteResultListener(cmdResultListener, cmdCache);

      listener = new OAsyncCommandResultListener(connection, cmdResultListener);
      command.setResultListener(listener);
    } else {
      listener = new OSyncCommandResultListener(null);
    }

    final long serverTimeout = OGlobalConfiguration.COMMAND_TIMEOUT.getValueAsLong();

    if (serverTimeout > 0 && command.getTimeoutTime() > serverTimeout)
      // FORCE THE SERVER'S TIMEOUT
      command.setTimeout(serverTimeout, command.getTimeoutStrategy());

    // REQUEST CAN'T MODIFY THE RESULT, SO IT'S CACHEABLE
    command.setCacheableResult(true);

    // ASSIGNED THE PARSED FETCHPLAN
    final OCommandRequestText commandRequest = (OCommandRequestText) connection.getDatabase().command(command);
    listener.setFetchPlan(commandRequest.getFetchPlan());
    OCommandResponse response;
    if (asynch) {
      // In case of async it execute the request during the write of the response
      response = new OCommandResponse(null, listener, false, asynch, connection.getDatabase(), command, params);
    } else {
      // SYNCHRONOUS
      final Object result;
      if (params == null)
        result =commandRequest.execute();
      else
        result = commandRequest.execute(params);

      // FETCHPLAN HAS TO BE ASSIGNED AGAIN, because it can be changed by SQL statement
      listener.setFetchPlan(commandRequest.getFetchPlan());
      boolean isRecordResultSet = true;
      isRecordResultSet = command.isRecordResultSet();
      response = new OCommandResponse(result, listener, isRecordResultSet, asynch, connection.getDatabase(), command, params);
    }
    return response;
  }

  @Override
  public OBinaryResponse executeCommit(OCommitRequest request) {
    final OTransactionOptimisticProxy tx = new OTransactionOptimisticProxy(connection.getDatabase(), request.getTxId(),
        request.isUsingLong(), request.getOperations(), request.getIndexChanges(), connection.getData().protocolVersion,
        connection.getData().serializationImpl);

    try {
      try {
        connection.getDatabase().begin(tx);
      } catch (final ORecordNotFoundException e) {
        throw e.getCause() instanceof OOfflineClusterException ? (OOfflineClusterException) e.getCause() : e;
      }

      try {
        try {
          connection.getDatabase().commit();
        } catch (final ORecordNotFoundException e) {
          throw e.getCause() instanceof OOfflineClusterException ? (OOfflineClusterException) e.getCause() : e;
        }
        List<OCreatedRecordResponse> createdRecords = new ArrayList<>(tx.getCreatedRecords().size());
        for (Entry<ORecordId, ORecord> entry : tx.getCreatedRecords().entrySet()) {
          createdRecords.add(new OCreatedRecordResponse(entry.getKey(), (ORecordId) entry.getValue().getIdentity()));
          // IF THE NEW OBJECT HAS VERSION > 0 MEANS THAT HAS BEEN UPDATED IN THE SAME TX. THIS HAPPENS FOR GRAPHS
          if (entry.getValue().getVersion() > 0)
            tx.getUpdatedRecords().put((ORecordId) entry.getValue().getIdentity(), entry.getValue());
        }

        List<OUpdatedRecordResponse> updatedRecords = new ArrayList<>(tx.getUpdatedRecords().size());
        for (Entry<ORecordId, ORecord> entry : tx.getUpdatedRecords().entrySet()) {
          updatedRecords.add(new OUpdatedRecordResponse(entry.getKey(), entry.getValue().getVersion()));
        }
        OSBTreeCollectionManager collectionManager = connection.getDatabase().getSbTreeCollectionManager();
        Map<UUID, OBonsaiCollectionPointer> changedIds = null;
        if (collectionManager != null) {
          changedIds = collectionManager.changedIds();
        }
        return new OCommitResponse(createdRecords, updatedRecords, changedIds);
      } catch (RuntimeException e) {
        if (connection != null && connection.getDatabase() != null) {
          if (connection.getDatabase().getTransaction().isActive())
            connection.getDatabase().rollback(true);

          final OSBTreeCollectionManager collectionManager = connection.getDatabase().getSbTreeCollectionManager();
          if (collectionManager != null)
            collectionManager.clearChangedIds();
        }

        throw e;
      }
    } catch (RuntimeException e) {
      // Error during TX initialization, possibly index constraints violation.
      if (tx.isActive())
        tx.rollback(true, -1);
      throw e;
    }
  }

  @Override
  public OBinaryResponse executeGetGlobalConfiguration(OGetGlobalConfigurationRequest request) {
    final OGlobalConfiguration cfg = OGlobalConfiguration.findByKey(request.getKey());
    String cfgValue = cfg != null ? cfg.isHidden() ? "<hidden>" : cfg.getValueAsString() : "";
    return new OGetGlobalConfigurationResponse(cfgValue);
  }

  @Override
  public OBinaryResponse executeListGlobalConfigurations(OListGlobalConfigurationsRequest request) {
    Map<String, String> configs = new HashMap<>();
    for (OGlobalConfiguration cfg : OGlobalConfiguration.values()) {
      String key;
      try {
        key = cfg.getKey();
      } catch (Exception e) {
        key = "?";
      }

      String value;
      if (cfg.isHidden())
        value = "<hidden>";
      else
        try {
          value = cfg.getValueAsString() != null ? cfg.getValueAsString() : "";
        } catch (Exception e) {
          value = "";
        }
      configs.put(key, value);
    }
    return new OListGlobalConfigurationsResponse(configs);

  }

  @Override
  public OBinaryResponse executeFreezeDatabase(OFreezeDatabaseRequest request) {
    ODatabaseDocumentInternal database = server.openDatabase(request.getName(), connection.getServerUser().name, null,
        connection.getData(), true);
    connection.setDatabase(database);

    OLogManager.instance().info(this, "Freezing database '%s'", connection.getDatabase().getURL());

    connection.getDatabase().freeze(true);
    return new OFreezeDatabaseResponse();
  }

  @Override
  public OBinaryResponse executeReleaseDatabase(OReleaseDatabaseRequest request) {
    ODatabaseDocumentInternal database = server.openDatabase(request.getName(), connection.getServerUser().name, null,
        connection.getData(), true);

    connection.setDatabase(database);

    OLogManager.instance().info(this, "Realising database '%s'", connection.getDatabase().getURL());

    connection.getDatabase().release();
    return new OReleaseDatabaseResponse();

  }

  @Override
  public OBinaryResponse executeCleanOutRecord(OCleanOutRecordRequest request) {
    connection.getDatabase().cleanOutRecord(request.getRecordId(), request.getRecordVersion());

    if (request.getMode() < 2) {
      return new OCleanOutRecordResponse(true);
    }
    return null;
  }

  @Override
  public OBinaryResponse executeSBTreeCreate(OSBTCreateTreeRequest request) {
    OBonsaiCollectionPointer collectionPointer = connection.getDatabase().getSbTreeCollectionManager()
        .createSBTree(request.getClusterId(), null);

    return new OSBTCreateTreeResponse(collectionPointer);

  }

  @Override
  public OBinaryResponse executeSBTGet(OSBTGetRequest request) {
    final OSBTreeCollectionManager sbTreeCollectionManager = connection.getDatabase().getSbTreeCollectionManager();
    final OSBTreeBonsai<OIdentifiable, Integer> tree = sbTreeCollectionManager.loadSBTree(request.getCollectionPointer());
    try {
      final OIdentifiable key = tree.getKeySerializer().deserialize(request.getKeyStream(), 0);

      Integer result = tree.get(key);
      final OBinarySerializer<? super Integer> valueSerializer;
      if (result == null) {
        valueSerializer = ONullSerializer.INSTANCE;
      } else {
        valueSerializer = tree.getValueSerializer();
      }

      byte[] stream = new byte[OByteSerializer.BYTE_SIZE + valueSerializer.getObjectSize(result)];
      OByteSerializer.INSTANCE.serialize(valueSerializer.getId(), stream, 0);
      valueSerializer.serialize(result, stream, OByteSerializer.BYTE_SIZE);
      return new OSBTGetResponse(stream);
    } finally {
      sbTreeCollectionManager.releaseSBTree(request.getCollectionPointer());
    }
  }

  @Override
  public OBinaryResponse executeSBTFirstKey(OSBTFirstKeyRequest request) {

    final OSBTreeCollectionManager sbTreeCollectionManager = connection.getDatabase().getSbTreeCollectionManager();
    final OSBTreeBonsai<OIdentifiable, Integer> tree = sbTreeCollectionManager.loadSBTree(request.getCollectionPointer());
    byte[] stream;
    try {

      OIdentifiable result = tree.firstKey();
      final OBinarySerializer<? super OIdentifiable> keySerializer;
      if (result == null) {
        keySerializer = ONullSerializer.INSTANCE;
      } else {
        keySerializer = tree.getKeySerializer();
      }

      stream = new byte[OByteSerializer.BYTE_SIZE + keySerializer.getObjectSize(result)];
      OByteSerializer.INSTANCE.serialize(keySerializer.getId(), stream, 0);
      keySerializer.serialize(result, stream, OByteSerializer.BYTE_SIZE);
      return new OSBTFirstKeyResponse(stream);
    } finally {
      sbTreeCollectionManager.releaseSBTree(request.getCollectionPointer());
    }
  }

  @Override
  public OBinaryResponse executeSBTFetchEntriesMajor(@SuppressWarnings("rawtypes") OSBTFetchEntriesMajorRequest request) {

    final OSBTreeCollectionManager sbTreeCollectionManager = connection.getDatabase().getSbTreeCollectionManager();
    final OSBTreeBonsai<OIdentifiable, Integer> tree = sbTreeCollectionManager.loadSBTree(request.getPointer());
    try {
      final OBinarySerializer<OIdentifiable> keySerializer = tree.getKeySerializer();
      OIdentifiable key = keySerializer.deserialize(request.getKeyStream(), 0);

      final OBinarySerializer<Integer> valueSerializer = tree.getValueSerializer();

      OTreeInternal.AccumulativeListener<OIdentifiable, Integer> listener = new OTreeInternal.AccumulativeListener<OIdentifiable, Integer>(
          request.getPageSize());
      tree.loadEntriesMajor(key, request.isInclusive(), true, listener);
      List<Entry<OIdentifiable, Integer>> result = listener.getResult();
      return new OSBTFetchEntriesMajorResponse<>(keySerializer, valueSerializer, result);
    } finally {
      sbTreeCollectionManager.releaseSBTree(request.getPointer());
    }
  }

  @Override
  public OBinaryResponse executeSBTGetRealSize(OSBTGetRealBagSizeRequest request) {
    final OSBTreeCollectionManager sbTreeCollectionManager = connection.getDatabase().getSbTreeCollectionManager();
    final OSBTreeBonsai<OIdentifiable, Integer> tree = sbTreeCollectionManager.loadSBTree(request.getCollectionPointer());
    try {
      int realSize = tree.getRealBagSize(request.getChanges());
      return new OSBTGetRealBagSizeResponse(realSize);
    } finally {
      sbTreeCollectionManager.releaseSBTree(request.getCollectionPointer());
    }
  }

  @Override
  public OBinaryResponse executeIncrementalBackup(OIncrementalBackupRequest request) {
    String fileName = connection.getDatabase().incrementalBackup(request.getBackupDirectory());
    return new OIncrementalBackupResponse(fileName);
  }

  @Override
  public OBinaryResponse executeImport(OImportRequest request) {
    List<String> result = new ArrayList<>();
    OLogManager.instance().info(this, "Starting database import");
    ODatabaseImport imp;
    try {
      imp = new ODatabaseImport(connection.getDatabase(), request.getImporPath(), new OCommandOutputListener() {
        @Override
        public void onMessage(String iText) {
          OLogManager.instance().debug(OConnectionBinaryExecutor.this, iText);
          if (iText != null)
            result.add(iText);
        }
      });
      imp.setOptions(request.getImporPath());
      imp.importDatabase();
      imp.close();
      new File(request.getImporPath()).delete();

    } catch (IOException e) {
      throw OException.wrapException(new ODatabaseException("error on import"), e);
    }
    return new OImportResponse(result);

  }

  @Override
  public OBinaryResponse executeConnect(OConnectRequest request) {
    connection.getData().driverName = request.getDriverName();
    connection.getData().driverVersion = request.getDriverVersion();
    connection.getData().protocolVersion = request.getProtocolVersion();
    connection.getData().clientId = request.getClientId();
    connection.getData().serializationImpl = request.getRecordFormat();

    connection.setTokenBased(request.isTokenBased());
    connection.getData().supportsPushMessages = request.isSupportPush();
    connection.getData().collectStats = request.isCollectStats();

    connection.setServerUser(server.serverLogin(request.getUsername(), request.getPassword(), "server.connect"));

    if (connection.getServerUser() == null)
      throw new OSecurityAccessException("Wrong user/password to [connect] to the remote OrientDB Server instance");

    byte[] token = null;
    if (connection.getData().protocolVersion > OChannelBinaryProtocol.PROTOCOL_VERSION_26) {
      connection.getData().serverUsername = connection.getServerUser().name;
      connection.getData().serverUser = true;

      if (Boolean.TRUE.equals(connection.getTokenBased())) {
        token = server.getTokenHandler().getSignedBinaryToken(null, null, connection.getData());
      } else
        token = OCommonConst.EMPTY_BYTE_ARRAY;
    }

    return new OConnectResponse(connection.getId(), token);
  }

  @Override
  public OBinaryResponse executeDatabaseOpen(OOpenRequest request) {
    connection.getData().driverName = request.getDriverName();
    connection.getData().driverVersion = request.getDriverVersion();
    connection.getData().protocolVersion = request.getProtocolVersion();
    connection.getData().clientId = request.getClientId();
    connection.getData().serializationImpl = request.getRecordFormat();
    connection.setTokenBased(request.isUseToken());
    connection.getData().supportsPushMessages = request.isSupportsPush();
    connection.getData().collectStats = request.isCollectStats();

    try {
      connection.setDatabase(
          server.openDatabase(request.getDatabaseName(), request.getUserName(), request.getUserPassword(), connection.getData()));
    } catch (OException e) {
      server.getClientConnectionManager().disconnect(connection);
      throw e;
    }

    byte[] token = null;

    if (Boolean.TRUE.equals(connection.getTokenBased())) {
      token = server.getTokenHandler().getSignedBinaryToken(connection.getDatabase(), connection.getDatabase().getUser(),
          connection.getData());
      // TODO: do not use the parse split getSignedBinaryToken in two methods.
      server.getClientConnectionManager().connect(connection.getProtocol(), connection, token, server.getTokenHandler());
    }

    if (connection.getDatabase().getStorage() instanceof OStorageProxy) {
      connection.getDatabase().getMetadata().getSecurity().authenticate(request.getUserName(), request.getUserPassword());
    }

    final Collection<? extends OCluster> clusters = connection.getDatabase().getStorage().getClusterInstances();
    final byte[] tokenToSend;
    if (Boolean.TRUE.equals(connection.getTokenBased())) {
      tokenToSend = token;
    } else
      tokenToSend = OCommonConst.EMPTY_BYTE_ARRAY;

    final OServerPlugin plugin = server.getPlugin("cluster");
    byte[] distriConf = null;
    ODocument distributedCfg = null;
    if (plugin != null && plugin instanceof ODistributedServerManager) {
      distributedCfg = ((ODistributedServerManager) plugin).getClusterConfiguration();

      final ODistributedConfiguration dbCfg = ((ODistributedServerManager) plugin)
          .getDatabaseConfiguration(connection.getDatabase().getName());
      if (dbCfg != null) {
        // ENHANCE SERVER CFG WITH DATABASE CFG
        distributedCfg.field("database", dbCfg.getDocument(), OType.EMBEDDED);
      }
      distriConf = getRecordBytes(connection, distributedCfg);
    }

    return new OOpenResponse(connection.getId(), tokenToSend, clusters, distriConf, OConstants.getVersion());

  }

  @Override
  public OBinaryResponse executeShutdown(OShutdownRequest request) {

    OLogManager.instance().info(this, "Received shutdown command from the remote client ");

    final String user = request.getRootUser();
    final String passwd = request.getRootPassword();

    if (server.authenticate(user, passwd, "server.shutdown")) {
      OLogManager.instance().info(this, "Remote client authenticated. Starting shutdown of server...");

      runShutdownInNonDaemonThread();

      return new OShutdownResponse();
    }

    OLogManager.instance().error(this, "Authentication error of remote client: shutdown is aborted.");

    throw new OSecurityAccessException("Invalid user/password to shutdown the server");
  }

  private void runShutdownInNonDaemonThread() {
    Thread shutdownThread = new Thread("OrientDB server shutdown thread") {
      public void run() {
        server.shutdown();
        ShutdownHelper.shutdown(1);
      }
    };
    shutdownThread.setDaemon(false);
    shutdownThread.start();

  }

  @Override
  public OBinaryResponse executeReopen(OReopenRequest request) {
    return new OReopenResponse(connection.getId());
  }

  @Override
  public OBinaryResponse executeSetGlobalConfig(OSetGlobalConfigurationRequest request) {

    final OGlobalConfiguration cfg = OGlobalConfiguration.findByKey(request.getKey());

    if (cfg != null) {
      cfg.setValue(request.getValue());
      if (!cfg.isChangeableAtRuntime())
        throw new OConfigurationException(
            "Property '" + request.getKey() + "' cannot be changed at runtime. Change the setting at startup");
    } else
      throw new OConfigurationException("Property '" + request.getKey() + "' was not found in global configuration");

    return new OSetGlobalConfigurationResponse();
  }

  public static byte[] getRecordBytes(OClientConnection connection, final ORecord iRecord) {
    final byte[] stream;
    String dbSerializerName = null;
    if (ODatabaseRecordThreadLocal.INSTANCE.getIfDefined() != null)
      dbSerializerName = ((ODatabaseDocumentInternal) iRecord.getDatabase()).getSerializer().toString();
    String name = connection.getData().serializationImpl;
    if (ORecordInternal.getRecordType(iRecord) == ODocument.RECORD_TYPE
        && (dbSerializerName == null || !dbSerializerName.equals(name))) {
      ((ODocument) iRecord).deserializeFields();
      ORecordSerializer ser = ORecordSerializerFactory.instance().getFormat(name);
      stream = ser.toStream(iRecord, false);
    } else
      stream = iRecord.toStream();

    return stream;
  }

  public void fillRecord(OClientConnection connection, final ORecordId rid, final byte[] buffer, final int version,
      final ORecord record) {
    String dbSerializerName = "";
    if (connection.getDatabase() != null)
      dbSerializerName = connection.getDatabase().getSerializer().toString();

    String name = connection.getData().serializationImpl;
    if (ORecordInternal.getRecordType(record) == ODocument.RECORD_TYPE && !dbSerializerName.equals(name)) {
      ORecordInternal.fill(record, rid, version, null, true);
      ORecordSerializer ser = ORecordSerializerFactory.instance().getFormat(name);
      ser.fromStream(buffer, record, null);
      record.setDirty();
    } else
      ORecordInternal.fill(record, rid, version, buffer, true);
  }

}