/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2008-2011 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */

package org.opends.guitools.controlpanel.datamodel;

import org.forgerock.opendj.ldap.DN;


/**
 * This class is used to represent a Base DN / Replica and is aimed to be
 * used by the classes in the BackendTableModel class.
 */
public class BaseDNDescriptor implements Comparable<BaseDNDescriptor>
{
  /** An enumeration describing the type of base DN for a given backend. */
  public enum Type
  {
    /** The base DN is not replicated. */
    NOT_REPLICATED,
    /** The base DN is replicated. */
    REPLICATED,
    /** Replication is disabled. */
    DISABLED
  }

  private int nEntries;
  private int missingChanges;
  private BackendDescriptor backend;
  private long ageOfOldestMissingChange;
  private Type type;
  private final DN baseDn;
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

  @Override
  public boolean equals(Object v)
  {
    if (this == v)
    {
      return true;
    }
    if (!(v instanceof BaseDNDescriptor))
    {
      return false;
    }

    BaseDNDescriptor desc = (BaseDNDescriptor)v;
    return getType() == desc.getType()
        && getDn().equals(desc.getDn())
        && getAgeOfOldestMissingChange() == desc.getAgeOfOldestMissingChange()
        && getMissingChanges() == desc.getMissingChanges()
        && getEntries() == desc.getEntries()
        && backendIdEqual(desc);
  }

  private boolean backendIdEqual(BaseDNDescriptor desc)
  {
    // Only compare the backend IDs.  In this context is enough
    return getBackend() != null
        && desc.getBackend() != null
        && getBackend().getBackendID().equals(desc.getBackend().getBackendID());
  }

  @Override
  public int hashCode()
  {
    return hashCode;
  }

  @Override
  public int compareTo(BaseDNDescriptor desc)
  {
    int returnValue = desc.getDn().compareTo(getDn());
    if (returnValue == 0)
    {
      returnValue = getType().compareTo(desc.getType());
    }
    if (returnValue == 0 && getBackend() != null && desc.getBackend() != null)
    {
      // Only compare the backend IDs. In this context is enough
      returnValue = getBackend().getBackendID().compareTo(
          desc.getBackend().getBackendID());
    }
    if (returnValue == 0)
    {
      returnValue = compare(getEntries(), desc.getEntries());
    }
    if (returnValue == 0)
    {
      returnValue = compare(getAgeOfOldestMissingChange(),
          desc.getAgeOfOldestMissingChange());
    }
    if (returnValue == 0)
    {
      returnValue = compare(getMissingChanges(), desc.getMissingChanges());
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
   */
  private void recalculateHashCode()
  {
    hashCode = (getType().toString() + getAgeOfOldestMissingChange() +
          getDn() +
        getBackend().getBackendID() + getMissingChanges()).hashCode();
  }

  private int compare(int i1, int i2)
  {
    if (i1 == i2)
    {
      return 0;
    }
    else if (i1 > i2)
    {
      return 1;
    }
    else
    {
      return -1;
    }
  }

  private int compare(long i1, long i2)
  {
    if (i1 == i2)
    {
      return 0;
    }
    else if (i1 > i2)
    {
      return 1;
    }
    else
    {
      return -1;
    }
  }
}
