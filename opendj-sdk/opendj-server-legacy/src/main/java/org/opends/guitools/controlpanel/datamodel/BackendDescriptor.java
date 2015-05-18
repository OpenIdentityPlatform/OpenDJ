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
 *      Copyright 2008-2011 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.guitools.controlpanel.datamodel;

import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.opends.admin.ads.ADSContext;
import org.opends.server.backends.jeb.RemoveOnceLocalDBBackendIsPluggable;

/** The class that describes the backend configuration. */
public class BackendDescriptor
{
  private final String backendID;
  private SortedSet<BaseDNDescriptor> baseDns;
  private SortedSet<IndexDescriptor> indexes;
  private SortedSet<VLVIndexDescriptor> vlvIndexes;
  private int entries;
  private final boolean isConfigBackend;
  private final boolean isEnabled;
  private CustomSearchResult monitoringEntry;
  private final Type type;
  private int hashCode;

  /** An enumeration describing the type of backend. */
  public enum Type
  {
    /** The backend is a backup backend. */
    BACKUP,
    /** The backend is a local backend. */
    @RemoveOnceLocalDBBackendIsPluggable
    LOCAL_DB,
    /** The backend is a LDIF backend. */
    LDIF,
    /** The backend is a memory backend. */
    MEMORY,
    /** The backend is a monitor backend. */
    MONITOR,
    /** The backend is another type of backend (for instance user defined). */
    OTHER,
    /** The backend is pluggable. */
    PLUGGABLE,
    /** The backend is a task backend. */
    TASK
  }

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
      Set<BaseDNDescriptor> baseDns,
      Set<IndexDescriptor> indexes,
      Set<VLVIndexDescriptor> vlvIndexes,
      int entries, boolean isEnabled, Type type)
  {
    this.backendID = backendID;
    this.entries = entries;
    isConfigBackend = isConfigBackend(backendID);
    this.type = type;
    this.isEnabled = isEnabled;
    updateBaseDnsAndIndexes(baseDns, indexes, vlvIndexes);
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

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object o)
  {
    if (this == o)
    {
      return true;
    }
    if (o instanceof BackendDescriptor)
    {
      BackendDescriptor desc = (BackendDescriptor)o;
      return getBackendID().equals(desc.getBackendID())
          && getEntries() == desc.getEntries()
          && desc.getBaseDns().equals(getBaseDns())
          && desc.getIndexes().equals(getIndexes())
          && desc.getVLVIndexes().equals(getVLVIndexes())
          && equal(getMonitoringEntry(), desc.getMonitoringEntry());
    }
    return false;
  }

  private boolean equal(CustomSearchResult m1, CustomSearchResult m2)
  {
    if (m1 == null)
    {
      return m2 == null;
    }
    return m1.equals(m2);
  }

  /**
   * Returns the monitoring entry information.
   * @return the monitoring entry information.
   */
  public CustomSearchResult getMonitoringEntry()
  {
    return monitoringEntry;
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode()
  {
    return hashCode;
  }

  /**
   * Method called when one of the elements that affect the value of the
   * hashcode is modified.  It is used to minimize the time spent calculating
   * hashCode.
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
   * have a reference to this backend.  It also initialize the members of this
   * class with the base DNs and indexes.
   * @param baseDns the base DNs associated with the Backend.
   * @param indexes the indexes defined in the backend.
   * @param vlvIndexes the VLV indexes defined in the backend.
   *
   */
  private void updateBaseDnsAndIndexes(Set<BaseDNDescriptor> baseDns,
      Set<IndexDescriptor> indexes, Set<VLVIndexDescriptor> vlvIndexes)
  {
    for (BaseDNDescriptor baseDN : baseDns)
    {
      baseDN.setBackend(this);
    }
    this.baseDns = new TreeSet<>(baseDns);
    for (IndexDescriptor index : indexes)
    {
      index.setBackend(this);
    }
    this.indexes = new TreeSet<>(indexes);
    for (VLVIndexDescriptor index : vlvIndexes)
    {
      index.setBackend(this);
    }
    this.vlvIndexes = new TreeSet<>(vlvIndexes);
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
    "ads-truststore".equalsIgnoreCase(id);
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
