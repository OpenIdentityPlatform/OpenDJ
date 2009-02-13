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

package org.opends.server.backends.ndb.importLDIF;

import org.opends.server.types.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.ErrorLogger.logError;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.LDIFException;
import org.opends.server.util.RuntimeInformation;
import static org.opends.server.util.DynamicConstants.BUILD_ID;
import static org.opends.server.util.DynamicConstants.REVISION_NUMBER;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.backends.ndb.*;
import org.opends.messages.Message;
import org.opends.messages.NdbMessages;
import static org.opends.messages.NdbMessages.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.*;
import java.io.IOException;
import org.opends.server.admin.std.server.NdbBackendCfg;

/**
 * Performs a LDIF import.
 */

public class Importer implements Thread.UncaughtExceptionHandler {


  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * The NDB backend configuration.
   */
  private NdbBackendCfg config;

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

  // Thread array.
  private CopyOnWriteArrayList<WorkThread> threads;

  // Progress task.
  private ProgressTask pTask;

  // A thread threw an Runtime exception stop the import.
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
  }

  /**
   * Start the worker threads.
   */
  private void startWorkerThreads() {

    int importThreadCount = config.getImportThreadCount();

    // Create one set of worker threads/buffer managers for each base DN.
    for (DNContext context : importMap.values()) {
      for (int i = 0; i < importThreadCount; i++) {
        WorkThread t =
          new WorkThread(context.getWorkQueue(), i, rootContainer);
        t.setUncaughtExceptionHandler(this);
        threads.add(t);
        t.start();
      }
    }
    // Start a timer for the progress report.
    timer = new Timer();
    TimerTask progressTask = new ProgressTask();
    pTask = (ProgressTask) progressTask;
    timer.scheduleAtFixedRate(progressTask, progressInterval,
                              progressInterval);
  }


  /**
   * Import a ldif using the specified root container.
   *
   * @param rootContainer  The root container.
   * @return A LDIF result.
   * @throws IOException If a IO error occurs.
   * @throws NDBException If a NDB error occurs.
   * @throws ConfigException If a configuration has an error.
   */
  public LDIFImportResult processImport(RootContainer rootContainer)
    throws IOException, ConfigException, NDBException {

    // Create an LDIF reader. Throws an exception if the file does not exist.
    reader = new LDIFReader(ldifImportConfig);
    this.rootContainer = rootContainer;
    this.config = rootContainer.getConfiguration();

    Message message;
    long startTime;
    try {
      int importThreadCount = config.getImportThreadCount();
      message = NOTE_NDB_IMPORT_STARTING.get(DirectoryServer.getVersionString(),
                                                     BUILD_ID, REVISION_NUMBER);
      logError(message);
      message = NOTE_NDB_IMPORT_THREAD_COUNT.get(importThreadCount);
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
        processLDIF();
      } finally {
        if(!unCaughtExceptionThrown) {
          cleanUp();
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

    message = NOTE_NDB_IMPORT_FINAL_STATUS.
        get(reader.getEntriesRead(), importedCount,
            reader.getEntriesIgnored(), reader.getEntriesRejected(),
            migratedCount, importTime/1000, rate);
    logError(message);
  }


  /**
   * Process a LDIF reader.
   *
   * @throws NDBException If a NDB problem occurs.
   */
  private void
  processLDIF() throws NDBException {
    Message message = NOTE_NDB_IMPORT_LDIF_START.get();
    logError(message);
    do {
      if (ldifImportConfig.isCancelled()) {
        break;
      }
      if(threads.size() <= 0) {
        message = ERR_NDB_IMPORT_NO_WORKER_THREADS.get();
        throw new NDBException(message);
      }
      if(unCaughtExceptionThrown) {
        abortImport();
      }
      try {
        // Read the next entry.
        Entry entry = reader.readEntry();
        // Check for end of file.
        if (entry == null) {
          message = NOTE_NDB_IMPORT_LDIF_END.get();
          logError(message);

          break;
        }
        // Route it according to base DN.
        DNContext DNContext = getImportConfig(entry.getDN());
        processEntry(DNContext, entry);
      }  catch (LDIFException e) {
        if (debugEnabled()) {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      } catch (DirectoryException e) {
        if (debugEnabled()) {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      } catch (Exception e)  {
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

  /**
   * Abort import.
   * @throws org.opends.server.backends.ndb.NDBException
   */
  private void abortImport() throws NDBException {
     // Stop work threads telling them to skip substring flush.
     stopWorkThreads(false);
     timer.cancel();
     Message message = ERR_NDB_IMPORT_LDIF_ABORT.get();
     throw new NDBException(message);
  }

  /**
   * Stop work threads.
   *
   * @param abort <CODE>True</CODE> if stop work threads was called from an
   *              abort.
   * @throws NDBException if a NDB error occurs.
   */
  private void
  stopWorkThreads(boolean abort) throws NDBException {
    for (WorkThread t : threads) {
      t.stopProcessing();
    }
    // Wait for each thread to stop.
    for (WorkThread t : threads) {
      try {
        if(!abort && unCaughtExceptionThrown) {
          timer.cancel();
          Message message = ERR_NDB_IMPORT_LDIF_ABORT.get();
          throw new NDBException(message);
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
   * @throws NDBException If a NDB error occurs.
   */
  private void cleanUp() throws NDBException {
    // Drain the work queue.
    drainWorkQueue();
    pTask.setPause(true);
    stopWorkThreads(true);
    timer.cancel();
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
     Message msg = ERR_NDB_IMPORT_THREAD_EXCEPTION.get(
         t.getName(), StaticUtils.stackTraceToSingleLineString(e.getCause()));
     logError(msg);
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
              NdbMessages.ERR_NDB_INCORRECT_ROUTING.get(String.valueOf(dn));
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message);
    }

    return DNContext;
  }

  /**
   * Creates an import context for the specified entry container.
   *
   * @param entryContainer The entry container.
   * @return Import context to use during import.
   * @throws ConfigException If a configuration contains error.
   */
   private DNContext getImportContext(EntryContainer entryContainer)
      throws ConfigException {
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

    // Create queue.
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
     * The number of bytes in a megabyte.
     * Note that 1024*1024 bytes may eventually become known as a mebibyte(MiB).
     */
    public static final int bytesPerMegabyte = 1024*1024;

    // Determines if the ldif is being read.
    private boolean ldifRead = false;

    // Suspend output.
    private boolean pause = false;

    /**
     * Create a new import progress task.
     */
    public ProgressTask()
    {
      previousTime = System.currentTimeMillis();
    }

    /**
     * Return if reading the LDIF file.
     */
    public void ldifRead() {
      ldifRead = true;
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
        message = NOTE_NDB_IMPORT_PROGRESS_REPORT.get(
            numRead, numIgnored, numRejected, 0, rate);
        logError(message);
      }
      previousCount = latestCount;
      previousTime = latestTime;
    }
  }
}
