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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 */
package org.opends.server.backends.ndb;
import com.mysql.cluster.ndbj.NdbApiException;
import com.mysql.cluster.ndbj.NdbOperation;
import org.opends.messages.Message;

import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.util.LDIFException;

import java.io.IOException;
import java.util.*;

import org.opends.server.backends.ndb.OperationContainer.DN2IDSearchCursor;
import org.opends.server.backends.ndb.OperationContainer.SearchCursorResult;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.messages.NdbMessages.*;

/**
 * Export a NDB backend to LDIF.
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
   * @throws NdbApiException If an error occurs in the NDB database.
   * @throws IOException If an I/O error occurs while writing an entry.
   * @throws LDIFException If an error occurs while trying to determine whether
   * to write an entry.
   */
  public void exportLDIF(RootContainer rootContainer)
       throws IOException, LDIFException, NdbApiException
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

    Message message = NOTE_NDB_EXPORT_FINAL_STATUS.get(
        exportedCount, skippedCount, totalTime/1000, rate);
    logError(message);

  }

  /**
   * Export the entries in a single entry entryContainer, in other words from
   * one of the base DNs.
   * @param entryContainer The entry container that holds the entries to be
   *                       exported.
   * @throws NdbApiException If an error occurs in the NDB database.
   * @throws IOException If an error occurs while writing an entry.
   * @throws  LDIFException  If an error occurs while trying to determine
   *                         whether to write an entry.
   */
  private void exportContainer(EntryContainer entryContainer)
       throws IOException, LDIFException, NdbApiException
  {
    OperationContainer dn2id = entryContainer.getDN2ID();
    RootContainer rc = entryContainer.getRootContainer();
    DN baseDN = DN.NULL_DN;

    AbstractTransaction txn = new AbstractTransaction(rc);

    DN2IDSearchCursor cursor = dn2id.getSearchCursor(txn, baseDN);
    cursor.open();

    try {
      SearchCursorResult result = cursor.getNext();
      while (result != null) {
        if (exportConfig.isCancelled()) {
          break;
        }
        DN dn = null;
        try {
          dn = DN.decode(result.dn);
        } catch (DirectoryException ex) {
          if (debugEnabled()) {
            TRACER.debugCaught(DebugLogLevel.ERROR, ex);
          }
          skippedCount++;
          continue;
        }
        Entry entry = null;
        AbstractTransaction leafTxn = new AbstractTransaction(rc);
        try {
          entry = dn2id.get(leafTxn, dn,
            NdbOperation.LockMode.LM_CommittedRead);
        } finally {
          if (leafTxn != null) {
            leafTxn.close();
          }
        }
        if ((entry != null) && entry.toLDIF(exportConfig)) {
          exportedCount++;
        } else {
          skippedCount++;
        }
        // Move to the next record.
        result = cursor.getNext();
      }
    } finally {
      cursor.close();
      if (txn != null) {
        txn.close();
      }
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
          NOTE_NDB_EXPORT_PROGRESS_REPORT.get(latestCount, skippedCount, rate);
      logError(message);

      previousCount = latestCount;
      previousTime = latestTime;
    }
  };

}
