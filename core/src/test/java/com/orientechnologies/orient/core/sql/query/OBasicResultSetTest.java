package com.orientechnologies.orient.core.sql.query;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collections;
import java.util.List;

import com.orientechnologies.orient.core.config.OGlobalConfiguration;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.assertEquals;

public class OBasicResultSetTest {

  @Test
  public void testSerializableWithSoftReferences() throws Exception {
    OGlobalConfiguration.QUERY_USE_SOFT_REFENCES_IN_RESULT_SET.setValue(Boolean.TRUE);
    List<String> before = new OBasicResultSet<String>("select from some_test_class");
    Collections.addAll(before, "This", "is", "an", "example", "result");
    assertEquals(before, serializationRoundTrip(before));
  }

  @Test
  public void testSerializableWithStrongReferences() throws Exception {
    OGlobalConfiguration.QUERY_USE_SOFT_REFENCES_IN_RESULT_SET.setValue(Boolean.FALSE);
    List<String> before = new OBasicResultSet<String>("select from some_test_class");
    Collections.addAll(before, "This", "is", "another", "example", "result");
    assertEquals(before, serializationRoundTrip(before));
  }

  private <T> T serializationRoundTrip(T object) throws Exception {

    ByteArrayOutputStream mem = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(mem);

    oos.writeObject(object);
    oos.flush();

    ByteArrayInputStream bais = new ByteArrayInputStream(mem.toByteArray());
    ObjectInputStream ios = new ObjectInputStream(bais);

    return (T) ios.readObject();
  }

}
