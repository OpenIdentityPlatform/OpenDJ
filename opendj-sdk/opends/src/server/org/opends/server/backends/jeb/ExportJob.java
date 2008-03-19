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

import com.sleepycat.je.Cursor;
import com.sleepycat.je.CursorConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;

import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.util.LDIFException;
import org.opends.server.util.StaticUtils;

import java.io.IOException;
import java.util.*;

import org.opends.server.types.DebugLogLevel;
import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.messages.JebMessages.*;

/**
 * Export a JE backend to LDIF.
 */
public class ExportJob
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();


  /**
   * The requested LDIF export configuration.
   */
  private LDIFExportConfig exportConfig;

  /**
   * The number of milliseconds between job progress reports.
   */
  private long progressInterval = 10000;

  /**
   * The current number of entries exported.
   */
  private long exportedCount = 0;

  /**
   * The current number of entries skipped.
   */
  private long skippedCount = 0;

  /**
   * Create a new export job.
   *
   * @param exportConfig The requested LDIF export configuration.
   */
  public ExportJob(LDIFExportConfig exportConfig)
  {
    this.exportConfig = exportConfig;
  }

  /**
   * Export entries from the backend to an LDIF file.
   * @param rootContainer The root container to export.
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws IOException If an I/O error occurs while writing an entry.
   * @throws JebException If an error occurs in the JE backend.
   * @throws LDIFException If an error occurs while trying to determine whether
   * to write an entry.
   */
  public void exportLDIF(RootContainer rootContainer)
       throws IOException, LDIFException, DatabaseException, JebException
  {
    List<DN> includeBranches = exportConfig.getIncludeBranches();
    DN baseDN;
    ArrayList<EntryContainer> exportContainers =
        new ArrayList<EntryContainer>();

    for (EntryContainer entryContainer : rootContainer.getEntryContainers())
    {
      // Skip containers that are not covered by the include branches.
      baseDN = entryContainer.getBaseDN();

      if (includeBranches == null || includeBranches.isEmpty())
      {
        exportContainers.add(entryContainer);
      }
      else
      {
        for (DN includeBranch : includeBranches)
        {
          if (includeBranch.isDescendantOf(baseDN) ||
               includeBranch.isAncestorOf(baseDN))
          {
            exportContainers.add(entryContainer);
          }
        }
      }
    }

    // Make a note of the time we started.
    long startTime = System.currentTimeMillis();

    // Start a timer for the progress report.
    Timer timer = new Timer();
    TimerTask progressTask = new ProgressTask();
    timer.scheduleAtFixedRate(progressTask, progressInterval,
                              progressInterval);

    // Iterate through the containers.
    try
    {
      for (EntryContainer exportContainer : exportContainers)
      {
        if (exportConfig.isCancelled())
        {
          break;
        }

        exportContainer.sharedLock.lock();
        try
        {
          exportContainer(exportContainer);
        }
        finally
        {
          exportContainer.sharedLock.unlock();
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
      rate = 1000f*exportedCount / totalTime;
    }

    Message message = NOTE_JEB_EXPORT_FINAL_STATUS.get(
        exportedCount, skippedCount, totalTime/1000, rate);
    logError(message);

  }

  /**
   * Export the entries in a single entry entryContainer, in other words from
   * one of the base DNs.
   * @param entryContainer The entry container that holds the entries to be
   *                       exported.
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws IOException If an error occurs while writing an entry.
   * @throws  LDIFException  If an error occurs while trying to determine
   *                         whether to write an entry.
   */
  private void exportContainer(EntryContainer entryContainer)
       throws DatabaseException, IOException, LDIFException
  {
    ID2Entry id2entry = entryContainer.getID2Entry();

    Cursor cursor = id2entry.openCursor(null, new CursorConfig());
    try
    {
      DatabaseEntry key = new DatabaseEntry();
      DatabaseEntry data = new DatabaseEntry();

      OperationStatus status;
      for (status = cursor.getFirst(key, data, LockMode.DEFAULT);
           status == OperationStatus.SUCCESS;
           status = cursor.getNext(key, data, LockMode.DEFAULT))
      {
        if (exportConfig.isCancelled())
        {
          break;
        }

        EntryID entryID = null;
        try
        {
          entryID = new EntryID(key);
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);

            TRACER.debugError("Malformed id2entry ID %s.%n",
                            StaticUtils.bytesToHex(key.getData()));
          }
          skippedCount++;
          continue;
        }

        if (entryID.longValue() == 0)
        {
          // This is the stored entry count.
          continue;
        }

        Entry entry = null;
        try
        {
          entry = JebFormat.entryFromDatabase(data.getData(),
                       entryContainer.getRootContainer().getCompressedSchema());
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);

            TRACER.debugError("Malformed id2entry record for ID %d:%n%s%n",
                       entryID.longValue(),
                       StaticUtils.bytesToHex(data.getData()));
          }
          skippedCount++;
          continue;
        }

        if (entry.toLDIF(exportConfig))
        {
          exportedCount++;
        }
        else
        {
          skippedCount++;
        }
      }
    }
    finally
    {
      cursor.close();
    }
  }

  /**
   * This class reports progress of the export job at fixed intervals.
   */
  class ProgressTask extends TimerTask
  {
    /**
     * The number of entries that had been exported at the time of the
     * previous progress report.
     */
    private long previousCount = 0;

    /**
     * The time in milliseconds of the previous progress report.
     */
    private long previousTime;

    /**
     * Create a new export progress task.
     */
    public ProgressTask()
    {
      previousTime = System.currentTimeMillis();
    }

    /**
     * The action to be performed by this timer task.
     */
    public void run()
    {
      long latestCount = exportedCount;
      long deltaCount = (latestCount - previousCount);
      long latestTime = System.currentTimeMillis();
      long deltaTime = latestTime - previousTime;

      if (deltaTime == 0)
      {
        return;
      }

      float rate = 1000f*deltaCount / deltaTime;

      Message message =
          NOTE_JEB_EXPORT_PROGRESS_REPORT.get(latestCount, skippedCount, rate);
      logError(message);

      previousCount = latestCount;
      previousTime = latestTime;
    }
  };

}
