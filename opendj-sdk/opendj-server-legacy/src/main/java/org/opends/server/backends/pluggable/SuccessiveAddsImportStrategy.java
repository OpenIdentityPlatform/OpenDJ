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
 *      Copyright 2015 ForgeRock AS
 */
package org.opends.server.backends.pluggable;

import static org.opends.messages.BackendMessages.*;
import static org.opends.messages.UtilityMessages.*;
import static org.opends.server.core.DirectoryServer.*;
import static org.opends.server.util.StaticUtils.*;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ServerContext;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.LDIFImportResult;
import org.opends.server.types.OpenDsException;
import org.opends.server.util.LDIFException;
import org.opends.server.util.LDIFReader;

/**
 * Imports LDIF entries one by one, by calling
 * {@link EntryContainer#addEntry(Entry, org.opends.server.core.AddOperation)}.
 */
final class SuccessiveAddsImportStrategy implements ImportStrategy
{
  /** Logs the progress of the import. */
  private static final class ImportProgress implements Runnable
  {
    private final LDIFReader reader;
    private long previousCount;
    private long previousTime;

    public ImportProgress(LDIFReader reader)
    {
      this.reader = reader;
    }

    @Override
    public void run()
    {
      long latestCount = reader.getEntriesRead() + 0;
      long deltaCount = latestCount - previousCount;
      long latestTime = System.currentTimeMillis();
      long deltaTime = latestTime - previousTime;
      if (deltaTime == 0)
      {
        return;
      }
      long entriesRead = reader.getEntriesRead();
      long entriesIgnored = reader.getEntriesIgnored();
      long entriesRejected = reader.getEntriesRejected();
      float rate = 1000f * deltaCount / deltaTime;
      logger.info(NOTE_IMPORT_PROGRESS_REPORT, entriesRead, entriesIgnored, entriesRejected, rate);

      previousCount = latestCount;
      previousTime = latestTime;
    }
  }

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private static final int IMPORT_PROGRESS_INTERVAL = 10000;

  /** {@inheritDoc} */
  @Override
  public LDIFImportResult importLDIF(LDIFImportConfig importConfig, RootContainer rootContainer,
      ServerContext serverContext) throws DirectoryException
  {
    try
    {
      ScheduledThreadPoolExecutor timerService = new ScheduledThreadPoolExecutor(1);
      try
      {
        final LDIFReader reader;
        try
        {
          reader = new LDIFReader(importConfig);
        }
        catch (Exception e)
        {
          LocalizableMessage m = ERR_LDIF_BACKEND_CANNOT_CREATE_LDIF_READER.get(stackTraceToSingleLineString(e));
          throw new DirectoryException(DirectoryServer.getServerErrorResultCode(), m, e);
        }

        long importCount = 0;
        final long startTime = System.currentTimeMillis();
        timerService.scheduleAtFixedRate(new ImportProgress(reader),
            IMPORT_PROGRESS_INTERVAL, IMPORT_PROGRESS_INTERVAL, TimeUnit.MILLISECONDS);
        while (true)
        {
          final Entry entry;
          try
          {
            entry = reader.readEntry();
            if (entry == null)
            {
              break;
            }
          }
          catch (LDIFException le)
          {
            if (!le.canContinueReading())
            {
              LocalizableMessage m = ERR_LDIF_BACKEND_ERROR_READING_LDIF.get(stackTraceToSingleLineString(le));
              throw new DirectoryException(DirectoryServer.getServerErrorResultCode(), m, le);
            }
            continue;
          }

          final DN dn = entry.getName();
          final EntryContainer ec = rootContainer.getEntryContainer(dn);
          if (ec == null)
          {
            final LocalizableMessage m = ERR_LDIF_SKIP.get(dn);
            logger.error(m);
            reader.rejectLastEntry(m);
            continue;
          }

          try
          {
            ec.addEntry(entry, null);
            importCount++;
          }
          catch (DirectoryException e)
          {
            switch (e.getResultCode().asEnum())
            {
            case ENTRY_ALREADY_EXISTS:
              if (importConfig.replaceExistingEntries())
              {
                final Entry oldEntry = ec.getEntry(entry.getName());
                ec.replaceEntry(oldEntry, entry, null);
              }
              else
              {
                reader.rejectLastEntry(WARN_IMPORT_ENTRY_EXISTS.get());
              }
              break;
            case NO_SUCH_OBJECT:
              reader.rejectLastEntry(ERR_IMPORT_PARENT_NOT_FOUND.get(dn.parent()));
              break;
            default:
              // Not sure why it failed.
              reader.rejectLastEntry(e.getMessageObject());
              break;
            }
          }
        }
        final long finishTime = System.currentTimeMillis();

        waitForShutdown(timerService);

        final long importTime = finishTime - startTime;
        float rate = 0;
        if (importTime > 0)
        {
          rate = 1000f * reader.getEntriesRead() / importTime;
        }
        logger.info(NOTE_IMPORT_FINAL_STATUS, reader.getEntriesRead(), importCount, reader.getEntriesIgnored(),
            reader.getEntriesRejected(), 0, importTime / 1000, rate);
        return new LDIFImportResult(reader.getEntriesRead(), reader.getEntriesRejected(), reader.getEntriesIgnored());
      }
      finally
      {
        rootContainer.close();

        // if not already stopped, then stop it
        waitForShutdown(timerService);
      }
    }
    catch (DirectoryException e)
    {
      logger.traceException(e);
      throw e;
    }
    catch (OpenDsException e)
    {
      logger.traceException(e);
      throw new DirectoryException(getServerErrorResultCode(), e.getMessageObject());
    }
    catch (Exception e)
    {
      logger.traceException(e);
      throw new DirectoryException(getServerErrorResultCode(), LocalizableMessage.raw(e.getMessage()));
    }
  }

  private void waitForShutdown(ScheduledThreadPoolExecutor timerService) throws InterruptedException
  {
    timerService.shutdown();
    timerService.awaitTermination(20, TimeUnit.SECONDS);
  }
}
