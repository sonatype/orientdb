/* Generated By:JJTree: Do not edit this line. OMethodCall.java Version 4.3 */
/* JavaCCOptions:MULTI=true,NODE_USES_PARSER=false,VISITOR=true,TRACK_TOKENS=true,NODE_PREFIX=O,NODE_EXTENDS=,NODE_FACTORY=,SUPPORT_CLASS_VISIBILITY_PUBLIC=true */
package com.orientechnologies.orient.core.sql.parser;

import com.orientechnologies.orient.core.command.OCommandContext;
import com.orientechnologies.orient.core.db.ODatabaseDocumentInternal;
import com.orientechnologies.orient.core.db.ODatabaseRecordThreadLocal;
import com.orientechnologies.orient.core.db.record.OIdentifiable;
import com.orientechnologies.orient.core.sql.OSQLEngine;
import com.orientechnologies.orient.core.sql.functions.OSQLFunction;
import com.orientechnologies.orient.core.sql.functions.OSQLFunctionFiltered;
import com.orientechnologies.orient.core.sql.method.OSQLMethod;

import java.util.*;

public class OMethodCall extends SimpleNode {

  static Set<String>          graphMethods         = new HashSet<String>(Arrays.asList(new String[] { "out", "in", "both", "outE",
      "inE", "bothE", "bothV", "outV", "inV"      }));

  static Set<String>          bidirectionalMethods = new HashSet<String>(Arrays.asList(new String[] { "out", "in", "both", "oute", "ine", "inv", "outv" }));

  protected OIdentifier       methodName;
  protected List<OExpression> params               = new ArrayList<OExpression>();

  public OMethodCall(int id) {
    super(id);
  }

  public OMethodCall(OrientSql p, int id) {
    super(p, id);
  }

  /**
   * Accept the visitor. *
   */
  public Object jjtAccept(OrientSqlVisitor visitor, Object data) {
    return visitor.visit(this, data);
  }

  public void toString(Map<Object, Object> params, StringBuilder builder) {
    builder.append(".");
    methodName.toString(params, builder);
    builder.append("(");
    boolean first = true;
    for (OExpression param : this.params) {
      if (!first) {
        builder.append(", ");
      }
      param.toString(params, builder);
      first = false;
    }
    builder.append(")");
  }

  public boolean isBidirectional() {
    return bidirectionalMethods.contains(methodName.getStringValue().toLowerCase(Locale.ENGLISH));
  }

  public Object execute(Object targetObjects, OCommandContext ctx) {
    return execute(targetObjects, ctx, methodName.getStringValue(), params, null);
  }

  public Object execute(Object targetObjects, Iterable<OIdentifiable> iPossibleResults, OCommandContext ctx) {
    return execute(targetObjects, ctx, methodName.getStringValue(), params, iPossibleResults);
  }

  private Object execute(Object targetObjects, OCommandContext ctx, String name, List<OExpression> iParams,
      Iterable<OIdentifiable> iPossibleResults) {
    List<Object> paramValues = new ArrayList<Object>();
    for (OExpression expr : iParams) {
      paramValues.add(expr.execute((OIdentifiable) ctx.getVariable("$current"), ctx));
    }
    if (graphMethods.contains(name)) {
      OSQLFunction function = OSQLEngine.getInstance().getFunction(name);
      if (function instanceof OSQLFunctionFiltered) {
        return ((OSQLFunctionFiltered) function).execute(targetObjects, (OIdentifiable) ctx.getVariable("$current"), null,
            paramValues.toArray(), iPossibleResults, ctx);
      } else {
        return function.execute(targetObjects, (OIdentifiable) ctx.getVariable("$current"), null, paramValues.toArray(), ctx);
      }

    }
    OSQLMethod method = OSQLEngine.getMethod(name);
    if (method != null) {
      return method.execute(targetObjects, (OIdentifiable) ctx.getVariable("$current"), ctx, targetObjects, paramValues.toArray());
    }
    throw new UnsupportedOperationException("OMethod call, something missing in the implementation...?");

  }

  public Object executeReverse(Object targetObjects, OCommandContext ctx) {
    if (!isBidirectional()) {
      throw new UnsupportedOperationException();
    }

    String straightName = methodName.getStringValue();
    if (straightName.equalsIgnoreCase("out")) {
      return execute(targetObjects, ctx, "in", params, null);
    }
    if (straightName.equalsIgnoreCase("in")) {
      return execute(targetObjects, ctx, "out", params, null);
    }

    if (straightName.equalsIgnoreCase("both")) {
      return execute(targetObjects, ctx, "both", params, null);
    }

    if (straightName.equalsIgnoreCase("outE")) {
      return execute(targetObjects, ctx, "outV", params, null);
    }

    if (straightName.equalsIgnoreCase("outV")) {
      return execute(targetObjects, ctx, "outE", params, null);
    }

    if (straightName.equalsIgnoreCase("inE")) {
      return execute(targetObjects, ctx, "inV", params, null);
    }

    if (straightName.equalsIgnoreCase("inV")) {
      return execute(targetObjects, ctx, "inE", params, null);
    }

    throw new UnsupportedOperationException("Invalid reverse traversal: " + methodName);
  }

  public static ODatabaseDocumentInternal getDatabase() {
    return ODatabaseRecordThreadLocal.instance().get();
  }

}
/* JavaCC - OriginalChecksum=da95662da21ceb8dee3ad88c0d980413 (do not edit this line) */
