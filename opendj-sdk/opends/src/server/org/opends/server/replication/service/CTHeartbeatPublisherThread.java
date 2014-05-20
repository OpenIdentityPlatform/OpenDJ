/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2014 ForgeRock AS
 */
package org.opends.server.replication.service;

import java.io.IOException;

import org.opends.server.api.DirectoryThread;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.protocol.ChangeTimeHeartbeatMsg;
import org.opends.server.replication.protocol.Session;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.util.StaticUtils;

import static org.opends.server.loggers.debug.DebugLogger.*;

/**
 * This thread publishes a {@link ChangeTimeHeartbeatMsg} on a given protocol
 * session at regular intervals when there are no other replication messages
 * being published.
 * <p>
 * These heartbeat messages are sent by a replica directory server.
 */
class CTHeartbeatPublisherThread extends DirectoryThread
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * The session on which heartbeats are to be sent.
   */
  private final Session session;

  /**
   * The time in milliseconds between heartbeats.
   */
  private final long heartbeatInterval;
  private final int serverId;

  /**
   * Set this to stop the thread.
   */
  private volatile boolean shutdown = false;
  private final Object shutdownLock = new Object();

  /**
   * Create a heartbeat thread.
   * @param threadName The name of the heartbeat thread.
   * @param session The session on which heartbeats are to be sent.
   * @param heartbeatInterval The interval between heartbeats sent
   *                          (in milliseconds).
   * @param serverId The serverId of the sender domain.
   */
  CTHeartbeatPublisherThread(String threadName, Session session,
      long heartbeatInterval, int serverId)
  {
    super(threadName);
    this.session = session;
    this.heartbeatInterval = heartbeatInterval;
    this.serverId = serverId;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void run()
  {
    long lastHeartbeatTime = 0;
    try
    {
      if (debugEnabled())
      {
        TRACER.debugInfo(getName() + " is starting, interval is %d",
                  heartbeatInterval);
      }

      while (!shutdown)
      {
        final long now = System.currentTimeMillis();
        if (now > session.getLastPublishTime() + heartbeatInterval)
        {
          final CSN csn = new CSN(now, 0, serverId);
          session.publish(ChangeTimeHeartbeatMsg.heartbeatMsg(csn));
          lastHeartbeatTime = csn.getTime();
        }

        long sleepTime = session.getLastPublishTime() + heartbeatInterval - now;
        if (sleepTime <= 0)
        {
          sleepTime = heartbeatInterval;
        }

        synchronized (shutdownLock)
        {
          if (!shutdown)
          {
            try
            {
              shutdownLock.wait(sleepTime);
            }
            catch (InterruptedException e)
            {
              // Server shutdown monitor may interrupt slow threads.
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, e);
              }
              shutdown = true;
            }
          }
        }
      }

      if (shutdown)
      {
        /*
         * Shortcoming: this thread is restarted each time the DS reconnects,
         * e.g. during load balancing. This is not that much of a problem
         * because the ChangeNumberIndexer tolerates receiving replica offline
         * heartbeats and then receiving messages back again.
         */
        /*
         * However, during shutdown we need to be sure that all pending client
         * operations have either completed or have been aborted before shutting
         * down replication. Otherwise, the medium consistency will move forward
         * without knowing about these changes.
         */
        final long now = System.currentTimeMillis();
        final int seqNum = lastHeartbeatTime == now ? 1 : 0;
        final CSN offlineCSN = new CSN(now, seqNum, serverId);
        session.publish(ChangeTimeHeartbeatMsg.replicaOfflineMsg(offlineCSN));
      }
    }
    catch (IOException e)
    {
      if (debugEnabled())
      {
        TRACER.debugInfo(getName() + " could not send a heartbeat: "
            + StaticUtils.stackTraceToSingleLineString(e));
      }
    }
    finally
    {
      if (debugEnabled())
      {
        TRACER.debugInfo(getName() + " is exiting.");
      }
    }
  }


  /**
   * Call this method to stop the thread.
   * This method is blocking until the thread has stopped.
   */
  void shutdown()
  {
    synchronized (shutdownLock)
    {
      shutdown = true;
      shutdownLock.notifyAll();
    }
  }
}
