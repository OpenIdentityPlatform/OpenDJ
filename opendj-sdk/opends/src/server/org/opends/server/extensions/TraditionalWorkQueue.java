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
package org.opends.server.extensions;



import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.opends.messages.Message;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.TraditionalWorkQueueCfg;
import org.opends.server.api.WorkQueue;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.monitors.TraditionalWorkQueueMonitor;
import org.opends.server.types.AbstractOperation;
import org.opends.server.types.CancelRequest;
import org.opends.server.types.CancelResult;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.InitializationException;
import org.opends.server.types.Operation;
import org.opends.server.types.ResultCode;

import static org.opends.messages.ConfigMessages.*;
import static org.opends.messages.CoreMessages.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.loggers.debug.DebugLogger.*;



/**
 * This class defines a data structure for storing and interacting with the
 * Directory Server work queue.
 */
public class TraditionalWorkQueue
       extends WorkQueue<TraditionalWorkQueueCfg>
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



  // The set of worker threads that will be used to process this work queue.
  private ArrayList<TraditionalWorkerThread> workerThreads;

  // The number of operations that have been submitted to the work queue for
  // processing.
  private AtomicLong opsSubmitted;

  // The number of times that an attempt to submit a new request has been
  // rejected because the work queue is already at its maximum capacity.
  private AtomicLong queueFullRejects;

  // Indicates whether one or more of the worker threads needs to be killed at
  // the next convenient opportunity.
  private boolean killThreads;

  // Indicates whether the Directory Server is shutting down.
  private boolean shutdownRequested;

  // The DN of the configuration entry with information to use to configure the
  // work queue.
  private DN configEntryDN;

  // The thread number used for the last worker thread that was created.
  private int lastThreadNumber;

  // The maximum number of pending requests that this work queue will allow
  // before it will start rejecting them.
  private int maxCapacity;

  // The number of worker threads that should be active (or will be shortly if
  // a configuration change has not been completely applied).
  private int numWorkerThreads;

  // The queue that will be used to actually hold the pending operations.
  private LinkedBlockingQueue<AbstractOperation> opQueue;

  // The lock used to provide threadsafe access for the queue.
  private Object queueLock;



  /**
   * Creates a new instance of this work queue.  All initialization should be
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
    shutdownRequested = false;
    killThreads       = false;
    opsSubmitted      = new AtomicLong(0);
    queueFullRejects  = new AtomicLong(0);
    queueLock         = new Object();


    // Register to be notified of any configuration changes.
    configuration.addTraditionalChangeListener(this);


    // Get the necessary configuration from the provided entry.
    configEntryDN    = configuration.dn();
    numWorkerThreads = configuration.getNumWorkerThreads();
    maxCapacity      = configuration.getMaxWorkQueueCapacity();


    // Create the actual work queue.
    if (maxCapacity > 0)
    {
      opQueue = new LinkedBlockingQueue<AbstractOperation>(maxCapacity);
    }
    else
    {
      opQueue = new LinkedBlockingQueue<AbstractOperation>();
    }


    // Create the set of worker threads that should be used to service the
    // work queue.
    workerThreads = new ArrayList<TraditionalWorkerThread>(numWorkerThreads);
    for (lastThreadNumber = 0; lastThreadNumber < numWorkerThreads;
    lastThreadNumber++)
    {
      TraditionalWorkerThread t =
           new TraditionalWorkerThread(this, lastThreadNumber);
      t.start();
      workerThreads.add(t);
    }


    // Create and register a monitor provider for the work queue.
    try
    {
      TraditionalWorkQueueMonitor monitor =
           new TraditionalWorkQueueMonitor(this);
      monitor.initializeMonitorProvider(null);
      monitor.start();
      DirectoryServer.registerMonitorProvider(monitor);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_CONFIG_WORK_QUEUE_CANNOT_CREATE_MONITOR.get(
          String.valueOf(TraditionalWorkQueueMonitor.class), String.valueOf(e));
      logError(message);
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void finalizeWorkQueue(Message reason)
  {
    shutdownRequested = true;


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
        if (o.getCancelResult() == null) {
          o.setCancelResult(CancelResult.CANCELED);
          o.cancel(cancelRequest);
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        logError(WARN_QUEUE_UNABLE_TO_CANCEL.get(
            String.valueOf(o), String.valueOf(e)));
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

        logError(WARN_QUEUE_UNABLE_TO_NOTIFY_THREAD.get(
            t.getName(), String.valueOf(e)));
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
  public void submitOperation(AbstractOperation operation)
         throws DirectoryException
  {
    if (shutdownRequested)
    {
      Message message = WARN_OP_REJECTED_BY_SHUTDOWN.get();
      throw new DirectoryException(ResultCode.UNAVAILABLE, message);
    }

    if (! opQueue.offer(operation))
    {
      queueFullRejects.incrementAndGet();

      Message message = WARN_OP_REJECTED_BY_QUEUE_FULL.get(maxCapacity);
      throw new DirectoryException(ResultCode.UNAVAILABLE, message);
    }

    opsSubmitted.incrementAndGet();
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
  public AbstractOperation nextOperation(TraditionalWorkerThread workerThread)
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
  private AbstractOperation retryNextOperation(
                                       TraditionalWorkerThread workerThread,
                                       int numFailures)
  {
    // See if we should kill off this thread.  This could be necessary if the
    // number of worker threads has been decreased with the server online.  If
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
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }
        }
      }
    }

    if ((shutdownRequested) || (numFailures > MAX_RETRY_COUNT))
    {
      if (numFailures > MAX_RETRY_COUNT)
      {
        Message message = ERR_CONFIG_WORK_QUEUE_TOO_MANY_FAILURES.get(
            Thread.currentThread().getName(), numFailures, MAX_RETRY_COUNT);
        logError(message);
      }

      return null;
    }

    try
    {
      while (true)
      {
        AbstractOperation nextOperation = opQueue.poll(5, TimeUnit.SECONDS);
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
                if (debugEnabled())
                {
                  TRACER.debugCaught(DebugLogLevel.ERROR, e);
                }
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
    catch (InterruptedException ie)
    {
      // This is somewhat expected so don't log.
      //      assert debugException(CLASS_NAME, "retryNextOperation", ie);

      // If this occurs, then the worker thread must have been interrupted for
      // some reason.  This could be because the Directory Server is shutting
      // down, in which case we should return null.
      if (shutdownRequested)
      {
        return null;
      }

      // If we've gotten here, then the worker thread was interrupted for some
      // other reason.  This should not happen, and we need to log a message.
      logError(WARN_WORKER_INTERRUPTED_WITHOUT_SHUTDOWN.get(
          Thread.currentThread().getName(), String.valueOf(ie)));
      return retryNextOperation(workerThread, numFailures+1);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      // This should not happen.  The only recourse we have is to log a message
      // and try again.
      logError(WARN_WORKER_WAITING_UNCAUGHT_EXCEPTION.get(
          Thread.currentThread().getName(), String.valueOf(e)));
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
  public boolean removeOperation(AbstractOperation operation)
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
   * Retrieves the total number of operations that have been rejected because
   * the work queue was already at its maximum capacity.
   *
   * @return  The total number of operations that have been rejected because the
   *          work queue was already at its maximum capacity.
   */
  public long getOpsRejectedDueToQueueFull()
  {
    return queueFullRejects.longValue();
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



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
                      TraditionalWorkQueueCfg configuration,
                      List<Message> unacceptableReasons)
  {
    // The provided configuration will always be acceptable.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
                                 TraditionalWorkQueueCfg configuration)
  {
    ArrayList<Message> resultMessages = new ArrayList<Message>();
    int newNumThreads  = configuration.getNumWorkerThreads();
    int newMaxCapacity = configuration.getMaxWorkQueueCapacity();


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
            for (int i=0; i < threadsToAdd; i++)
            {
              TraditionalWorkerThread t =
                   new TraditionalWorkerThread(this, lastThreadNumber++);
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
      }
    }


    // Apply a change to the maximum capacity if appropriate.  Since we can't
    // change capacity on the fly, then we'll have to create a new queue and
    // transfer any remaining items into it.  Any thread that is waiting on the
    // original queue will time out after at most a few seconds and further
    // checks will be against the new queue.
    if (newMaxCapacity != maxCapacity)
    {
      synchronized (queueLock)
      {
        try
        {
          LinkedBlockingQueue<AbstractOperation> newOpQueue;
          if (newMaxCapacity > 0)
          {
            newOpQueue =
              new LinkedBlockingQueue<AbstractOperation>(newMaxCapacity);
          }
          else
          {
            newOpQueue = new LinkedBlockingQueue<AbstractOperation>();
          }

          LinkedBlockingQueue<AbstractOperation> oldOpQueue = opQueue;
          opQueue = newOpQueue;

          LinkedList<AbstractOperation> pendingOps =
            new LinkedList<AbstractOperation>();
          oldOpQueue.drainTo(pendingOps);


          // We have to be careful when adding any existing pending operations
          // because the new capacity could be less than what was already
          // backlogged in the previous queue.  If that happens, we may have to
          // loop a few times to get everything in there.
          while (! pendingOps.isEmpty())
          {
            Iterator<AbstractOperation> iterator = pendingOps.iterator();
            while (iterator.hasNext())
            {
              AbstractOperation o = iterator.next();
              try
              {
                if (newOpQueue.offer(o, 1000, TimeUnit.MILLISECONDS))
                {
                  iterator.remove();
                }
              }
              catch (InterruptedException ie)
              {
                if (debugEnabled())
                {
                  TRACER.debugCaught(DebugLogLevel.ERROR, ie);
                }
              }
            }
          }

          maxCapacity = newMaxCapacity;
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }
        }
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
    if (opQueue.size() > 0)
    {
      return false;
    }

    synchronized (queueLock)
    {
      for (TraditionalWorkerThread t : workerThreads)
      {
        if (t.isActive())
        {
          return false;
        }
      }

      return true;
    }
  }
}

