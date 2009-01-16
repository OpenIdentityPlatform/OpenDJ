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

import java.util.Collections;
import java.util.SortedSet;

import org.opends.admin.ads.ADSContext;

/**
 * The class that describes the backend configuration.
 *
 */
public class BackendDescriptor
{
  private String backendID;
  private SortedSet<BaseDNDescriptor> baseDns;
  private SortedSet<IndexDescriptor> indexes;
  private SortedSet<VLVIndexDescriptor> vlvIndexes;
  private int entries;
  private boolean isConfigBackend;
  private boolean isEnabled;
  private CustomSearchResult monitoringEntry;
  private Type type;
  private int hashCode;

  /**
   * An enumeration describing the type of backend.
   */
  public enum Type
  {
    /**
     * The backend is a local backend.
     */
    LOCAL_DB,
    /**
     * The backend is a LDIF backend.
     */
    LDIF,
    /**
     * The backend is a memory backend.
     */
    MEMORY,
    /**
     * The backend is a backup backend.
     */
    BACKUP,
    /**
     * The backend is a monitor backend.
     */
    MONITOR,
    /**
     * The backend is a task backend.
     */
    TASK,
    /**
     * The backend is another type of backend (for instance user defined).
     */
    OTHER
  };

  /**
   * Constructor for this class.
   * @param backendID the backend ID of the Backend.
   * @param baseDns the base DNs associated with the Backend.
   * @param indexes the indexes defined in the backend.
   * @param vlvIndexes the VLV indexes defined in the backend.
   * @param entries the number of entries in the Backend.
   * @param isEnabled whether the backend is enabled or not.
   * @param type the type of the backend.
   */
  public BackendDescriptor(String backendID,
      SortedSet<BaseDNDescriptor> baseDns,
      SortedSet<IndexDescriptor> indexes,
      SortedSet<VLVIndexDescriptor> vlvIndexes,
      int entries, boolean isEnabled, Type type)
  {
    this.backendID = backendID;
    this.baseDns = Collections.unmodifiableSortedSet(baseDns);
    this.indexes = Collections.unmodifiableSortedSet(indexes);
    this.vlvIndexes = Collections.unmodifiableSortedSet(vlvIndexes);
    this.entries = entries;
    isConfigBackend = isConfigBackend(backendID);
    this.type = type;
    this.isEnabled = isEnabled;
    updateBaseDnsAndIndexes();
    recalculateHashCode();
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
   * Returns the Base DN objects associated with the backend.
   * @return the Base DN objects associated with the backend.
   */
  public SortedSet<BaseDNDescriptor> getBaseDns()
  {
    return baseDns;
  }

  /**
   * Returns the vlv index objects associated with the backend.
   * @return the vlv index objects associated with the backend.
   */
  public SortedSet<VLVIndexDescriptor> getVLVIndexes()
  {
    return vlvIndexes;
  }


  /**
   * Returns the index objects associated with the backend.
   * @return the index objects associated with the backend.
   */
  public SortedSet<IndexDescriptor> getIndexes()
  {
    return indexes;
  }

  /**
   * Return the number of entries in the backend.
   * -1 indicates that the number of entries could not be found.
   * @return the number of entries in the backend.
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
      if (v instanceof BackendDescriptor)
      {
        BackendDescriptor desc = (BackendDescriptor)v;
        equals = getBackendID().equals(desc.getBackendID()) &&
        (getEntries() == desc.getEntries());

        if (equals)
        {
          equals = desc.getBaseDns().equals(getBaseDns());
        }

        if (equals)
        {
          equals = desc.getIndexes().equals(getIndexes());
        }

        if (equals)
        {
          equals = desc.getVLVIndexes().equals(getVLVIndexes());
        }

        if (equals)
        {
          // Compare monitoring entries
          if (getMonitoringEntry() == null)
          {
            equals = desc.getMonitoringEntry() == null;
          }
          else
          {
            equals = getMonitoringEntry().equals(desc.getMonitoringEntry());
          }
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
   * Returns the monitoring entry information.
   * @return the monitoring entry information.
   */
  public CustomSearchResult getMonitoringEntry()
  {
    return monitoringEntry;
  }

  /**
   * {@inheritDoc}
   */
  public int hashCode()
  {
    return hashCode;
  }

  /**
   * Method called when one of the elements that affect the value of the
   * hashcode is modified.  It is used to minimize the time spent calculating
   * hashCode.
   *
   */
  private void recalculateHashCode()
  {
    hashCode = 0;
    for (BaseDNDescriptor rep: getBaseDns())
    {
      hashCode += rep.hashCode();
    }
    hashCode += entries;
    for (IndexDescriptor index : indexes)
    {
      hashCode += index.hashCode();
    }
    for (VLVIndexDescriptor index : vlvIndexes)
    {
      hashCode += index.hashCode();
    }
  }

  /**
   * Updates the base DNs and indexes contained in this backend so that they
   * have a reference to this backend.
   *
   */
  private void updateBaseDnsAndIndexes()
  {
    for (BaseDNDescriptor baseDN : baseDns)
    {
      baseDN.setBackend(this);
    }
    for (AbstractIndexDescriptor index : indexes)
    {
      index.setBackend(this);
    }
    for (AbstractIndexDescriptor index : vlvIndexes)
    {
      index.setBackend(this);
    }
  }

  /**
   * An convenience method to know if the provided ID corresponds to a
   * configuration backend or not.
   * @param id the backend ID to analyze
   * @return <CODE>true</CODE> if the the id corresponds to a configuration
   * backend and <CODE>false</CODE> otherwise.
   */
  private boolean isConfigBackend(String id)
  {
    return "tasks".equalsIgnoreCase(id) ||
    "schema".equalsIgnoreCase(id) ||
    "config".equalsIgnoreCase(id) ||
    "monitor".equalsIgnoreCase(id) ||
    "backup".equalsIgnoreCase(id) ||
    ADSContext.getDefaultBackendName().equalsIgnoreCase(id) ||
    "ads-truststore".equalsIgnoreCase(id) ||
    "replicationchanges".equalsIgnoreCase(id);
  }

  /**
   * Tells whether this is a configuration backend or not.
   * @return <CODE>true</CODE> if this is a configuration backend and
   * <CODE>false</CODE> otherwise.
   */
  public boolean isConfigBackend()
  {
    return isConfigBackend;
  }

  /**
   * Sets the number of entries contained in this backend.
   * @param entries the number of entries contained in this backend.
   */
  public void setEntries(int entries)
  {
    this.entries = entries;

    // Recalculate hashCode
    recalculateHashCode();
  }

  /**
   * Sets the monitoring entry corresponding to this backend.
   * @param monitoringEntry the monitoring entry corresponding to this backend.
   */
  public void setMonitoringEntry(CustomSearchResult monitoringEntry)
  {
    this.monitoringEntry = monitoringEntry;
  }

  /**
   * Returns the type of the backend.
   * @return the type of the backend.
   */
  public Type getType()
  {
    return type;
  }

  /**
   * Tells whether this backend is enabled or not.
   * @return <CODE>true</CODE> if this is backend is enabled
   * <CODE>false</CODE> otherwise.
   */
  public boolean isEnabled()
  {
    return isEnabled;
  }
}
