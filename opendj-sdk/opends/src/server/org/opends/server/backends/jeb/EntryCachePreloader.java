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

package org.opends.server.backends.jeb;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.CursorConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import java.util.Collection;
import org.opends.messages.Message;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.opends.server.api.DirectoryThread;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.loggers.debug.DebugTracer;

import org.opends.server.types.Entry;
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
 * - The Collector thread which collects all entries stored within the
 *   backend and places them to a blocking queue workers consume from.
 *
 * - Worker threads which are responsible for monitoring the collector feed
 *   and processing the actual entries for cache storage.
 *
 * This implementation is self-adjusting to any system workload and does not
 * require any configuration parameters to optimize for initial system
 * resources availability and/or any subsequent fluctuations.
 */
class EntryCachePreloader
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * BackendImpl object.
   */
  private BackendImpl jeb;

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
   * Default resolution time.
   */
  public static final long
    PRELOAD_DEFAULT_SLEEP_TIME = 10000;

  /**
   * Effective synchronization time.
   */
  private static long syncSleepTime;

  /**
   * Default queue capacity.
   */
  public static final int
    PRELOAD_DEFAULT_QUEUE_CAPACITY = 128;

  /**
   * Effective queue capacity.
   */
  private static int queueCapacity;

  /**
   * Worker threads.
   */
  private List<Thread> preloadThreads =
    Collections.synchronizedList(
    new LinkedList<Thread>());

  /**
   * Collector thread.
   */
  private EntryCacheCollector collector =
    new EntryCacheCollector();

  /**
   * This queue is for workers to take from.
   */
  private LinkedBlockingQueue<PreloadEntry> entryQueue;

  /**
   * The number of bytes in a megabyte.
   */
  private static final int bytesPerMegabyte = 1024*1024;

  /**
   * Constructs the Entry Cache Pre-loader for
   * a given JEB implementation instance.
   *
   * @param  jeb  The JEB instance to pre-load.
   */
  public EntryCachePreloader(BackendImpl jeb) {
    // These should not be exposed as configuration
    // parameters and are only useful for testing.
    syncSleepTime = Long.getLong(
      "org.opends.server.entrycache.preload.sleep",
      PRELOAD_DEFAULT_SLEEP_TIME);
    queueCapacity = Integer.getInteger(
      "org.opends.server.entrycache.preload.queue",
      PRELOAD_DEFAULT_QUEUE_CAPACITY);
    entryQueue =
      new LinkedBlockingQueue<PreloadEntry>(
      queueCapacity);
    this.jeb = jeb;
  }

  /**
   * The Arbiter thread.
   */
  protected void preload()
  {
    logError(NOTE_CACHE_PRELOAD_PROGRESS_START.get(jeb.getBackendID()));
    // Start collector thread first.
    collector.start();
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
            jeb.getBackendID(), processedEntries.get(), freeMemory);
          logError(message);
        }
      }
    };
    timer.scheduleAtFixedRate(progressTask, progressInterval,
      progressInterval);
    // Cycle to monitor progress and adjust workers.
    long processedEntriesCycle = 0;
    long processedEntriesDelta = 0;
    long processedEntriesDeltaLow = 0;
    long processedEntriesDeltaHigh = 0;
    long lastKnownProcessedEntries = 0;
    try {
      while (!entryQueue.isEmpty() || collector.isAlive()) {

        Thread.sleep(syncSleepTime);

        processedEntriesCycle = processedEntries.get();
        processedEntriesDelta =
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
      }
      // Join the collector.
      if (collector.isAlive()) {
        collector.join();
      }
      // Join all spawned workers.
      for (Thread workerThread : preloadThreads) {
        if (workerThread.isAlive()) {
          workerThread.join();
        }
      }
      // Cancel progress report task and report done.
      timer.cancel();
      Message message = NOTE_CACHE_PRELOAD_PROGRESS_DONE.get(
        jeb.getBackendID(), processedEntries.get());
      logError(message);
    } catch (InterruptedException ex) {
      if (debugEnabled()) {
        TRACER.debugCaught(DebugLogLevel.ERROR, ex);
      }
      // Interrupt the collector.
      collector.interrupt();
      // Interrupt all preload threads.
      for (Thread thread : preloadThreads) {
        thread.interrupt();
      }
      logError(WARN_CACHE_PRELOAD_INTERRUPTED.get(
        jeb.getBackendID()));
    } finally {
      // Kill the timer task.
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
      while (!entryQueue.isEmpty() || collector.isAlive()) {
        // Check if interrupted.
        if (Thread.interrupted()) {
          return;
        }
        // Check for scaling down interruption.
        if (interruptFlag.compareAndSet(true, false)) {
          preloadThreads.remove(Thread.currentThread());
          break;
        }
        // Dequeue the next entry.
        try {
          PreloadEntry preloadEntry = entryQueue.poll();
          if (preloadEntry == null) {
            continue;
          }
          long entryID =
            JebFormat.entryIDFromDatabase(preloadEntry.entryIDBytes);
          Entry entry =
            JebFormat.entryFromDatabase(preloadEntry.entryBytes,
            jeb.getRootContainer().getCompressedSchema());
          try {
            // Even if the entry does not end up in the cache its still
            // treated as a processed entry anyways.
            DirectoryServer.getEntryCache().putEntry(entry, jeb, entryID);
            processedEntries.getAndIncrement();
          } catch (Exception ex) {
            if (debugEnabled()) {
              TRACER.debugCaught(DebugLogLevel.ERROR, ex);
            }
            Message message = ERR_CACHE_PRELOAD_ENTRY_FAILED.get(
              entry.getDN().toNormalizedString(),
              (ex.getCause() != null ? ex.getCause().getMessage() :
                stackTraceToSingleLineString(ex)));
            logError(message);
          }
        } catch (Exception ex) {
          break;
        }
      }
    }
  }

  /**
   * The Collector thread.
   */
  private class EntryCacheCollector extends DirectoryThread {
    public EntryCacheCollector() {
      super("Entry Cache Preload Collector");
    }
    @Override
    public void run() {
      Cursor cursor = null;
      ID2Entry id2entry = null;
      DatabaseEntry key = new DatabaseEntry();
      DatabaseEntry data = new DatabaseEntry();
      Collection<EntryContainer> entryContainers =
        jeb.getRootContainer().getEntryContainers();
      Iterator<EntryContainer> ecIterator =
        entryContainers.iterator();
      OperationStatus status = OperationStatus.SUCCESS;

      try {
        while (status == OperationStatus.SUCCESS) {
          // Check if interrupted.
          if (Thread.interrupted()) {
            return;
          }
          try {
            if (cursor == null) {
              if (ecIterator.hasNext()) {
                id2entry = ecIterator.next().getID2Entry();
              } else {
                break;
              }
              if (id2entry != null) {
                cursor = id2entry.openCursor(null, new CursorConfig());
              } else {
                continue;
              }
            }
            // BUG cursor might be null ? If not why testing below ?
            status = cursor.getNext(key, data, LockMode.DEFAULT);
            if (status != OperationStatus.SUCCESS) {
              // Reset cursor and continue.
              if (cursor != null) {
                try {
                  cursor.close();
                } catch (DatabaseException de) {
                  if (debugEnabled()) {
                    TRACER.debugCaught(DebugLogLevel.ERROR, de);
                  }
                }
                status = OperationStatus.SUCCESS;
                cursor = null;
                continue;
              }
            } else {
              entryQueue.put(new PreloadEntry(data.getData(),
                key.getData()));
              continue;
            }
          } catch (InterruptedException e) {
            return;
          } catch (Exception e) {
            if (debugEnabled()) {
              TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }
          }
        }
      } finally {
        // Always close cursor.
        if (cursor != null) {
          try {
            cursor.close();
          } catch (DatabaseException de) {
            if (debugEnabled()) {
              TRACER.debugCaught(DebugLogLevel.ERROR, de);
            }
          }
        }
      }
    }
  }

  /**
   * This inner class represents pre-load entry object.
   */
  private class PreloadEntry {

    // Encoded Entry.
    public byte[] entryBytes;

    // Encoded EntryID.
    public byte[] entryIDBytes;

    /**
     * Default constructor.
     */
    public PreloadEntry(byte[] entryBytes, byte[] entryIDBytes) {
      this.entryBytes = entryBytes;
      this.entryIDBytes = entryIDBytes;
    }
  }
}
