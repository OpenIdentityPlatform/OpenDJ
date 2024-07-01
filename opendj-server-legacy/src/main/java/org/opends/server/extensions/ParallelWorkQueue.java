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
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import static org.opends.messages.ConfigMessages.*;
import static org.opends.messages.CoreMessages.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.server.config.server.ParallelWorkQueueCfg;
import org.opends.server.api.WorkQueue;
import org.opends.server.core.DirectoryServer;
import org.opends.server.monitors.ParallelWorkQueueMonitor;
import org.opends.server.types.CancelRequest;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;
import org.opends.server.types.Operation;

/**
 * This class defines a data structure for storing and interacting with the
 * Directory Server work queue.
 */
public class ParallelWorkQueue
       extends WorkQueue<ParallelWorkQueueCfg>
       implements ConfigurationChangeListener<ParallelWorkQueueCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * The maximum number of times to retry getting the next operation from the
   * queue if an unexpected failure occurs.
   */
  private static final int MAX_RETRY_COUNT = 5;

  /** The set of worker threads that will be used to process this work queue. */
  private ArrayList<ParallelWorkerThread> workerThreads;

  /** The number of operations that have been submitted to the work queue for processing. */
  private AtomicLong opsSubmitted;

  /**
   * Indicates whether one or more of the worker threads needs to be killed at
   * the next convenient opportunity.
   */
  private boolean killThreads;

  /** Indicates whether the Directory Server is shutting down. */
  private boolean shutdownRequested;

  /** The thread number used for the last worker thread that was created. */
  private int lastThreadNumber;

  /**
   * The number of worker threads that should be active (or will be shortly if a
   * configuration change has not been completely applied).
   */
  private int numWorkerThreads;

  /** The queue that will be used to actually hold the pending operations. */
  private ConcurrentLinkedQueue<Operation> opQueue;

  /** The lock used to provide threadsafe access for the queue. */
  private final Object queueLock = new Object();

  private final Semaphore queueSemaphore = new Semaphore(0, false);

  /**
   * Creates a new instance of this work queue.  All initialization should be
   * performed in the <CODE>initializeWorkQueue</CODE> method.
   */
  public ParallelWorkQueue()
  {
    // No implementation should be performed here.
  }

  @Override
  public void initializeWorkQueue(ParallelWorkQueueCfg configuration)
         throws ConfigException, InitializationException
  {
    shutdownRequested = false;
    killThreads       = false;
    opsSubmitted      = new AtomicLong(0);

    // Register to be notified of any configuration changes.
    configuration.addParallelChangeListener(this);

    // Get the necessary configuration from the provided entry.
    numWorkerThreads =
        computeNumWorkerThreads(configuration.getNumWorkerThreads());

    // Create the actual work queue.
    opQueue = new ConcurrentLinkedQueue<>();

    // Create the set of worker threads that should be used to service the work queue.
    workerThreads = new ArrayList<>(numWorkerThreads);
    for (lastThreadNumber = 0; lastThreadNumber < numWorkerThreads;
    lastThreadNumber++)
    {
      ParallelWorkerThread t =
           new ParallelWorkerThread(this, lastThreadNumber);
      t.start();
      workerThreads.add(t);
    }

    // Create and register a monitor provider for the work queue.
    try
    {
      ParallelWorkQueueMonitor monitor =
           new ParallelWorkQueueMonitor(this);
      monitor.initializeMonitorProvider(null);
      DirectoryServer.registerMonitorProvider(monitor);
    }
    catch (Exception e)
    {
      logger.traceException(e);
      logger.error(ERR_CONFIG_WORK_QUEUE_CANNOT_CREATE_MONITOR, ParallelWorkQueueMonitor.class, e);
    }
  }

  @Override
  public void finalizeWorkQueue(LocalizableMessage reason)
  {
    shutdownRequested = true;

    // Send responses to any operations in the pending queue to indicate that
    // they won't be processed because the server is shutting down.
    CancelRequest cancelRequest = new CancelRequest(true, reason);
    ArrayList<Operation> pendingOperations = new ArrayList<>();
    opQueue.removeAll(pendingOperations);

    for (Operation o : pendingOperations)
    {
      try
      {
        // The operation has no chance of responding to the cancel
        // request so avoid waiting for a cancel response.
        if (o.getCancelResult() == null) {
          o.abort(cancelRequest);
        }
      }
      catch (Exception e)
      {
        logger.traceException(e);
        logger.warn(WARN_QUEUE_UNABLE_TO_CANCEL, o, e);
      }
    }

    // Notify all the worker threads of the shutdown.
    for (ParallelWorkerThread t : workerThreads)
    {
      try
      {
        t.shutDown();
      }
      catch (Exception e)
      {
        logger.traceException(e);
        logger.warn(WARN_QUEUE_UNABLE_TO_NOTIFY_THREAD, t.getName(), e);
      }
    }
  }

  /**
   * Indicates whether this work queue has received a request to shut down.
   *
   * @return  <CODE>true</CODE> if the work queue has recieved a request to shut
   *          down, or <CODE>false</CODE> if not.
   */
  public boolean shutdownRequested()
  {
    return shutdownRequested;
  }

  /**
   * Submits an operation to be processed by one of the worker threads
   * associated with this work queue.
   *
   * @param  operation  The operation to be processed.
   *
   * @throws  DirectoryException  If the provided operation is not accepted for
   *                              some reason (e.g., if the server is shutting
   *                              down or the pending operation queue is already
   *                              at its maximum capacity).
   */
  @Override
  public void submitOperation(Operation operation) throws DirectoryException
  {
    if (shutdownRequested)
    {
      LocalizableMessage message = WARN_OP_REJECTED_BY_SHUTDOWN.get();
      throw new DirectoryException(ResultCode.UNAVAILABLE, message);
    }

    opQueue.add(operation);
    queueSemaphore.release();

    opsSubmitted.incrementAndGet();
  }

  @Override
  public boolean trySubmitOperation(Operation operation)
      throws DirectoryException
  {
    submitOperation(operation);
    return true;
  }

  /**
   * Retrieves the next operation that should be processed by one of the worker
   * threads, blocking if necessary until a new request arrives.  This method
   * should only be called by a worker thread associated with this work queue.
   *
   * @param  workerThread  The worker thread that is requesting the operation.
   *
   * @return  The next operation that should be processed, or <CODE>null</CODE>
   *          if the server is shutting down and no more operations will be
   *          processed.
   */
  public Operation nextOperation(ParallelWorkerThread workerThread)
  {
    return retryNextOperation(workerThread, 0);
  }

  /**
   * Retrieves the next operation that should be processed by one of the worker
   * threads following a previous failure attempt.  A maximum of five
   * consecutive failures will be allowed before returning <CODE>null</CODE>,
   * which will cause the associated thread to exit.
   *
   * @param  workerThread  The worker thread that is requesting the operation.
   * @param  numFailures   The number of consecutive failures that the worker
   *                       thread has experienced so far.  If this gets too
   *                       high, then this method will return <CODE>null</CODE>
   *                       rather than retrying.
   *
   * @return  The next operation that should be processed, or <CODE>null</CODE>
   *          if the server is shutting down and no more operations will be
   *          processed, or if there have been too many consecutive failures.
   */
  private Operation retryNextOperation(
                                       ParallelWorkerThread workerThread,
                                       int numFailures)
  {
    // See if we should kill off this thread.  This could be necessary if the
    // number of worker threads has been decreased with the server online. If
    // so, then return null and the thread will exit.
    if (killThreads)
    {
      synchronized (queueLock)
      {
        try
        {
          int currentThreads = workerThreads.size();
          if (currentThreads > numWorkerThreads)
          {
            if (workerThreads.remove(Thread.currentThread()))
            {
              currentThreads--;
            }

            if (currentThreads <= numWorkerThreads)
            {
              killThreads = false;
            }

            workerThread.setStoppedByReducedThreadNumber();
            return null;
          }
        }
        catch (Exception e)
        {
          logger.traceException(e);
        }
      }
    }

    if (shutdownRequested || numFailures > MAX_RETRY_COUNT)
    {
      if (numFailures > MAX_RETRY_COUNT)
      {
        logger.error(ERR_CONFIG_WORK_QUEUE_TOO_MANY_FAILURES, Thread
            .currentThread().getName(), numFailures, MAX_RETRY_COUNT);
      }

      return null;
    }

    try
    {
      while (true)
      {
        Operation nextOperation = null;
        if (queueSemaphore.tryAcquire(5, TimeUnit.SECONDS)) {
          nextOperation = opQueue.poll();
        }
        if (nextOperation == null)
        {
          // There was no work to do in the specified length of time.  See if
          // we should shutdown, and if not then just check again.
          if (shutdownRequested)
          {
            return null;
          }
          else if (killThreads)
          {
            synchronized (queueLock)
            {
              try
              {
                int currentThreads = workerThreads.size();
                if (currentThreads > numWorkerThreads)
                {
                  if (workerThreads.remove(Thread.currentThread()))
                  {
                    currentThreads--;
                  }

                  if (currentThreads <= numWorkerThreads)
                  {
                    killThreads = false;
                  }

                  workerThread.setStoppedByReducedThreadNumber();
                  return null;
                }
              }
              catch (Exception e)
              {
                logger.traceException(e);
              }
            }
          }
        }
        else
        {
          return nextOperation;
        }
      }
    }
    catch (Exception e)
    {
      logger.traceException(e);

      // This should not happen.  The only recourse we have is to log a message
      // and try again.
      logger.warn(WARN_WORKER_WAITING_UNCAUGHT_EXCEPTION, Thread.currentThread().getName(), e);
      return retryNextOperation(workerThread, numFailures + 1);
    }
  }

  /**
   * Attempts to remove the specified operation from this queue if it has not
   * yet been picked up for processing by one of the worker threads.
   *
   * @param  operation  The operation to remove from the queue.
   *
   * @return  <CODE>true</CODE> if the provided request was present in the queue
   *          and was removed successfully, or <CODE>false</CODE> it not.
   */
  public boolean removeOperation(Operation operation)
  {
    return opQueue.remove(operation);
  }

  /**
   * Retrieves the total number of operations that have been successfully
   * submitted to this work queue for processing since server startup.  This
   * does not include operations that have been rejected for some reason like
   * the queue already at its maximum capacity.
   *
   * @return  The total number of operations that have been successfully
   *          submitted to this work queue since startup.
   */
  public long getOpsSubmitted()
  {
    return opsSubmitted.longValue();
  }

  /**
   * Retrieves the number of pending operations in the queue that have not yet
   * been picked up for processing.  Note that this method is not a
   * constant-time operation and can be relatively inefficient, so it should be
   * used sparingly.
   *
   * @return  The number of pending operations in the queue that have not yet
   *          been picked up for processing.
   */
  public int size()
  {
    return opQueue.size();
  }

  @Override
  public boolean isConfigurationChangeAcceptable(
                      ParallelWorkQueueCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    return true;
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(
                                 ParallelWorkQueueCfg configuration)
  {
    int newNumThreads =
        computeNumWorkerThreads(configuration.getNumWorkerThreads());

    // Apply a change to the number of worker threads if appropriate.
    int currentThreads = workerThreads.size();
    if (newNumThreads != currentThreads)
    {
      synchronized (queueLock)
      {
        try
        {
          int threadsToAdd = newNumThreads - currentThreads;
          if (threadsToAdd > 0)
          {
            for (int i = 0; i < threadsToAdd; i++)
            {
              ParallelWorkerThread t =
                   new ParallelWorkerThread(this, lastThreadNumber++);
              workerThreads.add(t);
              t.start();
            }

            killThreads = false;
          }
          else
          {
            killThreads = true;
          }

          numWorkerThreads = newNumThreads;
        }
        catch (Exception e)
        {
          logger.traceException(e);
        }
      }
    }
    return new ConfigChangeResult();
  }

  @Override
  public boolean isIdle()
  {
    if (!opQueue.isEmpty()) {
      return false;
    }

    synchronized (queueLock)
    {
      for (ParallelWorkerThread t : workerThreads)
      {
        if (t.isActive())
        {
          return false;
        }
      }

      return true;
    }
  }

  /**
   * Return the number of worker threads used by this WorkQueue.
   *
   * @return the number of worker threads used by this WorkQueue
   */
  @Override
  public int getNumWorkerThreads()
  {
    return this.numWorkerThreads;
  }
}
