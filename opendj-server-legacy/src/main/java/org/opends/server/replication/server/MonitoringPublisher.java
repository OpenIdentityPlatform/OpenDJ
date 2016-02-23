/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2009-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2014 ForgeRock AS.
 */
package org.opends.server.replication.server;

import java.io.IOException;

import org.opends.server.api.DirectoryThread;
import org.forgerock.i18n.slf4j.LocalizedLogger;
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
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The replication domain we send monitoring for. */
  private final ReplicationServerDomain domain;

  /** Sleep time (in ms) before sending new monitoring messages. */
  private volatile long period;

  private final Object shutdownLock = new Object();

  /**
   * Create a monitoring publisher.
   * @param replicationServerDomain The ReplicationServerDomain the monitoring
   *        publisher is for.
   * @param period The sleep time to use
   */
  public MonitoringPublisher(ReplicationServerDomain replicationServerDomain,
    long period)
  {
    super("Replication server RS("
        + replicationServerDomain.getLocalRSServerId()
        + ") monitor publisher for domain \""
        + replicationServerDomain.getBaseDN() + "\"");

    this.domain = replicationServerDomain;
    this.period = period;
  }

  /**
   * Run method for the monitoring publisher.
   */
  @Override
  public void run()
  {
    if (logger.isTraceEnabled())
    {
      logger.trace(getMessage("Monitoring publisher starting."));
    }

    try
    {
      while (!isShutdownInitiated())
      {
        // Send global topology information to peer DSs
        final int senderId = domain.getLocalRSServerId();
        final MonitorMsg monitorMsg =
            domain.createGlobalTopologyMonitorMsg(senderId, 0);

        for (ServerHandler serverHandler : domain.getConnectedDSs().values())
        {
          // send() can be long operation, check for shutdown between each calls
          if (isShutdownInitiated())
          {
            break;
          }
          try
          {
            serverHandler.send(monitorMsg);
          }
          catch (IOException e)
          {
            // Server is disconnecting ? Forget it
          }
        }

        synchronized (shutdownLock)
        {
          // double check to ensure the call to notify() was not missed
          if (!isShutdownInitiated())
          {
            shutdownLock.wait(period);
          }
        }
      }
    }
    catch (InterruptedException e)
    {
      logger.trace(getMessage(
          "Monitoring publisher has been interrupted while sleeping."));
    }

    logger.trace(getMessage("Monitoring publisher is terminated."));
  }



  /**
   * Stops the thread.
   */
  public void shutdown()
  {
    initiateShutdown();
    synchronized (shutdownLock)
    {
      shutdownLock.notifyAll();
    }
    if (logger.isTraceEnabled())
    {
      logger.trace(getMessage("Shutting down monitoring publisher."));
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
      // Here, "this" is the monitoring publisher thread
      join(2000);
    }
    catch (InterruptedException e)
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
    if (logger.isTraceEnabled())
    {
      logger.trace(getMessage(
          "Monitoring publisher changing period value to " + period));
    }

    this.period = period;
  }

  private String getMessage(String message)
  {
    return "In RS " + domain.getLocalRSServerId() + ", for base dn "
        + domain.getBaseDN() + ": " + message;
  }
}
