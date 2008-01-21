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

import static org.opends.server.util.StaticUtils.getFileForPath;
import org.opends.server.types.*;

import java.util.*;
import java.io.*;

import com.sleepycat.je.DatabaseException;

/**
 * This class is used to create an VLV vlvIndex for an import process.
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
public class VLVIndexBuilder implements IndexBuilder
{
  /**
   * The directory in which temporary merge files are held.
   */
  private final File tempDir;

  /**
   * The vlvIndex database.
   */
  private final VLVIndex vlvIndex;

  /**
   * The add write buffer.
   */
  private TreeMap<SortValues,EntryID> addBuffer;

  /**
   * The delete write buffer.
   */
  private TreeMap<SortValues,EntryID> delBuffer;

  /**
   * The write buffer size.
   */
  private final int bufferSize;

  /**
   * Current output file number.
   */
  private int fileNumber = 0;

  /**
   * A unique prefix for temporary files to prevent conflicts.
   */
  private final String fileNamePrefix;

  /**
   * Indicates whether we are replacing existing data or not.
   */
  private final boolean replaceExisting;

  /**
   * A file name filter to identify temporary files we have written.
   */
  private final FilenameFilter filter = new FilenameFilter()
  {
    public boolean accept(File d, String name)
    {
      return name.startsWith(fileNamePrefix);
    }
  };

  /**
   * Construct an vlvIndex builder.
   *
   * @param importContext The import context.
   * @param vlvIndex The vlvIndex database we are writing.
   * @param bufferSize The amount of memory available for buffering.
   */
  public VLVIndexBuilder(ImportContext importContext,
                         VLVIndex vlvIndex, long bufferSize)
  {
    File parentDir = getFileForPath(importContext.getConfig()
        .getImportTempDirectory());
    this.tempDir = new File(parentDir,
        importContext.getConfig().getBackendId());

    this.vlvIndex = vlvIndex;
    this.bufferSize = (int)bufferSize/100;
    long tid = Thread.currentThread().getId();
    this.fileNamePrefix = vlvIndex.getName() + "_" + tid + "_";
    this.replaceExisting =
        importContext.getLDIFImportConfig().appendToExistingData() &&
            importContext.getLDIFImportConfig().replaceExistingEntries();
  }

  /**
   * {@inheritDoc}
   */
  public void startProcessing()
  {
    // Clean up any work files left over from a previous run.
    File[] files = tempDir.listFiles(filter);
    if (files != null)
    {
      for (File f : files)
      {
        f.delete();
      }
    }

    addBuffer = new TreeMap<SortValues,EntryID>();
    delBuffer = new TreeMap<SortValues, EntryID>();
  }

  /**
   * {@inheritDoc}
   */
  public void processEntry(Entry oldEntry, Entry newEntry, EntryID entryID)
      throws DatabaseException, IOException, DirectoryException
  {
    SortValues newValues = new SortValues(entryID, newEntry,
                                          vlvIndex.sortOrder);
    // Update the vlvIndex for this entry.
    if (oldEntry != null)
    {
      if(vlvIndex.shouldInclude(oldEntry))
      {
      // This is an entry being replaced.
      SortValues oldValues = new SortValues(entryID, oldEntry,
                                            vlvIndex.sortOrder);
      removeValues(oldValues, entryID);
      }

    }

    if(vlvIndex.shouldInclude(newEntry))
    {
      insertValues(newValues, entryID);
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
   * Record the insertion of an entry ID.
   * @param sortValues The sort values.
   * @param entryID The entry ID.
   * @throws IOException If an I/O error occurs while writing an intermediate
   * file.
   */
  private void insertValues(SortValues sortValues, EntryID entryID)
      throws IOException
  {
    if (addBuffer.size() + delBuffer.size() >= bufferSize)
    {
      flushBuffer();
    }

    addBuffer.put(sortValues, entryID);
  }

  /**
   * Record the deletion of an entry ID.
   * @param sortValues The sort values to remove.
   * @param entryID The entry ID.
   * @throws IOException If an I/O error occurs while writing an intermediate
   * file.
   */
  private void removeValues(SortValues sortValues, EntryID entryID)
      throws IOException
  {
    if (addBuffer.size() + delBuffer.size() >= bufferSize)
    {
      flushBuffer();
    }

    delBuffer.remove(sortValues);
  }

  /**
   * Called when the buffer is full. It first sorts the buffer using the same
   * key comparator used by the vlvIndex database. Then it merges all the
   * IDs for the same key together and writes each key and its list of IDs
   * to an intermediate binary file.
   * A list of deleted IDs is only present if we are replacing existing entries.
   *
   * @throws IOException If an I/O error occurs while writing an intermediate
   * file.
   */
  private void flushBuffer() throws IOException
  {
    if (addBuffer.size() + delBuffer.size() == 0)
    {
      return;
    }

    // Start a new file.
    fileNumber++;
    String fileName = fileNamePrefix + String.valueOf(fileNumber) + "_add";
    File file = new File(tempDir, fileName);
    BufferedOutputStream bufferedStream =
        new BufferedOutputStream(new FileOutputStream(file));
    DataOutputStream dataStream = new DataOutputStream(bufferedStream);

    try
    {
      for (SortValues values : addBuffer.keySet())
      {
        dataStream.writeLong(values.getEntryID());
        for(AttributeValue value : values.getValues())
        {
          if(value != null)
          {
            byte[] valueBytes = value.getValueBytes();
            dataStream.writeInt(valueBytes.length);
            dataStream.write(valueBytes);
          }
          else
          {
            dataStream.writeInt(0);
          }
        }
      }
    }
    finally
    {
      dataStream.close();
    }

    if (replaceExisting)
    {
      fileName = fileNamePrefix + String.valueOf(fileNumber) + "_del";
      file = new File(tempDir, fileName);
      bufferedStream =
          new BufferedOutputStream(new FileOutputStream(file));
      dataStream = new DataOutputStream(bufferedStream);

      try
      {

        for (SortValues values : delBuffer.keySet())
        {
          dataStream.writeLong(values.getEntryID());
          for(AttributeValue value : values.getValues())
          {
            byte[] valueBytes = value.getValueBytes();
            dataStream.writeInt(valueBytes.length);
            dataStream.write(valueBytes);
          }
        }
      }
      finally
      {
        dataStream.close();
      }
    }

    addBuffer = new TreeMap<SortValues,EntryID>();
    delBuffer = new TreeMap<SortValues, EntryID>();
  }

  /**
   * Get a string that identifies this vlvIndex builder.
   *
   * @return A string that identifies this vlvIndex builder.
   */
  public String toString()
  {
    return vlvIndex.toString() + " builder";
  }
}


