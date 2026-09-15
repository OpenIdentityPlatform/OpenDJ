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
 * Portions Copyright 2026 3A Systems, LLC.
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
            if (!domain.markInProgress(updateMsg))
            {
              /*
               * The domain restarted its session after a failed replay while this
               * message was waiting here, so it does not know about this change
               * anymore: the replication server sends it again over the new session.
               */
              continue;
            }
          }
          finally
          {
            switchQueueLock.unlock();
          }
          domain.replay(updateMsg, shutdown);
        }
      }
      catch (OutOfMemoryError e)
      {
        /*
         * The JVM is out of memory, which is not something to carry on replaying from: this
         * thread does not stay for the changes which follow. Nothing is reported here - the
         * uncaught exception handler of DirectoryThread is what says this thread is gone,
         * with an alert - and the change it was replaying has been given back, counted and
         * asked for again by the domain on its way out (issue #922).
         *
         * The other errors of the JVM are caught below: a StackOverflowError is gone once
         * the stack has unwound, and a thread which ends here is one nothing replaces.
         */
        throw e;
      }
      catch (Throwable t)
      {
        /*
         * catch all exceptions happening so that the thread never dies even
         * in case of problems.
         *
         * An Error is not an Exception, so one raised here used to unwind run() and end
         * this thread. Nothing creates a replay thread to replace it - the pool is created
         * when the first domain of this server is - so the shared replay queue would have
         * one consumer fewer for every domain, for as long as the server is up, until it
         * has none left and replication stops (issue #923).
         */
        logger.error(ERR_EXCEPTION_REPLAYING_REPLICATION_MESSAGE, stackTraceToSingleLineString(t));
      }
    }
    if (logger.isTraceEnabled())
    {
      logger.trace("Replication Replay thread stopping.");
    }
  }
}
