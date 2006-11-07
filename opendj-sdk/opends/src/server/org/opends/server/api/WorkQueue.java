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
package org.opends.server.api;



import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.Operation;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;

import static org.opends.server.loggers.Debug.*;



/**
 * This class defines the structure and methods that must be
 * implemented by a Directory Server work queue.  The work queue is
 * the component of the server that accepts requests from connection
 * handlers and ensures that they are properly processed.  The manner
 * in which the work queue is able to accomplish this may vary between
 * implementations, but in general it is assumed that one or more
 * worker threads will be associated with the queue and may be used to
 * process requests in parallel.
 */
public abstract class WorkQueue
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.api.WorkQueue";



  /**
   * Initializes this work queue based on the information in the
   * provided configuration entry.
   *
   * @param  configEntry  The configuration entry that contains the
   *                      information to use to initialize this work
   *                      queue.
   *
   * @throws  ConfigException  If the provided configuration entry
   *                           does not have a valid work queue
   *                           configuration.
   *
   * @throws  InitializationException  If a problem occurs during
   *                                   initialization that is not
   *                                   related to the server
   *                                   configuration.
   */
  public abstract void initializeWorkQueue(ConfigEntry configEntry)
         throws ConfigException, InitializationException;



  /**
   * Performs any necessary finalization for this work queue,
   * including ensuring that all active operations are interrupted or
   * will be allowed to complete, and that all pending operations will
   * be cancelled.
   *
   * @param  reason  The human-readable reason that the work queue is
   *                 being shut down.
   */
  public abstract void finalizeWorkQueue(String reason);



  /**
   * Submits an operation to be processed in the server.
   *
   * @param  operation  The operation to be processed.
   *
   * @throws  DirectoryException  If the provided operation is not
   *                              accepted for some reason (e.g., if
   *                              the server is shutting down or
   *                              already has too many pending
   *                              requests in the queue).
   */
  public abstract void submitOperation(Operation operation)
         throws DirectoryException;



  /**
   * Indicates whether the work queue is currently processing any
   * requests.  Note that this is a point-in-time determination, and
   * if any component of the server wishes to depend on a quiescent
   * state then it should use some external mechanism to ensure that
   * no other requests are submitted to the queue.
   *
   * @return  {@code true} if the work queue is currently idle, or
   *          {@code false} if it is being used to process one or more
   *          operations.
   */
  public abstract boolean isIdle();



  /**
   * Waits for the work queue to become idle before returning.  Note
   * that this is a point-in-time determination, and if any component
   * of the server wishes to depend on a quiescent state then it
   * should use some external mechanism to ensure that no other
   * requests are submitted to the queue.
   *
   * @param  timeLimit  The maximum length of time in milliseconds
   *                    that this method should wait for the queue to
   *                    become idle before giving up.  A time limit
   *                    that is less than or equal to zero indicates
   *                    that there should not be a time limit.
   *
   * @return  {@code true} if the work queue is idle at the time that
   *          this method returns, or {@code false} if the wait time
   *          limit was reached before the server became idle.
   */
  public boolean waitUntilIdle(long timeLimit)
  {
    assert debugEnter(CLASS_NAME, "waitUntilIdle",
                      String.valueOf(timeLimit));

    long stopWaitingTime;
    if (timeLimit <= 0)
    {
      stopWaitingTime = Long.MAX_VALUE;
    }
    else
    {
      stopWaitingTime = System.currentTimeMillis() + timeLimit;
    }

    while (System.currentTimeMillis() < stopWaitingTime)
    {
      if (isIdle())
      {
        return true;
      }

      try
      {
        Thread.sleep(1);
      } catch (InterruptedException ie) {}
    }

    return false;
  }
}

