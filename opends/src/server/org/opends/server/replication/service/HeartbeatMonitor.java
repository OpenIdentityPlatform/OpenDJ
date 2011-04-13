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
 *      Copyright 2007-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011 ForgeRock AS
 */

package org.opends.server.replication.service;

import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.replication.protocol.ProtocolSession;

import org.opends.server.api.DirectoryThread;

/**
 * This class implements a thread to monitor heartbeat messages from the
 * replication server.  Each broker runs one of these threads.
 */
final class HeartbeatMonitor extends DirectoryThread
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();



  /**
   * The session on which heartbeats are to be monitored.
   */
  private final ProtocolSession session;


  /**
   * The time in milliseconds between heartbeats from the replication
   * server.  Zero means heartbeats are off.
   */
  private final long heartbeatInterval;

  // Info required for logging.
  private final int serverID;
  private final int replicationServerID;
  private final String baseDN;


  /**
   * Set this to stop the thread.
   */
  private volatile boolean shutdown = false;



  /**
   * Create a heartbeat monitor thread.
   *
   * @param serverID
   *          The local directory server ID.
   * @param replicationServerID
   *          The remote replication server ID.
   * @param baseDN
   *          The name of the domain being replicated.
   * @param session
   *          The session on which heartbeats are to be monitored.
   * @param heartbeatInterval
   *          The expected interval between heartbeats received (in
   *          milliseconds).
   */
  HeartbeatMonitor(int serverID, int replicationServerID,
      String baseDN, ProtocolSession session, long heartbeatInterval)
  {
    super("Replica DS("
      + serverID + ") heartbeat monitor for domain \""
      + baseDN + "\" from RS(" + replicationServerID
      + ") at " + session.getReadableRemoteAddress());
    this.serverID = serverID;
    this.replicationServerID = replicationServerID;
    this.baseDN = baseDN;
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
    boolean gotOneFailure = false;
    if (debugEnabled())
    {
      TRACER.debugInfo(this + " is starting, expected interval is " +
                heartbeatInterval);
    }
    try
    {
      while (!shutdown)
      {
        long now = System.currentTimeMillis();
        long lastReceiveTime = session.getLastReceiveTime();
        if (now > lastReceiveTime + heartbeatInterval)
        {
          if (gotOneFailure == true)
          {
            // Heartbeat is well overdue so the server is assumed to be dead.
            logError(WARN_HEARTBEAT_FAILURE.get(serverID,
                replicationServerID,
                session.getReadableRemoteAddress(), baseDN));
            session.close();
            break;
          }
          else
          {
            gotOneFailure = true;
          }
        }
        else
        {
          gotOneFailure = false;
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
    finally
    {
      if (debugEnabled())
      {
        TRACER.debugInfo("Heartbeat monitor is exiting." +
            stackTraceToSingleLineString(new Exception()));
      }
    }
  }
}
