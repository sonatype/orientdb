package com.orientechnologies.orient.core.index.engine;

import com.orientechnologies.common.serialization.types.OBinarySerializer;
import com.orientechnologies.orient.core.encryption.OEncryption;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.metadata.schema.OType;

public interface OV1IndexEngine extends OBaseIndexEngine {
  int API_VERSION = 1;

  void put(Object key, ORID value);

  @Override
  default int getEngineAPIVersion() {
    return API_VERSION;
  }

  void load(final String name, final int keySize, final OType[] keyTypes, final OBinarySerializer keySerializer,
      final OEncryption encryption);
}
