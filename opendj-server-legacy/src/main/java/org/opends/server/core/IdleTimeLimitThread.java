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
 *      Portions Copyright 2012-2015 ForgeRock AS.
 */
package org.opends.server.core;

import org.forgerock.i18n.LocalizableMessage;

import org.opends.server.api.ClientConnection;
import org.opends.server.api.ConnectionHandler;
import org.opends.server.api.DirectoryThread;
import org.opends.server.api.ServerShutdownListener;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.types.DisconnectReason;

import static org.opends.messages.CoreMessages.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class defines a thread that will be used to terminate client
 * connections if they have been idle for too long.
 */
public class IdleTimeLimitThread
       extends DirectoryThread
       implements ServerShutdownListener
{
  /** The debug log tracer for this object. */
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();



  /** Shutdown monitor state. */
  private volatile boolean shutdownRequested;
  private final Object shutdownLock = new Object();



  /**
   * Creates a new instance of this idle time limit thread.
   */
  public IdleTimeLimitThread()
  {
    super("Idle Time Limit Thread");
    setDaemon(true);

    shutdownRequested = false;
    DirectoryServer.registerShutdownListener(this);
  }



  /**
   * Operates in a loop, teriminating any client connections that have been idle
   * for too long.
   */
  public void run()
  {
    LocalizableMessage disconnectMessage = INFO_IDLETIME_LIMIT_EXCEEDED.get();

    long sleepTime = 5000L;

    while (! shutdownRequested)
    {
      try
      {
        synchronized (shutdownLock)
        {
          if (!shutdownRequested)
          {
            try
            {
              shutdownLock.wait(sleepTime);
            }
            catch (InterruptedException e)
            {
              // Server shutdown monitor may interrupt slow threads.
              logger.traceException(e);
              shutdownRequested = true;
              break;
            }
          }
        }

        sleepTime = 5000L;
        for (ConnectionHandler<?> ch : DirectoryServer.getConnectionHandlers())
        {
          for (ClientConnection c : ch.getClientConnections())
          {
            if (c==null) {
              logger.trace("Null client connection found in \"" + ch.getConnectionHandlerName() + "\"");
              continue;
            }

            long idleTime = c.getIdleTime();
            if (idleTime > 0)
            {
              long idleTimeLimit = c.getIdleTimeLimit();
              if (idleTimeLimit > 0)
              {
                if (idleTime >= idleTimeLimit)
                {
                  if (logger.isTraceEnabled())
                  {
                    logger.trace("Terminating client connection " +
                                     c.getConnectionID() +
                                     " due to the idle time limit");
                  }

                  try
                  {
                    c.disconnect(DisconnectReason.IDLE_TIME_LIMIT_EXCEEDED,
                                 true, disconnectMessage);
                  }
                  catch (Exception e)
                  {
                    logger.traceException(e);

                    logger.error(ERR_IDLETIME_DISCONNECT_ERROR, c.getConnectionID(),
                            stackTraceToSingleLineString(e)
                    );
                  }
                }
                else
                {
                  long shouldSleepTime = idleTimeLimit - idleTime;
                  if (shouldSleepTime < sleepTime)
                  {
                    sleepTime = shouldSleepTime;
                  }
                }
              }
            }
          }
        }
      }
      catch (Exception e)
      {
        logger.traceException(e);

        logger.error(ERR_IDLETIME_UNEXPECTED_ERROR, stackTraceToSingleLineString(e));
      }
    }
  }



  /** {@inheritDoc} */
  public String getShutdownListenerName()
  {
    return "Idle Time Limit Thread";
  }



  /** {@inheritDoc} */
  public void processServerShutdown(LocalizableMessage reason)
  {
    synchronized (shutdownLock)
    {
      shutdownRequested = true;
      shutdownLock.notifyAll();
    }
  }
}

