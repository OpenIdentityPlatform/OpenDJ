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
 * Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.core;

import java.util.concurrent.atomic.AtomicInteger;

import org.opends.server.types.DirectoryException;
import org.opends.server.types.Operation;

/**
 * A QueueingStrategy that concurrently enqueues a bounded number of operations
 * to the DirectoryServer work queue. If the maximum number of concurrently
 * enqueued operations has been reached or if the work queue if full, then the
 * operation will be executed on the current thread.
 */
public class BoundedWorkQueueStrategy implements QueueingStrategy
{
  /** The number of concurrently running operations for this BoundedWorkQueueStrategy. */
  private final AtomicInteger nbRunningOperations = new AtomicInteger(0);
  /** Maximum number of concurrent operations. 0 means "unlimited". */
  private final int maxNbConcurrentOperations;

  /**
   * Constructor for BoundedWorkQueueStrategy.
   *
   * @param maxNbConcurrentOperations
   *          the maximum number of operations that can be concurrently enqueued
   *          to the DirectoryServer work queue
   */
  public BoundedWorkQueueStrategy(Integer maxNbConcurrentOperations)
  {
    if (maxNbConcurrentOperations != null)
    {
      this.maxNbConcurrentOperations = maxNbConcurrentOperations;
    }
    else
    {
      int cpus = Runtime.getRuntime().availableProcessors();
      this.maxNbConcurrentOperations =
          Math.max(cpus, getNumWorkerThreads() * 25 / 100);
    }
  }

  /**
   * Return the maximum number of worker threads that can be used by the
   * WorkQueue (The WorkQueue could have a thread pool which adjusts its size).
   *
   * @return the maximum number of worker threads that can be used by the
   *         WorkQueue
   */
  protected int getNumWorkerThreads()
  {
    return DirectoryServer.getWorkQueue().getNumWorkerThreads();
  }

  @Override
  public void enqueueRequest(final Operation operation)
      throws DirectoryException
  {
    if (!operation.getClientConnection().isConnectionValid())
    {
      // do not bother enqueueing
      return;
    }

    if (maxNbConcurrentOperations == 0)
    { // unlimited concurrent operations
      if (!tryEnqueueRequest(operation))
      { // avoid potential deadlocks by running in the current thread
        operation.run();
      }
    }
    else if (nbRunningOperations.getAndIncrement() > maxNbConcurrentOperations
        || !tryEnqueueRequest(wrap(operation)))
    { // avoid potential deadlocks by running in the current thread
      try
      {
        operation.run();
      }
      finally
      {
        // only decrement when the operation is run synchronously.
        // Otherwise it'll be decremented twice (once more in the wrapper).
        nbRunningOperations.decrementAndGet();
      }
    }
  }

  /**
   * Tries to add the provided operation to the work queue if not full so that
   * it will be processed by one of the worker threads.
   *
   * @param op
   *          The operation to be added to the work queue.
   * @return true if the operation could be enqueued, false otherwise
   * @throws DirectoryException
   *           If a problem prevents the operation from being added to the queue
   *           (e.g., the queue is full).
   */
  protected boolean tryEnqueueRequest(Operation op) throws DirectoryException
  {
    return DirectoryServer.tryEnqueueRequest(op);
  }

  private Operation wrap(final Operation operation)
  {
    if (operation instanceof AbandonOperation)
    {
      return new AbandonOperationWrapper((AbandonOperation) operation)
      {
        @Override
        public void run()
        {
          runWrapped(operation);
        }
      };
    }
    else if (operation instanceof AddOperation)
    {
      return new AddOperationWrapper((AddOperation) operation)
      {
        @Override
        public void run()
        {
          runWrapped(operation);
        }
      };
    }
    else if (operation instanceof BindOperation)
    {
      return new BindOperationWrapper((BindOperation) operation)
      {
        @Override
        public void run()
        {
          runWrapped(operation);
        }
      };
    }
    else if (operation instanceof CompareOperation)
    {
      return new CompareOperationWrapper((CompareOperation) operation)
      {
        @Override
        public void run()
        {
          runWrapped(operation);
        }
      };
    }
    else if (operation instanceof DeleteOperation)
    {
      return new DeleteOperationWrapper((DeleteOperation) operation)
      {
        @Override
        public void run()
        {
          runWrapped(operation);
        }
      };
    }
    else if (operation instanceof ExtendedOperation)
    {
      return new ExtendedOperationWrapper((ExtendedOperation) operation)
      {
        @Override
        public void run()
        {
          runWrapped(operation);
        }
      };
    }
    else if (operation instanceof ModifyDNOperation)
    {
      return new ModifyDNOperationWrapper((ModifyDNOperation) operation)
      {
        @Override
        public void run()
        {
          runWrapped(operation);
        }
      };
    }
    else if (operation instanceof ModifyOperation)
    {
      return new ModifyOperationWrapper((ModifyOperation) operation)
      {
        @Override
        public void run()
        {
          runWrapped(operation);
        }
      };
    }
    else if (operation instanceof SearchOperation)
    {
      return new SearchOperationWrapper((SearchOperation) operation)
      {
        @Override
        public void run()
        {
          runWrapped(operation);
        }
      };
    }
    else if (operation instanceof UnbindOperation)
    {
      return new UnbindOperationWrapper((UnbindOperation) operation)
      {
        @Override
        public void run()
        {
          runWrapped(operation);
        }
      };
    }
    else
    {
      throw new RuntimeException(
          "Not implemented for " + operation == null ? null : operation
              .getClass().getName());
    }
  }

  /**
   * Execute the provided operation and decrement the number of currently
   * running operations after it has finished executing.
   *
   * @param the
   *          operation to execute
   */
  private void runWrapped(final Operation operation)
  {
    try
    {
      operation.run();
    }
    finally
    {
      nbRunningOperations.decrementAndGet();
    }
  }
}
