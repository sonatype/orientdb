/*
 *
 *  *  Copyright 2010-2016 OrientDB LTD (http://orientdb.com)
 *  *
 *  *  Licensed under the Apache License, Version 2.0 (the "License");
 *  *  you may not use this file except in compliance with the License.
 *  *  You may obtain a copy of the License at
 *  *
 *  *       http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  *  Unless required by applicable law or agreed to in writing, software
 *  *  distributed under the License is distributed on an "AS IS" BASIS,
 *  *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *  See the License for the specific language governing permissions and
 *  *  limitations under the License.
 *  *
 *  * For more information: http://orientdb.com
 *
 */
package com.orientechnologies.orient.core.db.record;

import java.io.Serializable;
import java.util.*;

import com.orientechnologies.orient.core.record.ORecord;
import com.orientechnologies.orient.core.record.ORecordInternal;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.record.impl.ODocumentInternal;

/**
 * Implementation of Set bound to a source ORecord object to keep track of changes. This avoid to call the makeDirty() by hand when
 * the set is changed.
 * 
 * @author Luca Garulli (l.garulli--(at)--orientdb.com)
 * 
 */
@SuppressWarnings("serial")
public class OTrackedSet<T> extends HashSet<T> implements ORecordElement, OTrackedMultiValue<T, T>, Serializable {
  protected final ORecord                       sourceRecord;
  private final boolean                         embeddedCollection;
  protected Class<?>                            genericClass;
  private STATUS                                status          = STATUS.NOT_LOADED;
  private List<OMultiValueChangeListener<T, T>> changeListeners;

  public OTrackedSet(final ORecord iRecord, final Collection<? extends T> iOrigin, final Class<?> cls) {
    this(iRecord);
    genericClass = cls;
    if (iOrigin != null && !iOrigin.isEmpty())
      addAll(iOrigin);
  }

  public OTrackedSet(final ORecord iSourceRecord) {
    this.sourceRecord = iSourceRecord;
    embeddedCollection = this.getClass().equals(OTrackedSet.class);
  }

  @Override
  public ORecordElement getOwner() {
    return sourceRecord;
  }

  @Override
  public Iterator<T> iterator() {
    return new Iterator<T>() {
      private final Iterator<T> underlying = OTrackedSet.super.iterator();

      @Override
      public boolean hasNext() {
        return underlying.hasNext();
      }

      @Override
      public T next() {
        return underlying.next();
      }

      @Override
      public void remove() {
        underlying.remove();
        setDirty();
      }
    };
  }

  public boolean add(final T e) {
    if (super.add(e)) {
      addOwnerToEmbeddedDoc(e);

      fireCollectionChangedEvent(new OMultiValueChangeEvent<T, T>(OMultiValueChangeEvent.OChangeType.ADD, e, e));
      addNested(e);
      return true;
    }

    return false;
  }

  private void addNested(T element) {
    if (element instanceof OTrackedMultiValue) {
      ((OTrackedMultiValue) element)
          .addChangeListener(new ONestedValueChangeListener((ODocument) sourceRecord, this, (OTrackedMultiValue) element));
    }
  }


  @SuppressWarnings("unchecked")
  @Override
  public boolean remove(final Object o) {
    if (super.remove(o)) {
      if (o instanceof ODocument)
        ODocumentInternal.removeOwner((ODocument) o, this);

      fireCollectionChangedEvent(new OMultiValueChangeEvent<T, T>(OMultiValueChangeEvent.OChangeType.REMOVE, (T) o, null, (T) o));
      removeNested(o);
      return true;
    }
    return false;
  }

  @Override
  public void clear() {
    final Set<T> origValues;
    if (changeListeners == null )
      origValues = null;
    else
      origValues = new HashSet<T>(this);

    if (origValues == null) {
      for (final T item : this) {
        if (item instanceof ODocument)
          ODocumentInternal.removeOwner((ODocument) item, this);
      }
    }

    super.clear();

    if (origValues != null) {
      for (final T item : origValues) {
        if (item instanceof ODocument)
          ODocumentInternal.removeOwner((ODocument) item, this);

        fireCollectionChangedEvent(new OMultiValueChangeEvent<T, T>(OMultiValueChangeEvent.OChangeType.REMOVE, item, null, item));
        removeNested(item);
      }

    } else
      setDirty();
  }


  private void removeNested(Object element){
    if(element instanceof OTrackedMultiValue){
      //      ((OTrackedMultiValue) element).removeRecordChangeListener(null);
    }
  }

  @SuppressWarnings("unchecked")
  public OTrackedSet<T> setDirty() {
    if (status != STATUS.UNMARSHALLING && sourceRecord != null
        && !(sourceRecord.isDirty() && ORecordInternal.isContentChanged(sourceRecord)))
      sourceRecord.setDirty();
    return this;
  }

  @Override
  public void setDirtyNoChanged() {
    if (status != STATUS.UNMARSHALLING && sourceRecord != null)
      sourceRecord.setDirtyNoChanged();
  }

  public STATUS getInternalStatus() {
    return status;
  }

  public void setInternalStatus(final STATUS iStatus) {
    status = iStatus;
  }

  public void addChangeListener(final OMultiValueChangeListener<T, T> changeListener) {
    if(changeListeners == null)
      changeListeners = new LinkedList<OMultiValueChangeListener<T, T>>();
    changeListeners.add(changeListener);
  }

  public void removeRecordChangeListener(final OMultiValueChangeListener<T, T> changeListener) {
    if (changeListeners != null)
      changeListeners.remove(changeListener);
  }

  public Set<T> returnOriginalState(final List<OMultiValueChangeEvent<T, T>> multiValueChangeEvents) {
    final Set<T> reverted = new HashSet<T>(this);

    final ListIterator<OMultiValueChangeEvent<T, T>> listIterator = multiValueChangeEvents.listIterator(multiValueChangeEvents
        .size());

    while (listIterator.hasPrevious()) {
      final OMultiValueChangeEvent<T, T> event = listIterator.previous();
      switch (event.getChangeType()) {
      case ADD:
        reverted.remove(event.getKey());
        break;
      case REMOVE:
        reverted.add(event.getOldValue());
        break;
      default:
        throw new IllegalArgumentException("Invalid change type : " + event.getChangeType());
      }
    }

    return reverted;
  }

  public Class<?> getGenericClass() {
    return genericClass;
  }

  public void setGenericClass(Class<?> genericClass) {
    this.genericClass = genericClass;
  }

  public void fireCollectionChangedEvent(final OMultiValueChangeEvent<T, T> event) {
    if (status == STATUS.UNMARSHALLING)
      return;

    setDirty();
    if (changeListeners != null) {
      for (final OMultiValueChangeListener<T, T> changeListener : changeListeners) {
        if (changeListener != null)
          changeListener.onAfterRecordChanged(event);
      }
    }
  }

  private void addOwnerToEmbeddedDoc(T e) {
    if (embeddedCollection && e instanceof ODocument && !((ODocument) e).getIdentity().isValid()) {
      ODocumentInternal.addOwner((ODocument) e, this);
      ORecordInternal.track(sourceRecord, (ODocument) e);
    }
  }

  private Object writeReplace() {
    return new HashSet<T>(this);
  }

  @Override
  public void replace(OMultiValueChangeEvent<Object, Object> event, Object newValue) {
    super.remove(event.getKey());
    super.add((T) newValue);
    addNested((T) newValue);
  }
}
