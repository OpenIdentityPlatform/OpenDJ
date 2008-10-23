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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.replication.server;

import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;


import org.opends.server.api.DirectoryThread;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.replication.common.ServerStatus;
import org.opends.server.replication.common.StatusMachineEvent;

/**
 * This thread is in charge of periodically determining if the connected
 * directory servers of the domain it is associated with are late or not
 * regarding the changes they have to replay. A threshold is set for the maximum
 * allowed number of pending changes. When the threshold for a DS is crossed,
 * the status analyzer must make the DS status change to DEGRADED_STATUS. When
 * the threshold is uncrossed, the status analyzer must make the DS status
 * change back to NORMAL_STATUS. To have meaning of status, please refer to
 * ServerStatus class.
 */
public class StatusAnalyzer extends DirectoryThread
{

  private boolean finished = false;
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  private ReplicationServerDomain replicationServerDomain;
  private int degradedStatusThreshold = -1;

  // Sleep time for the thread, in ms.
  private int STATUS_ANALYZER_SLEEP_TIME = 5000;

  private boolean done = false;

  private Object sleeper = new Object();

  /**
   * Create a StatusAnalyzer.
   * @param replicationServerDomain The ReplicationServerDomain the status
   *        analyzer is for.
   * @param degradedStatusThreshold The pending changes threshold value to be
   * used for putting a DS in DEGRADED_STATUS.
   */
  public StatusAnalyzer(ReplicationServerDomain replicationServerDomain,
    int degradedStatusThreshold)
  {
    super("Replication Server Status Analyzer for " +
      replicationServerDomain.getBaseDn().toNormalizedString() + " in RS " +
      replicationServerDomain.getReplicationServer().getServerId());

    this.replicationServerDomain = replicationServerDomain;
    this.degradedStatusThreshold = degradedStatusThreshold;
  }

  /**
   * Run method for the StatusAnalyzer.
   * Analyzes if servers are late or not, and change their status accordingly.
   */
  @Override
  public void run()
  {
    if (debugEnabled())
    {
      TRACER.debugInfo("Directory server status analyzer starting for dn " +
        replicationServerDomain.getBaseDn().toString());
    }

    boolean interrupted = false;
    while (!finished && !interrupted)
    {
      try
      {
        synchronized (sleeper)
        {
          sleeper.wait(STATUS_ANALYZER_SLEEP_TIME);
        }
      } catch (InterruptedException ex)
      {
        TRACER.debugInfo("Status analyzer for dn " +
            replicationServerDomain.getBaseDn().toString() + " in RS " +
            replicationServerDomain.getReplicationServer().getServerId() +
            " has been interrupted while sleeping.");
      }

      // Go through each connected DS, get the number of pending changes we have
      // for it and change status accordingly if threshold value is
      // crossed/uncrossed
      for (ServerHandler serverHandler :
        replicationServerDomain.getConnectedDSs(). values())
      {
        // Get number of pending changes for this server
        int nChanges = serverHandler.getRcvMsgQueueSize();
        if (debugEnabled())
        {
          TRACER.debugInfo("Status analyzer for dn " +
            replicationServerDomain.getBaseDn().toString() + " DS " +
            Short.toString(serverHandler.getServerId()) + " has " + nChanges +
            " message(s) in writer queue. This is in RS " +
            replicationServerDomain.getReplicationServer().getServerId());
        }

        // Check status to know if it is relevant to change the status. Do not
        // take RSD lock to test. If we attempt to change the status whereas
        // we are in a status that do not allows that, this will be noticed by
        // the changeStatusFromStatusAnalyzer method. This allows to take the
        // lock roughly only when needed versus every sleep time timeout.
        if (degradedStatusThreshold > 0)
          // Threshold value = 0 means no status analyzer (no degrading system)
          // we should not have that as the status analyzer thread should not be
          // created if this is the case, but for sanity purpose, we add this
          // test
        {
          if (nChanges >= degradedStatusThreshold)
          {
            if (serverHandler.getStatus() == ServerStatus.NORMAL_STATUS)
            {
              interrupted =
                replicationServerDomain.changeStatusFromStatusAnalyzer(
                serverHandler,
                StatusMachineEvent.TO_DEGRADED_STATUS_EVENT);
              if (interrupted)
              {
                // Finih job and let thread die
                TRACER.debugInfo("Status analyzer for dn " +
                  replicationServerDomain.getBaseDn().toString() +
                  " has been interrupted and will die. This is in RS " +
                  replicationServerDomain.getReplicationServer().getServerId());
                break;
              }
            }
          } else
          {
            if (serverHandler.getStatus() == ServerStatus.DEGRADED_STATUS)
            {
              interrupted =
                replicationServerDomain.changeStatusFromStatusAnalyzer(
                serverHandler,
                StatusMachineEvent.TO_NORMAL_STATUS_EVENT);
              if (interrupted)
              {
                // Finih job and let thread die
                TRACER.debugInfo("Status analyzer for dn " +
                  replicationServerDomain.getBaseDn().toString() +
                  " has been interrupted and will die. This is in RS " +
                  replicationServerDomain.getReplicationServer().getServerId());
                break;
              }
            }
          }
        }
      }
    }

    done = true;
    TRACER.debugInfo("Status analyzer for dn " +
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
      TRACER.debugInfo("Shutting down status analyzer for dn " +
        replicationServerDomain.getBaseDn().toString() + " in RS " +
        replicationServerDomain.getReplicationServer().getServerId());
    }
    finished = true;
    synchronized (sleeper)
    {
      sleeper.notify();
    }
  }

  /**
   * Waits for analyzer death. If not finished within 2 seconds,
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
          TRACER.debugInfo("Interrupting status analyzer for dn " +
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
   * Sets the threshold value.
   * @param degradedStatusThreshold The new threshold value.
   */
  public void setDeradedStatusThreshold(int degradedStatusThreshold)
  {
    if (debugEnabled())
    {
      TRACER.debugInfo("Directory server status analyzer for dn " +
        replicationServerDomain.getBaseDn().toString() + " changing threshold" +
        " value to " + degradedStatusThreshold);
    }

    this.degradedStatusThreshold = degradedStatusThreshold;
  }
}
