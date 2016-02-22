/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package org.opends.server.backends.pluggable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.ReadOperation;
import org.opends.server.backends.pluggable.spi.ReadableTransaction;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.util.LDIFException;
import org.opends.server.util.StaticUtils;

import static org.opends.messages.BackendMessages.*;

/** Export a backend to LDIF. */
class ExportJob
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The requested LDIF export configuration. */
  private final LDIFExportConfig exportConfig;

  /** The number of milliseconds between job progress reports. */
  private final long progressInterval = 10000;

  /** The current number of entries exported. */
  private long exportedCount;

  /** The current number of entries skipped. */
  private long skippedCount;

  /**
   * Create a new export job.
   *
   * @param exportConfig The requested LDIF export configuration.
   */
  ExportJob(LDIFExportConfig exportConfig)
  {
    this.exportConfig = exportConfig;
  }

  /**
   * Export entries from the backend to an LDIF file.
   * @param rootContainer The root container to export.
   * @throws StorageRuntimeException If an error occurs in the storage.
   * @throws IOException If an I/O error occurs while writing an entry.
   * @throws LDIFException If an error occurs while trying to determine whether
   * to write an entry.
   */
  void exportLDIF(RootContainer rootContainer)
       throws IOException, LDIFException, StorageRuntimeException
  {
    List<DN> includeBranches = exportConfig.getIncludeBranches();
    final ArrayList<EntryContainer> exportContainers = new ArrayList<>();

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
          if (includeBranch.isSubordinateOrEqualTo(baseDN) ||
               includeBranch.isSuperiorOrEqualTo(baseDN))
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
    timer.scheduleAtFixedRate(progressTask, progressInterval, progressInterval);

    // Iterate through the containers.
    try
    {
      rootContainer.getStorage().read(new ReadOperation<Void>()
      {
        @Override
        public Void run(ReadableTransaction txn) throws Exception
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
              exportContainer(txn, exportContainer);
            }
            finally
            {
              exportContainer.sharedLock.unlock();
            }
          }
          return null;
        }
      });
    }
    catch (Exception e)
    {
      throw new StorageRuntimeException(e);
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
      rate = 1000f*exportedCount / totalTime;
    }

    logger.info(NOTE_EXPORT_FINAL_STATUS, exportedCount, skippedCount, totalTime/1000, rate);
  }

  /**
   * Export the entries in a single entry entryContainer, in other words from
   * one of the base DNs.
   * @param entryContainer The entry container that holds the entries to be
   *                       exported.
   * @throws StorageRuntimeException If an error occurs in the storage.
   * @throws IOException If an error occurs while writing an entry.
   * @throws  LDIFException  If an error occurs while trying to determine
   *                         whether to write an entry.
   */
  private void exportContainer(ReadableTransaction txn, EntryContainer entryContainer)
       throws StorageRuntimeException, IOException, LDIFException
  {
    ID2Entry id2entry = entryContainer.getID2Entry();
    try (final Cursor<ByteString, ByteString> cursor = txn.openCursor(id2entry.getName()))
    {
      while (cursor.next())
      {
        if (exportConfig.isCancelled())
        {
          break;
        }

        ByteString key = cursor.getKey();
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

            logger.trace("Malformed id2entry ID %s.%n", StaticUtils.bytesToHex(key));
          }
          skippedCount++;
          continue;
        }

        if (entryID.longValue() == 0)
        {
          // This is the stored entry count.
          continue;
        }

        ByteString value = cursor.getValue();
        Entry entry = null;
        try
        {
          entry = id2entry.entryFromDatabase(value, entryContainer.getRootContainer().getCompressedSchema());
        }
        catch (Exception e)
        {
          if (logger.isTraceEnabled())
          {
            logger.traceException(e);

            logger.trace("Malformed id2entry record for ID %d:%n%s%n",
                       entryID, StaticUtils.bytesToHex(value));
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
  }

  /** This class reports progress of the export job at fixed intervals. */
  private class ProgressTask extends TimerTask
  {
    /** The number of entries that had been exported at the time of the previous progress report. */
    private long previousCount;

    /** The time in milliseconds of the previous progress report. */
    private long previousTime;

    /** Create a new export progress task. */
    public ProgressTask()
    {
      previousTime = System.currentTimeMillis();
    }

    /** The action to be performed by this timer task. */
    @Override
    public void run()
    {
      long latestCount = exportedCount;
      long deltaCount = latestCount - previousCount;
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
