/* Generated By:JJTree: Do not edit this line. OSelectStatement.java Version 4.3 */
/* JavaCCOptions:MULTI=true,NODE_USES_PARSER=false,VISITOR=true,TRACK_TOKENS=true,NODE_PREFIX=O,NODE_EXTENDS=,NODE_FACTORY=,SUPPORT_CLASS_VISIBILITY_PUBLIC=true */
package com.orientechnologies.orient.core.sql.parser;

import com.orientechnologies.orient.core.command.OCommandRequest;
import com.orientechnologies.orient.core.command.OCommandRequestText;
import com.orientechnologies.orient.core.exception.OQueryParsingException;
import com.orientechnologies.orient.core.metadata.security.ORole;
import com.orientechnologies.orient.core.metadata.security.ORule;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.OCommandExecutorSQLAbstract;
import com.orientechnologies.orient.core.sql.OCommandExecutorSQLSelect;
import com.orientechnologies.orient.core.sql.OCommandSQLParsingException;
import com.orientechnologies.orient.core.sql.query.OSQLAsynchQuery;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OSelectStatement extends OStatement {

  protected OFromClause  target;

  protected OProjection  projection;

  protected OWhereClause whereClause;

  protected OGroupBy     groupBy;

  protected OOrderBy     orderBy;

  protected OUnwind      unwind;

  protected OSkip        skip;

  protected OLimit       limit;

  protected Boolean      lockRecord;

  protected OFetchPlan   fetchPlan;

  protected OLetClause   letClause;

  protected OTimeout       timeout;

  protected Boolean      parallel;

  protected Boolean      noCache;

  public OSelectStatement(int id) {
    super(id);
  }

  public OSelectStatement(OrientSql p, int id) {
    super(p, id);
  }

  @Override
  public OCommandExecutorSQLAbstract buildExecutor(final OCommandRequest iRequest) {
    getDatabase().checkSecurity(ORule.ResourceGeneric.COMMAND, ORole.PERMISSION_READ);
    final OCommandRequestText textRequest = (OCommandRequestText) iRequest;

    OSQLAsynchQuery<ODocument> request;

    if (iRequest instanceof OSQLSynchQuery) {
      request = (OSQLSynchQuery<ODocument>) iRequest;
    } else if (iRequest instanceof OSQLAsynchQuery) {
      request = (OSQLAsynchQuery<ODocument>) iRequest;
    } else {
      // BUILD A QUERY OBJECT FROM THE COMMAND REQUEST
      request = new OSQLSynchQuery<ODocument>(textRequest.getText());
      if (textRequest.getResultListener() != null) {
        request.setResultListener(textRequest.getResultListener());
      }
    }

    OCommandExecutorSQLSelect result = new OCommandExecutorSQLSelect();
    result.setRequest(request);

    result.initContext();

    result.setProjections(new LinkedHashMap<String, Object>());
    result.setProjectionDefinition(new LinkedHashMap<String, String>());

    if (this.projection != null && this.projection.getItems() != null) {
      for (OProjectionItem item : projection.getItems()) {
        result.getProjections().put(getAlias(item), item.getExpression().createExecutorFilter());
        result.getProjectionDefinition().put(getAlias(item), item.toString());
      }
    }

    return result;
  }

  private String getAlias(OProjectionItem item) {
    if (item.getAlias() != null) {
      return item.getAlias();
    } else {
      return item.getDefaultAlias();
    }

  }

  public OProjection getProjection() {
    return projection;
  }

  public void setProjection(OProjection projection) {
    this.projection = projection;
  }

  public OFromClause getTarget() {
    return target;
  }

  public void setTarget(OFromClause target) {
    this.target = target;
  }

  public OWhereClause getWhereClause() {
    return whereClause;
  }

  public void setWhereClause(OWhereClause whereClause) {
    this.whereClause = whereClause;
  }

  public OGroupBy getGroupBy() {
    return groupBy;
  }

  public void setGroupBy(OGroupBy groupBy) {
    this.groupBy = groupBy;
  }

  public OOrderBy getOrderBy() {
    return orderBy;
  }

  public void setOrderBy(OOrderBy orderBy) {
    this.orderBy = orderBy;
  }

  public OSkip getSkip() {
    return skip;
  }

  public void setSkip(OSkip skip) {
    this.skip = skip;
  }

  public OLimit getLimit() {
    return limit;
  }

  public void setLimit(OLimit limit) {
    this.limit = limit;
  }

  public Boolean getLockRecord() {
    return lockRecord;
  }

  public void setLockRecord(Boolean lockRecord) {
    this.lockRecord = lockRecord;
  }

  public OFetchPlan getFetchPlan() {
    return fetchPlan;
  }

  public void setFetchPlan(OFetchPlan fetchPlan) {
    this.fetchPlan = fetchPlan;
  }

  public OLetClause getLetClause() {
    return letClause;
  }

  public void setLetClause(OLetClause letClause) {
    this.letClause = letClause;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("SELECT");
    if (projection != null) {
      builder.append(" ");
      builder.append(projection.toString());
    }
    if (target != null) {
      builder.append(" FROM ");
      builder.append(target.toString());
    }

    if (letClause != null) {
      builder.append(" ");
      builder.append(letClause.toString());
    }

    if (whereClause != null) {
      builder.append(" WHERE ");
      builder.append(whereClause.toString());
    }

    if (groupBy != null) {
      builder.append(" ");
      builder.append(groupBy.toString());
    }

    if (orderBy != null) {
      builder.append(" ");
      builder.append(orderBy.toString());
    }

    if (unwind != null) {
      builder.append(" ");
      builder.append(unwind.toString());
    }

    if (skip != null) {
      builder.append(skip);
    }

    if (limit != null) {
      builder.append(limit);
    }

    if (Boolean.TRUE.equals(lockRecord)) {
      builder.append(" LOCK RECORD");
    }

    if (fetchPlan != null) {
      builder.append(" ");
      builder.append(fetchPlan.toString());
    }

    if (timeout != null) {
      builder.append(timeout);
    }

    if (Boolean.TRUE.equals(parallel)) {
      builder.append(" PARALLEL");
    }

    if (Boolean.TRUE.equals(noCache)) {
      builder.append(" NOCACHE");
    }

    return builder.toString();
  }

  @Override
  public void replaceParameters(Map<Object, Object> params) {
    if (target != null) {
      target.replaceParameters(params);
    }

    if (projection != null) {
      projection.replaceParameters(params);
    }

    if (letClause != null) {
      letClause.replaceParameters(params);
    }

    if (whereClause != null) {
      whereClause.replaceParameters(params);
    }

    if (groupBy != null) {
      groupBy.replaceParameters(params);
    }

    if (orderBy != null) {
      orderBy.replaceParameters(params);
    }

    if (skip != null) {
      skip.replaceParameters(params);
    }

    if (limit != null) {
      limit.replaceParameters(params);
    }

  }

  public void validate(OrientSql.ValidationStats stats) throws OCommandSQLParsingException {
    if (this.target == null || this.target.item == null || this.target.item.cluster != null || this.target.item.clusterList != null
        || this.target.item.metadata != null || this.target.item.modifier != null || this.target.item.rids.size() > 0
        || this.target.item.statement != null || !(isClassTarget(this.target) || isIndexTarget(this.target))) {
      if (stats.luceneCount > 0) {
        throw new OQueryParsingException("LUCENE condition is allowed only when query target is a Class or an Index");
      }
    }

    if (whereClause != null && whereClause.baseExpression.getNumberOfExternalCalculations() > 1) {
      StringBuilder exceptionText = new StringBuilder();
      exceptionText.append("Incompatible conditions found: \n");
      List<Object> conditions = whereClause.baseExpression.getExternalCalculationConditions();
      for (Object condition : conditions) {
        exceptionText.append(condition.toString() + "\n");
      }
      throw new OQueryParsingException(exceptionText.toString());
    }
  }

  private boolean isClassTarget(OFromClause target) {

    return target != null && target.item != null && target.item.identifier != null && target.item.identifier.suffix != null
        && target.item.identifier.suffix.identifier != null;
  }

  private boolean isIndexTarget(OFromClause target) {
    return target != null && target.item != null && target.item.index != null;
  }

}
/* JavaCC - OriginalChecksum=b26959b9726a8cf35d6283eca931da6b (do not edit this line) */
