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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.backends.jeb.importLDIF;

import org.opends.server.types.DN;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.AttributeType;
import org.opends.server.util.LDIFReader;
import org.opends.server.admin.std.server.LocalDBBackendCfg;
import org.opends.server.backends.jeb.*;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.LockMode;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;

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
  private LocalDBBackendCfg config;

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


  //This currently isn't used.
  private ArrayList<VLVIndex> vlvIndexes = new ArrayList<VLVIndex>();

  /**
   * The maximum number of parent ID values that we will remember.
   */
  private static final int PARENT_ID_MAP_SIZE = 100;

  /**
   * Map of likely parent entry DNs to their entry IDs.
   */
  private HashMap<DN,EntryID> parentIDMap =
       new HashMap<DN,EntryID>(PARENT_ID_MAP_SIZE);

  //Map of pending DNs added to the work queue. Used to check if a parent
  //entry has been added, but isn't in the dn2id DB.
  private ConcurrentHashMap<DN,DN> pendingMap =
                                              new ConcurrentHashMap<DN, DN>() ;

  //Used to synchronize the parent ID map, since multiple worker threads
  //can be accessing it.
  private final Object synchObject = new Object();

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
   * The superior IDs, in order from the parent up to the base DN, of the
   * previous imported entry. This is used together with the previous parent DN
   * to save time constructing the subtree index, in the typical case where many
   * contiguous entries from the LDIF file have the same parent.
   */
  private ArrayList<EntryID> IDs;

  //The buffer manager used to hold the substring cache.
  private BufferManager bufferManager;


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
  public void setConfig(LocalDBBackendCfg config)
  {
    this.config = config;
  }

  /**
   * Get the configuration of the destination backend.
   * @return The destination backend configuration.
   */
  public LocalDBBackendCfg getConfig()
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
   * Get the superior IDs of the previous imported entry.
   * @return The superior IDs of the previous imported entry.
   */
  public ArrayList<EntryID> getIDs()
  {
    return IDs;
  }

  /**
   * Set the superior IDs of the previous imported entry.
   * @param IDs The superior IDs of the previous imported entry.
   */
  public void setIDs(ArrayList<EntryID> IDs)
  {
    this.IDs = IDs;
  }

  /**
     * Retrieves the set of base DNs that specify the set of entries to
     * exclude from the import.  The contents of the returned list may
     * be altered by the caller.
     *
     * @return  The set of base DNs that specify the set of entries to
     *          exclude from the import.
     */
    public List<DN> getExcludeBranches()
    {
      return excludeBranches;
    }



    /**
     * Specifies the set of base DNs that specify the set of entries to
     * exclude from the import.
     *
     * @param  excludeBranches  The set of base DNs that specify the set
     *                          of entries to exclude from the import.
     */
    public void setExcludeBranches(List<DN> excludeBranches)
    {
      if (excludeBranches == null)
      {
        this.excludeBranches = new ArrayList<DN>(0);
      }
      else
      {
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
    public List<DN> getIncludeBranches()
    {
      return includeBranches;
    }



    /**
     * Specifies the set of base DNs that specify the set of entries to
     * include in the import.
     *
     * @param  includeBranches  The set of base DNs that specify the set
     *                          of entries to include in the import.
     */
    public void setIncludeBranches(List<DN> includeBranches)
    {
      if (includeBranches == null)
      {
        this.includeBranches = new ArrayList<DN>(0);
      }
      else
      {
        this.includeBranches = includeBranches;
      }
    }


    /**
     * Return the attribute type attribute index map.
     *
     * @return The attribute type attribute index map.
     */
    public Map<AttributeType, AttributeIndex> getAttrIndexMap() {
      return entryContainer.getAttributeIndexMap();
    }

    /**
     * Set all the indexes to trusted.
     *
     * @throws DatabaseException If the trusted value cannot be updated in the
     * index DB.
     */
    public void setIndexesTrusted() throws DatabaseException {
      entryContainer.getID2Children().setTrusted(null,true);
      entryContainer.getID2Subtree().setTrusted(null, true);
      for(AttributeIndex attributeIndex :
          entryContainer.getAttributeIndexes()) {
        Index index;
        if((index = attributeIndex.getEqualityIndex()) != null) {
          index.setTrusted(null, true);
        }
        if((index=attributeIndex.getPresenceIndex()) != null) {
          index.setTrusted(null, true);
        }
        if((index=attributeIndex.getSubstringIndex()) != null) {
          index.setTrusted(null, true);
        }
        if((index=attributeIndex.getOrderingIndex()) != null) {
          index.setTrusted(null, true);
        }
        if((index=attributeIndex.getApproximateIndex()) != null) {
          index.setTrusted(null, true);
        }
      }
    }


    /**
     * Get the Entry ID of the parent entry.
     * @param parentDN  The parent DN.
     * @param dn2id The DN2ID DB.
     * @return The entry ID of the parent entry.
     * @throws DatabaseException If a DB error occurs.
     */
    public
    EntryID getParentID(DN parentDN, DN2ID dn2id)
            throws DatabaseException {
      EntryID parentID;
      synchronized(synchObject) {
        parentID = parentIDMap.get(parentDN);
        if (parentID != null) {
          return parentID;
        }
      }
      int i=0;
      //If the parent is in the pending map, another thread is working on the
      //parent entry; wait until that thread is done with the parent.
      while(isPending(parentDN)) {
        try {
          Thread.sleep(50);
          if(i == 5) {
            return null;
          }
          i++;
        } catch (Exception e) {
          return null;
        }
      }
      parentID = dn2id.get(null, parentDN, LockMode.DEFAULT);
      //If the parent is in dn2id, add it to the cache.
      if (parentID != null) {
        synchronized(synchObject) {
          if (parentIDMap.size() >= PARENT_ID_MAP_SIZE) {
            Iterator<DN> iterator = parentIDMap.keySet().iterator();
            iterator.next();
            iterator.remove();
          }
          parentIDMap.put(parentDN, parentID);
        }
      }
      return parentID;
    }

    /**
     * Check if the parent DN is in the pending map.
     *
     * @param parentDN The DN of the parent.
     * @return <CODE>True</CODE> if the parent is in the pending map.
     */
    private boolean isPending(DN parentDN) {
      boolean ret = false;
      if(pendingMap.containsKey(parentDN)) {
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

    /**
     * Set the substring buffer manager to the specified buffer manager.
     *
     * @param bufferManager The buffer manager.
     */
    public void setBufferManager(BufferManager bufferManager) {
      this.bufferManager = bufferManager;
    }

    /**
     * Return the buffer manager.
     *
     * @return The buffer manager.
     */
    public BufferManager getBufferManager() {
      return bufferManager;
    }
  }
