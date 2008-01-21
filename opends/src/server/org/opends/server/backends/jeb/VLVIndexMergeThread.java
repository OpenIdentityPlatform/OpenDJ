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
 *      Portions Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.backends.jeb;
import org.opends.messages.Message;

import org.opends.server.api.DirectoryThread;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.ErrorLogger.logError;
import org.opends.server.admin.std.server.LocalDBBackendCfg;
import org.opends.server.types.*;
import org.opends.server.protocols.asn1.ASN1OctetString;
import static org.opends.server.util.StaticUtils.getFileForPath;
import static org.opends.messages.JebMessages.
    INFO_JEB_INDEX_MERGE_NO_DATA;
import static org.opends.messages.JebMessages.
    INFO_JEB_INDEX_MERGE_START;
import static org.opends.messages.JebMessages.
    INFO_JEB_INDEX_MERGE_COMPLETE;
import java.util.*;
import java.io.*;

import com.sleepycat.je.Transaction;

/**
 * A thread to merge a set of intermediate files from an vlvIndex builder
 * into an vlvIndex database.
 */
class VLVIndexMergeThread extends DirectoryThread
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();


  /**
   * The buffer size to use when reading data from disk.
   */
  private static final int INPUT_STREAM_BUFFER_SIZE = 65536;

  /**
   * The configuration of the JE backend containing the vlvIndex.
   */
  private LocalDBBackendCfg config;

  /**
   * The LDIF import configuration, which indicates whether we are
   * appending to existing data.
   */
  private LDIFImportConfig ldifImportConfig;

  /**
   * The vlvIndex database being written.
   */
  private VLVIndex vlvIndex;

  /**
   * Indicates whether we are replacing existing data or not.
   */
  private boolean replaceExisting = false;

  private List<DataInputStream> addDataStreams;
  private List<DataInputStream> delDataStreams;

  /**
   * A weak reference hash map used to cache last sort values read from files.
   */
  private HashMap<DataInputStream,SortValues> lastAddValues =
      new HashMap<DataInputStream,SortValues>();

  private HashMap<DataInputStream,SortValues> lastDelValues =
      new HashMap<DataInputStream,SortValues>();


  /**
   * A file name filter to identify temporary files we have written.
   */
  private FilenameFilter filter = new FilenameFilter()
  {
    public boolean accept(File d, String name)
    {
      return name.startsWith(vlvIndex.getName());
    }
  };

  /**
   * Create a new vlvIndex merge thread.
   * @param config The configuration of the JE backend containing the vlvIndex.
   * @param ldifImportConfig The LDIF import configuration, which indicates
   * whether we are appending to existing data.
   * @param vlvIndex The vlvIndex database to be written.
   */
  public VLVIndexMergeThread(LocalDBBackendCfg config,
                      LDIFImportConfig ldifImportConfig,
                      VLVIndex vlvIndex)
  {
    super("Index Merge Thread " + vlvIndex.getName());

    this.config = config;
    this.ldifImportConfig = ldifImportConfig;
    this.vlvIndex = vlvIndex;
    replaceExisting =
        ldifImportConfig.appendToExistingData() &&
            ldifImportConfig.replaceExistingEntries();
    addDataStreams = new ArrayList<DataInputStream>();
    delDataStreams = new ArrayList<DataInputStream>();
    lastAddValues = new HashMap<DataInputStream, SortValues>();
    lastDelValues = new HashMap<DataInputStream, SortValues>();
  }

  /**
   * Run this thread.
   */
  public void run()
  {
    try
    {
      merge();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      throw new RuntimeException(e);
    }
  }

  /**
   * The merge phase builds the vlvIndex from intermediate files written
   * during entry processing. Each line of an intermediate file has data for
   * one vlvIndex key and the keys are in order. For each vlvIndex key, the data
   * from each intermediate file containing a line for that key must be merged
   * and written to the vlvIndex.
   * @throws Exception If an error occurs.
   */
  public void merge() throws Exception
  {
    // Open all the files.
    File parentDir = getFileForPath(config.getImportTempDirectory());
    File tempDir = new File(parentDir, config.getBackendId());
    File[] files = tempDir.listFiles(filter);

    if (files == null || files.length == 0)
    {
      Message message = INFO_JEB_INDEX_MERGE_NO_DATA.get(vlvIndex.getName());
      logError(message);
      return;
    }

    if (debugEnabled())
    {
      Message message = INFO_JEB_INDEX_MERGE_START.get(
              files.length, vlvIndex.getName());
      TRACER.debugInfo(message.toString());
    }

    Transaction txn = null;

    try
    {
      for (int i = 0; i < files.length; i++)
      {
        // Open a reader for this file.
        BufferedInputStream bufferedStream =
            new BufferedInputStream(new FileInputStream(files[i]),
                                    INPUT_STREAM_BUFFER_SIZE);
        DataInputStream dis = new DataInputStream(bufferedStream);
        if(files[i].getName().endsWith("_add"))
        {
          addDataStreams.add(dis);
        }
        else if(files[i].getName().endsWith("_del"))
        {
          delDataStreams.add(dis);
        }
      }

      while(true)
      {
        SortValuesSet currentSet = null;
        SortValues maxKey = null;
        // Get a set by using the smallest sort values
        SortValues addValue = readNextAdd(maxKey);

        // Process deletes first for this set
        if(replaceExisting)
        {
          SortValues delValue = readNextDel(maxKey);
          if(delValue != null)
          {
            if(currentSet == null)
            {
              if(addValue == null || delValue.compareTo(addValue) < 0)
              {
                // Set the current set using the del value.
                currentSet = vlvIndex.getSortValuesSet(txn,
                                                       delValue.getEntryID(),
                                                       delValue.getValues());
              }
              else
              {
                // Set the current set using the add value.
                currentSet = vlvIndex.getSortValuesSet(txn,
                                                       addValue.getEntryID(),
                                                       addValue.getValues());
              }
              maxKey = currentSet.getKeySortValues();
            }
          }

          while(delValue != null)
          {
            currentSet.remove(delValue.getEntryID(), delValue.getValues());
            delValue = readNextDel(maxKey);
          }
        }

        if(addValue != null)
        {
          if(currentSet == null)
          {
            currentSet = vlvIndex.getSortValuesSet(txn, addValue.getEntryID(),
                                                   addValue.getValues());
            maxKey = currentSet.getKeySortValues();
          }

          while(addValue != null)
          {
            currentSet.add(addValue.getEntryID(), addValue.getValues());
            if(currentSet.size() > vlvIndex.getSortedSetCapacity())
            {
              // Need to split the set as it has exceeded the entry limit.
              SortValuesSet splitSortValuesSet =
                  currentSet.split(currentSet.size() / 2);
              // Find where the set split and see if the last added values
              // is before or after the split.
              SortValues newKey = currentSet.getKeySortValues();

              if(debugEnabled())
              {
                TRACER.debugInfo("SortValuesSet with key %s has reached" +
                    " the entry size of %d. Spliting into two sets with " +
                    " keys %s and %s.", maxKey, currentSet.size(), newKey,
                                        maxKey);
              }

              if(addValue.compareTo(newKey) < 0)
              {
                // The last added values is before the split so we have to
                // keep adding to it.
                vlvIndex.putSortValuesSet(txn, splitSortValuesSet);
                maxKey = newKey;
              }
              else
              {
                // The last added values is after the split so we can add to
                // the newly split set.
                vlvIndex.putSortValuesSet(txn, currentSet);
                currentSet = splitSortValuesSet;
              }
            }
            addValue = readNextAdd(maxKey);
          }
        }

        // We should have made all the modifications to this set. Store it back
        // to database.
        vlvIndex.putSortValuesSet(txn, currentSet);

        if(maxKey == null)
        {
          // If we reached here, we should have processed all the sets and
          // there should be nothing left to add or delete.
          break;
        }
      }

      if(!ldifImportConfig.appendToExistingData())
      {
        vlvIndex.setTrusted(txn, true);
      }
    }
    finally
    {
      for(DataInputStream stream : addDataStreams)
      {
        stream.close();
      }

      for(DataInputStream stream : delDataStreams)
      {
        stream.close();
      }

      // Delete all the files.
      if (files != null)
      {
        for (File f : files)
        {
          f.delete();
        }
      }
    }

    if (debugEnabled())
    {
      Message message = INFO_JEB_INDEX_MERGE_COMPLETE.get(vlvIndex.getName());
      TRACER.debugInfo(message.toString());
    }
  }

  /**
   * Reads the next sort values from the files that is smaller then the max.
   * @throws IOException If an I/O error occurs while reading the input file.
   */
  private SortValues readNextAdd(SortValues maxValues)
      throws IOException
  {
    for(DataInputStream dataInputStream : addDataStreams)
    {
      if(lastAddValues.get(dataInputStream) == null)
      {
        try
        {
          SortKey[] sortKeys = vlvIndex.sortOrder.getSortKeys();
          EntryID id = new EntryID(dataInputStream.readLong());
          AttributeValue[] attrValues =
              new AttributeValue[sortKeys.length];
          for(int i = 0; i < sortKeys.length; i++)
          {
            SortKey sortKey = sortKeys[i];
            int length = dataInputStream.readInt();
            if(length > 0)
            {
              byte[] valueBytes = new byte[length];
              if(length == dataInputStream.read(valueBytes, 0, length))
              {
                attrValues[i] =
                    new AttributeValue(sortKey.getAttributeType(),
                                       new ASN1OctetString(valueBytes));
              }
            }

          }
          lastAddValues.put(dataInputStream,
                            new SortValues(id, attrValues, vlvIndex.sortOrder));
        }
        catch (EOFException e)
        {
          continue;
        }
      }
    }

    Map.Entry<DataInputStream, SortValues> smallestEntry = null;
    for(Map.Entry<DataInputStream, SortValues> entry :
        lastAddValues.entrySet())
    {
      if(smallestEntry == null ||
          entry.getValue().compareTo(smallestEntry.getValue()) < 0)
      {
        smallestEntry = entry;
      }
    }

    if(smallestEntry != null)
    {
      SortValues smallestValues = smallestEntry.getValue();
      if(maxValues == null || smallestValues.compareTo(maxValues) <= 0)
      {
        lastAddValues.remove(smallestEntry.getKey());
        return smallestValues;
      }
    }

    return null;
  }

  /**
   * Reads the next sort values from the files that is smaller then the max.
   * @throws IOException If an I/O error occurs while reading the input file.
   */
  private SortValues readNextDel(SortValues maxValues)
      throws IOException
  {
    for(DataInputStream dataInputStream : delDataStreams)
    {
      if(lastDelValues.get(dataInputStream) == null)
      {
        try
        {
          EntryID id = new EntryID(dataInputStream.readLong());
          AttributeValue[] attrValues =
              new AttributeValue[vlvIndex.sortOrder.getSortKeys().length];
          int i = 0;
          for(SortKey sortKey : vlvIndex.sortOrder.getSortKeys())
          {
            int length = dataInputStream.readInt();
            if(length > 0)
            {
              byte[] valueBytes = new byte[length];
              if(length == dataInputStream.read(valueBytes, 0, length))
              {
                attrValues[i] =
                    new AttributeValue(sortKey.getAttributeType(),
                                       new ASN1OctetString(valueBytes));
              }
            }
          }
          lastDelValues.put(dataInputStream,
                            new SortValues(id, attrValues,
                                           vlvIndex.sortOrder));
        }
        catch (EOFException e)
        {
          continue;
        }
      }
    }

    Map.Entry<DataInputStream, SortValues> smallestEntry = null;
    for(Map.Entry<DataInputStream, SortValues> entry :
        lastDelValues.entrySet())
    {
      if(smallestEntry == null ||
          entry.getValue().compareTo(smallestEntry.getValue()) < 0)
      {
        smallestEntry = entry;
      }
    }

    if(smallestEntry != null)
    {
      SortValues smallestValues = smallestEntry.getValue();
      if(maxValues == null || smallestValues.compareTo(maxValues) <= 0)
      {
        lastDelValues.remove(smallestEntry.getKey());
        return smallestValues;
      }
    }

    return null;
  }
}
