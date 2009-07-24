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
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package org.opends.server.backends.jeb.importLDIF;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.opends.server.backends.jeb.*;
import org.opends.server.config.ConfigException;
import org.opends.server.types.*;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.LockMode;


/**
 * The class represents a suffix. OpenDS backends can have multiple suffixes.
 */
public class Suffix
{
  private final RootContainer rootContainer;
  private final LDIFImportConfig config;
  private final List<DN> includeBranches = new ArrayList<DN>();
  private final List<DN> excludeBranches = new ArrayList<DN>();
  private final DN baseDN;
  private EntryContainer srcEntryContainer = null;
  private EntryContainer entryContainer;
  private boolean exclude = false;
  private final Object synchObject = new Object();
  private static final int PARENT_ID_MAP_SIZE = 4096;

  private ConcurrentHashMap<DN,DN> pendingMap =
                      new ConcurrentHashMap<DN, DN>() ;
  private HashMap<DN,EntryID> parentIDMap =
    new HashMap<DN,EntryID>(PARENT_ID_MAP_SIZE);

  private DN parentDN;
  private ArrayList<EntryID> IDs;

  private Suffix(EntryContainer entryContainer, LDIFImportConfig config,
                 RootContainer rootContainer) throws InitializationException,
          ConfigException
  {
    this.rootContainer = rootContainer;
    this.entryContainer = entryContainer;
    this.config = config;
    this.baseDN = entryContainer.getBaseDN();
    init();
  }

  /**
   * Creates a suffix instance using the specified parameters.
   *
   * @param entryContainer The entry container pertaining to the suffix.
   * @param config The import config instance.
   * @param rootContainer The root container.
   *
   * @return A suffix instance.
   * @throws InitializationException If the suffix cannot be initialized.
   * @throws ConfigException If an error occured reading the configuration.
   */
  public static Suffix
  createSuffixContext(EntryContainer entryContainer, LDIFImportConfig config,
        RootContainer rootContainer) throws InitializationException,
        ConfigException
  {
    return new Suffix(entryContainer, config, rootContainer);
  }

  /**
   * Returns the DN2ID instance pertaining to a suffix instance.
   *
   * @return A DN2ID instance that can be used to manipulate the DN2ID database.
   */
  public DN2ID getDN2ID()
  {
    return entryContainer.getDN2ID();
  }


    /**
   * Returns the ID2Entry instance pertaining to a suffix instance.
   *
   * @return A ID2Entry instance that can be used to manipulate the ID2Entry
     *       database.
   */
  public ID2Entry getID2Entry()
  {
    return entryContainer.getID2Entry();
  }


   /**
   * Returns the DN2URI instance pertaining to a suffix instance.
   *
   * @return A DN2URI instance that can be used to manipulate the DN2URI
    *        database.
   */
  public DN2URI getDN2URI()
  {
    return entryContainer.getDN2URI();
  }


    /**
   * Returns the entry container pertaining to a suffix instance.
   *
   * @return The entry container used to create a suffix instance.
   */
  public EntryContainer getEntryContainer()
  {
    return entryContainer;
  }


  private void init() throws InitializationException, ConfigException
  {
    if(!config.appendToExistingData() && !config.clearBackend()) {
      for(DN dn : config.getExcludeBranches()) {
        if(baseDN.equals(dn))
          exclude = true;
        if(baseDN.isAncestorOf(dn))
          excludeBranches.add(dn);
      }

      if(!config.getIncludeBranches().isEmpty()) {
        for(DN dn : config.getIncludeBranches()) {
          if(baseDN.isAncestorOf(dn))
            includeBranches.add(dn);
        }
        if(includeBranches.isEmpty())
          this.exclude = true;

        // Remove any overlapping include branches.
        Iterator<DN> includeBranchIterator = includeBranches.iterator();
        while(includeBranchIterator.hasNext()) {
          DN includeDN = includeBranchIterator.next();
          boolean keep = true;
          for(DN dn : includeBranches)  {
            if(!dn.equals(includeDN) && dn.isAncestorOf(includeDN)) {
              keep = false;
              break;
            }
          }
          if(!keep)
            includeBranchIterator.remove();
        }

        // Remove any exclude branches that are not are not under a include
        // branch since they will be migrated as part of the existing entries
        // outside of the include branches anyways.
        Iterator<DN> excludeBranchIterator = excludeBranches.iterator();
        while(excludeBranchIterator.hasNext()) {
          DN excludeDN = excludeBranchIterator.next();
          boolean keep = false;
          for(DN includeDN : includeBranches) {
            if(includeDN.isAncestorOf(excludeDN)) {
              keep = true;
              break;
            }
          }
          if(!keep)
            excludeBranchIterator.remove();
        }

        try {
          if(includeBranches.size() == 1 && excludeBranches.size() == 0 &&
              includeBranches.get(0).equals(baseDN)) {
            // This entire base DN is explicitly included in the import with
            // no exclude branches that we need to migrate. Just clear the entry
            // container.
            entryContainer.lock();
            entryContainer.clear();
            entryContainer.unlock();
          } else {
            // Create a temporary entry container
            srcEntryContainer = entryContainer;
            String tmpName = baseDN.toNormalizedString() +"_importTmp";
            entryContainer = rootContainer.openEntryContainer(baseDN, tmpName);
          }
        } catch (DatabaseException e) {
   //       Message msg = ERR_CONFIG_IMPORT_SUFFIX_ERROR.get(e.getMessage());
    //      throw new InitializationException(msg);
        }
      }
    }
  }


  /**
   * Return the Attribute Type - Index map used to map an attribute type to an
   * index instance.
   *
   * @return A suffixes Attribute Type - Index map.
   */
  public Map<AttributeType, AttributeIndex> getAttrIndexMap()
  {
    return entryContainer.getAttributeIndexMap();
  }


  /**
   * Check if the parent DN is in the pending map.
   *
   * @param parentDN The DN of the parent.
   * @return <CODE>True</CODE> if the parent is in the pending map.
   */
  private boolean isPending(DN parentDN)
  {
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
  public void addPending(DN dn)
  {
    pendingMap.putIfAbsent(dn, dn);
  }

  /**
   * Remove the specified DN from the pending map.
   *
   * @param dn The DN to remove from the map.
   */
  public void removePending(DN dn)
  {
    pendingMap.remove(dn);
  }


  /**
   * Return the entry ID related to the specified entry DN. First the instance's
   * cache of parent IDs is checked, if it isn't found then the DN2ID is
   * searched.
   *
   * @param parentDN The DN to get the id for.
   * @return The entry ID related to the parent DN, or null if the id wasn't
   *         found in the cache or dn2id database.
   *
   * @throws DatabaseException If an error occurred search the dn2id database.
   */
  public
  EntryID getParentID(DN parentDN) throws DatabaseException {
    EntryID parentID;
    synchronized(synchObject) {
      parentID = parentIDMap.get(parentDN);
      if (parentID != null) {
        return parentID;
      }
    }
    int i=0;
    //If the parent is in the pending map, another thread is working on the
    //parent entry; wait 500 ms until that thread is done with the parent.
    while(isPending(parentDN)) {
      try {
        Thread.sleep(50);
        if(i == 10) {
          System.out.println("Timed out waiting for: " + parentDN.toString());
          return null;
        }
        i++;
      } catch (Exception e) {
        System.out.println("Exception: " + parentDN.toString());
        return null;
      }
    }
    parentID = entryContainer.getDN2ID().get(null, parentDN, LockMode.DEFAULT);
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
    }  else {
      System.out.println("parent not found: " + parentDN.toString());
    }
    return parentID;
  }


  /**
   * Sets all of the indexes, vlvIndexes, id2children and id2subtree indexes to
   * trusted.
   *
   * @throws DatabaseException If an error occurred setting the indexes to
   *                           trusted.
   */
  public void setIndexesTrusted() throws DatabaseException
  {
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
    for(VLVIndex vlvIdx : entryContainer.getVLVIndexes()) {
        vlvIdx.setTrusted(null, true);
    }
  }

  /**
   * Get the parent DN of the last entry added to a suffix.
   *
   * @return The parent DN of the last entry added.
   */
  public DN getParentDN()
  {
    return parentDN;
  }


  /**
   * Set the parent DN of the last entry added to a suffix.
   *
   * @param parentDN The parent DN to save.
   */
  public void setParentDN(DN parentDN)
  {
    this.parentDN = parentDN;
  }

  /**
   * Get the entry ID list of the last entry added to a suffix.
   *
   * @return Return the entry ID list of the last entry added to a suffix.
   */
  public ArrayList<EntryID> getIDs()
  {
    return IDs;
  }


  /**
   * Set the entry ID list of the last entry added to a suffix.
   *
   * @param IDs The entry ID list to save.
   */
  public void setIDs(ArrayList<EntryID> IDs)
  {
    this.IDs = IDs;
  }
}
