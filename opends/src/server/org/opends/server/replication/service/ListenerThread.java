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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2013 ForgeRock AS
 */
package org.opends.server.replication.service;

import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.StaticUtils.*;

import java.util.concurrent.atomic.AtomicBoolean;

import org.opends.messages.Message;
import org.opends.server.api.DirectoryThread;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.replication.protocol.UpdateMsg;

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

  private final ReplicationDomain repDomain;
  private AtomicBoolean shutdown = new AtomicBoolean(false);
  private volatile boolean done = false;


  /**
   * Constructor for the ListenerThread.
   *
   * @param repDomain the replication domain that created this thread
   */
  public ListenerThread(ReplicationDomain repDomain)
  {
    super("Replica DS(" + repDomain.getServerId()
        + ") listener for domain \"" + repDomain.getServiceID()
        + "\"");
    this.repDomain = repDomain;
  }

  /**
   * Shutdown this listener thread.
   */
  public void shutdown()
  {
    shutdown.set(true);
  }

  /**
   * Run method for this class.
   */
  @Override
  public void run()
  {
    if (debugEnabled())
    {
      TRACER.debugInfo("Replication Listener thread starting.");
    }

    while (!shutdown.get())
    {
      UpdateMsg updateMsg = null;
      try
      {
        // Loop receiving update messages and putting them in the update message
        // queue
        while (!shutdown.get() && ((updateMsg = repDomain.receive()) != null))
        {
          if (repDomain.processUpdate(updateMsg, shutdown))
          {
            repDomain.processUpdateDoneSynchronous(updateMsg);
          }
        }

        if (updateMsg == null)
        {
          shutdown.set(true);
        }
      }
      catch (Exception e)
      {
        /*
         * catch all exceptions happening in repDomain.receive so that the
         * thread never dies even in case of problems.
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
      int FACTOR = 40; // Wait for 2 seconds before interrupting the thread
      int n = 0;
      while (!done && this.isAlive())
      {
        Thread.sleep(50);
        n++;
        if (n >= FACTOR)
        {
          TRACER.debugInfo("Interrupting listener thread for dn "
              + repDomain.getServiceID() + " in DS " + repDomain.getServerId());
          this.interrupt();
        }
      }
    }
    catch (InterruptedException e)
    {
      // exit the loop if this thread is interrupted.
    }
  }
}
