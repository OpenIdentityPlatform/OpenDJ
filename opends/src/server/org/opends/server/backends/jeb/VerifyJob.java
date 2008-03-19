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

import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.CursorConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.EnvironmentStats;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.StatsConfig;
import com.sleepycat.je.Transaction;

import org.opends.server.api.OrderingMatchingRule;
import org.opends.server.api.ApproximateMatchingRule;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.ServerConstants;

import org.opends.server.types.*;
import static org.opends.messages.JebMessages.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

/**
 * This class is used to run an index verification process on the backend.
 */
public class VerifyJob
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();


  /**
   * The verify configuration.
   */
  private VerifyConfig verifyConfig;

  /**
   * The root container used for the verify job.
   */
  RootContainer rootContainer;

  /**
   * The number of milliseconds between job progress reports.
   */
  private long progressInterval = 10000;

  /**
   * The number of index keys processed.
   */
  private long keyCount = 0;

  /**
   * The number of errors found.
   */
  private long errorCount = 0;

  /**
   * The number of records that have exceeded the entry limit.
   */
  long entryLimitExceededCount = 0;

  /**
   * The number of records that reference more than one entry.
   */
  long multiReferenceCount = 0;

  /**
   * The total number of entry references.
   */
  long entryReferencesCount = 0;

  /**
   * The maximum number of references per record.
   */
  long maxEntryPerValue = 0;

  /**
   * This map is used to gather some statistics about values that have
   * exceeded the entry limit.
   */
  IdentityHashMap<Index,HashMap<ByteString,Long>> entryLimitMap =
       new IdentityHashMap<Index, HashMap<ByteString, Long>>();

  /**
   * Indicates whether the DN database is to be verified.
   */
  private boolean verifyDN2ID = false;

  /**
   * Indicates whether the children database is to be verified.
   */
  private boolean verifyID2Children = false;

  /**
   * Indicates whether the subtree database is to be verified.
   */
  private boolean verifyID2Subtree = false;

  /**
   * The entry database.
   */
  ID2Entry id2entry = null;

  /**
   * The DN database.
   */
  DN2ID dn2id = null;

  /**
   * The children database.
   */
  Index id2c = null;

  /**
   * The subtree database.
   */
  Index id2s = null;

  /**
   * A list of the attribute indexes to be verified.
   */
  ArrayList<AttributeIndex> attrIndexList = new ArrayList<AttributeIndex>();

  /**
   * A list of the VLV indexes to be verified.
   */
  ArrayList<VLVIndex> vlvIndexList = new ArrayList<VLVIndex>();

/**
 * The types of indexes that are verifiable.
 */
  enum IndexType
  {
      PRES, EQ, SUBSTRING, ORDERING, APPROXIMATE
  }

  /**
   * Construct a VerifyJob.
   *
   * @param verifyConfig The verify configuration.
   */
  public VerifyJob(VerifyConfig verifyConfig)
  {
    this.verifyConfig = verifyConfig;
  }

  /**
   * Verify the backend.
   *
   * @param rootContainer The root container that holds the entries to verify.
   * @param statEntry Optional statistics entry.
   * @return The error count.
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws JebException If an error occurs in the JE backend.
   * @throws DirectoryException If an error occurs while verifying the backend.
   */
  public long verifyBackend(RootContainer rootContainer, Entry statEntry) throws
      DatabaseException, JebException, DirectoryException
  {
    this.rootContainer = rootContainer;
    EntryContainer entryContainer =
        rootContainer.getEntryContainer(verifyConfig.getBaseDN());

    entryContainer.sharedLock.lock();
    try
    {
      ArrayList<String> completeList = verifyConfig.getCompleteList();
      ArrayList<String> cleanList = verifyConfig.getCleanList();

      boolean cleanMode = false;
      if (completeList.isEmpty() && cleanList.isEmpty())
      {
        verifyDN2ID = true;
        verifyID2Children = true;
        verifyID2Subtree = true;
        attrIndexList.addAll(entryContainer.getAttributeIndexes());
      }
      else
      {
        ArrayList<String> list;
        if (!completeList.isEmpty())
        {
          list = completeList;
        }
        else
        {
          list = cleanList;
          cleanMode = true;
        }

        for (String index : list)
        {
          String lowerName = index.toLowerCase();
          if (lowerName.equals("dn2id"))
          {
            verifyDN2ID = true;
          }
          else if (lowerName.equals("id2children"))
          {
            verifyID2Children = true;
          }
          else if (lowerName.equals("id2subtree"))
          {
            verifyID2Subtree = true;
          }
          else if(lowerName.startsWith("vlv."))
          {
            if(lowerName.length() < 5)
            {
              Message msg = ERR_JEB_VLV_INDEX_NOT_CONFIGURED.get(lowerName);
              throw new JebException(msg);
            }

            VLVIndex vlvIndex =
                entryContainer.getVLVIndex(lowerName.substring(4));
            if(vlvIndex == null)
            {
              Message msg =
                  ERR_JEB_VLV_INDEX_NOT_CONFIGURED.get(lowerName.substring(4));
              throw new JebException(msg);
            }

            vlvIndexList.add(vlvIndex);
          }
          else
          {
            AttributeType attrType =
                DirectoryServer.getAttributeType(lowerName);
            if (attrType == null)
            {
              Message msg = ERR_JEB_ATTRIBUTE_INDEX_NOT_CONFIGURED.get(index);
              throw new JebException(msg);
            }
            AttributeIndex attrIndex =
                entryContainer.getAttributeIndex(attrType);
            if (attrIndex == null)
            {
              Message msg = ERR_JEB_ATTRIBUTE_INDEX_NOT_CONFIGURED.get(index);
              throw new JebException(msg);
            }
            attrIndexList.add(attrIndex);
          }
        }
      }

      entryLimitMap =
          new IdentityHashMap<Index,HashMap<ByteString,Long>>(
              attrIndexList.size());

      // We will be updating these files independently of the indexes
      // so we need direct access to them rather than going through
      // the entry entryContainer methods.
      id2entry = entryContainer.getID2Entry();
      dn2id = entryContainer.getDN2ID();
      id2c = entryContainer.getID2Children();
      id2s = entryContainer.getID2Subtree();

      // Make a note of the time we started.
      long startTime = System.currentTimeMillis();

      // Start a timer for the progress report.
      Timer timer = new Timer();
      TimerTask progressTask = new ProgressTask();
      timer.scheduleAtFixedRate(progressTask, progressInterval,
                                progressInterval);

      // Iterate through the index keys.
      try
      {
        if (cleanMode)
        {
          iterateIndex();
        }
        else
        {
          iterateID2Entry();

          // Make sure the vlv indexes are in correct order.
          for(VLVIndex vlvIndex : vlvIndexList)
          {
            iterateVLVIndex(vlvIndex, false);
          }
        }
      }
      finally
      {
        timer.cancel();
      }

      long finishTime = System.currentTimeMillis();
      long totalTime = (finishTime - startTime);

      float rate = 0;
      if (totalTime > 0)
      {
        rate = 1000f*keyCount / totalTime;
      }

      addStatEntry(statEntry, "verify-error-count",
                   String.valueOf(errorCount));
      addStatEntry(statEntry, "verify-key-count",
                   String.valueOf(keyCount));
      if (cleanMode)
      {
        Message message = NOTE_JEB_VERIFY_CLEAN_FINAL_STATUS.get(
            keyCount, errorCount, totalTime/1000, rate);
        logError(message);

        if (multiReferenceCount > 0)
        {
          float averageEntryReferences = 0;
          if (keyCount > 0)
          {
            averageEntryReferences = (float)entryReferencesCount/keyCount;
          }

          message =
              INFO_JEB_VERIFY_MULTIPLE_REFERENCE_COUNT.get(multiReferenceCount);
          logError(message);
          addStatEntry(statEntry, "verify-multiple-reference-count",
                       String.valueOf(multiReferenceCount));

          message = INFO_JEB_VERIFY_ENTRY_LIMIT_EXCEEDED_COUNT.get(
              entryLimitExceededCount);
          logError(message);
          addStatEntry(statEntry, "verify-entry-limit-exceeded-count",
                       String.valueOf(entryLimitExceededCount));

          message = INFO_JEB_VERIFY_AVERAGE_REFERENCE_COUNT.get(
              averageEntryReferences);
          logError(message);
          addStatEntry(statEntry, "verify-average-reference-count",
                       String.valueOf(averageEntryReferences));

          message =
              INFO_JEB_VERIFY_MAX_REFERENCE_COUNT.get(maxEntryPerValue);
          logError(message);
          addStatEntry(statEntry, "verify-max-reference-count",
                       String.valueOf(maxEntryPerValue));
        }
      }
      else
      {
        Message message = NOTE_JEB_VERIFY_FINAL_STATUS.get(
            keyCount, errorCount, totalTime/1000, rate);
        logError(message);
        //TODO add entry-limit-stats to the statEntry
        if (entryLimitMap.size() > 0)
        {
          message = INFO_JEB_VERIFY_ENTRY_LIMIT_STATS_HEADER.get();
          logError(message);

          for (Map.Entry<Index,HashMap<ByteString,Long>> mapEntry :
              entryLimitMap.entrySet())
          {
            Index index = mapEntry.getKey();
            Long[] values = mapEntry.getValue().values().toArray(new Long[0]);

            // Calculate the median value for entry limit exceeded.
            Arrays.sort(values);
            long medianValue;
            int x = values.length / 2;
            if (values.length % 2 == 0)
            {
              medianValue = (values[x] + values[x-1]) / 2;
            }
            else
            {
              medianValue = values[x];
            }

            message = INFO_JEB_VERIFY_ENTRY_LIMIT_STATS_ROW.
                get(index.toString(), values.length, values[0],
                    values[values.length-1], medianValue);
            logError(message);
          }
        }
      }
    }
    finally
    {
      entryContainer.sharedLock.unlock();
    }
    return errorCount;
  }

  /**
   * Iterate through the entries in id2entry to perform a check for
   * index completeness. We check that the ID for the entry is indeed
   * present in the indexes for the appropriate values.
   *
   * @throws DatabaseException If an error occurs in the JE database.
   */
  private void iterateID2Entry() throws DatabaseException
  {
    Cursor cursor = id2entry.openCursor(null, new CursorConfig());
    try
    {
      DatabaseEntry key = new DatabaseEntry();
      DatabaseEntry data = new DatabaseEntry();

      Long storedEntryCount = id2entry.getRecordCount();

      OperationStatus status;
      for (status = cursor.getFirst(key, data, LockMode.DEFAULT);
           status == OperationStatus.SUCCESS;
           status = cursor.getNext(key, data, LockMode.DEFAULT))
      {
        EntryID entryID;
        try
        {
          entryID = new EntryID(key);
        }
        catch (Exception e)
        {
          errorCount++;
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);

            TRACER.debugError("Malformed id2entry ID %s.%n",
                       StaticUtils.bytesToHex(key.getData()));
          }
          continue;
        }

        keyCount++;

        Entry entry;
        try
        {
          entry = JebFormat.entryFromDatabase(data.getData(),
                                 rootContainer.getCompressedSchema());
        }
        catch (Exception e)
        {
          errorCount++;
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);

            TRACER.debugError("Malformed id2entry record for ID %d:%n%s%n",
                       entryID.longValue(),
                       StaticUtils.bytesToHex(data.getData()));
          }
          continue;
        }

        verifyEntry(entryID, entry);
      }
      if (keyCount != storedEntryCount)
      {
        errorCount++;
        if (debugEnabled())
        {
          TRACER.debugError("The stored entry count in id2entry (%d) does " +
              "not agree with the actual number of entry " +
              "records found (%d).%n", storedEntryCount, keyCount);
        }
      }
    }
    finally
    {
      cursor.close();
    }
  }

  /**
   * Iterate through the entries in an index to perform a check for
   * index cleanliness. For each ID in the index we check that the
   * entry it refers to does indeed contain the expected value.
   *
   * @throws JebException If an error occurs in the JE backend.
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws DirectoryException If an error occurs reading values in the index.
   */
  private void iterateIndex()
      throws JebException, DatabaseException, DirectoryException
  {
    if (verifyDN2ID)
    {
      iterateDN2ID();
    }
    else if (verifyID2Children)
    {
      iterateID2Children();
    }
    else if (verifyID2Subtree)
    {
      iterateID2Subtree();
    }
    else
    {
      if(attrIndexList.size() > 0)
      {
        AttributeIndex attrIndex = attrIndexList.get(0);
        iterateAttrIndex(attrIndex.getAttributeType(),
                         attrIndex.equalityIndex, IndexType.EQ );
        iterateAttrIndex(attrIndex.getAttributeType(),
                         attrIndex.presenceIndex, IndexType.PRES);
        iterateAttrIndex(attrIndex.getAttributeType(),
                         attrIndex.substringIndex, IndexType.SUBSTRING);
        iterateAttrIndex(attrIndex.getAttributeType(),
                         attrIndex.orderingIndex, IndexType.ORDERING);
        iterateAttrIndex(attrIndex.getAttributeType(),
                         attrIndex.approximateIndex, IndexType.APPROXIMATE);
      } else if(vlvIndexList.size() > 0)
      {
        iterateVLVIndex(vlvIndexList.get(0), true);
      }
    }
  }

  /**
   * Iterate through the entries in DN2ID to perform a check for
   * index cleanliness.
   *
   * @throws DatabaseException If an error occurs in the JE database.
   */
  private void iterateDN2ID() throws DatabaseException
  {
    Cursor cursor = dn2id.openCursor(null, new CursorConfig());
    try
    {
      DatabaseEntry key = new DatabaseEntry();
      DatabaseEntry data = new DatabaseEntry();

      OperationStatus status;
      for (status = cursor.getFirst(key, data, LockMode.DEFAULT);
           status == OperationStatus.SUCCESS;
           status = cursor.getNext(key, data, LockMode.DEFAULT))
      {
        keyCount++;

        DN dn;
        try
        {
          dn = DN.decode(new ASN1OctetString(key.getData()));
        }
        catch (DirectoryException e)
        {
          errorCount++;
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);

            TRACER.debugError("File dn2id has malformed key %s.%n",
                       StaticUtils.bytesToHex(key.getData()));
          }
          continue;
        }

        EntryID entryID;
        try
        {
          entryID = new EntryID(data);
        }
        catch (Exception e)
        {
          errorCount++;
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);

            TRACER.debugError("File dn2id has malformed ID for DN <%s>:%n%s%n",
                       dn.toNormalizedString(),
                       StaticUtils.bytesToHex(data.getData()));
          }
          continue;
        }

        Entry entry;
        try
        {
          entry = id2entry.get(null, entryID);
        }
        catch (Exception e)
        {
          errorCount++;
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }
          continue;
        }

        if (entry == null)
        {
          errorCount++;
          if (debugEnabled())
          {
            TRACER.debugError("File dn2id has DN <%s> referencing unknown " +
                "ID %d%n", dn.toNormalizedString(), entryID.longValue());
          }
        }
        else
        {
          if (!entry.getDN().equals(dn))
          {
            errorCount++;
            if (debugEnabled())
            {
              TRACER.debugError("File dn2id has DN <%s> referencing entry " +
                  "with wrong DN <%s>%n", dn.toNormalizedString(),
                                          entry.getDN().toNormalizedString());
            }
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
   * Iterate through the entries in ID2Children to perform a check for
   * index cleanliness.
   *
   * @throws JebException If an error occurs in the JE backend.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  private void iterateID2Children() throws JebException, DatabaseException
  {
    Cursor cursor = id2c.openCursor(null, new CursorConfig());
    try
    {
      DatabaseEntry key = new DatabaseEntry();
      DatabaseEntry data = new DatabaseEntry();

      OperationStatus status;
      for (status = cursor.getFirst(key, data, LockMode.DEFAULT);
           status == OperationStatus.SUCCESS;
           status = cursor.getNext(key, data, LockMode.DEFAULT))
      {
        keyCount++;

        EntryID entryID;
        try
        {
          entryID = new EntryID(key);
        }
        catch (Exception e)
        {
          errorCount++;
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);

            TRACER.debugError("File id2children has malformed ID %s%n",
                       StaticUtils.bytesToHex(key.getData()));
          }
          continue;
        }

        EntryIDSet entryIDList;

        try
        {
          JebFormat.entryIDListFromDatabase(data.getData());
          entryIDList = new EntryIDSet(key.getData(), data.getData());
        }
        catch (Exception e)
        {
          errorCount++;
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);

            TRACER.debugError("File id2children has malformed ID list " +
                "for ID %s:%n%s%n", entryID,
                                    StaticUtils.bytesToHex(data.getData()));
          }
          continue;
        }

        updateIndexStats(entryIDList);

        if (entryIDList.isDefined())
        {
          Entry entry;
          try
          {
            entry = id2entry.get(null, entryID);
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }
            errorCount++;
            continue;
          }

          if (entry == null)
          {
            errorCount++;
            if (debugEnabled())
            {
              TRACER.debugError("File id2children has unknown ID %d%n",
                         entryID.longValue());
            }
            continue;
          }

          for (EntryID id : entryIDList)
          {
            Entry childEntry;
            try
            {
              childEntry = id2entry.get(null, id);
            }
            catch (Exception e)
            {
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, e);
              }
              errorCount++;
              continue;
            }

            if (childEntry == null)
            {
              errorCount++;
              if (debugEnabled())
              {
                TRACER.debugError("File id2children has ID %d referencing " +
                    "unknown ID %d%n", entryID.longValue(), id.longValue());
              }
              continue;
            }

            if (!childEntry.getDN().isDescendantOf(entry.getDN()) ||
                 childEntry.getDN().getNumComponents() !=
                 entry.getDN().getNumComponents() + 1)
            {
              errorCount++;
              if (debugEnabled())
              {
                TRACER.debugError("File id2children has ID %d with DN <%s> " +
                    "referencing ID %d with non-child DN <%s>%n",
                           entryID.longValue(), entry.getDN().toString(),
                           id.longValue(), childEntry.getDN().toString());
              }
            }
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
   * Iterate through the entries in ID2Subtree to perform a check for
   * index cleanliness.
   *
   * @throws JebException If an error occurs in the JE backend.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  private void iterateID2Subtree() throws JebException, DatabaseException
  {
    Cursor cursor = id2s.openCursor(null, new CursorConfig());
    try
    {
      DatabaseEntry key = new DatabaseEntry();
      DatabaseEntry data = new DatabaseEntry();

      OperationStatus status;
      for (status = cursor.getFirst(key, data, LockMode.DEFAULT);
           status == OperationStatus.SUCCESS;
           status = cursor.getNext(key, data, LockMode.DEFAULT))
      {
        keyCount++;

        EntryID entryID;
        try
        {
          entryID = new EntryID(key);
        }
        catch (Exception e)
        {
          errorCount++;
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);

            TRACER.debugError("File id2subtree has malformed ID %s%n",
                       StaticUtils.bytesToHex(key.getData()));
          }
          continue;
        }

        EntryIDSet entryIDList;
        try
        {
          JebFormat.entryIDListFromDatabase(data.getData());
          entryIDList = new EntryIDSet(key.getData(), data.getData());
        }
        catch (Exception e)
        {
          errorCount++;
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);

            TRACER.debugError("File id2subtree has malformed ID list " +
                "for ID %s:%n%s%n", entryID,
                                    StaticUtils.bytesToHex(data.getData()));
          }
          continue;
        }

        updateIndexStats(entryIDList);

        if (entryIDList.isDefined())
        {
          Entry entry;
          try
          {
            entry = id2entry.get(null, entryID);
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }
            errorCount++;
            continue;
          }

          if (entry == null)
          {
            errorCount++;
            if (debugEnabled())
            {
              TRACER.debugError("File id2subtree has unknown ID %d%n",
                         entryID.longValue());
            }
            continue;
          }

          for (EntryID id : entryIDList)
          {
            Entry subordEntry;
            try
            {
              subordEntry = id2entry.get(null, id);
            }
            catch (Exception e)
            {
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, e);
              }
              errorCount++;
              continue;
            }

            if (subordEntry == null)
            {
              errorCount++;
              if (debugEnabled())
              {
                TRACER.debugError("File id2subtree has ID %d referencing " +
                    "unknown ID %d%n", entryID.longValue(), id.longValue());
              }
              continue;
            }

            if (!subordEntry.getDN().isDescendantOf(entry.getDN()))
            {
              errorCount++;
              if (debugEnabled())
              {
                TRACER.debugError("File id2subtree has ID %d with DN <%s> " +
                    "referencing ID %d with non-subordinate " +
                    "DN <%s>%n",
                           entryID.longValue(), entry.getDN().toString(),
                           id.longValue(), subordEntry.getDN().toString());
              }
            }
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
   * Increment the counter for a key that has exceeded the
   * entry limit. The counter gives the number of entries that have
   * referenced the key.
   *
   * @param index The index containing the key.
   * @param key A key that has exceeded the entry limit.
   */
  private void incrEntryLimitStats(Index index, byte[] key)
  {
    HashMap<ByteString,Long> hashMap = entryLimitMap.get(index);
    if (hashMap == null)
    {
      hashMap = new HashMap<ByteString, Long>();
      entryLimitMap.put(index, hashMap);
    }
    ByteString octetString = new ASN1OctetString(key);
    Long counter = hashMap.get(octetString);
    if (counter == null)
    {
      counter = 1L;
    }
    else
    {
      counter++;
    }
    hashMap.put(octetString, counter);
  }

  /**
   * Update the statistical information for an index record.
   *
   * @param entryIDSet The set of entry IDs for the index record.
   */
  private void updateIndexStats(EntryIDSet entryIDSet)
  {
    if (!entryIDSet.isDefined())
    {
      entryLimitExceededCount++;
      multiReferenceCount++;
    }
    else
    {
      if (entryIDSet.size() > 1)
      {
        multiReferenceCount++;
      }
      entryReferencesCount += entryIDSet.size();
      maxEntryPerValue = Math.max(maxEntryPerValue, entryIDSet.size());
    }
  }

  /**
   * Iterate through the entries in a VLV index to perform a check for index
   * cleanliness.
   *
   * @param vlvIndex The VLV index to perform the check against.
   * @param verifyID True to verify the IDs against id2entry.
   * @throws JebException If an error occurs in the JE backend.
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws DirectoryException If an error occurs reading values in the index.
   */
  private void iterateVLVIndex(VLVIndex vlvIndex, boolean verifyID)
      throws JebException, DatabaseException, DirectoryException
  {
    if(vlvIndex == null)
    {
      return;
    }

    Cursor cursor = vlvIndex.openCursor(null, new CursorConfig());
    try
    {
      DatabaseEntry key = new DatabaseEntry();
      OperationStatus status;
      LockMode lockMode = LockMode.DEFAULT;
      DatabaseEntry data = new DatabaseEntry();

      status = cursor.getFirst(key, data, lockMode);
      SortValues lastValues = null;
      while(status == OperationStatus.SUCCESS)
      {
        SortValuesSet sortValuesSet =
            new SortValuesSet(key.getData(), data.getData(), vlvIndex,
                              id2entry);
        for(int i = 0; i < sortValuesSet.getEntryIDs().length; i++)
        {
          keyCount++;
          SortValues values = sortValuesSet.getSortValues(i);
          if(lastValues != null && lastValues.compareTo(values) >= 1)
          {
            // Make sure the values is larger then the previous one.
            if(debugEnabled())
            {
              TRACER.debugError("Values %s and %s are incorrectly ordered",
                                lastValues, values, keyDump(vlvIndex,
                                          sortValuesSet.getKeySortValues()));
            }
            errorCount++;
          }
          if(i == sortValuesSet.getEntryIDs().length - 1 &&
              key.getData().length != 0)
          {
            // If this is the last one in a bounded set, make sure it is the
            // same as the database key.
            byte[] encodedKey = vlvIndex.encodeKey(values.getEntryID(),
                                                   values.getValues());
            if(!Arrays.equals(key.getData(), encodedKey))
            {
              if(debugEnabled())
              {
                TRACER.debugError("Incorrect key for SortValuesSet in VLV " +
                    "index %s. Last values bytes %s, Key bytes %s",
                                  vlvIndex.getName(), encodedKey, key);
              }
              errorCount++;
            }
          }
          lastValues = values;

          if(verifyID)
          {
            Entry entry;
            EntryID id = new EntryID(values.getEntryID());
            try
            {
              entry = id2entry.get(null, id);
            }
            catch (Exception e)
            {
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, e);
              }
              errorCount++;
              continue;
            }

            if (entry == null)
            {
              errorCount++;
              if (debugEnabled())
              {
                TRACER.debugError("Reference to unknown ID %d%n%s",
                                  id.longValue(),
                                  keyDump(vlvIndex,
                                          sortValuesSet.getKeySortValues()));
              }
              continue;
            }

            SortValues entryValues =
                new SortValues(id, entry, vlvIndex.sortOrder);
            if(entryValues.compareTo(values) != 0)
            {
              errorCount++;
              if(debugEnabled())
              {
                TRACER.debugError("Reference to entry ID %d " +
                    "which does not match the values%n%s",
                                  id.longValue(),
                                  keyDump(vlvIndex,
                                          sortValuesSet.getKeySortValues()));
              }
            }
          }
        }
        status = cursor.getNext(key, data, lockMode);
      }
    }
    finally
    {
      cursor.close();
    }
  }

  /**
   * Iterate through the entries in an attribute index to perform a check for
   * index cleanliness.
   * @param attrType The attribute type of the index to be checked.
   * @param index The index database to be checked.
   * @param indexType Type of the index (ie, SUBSTRING, ORDERING)
   * @throws JebException If an error occurs in the JE backend.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  private void iterateAttrIndex(AttributeType attrType,
          Index index, IndexType indexType)
       throws JebException, DatabaseException
  {
    if (index == null)
    {
      return;
    }

    Cursor cursor = index.openCursor(null, new CursorConfig());
    try
    {
      DatabaseEntry key = new DatabaseEntry();
      DatabaseEntry data = new DatabaseEntry();

      OrderingMatchingRule orderingMatchingRule =
          attrType.getOrderingMatchingRule();
      ApproximateMatchingRule approximateMatchingRule =
          attrType.getApproximateMatchingRule();
      ASN1OctetString previousValue = null;

      OperationStatus status;
      for (status = cursor.getFirst(key, data, LockMode.DEFAULT);
           status == OperationStatus.SUCCESS;
           status = cursor.getNext(key, data, LockMode.DEFAULT))
      {
        keyCount++;

        EntryIDSet entryIDList;
        try
        {
          JebFormat.entryIDListFromDatabase(data.getData());
          entryIDList = new EntryIDSet(key.getData(), data.getData());
        }
        catch (Exception e)
        {
          errorCount++;
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);

            TRACER.debugError("Malformed ID list: %s%n%s",
                       StaticUtils.bytesToHex(data.getData()),
                       keyDump(index, key.getData()));
          }
          continue;
        }

        updateIndexStats(entryIDList);

        if (entryIDList.isDefined())
        {
          byte[] value = key.getData();
          SearchFilter sf;
          AttributeValue assertionValue;

          switch (indexType)
          {
            case SUBSTRING:
              ArrayList<ByteString> subAnyElements =
                   new ArrayList<ByteString>(1);
              subAnyElements.add(new ASN1OctetString(value));

              sf = SearchFilter.createSubstringFilter(attrType,null,
                                                      subAnyElements,null);
              break;
            case ORDERING:
              // Ordering index checking is two fold:
              // 1. Make sure the entry has an attribute value that is the same
              //    as the key. This is done by falling through to the next
              //    case and create an equality filter.
              // 2. Make sure the key value is greater then the previous key
              //    value.
              assertionValue =
                  new AttributeValue(attrType, new ASN1OctetString(value));

              sf = SearchFilter.createEqualityFilter(attrType,assertionValue);

              if(orderingMatchingRule != null && previousValue != null)
              {
                ASN1OctetString thisValue = new ASN1OctetString(value);
                int order = orderingMatchingRule.compareValues(thisValue,
                                                               previousValue);
                if(order > 0)
                {
                  errorCount++;
                  if(debugEnabled())
                  {
                    TRACER.debugError("Reversed ordering of index keys " +
                        "(keys dumped in the order found in database)%n" +
                        "Key 1:%n%s%nKey 2:%n%s",
                               keyDump(index, thisValue.value()),
                               keyDump(index,previousValue.value()));
                  }
                  continue;
                }
                else if(order == 0)
                {
                  errorCount++;
                  if(debugEnabled())
                  {
                    TRACER.debugError("Duplicate index keys%nKey 1:%n%s%n" +
                        "Key2:%n%s", keyDump(index, thisValue.value()),
                                     keyDump(index,previousValue.value()));
                  }
                  continue;
                }
                else
                {
                  previousValue = thisValue;
                }
              }
              break;
            case EQ:
              assertionValue =
                   new AttributeValue(attrType, new ASN1OctetString(value));

              sf = SearchFilter.createEqualityFilter(attrType,assertionValue);
              break;

            case PRES:
              sf = SearchFilter.createPresenceFilter(attrType);
              break;

            case APPROXIMATE:
              // This must be handled differently since we can't use a search
              // filter to see if the key matches.
              sf = null;
              break;

            default:
              errorCount++;
              if (debugEnabled())
              {
                TRACER.debugError("Malformed value%n%s", keyDump(index, value));
              }
              continue;
          }

          EntryID prevID = null;
          for (EntryID id : entryIDList)
          {
            if (prevID != null && id.equals(prevID))
            {
              if (debugEnabled())
              {
                TRACER.debugError("Duplicate reference to ID %d%n%s",
                           id.longValue(), keyDump(index, key.getData()));
              }
            }
            prevID = id;

            Entry entry;
            try
            {
              entry = id2entry.get(null, id);
            }
            catch (Exception e)
            {
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, e);
              }
              errorCount++;
              continue;
            }

            if (entry == null)
            {
              errorCount++;
              if (debugEnabled())
              {
                TRACER.debugError("Reference to unknown ID %d%n%s",
                           id.longValue(), keyDump(index, key.getData()));
              }
              continue;
            }

            try
            {
              boolean match = false;
              if(indexType != IndexType.APPROXIMATE)
              {
                match = sf.matchesEntry(entry);
              }
              else
              {
                ByteString normalizedValue = new ASN1OctetString(value);
                List<Attribute> attrs = entry.getAttribute(attrType);
                if ((attrs != null) && (!attrs.isEmpty()))
                {
                  for (Attribute a : attrs)
                  {
                    for (AttributeValue v : a.getValues())
                    {
                      ByteString nv =
                          approximateMatchingRule.normalizeValue(v.getValue());
                      match = approximateMatchingRule.
                          approximatelyMatch(nv, normalizedValue);
                      if(match)
                      {
                        break;
                      }
                    }
                    if(match)
                    {
                      break;
                    }
                  }
                }
              }

              if (!match)
              {
                errorCount++;
                if (debugEnabled())
                {
                  TRACER.debugError("Reference to entry " +
                      "<%s> which does not match the value%n%s",
                             entry.getDN(), keyDump(index, value));
                }
              }
            }
            catch (DirectoryException e)
            {
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, e);
              }
            }
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
   * Check that an index is complete for a given entry.
   *
   * @param entryID The entry ID.
   * @param entry The entry to be checked.
   */
  private void verifyEntry(EntryID entryID, Entry entry)
  {
    if (verifyDN2ID)
    {
      verifyDN2ID(entryID, entry);
    }
    if (verifyID2Children)
    {
      verifyID2Children(entryID, entry);
    }
    if (verifyID2Subtree)
    {
      verifyID2Subtree(entryID, entry);
    }
    verifyIndex(entryID, entry);
  }

  /**
   * Check that the DN2ID index is complete for a given entry.
   *
   * @param entryID The entry ID.
   * @param entry The entry to be checked.
   */
  private void verifyDN2ID(EntryID entryID, Entry entry)
  {
    DN dn = entry.getDN();

    // Check the ID is in dn2id with the correct DN.
    try
    {
      EntryID id = dn2id.get(null, dn);
      if (id == null)
      {
        if (debugEnabled())
        {
          TRACER.debugError("File dn2id is missing key %s.%n",
                     dn.toNormalizedString());
        }
        errorCount++;
      }
      else if (!id.equals(entryID))
      {
        if (debugEnabled())
        {
          TRACER.debugError("File dn2id has ID %d instead of %d for key %s.%n",
                     id.longValue(),
                     entryID.longValue(),
                     dn.toNormalizedString());
        }
        errorCount++;
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);

        TRACER.debugError("File dn2id has error reading key %s: %s.%n",
                   dn.toNormalizedString(),
                   e.getMessage());
      }
      errorCount++;
    }

    // Check the parent DN is in dn2id.
    DN parentDN = getParent(dn);
    if (parentDN != null)
    {
      try
      {
        EntryID id = dn2id.get(null, parentDN);
        if (id == null)
        {
          if (debugEnabled())
          {
            TRACER.debugError("File dn2id is missing key %s.%n",
                       parentDN.toNormalizedString());
          }
          errorCount++;
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);

          TRACER.debugError("File dn2id has error reading key %s: %s.%n",
                     parentDN.toNormalizedString(),
                     e.getMessage());
        }
        errorCount++;
      }
    }
  }

  /**
   * Check that the ID2Children index is complete for a given entry.
   *
   * @param entryID The entry ID.
   * @param entry The entry to be checked.
   */
  private void verifyID2Children(EntryID entryID, Entry entry)
  {
    DN dn = entry.getDN();

    DN parentDN = getParent(dn);
    if (parentDN != null)
    {
      EntryID parentID = null;
      try
      {
        parentID = dn2id.get(null, parentDN);
        if (parentID == null)
        {
          if (debugEnabled())
          {
            TRACER.debugError("File dn2id is missing key %s.%n",
                       parentDN.toNormalizedString());
          }
          errorCount++;
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);

          TRACER.debugError("File dn2id has error reading key %s: %s.",
                     parentDN.toNormalizedString(),
                     e.getMessage());
        }
        errorCount++;
      }
      if (parentID != null)
      {
        try
        {
          ConditionResult cr;
          cr = id2c.containsID(null, parentID.getDatabaseEntry(), entryID);
          if (cr == ConditionResult.FALSE)
          {
            if (debugEnabled())
            {
              TRACER.debugError("File id2children is missing ID %d " +
                  "for key %d.%n",
                         entryID.longValue(), parentID.longValue());
            }
            errorCount++;
          }
          else if (cr == ConditionResult.UNDEFINED)
          {
            incrEntryLimitStats(id2c, parentID.getDatabaseEntry().getData());
          }
        }
        catch (DatabaseException e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);

            TRACER.debugError("File id2children has error reading key %d: %s.",
                       parentID.longValue(), e.getMessage());
          }
          errorCount++;
        }
      }
    }
  }

  /**
   * Check that the ID2Subtree index is complete for a given entry.
   *
   * @param entryID The entry ID.
   * @param entry The entry to be checked.
   */
  private void verifyID2Subtree(EntryID entryID, Entry entry)
  {
    for (DN dn = getParent(entry.getDN()); dn != null; dn = getParent(dn))
    {
      EntryID id = null;
      try
      {
        id = dn2id.get(null, dn);
        if (id == null)
        {
          if (debugEnabled())
          {
            TRACER.debugError("File dn2id is missing key %s.%n",
                       dn.toNormalizedString());
          }
          errorCount++;
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);

          TRACER.debugError("File dn2id has error reading key %s: %s.%n",
                     dn.toNormalizedString(),
                     e.getMessage());
        }
        errorCount++;
      }
      if (id != null)
      {
        try
        {
          ConditionResult cr;
          cr = id2s.containsID(null, id.getDatabaseEntry(), entryID);
          if (cr == ConditionResult.FALSE)
          {
            if (debugEnabled())
            {
              TRACER.debugError("File id2subtree is missing ID %d " +
                  "for key %d.%n",
                         entryID.longValue(), id.longValue());
            }
            errorCount++;
          }
          else if (cr == ConditionResult.UNDEFINED)
          {
            incrEntryLimitStats(id2s, id.getDatabaseEntry().getData());
          }
        }
        catch (DatabaseException e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);

            TRACER.debugError("File id2subtree has error reading key %d: %s.%n",
                       id.longValue(), e.getMessage());
          }
          errorCount++;
        }
      }
    }
  }

  /**
   * Construct a printable string from a raw key value.
   *
   * @param index The index database containing the key value.
   * @param keyBytes The bytes of the key.
   * @return A string that may be logged or printed.
   */
  private String keyDump(Index index, byte[] keyBytes)
  {
/*
    String str;
    try
    {
      str = new String(keyBytes, "UTF-8");
    }
    catch (UnsupportedEncodingException e)
    {
      str = StaticUtils.bytesToHex(keyBytes);
    }
    return str;
*/
    StringBuilder buffer = new StringBuilder(128);
    buffer.append("File: ");
    buffer.append(index.toString());
    buffer.append(ServerConstants.EOL);
    buffer.append("Key:");
    buffer.append(ServerConstants.EOL);
    StaticUtils.byteArrayToHexPlusAscii(buffer, keyBytes, 6);
    return buffer.toString();
  }

  /**
   * Construct a printable string from a raw key value.
   *
   * @param vlvIndex The vlvIndex database containing the key value.
   * @param keySortValues THe sort values that is being used as the key.
   * @return A string that may be logged or printed.
   */
  private String keyDump(VLVIndex vlvIndex, SortValues keySortValues)
  {
/*
    String str;
    try
    {
      str = new String(keyBytes, "UTF-8");
    }
    catch (UnsupportedEncodingException e)
    {
      str = StaticUtils.bytesToHex(keyBytes);
    }
    return str;
*/
    StringBuilder buffer = new StringBuilder(128);
    buffer.append("File: ");
    buffer.append(vlvIndex.toString());
    buffer.append(ServerConstants.EOL);
    buffer.append("Key (last sort values):");
    if(keySortValues == null)
    {
      buffer.append("UNBOUNDED (0x00)");
    }
    else
    {
      buffer.append(keySortValues.toString());
    }
    return buffer.toString();
  }

  /**
   * Check that an attribute index is complete for a given entry.
   *
   * @param entryID The entry ID.
   * @param entry The entry to be checked.
   */
  private void verifyIndex(EntryID entryID, Entry entry)
  {
    for (AttributeIndex attrIndex : attrIndexList)
    {
      try
      {
        List<Attribute> attrList =
             entry.getAttribute(attrIndex.getAttributeType());
        if (attrList != null)
        {
          verifyAttribute(attrIndex, entryID, attrList);
        }
      }
      catch (DirectoryException e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);

          TRACER.debugError("Error normalizing values of attribute %s in " +
              "entry <%s>: %s.%n",
                     attrIndex.getAttributeType().toString(),
                     entry.getDN().toString(),
                     String.valueOf(e.getMessageObject()));
        }
      }
    }

    for (VLVIndex vlvIndex : vlvIndexList)
    {
      try
      {
        if(vlvIndex.shouldInclude(entry))
        {
          if(!vlvIndex.containsValues(null, entryID.longValue(),
                                  vlvIndex.getSortValues(entry)))
          {
            if(debugEnabled())
            {
              TRACER.debugError("Missing entry %s in VLV index %s",
                                entry.getDN().toString(),
                                vlvIndex.getName());
            }
            errorCount++;
          }
        }
      }
      catch (DirectoryException e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);

          TRACER.debugError("Error checking entry %s against filter or " +
              "base DN for VLV index %s: %s",
                     entry.getDN().toString(),
                     vlvIndex.getName(),
                     String.valueOf(e.getMessageObject()));
        }
        errorCount++;
      }
      catch (DatabaseException e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);

          TRACER.debugError("Error reading VLV index %s for entry %s: %s",
                     vlvIndex.getName(),
                     entry.getDN().toString(),
                     StaticUtils.getBacktrace(e));
        }
        errorCount++;
      }
      catch (JebException e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);

          TRACER.debugError("Error reading VLV index %s for entry %s: %s",
                     vlvIndex.getName(),
                     entry.getDN().toString(),
                     StaticUtils.getBacktrace(e));
        }
        errorCount++;
      }
    }
  }

  /**
   * Check that an attribute index is complete for a given attribute.
   *
   * @param attrIndex The attribute index to be checked.
   * @param entryID The entry ID.
   * @param attrList The attribute to be checked.
   * @throws DirectoryException If a Directory Server error occurs.
   */
  private void verifyAttribute(AttributeIndex attrIndex, EntryID entryID,
                              List<Attribute> attrList)
       throws DirectoryException
  {
    Transaction txn = null;
    Index equalityIndex = attrIndex.equalityIndex;
    Index presenceIndex = attrIndex.presenceIndex;
    Index substringIndex = attrIndex.substringIndex;
    Index orderingIndex = attrIndex.orderingIndex;
    Index approximateIndex = attrIndex.approximateIndex;
    DatabaseEntry presenceKey = AttributeIndex.presenceKey;

    // Presence index.
    if (!attrList.isEmpty() && presenceIndex != null)
    {
      try
      {
        ConditionResult cr;
        cr = presenceIndex.containsID(txn, presenceKey, entryID);
        if (cr == ConditionResult.FALSE)
        {
          if (debugEnabled())
          {
            TRACER.debugError("Missing ID %d%n%s",
                       entryID.longValue(),
                       keyDump(presenceIndex, presenceKey.getData()));
          }
          errorCount++;
        }
        else if (cr == ConditionResult.UNDEFINED)
        {
          incrEntryLimitStats(presenceIndex, presenceKey.getData());
        }
      }
      catch (DatabaseException e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);

          TRACER.debugError("Error reading database: %s%n%s",
                     e.getMessage(),
                     keyDump(presenceIndex, presenceKey.getData()));
        }
        errorCount++;
      }
    }

    if (attrList != null)
    {
      for (Attribute attr : attrList)
      {
        LinkedHashSet<AttributeValue> values = attr.getValues();
        for (AttributeValue value : values)
        {
          byte[] normalizedBytes = value.getNormalizedValue().value();

          // Equality index.
          if (equalityIndex != null)
          {
            DatabaseEntry key = new DatabaseEntry(normalizedBytes);
            try
            {
              ConditionResult cr;
              cr = equalityIndex.containsID(txn, key, entryID);
              if (cr == ConditionResult.FALSE)
              {
                if (debugEnabled())
                {
                  TRACER.debugError("Missing ID %d%n%s",
                             entryID.longValue(),
                             keyDump(equalityIndex, normalizedBytes));
                }
                errorCount++;
              }
              else if (cr == ConditionResult.UNDEFINED)
              {
                incrEntryLimitStats(equalityIndex, normalizedBytes);
              }
            }
            catch (DatabaseException e)
            {
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, e);

                TRACER.debugError("Error reading database: %s%n%s",
                           e.getMessage(),
                           keyDump(equalityIndex, normalizedBytes));
              }
              errorCount++;
            }
          }

          // Substring index.
          if (substringIndex != null)
          {
            Set<ByteString> keyBytesSet =
                 attrIndex.substringKeys(normalizedBytes);
            DatabaseEntry key = new DatabaseEntry();
            for (ByteString keyBytes : keyBytesSet)
            {
              key.setData(keyBytes.value());
              try
              {
                ConditionResult cr;
                cr = substringIndex.containsID(txn, key, entryID);
                if (cr == ConditionResult.FALSE)
                {
                  if (debugEnabled())
                  {
                    TRACER.debugError("Missing ID %d%n%s",
                               entryID.longValue(),
                               keyDump(substringIndex, key.getData()));
                  }
                  errorCount++;
                }
                else if (cr == ConditionResult.UNDEFINED)
                {
                  incrEntryLimitStats(substringIndex, key.getData());
                }
              }
              catch (DatabaseException e)
              {
                if (debugEnabled())
                {
                  TRACER.debugCaught(DebugLogLevel.ERROR, e);

                  TRACER.debugError("Error reading database: %s%n%s",
                             e.getMessage(),
                             keyDump(substringIndex, key.getData()));
                }
                errorCount++;
              }
            }
          }

          // Ordering index.
          if (orderingIndex != null)
          {
            // Use the ordering matching rule to normalize the value.
            OrderingMatchingRule orderingRule =
                 attr.getAttributeType().getOrderingMatchingRule();

            normalizedBytes =
                 orderingRule.normalizeValue(value.getValue()).value();

            DatabaseEntry key = new DatabaseEntry(normalizedBytes);
            try
            {
              ConditionResult cr;
              cr = orderingIndex.containsID(txn, key, entryID);
              if (cr == ConditionResult.FALSE)
              {
                if (debugEnabled())
                {
                  TRACER.debugError("Missing ID %d%n%s",
                             entryID.longValue(),
                             keyDump(orderingIndex, normalizedBytes));
                }
                errorCount++;
              }
              else if (cr == ConditionResult.UNDEFINED)
              {
                incrEntryLimitStats(orderingIndex, normalizedBytes);
              }
            }
            catch (DatabaseException e)
            {
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, e);

                TRACER.debugError("Error reading database: %s%n%s",
                           e.getMessage(),
                           keyDump(orderingIndex, normalizedBytes));
              }
              errorCount++;
            }
          }
          // Approximate index.
          if (approximateIndex != null)
          {
            // Use the approximate matching rule to normalize the value.
            ApproximateMatchingRule approximateRule =
                attr.getAttributeType().getApproximateMatchingRule();

            normalizedBytes =
                approximateRule.normalizeValue(value.getValue()).value();

            DatabaseEntry key = new DatabaseEntry(normalizedBytes);
            try
            {
              ConditionResult cr;
              cr = approximateIndex.containsID(txn, key, entryID);
              if (cr == ConditionResult.FALSE)
              {
                if (debugEnabled())
                {
                  TRACER.debugError("Missing ID %d%n%s",
                             entryID.longValue(),
                             keyDump(orderingIndex, normalizedBytes));
                }
                errorCount++;
              }
              else if (cr == ConditionResult.UNDEFINED)
              {
                incrEntryLimitStats(orderingIndex, normalizedBytes);
              }
            }
            catch (DatabaseException e)
            {
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, e);

                TRACER.debugError("Error reading database: %s%n%s",
                           e.getMessage(),
                           keyDump(approximateIndex, normalizedBytes));
              }
              errorCount++;
            }
          }
        }
      }
    }
  }

  /**
   * Get the parent DN of a given DN.
   *
   * @param dn The DN.
   * @return The parent DN or null if the given DN is a base DN.
   */
  private DN getParent(DN dn)
  {
    if (dn.equals(verifyConfig.getBaseDN()))
    {
      return null;
    }
    return dn.getParentDNInSuffix();
  }

  /**
   * This class reports progress of the verify job at fixed intervals.
   */
  class ProgressTask extends TimerTask
  {
    /**
     * The total number of records to process.
     */
    private long totalCount;

    /**
     * The number of records that had been processed at the time of the
     * previous progress report.
     */
    private long previousCount = 0;

    /**
     * The time in milliseconds of the previous progress report.
     */
    private long previousTime;

    /**
     * The environment statistics at the time of the previous report.
     */
    private EnvironmentStats prevEnvStats;

    /**
     * The number of bytes in a megabyte.
     * Note that 1024*1024 bytes may eventually become known as a mebibyte(MiB).
     */
    private static final int bytesPerMegabyte = 1024*1024;

    /**
     * Create a new verify progress task.
     * @throws DatabaseException An error occurred while accessing the JE
     * database.
     */
    public ProgressTask() throws DatabaseException
    {
      previousTime = System.currentTimeMillis();
      prevEnvStats =
          rootContainer.getEnvironmentStats(new StatsConfig());
      totalCount = rootContainer.getEntryContainer(
        verifyConfig.getBaseDN()).getEntryCount();
    }

    /**
     * The action to be performed by this timer task.
     */
    public void run()
    {
      long latestCount = keyCount;
      long deltaCount = (latestCount - previousCount);
      long latestTime = System.currentTimeMillis();
      long deltaTime = latestTime - previousTime;

      if (deltaTime == 0)
      {
        return;
      }

      float rate = 1000f*deltaCount / deltaTime;

      Message message = NOTE_JEB_VERIFY_PROGRESS_REPORT.get(
        latestCount, totalCount, errorCount, rate);
      logError(message);

      try
      {
        Runtime runtime = Runtime.getRuntime();
        long freeMemory = runtime.freeMemory() / bytesPerMegabyte;

        EnvironmentStats envStats =
            rootContainer.getEnvironmentStats(new StatsConfig());
        long nCacheMiss =
             envStats.getNCacheMiss() - prevEnvStats.getNCacheMiss();

        float cacheMissRate = 0;
        if (deltaCount > 0)
        {
          cacheMissRate = nCacheMiss/(float)deltaCount;
        }

        message = INFO_JEB_VERIFY_CACHE_AND_MEMORY_REPORT.get(
            freeMemory, cacheMissRate);
        logError(message);

        prevEnvStats = envStats;
      }
      catch (DatabaseException e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }


      previousCount = latestCount;
      previousTime = latestTime;
    }
  }

    /**
     * Adds an attribute of type t and value v to the statEntry, only if the
     * statEntry is not null.
     * @param statEntry passed in from backentryImpl.verifyBackend.
     * @param t String to be used as the attribute type.
     * @param v String to be used as the attribute value.
     */
    private void addStatEntry(Entry statEntry, String t, String v)
    {
        if (statEntry != null)
        {
            Attribute a = new Attribute(t, v);
            statEntry.addAttribute(a, null);
        }
    }
}
