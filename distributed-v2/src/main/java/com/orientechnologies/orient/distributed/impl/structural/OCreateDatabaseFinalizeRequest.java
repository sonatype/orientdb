package com.orientechnologies.orient.distributed.impl.structural;

import com.orientechnologies.orient.distributed.OrientDBDistributed;
import com.orientechnologies.orient.distributed.impl.coordinator.OLogId;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static com.orientechnologies.orient.distributed.impl.coordinator.OCoordinateMessagesFactory.CREATE_DATABASE_FINALIZE_REQUEST;

public class OCreateDatabaseFinalizeRequest implements OStructuralNodeRequest {

  private boolean success;
  private String  database;

  public OCreateDatabaseFinalizeRequest() {

  }

  public OCreateDatabaseFinalizeRequest(boolean success, String database) {
    this.success = success;
    this.database = database;
  }

  @Override
  public OStructuralNodeResponse execute(OStructuralDistributedMember nodeFrom, OLogId opId,
      OStructuralDistributedExecutor executor, OrientDBDistributed context) {
    if (success) {
      context.finalizeCreateDatabase(database);
    } else {
      context.internalDropDatabase(database);
    }
    return new OCreateDatabaseFinalizeResponse();
  }

  @Override
  public void serialize(DataOutput output) throws IOException {
    output.writeBoolean(success);
    output.writeUTF(database);
  }

  @Override
  public void deserialize(DataInput input) throws IOException {
    this.success = input.readBoolean();
    this.database = input.readUTF();
  }

  @Override
  public int getRequestType() {
    return CREATE_DATABASE_FINALIZE_REQUEST;
  }
}
