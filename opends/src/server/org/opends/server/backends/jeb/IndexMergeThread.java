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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.backends.jeb;

import org.opends.server.api.DirectoryThread;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.LDIFImportConfig;

import com.sleepycat.je.*;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.TreeMap;
import java.util.WeakHashMap;

import org.opends.server.types.DebugLogLevel;
import static org.opends.server.messages.MessageHandler.getMessage;
import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.server.messages.JebMessages.*;
import org.opends.server.admin.std.server.JEBackendCfg;
import static org.opends.server.util.StaticUtils.getFileForPath;

/**
 * A thread to merge a set of intermediate files from an index builder
 * into an index database.
 */
public class IndexMergeThread extends DirectoryThread
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
   * The configuration of the JE backend containing the index.
   */
  JEBackendCfg config;

  /**
   * The LDIF import configuration, which indicates whether we are
   * appending to existing data.
   */
  LDIFImportConfig ldifImportConfig;


  /**
   * The indexer to generate and compare index keys.
   */
  Indexer indexer;

  /**
   * The index database being written.
   */
  Index index;


  /**
   * The index entry limit.
   */
  int entryLimit;

  /**
   * The name of the index for use in file names and log messages.
   */
  String indexName;

  /**
   * Indicates whether we are replacing existing data or not.
   */
  private boolean replaceExisting = false;

  /**
   * A weak reference hash map used to cache byte arrays for holding DB keys.
   */
  private WeakHashMap<Integer,LinkedList<byte[]>> arrayMap =
       new WeakHashMap<Integer,LinkedList<byte[]>>();


  /**
   * A file name filter to identify temporary files we have written.
   */
  private FilenameFilter filter = new FilenameFilter()
  {
    public boolean accept(File d, String name)
    {
      return name.startsWith(index.getName());
    }
  };

  /**
   * Create a new index merge thread.
   * @param config The configuration of the JE backend containing the index.
   * @param ldifImportConfig The LDIF import configuration, which indicates
   * whether we are appending to existing data.
   * @param index The index database to be written.
   * @param entryLimit The configured index entry limit.
   */
  IndexMergeThread(JEBackendCfg config,
                   LDIFImportConfig ldifImportConfig,
                   Index index, int entryLimit)
  {
    super("Index Merge Thread " + index.getName());

    this.config = config;
    this.ldifImportConfig = ldifImportConfig;
    this.indexer = index.indexer;
    this.index = index;
    this.entryLimit = entryLimit;
    replaceExisting =
         ldifImportConfig.appendToExistingData() &&
         ldifImportConfig.replaceExistingEntries();
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
    }
  }

  /**
   * The merge phase builds the index from intermediate files written
   * during entry processing. Each line of an intermediate file has data for
   * one index key and the keys are in order. For each index key, the data from
   * each intermediate file containing a line for that key must be merged and
   * written to the index.
   * @throws Exception If an error occurs.
   */
  public void merge() throws Exception
  {
    // An ordered map of the current input keys from each file.
    OctetStringKeyComparator comparator =
         new OctetStringKeyComparator(indexer.getComparator());
    TreeMap<ASN1OctetString, MergeValue> inputs =
         new TreeMap<ASN1OctetString, MergeValue>(comparator);

    // Open all the files.
    File tempDir = getFileForPath(config.getBackendImportTempDirectory());
    File[] files = tempDir.listFiles(filter);

    if (files == null || files.length == 0)
    {
      int msgID = MSGID_JEB_INDEX_MERGE_NO_DATA;
      String message = getMessage(msgID, index.getName());
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE,
               message, msgID);
      return;
    }

    int msgID = MSGID_JEB_INDEX_MERGE_START;
    String message = getMessage(msgID, files.length, index.getName());
    logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE,
             message, msgID);

    MergeReader[] readers = new MergeReader[files.length];

    Transaction txn = null;
    DatabaseEntry dbKey = new DatabaseEntry();
    DatabaseEntry dbData = new DatabaseEntry();
    byte[] mergedBytes = new byte[0];
    Longs merged = new Longs();


    try
    {

      for (int i = 0; i < files.length; i++)
      {
        // Open a reader for this file.
        BufferedInputStream bufferedStream =
             new BufferedInputStream(new FileInputStream(files[i]),
                                     INPUT_STREAM_BUFFER_SIZE);
        DataInputStream dis = new DataInputStream(bufferedStream);
        readers[i] = new MergeReader(dis);

        // Read a value from each file.
        readNext(inputs, readers, i);
      }

      // Process the lowest input value until done.
      try
      {
        while (true)
        {
          ASN1OctetString lowest = inputs.firstKey();
          MergeValue mv = inputs.remove(lowest);

          byte[] keyBytes = mv.getKey();
          dbKey.setData(keyBytes);
          List<Longs> addValues = mv.getAddValues();
          List<Longs> delValues = mv.getDelValues();

          writeMergedValue:
          {
            merged.clear();
            if (ldifImportConfig.appendToExistingData())
            {
              if (index.read(txn, dbKey, dbData, LockMode.RMW) ==
                   OperationStatus.SUCCESS)
              {
                if (dbData.getSize() == 0)
                {
                  // Entry limit already exceeded.
                  break writeMergedValue;
                }
                merged.decode(dbData.getData());
              }
            }

            for (Longs l : addValues)
            {
              merged.addAll(l);
            }

            if (replaceExisting)
            {
              for (Longs l : delValues)
              {
                merged.deleteAll(l);
              }
            }

            if (merged.size() > entryLimit)
            {
              index.writeKey(txn, dbKey, new EntryIDSet());
            }
            else
            {
              mergedBytes = merged.encode(mergedBytes);

              dbData.setData(mergedBytes);
              dbData.setSize(merged.encodedSize());
              index.put(txn, dbKey, dbData);
            }

            LinkedList<byte[]> arrayList = arrayMap.get(keyBytes.length);
            if (arrayList == null)
            {
              arrayList = new LinkedList<byte[]>();
              arrayMap.put(keyBytes.length, arrayList);
            }

            arrayList.add(keyBytes);
          }

          for (int r : mv.getReaders())
          {
            readNext(inputs, readers, r);
          }
        }
      }
      catch (NoSuchElementException e)
      {
      }

      if(replaceExisting)
      {
        index.setTrusted(txn, true);
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      throw e;
    }
    finally
    {
      // Close the readers.
      if (readers != null)
      {
        for (MergeReader r : readers)
        {
          if (r != null)
          {
            r.dataInputStream.close();
          }
        }
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

    msgID = MSGID_JEB_INDEX_MERGE_COMPLETE;
    message = getMessage(msgID, index.getName());
    logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE,
             message, msgID);
  }

  /**
   * Reads the next line from one of the merge input files.
   * @param inputs The ordered map of current input keys.
   * @param readers The array of input readers.
   * @param reader The index of the input reader we wish to read from.
   * @throws IOException If an I/O error occurs while reading the input file.
   */
  private void readNext(TreeMap<ASN1OctetString, MergeValue> inputs,
                        MergeReader[] readers, int reader)
       throws IOException
  {
    MergeReader mergeReader = readers[reader];
    DataInputStream dataInputStream = mergeReader.dataInputStream;
    int len;
    try
    {
      len = dataInputStream.readInt();
    }
    catch (EOFException e)
    {
      // End of file.
      return;
    }

    byte[] keyBytes;
    LinkedList<byte[]> arrayList = arrayMap.get(len);
    if (arrayList == null)
    {
      keyBytes = new byte[len];
      arrayList = new LinkedList<byte[]>();
      arrayMap.put(len, arrayList);
    }
    else if (arrayList.isEmpty())
    {
      keyBytes = new byte[len];
    }
    else
    {
      keyBytes = arrayList.removeFirst();
    }

    dataInputStream.readFully(keyBytes);

    Longs addData = mergeReader.addData;
    addData.decode(dataInputStream);

    Longs delData = mergeReader.delData;
    if (replaceExisting)
    {
      delData.decode(dataInputStream);
    }

    // If this key is not yet in the ordered map then insert it,
    // otherwise merge the data into the existing data for the key.

    ASN1OctetString mk = new ASN1OctetString(keyBytes);
    MergeValue mv = inputs.get(mk);
    if (mv == null)
    {
      mv = new MergeValue(readers.length, entryLimit);
      mv.setKey(keyBytes);
      inputs.put(mk, mv);
    }
    else
    {
      arrayList.add(keyBytes);
    }

    mv.mergeData(reader, addData, delData);
  }

}
