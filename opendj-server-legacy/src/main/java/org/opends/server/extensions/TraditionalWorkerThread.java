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
 * Portions Copyright 2011-2016 ForgeRock AS.
 * Portions Copyright 2025 3A Systems, LLC.
 */
package org.opends.server.extensions;

import java.util.Map;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.DirectoryThread;
import org.opends.server.core.DirectoryServer;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.types.AbstractOperation;
import org.opends.server.types.CancelRequest;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.Operation;

import static org.opends.messages.CoreMessages.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class defines a data structure for storing and interacting with a
 * Directory Server worker thread.
 */
public class TraditionalWorkerThread
       extends DirectoryThread
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * Indicates whether the Directory Server is shutting down and this thread
   * should stop running.
   */
  private volatile boolean shutdownRequested;

  /**
   * Indicates whether this thread was stopped because the server thread number
   * was reduced.
   */
  private boolean stoppedByReducedThreadNumber;

  /** Indicates whether this thread is currently waiting for work. */
  private boolean waitingForWork;

  /** The operation that this worker thread is currently processing. */
  private volatile Operation operation;

  /** The handle to the actual thread for this worker thread. */
  private Thread workerThread;

  /** The work queue that this worker thread will service. */
  private final TraditionalWorkQueue workQueue;



  /**
   * Creates a new worker thread that will service the provided work queue and
   * process any new requests that are submitted.
   *
   * @param  workQueue  The work queue with which this worker thread is
   *                    associated.
   * @param  threadID   The thread ID for this worker thread.
   */
  public TraditionalWorkerThread(TraditionalWorkQueue workQueue, int threadID)
  {
    super("Worker Thread " + threadID);


    this.workQueue = workQueue;

    stoppedByReducedThreadNumber = false;
    shutdownRequested            = false;
    waitingForWork               = false;
    operation                    = null;
    workerThread                 = null;
  }



  /**
   * Indicates that this thread is about to be stopped because the Directory
   * Server configuration has been updated to reduce the number of worker
   * threads.
   */
  public void setStoppedByReducedThreadNumber()
  {
    stoppedByReducedThreadNumber = true;
  }



  /**
   * Indicates whether this worker thread is actively processing a request.
   * Note that this is a point-in-time determination and if a reliable answer is
   * expected then the server should impose some external constraint to ensure
   * that no new requests are enqueued.
   *
   * @return  {@code true} if this worker thread is actively processing a
   *          request, or {@code false} if it is idle.
   */
  public boolean isActive()
  {
    return isAlive() && operation != null;
  }



  /**
   * Operates in a loop, retrieving the next request from the work queue,
   * processing it, and then going back to the queue for more.
   */
  @Override
  public void run()
  {
    workerThread = currentThread();

    while (! shutdownRequested)
    {
      try
      {
        waitingForWork = true;
        operation = null; // this line is necessary because next line can block
        operation = workQueue.nextOperation(this);
        waitingForWork = false;


        if (operation == null)
        {
          // The operation may be null if the server is shutting down.  If that
          // is the case, then break out of the while loop.
          break;
        }
        else
        {
          // The operation is not null, so process it.  Make sure that when
          // processing is complete.

          //check has transactionId control
          ClientConnection.Transaction transaction=null;
          if (operation instanceof AbstractOperation) {
            String transactionId = ((AbstractOperation) operation).getTransactionId();
            if (transactionId!=null){
              transaction=operation.getClientConnection().getTransaction(transactionId);
              if (transaction==null){ //unknown transactionId
                operation.setResultCode(ResultCode.CANCELLED);
                operation.appendErrorMessage(LocalizableMessage.raw("unknown transactionId="+transactionId));
                operation.getClientConnection().sendResponse(operation);
                continue;
              }
            }
          }
          if (transaction==null) {  //run
            operation.run();
            operation.operationCompleted();
          }else { //suspend for commit
            transaction.add(operation);
            operation.setResultCode(ResultCode.SUCCESS);
            operation.getClientConnection().sendResponse(operation);
          }
        }
      }
      catch (Throwable t)
      {
        if (logger.isTraceEnabled())
        {
          logger.trace(
            "Uncaught exception in worker thread while processing " +
                "operation %s: %s", operation, t);
          logger.traceException(t);
        }

        try
        {
          LocalizableMessage message =
              ERR_UNCAUGHT_WORKER_THREAD_EXCEPTION.get(getName(), operation, stackTraceToSingleLineString(t));
          logger.error(message);

          // Ensure that the client receives some kind of result so that it does
          // not hang.
          operation.setResultCode(DirectoryServer.getCoreConfigManager().getServerErrorResultCode());
          operation.appendErrorMessage(message);
          operation.getClientConnection().sendResponse(operation);
        }
        catch (Throwable t2)
        {
          if (logger.isTraceEnabled())
          {
            logger.trace(
              "Exception in worker thread while trying to log a " +
                  "message about an uncaught exception %s: %s", t, t2);

            logger.traceException(t2);
          }
        }


        try
        {
          LocalizableMessage message = ERR_UNCAUGHT_WORKER_THREAD_EXCEPTION.get(
              getName(), operation, stackTraceToSingleLineString(t));

          operation.disconnectClient(DisconnectReason.SERVER_ERROR, true, message);
        }
        catch (Throwable t2)
        {
          logger.traceException(t2);
        }
      }
    }

    // If we have gotten here, then we presume that the server thread is
    // shutting down.  However, if that's not the case then that is a problem
    // and we will want to log a message.
    if (stoppedByReducedThreadNumber)
    {
      logger.debug(INFO_WORKER_STOPPED_BY_REDUCED_THREADNUMBER, getName());
    }
    else if (! workQueue.shutdownRequested())
    {
      logger.warn(WARN_UNEXPECTED_WORKER_THREAD_EXIT, getName());
    }


    if (logger.isTraceEnabled())
    {
      logger.trace(getName() + " exiting.");
    }
  }



  /**
   * Indicates that the Directory Server has received a request to stop running
   * and that this thread should stop running as soon as possible.
   */
  public void shutDown()
  {
    if (logger.isTraceEnabled())
    {
      logger.trace(getName() + " being signaled to shut down.");
    }

    // Set a flag that indicates that the thread should stop running.
    shutdownRequested = true;


    // Check to see if the thread is waiting for work.  If so, then interrupt
    // it.
    if (waitingForWork)
    {
      try
      {
        workerThread.interrupt();
      }
      catch (Exception e)
      {
        if (logger.isTraceEnabled())
        {
          logger.trace(
            "Caught an exception while trying to interrupt the worker " +
                "thread waiting for work: %s", e);
          logger.traceException(e);
        }
      }
    }
    else
    {
      try
      {
        final Operation localOperation = operation;
        if (localOperation != null)
        {
          CancelRequest cancelRequest = new CancelRequest(true,
              INFO_CANCELED_BY_SHUTDOWN.get());
          localOperation.cancel(cancelRequest);
        }
      }
      catch (Exception e)
      {
        if (logger.isTraceEnabled())
        {
          logger.trace(
            "Caught an exception while trying to abandon the " +
                "operation in progress for the worker thread: %s", e);
          logger.traceException(e);
        }
      }
    }
  }

  /**
   * Retrieves any relevant debug information with which this tread is
   * associated so they can be included in debug messages.
   *
   * @return debug information about this thread as a string.
   */
  @Override
  public Map<String, String> getDebugProperties()
  {
    Map<String, String> properties = super.getDebugProperties();
    properties.put("clientConnection", operation != null
        ? String.valueOf(operation.getClientConnection()) : "none");
    properties.put("operation", String.valueOf(operation));
    return properties;
  }
}

