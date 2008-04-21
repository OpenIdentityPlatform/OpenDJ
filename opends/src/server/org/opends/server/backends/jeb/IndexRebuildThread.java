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
package org.opends.server.backends.jeb;
import org.opends.messages.Message;

import org.opends.server.api.DirectoryThread;

import com.sleepycat.je.*;

import org.opends.server.types.*;

import static org.opends.messages.JebMessages.
    ERR_JEB_MISSING_DN2ID_RECORD;
import static org.opends.messages.JebMessages.
    ERR_JEB_REBUILD_INDEX_FAILED;
import static org.opends.messages.JebMessages.
    ERR_JEB_REBUILD_INSERT_ENTRY_FAILED;
import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;


/**
 * A thread to do the actual work of rebuilding an index.
 */
public class IndexRebuildThread extends DirectoryThread
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * The entry container.
   */
  EntryContainer ec = null;

  /**
   * The internal database/indexType to rebuild.
   */
  IndexType indexType = null;

  /**
   * The attribute indexType to rebuild.
   */
  AttributeIndex attrIndex = null;

  /**
   * The VLV index to rebuild.
   */
  VLVIndex vlvIndex = null;

  /**
   * The indexType to rebuild.
   */
  Index index = null;

  /**
   * The ID2ENTRY database.
   */
  ID2Entry id2entry = null;

  /**
   * The number of total entries to rebuild. An negative value indicates this
   * value is not yet known.
   */
  long totalEntries = -1;

  /**
   * The number of entries processed.
   */
  long processedEntries = 0;

  /**
   * The number of entries rebuilt successfully.
   */
  long rebuiltEntries = 0;

  /**
   * The number of entries rebuilt with possible duplicates.
   */
  long duplicatedEntries = 0;

  /**
   * The number of entries that were skipped because they were not applicable
   * for the indexType or because an error occurred.
   */
  long skippedEntries = 0;

  /**
   * The types of internal indexes that are rebuildable.
   */
  enum IndexType
  {
    DN2ID, DN2URI, ID2CHILDREN, ID2SUBTREE, INDEX, ATTRIBUTEINDEX, VLVINDEX
  }

  /**
   * Construct a new index rebuild thread to rebuild a system index.
   *
   * @param ec The entry container to rebuild in.
   * @param index The index type to rebuild.
   */
  IndexRebuildThread(EntryContainer ec, IndexType index)
  {
    super("Index Rebuild Thread " + ec.getDatabasePrefix() + "_" +
        index.toString());
    this.ec = ec;
    this.indexType = index;
    this.id2entry = ec.getID2Entry();
  }

  /**
   * Construct a new index rebuild thread to rebuild an index.
   *
   * @param ec The entry container to rebuild in.
   * @param index The index to rebuild.
   */
  IndexRebuildThread(EntryContainer ec, Index index)
  {
    super("Index Rebuild Thread " + index.getName());
    this.ec = ec;
    this.indexType = IndexType.INDEX;
    this.index = index;
    this.id2entry = ec.getID2Entry();
  }

  /**
   * Construct a new index rebuild thread to rebuild an attribute index.
   *
   * @param ec The entry container to rebuild in.
   * @param index The attribute index to rebuild.
   */
  IndexRebuildThread(EntryContainer ec, AttributeIndex index)
  {
    super("Index Rebuild Thread " + index.getName());
    this.ec = ec;
    this.indexType = IndexType.ATTRIBUTEINDEX;
    this.attrIndex = index;
    this.id2entry = ec.getID2Entry();
  }

  /**
   * Construct a new index rebuild thread to rebuild an VLV index.
   *
   * @param ec The entry container to rebuild in.
   * @param vlvIndex The VLV index to rebuild.
   */
  IndexRebuildThread(EntryContainer ec, VLVIndex vlvIndex)
  {
    super("Index Rebuild Thread " + vlvIndex.getName());
    this.ec = ec;
    this.indexType = IndexType.VLVINDEX;
    this.vlvIndex = vlvIndex;
    this.id2entry = ec.getID2Entry();
  }

  /**
   * Clear the database and prep it for the rebuild.
   *
   * @throws DatabaseException if a JE databse error occurs while clearing
   * the database being rebuilt.
   */
  public void clearDatabase() throws DatabaseException
  {
    if(indexType == null)
    {
      //TODO: throw error
      if(debugEnabled())
      {
        TRACER.debugError("No index type specified. Rebuild process " +
            "terminated.");
      }

      return;
    }
    if(indexType == IndexType.ATTRIBUTEINDEX && attrIndex == null)
    {
      //TODO: throw error
      if(debugEnabled())
      {
        TRACER.debugError("No attribute index specified. Rebuild process " +
            "terminated.");
      }

      return;
    }

    if(indexType == IndexType.INDEX && index == null)
    {
      //TODO: throw error
      if(debugEnabled())
      {
        TRACER.debugError("No index specified. Rebuild process terminated.");
      }

      return;
    }

    if(indexType == IndexType.VLVINDEX && vlvIndex == null)
    {
      //TODO: throw error
      if(debugEnabled())
      {
        TRACER.debugError("No VLV index specified. Rebuild process " +
            "terminated.");
      }

      return;
    }

    switch(indexType)
    {
      case DN2ID :
        ec.clearDatabase(ec.getDN2ID());
        break;
      case DN2URI :
        ec.clearDatabase(ec.getDN2URI());
        break;
      case ID2CHILDREN :
        ec.clearDatabase(ec.getID2Children());
        ec.getID2Children().setRebuildStatus(true);
        break;
      case ID2SUBTREE :
        ec.clearDatabase(ec.getID2Subtree());
        ec.getID2Subtree().setRebuildStatus(true);
        break;
      case ATTRIBUTEINDEX :
        ec.clearAttributeIndex(attrIndex);
        attrIndex.setRebuildStatus(true);
        break;
      case VLVINDEX :
        ec.clearDatabase(vlvIndex);
        vlvIndex.setRebuildStatus(true);
        break;
      case INDEX :
        ec.clearDatabase(index);
        index.setRebuildStatus(true);
    }
  }

  /**
   * Start the rebuild process.
   */
  public void run()
  {
    if(indexType == null)
    {
      //TODO: throw error
      if(debugEnabled())
      {
        TRACER.debugError("No index type specified. Rebuild process " +
            "terminated.");
      }

      return;
    }
    if(indexType == IndexType.ATTRIBUTEINDEX && attrIndex == null)
    {
      //TODO: throw error
      if(debugEnabled())
      {
        TRACER.debugError("No attribute index specified. Rebuild process " +
            "terminated.");
      }

      return;
    }

    if(indexType == IndexType.INDEX && index == null)
    {
      //TODO: throw error
      if(debugEnabled())
      {
        TRACER.debugError("No index specified. Rebuild process terminated.");
      }

      return;
    }

    if(indexType == IndexType.VLVINDEX && vlvIndex == null)
    {
      //TODO: throw error
      if(debugEnabled())
      {
        TRACER.debugError("No VLV index specified. Rebuild process " +
            "terminated.");
      }

      return;
    }

    try
    {
      totalEntries = getTotalEntries();

      switch(indexType)
      {
        case DN2ID : rebuildDN2ID();
          break;
        case DN2URI : rebuildDN2URI();
          break;
        case ID2CHILDREN : rebuildID2Children();
          break;
        case ID2SUBTREE : rebuildID2Subtree();
          break;
        case ATTRIBUTEINDEX : rebuildAttributeIndex(attrIndex);
          break;
        case VLVINDEX : rebuildVLVIndex(vlvIndex);
          break;
        case INDEX : rebuildAttributeIndex(index);
      }

      if(debugEnabled())
      {
        TRACER.debugVerbose("Rebuilt %d entries", rebuiltEntries);
      }
    }
    catch(Exception e)
    {
      Message message = ERR_JEB_REBUILD_INDEX_FAILED.get(
          this.getName(), stackTraceToSingleLineString(e));
      logError(message);

      if(debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
    }
  }

  /**
   * Rebuild an interal DN2ID database.
   *
   * @throws DatabaseException If an error occurs during the rebuild.
   */
  private void rebuildDN2ID() throws DatabaseException
  {
    DN2ID dn2id = ec.getDN2ID();

    if(debugEnabled())
    {
      TRACER.debugInfo("Initiating rebuild of the %s database",
                       dn2id.getName());
      TRACER.debugVerbose("%d entries will be rebuilt", totalEntries);
    }


    //Iterate through the id2entry database and insert associated dn2id
    //records.
    Cursor cursor = id2entry.openCursor(null, CursorConfig.READ_COMMITTED);
    try
    {
      DatabaseEntry key = new DatabaseEntry();
      DatabaseEntry data = new DatabaseEntry();
      LockMode lockMode = LockMode.DEFAULT;

      OperationStatus status;
      for (status = cursor.getFirst(key, data, lockMode);
           status == OperationStatus.SUCCESS;
           status = cursor.getNext(key, data, lockMode))
      {
        Transaction txn = ec.beginTransaction();
        try
        {
          EntryID entryID = new EntryID(key);
          Entry entry = JebFormat.entryFromDatabase(data.getData(),
                             ec.getRootContainer().getCompressedSchema());

          // Insert into dn2id.
          if (dn2id.insert(txn, entry.getDN(), entryID))
          {
            rebuiltEntries++;
          }
          else
          {
            // The entry ID already exists in the database.
            // This could happen if some other process got to this entry
            // before we did. Since the backend should be offline, this
            // might be a problem.
            duplicatedEntries++;
            if(debugEnabled())
            {
              TRACER.debugInfo("Unable to insert entry with DN %s and ID %d " +
                  "into the DN2ID database because it already exists.",
                        entry.getDN().toString(), entryID.longValue());
            }
          }
          EntryContainer.transactionCommit(txn);
          processedEntries++;
        }
        catch (Exception e)
        {
          EntryContainer.transactionAbort(txn);
          skippedEntries++;

          Message message = ERR_JEB_REBUILD_INSERT_ENTRY_FAILED.get(
              dn2id.getName(), stackTraceToSingleLineString(e));
          logError(message);

          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }
        }
      }
    }
    finally
    {
      cursor.close();
    }
  }

  /**
   * Rebuild the ID2URI internal database.
   *
   * @throws DatabaseException if an error occurs during rebuild.
   */
  private void rebuildDN2URI() throws DatabaseException
  {
    DN2URI dn2uri = ec.getDN2URI();

    if(debugEnabled())
    {
      TRACER.debugInfo("Initiating rebuild of the %s database",
                       dn2uri.getName());
      TRACER.debugVerbose("%d entries will be rebuilt", totalEntries);
    }


    //Iterate through the id2entry database and insert associated dn2uri
    //records.
    Cursor cursor = id2entry.openCursor(null, CursorConfig.READ_COMMITTED);
    try
    {
      DatabaseEntry key = new DatabaseEntry();
      DatabaseEntry data = new DatabaseEntry();
      LockMode lockMode = LockMode.DEFAULT;


      OperationStatus status;
      for (status = cursor.getFirst(key, data, lockMode);
           status == OperationStatus.SUCCESS;
           status = cursor.getNext(key, data, lockMode))
      {
        Transaction txn = ec.beginTransaction();
        try
        {
          EntryID entryID = new EntryID(key);
          Entry entry = JebFormat.entryFromDatabase(data.getData(),
                             ec.getRootContainer().getCompressedSchema());

          // Insert into dn2uri.
          if (dn2uri.addEntry(txn, entry))
          {
            rebuiltEntries++;
          }
          else
          {
            // The entry DN and URIs already exists in the database.
            // This could happen if some other process got to this entry
            // before we did. Since the backend should be offline, this
            // might be a problem.
            duplicatedEntries++;
            if(debugEnabled())
            {
              TRACER.debugInfo("Unable to insert entry with DN %s and ID %d " +
                  "into the DN2URI database because it already exists.",
                        entry.getDN().toString(), entryID.longValue());
            }
          }
          EntryContainer.transactionCommit(txn);
          processedEntries++;
        }
        catch (Exception e)
        {
          EntryContainer.transactionAbort(txn);
          skippedEntries++;

          Message message = ERR_JEB_REBUILD_INSERT_ENTRY_FAILED.get(
              dn2uri.getName(), stackTraceToSingleLineString(e));
          logError(message);

          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }
        }
      }
    }
    finally
    {
      cursor.close();
    }
  }

  /**
   * Rebuild the ID2Subtree internal index. This depends on the DN2ID and DN2URI
   * databases being complete.
   *
   * @throws DatabaseException if an error occurs during rebuild.
   */
  private void rebuildID2Children() throws DatabaseException
  {
    Index id2children = ec.getID2Children();

    if(debugEnabled())
    {
      TRACER.debugInfo("Initiating rebuild of the %s index",
                       id2children.getName());
      TRACER.debugVerbose("%d entries will be rebuilt", totalEntries);
    }


    DN2ID dn2id = ec.getDN2ID();
    DN2URI dn2uri = ec.getDN2URI();

    //Iterate through the id2entry database and insert associated dn2children
    //records.
    Cursor cursor = id2entry.openCursor(null, CursorConfig.READ_COMMITTED);
    try
    {
      DatabaseEntry key = new DatabaseEntry();
      DatabaseEntry data = new DatabaseEntry();
      LockMode lockMode = LockMode.DEFAULT;

      OperationStatus status;
      for (status = cursor.getFirst(key, data, lockMode);
           status == OperationStatus.SUCCESS;
           status = cursor.getNext(key, data, lockMode))
      {
        Transaction txn = ec.beginTransaction();
        try
        {
          EntryID entryID = new EntryID(key);
          Entry entry = JebFormat.entryFromDatabase(data.getData(),
                             ec.getRootContainer().getCompressedSchema());

          // Check that the parent entry exists.
          DN parentDN = ec.getParentWithinBase(entry.getDN());
          if (parentDN != null)
          {
            // Check for referral entries above the target.
            dn2uri.targetEntryReferrals(entry.getDN(), null);

            // Read the parent ID from dn2id.
            EntryID parentID = dn2id.get(txn, parentDN, LockMode.DEFAULT);
            if (parentID != null)
            {
              // Insert into id2children for parent ID.
              if(id2children.insertID(txn, parentID.getDatabaseEntry(),
                                      entryID))
              {
                rebuiltEntries++;
              }
              else
              {
                // The entry already exists in the database.
                // This could happen if some other process got to this entry
                // before we did. Since the backend should be offline, this
                // might be a problem.
                if(debugEnabled())
                {
                  duplicatedEntries++;
                  TRACER.debugInfo("Unable to insert entry with DN %s and " +
                      "ID %d into the DN2Subtree database because it already " +
                      "exists.",
                            entry.getDN().toString(), entryID.longValue());
                }
              }
            }
            else
            {
              Message msg = ERR_JEB_MISSING_DN2ID_RECORD.get(
                  parentDN.toNormalizedString());
              throw new JebException(msg);
            }
          }
          else
          {
            skippedEntries++;
          }
          EntryContainer.transactionCommit(txn);
          processedEntries++;
        }
        catch (Exception e)
        {
          EntryContainer.transactionAbort(txn);
          skippedEntries++;

          Message message = ERR_JEB_REBUILD_INSERT_ENTRY_FAILED.get(
              id2children.getName(), stackTraceToSingleLineString(e));
          logError(message);

          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }
        }
      }
      id2children.setRebuildStatus(false);
      id2children.setTrusted(null, true);
    }
    finally
    {
      cursor.close();
    }
  }

  /**
   * Rebuild the ID2Subtree internal index. This depends on the DN2ID and DN2URI
   * databases being complete.
   *
   * @throws DatabaseException if an error occurs during rebuild.
   */
  private void rebuildID2Subtree() throws DatabaseException
  {
    Index id2subtree = ec.getID2Subtree();

    if(debugEnabled())
    {
      TRACER.debugInfo("Initiating rebuild of the %s index",
                       id2subtree.getName());
      TRACER.debugVerbose("%d entries will be rebuilt", totalEntries);
    }


    DN2ID dn2id = ec.getDN2ID();
    DN2URI dn2uri = ec.getDN2URI();

    //Iterate through the id2entry database and insert associated dn2subtree
    //records.
    Cursor cursor = id2entry.openCursor(null, CursorConfig.READ_COMMITTED);
    try
    {
      DatabaseEntry key = new DatabaseEntry();
      DatabaseEntry data = new DatabaseEntry();
      LockMode lockMode = LockMode.DEFAULT;

      OperationStatus status;
      for (status = cursor.getFirst(key, data, lockMode);
           status == OperationStatus.SUCCESS;
           status = cursor.getNext(key, data, lockMode))
      {
        Transaction txn = ec.beginTransaction();
        try
        {
          EntryID entryID = new EntryID(key);
          Entry entry = JebFormat.entryFromDatabase(data.getData(),
                             ec.getRootContainer().getCompressedSchema());

          // Check that the parent entry exists.
          DN parentDN = ec.getParentWithinBase(entry.getDN());
          if (parentDN != null)
          {
            boolean success = true;

            // Check for referral entries above the target.
            dn2uri.targetEntryReferrals(entry.getDN(), null);

            // Read the parent ID from dn2id.
            EntryID parentID = dn2id.get(txn, parentDN, LockMode.DEFAULT);
            if (parentID != null)
            {
              // Insert into id2subtree for parent ID.
              if(!id2subtree.insertID(txn, parentID.getDatabaseEntry(),
                                      entryID))
              {
                success = false;
              }

              // Iterate up through the superior entries, starting above the
              // parent.
              for (DN dn = ec.getParentWithinBase(parentDN); dn != null;
                   dn = ec.getParentWithinBase(dn))
              {
                // Read the ID from dn2id.
                EntryID nodeID = dn2id.get(null, dn, LockMode.DEFAULT);
                if (nodeID != null)
                {
                  // Insert into id2subtree for this node.
                  if(!id2subtree.insertID(null, nodeID.getDatabaseEntry(),
                                          entryID))
                  {
                    success = false;
                  }
                }
                else
                {
                  Message msg =
                      ERR_JEB_MISSING_DN2ID_RECORD.get(dn.toNormalizedString());
                  throw new JebException(msg);
                }
              }
            }
            else
            {
              Message msg = ERR_JEB_MISSING_DN2ID_RECORD.get(
                  parentDN.toNormalizedString());
              throw new JebException(msg);
            }

            if(success)
            {
              rebuiltEntries++;
            }
            else
            {
              // The entry already exists in the database.
              // This could happen if some other process got to this entry
              // before we did. Since the backend should be offline, this
              // might be a problem.
              if(debugEnabled())
              {
                duplicatedEntries++;
                TRACER.debugInfo("Unable to insert entry with DN %s and ID " +
                    "%d into the DN2Subtree database because it already " +
                    "exists.", entry.getDN().toString(), entryID.longValue());
              }
            }
          }
          else
          {
            skippedEntries++;
          }
          EntryContainer.transactionCommit(txn);
          processedEntries++;
        }
        catch (Exception e)
        {
          EntryContainer.transactionAbort(txn);
          skippedEntries++;

          Message message = ERR_JEB_REBUILD_INSERT_ENTRY_FAILED.get(
              id2subtree.getName(), stackTraceToSingleLineString(e));
          logError(message);

          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }
        }
      }
      id2subtree.setRebuildStatus(false);
      id2subtree.setTrusted(null, true);
    }
    finally
    {
      cursor.close();
    }
  }

  /**
   * Rebuild the attribute index.
   *
   * @param index The indexType to rebuild.
   * @throws DatabaseException if an error occurs during rebuild.
   */
  private void rebuildAttributeIndex(AttributeIndex index)
      throws DatabaseException
  {
    if(debugEnabled())
    {
      TRACER.debugInfo("Initiating rebuild of the %s index",
                       index.getName());
      TRACER.debugVerbose("%d entries will be rebuilt", totalEntries);
    }

    //Iterate through the id2entry database and insert associated indexType
    //records.
    Cursor cursor = id2entry.openCursor(null, CursorConfig.READ_COMMITTED);
    try
    {
      DatabaseEntry key = new DatabaseEntry();
      DatabaseEntry data = new DatabaseEntry();
      LockMode lockMode = LockMode.DEFAULT;

      OperationStatus status;
      for (status = cursor.getFirst(key, data, lockMode);
           status == OperationStatus.SUCCESS;
           status = cursor.getNext(key, data, lockMode))
      {
        Transaction txn = ec.beginTransaction();
        try
        {
          EntryID entryID = new EntryID(key);
          Entry entry = JebFormat.entryFromDatabase(data.getData(),
                             ec.getRootContainer().getCompressedSchema());

          // Insert into attribute indexType.
          if(index.addEntry(txn, entryID, entry))
          {
            rebuiltEntries++;
          }
          else
          {
            // The entry already exists in one or more entry sets.
            // This could happen if some other process got to this entry
            // before we did. Since the backend should be offline, this
            // might be a problem.
            if(debugEnabled())
            {
              duplicatedEntries++;
              TRACER.debugInfo("Unable to insert entry with DN %s and ID %d " +
                  "into the DN2Subtree database because it already " +
                  "exists.",
                        entry.getDN().toString(), entryID.longValue());
            }
          }
          EntryContainer.transactionCommit(txn);
          processedEntries++;
        }
        catch (Exception e)
        {
          EntryContainer.transactionAbort(txn);
          skippedEntries++;

          Message message = ERR_JEB_REBUILD_INSERT_ENTRY_FAILED.get(
              index.getName(), stackTraceToSingleLineString(e));
          logError(message);

          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }
        }
      }
      index.setRebuildStatus(false);
      index.setTrusted(null, true);
    }
    finally
    {
      cursor.close();
    }
  }

  /**
   * Rebuild the VLV index.
   *
   * @param vlvIndex The VLV index to rebuild.
   * @throws DatabaseException if an error occurs during rebuild.
   */
  private void rebuildVLVIndex(VLVIndex vlvIndex)
      throws DatabaseException
  {

    //Iterate through the id2entry database and insert associated indexType
    //records.
    Cursor cursor = id2entry.openCursor(null, CursorConfig.READ_COMMITTED);
    try
    {
      DatabaseEntry key = new DatabaseEntry();
      DatabaseEntry data = new DatabaseEntry();
      LockMode lockMode = LockMode.DEFAULT;

      OperationStatus status;
      for (status = cursor.getFirst(key, data, lockMode);
           status == OperationStatus.SUCCESS;
           status = cursor.getNext(key, data, lockMode))
      {
        Transaction txn = ec.beginTransaction();
        try
        {
          EntryID entryID = new EntryID(key);
          Entry entry = JebFormat.entryFromDatabase(data.getData(),
                             ec.getRootContainer().getCompressedSchema());

          // Insert into attribute indexType.
          if(vlvIndex.addEntry(txn, entryID, entry))
          {
            rebuiltEntries++;
          }
          else
          {
            // The entry already exists in one or more entry sets.
            // This could happen if some other process got to this entry
            // before we did. Since the backend should be offline, this
            // might be a problem.
            if(debugEnabled())
            {
              duplicatedEntries++;
              TRACER.debugInfo("Unable to insert entry with DN %s and ID %d " +
                  "into the VLV index %s because it already " +
                  "exists.",
                        entry.getDN().toString(), entryID.longValue(),
                        vlvIndex.getName());
            }
          }

          EntryContainer.transactionCommit(txn);
          processedEntries++;
        }
        catch (Exception e)
        {
          EntryContainer.transactionAbort(txn);
          skippedEntries++;

          Message message = ERR_JEB_REBUILD_INSERT_ENTRY_FAILED.get(
              vlvIndex.getName(), stackTraceToSingleLineString(e));
          logError(message);

          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }
        }
      }
      vlvIndex.setRebuildStatus(false);
      vlvIndex.setTrusted(null, true);
    }
    finally
    {
      cursor.close();
    }
  }

  /**
   * Rebuild the partial attribute index.
   *
   * @param index The indexType to rebuild.
   * @throws DatabaseException if an error occurs during rebuild.
   */
  private void rebuildAttributeIndex(Index index)
      throws DatabaseException
  {
    if(debugEnabled())
    {
      TRACER.debugInfo("Initiating rebuild of the %s attribute index",
                       index.getName());
      TRACER.debugVerbose("%d entries will be rebuilt", totalEntries);
    }

    //Iterate through the id2entry database and insert associated indexType
    //records.
    Cursor cursor = id2entry.openCursor(null, CursorConfig.READ_COMMITTED);
    try
    {
      DatabaseEntry key = new DatabaseEntry();
      DatabaseEntry data = new DatabaseEntry();
      LockMode lockMode = LockMode.DEFAULT;

      OperationStatus status;
      for (status = cursor.getFirst(key, data, lockMode);
           status == OperationStatus.SUCCESS;
           status = cursor.getNext(key, data, lockMode))
      {
        Transaction txn = ec.beginTransaction();
        try
        {
          EntryID entryID = new EntryID(key);
          Entry entry = JebFormat.entryFromDatabase(data.getData(),
                             ec.getRootContainer().getCompressedSchema());

          // Insert into attribute indexType.
          if(index.addEntry(txn, entryID, entry))
          {
            rebuiltEntries++;
          }
          else
          {
            // The entry already exists in one or more entry sets.
            // This could happen if some other process got to this entry
            // before we did. Since the backend should be offline, this
            // might be a problem.
            if(debugEnabled())
            {
              duplicatedEntries++;
              TRACER.debugInfo("Unable to insert entry with DN %s and ID %d " +
                  "into the DN2Subtree database because it already " +
                  "exists.",
                        entry.getDN().toString(), entryID.longValue());
            }
          }
          EntryContainer.transactionCommit(txn);
          processedEntries++;
        }
        catch (Exception e)
        {
          EntryContainer.transactionAbort(txn);
          skippedEntries++;

          Message message = ERR_JEB_REBUILD_INSERT_ENTRY_FAILED.get(
              index.getName(), stackTraceToSingleLineString(e));
          logError(message);

          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }
        }
      }
      index.setRebuildStatus(false);
      index.setTrusted(null, true);
    }
    finally
    {
      cursor.close();
    }
  }

  /**
   * Get the total entries to process in the rebuild.
   *
   * @return The total entries to process.
   * @throws DatabaseException if an error occurs while getting the total
   *         number of entries to process.
   */
  public long getTotalEntries() throws DatabaseException
  {
    //If total entries is not calculated yet, do it now.
    if(totalEntries < 0)
    {
      totalEntries = id2entry.getRecordCount();
    }
    return totalEntries;
  }

  /**
   * Get the number of entries processed in the rebuild.
   *
   * @return The total entries processed.
   */
  public long getProcessedEntries()
  {
    return processedEntries;
  }

  /**
   * Get the number of entries successfully rebuilt.
   *
   * @return The number of entries successfully rebuilt.
   */
  public long getRebuiltEntries()
  {
    return rebuiltEntries;
  }

  /**
   * Get the number of entries that encountered duplicated indexType values in
   * the rebuild process.
   *
   * @return The number of entries that encountered duplicated indexType values
   *         in the rebuild process.
   */
  public long getDuplicatedEntries()
  {
    return duplicatedEntries;
  }

  /**
   * Get the number of entries skipped because they were either not applicable
   * or an error occurred during the process.
   *
   * @return The number of entries skipped.
   */
  public long getSkippedEntries()
  {
    return skippedEntries;
  }

  /**
   * Get the index type being rebuilt by this thread.
   *
   * @return The index type being rebuilt by this thread.
   */
  public IndexType getIndexType()
  {
    return indexType;
  }
}


