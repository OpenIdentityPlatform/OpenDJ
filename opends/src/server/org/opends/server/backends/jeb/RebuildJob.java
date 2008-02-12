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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.backends.jeb;
import org.opends.messages.Message;

import org.opends.server.types.*;

import java.util.ArrayList;
import java.util.TimerTask;
import java.util.Timer;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.StatsConfig;
import com.sleepycat.je.EnvironmentStats;

import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.core.DirectoryServer;
import static org.opends.messages.JebMessages.
    ERR_JEB_ATTRIBUTE_INDEX_NOT_CONFIGURED;
import static org.opends.messages.JebMessages.
    INFO_JEB_REBUILD_PROGRESS_REPORT;
import static org.opends.messages.JebMessages.
    INFO_JEB_REBUILD_FINAL_STATUS;
import static org.opends.messages.JebMessages.
    INFO_JEB_REBUILD_CACHE_AND_MEMORY_REPORT;
import static org.opends.messages.JebMessages.
    ERR_JEB_REBUILD_INDEX_CONFLICT;
import static org.opends.messages.JebMessages.
    INFO_JEB_REBUILD_START;
import static org.opends.messages.JebMessages.
    ERR_JEB_VLV_INDEX_NOT_CONFIGURED;
/**
 * Runs a index rebuild process on the backend. Each index selected for rebuild
 * will be done from scratch by first clearing out the database for that index.
 * Different threads will be used to rebuild each index.
 * The rebuild process can run concurrently with the backend online and
 * performing write and read operations. However, during the rebuild process,
 * other reader and writer activeThreads might notice inconsistencies in index
 * databases being rebuilt. They can safely ignore these inconsistencies as long
 * as a rebuild is in progress.
 */
public class RebuildJob
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * The rebuild configuraiton.
   */
  private RebuildConfig rebuildConfig;

  /**
   * The root container used for the verify job.
   */
  private RootContainer rootContainer;

  /**
   * The number of milliseconds between job progress reports.
   */
  private long progressInterval = 10000;

  /**
   * The waiting rebuild threads created to process the rebuild.
   */
  private CopyOnWriteArrayList<IndexRebuildThread> waitingThreads =
      new CopyOnWriteArrayList<IndexRebuildThread>();

  /**
   * The active rebuild threads created to process the rebuild.
   */
  private CopyOnWriteArrayList<IndexRebuildThread> activeThreads =
      new CopyOnWriteArrayList<IndexRebuildThread>();

  /**
   * The completed rebuild threads used to process the rebuild.
   */
  private CopyOnWriteArrayList<IndexRebuildThread> completedThreads =
      new CopyOnWriteArrayList<IndexRebuildThread>();

  /**
   * Rebuild jobs currently running.
   */
  private static CopyOnWriteArrayList<RebuildJob> rebuildJobs =
      new CopyOnWriteArrayList<RebuildJob>();

  /**
   * A mutex that will be used to provide threadsafe access to methods changing
   * the set of currently running rebuild jobs.
   */
  private static ReentrantLock jobsMutex = new ReentrantLock();

  /**
   * This class reports progress of the rebuild job at fixed intervals.
   */
  class ProgressTask extends TimerTask
  {
    /**
     * The number of records that had been processed at the time of the
     * previous progress report.
     */
    private long previousProcessed = 0;

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
     * Create a new verify progress task.
     * @throws DatabaseException An error occurred while accessing the JE
     * database.
     */
    public ProgressTask() throws DatabaseException
    {
      previousTime = System.currentTimeMillis();
      prevEnvStats =
          rootContainer.getEnvironmentStats(new StatsConfig());
    }

    /**
     * The action to be performed by this timer task.
     */
    public void run()
    {
      long latestTime = System.currentTimeMillis();
      long deltaTime = latestTime - previousTime;

      if (deltaTime == 0)
      {
        return;
      }

      long totalEntries = 0;
      long latestProcessed = 0;

      ArrayList<IndexRebuildThread> allThreads =
          new ArrayList<IndexRebuildThread>(waitingThreads);
      allThreads.addAll(activeThreads);
      allThreads.addAll(completedThreads);

      for(IndexRebuildThread thread : allThreads)
      {
        try
        {
          totalEntries += thread.getTotalEntries();
          latestProcessed += thread.getProcessedEntries();

          if(debugEnabled())
          {
            TRACER.debugVerbose("Rebuild thread %s stats: total %d " +
                "processed %d rebuilt %d duplicated %d skipped %d",
                         thread.getTotalEntries(), thread.getProcessedEntries(),
                         thread.getRebuiltEntries(),
                         thread.getDuplicatedEntries(),
                         thread.getSkippedEntries());
          }
        }
        catch(Exception e)
        {
          if(debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }
        }
      }

      long deltaCount = (latestProcessed - previousProcessed);
      float rate = 1000f*deltaCount / deltaTime;
      float completed = 0;
      if(totalEntries > 0)
      {
        completed = 100f*latestProcessed / totalEntries;
      }

      Message message = INFO_JEB_REBUILD_PROGRESS_REPORT.get(
          completed, latestProcessed, totalEntries, rate);
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

        message = INFO_JEB_REBUILD_CACHE_AND_MEMORY_REPORT.get(
            freeMemory, cacheMissRate);
        logError(message);

        prevEnvStats = envStats;
      }
      catch (DatabaseException e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }


      previousProcessed = latestProcessed;
      previousTime = latestTime;
    }
  }

  /**
   * Construct a new rebuild job.
   *
   * @param rebuildConfig The configuration to use for this rebuild job.
   */
  public RebuildJob(RebuildConfig rebuildConfig)
  {
    this.rebuildConfig = rebuildConfig;
  }

  private static void addJob(RebuildJob job)
      throws DatabaseException, JebException
  {
    //Make sure there are no running rebuild jobs
    jobsMutex.lock();

    try
    {
      for(RebuildJob otherJob : rebuildJobs)
      {
        String conflictIndex =
            job.rebuildConfig.checkConflicts(otherJob.rebuildConfig);
        if(conflictIndex != null)
        {
          if(debugEnabled())
          {
            TRACER.debugError("Conflit detected. This job config: %s, " +
                "That job config: %s.",
                              job.rebuildConfig, otherJob.rebuildConfig);
          }

          Message msg = ERR_JEB_REBUILD_INDEX_CONFLICT.get(conflictIndex);
          throw new JebException(msg);
        }
      }

      //No conflicts are found. Add the job to the list of currently running
      // jobs.
      rebuildJobs.add(job);
    }
    finally
    {
      jobsMutex.unlock();
    }
  }

  private static void removeJob(RebuildJob job)
  {
    jobsMutex.lock();

    rebuildJobs.remove(job);

    jobsMutex.unlock();
  }

  /**
   * Initiate the rebuild process on a backend.
   *
   * @param rootContainer The root container to rebuild in.
   * @throws DirectoryException If an error occurs during the rebuild process.
   * @throws DatabaseException If a JE database error occurs during the rebuild
   *                           process.
   * @throws JebException If a JE database error occurs during the rebuild
   *                      process.
   */
  public void rebuildBackend(RootContainer rootContainer)
      throws DirectoryException, DatabaseException, JebException
  {
    //TODO: Add check for only performing internal indexType rebuilds when
    // backend is offline.

    addJob(this);

    try
    {
      this.rootContainer = rootContainer;
      EntryContainer entryContainer =
          rootContainer.getEntryContainer(rebuildConfig.getBaseDN());

      ArrayList<String> rebuildList = rebuildConfig.getRebuildList();

      if(!rebuildList.isEmpty())
      {

        for (String index : rebuildList)
        {
          IndexRebuildThread rebuildThread;
          String lowerName = index.toLowerCase();
          if (lowerName.equals("dn2id"))
          {
            rebuildThread = new IndexRebuildThread(entryContainer,
                                            IndexRebuildThread.IndexType.DN2ID);
          }
          else if (lowerName.equals("dn2uri"))
          {
            rebuildThread = new IndexRebuildThread(entryContainer,
                                           IndexRebuildThread.IndexType.DN2URI);
          }
          else if (lowerName.equals("id2children"))
          {
            rebuildThread = new IndexRebuildThread(entryContainer,
                                      IndexRebuildThread.IndexType.ID2CHILDREN);
          }
          else if (lowerName.equals("id2subtree"))
          {
            rebuildThread = new IndexRebuildThread(entryContainer,
                                       IndexRebuildThread.IndexType.ID2SUBTREE);
          }
          else if (lowerName.startsWith("vlv."))
          {
            if(lowerName.length() < 5)
            {
              Message msg = ERR_JEB_VLV_INDEX_NOT_CONFIGURED.get(lowerName);
              throw new JebException(msg);
            }

            VLVIndex vlvIndex =
                entryContainer.getVLVIndex(lowerName.substring(4));
            if(vlvIndex == null)
            {
              Message msg =
                  ERR_JEB_VLV_INDEX_NOT_CONFIGURED.get(lowerName.substring(4));
              throw new JebException(msg);
            }

            rebuildThread = new IndexRebuildThread(entryContainer, vlvIndex);
          }
          else
          {
            String[] attrIndexParts = lowerName.split("\\.");
            if(attrIndexParts.length <= 0)
            {
              Message msg = ERR_JEB_ATTRIBUTE_INDEX_NOT_CONFIGURED.get(index);
              throw new JebException(msg);
            }

            AttributeType attrType =
                DirectoryServer.getAttributeType(attrIndexParts[0]);

            if (attrType == null)
            {
              Message msg = ERR_JEB_ATTRIBUTE_INDEX_NOT_CONFIGURED.get(index);
              throw new JebException(msg);
            }
            AttributeIndex attrIndex =
                entryContainer.getAttributeIndex(attrType);
            if (attrIndex == null)
            {
              Message msg = ERR_JEB_ATTRIBUTE_INDEX_NOT_CONFIGURED.get(index);
              throw new JebException(msg);
            }

            if(attrIndexParts.length > 1)
            {
              Index partialAttrIndex = null;
              if(attrIndexParts[1].equals("presence"))
              {
                partialAttrIndex = attrIndex.presenceIndex;
              }
              else if(attrIndexParts[1].equals("equality"))
              {
                partialAttrIndex = attrIndex.equalityIndex;
              }
              else if(attrIndexParts[1].equals("substring"))
              {
                partialAttrIndex = attrIndex.substringIndex;
              }
              else if(attrIndexParts[1].equals("ordering"))
              {
                partialAttrIndex = attrIndex.orderingIndex;
              }
              else if(attrIndexParts[1].equals("approximate"))
              {
                partialAttrIndex = attrIndex.approximateIndex;
              }

              if(partialAttrIndex == null)
              {
                Message msg = ERR_JEB_ATTRIBUTE_INDEX_NOT_CONFIGURED.get(index);
                throw new JebException(msg);
              }

              rebuildThread =
                  new IndexRebuildThread(entryContainer, partialAttrIndex);
            }
            else
            {
              rebuildThread = new IndexRebuildThread(entryContainer,
                                                     attrIndex);
            }
          }

          waitingThreads.add(rebuildThread);

          if(debugEnabled())
          {
            TRACER.debugInfo("Created rebuild thread %s",
                             rebuildThread.getName());
          }
        }

        //Log a start message.
        long totalToProcess = 0;

        for(IndexRebuildThread thread : waitingThreads)
        {
          totalToProcess += thread.getTotalEntries();
        }

        StringBuilder sb = new StringBuilder();
        for(String index : rebuildList)
        {
          if(sb.length() > 0)
          {
            sb.append(", ");
          }
          sb.append(index);
        }
        Message message =
            INFO_JEB_REBUILD_START.get(sb.toString(), totalToProcess);
        logError(message);

        // Make a note of the time we started.
        long startTime = System.currentTimeMillis();

        // Start a timer for the progress report.
        Timer timer = new Timer();
        TimerTask progressTask = new ProgressTask();
        timer.scheduleAtFixedRate(progressTask, progressInterval,
                                  progressInterval);

        entryContainer.exclusiveLock.lock();
        try
        {
          for(IndexRebuildThread thread : waitingThreads)
          {
            thread.clearDatabase();
          }
        }
        finally
        {
          if(!rebuildConfig.includesSystemIndex())
          {
            entryContainer.exclusiveLock.unlock();
          }
        }


        if(!rebuildConfig.includesSystemIndex())
        {
          entryContainer.sharedLock.lock();
        }
        try
        {
          while(!waitingThreads.isEmpty())
          {
            dispatchThreads();
            joinThreads();
          }
        }
        finally
        {
          timer.cancel();
          if(rebuildConfig.includesSystemIndex())
          {
            entryContainer.exclusiveLock.unlock();
          }
          else
          {
            entryContainer.sharedLock.unlock();
          }
        }

        long totalProcessed = 0;
        long totalRebuilt = 0;
        long totalDuplicated = 0;
        long totalSkipped = 0;

        for(IndexRebuildThread thread : completedThreads)
        {
          totalProcessed += thread.getProcessedEntries();
          totalRebuilt += thread.getRebuiltEntries();
          totalDuplicated += thread.getDuplicatedEntries();
          totalSkipped += thread.getSkippedEntries();
        }

        long finishTime = System.currentTimeMillis();
        long totalTime = (finishTime - startTime);

        float rate = 0;
        if (totalTime > 0)
        {
          rate = 1000f*totalProcessed / totalTime;
        }

        message = INFO_JEB_REBUILD_FINAL_STATUS.get(
            totalProcessed, totalTime/1000, rate);
        logError(message);

        if(debugEnabled())
        {
          TRACER.debugInfo("Detailed overall rebuild job stats: rebuilt %d, " +
              "duplicated %d, skipped %d",
                    totalRebuilt, totalDuplicated, totalSkipped);
        }
      }
    }
    finally
    {
      removeJob(this);
    }

  }

  /**
   * Dispatch a set of threads based on their dependency and ordering.
   */
  private void dispatchThreads() throws DatabaseException
  {
    for(IndexRebuildThread t : waitingThreads)
    {
      boolean start = true;

      //Check to see if we have exceeded the max number of threads to use at
      //one time.
      if(rebuildConfig.getMaxRebuildThreads() > 0 &&
          activeThreads.size() > rebuildConfig.getMaxRebuildThreads())
      {
        if(debugEnabled())
        {
          TRACER.debugInfo("Delaying the start of thread %s because " +
              "the max number of rebuild threads has been reached.");
        }
        start = false;
      }

      /**
       * We may need to start the threads in stages since the rebuild process
       * of some index types (id2children, id2subtree) depends on another
       * index being rebuilt to be completed first.
       */
      if(t.getIndexType() == IndexRebuildThread.IndexType.ID2CHILDREN ||
          t.getIndexType() == IndexRebuildThread.IndexType.ID2SUBTREE)
      {
        //Check to see if we have any waiting threads that needs to go
        //first
        for(IndexRebuildThread t2 : waitingThreads)
        {
          if(t2.getIndexType() == IndexRebuildThread.IndexType.DN2ID ||
              t2.getIndexType() == IndexRebuildThread.IndexType.DN2URI)
          {
            //We gotta wait for these to start before running the
            //rebuild on ID2CHILDREN or ID2SUBTREE

            if(debugEnabled())
            {
              TRACER.debugInfo("Delaying the start of thread %s because " +
                  "it depends on another index rebuilt to " +
                  "go first.", t.getName());
            }
            start = false;
            break;
          }
        }

        //Check to see if we have any active threads that needs to
        //finish first
        for(IndexRebuildThread t3 : activeThreads)
        {
          if(t3.getIndexType() == IndexRebuildThread.IndexType.DN2ID ||
              t3.getIndexType() == IndexRebuildThread.IndexType.DN2URI)
          {
            //We gotta wait for these to start before running the
            //rebuild on ID2CHILDREN or ID2SUBTREE

            if(debugEnabled())
            {
              TRACER.debugInfo("Delaying the start of thread %s because " +
                  "it depends on another index being rebuilt to " +
                  "finish.", t.getName());
            }
            start = false;
            break;
          }
        }
      }

      if(start)
      {
        if(debugEnabled())
        {
          TRACER.debugInfo("Starting rebuild thread %s.", t.getName());
        }
        waitingThreads.remove(t);
        activeThreads.add(t);
        t.start();
      }
    }
  }

  /**
   * Wait for all worker activeThreads to exit.
   */
  private void joinThreads()
  {
    for (IndexRebuildThread t : activeThreads)
    {
      try
      {
        t.join();

        if(debugEnabled())
        {
          TRACER.debugInfo("Rebuild thread %s finished.", t.getName());
        }
        activeThreads.remove(t);
        completedThreads.add(t);
      }
      catch (InterruptedException ie)
      {
        // No action needed?
      }
    }
  }
}
