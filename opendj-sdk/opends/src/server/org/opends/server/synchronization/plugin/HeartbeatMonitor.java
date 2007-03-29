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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.server.synchronization.plugin;

import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.debugInfo;

import java.io.IOException;

import org.opends.server.api.DirectoryThread;
import org.opends.server.synchronization.protocol.ProtocolSession;

/**
 * This class implements a thread to monitor heartbeat messages from the
 * synchronization server.  Each broker runs one of these threads.
 */
public class HeartbeatMonitor extends DirectoryThread
{


  /**
   * The session on which heartbeats are to be monitored.
   */
  private ProtocolSession session;


  /**
   * The time in milliseconds between heartbeats from the synchronization
   * server.  Zero means heartbeats are off.
   */
  private long heartbeatInterval;


  /**
   * Set this to stop the thread.
   */
  private boolean shutdown = false;


  /**
   * Create a heartbeat monitor thread.
   * @param threadName The name of the heartbeat thread.
   * @param session The session on which heartbeats are to be monitored.
   * @param heartbeatInterval The expected interval between heartbeats in
   * milliseconds.
   */
  public HeartbeatMonitor(String threadName, ProtocolSession session,
                          long heartbeatInterval)
  {
    super(threadName);
    this.session = session;
    this.heartbeatInterval = heartbeatInterval;
  }

  /**
   * Call this method to stop the thread.
   */
  public void shutdown()
  {
    shutdown = true;
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public void run()
  {
    if (debugEnabled())
    {
      debugInfo("Heartbeat monitor is starting, expected interval is %d",
                heartbeatInterval);
    }
    try
    {
      while (!shutdown)
      {
        long now = System.currentTimeMillis();
        long lastReceiveTime = session.getLastReceiveTime();
        if (now > lastReceiveTime + 2 * heartbeatInterval)
        {
          debugInfo("Heartbeat monitor is closing the broker session " +
          "because it could not detect a heartbeat.");

          // Heartbeat is well overdue so the server is assumed to be dead.
          if (debugEnabled())
          {
            debugInfo("Heartbeat monitor is closing the broker session " +
                "because it could not detect a heartbeat.");
          }
          session.close();
          break;
        }
        try
        {
          Thread.sleep(heartbeatInterval);
        }
        catch (InterruptedException e)
        {
          // That's OK.
        }
      }
    }
    catch (IOException e)
    {
      // Hope that's OK.
    }
    finally
    {
      if (debugEnabled())
      {
        debugInfo("Heartbeat monitor is exiting.");
      }
    }
  }
}
