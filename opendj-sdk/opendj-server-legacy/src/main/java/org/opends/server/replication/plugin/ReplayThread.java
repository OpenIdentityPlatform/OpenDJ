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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.replication.plugin;

import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.util.StaticUtils.*;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.opends.server.api.DirectoryThread;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.replication.protocol.LDAPUpdateMsg;

/**
 * Thread that is used to get message from the replication servers (stored
 * in the updates queue) and replay them in the current server. A configurable
 * number of this thread is created for the whole MultimasterReplication object
 * (i.e: these threads are shared across the ReplicationDomain objects for
 * replaying the updates they receive)
 */
public class ReplayThread extends DirectoryThread
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private final BlockingQueue<UpdateToReplay> updateToReplayQueue;
  private AtomicBoolean shutdown = new AtomicBoolean(false);
  private static int count;

  /**
   * Constructor for the ReplayThread.
   *
   * @param updateToReplayQueue The queue of update messages we have to replay
   */
  public ReplayThread(BlockingQueue<UpdateToReplay> updateToReplayQueue)
  {
     super("Replica replay thread " + count++);
     this.updateToReplayQueue = updateToReplayQueue;
  }

  /**
   * Shutdown this replay thread.
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
    if (logger.isTraceEnabled())
    {
      logger.trace("Replication Replay thread starting.");
    }

    while (!shutdown.get())
    {
      try
      {
        UpdateToReplay updateToreplay;
        // Loop getting an updateToReplayQueue from the update message queue and
        // replaying matching changes
        while (!shutdown.get() &&
          ((updateToreplay = updateToReplayQueue.poll(1L,
          TimeUnit.SECONDS)) != null))
        {
          // Find replication domain for that update message
          LDAPUpdateMsg updateMsg = updateToreplay.getUpdateMessage();
          LDAPReplicationDomain domain = updateToreplay.getReplicationDomain();
          domain.replay(updateMsg, shutdown);
        }
      } catch (Exception e)
      {
        /*
         * catch all exceptions happening so that the thread never dies even
         * in case of problems.
         */
        logger.error(ERR_EXCEPTION_REPLAYING_REPLICATION_MESSAGE, stackTraceToSingleLineString(e));
      }
    }
    if (logger.isTraceEnabled())
    {
      logger.trace("Replication Replay thread stopping.");
    }
  }
}
