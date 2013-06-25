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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions copyright 2013 ForgeRock AS.
 */
package org.opends.server.extensions;



import static org.opends.messages.ConfigMessages.*;
import static org.opends.messages.CoreMessages.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.loggers.debug.DebugLogger.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import org.opends.messages.Message;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.TraditionalWorkQueueCfg;
import org.opends.server.api.WorkQueue;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.monitors.TraditionalWorkQueueMonitor;
import org.opends.server.types.CancelRequest;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;
import org.opends.server.types.Operation;
import org.opends.server.types.ResultCode;



/**
 * This class defines a data structure for storing and interacting with the
 * Directory Server work queue.
 */
public class TraditionalWorkQueue extends WorkQueue<TraditionalWorkQueueCfg>
    implements ConfigurationChangeListener<TraditionalWorkQueueCfg>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * The maximum number of times to retry getting the next operation from the
   * queue if an unexpected failure occurs.
   */
  private static final int MAX_RETRY_COUNT = 5;

  /** The set of worker threads that will be used to process this work queue. */
  private final ArrayList<TraditionalWorkerThread> workerThreads =
    new ArrayList<TraditionalWorkerThread>();

  /**
   * The number of operations that have been submitted to the work queue for
   * processing.
   */
  private AtomicLong opsSubmitted;

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

  /** Indicates whether the Directory Server is shutting down. */
  private boolean shutdownRequested;

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
  private int numWorkerThreads;

  /**
   * The queue overflow policy: true indicates that operations will be blocked
   * until the queue has available capacity, otherwise operations will be
   * rejected.
   * <p>
   * This is hard-coded to true for now because a reject on full policy does not
   * seem to have a valid use case.
   * </p>
   */
  private final boolean isBlocking = true;

  /** The queue that will be used to actually hold the pending operations. */
  private LinkedBlockingQueue<Operation> opQueue;

  /**
   * The lock used to provide threadsafe access for the queue, used for
   * non-config changes.
   */
  private final ReadLock queueReadLock;

  /**
   * The lock used to provide threadsafe access for the queue, used for config
   * changes.
   */
  private final WriteLock queueWriteLock;
  {
    ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    queueReadLock = lock.readLock();
    queueWriteLock = lock.writeLock();
  }



  /**
   * Creates a new instance of this work queue. All initialization should be
   * performed in the <CODE>initializeWorkQueue</CODE> method.
   */
  public TraditionalWorkQueue()
  {
    // No implementation should be performed here.
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void initializeWorkQueue(TraditionalWorkQueueCfg configuration)
      throws ConfigException, InitializationException
  {
    queueWriteLock.lock();
    try
    {
      shutdownRequested = false;
      killThreads = false;
      opsSubmitted = new AtomicLong(0);
      queueFullRejects = new AtomicLong(0);

      // Register to be notified of any configuration changes.
      configuration.addTraditionalChangeListener(this);

      // Get the necessary configuration from the provided entry.
      numWorkerThreads =
          computeNumWorkerThreads(configuration.getNumWorkerThreads());
      maxCapacity = configuration.getMaxWorkQueueCapacity();

      // Create the actual work queue.
      if (maxCapacity > 0)
      {
        opQueue = new LinkedBlockingQueue<Operation>(maxCapacity);
      }
      else
      {
        // This will never be the case, since the configuration definition
        // ensures that the capacity is always finite.
        opQueue = new LinkedBlockingQueue<Operation>();
      }

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

      // Create and register a monitor provider for the work queue.
      try
      {
        TraditionalWorkQueueMonitor monitor = new TraditionalWorkQueueMonitor(
            this);
        monitor.initializeMonitorProvider(null);
        DirectoryServer.registerMonitorProvider(monitor);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        Message message = ERR_CONFIG_WORK_QUEUE_CANNOT_CREATE_MONITOR.get(
            String.valueOf(TraditionalWorkQueueMonitor.class),
            String.valueOf(e));
        logError(message);
      }
    }
    finally
    {
      queueWriteLock.unlock();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void finalizeWorkQueue(Message reason)
  {
    queueWriteLock.lock();
    try
    {
      shutdownRequested = true;
    }
    finally
    {
      queueWriteLock.unlock();
    }

    // From now on no more operations can be enqueued or dequeued.

    // Send responses to any operations in the pending queue to indicate that
    // they won't be processed because the server is shutting down.
    CancelRequest cancelRequest = new CancelRequest(true, reason);
    ArrayList<Operation> pendingOperations = new ArrayList<Operation>();
    opQueue.removeAll(pendingOperations);
    for (Operation o : pendingOperations)
    {
      try
      {
        // The operation has no chance of responding to the cancel
        // request so avoid waiting for a cancel response.
        if (o.getCancelResult() == null)
        {
          o.abort(cancelRequest);
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        logError(WARN_QUEUE_UNABLE_TO_CANCEL.get(String.valueOf(o),
            String.valueOf(e)));
      }
    }

    // Notify all the worker threads of the shutdown.
    for (TraditionalWorkerThread t : workerThreads)
    {
      try
      {
        t.shutDown();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        logError(WARN_QUEUE_UNABLE_TO_NOTIFY_THREAD.get(t.getName(),
            String.valueOf(e)));
      }
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
    queueReadLock.lock();
    try
    {
      return shutdownRequested;
    }
    finally
    {
      queueReadLock.unlock();
    }
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

  /** {@inheritDoc} */
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

  private void submitOperation(Operation operation,
      boolean blockEnqueuingWhenFull) throws DirectoryException
  {
    queueReadLock.lock();
    try
    {
      if (shutdownRequested)
      {
        Message message = WARN_OP_REJECTED_BY_SHUTDOWN.get();
        throw new DirectoryException(ResultCode.UNAVAILABLE, message);
      }

      if (blockEnqueuingWhenFull)
      {
        try
        {
          // If the queue is full and there is an administrative change taking
          // place then starvation could arise: this thread will hold the read
          // lock, the admin thread will be waiting on the write lock, and the
          // worker threads may be queued behind the admin thread. Since the
          // worker threads cannot run, the queue will never empty and allow
          // this thread to proceed. To help things out we can periodically
          // yield the read lock when the queue is full.
          while (!opQueue.offer(operation, 1, TimeUnit.SECONDS))
          {
            queueReadLock.unlock();
            Thread.yield();
            queueReadLock.lock();

            if (shutdownRequested)
            {
              Message message = WARN_OP_REJECTED_BY_SHUTDOWN.get();
              throw new DirectoryException(ResultCode.UNAVAILABLE, message);
            }
          }
        }
        catch (InterruptedException e)
        {
          // We cannot handle the interruption here. Reject the request and
          // re-interrupt this thread.
          Thread.currentThread().interrupt();

          queueFullRejects.incrementAndGet();

          Message message = WARN_OP_REJECTED_BY_QUEUE_INTERRUPT.get();
          throw new DirectoryException(ResultCode.BUSY, message);
        }
      }
      else
      {
        if (!opQueue.offer(operation))
        {
          queueFullRejects.incrementAndGet();

          Message message = WARN_OP_REJECTED_BY_QUEUE_FULL.get(maxCapacity);
          throw new DirectoryException(ResultCode.BUSY, message);
        }
      }

      opsSubmitted.incrementAndGet();
    }
    finally
    {
      queueReadLock.unlock();
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
  private Operation retryNextOperation(TraditionalWorkerThread workerThread,
      int numFailures)
  {
    // See if we should kill off this thread. This could be necessary if the
    // number of worker threads has been decreased with the server online. If
    // so, then return null and the thread will exit.
    queueReadLock.lock();
    try
    {
      if (shutdownRequested)
      {
        return null;
      }

      if (killThreads && tryKillThisWorkerThread(workerThread))
      {
        return null;
      }

      if (numFailures > MAX_RETRY_COUNT)
      {
        Message message = ERR_CONFIG_WORK_QUEUE_TOO_MANY_FAILURES.get(Thread
            .currentThread().getName(), numFailures, MAX_RETRY_COUNT);
        logError(message);

        return null;
      }

      while (true)
      {
        Operation nextOperation = opQueue.poll(5, TimeUnit.SECONDS);
        if (nextOperation != null)
        {
          return nextOperation;
        }

        // There was no work to do in the specified length of time. Release the
        // read lock allowing shutdown or config changes to proceed and then see
        // if we should give up or check again.
        queueReadLock.unlock();
        Thread.yield();
        queueReadLock.lock();

        if (shutdownRequested)
        {
          return null;
        }

        if (killThreads && tryKillThisWorkerThread(workerThread))
        {
          return null;
        }
      }
    }
    catch (InterruptedException ie)
    {
      // This is somewhat expected so don't log.
      // assert debugException(CLASS_NAME, "retryNextOperation", ie);

      // If this occurs, then the worker thread must have been interrupted for
      // some reason. This could be because the Directory Server is shutting
      // down, in which case we should return null.
      if (shutdownRequested)
      {
        return null;
      }

      // If we've gotten here, then the worker thread was interrupted for some
      // other reason. This should not happen, and we need to log a message.
      logError(WARN_WORKER_INTERRUPTED_WITHOUT_SHUTDOWN.get(Thread
          .currentThread().getName(), String.valueOf(ie)));
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      // This should not happen. The only recourse we have is to log a message
      // and try again.
      logError(WARN_WORKER_WAITING_UNCAUGHT_EXCEPTION.get(Thread
          .currentThread().getName(), String.valueOf(e)));
    }
    finally
    {
      queueReadLock.unlock();
    }

    // An exception has occurred - retry.
    return retryNextOperation(workerThread, numFailures + 1);
  }



  /**
   * Kills this worker thread if needed. This method assumes that the read lock
   * is already taken and ensure that it is taken on exit.
   *
   * @param workerThread
   *          The worker thread associated with this thread.
   * @return {@code true} if this thread was killed or is about to be killed as
   *         a result of shutdown.
   */
  private boolean tryKillThisWorkerThread(TraditionalWorkerThread workerThread)
  {
    queueReadLock.unlock();
    queueWriteLock.lock();
    try
    {
      if (shutdownRequested)
      {
        // Shutdown may have been requested between unlock/lock. This thread is
        // about to shutdown anyway, so return true.
        return true;
      }

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
        return true;
      }
    }
    finally
    {
      queueWriteLock.unlock();
      queueReadLock.lock();

      if (shutdownRequested)
      {
        // Shutdown may have been requested between unlock/lock. This thread is
        // about to shutdown anyway, so return true.
        return true;
      }
    }
    return false;
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
   * been picked up for processing. Note that this method is not a constant-time
   * operation and can be relatively inefficient, so it should be used
   * sparingly.
   *
   * @return The number of pending operations in the queue that have not yet
   *         been picked up for processing.
   */
  public int size()
  {
    queueReadLock.lock();
    try
    {
      return opQueue.size();
    }
    finally
    {
      queueReadLock.unlock();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isConfigurationChangeAcceptable(
      TraditionalWorkQueueCfg configuration, List<Message> unacceptableReasons)
  {
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public ConfigChangeResult applyConfigurationChange(
      TraditionalWorkQueueCfg configuration)
  {
    ArrayList<Message> resultMessages = new ArrayList<Message>();
    int newNumThreads =
        computeNumWorkerThreads(configuration.getNumWorkerThreads());
    int newMaxCapacity = configuration.getMaxWorkQueueCapacity();

    // Apply a change to the number of worker threads if appropriate.
    int currentThreads = workerThreads.size();
    if (newNumThreads != currentThreads)
    {
      queueWriteLock.lock();
      try
      {
        int threadsToAdd = newNumThreads - currentThreads;
        if (threadsToAdd > 0)
        {
          for (int i = 0; i < threadsToAdd; i++)
          {
            TraditionalWorkerThread t = new TraditionalWorkerThread(this,
                lastThreadNumber++);
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
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }
      finally
      {
        queueWriteLock.unlock();
      }
    }


    // Apply a change to the maximum capacity if appropriate. Since we can't
    // change capacity on the fly, then we'll have to create a new queue and
    // transfer any remaining items into it. Any thread that is waiting on the
    // original queue will time out after at most a few seconds and further
    // checks will be against the new queue.
    if (newMaxCapacity != maxCapacity)
    {
      // First switch the queue with the exclusive lock.
      queueWriteLock.lock();
      LinkedBlockingQueue<Operation> oldOpQueue;
      try
      {
        LinkedBlockingQueue<Operation> newOpQueue = null;
        if (newMaxCapacity > 0)
        {
          newOpQueue = new LinkedBlockingQueue<Operation>(
              newMaxCapacity);
        }
        else
        {
          newOpQueue = new LinkedBlockingQueue<Operation>();
        }

        oldOpQueue = opQueue;
        opQueue = newOpQueue;

        maxCapacity = newMaxCapacity;
      }
      finally
      {
        queueWriteLock.unlock();
      }

      // Now resubmit any pending requests - we'll need the shared lock.
      Operation pendingOperation = null;
      queueReadLock.lock();
      try
      {
        // We have to be careful when adding any existing pending operations
        // because the new capacity could be less than what was already
        // backlogged in the previous queue. If that happens, we may have to
        // loop a few times to get everything in there.
        while ((pendingOperation = oldOpQueue.poll()) != null)
        {
          opQueue.put(pendingOperation);
        }
      }
      catch (InterruptedException e)
      {
        // We cannot handle the interruption here. Cancel pending requests and
        // re-interrupt this thread.
        Thread.currentThread().interrupt();

        Message message = WARN_OP_REJECTED_BY_QUEUE_INTERRUPT.get();
        CancelRequest cancelRequest = new CancelRequest(true, message);
        if (pendingOperation != null)
        {
          pendingOperation.abort(cancelRequest);
        }
        while ((pendingOperation = oldOpQueue.poll()) != null)
        {
          pendingOperation.abort(cancelRequest);
        }
      }
      finally
      {
        queueReadLock.unlock();
      }
    }

    return new ConfigChangeResult(ResultCode.SUCCESS, false, resultMessages);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isIdle()
  {
    queueReadLock.lock();
    try
    {
      if (opQueue.size() > 0)
      {
        return false;
      }

      for (TraditionalWorkerThread t : workerThreads)
      {
        if (t.isActive())
        {
          return false;
        }
      }

      return true;
    }
    finally
    {
      queueReadLock.unlock();
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
