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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;



import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import org.opends.server.api.ConfigurableComponent;
import org.opends.server.api.WorkQueue;
import org.opends.server.config.ConfigAttribute;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.config.IntegerConfigAttribute;
import org.opends.server.core.CancelRequest;
import org.opends.server.core.DirectoryException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.InitializationException;
import org.opends.server.core.Operation;
import org.opends.server.monitors.TraditionalWorkQueueMonitor;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.ResultCode;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.Debug.*;
import static org.opends.server.loggers.Error.*;
import static org.opends.server.messages.ConfigMessages.*;
import static org.opends.server.messages.CoreMessages.*;
import static org.opends.server.messages.MessageHandler.*;



/**
 * This class defines a data structure for storing and interacting with the
 * Directory Server work queue.
 */
public class TraditionalWorkQueue
       extends WorkQueue
       implements ConfigurableComponent
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME = "org.opends.server.core.WorkQueue";



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
  private LinkedBlockingQueue<Operation> opQueue;

  // The lock used to provide threadsafe access for the queue.
  private ReentrantLock queueLock;



  /**
   * Creates a new instance of this work queue.  All initialization should be
   * performed in the <CODE>initializeWorkQueue</CODE> method.
   */
  public TraditionalWorkQueue()
  {
    assert debugConstructor(CLASS_NAME);

    // No implementation should be performed here.
  }



  /**
   * Initializes this work queue based on the information in the provided
   * configuration entry.
   *
   * @param  configEntry  The configuration entry that contains the information
   *                      to use to initialize this work queue.
   *
   * @throws  ConfigException  If the provided configuration entry does not have
   *                           a valid work queue configuration.
   *
   * @throws  InitializationException  If a problem occurs during initialization
   *                                   that is not related to the server
   *                                   configuration.
   */
  public void initializeWorkQueue(ConfigEntry configEntry)
         throws ConfigException, InitializationException
  {
    assert debugEnter(CLASS_NAME, "initializeWorkQueue",
                      String.valueOf(configEntry));


    shutdownRequested = false;
    killThreads       = false;
    opsSubmitted      = new AtomicLong(0);
    queueFullRejects  = new AtomicLong(0);
    queueLock         = new ReentrantLock();


    // Get the necessary configuration from the provided entry.
    configEntryDN = configEntry.getDN();

    int msgID = MSGID_CONFIG_WORK_QUEUE_DESCRIPTION_NUM_THREADS;
    IntegerConfigAttribute numThreadsStub =
      new IntegerConfigAttribute(ATTR_NUM_WORKER_THREADS, getMessage(msgID),
                                 true, false, false, true, 1, false, 0,
                                 DEFAULT_NUM_WORKER_THREADS);
    try
    {
      IntegerConfigAttribute numThreadsAttr =
        (IntegerConfigAttribute)
        configEntry.getConfigAttribute(numThreadsStub);
      if (numThreadsAttr == null)
      {
        numWorkerThreads = DEFAULT_NUM_WORKER_THREADS;
      }
      else
      {
        numWorkerThreads = numThreadsAttr.activeIntValue();
        if (numWorkerThreads <= 0)
        {
          //This is not valid.  The number of worker threads must be a positive
          // integer.
          msgID = MSGID_CONFIG_WORK_QUEUE_NUM_THREADS_INVALID_VALUE;
          String message = getMessage(msgID,
                                      String.valueOf(configEntryDN),
                                      numWorkerThreads);
          logError(ErrorLogCategory.CONFIGURATION,
                   ErrorLogSeverity.SEVERE_WARNING, message, msgID);
          numWorkerThreads = DEFAULT_NUM_WORKER_THREADS;
        }
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "<init>", e);

      msgID = MSGID_CONFIG_WORK_QUEUE_CANNOT_DETERMINE_NUM_WORKER_THREADS;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  String.valueOf(e));
      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
               message, msgID);

      numWorkerThreads = DEFAULT_NUM_WORKER_THREADS;
    }


    msgID = MSGID_CONFIG_WORK_QUEUE_DESCRIPTION_MAX_CAPACITY;
    IntegerConfigAttribute capacityStub =
      new IntegerConfigAttribute(ATTR_MAX_WORK_QUEUE_CAPACITY,
                                 getMessage(msgID), true, false, false, true,
                                 0, false, 0,
                                 DEFAULT_MAX_WORK_QUEUE_CAPACITY);
    try
    {
      IntegerConfigAttribute capacityAttr =
        (IntegerConfigAttribute)
        configEntry.getConfigAttribute(capacityStub);
      if (capacityAttr == null)
      {
        maxCapacity = DEFAULT_MAX_WORK_QUEUE_CAPACITY;
      }
      else
      {
        maxCapacity = capacityAttr.activeIntValue();
        if (maxCapacity < 0)
        {
          // This is not valid.  The maximum capacity must be greater than or
          // equal to zero.
          msgID = MSGID_CONFIG_WORK_QUEUE_CAPACITY_INVALID_VALUE;
          String message = getMessage(msgID, String.valueOf(configEntryDN),
                                      maxCapacity);
          logError(ErrorLogCategory.CONFIGURATION,
                   ErrorLogSeverity.SEVERE_WARNING, message, msgID);

          maxCapacity = DEFAULT_MAX_WORK_QUEUE_CAPACITY;
        }
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "<init>", e);

      msgID = MSGID_CONFIG_WORK_QUEUE_CANNOT_DETERMINE_QUEUE_CAPACITY;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  String.valueOf(e));
      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
               message, msgID);

      maxCapacity = DEFAULT_MAX_WORK_QUEUE_CAPACITY;
    }


    // Create the actual work queue.
    if (maxCapacity > 0)
    {
      opQueue = new LinkedBlockingQueue<Operation>(maxCapacity);
    }
    else
    {
      opQueue = new LinkedBlockingQueue<Operation>();
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


    // Register with the Directory Server as a configurable component.
    DirectoryServer.registerConfigurableComponent(this);


    // Create and register a monitor provider for the work queue.
    try
    {
      TraditionalWorkQueueMonitor monitor =
           new TraditionalWorkQueueMonitor(this);
      monitor.initializeMonitorProvider(configEntry);
      monitor.start();
      DirectoryServer.registerMonitorProvider(monitor);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "<init>", e);

      msgID = MSGID_CONFIG_WORK_QUEUE_CANNOT_CREATE_MONITOR;
      String message = getMessage(msgID, TraditionalWorkQueueMonitor.class,
                                  String.valueOf(e));
      logError(ErrorLogCategory.CORE_SERVER, ErrorLogSeverity.SEVERE_ERROR,
               message, msgID);
    }
  }



  /**
   * Performs any necessary finalization for this work queue,
   * including ensuring that all active operations are interrupted or
   * will be allowed to complete, and that all pending operations will
   * be cancelled.
   *
   * @param  reason  The human-readable reason that the work queue is being
   *                 shut down.
   */
  public void finalizeWorkQueue(String reason)
  {
    assert debugEnter(CLASS_NAME, "finalizeWorkQueue");

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
        o.cancel(cancelRequest);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "processServerShutdown", e);

        logError(ErrorLogCategory.CORE_SERVER, ErrorLogSeverity.SEVERE_WARNING,
                 MSGID_QUEUE_UNABLE_TO_CANCEL, String.valueOf(o),
                 String.valueOf(e));
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
        assert debugException(CLASS_NAME, "processServerShutdown", e);

        logError(ErrorLogCategory.CORE_SERVER, ErrorLogSeverity.SEVERE_WARNING,
                 MSGID_QUEUE_UNABLE_TO_NOTIFY_THREAD, t.getName(),
                 String.valueOf(e));
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
    assert debugEnter(CLASS_NAME, "shutdownRequested");

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
  public void submitOperation(Operation operation)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "submitOperation", String.valueOf(operation));

    if (shutdownRequested)
    {
      int    messageID = MSGID_OP_REJECTED_BY_SHUTDOWN;
      String message   = getMessage(messageID);
      throw new DirectoryException(ResultCode.UNAVAILABLE, message, messageID);
    }

    if (! opQueue.offer(operation))
    {
      queueFullRejects.incrementAndGet();

      int    messageID = MSGID_OP_REJECTED_BY_QUEUE_FULL;
      String message   = getMessage(messageID, maxCapacity);
      throw new DirectoryException(ResultCode.UNAVAILABLE, message, messageID);
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
  public Operation nextOperation(TraditionalWorkerThread workerThread)
  {
    assert debugEnter(CLASS_NAME, "nextOperation");

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
  private Operation retryNextOperation(TraditionalWorkerThread workerThread,
                                       int numFailures)
  {
    assert debugEnter(CLASS_NAME, "retryNextOperation");


    // See if we should kill off this thread.  This could be necessary if the
    // number of worker threads has been decreased with the server online.  If
    // so, then return null and the thread will exit.
    if (killThreads)
    {
      queueLock.lock();

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
        assert debugException(CLASS_NAME, "retryNextOperation", e);
      }
      finally
      {
        queueLock.unlock();
      }
    }

    if ((shutdownRequested) || (numFailures > MAX_RETRY_COUNT))
    {
      if (numFailures > MAX_RETRY_COUNT)
      {
        int msgID = MSGID_CONFIG_WORK_QUEUE_TOO_MANY_FAILURES;
        String message = getMessage(msgID, Thread.currentThread().getName(),
                                    numFailures, MAX_RETRY_COUNT);
        logError(ErrorLogCategory.CORE_SERVER, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
      }

      return null;
    }

    try
    {
      while (true)
      {
        Operation nextOperation = opQueue.poll(5, TimeUnit.SECONDS);
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
            queueLock.lock();

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
              assert debugException(CLASS_NAME, "retryNextOperation", e);
            }
            finally
            {
              queueLock.unlock();
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
      assert debugException(CLASS_NAME, "retryNextOperation", ie);

      // If this occurs, then the worker thread must have been interrupted for
      // some reason.  This could be because the Directory Server is shutting
      // down, in which case we should return null.
      if (shutdownRequested)
      {
        return null;
      }

      // If we've gotten here, then the worker thread was interrupted for some
      // other reason.  This should not happen, and we need to log a message.
      logError(ErrorLogCategory.CORE_SERVER, ErrorLogSeverity.SEVERE_WARNING,
               MSGID_WORKER_INTERRUPTED_WITHOUT_SHUTDOWN,
               Thread.currentThread().getName(), String.valueOf(ie));
      return retryNextOperation(workerThread, numFailures+1);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "nextOperation", e);

      // This should not happen.  The only recourse we have is to log a message
      // and try again.
      logError(ErrorLogCategory.CORE_SERVER, ErrorLogSeverity.SEVERE_WARNING,
               MSGID_WORKER_WAITING_UNCAUGHT_EXCEPTION,
               Thread.currentThread().getName(), String.valueOf(e));
      return retryNextOperation(workerThread, numFailures+1);
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
    assert debugEnter(CLASS_NAME, "removeOperation", String.valueOf(operation));

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
    assert debugEnter(CLASS_NAME, "getOpsSubmitted");

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
    assert debugEnter(CLASS_NAME, "getOpsRejectedDueToQueueFull");

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
    assert debugEnter(CLASS_NAME, "size");

    return opQueue.size();
  }



  /**
   * Retrieves the DN of the configuration entry with which this component is
   * associated.
   *
   * @return  The DN of the configuration entry with which this component is
   *          associated.
   */
  public DN getConfigurableComponentEntryDN()
  {
    assert debugEnter(CLASS_NAME, "getConfigurableComponentEntryDN");

    return configEntryDN;
  }



  /**
   * Retrieves the set of configuration attributes that are associated with this
   * configurable component.
   *
   * @return  The set of configuration attributes that are associated with this
   *          configurable component.
   */
  public List<ConfigAttribute> getConfigurationAttributes()
  {
    assert debugEnter(CLASS_NAME, "getConfigurationAttributes");

    LinkedList<ConfigAttribute> attrList = new LinkedList<ConfigAttribute>();


    int msgID = MSGID_CONFIG_WORK_QUEUE_DESCRIPTION_NUM_THREADS;
    IntegerConfigAttribute numThreadsAttr =
      new IntegerConfigAttribute(ATTR_NUM_WORKER_THREADS, getMessage(msgID),
                                 true, false, false, true, 1, false, 0,
                                 workerThreads.size());
    attrList.add(numThreadsAttr);


    msgID = MSGID_CONFIG_WORK_QUEUE_DESCRIPTION_MAX_CAPACITY;
    IntegerConfigAttribute capacityAttr =
      new IntegerConfigAttribute(ATTR_MAX_WORK_QUEUE_CAPACITY,
                                 getMessage(msgID), true, false, false, true,
                                 0, false, 0, maxCapacity);
    attrList.add(capacityAttr);


    return attrList;
  }



  /**
   * Indicates whether the provided configuration entry has an acceptable
   * configuration for this component.  If it does not, then detailed
   * information about the problem(s) should be added to the provided list.
   *
   * @param  configEntry          The configuration entry for which to make the
   *                              determination.
   * @param  unacceptableReasons  A list that can be used to hold messages about
   *                              why the provided entry does not have an
   *                              acceptable configuration.
   *
   * @return  <CODE>true</CODE> if the provided entry has an acceptable
   *          configuration for this component, or <CODE>false</CODE> if not.
   */
  public boolean hasAcceptableConfiguration(ConfigEntry configEntry,
                                            List<String> unacceptableReasons)
  {
    assert debugEnter(CLASS_NAME, "hasAcceptableConfiguration",
                      String.valueOf(configEntry), "java.util.List");


    boolean configIsAcceptable = true;


    // Check the configuration for the number of worker threads.
    int msgID = MSGID_CONFIG_WORK_QUEUE_DESCRIPTION_NUM_THREADS;
    IntegerConfigAttribute numThreadsStub =
      new IntegerConfigAttribute(ATTR_NUM_WORKER_THREADS, getMessage(msgID),
                                 true, false, false, true, 1, false, 0,
                                 workerThreads.size());
    try
    {
      IntegerConfigAttribute numThreadsAttr =
        (IntegerConfigAttribute)
        configEntry.getConfigAttribute(numThreadsStub);
      if (numThreadsAttr == null)
      {
        // This means that the entry doesn't contain the attribute.  This is
        // fine, since we'll just use the default.
      }
      else
      {
        int numWorkerThreads = numThreadsAttr.activeIntValue();
        if (numWorkerThreads <= 0)
        {
          //This is not valid.  The number of worker threads must be a positive
          // integer.
          msgID = MSGID_CONFIG_WORK_QUEUE_NUM_THREADS_INVALID_VALUE;
          String message = getMessage(msgID,
                                      String.valueOf(configEntryDN),
                                      numWorkerThreads);
          unacceptableReasons.add(message);
          configIsAcceptable = false;
        }
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "<init>", e);

      msgID = MSGID_CONFIG_WORK_QUEUE_CANNOT_DETERMINE_NUM_WORKER_THREADS;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  String.valueOf(e));
      unacceptableReasons.add(message);
      configIsAcceptable = false;
    }


    // Check the configuration for the maximum work queue capacity.
    msgID = MSGID_CONFIG_WORK_QUEUE_DESCRIPTION_MAX_CAPACITY;
    IntegerConfigAttribute capacityStub =
      new IntegerConfigAttribute(ATTR_MAX_WORK_QUEUE_CAPACITY,
                                 getMessage(msgID), true, false, false, true,
                                 0, false, 0,
                                 maxCapacity);
    try
    {
      IntegerConfigAttribute capacityAttr =
        (IntegerConfigAttribute)
        configEntry.getConfigAttribute(capacityStub);
      if (capacityAttr == null)
      {
        //This means that the entry doesn't contain the attribute.  This is
        // fine, since we'll just use the default.
      }
      else
      {
        int newMaxCapacity = capacityAttr.activeIntValue();
        if (newMaxCapacity < 0)
        {
          // This is not valid.  The maximum capacity must be greater than or
          // equal to zero.
          msgID = MSGID_CONFIG_WORK_QUEUE_CAPACITY_INVALID_VALUE;
          String message = getMessage(msgID, String.valueOf(configEntryDN),
                                      newMaxCapacity);
          unacceptableReasons.add(message);
          configIsAcceptable = false;
        }
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "<init>", e);

      msgID = MSGID_CONFIG_WORK_QUEUE_CANNOT_DETERMINE_QUEUE_CAPACITY;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  String.valueOf(e));
      unacceptableReasons.add(message);
      configIsAcceptable = false;
    }


    return configIsAcceptable;
  }



  /**
   * Makes a best-effort attempt to apply the configuration contained in the
   * provided entry.  Information about the result of this processing should be
   * added to the provided message list.  Information should always be added to
   * this list if a configuration change could not be applied.  If detailed
   * results are requested, then information about the changes applied
   * successfully (and optionally about parameters that were not changed) should
   * also be included.
   *
   * @param  configEntry      The entry containing the new configuration to
   *                          apply for this component.
   * @param  detailedResults  Indicates whether detailed information about the
   *                          processing should be added to the list.
   *
   * @return  Information about the result of the configuration update.
   */
  public ConfigChangeResult applyNewConfiguration(ConfigEntry configEntry,
                                                  boolean detailedResults)
  {
    assert debugEnter(CLASS_NAME, "applyNewConfiguration",
                      String.valueOf(configEntry),
                      String.valueOf(detailedResults));


    ArrayList<String> resultMessages = new ArrayList<String>();
    int newNumThreads;
    int newMaxCapacity;


    // Check the configuration for the number of worker threads.
    int msgID = MSGID_CONFIG_WORK_QUEUE_DESCRIPTION_NUM_THREADS;
    IntegerConfigAttribute numThreadsStub =
      new IntegerConfigAttribute(ATTR_NUM_WORKER_THREADS, getMessage(msgID),
                                 true, false, false, true, 1, false, 0,
                                 workerThreads.size());
    try
    {
      IntegerConfigAttribute numThreadsAttr =
        (IntegerConfigAttribute)
        configEntry.getConfigAttribute(numThreadsStub);
      if (numThreadsAttr == null)
      {
        // This means that the entry doesn't contain the attribute.  This is
        // fine, since we'll just use the default.
        newNumThreads = DEFAULT_NUM_WORKER_THREADS;
      }
      else
      {
        newNumThreads = numThreadsAttr.activeIntValue();
        if (newNumThreads <= 0)
        {
          //This is not valid.  The number of worker threads must be a positive
          // integer.  This should never happen since it should be filtered out
          // by the hasAcceptableConfiguration method, but if it does for some
          // reason then handle it.
          msgID = MSGID_CONFIG_WORK_QUEUE_NUM_THREADS_INVALID_VALUE;
          String message = getMessage(msgID,
                                      String.valueOf(configEntryDN),
                                      newNumThreads);
          resultMessages.add(message);
          newNumThreads = DEFAULT_NUM_WORKER_THREADS;
        }
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyNewConfiguration", e);

      msgID = MSGID_CONFIG_WORK_QUEUE_CANNOT_DETERMINE_NUM_WORKER_THREADS;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  String.valueOf(e));
      resultMessages.add(message);
      newNumThreads = DEFAULT_NUM_WORKER_THREADS;
    }


    // Check the configuration for the maximum work queue capacity.
    msgID = MSGID_CONFIG_WORK_QUEUE_DESCRIPTION_MAX_CAPACITY;
    IntegerConfigAttribute capacityStub =
      new IntegerConfigAttribute(ATTR_MAX_WORK_QUEUE_CAPACITY,
                                 getMessage(msgID), true, false, false, true,
                                 0, false, 0,
                                 maxCapacity);
    try
    {
      IntegerConfigAttribute capacityAttr =
        (IntegerConfigAttribute)
        configEntry.getConfigAttribute(capacityStub);
      if (capacityAttr == null)
      {
        //This means that the entry doesn't contain the attribute.  This is
        // fine, since we'll just use the default.
        newMaxCapacity = DEFAULT_MAX_WORK_QUEUE_CAPACITY;
      }
      else
      {
        newMaxCapacity = capacityAttr.activeIntValue();
        if (newMaxCapacity < 0)
        {
          // This is not valid.  The maximum capacity must be greater than or
          // equal to zero.
          msgID = MSGID_CONFIG_WORK_QUEUE_CAPACITY_INVALID_VALUE;
          String message = getMessage(msgID, String.valueOf(configEntryDN),
                                      newMaxCapacity);
          resultMessages.add(message);
          newMaxCapacity = DEFAULT_MAX_WORK_QUEUE_CAPACITY;
        }
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyNewConfiguration", e);

      msgID = MSGID_CONFIG_WORK_QUEUE_CANNOT_DETERMINE_QUEUE_CAPACITY;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  String.valueOf(e));
      resultMessages.add(message);
      newMaxCapacity = DEFAULT_MAX_WORK_QUEUE_CAPACITY;
    }


    // Apply a change to the number of worker threads if appropriate.
    int currentThreads = workerThreads.size();
    if (newNumThreads != currentThreads)
    {
      queueLock.lock();

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

          if (detailedResults)
          {
            msgID = MSGID_CONFIG_WORK_QUEUE_CREATED_THREADS;
            String message = getMessage(msgID, threadsToAdd, newNumThreads);
            resultMessages.add(message);
          }

          killThreads = false;
        }
        else
        {
          if (detailedResults)
          {
            msgID = MSGID_CONFIG_WORK_QUEUE_DESTROYING_THREADS;
            String message = getMessage(msgID, Math.abs(threadsToAdd),
                                        newNumThreads);
            resultMessages.add(message);
          }

          killThreads = true;
        }

        numWorkerThreads = newNumThreads;
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "applyNewConfiguration", e);
      }
      finally
      {
        queueLock.unlock();
      }
    }


    // Apply a change to the maximum capacity if appropriate.  Since we can't
    // change capacity on the fly, then we'll have to create a new queue and
    // transfer any remaining items into it.  Any thread that is waiting on the
    // original queue will time out after at most a few seconds and further
    // checks will be against the new queue.
    if (newMaxCapacity != maxCapacity)
    {
      queueLock.lock();

      try
      {
        LinkedBlockingQueue<Operation> newOpQueue;
        if (newMaxCapacity > 0)
        {
          newOpQueue = new LinkedBlockingQueue<Operation>(newMaxCapacity);
        }
        else
        {
          newOpQueue = new LinkedBlockingQueue<Operation>();
        }

        LinkedBlockingQueue<Operation> oldOpQueue = opQueue;
        opQueue = newOpQueue;

        LinkedList<Operation> pendingOps = new LinkedList<Operation>();
        oldOpQueue.drainTo(pendingOps);


        // We have to be careful when adding any existing pending operations
        // because the new capacity could be less than what was already
        // backlogged in the previous queue.  If that happens, we may have to
        // loop a few times to get everything in there.
        while (! pendingOps.isEmpty())
        {
          Iterator<Operation> iterator = pendingOps.iterator();
          while (iterator.hasNext())
          {
            Operation o = iterator.next();
            try
            {
              if (newOpQueue.offer(o, 1000, TimeUnit.MILLISECONDS))
              {
                iterator.remove();
              }
            }
            catch (InterruptedException ie)
            {
              assert debugException(CLASS_NAME, "applyNewConfiguration", ie);
            }
          }
        }

        if (detailedResults)
        {
          msgID = MSGID_CONFIG_WORK_QUEUE_NEW_CAPACITY;
          String message = getMessage(msgID, newMaxCapacity);
          resultMessages.add(message);
        }

        maxCapacity = newMaxCapacity;
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "applyNewConfiguration", e);
      }
      finally
      {
        queueLock.unlock();
      }
    }


    return new ConfigChangeResult(ResultCode.SUCCESS, false, resultMessages);
  }
}

