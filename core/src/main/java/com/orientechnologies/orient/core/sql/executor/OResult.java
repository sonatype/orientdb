package com.orientechnologies.orient.core.sql.executor;

import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.record.OEdge;
import com.orientechnologies.orient.core.record.OElement;
import com.orientechnologies.orient.core.record.OVertex;
import com.orientechnologies.orient.core.record.impl.OBlob;

import java.util.Optional;
import java.util.Set;

/**
 * Created by luigidellaquila on 21/07/16.
 */
public interface OResult {
  <T> T getProperty(String name);

  Set<String> getPropertyNames();

  Optional<ORID> getIdentity();

  boolean isElement();

  Optional<OElement> getElement();

  OElement toElement();

  default boolean isVertex() {
    return getElement().map(x -> x.isVertex()).orElse(false);
  }

  default Optional<OVertex> getVertex() {
    Optional<OVertex> result = getElement().flatMap(x -> x.asVertex());
    return result;
  }

  default boolean isEdge() {
    return getElement().map(x -> x.isEdge()).orElse(false);
  }

  default Optional<OEdge> getEdge() {

    return getElement().flatMap(x -> x.asEdge());
  }

  boolean isBlob();

  Optional<OBlob> getBlob();

  /**
   * return metadata related to current result given a key
   * @param key the metadata key
   * @return metadata related to current result given a key
   */
  Object getMetadata(String key);

  /**
   * return all the metadata keys available
   * @return all the metadata keys available
   */
  Set<String> getMetadataKeys();

}
