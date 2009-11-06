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
 *      Copyright 2009 Sun Microsystems, Inc.
 */
package org.opends.server.replication.server;

import java.io.IOException;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;

import org.opends.server.api.DirectoryThread;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.replication.protocol.MonitorMsg;

/**
 * This thread regularly publishes monitoring information:
 * - it sends monitoring messages regarding the direct topology (directly
 *   connected DSs and RSs) to the connected RSs
 * - it sends monitoring messages regarding the whole topology (also includes
 *   the local RS) to the connected DSs
 * Note: as of today, monitoring messages mainly contains the server state of
 * the entities.
 */
public class MonitoringPublisher extends DirectoryThread
{

  private boolean shutdown = false;
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // The domain we send monitoring for
  private ReplicationServerDomain replicationServerDomain;

  // Sleep time (in ms) before sending new monitoring messages.
  private long period = 3000;

  // Is the thread terminated ?
  private boolean done = false;

  private final Object sleeper = new Object();

  /**
   * Create a monitoring publisher.
   * @param replicationServerDomain The ReplicationServerDomain the monitoring
   *        publisher is for.
   * @param period The sleep time to use
   */
  public MonitoringPublisher(ReplicationServerDomain replicationServerDomain,
    long period)
  {
    super("Monitoring publisher for " +
      replicationServerDomain.getBaseDn() + " in RS " +
      replicationServerDomain.getReplicationServer().getServerId());

    this.replicationServerDomain = replicationServerDomain;
    this.period = period;
  }

  /**
   * Run method for the monitoring publisher.
   */
  @Override
  public void run()
  {
    if (debugEnabled())
    {
      TRACER.debugInfo("Monitoring publisher starting for dn " +
        replicationServerDomain.getBaseDn().toString());
    }

    while (!shutdown)
    {
      try
      {
        synchronized (sleeper)
        {
          sleeper.wait(period);
        }
      } catch (InterruptedException ex)
      {
        TRACER.debugInfo("Monitoring publisher for dn " +
            replicationServerDomain.getBaseDn().toString() + " in RS " +
            replicationServerDomain.getReplicationServer().getServerId() +
            " has been interrupted while sleeping.");
      }

      // Send global topology information to peer DSs
      MonitorMsg monitorMsg =
        replicationServerDomain.createGlobalTopologyMonitorMsg(0, 0);
      int localServerId =
          replicationServerDomain.getReplicationServer().getServerId();
      if (monitorMsg != null)
      {
        for (ServerHandler serverHandler :
          replicationServerDomain.getConnectedDSs().values())
        {
          // Set the right sender and destination ids
          monitorMsg.setSenderID(localServerId);
          monitorMsg.setDestination(serverHandler.getServerId());
          try
          {
            serverHandler.send(monitorMsg);
          } catch (IOException e)
          {
            // Server is disconnecting ? Forget it
          }
        }
      }
    }

    done = true;
    TRACER.debugInfo("Monitoring publisher for dn " +
      replicationServerDomain.getBaseDn().toString() + " is terminated." +
      " This is in RS " +
      replicationServerDomain.getReplicationServer().getServerId());
  }

  /**
   * Stops the thread.
   */
  public void shutdown()
  {
    if (debugEnabled())
    {
      TRACER.debugInfo("Shutting down monitoring publisher for dn " +
        replicationServerDomain.getBaseDn().toString() + " in RS " +
        replicationServerDomain.getReplicationServer().getServerId());
    }
    shutdown = true;
    synchronized (sleeper)
    {
      sleeper.notify();
    }
  }

  /**
   * Waits for thread death. If not terminated within 2 seconds,
   * forces interruption
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
          TRACER.debugInfo("Interrupting monitoring publisher for dn " +
            replicationServerDomain.getBaseDn().toString() + " in RS " +
            replicationServerDomain.getReplicationServer().getServerId());
          this.interrupt();
        }
      }
    } catch (InterruptedException e)
    {
      // exit the loop if this thread is interrupted.
    }
  }

  /**
   * Sets the period value.
   * @param period The new period value.
   */
  public void setPeriod(long period)
  {
    if (debugEnabled())
    {
      TRACER.debugInfo("Monitoring publisher for dn " +
        replicationServerDomain.getBaseDn().toString() +
        " changing period value to " + period);
    }

    this.period = period;
  }
}
