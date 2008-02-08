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

package org.opends.guitools.statuspanel;

import java.util.SortedSet;

/**
 * This class is used to represent a Database and is aimed to be used by the
 * classes in the DatabasesTableModel class.
 */
public class DatabaseDescriptor
{
  private String backendID;
  private SortedSet<BaseDNDescriptor> baseDns;
  private int entries;

  /**
   * Constructor for this class.
   * @param backendID the backend ID of the Database.
   * @param baseDns the base DNs associated with the Database.
   * @param entries the number of entries in the Database.
   */
  public DatabaseDescriptor(String backendID,
      SortedSet<BaseDNDescriptor> baseDns,
      int entries)
  {
    this.backendID = backendID;
    this.baseDns = baseDns;
    this.entries = entries;
  }

  /**
   * Returns the ID of the Backend.
   * @return the ID of the Backend.
   */
  public String getBackendID()
  {
    return backendID;
  }

  /**
   * Returns the Base DN objects associated with the database.
   * @return the Base DN objects associated with the database.
   */
  public SortedSet<BaseDNDescriptor> getBaseDns()
  {
    return baseDns;
  }

  /**
   * Return the number of entries in the database.
   * -1 indicates that the number of entries could not be found.
   * @return the number of entries in the database.
   */
  public int getEntries()
  {
    return entries;
  }

  /**
   * {@inheritDoc}
   */
  public boolean equals(Object v)
  {
    boolean equals = false;
    if (this != v)
    {
      if (v instanceof DatabaseDescriptor)
      {
        DatabaseDescriptor desc = (DatabaseDescriptor)v;
        equals = getBackendID().equals(desc.getBackendID()) &&
        (getEntries() == desc.getEntries());

        if (equals)
        {
          equals = desc.getBaseDns().equals(getBaseDns());
        }
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
    StringBuilder buf = new StringBuilder();
    for (BaseDNDescriptor rep: getBaseDns())
    {
      buf.append(rep.hashCode()+";");
    }
    buf.append(getBackendID() + getEntries());

    return buf.toString().hashCode();
  }
}
