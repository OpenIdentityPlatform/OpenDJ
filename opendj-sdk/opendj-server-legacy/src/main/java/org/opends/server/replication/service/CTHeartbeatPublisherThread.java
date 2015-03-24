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
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.replication.service;

import java.io.IOException;

import org.opends.server.api.DirectoryThread;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.protocol.ChangeTimeHeartbeatMsg;
import org.opends.server.replication.protocol.Session;
import org.opends.server.util.StaticUtils;

/**
 * This thread publishes a {@link ChangeTimeHeartbeatMsg} on a given protocol
 * session at regular intervals when there are no other replication messages
 * being published.
 * <p>
 * These heartbeat messages are sent by a replica directory server.
 */
class CTHeartbeatPublisherThread extends DirectoryThread
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

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
  private volatile boolean shutdown;
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

  /** {@inheritDoc} */
  @Override
  public void run()
  {
    try
    {
      if (logger.isTraceEnabled())
      {
        logger.trace(getName() + " is starting, interval is %d",
                  heartbeatInterval);
      }

      while (!shutdown)
      {
        final long now = System.currentTimeMillis();
        if (now > session.getLastPublishTime() + heartbeatInterval)
        {
          final CSN csn = new CSN(now, 0, serverId);
          session.publish(new ChangeTimeHeartbeatMsg(csn));
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
              logger.traceException(e);
              shutdown = true;
            }
          }
        }
      }
    }
    catch (IOException e)
    {
      if (logger.isTraceEnabled())
      {
        logger.trace(getName() + " could not send a heartbeat: "
            + StaticUtils.stackTraceToSingleLineString(e));
      }
    }
    finally
    {
      if (logger.isTraceEnabled())
      {
        logger.trace(getName() + " is exiting.");
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
