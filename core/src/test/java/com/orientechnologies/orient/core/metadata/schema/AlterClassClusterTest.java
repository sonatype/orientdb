package com.orientechnologies.orient.core.metadata.schema;

import static org.testng.AssertJUnit.assertTrue;
import static org.testng.AssertJUnit.assertEquals;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.orientechnologies.orient.core.db.document.ODatabaseDocument;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.exception.ODatabaseException;
import com.orientechnologies.orient.core.exception.OSchemaException;

public class AlterClassClusterTest {

  private ODatabaseDocument db;

  @BeforeMethod
  public void before() {
    db = new ODatabaseDocumentTx("memory:" + AlterClassClusterTest.class.getSimpleName());
    db.create();
  }

  @AfterMethod
  public void after() {
    db.drop();
  }

  @Test
  public void testRemoveClusterDefaultCluster() {
    OClass clazz = db.getMetadata().getSchema().createClass("Test", 1, null);
    clazz.addCluster("TestOneMore");

    clazz.removeClusterId(db.getClusterIdByName("Test"));
    db.getMetadata().getSchema().reload();
    clazz = db.getMetadata().getSchema().getClass("Test");
    assertEquals(clazz.getDefaultClusterId(), db.getClusterIdByName("TestOneMore"));

  }

  @Test(expectedExceptions = ODatabaseException.class)
  public void testRemoveLastClassCluster() {
    OClass clazz = db.getMetadata().getSchema().createClass("Test", 1, null);
    clazz.removeClusterId(db.getClusterIdByName("Test"));
  }

  @Test(expectedExceptions = OSchemaException.class)
  public void testAddClusterToAbstracClass() {
    OClass clazz = db.getMetadata().getSchema().createAbstractClass("Test");
    clazz.addCluster("TestOneMore");
  }

  @Test(expectedExceptions = OSchemaException.class)
  public void testAddClusterIdToAbstracClass() {
    OClass clazz = db.getMetadata().getSchema().createAbstractClass("Test");
    int id = db.addCluster("TestOneMore");
    clazz.addClusterId(id);
  }

  @Test
  public void testSetAbstractRestrictedClass() {
    OSchema oSchema = db.getMetadata().getSchema();
    OClass oRestricted = oSchema.getClass("ORestricted");
    OClass v = oSchema.getClass("V");
    v.addSuperClass(oRestricted);

    OClass ovt = oSchema.createClass("Some", v);
    ovt.setAbstract(true);

    assertTrue(ovt.isAbstract());
  }

}
