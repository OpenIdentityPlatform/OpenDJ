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

package org.opends.server.backends.jeb.importLDIF;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.*;
import org.opends.server.api.DirectoryThread;
import org.opends.server.backends.jeb.*;
import org.opends.messages.Message;
import static org.opends.messages.JebMessages.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.*;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Transaction;

/**
 * A thread to process import entries from a queue.  Multiple instances of
 * this class process entries from a single shared queue.
 */
public class WorkThread extends DirectoryThread {

  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /*
   * Work queue of work items.
   */
  private BlockingQueue<WorkElement> workQueue;


  /**
   * The number of entries imported by this thread.
   */
  private int importedCount = 0;

  //Root container.
  private RootContainer rootContainer;

  /**
   * A flag that is set when the thread has been told to stop processing.
   */
  private boolean stopRequested = false;

  //The thread number related to a thread.
  private int threadNumber;

  //The substring buffer manager to use.
  private BufferManager bufferMgr;

  /**
   * Create a work thread instance using the specified parameters.
   *
   * @param workQueue  The work queue to pull work off of.
   * @param threadNumber The thread number.
   * @param bufferMgr  The buffer manager to use.
   * @param rootContainer The root container.
   */
  public WorkThread(BlockingQueue<WorkElement> workQueue, int threadNumber,
                                BufferManager bufferMgr,
                                RootContainer rootContainer) {
    super("Import Worker Thread " + threadNumber);
    this.threadNumber = threadNumber;
    this.workQueue = workQueue;
    this.bufferMgr = bufferMgr;
    this.rootContainer = rootContainer;
  }

  /**
   * Get the number of entries imported by this thread.
   * @return The number of entries imported by this thread.
   */
   int getImportedCount() {
    return importedCount;
  }

  /**
   * Tells the thread to stop processing.
   */
   void stopProcessing() {
    stopRequested = true;
  }

  /**
   * Run the thread. Read from item from queue and give it to the
   * buffer manage, unless told to stop. Once stopped, ask buffer manager
   * to flush and exit.
   *
   */
  public void run()
  {
    try {
      do {
        try {
          WorkElement element = workQueue.poll(1000, TimeUnit.MILLISECONDS);
          if(element != null) {
           process(element);
          }
        }
        catch (InterruptedException e) {
          if (debugEnabled()) {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }
        }
      } while (!stopRequested);
    } catch (Exception e) {
      if (debugEnabled()) {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      throw new RuntimeException(e);
    }
  }

  /**
   * Process a work element.
   *
   * @param element The work elemenet to process.
   *
   * @throws DatabaseException If a database error occurs.
   * @throws DirectoryException If a directory error occurs.
   * @throws JebException If a JEB error occurs.
   */
  private void process(WorkElement element)
  throws DatabaseException, DirectoryException, JebException {
    Transaction txn = null;
    EntryID entryID;
    if((entryID = processDN2ID(element, txn)) == null)
      return;
    if(!processID2Entry(element, entryID, txn))
      return;
    procesID2SCEntry(element, entryID, txn);
    processIndexesEntry(element, entryID, txn);
  }

  /**
   * Delete all indexes related to the specified entry ID using the specified
   * entry to generate the keys.
   *
   * @param element The work element.
   * @param existingEntry The existing entry to replace.
   * @param entryID The entry ID to remove from the keys.
   * @param txn A transaction.
   * @throws DatabaseException If a database error occurs.
   */
  private void
  processIndexesEntryDelete(WorkElement element, Entry existingEntry,
                            EntryID entryID, Transaction txn)
          throws DatabaseException {
    DNContext context = element.getContext();
    Map<AttributeType, AttributeIndex> attrIndexMap =
            context.getAttrIndexMap();
    for(Map.Entry<AttributeType, AttributeIndex> mapEntry :
            attrIndexMap.entrySet()) {
      AttributeType attrType = mapEntry.getKey();
      if(existingEntry.hasAttribute(attrType)) {
        AttributeIndex attributeIndex = mapEntry.getValue();
        Index index;
        if((index=attributeIndex.getEqualityIndex()) != null) {
          delete(index, existingEntry, entryID, txn);
        }
        if((index=attributeIndex.getPresenceIndex()) != null) {
          delete(index, existingEntry, entryID, txn);
        }
        if((index=attributeIndex.getSubstringIndex()) != null) {
          delete(index, existingEntry, entryID, txn);
        }
        if((index=attributeIndex.getOrderingIndex()) != null) {
          delete(index, existingEntry, entryID, txn);
        }
        if((index=attributeIndex.getApproximateIndex()) != null) {
          delete(index, existingEntry, entryID, txn);
        }
      }
    }
  }

  /**
   * Process all indexes using the specified entry ID.
   *
   * @param element The work element.
   * @param entryID The entry ID to process.
   * @param txn A transaction.
   * @throws DatabaseException If an database error occurs.
   */
  private void
  processIndexesEntry(WorkElement element, EntryID entryID, Transaction txn)
          throws DatabaseException {
    Entry entry = element.getEntry();
    DNContext context = element.getContext();
    LDIFImportConfig ldifImportConfig = context.getLDIFImportConfig();
    if (ldifImportConfig.appendToExistingData() &&
            ldifImportConfig.replaceExistingEntries()) {
      Entry existingEntry = element.getExistingEntry();
      if(existingEntry != null) {
          processIndexesEntryDelete(element, existingEntry, entryID, txn);
      }
    }
    Map<AttributeType, AttributeIndex> attrIndexMap =
            context.getAttrIndexMap();
    for(Map.Entry<AttributeType, AttributeIndex> mapEntry :
            attrIndexMap.entrySet()) {
      AttributeType attrType = mapEntry.getKey();
      if(entry.hasAttribute(attrType)) {
        AttributeIndex attributeIndex = mapEntry.getValue();
        Index index;
        if((index=attributeIndex.getEqualityIndex()) != null) {
          insert(index, entry, entryID, txn);
        }
        if((index=attributeIndex.getPresenceIndex()) != null) {
          insert(index, entry, entryID, txn);
        }
        if((index=attributeIndex.getSubstringIndex()) != null) {
          bufferMgr.insert(index,entry, entryID, txn);
        }
        if((index=attributeIndex.getOrderingIndex()) != null) {
          insert(index, entry, entryID, txn);
        }
        if((index=attributeIndex.getApproximateIndex()) != null) {
          insert(index, entry, entryID, txn);
        }
      }
    }
  }

  /**
   * Process id2children/id2subtree indexes for the specified entry ID.
   *
   * @param element The work element.
   * @param entryID The entry ID to process.
   * @param txn A transaction.
   * @throws DatabaseException If an database error occurs.
   */
  private  void
  procesID2SCEntry(WorkElement element, EntryID entryID,
                   Transaction txn) throws DatabaseException {
    Entry entry = element.getEntry();
    DNContext context = element.getContext();
    LDIFImportConfig ldifImportConfig = context.getLDIFImportConfig();
    if (ldifImportConfig.appendToExistingData() &&
            ldifImportConfig.replaceExistingEntries()) {
      return;
    }
    Index id2children = context.getEntryContainer().getID2Children();
    Index id2subtree = context.getEntryContainer().getID2Subtree();
    bufferMgr.insert(id2children, id2subtree, entry, entryID, txn);
  }

  /**
   * Insert specified entry ID into the specified index using the entry to
   * generate the keys.
   *
   * @param index  The index to insert into.
   * @param entry The entry to generate the keys from.
   * @param entryID The entry ID to insert.
   * @param txn A transaction.
   * @return <CODE>True</CODE> if insert succeeded.
   * @throws DatabaseException If a database error occurs.
   */
  private boolean
  insert(Index index, Entry entry, EntryID entryID,
         Transaction txn) throws DatabaseException {
    Set<byte[]> keySet = new HashSet<byte[]>();
    index.indexer.indexEntry(txn, entry, keySet);
    return index.insert(txn, keySet,  entryID);
  }

  /**
   * Delete specified entry ID into the specified index using the entry to
   * generate the keys.
   *
   * @param index  The index to insert into.
   * @param entry The entry to generate the keys from.
   * @param entryID The entry ID to insert.
   * @param txn A transaction.
   * @throws DatabaseException If a database error occurs.
   */
  private void
  delete(Index index, Entry entry, EntryID entryID,
         Transaction txn) throws DatabaseException {
    Set<byte[]> keySet = new HashSet<byte[]>();
    index.indexer.indexEntry(txn, entry, keySet);
    index.delete(txn, keySet,  entryID);
  }

  /**
   * Insert entry from work element into id2entry DB.
   *
   * @param element The work element containing the entry.
   * @param entryID The entry ID to use as the key.
   * @param txn A transaction.
   * @return <CODE>True</CODE> If the insert succeeded.
   * @throws DatabaseException If a database error occurs.
   * @throws DirectoryException  If a directory error occurs.
   */
  private boolean
  processID2Entry(WorkElement element, EntryID entryID, Transaction txn)
          throws DatabaseException, DirectoryException {
    boolean ret;
    Entry entry = element.getEntry();
    DNContext context = element.getContext();
    ID2Entry id2entry = context.getEntryContainer().getID2Entry();
    DN2URI dn2uri = context.getEntryContainer().getDN2URI();
    ret=id2entry.put(txn, entryID, entry);
    if(ret) {
      importedCount++;
      LDIFImportConfig ldifImportConfig = context.getLDIFImportConfig();
      if (ldifImportConfig.appendToExistingData() &&
              ldifImportConfig.replaceExistingEntries()) {
        Entry existingEntry = element.getExistingEntry();
        if(existingEntry != null) {
          dn2uri.replaceEntry(txn, existingEntry, entry);
        }
      } else {
        ret= dn2uri.addEntry(txn, entry);
      }
    }
    return ret;
  }

  /**
   * Process entry from work element checking if it's parent exists.
   *
   * @param element The work element containing the entry.
   * @param txn A transaction.
   * @return <CODE>True</CODE> If the insert succeeded.
   * @throws DatabaseException If a database error occurs.
   */
  private boolean
  processParent(WorkElement element, Transaction txn)
          throws DatabaseException {
    Entry entry = element.getEntry();
    DNContext context = element.getContext();
    LDIFImportConfig ldifImportConfig = context.getLDIFImportConfig();
    if (ldifImportConfig.appendToExistingData() &&
            ldifImportConfig.replaceExistingEntries()) {
      return true;
    }
    EntryID parentID = null;
    DN entryDN = entry.getDN();
    DN parentDN = context.getEntryContainer().getParentWithinBase(entryDN);
    DN2ID dn2id = context.getEntryContainer().getDN2ID();
    if (parentDN != null) {
      parentID = context.getParentID(parentDN, dn2id, txn);
      if (parentID == null) {
        dn2id.remove(txn, entryDN);
        Message msg =
                ERR_JEB_IMPORT_PARENT_NOT_FOUND.get(parentDN.toString());
        context.getLDIFReader().rejectLastEntry(msg);
        return false;
      }
    }
    EntryID entryID = rootContainer.getNextEntryID();
    ArrayList<EntryID> IDs;
    if (parentDN != null && context.getParentDN() != null &&
            parentDN.equals(context.getParentDN())) {
      IDs = new ArrayList<EntryID>(context.getIDs());
      IDs.set(0, entryID);
    } else {
      EntryID nodeID;
      IDs = new ArrayList<EntryID>(entryDN.getNumComponents());
      IDs.add(entryID);
      if (parentID != null)
      {
        IDs.add(parentID);
        EntryContainer ec = context.getEntryContainer();
        for (DN dn = ec.getParentWithinBase(parentDN); dn != null;
             dn = ec.getParentWithinBase(dn)) {
          if((nodeID =  getAncestorID(dn2id, dn, txn)) == null) {
            return false;
          } else {
            IDs.add(nodeID);
          }
        }
      }
    }
    context.setParentDN(parentDN);
    context.setIDs(IDs);
    entry.setAttachment(IDs);
    return true;
  }

  private EntryID getAncestorID(DN2ID dn2id, DN dn, Transaction txn)
          throws DatabaseException {
    int i=0;
    EntryID nodeID = dn2id.get(txn, dn);
    if(nodeID == null) {
      while((nodeID = dn2id.get(txn, dn)) == null) {
        try {
          Thread.sleep(50);
          if(i == 3) {
            return null;
          }
          i++;
        } catch (Exception e) {
          return null;
        }
      }
    }
    return nodeID;
  }

  /**
   * Process the a entry from the work element into the dn2id DB.
   *
   * @param element The work element containing the entry.
   * @param txn A transaction.
   * @return An entry ID.
   * @throws DatabaseException If a database error occurs.
   * @throws JebException If a JEB error occurs.
   */
  private EntryID
  processDN2ID(WorkElement element, Transaction txn)
          throws DatabaseException, JebException {
    Entry entry = element.getEntry();
    DNContext context = element.getContext();
    DN2ID dn2id = context.getEntryContainer().getDN2ID();
    LDIFImportConfig ldifImportConfig = context.getLDIFImportConfig();
    DN entryDN = entry.getDN();
    EntryID entryID = dn2id.get(txn, entryDN);
    if (entryID != null) {
      if (ldifImportConfig.appendToExistingData() &&
              ldifImportConfig.replaceExistingEntries()) {
        ID2Entry id2entry = context.getEntryContainer().getID2Entry();
        Entry existingEntry = id2entry.get(txn, entryID);
        element.setExistingEntry(existingEntry);
      } else {
        Message msg = WARN_JEB_IMPORT_ENTRY_EXISTS.get();
        context.getLDIFReader().rejectLastEntry(msg);
        entryID = null;
      }
    } else {
      if(!processParent(element, txn))
         return null;
      ArrayList IDs = (ArrayList)entry.getAttachment();
      entryID = (EntryID)IDs.get(0);
      dn2id.insert(txn, entryDN, entryID);
    }
    context.removePending(entryDN);
    return entryID;
  }
}
