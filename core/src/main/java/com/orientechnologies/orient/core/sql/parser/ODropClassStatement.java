/* Generated By:JJTree: Do not edit this line. ODropClassStatement.java Version 4.3 */
/* JavaCCOptions:MULTI=true,NODE_USES_PARSER=false,VISITOR=true,TRACK_TOKENS=true,NODE_PREFIX=O,NODE_EXTENDS=,NODE_FACTORY=,SUPPORT_CLASS_VISIBILITY_PUBLIC=true */
package com.orientechnologies.orient.core.sql.parser;

import com.orientechnologies.orient.core.command.OCommandContext;
import com.orientechnologies.orient.core.exception.OCommandExecutionException;
import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.metadata.schema.OSchema;
import com.orientechnologies.orient.core.sql.executor.OInternalResultSet;
import com.orientechnologies.orient.core.sql.executor.OResultInternal;
import com.orientechnologies.orient.core.sql.executor.OTodoResultSet;

import java.util.Map;

public class ODropClassStatement extends ODDLStatement {

  public OIdentifier name;
  public boolean ifExists = false;
  public boolean unsafe   = false;

  public ODropClassStatement(int id) {
    super(id);
  }

  public ODropClassStatement(OrientSql p, int id) {
    super(p, id);
  }

  @Override public OTodoResultSet executeDDL(OCommandContext ctx) {
    OSchema schema = ctx.getDatabase().getMetadata().getSchema();
    OClass clazz = schema.getClass(name.getStringValue());
    if (clazz == null) {
      if (ifExists) {
        return new OInternalResultSet();
      }
      throw new OCommandExecutionException("Class " + name.getStringValue() + " does not exist");
    }

    if (!unsafe && clazz.count() > 0) {
      //check vertex or edge
      if (clazz.isVertexType()) {
        throw new OCommandExecutionException("'DROP CLASS' command cannot drop class '" + name.getStringValue()
            + "' because it contains Vertices. Use 'DELETE VERTEX' command first to avoid broken edges in a database, or apply the 'UNSAFE' keyword to force it");
      } else if (clazz.isEdgeType()) {
        // FOUND EDGE CLASS
        throw new OCommandExecutionException("'DROP CLASS' command cannot drop class '" + name.getStringValue()
            + "' because it contains Edges. Use 'DELETE EDGE' command first to avoid broken vertices in a database, or apply the 'UNSAFE' keyword to force it");
      }
    }

    schema.dropClass(name.getStringValue());

    OInternalResultSet rs = new OInternalResultSet();
    OResultInternal result = new OResultInternal();
    result.setProperty("operation", "drop class");
    result.setProperty("className", name.getStringValue());
    rs.add(result);
    return rs;
  }

  @Override public void toString(Map<Object, Object> params, StringBuilder builder) {
    builder.append("DROP CLASS ");
    name.toString(params, builder);
    if (ifExists) {
      builder.append(" IF EXISTS");
    }
    if (unsafe) {
      builder.append(" UNSAFE");
    }
  }

  @Override public ODropClassStatement copy() {
    ODropClassStatement result = new ODropClassStatement(-1);
    result.name = name == null ? null : name.copy();
    result.ifExists = ifExists;
    result.unsafe = unsafe;
    return result;
  }

  @Override public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    ODropClassStatement that = (ODropClassStatement) o;

    if (unsafe != that.unsafe)
      return false;
    if(ifExists != that.ifExists)
      return false;
    if (name != null ? !name.equals(that.name) : that.name != null)
      return false;

    return true;
  }

  @Override public int hashCode() {
    int result = name != null ? name.hashCode() : 0;
    result = 31 * result + (unsafe ? 1 : 0);
    return result;
  }
}
/* JavaCC - OriginalChecksum=8c475e1225074f68be37fce610987d54 (do not edit this line) */
