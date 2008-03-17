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
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.server.backends.jeb.importLDIF;

import org.opends.server.types.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.ErrorLogger.logError;
import org.opends.server.admin.std.server.LocalDBBackendCfg;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.LDIFException;
import org.opends.server.util.RuntimeInformation;
import static org.opends.server.util.DynamicConstants.BUILD_ID;
import static org.opends.server.util.DynamicConstants.REVISION_NUMBER;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.backends.jeb.*;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.messages.Message;
import org.opends.messages.JebMessages;
import static org.opends.messages.JebMessages.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.*;
import java.io.IOException;

import com.sleepycat.je.*;

/**
 * Performs a LDIF import.
 */

public class Importer implements Thread.UncaughtExceptionHandler {


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
  private LinkedHashMap<DN, DNContext> importMap =
      new LinkedHashMap<DN, DNContext>();


  /**
    * The number of entries migrated.
    */
   private int migratedCount;

  /**
   * The number of entries imported.
   */
  private int importedCount;

  /**
   * The number of milliseconds between job progress reports.
   */
  private long progressInterval = 10000;

  /**
   * The progress report timer.
   */
  private Timer timer;

  //Thread array.
  private CopyOnWriteArrayList<WorkThread> threads;

  //Progress task.
  private ProgressTask pTask;

  //Number of entries import before checking if cleaning is needed after
  //eviction has been detected.
  private static final int entryCleanInterval = 250000;

  //Minimum buffer amount to give to a buffer manager.
  private static final long minBuffer = 1024 * 1024;

  //Total available memory for the buffer managers.
  private long totalAvailBufferMemory = 0;

  //Memory size to be used for the DB cache in string format.
  private String dbCacheSizeStr;

  //Used to do an initial clean after eviction has been detected.
  private boolean firstClean=false;

  //A thread threw an Runtime exception stop the import.
  private boolean unCaughtExceptionThrown = false;

  /**
   * Create a new import job with the specified ldif import config.
   *
   * @param ldifImportConfig The LDIF import config.
   */
  public Importer(LDIFImportConfig ldifImportConfig)
  {
    this.ldifImportConfig = ldifImportConfig;
    this.threads = new CopyOnWriteArrayList<WorkThread>();
    calcMemoryLimits();
  }

  /**
   * Start the worker threads.
   *
   * @throws DatabaseException If a DB problem occurs.
   */
  private void startWorkerThreads()
          throws DatabaseException {

    int importThreadCount = config.getImportThreadCount();
    //Figure out how much buffer memory to give to each context.
    int contextCount = importMap.size();
    long memoryPerContext = totalAvailBufferMemory / contextCount;
    //Below min, use the min value.
    if(memoryPerContext < minBuffer) {
      Message msg =
            INFO_JEB_IMPORT_LDIF_BUFFER_CONTEXT_AVAILMEM.get(memoryPerContext,
                                                             minBuffer);
      logError(msg);
      memoryPerContext = minBuffer;
    }
    // Create one set of worker threads/buffer managers for each base DN.
    for (DNContext context : importMap.values()) {
      BufferManager bufferManager = new BufferManager(memoryPerContext,
                                                      importThreadCount);
      context.setBufferManager(bufferManager);
      for (int i = 0; i < importThreadCount; i++) {
        WorkThread t = new WorkThread(context.getWorkQueue(), i,
                                      bufferManager, rootContainer);
        t.setUncaughtExceptionHandler(this);
        threads.add(t);
        t.start();
      }
    }
    // Start a timer for the progress report.
    timer = new Timer();
    TimerTask progressTask = new ProgressTask();
    //Used to get at extra functionality such as eviction detected.
    pTask = (ProgressTask) progressTask;
    timer.scheduleAtFixedRate(progressTask, progressInterval,
                              progressInterval);

  }


  /**
   * Import a ldif using the specified root container.
   *
   * @param rootContainer  The root container.
   * @return A LDIF result.
   * @throws DatabaseException  If a DB error occurs.
   * @throws IOException If a IO error occurs.
   * @throws org.opends.server.backends.jeb.JebException If a JEB error occurs.
   * @throws DirectoryException If a directory error occurs.
   * @throws ConfigException If a configuration has an error.
   */
  public LDIFImportResult processImport(RootContainer rootContainer)
      throws DatabaseException, IOException, JebException, DirectoryException,
            ConfigException {

    // Create an LDIF reader. Throws an exception if the file does not exist.
    reader = new LDIFReader(ldifImportConfig);
    this.rootContainer = rootContainer;
    this.config = rootContainer.getConfiguration();

    Message message;
    long startTime;
    try {
      int importThreadCount = config.getImportThreadCount();
      message = INFO_JEB_IMPORT_STARTING.get(DirectoryServer.getVersionString(),
                                                     BUILD_ID, REVISION_NUMBER);
      logError(message);
      message = INFO_JEB_IMPORT_THREAD_COUNT.get(importThreadCount);
      logError(message);
      RuntimeInformation.logInfo();
      for (EntryContainer entryContainer : rootContainer.getEntryContainers()) {
        DNContext DNContext =  getImportContext(entryContainer);
        if(DNContext != null) {
          importMap.put(entryContainer.getBaseDN(), DNContext);
        }
      }
      // Make a note of the time we started.
      startTime = System.currentTimeMillis();
      startWorkerThreads();
      try {
        importedCount = 0;
        migratedCount = 0;
        migrateExistingEntries();
        processLDIF();
        migrateExcludedEntries();
      } finally {
        if(!unCaughtExceptionThrown) {
          cleanUp();
          switchContainers();
        }
      }
    }
    finally {
      reader.close();
    }
    importProlog(startTime);
    return new LDIFImportResult(reader.getEntriesRead(),
                                reader.getEntriesRejected(),
                                reader.getEntriesIgnored());
  }

  /**
   * Switch containers if the migrated entries were written to the temporary
   * container.
   *
   * @throws DatabaseException If a DB problem occurs.
   * @throws JebException If a JEB problem occurs.
   */
  private void switchContainers() throws DatabaseException, JebException {

    for(DNContext importContext : importMap.values()) {
      DN baseDN = importContext.getBaseDN();
      EntryContainer srcEntryContainer =
              importContext.getSrcEntryContainer();
      if(srcEntryContainer != null) {
        if (debugEnabled()) {
          TRACER.debugInfo("Deleteing old entry container for base DN " +
                  "%s and renaming temp entry container", baseDN);
        }
        EntryContainer unregEC =
                rootContainer.unregisterEntryContainer(baseDN);
        //Make sure the unregistered EC for the base DN is the same as
        //the one in the import context.
        if(unregEC != srcEntryContainer) {
          if(debugEnabled()) {
            TRACER.debugInfo("Current entry container used for base DN " +
                    "%s is not the same as the source entry container used " +
                    "during the migration process.", baseDN);
          }
          rootContainer.registerEntryContainer(baseDN, unregEC);
          continue;
        }
        srcEntryContainer.lock();
        srcEntryContainer.delete();
        srcEntryContainer.unlock();
        EntryContainer newEC = importContext.getEntryContainer();
        newEC.lock();
        newEC.setDatabasePrefix(baseDN.toNormalizedString());
        newEC.unlock();
        rootContainer.registerEntryContainer(baseDN, newEC);
      }
    }
  }

  /**
   * Create and log messages at the end of the successful import.
   *
   * @param startTime The time the import started.
   */
  private void importProlog(long startTime) {
    Message message;
    long finishTime = System.currentTimeMillis();
    long importTime = (finishTime - startTime);

    float rate = 0;
    if (importTime > 0)
    {
      rate = 1000f*importedCount / importTime;
    }

    message = INFO_JEB_IMPORT_FINAL_STATUS.
        get(reader.getEntriesRead(), importedCount,
            reader.getEntriesIgnored(), reader.getEntriesRejected(),
            migratedCount, importTime/1000, rate);
    logError(message);

    message = INFO_JEB_IMPORT_ENTRY_LIMIT_EXCEEDED_COUNT.get(
        getEntryLimitExceededCount());
    logError(message);

  }


  /**
   * Run the cleaner if it is needed.
   *
   * @param entriesRead The number of entries read so far.
   * @param evictEntryNumber The number of entries to run the cleaner after
   * being read.
   * @throws DatabaseException If a DB problem occurs.
   */
  private void
  runCleanerIfNeeded(long entriesRead, long evictEntryNumber)
          throws DatabaseException {
    if(!firstClean || (entriesRead %  evictEntryNumber) == 0) {
      //Make sure work queue is empty before starting.
      drainWorkQueue();
      Message msg = INFO_JEB_IMPORT_LDIF_CLEAN.get();
      runCleaner(msg);
      if(!firstClean) {
        firstClean=true;
      }
    }
  }

  /**
   * Run the cleaner, pausing the task thread output.
   *
   * @param header Message to be printed before cleaning.
   * @throws DatabaseException If a DB problem occurs.
   */
  private void runCleaner(Message header) throws DatabaseException {
    Message msg;
    long startTime = System.currentTimeMillis();
    //Need to force a checkpoint.
    rootContainer.importForceCheckPoint();
    logError(header);
    pTask.setPause(true);
    //Actually clean the files.
    int cleaned = rootContainer.cleanedLogFiles();
    //This checkpoint removes the files if any were cleaned.
    if(cleaned > 0) {
      msg = INFO_JEB_IMPORT_LDIF_CLEANER_REMOVE_LOGS.get(cleaned);
      logError(msg);
      rootContainer.importForceCheckPoint();
    }
    pTask.setPause(false);
    long finishTime = System.currentTimeMillis();
    long cleanTime = (finishTime - startTime) / 1000;
    msg = INFO_JEB_IMPORT_LDIF_CLEANER_RUN_DONE.get(cleanTime, cleaned);
    logError(msg);
  }

  /**
   * Process a LDIF reader.
   *
   * @throws JebException If a JEB problem occurs.
   * @throws DatabaseException If a DB problem occurs.
   * @throws IOException If an IO exception occurs.
   */
  private void
  processLDIF() throws JebException, DatabaseException, IOException {
    Message message = INFO_JEB_IMPORT_LDIF_START.get();
    logError(message);
    do {
      if (ldifImportConfig.isCancelled()) {
        break;
      }
      if(threads.size() <= 0) {
        message = ERR_JEB_IMPORT_NO_WORKER_THREADS.get();
        throw new JebException(message);
      }
      if(unCaughtExceptionThrown) {
        abortImport();
      }
      try {
        // Read the next entry.
        Entry entry = reader.readEntry();
        // Check for end of file.
        if (entry == null) {
          message = INFO_JEB_IMPORT_LDIF_END.get();
          logError(message);

          break;
        }
        // Route it according to base DN.
        DNContext DNContext = getImportConfig(entry.getDN());
        processEntry(DNContext, entry);
        //If the progress task has noticed eviction proceeding, start running
        //the cleaner.
        if(pTask.isEvicting()) {
          runCleanerIfNeeded(reader.getEntriesRead(), entryCleanInterval);
        }
      }  catch (LDIFException e) {
        if (debugEnabled()) {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      } catch (DirectoryException e) {
        if (debugEnabled()) {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      } catch (DatabaseException e)  {
        if (debugEnabled()) {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }
    } while (true);
  }

  /**
   * Process an entry using the specified import context.
   *
   * @param DNContext The import context.
   * @param entry The entry to process.
   */
  private void processEntry(DNContext DNContext, Entry entry) {
    //Add this DN to the pending map.
    DNContext.addPending(entry.getDN());
    addEntryQueue(DNContext, entry);
  }

  /**
   * Add work item to specified import context's queue.
   * @param context The import context.
   * @param item The work item to add.
   * @return <CODE>True</CODE> if the the work  item was added to the queue.
   */
  private boolean
  addQueue(DNContext context, WorkElement item) {
    try {
      while(!context.getWorkQueue().offer(item, 1000,
                                            TimeUnit.MILLISECONDS)) {
        if(threads.size() <= 0) {
          // All worker threads died. We must stop now.
          return false;
        }
      }
    } catch (InterruptedException e) {
      if (debugEnabled()) {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
    }
    return true;
  }


  /**
   * Wait until the work queue is empty.
   */
  private void drainWorkQueue() {
    if(threads.size() > 0) {
      for (DNContext context : importMap.values()) {
        while (context.getWorkQueue().size() > 0) {
          try {
            Thread.sleep(100);
          } catch (Exception e) {
            // No action needed.
          }
        }
      }
    }
  }

  private void abortImport() throws JebException {
    //Stop work threads telling them to skip substring flush.
     stopWorkThreads(false, true);
     timer.cancel();
     Message message = ERR_JEB_IMPORT_LDIF_ABORT.get();
     throw new JebException(message);
  }

  /**
   * Stop work threads.
   *
   * @param flushBuffer Flag telling threads that it should do substring flush.
   * @param abort <CODE>True</CODE> if stop work threads was called from an
   *              abort.
   * @throws JebException if a Jeb error occurs.
   */
  private void
  stopWorkThreads(boolean flushBuffer, boolean abort) throws JebException {
    for (WorkThread t : threads) {
      if(!flushBuffer) {
        t.setFlush(false);
      }
      t.stopProcessing();
    }
    // Wait for each thread to stop.
    for (WorkThread t : threads) {
      try {
        if(!abort && unCaughtExceptionThrown) {
          timer.cancel();
          Message message = ERR_JEB_IMPORT_LDIF_ABORT.get();
          throw new JebException(message);
        }
        t.join();
        importedCount += t.getImportedCount();
      } catch (InterruptedException ie) {
        // No action needed?
      }
    }
  }

  /**
   * Clean up after a successful import.
   *
   * @throws DatabaseException If a DB error occurs.
   * @throws JebException If a Jeb error occurs.
   */
  private void cleanUp() throws DatabaseException, JebException {
     Message msg;
    //Drain the work queue.
    drainWorkQueue();
    //Prepare the buffer managers to flush.
    for(DNContext context : importMap.values()) {
      context.getBufferManager().prepareFlush();
    }
    pTask.setPause(true);
    long startTime = System.currentTimeMillis();
    stopWorkThreads(true, false);
    long finishTime = System.currentTimeMillis();
    long flushTime = (finishTime - startTime) / 1000;
     msg = INFO_JEB_IMPORT_LDIF_BUFFER_FLUSH_COMPLETED.get(flushTime);
    logError(msg);
    timer.cancel();
    for(DNContext context : importMap.values()) {
      context.setIndexesTrusted();
    }
    msg = INFO_JEB_IMPORT_LDIF_FINAL_CLEAN.get();
    //Run the cleaner.
    runCleaner(msg);
  }

  /**
   * Uncaught exception handler.
   *
   * @param t The thread working when the exception was thrown.
   * @param e The exception.
   */
  public void uncaughtException(Thread t, Throwable e) {
     unCaughtExceptionThrown = true;
     threads.remove(t);
     Message msg = ERR_JEB_IMPORT_THREAD_EXCEPTION.get(
         t.getName(), StaticUtils.stackTraceToSingleLineString(e.getCause()));
     logError(msg);
   }

  /**
   * Get the entry limit exceeded counts from the indexes.
   *
   * @return Count of the index with entry limit exceeded values.
   */
  private int getEntryLimitExceededCount() {
    int count = 0;
    for (DNContext ic : importMap.values())
    {
      count += ic.getEntryContainer().getEntryLimitExceededCount();
    }
    return count;
  }

  /**
   * Return an import context related to the specified DN.
   * @param dn The dn.
   * @return  An import context.
   * @throws DirectoryException If an directory error occurs.
   */
  private DNContext getImportConfig(DN dn) throws DirectoryException {
    DNContext DNContext = null;
    DN nodeDN = dn;

    while (DNContext == null && nodeDN != null) {
      DNContext = importMap.get(nodeDN);
      if (DNContext == null)
      {
        nodeDN = nodeDN.getParentDNInSuffix();
      }
    }

    if (nodeDN == null) {
      // The entry should not have been given to this backend.
      Message message =
              JebMessages.ERR_JEB_INCORRECT_ROUTING.get(String.valueOf(dn));
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message);
    }

    return DNContext;
  }

  /**
   * Creates an import context for the specified entry container.
   *
   * @param entryContainer The entry container.
   * @return Import context to use during import.
   * @throws DatabaseException If a database error occurs.
   * @throws JebException If a JEB error occurs.
   * @throws ConfigException If a configuration contains error.
   */
   private DNContext getImportContext(EntryContainer entryContainer)
      throws DatabaseException, JebException, ConfigException {
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
          entryContainer.lock();
          entryContainer.clear();
          entryContainer.unlock();
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
    DNContext DNContext = new DNContext();
    DNContext.setConfig(config);
    DNContext.setLDIFImportConfig(this.ldifImportConfig);
    DNContext.setLDIFReader(reader);

    DNContext.setBaseDN(baseDN);
    DNContext.setEntryContainer(entryContainer);
    DNContext.setSrcEntryContainer(srcEntryContainer);

    //Create queue.
    LinkedBlockingQueue<WorkElement> works =
        new LinkedBlockingQueue<WorkElement>
                     (config.getImportQueueSize());
    DNContext.setWorkQueue(works);

    // Set the include and exclude branches
    DNContext.setIncludeBranches(includeBranches);
    DNContext.setExcludeBranches(excludeBranches);

    return DNContext;
  }

  /**
   * Add specified context and entry to the work queue.
   *
   * @param context The context related to the entry DN.
   * @param entry The entry to work on.
   * @return  <CODE>True</CODE> if the element was added to the work queue.
   */
  private boolean
  addEntryQueue(DNContext context,  Entry entry) {
    WorkElement element =
            WorkElement.decode(entry, context);
    return addQueue(context, element);
  }

  /**
   * Calculate the memory usage for the substring buffer and the DB cache.
   */
  private void calcMemoryLimits() {
    Message msg;
    Runtime runtime = Runtime.getRuntime();
    long freeMemory = runtime.freeMemory();
    long maxMemory = runtime.maxMemory();
    long totMemory = runtime.totalMemory();
    long totFreeMemory = (freeMemory + (maxMemory - totMemory));
    long dbCacheLimit = (totFreeMemory * 40) / 100;
    dbCacheSizeStr = Long.toString(dbCacheLimit);
    totalAvailBufferMemory = (totFreeMemory * 10) / 100;
    if(totalAvailBufferMemory < (10 * minBuffer)) {
       msg =
          INFO_JEB_IMPORT_LDIF_BUFFER_TOT_AVAILMEM.get(totalAvailBufferMemory,
                                                      (10 * minBuffer));
      logError(msg);
      totalAvailBufferMemory = (10 * minBuffer);
    }
    msg=INFO_JEB_IMPORT_LDIF_MEMORY_INFO.get(dbCacheLimit,
                                             totalAvailBufferMemory);
    logError(msg);
  }

  /**
   * Return the string representation of the DB cache size.
   *
   * @return DB cache size string.
   */
  public String getDBCacheSize() {
    return dbCacheSizeStr;
  }

  /**
   * Migrate any existing entries.
   *
   * @throws JebException If a JEB error occurs.
   * @throws DatabaseException  If a DB error occurs.
   * @throws DirectoryException If a directory error occurs.
   */
  private void migrateExistingEntries()
      throws JebException, DatabaseException, DirectoryException {
    for(DNContext context : importMap.values()) {
      EntryContainer srcEntryContainer = context.getSrcEntryContainer();
      if(srcEntryContainer != null &&
          !context.getIncludeBranches().isEmpty()) {
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        LockMode lockMode = LockMode.DEFAULT;
        OperationStatus status;
        Message message = INFO_JEB_IMPORT_MIGRATION_START.get(
            "existing", String.valueOf(context.getBaseDN()));
        logError(message);
        Cursor cursor =
            srcEntryContainer.getDN2ID().openCursor(null,
                                                   CursorConfig.READ_COMMITTED);
        try {
          status = cursor.getFirst(key, data, lockMode);
          while(status == OperationStatus.SUCCESS &&
                !ldifImportConfig.isCancelled()) {
            if(threads.size() <= 0) {
              message = ERR_JEB_IMPORT_NO_WORKER_THREADS.get();
              throw new JebException(message);
            }
            DN dn = DN.decode(new ASN1OctetString(key.getData()));
            if(!context.getIncludeBranches().contains(dn)) {
              EntryID id = new EntryID(data);
              Entry entry = srcEntryContainer.getID2Entry().get(null, id);
              processEntry(context, entry);
              migratedCount++;
              status = cursor.getNext(key, data, lockMode);
            }  else {
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
        } finally {
          cursor.close();
        }
      }
    }
  }


  /**
   * Migrate excluded entries.
   *
   * @throws JebException If a JEB error occurs.
   * @throws DatabaseException  If a DB error occurs.
   * @throws DirectoryException If a directory error occurs.
   */
  private void migrateExcludedEntries()
      throws JebException, DatabaseException, DirectoryException {
    for(DNContext importContext : importMap.values()) {
      EntryContainer srcEntryContainer = importContext.getSrcEntryContainer();
      if(srcEntryContainer != null &&
          !importContext.getExcludeBranches().isEmpty()) {
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
        try {
          for(DN excludedDN : importContext.getExcludeBranches()) {
            byte[] suffix =
                StaticUtils.getBytes(excludedDN.toNormalizedString());
            key.setData(suffix);
            status = cursor.getSearchKeyRange(key, data, lockMode);
            if(status == OperationStatus.SUCCESS &&
                Arrays.equals(key.getData(), suffix)) {
              // This is the base entry for a branch that was excluded in the
              // import so we must migrate all entries in this branch over to
              // the new entry container.
              byte[] end =
                  StaticUtils.getBytes("," + excludedDN.toNormalizedString());
              end[0] = (byte) (end[0] + 1);

              while(status == OperationStatus.SUCCESS &&
                  dn2idComparator.compare(key.getData(), end) < 0 &&
                  !ldifImportConfig.isCancelled()) {
                if(threads.size() <= 0) {
                  message = ERR_JEB_IMPORT_NO_WORKER_THREADS.get();
                  throw new JebException(message);
                }
                EntryID id = new EntryID(data);
                Entry entry = srcEntryContainer.getID2Entry().get(null, id);
                processEntry(importContext, entry);
                migratedCount++;
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
   * This class reports progress of the import job at fixed intervals.
   */
  private final class ProgressTask extends TimerTask
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
    public static final int bytesPerMegabyte = 1024*1024;

    //Determines if the ldif is being read.
    private boolean ldifRead = false;

    //Determines if eviction has been detected.
    private boolean evicting = false;

    //Entry count when eviction was detected.
    private long evictionEntryCount = 0;

    //Suspend output.
    private boolean pause = false;

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
     * Return if reading the LDIF file.
     */
    public void ldifRead() {
      ldifRead=true;
    }

   /**
    * Return value of evicting flag.
    *
    * @return <CODE>True</CODE> if eviction is detected.
    */
    public  boolean isEvicting() {
     return evicting;
    }

    /**
     * Return count of entries when eviction was detected.
     *
     * @return  The entry count when eviction was detected.
     */
    public long getEvictionEntryCount() {
      return evictionEntryCount;
    }

    /**
     * Suspend output if true.
     *
     * @param v The value to set the suspend value to.
     */
    public void setPause(boolean v) {
    pause=v;
   }

    /**
     * The action to be performed by this timer task.
     */
    public void run() {
      long latestCount = reader.getEntriesRead() + 0;
      long deltaCount = (latestCount - previousCount);
      long latestTime = System.currentTimeMillis();
      long deltaTime = latestTime - previousTime;
      Message message;
      if (deltaTime == 0) {
        return;
      }
      if(pause) {
        return;
      }
      if(!ldifRead) {
        long numRead     = reader.getEntriesRead();
        long numIgnored  = reader.getEntriesIgnored();
        long numRejected = reader.getEntriesRejected();
         float rate = 1000f*deltaCount / deltaTime;
         message = INFO_JEB_IMPORT_PROGRESS_REPORT.get(
            numRead, numIgnored, numRejected, 0, rate);
        logError(message);
      }
      try
      {
        Runtime runtime = Runtime.getRuntime();
        long freeMemory = runtime.freeMemory() / bytesPerMegabyte;
        EnvironmentStats envStats =
            rootContainer.getEnvironmentStats(new StatsConfig());
        long nCacheMiss =
            envStats.getNCacheMiss() - prevEnvStats.getNCacheMiss();

        float cacheMissRate = 0;
        if (deltaCount > 0) {
          cacheMissRate = nCacheMiss/(float)deltaCount;
        }
        message = INFO_JEB_IMPORT_CACHE_AND_MEMORY_REPORT.get(
            freeMemory, cacheMissRate);
        logError(message);
        long evictPasses = envStats.getNEvictPasses();
        long evictNodes = envStats.getNNodesExplicitlyEvicted();
        long evictBinsStrip = envStats.getNBINsStripped();
        int cleanerRuns = envStats.getNCleanerRuns();
        int cleanerDeletions = envStats.getNCleanerDeletions();
        int cleanerEntriesRead = envStats.getNCleanerEntriesRead();
        int cleanerINCleaned = envStats.getNINsCleaned();
        int checkPoints = envStats.getNCheckpoints();
        if(evictPasses != 0) {
          if(!evicting) {
            evicting=true;
            if(!ldifRead) {
              evictionEntryCount=reader.getEntriesRead();
              message =
                 INFO_JEB_IMPORT_LDIF_EVICTION_DETECTED.get(evictionEntryCount);
              logError(message);
            }
          }
          message =
                  INFO_JEB_IMPORT_LDIF_EVICTION_DETECTED_STATS.get(evictPasses,
                          evictNodes, evictBinsStrip);
          logError(message);
        }
        if(cleanerRuns != 0) {
          message = INFO_JEB_IMPORT_LDIF_CLEANER_STATS.get(cleanerRuns,
                  cleanerDeletions, cleanerEntriesRead, cleanerINCleaned);
          logError(message);
        }
        if(checkPoints  > 1) {
          message = INFO_JEB_IMPORT_LDIF_BUFFER_CHECKPOINTS.get(checkPoints);
          logError(message);
        }
        prevEnvStats = envStats;
      } catch (DatabaseException e) {
        // Unlikely to happen and not critical.
      }
      previousCount = latestCount;
      previousTime = latestTime;
    }
  }
}

