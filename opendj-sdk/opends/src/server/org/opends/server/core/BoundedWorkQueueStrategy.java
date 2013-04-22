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
 *      Copyright 2013 ForgeRock AS
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

  /**
   * The number of concurrently running operations for this
   * BoundedWorkQueueStrategy.
   */
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
      int numWorkerThreads =
          DirectoryServer.getWorkQueue().getNumWorkerThreads();
      this.maxNbConcurrentOperations =
          Math.max(cpus, numWorkerThreads * 25 / 100);
    }
  }

  /** {@inheritDoc} */
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
      if (!DirectoryServer.tryEnqueueRequest(operation))
      { // avoid potential deadlocks by running in the current thread
        operation.run();
      }
    }
    else if (nbRunningOperations.getAndIncrement() > maxNbConcurrentOperations
        || !DirectoryServer.tryEnqueueRequest(wrap(operation)))
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
