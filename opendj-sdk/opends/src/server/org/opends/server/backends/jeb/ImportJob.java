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

import com.sleepycat.je.*;

import org.opends.server.types.DebugLogLevel;
import org.opends.messages.JebMessages;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.LDIFImportResult;
import org.opends.server.types.ResultCode;
import org.opends.server.util.LDIFException;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.RuntimeInformation;
import static org.opends.server.util.StaticUtils.getFileForPath;

import static org.opends.server.util.DynamicConstants.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.opends.messages.JebMessages.
    WARN_JEB_IMPORT_ENTRY_EXISTS;
import static org.opends.messages.JebMessages.
    ERR_JEB_IMPORT_PARENT_NOT_FOUND;
import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.messages.JebMessages.*;
import org.opends.server.admin.std.server.LocalDBBackendCfg;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;

/**
 * Import from LDIF to a JE backend.
 */
public class ImportJob implements Thread.UncaughtExceptionHandler
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * The JE backend configuration.
   */
  private LocalDBBackendCfg config;

  /**
   * The root container used for this import job.
   */
  private RootContainer rootContainer;

  /**
   * The LDIF import configuration.
   */
  private LDIFImportConfig ldifImportConfig;

  /**
   * The LDIF reader.
   */
  private LDIFReader reader;

  /**
   * Map of base DNs to their import context.
   */
  private LinkedHashMap<DN,ImportContext> importMap =
      new LinkedHashMap<DN, ImportContext>();

  /**
   * The maximum number of parent ID values that we will remember.
   */
  private static final int PARENT_ID_MAP_SIZE = 50;

  /**
   * Map of likely parent entry DNs to their entry IDs.
   */
  private HashMap<DN,EntryID> parentIDMap =
       new HashMap<DN,EntryID>(PARENT_ID_MAP_SIZE);

  /**
   * The number of entries imported.
   */
  private int importedCount;

  /**
   * The number of entries migrated.
   */
  private int migratedCount;

  /**
   * The number of merge passes.
   */
  int mergePassNumber = 1;


  /**
   * The number of milliseconds between job progress reports.
   */
  private long progressInterval = 10000;

  /**
   * The progress report timer.
   */
  private Timer timer;

  private int entriesProcessed;
  private int importPassSize;


  /**
   * The import worker threads.
   */
  private CopyOnWriteArrayList<ImportThread> threads;

  /**
   * Create a new import job.
   *
   * @param ldifImportConfig The LDIF import configuration.
   */
  public ImportJob(LDIFImportConfig ldifImportConfig)
  {
    this.ldifImportConfig = ldifImportConfig;
    this.threads = new CopyOnWriteArrayList<ImportThread>();
  }

  /**
   * Import from LDIF file to one or more base DNs. Opens the database
   * environment and deletes existing data for each base DN unless we are
   * appending to existing data. Creates a temporary working directory,
   * processes the LDIF file, then merges the resulting intermediate
   * files to load the index databases.
   *
   * @param rootContainer The root container to import into.
   *
   * @return  Information about the result of the import.
   *
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws IOException  If a problem occurs while opening the LDIF file for
   *                      reading, or while reading from the LDIF file.
   * @throws JebException If an error occurs in the JE backend.
   * @throws DirectoryException if a directory server related error occurs.
   * @throws ConfigException if a configuration related error occurs.
   */
  public LDIFImportResult importLDIF(RootContainer rootContainer)
      throws DatabaseException, IOException, JebException, DirectoryException,
             ConfigException
  {

    // Create an LDIF reader. Throws an exception if the file does not exist.
    reader = new LDIFReader(ldifImportConfig);
    this.rootContainer = rootContainer;
    this.config = rootContainer.getConfiguration();
    this.mergePassNumber = 1;
    this.entriesProcessed = 0;
    this.importPassSize   = config.getImportPassSize();
    if (importPassSize <= 0)
    {
      importPassSize = Integer.MAX_VALUE;
    }

    Message message;
    long startTime;

    try
    {
      // Divide the total buffer size by the number of threads
      // and give that much to each thread.
      int importThreadCount = config.getImportThreadCount();
      long bufferSize = config.getImportBufferSize() /
          (importThreadCount*rootContainer.getBaseDNs().size());

      message = INFO_JEB_IMPORT_STARTING.get(DirectoryServer.getVersionString(),
                                                     BUILD_ID, REVISION_NUMBER);
      logError(message);
      message = INFO_JEB_IMPORT_THREAD_COUNT.get(importThreadCount);
      logError(message);

      RuntimeInformation.logInfo();
      if (debugEnabled())
      {

        message = INFO_JEB_IMPORT_BUFFER_SIZE.get(bufferSize);
        TRACER.debugInfo(message.toString());
        message = INFO_JEB_IMPORT_ENVIRONMENT_CONFIG.get(
                rootContainer.getEnvironmentConfig().toString());
        TRACER.debugInfo(message.toString());
      }

      for (EntryContainer entryContainer : rootContainer.getEntryContainers())
      {
        ImportContext importContext =
            getImportContext(entryContainer, bufferSize);

        if(importContext != null)
        {
          importMap.put(entryContainer.getBaseDN(), importContext);
        }
      }

      // Make a note of the time we started.
      startTime = System.currentTimeMillis();

      // Create a temporary work directory.
      File tempDir = getFileForPath(config.getImportTempDirectory());
      if(!tempDir.exists() && !tempDir.mkdir())
      {
        Message msg = ERR_JEB_IMPORT_CREATE_TMPDIR_ERROR.get(
                String.valueOf(tempDir));
        throw new IOException(msg.toString());
      }

      if (tempDir.listFiles() != null)
      {
        for (File f : tempDir.listFiles())
        {
          f.delete();
        }
      }

      startWorkerThreads();
      try
      {
        importedCount = 0;
        migratedCount = 0;
        migrateExistingEntries();
        processLDIF();
        migrateExcludedEntries();
      }
      finally
      {
        merge(false);
        tempDir.delete();

        for(ImportContext importContext : importMap.values())
        {
          DN baseDN = importContext.getBaseDN();
          EntryContainer srcEntryContainer =
              importContext.getSrcEntryContainer();
          if(srcEntryContainer != null)
          {
            if (debugEnabled())
            {
              TRACER.debugInfo("Deleteing old entry container for base DN " +
                  "%s and renaming temp entry container", baseDN);
            }
            EntryContainer unregEC =
              rootContainer.unregisterEntryContainer(baseDN);
            //Make sure the unregistered EC for the base DN is the same as
            //the one in the import context.
            if(unregEC != srcEntryContainer)
            {
              if(debugEnabled())
              {
                TRACER.debugInfo("Current entry container used for base DN " +
                    "%s is not the same as the source entry container used " +
                    "during the migration process.", baseDN);
              }
              rootContainer.registerEntryContainer(baseDN, unregEC);
              continue;
            }
            srcEntryContainer.exclusiveLock.lock();
            srcEntryContainer.delete();
            srcEntryContainer.exclusiveLock.unlock();
            EntryContainer newEC = importContext.getEntryContainer();
            newEC.exclusiveLock.lock();
            newEC.setDatabasePrefix(baseDN.toNormalizedString());
            newEC.exclusiveLock.unlock();
            rootContainer.registerEntryContainer(baseDN, newEC);
          }
        }
      }
    }
    finally
    {
      reader.close();
    }

    long finishTime = System.currentTimeMillis();
    long importTime = (finishTime - startTime);

    float rate = 0;
    if (importTime > 0)
    {
      rate = 1000f*importedCount / importTime;
    }

    message = INFO_JEB_IMPORT_FINAL_STATUS.
        get(reader.getEntriesRead(), importedCount - migratedCount,
            reader.getEntriesIgnored(), reader.getEntriesRejected(),
            migratedCount, importTime/1000, rate);
    logError(message);

    message = INFO_JEB_IMPORT_ENTRY_LIMIT_EXCEEDED_COUNT.get(
        getEntryLimitExceededCount());
    logError(message);

    return new LDIFImportResult(reader.getEntriesRead(),
                                reader.getEntriesRejected(),
                                reader.getEntriesIgnored());
  }

  /**
   * Merge the intermediate files to load the index databases.
   *
   * @param moreData <CODE>true</CODE> if this is a intermediate merge or
   * <CODE>false</CODE> if this is a final merge.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  private void merge(boolean moreData) throws DatabaseException
  {
    stopWorkerThreads();

    try
    {
      if (moreData)
      {
        Message message =
            INFO_JEB_IMPORT_BEGINNING_INTERMEDIATE_MERGE.get(mergePassNumber++);
        logError(message);
      }
      else
      {
        Message message = INFO_JEB_IMPORT_BEGINNING_FINAL_MERGE.get();
        logError(message);
      }


      long mergeStartTime = System.currentTimeMillis();

      ArrayList<IndexMergeThread> mergers = new ArrayList<IndexMergeThread>();

      ArrayList<VLVIndexMergeThread> vlvIndexMergeThreads =
        new ArrayList<VLVIndexMergeThread>();

      // Create merge threads for each base DN.
      for (ImportContext importContext : importMap.values())
      {
        EntryContainer entryContainer = importContext.getEntryContainer();

        // For each configured attribute index.
        for (AttributeIndex attrIndex : entryContainer.getAttributeIndexes())
        {
          int indexEntryLimit = config.getIndexEntryLimit();
          if(attrIndex.getConfiguration().getIndexEntryLimit() != null)
          {
            indexEntryLimit = attrIndex.getConfiguration().getIndexEntryLimit();
          }

          if (attrIndex.equalityIndex != null)
          {
            Index index = attrIndex.equalityIndex;
            IndexMergeThread indexMergeThread =
                new IndexMergeThread(config,
                                     ldifImportConfig, index,
                                     indexEntryLimit);
            mergers.add(indexMergeThread);
          }
          if (attrIndex.presenceIndex != null)
          {
            Index index = attrIndex.presenceIndex;
            IndexMergeThread indexMergeThread =
                new IndexMergeThread(config,
                                     ldifImportConfig, index,
                                     indexEntryLimit);
            mergers.add(indexMergeThread);
          }
          if (attrIndex.substringIndex != null)
          {
            Index index = attrIndex.substringIndex;
            IndexMergeThread indexMergeThread =
                new IndexMergeThread(config,
                                     ldifImportConfig, index,
                                     indexEntryLimit);
            mergers.add(indexMergeThread);
          }
          if (attrIndex.orderingIndex != null)
          {
            Index index = attrIndex.orderingIndex;
            IndexMergeThread indexMergeThread =
                new IndexMergeThread(config,
                                     ldifImportConfig, index,
                                     indexEntryLimit);
            mergers.add(indexMergeThread);
          }
          if (attrIndex.approximateIndex != null)
          {
            Index index = attrIndex.approximateIndex;
            IndexMergeThread indexMergeThread =
                new IndexMergeThread(config,
                                     ldifImportConfig, index,
                                     indexEntryLimit);
            mergers.add(indexMergeThread);
          }
        }

        for(VLVIndex vlvIndex : entryContainer.getVLVIndexes())
        {
          VLVIndexMergeThread vlvIndexMergeThread =
              new VLVIndexMergeThread(config, ldifImportConfig, vlvIndex);
          vlvIndexMergeThread.setUncaughtExceptionHandler(this);
          vlvIndexMergeThreads.add(vlvIndexMergeThread);
        }

        // Id2Children index.
        Index id2Children = entryContainer.getID2Children();
        IndexMergeThread indexMergeThread =
            new IndexMergeThread(config,
                                 ldifImportConfig,
                                 id2Children,
                                 config.getIndexEntryLimit());
        mergers.add(indexMergeThread);

        // Id2Subtree index.
        Index id2Subtree = entryContainer.getID2Subtree();
        indexMergeThread =
            new IndexMergeThread(config,
                                 ldifImportConfig,
                                 id2Subtree,
                                 config.getIndexEntryLimit());
        mergers.add(indexMergeThread);
      }

      // Run all the merge threads.
      for (IndexMergeThread imt : mergers)
      {
        imt.start();
      }
      for (VLVIndexMergeThread imt : vlvIndexMergeThreads)
      {
        imt.start();
      }

      // Wait for the threads to finish.
      for (IndexMergeThread imt : mergers)
      {
        try
        {
          imt.join();
        }
        catch (InterruptedException e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }
        }
      }
      // Wait for the threads to finish.
      for (VLVIndexMergeThread imt : vlvIndexMergeThreads)
      {
        try
        {
          imt.join();
        }
        catch (InterruptedException e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }
        }
      }

      long mergeEndTime = System.currentTimeMillis();

      if (moreData)
      {
        Message message = INFO_JEB_IMPORT_RESUMING_LDIF_PROCESSING.get(
            ((mergeEndTime-mergeStartTime)/1000));
        logError(message);
      }
      else
      {
        Message message = INFO_JEB_IMPORT_FINAL_MERGE_COMPLETED.get(
            ((mergeEndTime-mergeStartTime)/1000));
        logError(message);
      }
    }
    finally
    {
      if(moreData)
      {
        startWorkerThreads();
      }
    }
  }

  private void startWorkerThreads() throws DatabaseException
  {
    // Create one set of worker threads for each base DN.
    int importThreadCount = config.getImportThreadCount();
    for (ImportContext ic : importMap.values())
    {
      for (int i = 0; i < importThreadCount; i++)
      {
        ImportThread t = new ImportThread(ic, i);
        t.setUncaughtExceptionHandler(this);
        threads.add(t);

        t.start();
      }
    }

    // Start a timer for the progress report.
    timer = new Timer();
    TimerTask progressTask = new ImportJob.ProgressTask();
    timer.scheduleAtFixedRate(progressTask, progressInterval,
                              progressInterval);
  }

  private void stopWorkerThreads()
  {
    if(threads.size() > 0)
    {
      // Wait for the queues to be drained.
      for (ImportContext ic : importMap.values())
      {
        while (ic.getQueue().size() > 0)
        {
          try
          {
            Thread.sleep(100);
          } catch (Exception e)
          {
            // No action needed.
          }
        }
      }
    }

    // Order the threads to stop.
    for (ImportThread t : threads)
    {
      t.stopProcessing();
    }

    // Wait for each thread to stop.
    for (ImportThread t : threads)
    {
      try
      {
        t.join();
        importedCount += t.getImportedCount();
      }
      catch (InterruptedException ie)
      {
        // No action needed?
      }
    }

    timer.cancel();
  }

  /**
   * Create a set of worker threads, one set for each base DN.
   * Read each entry from the LDIF and determine which
   * base DN the entry belongs to. Write the dn2id database, then put the
   * entry on the appropriate queue for the worker threads to consume.
   * Record the entry count for each base DN when all entries have been
   * processed.
   *
   * pass size was reached), false if the entire LDIF file has been read.
   *
   * @throws JebException If an error occurs in the JE backend.
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws  IOException  If a problem occurs while opening the LDIF file for
   *                       reading, or while reading from the LDIF file.
   */
  private void processLDIF()
      throws JebException, DatabaseException, IOException
  {
    Message message = INFO_JEB_IMPORT_LDIF_START.get();
    logError(message);

    do
    {
      if (ldifImportConfig.isCancelled())
      {
        break;
      }

      if(threads.size() <= 0)
      {
        message = ERR_JEB_IMPORT_NO_WORKER_THREADS.get();
        throw new JebException(message);
      }
      try
      {
        // Read the next entry.
        Entry entry = reader.readEntry();

        // Check for end of file.
        if (entry == null)
        {
          message = INFO_JEB_IMPORT_LDIF_END.get();
          logError(message);

          break;
        }

        // Route it according to base DN.
        ImportContext importContext = getImportConfig(entry.getDN());

        processEntry(importContext, entry);

        entriesProcessed++;
        if (entriesProcessed >= importPassSize)
        {
          merge(true);
          entriesProcessed = 0;
        }
      }
      catch (LDIFException e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }
      catch (DirectoryException e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }
    } while (true);
  }

  private void migrateExistingEntries()
      throws JebException, DatabaseException, DirectoryException
  {
    for(ImportContext importContext : importMap.values())
    {
      EntryContainer srcEntryContainer = importContext.getSrcEntryContainer();
      if(srcEntryContainer != null &&
          !importContext.getIncludeBranches().isEmpty())
      {
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        LockMode lockMode = LockMode.DEFAULT;
        OperationStatus status;

        Message message = INFO_JEB_IMPORT_MIGRATION_START.get(
            "existing", String.valueOf(importContext.getBaseDN()));
        logError(message);

        Cursor cursor =
            srcEntryContainer.getDN2ID().openCursor(null,
                                                   CursorConfig.READ_COMMITTED);
        try
        {
          status = cursor.getFirst(key, data, lockMode);

          while(status == OperationStatus.SUCCESS &&
                !ldifImportConfig.isCancelled())
          {
            if(threads.size() <= 0)
            {
              message = ERR_JEB_IMPORT_NO_WORKER_THREADS.get();
              throw new JebException(message);
            }

            DN dn = DN.decode(new ASN1OctetString(key.getData()));
            if(!importContext.getIncludeBranches().contains(dn))
            {
              EntryID id = new EntryID(data);
              Entry entry = srcEntryContainer.getID2Entry().get(null, id);
              processEntry(importContext, entry);

              entriesProcessed++;
              migratedCount++;
              if (entriesProcessed >= importPassSize)
              {
                merge(true);
                entriesProcessed = 0;
              }
              status = cursor.getNext(key, data, lockMode);
            }
            else
            {
              // This is the base entry for a branch that will be included
              // in the import so we don't want to copy the branch to the new
              // entry container.

              /**
               * Advance the cursor to next entry at the same level in the DIT
               * skipping all the entries in this branch.
               * Set the next starting value to a value of equal length but
               * slightly greater than the previous DN. Since keys are compared
               * in reverse order we must set the first byte (the comma).
               * No possibility of overflow here.
               */
              byte[] begin =
                  StaticUtils.getBytes("," + dn.toNormalizedString());
              begin[0] = (byte) (begin[0] + 1);
              key.setData(begin);
              status = cursor.getSearchKeyRange(key, data, lockMode);
            }
          }
        }
        finally
        {
          cursor.close();
        }
      }
    }
  }

  private void migrateExcludedEntries()
      throws JebException, DatabaseException
  {
    for(ImportContext importContext : importMap.values())
    {
      EntryContainer srcEntryContainer = importContext.getSrcEntryContainer();
      if(srcEntryContainer != null &&
          !importContext.getExcludeBranches().isEmpty())
      {
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        LockMode lockMode = LockMode.DEFAULT;
        OperationStatus status;

        Message message = INFO_JEB_IMPORT_MIGRATION_START.get(
            "excluded", String.valueOf(importContext.getBaseDN()));
        logError(message);

        Cursor cursor =
            srcEntryContainer.getDN2ID().openCursor(null,
                                                   CursorConfig.READ_COMMITTED);
        Comparator<byte[]> dn2idComparator =
            srcEntryContainer.getDN2ID().getComparator();
        try
        {
          for(DN excludedDN : importContext.getExcludeBranches())
          {
            byte[] suffix =
                StaticUtils.getBytes(excludedDN.toNormalizedString());
            key.setData(suffix);
            status = cursor.getSearchKeyRange(key, data, lockMode);

            if(status == OperationStatus.SUCCESS &&
                Arrays.equals(key.getData(), suffix))
            {
              // This is the base entry for a branch that was excluded in the
              // import so we must migrate all entries in this branch over to
              // the new entry container.

              byte[] end =
                  StaticUtils.getBytes("," + excludedDN.toNormalizedString());
              end[0] = (byte) (end[0] + 1);

              while(status == OperationStatus.SUCCESS &&
                  dn2idComparator.compare(key.getData(), end) < 0 &&
                  !ldifImportConfig.isCancelled())
              {
                if(threads.size() <= 0)
                {
                  message = ERR_JEB_IMPORT_NO_WORKER_THREADS.get();
                  throw new JebException(message);
                }

                EntryID id = new EntryID(data);
                Entry entry = srcEntryContainer.getID2Entry().get(null, id);
                processEntry(importContext, entry);

                entriesProcessed++;
                migratedCount++;
                if (entriesProcessed >= importPassSize)
                {
                  merge(true);
                  entriesProcessed = 0;
                }
                status = cursor.getNext(key, data, lockMode);
              }
            }
          }
        }
        finally
        {
          cursor.close();
        }
      }
    }
  }

  /**
   * Process an entry to be imported. Read dn2id to check if the entry already
   * exists, and write dn2id if it does not. Put the entry on the worker
   * thread queue.
   *
   * @param importContext The import context for this entry.
   * @param entry The entry to be imported.
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws JebException If an error occurs in the JE backend.
   */
  public void processEntry(ImportContext importContext, Entry entry)
      throws JebException, DatabaseException
  {
    DN entryDN = entry.getDN();
    LDIFImportConfig ldifImportConfig = importContext.getLDIFImportConfig();

    Transaction txn = null;
    if (ldifImportConfig.appendToExistingData())
    {
      txn = importContext.getEntryContainer().beginTransaction();
    }

    DN2ID dn2id = importContext.getEntryContainer().getDN2ID();
    ID2Entry id2entry = importContext.getEntryContainer().getID2Entry();

    try
    {
      // See if the entry already exists.
      EntryID entryID = dn2id.get(txn, entryDN);
      if (entryID != null)
      {
        // See if we are allowed to replace the entry that exists.
        if (ldifImportConfig.appendToExistingData() &&
            ldifImportConfig.replaceExistingEntries())
        {
          // Read the existing entry contents.
          Entry oldEntry = id2entry.get(txn, entryID);

          // Attach the ID to the old entry.
          oldEntry.setAttachment(entryID);

          // Attach the old entry to the new entry.
          entry.setAttachment(oldEntry);

          // Put the entry on the queue.
          try
          {
            importContext.getQueue().put(entry);
          }
          catch (InterruptedException e)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }
          }
        }
        else
        {
          // Reject the entry.

          Message msg = WARN_JEB_IMPORT_ENTRY_EXISTS.get();
          importContext.getLDIFReader().rejectLastEntry(msg);
          return;
        }
      }
      else
      {
        // Make sure the parent entry exists, unless this entry is a base DN.
        EntryID parentID = null;
        DN parentDN = importContext.getEntryContainer().
            getParentWithinBase(entryDN);
        if (parentDN != null)
        {
          parentID = getParentID(parentDN, dn2id, txn);
          if (parentID == null)
          {
            // Reject the entry.
            Message msg =
                    ERR_JEB_IMPORT_PARENT_NOT_FOUND.get(parentDN.toString());
            importContext.getLDIFReader().rejectLastEntry(msg);
            return;
          }
        }

        // Assign a new entry identifier and write the new DN.
        entryID = rootContainer.getNextEntryID();
        dn2id.insert(txn, entryDN, entryID);

        // Construct a list of IDs up the DIT.
        ArrayList<EntryID> IDs;
        if (parentDN != null && importContext.getParentDN() != null &&
             parentDN.equals(importContext.getParentDN()))
        {
          // Reuse the previous values.
          IDs = new ArrayList<EntryID>(importContext.getIDs());
          IDs.set(0, entryID);
        }
        else
        {
          IDs = new ArrayList<EntryID>(entryDN.getNumComponents());
          IDs.add(entryID);
          if (parentID != null)
          {
            IDs.add(parentID);
            EntryContainer ec = importContext.getEntryContainer();
            for (DN dn = ec.getParentWithinBase(parentDN); dn != null;
                 dn = ec.getParentWithinBase(dn))
            {
              // Read the ID from dn2id.
              EntryID nodeID = dn2id.get(txn, dn);
              IDs.add(nodeID);
            }
          }
        }
        importContext.setParentDN(parentDN);
        importContext.setIDs(IDs);

        // Attach the list of IDs to the entry.
        entry.setAttachment(IDs);

        // Put the entry on the queue.
        try
        {
          while(!importContext.getQueue().offer(entry, 1000,
                                                TimeUnit.MILLISECONDS))
          {
            if(threads.size() <= 0)
            {
              // All worker threads died. We must stop now.
              return;
            }
          }
        }
        catch (InterruptedException e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }
        }
      }

      if (txn != null)
      {
        importContext.getEntryContainer().transactionCommit(txn);
        txn = null;
      }
    }
    finally
    {
      if (txn != null)
      {
        importContext.getEntryContainer().transactionAbort(txn);
      }
    }
  }

  /**
   * Retrieves the entry ID for the entry with the given DN.  This will use an
   * in-memory hash if possible, or will go to the database if it's not in
   * cache.  This should only be used for cacheable operations (like getting the
   * entry ID for the parent entry) where the same parent ID is likely to be
   * used multiple times.
   *
   * @param  parentDN  The DN of the parent entry for which to retrieve the
   *                   corresponding entry ID.
   * @param  dn2id     The handle to the dn2id database to use if the parent DN
   *                   isn't found in the local cache.
   * @param  txn       The transaction to use when interacting with the dn2id
   *                   database.
   *
   * @return  The entry ID for the entry with the given DN, or {@code null} if
   *          no such entry exists.
   */
  private EntryID getParentID(DN parentDN, DN2ID dn2id, Transaction txn)
          throws DatabaseException
  {
    EntryID parentID = parentIDMap.get(parentDN);
    if (parentID != null)
    {
      return parentID;
    }

    parentID = dn2id.get(txn, parentDN);
    if (parentID != null)
    {
      if (parentIDMap.size() >= PARENT_ID_MAP_SIZE)
      {
        Iterator<DN> iterator = parentIDMap.keySet().iterator();
        iterator.next();
        iterator.remove();
      }

      parentIDMap.put(parentDN, parentID);
    }

    return parentID;
  }

  /**
   * Get a statistic of the number of keys that reached the entry limit.
   * @return The number of keys that reached the entry limit.
   */
  private int getEntryLimitExceededCount()
  {
    int count = 0;
    for (ImportContext ic : importMap.values())
    {
      count += ic.getEntryContainer().getEntryLimitExceededCount();
    }
    return count;
  }

  /**
   * Method invoked when the given thread terminates due to the given uncaught
   * exception. <p>Any exception thrown by this method will be ignored by the
   * Java Virtual Machine.
   *
   * @param t the thread
   * @param e the exception
   */
  public void uncaughtException(Thread t, Throwable e)
  {
    threads.remove(t);
    Message msg = ERR_JEB_IMPORT_THREAD_EXCEPTION.get(
        t.getName(), StaticUtils.stackTraceToSingleLineString(e.getCause()));
    logError(msg);
  }

  /**
   * Determine the appropriate import context for an entry.
   *
   * @param dn The DN of an entry
   * @return The import context.
   * @throws DirectoryException If the entry DN does not match any
   *                            of the base DNs.
   */
  private ImportContext getImportConfig(DN dn) throws DirectoryException
  {
    ImportContext importContext = null;
    DN nodeDN = dn;

    while (importContext == null && nodeDN != null)
    {
      importContext = importMap.get(nodeDN);
      if (importContext == null)
      {
        nodeDN = nodeDN.getParentDNInSuffix();
      }
    }

    if (nodeDN == null)
    {
      // The entry should not have been given to this backend.
      Message message =
              JebMessages.ERR_JEB_INCORRECT_ROUTING.get(String.valueOf(dn));
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message);
    }

    return importContext;
  }

  private ImportContext getImportContext(EntryContainer entryContainer,
                                         long bufferSize)
      throws DatabaseException, JebException, ConfigException
  {
    DN baseDN = entryContainer.getBaseDN();
    EntryContainer srcEntryContainer = null;
    List<DN> includeBranches = new ArrayList<DN>();
    List<DN> excludeBranches = new ArrayList<DN>();

    if(!ldifImportConfig.appendToExistingData() &&
        !ldifImportConfig.clearBackend())
    {
      for(DN dn : ldifImportConfig.getExcludeBranches())
      {
        if(baseDN.equals(dn))
        {
          // This entire base DN was explicitly excluded. Skip.
          return null;
        }
        if(baseDN.isAncestorOf(dn))
        {
          excludeBranches.add(dn);
        }
      }

      if(!ldifImportConfig.getIncludeBranches().isEmpty())
      {
        for(DN dn : ldifImportConfig.getIncludeBranches())
        {
          if(baseDN.isAncestorOf(dn))
          {
            includeBranches.add(dn);
          }
        }

        if(includeBranches.isEmpty())
        {
          // There are no branches in the explicitly defined include list under
          // this base DN. Skip this base DN alltogether.

          return null;
        }

        // Remove any overlapping include branches.
        Iterator<DN> includeBranchIterator = includeBranches.iterator();
        while(includeBranchIterator.hasNext())
        {
          DN includeDN = includeBranchIterator.next();
          boolean keep = true;
          for(DN dn : includeBranches)
          {
            if(!dn.equals(includeDN) && dn.isAncestorOf(includeDN))
            {
              keep = false;
              break;
            }
          }
          if(!keep)
          {
            includeBranchIterator.remove();
          }
        }

        // Remvoe any exclude branches that are not are not under a include
        // branch since they will be migrated as part of the existing entries
        // outside of the include branches anyways.
        Iterator<DN> excludeBranchIterator = excludeBranches.iterator();
        while(excludeBranchIterator.hasNext())
        {
          DN excludeDN = excludeBranchIterator.next();
          boolean keep = false;
          for(DN includeDN : includeBranches)
          {
            if(includeDN.isAncestorOf(excludeDN))
            {
              keep = true;
              break;
            }
          }
          if(!keep)
          {
            excludeBranchIterator.remove();
          }
        }

        if(includeBranches.size() == 1 && excludeBranches.size() == 0 &&
            includeBranches.get(0).equals(baseDN))
        {
          // This entire base DN is explicitly included in the import with
          // no exclude branches that we need to migrate. Just clear the entry
          // container.
          entryContainer.exclusiveLock.lock();
          entryContainer.clear();
          entryContainer.exclusiveLock.unlock();
        }
        else
        {
          // Create a temp entry container
          srcEntryContainer = entryContainer;
          entryContainer =
              rootContainer.openEntryContainer(baseDN,
                                               baseDN.toNormalizedString() +
                                                   "_importTmp");
        }
      }
    }

    // Create an import context.
    ImportContext importContext = new ImportContext();
    importContext.setBufferSize(bufferSize);
    importContext.setConfig(config);
    importContext.setLDIFImportConfig(this.ldifImportConfig);
    importContext.setLDIFReader(reader);

    importContext.setBaseDN(baseDN);
    importContext.setEntryContainer(entryContainer);
    importContext.setSrcEntryContainer(srcEntryContainer);
    importContext.setBufferSize(bufferSize);

    // Create an entry queue.
    LinkedBlockingQueue<Entry> queue =
        new LinkedBlockingQueue<Entry>(config.getImportQueueSize());
    importContext.setQueue(queue);

    // Set the include and exclude branches
    importContext.setIncludeBranches(includeBranches);
    importContext.setExcludeBranches(excludeBranches);

    return importContext;
  }

  /**
   * This class reports progress of the import job at fixed intervals.
   */
  class ProgressTask extends TimerTask
  {
    /**
     * The number of entries that had been read at the time of the
     * previous progress report.
     */
    private long previousCount = 0;

    /**
     * The time in milliseconds of the previous progress report.
     */
    private long previousTime;

    /**
     * The environment statistics at the time of the previous report.
     */
    private EnvironmentStats prevEnvStats;

    /**
     * The number of bytes in a megabyte.
     * Note that 1024*1024 bytes may eventually become known as a mebibyte(MiB).
     */
    private static final int bytesPerMegabyte = 1024*1024;

    /**
     * Create a new import progress task.
     * @throws DatabaseException If an error occurs in the JE database.
     */
    public ProgressTask() throws DatabaseException
    {
      previousTime = System.currentTimeMillis();
      prevEnvStats = rootContainer.getEnvironmentStats(new StatsConfig());
    }

    /**
     * The action to be performed by this timer task.
     */
    public void run()
    {
      long latestCount = reader.getEntriesRead() + migratedCount;
      long deltaCount = (latestCount - previousCount);
      long latestTime = System.currentTimeMillis();
      long deltaTime = latestTime - previousTime;

      if (deltaTime == 0)
      {
        return;
      }

      long numRead     = reader.getEntriesRead();
      long numIgnored  = reader.getEntriesIgnored();
      long numRejected = reader.getEntriesRejected();
      float rate = 1000f*deltaCount / deltaTime;

      Message message = INFO_JEB_IMPORT_PROGRESS_REPORT.get(
          numRead, numIgnored, numRejected, migratedCount, rate);
      logError(message);

      try
      {
        Runtime runtime = Runtime.getRuntime();
        long freeMemory = runtime.freeMemory() / bytesPerMegabyte;

        EnvironmentStats envStats =
            rootContainer.getEnvironmentStats(new StatsConfig());
        long nCacheMiss =
            envStats.getNCacheMiss() - prevEnvStats.getNCacheMiss();

        float cacheMissRate = 0;
        if (deltaCount > 0)
        {
          cacheMissRate = nCacheMiss/(float)deltaCount;
        }

        message = INFO_JEB_IMPORT_CACHE_AND_MEMORY_REPORT.get(
            freeMemory, cacheMissRate);
        logError(message);

        prevEnvStats = envStats;
      }
      catch (DatabaseException e)
      {
        // Unlikely to happen and not critical.
      }


      previousCount = latestCount;
      previousTime = latestTime;
    }
  }

}
