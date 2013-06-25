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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions copyright 2011-2013 ForgeRock AS
 */
package org.opends.server.replication.server;

import static org.opends.messages.ReplicationMessages.ERR_RS_DN_DOES_NOT_MATCH;
import static org.opends.server.loggers.debug.DebugLogger.*;

import java.util.ArrayList;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

import org.opends.messages.Message;
import org.opends.server.admin.std.server.MonitorProviderCfg;
import org.opends.server.api.MonitorProvider;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;

/**
 * This class implements a buffering/producer/consumer mechanism of
 * replication changes (UpdateMsg) used inside the replication server.
 *
 * MessageHandlers are registered into Replication server domains.
 * When an update message is received by a domain, the domain forwards
 * the message to the registered message handlers.
 * Message are buffered into a queue.
 * Consumers are expected to come and consume the UpdateMsg from the queue.
 */
public class MessageHandler extends MonitorProvider<MonitorProviderCfg>
{

  /**
   * The tracer object for the debug logger.
   */
  protected static final DebugTracer TRACER = getTracer();
  /**
   * UpdateMsg queue.
   */
  private final MsgQueue msgQueue = new MsgQueue();
  /**
   * Late queue. All access to the lateQueue in getNextMessage() is
   * single-threaded. However, reads from threads calling getOlderUpdateCN()
   * need protecting against removals performed using getNextMessage().
   */
  private final MsgQueue lateQueue = new MsgQueue();
  /**
   * Local hosting RS.
   */
  protected ReplicationServer replicationServer = null;
  /**
   * The URL of the hosting replication server.
   */
  protected String replicationServerURL = null;
  /**
   * The serverID of the hosting replication server.
   */
  protected int replicationServerId;
  /**
   * Specifies the related replication server domain based on serviceId(baseDn).
   */
  protected ReplicationServerDomain replicationServerDomain = null;
  /**
   * Number of update sent to the server.
   */
  protected int outCount = 0;
  /**
   * Number of updates received from the server.
   */
  protected int inCount = 0;
  /**
   * Specifies the max queue size for this handler.
   */
  protected int maxQueueSize = 5000;
  /**
   * Specifies the max queue size in bytes for this handler.
   */
  protected int maxQueueBytesSize = maxQueueSize * 100;
  /**
   * Specifies whether the consumer is following the producer (is not late).
   */
  protected boolean following = false;
  /**
   * Specifies the current serverState of this handler.
   */
  private ServerState serverState;
  /**
   * Specifies the identifier of the service (usually the baseDn of the domain).
   */
  private String serviceId = null;
  /**
   * Specifies whether the consumer is still active or not.
   * If not active, the handler will not return any message.
   * Called at the beginning of shutdown process.
   */
  private boolean activeConsumer = true;
  /**
   * Set when ServerHandler is stopping.
   */
  private AtomicBoolean shuttingDown = new AtomicBoolean(false);

  /**
   * Creates a new server handler instance with the provided socket.
   * @param queueSize The maximum number of update that will be kept
   *                  in memory by this ServerHandler.
   * @param replicationServerURL The URL of the hosting replication server.
   * @param replicationServerId The ID of the hosting replication server.
   * @param replicationServer The hosting replication server.
   */
  public MessageHandler(
      int queueSize,
      String replicationServerURL,
      int replicationServerId,
      ReplicationServer replicationServer)
  {
    this.maxQueueSize = queueSize;
    this.maxQueueBytesSize = queueSize * 100;
    this.replicationServerURL = replicationServerURL;
    this.replicationServerId = replicationServerId;
    this.replicationServer = replicationServer;
  }

  /**
   * Add an update to the list of updates that must be sent to the server
   * managed by this Handler.
   *
   * @param update The update that must be added to the list of updates of
   * this handler.
   * @param sourceHandler The source handler that generated the update.
   */
  public void add(UpdateMsg update, MessageHandler sourceHandler)
  {
    synchronized (msgQueue)
    {
      /*
       * If queue was empty the writer thread was probably asleep
       * waiting for some changes, wake it up
       */
      if (msgQueue.isEmpty())
        msgQueue.notify();

      msgQueue.add(update);

      /* TODO : size should be configurable
       * and larger than max-receive-queue-size
       */
      while ((msgQueue.count() > maxQueueSize) ||
          (msgQueue.bytesCount() > maxQueueBytesSize))
      {
        following = false;
        msgQueue.removeFirst();
      }
    }
  }
  /**
   * Set the shut down flag to true and returns the previous value of the flag.
   * @return The previous value of the shut down flag
   */
  public boolean engageShutdown()
  {
    // Use thread safe boolean
    return shuttingDown.getAndSet(true);
  }

  /**
   * Returns the Replication Server Domain to which belongs this handler.
   *
   * @param createIfNotExist    Creates the domain if it does not exist.
   * @param waitConnections     Waits for the Connections with other RS to
   *                            be established before returning.
   * @return The replication server domain.
   */
  public ReplicationServerDomain getDomain(
      boolean createIfNotExist, boolean waitConnections)
  {
    if (replicationServerDomain==null)
    {
      replicationServerDomain =
        replicationServer.getReplicationServerDomain(
            serviceId, createIfNotExist, waitConnections);
    }
    return replicationServerDomain;
  }

  /**
   * Get the count of updates received from the server.
   * @return the count of update received from the server.
   */
  public int getInCount()
  {
    return inCount;
  }

  /**
   * Retrieves a set of attributes containing monitor data that should be
   * returned to the client if the corresponding monitor entry is requested.
   *
   * @return  A set of attributes containing monitor data that should be
   *          returned to the client if the corresponding monitor entry is
   *          requested.
   */
  @Override
  public ArrayList<Attribute> getMonitorData()
  {
    ArrayList<Attribute> attributes = new ArrayList<Attribute>();
    attributes.add(Attributes.create("handler", getMonitorInstanceName()));
    attributes.add(
        Attributes.create("queue-size", String.valueOf(msgQueue.count())));
    attributes.add(
        Attributes.create(
            "queue-size-bytes", String.valueOf(msgQueue.bytesCount())));
    attributes.add(
        Attributes.create(
            "following", String.valueOf(following)));
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
    return "Message Handler";
  }

  /**
   * Get the next update that must be sent to the consumer
   * from the message queue or from the database.
   *
   * @param  synchronous specifies what to do when the queue is empty.
   *         when true, the method blocks; when false the method return null.
   * @return The next update that must be sent to the consumer.
   *         null when synchronous is false and queue is empty.
   */
  protected UpdateMsg getNextMessage(boolean synchronous)
  {
    UpdateMsg msg;
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
           *
           */
          ReplicationIteratorComparator comparator =
            new ReplicationIteratorComparator();
          SortedSet<ReplicationIterator> iteratorSortedSet =
            new TreeSet<ReplicationIterator>(comparator);
          try
          {
            /* fill the lateQueue */
            for (int serverId : replicationServerDomain.getServers())
            {
              ChangeNumber lastCsn = serverState
                  .getMaxChangeNumber(serverId);
              ReplicationIterator iterator = replicationServerDomain
                  .getChangelogIterator(serverId, lastCsn);
              if (iterator != null)
              {
                if (iterator.getChange() != null)
                {
                  iteratorSortedSet.add(iterator);
                }
                else
                {
                  iterator.releaseCursor();
                }
              }
            }

            // The loop below relies on the fact that it is sorted based
            // on the currentChange of each iterator to consider the next
            // change across all servers.
            //
            // Hence it is necessary to remove and eventual add again an
            // iterator when looping in order to keep consistent the order of
            // the iterators (see ReplicationIteratorComparator.
            while (!iteratorSortedSet.isEmpty()
                && (lateQueue.count() < 100)
                && (lateQueue.bytesCount() < 50000))
            {
              ReplicationIterator iterator = iteratorSortedSet
                  .first();
              iteratorSortedSet.remove(iterator);
              lateQueue.add(iterator.getChange());
              if (iterator.next())
              {
                iteratorSortedSet.add(iterator);
              }
              else
              {
                iterator.releaseCursor();
              }
            }
          }
          finally
          {
            for (ReplicationIterator iterator : iteratorSortedSet)
            {
              iterator.releaseCursor();
            }
          }

          /*
           * If the late queue is empty then we could not find any
           * messages in the replication log so the remote serevr is not
           * late anymore.
           */

          if (lateQueue.isEmpty())
          {
            synchronized (msgQueue)
            {
              if ((msgQueue.count() < maxQueueSize) &&
                  (msgQueue.bytesCount() < maxQueueBytesSize))
              {
                following = true;
              }
            }
          } else
          {
            /*
             * if the first change in the lateQueue is also on the regular
             * queue, we can resume the processing from the regular queue
             * -> set following to true and empty the lateQueue.
             */
            msg = lateQueue.first();
            synchronized (msgQueue)
            {
              if (msgQueue.contains(msg))
              {
                /* we finally catch up with the regular queue */
                following = true;
                lateQueue.clear();
                UpdateMsg msg1;
                do
                {
                  msg1 = msgQueue.removeFirst();
                } while (!msg.getChangeNumber().equals(msg1.getChangeNumber()));
                this.updateServerState(msg);
                return msg1;
              }
            }
          }
        } else
        {
          /* get the next change from the lateQueue */
          synchronized (msgQueue)
          {
            msg = lateQueue.removeFirst();
          }
          this.updateServerState(msg);
          return msg;
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
              if (!synchronous)
                return null;
              msgQueue.wait(500);
              if (!activeConsumer)
                return null;
            }
          } catch (InterruptedException e)
          {
            return null;
          }
          msg = msgQueue.removeFirst();

          if (this.updateServerState(msg))
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
       * Need to loop because following flag may have gone to false between
       * the first check at the beginning of this method
       * and the second check just above.
       */
    }
    return null;
  }

  /**
   * Get the older Change Number for that server.
   * Returns null when the queue is empty.
   * @return The older change number.
   */
  public ChangeNumber getOlderUpdateCN()
  {
    ChangeNumber result = null;
    synchronized (msgQueue)
    {
      if (following)
      {
        if (msgQueue.isEmpty())
        {
          result = null;
        } else
        {
          UpdateMsg msg = msgQueue.first();
          result = msg.getChangeNumber();
        }
      } else
      {
        if (lateQueue.isEmpty())
        {
          /*
          following is false AND lateQueue is empty
          We may be at the very moment when the writer has emptied the
          lateQueue when it sent the last update. The writer will fill again
          the lateQueue when it will send the next update but we are not yet
          there. So let's take the last change not sent directly from
          the db.
          */
          ReplicationIteratorComparator comparator =
            new ReplicationIteratorComparator();
          SortedSet<ReplicationIterator> iteratorSortedSet =
            new TreeSet<ReplicationIterator>(comparator);
          try
          {
            // Build a list of candidates iterator (i.e. db i.e. server)
            for (int serverId : replicationServerDomain.getServers())
            {
              // get the last already sent CN from that server
              ChangeNumber lastCsn = serverState.getMaxChangeNumber(serverId);
              // get an iterator in this server db from that last change
              ReplicationIterator iterator =
                replicationServerDomain.getChangelogIterator(serverId, lastCsn);
              /*
              if that iterator has changes, then it is a candidate
              it is added in the sorted list at a position given by its
              current change (see ReplicationIteratorComparator).
              */
              if (iterator != null)
              {
                if (iterator.getChange() != null)
                {
                  iteratorSortedSet.add(iterator);
                }
                else
                {
                  iterator.releaseCursor();
                }
              }
            }
            UpdateMsg msg = iteratorSortedSet.first().getChange();
            result = msg.getChangeNumber();
          } catch (Exception e)
          {
            result = null;
          } finally
          {
            for (ReplicationIterator iterator : iteratorSortedSet)
            {
              iterator.releaseCursor();
            }
          }
        } else
        {
          UpdateMsg msg = lateQueue.first();
          result = msg.getChangeNumber();
        }
      }
    }
    return result;
  }

  /**
   * Get the count of updates sent to this server.
   * @return  The count of update sent to this server.
   */
  public int getOutCount()
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
        return msgQueue.count();
      else
      {
        /**
         * When the server  is not able to follow, the msgQueue
         * may become too large and therefore won't contain all the
         * changes. Some changes may only be stored in the backing DB
         * of the servers.
         * The total size of the receive queue is calculated by doing
         * the sum of the number of missing changes for every dbHandler.
         */
        ServerState dbState = replicationServerDomain.getDbServerState();
        return ServerState.diffChanges(dbState, serverState);
      }
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
   * Get the name of the serviceId (usually baseDn) for this handler.
   * @return The name of the serviceId.
   */
  protected String getServiceId()
  {
    return serviceId;
  }

  /**
   * Increase the counter of update received from the server.
   */
  public void incrementInCount()
  {
    inCount++;
  }

  /**
   * Increase the counter of updates sent to the server.
   */
  public void incrementOutCount()
  {
    outCount++;
  }

  /**
   * {@inheritDoc}
   */
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
   * Set the serviceId (usually baseDn) for this handler. Expected to be done
   * once and never changed during the handler life.
   *
   * @param serviceId       The provided serviceId.
   * @param isDataServer    The handler is a dataServer
   *
   * @exception DirectoryException raised when a problem occurs.
   */
  protected void setServiceIdAndDomain(String serviceId, boolean isDataServer)
  throws DirectoryException
  {
    if (this.serviceId != null)
    {
      if (!this.serviceId.equalsIgnoreCase(serviceId))
      {
        Message message = ERR_RS_DN_DOES_NOT_MATCH.get(
            this.serviceId,
            serviceId);
        throw new DirectoryException(ResultCode.OTHER,
            message, null);
      }
    }
    else
    {
      this.serviceId = serviceId;
      if (!serviceId.equalsIgnoreCase("cn=changelog"))
        this.replicationServerDomain = getDomain(true, isDataServer);
    }
  }

  /**
   * Shutdown this handler.
   */
  public void shutdown()
  {
    /*
     * Shutdown ServerWriter
     */
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
  public boolean updateServerState(UpdateMsg msg)
  {
    return serverState.update(msg.getChangeNumber());
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
    return this.replicationServerId;
  }
}
