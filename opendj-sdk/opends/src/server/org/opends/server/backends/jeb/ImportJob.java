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

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.EnvironmentStats;
import com.sleepycat.je.StatsConfig;
import com.sleepycat.je.Transaction;

import org.opends.server.api.Backend;
import org.opends.server.loggers.debug.DebugLogger;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.messages.JebMessages;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.ResultCode;
import org.opends.server.util.LDIFException;
import org.opends.server.util.LDIFReader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;

import static org.opends.server.loggers.debug.DebugLogger.debugCaught;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.messages.JebMessages.
     MSGID_JEB_IMPORT_ENTRY_EXISTS;
import static org.opends.server.messages.MessageHandler.getMessage;
import static org.opends.server.messages.JebMessages.
     MSGID_JEB_IMPORT_PARENT_NOT_FOUND;
import static org.opends.server.loggers.Error.logError;
import static org.opends.server.messages.JebMessages.*;

/**
 * Import from LDIF to a JE backend.
 */
public class ImportJob implements Thread.UncaughtExceptionHandler
{

  /**
   * The backend instance we are importing into.
   */
  private Backend backend;

  /**
   * The JE backend configuration.
   */
  private Config config;

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
  private HashMap<DN,ImportContext> importMap =
       new HashMap<DN, ImportContext>();

  /**
   * The number of entries imported.
   */
  private int importedCount;


  /**
   * The number of milliseconds between job progress reports.
   */
  private long progressInterval = 10000;

  /**
   * Create a new import job.
   *
   * @param backend The backend performing the import.
   * @param config The backend configuration.
   * @param ldifImportConfig The LDIF import configuration.
   */
  public ImportJob(Backend backend, Config config,
                   LDIFImportConfig ldifImportConfig)
  {
    this.backend = backend;
    this.config = config;
    this.ldifImportConfig = ldifImportConfig;
  }

  /**
   * Import from LDIF file to one or more base DNs. Opens the database
   * environment and deletes existing data for each base DN unless we are
   * appending to existing data. Creates a temporary working directory,
   * processes the LDIF file, then merges the resulting intermediate
   * files to load the index databases.
   *
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws IOException  If a problem occurs while opening the LDIF file for
   *                      reading, or while reading from the LDIF file.
   * @throws JebException If an error occurs in the JE backend.
   */
  public void importLDIF()
      throws DatabaseException, IOException, JebException
  {
/*
    envConfig.setConfigParam("je.env.runCleaner", "false");
    envConfig.setConfigParam("je.log.numBuffers", "2");
    envConfig.setConfigParam("je.log.bufferSize", "15000000");
    envConfig.setConfigParam("je.log.totalBufferBytes", "30000000");
    envConfig.setConfigParam("je.log.fileMax", "100000000");
*/

    // Create an LDIF reader. Throws an exception if the file does not exist.
    reader = new LDIFReader(ldifImportConfig);

    int msgID;
    String message;
    long startTime;
    try
    {
      rootContainer = new RootContainer(config, backend);
      if (ldifImportConfig.appendToExistingData())
      {
        rootContainer.open(config.getBackendDirectory(),
                           config.getBackendPermission(),
                           false, true, true, true, true, false);
      }
      else
      {
        rootContainer.open(config.getBackendDirectory(),
                           config.getBackendPermission(),
                           false, true, false, false, false, false);
      }

      if (!ldifImportConfig.appendToExistingData())
      {
        // We have the writer lock on the environment, now delete the
        // environment and re-open it. Only do this when we are
        // importing to all the base DNs in the backend.
        rootContainer.close();
        EnvManager.removeFiles(config.getBackendDirectory().getPath());
        rootContainer.open(config.getBackendDirectory(),
                           config.getBackendPermission(),
                           false, true, false, false, false, false);
      }

      // Divide the total buffer size by the number of threads
      // and give that much to each thread.
      int importThreadCount = config.getImportThreadCount();
      long bufferSize = config.getImportBufferSize() /
           (importThreadCount*config.getBaseDNs().length);

      msgID = MSGID_JEB_IMPORT_THREAD_COUNT;
      message = getMessage(msgID, importThreadCount);
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE,
               message, msgID);

      msgID = MSGID_JEB_IMPORT_BUFFER_SIZE;
      message = getMessage(msgID, bufferSize);
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE,
               message, msgID);

      msgID = MSGID_JEB_IMPORT_ENVIRONMENT_CONFIG;
      message = getMessage(msgID,
                           rootContainer.getEnvironmentConfig().toString());
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE,
               message, msgID);

      DebugLogger.debugInfo(
        rootContainer.getEnvironmentConfig().toString());


      rootContainer.openEntryContainers(config.getBaseDNs());

      // Create the import contexts for each base DN.
      DN baseDN;

      for (EntryContainer entryContainer : rootContainer.getEntryContainers())
      {
        baseDN = entryContainer.getBaseDN();

        // Create an import context.
        ImportContext importContext = new ImportContext();
        importContext.setBufferSize(bufferSize);
        importContext.setConfig(config);
        importContext.setLDIFImportConfig(this.ldifImportConfig);
        importContext.setLDIFReader(reader);

        importContext.setBaseDN(baseDN);
        importContext.setContainerName(entryContainer.getContainerName());
        importContext.setEntryContainer(entryContainer);
        importContext.setBufferSize(bufferSize);

        // Create an entry queue.
        LinkedBlockingQueue<Entry> queue =
             new LinkedBlockingQueue<Entry>(config.getImportQueueSize());
        importContext.setQueue(queue);

        importMap.put(baseDN, importContext);
      }

      // Make a note of the time we started.
      startTime = System.currentTimeMillis();

      try
      {
        // Create a temporary work directory.
        File tempDir = new File(config.getImportTempDirectory());
        tempDir.mkdir();
        if (tempDir.listFiles() != null)
        {
          for (File f : tempDir.listFiles())
          {
            f.delete();
          }
        }

        try
        {
          importedCount = 0;
          int     passNumber = 1;
          boolean moreData   = true;
          while (moreData)
          {
            moreData = processLDIF();
            if (moreData)
            {
              msgID = MSGID_JEB_IMPORT_BEGINNING_INTERMEDIATE_MERGE;
              message = getMessage(msgID, passNumber++);
              logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE,
                       message, msgID);
            }
            else
            {
              msgID = MSGID_JEB_IMPORT_BEGINNING_FINAL_MERGE;
              message = getMessage(msgID);
              logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE,
                       message, msgID);
            }


            long mergeStartTime = System.currentTimeMillis();
            merge();
            long mergeEndTime = System.currentTimeMillis();

            if (moreData)
            {
              msgID = MSGID_JEB_IMPORT_RESUMING_LDIF_PROCESSING;
              message = getMessage(msgID, ((mergeEndTime-mergeStartTime)/1000));
              logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE,
                       message, msgID);
            }
            else
            {
              msgID = MSGID_JEB_IMPORT_FINAL_MERGE_COMPLETED;
              message = getMessage(msgID, ((mergeEndTime-mergeStartTime)/1000));
              logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE,
                       message, msgID);
            }
          }
        }
        finally
        {
          tempDir.delete();
        }
      }
      finally
      {
        rootContainer.close();

        // Sync the environment to disk.
        msgID = MSGID_JEB_IMPORT_CLOSING_DATABASE;
        message = getMessage(msgID);
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE,
                 message, msgID);
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

    msgID = MSGID_JEB_IMPORT_FINAL_STATUS;
    message = getMessage(msgID, reader.getEntriesRead(), importedCount,
                         reader.getEntriesIgnored(),
                         reader.getEntriesRejected(), importTime/1000, rate);
    logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE,
             message, msgID);

    msgID = MSGID_JEB_IMPORT_ENTRY_LIMIT_EXCEEDED_COUNT;
    message = getMessage(msgID, getEntryLimitExceededCount());
    logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE,
             message, msgID);
  }

  /**
   * Merge the intermediate files to load the index databases.
   */
  public void merge()
  {
    Map<AttributeType,IndexConfig>
         indexConfigs = config.getIndexConfigMap();
    ArrayList<IndexMergeThread> mergers = new ArrayList<IndexMergeThread>();

    // Create merge threads for each base DN.
    for (ImportContext importContext : importMap.values())
    {
      String containerName = importContext.getContainerName();
      EntryContainer entryContainer = importContext.getEntryContainer();

      // For each configured attribute index.
      for (IndexConfig indexConfig : indexConfigs.values())
      {
        AttributeIndex attrIndex =
             entryContainer.getAttributeIndex(indexConfig.getAttributeType());
        if (indexConfig.isEqualityIndex())
        {
          Index index = attrIndex.equalityIndex;
          String name = containerName + "_" + index.toString();
          IndexMergeThread indexMergeThread =
               new IndexMergeThread(name, config, ldifImportConfig, index,
                                    indexConfig.getEqualityEntryLimit());
          mergers.add(indexMergeThread);
        }
        if (indexConfig.isPresenceIndex())
        {
          Index index = attrIndex.presenceIndex;
          String name = containerName + "_" + index.toString();
          IndexMergeThread indexMergeThread =
               new IndexMergeThread(name, config, ldifImportConfig, index,
                                    indexConfig.getPresenceEntryLimit());
          mergers.add(indexMergeThread);
        }
        if (indexConfig.isSubstringIndex())
        {
          Index index = attrIndex.substringIndex;
          String name = containerName + "_" + index.toString();
          IndexMergeThread indexMergeThread =
               new IndexMergeThread(name, config, ldifImportConfig, index,
                                    indexConfig.getSubstringEntryLimit());
          mergers.add(indexMergeThread);
        }
        if (indexConfig.isOrderingIndex())
        {
          Index index = attrIndex.orderingIndex;
          String name = containerName + "_" + index.toString();
          IndexMergeThread indexMergeThread =
               new IndexMergeThread(name, config, ldifImportConfig, index,
                                    indexConfig.getEqualityEntryLimit());
          mergers.add(indexMergeThread);
        }
        if (indexConfig.isApproximateIndex())
        {
          Index index = attrIndex.approximateIndex;
          String name = containerName + "_" + index.toString();
          IndexMergeThread indexMergeThread =
              new IndexMergeThread(name, config, ldifImportConfig, index,
                                   indexConfig.getEqualityEntryLimit());
          mergers.add(indexMergeThread);
        }
      }

      // Id2Children index.
      Index id2Children = entryContainer.getID2Children();
      String name = containerName + "_" + id2Children.toString();
      IndexMergeThread indexMergeThread =
           new IndexMergeThread(name, config, ldifImportConfig,
                                id2Children,
                                config.getBackendIndexEntryLimit());
      mergers.add(indexMergeThread);

      // Id2Subtree index.
      Index id2Subtree = entryContainer.getID2Subtree();
      name = containerName + "_" + id2Subtree.toString();
      indexMergeThread =
           new IndexMergeThread(name, config, ldifImportConfig,
                           id2Subtree,
                           config.getBackendIndexEntryLimit());
      mergers.add(indexMergeThread);
    }

    // Run all the merge threads.
    for (IndexMergeThread imt : mergers)
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
          debugCaught(DebugLogLevel.ERROR, e);
        }
      }
    }
  }

  /**
   * Create a set of worker threads, one set for each base DN.
   * Read each entry from the LDIF and determine which
   * base DN the entry belongs to. Write the dn2id database, then put the
   * entry on the appropriate queue for the worker threads to consume.
   * Record the entry count for each base DN when all entries have been
   * processed.
   *
   * @return true if thre is more data to be read from the LDIF file (the import
   * pass size was reached), false if the entire LDIF file has been read.
   *
   * @throws JebException If an error occurs in the JE backend.
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws  IOException  If a problem occurs while opening the LDIF file for
   *                       reading, or while reading from the LDIF file.
   */
  private boolean processLDIF()
          throws JebException, DatabaseException, IOException
  {
    boolean moreData = false;

    ArrayList<ImportThread> threads;

    // Create one set of worker threads for each base DN.
    int importThreadCount = config.getImportThreadCount();
    threads = new ArrayList<ImportThread>(importThreadCount*importMap.size());
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

    try
    {
      // Create a counter to use to determine whether we've hit the import
      // pass size.
      int entriesProcessed = 0;
      int importPassSize   = config.getImportPassSize();
      if (importPassSize <= 0)
      {
        importPassSize = Integer.MAX_VALUE;
      }

      // Start a timer for the progress report.
      Timer timer = new Timer();
      TimerTask progressTask = new ImportJob.ProgressTask();
      timer.scheduleAtFixedRate(progressTask, progressInterval,
                                progressInterval);

      try
      {
        do
        {
          try
          {
            // Read the next entry.
            Entry entry = reader.readEntry();

            // Check for end of file.
            if (entry == null)
            {
              break;
            }

            // Route it according to base DN.
            ImportContext importContext = getImportConfig(entry.getDN());

            processEntry(importContext, entry);

            entriesProcessed++;
            if (entriesProcessed >= importPassSize)
            {
              moreData = true;
              break;
            }
          }
          catch (LDIFException e)
          {
            if (debugEnabled())
            {
              debugCaught(DebugLogLevel.ERROR, e);
            }
          }
          catch (DirectoryException e)
          {
            if (debugEnabled())
            {
              debugCaught(DebugLogLevel.ERROR, e);
            }
          }
        } while (true);

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
      finally
      {
        timer.cancel();
      }
    }
    finally
    {
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
    }


    return moreData;
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
              debugCaught(DebugLogLevel.ERROR, e);
            }
          }
        }
        else
        {
          // Reject the entry.
          int msgID = MSGID_JEB_IMPORT_ENTRY_EXISTS;
          String msg = getMessage(msgID);
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
          parentID = dn2id.get(txn, parentDN);
          if (parentID == null)
          {
            // Reject the entry.
            int msgID = MSGID_JEB_IMPORT_PARENT_NOT_FOUND;
            String msg = getMessage(msgID, parentDN.toString());
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
          importContext.getQueue().put(entry);
        }
        catch (InterruptedException e)
        {
          if (debugEnabled())
          {
            debugCaught(DebugLogLevel.ERROR, e);
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
    e.printStackTrace();
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
      String message = getMessage(JebMessages.MSGID_JEB_INCORRECT_ROUTING,
                                  String.valueOf(dn));
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message,
                                   JebMessages.MSGID_JEB_INCORRECT_ROUTING);
    }

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
      long latestCount = reader.getEntriesRead();
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

      int msgID = MSGID_JEB_IMPORT_PROGRESS_REPORT;
      String message = getMessage(msgID, numRead, numIgnored, numRejected,
                                  rate);
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE,
               message, msgID);

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

        msgID = MSGID_JEB_IMPORT_CACHE_AND_MEMORY_REPORT;
        message = getMessage(msgID, freeMemory, cacheMissRate);
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE,
                 message, msgID);

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
