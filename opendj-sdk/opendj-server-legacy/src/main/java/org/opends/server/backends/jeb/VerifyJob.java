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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.backends.jeb;

import static org.opends.messages.BackendMessages.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.forgerock.opendj.ldap.spi.IndexingOptions;
import org.opends.server.backends.VerifyConfig;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.*;
import org.opends.server.util.ServerConstants;
import org.opends.server.util.StaticUtils;

import com.sleepycat.je.*;

/** This class is used to run an index verification process on the backend. */
public class VerifyJob
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The verify configuration. */
  private final VerifyConfig verifyConfig;
  /** The root container used for the verify job. */
  private RootContainer rootContainer;

  /** The number of milliseconds between job progress reports. */
  private final long progressInterval = 10000;
  /** The number of index keys processed. */
  private long keyCount;
  /** The number of errors found. */
  private long errorCount;
  /** The number of records that have exceeded the entry limit. */
  private long entryLimitExceededCount;
  /** The number of records that reference more than one entry. */
  private long multiReferenceCount;
  /** The total number of entry references. */
  private long entryReferencesCount;
  /** The maximum number of references per record. */
  private long maxEntryPerValue;

  /**
   * This map is used to gather some statistics about values that have
   * exceeded the entry limit.
   */
  private IdentityHashMap<Index, HashMap<ByteString, Long>> entryLimitMap =
       new IdentityHashMap<Index, HashMap<ByteString, Long>>();

  /** Indicates whether the DN database is to be verified. */
  private boolean verifyDN2ID;
  /** Indicates whether the children database is to be verified. */
  private boolean verifyID2Children;
  /** Indicates whether the subtree database is to be verified. */
  private boolean verifyID2Subtree;

  /** The entry database. */
  private ID2Entry id2entry;
  /** The DN database. */
  private DN2ID dn2id;
  /** The children database. */
  private Index id2c;
  /** The subtree database. */
  private Index id2s;

  /** A list of the attribute indexes to be verified. */
  private final ArrayList<AttributeIndex> attrIndexList = new ArrayList<AttributeIndex>();
  /** A list of the VLV indexes to be verified. */
  private final ArrayList<VLVIndex> vlvIndexList = new ArrayList<VLVIndex>();

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
   * @return The error count.
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws JebException If an error occurs in the JE backend.
   * @throws DirectoryException If an error occurs while verifying the backend.
   */
  public long verifyBackend(RootContainer rootContainer) throws DatabaseException, JebException, DirectoryException
  {
    this.rootContainer = rootContainer;
    EntryContainer entryContainer =
        rootContainer.getEntryContainer(verifyConfig.getBaseDN());

    entryContainer.sharedLock.lock();
    try
    {
      final List<String> completeList = verifyConfig.getCompleteList();
      final List<String> cleanList = verifyConfig.getCleanList();

      boolean cleanMode = false;
      if (completeList.isEmpty() && cleanList.isEmpty())
      {
        verifyDN2ID = true;
        if (rootContainer.getConfiguration().isSubordinateIndexesEnabled())
        {
          verifyID2Children = true;
          verifyID2Subtree = true;
        }
        attrIndexList.addAll(entryContainer.getAttributeIndexes());
      }
      else
      {
        final List<String> list;
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
          if ("dn2id".equals(lowerName))
          {
            verifyDN2ID = true;
          }
          else if ("id2children".equals(lowerName))
          {
            if (rootContainer.getConfiguration().isSubordinateIndexesEnabled())
            {
              verifyID2Children = true;
            }
            else
            {
              LocalizableMessage msg = NOTE_JEB_SUBORDINATE_INDEXES_DISABLED
                  .get(rootContainer.getConfiguration().getBackendId());
              throw new JebException(msg);
            }
          }
          else if ("id2subtree".equals(lowerName))
          {
            if (rootContainer.getConfiguration().isSubordinateIndexesEnabled())
            {
              verifyID2Subtree = true;
            }
            else
            {
              LocalizableMessage msg = NOTE_JEB_SUBORDINATE_INDEXES_DISABLED
                  .get(rootContainer.getConfiguration().getBackendId());
              throw new JebException(msg);
            }
          }
          else if(lowerName.startsWith("vlv."))
          {
            if(lowerName.length() < 5)
            {
              throw new JebException(ERR_VLV_INDEX_NOT_CONFIGURED.get(lowerName));
            }

            VLVIndex vlvIndex =
                entryContainer.getVLVIndex(lowerName.substring(4));
            if(vlvIndex == null)
            {
              throw new JebException(ERR_VLV_INDEX_NOT_CONFIGURED.get(lowerName.substring(4)));
            }

            vlvIndexList.add(vlvIndex);
          }
          else
          {
            AttributeType attrType = DirectoryServer.getAttributeType(lowerName);
            if (attrType == null)
            {
              throw new JebException(ERR_ATTRIBUTE_INDEX_NOT_CONFIGURED.get(index));
            }
            AttributeIndex attrIndex = entryContainer.getAttributeIndex(attrType);
            if (attrIndex == null)
            {
              throw new JebException(ERR_ATTRIBUTE_INDEX_NOT_CONFIGURED.get(index));
            }
            attrIndexList.add(attrIndex);
          }
        }
      }

      entryLimitMap = new IdentityHashMap<Index, HashMap<ByteString, Long>>(attrIndexList.size());

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
      // Create a new progressTask based on the index count.
      TimerTask progressTask = new ProgressTask(cleanMode);
      timer.scheduleAtFixedRate(progressTask, progressInterval, progressInterval);

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
      long totalTime = finishTime - startTime;

      float rate = 0;
      if (totalTime > 0)
      {
        rate = 1000f*keyCount / totalTime;
      }

      if (cleanMode)
      {
        logger.info(NOTE_VERIFY_CLEAN_FINAL_STATUS, keyCount, errorCount, totalTime / 1000, rate);

        if (multiReferenceCount > 0)
        {
          float averageEntryReferences = 0;
          if (keyCount > 0)
          {
            averageEntryReferences = entryReferencesCount/keyCount;
          }

          if (logger.isDebugEnabled())
          {
            logger.debug(INFO_VERIFY_MULTIPLE_REFERENCE_COUNT, multiReferenceCount);
            logger.debug(INFO_VERIFY_ENTRY_LIMIT_EXCEEDED_COUNT, entryLimitExceededCount);
            logger.debug(INFO_VERIFY_AVERAGE_REFERENCE_COUNT, averageEntryReferences);
            logger.debug(INFO_VERIFY_MAX_REFERENCE_COUNT, maxEntryPerValue);
          }
        }
      }
      else
      {
        logger.info(NOTE_VERIFY_FINAL_STATUS, keyCount, errorCount, totalTime/1000, rate);
        if (entryLimitMap.size() > 0)
        {
          logger.debug(INFO_VERIFY_ENTRY_LIMIT_STATS_HEADER);

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

            logger.debug(INFO_VERIFY_ENTRY_LIMIT_STATS_ROW, index, values.length, values[0],
                    values[values.length-1], medianValue);
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
    DiskOrderedCursor cursor =
        id2entry.openCursor(new DiskOrderedCursorConfig());
    long storedEntryCount = id2entry.getRecordCount();
    try
    {
      DatabaseEntry key = new DatabaseEntry();
      DatabaseEntry data = new DatabaseEntry();
      while (cursor.getNext(key, data, null) == OperationStatus.SUCCESS)
      {
        EntryID entryID;
        try
        {
          entryID = new EntryID(key);
        }
        catch (Exception e)
        {
          errorCount++;
          if (logger.isTraceEnabled())
          {
            logger.traceException(e);

            logger.trace("Malformed id2entry ID %s.%n",
                       StaticUtils.bytesToHex(key.getData()));
          }
          continue;
        }

        keyCount++;

        Entry entry;
        try
        {
          entry = ID2Entry.entryFromDatabase(
              ByteString.wrap(data.getData()),
              rootContainer.getCompressedSchema());
        }
        catch (Exception e)
        {
          errorCount++;
          if (logger.isTraceEnabled())
          {
            logger.traceException(e);

            logger.trace("Malformed id2entry record for ID %d:%n%s%n",
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
        if (logger.isTraceEnabled())
        {
          logger.trace("The stored entry count in id2entry (%d) does " +
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
    else if (attrIndexList.size() > 0)
    {
      AttributeIndex attrIndex = attrIndexList.get(0);
      final IndexingOptions options = attrIndex.getIndexingOptions();
      iterateAttrIndex(attrIndex.getEqualityIndex(), options);
      iterateAttrIndex(attrIndex.getPresenceIndex(), options);
      iterateAttrIndex(attrIndex.getSubstringIndex(), options);
      iterateAttrIndex(attrIndex.getOrderingIndex(), options);
      iterateAttrIndex(attrIndex.getApproximateIndex(), options);
     // TODO: Need to iterate through ExtendedMatchingRules indexes.
    }
    else if (vlvIndexList.size() > 0)
    {
      iterateVLVIndex(vlvIndexList.get(0), true);
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
    DiskOrderedCursor cursor = dn2id.openCursor(new DiskOrderedCursorConfig());
    try
    {
      DatabaseEntry key = new DatabaseEntry();
      DatabaseEntry data = new DatabaseEntry();

      while (cursor.getNext(key, data, null) == OperationStatus.SUCCESS)
      {
        keyCount++;

        EntryID entryID;
        try
        {
          entryID = new EntryID(data);
        }
        catch (Exception e)
        {
          errorCount++;
          if (logger.isTraceEnabled())
          {
            logger.traceException(e);

            logger.trace("File dn2id has malformed ID for DN <%s>:%n%s%n",
                       new String(key.getData()),
                       StaticUtils.bytesToHex(data.getData()));
          }
          continue;
        }

        Entry entry;
        try
        {
          entry = id2entry.get(null, entryID, LockMode.DEFAULT);
        }
        catch (Exception e)
        {
          errorCount++;
          logger.traceException(e);
          continue;
        }

        if (entry == null)
        {
          errorCount++;
          if (logger.isTraceEnabled())
          {
            logger.trace("File dn2id has DN <%s> referencing unknown " +
                "ID %d%n", new String(key.getData()), entryID.longValue());
          }
        }
        else if (!Arrays.equals(JebFormat.dnToDNKey(
            entry.getName(), verifyConfig.getBaseDN().size()), key.getData()))
        {
          errorCount++;
          if (logger.isTraceEnabled())
          {
            logger.trace("File dn2id has DN <%s> referencing entry with wrong DN <%s>%n",
                new String(key.getData()), entry.getName());
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
    DiskOrderedCursor cursor = id2c.openCursor(new DiskOrderedCursorConfig());
    try
    {
      DatabaseEntry key = new DatabaseEntry();
      DatabaseEntry data = new DatabaseEntry();

      while (cursor.getNext(key, data, null) == OperationStatus.SUCCESS)
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
          if (logger.isTraceEnabled())
          {
            logger.traceException(e);

            logger.trace("File id2children has malformed ID %s%n",
                       StaticUtils.bytesToHex(key.getData()));
          }
          continue;
        }

        EntryIDSet entryIDList;

        try
        {
          entryIDList = new EntryIDSet(key.getData(), data.getData());
        }
        catch (Exception e)
        {
          errorCount++;
          if (logger.isTraceEnabled())
          {
            logger.traceException(e);

            logger.trace("File id2children has malformed ID list " +
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
            entry = id2entry.get(null, entryID, LockMode.DEFAULT);
          }
          catch (Exception e)
          {
            logger.traceException(e);
            errorCount++;
            continue;
          }

          if (entry == null)
          {
            errorCount++;
            if (logger.isTraceEnabled())
            {
              logger.trace("File id2children has unknown ID %d%n",
                         entryID.longValue());
            }
            continue;
          }

          for (EntryID id : entryIDList)
          {
            Entry childEntry;
            try
            {
              childEntry = id2entry.get(null, id, LockMode.DEFAULT);
            }
            catch (Exception e)
            {
              logger.traceException(e);
              errorCount++;
              continue;
            }

            if (childEntry == null)
            {
              errorCount++;
              if (logger.isTraceEnabled())
              {
                logger.trace("File id2children has ID %d referencing " +
                    "unknown ID %d%n", entryID.longValue(), id.longValue());
              }
              continue;
            }

            if (!childEntry.getName().isDescendantOf(entry.getName()) ||
                 childEntry.getName().size() !=
                 entry.getName().size() + 1)
            {
              errorCount++;
              if (logger.isTraceEnabled())
              {
                logger.trace("File id2children has ID %d with DN <%s> " +
                    "referencing ID %d with non-child DN <%s>%n",
                    entryID.longValue(), entry.getName(), id.longValue(), childEntry.getName());
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
    DiskOrderedCursor cursor = id2s.openCursor(new DiskOrderedCursorConfig());
    try
    {
      DatabaseEntry key = new DatabaseEntry();
      DatabaseEntry data = new DatabaseEntry();

      while (cursor.getNext(key, data, null) == OperationStatus.SUCCESS)
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
          if (logger.isTraceEnabled())
          {
            logger.traceException(e);

            logger.trace("File id2subtree has malformed ID %s%n",
                       StaticUtils.bytesToHex(key.getData()));
          }
          continue;
        }

        EntryIDSet entryIDList;
        try
        {
          entryIDList = new EntryIDSet(key.getData(), data.getData());
        }
        catch (Exception e)
        {
          errorCount++;
          if (logger.isTraceEnabled())
          {
            logger.traceException(e);

            logger.trace("File id2subtree has malformed ID list " +
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
            entry = id2entry.get(null, entryID, LockMode.DEFAULT);
          }
          catch (Exception e)
          {
            logger.traceException(e);
            errorCount++;
            continue;
          }

          if (entry == null)
          {
            errorCount++;
            if (logger.isTraceEnabled())
            {
              logger.trace("File id2subtree has unknown ID %d%n",
                         entryID.longValue());
            }
            continue;
          }

          for (EntryID id : entryIDList)
          {
            Entry subordEntry;
            try
            {
              subordEntry = id2entry.get(null, id, LockMode.DEFAULT);
            }
            catch (Exception e)
            {
              logger.traceException(e);
              errorCount++;
              continue;
            }

            if (subordEntry == null)
            {
              errorCount++;
              if (logger.isTraceEnabled())
              {
                logger.trace("File id2subtree has ID %d referencing " +
                    "unknown ID %d%n", entryID.longValue(), id.longValue());
              }
              continue;
            }

            if (!subordEntry.getName().isDescendantOf(entry.getName()))
            {
              errorCount++;
              if (logger.isTraceEnabled())
              {
                logger.trace("File id2subtree has ID %d with DN <%s> " +
                    "referencing ID %d with non-subordinate DN <%s>%n",
                    entryID.longValue(), entry.getName(), id.longValue(), subordEntry.getName());
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
    ByteString octetString = ByteString.wrap(key);
    Long counter = hashMap.get(octetString);
    if (counter != null)
    {
      counter++;
    }
    else
    {
      counter = 1L;
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

    DiskOrderedCursor cursor =
        vlvIndex.openCursor(new DiskOrderedCursorConfig());
    try
    {
      DatabaseEntry key = new DatabaseEntry();
      DatabaseEntry data = new DatabaseEntry();

      SortValues lastValues = null;
      while(cursor.getNext(key, data, null) == OperationStatus.SUCCESS)
      {
        SortValuesSet sortValuesSet =
            new SortValuesSet(key.getData(), data.getData(), vlvIndex);
        for(int i = 0; i < sortValuesSet.getEntryIDs().length; i++)
        {
          keyCount++;
          SortValues values = sortValuesSet.getSortValues(i);
          if(lastValues != null && lastValues.compareTo(values) >= 1)
          {
            // Make sure the values is larger then the previous one.
            if(logger.isTraceEnabled())
            {
              logger.trace("Values %s and %s are incorrectly ordered",
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
            byte[] encodedKey = vlvIndex.encodeKey(
                values.getEntryID(), values.getValues(), values.getTypes());
            if(!Arrays.equals(key.getData(), encodedKey))
            {
              if(logger.isTraceEnabled())
              {
                logger.trace("Incorrect key for SortValuesSet in VLV " +
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
              entry = id2entry.get(null, id, LockMode.DEFAULT);
            }
            catch (Exception e)
            {
              logger.traceException(e);
              errorCount++;
              continue;
            }

            if (entry == null)
            {
              errorCount++;
              if (logger.isTraceEnabled())
              {
                logger.trace("Reference to unknown ID %d%n%s",
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
              if(logger.isTraceEnabled())
              {
                logger.trace("Reference to entry ID %d " +
                    "which does not match the values%n%s",
                                  id.longValue(),
                                  keyDump(vlvIndex,
                                          sortValuesSet.getKeySortValues()));
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
   * Iterate through the entries in an attribute index to perform a check for
   * index cleanliness.
   * @param index The index database to be checked.
   * @throws JebException If an error occurs in the JE backend.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  private void iterateAttrIndex(Index index, IndexingOptions options)
      throws JebException, DatabaseException
  {
    if (index == null)
    {
      return;
    }

    DiskOrderedCursor cursor = index.openCursor(new DiskOrderedCursorConfig());
    try
    {
      DatabaseEntry key = new DatabaseEntry();
      DatabaseEntry data = new DatabaseEntry();

      while (cursor.getNext(key, data, null) == OperationStatus.SUCCESS)
      {
        keyCount++;

        EntryIDSet entryIDList;
        try
        {
          entryIDList = new EntryIDSet(key.getData(), data.getData());
        }
        catch (Exception e)
        {
          errorCount++;
          if (logger.isTraceEnabled())
          {
            logger.traceException(e);

            logger.trace("Malformed ID list: %s%n%s",
                       StaticUtils.bytesToHex(data.getData()),
                       keyDump(index, key.getData()));
          }
          continue;
        }

        updateIndexStats(entryIDList);

        if (entryIDList.isDefined())
        {
          final ByteString value = ByteString.wrap(key.getData());
          EntryID prevID = null;

          for (EntryID id : entryIDList)
          {
            if (prevID != null && id.equals(prevID) && logger.isTraceEnabled())
            {
              logger.trace("Duplicate reference to ID %d%n%s",
                         id.longValue(), keyDump(index, key.getData()));
            }
            prevID = id;

            Entry entry;
            try
            {
              entry = id2entry.get(null, id, LockMode.DEFAULT);
            }
            catch (Exception e)
            {
              logger.traceException(e);
              errorCount++;
              continue;
            }

            if (entry == null)
            {
              errorCount++;
              if (logger.isTraceEnabled())
              {
                logger.trace("Reference to unknown ID %d%n%s",
                           id.longValue(), keyDump(index, key.getData()));
              }
              continue;
            }

            // As an optimization avoid passing in a real set and wasting time
            // hashing and comparing a potentially large set of values, as well
            // as using up memory. Instead just intercept the add() method and
            // detect when an equivalent value has been added.

            // We need to use an AtomicBoolean here since anonymous classes
            // require referenced external variables to be final.
            final AtomicBoolean foundMatchingKey = new AtomicBoolean(false);

            Set<ByteString> dummySet = new AbstractSet<ByteString>()
            {
              @Override
              public Iterator<ByteString> iterator()
              {
                // The set is always empty.
                return Collections.<ByteString> emptySet().iterator();
              }

              @Override
              public int size()
              {
                // The set is always empty.
                return 0;
              }

              @Override
              public boolean add(ByteString e)
              {
                if (value.equals(e))
                {
                  // We could terminate processing at this point by throwing an
                  // UnsupportedOperationException, but this optimization is
                  // already ugly enough.
                  foundMatchingKey.set(true);
                }
                return true;
              }

            };

            index.indexEntry(entry, dummySet, options);

            if (!foundMatchingKey.get())
            {
              errorCount++;
              if (logger.isTraceEnabled())
              {
                logger.trace("Reference to entry "
                    + "<%s> which does not match the value%n%s",
                    entry.getName(),
                    keyDump(index, value.toByteArray()));
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
    DN dn = entry.getName();

    // Check the ID is in dn2id with the correct DN.
    try
    {
      EntryID id = dn2id.get(null, dn, LockMode.DEFAULT);
      if (id == null)
      {
        if (logger.isTraceEnabled())
        {
          logger.trace("File dn2id is missing key %s.%n", dn);
        }
        errorCount++;
      }
      else if (!id.equals(entryID))
      {
        if (logger.isTraceEnabled())
        {
          logger.trace("File dn2id has ID %d instead of %d for key %s.%n", id.longValue(), entryID.longValue(), dn);
        }
        errorCount++;
      }
    }
    catch (Exception e)
    {
      if (logger.isTraceEnabled())
      {
        logger.traceException(e);
        logger.trace("File dn2id has error reading key %s: %s.%n", dn, e.getMessage());
      }
      errorCount++;
    }

    // Check the parent DN is in dn2id.
    DN parentDN = getParent(dn);
    if (parentDN != null)
    {
      try
      {
        EntryID id = dn2id.get(null, parentDN, LockMode.DEFAULT);
        if (id == null)
        {
          if (logger.isTraceEnabled())
          {
            logger.trace("File dn2id is missing key %s.%n", parentDN);
          }
          errorCount++;
        }
      }
      catch (Exception e)
      {
        if (logger.isTraceEnabled())
        {
          logger.traceException(e);
          logger.trace("File dn2id has error reading key %s: %s.%n", parentDN, e.getMessage());
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
    DN dn = entry.getName();

    DN parentDN = getParent(dn);
    if (parentDN != null)
    {
      EntryID parentID = null;
      try
      {
        parentID = dn2id.get(null, parentDN, LockMode.DEFAULT);
        if (parentID == null)
        {
          if (logger.isTraceEnabled())
          {
            logger.trace("File dn2id is missing key %s.%n", parentDN);
          }
          errorCount++;
        }
      }
      catch (Exception e)
      {
        if (logger.isTraceEnabled())
        {
          logger.traceException(e);
          logger.trace("File dn2id has error reading key %s: %s.", parentDN, e.getMessage());
        }
        errorCount++;
      }
      if (parentID != null)
      {
        try
        {
          ConditionResult cr = id2c.containsID(null, parentID.getDatabaseEntry(), entryID);
          if (cr == ConditionResult.FALSE)
          {
            if (logger.isTraceEnabled())
            {
              logger.trace("File id2children is missing ID %d for key %d.%n",
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
          if (logger.isTraceEnabled())
          {
            logger.traceException(e);

            logger.trace("File id2children has error reading key %d: %s.",
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
    for (DN dn = getParent(entry.getName()); dn != null; dn = getParent(dn))
    {
      EntryID id = null;
      try
      {
        id = dn2id.get(null, dn, LockMode.DEFAULT);
        if (id == null)
        {
          if (logger.isTraceEnabled())
          {
            logger.trace("File dn2id is missing key %s.%n", dn);
          }
          errorCount++;
        }
      }
      catch (Exception e)
      {
        if (logger.isTraceEnabled())
        {
          logger.traceException(e);
          logger.trace("File dn2id has error reading key %s: %s.%n", dn, e.getMessage());
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
            if (logger.isTraceEnabled())
            {
              logger.trace("File id2subtree is missing ID %d " +
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
          if (logger.isTraceEnabled())
          {
            logger.traceException(e);

            logger.trace("File id2subtree has error reading key %d: %s.%n",
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
    StringBuilder buffer = new StringBuilder(128);
    buffer.append("File: ");
    buffer.append(index);
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
    StringBuilder buffer = new StringBuilder(128);
    buffer.append("File: ");
    buffer.append(vlvIndex);
    buffer.append(ServerConstants.EOL);
    buffer.append("Key (last sort values):");
    if(keySortValues != null)
    {
      buffer.append(keySortValues);
    }
    else
    {
      buffer.append("UNBOUNDED (0x00)");
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
        if (logger.isTraceEnabled())
        {
          logger.traceException(e);

          logger.trace("Error normalizing values of attribute %s in " +
              "entry <%s>: %s.%n",
                     attrIndex.getAttributeType(), entry.getName(), e.getMessageObject());
        }
      }
    }

    for (VLVIndex vlvIndex : vlvIndexList)
    {
      try
      {
        if (vlvIndex.shouldInclude(entry)
            && !vlvIndex.containsValues(null, entryID.longValue(),
                    vlvIndex.getSortValues(entry), vlvIndex.getSortTypes()))
        {
          if(logger.isTraceEnabled())
          {
            logger.trace("Missing entry %s in VLV index %s", entry.getName(), vlvIndex.getName());
          }
          errorCount++;
        }
      }
      catch (DirectoryException e)
      {
        if (logger.isTraceEnabled())
        {
          logger.traceException(e);
          logger.trace("Error checking entry %s against filter or base DN for VLV index %s: %s",
                     entry.getName(), vlvIndex.getName(), e.getMessageObject());
        }
        errorCount++;
      }
      catch (DatabaseException e)
      {
        if (logger.isTraceEnabled())
        {
          logger.traceException(e);
          logger.trace("Error reading VLV index %s for entry %s: %s",
              vlvIndex.getName(), entry.getName(), StaticUtils.getBacktrace(e));
        }
        errorCount++;
      }
      catch (JebException e)
      {
        if (logger.isTraceEnabled())
        {
          logger.traceException(e);
          logger.trace("Error reading VLV index %s for entry %s: %s",
              vlvIndex.getName(), entry.getName(), StaticUtils.getBacktrace(e));
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
    if (attrList == null || attrList.isEmpty())
    {
      return;
    }

    Transaction txn = null;
    Index equalityIndex = attrIndex.getEqualityIndex();
    Index presenceIndex = attrIndex.getPresenceIndex();
    Index substringIndex = attrIndex.getSubstringIndex();
    Index orderingIndex = attrIndex.getOrderingIndex();
    Index approximateIndex = attrIndex.getApproximateIndex();
    // TODO: Add support for Extended Matching Rules indexes.

    if (presenceIndex != null)
    {
      verifyAttributeInIndex(presenceIndex, txn, JEBUtils.presenceKey, entryID);
    }

    final DatabaseEntry key = new DatabaseEntry();
    for (Attribute attr : attrList)
    {
      final AttributeType attrType = attr.getAttributeType();
      MatchingRule equalityRule = attrType.getEqualityMatchingRule();
      for (ByteString value : attr)
      {
        byte[] normalizedBytes = normalize(equalityRule, value);

        if (equalityIndex != null)
        {
          key.setData(normalizedBytes);
          verifyAttributeInIndex(equalityIndex, txn, key, entryID);
        }

        if (substringIndex != null)
        {
          for (ByteString keyBytes : attrIndex.substringKeys(normalizedBytes))
          {
            key.setData(keyBytes.toByteArray());
            verifyAttributeInIndex(substringIndex, txn, key, entryID);
          }
        }

        if (orderingIndex != null)
        {
          key.setData(normalize(attrType.getOrderingMatchingRule(), value));
          verifyAttributeInIndex(orderingIndex, txn, key, entryID);
        }

        if (approximateIndex != null)
        {
          key.setData(normalize(attrType.getApproximateMatchingRule(), value));
          verifyAttributeInIndex(approximateIndex, txn, key, entryID);
        }
      }
    }
  }

  private void verifyAttributeInIndex(Index index, Transaction txn,
      DatabaseEntry key, EntryID entryID)
  {
    try
    {
      ConditionResult cr = index.containsID(txn, key, entryID);
      if (cr == ConditionResult.FALSE)
      {
        if (logger.isTraceEnabled())
        {
          logger.trace("Missing ID %d%n%s",
                     entryID.longValue(),
                     keyDump(index, key.getData()));
        }
        errorCount++;
      }
      else if (cr == ConditionResult.UNDEFINED)
      {
        incrEntryLimitStats(index, key.getData());
      }
    }
    catch (DatabaseException e)
    {
      if (logger.isTraceEnabled())
      {
        logger.traceException(e);

        logger.trace("Error reading database: %s%n%s",
                   e.getMessage(),
                   keyDump(index, key.getData()));
      }
      errorCount++;
    }
  }

  private byte[] normalize(MatchingRule matchingRule,
      ByteString value) throws DirectoryException
  {
    try
    {
      return matchingRule.normalizeAttributeValue(value).toByteArray();
    }
    catch (DecodeException e)
    {
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
          e.getMessageObject(), e);
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

  /** This class reports progress of the verify job at fixed intervals. */
  private final class ProgressTask extends TimerTask
  {
    /** The total number of records to process. */
    private long totalCount;

    /**
     * The number of records that had been processed at the time of the
     * previous progress report.
     */
    private long previousCount;

    /** The time in milliseconds of the previous progress report. */
    private long previousTime;
    /** The environment statistics at the time of the previous report. */
    private EnvironmentStats prevEnvStats;

    /**
     * The number of bytes in a megabyte.
     * Note that 1024*1024 bytes may eventually become known as a mebibyte(MiB).
     */
    private static final int bytesPerMegabyte = 1024*1024;

    /**
     * Create a new verify progress task.
     * @param indexIterator boolean, indicates if the task is iterating
     * through indexes or the entries.
     * @throws DatabaseException An error occurred while accessing the JE
     * database.
     */
    private ProgressTask(boolean indexIterator) throws DatabaseException
    {
      previousTime = System.currentTimeMillis();
      prevEnvStats = rootContainer.getEnvironmentStats(new StatsConfig());

      if (indexIterator)
      {
        if (verifyDN2ID)
        {
          totalCount = dn2id.getRecordCount();
        }
        else if (verifyID2Children)
        {
          totalCount = id2c.getRecordCount();
        }
        else if (verifyID2Subtree)
        {
          totalCount = id2s.getRecordCount();
        }
        else if(attrIndexList.size() > 0)
        {
          AttributeIndex attrIndex = attrIndexList.get(0);
          totalCount = 0;
          totalCount += getRecordCount(attrIndex.getEqualityIndex());
          totalCount += getRecordCount(attrIndex.getPresenceIndex());
          totalCount += getRecordCount(attrIndex.getSubstringIndex());
          totalCount += getRecordCount(attrIndex.getOrderingIndex());
          totalCount += getRecordCount(attrIndex.getApproximateIndex());
          // TODO: Add support for Extended Matching Rules indexes.
        }
        else if (vlvIndexList.size() > 0)
        {
          totalCount = vlvIndexList.get(0).getRecordCount();
        }
      }
      else
      {
        totalCount = rootContainer.getEntryContainer(
          verifyConfig.getBaseDN()).getEntryCount();
      }
    }

    private long getRecordCount(Index index)
    {
      return index != null ? index.getRecordCount() : 0;
    }

    /** The action to be performed by this timer task. */
    @Override
    public void run()
    {
      long latestCount = keyCount;
      long deltaCount = latestCount - previousCount;
      long latestTime = System.currentTimeMillis();
      long deltaTime = latestTime - previousTime;

      if (deltaTime == 0)
      {
        return;
      }

      float rate = 1000f*deltaCount / deltaTime;

      logger.info(NOTE_VERIFY_PROGRESS_REPORT, latestCount, totalCount, errorCount, rate);

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

        logger.debug(INFO_CACHE_AND_MEMORY_REPORT, freeMemory, cacheMissRate);

        prevEnvStats = envStats;
      }
      catch (DatabaseException e)
      {
        logger.traceException(e);
      }


      previousCount = latestCount;
      previousTime = latestTime;
    }
  }
}
