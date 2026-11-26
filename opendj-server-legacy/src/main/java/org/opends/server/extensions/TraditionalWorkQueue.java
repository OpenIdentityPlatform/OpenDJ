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
 * Portions Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.extensions;

import static org.opends.messages.ConfigMessages.*;
import static org.opends.messages.CoreMessages.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.server.config.server.TraditionalWorkQueueCfg;
import org.opends.server.api.WorkQueue;
import org.opends.server.core.DirectoryServer;
import org.opends.server.monitors.TraditionalWorkQueueMonitor;
import org.opends.server.types.CancelRequest;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;
import org.opends.server.types.Operation;

/**
 * This class defines a data structure for storing and interacting with the
 * Directory Server work queue.
 */
public class TraditionalWorkQueue extends WorkQueue<TraditionalWorkQueueCfg>
    implements ConfigurationChangeListener<TraditionalWorkQueueCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * The maximum number of times to retry getting the next operation from the
   * queue if an unexpected failure occurs.
   */
  private static final int MAX_RETRY_COUNT = 5;

  /** The set of worker threads that will be used to process this work queue. */
  private final ArrayList<TraditionalWorkerThread> workerThreads = new ArrayList<>();

  /** The number of operations that have been submitted to the work queue for processing. */
  private AtomicLong opsSubmitted;

  /**
   * The number of operations that have been accepted by the work queue and are
   * not yet fully processed: this covers operations sitting in the queue as
   * well as operations currently handled by worker threads, including the
   * hand-off window in between, which is invisible to both the queue and the
   * worker thread activity flag.
   */
  private final AtomicInteger pendingOpsCount = new AtomicInteger();

  /**
   * The number of times that an attempt to submit a new request has been
   * rejected because the work queue is already at its maximum capacity.
   */
  private AtomicLong queueFullRejects;

  /**
   * Indicates whether one or more of the worker threads needs to be killed at
   * the next convenient opportunity.
   */
  private boolean killThreads;

  /** Indicates whether submissions have been closed before queue finalization. Guarded by queueLock. */
  private boolean submissionsClosed;

  /** Indicates whether the Directory Server is shutting down. */
  private volatile boolean shutdownRequested;

  /** The thread number used for the last worker thread that was created. */
  private int lastThreadNumber;

  /**
   * The maximum number of pending requests that this work queue will allow
   * before it will start rejecting them.
   */
  private int maxCapacity;

  /**
   * The number of worker threads that should be active (or will be shortly if a
   * configuration change has not been completely applied).
   */
  private volatile int numWorkerThreads;

  /**
   * The queue overflow policy: true indicates that operations will be blocked
   * until the queue has available capacity, otherwise operations will be
   * rejected.
   * <p>
   * This is hard-coded to true for now because a reject on full policy does not
   * seem to have a valid use case.
   */
  private final boolean isBlocking = true;

  /** Guards the queue, capacity, and worker pool. Condition waits release the lock. */
  private final ReentrantLock queueLock = new ReentrantLock();
  private final Condition workAvailable = queueLock.newCondition();
  private final Condition spaceAvailable = queueLock.newCondition();
  private final Deque<Operation> opQueue = new ArrayDeque<>();

  /**
   * Creates a new instance of this work queue. All initialization should be
   * performed in the <CODE>initializeWorkQueue</CODE> method.
   */
  public TraditionalWorkQueue()
  {
    // No implementation should be performed here.
  }

  @Override
  public void initializeWorkQueue(TraditionalWorkQueueCfg configuration)
      throws ConfigException, InitializationException
  {
    queueLock.lock();
    try
    {
      submissionsClosed = false;
      shutdownRequested = false;
      killThreads = false;
      opsSubmitted = new AtomicLong(0);
      queueFullRejects = new AtomicLong(0);
      pendingOpsCount.set(0);

      // Register to be notified of any configuration changes.
      configuration.addTraditionalChangeListener(this);

      // Get the necessary configuration from the provided entry.
      numWorkerThreads =
          computeNumWorkerThreads(configuration.getNumWorkerThreads());
      maxCapacity = configuration.getMaxWorkQueueCapacity();

      opQueue.clear();

      // Create the set of worker threads that should be used to service the
      // work queue.
      for (lastThreadNumber = 0; lastThreadNumber < numWorkerThreads;
        lastThreadNumber++)
      {
        TraditionalWorkerThread t = new TraditionalWorkerThread(this,
            lastThreadNumber);
        t.start();
        workerThreads.add(t);
      }

      registerQueueMonitor();
    }
    finally
    {
      queueLock.unlock();
    }
  }

  void registerQueueMonitor()
  {
    try
    {
      TraditionalWorkQueueMonitor monitor = new TraditionalWorkQueueMonitor(this);
      monitor.initializeMonitorProvider(null);
      DirectoryServer.registerMonitorProvider(monitor);
    }
    catch (Exception e)
    {
      logger.traceException(e);
      logger.error(ERR_CONFIG_WORK_QUEUE_CANNOT_CREATE_MONITOR, TraditionalWorkQueue.class, e);
    }
  }

  @Override
  public void stopAcceptingOperations()
  {
    queueLock.lock();
    try
    {
      submissionsClosed = true;
      spaceAvailable.signalAll();
    }
    finally
    {
      queueLock.unlock();
    }
  }

  @Override
  public void finalizeWorkQueue(LocalizableMessage reason)
  {
    List<Operation> pendingOperations;
    List<TraditionalWorkerThread> workers;
    queueLock.lock();
    try
    {
      shutdownRequested = true;
      pendingOperations = new ArrayList<>(opQueue);
      opQueue.clear();
      workers = new ArrayList<>(workerThreads);
      workAvailable.signalAll();
      spaceAvailable.signalAll();
    }
    finally
    {
      queueLock.unlock();
    }

    CancelRequest cancelRequest = new CancelRequest(true, reason);
    for (Operation operation : pendingOperations)
    {
      abortPendingOperation(operation, cancelRequest);
    }

    for (TraditionalWorkerThread worker : workers)
    {
      try
      {
        worker.shutDown();
      }
      catch (Exception e)
      {
        logger.traceException(e);
        logger.warn(WARN_QUEUE_UNABLE_TO_NOTIFY_THREAD, worker.getName(), e);
      }
    }
  }

  void abortPendingOperation(Operation operation, CancelRequest cancelRequest)
  {
    try
    {
      // Pending operations have no worker to answer a cancel request.
      if (operation.getCancelResult() == null)
      {
        operation.abort(cancelRequest);
      }
    }
    catch (Exception e)
    {
      logger.traceException(e);
      logger.warn(WARN_QUEUE_UNABLE_TO_CANCEL, operation, e);
    }
    finally
    {
      operationDone();
    }
  }

  /**
   * Indicates whether this work queue has received a request to shut down.
   *
   * @return <CODE>true</CODE> if the work queue has recieved a request to shut
   *         down, or <CODE>false</CODE> if not.
   */
  public boolean shutdownRequested()
  {
    return shutdownRequested;
  }

  /**
   * Submits an operation to be processed by one of the worker threads
   * associated with this work queue.
   *
   * @param operation
   *          The operation to be processed.
   * @throws DirectoryException
   *           If the provided operation is not accepted for some reason (e.g.,
   *           if the server is shutting down or the pending operation queue is
   *           already at its maximum capacity).
   */
  @Override
  public void submitOperation(Operation operation) throws DirectoryException
  {
    submitOperation(operation, isBlocking);
  }

  @Override
  public boolean trySubmitOperation(Operation operation)
      throws DirectoryException
  {
    try
    {
      submitOperation(operation, false);
      return true;
    }
    catch (DirectoryException e)
    {
      if (ResultCode.BUSY == e.getResultCode())
      {
        return false;
      }
      throw e;
    }
  }

  private void submitOperation(Operation operation, boolean blockEnqueuingWhenFull) throws DirectoryException
  {
    // Include the hand-off from the queue to a worker in the pending operation count.
    pendingOpsCount.incrementAndGet();
    boolean submitted = false;
    queueLock.lock();
    try
    {
      checkForShutdown();
      if (blockEnqueuingWhenFull && Thread.interrupted())
      {
        throw new InterruptedException();
      }
      while (maxCapacity > 0 && opQueue.size() >= maxCapacity)
      {
        if (!blockEnqueuingWhenFull)
        {
          queueFullRejects.incrementAndGet();
          throw new DirectoryException(ResultCode.BUSY, WARN_OP_REJECTED_BY_QUEUE_FULL.get(maxCapacity));
        }
        spaceAvailable.await();
        checkForShutdown();
      }

      opQueue.addLast(operation);
      opsSubmitted.incrementAndGet();
      submitted = true;
      workAvailable.signal();
    }
    catch (InterruptedException e)
    {
      Thread.currentThread().interrupt();
      queueFullRejects.incrementAndGet();
      throw new DirectoryException(ResultCode.BUSY, WARN_OP_REJECTED_BY_QUEUE_INTERRUPT.get());
    }
    finally
    {
      if (!submitted)
      {
        pendingOpsCount.decrementAndGet();
      }
      queueLock.unlock();
    }
  }

  private void checkForShutdown() throws DirectoryException
  {
    if (submissionsClosed || shutdownRequested)
    {
      throw new DirectoryException(ResultCode.UNAVAILABLE, WARN_OP_REJECTED_BY_SHUTDOWN.get());
    }
  }

  /**
   * Retrieves the next operation that should be processed by one of the worker
   * threads, blocking if necessary until a new request arrives. This method
   * should only be called by a worker thread associated with this work queue.
   *
   * @param workerThread
   *          The worker thread that is requesting the operation.
   * @return The next operation that should be processed, or <CODE>null</CODE>
   *         if the server is shutting down and no more operations will be
   *         processed.
   */
  public Operation nextOperation(TraditionalWorkerThread workerThread)
  {
    return retryNextOperation(workerThread, 0);
  }

  /**
   * Retrieves the next operation that should be processed by one of the worker
   * threads following a previous failure attempt. A maximum of five consecutive
   * failures will be allowed before returning <CODE>null</CODE>, which will
   * cause the associated thread to exit.
   *
   * @param workerThread
   *          The worker thread that is requesting the operation.
   * @param numFailures
   *          The number of consecutive failures that the worker thread has
   *          experienced so far. If this gets too high, then this method will
   *          return <CODE>null</CODE> rather than retrying.
   * @return The next operation that should be processed, or <CODE>null</CODE>
   *         if the server is shutting down and no more operations will be
   *         processed, or if there have been too many consecutive failures.
   */
  private Operation retryNextOperation(TraditionalWorkerThread workerThread, int numFailures)
  {
    queueLock.lock();
    try
    {
      while (!shutdownRequested)
      {
        if (killThreads && tryKillThisWorkerThread(workerThread))
        {
          return null;
        }
        if (numFailures > MAX_RETRY_COUNT)
        {
          logger.error(ERR_CONFIG_WORK_QUEUE_TOO_MANY_FAILURES,
              Thread.currentThread().getName(), numFailures, MAX_RETRY_COUNT);
          return null;
        }
        if (Thread.interrupted())
        {
          throw new InterruptedException();
        }
        Operation nextOperation = opQueue.pollFirst();
        if (nextOperation != null)
        {
          spaceAvailable.signal();
          return nextOperation;
        }
        workAvailable.await();
      }
      return null;
    }
    catch (InterruptedException e)
    {
      if (shutdownRequested)
      {
        return null;
      }
      logger.warn(WARN_WORKER_INTERRUPTED_WITHOUT_SHUTDOWN, Thread.currentThread().getName(), e);
    }
    catch (Exception e)
    {
      logger.traceException(e);
      logger.warn(WARN_WORKER_WAITING_UNCAUGHT_EXCEPTION, Thread.currentThread().getName(), e);
    }
    finally
    {
      queueLock.unlock();
    }

    return retryNextOperation(workerThread, numFailures + 1);
  }

  /** Retires an excess worker while holding the queue lock. */
  private boolean tryKillThisWorkerThread(TraditionalWorkerThread workerThread)
  {
    if (workerThreads.size() <= numWorkerThreads)
    {
      killThreads = false;
      return false;
    }
    workerThreads.remove(workerThread);
    killThreads = workerThreads.size() > numWorkerThreads;
    workerThread.setStoppedByReducedThreadNumber();
    return true;
  }

  /**
   * Retrieves the total number of operations that have been successfully
   * submitted to this work queue for processing since server startup. This does
   * not include operations that have been rejected for some reason like the
   * queue already at its maximum capacity.
   *
   * @return The total number of operations that have been successfully
   *         submitted to this work queue since startup.
   */
  public long getOpsSubmitted()
  {
    return opsSubmitted.longValue();
  }

  /**
   * Retrieves the total number of operations that have been rejected because
   * the work queue was already at its maximum capacity.
   *
   * @return The total number of operations that have been rejected because the
   *         work queue was already at its maximum capacity.
   */
  public long getOpsRejectedDueToQueueFull()
  {
    return queueFullRejects.longValue();
  }

  /**
   * Retrieves the number of pending operations in the queue that have not yet
   * been picked up for processing.
   *
   * @return The number of pending operations in the queue that have not yet
   *         been picked up for processing.
   */
  public int size()
  {
    queueLock.lock();
    try
    {
      return opQueue.size();
    }
    finally
    {
      queueLock.unlock();
    }
  }

  @Override
  public boolean isConfigurationChangeAcceptable(
      TraditionalWorkQueueCfg configuration, List<LocalizableMessage> unacceptableReasons)
  {
    return true;
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(TraditionalWorkQueueCfg configuration)
  {
    int newNumThreads = computeNumWorkerThreads(configuration.getNumWorkerThreads());
    queueLock.lock();
    try
    {
      if (shutdownRequested)
      {
        return new ConfigChangeResult();
      }
      int threadsToAdd = newNumThreads - workerThreads.size();
      for (int i = 0; i < threadsToAdd; i++)
      {
        TraditionalWorkerThread worker = new TraditionalWorkerThread(this, lastThreadNumber++);
        workerThreads.add(worker);
        worker.start();
      }
      numWorkerThreads = newNumThreads;
      killThreads = threadsToAdd < 0;
      if (killThreads)
      {
        workAvailable.signalAll();
      }

      // Keep queued work in FIFO order even when the new limit is below the backlog size.
      maxCapacity = configuration.getMaxWorkQueueCapacity();
      spaceAvailable.signalAll();
    }
    finally
    {
      queueLock.unlock();
    }
    return new ConfigChangeResult();
  }

  @Override
  public boolean isIdle()
  {
    return pendingOpsCount.get() == 0;
  }

  /**
   * Notifies this work queue that a worker thread is done handling an
   * operation previously taken from the queue.
   */
  void operationDone()
  {
    pendingOpsCount.decrementAndGet();
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
