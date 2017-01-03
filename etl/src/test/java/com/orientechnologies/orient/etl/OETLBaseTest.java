/*
 *
 *  * Copyright 2010-2016 OrientDB LTD (info(-at-)orientdb.com)
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.orientechnologies.orient.etl;

import com.orientechnologies.common.log.OLogManager;
import com.orientechnologies.orient.core.command.OBasicCommandContext;
import com.orientechnologies.orient.core.command.OCommandContext;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.tinkerpop.blueprints.impls.orient.OrientGraph;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;

import java.util.List;

/**
 * Tests ETL JSON Extractor.
 *
 * @author Luca Garulli (l.garulli--(at)--orientdb.com)
 */
public abstract class OETLBaseTest {
  @Rule
  public TestName name = new TestName();

  protected String[] names    = new String[] { "Jay", "Luca", "Bill", "Steve", "Jill", "Luigi", "Enrico", "Emanuele" };
  protected String[] surnames = new String[] { "Miner", "Ferguson", "Cancelli", "Lavori", "Raggio", "Eagles", "Smiles",
      "Ironcutter" };
  protected OrientGraph graph;
  protected OETLProcessor             proc;
  private   OETLProcessorConfigurator configurator;

  @Before
  public void setupDatabse() throws Throwable {

    OLogManager.instance().installCustomFormatter();
    String config = System.getProperty("orientdb.test.env", "memory");

    graph = new OrientGraph("memory:" + name.getMethodName());

    graph.setUseLightweightEdges(false);

  }

  @After
  public void dropDatabase() {
    graph.drop();
  }

  @Before
  public void setUpConfigurator() {
    OETLComponentFactory factory = new OETLComponentFactory().registerLoader(OETLStubLoader.class)
        .registerExtractor(OETLStubRandomExtractor.class);

    configurator = new OETLProcessorConfigurator(factory);
  }

  protected List<ODocument> getResult() {
    return ((OETLStubLoader) proc.getLoader()).loadedRecords;
  }

  protected void process(final String cfgJson) {

    process(cfgJson, new OBasicCommandContext());
  }

  protected void process(final String cfgJson, final OCommandContext iContext) {
    ODocument cfg = new ODocument().fromJSON(cfgJson, "noMap");

    proc = configurator.parse(cfg, iContext);
    proc.execute();
  }
}
