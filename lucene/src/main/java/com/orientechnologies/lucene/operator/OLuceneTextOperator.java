/*
 * Copyright 2010-2016 OrientDB LTD (http://orientdb.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.orientechnologies.lucene.operator;

import com.orientechnologies.common.log.OLogManager;
import com.orientechnologies.lucene.collections.OLuceneCompositeKey;
import com.orientechnologies.lucene.index.OLuceneFullTextIndex;
import com.orientechnologies.orient.core.command.OCommandContext;
import com.orientechnologies.orient.core.db.ODatabase;
import com.orientechnologies.orient.core.db.ODatabaseDocumentInternal;
import com.orientechnologies.orient.core.db.ODatabaseRecordThreadLocal;
import com.orientechnologies.orient.core.db.record.OIdentifiable;
import com.orientechnologies.orient.core.exception.OCommandExecutionException;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.index.OIndex;
import com.orientechnologies.orient.core.index.OIndexCursor;
import com.orientechnologies.orient.core.index.OIndexCursorCollectionValue;
import com.orientechnologies.orient.core.index.OIndexCursorSingleValue;
import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.OIndexSearchResult;
import com.orientechnologies.orient.core.sql.filter.OSQLFilterCondition;
import com.orientechnologies.orient.core.sql.filter.OSQLFilterItemField;
import com.orientechnologies.orient.core.sql.operator.OIndexReuseType;
import com.orientechnologies.orient.core.sql.operator.OQueryTargetOperator;
import com.orientechnologies.orient.core.sql.parser.ParseException;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.memory.MemoryIndex;

import java.util.*;

public class OLuceneTextOperator extends OQueryTargetOperator {

  public static final String MEMORY_INDEX = "_memoryIndex";

  public OLuceneTextOperator() {
    this("LUCENE", 5, false);
  }

  public OLuceneTextOperator(String iKeyword, int iPrecedence, boolean iLogical) {
    super(iKeyword, iPrecedence, iLogical);
  }

  protected static ODatabaseDocumentInternal getDatabase() {
    return ODatabaseRecordThreadLocal.INSTANCE.get();
  }

  @Override
  public OIndexReuseType getIndexReuseType(Object iLeft, Object iRight) {
    return OIndexReuseType.INDEX_OPERATOR;
  }

  @Override
  public OIndexSearchResult getOIndexSearchResult(OClass iSchemaClass, OSQLFilterCondition iCondition,
      List<OIndexSearchResult> iIndexSearchResults, OCommandContext context) {

    //FIXME questo non trova l'indice se l'ordine e' errato
    return OLuceneOperatorUtil.buildOIndexSearchResult(iSchemaClass, iCondition, iIndexSearchResults, context);
  }

  @Override
  public OIndexCursor executeIndexQuery(OCommandContext iContext, OIndex<?> index, List<Object> keyParams, boolean ascSortOrder) {
    OIndexCursor cursor;
    Object indexResult = index.get(new OLuceneCompositeKey(keyParams).setContext(iContext));
    if (indexResult == null || indexResult instanceof OIdentifiable)
      cursor = new OIndexCursorSingleValue((OIdentifiable) indexResult, new OLuceneCompositeKey(keyParams));
    else
      cursor = new OIndexCursorCollectionValue(((Collection<OIdentifiable>) indexResult), new OLuceneCompositeKey(keyParams));
    return cursor;
  }

  @Override
  public ORID getBeginRidRange(Object iLeft, Object iRight) {
    return null;
  }

  @Override
  public ORID getEndRidRange(Object iLeft, Object iRight) {
    return null;
  }

  @Override
  public boolean canBeMerged() {
    return false;
  }

  @Override
  public Collection<OIdentifiable> filterRecords(ODatabase<?> iRecord, List<String> iTargetClasses, OSQLFilterCondition iCondition,
      Object iLeft, Object iRight) {
    return null;
  }

  @Override
  public Object evaluateRecord(OIdentifiable iRecord,
      ODocument iCurrentResult,
      OSQLFilterCondition iCondition,
      Object iLeft,
      Object iRight,
      OCommandContext iContext) {

    OLuceneFullTextIndex index = involvedIndex(iRecord, iCurrentResult, iCondition, iLeft, iRight);
    if (index == null) {
      throw new OCommandExecutionException("Cannot evaluate lucene condition without index configuration.");
    }

    MemoryIndex memoryIndex = (MemoryIndex) iContext.getVariable(MEMORY_INDEX);
    if (memoryIndex == null) {
      memoryIndex = new MemoryIndex();
      iContext.setVariable(MEMORY_INDEX, memoryIndex);
    }
    memoryIndex.reset();

    try {
      for (IndexableField field : index.buildDocument(iLeft).getFields()) {

        memoryIndex.addField(field, index.indexAnalyzer());
      }
      boolean b = memoryIndex.search(index.buildQuery(iRight)) > 0.0f;
      return b;
    } catch (ParseException e) {
      OLogManager.instance().error(this, "error occurred while building query", e);

    }
    return null;
  }

  protected OLuceneFullTextIndex involvedIndex(OIdentifiable iRecord, ODocument iCurrentResult, OSQLFilterCondition iCondition,
      Object iLeft, Object iRight) {

    ODocument doc = iRecord.getRecord();
    OClass cls = getDatabase().getMetadata().getSchema().getClass(doc.getClassName());

    if (isChained(iCondition.getLeft())) {

      OSQLFilterItemField chained = (OSQLFilterItemField) iCondition.getLeft();

      OSQLFilterItemField.FieldChain fieldChain = chained.getFieldChain();
      OClass oClass = cls;
      for (int i = 0; i < fieldChain.getItemCount() - 1; i++) {
        oClass = oClass.getProperty(fieldChain.getItemName(i)).getLinkedClass();
      }
      if (oClass != null) {
        cls = oClass;
      }
    }
    Set<OIndex<?>> classInvolvedIndexes = cls.getInvolvedIndexes(fields(iCondition));
    OLuceneFullTextIndex idx = null;
    for (OIndex<?> classInvolvedIndex : classInvolvedIndexes) {

      if (classInvolvedIndex.getInternal() instanceof OLuceneFullTextIndex) {
        idx = (OLuceneFullTextIndex) classInvolvedIndex.getInternal();
        break;
      }
    }
    return idx;
  }

  private boolean isChained(Object left) {
    if (left instanceof OSQLFilterItemField) {
      OSQLFilterItemField field = (OSQLFilterItemField) left;
      return field.isFieldChain();
    }
    return false;
  }

  //returns a list of field names
  protected Collection<String> fields(OSQLFilterCondition iCondition) {

    Object left = iCondition.getLeft();

    if (left instanceof String) {
      String fName = (String) left;
      return Arrays.asList(fName);
    }
    if (left instanceof Collection) {
      Collection<OSQLFilterItemField> f = (Collection<OSQLFilterItemField>) left;

      List<String> fields = new ArrayList<String>();
      for (OSQLFilterItemField field : f) {
        fields.add(field.toString());
      }
      return fields;
    }
    if (left instanceof OSQLFilterItemField) {

      OSQLFilterItemField fName = (OSQLFilterItemField) left;
      if (fName.isFieldChain()) {
        int itemCount = fName.getFieldChain().getItemCount();
        return Arrays.asList(fName.getFieldChain().getItemName(itemCount - 1));
      } else {
        return Arrays.asList(fName.toString());
      }
    }
    return Collections.emptyList();
  }
}
