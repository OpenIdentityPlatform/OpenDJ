/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.guitools.controlpanel.datamodel;

import java.util.SortedSet;
import java.util.TreeSet;

import org.opends.server.admin.std.meta.LocalDBIndexCfgDefn.IndexType;
import org.opends.server.types.AttributeType;

/**
 * The class used to describe the index configuration (the normal index: the
 * one used to improve search performance on a given attribute).
 *
 */
public class IndexDescriptor extends AbstractIndexDescriptor
{

  private SortedSet<IndexType> types = new TreeSet<IndexType>();
  private boolean isDatabaseIndex;
  private int entryLimit;
  private AttributeType attr;
  private int hashCode;

  /**
   * Constructor of the index.
   * @param name name of the index.
   * @param attr the attribute type associated with the index attribute.
   * @param backend the backend where the index is defined.
   * @param types the type of indexes (equality, substring, etc.).
   * @param entryLimit the entry limit for the index.
   */
  public IndexDescriptor(String name, AttributeType attr,
      BackendDescriptor backend,
      SortedSet<IndexType> types, int entryLimit)
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
   * @return the attribute type associated with the index attribute.
   */
  public AttributeType getAttributeType()
  {
    return attr;
  }

  /**
   * {@inheritDoc}
   */
  public int compareTo(Object o)
  {
    int returnValue = -1;
    if (o instanceof AbstractIndexDescriptor)
    {
      AbstractIndexDescriptor index = (AbstractIndexDescriptor)o;
      returnValue = getName().compareTo(index.getName());
    }
    return returnValue;
  }

  /**
   * {@inheritDoc}
   */
  public int hashCode()
  {
    return hashCode;
  }

  /**
   * Returns the type of indexes (equality, substring, etc.).
   * @return the type of indexes (equality, substring, etc.).
   */
  public SortedSet<IndexType> getTypes()
  {
    return new TreeSet<IndexType>(types);
  }

  /**
   * Tells whether this is a database index or not.  Database indexes are not
   * modifiable and for internal use only.
   * @return <CODE>true</CODE> if this is a database index and
   * <CODE>false</CODE> otherwise.
   */
  public boolean isDatabaseIndex()
  {
    return isDatabaseIndex;
  }

  /**
   * Tells whether the provide index name corresponds to a database index or
   * not.  Database indexes are not modifiable and for internal use only.
   * @return <CODE>true</CODE> if the provide index name corresponds to a
   * database index and <CODE>false</CODE> otherwise.
   */
  private boolean isDatabaseIndex(String name)
  {
    return name.equalsIgnoreCase("dn2id") ||
    name.equalsIgnoreCase("id2children") ||
    name.equalsIgnoreCase("id2subtree");
  }

  /**
   * {@inheritDoc}
   */
  public boolean equals(Object o)
  {
    boolean equals = o == this;
    if (!equals)
    {
      equals = o instanceof IndexDescriptor;
      if (equals)
      {
        IndexDescriptor index = (IndexDescriptor)o;
        equals = index.getName().equalsIgnoreCase(getName()) &&
          index.isDatabaseIndex() == isDatabaseIndex() &&
          index.getTypes().equals(getTypes()) &&
          index.getEntryLimit() == getEntryLimit();

        if (equals)
        {
          if ((getBackend() != null) && (index.getBackend() != null))
          {
            // Only compare the backend IDs.  In this context is enough
            equals = getBackend().getBackendID().equals(
                index.getBackend().getBackendID());
          }
        }
      }
    }
    return equals;
  }

  /**
   * Returns the entry limit of the index.
   * @return the entry limit of the index.
   */
  public int getEntryLimit()
  {
    return entryLimit;
  }

  /**
   * {@inheritDoc}
   */
  protected void recalculateHashCode()
  {
    StringBuilder sb = new StringBuilder();
    for (IndexType t : types)
    {
      sb.append(t+",");
    }
    if (getBackend() != null)
    {
      sb.append(getBackend().getBackendID());
    }
    hashCode = (getName()+sb+entryLimit).hashCode();
  }
}
