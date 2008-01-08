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
 *      Portions Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.server.extensions;
import org.opends.messages.Message;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import org.opends.server.api.Backend;
import org.opends.server.api.DirectoryThread;
import org.opends.server.api.ServerShutdownListener;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DN;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.loggers.debug.DebugTracer;

import org.opends.server.types.LockManager;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.messages.ExtensionMessages.*;

/**
 * This class defines a utility that will be used to pre-load the Directory
 * Server entry cache.  Pre-loader is multi-threaded and consist of the
 * following threads:
 *
 * - The Arbiter thread which monitors overall pre-load progress and manages
 *   pre-load worker threads by adding or removing them as deemed necessary.
 *
 * - The Collector thread which collects all entry DNs stored within every
 *   configured and active backend to a shared object workers consume from.
 *
 * - Worker threads which are responsible for monitoring the collector feed
 *   and requesting the actual entries for retrieval and in cache storage.
 *
 * This implementation is entry cache and backend independent and can be
 * used to pre-load from any backend to any entry cache as long as both
 * are capable of initiating and sustaining such pre-load activity.
 *
 * This implementation is fully synchronized and safe to use with the server
 * online which pre-load activities going in parallel with server operations.
 *
 * This implementation is self-adjusting to any system workload and does not
 * require any configuration parameters to optimize for initial system
 * resources availability and/or any subsequent fluctuations.
 */
public class EntryCachePreloader
  extends DirectoryThread
  implements ServerShutdownListener
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * Interrupt flag for the arbiter to terminate worker threads.
   */
  private AtomicBoolean interruptFlag = new AtomicBoolean(false);

  /**
   * Processed entries counter.
   */
  private AtomicLong processedEntries = new AtomicLong(0);

  /**
   * Progress report resolution.
   */
  private static final long progressInterval = 5000;

  /**
   * Default arbiter resolution time.
   */
  public static final long
    PRELOAD_ARBITER_DEFAULT_SLEEP_TIME = 1000;

  /**
   * Effective arbiter resolution time.
   */
  private static long arbiterSleepTime;

  /**
   * Pre-load arbiter thread name.
   */
  private String preloadArbiterThreadName;

  /**
   * Pre-load arbiter thread.
   */
  private Thread preloadArbiterThread;

  /**
   * Worker threads.
   */
  private List<Thread> preloadThreads =
    Collections.synchronizedList(
    new LinkedList<Thread>());

  /**
   * DN Collector thread.
   */
  private EntryCacheDNCollector dnCollector =
    new EntryCacheDNCollector();

  /**
   * This queue is for workers to take from.
   */
  private LinkedBlockingQueue<DN> dnQueue =
      new LinkedBlockingQueue<DN>();

  /**
   * The number of bytes in a megabyte.
   */
  private static final int bytesPerMegabyte = 1024*1024;

  /**
   * Default constructor.
   */
  public EntryCachePreloader() {
    super("Entry Cache Preload Arbiter");
    preloadArbiterThreadName = getName();
    DirectoryServer.registerShutdownListener(this);
    // This should not be exposed as configuration
    // parameter and is only useful for testing.
    arbiterSleepTime = Long.getLong(
      "org.opends.server.entrycache.preload.sleep",
      PRELOAD_ARBITER_DEFAULT_SLEEP_TIME);
  }

  /**
   * The Arbiter thread.
   */
  @Override
  public void run() {
    preloadArbiterThread = Thread.currentThread();
    logError(NOTE_CACHE_PRELOAD_PROGRESS_START.get());
    // Start DN collector thread first.
    dnCollector.start();
    // Kick off a single worker.
    EntryCachePreloadWorker singleWorkerThread =
      new EntryCachePreloadWorker();
    singleWorkerThread.start();
    preloadThreads.add(singleWorkerThread);
    // Progress report timer task.
    Timer timer = new Timer();
    TimerTask progressTask = new TimerTask() {
      // Persistent state restore progress report.
      public void run() {
        if (processedEntries.get() > 0) {
          long freeMemory =
            Runtime.getRuntime().freeMemory() / bytesPerMegabyte;
          Message message = NOTE_CACHE_PRELOAD_PROGRESS_REPORT.get(
            processedEntries.get(), freeMemory);
          logError(message);
        }
      }
    };
    timer.scheduleAtFixedRate(progressTask, progressInterval,
      progressInterval);
    // Cycle to monitor progress and adjust workers.
    long processedEntriesDeltaLow  = 0;
    long processedEntriesDeltaHigh = 0;
    long lastKnownProcessedEntries = 0;
    try {
      while (!dnQueue.isEmpty() || dnCollector.isAlive()) {
        long processedEntriesCycle = processedEntries.get();
        long processedEntriesDelta =
          processedEntriesCycle - lastKnownProcessedEntries;
        lastKnownProcessedEntries = processedEntriesCycle;
        // Spawn another worker if scaling up.
        if (processedEntriesDelta > processedEntriesDeltaHigh) {
          processedEntriesDeltaLow = processedEntriesDeltaHigh;
          processedEntriesDeltaHigh = processedEntriesDelta;
          EntryCachePreloadWorker workerThread =
            new EntryCachePreloadWorker();
          workerThread.start();
          preloadThreads.add(workerThread);
        }
        // Interrupt random worker if scaling down.
        if (processedEntriesDelta < processedEntriesDeltaLow) {
          processedEntriesDeltaHigh = processedEntriesDeltaLow;
          processedEntriesDeltaLow = processedEntriesDelta;
          // Leave at least one worker to progress.
          if (preloadThreads.size() > 1) {
            interruptFlag.set(true);
          }
        }
        Thread.sleep(arbiterSleepTime);
      }
      // Join the collector.
      dnCollector.join();
      // Join all spawned workers.
      for (Thread workerThread : preloadThreads) {
        workerThread.join();
      }
      // Cancel progress report task and report done.
      timer.cancel();
      Message message = NOTE_CACHE_PRELOAD_PROGRESS_DONE.get(
        processedEntries.get());
      logError(message);
    } catch (InterruptedException ex) {
      if (debugEnabled()) {
        TRACER.debugCaught(DebugLogLevel.ERROR, ex);
      }
      // Interrupt the collector.
      dnCollector.interrupt();
      // Interrupt all preload threads.
      for (Thread thread : preloadThreads) {
        thread.interrupt();
      }
      logError(WARN_CACHE_PRELOAD_INTERRUPTED.get());
    } finally {
      // Kill the task in case of exception.
      timer.cancel();
    }
  }

  /**
   * The worker thread.
   */
  private class EntryCachePreloadWorker extends DirectoryThread {
    public EntryCachePreloadWorker() {
      super("Entry Cache Preload Worker");
    }
    @Override
    public void run() {
      while (!dnQueue.isEmpty() || dnCollector.isAlive()) {
        // Check if interrupted.
        if (Thread.interrupted()) {
          break;
        }
        if (interruptFlag.compareAndSet(true, false)) {
          break;
        }
        // Dequeue the next entry DN.
        try {
          DN entryDN = dnQueue.take();
          Lock readLock = null;
          try {
            // Acquire a read lock on the entry.
            readLock = LockManager.lockRead(entryDN);
            if (readLock == null) {
              // It is cheaper to put this DN back on the
              // queue then pick it up and process later.
              dnQueue.add(entryDN);
              continue;
            }
            // Even if getEntry() below fails the entry is
            // still treated as a processed entry anyways.
            processedEntries.getAndIncrement();
            // getEntry() will trigger putEntryIfAbsent() to the
            // cache if given entry is not in the cache already.
            DirectoryServer.getEntry(entryDN);
          } catch (DirectoryException ex) {
            if (debugEnabled()) {
              TRACER.debugCaught(DebugLogLevel.ERROR, ex);
            }
            Message message = ERR_CACHE_PRELOAD_ENTRY_FAILED.get(
              entryDN.toNormalizedString(),
              (ex.getCause() != null ? ex.getCause().getMessage() :
                stackTraceToSingleLineString(ex)));
            logError(message);
          } finally {
            LockManager.unlock(entryDN, readLock);
          }
        } catch (InterruptedException ex) {
          break;
        }
      }
      preloadThreads.remove(Thread.currentThread());
    }
  }

  /**
   * The Collector thread.
   */
  private class EntryCacheDNCollector extends DirectoryThread {
    public EntryCacheDNCollector() {
      super("Entry Cache Preload Collector");
    }
    @Override
    public void run() {
      Map<DN, Backend> baseDNMap =
        DirectoryServer.getPublicNamingContexts();
      Set<Backend> proccessedBackends = new HashSet<Backend>();
      // Collect all DNs from every active public backend.
      for (Backend backend : baseDNMap.values()) {
        // Check if interrupted.
        if (Thread.interrupted()) {
          return;
        }
        if (!proccessedBackends.contains(backend)) {
          proccessedBackends.add(backend);
          try {
            if (!backend.collectStoredDNs(dnQueue)) {
              // DN collection is incomplete, likely
              // due to some backend problem occured.
              // Log an error message and carry on.
              Message message =
                ERR_CACHE_PRELOAD_COLLECTOR_FAILED.get(
                backend.getBackendID());
              logError(message);
            }
          } catch (UnsupportedOperationException ex) {
            // Some backends dont have collectStoredDNs()
            // method implemented, log a warning, skip
            // such backend and continue.
            if (debugEnabled()) {
              TRACER.debugCaught(DebugLogLevel.ERROR, ex);
            }
            Message message =
              WARN_CACHE_PRELOAD_BACKEND_FAILED.get(
              backend.getBackendID());
            logError(message);
          }
        }
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  public String getShutdownListenerName() {
    return preloadArbiterThreadName;
  }

  /**
   * {@inheritDoc}
   */
  public void processServerShutdown(Message reason) {
    if ((preloadArbiterThread != null) &&
         preloadArbiterThread.isAlive()) {
      // Interrupt the arbiter so it can interrupt
      // the collector and all spawned workers.
      preloadArbiterThread.interrupt();
      try {
        // This should be quick although if it
        // gets interrupted it is no big deal.
        preloadArbiterThread.join();
      } catch (InterruptedException ex) {
        if (debugEnabled()) {
          TRACER.debugCaught(DebugLogLevel.ERROR, ex);
        }
      }
    }
    DirectoryServer.deregisterShutdownListener(this);
  }
}
