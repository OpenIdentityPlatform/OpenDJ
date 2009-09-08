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
import static org.opends.server.loggers.ErrorLogger.logError;
import org.opends.messages.Message;
import org.opends.messages.Category;
import org.opends.messages.Severity;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.LockMode;


/**
 * The class represents a suffix. OpenDS backends can have multiple suffixes.
 */
public class Suffix
{
  private final List<DN> includeBranches;
  private final List<DN> excludeBranches;
  private final DN baseDN;
  private final EntryContainer srcEntryContainer;
  private EntryContainer entryContainer;
  private final Object synchObject = new Object();
  private static final int PARENT_ID_MAP_SIZE = 4096;

  private ConcurrentHashMap<DN,DN> pendingMap =
                      new ConcurrentHashMap<DN, DN>() ;
  private HashMap<DN,EntryID> parentIDMap =
    new HashMap<DN,EntryID>(PARENT_ID_MAP_SIZE);

  private DN parentDN;
  private ArrayList<EntryID> IDs;

  private
  Suffix(EntryContainer entryContainer, EntryContainer srcEntryContainer,
         List<DN> includeBranches, List<DN> excludeBranches)
          throws InitializationException, ConfigException
  {
    this.entryContainer = entryContainer;
    this.srcEntryContainer = srcEntryContainer;
    this.baseDN = entryContainer.getBaseDN();
    if (includeBranches == null)
    {
      this.includeBranches = new ArrayList<DN>(0);
    }
    else
    {
      this.includeBranches = includeBranches;
    }
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
   * Creates a suffix instance using the specified parameters.
   *
   * @param entryContainer The entry container pertaining to the suffix.
   * @param srcEntryContainer The original entry container.
   * @param includeBranches The include branches.
   * @param excludeBranches The exclude branches.
   *
   * @return A suffix instance.
   * @throws InitializationException If the suffix cannot be initialized.
   * @throws ConfigException If an error occured reading the configuration.
   */
  public static Suffix
  createSuffixContext(EntryContainer entryContainer,
                      EntryContainer srcEntryContainer,
        List<DN> includeBranches, List<DN> excludeBranches)
        throws InitializationException, ConfigException
  {
    return new Suffix(entryContainer, srcEntryContainer,
                      includeBranches, excludeBranches);
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
          //Temporary message until this code is removed.
           Message message = Message.raw(Category.JEB, Severity.SEVERE_ERROR,
               "time out in parentID check");
          logError(message);
          return null;
        }
        i++;
      } catch (Exception e) {
        //Temporary message until this code is removed.
         Message message = Message.raw(Category.JEB, Severity.SEVERE_ERROR,
                "Exception thrown in parentID check");
         logError(message);
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

  /**
   * Return a src entry container.
   *
   * @return  The src entry container.
   */
  public EntryContainer getSrcEntryContainer()
  {
    return this.srcEntryContainer;
  }

  /**
   * Return include branches.
   *
   * @return The include branches.
   */
  public List<DN> getIncludeBranches()
  {
    return this.includeBranches;
  }

  /**
   * Return exclude branches.
   *
   * @return the exclude branches.
   */
  public List<DN> getExcludeBranches()
  {
    return this.excludeBranches;
  }

  /**
   * Return base DN.
   *
   * @return The base DN.
   */
  public DN getBaseDN()
  {
    return this.baseDN;
  }
}
