package com.orientechnologies.orient.etl.loader;

import com.orientechnologies.orient.core.db.document.ODatabaseDocument;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.exception.OConfigurationException;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.index.OIndex;
import com.orientechnologies.orient.core.index.OIndexManagerProxy;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.OCommandSQL;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;
import com.orientechnologies.orient.etl.OETLBaseTest;
import com.tinkerpop.blueprints.Vertex;
import org.junit.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Created by frank on 9/14/15.
 */
public class OOrientDBLoaderTest extends OETLBaseTest {

  @Test(expected = OConfigurationException.class)
  public void shouldFailToManageRemoteServer() throws Exception {

    process("{source: { content: { value: 'name,surname\nJay,Miner' } }, extractor : { csv: {} }, loader: { orientdb: {\n"
        + "      dbURL: \"remote:sadserver/OETLBaseTest\",\n" + "      dbUser: \"admin\",\n" + "      dbPassword: \"admin\",\n"
        + "      serverUser: \"admin\",\n" + "      serverPassword: \"admin\",\n" + "      dbAutoCreate: true,\n"
        + "      tx: false,\n" + "      batchCommit: 1000,\n" + "      wal : true,\n" + "      dbType: \"graph\",\n"
        + "      classes: [\n" + "        {name:\"Person\", extends: \"V\" },\n" + "      ],\n"
        + "      indexes: [{class:\"V\" , fields:[\"surname:String\"], \"type\":\"NOTUNIQUE\", \"metadata\": { \"ignoreNullValues\" : \"false\"}} ]  } } }");

  }

  @Test
  public void testAddMetadataToIndex() {

    process("{source: { content: { value: 'name,surname\nJay,Miner' } }, extractor : { csv: {} }, loader: { orientdb: {\n"
        + "      dbURL: \"memory:OETLBaseTest\",\n" + "      dbUser: \"admin\",\n" + "      dbPassword: \"admin\",\n"
        + "      dbAutoCreate: true,\n" + "      tx: false,\n" + "      batchCommit: 1000,\n" + "      wal : true,\n"
        + "      dbType: \"graph\",\n" + "      classes: [\n" + "        {name:\"Person\", extends: \"V\" },\n" + "      ],\n"
        + "      indexes: [{class:\"V\" , fields:[\"surname:String\"], \"type\":\"NOTUNIQUE\", \"metadata\": { \"ignoreNullValues\" : \"false\"}} ]  } } }");

    final OIndexManagerProxy indexManager = graph.getRawGraph().getMetadata().getIndexManager();

    assertThat(indexManager.existsIndex("V.surname")).isTrue();

    final ODocument indexMetadata = indexManager.getIndex("V.surname").getMetadata();
    assertThat(indexMetadata.containsField("ignoreNullValues")).isTrue();
    assertThat(indexMetadata.<String>field("ignoreNullValues")).isEqualTo("false");

  }

  @Test
  public void testCreateLuceneIndex() {

    process("{source: { content: { value: 'name,surname\nJay,Miner' } }, extractor : { csv: {} }, "
        + "\"transformers\": [\n"
        + "    {\n"
        + "      \"vertex\": {\n"
        + "        \"class\": \"Person\",\n"
        + "        \"skipDuplicates\": true\n"
        + "      }\n"
        + "    }],"
        + "loader: { orientdb: {\n"
        + "      dbURL: 'memory:OETLBaseTest',\n" + "      dbUser: \"admin\",\n" + "      dbPassword: \"admin\",\n"
        + "      dbAutoCreate: true,\n" + "      tx: false,\n" + "      batchCommit: 1000,\n" + "      wal : true,\n"
        + "      dbType: \"graph\",\n" + "      classes: [\n" + "        {name:\"Person\", extends: \"V\" },\n" + "      ],\n"
        + "      indexes: [{class:\"Person\" , fields:[\"surname:String\"], \"type\":\"FULLTEXT\",  \"algorithm\":\"LUCENE\",  \"metadata\": { \"ignoreNullValues\" : \"false\"}} ]  } } }");

    graph.makeActive();

    ODatabaseDocumentTx db = graph.getRawGraph();

    final OIndexManagerProxy indexManager = db.getMetadata().getIndexManager();

    assertThat(indexManager.existsIndex("Person.surname")).isTrue();

    final OIndex<?> index = indexManager.getIndex("Person.surname");
    final ODocument indexMetadata = index.getMetadata();
    assertThat(index.getAlgorithm()).isEqualTo("LUCENE");

    assertThat(indexMetadata.containsField("ignoreNullValues")).isTrue();
    assertThat(indexMetadata.<String>field("ignoreNullValues")).isEqualTo("false");

    List<ODocument> records = db.query(new OSQLSynchQuery<ODocument>("select from Person where surname LUCENE 'mi*' "));

    assertThat(records).hasSize(1);

  }

  @Test
  public void shouldSaveDocumentsOnGivenCluster() {

    process("{source: { content: { value: 'name,surname\nJay,Miner' } }, extractor : { csv: {} }, loader: { orientdb: {\n"
        + "      dbURL: \"memory:OETLBaseTest\",\n" + "      dbUser: \"admin\",\n" + "      dbPassword: \"admin\",\n"
        + "      dbAutoCreate: true,\n" + "      cluster : \"myCluster\",\n" + "      tx: false,\n" + "      batchCommit: 1000,\n"
        + "      wal : true,\n" + "      dbType: \"graph\",\n" + "      classes: [\n"
        + "        {name:\"Person\", extends: \"V\" },\n" + "      ],\n"
        + "      indexes: [{class:\"V\" , fields:[\"surname:String\"], \"type\":\"NOTUNIQUE\", \"metadata\": { \"ignoreNullValues\" : \"false\"}} ]  } } }");

    graph.makeActive();
    int idByName = graph.getRawGraph().getClusterIdByName("myCluster");

    Iterable<Vertex> vertices = graph.getVertices();

    for (Vertex vertex : vertices) {
      assertThat(((ORID) vertex.getId()).getClusterId()).isEqualTo(idByName);
    }
  }

  @Test
  public void shouldSaveDocuments() {

    process(
        "{source: { content: { value: 'name,surname,@class\nJay,Miner,Person' } }, extractor : { csv: {} }, loader: { orientdb: {\n"
            + "      dbURL: \"memory:OETLBaseTest\",\n" + "      dbUser: \"admin\",\n" + "      dbPassword: \"admin\",\n"
            + "      dbAutoCreate: true,\n      tx: false,\n" + "      batchCommit: 1000,\n" + "      wal : false,\n"
            + "      dbType: \"document\" , \"classes\": [\n" + "        {\n" + "          \"name\": \"Person\"\n" + "        },\n"
            + "        {\n" + "          \"name\": \"UpdateDetails\"\n" + "        }\n" + "      ]      } } }");

    graph.makeActive();

    ODatabaseDocumentTx db = graph.getRawGraph();

    List<?> res = db.query(new OSQLSynchQuery<ODocument>("SELECT FROM Person"));

    assertThat(res.size()).isEqualTo(1);

  }

  @Test
  public void shouldSaveDocumentsWithPredefinedSchema() {

    //create class
    ODatabaseDocument db = new ODatabaseDocumentTx(graph.getRawGraph().getURL()).open("admin", "admin");

    db.command(new OCommandSQL("CREATE Class Person")).execute();
    db.command(new OCommandSQL("CREATE property Person.name STRING")).execute();
    db.command(new OCommandSQL("CREATE property Person.surname STRING")).execute();
    db.command(new OCommandSQL("CREATE property Person.married BOOLEAN")).execute();
    db.command(new OCommandSQL("CREATE property Person.birthday DATETIME")).execute();

    db.close();

    //store data
    process(
        "{source: { content: { value: 'name,surname,married,birthday\nJay,Miner,false,1970-01-01 05:30:00' } }, "
            + "extractor : { csv: {columns:['name:string','surname:string','married:boolean','birthday:datetime'], dateFormat :'yyyy-MM-dd HH:mm:ss'} }, loader: { orientdb: {\n"
            + "      dbURL: \"memory:OETLBaseTest\", class:'Person',     dbUser: \"admin\",\n" + "      dbPassword: \"admin\",\n"
            + "      dbAutoCreate: false,\n      tx: false,\n" + "      batchCommit: 1000,\n" + "      wal : false,\n"
            + "      dbType: \"document\" } } }");

    graph.makeActive();

    db = graph.getRawGraph();

    List<?> res = db.query(new OSQLSynchQuery<ODocument>("SELECT FROM Person"));

    assertThat(res.size()).isEqualTo(1);

  }
}