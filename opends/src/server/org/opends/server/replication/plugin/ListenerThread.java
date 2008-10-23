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
package org.opends.server.replication.plugin;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.opends.messages.Message;

import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import org.opends.server.api.DirectoryThread;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.replication.protocol.UpdateMsg;

/**
 * Thread that is used to get messages from the Replication servers
 * and replay them in the current server.
 */
public class ListenerThread extends DirectoryThread
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  private ReplicationDomain repDomain;
  private boolean shutdown = false;
  private boolean done = false;
  private LinkedBlockingQueue<UpdateToReplay> updateToReplayQueue;


  /**
   * Constructor for the ListenerThread.
   *
   * @param repDomain the replication domain that created this thread
   * @param updateToReplayQueue The update messages queue we must
   * store messages in
   */
  public ListenerThread(ReplicationDomain repDomain,
    LinkedBlockingQueue<UpdateToReplay> updateToReplayQueue)
  {
     super("Replication Listener for server id " + repDomain.getServerId() +
         " and domain " + repDomain.getBaseDN());
     this.repDomain = repDomain;
     this.updateToReplayQueue = updateToReplayQueue;
  }

  /**
   * Shutdown this listener thread.
   */
  public void shutdown()
  {
    shutdown = true;
  }

  /**
   * Run method for this class.
   */
  @Override
  public void run()
  {
    UpdateMsg updateMsg = null;

    if (debugEnabled())
    {
      TRACER.debugInfo("Replication Listener thread starting.");
    }

    while (!shutdown)
    {
      try
      {
        // Loop receiving update messages and puting them in the update message
        // queue
        while ((!shutdown) && ((updateMsg = repDomain.receive()) != null))
        {
          // Put update message into the queue (block until some place in the
          // queue is available)
          UpdateToReplay updateToReplay =
            new UpdateToReplay(updateMsg, repDomain);
          boolean queued = false;
          while (!queued && !shutdown)
          {
            // Use timedout method (offer) instead of put for being able to
            // shutdown the thread
            queued = updateToReplayQueue.offer(updateToReplay,
              1L, TimeUnit.SECONDS);
          }
          if (!queued)
          {
            // Shutdown requested but could not push message: ensure this one is
            // not lost and put it in the queue before dying
            updateToReplayQueue.offer(updateToReplay);
          }
        }
        if (updateMsg == null)
          shutdown = true;
      } catch (Exception e)
      {
        /*
         * catch all exceptions happening in repDomain.receive so that the
         * thread never dies even in case of problems.
         */
        Message message = ERR_EXCEPTION_RECEIVING_REPLICATION_MESSAGE.get(
            stackTraceToSingleLineString(e));
        logError(message);
      }
    }

    // Stop the HeartBeat thread
    repDomain.getBroker().stopHeartBeat();

    done = true;

    if (debugEnabled())
    {
      TRACER.debugInfo("Replication Listener thread stopping.");
    }
  }

  /**
   * Wait for the completion of this thread.
   */
  public void waitForShutdown()
  {
    try
    {
      int FACTOR = 40; // Wait for 2 seconds before interrupting the thread
      int n = 0;
      while ((done == false) && (this.isAlive()))
      {
        Thread.sleep(50);
        n++;
        if (n >= FACTOR)
        {
          TRACER.debugInfo("Interrupting listener thread for dn " +
            repDomain.getBaseDN() + " in DS " + repDomain.getServerId());
          this.interrupt();
        }
      }
    } catch (InterruptedException e)
    {
      // exit the loop if this thread is interrupted.
    }
  }
}
