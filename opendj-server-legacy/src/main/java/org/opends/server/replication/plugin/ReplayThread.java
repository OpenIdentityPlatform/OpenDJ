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
import java.util.function.Consumer;

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
  /**
   * The give-back a thread runs on every domain of this server on its way out, held here
   * rather than written where it is run: a method reference is linked, and its instance
   * made, where it is first run, and this one is first run on the way out of a thread -
   * which an OutOfMemoryError may be ending, on the road this give-back is there for. Made
   * when this class is loaded instead, on a thread which can allocate (issue #986).
   */
  private static final Consumer<LDAPReplicationDomain> GIVE_BACK_PARKED_CHANGES =
      LDAPReplicationDomain::giveBackChangesParkedByStoppingThread;

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

    try
    {
      replayUntilStopped();
    }
    finally
    {
      /*
       * The changes this thread parked as waiting for another change are handed out again
       * by getNextUpdate() alone, which every replay loop of a domain runs once it is done
       * with a change: a parked change is replayed by whichever thread clears the change it
       * was waiting for. A thread which is stopping is not on that road anymore, so what it
       * parked would be left owned by a thread which does not exist, while every redelivery
       * of a change a replay thread owns is refused as a duplicate: on a domain which then
       * goes quiet that change is where the ServerState of this replica, and every change
       * behind it from every master, stops (issue #986).
       *
       * Given back by the thread which owns them, so that the rule every road which reads
       * ownership follows holds on this one as well: a change is given back by the thread it
       * was handed to and by nobody else (issue #922). It is also the one place which sees
       * them all - the pool is shared by every domain of this server, while a replay knows
       * only the domain it was replaying for.
       *
       * The session which brings them back is asked for and left standing, in every domain
       * which got something back, and the state checkpointer of each of them runs it within
       * its tick: a thread on its way out is not held for a session - the threads of the
       * pool are stopped one after the other and joined, and each running a restart of its
       * own would have the configuration change which is stopping them wait for one restart
       * per thread - and a change delivered again before the pool which replaces this one
       * is up waits in the replay queue for it. A thread which an OutOfMemoryError is ending
       * gives back here what it parked in the domains it was not replaying for, on the same
       * terms; the change it was replaying, and what it had parked in that same domain, were
       * given back and asked for again on its way out of replay().
       */
      giveBackParkedChanges();
    }
    if (logger.isTraceEnabled())
    {
      logger.trace("Replication Replay thread stopping.");
    }
  }

  /**
   * Takes the deliveries of the domains of this server off the shared replay queue and
   * replays them, until this thread is stopped.
   */
  private void replayUntilStopped()
  {
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
  }

  /**
   * Gives back the changes this thread parked as waiting for another change, in every
   * domain of this server.
   * <p>
   * A change which is given back stays listed and uncommitted, the way a change whose replay
   * failed does: it is not in the data, so it holds the ServerState of its domain back and
   * the changes which follow it keep waiting for it, until the delivery which takes it over
   * replays it.
   * <p>
   * Every domain gets its turn whatever one of them threw: what can throw here is an
   * allocation, on the way out of a thread an OutOfMemoryError may be ending - the iterator
   * over the domains, before any of them is reached, then for each of them the list of what
   * it released, made before anything is released, or the report of a change once it is,
   * and between two domains the list a second failure is recorded in under the first, which
   * the loop guards on its own - and the domains which follow would otherwise be left with
   * changes owned by a thread which does not exist anymore, the state this give-back is
   * for. A domain which threw past the release has asked for its restart already: the
   * request is made before the report. The first failure is thrown once the loop is over,
   * so that the uncaught exception handler of {@link DirectoryThread} writes the line and
   * raises the alert.
   */
  private void giveBackParkedChanges()
  {
    MultimasterReplication.forEachDomain(GIVE_BACK_PARKED_CHANGES);
  }
}
