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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2012-2015 ForgeRock AS.
 */
package org.opends.server.backends.jeb;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.CursorConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;

import org.opends.server.util.LDIFException;
import org.opends.server.util.StaticUtils;

import java.io.IOException;
import java.util.*;

import org.opends.server.types.*;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import static org.opends.messages.BackendMessages.*;

/**
 * Export a JE backend to LDIF.
 */
public class ExportJob
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();


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
  private long exportedCount;

  /**
   * The current number of entries skipped.
   */
  private long skippedCount;

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
    ArrayList<EntryContainer> exportContainers = new ArrayList<>();

    for (EntryContainer entryContainer : rootContainer.getEntryContainers())
    {
      // Skip containers that are not covered by the include branches.
      DN baseDN = entryContainer.getBaseDN();

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
            break;
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

    logger.info(NOTE_EXPORT_FINAL_STATUS, exportedCount, skippedCount, totalTime/1000, rate);

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
          if (logger.isTraceEnabled())
          {
            logger.traceException(e);

            logger.trace("Malformed id2entry ID %s.%n",
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
          entry = ID2Entry.entryFromDatabase(ByteString.wrap(data.getData()),
                       entryContainer.getRootContainer().getCompressedSchema());
        }
        catch (Exception e)
        {
          if (logger.isTraceEnabled())
          {
            logger.traceException(e);

            logger.trace("Malformed id2entry record for ID %d:%n%s%n",
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
    private long previousCount;

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

      logger.info(NOTE_EXPORT_PROGRESS_REPORT, latestCount, skippedCount, rate);

      previousCount = latestCount;
      previousTime = latestTime;
    }
  }

}
