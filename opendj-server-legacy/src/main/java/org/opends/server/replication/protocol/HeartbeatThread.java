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
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.replication.protocol;

import java.io.IOException;

import org.opends.server.api.DirectoryThread;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.util.StaticUtils;

/**
 * This thread publishes a {@link HeartbeatMsg} on a given protocol session at
 * regular intervals when there are no other replication messages being
 * published.
 * <p>
 * These heartbeat messages are sent by a replication server.
 */
public class HeartbeatThread extends DirectoryThread
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();


  /**
   * For test purposes only to simulate loss of heartbeats.
   */
  private static volatile boolean heartbeatsDisabled;

  /**
   * The session on which heartbeats are to be sent.
   */
  private final Session session;


  /**
   * The time in milliseconds between heartbeats.
   */
  private final long heartbeatInterval;


  /**
   * Set this to stop the thread.
   */
  private volatile boolean shutdown;
  private final Object shutdownLock = new Object();


  /**
   * Create a heartbeat thread.
   * @param threadName The name of the heartbeat thread.
   * @param session The session on which heartbeats are to be sent.
   * @param heartbeatInterval The desired interval between heartbeats in
   * milliseconds.
   */
  public HeartbeatThread(String threadName, Session session,
                  long heartbeatInterval)
  {
    super(threadName);
    this.session = session;
    this.heartbeatInterval = heartbeatInterval;
  }

  /** {@inheritDoc} */
  @Override
  public void run()
  {
    try
    {
      if (logger.isTraceEnabled())
      {
        logger.trace("Heartbeat thread is starting, interval is %d",
                  heartbeatInterval);
      }
      HeartbeatMsg heartbeatMessage = new HeartbeatMsg();

      while (!shutdown)
      {
        long now = System.currentTimeMillis();
        if (logger.isTraceEnabled())
        {
          logger.trace("Heartbeat thread awoke at %d, last message " +
              "was sent at %d", now, session.getLastPublishTime());
        }

        if (now > session.getLastPublishTime() + heartbeatInterval
            && !heartbeatsDisabled)
        {
          if (logger.isTraceEnabled())
          {
            logger.trace("Heartbeat sent at %d", now);
          }
          session.publish(heartbeatMessage);
        }

        long sleepTime = session.getLastPublishTime() + heartbeatInterval - now;
        if (sleepTime <= 0)
        {
          sleepTime = heartbeatInterval;
        }

        if (logger.isTraceEnabled())
        {
          logger.trace("Heartbeat thread sleeping for %d", sleepTime);
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
        logger.trace("Heartbeat thread could not send a heartbeat."
            + StaticUtils.stackTraceToSingleLineString(e));
      }
    }
    finally
    {
      if (logger.isTraceEnabled())
      {
        logger.trace("Heartbeat thread is exiting.");
      }
    }
  }


  /**
   * Call this method to stop the thread.
   * This method is blocking until the thread has stopped.
   */
  public void shutdown()
  {
    synchronized (shutdownLock)
    {
      shutdown = true;
      shutdownLock.notifyAll();
      if (logger.isTraceEnabled())
      {
        logger.trace("Going to notify Heartbeat thread.");
      }
    }
    if (logger.isTraceEnabled())
    {
      logger.trace("Returning from Heartbeat shutdown.");
    }
  }


  /**
   * For testing purposes only to simulate loss of heartbeats.
   * @param heartbeatsDisabled Set true to prevent heartbeats from being sent.
   */
  public static void setHeartbeatsDisabled(boolean heartbeatsDisabled)
  {
    HeartbeatThread.heartbeatsDisabled = heartbeatsDisabled;
  }
}
