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

import org.opends.server.types.DN;


/**
 * This class is used to represent a Base DN / Replica and is aimed to be
 * used by the classes in the BackendTableModel class.
 *
 */
public class BaseDNDescriptor implements Comparable
{
  /**
   * An enumeration describing the type of base DN for a given backend.
   */
  public enum Type
  {
    /**
     * The base DN is not replicated.
     */
    NOT_REPLICATED,
    /**
     * The base DN is replicated.
     */
    REPLICATED
  };

  private int nEntries;
  private int missingChanges;
  private BackendDescriptor backend;
  private long ageOfOldestMissingChange;
  private Type type;
  private DN baseDn;
  private int replicaID = -1;

  private int hashCode;

  /**
   * Constructor for this class.
   * @param type the type of replication.
   * @param baseDn the base DN associated with the Replication.
   * @param backend the backend containing this base DN.
   * @param nEntries the number of entries for the base DN.
   * @param ageOfOldestMissingChange the number of missing changes.
   * @param missingChanges the number of missing changes.
   */
  public BaseDNDescriptor(Type type, DN baseDn, BackendDescriptor backend,
      int nEntries, long ageOfOldestMissingChange, int missingChanges)
  {
    this.baseDn = baseDn;
    this.backend = backend;
    this.type = type;
    this.nEntries = nEntries;
    this.ageOfOldestMissingChange = ageOfOldestMissingChange;
    this.missingChanges = missingChanges;

    if (backend != null)
    {
      recalculateHashCode();
    }
  }

  /**
   * Return the String DN associated with the base DN..
   * @return the String DN associated with the base DN.
   */
  public DN getDn()
  {
    return baseDn;
  }

  /**
   * {@inheritDoc}
   */
  public boolean equals(Object v)
  {
    boolean equals = false;
    if (this != v)
    {
      if (v instanceof BaseDNDescriptor)
      {
        BaseDNDescriptor desc = (BaseDNDescriptor)v;
        equals = (getType() == desc.getType()) &&
        getDn().equals(desc.getDn()) &&
        (getAgeOfOldestMissingChange() == desc.getAgeOfOldestMissingChange()) &&
        (getMissingChanges() == desc.getMissingChanges()) &&
        getBackend().getBackendID().equals(
            desc.getBackend().getBackendID()) &&
        (getEntries() == desc.getEntries());
      }
    }
    else
    {
      equals = true;
    }
    return equals;
  }

  /**
   * {@inheritDoc}
   */
  public int hashCode()
  {
    return hashCode;
  }

  /**
   * {@inheritDoc}
   */
  public int compareTo(Object o)
  {
    int returnValue = -1;
    if (o instanceof BaseDNDescriptor)
    {
      BaseDNDescriptor desc = (BaseDNDescriptor)o;
      returnValue = desc.getDn().compareTo(getDn());
    }
    return returnValue;
  }

  /**
   * Returns the number of entries in the backend for this base DN.
   * @return the number of entries in the backend for this base DN.
   */
  public int getEntries()
  {
    return nEntries;
  }

  /**
   * Returns the number of missing changes in the replication topology for
   * this base DN.
   * @return the number of missing changes in the replication topology for
   * this base DN.
   */
  public int getMissingChanges()
  {
    return missingChanges;
  }

  /**
   * Sets the number of missing changes in the replication topology for
   * this base DN.
   * @param missingChanges the missing changes.
   */
  public void setMissingChanges(int missingChanges)
  {
    this.missingChanges = missingChanges;
    recalculateHashCode();
  }

  /**
   * Returns the age of the oldest missing change in seconds in the
   * replication topology for this base DN.
   * @return the age of the oldest missing change in seconds in the
   * replication topology for this base DN.
   */
  public long getAgeOfOldestMissingChange()
  {
    return ageOfOldestMissingChange;
  }

  /**
   * Sets the age of the oldest missing change in seconds in the
   * replication topology for this base DN.
   * @param ageOfOldestMissingChange the age of the oldest missing change in
   * seconds in the replication topology for this base DN.
   */
  public void setAgeOfOldestMissingChange(long ageOfOldestMissingChange)
  {
    this.ageOfOldestMissingChange = ageOfOldestMissingChange;
    recalculateHashCode();
  }

  /**
   * Returns the type for this base DN.
   * @return the type for this base DN.
   */
  public Type getType()
  {
    return type;
  }

  /**
   * Returns the backend where this base DN is defined.
   * @return the backend where this base DN is defined.
   */
  public BackendDescriptor getBackend()
  {
    return backend;
  }


  /**
   * Sets the backend of this base DN.
   * @param backend the backend for this base DN.
   */
  public void setBackend(BackendDescriptor backend)
  {
    this.backend = backend;
    recalculateHashCode();
  }

  /**
   * Sets the type of this base DN.
   * @param type the new type for this base DN.
   */
  public void setType(Type type)
  {
    this.type = type;
    recalculateHashCode();
  }

  /**
   * Sets the number of entries for this base DN in this database.
   * @param nEntries the number of entries.
   */
  public void setEntries(int nEntries)
  {
    this.nEntries = nEntries;
    recalculateHashCode();
  }

  /**
   * Returns the ID of the replication domain associated with this base DN. -1
   * if this base DN is not replicated.
   * @return the ID of the replication domain associated with this base DN.
   */
  public int getReplicaID()
  {
    return replicaID;
  }

  /**
   * Sets the ID of the replication domain associated with this base DN.
   * @param replicaID the ID of the replication domain associated with this base
   * DN.
   */
  public void setReplicaID(int replicaID)
  {
    this.replicaID = replicaID;
    recalculateHashCode();
  }

  /**
   * Method called when one of the elements that affect the value of the
   * hashcode is modified.  It is used to minimize the time spent calculating
   * hashCode.
   *
   */
  private void recalculateHashCode()
  {
    hashCode = (getType().toString() + getAgeOfOldestMissingChange() +
          getDn() +
        getBackend().getBackendID() + getMissingChanges()).hashCode();
  }
}
