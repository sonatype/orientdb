/* Generated By:JJTree: Do not edit this line. OTraverseProjectionItem.java Version 4.3 */
/* JavaCCOptions:MULTI=true,NODE_USES_PARSER=false,VISITOR=true,TRACK_TOKENS=true,NODE_PREFIX=O,NODE_EXTENDS=,NODE_FACTORY=,SUPPORT_CLASS_VISIBILITY_PUBLIC=true */
package com.orientechnologies.orient.core.sql.parser;

import com.orientechnologies.orient.core.command.OCommandContext;
import com.orientechnologies.orient.core.db.record.OIdentifiable;
import com.orientechnologies.orient.core.sql.executor.OResult;
import com.orientechnologies.orient.core.sql.executor.OResultSet;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class OTraverseProjectionItem extends SimpleNode {

  protected OBaseIdentifier base;
  protected OModifier       modifier;

  public OTraverseProjectionItem(int id) {
    super(id);
  }

  public OTraverseProjectionItem(OrientSql p, int id) {
    super(p, id);
  }

  public Object execute(OResult iCurrentRecord, OCommandContext ctx) {
    if (isStar()) {
      return handleStar(iCurrentRecord, ctx);
    }
    Object result = base.execute(iCurrentRecord, ctx);
    if (modifier != null) {
      result = modifier.execute(iCurrentRecord, result, ctx);
    }
    return result;
  }

  private boolean isStar() {
    return base.toString().equals("*") && modifier == null;
  }

  private Object handleStar(OResult iCurrentRecord, OCommandContext ctx) {
    Set<Object> result = new HashSet<>();
    for (String prop : iCurrentRecord.getPropertyNames()) {
      Object val = iCurrentRecord.getProperty(prop);
      if (isOResult(val) || isValidIdentifiable(val)) {
        result.add(val);

      } else {
        if (val instanceof Iterable) {
          val = ((Iterable) val).iterator();
        }
        if (val instanceof Iterator) {
          while (((Iterator) val).hasNext()) {
            Object sub = ((Iterator) val).next();
            if (isOResult(sub) || isValidIdentifiable(sub)) {
              result.add(sub);
            }
          }
        } else if (val instanceof OResultSet) {
          while (((OResultSet) val).hasNext()) {
            result.add(((OResultSet) val).next());
          }
        }
      }
    }
    return result;
  }

  private boolean isValidIdentifiable(Object val) {
    if (!(val instanceof OIdentifiable)) {
      return false;
    }
    return ((OIdentifiable) val).getIdentity().isPersistent();
  }

  private boolean isOResult(Object val) {
    return val instanceof OResult;
  }

  /**
   * Accept the visitor.
   **/
  public Object jjtAccept(OrientSqlVisitor visitor, Object data) {
    return visitor.visit(this, data);
  }

  public void toString(Map<Object, Object> params, StringBuilder builder) {

    base.toString(params, builder);
    if (modifier != null) {
      modifier.toString(params, builder);
    }
  }

  public OTraverseProjectionItem copy() {
    OTraverseProjectionItem result = new OTraverseProjectionItem(-1);
    result.base = base == null ? null : base.copy();
    result.modifier = modifier == null ? null : modifier.copy();
    return result;
  }

  @Override public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    OTraverseProjectionItem that = (OTraverseProjectionItem) o;

    if (base != null ? !base.equals(that.base) : that.base != null)
      return false;
    if (modifier != null ? !modifier.equals(that.modifier) : that.modifier != null)
      return false;

    return true;
  }

  @Override public int hashCode() {
    int result = (base != null ? base.hashCode() : 0);
    result = 31 * result + (modifier != null ? modifier.hashCode() : 0);
    return result;
  }
}
/* JavaCC - OriginalChecksum=0c562254fd4d11266edc0504fd36fc99 (do not edit this line) */
