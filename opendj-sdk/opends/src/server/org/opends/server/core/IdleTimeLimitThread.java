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
package org.opends.server.core;
import org.opends.messages.Message;



import org.opends.server.admin.std.server.ConnectionHandlerCfg;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ConnectionHandler;
import org.opends.server.api.DirectoryThread;
import org.opends.server.api.ServerShutdownListener;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DisconnectReason;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.ErrorLogger;
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
  /**
   * The debug log tracer for this object.
   */
  private static final DebugTracer TRACER = getTracer();



  // Indicates whether a shutdown request has been received.
  private boolean shutdownRequested;



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
    Message disconnectMessage = INFO_IDLETIME_LIMIT_EXCEEDED.get();

    long sleepTime = 5000L;

    while (! shutdownRequested)
    {
      try
      {
        try
        {
          sleep(sleepTime);
        } catch (InterruptedException ie) {}

        sleepTime = 5000L;
        for (ConnectionHandler ch : DirectoryServer.getConnectionHandlers())
        {
          ConnectionHandler<? extends ConnectionHandlerCfg> connHandler =
               (ConnectionHandler<? extends ConnectionHandlerCfg>) ch;
          for (ClientConnection c : connHandler.getClientConnections())
          {
            long idleTime = c.getIdleTime();
            if (idleTime > 0)
            {
              long idleTimeLimit = c.getIdleTimeLimit();
              if (idleTimeLimit > 0)
              {
                if (idleTime > idleTimeLimit)
                {
                  if (debugEnabled())
                  {
                    TRACER.debugInfo("Terminating client connection " +
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
                    if (debugEnabled())
                    {
                      TRACER.debugCaught(DebugLogLevel.ERROR, e);
                    }

                    Message message = ERR_IDLETIME_DISCONNECT_ERROR.get(
                            c.getConnectionID(),
                            stackTraceToSingleLineString(e)
                    );
                    ErrorLogger.logError(message);
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
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        Message message =
            ERR_IDLETIME_UNEXPECTED_ERROR.get(stackTraceToSingleLineString(e));
        ErrorLogger.logError(message);
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  public String getShutdownListenerName()
  {
    return "Idle Time Limit Thread";
  }



  /**
   * {@inheritDoc}
   */
  public void processServerShutdown(Message reason)
  {
    shutdownRequested = true;
  }
}

