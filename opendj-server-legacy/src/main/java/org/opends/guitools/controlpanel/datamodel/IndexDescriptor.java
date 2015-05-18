/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2015 ForgeRock AS
 */
package org.opends.guitools.controlpanel.datamodel;

import static org.opends.server.backends.pluggable.SuffixContainer.*;

import java.util.Collections;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.opends.server.types.AttributeType;

/**
 * The class used to describe the index configuration (the normal index: the one
 * used to improve search performance on a given attribute).
 */
public class IndexDescriptor extends AbstractIndexDescriptor
{

  private static final String[] DATABASE_INDEXES = new String[] {
    DN2ID_INDEX_NAME, ID2CHILDREN_COUNT_NAME, ID2CHILDREN_INDEX_NAME, ID2SUBTREE_INDEX_NAME };

  private final SortedSet<IndexTypeDescriptor> types = new TreeSet<>();
  private final boolean isDatabaseIndex;
  private final int entryLimit;
  private final AttributeType attr;
  private int hashCode;

  /**
   * Constructor of the index descriptor.
   *
   * @param indexName
   *          name of the index.
   */
  public IndexDescriptor(String indexName)
  {
    this(indexName, null, null, Collections.EMPTY_SET, -1);
  }

  /**
   * Constructor of the index descriptor.
   *
   * @param name
   *          name of the index.
   * @param attr
   *          the attribute type associated with the index attribute.
   * @param backend
   *          the backend where the index is defined.
   * @param types
   *          the type of indexes (equality, substring, etc.).
   * @param entryLimit
   *          the entry limit for the index.
   */
  public IndexDescriptor(
      String name, AttributeType attr, BackendDescriptor backend, Set<IndexTypeDescriptor> types, int entryLimit)
  {
    super(name, backend);
    this.attr = attr;
    this.types.addAll(types);
    isDatabaseIndex = isDatabaseIndex(name);
    this.entryLimit = entryLimit;
    recalculateHashCode();
  }

  /**
   * Returns the attribute type associated with the index attribute.
   *
   * @return the attribute type associated with the index attribute.
   */
  public AttributeType getAttributeType()
  {
    return attr;
  }

  @Override
  public int compareTo(AbstractIndexDescriptor o)
  {
    return getName().toLowerCase().compareTo(o.getName().toLowerCase());
  }

  @Override
  public int hashCode()
  {
    return hashCode;
  }

  /**
   * Returns the type of indexes (equality, substring, etc.).
   *
   * @return the type of indexes (equality, substring, etc.).
   */
  public SortedSet<IndexTypeDescriptor> getTypes()
  {
    return new TreeSet<>(types);
  }

  /**
   * Tells whether this is a database index or not. Database indexes are not
   * modifiable and for internal use only.
   *
   * @return <CODE>true</CODE> if this is a database index and
   *         <CODE>false</CODE> otherwise.
   */
  public boolean isDatabaseIndex()
  {
    return isDatabaseIndex;
  }

  /**
   * Tells whether the provide index name corresponds to a database index or
   * not. Database indexes are not modifiable and for internal use only.
   *
   * @return <CODE>true</CODE> if the provide index name corresponds to a
   *         database index and <CODE>false</CODE> otherwise.
   */
  private boolean isDatabaseIndex(final String indexName)
  {
    for (final String dbIndex : DATABASE_INDEXES)
    {
      if (indexName.equalsIgnoreCase(dbIndex))
      {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean equals(Object o)
  {
    if (o == this)
    {
      return true;
    }
    if (!(o instanceof IndexDescriptor))
    {
      return false;
    }
    final IndexDescriptor index = (IndexDescriptor)o;
    return index.getName().equalsIgnoreCase(getName())
        && index.isDatabaseIndex() == isDatabaseIndex()
        && index.getTypes().equals(getTypes())
        && index.getEntryLimit() == getEntryLimit()
        && backendIdEqual(index);
  }

  private boolean backendIdEqual(IndexDescriptor index)
  {
    BackendDescriptor backend1 = getBackend();
    BackendDescriptor backend2 = index.getBackend();
    return backend1 != null && backend2 != null && backend1.getBackendID().equals(backend2.getBackendID());
  }

  /**
   * Returns the entry limit of the index.
   *
   * @return the entry limit of the index.
   */
  public int getEntryLimit()
  {
    return entryLimit;
  }

  @Override
  protected void recalculateHashCode()
  {
    final StringBuilder sb = new StringBuilder();
    for (final IndexTypeDescriptor t : types)
    {
      sb.append(t).append(",");
    }
    if (getBackend() != null)
    {
      sb.append(getBackend().getBackendID());
    }
    hashCode = (getName()+sb+entryLimit).hashCode();
  }
}
