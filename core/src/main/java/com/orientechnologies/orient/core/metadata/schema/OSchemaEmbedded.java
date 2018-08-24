package com.orientechnologies.orient.core.metadata.schema;

import com.orientechnologies.common.exception.OException;
import com.orientechnologies.orient.core.Orient;
import com.orientechnologies.orient.core.db.*;
import com.orientechnologies.orient.core.db.viewmanager.ViewCreationListener;
import com.orientechnologies.orient.core.exception.OSchemaException;
import com.orientechnologies.orient.core.index.OIndex;
import com.orientechnologies.orient.core.index.OIndexManager;
import com.orientechnologies.orient.core.metadata.security.ORole;
import com.orientechnologies.orient.core.metadata.security.ORule;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.OCommandSQL;
import com.orientechnologies.orient.core.storage.OAutoshardedStorage;
import com.orientechnologies.orient.core.storage.OStorage;

import java.util.*;

/**
 * Created by tglman on 13/06/17.
 */
public class OSchemaEmbedded extends OSchemaShared {

  public OSchemaEmbedded(OSharedContext sharedContext) {
    super();
    sharedContext.registerListener(new SchemaSnapshotOnIndexesUpdateListener(this));
  }

  public OClass createClass(ODatabaseDocumentInternal database, final String className, int[] clusterIds, OClass... superClasses) {
    final Character wrongCharacter = OSchemaShared.checkClassNameIfValid(className);
    if (wrongCharacter != null)
      throw new OSchemaException(
          "Invalid class name found. Character '" + wrongCharacter + "' cannot be used in class name '" + className + "'");

    OClass result;
    int retry = 0;

    while (true)

      try {
        result = doCreateClass(database, className, clusterIds, retry, superClasses);
        break;
      } catch (ClusterIdsAreEmptyException ignore) {
        classes.remove(className.toLowerCase(Locale.ENGLISH));
        clusterIds = createClusters(database, className);
        retry++;
      }
    return result;
  }

  public OClass createClass(ODatabaseDocumentInternal database, final String className, int clusters, OClass... superClasses) {
    final Character wrongCharacter = OSchemaShared.checkClassNameIfValid(className);
    if (wrongCharacter != null)
      throw new OSchemaException(
          "Invalid class name found. Character '" + wrongCharacter + "' cannot be used in class name '" + className + "'");

    return doCreateClass(database, className, clusters, superClasses);
  }

  private OClass doCreateClass(ODatabaseDocumentInternal database, final String className, final int clusters,
      OClass... superClasses) {
    OClass result;

    database.checkSecurity(ORule.ResourceGeneric.SCHEMA, ORole.PERMISSION_CREATE);
    if (superClasses != null)
      OClassImpl.checkParametersConflict(Arrays.asList(superClasses));
    acquireSchemaWriteLock(database);
    try {

      final String key = className.toLowerCase(Locale.ENGLISH);
      if (classes.containsKey(key))
        throw new OSchemaException("Class '" + className + "' already exists in current database");

      if (executeThroughDistributedStorage(database)) {
        final OStorage storage = database.getStorage();
        StringBuilder cmd = new StringBuilder("create class ");
        cmd.append('`');
        cmd.append(className);
        cmd.append('`');

        List<OClass> superClassesList = new ArrayList<OClass>();
        if (superClasses != null && superClasses.length > 0) {
          boolean first = true;
          for (OClass superClass : superClasses) {
            // Filtering for null
            if (superClass != null) {
              if (first)
                cmd.append(" extends ");
              else
                cmd.append(", ");
              cmd.append(superClass.getName());
              first = false;
              superClassesList.add(superClass);
            }
          }
        }

        if (clusters == 0)
          cmd.append(" abstract");
        final int[] clusterIds = createClusters(database, className, clusters);
        createClassInternal(database, className, clusterIds, superClassesList);

        if (clusters > 0) {
          cmd.append(" cluster ");
          for (int i = 0; i < clusterIds.length; ++i) {
            if (i > 0)
              cmd.append(',');
            else
              cmd.append(' ');
            cmd.append(clusterIds[i]);
          }
        }

        final OAutoshardedStorage autoshardedStorage = (OAutoshardedStorage) storage;
        OCommandSQL commandSQL = new OCommandSQL(cmd.toString());
        commandSQL.addExcludedNode(autoshardedStorage.getNodeId());

        final Object res = database.command(commandSQL).execute();

      } else {

        List<OClass> superClassesList = new ArrayList<OClass>();
        if (superClasses != null && superClasses.length > 0) {
          for (OClass superClass : superClasses) {
            // Filtering for null
            if (superClass != null) {
              superClassesList.add(superClass);
            }
          }
        }
        final int[] clusterIds = createClusters(database, className, clusters);
        createClassInternal(database, className, clusterIds, superClassesList);
      }

      result = classes.get(className.toLowerCase(Locale.ENGLISH));

      // WAKE UP DB LIFECYCLE LISTENER
      for (Iterator<ODatabaseLifecycleListener> it = Orient.instance().getDbLifecycleListeners(); it.hasNext(); )
        it.next().onCreateClass(database, result);

      for (Iterator<ODatabaseListener> it = database.getListeners().iterator(); it.hasNext(); )
        it.next().onCreateClass(database, result);

    } catch (ClusterIdsAreEmptyException e) {
      throw OException.wrapException(new OSchemaException("Cannot create class '" + className + "'"), e);
    } finally {
      releaseSchemaWriteLock(database);
    }

    return result;
  }

  private OClass createClassInternal(ODatabaseDocumentInternal database, final String className, final int[] clusterIdsToAdd,
      final List<OClass> superClasses) throws ClusterIdsAreEmptyException {
    acquireSchemaWriteLock(database);
    try {
      if (className == null || className.length() == 0)
        throw new OSchemaException("Found class name null or empty");

      checkEmbedded();

      checkClustersAreAbsent(clusterIdsToAdd);

      final int[] clusterIds;
      if (clusterIdsToAdd == null || clusterIdsToAdd.length == 0) {
        throw new ClusterIdsAreEmptyException();

      } else
        clusterIds = clusterIdsToAdd;

      database.checkSecurity(ORule.ResourceGeneric.SCHEMA, ORole.PERMISSION_CREATE);

      final String key = className.toLowerCase(Locale.ENGLISH);

      if (classes.containsKey(key))
        throw new OSchemaException("Class '" + className + "' already exists in current database");

      OClassImpl cls = createClassInstance(className, clusterIds);

      classes.put(key, cls);

      if (superClasses != null && superClasses.size() > 0) {
        cls.setSuperClassesInternal(superClasses);
        for (OClass superClass : superClasses) {
          // UPDATE INDEXES
          final int[] clustersToIndex = superClass.getPolymorphicClusterIds();
          final String[] clusterNames = new String[clustersToIndex.length];
          for (int i = 0; i < clustersToIndex.length; i++)
            clusterNames[i] = database.getClusterNameById(clustersToIndex[i]);

          for (OIndex<?> index : superClass.getIndexes())
            for (String clusterName : clusterNames)
              if (clusterName != null)
                database.getMetadata().getIndexManager().addClusterToIndex(clusterName, index.getName());
        }
      }

      addClusterClassMap(cls);

      return cls;
    } finally {
      releaseSchemaWriteLock(database);
    }
  }

  @Override
  public OView createView(ODatabaseDocumentInternal database, String viewName, String statement, Map<String, Object> metadata) {
    OViewConfig cfg = new OViewConfig(viewName, statement);
    if (metadata != null) {
      cfg.setUpdatable(Boolean.TRUE.equals(metadata.get("updatable")));

      Object updateInterval = metadata.get("updateIntervalSeconds");
      if (updateInterval instanceof Integer) {
        cfg.setUpdateIntervalSeconds((Integer) updateInterval);
      }

      Object updateStrategy = metadata.get("updateStrategy");
      if (updateStrategy instanceof String) {
        cfg.setUpdateStrategy((String) updateStrategy);
      }

      Object watchClasses = metadata.get("watchClasses");
      if (watchClasses instanceof List) {
        cfg.setWatchClasses((List) watchClasses);
      }

      Object nodes = metadata.get("nodes");
      if (nodes instanceof List) {
        cfg.setNodes((List) nodes);
      }

      Object originRidField = metadata.get("originRidField");
      if (originRidField instanceof String) {
        cfg.setOriginRidField((String) originRidField);
      }

//      result.setProperty("indexes", indexes);
    }
    return createView(database, cfg);
  }

  @Override
  public OView createView(ODatabaseDocumentInternal database, OViewConfig cfg) {
    return createView(database, cfg, null);
  }

  public OView createView(ODatabaseDocumentInternal database, OViewConfig cfg, ViewCreationListener listener) {
    final Character wrongCharacter = OSchemaShared.checkClassNameIfValid(cfg.getName());
    if (wrongCharacter != null)
      throw new OSchemaException(
          "Invalid class name found. Character '" + wrongCharacter + "' cannot be used in view name '" + cfg.getName() + "'");

    return doCreateView(database, cfg, listener);
  }

  private OView doCreateView(ODatabaseDocumentInternal database, final OViewConfig config, ViewCreationListener listener) {
    OView result;

    database.checkSecurity(ORule.ResourceGeneric.SCHEMA, ORole.PERMISSION_CREATE);
    acquireSchemaWriteLock(database);
    try {

      final String key = config.getName().toLowerCase(Locale.ENGLISH);
      if (classes.containsKey(key) || views.containsKey(key))
        throw new OSchemaException("View (or class) '" + config.getName() + "' already exists in current database");

      if (executeThroughDistributedStorage(database)) {
        final OStorage storage = database.getStorage();
        StringBuilder cmd = new StringBuilder("create view ");
        cmd.append('`');
        cmd.append(config.getName());
        cmd.append('`');

        final int[] clusterIds = createClusters(database, config.getName(), 1);
        createViewInternal(database, config, clusterIds);

        cmd.append(" cluster ");
        for (int i = 0; i < clusterIds.length; ++i) {
          if (i > 0)
            cmd.append(',');
          else
            cmd.append(' ');
          cmd.append(clusterIds[i]);
        }

        final OAutoshardedStorage autoshardedStorage = (OAutoshardedStorage) storage;
        OCommandSQL commandSQL = new OCommandSQL(cmd.toString());
        commandSQL.addExcludedNode(autoshardedStorage.getNodeId());

        final Object res = database.command(commandSQL).execute();

      } else {

        final int[] clusterIds = createClusters(database, config.getName(), 1);
        createViewInternal(database, config, clusterIds);
      }

      result = views.get(config.getName().toLowerCase(Locale.ENGLISH));

      // WAKE UP DB LIFECYCLE LISTENER
      for (Iterator<ODatabaseLifecycleListener> it = Orient.instance().getDbLifecycleListeners(); it.hasNext(); )
        it.next().onCreateView(database, result);

      for (Iterator<ODatabaseListener> it = database.getListeners().iterator(); it.hasNext(); )
        it.next().onCreateView(database, result);

      ((OSharedContextEmbedded) database.getSharedContext()).getViewManager().updateViewAsync(result.getName(), listener);

    } catch (ClusterIdsAreEmptyException e) {
      throw OException.wrapException(new OSchemaException("Cannot create view '" + config.getName() + "'"), e);
    } finally {
      releaseSchemaWriteLock(database);
    }

    return result;
  }

  private OClass createViewInternal(ODatabaseDocumentInternal database, final OViewConfig cfg, final int[] clusterIdsToAdd)
      throws ClusterIdsAreEmptyException {
    acquireSchemaWriteLock(database);
    try {
      if (cfg.getName() == null || cfg.getName().length() == 0)
        throw new OSchemaException("Found view name null or empty");

      checkEmbedded();

      checkClustersAreAbsent(clusterIdsToAdd);

      final int[] clusterIds;
      if (clusterIdsToAdd == null || clusterIdsToAdd.length == 0) {
        throw new ClusterIdsAreEmptyException();

      } else
        clusterIds = clusterIdsToAdd;

      database.checkSecurity(ORule.ResourceGeneric.SCHEMA, ORole.PERMISSION_CREATE);

      final String key = cfg.getName().toLowerCase(Locale.ENGLISH);

      if (views.containsKey(key))
        throw new OSchemaException("View '" + cfg.getName() + "' already exists in current database");

      //TODO updatable and the
      OViewImpl cls = createViewInstance(cfg, clusterIds);

      views.put(key, cls);

      addClusterViewMap(cls);

      return cls;
    } finally {
      releaseSchemaWriteLock(database);
    }
  }

  protected OClassImpl createClassInstance(String className, int[] clusterIds) {
    return new OClassEmbedded(this, className, clusterIds);
  }

  protected OViewImpl createViewInstance(OViewConfig cfg, int[] clusterIds) {
    if (cfg.getQuery() == null) {
      throw new IllegalArgumentException("Invalid view configuration: no query defined");
    }
    return new OViewEmbedded(this, cfg.getName(), cfg, clusterIds);
  }

  public OClass getOrCreateClass(ODatabaseDocumentInternal database, final String iClassName, final OClass... superClasses) {
    if (iClassName == null)
      return null;

    acquireSchemaReadLock();
    try {
      OClass cls = classes.get(iClassName.toLowerCase(Locale.ENGLISH));
      if (cls != null)
        return cls;
    } finally {
      releaseSchemaReadLock();
    }

    OClass cls;

    int[] clusterIds = null;
    int retry = 0;

    while (true)
      try {
        acquireSchemaWriteLock(database);
        try {
          cls = classes.get(iClassName.toLowerCase(Locale.ENGLISH));
          if (cls != null)
            return cls;

          cls = doCreateClass(database, iClassName, clusterIds, retry, superClasses);
          addClusterClassMap(cls);
        } finally {
          releaseSchemaWriteLock(database);
        }
        break;
      } catch (ClusterIdsAreEmptyException ignore) {
        clusterIds = createClusters(database, iClassName);
        retry++;
      }

    return cls;
  }

  private OClass doCreateClass(ODatabaseDocumentInternal database, final String className, int[] clusterIds, int retry,
      OClass... superClasses) throws ClusterIdsAreEmptyException {
    OClass result;
    database.checkSecurity(ORule.ResourceGeneric.SCHEMA, ORole.PERMISSION_CREATE);
    if (superClasses != null)
      OClassImpl.checkParametersConflict(Arrays.asList(superClasses));

    acquireSchemaWriteLock(database);
    try {

      final String key = className.toLowerCase(Locale.ENGLISH);
      if (classes.containsKey(key) && retry == 0)
        throw new OSchemaException("Class '" + className + "' already exists in current database");

      if (!executeThroughDistributedStorage(database))
        checkClustersAreAbsent(clusterIds);

      if (clusterIds == null || clusterIds.length == 0) {
        clusterIds = createClusters(database, className, database.getStorage().getConfiguration().getMinimumClusters());
      }

      if (executeThroughDistributedStorage(database)) {
        final OStorage storage = database.getStorage();
        StringBuilder cmd = new StringBuilder("create class ");
        cmd.append('`');
        cmd.append(className);
        cmd.append('`');

        List<OClass> superClassesList = new ArrayList<OClass>();
        if (superClasses != null && superClasses.length > 0) {
          boolean first = true;
          for (OClass superClass : superClasses) {
            // Filtering for null
            if (superClass != null) {
              if (first)
                cmd.append(" extends ");
              else
                cmd.append(", ");
              cmd.append('`').append(superClass.getName()).append('`');
              first = false;
              superClassesList.add(superClass);
            }
          }
        }

        if (clusterIds != null) {
          if (clusterIds.length == 1 && clusterIds[0] == -1)
            cmd.append(" abstract");
          else {
            cmd.append(" cluster ");
            for (int i = 0; i < clusterIds.length; ++i) {
              if (i > 0)
                cmd.append(',');
              else
                cmd.append(' ');

              cmd.append(clusterIds[i]);
            }
          }
        }

        createClassInternal(database, className, clusterIds, superClassesList);

        final OAutoshardedStorage autoshardedStorage = (OAutoshardedStorage) storage;
        OCommandSQL commandSQL = new OCommandSQL(cmd.toString());
        commandSQL.addExcludedNode(autoshardedStorage.getNodeId());

        final Object res = database.command(commandSQL).execute();
      } else {
        List<OClass> superClassesList = new ArrayList<OClass>();
        if (superClasses != null && superClasses.length > 0) {
          for (OClass superClass : superClasses) {
            if (superClass != null) {
              superClassesList.add(superClass);
            }
          }
        }
        createClassInternal(database, className, clusterIds, superClassesList);
      }

      result = classes.get(className.toLowerCase(Locale.ENGLISH));

      // WAKE UP DB LIFECYCLE LISTENER
      for (Iterator<ODatabaseLifecycleListener> it = Orient.instance().getDbLifecycleListeners(); it.hasNext(); )
        it.next().onCreateClass(database, result);

      for (Iterator<ODatabaseListener> it = database.getListeners().iterator(); it.hasNext(); )
        it.next().onCreateClass(database, result);

    } finally {
      releaseSchemaWriteLock(database);
    }

    return result;
  }

  private int[] createClusters(ODatabaseDocumentInternal database, final String iClassName) {
    return createClusters(database, iClassName, database.getStorage().getConfiguration().getMinimumClusters());
  }

  private int[] createClusters(ODatabaseDocumentInternal database, String className, int minimumClusters) {
    className = className.toLowerCase(Locale.ENGLISH);

    int[] clusterIds;

    if (internalClasses.contains(className.toLowerCase(Locale.ENGLISH))) {
      // INTERNAL CLASS, SET TO 1
      minimumClusters = 1;
    }

    clusterIds = new int[minimumClusters];
    clusterIds[0] = database.getClusterIdByName(className);
    if (clusterIds[0] > -1) {
      // CHECK THE CLUSTER HAS NOT BEEN ALREADY ASSIGNED
      final OClass cls = clustersToClasses.get(clusterIds[0]);
      if (cls != null)
        clusterIds[0] = database.addCluster(getNextAvailableClusterName(database, className));
    } else
      // JUST KEEP THE CLASS NAME. THIS IS FOR LEGACY REASONS
      clusterIds[0] = database.addCluster(className);

    for (int i = 1; i < minimumClusters; ++i)
      clusterIds[i] = database.addCluster(getNextAvailableClusterName(database, className));

    return clusterIds;
  }

  private String getNextAvailableClusterName(ODatabaseDocumentInternal database, final String className) {
    for (int i = 1; ; ++i) {
      final String clusterName = className + "_" + i;
      if (database.getClusterIdByName(clusterName) < 0)
        // FREE NAME
        return clusterName;
    }
  }

  private void checkClustersAreAbsent(final int[] iClusterIds) {
    if (iClusterIds == null)
      return;

    for (int clusterId : iClusterIds) {
      if (clusterId < 0)
        continue;

      if (clustersToClasses.containsKey(clusterId))
        throw new OSchemaException(
            "Cluster with id " + clusterId + " already belongs to class " + clustersToClasses.get(clusterId));
    }
  }

  public void dropClass(ODatabaseDocumentInternal database, final String className) {
    final OStorage storage = database.getStorage();
    final StringBuilder cmd;

    acquireSchemaWriteLock(database);
    try {
      if (database.getTransaction().isActive())
        throw new IllegalStateException("Cannot drop a class inside a transaction");

      if (className == null)
        throw new IllegalArgumentException("Class name is null");

      database.checkSecurity(ORule.ResourceGeneric.SCHEMA, ORole.PERMISSION_DELETE);

      final String key = className.toLowerCase(Locale.ENGLISH);

      OClass cls = classes.get(key);

      if (cls == null)
        throw new OSchemaException("Class '" + className + "' was not found in current database");

      if (!cls.getSubclasses().isEmpty())
        throw new OSchemaException("Class '" + className + "' cannot be dropped because it has sub classes " + cls.getSubclasses()
            + ". Remove the dependencies before trying to drop it again");

      if (executeThroughDistributedStorage(database)) {
        cmd = new StringBuilder("drop class ");
        cmd.append(className);
        cmd.append(" unsafe");

        final OAutoshardedStorage autoshardedStorage = (OAutoshardedStorage) storage;
        OCommandSQL commandSQL = new OCommandSQL(cmd.toString());
        commandSQL.addExcludedNode(autoshardedStorage.getNodeId());
        database.command(commandSQL).execute();

        dropClassInternal(database, className);
      } else
        dropClassInternal(database, className);

      // FREE THE RECORD CACHE
      database.getLocalCache().freeCluster(cls.getDefaultClusterId());

    } finally {
      releaseSchemaWriteLock(database);
    }
  }

  private void dropClassInternal(ODatabaseDocumentInternal database, final String className) {
    acquireSchemaWriteLock(database);
    try {
      if (database.getTransaction().isActive())
        throw new IllegalStateException("Cannot drop a class inside a transaction");

      if (className == null)
        throw new IllegalArgumentException("Class name is null");

      database.checkSecurity(ORule.ResourceGeneric.SCHEMA, ORole.PERMISSION_DELETE);

      final String key = className.toLowerCase(Locale.ENGLISH);

      final OClass cls = classes.get(key);
      if (cls == null)
        throw new OSchemaException("Class '" + className + "' was not found in current database");

      if (!cls.getSubclasses().isEmpty())
        throw new OSchemaException("Class '" + className + "' cannot be dropped because it has sub classes " + cls.getSubclasses()
            + ". Remove the dependencies before trying to drop it again");

      checkEmbedded();

      for (OClass superClass : cls.getSuperClasses()) {
        // REMOVE DEPENDENCY FROM SUPERCLASS
        ((OClassImpl) superClass).removeBaseClassInternal(cls);
      }
      for (int id : cls.getClusterIds()) {
        if (id != -1)
          deleteCluster(database, id);
      }

      dropClassIndexes(database, cls);

      classes.remove(key);

      if (cls.getShortName() != null)
        // REMOVE THE ALIAS TOO
        classes.remove(cls.getShortName().toLowerCase(Locale.ENGLISH));

      removeClusterClassMap(cls);

      // WAKE UP DB LIFECYCLE LISTENER
      for (Iterator<ODatabaseLifecycleListener> it = Orient.instance().getDbLifecycleListeners(); it.hasNext(); )
        it.next().onDropClass(database, cls);

      for (Iterator<ODatabaseListener> it = database.getListeners().iterator(); it.hasNext(); )
        it.next().onDropClass(database, cls);
    } finally {
      releaseSchemaWriteLock(database);
    }
  }

  public void dropView(ODatabaseDocumentInternal database, final String name) {
    final OStorage storage = database.getStorage();
    final StringBuilder cmd;

    acquireSchemaWriteLock(database);
    try {
      if (database.getTransaction().isActive())
        throw new IllegalStateException("Cannot drop a class inside a transaction");

      if (name == null)
        throw new IllegalArgumentException("Class name is null");

      database.checkSecurity(ORule.ResourceGeneric.SCHEMA, ORole.PERMISSION_DELETE);

      final String key = name.toLowerCase(Locale.ENGLISH);

      OView cls = views.get(key);

      if (cls == null)
        throw new OSchemaException("View '" + name + "' was not found in current database");

      if (!cls.getSubclasses().isEmpty())
        throw new OSchemaException("View '" + name + "' cannot be dropped because it has sub classes " + cls.getSubclasses()
            + ". Remove the dependencies before trying to drop it again");

      if (executeThroughDistributedStorage(database)) {
        cmd = new StringBuilder("drop view ");
        cmd.append(name);
        cmd.append(" unsafe");

        final OAutoshardedStorage autoshardedStorage = (OAutoshardedStorage) storage;
        OCommandSQL commandSQL = new OCommandSQL(cmd.toString());
        commandSQL.addExcludedNode(autoshardedStorage.getNodeId());
        database.command(commandSQL).execute();
      }

      dropViewInternal(database, name);

      // FREE THE RECORD CACHE
      database.getLocalCache().freeCluster(cls.getDefaultClusterId());

    } finally {
      releaseSchemaWriteLock(database);
    }
  }

  private void dropViewInternal(ODatabaseDocumentInternal database, final String view) {
    acquireSchemaWriteLock(database);
    try {
      if (database.getTransaction().isActive())
        throw new IllegalStateException("Cannot drop a class inside a transaction");

      if (view == null)
        throw new IllegalArgumentException("Class name is null");

      database.checkSecurity(ORule.ResourceGeneric.SCHEMA, ORole.PERMISSION_DELETE);

      final String key = view.toLowerCase(Locale.ENGLISH);

      final OView cls = views.get(key);
      if (cls == null)
        throw new OSchemaException("View '" + view + "' was not found in current database");

      if (!cls.getSubclasses().isEmpty())
        throw new OSchemaException("View '" + view + "' cannot be dropped because it has sub classes " + cls.getSubclasses()
            + ". Remove the dependencies before trying to drop it again");

      checkEmbedded();

      for (int id : cls.getClusterIds()) {
        if (id != -1)
          deleteCluster(database, id);
      }

      dropClassIndexes(database, cls);

      views.remove(key);

      if (cls.getShortName() != null)
        // REMOVE THE ALIAS TOO
        views.remove(cls.getShortName().toLowerCase(Locale.ENGLISH));

      removeClusterViewMap(cls);

      // WAKE UP DB LIFECYCLE LISTENER
      for (Iterator<ODatabaseLifecycleListener> it = Orient.instance().getDbLifecycleListeners(); it.hasNext(); )
        it.next().onDropView(database, cls);

      for (Iterator<ODatabaseListener> it = database.getListeners().iterator(); it.hasNext(); )
        it.next().onDropView(database, cls);
    } finally {
      releaseSchemaWriteLock(database);
    }
  }

  protected OClassImpl createClassInstance(ODocument c) {
    return new OClassEmbedded(this, c, (String) c.field("name"));
  }

  protected OViewImpl createViewInstance(ODocument c) {
    return new OViewEmbedded(this, c, (String) c.field("name"));
  }

  private void dropClassIndexes(ODatabaseDocumentInternal database, final OClass cls) {
    final OIndexManager indexManager = database.getMetadata().getIndexManager();

    for (final OIndex<?> index : indexManager.getClassIndexes(cls.getName()))
      indexManager.dropIndex(index.getName());
  }

  private void deleteCluster(final ODatabaseDocumentInternal db, final int clusterId) {
    db.getStorage().dropCluster(clusterId, false);
    db.getLocalCache().freeCluster(clusterId);
  }

  private void removeClusterClassMap(final OClass cls) {
    for (int clusterId : cls.getClusterIds()) {
      if (clusterId < 0)
        continue;

      clustersToClasses.remove(clusterId);
    }
  }

  private void removeClusterViewMap(final OView cls) {
    for (int clusterId : cls.getClusterIds()) {
      if (clusterId < 0)
        continue;

      clustersToViews.remove(clusterId);
    }
  }

  public void checkEmbedded() {
  }

  void addClusterForClass(ODatabaseDocumentInternal database, final int clusterId, final OClass cls) {
    acquireSchemaWriteLock(database);
    try {
      if (clusterId < 0)
        return;

      checkEmbedded();

      final OClass existingCls = clustersToClasses.get(clusterId);
      if (existingCls != null && !cls.equals(existingCls))
        throw new OSchemaException(
            "Cluster with id " + clusterId + " already belongs to class " + clustersToClasses.get(clusterId));

      clustersToClasses.put(clusterId, cls);
    } finally {
      releaseSchemaWriteLock(database);
    }
  }

  void addClusterForView(ODatabaseDocumentInternal database, final int clusterId, final OView view) {
    acquireSchemaWriteLock(database);
    try {
      if (clusterId < 0)
        return;

      checkEmbedded();

      final OView existingView = clustersToViews.get(clusterId);
      if (existingView != null && !view.equals(existingView))
        throw new OSchemaException("Cluster with id " + clusterId + " already belongs to view " + clustersToViews.get(clusterId));

      clustersToViews.put(clusterId, view);
    } finally {
      releaseSchemaWriteLock(database);
    }
  }

  void removeClusterForClass(ODatabaseDocumentInternal database, int clusterId, OClass cls) {
    acquireSchemaWriteLock(database);
    try {
      if (clusterId < 0)
        return;

      checkEmbedded();

      clustersToClasses.remove(clusterId);
    } finally {
      releaseSchemaWriteLock(database);
    }
  }

  void removeClusterForView(ODatabaseDocumentInternal database, int clusterId, OView view) {
    acquireSchemaWriteLock(database);
    try {
      if (clusterId < 0)
        return;

      checkEmbedded();

      clustersToViews.remove(clusterId);
    } finally {
      releaseSchemaWriteLock(database);
    }
  }
}
