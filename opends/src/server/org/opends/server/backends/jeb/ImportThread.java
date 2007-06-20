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

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.api.DirectoryThread;
import org.opends.server.types.Entry;

import com.sleepycat.je.Transaction;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;

/**
 * A thread to process import entries from a queue.  Multiple instances of
 * this class process entries from a single shared queue.
 */
public class ImportThread extends DirectoryThread
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();


  /**
   * The import context of this thread.
   */
  private ImportContext importContext;

  /**
   * The destination entry entryContainer for entries read from the queue.
   */
  private EntryContainer entryContainer;

  /**
   * The entry database of the destination entry entryContainer.
   */
  private ID2Entry id2entry;

  /**
   * The referral database of the destination entry entryContainer.
   */
  private DN2URI dn2uri;

  /**
   * A set of index builder objects to construct the index databases.
   */
  private ArrayList<IndexBuilder> builders = new ArrayList<IndexBuilder>();

  /**
   * This queue is the source of entries to be processed.
   */
  private BlockingQueue<Entry> queue;

  /**
   * The number of entries imported by this thread.
   */
  private int importedCount = 0;

  /**
   * Of the total number of entries imported by this thread, this is the
   * number of new entries inserted (as opposed to existing entries that have
   * been replaced).
   */
  private int entryInsertCount = 0;

  /**
   * A flag that is set when the thread has been told to stop processing.
   */
  private boolean stopRequested = false;

  /**
   * Create a new import thread.
   * @param importContext The import context of the thread.
   * @param threadNumber A number to identify this thread instance among
   * other instances of the same class.
   */
  public ImportThread(ImportContext importContext, int threadNumber)
  {
    super("Import Worker Thread " + threadNumber);

    this.importContext = importContext;
    entryContainer = importContext.getEntryContainer();
    queue = importContext.getQueue();
    id2entry = entryContainer.getID2Entry();
    dn2uri = entryContainer.getDN2URI();
  }

  /**
   * Get the number of entries imported by this thread.
   * @return The number of entries imported by this thread.
   */
  public int getImportedCount()
  {
    return importedCount;
  }

  /**
   * Tells the thread to stop processing.
   */
  public void stopProcessing()
  {
    stopRequested = true;
  }

  /**
   * Run the thread.  Creates index builder objects for each index database,
   * then polls the queue until it is told to stop processing.  Each entry
   * taken from the queue is written to the entry database and processed by
   * all the index builders.
   */
  public void run()
  {
    Entry entry;

    // Figure out how many indexes there will be.
    int nIndexes = 0;

    nIndexes += 2; // For id2children and id2subtree.

    for (AttributeIndex attrIndex : entryContainer.getAttributeIndexes())
    {
      if (attrIndex.equalityIndex != null)
      {
        nIndexes++;
      }
      if (attrIndex.presenceIndex != null)
      {
        nIndexes++;
      }
      if (attrIndex.substringIndex != null)
      {
        nIndexes++;
      }
      if (attrIndex.orderingIndex != null)
      {
        nIndexes++;
      }
      if (attrIndex.approximateIndex != null)
      {
        nIndexes++;
      }
    }

    // Divide the total buffer size by the number of threads
    // and give that much to each index.
    long indexBufferSize = importContext.getBufferSize() / nIndexes;

    // Create an index builder for each attribute index database.
    for (AttributeIndex attrIndex : entryContainer.getAttributeIndexes())
    {
      int indexEntryLimit =
          importContext.getConfig().getBackendIndexEntryLimit();
      if(attrIndex.getConfiguration().getIndexEntryLimit() != null)
      {
        indexEntryLimit = attrIndex.getConfiguration().getIndexEntryLimit();
      }

      if (attrIndex.equalityIndex != null)
      {
        IndexBuilder indexBuilder =
             new IndexBuilder(importContext,
                              attrIndex.equalityIndex,
                              indexEntryLimit,
                              indexBufferSize);
        builders.add(indexBuilder);
      }
      if (attrIndex.presenceIndex != null)
      {
        IndexBuilder indexBuilder =
             new IndexBuilder(importContext,
                              attrIndex.presenceIndex,
                              indexEntryLimit,
                              indexBufferSize);
        builders.add(indexBuilder);
      }
      if (attrIndex.substringIndex != null)
      {
        IndexBuilder indexBuilder =
             new IndexBuilder(importContext,
                              attrIndex.substringIndex,
                              indexEntryLimit,
                              indexBufferSize);
        builders.add(indexBuilder);
      }
      if (attrIndex.orderingIndex != null)
      {
        IndexBuilder indexBuilder =
             new IndexBuilder(importContext, attrIndex.orderingIndex,
                              indexEntryLimit,
                              indexBufferSize);
        builders.add(indexBuilder);
      }
      if (attrIndex.approximateIndex != null)
      {
        IndexBuilder indexBuilder =
            new IndexBuilder(importContext, attrIndex.approximateIndex,
                             indexEntryLimit,
                             indexBufferSize);
        builders.add(indexBuilder);
      }
    }

    // Create an index builder for the children index.
    Index id2Children = entryContainer.getID2Children();
    IndexBuilder indexBuilder =
         new IndexBuilder(importContext, id2Children,
                          importContext.getConfig().getBackendIndexEntryLimit(),
                          indexBufferSize);
    builders.add(indexBuilder);

    // Create an index builder for the subtree index.
    Index id2Subtree = entryContainer.getID2Subtree();
    indexBuilder =
         new IndexBuilder(importContext, id2Subtree,
                          importContext.getConfig().getBackendIndexEntryLimit(),
                          indexBufferSize);
    builders.add(indexBuilder);

    for (IndexBuilder b : builders)
    {
      b.startProcessing();
    }

    try
    {
      do
      {
        try
        {
          entry = queue.poll(1000, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }
          continue;
        }

        if (entry != null)
        {
          Transaction txn = null;

          Entry existingEntry = null;
          EntryID entryID = null;

          if (entry.getAttachment() instanceof Entry)
          {
            // Replace an existing entry.
            existingEntry = (Entry)entry.getAttachment();
            entryID = (EntryID)existingEntry.getAttachment();

            // Update the referral database for referral entries.
            dn2uri.replaceEntry(txn, existingEntry, entry);
          }
          else
          {
            // Insert a new entry.
            existingEntry = null;
            ArrayList ids = (ArrayList)entry.getAttachment();
            entryID = (EntryID)ids.get(0);
            entryInsertCount++;

            // Update the referral database for referral entries.
            dn2uri.addEntry(txn, entry);
          }

          id2entry.put(txn, entryID, entry);

          for (IndexBuilder b : builders)
          {
            b.processEntry(existingEntry, entry, entryID);
          }

          importedCount++;
        }
      } while (!stopRequested);

      for (IndexBuilder b : builders)
      {
        b.stopProcessing();
      }

      // Increment the entry count.
      importContext.incrEntryInsertCount(entryInsertCount);
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
}
