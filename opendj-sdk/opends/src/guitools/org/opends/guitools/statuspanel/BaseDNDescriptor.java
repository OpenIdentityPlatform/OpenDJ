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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.guitools.statuspanel;

import org.opends.quicksetup.util.Utils;

/**
 * This class is used to represent a Base DN / Replica and is aimed to be
 * used by the classes in the DatabasesTableModel class.
 *
 */
public class BaseDNDescriptor implements Comparable
{
  /**
   * An enumeration describing the type of base DN for a given Database.
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

  private int missingChanges;
  private DatabaseDescriptor db;
  private int ageOfOldestMissingChange;
  private Type type;
  private String baseDn;

  /**
   * Constructor for this class.
   * @param type the type of replication.
   * @param baseDn the base DN associated with the Replication.
   * @param db the database containing this base DN.
   * @param ageOfOldestMissingChange the number of missing changes.
   * @param missingChanges the number of missing changes.
   */
  public BaseDNDescriptor(Type type, String baseDn, DatabaseDescriptor db,
      int ageOfOldestMissingChange, int missingChanges)
  {
    this.baseDn = baseDn;
    this.db = db;
    this.type = type;
    this.ageOfOldestMissingChange = ageOfOldestMissingChange;
    this.missingChanges = missingChanges;
  }

  /**
   * Return the String DN associated with the base DN..
   * @return the String DN associated with the base DN.
   */
  public String getDn()
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
        Utils.areDnsEqual(getDn(), desc.getDn()) &&
        (getAgeOfOldestMissingChange() == desc.getAgeOfOldestMissingChange()) &&
        (getMissingChanges() == desc.getMissingChanges()) &&
        getDatabase().getBackendID().equals(
            desc.getDatabase().getBackendID()) &&
        (getDatabase().getEntries() == desc.getDatabase().getEntries());
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
    return (getType().toString() + getAgeOfOldestMissingChange() + getDn() +
        getDatabase().getBackendID() + getMissingChanges()).hashCode();
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
   * Returns the age of the oldest missing change in seconds in the
   * replication topology for this base DN.
   * @return the age of the oldest missing change in seconds in the
   * replication topology for this base DN.
   */
  public int getAgeOfOldestMissingChange()
  {
    return ageOfOldestMissingChange;
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
   * Returns the database where this base DN is defined.
   * @return the database where this base DN is defined.
   */
  public DatabaseDescriptor getDatabase()
  {
    return db;
  }

  /**
   * Sets the type of this base DN.
   * @param type the new type for this base DN.
   */
  void setType(Type type)
  {
    this.type = type;
  }

  /**
   * Sets the database containing this base DN.
   * @param db the database containing this base DN.
   */
  void setDatabase(DatabaseDescriptor db)
  {
    this.db = db;
  }
}
