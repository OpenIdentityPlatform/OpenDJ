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
 *      Copyright 2009 Sun Microsystems, Inc.
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
import org.opends.server.util.TimeThread;

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
   * Late queue.
   */
  protected MsgQueue lateQueue = new MsgQueue();
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
  protected short replicationServerId;
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
   * Specifies the max receive queue for this handler.
   */
  protected int maxReceiveQueue = 0;
  /**
   * Specifies the max send queue for this handler.
   */
  protected int maxSendQueue = 0;
  /**
   * Specifies the max receive delay for this handler.
   */
  protected int maxReceiveDelay = 0;
  /**
   * Specifies the max send delay for this handler.
   */
  protected int maxSendDelay = 0;
  /**
   * Specifies the max queue size for this handler.
   */
  protected int maxQueueSize = 5000;
  /**
   * Specifies the max queue size in bytes for this handler.
   */
  protected int maxQueueBytesSize = maxQueueSize * 100;
  /**
   * Specifies the max restart receive queue for this handler.
   */
  protected int restartReceiveQueue;
  /**
   * Specifies the max restart send queue for this handler.
   */
  protected int restartSendQueue;
  /**
   * Specifies the max restart receive delay for this handler.
   */
  protected int restartReceiveDelay;
  /**
   * Specifies the max restart send delay for this handler.
   */
  protected int restartSendDelay;
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
   * Specifies whether the server is flow controlled and should be stopped from
   * sending messages.
   */
  protected boolean flowControl = false;
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
      short replicationServerId,
      ReplicationServer replicationServer)
  {
    super("Message Handler");
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
        setFollowing(false);
        msgQueue.removeFirst();
      }
    }

    if (isSaturated(update.getChangeNumber(), sourceHandler))
    {
      sourceHandler.setSaturated(true);
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
   * Get an approximation of the delay by looking at the age of the oldest
   * message that has not been sent to this server.
   * This is an approximation because the age is calculated using the
   * clock of the server where the replicationServer is currently running
   * while it should be calculated using the clock of the server
   * that originally processed the change.
   *
   * The approximation error is therefore the time difference between
   *
   * @return the approximate delay for the connected server.
   */
  public long getApproxDelay()
  {
    long olderUpdateTime = getOlderUpdateTime();
    if (olderUpdateTime == 0)
      return 0;

    long currentTime = TimeThread.getTime();
    return ((currentTime - olderUpdateTime) / 1000);
  }

  /**
   * Get the age of the older change that has not yet been replicated
   * to the server handled by this ServerHandler.
   * @return The age if the older change has not yet been replicated
   *         to the server handled by this ServerHandler.
   */
  public Long getApproxFirstMissingDate()
  {
    Long result = (long) 0;

    // Get the older CN received
    ChangeNumber olderUpdateCN = getOlderUpdateCN();
    if (olderUpdateCN != null)
    {
      // If not present in the local RS db,
      // then approximate with the older update time
      result = olderUpdateCN.getTime();
    }
    return result;
  }

  /**
   * Returns the Replication Server Domain to which belongs this handler.
   * @param createIfNotExist Creates the domain if it does not exist.
   * @return The replication server domain.
   */
  public ReplicationServerDomain getDomain(boolean createIfNotExist)
  {
    if (replicationServerDomain==null)
    {
      replicationServerDomain =
      replicationServer.getReplicationServerDomain(serviceId,createIfNotExist);
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
  protected UpdateMsg getnextMessage(boolean synchronous)
  {
    UpdateMsg msg;
    while (activeConsumer == true)
    {
      if (following == false)
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
          /* fill the lateQueue */
          for (short serverId : replicationServerDomain.getServers())
          {
            ChangeNumber lastCsn = serverState.getMaxChangeNumber(serverId);
            ReplicationIterator iterator =
              replicationServerDomain.getChangelogIterator(serverId, lastCsn);
            if (iterator != null)
            {
              if (iterator.getChange() != null)
              {
                iteratorSortedSet.add(iterator);
              } else
              {
                iterator.releaseCursor();
              }
            }
          }

          // The loop below relies on the fact that it is sorted based
          // on the currentChange of each iterator to consider the next
          // change across all servers.
          // Hence it is necessary to remove and eventual add again an iterator
          // when looping in order to keep consistent the order of the
          // iterators (see ReplicationIteratorComparator.
          while (!iteratorSortedSet.isEmpty() &&
              (lateQueue.count()<100) &&
              (lateQueue.bytesCount()<50000) )
          {
            ReplicationIterator iterator = iteratorSortedSet.first();
            iteratorSortedSet.remove(iterator);
            lateQueue.add(iterator.getChange());
            if (iterator.next())
              iteratorSortedSet.add(iterator);
            else
              iterator.releaseCursor();
          }
          for (ReplicationIterator iterator : iteratorSortedSet)
          {
            iterator.releaseCursor();
          }
          /*
           * Check if the first change in the lateQueue is also on the regular
           * queue
           */
          if (lateQueue.isEmpty())
          {
            synchronized (msgQueue)
            {
              if ((msgQueue.count() < maxQueueSize) &&
                  (msgQueue.bytesCount() < maxQueueBytesSize))
              {
                setFollowing(true);
              }
            }
          } else
          {
            msg = lateQueue.first();
            synchronized (msgQueue)
            {
              if (msgQueue.contains(msg))
              {
                /* we finally catch up with the regular queue */
                setFollowing(true);
                lateQueue.clear();
                UpdateMsg msg1;
                do
                {
                  msg1 = msgQueue.removeFirst();
                } while (!msg.getChangeNumber().equals(msg1.getChangeNumber()));
                this.updateServerState(msg);
                return msg;
              }
            }
          }
        } else
        {
          /* get the next change from the lateQueue */
          msg = lateQueue.removeFirst();
          this.updateServerState(msg);
          return msg;
        }
      }
      synchronized (msgQueue)
      {
        if (following == true)
        {
          try
          {
            while (msgQueue.isEmpty() && (following == true))
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
      if (isFollowing())
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
          // isFollowing is false AND lateQueue is empty
          // We may be at the very moment when the writer has emptyed the
          // lateQueue when it sent the last update. The writer will fill again
          // the lateQueue when it will send the next update but we are not yet
          // there. So let's take the last change not sent directly from
          // the db.

          ReplicationIteratorComparator comparator =
            new ReplicationIteratorComparator();
          SortedSet<ReplicationIterator> iteratorSortedSet =
            new TreeSet<ReplicationIterator>(comparator);
          try
          {
            // Build a list of candidates iterator (i.e. db i.e. server)
            for (short serverId : replicationServerDomain.getServers())
            {
              // get the last already sent CN from that server
              ChangeNumber lastCsn = serverState.getMaxChangeNumber(serverId);
              // get an iterator in this server db from that last change
              ReplicationIterator iterator =
                replicationServerDomain.getChangelogIterator(serverId, lastCsn);
              // if that iterator has changes, then it is a candidate
              // it is added in the sorted list at a position given by its
              // current change (see ReplicationIteratorComparator).
              if ((iterator != null) && (iterator.getChange() != null))
              {
                iteratorSortedSet.add(iterator);
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
   * Get the older update time for that server.
   * @return The older update time.
   */
  public long getOlderUpdateTime()
  {
    ChangeNumber olderUpdateCN = getOlderUpdateCN();
    if (olderUpdateCN == null)
      return 0;
    return olderUpdateCN.getTime();
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
      if (isFollowing())
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
   * Retrieves the length of time in milliseconds that should elapse between
   * calls to the <CODE>updateMonitorData()</CODE> method.  A negative or zero
   * return value indicates that the <CODE>updateMonitorData()</CODE> method
   * should not be periodically invoked.
   *
   * @return  The length of time in milliseconds that should elapse between
   *          calls to the <CODE>updateMonitorData()</CODE> method.
   */
  @Override
  public long getUpdateInterval()
  {
    /* we don't wont to do polling on this monitor */
    return 0;
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
   * Check if the LDAP server can follow the speed of the other servers.
   * @return true when the server has all the not yet sent changes
   *         in its queue.
   */
  public boolean isFollowing()
  {
    return following;
  }

  /**
   * Check is this server is saturated (this server has already been
   * sent a bunch of updates and has not processed them so they are staying
   * in the message queue for this server an the size of the queue
   * for this server is above the configured limit.
   *
   * The limit can be defined in number of updates or with a maximum delay
   *
   * @param changeNumber The changenumber to use to make the delay calculations.
   * @param sourceHandler The ServerHandler which is sending the update.
   * @return true is saturated false if not saturated.
   */
  public boolean isSaturated(ChangeNumber changeNumber,
      MessageHandler sourceHandler)
  {
    synchronized (msgQueue)
    {
      int size = msgQueue.count();

      if ((maxReceiveQueue > 0) && (size >= maxReceiveQueue))
        return true;

      if ((sourceHandler.maxSendQueue > 0) &&
          (size >= sourceHandler.maxSendQueue))
        return true;

      if (!msgQueue.isEmpty())
      {
        UpdateMsg firstUpdate = msgQueue.first();

        if (firstUpdate != null)
        {
          long timeDiff = changeNumber.getTimeSec() -
          firstUpdate.getChangeNumber().getTimeSec();

          if ((maxReceiveDelay > 0) && (timeDiff >= maxReceiveDelay))
            return true;

          if ((sourceHandler.maxSendDelay > 0) &&
              (timeDiff >= sourceHandler.maxSendDelay))
            return true;
        }
      }
      return false;
    }
  }

  /**
   * Check that the size of the Server Handler messages Queue has lowered
   * below the limit and therefore allowing the reception of messages
   * from other servers to restart.
   * @param source The ServerHandler which was sending the update.
   *        can be null.
   * @return true if the processing can restart
   */
  public boolean restartAfterSaturation(MessageHandler source)
  {
    synchronized (msgQueue)
    {
      int queueSize = msgQueue.count();
      if ((maxReceiveQueue > 0) && (queueSize >= restartReceiveQueue))
        return false;
      if ((source != null) && (source.maxSendQueue > 0) &&
          (queueSize >= source.restartSendQueue))
        return false;

      if (!msgQueue.isEmpty())
      {
        UpdateMsg firstUpdate = msgQueue.first();
        UpdateMsg lastUpdate = msgQueue.last();

        if ((firstUpdate != null) && (lastUpdate != null))
        {
          long timeDiff = lastUpdate.getChangeNumber().getTimeSec() -
          firstUpdate.getChangeNumber().getTimeSec();
          if ((maxReceiveDelay > 0) && (timeDiff >= restartReceiveDelay))
            return false;
          if ((source != null) && (source.maxSendDelay > 0) && (timeDiff >=
            source.restartSendDelay))
            return false;
        }
      }
    }
    return true;
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
   * Set the following flag of this server.
   * @param following the value that should be set.
   */
  private void setFollowing(boolean following)
  {
    this.following = following;
  }

  private void setSaturated(boolean value)
  {
    flowControl = value;
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
   * @param serviceId The provided serviceId.
   * @exception DirectoryException raised when a problem occurs.
   */
  protected void setServiceIdAndDomain(String serviceId)
  throws DirectoryException
  {
    if (this.serviceId != null)
    {
      if (!this.serviceId.equalsIgnoreCase(serviceId))
      {
        Message message = ERR_RS_DN_DOES_NOT_MATCH.get(
            this.serviceId.toString(),
            serviceId.toString());
        throw new DirectoryException(ResultCode.OTHER,
            message, null);
      }
    }
    else
    {
      this.serviceId = serviceId;
      this.replicationServerDomain = getDomain(true);
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

    DirectoryServer.deregisterMonitorProvider(getMonitorInstanceName());
  }

  /**
   * Performs any processing periodic processing that may be desired to update
   * the information associated with this monitor.  Note that best-effort
   * attempts will be made to ensure that calls to this method come
   * <CODE>getUpdateInterval()</CODE> milliseconds apart, but no guarantees will
   * be made.
   */
  @Override
  public void updateMonitorData()
  {
    // As long as getUpdateInterval() returns 0, this will never get called
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

}
