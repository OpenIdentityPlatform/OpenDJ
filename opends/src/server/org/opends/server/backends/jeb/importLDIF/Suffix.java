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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 */

package org.opends.server.backends.jeb.importLDIF;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import org.opends.server.backends.jeb.*;
import org.opends.server.config.ConfigException;
import org.opends.server.types.*;
import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.backends.jeb.importLDIF.Importer.*;
import org.opends.messages.Message;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.LockMode;
import static org.opends.messages.JebMessages.*;

/**
 * The class represents a suffix that is to be loaded during an import, or
 * rebuild index process. Multiple instances of this class can be instantiated
 * during and import to support multiple suffixes in a backend. A rebuild
 * index has only one of these instances.
 */
public class Suffix
{
  private final List<DN> includeBranches, excludeBranches;
  private final DN baseDN;
  private final EntryContainer srcEntryContainer;
  private EntryContainer entryContainer;
  private final Object synchObject = new Object();
  private static final int PARENT_ID_SET_SIZE = 16 * 1024;
  private ConcurrentHashMap<DN, CountDownLatch> pendingMap =
          new ConcurrentHashMap<DN, CountDownLatch>();
  private Set<DN> parentSet = new HashSet<DN>(PARENT_ID_SET_SIZE);
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
   * Make sure the specified parent DN is not in the pending map.
   *
   * @param parentDN The DN of the parent.
   */
  private void assureNotPending(DN parentDN)  throws InterruptedException
  {
    CountDownLatch l;
    if((l=pendingMap.get(parentDN)) != null)
    {
      l.await();
    }
  }


  /**
   * Add specified DN to the pending map.
   *
   * @param dn The DN to add to the map.
   */
  public void addPending(DN dn)
  {
    pendingMap.putIfAbsent(dn, new CountDownLatch(1));
  }


  /**
   * Remove the specified DN from the pending map, it may not exist if the
   * entries are being migrated so just return.
   *
   * @param dn The DN to remove from the map.
   */
  public void removePending(DN dn)
  {
    CountDownLatch l = pendingMap.remove(dn);
    if(l != null)
    {
      l.countDown();
    }
  }


  /**
   * Return {@code true} if the specified dn is contained in the parent set, or
   * in the specifed DN cache. This would indicate that the parent has already
   * been processesd. It returns {@code false} otherwise.
   *
   * It will optionally check the dn2id database for the dn if the specifed
   * cleared backend boolean is {@code true}.
   *
   * @param dn The DN to check for.
   * @param dnCache The importer DN cache.
   * @param clearedBackend Set to {@code true} if the import process cleared the
   *                       backend before processing.
   * @return {@code true} if the dn is contained in the parent ID, or
   *         {@code false} otherwise.
   *
   * @throws DatabaseException If an error occurred searching the DN cache, or
   *                           dn2id database.
   * @throws InterruptedException If an error occurred processing the pending
   *                              map.
   */
  public
  boolean isParentProcessed(DN dn, DNCache dnCache, boolean clearedBackend)
                            throws DatabaseException, InterruptedException {
    synchronized(synchObject) {
      if(parentSet.contains(dn))
      {
        return true;
      }
    }
    //The DN was not in the parent set. Make sure it isn't pending.
    try {
      assureNotPending(dn);
    } catch (InterruptedException e) {
      Message message = ERR_JEB_IMPORT_LDIF_PENDING_ERR.get(e.getMessage());
      logError(message);
      throw e;
    }
    //Check the DN cache.
    boolean parentThere = dnCache.contains(dn);
    //If the parent isn't found in the DN cache, then check the dn2id database
    //for the DN only if the backend wasn't cleared.
    if(!parentThere && !clearedBackend)
    {
      if(getDN2ID().get(null, dn, LockMode.DEFAULT) != null)
      {
        parentThere = true;
      }
    }
    //Add the DN to the parent set if needed.
    if (parentThere) {
      synchronized(synchObject) {
        if (parentSet.size() >= PARENT_ID_SET_SIZE) {
          Iterator<DN> iterator = parentSet.iterator();
          iterator.next();
          iterator.remove();
        }
        parentSet.add(dn);
      }
    }
    return parentThere;
  }


  /**
   * Sets the trusted status of all of the indexes, vlvIndexes, id2children
   * and id2subtree indexes.
   *
   * @param trusted True if the indexes should be trusted or false
   *                otherwise.
   *
   * @throws DatabaseException If an error occurred setting the indexes to
   *                           trusted.
   */
  public void setIndexesTrusted(boolean trusted) throws DatabaseException
  {
    entryContainer.getID2Children().setTrusted(null,trusted);
    entryContainer.getID2Subtree().setTrusted(null, trusted);
    for(AttributeIndex attributeIndex :
            entryContainer.getAttributeIndexes()) {
      Index index;
      if((index = attributeIndex.getEqualityIndex()) != null) {
        index.setTrusted(null, trusted);
      }
      if((index=attributeIndex.getPresenceIndex()) != null) {
        index.setTrusted(null, trusted);
      }
      if((index=attributeIndex.getSubstringIndex()) != null) {
        index.setTrusted(null, trusted);
      }
      if((index=attributeIndex.getOrderingIndex()) != null) {
        index.setTrusted(null, trusted);
      }
      if((index=attributeIndex.getApproximateIndex()) != null) {
        index.setTrusted(null, trusted);
      }
      Map<String,Collection<Index>> exIndexes =
              attributeIndex.getExtensibleIndexes();
      if(!exIndexes.isEmpty())
      {
        Collection<Index> subIndexes = attributeIndex.getExtensibleIndexes().
                get(EXTENSIBLE_INDEXER_ID_SUBSTRING);
        if(subIndexes != null) {
          for(Index subIndex : subIndexes) {
            subIndex.setTrusted(null, trusted);
          }
        }
        Collection<Index> sharedIndexes = attributeIndex.
                getExtensibleIndexes().get(EXTENSIBLE_INDEXER_ID_SHARED);
        if(sharedIndexes !=null) {
          for(Index sharedIndex : sharedIndexes) {
            sharedIndex.setTrusted(null, trusted);
          }
        }
      }
    }
    for(VLVIndex vlvIdx : entryContainer.getVLVIndexes()) {
      vlvIdx.setTrusted(null, trusted);
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
