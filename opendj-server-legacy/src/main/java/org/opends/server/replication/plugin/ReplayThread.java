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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.replication.plugin;

import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.util.StaticUtils.*;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

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
  private final ReentrantLock switchQueueLock;
  private AtomicBoolean shutdown = new AtomicBoolean(false);
  private static int count;

  /**
   * Constructor for the ReplayThread.
   *
   * @param updateToReplayQueue The queue of update messages we have to replay
   * @param switchQueueLock lock to ensure moving updates from one queue to another is atomic
   */
  public ReplayThread(BlockingQueue<UpdateToReplay> updateToReplayQueue, ReentrantLock switchQueueLock)
  {
    super("Replica replay thread " + count++);
    this.updateToReplayQueue = updateToReplayQueue;
    this.switchQueueLock = switchQueueLock;
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
        if (switchQueueLock.tryLock(1L, TimeUnit.SECONDS))
        {
          LDAPReplicationDomain domain;
          LDAPUpdateMsg updateMsg;
          try
          {
            if (shutdown.get())
            {
              break;
            }
            UpdateToReplay updateToreplay = updateToReplayQueue.poll(1L, TimeUnit.SECONDS);
            if (updateToreplay == null)
            {
              continue;
            }
            // Find replication domain for that update message and mark it as "in progress"
            updateMsg = updateToreplay.getUpdateMessage();
            domain = updateToreplay.getReplicationDomain();
            domain.markInProgress(updateMsg);
          }
          finally
          {
            switchQueueLock.unlock();
          }
          domain.replay(updateMsg, shutdown);
        }
      }
      catch (Exception e)
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
