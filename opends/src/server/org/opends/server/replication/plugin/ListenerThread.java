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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.replication.plugin;
import org.opends.messages.Message;

import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import org.opends.server.api.DirectoryThread;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.replication.protocol.UpdateMessage;

/**
 * Thread that is used to get messages from the Replication servers
 * and replay them in the current server.
 */
public class ListenerThread extends DirectoryThread
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  private ReplicationDomain listener;
  private boolean shutdown = false;
  private boolean done = false;


  /**
   * Constructor for the ListenerThread.
   *
   * @param listener the Plugin that created this thread
   */
  public ListenerThread(ReplicationDomain listener)
  {
     super("Replication Listener thread " +
         "serverID=" + listener.serverId +
         " domain=" + listener.getName());
     this.listener = listener;
  }

  /**
   * Shutdown this listener thread.
   */
  public void shutdown()
  {
    shutdown = true;
  }

  /**
   * Run method for this class.
   */
  public void run()
  {
    UpdateMessage msg;

    if (debugEnabled())
    {
      TRACER.debugInfo("Replication Listener thread starting.");
    }

    while (shutdown == false)
    {
      try
      {
        while (((msg = listener.receive()) != null) && (shutdown == false))
        {
          listener.replay(msg);
        }
        if (msg == null)
          shutdown = true;
      } catch (Exception e)
      {
        /*
         * catch all exceptions happening in listener.receive and
         * listener.replay so that the thread never dies even in case
         * of problems.
         */
        Message message = ERR_EXCEPTION_RECEIVING_REPLICATION_MESSAGE.get(
            stackTraceToSingleLineString(e));
        logError(message);
      }
    }
    done = true;
    if (debugEnabled())
    {
      TRACER.debugInfo("Replication Listener thread stopping.");
    }
  }

  /**
   * Wait for the completion of this thread.
   */
  public void waitForShutdown()
  {
    try
    {
      while (done == false)
      {
        Thread.sleep(50);
      }
    } catch (InterruptedException e)
    {
      // exit the loop if this thread is interrupted.
    }
  }
}
