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

import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.Entry;
import static org.opends.server.util.StaticUtils.getFileForPath;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Transaction;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.io.ByteArrayOutputStream;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FilenameFilter;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * This class is used to create an attribute index for an import process.
 * It is used as follows.
 * <pre>
 * startProcessing();
 * processEntry(entry);
 * processEntry(entry);
 * ...
 * stopProcessing();
 * merge();
 * </pre>
 */
public class AttributeIndexBuilder implements IndexBuilder
{
  /**
   * The import context.
   */
  private ImportContext importContext;

  /**
   * The index database.
   */
  private Index index;

  /**
   * The indexer to generate the index keys.
   */
  private Indexer indexer;

  /**
   * The write buffer.
   */
  ArrayList<IndexMod> buffer;

  /**
   * The write buffer size.
   */
  private int bufferSize;

  /**
   * Current output file number.
   */
  private int fileNumber = 0;

  /**
   * The index entry limit.
   */
  private int entryLimit;

  /**
   * A unique prefix for temporary files to prevent conflicts.
   */
  private String fileNamePrefix;

  /**
   * Indicates whether we are replacing existing data or not.
   */
  private boolean replaceExisting = false;


  private ByteArrayOutputStream addBytesStream = new ByteArrayOutputStream();
  private ByteArrayOutputStream delBytesStream = new ByteArrayOutputStream();

  private DataOutputStream addBytesDataStream;
  private DataOutputStream delBytesDataStream;

  /**
   * A file name filter to identify temporary files we have written.
   */
  private FilenameFilter filter = new FilenameFilter()
  {
    public boolean accept(File d, String name)
    {
      return name.startsWith(fileNamePrefix);
    }
  };

  /**
   * Construct an index builder.
   *
   * @param importContext The import context.
   * @param index The index database we are writing.
   * @param entryLimit The index entry limit.
   * @param bufferSize The amount of memory available for buffering.
   */
  public AttributeIndexBuilder(ImportContext importContext,
                      Index index, int entryLimit, long bufferSize)
  {
    this.importContext = importContext;
    this.index = index;
    this.indexer = index.indexer;
    this.entryLimit = entryLimit;
    this.bufferSize = (int)bufferSize/100;
    long tid = Thread.currentThread().getId();
    fileNamePrefix = index.getName() + "_" + tid + "_";
    replaceExisting =
         importContext.getLDIFImportConfig().appendToExistingData() &&
         importContext.getLDIFImportConfig().replaceExistingEntries();
    addBytesDataStream = new DataOutputStream(addBytesStream);
    delBytesDataStream = new DataOutputStream(delBytesStream);
  }

  /**
   * {@inheritDoc}
   */
  public void startProcessing()
  {
    // Clean up any work files left over from a previous run.
    File tempDir = getFileForPath(
        importContext.getConfig().getBackendImportTempDirectory());
    File[] files = tempDir.listFiles(filter);
    if (files != null)
    {
      for (File f : files)
      {
        f.delete();
      }
    }

    buffer = new ArrayList<IndexMod>(bufferSize);
  }

  /**
   * {@inheritDoc}
   */
  public void processEntry(Entry oldEntry, Entry newEntry, EntryID entryID)
       throws DatabaseException, IOException
  {
    Transaction txn = null;

    // Update the index for this entry.
    if (oldEntry != null)
    {
      // This is an entry being replaced.
      Set<ASN1OctetString> addKeys = new HashSet<ASN1OctetString>();
      Set<ASN1OctetString> delKeys = new HashSet<ASN1OctetString>();

      indexer.replaceEntry(txn, oldEntry, newEntry, addKeys, delKeys);

      for (ASN1OctetString k : delKeys)
      {
        removeID(k.value(), entryID);
      }

      for (ASN1OctetString k : addKeys)
      {
        insertID(k.value(), entryID);
      }
    }
    else
    {
      // This is a new entry.
      Set<ASN1OctetString> addKeys = new HashSet<ASN1OctetString>();
      indexer.indexEntry(txn, newEntry, addKeys);
      for (ASN1OctetString k : addKeys)
      {
        insertID(k.value(), entryID);
      }
    }

  }



  /**
   * {@inheritDoc}
   */
  public void stopProcessing() throws IOException
  {
    flushBuffer();
  }



  /**
   * Get a statistic of the number of keys that reached the entry limit.
   *
   * @return The number of keys that reached the entry limit.
   */
  public int getEntryLimitExceededCount()
  {
    return index.getEntryLimitExceededCount();
  }

  /**
   * Record the insertion of an entry ID.
   * @param key The index key.
   * @param entryID The entry ID.
   * @throws IOException If an I/O error occurs while writing an intermediate
   * file.
   */
  private void insertID(byte[] key, EntryID entryID)
       throws IOException
  {
    if (buffer.size() >= bufferSize)
    {
      flushBuffer();
    }

    IndexMod kav = new IndexMod(key, entryID, false);
    buffer.add(kav);
  }

  /**
   * Record the deletion of an entry ID.
   * @param key The index key.
   * @param entryID The entry ID.
   * @throws IOException If an I/O error occurs while writing an intermediate
   * file.
   */
  private void removeID(byte[] key, EntryID entryID)
       throws IOException
  {
    if (buffer.size() >= bufferSize)
    {
      flushBuffer();
    }

    IndexMod kav = new IndexMod(key, entryID, true);
    buffer.add(kav);
  }

  /**
   * Called when the buffer is full. It first sorts the buffer using the same
   * key comparator used by the index database. Then it merges all the
   * IDs for the same key together and writes each key and its list of IDs
   * to an intermediate binary file.
   * A list of deleted IDs is only present if we are replacing existing entries.
   *
   * @throws IOException If an I/O error occurs while writing an intermediate
   * file.
   */
  private void flushBuffer() throws IOException
  {
    if (buffer.size() == 0)
    {
      return;
    }

    // Keys must be sorted before we can merge duplicates.
    IndexModComparator comparator;
    if (replaceExisting)
    {
      // The entry IDs may be out of order.
      // We must sort by key and ID.
      comparator = new IndexModComparator(indexer.getComparator(), true);
    }
    else
    {
      // The entry IDs are all new and are therefore already ordered.
      // We just need to sort by key.
      comparator = new IndexModComparator(indexer.getComparator(), false);
    }
    Collections.sort(buffer, comparator);

    // Start a new file.
    fileNumber++;
    String fileName = fileNamePrefix + String.valueOf(fileNumber);
    File file = new File(getFileForPath(
        importContext.getConfig().getBackendImportTempDirectory()),
                         fileName);
    BufferedOutputStream bufferedStream =
         new BufferedOutputStream(new FileOutputStream(file));
    DataOutputStream dataStream = new DataOutputStream(bufferedStream);

    // Reset the byte array output streams but preserve the underlying arrays.
    addBytesStream.reset();
    delBytesStream.reset();

    try
    {
      byte[] currentKey = null;
      for (IndexMod key : buffer)
      {
        byte[] keyString = key.key;
        if (!Arrays.equals(keyString,currentKey))
        {
          if (currentKey != null)
          {
            dataStream.writeInt(currentKey.length);
            dataStream.write(currentKey);
            dataStream.writeInt(addBytesStream.size());
            addBytesStream.writeTo(dataStream);
            if (replaceExisting)
            {
              dataStream.writeInt(delBytesStream.size());
              delBytesStream.writeTo(dataStream);
            }
          }

          currentKey = keyString;
          addBytesStream.reset();
          delBytesStream.reset();
        }

        if (key.isDelete)
        {
          delBytesDataStream.writeLong(key.value.longValue());
        }
        else
        {
          addBytesDataStream.writeLong(key.value.longValue());
        }

      }

      if (currentKey != null)
      {
        dataStream.writeInt(currentKey.length);
        dataStream.write(currentKey);
        dataStream.writeInt(addBytesStream.size());
        addBytesStream.writeTo(dataStream);
        if (replaceExisting)
        {
          dataStream.writeInt(delBytesStream.size());
          delBytesStream.writeTo(dataStream);
        }
      }

      buffer = new ArrayList<IndexMod>(bufferSize);
    }
    finally
    {
      dataStream.close();
    }
  }

  /**
   * Get a string that identifies this index builder.
   *
   * @return A string that identifies this index builder.
   */
  public String toString()
  {
    return indexer.toString();
  }
}

