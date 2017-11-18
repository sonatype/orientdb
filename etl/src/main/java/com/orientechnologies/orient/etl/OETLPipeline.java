/*
 *
 *  * Copyright 2010-2014 Orient Technologies LTD (info(at)orientechnologies.com)
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

import com.orientechnologies.common.concur.ONeedRetryException;
import com.orientechnologies.common.exception.OException;
import com.orientechnologies.orient.core.command.OCommandContext;
import com.orientechnologies.orient.core.db.document.ODatabaseDocument;
import com.orientechnologies.orient.etl.OETLProcessor.LOG_LEVELS;
import com.orientechnologies.orient.etl.context.OETLContext;
import com.orientechnologies.orient.etl.loader.OLoader;
import com.orientechnologies.orient.etl.transformer.OTransformer;
import com.tinkerpop.blueprints.impls.orient.OrientBaseGraph;
import com.orientechnologies.orient.etl.context.OETLContextWrapper;

import java.util.List;

import static com.orientechnologies.orient.etl.OETLProcessor.LOG_LEVELS.*;

/**
 * ETL pipeline: sequence of OTransformer and a OLoader.
 *
 * @author Luca Garulli (l.garulli-at-orientechnologies.com)
 */
public class OETLPipeline {
  protected final OETLProcessor      processor;
  protected final List<OTransformer> transformers;
  protected final OLoader            loader;
  protected final OCommandContext    context;
  protected final LOG_LEVELS         logLevel;
  protected final int                maxRetries;
  protected       boolean            haltOnError;
  protected       ODatabaseDocument  db;
  protected       OrientBaseGraph    graph;

  public OETLPipeline(final OETLProcessor processor, final List<OTransformer> transformers, final OLoader loader,
      final LOG_LEVELS logLevel, final int maxRetries, final boolean haltOnError) {
    this.processor = processor;
    this.transformers = transformers;
    this.loader = loader;
    this.logLevel = logLevel;
    this.maxRetries = maxRetries;
    this.haltOnError = haltOnError;

    context = new OETLContext();

    for (OTransformer t : this.transformers)
      t.setPipeline(this);

  }

  public synchronized void begin() {
    loader.beginLoader(this);
    for (OTransformer t : transformers)
      t.begin();
  }

  public ODatabaseDocument getDocumentDatabase() {
    if (db != null)
      db.activateOnCurrentThread();
    return db;
  }

  public OETLPipeline setDocumentDatabase(final ODatabaseDocument iDb) {
    db = iDb;
    return this;
  }

  public OrientBaseGraph getGraphDatabase() {
    if (graph != null)
      graph.makeActive();
    return graph;
  }

  public OETLPipeline setGraphDatabase(final OrientBaseGraph iGraph) {
    graph = iGraph;
    return this;
  }

  public OCommandContext getContext() {
    return context;
  }

  protected Object execute(final OExtractedItem source) {
    int retry = 0;
    do {
      try {
        Object current = source.payload;

        context.setVariable("extractedNum", source.num);
        context.setVariable("extractedPayload", source.payload);

        for (OTransformer t : transformers) {
          current = t.transform(current);
          if (current == null) {
            OETLContextWrapper.getInstance().getMessageHandler().warn(this, "Transformer [%s] returned null, skip rest of pipeline execution", t);
          }
        }
        if (current != null)
          // LOAD
          loader.load(this, current, context);

        return current;
      } catch (ONeedRetryException e) {
        loader.rollback(this);
        retry++;
        OETLContextWrapper.getInstance().getMessageHandler().info(this, "Error in pipeline execution, retry = %d/%d (exception=)", retry, maxRetries, e);
      } catch (OETLProcessHaltedException e) {
        OETLContextWrapper.getInstance().getMessageHandler().error(this, "Pipeline execution halted");

        processor.getStats().incrementErrors();

        loader.rollback(this);
        throw e;

      } catch (Exception e) {
        OETLContextWrapper.getInstance().getMessageHandler().error(this, "Error in Pipeline execution:", e);

        processor.getStats().incrementErrors();

        if (!haltOnError) {
          return null;
        }

        loader.rollback(this);
        throw OException.wrapException(new OETLProcessHaltedException("Halt"), e);

      }
    } while (retry < maxRetries);

    return this;
  }

  public void end() {
    loader.endLoader(this);
  }
}
