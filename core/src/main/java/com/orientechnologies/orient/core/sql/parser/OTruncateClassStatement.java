/* Generated By:JJTree: Do not edit this line. OTruncateClassStatement.java Version 4.3 */
/* JavaCCOptions:MULTI=true,NODE_USES_PARSER=false,VISITOR=true,TRACK_TOKENS=true,NODE_PREFIX=O,NODE_EXTENDS=,NODE_FACTORY=,SUPPORT_CLASS_VISIBILITY_PUBLIC=true */
package com.orientechnologies.orient.core.sql.parser;

import com.orientechnologies.common.exception.OException;
import com.orientechnologies.orient.core.cache.OCommandCache;
import com.orientechnologies.orient.core.command.OCommandContext;
import com.orientechnologies.orient.core.db.ODatabase;
import com.orientechnologies.orient.core.exception.OCommandExecutionException;
import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.metadata.schema.OSchema;
import com.orientechnologies.orient.core.sql.executor.OInternalResultSet;
import com.orientechnologies.orient.core.sql.executor.OResultInternal;
import com.orientechnologies.orient.core.sql.executor.OResultSet;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

public class OTruncateClassStatement extends ODDLStatement {

  protected OIdentifier className;
  protected boolean polymorphic = false;
  protected boolean unsafe      = false;

  public OTruncateClassStatement(int id) {
    super(id);
  }

  public OTruncateClassStatement(OrientSql p, int id) {
    super(p, id);
  }

  @Override public OResultSet executeDDL(OCommandContext ctx) {
    ODatabase db = ctx.getDatabase();
    OSchema schema = db.getMetadata().getSchema();
    OClass clazz = schema.getClass(className.getStringValue());
    if (clazz == null) {
      throw new OCommandExecutionException("Schema Class not found: " + className);
    }

    final long recs = clazz.count(polymorphic);
    if (recs > 0 && !unsafe) {
      if (clazz.isSubClassOf("V")) {
        throw new OCommandExecutionException(
            "'TRUNCATE CLASS' command cannot be used on not empty vertex classes. Apply the 'UNSAFE' keyword to force it (at your own risk)");
      } else if (clazz.isSubClassOf("E")) {
        throw new OCommandExecutionException(
            "'TRUNCATE CLASS' command cannot be used on not empty edge classes. Apply the 'UNSAFE' keyword to force it (at your own risk)");
      }
    }


    OInternalResultSet rs = new OInternalResultSet();
    Collection<OClass> subclasses = clazz.getAllSubclasses();
    if (polymorphic && !unsafe) {// for multiple inheritance
      for (OClass subclass : subclasses) {
        long subclassRecs = clazz.count();
        if (subclassRecs > 0) {
          if (subclass.isSubClassOf("V")) {
            throw new OCommandExecutionException(
                "'TRUNCATE CLASS' command cannot be used on not empty vertex classes (" + subclass.getName()
                    + "). Apply the 'UNSAFE' keyword to force it (at your own risk)");
          } else if (subclass.isSubClassOf("E")) {
            throw new OCommandExecutionException(
                "'TRUNCATE CLASS' command cannot be used on not empty edge classes (" + subclass.getName()
                    + "). Apply the 'UNSAFE' keyword to force it (at your own risk)");
          }
        }
      }
    }

    try {
      clazz.truncate();
      OResultInternal result = new OResultInternal();
      result.setProperty("operation", "drop class");
      result.setProperty("className", className.getStringValue());
      rs.add(result);
      invalidateCommandCache(clazz, db);
      if (polymorphic) {
        for (OClass subclass : subclasses) {
          subclass.truncate();
          result = new OResultInternal();
          result.setProperty("operation", "truncate class");
          result.setProperty("className", className.getStringValue());
          rs.add(result);
          invalidateCommandCache(subclass, db);
        }
      }
    } catch (IOException e) {
      throw OException.wrapException(new OCommandExecutionException("Error on executing command"), e);
    }


    return rs;
  }

  private void invalidateCommandCache(OClass clazz, ODatabase db) {
    if (clazz == null) {
      return;
    }
    OCommandCache commandCache = db.getMetadata().getCommandCache();
    if (commandCache != null && commandCache.isEnabled()) {
      int[] clusterIds = clazz.getClusterIds();
      if (clusterIds != null) {
        for (int i : clusterIds) {
          String clusterName = getDatabase().getClusterNameById(i);
          if (clusterName != null) {
            commandCache.invalidateResultsOfCluster(clusterName);
          }
        }
      }
    }
  }

  /**
   * Accept the visitor.
   **/
  public Object jjtAccept(OrientSqlVisitor visitor, Object data) {
    return visitor.visit(this, data);
  }

  @Override public void toString(Map<Object, Object> params, StringBuilder builder) {
    builder.append("TRUNCATE CLASS " + className.toString());
    if (polymorphic) {
      builder.append(" POLYMORPHIC");
    }
    if (unsafe) {
      builder.append(" UNSAFE");
    }
  }

  @Override public OTruncateClassStatement copy() {
    OTruncateClassStatement result = new OTruncateClassStatement(-1);
    result.className = className == null ? null : className.copy();
    result.polymorphic = polymorphic;
    result.unsafe = unsafe;
    return result;
  }

  @Override public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    OTruncateClassStatement that = (OTruncateClassStatement) o;

    if (polymorphic != that.polymorphic)
      return false;
    if (unsafe != that.unsafe)
      return false;
    if (className != null ? !className.equals(that.className) : that.className != null)
      return false;

    return true;
  }

  @Override public int hashCode() {
    int result = className != null ? className.hashCode() : 0;
    result = 31 * result + (polymorphic ? 1 : 0);
    result = 31 * result + (unsafe ? 1 : 0);
    return result;
  }
}
/* JavaCC - OriginalChecksum=301f993f6ba2893cb30c8f189674b974 (do not edit this line) */
