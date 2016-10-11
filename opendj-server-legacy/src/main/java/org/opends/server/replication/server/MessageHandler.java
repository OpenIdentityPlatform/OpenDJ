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
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.replication.server;

import static org.opends.messages.ReplicationMessages.*;

import java.util.concurrent.atomic.AtomicBoolean;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.server.config.server.MonitorProviderCfg;
import org.opends.server.api.MonitorData;
import org.opends.server.api.MonitorProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.DBCursor;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;

/**
 * This class implements a buffering/producer/consumer mechanism of
 * replication changes (UpdateMsg) used inside the replication server.
 * It logically represents another RS than the current one, and is used when the
 * other RS is brought online and needs to catch up with the changes in the
 * current RS.
 *
 * MessageHandlers are registered into Replication server domains.
 * When an update message is received by a domain, the domain forwards
 * the message to the registered message handlers.
 * LocalizableMessage are buffered into a queue.
 * Consumers are expected to come and consume the UpdateMsg from the queue.
 */
class MessageHandler extends MonitorProvider<MonitorProviderCfg>
{
  /** The logger of this class. */
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private static final int MINIMUM_TRESHOLD_MSG_QUEUE_SIZE = 5;

  /** UpdateMsg queue. */
  private final MsgQueue msgQueue = new MsgQueue();
  /**
   * Late queue. All access to the lateQueue in getNextMessage() is
   * single-threaded. However, reads from threads calling getOlderUpdateCN()
   * need protecting against removals performed using getNextMessage().
   */
  private final MsgQueue lateQueue = new MsgQueue();
  /** Local hosting RS. */
  protected final ReplicationServer replicationServer;
  /** Specifies the related replication server domain based on baseDN. */
  protected ReplicationServerDomain replicationServerDomain;
  /** Number of update sent to the server. */
  private int outCount;
  /** Number of updates received from the server. */
  private int inCount;
  /** Specifies the max queue size for this handler. */
  protected final int maxQueueSize;
  /** Specifies the max queue size in bytes for this handler. */
  private final int maxQueueBytesSize;
  /** Specifies whether the consumer is following the producer (is not late). */
  private boolean following;
  /** Specifies the current serverState of this handler. */
  private ServerState serverState;
  /** Specifies the baseDN of the domain. */
  private DN baseDN;
  /**
   * Specifies whether the consumer is still active or not.
   * If not active, the handler will not return any message.
   * Called at the beginning of shutdown process.
   */
  private boolean activeConsumer = true;
  /** Set when ServerHandler is stopping. */
  private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

  /**
   * Creates a new server handler instance with the provided socket.
   * @param queueSize The maximum number of update that will be kept
   *                  in memory by this ServerHandler.
   * @param replicationServer The hosting replication server.
   */
  MessageHandler(int queueSize, ReplicationServer replicationServer)
  {
    this.maxQueueSize = queueSize;
    this.maxQueueBytesSize = queueSize * 100;
    this.replicationServer = replicationServer;
  }

  /**
   * Add an update to the list of updates that must be sent to the server
   * managed by this Handler.
   *
   * @param update The update that must be added to the list of updates of
   * this handler.
   */
  void add(UpdateMsg update)
  {
    synchronized (msgQueue)
    {
      /*
       * If queue was empty the writer thread was probably asleep
       * waiting for some changes, wake it up
       */
      if (msgQueue.isEmpty())
      {
        msgQueue.notify();
      }

      msgQueue.add(update);

      // TODO : size should be configurable and larger than max-receive-queue-size
      while (isMsgQueueAboveThreshold())
      {
        following = false;
        msgQueue.removeFirst();
      }
    }
  }

  private boolean isMsgQueueAboveThreshold()
  {
    final long count = msgQueue.count();
    // ensure there is a minimum number of messages in queue to avoid wrong behavior
    return count > maxQueueSize
        || (count >= MINIMUM_TRESHOLD_MSG_QUEUE_SIZE && msgQueue.bytesCount() > maxQueueBytesSize);
  }

  private boolean isMsgQueueBelowThreshold()
  {
    return !isMsgQueueAboveThreshold();
  }

  /**
   * Set the shut down flag to true and returns the previous value of the flag.
   * @return The previous value of the shut down flag
   */
  boolean engageShutdown()
  {
    return shuttingDown.getAndSet(true);
  }

  /**
   * Returns the shutdown flag.
   * @return The shutdown flag value.
   */
  boolean shuttingDown()
  {
    return shuttingDown.get();
  }

  /**
   * Returns the Replication Server Domain to which belongs this handler.
   *
   * @param waitConnections     Waits for the Connections with other RS to
   *                            be established before returning.
   */
  private void setDomain(boolean waitConnections)
  {
    if (replicationServerDomain == null)
    {
      replicationServerDomain = replicationServer.getReplicationServerDomain(
          baseDN, true);
      if (waitConnections)
      {
        replicationServer.waitConnections();
      }
    }
  }

  /**
   * Get the count of updates received from the server.
   * @return the count of update received from the server.
   */
  int getInCount()
  {
    return inCount;
  }

  @Override
  public MonitorData getMonitorData()
  {
    MonitorData attributes = new MonitorData(4);
    attributes.add("handler", getMonitorInstanceName());
    attributes.add("queue-size", msgQueue.count());
    attributes.add("queue-size-bytes", msgQueue.bytesCount());
    attributes.add("following", following);
    return attributes;
  }

  /**
   * Retrieves the name of this monitor provider.  It should be unique among all
   * monitor providers, including all instances of the same monitor provider.
   *
   * @return  The name of this monitor provider.
   */
  @Override
  public String getMonitorInstanceName()
  {
    return "LocalizableMessage Handler";
  }

  /**
   * Get the next update that must be sent to the consumer from the message queue or from the database.
   *
   * @return The next update that must be sent to the consumer, or {@code null} when queue is empty
   * @throws ChangelogException
   *            If a problem occurs when reading the changelog
   */
  protected UpdateMsg getNextMessage() throws ChangelogException
  {
    while (activeConsumer)
    {
      if (!following)
      {
        /* this server is late with regard to some other masters
         * in the topology or just joined the topology.
         * In such cases, we can't keep all changes in the queue
         * without saturating the memory, we therefore use
         * a lateQueue that is filled with a few changes from the changelogDB
         * If this server is able to close the gap, it will start using again
         * the regular msgQueue later.
         */
        if (lateQueue.isEmpty())
        {
          /*
           * Start from the server State
           * Loop until the queue high mark or until no more changes
           *   for each known LDAP master
           *      get the next CSN after this last one :
           *         - try to get next from the file
           *         - if not found in the file
           *             - try to get the next from the queue
           *   select the smallest of changes
           *   check if it is in the memory tree
           *     yes : lock memory tree.
           *           check all changes from the list, remove the ones that
           *           are already sent
           *           unlock memory tree
           *           restart as usual
           *   load this change on the delayList
           */
          fillLateQueue();
          if (lateQueue.isEmpty())
          {
            // we could not find any messages in the changelog
            // so the remote server is not late anymore.
            synchronized (msgQueue)
            {
              // Ensure we are below threshold so this server will follow the
              // msgQueue without fearing the msgQueue gets trimmed
              if (isMsgQueueBelowThreshold())
              {
                following = true;
              }
            }
          }
          else
          {
            /*
             * if the first change in the lateQueue is also on the regular queue,
             * we can resume the processing from the regular queue
             * -> set following to true and empty the lateQueue.
             */
            UpdateMsg msg = lateQueue.first();
            synchronized (msgQueue)
            {
              if (msgQueue.contains(msg))
              {
                /* we finally catch up with the regular queue */
                following = true;
                lateQueue.clear();
                msgQueue.consumeUpTo(msg);
                if (updateServerState(msg))
                {
                  return msg;
                }
              }
            }
          }
        }
        else
        {
          // get the next change from the lateQueue
          UpdateMsg msg;
          synchronized (msgQueue) // TODO JNR why synchronize(msgQueue) here?
          {
            msg = lateQueue.removeFirst();
            // By default a server is always not following. A weird case where messages not representing
            // an operation may happen, making the late queue repeatedly fill and be emptied without ever
            // getting the server out of state "not following".
            if (lateQueue.isEmpty() && msgQueue.isEmpty())
            {
              following = true;
            }
          }
          if (updateServerState(msg))
          {
            return msg;
          }
          continue;
        }
      }

      synchronized (msgQueue)
      {
        if (following)
        {
          try
          {
            while (msgQueue.isEmpty() && following)
            {
              msgQueue.wait(500);
              if (!activeConsumer)
              {
                return null;
              }
            }
          } catch (InterruptedException e)
          {
            return null;
          }
          UpdateMsg msg = msgQueue.removeFirst();
          if (updateServerState(msg))
          {
            /*
             * Only push the message if it has not yet been seen
             * by the other server.
             * Otherwise just loop to select the next message.
             */
            return msg;
          }
        }
      }
      /*
       * Need to loop because following flag may have gone to false between the
       * first check at the beginning of this method and the second check just
       * above.
       */
    }
    return null;
  }

  /**
   * Fills the late queue with the most recent changes, accepting only the
   * messages from provided replica ids.
   */
  private void fillLateQueue() throws ChangelogException
  {
    try (DBCursor<UpdateMsg> cursor = replicationServerDomain.getCursorFrom(serverState);)
    {
      while (cursor.next() && isLateQueueBelowThreshold())
      {
        lateQueue.add(cursor.getRecord());
      }
    }
  }

  private boolean isLateQueueBelowThreshold()
  {
    return lateQueue.count() < 100 && lateQueue.bytesCount() < 50000;
  }

  /**
   * Get the older CSN for that server.
   * Returns null when the queue is empty.
   * @return The older CSN.
   */
  public CSN getOlderUpdateCSN()
  {
    synchronized (msgQueue)
    {
      if (following)
      {
        if (!msgQueue.isEmpty())
        {
          return msgQueue.first().getCSN();
        }
      }
      else
      {
        if (!lateQueue.isEmpty())
        {
          return lateQueue.first().getCSN();
        }
        else
        {
          /*
          following is false AND lateQueue is empty
          We may be at the very moment when the writer has emptied the
          lateQueue when it sent the last update. The writer will fill again
          the lateQueue when it will send the next update but we are not yet
          there. So let's take the last change not sent directly from the db.
          */
          return findOldestCSNFromReplicaDBs();
        }
      }
    }
    return null;
  }

  private CSN findOldestCSNFromReplicaDBs()
  {
    try (DBCursor<UpdateMsg> cursor = replicationServerDomain.getCursorFrom(serverState))
    {
      while (cursor.next())
      {
        final UpdateMsg record = cursor.getRecord();
        if (record.contributesToDomainState())
        {
          return record.getCSN();
        }
      }
      return null;
    }
    catch (ChangelogException e)
    {
      return null;
    }
  }

  /**
   * Get the count of updates sent to this server.
   * @return  The count of update sent to this server.
   */
  int getOutCount()
  {
    return outCount;
  }

  /**
   * Get the number of message in the receive message queue.
   * @return Size of the receive message queue.
   */
  public int getRcvMsgQueueSize()
  {
    synchronized (msgQueue)
    {
      /*
       * When the server is up to date or close to be up to date,
       * the number of updates to be sent is the size of the receive queue.
       */
      if (following)
      {
        return msgQueue.count();
      }

      /*
       * When the server is not able to follow, the msgQueue may become too
       * large and therefore won't contain all the changes. Some changes may
       * only be stored in the backing DB of the servers.
       * The total size of the receive queue is calculated by doing the sum of
       * the number of missing changes for every replicaDB.
       */
      ServerState latestState = replicationServerDomain.getLatestServerState();
      return ServerState.diffChanges(latestState, serverState);
    }
  }

  /**
   * Get the state of this server.
   *
   * @return ServerState the state for this server..
   */
  public ServerState getServerState()
  {
    return serverState;
  }

  /**
   * Get the baseDN for this handler.
   *
   * @return The baseDN.
   */
  protected DN getBaseDN()
  {
    return baseDN;
  }

  /** Increase the counter of updates received from the server. */
  void incrementInCount()
  {
    inCount++;
  }

  /** Increase the counter of updates sent to the server. */
  void incrementOutCount()
  {
    outCount++;
  }

  /** {@inheritDoc} */
  @Override
  public void initializeMonitorProvider(MonitorProviderCfg configuration)
  throws ConfigException, InitializationException
  {
    // Nothing to do for now
  }

  /**
   * Set that the consumer is now becoming inactive and thus getNextMessage
   * should not return any UpdateMsg any more.
   * @param active the provided state of the consumer.
   */
  public void setConsumerActive(boolean active)
  {
    this.activeConsumer = active;
  }

  /**
   * Set the initial value of the serverState for this handler.
   * Expected to be done once, then the state will be updated using
   * updateServerState() method.
   * @param serverState the provided serverState.
   * @exception DirectoryException raised when a problem occurs.
   */
  public void setInitialServerState(ServerState serverState)
  throws DirectoryException
  {
    this.serverState = serverState;
  }

  /**
   * Set the baseDN for this handler. Expected to be done once and never changed
   * during the handler life.
   *
   * @param baseDN
   *          The provided baseDN.
   * @param isDataServer
   *          The handler is a dataServer
   * @exception DirectoryException
   *              raised when a problem occurs.
   */
  protected void setBaseDNAndDomain(DN baseDN, boolean isDataServer)
      throws DirectoryException
  {
    if (this.baseDN != null)
    {
      if (!this.baseDN.equals(baseDN))
      {
        LocalizableMessage message = ERR_RS_DN_DOES_NOT_MATCH.get(this.baseDN, baseDN);
        throw new DirectoryException(ResultCode.OTHER, message, null);
      }
    }
    else
    {
      this.baseDN = baseDN;
      setDomain(!"cn=changelog".equals(baseDN.toNormalizedUrlSafeString())
          && isDataServer);
    }
  }

  /** Shutdown this handler. */
  public void shutdown()
  {
    synchronized (msgQueue)
    {
      msgQueue.clear();
      msgQueue.notify();
      msgQueue.notifyAll();
    }

    DirectoryServer.deregisterMonitorProvider(this);
  }

  /**
   * Update the serverState with the last message sent.
   *
   * @param msg the last update sent.
   * @return boolean indicating if the update was meaningful.
   */
  boolean updateServerState(UpdateMsg msg)
  {
    return msg.contributesToDomainState()
        ? serverState.update(msg.getCSN())
        // Do not update the server state with an offline CSN,
        // to avoid missing-changes being incorrectly reported
        : !serverState.cover(msg.getCSN());
  }

  /**
   * Get the groupId of the hosting RS.
   * @return the group id.
   */
  public byte getLocalGroupId()
  {
    return replicationServer.getGroupId();
  }

  /**
   * Get the serverId of the hosting replication server.
   * @return the replication serverId.
   */
  public int getReplicationServerId()
  {
    return this.replicationServer.getServerId();
  }

  /**
   * Get the server URL of the hosting replication server.
   *
   * @return the replication server URL.
   */
  public String getReplicationServerURL()
  {
    return this.replicationServer.getServerURL();
  }

  @Override
  public String toString()
  {
    return getMonitorInstanceName();
  }
}
