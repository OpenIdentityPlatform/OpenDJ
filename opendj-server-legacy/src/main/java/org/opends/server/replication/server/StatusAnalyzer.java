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
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.replication.server;



import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.api.DirectoryThread;



/**
 * This thread is in charge of periodically determining if the connected
 * directory servers of the domain it is associated with are late or not
 * regarding the changes they have to replay. A threshold is set for the maximum
 * allowed number of pending changes. When the threshold for a DS is crossed,
 * the status analyzer must make the DS status change to DEGRADED_STATUS. When
 * the threshold is uncrossed, the status analyzer must make the DS status
 * change back to NORMAL_STATUS. To have meaning of status, please refer to
 * ServerStatus class.
 * <p>
 * In addition, this thread is responsible for publishing any pending status
 * messages.
 */
class StatusAnalyzer extends DirectoryThread
{
  private static final LocalizedLogger logger = LocalizedLogger
      .getLoggerForThisClass();

  /** Sleep time for the thread, in ms. */
  private static final int STATUS_ANALYZER_SLEEP_TIME = 5000;

  private final ReplicationServerDomain replicationServerDomain;
  private final Object eventMonitor = new Object();
  private boolean pendingStatusMessage;
  private long nextCheckDSDegradedStatusTime;



  /**
   * Create a StatusAnalyzer.
   *
   * @param replicationServerDomain
   *          The ReplicationServerDomain the status analyzer is for.
   */
  StatusAnalyzer(ReplicationServerDomain replicationServerDomain)
  {
    super("Replication server RS("
        + replicationServerDomain.getLocalRSServerId()
        + ") status monitor for domain \""
        + replicationServerDomain.getBaseDN()
        + "\"");
    this.replicationServerDomain = replicationServerDomain;
  }



  /**
   * Analyzes if servers are late or not, and change their status accordingly.
   */
  @Override
  public void run()
  {
    if (logger.isTraceEnabled())
    {
      logger.trace(getMessage("Directory server status analyzer starting."));
    }

    try
    {
      while (true)
      {
        final boolean requestStatusBroadcastWasRequested;
        synchronized (eventMonitor)
        {
          if (!isShutdownInitiated() && !pendingStatusMessage)
          {
            eventMonitor.wait(STATUS_ANALYZER_SLEEP_TIME);
          }
          requestStatusBroadcastWasRequested = pendingStatusMessage;
          pendingStatusMessage = false;
        }

        if (isShutdownInitiated())
        {
          break;
        }

        // Broadcast heartbeats, topology messages, etc if requested.
        if (requestStatusBroadcastWasRequested)
        {
          replicationServerDomain.sendPendingStatusMessages();
        }

        /*
         * Check the degraded status for connected DS instances only if
         * sufficient time has passed. The current time is not cached because
         * the call to checkDSDegradedStatus may take some time.
         */
        if (nextCheckDSDegradedStatusTime < System.currentTimeMillis())
        {
          replicationServerDomain.checkDSDegradedStatus();
          nextCheckDSDegradedStatusTime = System.currentTimeMillis()
              + STATUS_ANALYZER_SLEEP_TIME;
        }
      }
    }
    catch (InterruptedException e)
    {
      // Forcefully stopped.
    }

    logger.trace(getMessage("Status analyzer is terminated."));
  }



  private String getMessage(String message)
  {
    return "In RS " + replicationServerDomain.getLocalRSServerId()
        + ", for baseDN=" + replicationServerDomain.getBaseDN() + ": "
        + message;
  }



  /**
   * Stops the thread.
   */
  void shutdown()
  {
    initiateShutdown();
    if (logger.isTraceEnabled())
    {
      logger.trace(getMessage("Shutting down status analyzer."));
    }
    synchronized (eventMonitor)
    {
      eventMonitor.notifyAll();
    }
    try
    {
      join(2000);
    }
    catch (InterruptedException e)
    {
      // Trapped: forcefully stop the thread.
    }
    if (isAlive())
    {
      // The join timed out or was interrupted so attempt to forcefully stop the
      // analyzer.
      interrupt();
    }
  }



  /**
   * Requests that a topology state related message be broadcast to the rest of
   * the topology. Messages include DS heartbeats, topology information, etc.
   */
  void notifyPendingStatusMessage()
  {
    synchronized (eventMonitor)
    {
      pendingStatusMessage = true;
      eventMonitor.notifyAll();
    }
  }
}
