/* Generated By:JJTree: Do not edit this line. ORevokeStatement.java Version 4.3 */
/* JavaCCOptions:MULTI=true,NODE_USES_PARSER=false,VISITOR=true,TRACK_TOKENS=true,NODE_PREFIX=O,NODE_EXTENDS=,NODE_FACTORY=,SUPPORT_CLASS_VISIBILITY_PUBLIC=true */
package com.orientechnologies.orient.core.sql.parser;

import com.orientechnologies.orient.core.command.OCommandContext;
import com.orientechnologies.orient.core.exception.OCommandExecutionException;
import com.orientechnologies.orient.core.metadata.security.ORole;
import com.orientechnologies.orient.core.sql.executor.OInternalResultSet;
import com.orientechnologies.orient.core.sql.executor.OResultInternal;
import com.orientechnologies.orient.core.sql.executor.OTodoResultSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ORevokeStatement extends OSimpleExecStatement {

  protected OPermission permission;
  protected List<OResourcePathItem> resourceChain = new ArrayList<OResourcePathItem>();
  protected OIdentifier actor;

  public ORevokeStatement(int id) {
    super(id);
  }

  public ORevokeStatement(OrientSql p, int id) {
    super(p, id);
  }

  @Override public OTodoResultSet executeSimple(OCommandContext ctx) {
    ORole role = getDatabase().getMetadata().getSecurity().getRole(actor.getStringValue());
    if (role == null)
      throw new OCommandExecutionException("Invalid role: " + actor.getStringValue());

    String resourcePath = toResourcePath(resourceChain, ctx);
    role.revoke(resourcePath, toPrivilege(permission.permission));
    role.save();

    OInternalResultSet rs = new OInternalResultSet();
    OResultInternal result = new OResultInternal();
    result.setProperty("operation", "grant");
    result.setProperty("role", actor.getStringValue());
    result.setProperty("permission", permission.toString());
    result.setProperty("resource", resourcePath);
    rs.add(result);
    return rs;
  }

  private String toResourcePath(List<OResourcePathItem> resourceChain, OCommandContext ctx) {
    Map<Object, Object> params = ctx.getInputParameters();
    StringBuilder builder = new StringBuilder();
    boolean first = true;
    for (OResourcePathItem res : resourceChain) {
      if (!first) {
        builder.append(".");
      }
      res.toString(params, builder);
      first = false;
    }
    return builder.toString();
  }

  protected int toPrivilege(String privilegeName) {
    int privilege;
    if ("CREATE".equals(privilegeName))
      privilege = ORole.PERMISSION_CREATE;
    else if ("READ".equals(privilegeName))
      privilege = ORole.PERMISSION_READ;
    else if ("UPDATE".equals(privilegeName))
      privilege = ORole.PERMISSION_UPDATE;
    else if ("DELETE".equals(privilegeName))
      privilege = ORole.PERMISSION_DELETE;
    else if ("EXECUTE".equals(privilegeName))
      privilege = ORole.PERMISSION_EXECUTE;
    else if ("ALL".equals(privilegeName))
      privilege = ORole.PERMISSION_ALL;
    else if ("NONE".equals(privilegeName))
      privilege = ORole.PERMISSION_NONE;
    else
      throw new OCommandExecutionException("Unrecognized privilege '" + privilegeName + "'");
    return privilege;
  }

  @Override public void toString(Map<Object, Object> params, StringBuilder builder) {
    builder.append("REVOKE ");
    permission.toString(params, builder);
    builder.append(" ON ");
    boolean first = true;
    for (OResourcePathItem res : resourceChain) {
      if (!first) {
        builder.append(".");
      }
      res.toString(params, builder);
      first = false;
    }
    builder.append(" FROM ");
    actor.toString(params, builder);
  }

  @Override public ORevokeStatement copy() {
    ORevokeStatement result = new ORevokeStatement(-1);
    result.permission = permission == null ? null : permission.copy();
    result.resourceChain =
        resourceChain == null ? null : resourceChain.stream().map(OResourcePathItem::copy).collect(Collectors.toList());
    result.actor = actor == null ? null : actor.copy();
    return result;
  }

  @Override public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    ORevokeStatement that = (ORevokeStatement) o;

    if (permission != null ? !permission.equals(that.permission) : that.permission != null)
      return false;
    if (resourceChain != null ? !resourceChain.equals(that.resourceChain) : that.resourceChain != null)
      return false;
    if (actor != null ? !actor.equals(that.actor) : that.actor != null)
      return false;

    return true;
  }

  @Override public int hashCode() {
    int result = permission != null ? permission.hashCode() : 0;
    result = 31 * result + (resourceChain != null ? resourceChain.hashCode() : 0);
    result = 31 * result + (actor != null ? actor.hashCode() : 0);
    return result;
  }
}
/* JavaCC - OriginalChecksum=d483850d10e1562c1b942fcc249278eb (do not edit this line) */
