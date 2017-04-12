package com.orientechnologies.orient.server.network;

import com.orientechnologies.common.exception.OException;
import com.orientechnologies.orient.client.remote.OServerAdmin;
import com.orientechnologies.orient.core.Orient;
import com.orientechnologies.orient.core.db.document.ODatabaseDocument;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.db.record.ORecordOperation;
import com.orientechnologies.orient.core.sql.query.OLiveQuery;
import com.orientechnologies.orient.core.sql.query.OLiveResultListener;
import com.orientechnologies.orient.server.OServer;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;

public class OLiveQueryShutdownTest {

  private static final String SERVER_DIRECTORY = "./target/db";
  private OServer             server;

  public void bootServer() throws Exception {
    server = new OServer();
    server.setServerRootDirectory(SERVER_DIRECTORY);
    server.startup(getClass().getResourceAsStream("orientdb-server-config.xml"));
    server.activate();

    OServerAdmin server = new OServerAdmin("remote:localhost");
    server.connect("root", "D2AFD02F20640EC8B7A5140F34FCA49D2289DB1F0D0598BB9DE8AAA75A0792F3");
    server.createDatabase(OLiveQueryShutdownTest.class.getSimpleName(), "graph", "memory");

  }

  public void shutdownServer() {
    server.shutdown();
    Orient.instance().startup();
  }

  @Test
  public void testShutDown() throws Exception {
    bootServer();
    ODatabaseDocument db = new ODatabaseDocumentTx("remote:localhost/" + OLiveQueryShutdownTest.class.getSimpleName());
    db.open("admin", "admin");
    db.getMetadata().getSchema().createClass("Test");
    final CountDownLatch error = new CountDownLatch(1);
    try {
      db.command(new OLiveQuery("live select from Test", new OLiveResultListener() {

        @Override
        public void onUnsubscribe(int iLiveToken) {
        }

        @Override
        public void onLiveResult(int iLiveToken, ORecordOperation iOp) throws OException {
        }

        @Override
        public void onError(int iLiveToken) {
          error.countDown();

        }
      })).execute();

      shutdownServer();

      assertTrue("onError method never called on shutdow", error.await(2, TimeUnit.SECONDS));

    } finally {
//      db.close();
    }
  }

}
