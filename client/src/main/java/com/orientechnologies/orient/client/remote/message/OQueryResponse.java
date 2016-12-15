package com.orientechnologies.orient.client.remote.message;

import com.orientechnologies.orient.client.remote.OBinaryResponse;
import com.orientechnologies.orient.client.remote.OStorageRemoteSession;
import com.orientechnologies.orient.core.db.ODatabaseRecordThreadLocal;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentRemote;
import com.orientechnologies.orient.core.record.ORecord;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.record.impl.OEdgeDelegate;
import com.orientechnologies.orient.core.record.impl.ORecordBytes;
import com.orientechnologies.orient.core.record.impl.OVertexDelegate;
import com.orientechnologies.orient.core.serialization.serializer.result.binary.OResultSerializerNetwork;
import com.orientechnologies.orient.core.sql.executor.OExecutionPlan;
import com.orientechnologies.orient.core.sql.executor.OResult;
import com.orientechnologies.orient.core.sql.executor.OResultInternal;
import com.orientechnologies.orient.core.sql.executor.OTodoResultSet;
import com.orientechnologies.orient.core.sql.parser.OLocalResultSetLifecycleDecorator;
import com.orientechnologies.orient.enterprise.channel.binary.OChannelBinaryProtocol;
import com.orientechnologies.orient.enterprise.channel.binary.OChannelDataInput;
import com.orientechnologies.orient.enterprise.channel.binary.OChannelDataOutput;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

/**
 * Created by luigidellaquila on 01/12/16.
 */
public class OQueryResponse implements OBinaryResponse {

  private static final byte RECORD_TYPE_BLOB       = 0;
  private static final byte RECORD_TYPE_VERTEX     = 1;
  private static final byte RECORD_TYPE_EDGE       = 2;
  private static final byte RECORD_TYPE_ELEMENT    = 3;
  private static final byte RECORD_TYPE_PROJECTION = 4;

  private OTodoResultSet result;

  public OQueryResponse() {
  }

  @Override public void write(OChannelDataOutput channel, int protocolVersion, String recordSerializer) throws IOException {
    if (!(result instanceof OLocalResultSetLifecycleDecorator)) {
      throw new IllegalStateException();
    }
    channel.writeString(((OLocalResultSetLifecycleDecorator) result).getQueryId());

    writeExecutionPlan(result.getExecutionPlan(), channel);
    while (result.hasNext()) {
      OResult row = result.next();
      channel.writeBoolean(true);
      writeResult(row, channel, recordSerializer);
    }
    channel.writeBoolean(false);
    channel.writeBoolean(((OLocalResultSetLifecycleDecorator) result).hasNextPage());
    writeQueryStats(result.getQueryStats(), channel);
  }

  @Override public void read(OChannelDataInput network, OStorageRemoteSession session) throws IOException {
    String queryId = network.readString();
    ORemoteResultSet rs = new ORemoteResultSet((ODatabaseDocumentRemote) ODatabaseRecordThreadLocal.INSTANCE.get(), queryId);
    rs.setExecutionPlan(readExecutionPlan(network));
    boolean hasNext = network.readBoolean();
    while (hasNext) {
      OResult item = readResult(network);
      rs.add(item);
      hasNext = network.readBoolean();
    }
    rs.setHasNextPage(network.readBoolean());
    rs.setQueryStats(readQueryStats(rs));
    this.result = rs;
  }

  private void writeQueryStats(Map<String, Object> queryStats, OChannelDataOutput channel) {
    //TODO
  }

  private Map<String, Object> readQueryStats(ORemoteResultSet rs) {
    return null; //TODO
  }

  private void writeExecutionPlan(Optional<OExecutionPlan> executionPlan, OChannelDataOutput channel) {
    //TODO
  }

  private OExecutionPlan readExecutionPlan(OChannelDataInput network) {
    return null; //TODO
  }

  private void writeResult(OResult row, OChannelDataOutput channel, String recordSerializer) throws IOException {
    if (row.isBlob()) {
      writeBlob(row, channel, recordSerializer);
    } else if (row.isVertex()) {
      writeVertex(row, channel, recordSerializer);
    } else if (row.isEdge()) {
      writeEdge(row, channel, recordSerializer);
    } else if (row.isElement()) {
      writeElement(row, channel, recordSerializer);
    } else {
      writeProjection(row, channel);
    }
  }

  private void writeElement(OResult row, OChannelDataOutput channel, String recordSerializer) throws IOException {
    channel.writeByte(RECORD_TYPE_ELEMENT);
    writeDocument(channel, row.getElement().get().getRecord(), recordSerializer);
  }

  private void writeEdge(OResult row, OChannelDataOutput channel, String recordSerializer) throws IOException {
    channel.writeByte(RECORD_TYPE_EDGE);
    writeDocument(channel, row.getElement().get().getRecord(), recordSerializer);
  }

  private void writeVertex(OResult row, OChannelDataOutput channel, String recordSerializer) throws IOException {
    channel.writeByte(RECORD_TYPE_VERTEX);
    writeDocument(channel, row.getElement().get().getRecord(), recordSerializer);
  }

  private void writeBlob(OResult row, OChannelDataOutput channel, String recordSerializer) throws IOException {
    channel.writeByte(RECORD_TYPE_BLOB);
    row.getBlob().get().toOutputStream(channel.getDataOutput());
  }

  private OResult readBlob(OChannelDataInput channel) throws IOException {
    ORecordBytes bytes = new ORecordBytes();
    bytes.fromInputStream(channel.getDataInput());
    OResultInternal result = new OResultInternal();
    result.setElement(bytes);
    return result;
  }

  private OResult readResult(OChannelDataInput channel) throws IOException {
    byte type = channel.readByte();
    switch (type) {
    case RECORD_TYPE_BLOB:
      return readBlob(channel);
    case RECORD_TYPE_VERTEX:
      return readVertex(channel);
    case RECORD_TYPE_EDGE:
      return readEdge(channel);
    case RECORD_TYPE_ELEMENT:
      return readElement(channel);
    case RECORD_TYPE_PROJECTION:
      return readProjection(channel);

    }
    return new OResultInternal();
  }

  private OResult readElement(OChannelDataInput channel) throws IOException {
    OResultInternal result = new OResultInternal();
    result.setElement(readDocument(channel));
    return result;
  }

  private OResult readVertex(OChannelDataInput channel) throws IOException {
    OResultInternal result = new OResultInternal();
    result.setElement(new OVertexDelegate((ODocument) readDocument(channel)));
    return result;
  }

  private OResult readEdge(OChannelDataInput channel) throws IOException {
    OResultInternal result = new OResultInternal();
    result.setElement(new OEdgeDelegate((ODocument) readDocument(channel)));
    return result;
  }

  private ORecord readDocument(OChannelDataInput channel) throws IOException {
    final ORecord record = (ORecord) OChannelBinaryProtocol.readIdentifiable(channel);
    return record;
  }

  private void writeDocument(OChannelDataOutput channel, ODocument doc, String serializer) throws IOException {
    OBinaryProtocolHelper.writeIdentifiable(channel, doc, serializer);
  }

  private OResult readProjection(OChannelDataInput channel) throws IOException {
    OResultSerializerNetwork ser = new OResultSerializerNetwork();
    return ser.fromStream(channel);
  }

  private void writeProjection(OResult item, OChannelDataOutput channel) throws IOException {
    channel.writeByte(RECORD_TYPE_PROJECTION);
    OResultSerializerNetwork ser = new OResultSerializerNetwork();
    ser.toStream(item, channel);
  }

  public void setResult(OLocalResultSetLifecycleDecorator result) {
    this.result = result;
  }

  public OTodoResultSet getResult() {
    return result;
  }
}
