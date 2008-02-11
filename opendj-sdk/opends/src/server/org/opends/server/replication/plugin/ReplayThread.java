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
package org.opends.server.replication.plugin;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.opends.messages.Message;

import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import org.opends.server.api.DirectoryThread;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.replication.protocol.UpdateMessage;

/**
 * Thread that is used to get message from the replication servers (stored
 * in the updates queue) and replay them in the current server. A configurable
 * number of this thread is created for the whole MultimasterReplication object
 * (i.e: these threads are shared accross the ReplicationDomain objects for
 * replaying the updates they receive)
 */
public class ReplayThread extends DirectoryThread
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  private BlockingQueue<UpdateToReplay> updateToReplayQueue = null;
  private boolean shutdown = false;
  private boolean done = false;
  private static int count = 0;

  /**
   * Constructor for the ReplayThread.
   *
   * @param updateToReplayQueue The queue of update messages we have to replay
   */
  public ReplayThread(BlockingQueue<UpdateToReplay> updateToReplayQueue)
  {
     super("Replication Replay thread " + count++);
     this.updateToReplayQueue = updateToReplayQueue;
  }

  /**
   * Shutdown this replay thread.
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

    if (debugEnabled())
    {
      TRACER.debugInfo("Replication Replay thread starting.");
    }

    UpdateToReplay updateToreplay = null;

    while (!shutdown)
    {
      try
      {
        // Loop getting an updateToReplayQueue from the update message queue and
        // replaying matching changes
        while ( (!shutdown) &&
          ((updateToreplay = updateToReplayQueue.poll(1L,
          TimeUnit.SECONDS)) != null))
        {
          // Find replication domain for that update message
          UpdateMessage updateMsg = updateToreplay.getUpdateMessage();
          ReplicationDomain domain = updateToreplay.getReplicationDomain();
          domain.replay(updateMsg);
        }
      } catch (Exception e)
      {
        /*
         * catch all exceptions happening so that the thread never dies even
         * in case of problems.
         */
        Message message = ERR_EXCEPTION_REPLAYING_REPLICATION_MESSAGE.get(
            stackTraceToSingleLineString(e));
        logError(message);
      }
    }
    done = true;
    if (debugEnabled())
    {
      TRACER.debugInfo("Replication Replay thread stopping.");
    }
  }

  /**
   * Wait for the completion of this thread.
   */
  public void waitForShutdown()
  {
    try
    {
      while ((done == false) && (this.isAlive()))
      {
        Thread.sleep(50);
      }
    } catch (InterruptedException e)
    {
      // exit the loop if this thread is interrupted.
    }
  }
}
