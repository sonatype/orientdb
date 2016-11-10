/*
 *
 *  * Copyright 2010-2016 OrientDB LTD (http://orientdb.com)
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

package com.orientechnologies.lucene.test;

import com.orientechnologies.lucene.functions.OLuceneSearchFunction;
import com.orientechnologies.orient.core.command.script.OCommandScript;
import com.orientechnologies.orient.core.db.record.OIdentifiable;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.OCommandSQL;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.junit.Before;
import org.junit.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

/**
 * Created by enricorisa on 26/09/14.
 */

public class LuceneAllIndexesTest extends BaseLuceneTest {

  @Before
  public void setUp() throws Exception {
    InputStream stream = ClassLoader.getSystemResourceAsStream("testLuceneIndex.sql");

    db.command(new OCommandScript("sql", getScriptFromStream(stream))).execute();

    db.command(new OCommandSQL(
        "create index Song.title on Song (title) FULLTEXT ENGINE LUCENE METADATA {\"analyzer\":\"" + StandardAnalyzer.class
            .getName() + "\"}")).execute();
    db.command(new OCommandSQL(
        "create index Song.author on Song (author) FULLTEXT ENGINE LUCENE METADATA {\"analyzer\":\"" + StandardAnalyzer.class
            .getName() + "\"}")).execute();
    db.command(new OCommandSQL(
        "create index Author.name on Author(name) FULLTEXT ENGINE LUCENE METADATA {\"analyzer\":\"" + StandardAnalyzer.class
            .getName() + "\"}")).execute();

  }

  @Test
  public void shouldSearchTermAcrossAllSubIndexes() throws Exception {

    String query = "select SEARCH('mountain') ";

    List<ODocument> docs = db.query(new OSQLSynchQuery<ODocument>(query));

    assertThat(docs).hasSize(1);

    List<ODocument> results = fetchDocs(docs);

    for (ODocument doc : results) {
      if (doc.getClassName().equals("Song"))
        assertThat(doc.<String>field("title")).containsIgnoringCase("mountain");
      if (doc.getClassName().equals("Author"))
        assertThat(doc.<String>field("name")).containsIgnoringCase("mountain");

    }

  }

  private List<ODocument> fetchDocs(List<ODocument> docs) {
    return docs.get(0)
        .<Set<OIdentifiable>>field(OLuceneSearchFunction.NAME)
        .stream()
        .map(orid -> orid.<ODocument>getRecord())
        .collect(Collectors.toList());
  }

  @Test
  public void shouldSearchAcrossAllSubIndexesWithStrictQuery() {

    String query = "select SEARCH('Song.title:mountain Author.name:Chuck') ";
    List<ODocument> docs = db.query(new OSQLSynchQuery<ODocument>(query));

    assertThat(docs).hasSize(1);

    List<ODocument> results = fetchDocs(docs);

    for (ODocument doc : results) {
      if (doc.getClassName().equals("Song"))
        assertThat(doc.<String>field("title")).containsIgnoringCase("mountain");
      if (doc.getClassName().equals("Author"))
        assertThat(doc.<String>field("name")).containsIgnoringCase("chuck");

    }
  }

}
