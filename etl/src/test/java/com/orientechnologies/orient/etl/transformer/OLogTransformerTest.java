package com.orientechnologies.orient.etl.transformer;

import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.etl.OETLBaseTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OLogTransformerTest extends OETLBaseTest {

  private PrintStream sysOut;

  @Before
  public void redirectSysOutToByteBuff() {

    sysOut = System.out;
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    System.setOut(new PrintStream(output, true));


  }



  @After
  public void redirecByteBuffToSysout() {

    System.setOut(sysOut);
  }

  @Test
  public void testPrefix() throws Exception {
    ByteArrayOutputStream output = getByteArrayOutputStream();
    String cfgJson = "{source: { content: { value: 'id,text\n1,Hello\n2,Bye'} }, extractor : { csv: {} }, transformers : [{ log : {prefix:'-> '}}], loader : { test: {} } }";
    process(cfgJson);
    List<ODocument> res = getResult();
    ODocument doc = res.get(0);
    String[] stringList = output.toString().split(System.getProperty("line.separator"));


    String firstLogExpectedValue = "[1:log] INFO -> {id:1,text:Hello}";
    String secondLogExpectedValue = "[2:log] INFO -> {id:2,text:Bye}";

    boolean firstLogRowPresent = false;
    boolean secondLogRowPresent = false;
    for(int i=0; i<stringList.length; i++) {
      if(stringList[i].equals(firstLogExpectedValue)) {
        firstLogRowPresent = true;
      }
      else if(stringList[i].equals(secondLogExpectedValue)) {
        secondLogRowPresent = true;
      }

      if(firstLogRowPresent && secondLogRowPresent) {
        break;
      }
    }

    assertTrue(firstLogRowPresent);
    assertTrue(secondLogRowPresent);

  }

  @Test
  public void testPostfix() throws Exception {
    ByteArrayOutputStream output = getByteArrayOutputStream();
    String cfgJson = "{source: { content: { value: 'id,text\n1,Hello\n2,Bye'} }, extractor : { csv : {} }, transformers : [{ log : {postfix:'-> '}}], loader : { test: {} } }";
    process(cfgJson);
    List<ODocument> res = getResult();
    ODocument doc = res.get(0);
    String[] stringList = output.toString().split(System.getProperty("line.separator"));

    String firstLogExpectedValue = "[1:log] INFO {id:1,text:Hello}-> ";
    String secondLogExpectedValue = "[2:log] INFO {id:2,text:Bye}-> ";

    boolean firstLogRowPresent = false;
    boolean secondLogRowPresent = false;
    for(int i=0; i<stringList.length; i++) {
      if(stringList[i].equals(firstLogExpectedValue)) {
        firstLogRowPresent = true;
      }
      else if(stringList[i].equals(secondLogExpectedValue)) {
        secondLogRowPresent = true;
      }

      if(firstLogRowPresent && secondLogRowPresent) {
        break;
      }
    }

    assertTrue(firstLogRowPresent);
    assertTrue(secondLogRowPresent);
  }

  private ByteArrayOutputStream getByteArrayOutputStream() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    System.setOut(new PrintStream(output, true));
    return output;
  }

}