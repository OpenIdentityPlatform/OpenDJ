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
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.datamodel;

import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.opends.admin.ads.ADSContext;

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
  private PluggableType pluggableType;
  private int hashCode;

  /** An enumeration describing the type of backend. */
  public enum Type
  {
    /** The backend is a backup backend. */
    BACKUP,
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

  /** An enumeration describing the different pluggable backends. */
  public enum PluggableType
  {
    /** JE Backend. */
    JE,
    /** PDB Backend. */
    PDB,
    /** Unknown Type, should never fall through this. */
    UNKNOWN
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
          && Objects.equals(getMonitoringEntry(), desc.getMonitoringEntry());
    }
    return false;
  }

  /**
   * Returns the monitoring entry information.
   * @return the monitoring entry information.
   */
  public CustomSearchResult getMonitoringEntry()
  {
    return monitoringEntry;
  }

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

  /**
   * Set the type of pluggable backend.
   * @param pluggableType the type of pluggable backend.
   */
  public void setPluggableType(PluggableType pluggableType)
  {
    this.pluggableType = pluggableType;
  }

  /**
   * Get the type of pluggable backend.
   * @return the type of pluggable backend.
   */
  public PluggableType getPluggableType()
  {
    return pluggableType;
  }
}
