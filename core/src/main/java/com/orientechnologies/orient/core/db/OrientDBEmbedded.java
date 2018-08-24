/*
 *
 *  *  Copyright 2010-2016 OrientDB LTD (http://orientdb.com)
 *  *
 *  *  Licensed under the Apache License, Version 2.0 (the "License");
 *  *  you may not use this file except in compliance with the License.
 *  *  You may obtain a copy of the License at
 *  *
 *  *       http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  *  Unless required by applicable law or agreed to in writing, software
 *  *  distributed under the License is distributed on an "AS IS" BASIS,
 *  *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *  See the License for the specific language governing permissions and
 *  *  limitations under the License.
 *  *
 *  * For more information: http://orientdb.com
 *
 */
package com.orientechnologies.orient.core.db;

import com.orientechnologies.common.exception.OException;
import com.orientechnologies.common.io.OIOUtils;
import com.orientechnologies.common.log.OLogManager;
import com.orientechnologies.orient.core.Orient;
import com.orientechnologies.orient.core.command.OCommandOutputListener;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentEmbedded;
import com.orientechnologies.orient.core.engine.OEngine;
import com.orientechnologies.orient.core.engine.OMemoryAndLocalPaginatedEnginesInitializer;
import com.orientechnologies.orient.core.exception.ODatabaseException;
import com.orientechnologies.orient.core.serialization.serializer.record.ORecordSerializer;
import com.orientechnologies.orient.core.serialization.serializer.record.ORecordSerializerFactory;
import com.orientechnologies.orient.core.sql.parser.OStatement;
import com.orientechnologies.orient.core.storage.OStorage;
import com.orientechnologies.orient.core.storage.impl.local.OAbstractPaginatedStorage;
import com.orientechnologies.orient.core.storage.impl.local.paginated.OLocalPaginatedStorage;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Created by tglman on 08/04/16.
 */
public class OrientDBEmbedded implements OrientDBInternal {
  protected final  Map<String, OAbstractPaginatedStorage> storages       = new HashMap<>();
  protected final  Map<String, OSharedContext>            sharedContexts = new HashMap<>();
  protected final  Set<ODatabasePoolInternal>             pools          = new HashSet<>();
  protected final  OrientDBConfig                         configurations;
  protected final  String                                 basePath;
  protected final  OEngine                                memory;
  protected final  OEngine                                disk;
  protected final  Orient                                 orient;
  private volatile boolean                                open           = true;
  private          ExecutorService                        executor;
  private          Timer                                  timer;

  public OrientDBEmbedded(String directoryPath, OrientDBConfig configurations, Orient orient) {
    super();
    this.orient = orient;
    orient.onEmbeddedFactoryInit(this);
    memory = orient.getEngine("memory");
    disk = orient.getEngine("plocal");
    directoryPath = directoryPath.trim();
    if (directoryPath.length() != 0)
      this.basePath = new java.io.File(directoryPath).getAbsolutePath();
    else
      this.basePath = null;
    this.configurations = configurations != null ? configurations : OrientDBConfig.defaultConfig();

    OMemoryAndLocalPaginatedEnginesInitializer.INSTANCE.initialize();

    orient.addOrientDB(this);

    executor = new ThreadPoolExecutor(1, Runtime.getRuntime().availableProcessors(), 30, TimeUnit.MINUTES,
        new LinkedBlockingQueue<>());
    timer = new Timer();

  }

  @Override
  public ODatabaseDocumentInternal open(String name, String user, String password) {
    return open(name, user, password, null);
  }

  public ODatabaseDocumentEmbedded openNoAuthenticate(String name, String user) {
    try {
      final ODatabaseDocumentEmbedded embedded;
      OrientDBConfig config = solveConfig(null);
      synchronized (this) {
        OAbstractPaginatedStorage storage = getOrInitStorage(name);
        // THIS OPEN THE STORAGE ONLY THE FIRST TIME
        storage.open(config.getConfigurations());
        embedded = newSessionInstance(storage);
        embedded.init(config, getOrCreateSharedContext(storage));
      }
      embedded.rebuildIndexes();
      embedded.internalOpen(user, "nopwd", false);
      embedded.callOnOpenListeners();
      return embedded;
    } catch (Exception e) {
      throw OException.wrapException(new ODatabaseException("Cannot open database '" + name + "'"), e);
    }
  }

  protected ODatabaseDocumentEmbedded newSessionInstance(OAbstractPaginatedStorage storage) {
    return new ODatabaseDocumentEmbedded(storage);
  }

  public ODatabaseDocumentEmbedded openNoAuthorization(String name) {
    try {
      final ODatabaseDocumentEmbedded embedded;
      OrientDBConfig config = solveConfig(null);
      synchronized (this) {
        OAbstractPaginatedStorage storage = getOrInitStorage(name);
        // THIS OPEN THE STORAGE ONLY THE FIRST TIME
        storage.open(config.getConfigurations());
        embedded = newSessionInstance(storage);
        embedded.init(config, getOrCreateSharedContext(storage));
      }
      embedded.rebuildIndexes();
      embedded.callOnOpenListeners();
      return embedded;
    } catch (Exception e) {
      throw OException.wrapException(new ODatabaseException("Cannot open database '" + name + "'"), e);
    }
  }

  @Override
  public ODatabaseDocumentInternal open(String name, String user, String password, OrientDBConfig config) {
    try {
      final ODatabaseDocumentEmbedded embedded;
      synchronized (this) {
        checkOpen();
        config = solveConfig(config);
        OAbstractPaginatedStorage storage = getOrInitStorage(name);
        // THIS OPEN THE STORAGE ONLY THE FIRST TIME
        storage.open(config.getConfigurations());
        embedded = newSessionInstance(storage);
        embedded.init(config, getOrCreateSharedContext(storage));
      }
      embedded.rebuildIndexes();
      embedded.internalOpen(user, password);
      embedded.callOnOpenListeners();
      return embedded;
    } catch (Exception e) {
      throw OException.wrapException(new ODatabaseException("Cannot open database '" + name + "'"), e);
    }
  }

  protected OrientDBConfig solveConfig(OrientDBConfig config) {
    if (config != null) {
      config.setParent(this.configurations);
      return config;
    } else {
      OrientDBConfig cfg = OrientDBConfig.defaultConfig();
      cfg.setParent(this.configurations);
      return cfg;
    }
  }

  public ODatabaseDocumentInternal poolOpen(String name, String user, String password, ODatabasePoolInternal pool) {
    final ODatabaseDocumentEmbedded embedded;
    synchronized (this) {
      checkOpen();
      OAbstractPaginatedStorage storage = getOrInitStorage(name);
      storage.open(pool.getConfig().getConfigurations());
      embedded = newPooledSessionInstance(pool, storage);
      embedded.init(pool.getConfig(), getOrCreateSharedContext(storage));
    }
    embedded.rebuildIndexes();
    embedded.internalOpen(user, password);
    embedded.callOnOpenListeners();
    return embedded;
  }

  protected ODatabaseDocumentEmbedded newPooledSessionInstance(ODatabasePoolInternal pool, OAbstractPaginatedStorage storage) {
    return new ODatabaseDocumentEmbeddedPooled(pool, storage);
  }

  protected OAbstractPaginatedStorage getOrInitStorage(String name) {
    OAbstractPaginatedStorage storage = storages.get(name);
    if (storage == null) {
      storage = (OAbstractPaginatedStorage) disk.createStorage(buildName(name), new HashMap<>());
      if (storage.exists())
        storages.put(name, storage);
    }
    return storage;
  }

  public synchronized OAbstractPaginatedStorage getStorage(String name) {
    return storages.get(name);
  }

  protected String buildName(String name) {
    if (basePath == null) {
      throw new ODatabaseException("OrientDB instanced created without physical path, only memory databases are allowed");
    }
    return basePath + "/" + name;
  }

  public void create(String name, String user, String password, ODatabaseType type) {
    create(name, user, password, type, null);
  }

  @Override
  public void create(String name, String user, String password, ODatabaseType type, OrientDBConfig config) {
    final ODatabaseDocumentEmbedded embedded;
    synchronized (this) {
      if (!exists(name, user, password)) {
        try {
          config = solveConfig(config);
          OAbstractPaginatedStorage storage;
          if (type == ODatabaseType.MEMORY) {
            storage = (OAbstractPaginatedStorage) memory.createStorage(name, new HashMap<>());
          } else {
            storage = (OAbstractPaginatedStorage) disk.createStorage(buildName(name), new HashMap<>());
          }
          storages.put(name, storage);
          embedded = internalCreate(config, storage);
        } catch (Exception e) {
          throw OException.wrapException(new ODatabaseException("Cannot create database '" + name + "'"), e);
        }
      } else
        throw new ODatabaseException("Cannot create new database '" + name + "' because it already exists");
    }
    embedded.callOnCreateListeners();
  }

  public void restore(String name, String user, String password, ODatabaseType type, String path, OrientDBConfig config) {
    final ODatabaseDocumentEmbedded embedded;
    synchronized (this) {
      if (!exists(name, null, null)) {
        try {
          OAbstractPaginatedStorage storage;
          storage = (OAbstractPaginatedStorage) disk.createStorage(buildName(name), new HashMap<>());
          embedded = internalCreate(config, storage);
          storage.restoreFromIncrementalBackup(path);
          storages.put(name, storage);
        } catch (Exception e) {
          throw OException.wrapException(new ODatabaseException("Cannot restore database '" + name + "'"), e);
        }
      } else
        throw new ODatabaseException("Cannot create new storage '" + name + "' because it already exists");
    }
    embedded.callOnCreateListeners();
  }

  public synchronized void restore(String name, InputStream in, Map<String, Object> options, Callable<Object> callable,
      OCommandOutputListener iListener) {
    try {
      OAbstractPaginatedStorage storage = getOrInitStorage(name);
      storage.restore(in, options, callable, iListener);
      storages.put(name, storage);
    } catch (Exception e) {
      throw OException.wrapException(new ODatabaseException("Cannot create database '" + name + "'"), e);
    }
  }

  protected ODatabaseDocumentEmbedded internalCreate(OrientDBConfig config, OAbstractPaginatedStorage storage) {
    try {
      storage.create(config.getConfigurations());
    } catch (IOException e) {
      throw OException.wrapException(new ODatabaseException("Error on database creation"), e);
    }

    ORecordSerializer serializer = ORecordSerializerFactory.instance().getDefaultRecordSerializer();
    if (serializer.toString().equals("ORecordDocument2csv"))
      throw new ODatabaseException("Impossible to create the database with ORecordDocument2csv serializer");
    storage.setRecordSerializer(serializer.toString(), serializer.getCurrentVersion());
    // since 2.1 newly created databases use strict SQL validation by default
    storage.setProperty(OStatement.CUSTOM_STRICT_SQL, "true");

    // No need to close
    final ODatabaseDocumentEmbedded embedded = newSessionInstance(storage);
    embedded.setSerializer(serializer);
    embedded.internalCreate(config, getOrCreateSharedContext(storage));
    return embedded;
  }

  protected synchronized OSharedContext getOrCreateSharedContext(OAbstractPaginatedStorage storage) {

    OSharedContext result = sharedContexts.get(storage.getName());
    if (result == null) {
      result = createSharedContext(storage);
      sharedContexts.put(storage.getName(), result);
    }
    return result;
  }

  protected OSharedContext createSharedContext(OAbstractPaginatedStorage storage) {
    return new OSharedContextEmbedded(storage, this);
  }

  @Override
  public synchronized boolean exists(String name, String user, String password) {
    checkOpen();
    OStorage storage = storages.get(name);
    if (storage == null) {
      if (basePath != null) {
        return OLocalPaginatedStorage.exists(Paths.get(buildName(name)));
      } else
        return false;
    }
    return storage.exists();
  }

  @Override
  public void drop(String name, String user, String password) {
    synchronized (this) {
      checkOpen();
    }
    ODatabaseDocumentInternal db = openNoAuthenticate(name, user);
    for (Iterator<ODatabaseLifecycleListener> it = orient.getDbLifecycleListeners(); it.hasNext(); ) {
      it.next().onDrop(db);
    }
    db.close();
    synchronized (this) {
      if (exists(name, user, password)) {
        OAbstractPaginatedStorage storage = getOrInitStorage(name);
        OSharedContext sharedContext = sharedContexts.get(name);
        if (sharedContext != null)
          sharedContext.close();
        storage.delete();
        storages.remove(name);
        sharedContexts.remove(name);
      }
    }
  }

  protected interface DatabaseFound {
    void found(String name);
  }

  @Override
  public synchronized Set<String> listDatabases(String user, String password) {
    checkOpen();
    // SEARCH IN CONFIGURED PATHS
    final Set<String> databases = new HashSet<>();
    // SEARCH IN DEFAULT DATABASE DIRECTORY
    if (basePath != null) {
      scanDatabaseDirectory(new File(basePath), (name) -> databases.add(name));
    }
    databases.addAll(this.storages.keySet());
    // TODO remove OSystemDatabase.SYSTEM_DB_NAME from the list
    return databases;
  }

  public synchronized void loadAllDatabases() {
    if (basePath != null) {
      scanDatabaseDirectory(new File(basePath), (name) -> {
        if (!storages.containsKey(name)) {
          OAbstractPaginatedStorage storage = getOrInitStorage(name);
          // THIS OPEN THE STORAGE ONLY THE FIRST TIME
          storage.open(getConfigurations().getConfigurations());
        }
      });
    }
  }

  public ODatabasePoolInternal openPool(String name, String user, String password) {
    return openPool(name, user, password, null);
  }

  @Override
  public ODatabasePoolInternal openPool(String name, String user, String password, OrientDBConfig config) {
    checkOpen();
    ODatabasePoolImpl pool = new ODatabasePoolImpl(this, name, user, password, solveConfig(config));
    pools.add(pool);
    return pool;
  }

  @Override
  public void close() {
    if (!open)
      return;
    timer.cancel();
    executor.shutdown();
    try {
      if (!executor.awaitTermination(1, TimeUnit.MINUTES)) {
        OLogManager.instance().warn(this, "Failed waiting background operations termination");
        executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    synchronized (this) {
      removeShutdownHook();
      internalClose();
    }
  }

  public synchronized void internalClose() {
    if (!open)
      return;
    open = false;
    this.sharedContexts.values().forEach(x -> x.close());
    final List<OAbstractPaginatedStorage> storagesCopy = new ArrayList<>(storages.values());
    for (OAbstractPaginatedStorage stg : storagesCopy) {
      try {
        OLogManager.instance().info(this, "- shutdown storage: " + stg.getName() + "...");
        stg.shutdown();
      } catch (Exception e) {
        OLogManager.instance().warn(this, "-- error on shutdown storage", e);
      } catch (Error e) {
        OLogManager.instance().warn(this, "-- error on shutdown storage", e);
        throw e;
      }
    }
    this.sharedContexts.clear();
    storages.clear();
    orient.onEmbeddedFactoryClose(this);
  }

  public OrientDBConfig getConfigurations() {
    return configurations;
  }

  public void removePool(ODatabasePoolInternal pool) {
    pools.remove(pool);
  }

  public interface InstanceFactory<T> {
    T create(OAbstractPaginatedStorage storage);
  }

  protected void scanDatabaseDirectory(final File directory, DatabaseFound found) {
    if (directory.exists() && directory.isDirectory()) {
      final File[] files = directory.listFiles();
      if (files != null)
        for (File db : files) {
          if (db.isDirectory()) {
            final File plocalFile = new File(db.getAbsolutePath() + "/database.ocf");
            final String dbPath = db.getPath().replace('\\', '/');
            final int lastBS = dbPath.lastIndexOf('/', dbPath.length() - 1) + 1;// -1 of dbPath may be ended with slash
            if (plocalFile.exists()) {
              found.found(OIOUtils.getDatabaseNameFromPath(dbPath.substring(lastBS)));
            }
          }
        }
    }
  }

  public synchronized void initCustomStorage(String name, String path, String userName, String userPassword) {
    ODatabaseDocumentEmbedded embedded = null;
    synchronized (this) {
      boolean exists = OLocalPaginatedStorage.exists(Paths.get(path));
      OAbstractPaginatedStorage storage = (OAbstractPaginatedStorage) disk.createStorage(path, new HashMap<>());
      // TODO: Add Creation settings and parameters
      if (!exists) {
        embedded = internalCreate(getConfigurations(), storage);
      }
      storages.put(name, storage);
    }
    if (embedded != null)
      embedded.callOnCreateListeners();
  }

  public synchronized void removeShutdownHook() {
    orient.removeOrientDB(this);
  }

  public synchronized Collection<OStorage> getStorages() {
    return storages.values().stream().map((x) -> (OStorage) x).collect(Collectors.toSet());
  }

  public synchronized void forceDatabaseClose(String iDatabaseName) {
    OAbstractPaginatedStorage storage = storages.remove(iDatabaseName);
    if (storage != null) {
      OSharedContext ctx = sharedContexts.get(iDatabaseName);
      if (ctx != null) {
        ctx.close();
        sharedContexts.remove(iDatabaseName);
      }
      storage.shutdown();
    }
  }

  public String getDatabasePath(String iDatabaseName) {
    OAbstractPaginatedStorage storage = storages.get(iDatabaseName);
    if (storage != null && storage instanceof OLocalPaginatedStorage)
      return ((OLocalPaginatedStorage) storage).getStoragePath().toString();
    return null;
  }

  private void checkOpen() {
    if (!open)
      throw new ODatabaseException("OrientDB Instance is closed");
  }

  public boolean isOpen() {
    return open;
  }

  @Override
  public boolean isEmbedded() {
    return true;
  }

  public void schedule(TimerTask task, long delay, long period) {
    timer.schedule(task, delay, period);
  }

  public void scheduleOnce(TimerTask task, long delay) {
    timer.schedule(task, delay);
  }

  @Override
  public <X> Future<X> execute(String database, String user, ODatabaseTask<X> task) {
    return executor.submit(() -> {
      try (ODatabaseSession session = openNoAuthenticate(database, user)) {
        return task.call(session);
      }
    });
  }

  @Override
  public <X> Future<X> executeNoAuthorization(String database, ODatabaseTask<X> task) {
    return executor.submit(() -> {
      try (ODatabaseSession session = openNoAuthorization(database)) {
        return task.call(session);
      }
    });
  }

}
