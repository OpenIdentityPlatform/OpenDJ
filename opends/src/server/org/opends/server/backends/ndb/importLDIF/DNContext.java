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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 */
package org.opends.server.backends.ndb.importLDIF;

import org.opends.server.types.DN;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.util.LDIFReader;
import org.opends.server.backends.ndb.*;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import org.opends.server.admin.std.server.NdbBackendCfg;

/**
 * This class represents the import context for a destination base DN.
 */
public class DNContext {

  /**
   * The destination base DN.
   */
  private DN baseDN;

  /**
   * The include branches below the base DN.
   */
  private List<DN> includeBranches;

  /**
   * The exclude branches below the base DN.
   */
  private List<DN> excludeBranches;

  /**
   * The configuration of the destination backend.
   */
  private NdbBackendCfg config;

  /**
   * The requested LDIF import configuration.
   */
  private LDIFImportConfig ldifImportConfig;

  /**
   * A reader for the source LDIF file.
   */
  private LDIFReader ldifReader;

  /**
   * The entry entryContainer for the destination base DN.
   */
  private EntryContainer entryContainer;

  /**
   * The source entryContainer if this is a partial import of a base DN.
   */
  private EntryContainer srcEntryContainer;

  /**
   * A queue of elements that have been read from the LDIF and are ready
   * to be imported.
   */

  private BlockingQueue<WorkElement> workQueue;

  /**
   * Map of pending DNs added to the work queue. Used to check if a parent
   * entry has been added, but isn't in the database.
   */
  private ConcurrentHashMap<DN, DN> pendingMap =
    new ConcurrentHashMap<DN, DN>() ;

  /**
   * The number of LDAP entries added to the database, used to update the
   * entry database record count after import.  The value is not updated
   * for replaced entries.  Multiple threads may be updating this value.
   */
  private AtomicLong entryInsertCount = new AtomicLong(0);

  /**
   * The parent DN of the previous imported entry.
   */
  private DN parentDN;


  /**
   * Get the work queue.
   *
   * @return  The work queue.
   */
  public BlockingQueue<WorkElement> getWorkQueue() {
      return workQueue;
    }


  /**
   * Set the work queue to the specified work queue.
   *
   * @param workQueue The work queue.
   */
  public void
   setWorkQueue(BlockingQueue<WorkElement> workQueue) {
    this.workQueue = workQueue;
  }

  /**
   * Set the destination base DN.
   * @param baseDN The destination base DN.
   */
  public void setBaseDN(DN baseDN)
  {
    this.baseDN = baseDN;
  }

  /**
   * Get the destination base DN.
   * @return The destination base DN.
   */
  public DN getBaseDN()
  {
    return baseDN;
  }

  /**
   * Set the configuration of the destination backend.
   * @param config The destination backend configuration.
   */
  public void setConfig(NdbBackendCfg config)
  {
    this.config = config;
  }

  /**
   * Get the configuration of the destination backend.
   * @return The destination backend configuration.
   */
  public NdbBackendCfg getConfig()
  {
    return config;
  }

  /**
   * Set the requested LDIF import configuration.
   * @param ldifImportConfig The LDIF import configuration.
   */
  public void setLDIFImportConfig(LDIFImportConfig ldifImportConfig)
  {
    this.ldifImportConfig = ldifImportConfig;
  }

  /**
   * Get the requested LDIF import configuration.
   * @return The requested LDIF import configuration.
   */
  public LDIFImportConfig getLDIFImportConfig()
  {
    return ldifImportConfig;
  }

  /**
   * Set the source LDIF reader.
   * @param ldifReader The source LDIF reader.
   */
  public void setLDIFReader(LDIFReader ldifReader)
  {
    this.ldifReader = ldifReader;
  }

  /**
   * Get the source LDIF reader.
   * @return The source LDIF reader.
   */
  public LDIFReader getLDIFReader()
  {
    return ldifReader;
  }

  /**
   * Set the entry entryContainer for the destination base DN.
   * @param entryContainer The entry entryContainer for the destination base DN.
   */
  public void setEntryContainer(EntryContainer entryContainer)
  {
    this.entryContainer = entryContainer;
  }

  /**
   * Get the entry entryContainer for the destination base DN.
   * @return The entry entryContainer for the destination base DN.
   */
  public EntryContainer getEntryContainer()
  {
    return entryContainer;
  }

  /**
   * Set the source entry entryContainer for the destination base DN.
   * @param srcEntryContainer The entry source entryContainer for the
   * destination base DN.
   */
  public void setSrcEntryContainer(EntryContainer srcEntryContainer)
  {
    this.srcEntryContainer = srcEntryContainer;
  }

  /**
   * Get the source entry entryContainer for the destination base DN.
   * @return The source entry entryContainer for the destination base DN.
   */
  public EntryContainer getSrcEntryContainer()
  {
    return srcEntryContainer;
  }

  /**
   * Get the number of new LDAP entries imported into the entry database.
   * @return The number of new LDAP entries imported into the entry database.
   */
  public long getEntryInsertCount()
  {
    return entryInsertCount.get();
  }

  /**
   * Increment the number of new LDAP entries imported into the entry database
   * by the given amount.
   * @param delta The amount to add.
   */
  public void incrEntryInsertCount(long delta)
  {
    entryInsertCount.getAndAdd(delta);
  }

  /**
   * Get the parent DN of the previous imported entry.
   * @return The parent DN of the previous imported entry.
   */
  public DN getParentDN()
  {
    return parentDN;
  }

  /**
   * Set the parent DN of the previous imported entry.
   * @param parentDN The parent DN of the previous imported entry.
   */
  public void setParentDN(DN parentDN)
  {
    this.parentDN = parentDN;
  }

  /**
   * Retrieves the set of base DNs that specify the set of entries to
   * exclude from the import.  The contents of the returned list may
   * be altered by the caller.
   *
   * @return  The set of base DNs that specify the set of entries to
   *          exclude from the import.
   */
  public List<DN> getExcludeBranches() {
    return excludeBranches;
  }

  /**
   * Specifies the set of base DNs that specify the set of entries to
   * exclude from the import.
   *
   * @param  excludeBranches  The set of base DNs that specify the set
   *                          of entries to exclude from the import.
   */
  public void setExcludeBranches(List<DN> excludeBranches) {
    if (excludeBranches == null) {
      this.excludeBranches = new ArrayList<DN>(0);
    } else {
      this.excludeBranches = excludeBranches;
    }
  }

  /**
   * Retrieves the set of base DNs that specify the set of entries to
   * include in the import.  The contents of the returned list may be
   * altered by the caller.
   *
   * @return  The set of base DNs that specify the set of entries to
   *          include in the import.
   */
  public List<DN> getIncludeBranches() {
    return includeBranches;
  }

  /**
   * Specifies the set of base DNs that specify the set of entries to
   * include in the import.
   *
   * @param  includeBranches  The set of base DNs that specify the set
   *                          of entries to include in the import.
   */
  public void setIncludeBranches(List<DN> includeBranches) {
    if (includeBranches == null) {
      this.includeBranches = new ArrayList<DN>(0);
    } else {
      this.includeBranches = includeBranches;
    }
  }

  /**
   * Check if the parent DN is in the pending map.
   *
   * @param parentDN The DN of the parent.
   * @return <CODE>True</CODE> if the parent is in the pending map.
   */
  public boolean isPending(DN parentDN) {
    boolean ret = false;
    if (pendingMap.containsKey(parentDN)) {
      ret = true;
    }
    return ret;
  }

  /**
   * Add specified DN to the pending map.
   *
   * @param dn The DN to add to the map.
   */
  public void addPending(DN dn) {
    pendingMap.putIfAbsent(dn, dn);
  }

  /**
   * Remove the specified DN from the pending map.
   *
   * @param dn The DN to remove from the map.
   */
  public void removePending(DN dn) {
    pendingMap.remove(dn);
  }
}
