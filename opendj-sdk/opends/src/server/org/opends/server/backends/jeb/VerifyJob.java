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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.backends.jeb;

import static org.opends.server.loggers.Error.logError;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.CursorConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.EnvironmentStats;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.StatsConfig;
import com.sleepycat.je.Transaction;

import org.opends.server.api.Backend;
import org.opends.server.api.OrderingMatchingRule;
import org.opends.server.core.DirectoryException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.Debug;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.ByteString;
import org.opends.server.types.ConditionResult;
import org.opends.server.types.DebugLogCategory;
import org.opends.server.types.DebugLogSeverity;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.SearchFilter;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.ServerConstants;

import static org.opends.server.loggers.Debug.debugException;
import static org.opends.server.messages.MessageHandler.getMessage;
import static org.opends.server.messages.JebMessages.*;

import java.io.File;
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
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.backends.jeb.VerifyJob";

  /**
   * The verify configuration.
   */
  private VerifyConfig verifyConfig;

  /**
   * The JE backend to be verified.
   */
  private Backend backend;

  /**
   * The database container to be verified within the backend.
   */
  private Container container;

  /**
   * The configuration of the JE backend.
   */
  private Config config;

  /**
   * A read-only JE database environment handle for the purpose of verification.
   */
  private Environment env;

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
   * Construct a VerifyJob.
   *
   * @param backend The backend performing the verify process.
   * @param config The backend configuration.
   * @param verifyConfig The verify configuration.
   */
  public VerifyJob(Backend backend, Config config, VerifyConfig verifyConfig)
  {
    this.verifyConfig = verifyConfig;
    this.backend = backend;
    this.config = config;
  }

  /**
   * Verify the backend.
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws JebException If an error occurs in the JE backend.
   */
  public void verifyBackend() throws DatabaseException, JebException
  {
    File backendDirectory = config.getBackendDirectory();

    // Open the environment read-only.
    EnvironmentConfig envConfig = config.getEnvironmentConfig();
    envConfig.setReadOnly(true);
    envConfig.setAllowCreate(false);
    envConfig.setTransactional(false);
    env = new Environment(backendDirectory, envConfig);

    Debug.debugMessage(DebugLogCategory.BACKEND, DebugLogSeverity.INFO,
                       CLASS_NAME, "verifyBackend",
                       env.getConfig().toString());

    // Open a container read-only.
    String containerName =
         BackendImpl.getContainerName(verifyConfig.getBaseDN());
    container = new Container(env, containerName);
    EntryContainer entryContainer =
         new EntryContainer(backend, config, container);
    entryContainer.openReadOnly();

    ArrayList<String> completeList = verifyConfig.getCompleteList();
    ArrayList<String> cleanList = verifyConfig.getCleanList();

    boolean cleanMode = false;
    if (completeList.isEmpty() && cleanList.isEmpty())
    {
      verifyDN2ID = true;
      verifyID2Children = true;
      verifyID2Subtree = true;
      Map<AttributeType,IndexConfig> indexMap = config.getIndexConfigMap();
      for (IndexConfig ic : indexMap.values())
      {
        AttributeIndex attrIndex =
             entryContainer.getAttributeIndex(ic.getAttributeType());
        attrIndexList.add(attrIndex);
      }
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
        else
        {
          AttributeType attrType = DirectoryServer.getAttributeType(lowerName);
          if (attrType == null)
          {
            int msgID = MSGID_JEB_ATTRIBUTE_INDEX_NOT_CONFIGURED;
            String msg = getMessage(msgID, index);
            throw new JebException(msgID, msg);
          }
          AttributeIndex attrIndex = entryContainer.getAttributeIndex(attrType);
          if (attrIndex == null)
          {
            int msgID = MSGID_JEB_ATTRIBUTE_INDEX_NOT_CONFIGURED;
            String msg = getMessage(msgID, index);
            throw new JebException(msgID, msg);
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
    // the entry container methods.
    id2entry = entryContainer.getID2Entry();
    dn2id = entryContainer.getDN2ID();
    id2c = entryContainer.getID2Children();
    id2s = entryContainer.getID2Subtree();

    // Make a note of the time we started.
    long startTime = System.currentTimeMillis();

    try
    {
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
          }
        }
        finally
        {
          timer.cancel();
        }
    }
    finally
    {
      entryContainer.close();
    }

    long finishTime = System.currentTimeMillis();
    long totalTime = (finishTime - startTime);

    float rate = 0;
    if (totalTime > 0)
    {
      rate = 1000f*keyCount / totalTime;
    }

    if (cleanMode)
    {
      int msgID = MSGID_JEB_VERIFY_CLEAN_FINAL_STATUS;
      String message = getMessage(msgID, keyCount, errorCount,
                                  totalTime/1000, rate);
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE,
               message, msgID);

      if (multiReferenceCount > 0)
      {
        float averageEntryReferences = 0;
        if (keyCount > 0)
        {
          averageEntryReferences = (float)entryReferencesCount/keyCount;
        }

        msgID = MSGID_JEB_VERIFY_MULTIPLE_REFERENCE_COUNT;
        message = getMessage(msgID, multiReferenceCount);
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE,
                 message, msgID);

        msgID = MSGID_JEB_VERIFY_ENTRY_LIMIT_EXCEEDED_COUNT;
        message = getMessage(msgID, entryLimitExceededCount);
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE,
                 message, msgID);

        msgID = MSGID_JEB_VERIFY_AVERAGE_REFERENCE_COUNT;
        message = getMessage(msgID, averageEntryReferences);
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE,
                 message, msgID);

        msgID = MSGID_JEB_VERIFY_MAX_REFERENCE_COUNT;
        message = getMessage(msgID, maxEntryPerValue);
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE,
                 message, msgID);
      }
    }
    else
    {
      int msgID = MSGID_JEB_VERIFY_FINAL_STATUS;
      String message = getMessage(msgID, keyCount, errorCount,
                                  totalTime/1000, rate);
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE,
               message, msgID);

      if (entryLimitMap.size() > 0)
      {
        msgID = MSGID_JEB_VERIFY_ENTRY_LIMIT_STATS_HEADER;
        message = getMessage(msgID);
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE,
                 message, msgID);

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

          msgID = MSGID_JEB_VERIFY_ENTRY_LIMIT_STATS_ROW;
          message = getMessage(msgID, index.toString(), values.length,
                               values[0], values[values.length-1], medianValue);
          logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE,
                   message, msgID);
        }
      }
    }
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

      Long storedEntryCount = null;

      OperationStatus status;
      for (status = cursor.getFirst(key, data, LockMode.DEFAULT);
           status == OperationStatus.SUCCESS;
           status = cursor.getNext(key, data, LockMode.DEFAULT))
      {
        EntryID entryID = null;
        try
        {
          entryID = new EntryID(key);
        }
        catch (Exception e)
        {
          assert debugException(CLASS_NAME, "iterateID2Entry", e);
          errorCount++;
          System.err.printf("Malformed id2entry ID %s.%n",
                            StaticUtils.bytesToHex(key.getData()));
          continue;
        }

        if (entryID.longValue() == 0)
        {
          // This is the stored entry count.
          storedEntryCount = JebFormat.entryIDFromDatabase(data.getData());
        }
        else
        {
          keyCount++;

          Entry entry = null;
          try
          {
            entry = JebFormat.entryFromDatabase(data.getData());
          }
          catch (Exception e)
          {
            assert debugException(CLASS_NAME, "iterateID2Entry", e);
            errorCount++;
            System.err.printf("Malformed id2entry record for ID %d:%n%s%n",
                              entryID.longValue(),
                              StaticUtils.bytesToHex(data.getData()));
            continue;
          }

          verifyEntry(entryID, entry);
        }
      }
      if (storedEntryCount != null)
      {
        if (keyCount != storedEntryCount)
        {
          errorCount++;
          System.err.printf("The stored entry count in id2entry (%d) does " +
                            "not agree with the actual number of entry " +
                            "records found (%d).%n",
                            storedEntryCount, keyCount);
        }
      }
      else
      {
        errorCount++;
        System.err.printf("Missing record count in id2entry.%n");
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
   */
  private void iterateIndex() throws JebException, DatabaseException
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
      AttributeIndex attrIndex = attrIndexList.get(0);

      iterateAttrIndex(attrIndex.getAttributeType(), attrIndex.equalityIndex);
      iterateAttrIndex(attrIndex.getAttributeType(), attrIndex.presenceIndex);
      iterateAttrIndex(attrIndex.getAttributeType(), attrIndex.substringIndex);
      iterateAttrIndex(attrIndex.getAttributeType(), attrIndex.orderingIndex);
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
          assert debugException(CLASS_NAME, "iterateDN2ID", e);
          errorCount++;
          System.err.printf("File dn2id has malformed key %s.%n",
                            StaticUtils.bytesToHex(key.getData()));
          continue;
        }

        EntryID entryID = null;
        try
        {
          entryID = new EntryID(data);
        }
        catch (Exception e)
        {
          assert debugException(CLASS_NAME, "iterateDN2ID", e);
          errorCount++;
          System.err.printf("File dn2id has malformed ID for DN <%s>:%n%s%n",
                            dn.toNormalizedString(),
                            StaticUtils.bytesToHex(data.getData()));
          continue;
        }

        Entry entry = null;
        try
        {
          entry = id2entry.get(null, entryID);
        }
        catch (Exception e)
        {
          assert debugException(CLASS_NAME, "iterateDN2ID", e);
          errorCount++;
          System.err.println(e.getMessage());
          continue;
        }

        if (entry == null)
        {
          errorCount++;
          System.err.printf("File dn2id has DN <%s> referencing unknown " +
                            "ID %d%n",
                            dn.toNormalizedString(), entryID.longValue());
        }
        else
        {
          if (!entry.getDN().equals(dn))
          {
            errorCount++;
            System.err.printf("File dn2id has DN <%s> referencing entry " +
                              "with wrong DN <%s>%n",
                              dn.toNormalizedString(),
                              entry.getDN().toNormalizedString());
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

        EntryID entryID = null;
        try
        {
          entryID = new EntryID(key);
        }
        catch (Exception e)
        {
          assert debugException(CLASS_NAME, "iterateID2Children", e);
          errorCount++;
          System.err.printf("File id2children has malformed ID %s%n",
                            StaticUtils.bytesToHex(key.getData()));
          continue;
        }

        EntryIDSet entryIDList = null;
        try
        {
          JebFormat.entryIDListFromDatabase(data.getData());
          entryIDList = new EntryIDSet(key.getData(), data.getData());
        }
        catch (Exception e)
        {
          assert debugException(CLASS_NAME, "iterateID2Children", e);
          errorCount++;
          System.err.printf("File id2children has malformed ID list " +
                            "for ID %d:%n%s%n",
                            entryID,
                            StaticUtils.bytesToHex(data.getData()));
          continue;
        }

        updateIndexStats(entryIDList);

        if (entryIDList.isDefined())
        {
          Entry entry = null;
          try
          {
            entry = id2entry.get(null, entryID);
          }
          catch (Exception e)
          {
            assert debugException(CLASS_NAME, "iterateID2Children", e);
            errorCount++;
            System.err.println(e.getMessage());
            continue;
          }

          if (entry == null)
          {
            errorCount++;
            System.err.printf("File id2children has unknown ID %d%n",
                              entryID.longValue());
            continue;
          }

          for (EntryID id : entryIDList)
          {
            Entry childEntry = null;
            try
            {
              childEntry = id2entry.get(null, id);
            }
            catch (Exception e)
            {
              assert debugException(CLASS_NAME, "iterateID2Children", e);
              errorCount++;
              System.err.println(e.getMessage());
              continue;
            }

            if (childEntry == null)
            {
              errorCount++;
              System.err.printf("File id2children has ID %d referencing " +
                                "unknown ID %d%n",
                                entryID.longValue(), id.longValue());
              continue;
            }

            if (!childEntry.getDN().isDescendantOf(entry.getDN()) ||
                 childEntry.getDN().getRDNComponents().length !=
                 entry.getDN().getRDNComponents().length + 1)
            {
              errorCount++;
              System.err.printf("File id2children has ID %d with DN <%s> " +
                                "referencing ID %d with non-child DN <%s>%n",
                                entryID.longValue(), entry.getDN().toString(),
                                id.longValue(), childEntry.getDN().toString());
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

        EntryID entryID = null;
        try
        {
          entryID = new EntryID(key);
        }
        catch (Exception e)
        {
          assert debugException(CLASS_NAME, "iterateID2Subtree", e);
          errorCount++;
          System.err.printf("File id2subtree has malformed ID %s%n",
                            StaticUtils.bytesToHex(key.getData()));
          continue;
        }

        EntryIDSet entryIDList = null;
        try
        {
          JebFormat.entryIDListFromDatabase(data.getData());
          entryIDList = new EntryIDSet(key.getData(), data.getData());
        }
        catch (Exception e)
        {
          assert debugException(CLASS_NAME, "iterateID2Subtree", e);
          errorCount++;
          System.err.printf("File id2subtree has malformed ID list " +
                            "for ID %d:%n%s%n",
                            entryID,
                            StaticUtils.bytesToHex(data.getData()));
          continue;
        }

        updateIndexStats(entryIDList);

        if (entryIDList.isDefined())
        {
          Entry entry = null;
          try
          {
            entry = id2entry.get(null, entryID);
          }
          catch (Exception e)
          {
            assert debugException(CLASS_NAME, "iterateID2Subtree", e);
            errorCount++;
            System.err.println(e.getMessage());
            continue;
          }

          if (entry == null)
          {
            errorCount++;
            System.err.printf("File id2subtree has unknown ID %d%n",
                              entryID.longValue());
            continue;
          }

          for (EntryID id : entryIDList)
          {
            Entry subordEntry = null;
            try
            {
              subordEntry = id2entry.get(null, id);
            }
            catch (Exception e)
            {
              assert debugException(CLASS_NAME, "iterateID2Subtree", e);
              errorCount++;
              System.err.println(e.getMessage());
              continue;
            }

            if (subordEntry == null)
            {
              errorCount++;
              System.err.printf("File id2subtree has ID %d referencing " +
                                "unknown ID %d%n",
                                entryID.longValue(), id.longValue());
              continue;
            }

            if (!subordEntry.getDN().isDescendantOf(entry.getDN()))
            {
              errorCount++;
              System.err.printf("File id2subtree has ID %d with DN <%s> " +
                                "referencing ID %d with non-subordinate " +
                                "DN <%s>%n",
                                entryID.longValue(), entry.getDN().toString(),
                                id.longValue(), subordEntry.getDN().toString());
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
      counter = new Long(1);
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
   * Iterate through the entries in an attribute index to perform a check for
   * index cleanliness.
   * @param attrType The attribute type of the index to be checked.
   * @param index The index database to be checked.
   * @throws JebException If an error occurs in the JE backend.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  private void iterateAttrIndex(AttributeType attrType, Index index)
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

      OperationStatus status;
      for (status = cursor.getFirst(key, data, LockMode.DEFAULT);
           status == OperationStatus.SUCCESS;
           status = cursor.getNext(key, data, LockMode.DEFAULT))
      {
        keyCount++;

        EntryIDSet entryIDList = null;
        try
        {
          JebFormat.entryIDListFromDatabase(data.getData());
          entryIDList = new EntryIDSet(key.getData(), data.getData());
        }
        catch (Exception e)
        {
          assert debugException(CLASS_NAME, "iterateAttrIndex", e);
          errorCount++;
          System.err.printf("Malformed ID list: %s%n%s",
                            StaticUtils.bytesToHex(data.getData()),
                            keyDump(index, key.getData()));
          continue;
        }

        updateIndexStats(entryIDList);

        if (entryIDList.isDefined())
        {
          byte[] value = key.getData();
          byte[] bytes;
          SearchFilter sf;

          switch (value[0])
          {
            case '*':
              bytes = new byte[value.length-1];
              System.arraycopy(value, 1, bytes, 0, value.length-1);

              ArrayList<ByteString> subAnyElements =
                   new ArrayList<ByteString>(1);
              subAnyElements.add(new ASN1OctetString(bytes));

              sf = SearchFilter.createSubstringFilter(attrType,null,
                                                      subAnyElements,null);
              break;

            case '=':
              bytes = new byte[value.length-1];
              System.arraycopy(value, 1, bytes, 0, value.length-1);

              AttributeValue assertionValue =
                   new AttributeValue(attrType, new ASN1OctetString(bytes));

              sf = SearchFilter.createEqualityFilter(attrType,assertionValue);
              break;

            case '+':
              sf = SearchFilter.createPresenceFilter(attrType);
              break;

            default:
              errorCount++;
              System.err.printf("Malformed value%n%s",
                                keyDump(index, value));
              continue;
          }

          EntryID prevID = null;
          for (EntryID id : entryIDList)
          {
            if (prevID != null && id.equals(prevID))
            {
              System.err.printf("Duplicate reference to ID %d%n%s",
                                id.longValue(), keyDump(index, key.getData()));
            }
            prevID = id;

            Entry entry = null;
            try
            {
              entry = id2entry.get(null, id);
            }
            catch (Exception e)
            {
              assert debugException(CLASS_NAME, "iterateAttrIndex", e);
              errorCount++;
              System.err.println(e.getMessage());
              continue;
            }

            if (entry == null)
            {
              errorCount++;
              System.err.printf("Reference to unknown ID %d%n%s",
                                id.longValue(), keyDump(index, key.getData()));
              continue;
            }

            try
            {
              if (!sf.matchesEntry(entry))
              {
                errorCount++;
                System.err.printf("Reference to entry " +
                                  "<%s> which does not match the value%n%s",
                                  entry.getDN(), keyDump(index, value));
              }
            }
            catch (DirectoryException e)
            {
              assert debugException(CLASS_NAME, "iterateAttrIndex", e);
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
    verifyAttrIndex(entryID, entry);
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
        System.err.printf("File dn2id is missing key %s.%n",
                          dn.toNormalizedString());
        errorCount++;
      }
      else if (!id.equals(entryID))
      {
        System.err.printf("File dn2id has ID %d instead of %d for key %s.%n",
                          id.longValue(),
                          entryID.longValue(),
                          dn.toNormalizedString());
        errorCount++;
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "verifyDN2ID", e);
      System.err.printf("File dn2id has error reading key %s: %s.%n",
                        dn.toNormalizedString(),
                        e.getMessage());
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
          System.err.printf("File dn2id is missing key %s.%n",
                            parentDN.toNormalizedString());
          errorCount++;
        }
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "verifyDN2ID", e);
        System.err.printf("File dn2id has error reading key %s: %s.%n",
                          parentDN.toNormalizedString(),
                          e.getMessage());
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
          System.err.printf("File dn2id is missing key %s.%n",
                            parentDN.toNormalizedString());
          errorCount++;
        }
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "verifyID2Children", e);
        System.err.printf("File dn2id has error reading key %s: %s.",
                          parentDN.toNormalizedString(),
                          e.getMessage());
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
            System.err.printf("File id2children is missing ID %d " +
                              "for key %d.%n",
                              entryID.longValue(), parentID.longValue());
            errorCount++;
          }
          else if (cr == ConditionResult.UNDEFINED)
          {
            incrEntryLimitStats(id2c, parentID.getDatabaseEntry().getData());
          }
        }
        catch (DatabaseException e)
        {
          assert debugException(CLASS_NAME, "verifyID2Children", e);
          System.err.printf("File id2children has error reading key %d: %s.",
                            parentID.longValue(), e.getMessage());
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
          System.err.printf("File dn2id is missing key %s.%n",
                            dn.toNormalizedString());
          errorCount++;
        }
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "verifyID2Children", e);
        System.err.printf("File dn2id has error reading key %s: %s.%n",
                          dn.toNormalizedString(),
                          e.getMessage());
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
            System.err.printf("File id2subtree is missing ID %d " +
                              "for key %d.%n",
                              entryID.longValue(), id.longValue());
            errorCount++;
          }
          else if (cr == ConditionResult.UNDEFINED)
          {
            incrEntryLimitStats(id2s, id.getDatabaseEntry().getData());
          }
        }
        catch (DatabaseException e)
        {
          assert debugException(CLASS_NAME, "verifyID2Subtree", e);
          System.err.printf("File id2subtree has error reading key %d: %s.%n",
                            id.longValue(), e.getMessage());
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
  public String keyDump(Index index, byte[] keyBytes)
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
   * Check that an attribute index is complete for a given entry.
   *
   * @param entryID The entry ID.
   * @param entry The entry to be checked.
   */
  private void verifyAttrIndex(EntryID entryID, Entry entry)
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
        assert debugException(CLASS_NAME, "verifyAttrIndex", e);
        System.err.printf("Error normalizing values of attribute %s in " +
                          "entry <%s>: %s.%n",
                          attrIndex.getAttributeType().toString(),
                          entry.getDN().toString(),
                          e.getErrorMessage());
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
  public void verifyAttribute(AttributeIndex attrIndex, EntryID entryID,
                              List<Attribute> attrList)
       throws DirectoryException
  {
    Transaction txn = null;
    Index equalityIndex = attrIndex.equalityIndex;
    Index presenceIndex = attrIndex.presenceIndex;
    Index substringIndex = attrIndex.substringIndex;
    Index orderingIndex = attrIndex.orderingIndex;
    IndexConfig indexConfig = attrIndex.indexConfig;
    DatabaseEntry presenceKey = AttributeIndex.presenceKey;

    // Presence index.
    if (!attrList.isEmpty() && indexConfig.isPresenceIndex())
    {
      try
      {
        ConditionResult cr;
        cr = presenceIndex.containsID(txn, presenceKey, entryID);
        if (cr == ConditionResult.FALSE)
        {
          System.err.printf("Missing ID %d%n%s",
                            entryID.longValue(),
                            keyDump(presenceIndex, presenceKey.getData()));
          errorCount++;
        }
        else if (cr == ConditionResult.UNDEFINED)
        {
          incrEntryLimitStats(presenceIndex, presenceKey.getData());
        }
      }
      catch (DatabaseException e)
      {
        assert debugException(CLASS_NAME, "verifyAttribute", e);
        System.err.printf("Error reading database: %s%n%s",
                          e.getMessage(),
                          keyDump(presenceIndex, presenceKey.getData()));
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
          if (indexConfig.isEqualityIndex())
          {
            byte[] keyBytes = attrIndex.makeEqualityKey(normalizedBytes);
            DatabaseEntry key = new DatabaseEntry(keyBytes);
            try
            {
              ConditionResult cr;
              cr = equalityIndex.containsID(txn, key, entryID);
              if (cr == ConditionResult.FALSE)
              {
                System.err.printf("Missing ID %d%n%s",
                                  entryID.longValue(),
                                  keyDump(equalityIndex, keyBytes));
                errorCount++;
              }
              else if (cr == ConditionResult.UNDEFINED)
              {
                incrEntryLimitStats(equalityIndex, keyBytes);
              }
            }
            catch (DatabaseException e)
            {
              assert debugException(CLASS_NAME, "verifyAttribute", e);
              System.err.printf("Error reading database: %s%n%s",
                                e.getMessage(),
                                keyDump(equalityIndex, keyBytes));
              errorCount++;
            }
          }

          // Substring index.
          if (indexConfig.isSubstringIndex())
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
                  System.err.printf("Missing ID %d%n%s",
                                    entryID.longValue(),
                                    keyDump(substringIndex, key.getData()));
                  errorCount++;
                }
                else if (cr == ConditionResult.UNDEFINED)
                {
                  incrEntryLimitStats(substringIndex, key.getData());
                }
              }
              catch (DatabaseException e)
              {
                assert debugException(CLASS_NAME, "verifyAttribute", e);
                System.err.printf("Error reading database: %s%n%s",
                                  e.getMessage(),
                                  keyDump(substringIndex, key.getData()));
                errorCount++;
              }
            }
          }

          // Ordering index.
          if (indexConfig.isOrderingIndex())
          {
            // Use the ordering matching rule to normalize the value.
            OrderingMatchingRule orderingRule =
                 attr.getAttributeType().getOrderingMatchingRule();

            normalizedBytes =
                 orderingRule.normalizeValue(value.getValue()).value();

            byte[] keyBytes = attrIndex.makeEqualityKey(normalizedBytes);
            DatabaseEntry key = new DatabaseEntry(keyBytes);
            try
            {
              ConditionResult cr;
              cr = orderingIndex.containsID(txn, key, entryID);
              if (cr == ConditionResult.FALSE)
              {
                System.err.printf("Missing ID %d%n%s",
                                  entryID.longValue(),
                                  keyDump(orderingIndex, keyBytes));
                errorCount++;
              }
              else if (cr == ConditionResult.UNDEFINED)
              {
                incrEntryLimitStats(orderingIndex, keyBytes);
              }
            }
            catch (DatabaseException e)
            {
              assert debugException(CLASS_NAME, "verifyAttribute", e);
              System.err.printf("Error reading database: %s%n%s",
                                e.getMessage(),
                                keyDump(orderingIndex, keyBytes));
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
  public DN getParent(DN dn)
  {
    if (dn.equals(verifyConfig.getBaseDN()))
    {
      return null;
    }
    return dn.getParent();
  }

  /**
   * This class reports progress of the verify job at fixed intervals.
   */
  class ProgressTask extends TimerTask
  {
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
      prevEnvStats = env.getStats(new StatsConfig());
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

      int msgID = MSGID_JEB_VERIFY_PROGRESS_REPORT;
      String message = getMessage(msgID, latestCount, errorCount, rate);
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE,
               message, msgID);

      try
      {
        Runtime runtime = Runtime.getRuntime();
        long freeMemory = runtime.freeMemory() / bytesPerMegabyte;

        EnvironmentStats envStats = env.getStats(new StatsConfig());
        long nCacheMiss =
             envStats.getNCacheMiss() - prevEnvStats.getNCacheMiss();

        float cacheMissRate = 0;
        if (deltaCount > 0)
        {
          cacheMissRate = nCacheMiss/(float)deltaCount;
        }

        msgID = MSGID_JEB_VERIFY_CACHE_AND_MEMORY_REPORT;
        message = getMessage(msgID, freeMemory, cacheMissRate);
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE,
                 message, msgID);

        prevEnvStats = envStats;
      }
      catch (DatabaseException e) {}


      previousCount = latestCount;
      previousTime = latestTime;
    }
  };
}
