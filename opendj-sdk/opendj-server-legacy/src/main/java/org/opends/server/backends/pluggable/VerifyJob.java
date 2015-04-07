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
package org.opends.server.backends.pluggable;

import static org.opends.messages.JebMessages.*;
import static org.opends.server.backends.pluggable.JebFormat.*;
import static org.opends.server.backends.pluggable.VLVIndex.*;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.forgerock.opendj.ldap.spi.IndexingOptions;
import org.opends.server.admin.std.meta.BackendIndexCfgDefn.IndexType;
import org.opends.server.backends.VerifyConfig;
import org.opends.server.backends.pluggable.AttributeIndex.MatchingRuleIndex;
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.ReadOperation;
import org.opends.server.backends.pluggable.spi.ReadableTransaction;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.util.ServerConstants;
import org.opends.server.util.StaticUtils;

/** This class is used to run an index verification process on the backend. */
class VerifyJob
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
  VerifyJob(VerifyConfig verifyConfig)
  {
    this.verifyConfig = verifyConfig;
  }

  /**
   * Verify the backend.
   *
   * @param rootContainer The root container that holds the entries to verify.
   * @return The error count.
   * @throws StorageRuntimeException If an error occurs in the database.
   * @throws DirectoryException If an error occurs while verifying the backend.
   */
  long verifyBackend(final RootContainer rootContainer) throws StorageRuntimeException,
      DirectoryException
  {
    try
    {
      return rootContainer.getStorage().read(new ReadOperation<Long>()
      {
        @Override
        public Long run(ReadableTransaction txn) throws Exception
        {
          return verifyBackend0(txn, rootContainer);
        }
      });
    }
    catch (StorageRuntimeException e)
    {
      throw e;
    }
    catch (Exception e)
    {
      throw new StorageRuntimeException(e);
    }
  }

  private long verifyBackend0(ReadableTransaction txn, RootContainer rootContainer)
      throws StorageRuntimeException, DirectoryException
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
              throw new StorageRuntimeException(msg.toString());
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
              throw new StorageRuntimeException(msg.toString());
            }
          }
          else if(lowerName.startsWith("vlv."))
          {
            if(lowerName.length() < 5)
            {
              LocalizableMessage msg = ERR_JEB_VLV_INDEX_NOT_CONFIGURED.get(lowerName);
              throw new StorageRuntimeException(msg.toString());
            }

            VLVIndex vlvIndex =
                entryContainer.getVLVIndex(lowerName.substring(4));
            if(vlvIndex == null)
            {
              LocalizableMessage msg =
                  ERR_JEB_VLV_INDEX_NOT_CONFIGURED.get(lowerName.substring(4));
              throw new StorageRuntimeException(msg.toString());
            }

            vlvIndexList.add(vlvIndex);
          }
          else
          {
            AttributeType attrType = DirectoryServer.getAttributeType(lowerName);
            if (attrType == null)
            {
              LocalizableMessage msg = ERR_JEB_ATTRIBUTE_INDEX_NOT_CONFIGURED.get(index);
              throw new StorageRuntimeException(msg.toString());
            }
            AttributeIndex attrIndex = entryContainer.getAttributeIndex(attrType);
            if (attrIndex == null)
            {
              LocalizableMessage msg = ERR_JEB_ATTRIBUTE_INDEX_NOT_CONFIGURED.get(index);
              throw new StorageRuntimeException(msg.toString());
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
      TimerTask progressTask = new ProgressTask(cleanMode, txn);
      timer.scheduleAtFixedRate(progressTask, progressInterval, progressInterval);

      // Iterate through the index keys.
      try
      {
        if (cleanMode)
        {
          iterateIndex(txn);
        }
        else
        {
          iterateID2Entry(txn);

          // Make sure the vlv indexes are in correct order.
          for(VLVIndex vlvIndex : vlvIndexList)
          {
            iterateVLVIndex(txn, vlvIndex, false);
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
        logger.info(NOTE_JEB_VERIFY_CLEAN_FINAL_STATUS, keyCount, errorCount, totalTime/1000, rate);

        if (multiReferenceCount > 0)
        {
          float averageEntryReferences = 0;
          if (keyCount > 0)
          {
            averageEntryReferences = entryReferencesCount/keyCount;
          }

          if (logger.isDebugEnabled())
          {
            logger.debug(INFO_JEB_VERIFY_MULTIPLE_REFERENCE_COUNT, multiReferenceCount);
            logger.debug(INFO_JEB_VERIFY_ENTRY_LIMIT_EXCEEDED_COUNT, entryLimitExceededCount);
            logger.debug(INFO_JEB_VERIFY_AVERAGE_REFERENCE_COUNT, averageEntryReferences);
            logger.debug(INFO_JEB_VERIFY_MAX_REFERENCE_COUNT, maxEntryPerValue);
          }
        }
      }
      else
      {
        logger.info(NOTE_JEB_VERIFY_FINAL_STATUS, keyCount, errorCount, totalTime/1000, rate);
        if (entryLimitMap.size() > 0)
        {
          logger.debug(INFO_JEB_VERIFY_ENTRY_LIMIT_STATS_HEADER);

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

            logger.debug(INFO_JEB_VERIFY_ENTRY_LIMIT_STATS_ROW, index, values.length, values[0],
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
   * @throws StorageRuntimeException If an error occurs in the database.
   */
  private void iterateID2Entry(ReadableTransaction txn) throws StorageRuntimeException
  {
    Cursor<ByteString, ByteString> cursor = txn.openCursor(id2entry.getName());
    try
    {
      long storedEntryCount = id2entry.getRecordCount(txn);
      while (cursor.next())
      {
        ByteString key = cursor.getKey();
        ByteString value = cursor.getValue();

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

            logger.trace("Malformed id2entry ID %s.%n", StaticUtils.bytesToHex(key));
          }
          continue;
        }

        keyCount++;

        Entry entry;
        try
        {
          entry = ID2Entry.entryFromDatabase(value, rootContainer.getCompressedSchema());
        }
        catch (Exception e)
        {
          errorCount++;
          if (logger.isTraceEnabled())
          {
            logger.traceException(e);

            logger.trace("Malformed id2entry record for ID %d:%n%s%n", entryID, StaticUtils.bytesToHex(value));
          }
          continue;
        }

        verifyEntry(txn, entryID, entry);
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
   * @throws StorageRuntimeException If an error occurs in the database.
   * @throws DirectoryException If an error occurs reading values in the index.
   */
  private void iterateIndex(ReadableTransaction txn) throws StorageRuntimeException, DirectoryException
  {
    if (verifyDN2ID)
    {
      iterateDN2ID(txn);
    }
    else if (verifyID2Children)
    {
      iterateID2Children(txn);
    }
    else if (verifyID2Subtree)
    {
      iterateID2Subtree(txn);
    }
    else if (attrIndexList.size() > 0)
    {
      AttributeIndex attrIndex = attrIndexList.get(0);
      final IndexingOptions options = attrIndex.getIndexingOptions();
      for (MatchingRuleIndex index : attrIndex.getDefaultNameToIndexes().values())
      {
        iterateAttrIndex(txn, index, options);
      }
     // TODO: Need to iterate through ExtendedMatchingRules indexes.
    }
    else if (vlvIndexList.size() > 0)
    {
      iterateVLVIndex(txn, vlvIndexList.get(0), true);
    }
  }

  /**
   * Iterate through the entries in DN2ID to perform a check for
   * index cleanliness.
   *
   * @throws StorageRuntimeException If an error occurs in the database.
   */
  private void iterateDN2ID(ReadableTransaction txn) throws StorageRuntimeException
  {
    Cursor<ByteString, ByteString> cursor = txn.openCursor(dn2id.getName());
    try
    {
      while (cursor.next())
      {
        keyCount++;

        ByteString key = cursor.getKey();
        ByteString value = cursor.getValue();

        EntryID entryID;
        try
        {
          entryID = new EntryID(value);
        }
        catch (Exception e)
        {
          errorCount++;
          if (logger.isTraceEnabled())
          {
            logger.traceException(e);

            logger.trace("File dn2id has malformed ID for DN <%s>:%n%s%n", key, StaticUtils.bytesToHex(value));
          }
          continue;
        }

        Entry entry;
        try
        {
          entry = id2entry.get(txn, entryID);
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
            logger.trace("File dn2id has DN <%s> referencing unknown ID %d%n", key, entryID);
          }
        }
        else if (!key.equals(dnToDNKey(entry.getName(), verifyConfig.getBaseDN().size())))
        {
          errorCount++;
          if (logger.isTraceEnabled())
          {
            logger.trace("File dn2id has DN <%s> referencing entry with wrong DN <%s>%n", key, entry.getName());
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
   * @throws StorageRuntimeException If an error occurs in the database.
   */
  private void iterateID2Children(ReadableTransaction txn) throws StorageRuntimeException
  {
    Cursor<ByteString, EntryIDSet> cursor = id2c.openCursor(txn);
    try
    {
      while (cursor.next())
      {
        keyCount++;

        ByteString key = cursor.getKey();

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

            logger.trace("File id2children has malformed ID %s%n", StaticUtils.bytesToHex(key));
          }
          continue;
        }

        EntryIDSet entryIDSet;

        try
        {
          entryIDSet = cursor.getValue();
        }
        catch (Exception e)
        {
          errorCount++;
          logger.traceException(e);
          logger.trace("File id2children has malformed ID list for ID %s", entryID);
          continue;
        }

        updateIndexStats(entryIDSet);

        if (entryIDSet.isDefined())
        {
          Entry entry;
          try
          {
            entry = id2entry.get(txn, entryID);
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
              logger.trace("File id2children has unknown ID %d%n", entryID);
            }
            continue;
          }

          for (EntryID id : entryIDSet)
          {
            Entry childEntry;
            try
            {
              childEntry = id2entry.get(txn, id);
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
                logger.trace("File id2children has ID %d referencing unknown ID %d%n", entryID, id);
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
                    entryID, entry.getName(), id, childEntry.getName());
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
   * @throws StorageRuntimeException If an error occurs in the database.
   */
  private void iterateID2Subtree(ReadableTransaction txn) throws StorageRuntimeException
  {
    Cursor<ByteString, EntryIDSet> cursor = id2s.openCursor(txn);
    try
    {
      while (cursor.next())
      {
        keyCount++;

        ByteString key = cursor.getKey();
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

            logger.trace("File id2subtree has malformed ID %s%n", StaticUtils.bytesToHex(key));
          }
          continue;
        }

        EntryIDSet entryIDSet;
        try
        {
          entryIDSet = cursor.getValue();
        }
        catch (Exception e)
        {
          errorCount++;
          logger.traceException(e);
          logger.trace("File id2subtree has malformed ID list  for ID %s", entryID);
          continue;
        }

        updateIndexStats(entryIDSet);

        if (entryIDSet.isDefined())
        {
          Entry entry;
          try
          {
            entry = id2entry.get(txn, entryID);
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
              logger.trace("File id2subtree has unknown ID %d%n", entryID);
            }
            continue;
          }

          for (EntryID id : entryIDSet)
          {
            Entry subordEntry;
            try
            {
              subordEntry = id2entry.get(txn, id);
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
                    "unknown ID %d%n", entryID, id);
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
                    entryID, entry.getName(), id, subordEntry.getName());
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
  private void incrEntryLimitStats(Index index, ByteString key)
  {
    HashMap<ByteString,Long> hashMap = entryLimitMap.get(index);
    if (hashMap == null)
    {
      hashMap = new HashMap<ByteString, Long>();
      entryLimitMap.put(index, hashMap);
    }
    Long counter = hashMap.get(key);
    if (counter != null)
    {
      counter++;
    }
    else
    {
      counter = 1L;
    }
    hashMap.put(key, counter);
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
   * @throws StorageRuntimeException If an error occurs in the database.
   * @throws DirectoryException If an error occurs reading values in the index.
   */
  private void iterateVLVIndex(ReadableTransaction txn, VLVIndex vlvIndex, boolean verifyID)
      throws StorageRuntimeException, DirectoryException
  {
    if(vlvIndex == null || !verifyID)
    {
      return;
    }

    Cursor<ByteString, ByteString> cursor = txn.openCursor(vlvIndex.getName());
    try
    {
      while (cursor.next())
      {
        ByteString key = cursor.getKey();
        EntryID id = new EntryID(decodeEntryIDFromVLVKey(key));
        Entry entry;
        try
        {
          entry = id2entry.get(txn, id);
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
            logger.trace("Reference to unknown entry ID %s%n%s", id, keyDump(vlvIndex.toString(), key));
          }
          continue;
        }

        ByteString expectedKey = vlvIndex.encodeVLVKey(entry, id.longValue());
        if (expectedKey.compareTo(key) != 0)
        {
          errorCount++;
          if (logger.isTraceEnabled())
          {
            logger.trace("Reference to entry ID %s has a key which does not match the expected key%n%s",
                id, keyDump(vlvIndex.toString(), expectedKey));
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
   * @throws StorageRuntimeException If an error occurs in the database.
   */
  private void iterateAttrIndex(ReadableTransaction txn, MatchingRuleIndex index, IndexingOptions options)
      throws StorageRuntimeException
  {
    if (index == null)
    {
      return;
    }

    Cursor<ByteString,EntryIDSet> cursor = index.openCursor(txn);
    try
    {
      while (cursor.next())
      {
        keyCount++;

        final ByteString key = cursor.getKey();

        EntryIDSet entryIDSet;
        try
        {
          entryIDSet = cursor.getValue();
        }
        catch (Exception e)
        {
          errorCount++;
          logger.traceException(e);
          logger.trace("Malformed ID list: %n%s", keyDump(index.toString(), key));
          continue;
        }

        updateIndexStats(entryIDSet);

        if (entryIDSet.isDefined())
        {
          EntryID prevID = null;

          for (EntryID id : entryIDSet)
          {
            if (prevID != null && id.equals(prevID) && logger.isTraceEnabled())
            {
              if (logger.isTraceEnabled())
              {
                logger.trace("Duplicate reference to ID %d%n%s", id, keyDump(index.toString(), key));
              }
            }
            prevID = id;

            Entry entry;
            try
            {
              entry = id2entry.get(txn, id);
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
                logger.trace("Reference to unknown ID %d%n%s", id, keyDump(index.toString(), key));
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
                if (key.equals(e))
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
                logger.trace("Reference to entry <%s> which does not match the value%n%s",
                    entry.getName(), keyDump(index.toString(), key));
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
  private void verifyEntry(ReadableTransaction txn, EntryID entryID, Entry entry)
  {
    if (verifyDN2ID)
    {
      verifyDN2ID(txn, entryID, entry);
    }
    if (verifyID2Children)
    {
      verifyID2Children(txn, entryID, entry);
    }
    if (verifyID2Subtree)
    {
      verifyID2Subtree(txn, entryID, entry);
    }
    verifyIndex(txn, entryID, entry);
  }

  /**
   * Check that the DN2ID index is complete for a given entry.
   *
   * @param entryID The entry ID.
   * @param entry The entry to be checked.
   */
  private void verifyDN2ID(ReadableTransaction txn, EntryID entryID, Entry entry)
  {
    DN dn = entry.getName();

    // Check the ID is in dn2id with the correct DN.
    try
    {
      EntryID id = dn2id.get(txn, dn);
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
          logger.trace("File dn2id has ID %d instead of %d for key %s.%n", id, entryID, dn);
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
        EntryID id = dn2id.get(txn, parentDN);
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
  private void verifyID2Children(ReadableTransaction txn, EntryID entryID, Entry entry)
  {
    DN dn = entry.getName();

    DN parentDN = getParent(dn);
    if (parentDN != null)
    {
      EntryID parentID = null;
      try
      {
        parentID = dn2id.get(txn, parentDN);
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
          ConditionResult cr = indexContainsID(id2c, txn, parentID.toByteString(), entryID);
          if (cr == ConditionResult.FALSE)
          {
            if (logger.isTraceEnabled())
            {
              logger.trace("File id2children is missing ID %d for key %d.%n", entryID, parentID);
            }
            errorCount++;
          }
          else if (cr == ConditionResult.UNDEFINED)
          {
            incrEntryLimitStats(id2c, parentID.toByteString());
          }
        }
        catch (StorageRuntimeException e)
        {
          if (logger.isTraceEnabled())
          {
            logger.traceException(e);

            logger.trace("File id2children has error reading key %d: %s.", parentID, e.getMessage());
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
  private void verifyID2Subtree(ReadableTransaction txn, EntryID entryID, Entry entry)
  {
    for (DN dn = getParent(entry.getName()); dn != null; dn = getParent(dn))
    {
      EntryID id = null;
      try
      {
        id = dn2id.get(txn, dn);
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
          ConditionResult cr = indexContainsID(id2s, txn, id.toByteString(), entryID);
          if (cr == ConditionResult.FALSE)
          {
            if (logger.isTraceEnabled())
            {
              logger.trace("File id2subtree is missing ID %d for key %d.%n", entryID, id);
            }
            errorCount++;
          }
          else if (cr == ConditionResult.UNDEFINED)
          {
            incrEntryLimitStats(id2s, id.toByteString());
          }
        }
        catch (StorageRuntimeException e)
        {
          if (logger.isTraceEnabled())
          {
            logger.traceException(e);

            logger.trace("File id2subtree has error reading key %d: %s.%n", id, e.getMessage());
          }
          errorCount++;
        }
      }
    }
  }

  /**
   * Construct a printable string from a raw key value.
   *
   * @param indexName
   *          The name of the index database containing the key value.
   * @param key
   *          The bytes of the key.
   * @return A string that may be logged or printed.
   */
  private String keyDump(String indexName, ByteSequence key)
  {
    StringBuilder buffer = new StringBuilder(128);
    buffer.append("Index: ");
    buffer.append(indexName);
    buffer.append(ServerConstants.EOL);
    buffer.append("Key:");
    buffer.append(ServerConstants.EOL);
    StaticUtils.byteArrayToHexPlusAscii(buffer, key.toByteArray(), 6);
    return buffer.toString();
  }

  /**
   * Check that an attribute index is complete for a given entry.
   *
   * @param entryID
   *          The entry ID.
   * @param entry
   *          The entry to be checked.
   */
  private void verifyIndex(ReadableTransaction txn, EntryID entryID, Entry entry)
  {
    for (AttributeIndex attrIndex : attrIndexList)
    {
      try
      {
        List<Attribute> attrList = entry.getAttribute(attrIndex.getAttributeType());
        if (attrList != null)
        {
          verifyAttribute(txn, attrIndex, entryID, attrList);
        }
      }
      catch (DirectoryException e)
      {
        if (logger.isTraceEnabled())
        {
          logger.traceException(e);

          logger.trace("Error normalizing values of attribute %s in entry <%s>: %s.%n",
              attrIndex.getAttributeType(), entry.getName(), e.getMessageObject());
        }
      }
    }

    for (VLVIndex vlvIndex : vlvIndexList)
    {
      try
      {
        if (vlvIndex.verifyEntry(txn, entryID, entry))
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
      catch (StorageRuntimeException e)
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
  private void verifyAttribute(ReadableTransaction txn, AttributeIndex attrIndex, EntryID entryID,
      List<Attribute> attrList) throws DirectoryException
  {
    if (attrList == null || attrList.isEmpty())
    {
      return;
    }

    final Map<String, MatchingRuleIndex> nameToIndexes = attrIndex.getNameToIndexes();
    Index equalityIndex = nameToIndexes.get(IndexType.EQUALITY.toString());
    Index presenceIndex = nameToIndexes.get(IndexType.PRESENCE.toString());
    Index substringIndex = nameToIndexes.get(IndexType.SUBSTRING.toString());
    Index orderingIndex = nameToIndexes.get(IndexType.ORDERING.toString());
    Index approximateIndex = nameToIndexes.get(IndexType.APPROXIMATE.toString());
    // TODO: Add support for Extended Matching Rules indexes.

    if (presenceIndex != null)
    {
      verifyAttributeInIndex(presenceIndex, txn, AttributeIndex.PRESENCE_KEY, entryID);
    }

    for (Attribute attr : attrList)
    {
      final AttributeType attrType = attr.getAttributeType();
      MatchingRule equalityRule = attrType.getEqualityMatchingRule();
      for (ByteString value : attr)
      {
        ByteString normalizedBytes = normalize(equalityRule, value);

        if (equalityIndex != null)
        {
          verifyAttributeInIndex(equalityIndex, txn, normalizedBytes, entryID);
        }

        if (substringIndex != null)
        {
          for (ByteString key : attrIndex.substringKeys(normalizedBytes))
          {
            verifyAttributeInIndex(substringIndex, txn, key, entryID);
          }
        }

        if (orderingIndex != null)
        {
          ByteString key = normalize(attrType.getOrderingMatchingRule(), value);
          verifyAttributeInIndex(orderingIndex, txn, key, entryID);
        }

        if (approximateIndex != null)
        {
          ByteString key = normalize(attrType.getApproximateMatchingRule(), value);
          verifyAttributeInIndex(approximateIndex, txn, key, entryID);
        }
      }
    }
  }

  private void verifyAttributeInIndex(Index index, ReadableTransaction txn,
      ByteString key, EntryID entryID)
  {
    try
    {
      ConditionResult cr = indexContainsID(index, txn, key, entryID);
      if (cr == ConditionResult.FALSE)
      {
        if (logger.isTraceEnabled())
        {
          logger.trace("Missing ID %d%n%s", entryID, keyDump(index.toString(), key));
        }
        errorCount++;
      }
      else if (cr == ConditionResult.UNDEFINED)
      {
        incrEntryLimitStats(index, key);
      }
    }
    catch (StorageRuntimeException e)
    {
      if (logger.isTraceEnabled())
      {
        logger.traceException(e);

        logger.trace("Error reading database: %s%n%s", e.getMessage(), keyDump(index.toString(), key));
      }
      errorCount++;
    }
  }

  private ConditionResult indexContainsID(Index index, ReadableTransaction txn, ByteString key, EntryID entryID)
  {
    EntryIDSet entryIDSet = index.get(txn, key);
    if (entryIDSet.isDefined())
    {
      return ConditionResult.valueOf(entryIDSet.contains(entryID));
    }
    return ConditionResult.UNDEFINED;
  }

  private ByteString normalize(MatchingRule matchingRule, ByteString value) throws DirectoryException
  {
    try
    {
      return matchingRule.normalizeAttributeValue(value);
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

    /**
     * The number of bytes in a megabyte.
     * Note that 1024*1024 bytes may eventually become known as a mebibyte(MiB).
     */
    private static final int bytesPerMegabyte = 1024*1024;

    /**
     * Create a new verify progress task.
     * @param indexIterator boolean, indicates if the task is iterating
     * through indexes or the entries.
     * @throws StorageRuntimeException An error occurred while accessing the JE
     * database.
     */
    private ProgressTask(boolean indexIterator, ReadableTransaction txn) throws StorageRuntimeException
    {
      previousTime = System.currentTimeMillis();

      if (indexIterator)
      {
        if (verifyDN2ID)
        {
          totalCount = dn2id.getRecordCount(txn);
        }
        else if (verifyID2Children)
        {
          totalCount = id2c.getRecordCount(txn);
        }
        else if (verifyID2Subtree)
        {
          totalCount = id2s.getRecordCount(txn);
        }
        else if(attrIndexList.size() > 0)
        {
          AttributeIndex attrIndex = attrIndexList.get(0);
          totalCount = 0;
          for (MatchingRuleIndex index : attrIndex.getDefaultNameToIndexes().values())
          {
            totalCount += getRecordCount(txn, index);
          }
          // TODO: Add support for Extended Matching Rules indexes.
        }
        else if (vlvIndexList.size() > 0)
        {
          totalCount = vlvIndexList.get(0).getRecordCount(txn);
        }
      }
      else
      {
        totalCount = rootContainer.getEntryContainer(verifyConfig.getBaseDN()).getEntryCount(txn);
      }
    }

    private long getRecordCount(ReadableTransaction txn, Index index)
    {
      if (index != null)
      {
        return index.getRecordCount(txn);
      }
      return 0;
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

      logger.info(NOTE_JEB_VERIFY_PROGRESS_REPORT, latestCount, totalCount, errorCount, rate);

      try
      {
        Runtime runtime = Runtime.getRuntime();
        long freeMemory = runtime.freeMemory() / bytesPerMegabyte;

        // FIXME JNR compute the cache miss rate
        float cacheMissRate = 0;

        logger.debug(INFO_JEB_VERIFY_CACHE_AND_MEMORY_REPORT, freeMemory, cacheMissRate);
      }
      catch (StorageRuntimeException e)
      {
        logger.traceException(e);
      }


      previousCount = latestCount;
      previousTime = latestTime;
    }
  }
}
