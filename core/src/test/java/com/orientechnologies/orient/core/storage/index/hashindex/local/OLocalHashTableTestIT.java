package com.orientechnologies.orient.core.storage.index.hashindex.local;

import com.orientechnologies.common.io.OFileUtils;
import com.orientechnologies.common.serialization.types.OIntegerSerializer;
import com.orientechnologies.orient.core.db.ODatabaseInternal;
import com.orientechnologies.orient.core.db.ODatabaseSession;
import com.orientechnologies.orient.core.db.ODatabaseType;
import com.orientechnologies.orient.core.db.OrientDB;
import com.orientechnologies.orient.core.db.OrientDBConfig;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.serialization.serializer.binary.OBinarySerializerFactory;
import com.orientechnologies.orient.core.storage.impl.local.OAbstractPaginatedStorage;
import org.junit.After;
import org.junit.Before;

import java.io.File;

/**
 * @author Andrey Lomakin (a.lomakin-at-orientdb.com)
 * @since 19.02.13
 */
public class OLocalHashTableTestIT extends OLocalHashTableBase {
  private OrientDB orientDB;

  private static final String DB_NAME = "localHashTableTest";

  @Before
  public void before() throws Exception {
    String buildDirectory = System.getProperty("buildDirectory", ".");
    final File dbDirectory = new File(buildDirectory, DB_NAME);

    OFileUtils.deleteRecursively(dbDirectory);
    orientDB = new OrientDB("plocal:" + buildDirectory, OrientDBConfig.defaultConfig());

    orientDB.create(DB_NAME, ODatabaseType.PLOCAL);
    final ODatabaseSession databaseDocumentTx = orientDB.open(DB_NAME, "admin", "admin");

    OMurmurHash3HashFunction<Integer> murmurHash3HashFunction = new OMurmurHash3HashFunction<Integer>(OIntegerSerializer.INSTANCE);

    localHashTable = new OLocalHashTable<>("localHashTableTest", ".imc", ".tsc", ".obf", ".nbh",
        (OAbstractPaginatedStorage) ((ODatabaseInternal) databaseDocumentTx).getStorage());

    localHashTable
        .create(OIntegerSerializer.INSTANCE, OBinarySerializerFactory.getInstance().getObjectSerializer(OType.STRING), null, null,
            murmurHash3HashFunction, true);

  }

  @After
  public void after() {
    orientDB.drop(DB_NAME);
    orientDB.close();
  }

}
