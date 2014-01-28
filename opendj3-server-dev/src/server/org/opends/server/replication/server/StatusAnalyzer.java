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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2014 ForgeRock AS
 */
package org.opends.server.replication.server;

import org.opends.server.api.DirectoryThread;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.replication.common.StatusMachineEvent;

import static org.opends.server.replication.common.ServerStatus.*;
import static org.opends.server.replication.common.StatusMachineEvent.*;

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

  private volatile boolean shutdown = false;
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private final ReplicationServerDomain replicationServerDomain;
  private volatile int degradedStatusThreshold = -1;

  /** Sleep time for the thread, in ms. */
  private static final int STATUS_ANALYZER_SLEEP_TIME = 5000;

  private volatile boolean done = false;

  private final Object shutdownLock = new Object();

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
    super("Replication server RS("
        + replicationServerDomain.getLocalRSServerId()
        + ") delay monitor for domain \"" + replicationServerDomain.getBaseDN()
        + "\"");

    this.replicationServerDomain = replicationServerDomain;
    this.degradedStatusThreshold = degradedStatusThreshold;
  }

  /**
   * Analyzes if servers are late or not, and change their status accordingly.
   */
  @Override
  public void run()
  {
    if (logger.isTraceEnabled())
    {
      logger.trace(
          getMessage("Directory server status analyzer starting."));
    }

    while (!shutdown)
    {
      synchronized (shutdownLock)
      {
        if (!shutdown)
        {
          try
          {
            shutdownLock.wait(STATUS_ANALYZER_SLEEP_TIME);
          }
          catch (InterruptedException e)
          {
            // Server shutdown monitor may interrupt slow threads.
            logger.traceException(e);
            shutdown = true;
            break;
          }
        }
      }

      // Go through each connected DS, get the number of pending changes we have
      // for it and change status accordingly if threshold value is
      // crossed/uncrossed
      for (DataServerHandler serverHandler :
        replicationServerDomain.getConnectedDSs().values())
      {
        // Get number of pending changes for this server
        int nChanges = serverHandler.getRcvMsgQueueSize();
        if (logger.isTraceEnabled())
        {
          logger.trace(getMessage("Status analyzer: DS "
              + serverHandler.getServerId() + " has " + nChanges
              + " message(s) in writer queue."));
        }

        // Check status to know if it is relevant to change the status. Do not
        // take RSD lock to test. If we attempt to change the status whereas
        // the current status does allow it, this will be noticed by
        // the changeStatusFromStatusAnalyzer() method. This allows to take the
        // lock roughly only when needed versus every sleep time timeout.
        if (degradedStatusThreshold > 0)
          // Threshold value = 0 means no status analyzer (no degrading system)
          // we should not have that as the status analyzer thread should not be
          // created if this is the case, but for sanity purpose, we add this
          // test
        {
          if (nChanges >= degradedStatusThreshold)
          {
            if (serverHandler.getStatus() == NORMAL_STATUS
                && isInterrupted(serverHandler, TO_DEGRADED_STATUS_EVENT))
            {
              break;
            }
          }
          else
          {
            if (serverHandler.getStatus() == DEGRADED_STATUS
                && isInterrupted(serverHandler, TO_NORMAL_STATUS_EVENT))
            {
              break;
            }
          }
        }
      }
    }

    done = true;
    logger.trace(getMessage("Status analyzer is terminated."));
  }

  private String getMessage(String message)
  {
    return "In RS " + replicationServerDomain.getLocalRSServerId()
        + ", for baseDN=" + replicationServerDomain.getBaseDN() + ": "
        + message;
  }

  private boolean isInterrupted(DataServerHandler serverHandler,
      StatusMachineEvent event)
  {
    if (replicationServerDomain.changeStatus(serverHandler, event))
    {
      // Finish job and let thread die
      logger.trace(
          getMessage("Status analyzer has been interrupted and will die."));
      return true;
    }
    return false;
  }

  /**
   * Stops the thread.
   */
  public void shutdown()
  {
    synchronized (shutdownLock)
    {
      shutdown = true;
      shutdownLock.notifyAll();

      if (logger.isTraceEnabled())
      {
        logger.trace(getMessage("Shutting down status analyzer."));
      }
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
      while (!done && this.isAlive())
      {
        Thread.sleep(50);
        n++;
        if (n >= FACTOR)
        {
          logger.trace(getMessage("Interrupting status analyzer."));
          interrupt();
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
  public void setDegradedStatusThreshold(int degradedStatusThreshold)
  {
    if (logger.isTraceEnabled())
    {
      logger.trace(getMessage(
          "Directory server status analyzer changing threshold value to "
              + degradedStatusThreshold));
    }

    this.degradedStatusThreshold = degradedStatusThreshold;
  }
}
