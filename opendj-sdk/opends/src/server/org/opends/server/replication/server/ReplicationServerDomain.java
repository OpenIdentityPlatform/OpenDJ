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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions copyright 2011-2013 ForgeRock AS
 */
package org.opends.server.replication.server;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import org.opends.messages.Category;
import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.messages.Severity;
import org.opends.server.admin.std.server.MonitorProviderCfg;
import org.opends.server.api.MonitorProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.replication.common.*;
import org.opends.server.replication.protocol.*;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.ReplicaDBCursor;
import org.opends.server.replication.server.changelog.je.DbHandler;
import org.opends.server.types.*;

import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.replication.protocol.ProtocolVersion.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class define an in-memory cache that will be used to store
 * the messages that have been received from an LDAP server or
 * from another replication server and that should be forwarded to
 * other servers.
 *
 * The size of the cache is set by configuration.
 * If the cache becomes bigger than the configured size, the older messages
 * are removed and should they be needed again must be read from the backing
 * file
 *
 * it runs a thread that is responsible for saving the messages
 * received to the disk and for trimming them
 * Decision to trim can be based on disk space or age of the message
 */
public class ReplicationServerDomain extends MonitorProvider<MonitorProviderCfg>
{
  private final String baseDn;

  /**
   * The Status analyzer that periodically verifies whether the connected DSs
   * are late. Using an AtomicReference to avoid leaking references to costly
   * threads.
   */
  private AtomicReference<StatusAnalyzer> statusAnalyzer =
      new AtomicReference<StatusAnalyzer>();

  /**
   * The monitoring publisher that periodically sends monitoring messages to the
   * topology. Using an AtomicReference to avoid leaking references to costly
   * threads.
   */
  private AtomicReference<MonitoringPublisher> monitoringPublisher =
      new AtomicReference<MonitoringPublisher>();
  /**
   * Maintains monitor data for the current domain.
   */
  private ReplicationDomainMonitor domainMonitor =
      new ReplicationDomainMonitor(this);

  /**
   * The following map contains one balanced tree for each replica ID to which
   * we are currently publishing the first update in the balanced tree is the
   * next change that we must push to this particular server.
   */
  private final Map<Integer, DataServerHandler> connectedDSs =
    new ConcurrentHashMap<Integer, DataServerHandler>();

  /**
   * This map contains one ServerHandler for each replication servers with which
   * we are connected (so normally all the replication servers) the first update
   * in the balanced tree is the next change that we must push to this
   * particular server.
   */
  private final Map<Integer, ReplicationServerHandler> connectedRSs =
    new ConcurrentHashMap<Integer, ReplicationServerHandler>();

  private final Queue<MessageHandler> otherHandlers =
    new ConcurrentLinkedQueue<MessageHandler>();

  /**
   * This map contains the List of updates received from each LDAP server.
   */
  private final Map<Integer, DbHandler> sourceDbHandlers =
      new ConcurrentHashMap<Integer, DbHandler>();
  /** The ReplicationServer that created the current instance. */
  private ReplicationServer localReplicationServer;

  /** GenerationId management. */
  private volatile long generationId = -1;
  private boolean generationIdSavedStatus = false;

  /** The tracer object for the debug logger. */
  private static final DebugTracer TRACER = getTracer();

  /**
   * The needed info for each received assured update message we are waiting
   * acks for.
   * <p>
   * Key: a CSN matching a received update message which requested
   * assured mode usage (either safe read or safe data mode)
   * <p>
   * Value: The object holding every info needed about the already received acks
   * as well as the acks to be received.
   *
   * @see ExpectedAcksInfo For more details, see ExpectedAcksInfo and its sub
   *      classes javadoc.
   */
  private final Map<CSN, ExpectedAcksInfo> waitingAcks =
    new ConcurrentHashMap<CSN, ExpectedAcksInfo>();

  /**
   * The timer used to run the timeout code (timer tasks) for the assured update
   * messages we are waiting acks for.
   */
  private Timer assuredTimeoutTimer;
  /**
   * Counter used to purge the timer tasks references in assuredTimeoutTimer,
   * every n number of treated assured messages.
   */
  private int assuredTimeoutTimerPurgeCounter = 0;

  private ServerState ctHeartbeatState;

  /**
   * Creates a new ReplicationServerDomain associated to the DN baseDn.
   *
   * @param baseDn
   *          The baseDn associated to the ReplicationServerDomain.
   * @param localReplicationServer
   *          the ReplicationServer that created this instance.
   */
  public ReplicationServerDomain(String baseDn,
      ReplicationServer localReplicationServer)
  {
    this.baseDn = baseDn;
    this.localReplicationServer = localReplicationServer;
    this.assuredTimeoutTimer = new Timer("Replication server RS("
        + localReplicationServer.getServerId()
        + ") assured timer for domain \"" + baseDn + "\"", true);

    DirectoryServer.registerMonitorProvider(this);
  }

  /**
   * Add an update that has been received to the list of
   * updates that must be forwarded to all other servers.
   *
   * @param update  The update that has been received.
   * @param sourceHandler The ServerHandler for the server from which the
   *        update was received
   * @throws IOException When an IO exception happens during the update
   *         processing.
   */
  public void put(UpdateMsg update, ServerHandler sourceHandler)
    throws IOException
  {
    CSN csn = update.getCSN();
    int serverId = csn.getServerId();

    sourceHandler.updateServerState(update);
    sourceHandler.incrementInCount();
    setGenerationIdIfUnset(sourceHandler.getGenerationId());

    /**
     * If this is an assured message (a message requesting ack), we must
     * construct the ExpectedAcksInfo object with the right number of expected
     * acks before posting message to the writers. Otherwise some writers may
     * have time to post, receive the ack and increment received ack counter
     * (kept in ExpectedAcksInfo object) and we could think the acknowledgment
     * is fully processed although it may be not (some other acks from other
     * servers are not yet arrived). So for that purpose we do a pre-loop
     * to determine to who we will post an assured message.
     * Whether the assured mode is safe read or safe data, we anyway do not
     * support the assured replication feature across topologies with different
     * group ids. The assured feature insures assured replication based on the
     * same locality (group id). For instance in double data center deployment
     * (2 group id usage) with assured replication enabled, an assured message
     * sent from data center 1 (group id = 1) will be sent to servers of both
     * data centers, but one will request and wait acks only from servers of the
     * data center 1.
     */
    boolean assuredMessage = update.isAssured();
    PreparedAssuredInfo preparedAssuredInfo = null;
    if (assuredMessage)
    {
      // Assured feature is supported starting from replication protocol V2
      if (sourceHandler.getProtocolVersion() >= REPLICATION_PROTOCOL_V2)
      {
        // According to assured sub-mode, prepare structures to keep track of
        // the acks we are interested in.
        AssuredMode assuredMode = update.getAssuredMode();
        if (assuredMode == AssuredMode.SAFE_DATA_MODE)
        {
          sourceHandler.incrementAssuredSdReceivedUpdates();
          preparedAssuredInfo = processSafeDataUpdateMsg(update, sourceHandler);
        } else if (assuredMode == AssuredMode.SAFE_READ_MODE)
        {
          sourceHandler.incrementAssuredSrReceivedUpdates();
          preparedAssuredInfo = processSafeReadUpdateMsg(update, sourceHandler);
        } else
        {
          // Unknown assured mode: should never happen
          Message errorMsg = ERR_RS_UNKNOWN_ASSURED_MODE.get(
            Integer.toString(localReplicationServer.getServerId()),
            assuredMode.toString(), baseDn, update.toString());
          logError(errorMsg);
          assuredMessage = false;
        }
      } else
      {
        assuredMessage = false;
      }
    }

    if (!publishMessage(update, serverId))
    {
      return;
    }

    List<Integer> expectedServers = null;
    if (assuredMessage)
    {
      expectedServers = preparedAssuredInfo.expectedServers;
      if (expectedServers != null)
      {
        // Store the expected acks info into the global map.
        // The code for processing reception of acks for this update will update
        // info kept in this object and if enough acks received, it will send
        // back the final ack to the requester and remove the object from this
        // map
        // OR
        // The following timer will time out and send an timeout ack to the
        // requester if the acks are not received in time. The timer will also
        // remove the object from this map.
        waitingAcks.put(csn, preparedAssuredInfo.expectedAcksInfo);

        // Arm timer for this assured update message (wait for acks until it
        // times out)
        AssuredTimeoutTask assuredTimeoutTask = new AssuredTimeoutTask(csn);
        assuredTimeoutTimer.schedule(assuredTimeoutTask,
            localReplicationServer.getAssuredTimeout());
        // Purge timer every 100 treated messages
        assuredTimeoutTimerPurgeCounter++;
        if ((assuredTimeoutTimerPurgeCounter % 100) == 0)
        {
          assuredTimeoutTimer.purge();
        }
      }
    }

    if (expectedServers == null)
    {
      expectedServers = Collections.emptyList();
    }

    /**
     * The update message equivalent to the originally received update message,
     * but with assured flag disabled. This message is the one that should be
     * sent to non eligible servers for assured mode.
     * We need a clone like of the original message with assured flag off, to be
     * posted to servers we don't want to wait the ack from (not normal status
     * servers or servers with different group id). This must be done because
     * the posted message is a reference so each writer queue gets the same
     * reference, thus, changing the assured flag of an object is done for every
     * references posted on every writer queues. That is why we need a message
     * version with assured flag on and another one with assured flag off.
     */
    NotAssuredUpdateMsg notAssuredUpdate = null;

    // Push the message to the replication servers
    if (sourceHandler.isDataServer())
    {
      for (ReplicationServerHandler rsHandler : connectedRSs.values())
      {
        /**
         * Ignore updates to RS with bad gen id
         * (no system managed status for a RS)
         */
        if (isDifferentGenerationId(rsHandler.getGenerationId()))
        {
          if (debugEnabled())
          {
            debug("update " + update.getCSN()
                + " will not be sent to replication server "
                + rsHandler.getServerId() + " with generation id "
                + rsHandler.getGenerationId() + " different from local "
                + "generation id " + generationId);
          }

          continue;
        }

        notAssuredUpdate = addUpdate(rsHandler, update, notAssuredUpdate,
            assuredMessage, expectedServers);
      }
    }

    // Push the message to the LDAP servers
    for (DataServerHandler dsHandler : connectedDSs.values())
    {
      // Don't forward the change to the server that just sent it
      if (dsHandler == sourceHandler)
      {
        continue;
      }

      /**
       * Ignore updates to DS in bad BAD_GENID_STATUS or FULL_UPDATE_STATUS
       *
       * The RSD lock should not be taken here as it is acceptable to have a
       * delay between the time the server has a wrong status and the fact we
       * detect it: the updates that succeed to pass during this time will have
       * no impact on remote server. But it is interesting to not saturate
       * uselessly the network if the updates are not necessary so this check to
       * stop sending updates is interesting anyway. Not taking the RSD lock
       * allows to have better performances in normal mode (most of the time).
       */
      ServerStatus dsStatus = dsHandler.getStatus();
      if (dsStatus == ServerStatus.BAD_GEN_ID_STATUS
          || dsStatus == ServerStatus.FULL_UPDATE_STATUS)
      {
        if (debugEnabled())
        {
          if (dsStatus == ServerStatus.BAD_GEN_ID_STATUS)
          {
            debug("update " + update.getCSN()
                + " will not be sent to directory server "
                + dsHandler.getServerId() + " with generation id "
                + dsHandler.getGenerationId() + " different from local "
                + "generation id " + generationId);
          }
          if (dsStatus == ServerStatus.FULL_UPDATE_STATUS)
          {
            debug("update " + update.getCSN()
                + " will not be sent to directory server "
                + dsHandler.getServerId() + " as it is in full update");
          }
        }

        continue;
      }

      notAssuredUpdate = addUpdate(dsHandler, update, notAssuredUpdate,
          assuredMessage, expectedServers);
    }

    // Push the message to the other subscribing handlers
    for (MessageHandler mHandler : otherHandlers) {
      mHandler.add(update);
    }
  }

  private boolean publishMessage(UpdateMsg update, int serverId)
  {
    // look for the dbHandler that is responsible for the LDAP server which
    // generated the change.
    DbHandler dbHandler;
    synchronized (sourceDbHandlers)
    {
      dbHandler = sourceDbHandlers.get(serverId);
      if (dbHandler == null)
      {
        try
        {
          dbHandler = localReplicationServer.newDbHandler(serverId, baseDn);
          generationIdSavedStatus = true;
        } catch (ChangelogException e)
        {
          /*
           * Because of database problem we can't save any more changes
           * from at least one LDAP server.
           * This replicationServer therefore can't do it's job properly anymore
           * and needs to close all its connections and shutdown itself.
           */
          MessageBuilder mb = new MessageBuilder();
          mb.append(ERR_CHANGELOG_SHUTDOWN_DATABASE_ERROR.get());
          mb.append(" ");
          mb.append(stackTraceToSingleLineString(e));
          logError(mb.toMessage());
          localReplicationServer.shutdown();
          return false;
        }
        sourceDbHandlers.put(serverId, dbHandler);
      }
    }

    // Publish the messages to the source handler
    dbHandler.add(update);
    return true;
  }

  private NotAssuredUpdateMsg addUpdate(ServerHandler sHandler,
      UpdateMsg update, NotAssuredUpdateMsg notAssuredUpdate,
      boolean assuredMessage, List<Integer> expectedServers)
      throws UnsupportedEncodingException
  {
    if (assuredMessage)
    {
      // Assured mode: post an assured or not assured matching update
      // message according to what has been computed for the destination server
      if (expectedServers.contains(sHandler.getServerId()))
      {
        sHandler.add(update);
      }
      else
      {
        if (notAssuredUpdate == null)
        {
          notAssuredUpdate = new NotAssuredUpdateMsg(update);
        }
        sHandler.add(notAssuredUpdate);
      }
    }
    else
    {
      sHandler.add(update);
    }
    return notAssuredUpdate;
  }

  /**
   * Helper class to be the return type of a method that processes a just
   * received assured update message:
   * - processSafeReadUpdateMsg
   * - processSafeDataUpdateMsg
   * This is a facility to pack many interesting returned object.
   */
  private class PreparedAssuredInfo
  {
      /**
       * The list of servers identified as servers we are interested in
       * receiving acks from. If this list is not null, then expectedAcksInfo
       * should be not null.
       * Servers that are not in this list are servers not eligible for an ack
       * request.
       */
      public List<Integer> expectedServers;

      /**
       * The constructed ExpectedAcksInfo object to be used when acks will be
       * received. Null if expectedServers is null.
       */
      public ExpectedAcksInfo expectedAcksInfo;
  }

  /**
   * Process a just received assured update message in Safe Read mode. If the
   * ack can be sent immediately, it is done here. This will also determine to
   * which suitable servers an ack should be requested from, and which ones are
   * not eligible for an ack request.
   * This method is an helper method for the put method. Have a look at the put
   * method for a better understanding.
   * @param update The just received assured update to process.
   * @param sourceHandler The ServerHandler for the server from which the
   *        update was received
   * @return A suitable PreparedAssuredInfo object that contains every needed
   * info to proceed with post to server writers.
   * @throws IOException When an IO exception happens during the update
   *         processing.
   */
  private PreparedAssuredInfo processSafeReadUpdateMsg(
    UpdateMsg update, ServerHandler sourceHandler) throws IOException
  {
    CSN csn = update.getCSN();
    byte groupId = localReplicationServer.getGroupId();
    byte sourceGroupId = sourceHandler.getGroupId();
    List<Integer> expectedServers = new ArrayList<Integer>();
    List<Integer> wrongStatusServers = new ArrayList<Integer>();

    if (sourceGroupId == groupId)
      // Assured feature does not cross different group ids
    {
      if (sourceHandler.isDataServer())
      {
        collectRSsEligibleForAssuredReplication(groupId, expectedServers);
      }

      // Look for DS eligible for assured
      for (DataServerHandler dsHandler : connectedDSs.values())
      {
        // Don't forward the change to the server that just sent it
        if (dsHandler == sourceHandler)
        {
          continue;
        }
        if (dsHandler.getGroupId() == groupId)
          // No ack expected from a DS with different group id
        {
          ServerStatus serverStatus = dsHandler.getStatus();
          if (serverStatus == ServerStatus.NORMAL_STATUS)
          {
            expectedServers.add(dsHandler.getServerId());
          } else if (serverStatus == ServerStatus.DEGRADED_STATUS) {
            // No ack expected from a DS with wrong status
            wrongStatusServers.add(dsHandler.getServerId());
          }
          /*
           * else
           * BAD_GEN_ID_STATUS or FULL_UPDATE_STATUS:
           * We do not want this to be reported as an error to the update
           * maker -> no pollution or potential misunderstanding when
           * reading logs or monitoring and it was just administration (for
           * instance new server is being configured in topo: it goes in bad
           * gen then full update).
           */
        }
      }
    }

    // Return computed structures
    PreparedAssuredInfo preparedAssuredInfo = new PreparedAssuredInfo();
    if (expectedServers.size() > 0)
    {
      // Some other acks to wait for
      preparedAssuredInfo.expectedAcksInfo = new SafeReadExpectedAcksInfo(csn,
        sourceHandler, expectedServers, wrongStatusServers);
      preparedAssuredInfo.expectedServers = expectedServers;
    }

    if (preparedAssuredInfo.expectedServers == null)
    {
      // No eligible servers found, send the ack immediately
      sourceHandler.send(new AckMsg(csn));
    }

    return preparedAssuredInfo;
  }

  /**
   * Process a just received assured update message in Safe Data mode. If the
   * ack can be sent immediately, it is done here. This will also determine to
   * which suitable servers an ack should be requested from, and which ones are
   * not eligible for an ack request.
   * This method is an helper method for the put method. Have a look at the put
   * method for a better understanding.
   * @param update The just received assured update to process.
   * @param sourceHandler The ServerHandler for the server from which the
   *        update was received
   * @return A suitable PreparedAssuredInfo object that contains every needed
   * info to proceed with post to server writers.
   * @throws IOException When an IO exception happens during the update
   *         processing.
   */
  private PreparedAssuredInfo processSafeDataUpdateMsg(
    UpdateMsg update, ServerHandler sourceHandler) throws IOException
  {
    CSN csn = update.getCSN();
    boolean interestedInAcks = false;
    byte safeDataLevel = update.getSafeDataLevel();
    byte groupId = localReplicationServer.getGroupId();
    byte sourceGroupId = sourceHandler.getGroupId();
    if (safeDataLevel < (byte) 1)
    {
      // Should never happen
      Message errorMsg = ERR_UNKNOWN_ASSURED_SAFE_DATA_LEVEL.get(
        Integer.toString(localReplicationServer.getServerId()),
        Byte.toString(safeDataLevel), baseDn, update.toString());
      logError(errorMsg);
    } else if (sourceGroupId == groupId
    // Assured feature does not cross different group IDS
        && isSameGenerationId(sourceHandler.getGenerationId()))
    // Ignore assured updates from wrong generationId servers
    {
        if (sourceHandler.isDataServer())
        {
          if (safeDataLevel == (byte) 1)
          {
            /**
             * Immediately return the ack for an assured message in safe data
             * mode with safe data level 1, coming from a DS. No need to wait
             * for more acks
             */
            sourceHandler.send(new AckMsg(csn));
          } else
          {
            /**
             * level > 1 : We need further acks
             * The message will be posted in assured mode to eligible
             * servers. The embedded safe data level is not changed, and his
             * value will be used by a remote RS to determine if he must send
             * an ack (level > 1) or not (level = 1)
             */
            interestedInAcks = true;
          }
        } else
        { // A RS sent us the safe data message, for sure no further ack to wait
          /**
           * Level 1 has already been reached so no further acks to wait.
           * Just deal with level > 1
           */
          if (safeDataLevel > (byte) 1)
          {
            sourceHandler.send(new AckMsg(csn));
          }
        }
    }

    List<Integer> expectedServers = new ArrayList<Integer>();
    if (interestedInAcks && sourceHandler.isDataServer())
    {
      collectRSsEligibleForAssuredReplication(groupId, expectedServers);
    }

    // Return computed structures
    PreparedAssuredInfo preparedAssuredInfo = new PreparedAssuredInfo();
    int nExpectedServers = expectedServers.size();
    if (interestedInAcks) // interestedInAcks so level > 1
    {
      if (nExpectedServers > 0)
      {
        // Some other acks to wait for
        int sdl = update.getSafeDataLevel();
        int neededAdditionalServers = sdl - 1;
        // Change the number of expected acks if not enough available eligible
        // servers: the level is a best effort thing, we do not want to timeout
        // at every assured SD update for instance if a RS has had his gen id
        // reseted
        byte finalSdl = (nExpectedServers >= neededAdditionalServers) ?
          (byte)sdl : // Keep level as it was
          (byte)(nExpectedServers+1); // Change level to match what's available
        preparedAssuredInfo.expectedAcksInfo = new SafeDataExpectedAcksInfo(csn,
          sourceHandler, finalSdl, expectedServers);
        preparedAssuredInfo.expectedServers = expectedServers;
      } else
      {
        // level > 1 and source is a DS but no eligible servers found, send the
        // ack immediately
        sourceHandler.send(new AckMsg(csn));
      }
    }

    return preparedAssuredInfo;
  }

  private void collectRSsEligibleForAssuredReplication(byte groupId,
      List<Integer> expectedServers)
  {
    for (ReplicationServerHandler rsHandler : connectedRSs.values())
    {
      if (rsHandler.getGroupId() == groupId
      // No ack expected from a RS with different group id
            && isSameGenerationId(rsHandler.getGenerationId())
        // No ack expected from a RS with bad gen id
        )
      {
        expectedServers.add(rsHandler.getServerId());
      }
    }
  }

  private boolean isSameGenerationId(long generationId)
  {
    return this.generationId > 0 && this.generationId == generationId;
  }

  private boolean isDifferentGenerationId(long generationId)
  {
    return this.generationId > 0 && this.generationId != generationId;
  }

  /**
   * Process an ack received from a given server.
   *
   * @param ack The ack message received.
   * @param ackingServer The server handler of the server that sent the ack.
   */
  public void processAck(AckMsg ack, ServerHandler ackingServer)
  {
    // Retrieve the expected acks info for the update matching the original
    // sent update.
    CSN csn = ack.getCSN();
    ExpectedAcksInfo expectedAcksInfo = waitingAcks.get(csn);

    if (expectedAcksInfo != null)
    {
      // Prevent concurrent access from processAck() or AssuredTimeoutTask.run()
      synchronized (expectedAcksInfo)
      {
        if (expectedAcksInfo.isCompleted())
        {
          // Timeout code is sending a timeout ack, do nothing and let him
          // remove object from the map
          return;
        }
        /**
         *
         * If this is the last ack we were waiting from, immediately create and
         * send the final ack to the original server
         */
        if (expectedAcksInfo.processReceivedAck(ackingServer, ack))
        {
          // Remove the object from the map as no more needed
          waitingAcks.remove(csn);
          AckMsg finalAck = expectedAcksInfo.createAck(false);
          ServerHandler origServer = expectedAcksInfo.getRequesterServer();
          try
          {
            origServer.send(finalAck);
          } catch (IOException e)
          {
            /**
             * An error happened trying the send back an ack to the server.
             * Log an error and close the connection to this server.
             */
            MessageBuilder mb = new MessageBuilder();
            mb.append(ERR_RS_ERROR_SENDING_ACK.get(
              Integer.toString(localReplicationServer.getServerId()),
              Integer.toString(origServer.getServerId()),
              csn.toString(), baseDn));
            mb.append(" ");
            mb.append(stackTraceToSingleLineString(e));
            logError(mb.toMessage());
            stopServer(origServer, false);
          }
          // Mark the ack info object as completed to prevent potential timeout
          // code parallel run
          expectedAcksInfo.completed();
        }
      }
    }
    /* Else the timeout occurred for the update matching this CSN
     * and the ack with timeout error has probably already been sent.
     */
  }

  /**
   * The code run when the timeout occurs while waiting for acks of the
   * eligible servers. This basically sends a timeout ack (with any additional
   * error info) to the original server that sent an assured update message.
   */
  private class AssuredTimeoutTask extends TimerTask
  {
    private CSN csn;

    /**
     * Constructor for the timer task.
     * @param csn The CSN of the assured update we are waiting acks for
     */
    public AssuredTimeoutTask(CSN csn)
    {
      this.csn = csn;
    }

    /**
     * Run when the assured timeout for an assured update message we are waiting
     * acks for occurs.
     */
    @Override
    public void run()
    {
      ExpectedAcksInfo expectedAcksInfo = waitingAcks.get(csn);

      if (expectedAcksInfo != null)
      {
        synchronized (expectedAcksInfo)
        {
          if (expectedAcksInfo.isCompleted())
          {
            // processAck() code is sending the ack, do nothing and let him
            // remove object from the map
            return;
          }
          // Remove the object from the map as no more needed
          waitingAcks.remove(csn);
          // Create the timeout ack and send him to the server the assured
          // update message came from
          AckMsg finalAck = expectedAcksInfo.createAck(true);
          ServerHandler origServer = expectedAcksInfo.getRequesterServer();
          if (debugEnabled())
          {
            debug("sending timeout for assured update with CSN " + csn
                + " to serverId=" + origServer.getServerId());
          }
          try
          {
            origServer.send(finalAck);
          } catch (IOException e)
          {
            /**
             * An error happened trying the send back an ack to the server.
             * Log an error and close the connection to this server.
             */
            MessageBuilder mb = new MessageBuilder();
            mb.append(ERR_RS_ERROR_SENDING_ACK.get(
                Integer.toString(localReplicationServer.getServerId()),
                Integer.toString(origServer.getServerId()),
                csn.toString(), baseDn));
            mb.append(" ");
            mb.append(stackTraceToSingleLineString(e));
            logError(mb.toMessage());
            stopServer(origServer, false);
          }
          // Increment assured counters
          boolean safeRead =
              expectedAcksInfo instanceof SafeReadExpectedAcksInfo;
          if (safeRead)
          {
            origServer.incrementAssuredSrReceivedUpdatesTimeout();
          } else
          {
            if (origServer.isDataServer())
            {
              origServer.incrementAssuredSdReceivedUpdatesTimeout();
            }
          }
          //   retrieve expected servers in timeout to increment their counter
          List<Integer> serversInTimeout = expectedAcksInfo.getTimeoutServers();
          for (Integer serverId : serversInTimeout)
          {
            ServerHandler expectedDSInTimeout = connectedDSs.get(serverId);
            ServerHandler expectedRSInTimeout = connectedRSs.get(serverId);
            if (expectedDSInTimeout != null)
            {
              if (safeRead)
              {
                expectedDSInTimeout.incrementAssuredSrSentUpdatesTimeout();
              } // else no SD update sent to a DS (meaningless)
            } else if (expectedRSInTimeout != null)
            {
              if (safeRead)
              {
                expectedRSInTimeout.incrementAssuredSrSentUpdatesTimeout();
              }
              else
              {
                expectedRSInTimeout.incrementAssuredSdSentUpdatesTimeout();
              }
            }
            // else server disappeared ? Let's forget about it.
          }
          // Mark the ack info object as completed to prevent potential
          // processAck() code parallel run
          expectedAcksInfo.completed();
        }
      }
    }
  }


  /**
   * Stop operations with a list of replication servers.
   *
   * @param replServerURLs
   *          the replication servers URLs for which we want to stop operations
   */
  public void stopReplicationServers(Collection<String> replServerURLs)
  {
    for (ReplicationServerHandler rsHandler : connectedRSs.values())
    {
      if (replServerURLs.contains(rsHandler.getServerAddressURL()))
      {
        stopServer(rsHandler, false);
      }
    }
  }

  /**
   * Stop operations with all servers this domain is connected with (RS and DS).
   *
   * @param shutdown A boolean indicating if the stop is due to a
   *                 shutdown condition.
   */
  public void stopAllServers(boolean shutdown)
  {
    for (ReplicationServerHandler rsHandler : connectedRSs.values())
    {
      stopServer(rsHandler, shutdown);
    }

    for (DataServerHandler dsHandler : connectedDSs.values())
    {
      stopServer(dsHandler, shutdown);
    }
  }

  /**
   * Checks whether it is already connected to a DS with same id.
   *
   * @param dsHandler
   *          the DS we want to check
   * @return true if this DS is already connected to the current server
   */
  public boolean isAlreadyConnectedToDS(DataServerHandler dsHandler)
  {
    if (connectedDSs.containsKey(dsHandler.getServerId()))
    {
      // looks like two connected LDAP servers have the same serverId
      Message message = ERR_DUPLICATE_SERVER_ID.get(
          localReplicationServer.getMonitorInstanceName(),
          connectedDSs.get(dsHandler.getServerId()).toString(),
          dsHandler.toString(), dsHandler.getServerId());
      logError(message);
      return true;
    }
    return false;
  }

  /**
   * Stop operations with a given server.
   *
   * @param sHandler the server for which we want to stop operations.
   * @param shutdown A boolean indicating if the stop is due to a
   *                 shutdown condition.
   */
  public void stopServer(ServerHandler sHandler, boolean shutdown)
  {
    // TODO JNR merge with stopServer(MessageHandler)
    if (debugEnabled())
    {
      debug("stopServer() on the server handler " + sHandler);
    }
    /*
     * We must prevent deadlock on replication server domain lock, when for
     * instance this code is called from dying ServerReader but also dying
     * ServerWriter at the same time, or from a thread that wants to shut down
     * the handler. So use a thread safe flag to know if the job must be done
     * or not (is already being processed or not).
     */
    if (!sHandler.engageShutdown())
      // Only do this once (prevent other thread to enter here again)
    {
      if (!shutdown)
      {
        try
        {
          // Acquire lock on domain (see more details in comment of start()
          // method of ServerHandler)
          lock();
        }
        catch (InterruptedException ex)
        {
          // We can't deal with this here, so re-interrupt thread so that it is
          // caught during subsequent IO.
          Thread.currentThread().interrupt();
          return;
        }
      }

      try
      {
        // Stop useless monitoring publisher if no more RS or DS in domain
        if ( (connectedDSs.size() + connectedRSs.size() )== 1)
        {
          if (debugEnabled())
          {
            debug("remote server " + sHandler
                + " is the last RS/DS to be stopped:"
                + " stopping monitoring publisher");
          }
          stopMonitoringPublisher();
        }

        if (connectedRSs.containsKey(sHandler.getServerId()))
        {
          unregisterServerHandler(sHandler, shutdown, false);
        } else if (connectedDSs.containsKey(sHandler.getServerId()))
        {
          // If this is the last DS for the domain,
          // shutdown the status analyzer
          if (connectedDSs.size() == 1)
          {
            if (debugEnabled())
            {
              debug("remote server " + sHandler
                  + " is the last DS to be stopped: stopping status analyzer");
            }
            stopStatusAnalyzer();
          }
          unregisterServerHandler(sHandler, shutdown, true);
        } else if (otherHandlers.contains(sHandler))
        {
          unregisterOtherHandler(sHandler);
        }
      }
      catch(Exception e)
      {
        logError(Message.raw(Category.SYNC, Severity.NOTICE,
            stackTraceToSingleLineString(e)));
      }
      finally
      {
        if (!shutdown)
        {
          release();
        }
      }
    }
  }

  private void unregisterOtherHandler(MessageHandler mHandler)
  {
    unRegisterHandler(mHandler);
    mHandler.shutdown();
  }

  private void unregisterServerHandler(ServerHandler sHandler, boolean shutdown,
      boolean isDirectoryServer)
  {
    unregisterServerHandler(sHandler);
    sHandler.shutdown();

    resetGenerationIdIfPossible();
    if (!shutdown)
    {
      if (isDirectoryServer)
      {
        // Update the remote replication servers with our list
        // of connected LDAP servers
        sendTopoInfoToAllRSs();
      }
      // Warn our DSs that a RS or DS has quit (does not use this
      // handler as already removed from list)
      sendTopoInfoToAllDSsExcept(null);
    }
  }

  /**
   * Stop the handler.
   * @param mHandler The handler to stop.
   */
  public void stopServer(MessageHandler mHandler)
  {
    // TODO JNR merge with stopServer(ServerHandler, boolean)
    if (debugEnabled())
    {
      debug("stopServer() on the message handler " + mHandler);
    }
    /*
     * We must prevent deadlock on replication server domain lock, when for
     * instance this code is called from dying ServerReader but also dying
     * ServerWriter at the same time, or from a thread that wants to shut down
     * the handler. So use a thread safe flag to know if the job must be done
     * or not (is already being processed or not).
     */
    if (!mHandler.engageShutdown())
      // Only do this once (prevent other thread to enter here again)
    {
      try
      {
        // Acquire lock on domain (see more details in comment of start() method
        // of ServerHandler)
        lock();
      }
      catch (InterruptedException ex)
      {
        // We can't deal with this here, so re-interrupt thread so that it is
        // caught during subsequent IO.
        Thread.currentThread().interrupt();
        return;
      }

      try
      {
        if (otherHandlers.contains(mHandler))
        {
          unregisterOtherHandler(mHandler);
        }
      }
      catch(Exception e)
      {
        logError(Message.raw(Category.SYNC, Severity.NOTICE,
            stackTraceToSingleLineString(e)));
      }
      finally
      {
        release();
      }
    }
  }

  /**
   * Unregister this handler from the list of handlers registered to this
   * domain.
   * @param sHandler the provided handler to unregister.
   */
  private void unregisterServerHandler(ServerHandler sHandler)
  {
    if (sHandler.isReplicationServer())
    {
      connectedRSs.remove(sHandler.getServerId());
    }
    else
    {
      connectedDSs.remove(sHandler.getServerId());
    }
  }

  /**
   * This method resets the generationId for this domain if there is no LDAP
   * server currently connected in the whole topology on this domain and if the
   * generationId has never been saved.
   * <ul>
   * <li>test emptiness of {@link #connectedDSs} list</li>
   * <li>traverse {@link #connectedRSs} list and test for each if DS are
   * connected</li>
   * </ul>
   * So it strongly relies on the {@link #connectedDSs} list
   */
  private void resetGenerationIdIfPossible()
  {
    if (debugEnabled())
    {
      debug("mayResetGenerationId generationIdSavedStatus="
          + generationIdSavedStatus);
    }

    // If there is no more any LDAP server connected to this domain in the
    // topology and the generationId has never been saved, then we can reset
    // it and the next LDAP server to connect will become the new reference.
    boolean ldapServersConnectedInTheTopology = false;
    if (connectedDSs.isEmpty())
    {
      for (ReplicationServerHandler rsHandler : connectedRSs.values())
      {
        if (generationId != rsHandler.getGenerationId())
        {
          if (debugEnabled())
          {
            debug("mayResetGenerationId skip RS " + rsHandler
                + " that has different genId");
          }
        }
        else if (rsHandler.hasRemoteLDAPServers())
        {
          ldapServersConnectedInTheTopology = true;

          if (debugEnabled())
          {
            debug("mayResetGenerationId RS " + rsHandler
                + " has ldap servers connected to it"
                + " - will not reset generationId");
          }
          break;
        }
      }
    }
    else
    {
      ldapServersConnectedInTheTopology = true;

      if (debugEnabled())
      {
        debug("has ldap servers connected to it - will not reset generationId");
      }
    }

    if (!ldapServersConnectedInTheTopology
        && !generationIdSavedStatus
        && generationId != -1)
    {
      changeGenerationId(-1, false);
    }
  }

  /**
   * Checks whether a remote RS is already connected to this hosting RS.
   *
   * @param rsHandler
   *          The handler for the remote RS.
   * @return flag specifying whether the remote RS is already connected.
   * @throws DirectoryException
   *           when a problem occurs.
   */
  public boolean isAlreadyConnectedToRS(ReplicationServerHandler rsHandler)
      throws DirectoryException
  {
    ReplicationServerHandler oldRsHandler =
        connectedRSs.get(rsHandler.getServerId());
    if (oldRsHandler == null)
    {
      return false;
    }

    if (oldRsHandler.getServerAddressURL().equals(
        rsHandler.getServerAddressURL()))
    {
      // this is the same server, this means that our ServerStart messages
      // have been sent at about the same time and 2 connections
      // have been established.
      // Silently drop this connection.
      return true;
    }

    // looks like two replication servers have the same serverId
    // log an error message and drop this connection.
    Message message = ERR_DUPLICATE_REPLICATION_SERVER_ID.get(
        localReplicationServer.getMonitorInstanceName(),
        oldRsHandler.getServerAddressURL(), rsHandler.getServerAddressURL(),
        rsHandler.getServerId());
    throw new DirectoryException(ResultCode.OTHER, message);
  }

  /**
   * Get the next update that need to be sent to a given LDAP server.
   * This call is blocking when no update is available or when dependencies
   * do not allow to send the next available change
   *
   * @param sHandler The server handler for the target directory server.
   *
   * @return the update that must be forwarded
   */
  public UpdateMsg take(ServerHandler sHandler)
  {
    /*
     * Get the balanced tree that we use to sort the changes to be
     * sent to the replica from the cookie
     *
     * The next change to send is always the first one in the tree
     * So this methods simply need to check that dependencies are OK
     * and update this replicaId RUV
     */
    return sHandler.take();
  }

  /**
   * Returns a set containing the serverIds that produced updates and known by
   * this replicationServer from all over the topology, whether directly
   * connected or connected to another RS.
   *
   * @return a set containing the serverIds known by this replicationServer.
   */
  public Set<Integer> getServerIds()
  {
    return sourceDbHandlers.keySet();
  }

  /**
   * Creates and returns a cursor. When the cursor is not used anymore, the
   * caller MUST call the {@link ReplicaDBCursor#close()} method to free the
   * resources and locks used by the cursor.
   *
   * @param serverId
   *          Identifier of the server for which the cursor is created.
   * @param startAfterCSN
   *          Starting point for the cursor.
   * @return the created {@link ReplicaDBCursor}. Null when no DB is available
   *         or the DB is empty for the provided serverId .
   */
  public ReplicaDBCursor getCursorFrom(int serverId, CSN startAfterCSN)
  {
    DbHandler dbHandler = sourceDbHandlers.get(serverId);
    if (dbHandler == null)
    {
      return null;
    }

    ReplicaDBCursor cursor;
    try
    {
      cursor = dbHandler.generateCursorFrom(startAfterCSN);
    }
    catch (Exception e)
    {
      return null;
    }

    if (!cursor.next())
    {
      close(cursor);
      return null;
    }

    return cursor;
  }

 /**
  * Count the number of changes in the replication changelog for the provided
  * serverID, between 2 provided CSNs.
  * @param serverId Identifier of the server for which to compute the count.
  * @param from lower limit CSN.
  * @param to   upper limit CSN.
  * @return the number of changes.
  */
  public long getCount(int serverId, CSN from, CSN to)
  {
    DbHandler dbHandler = sourceDbHandlers.get(serverId);
    if (dbHandler != null)
    {
      return dbHandler.getCount(from, to);
    }
    return 0;
  }

  /**
   * Returns the change count for that ReplicationServerDomain.
   *
   * @return the change count.
   */
  public long getChangesCount()
  {
    long entryCount = 0;
    for (DbHandler dbHandler : sourceDbHandlers.values())
    {
      entryCount += dbHandler.getChangesCount();
    }
    return entryCount;
  }

  /**
   * Get the baseDn.
   * @return Returns the baseDn.
   */
  public String getBaseDn()
  {
    return baseDn;
  }

  /**
   * Sets the provided DbHandler associated to the provided serverId.
   *
   * @param serverId  the serverId for the server to which is
   *                  associated the DbHandler.
   * @param dbHandler the dbHandler associated to the serverId.
   *
   * @throws ChangelogException If a database error happened.
   */
  public void setDbHandler(int serverId, DbHandler dbHandler)
    throws ChangelogException
  {
    synchronized (sourceDbHandlers)
    {
      sourceDbHandlers.put(serverId, dbHandler);
    }
  }

  /**
   * Retrieves the destination handlers for a routable message.
   *
   * @param msg The message to route.
   * @param senderHandler The handler of the server that published this message.
   * @return The list of destination handlers.
   */
  private List<ServerHandler> getDestinationServers(RoutableMsg msg,
    ServerHandler senderHandler)
  {
    List<ServerHandler> servers = new ArrayList<ServerHandler>();

    if (msg.getDestination() == RoutableMsg.THE_CLOSEST_SERVER)
    {
      // TODO Import from the "closest server" to be implemented
    } else if (msg.getDestination() == RoutableMsg.ALL_SERVERS)
    {
      if (!senderHandler.isReplicationServer())
      {
        // Send to all replication servers with a least one remote
        // server connected
        for (ReplicationServerHandler rsh : connectedRSs.values())
        {
          if (rsh.hasRemoteLDAPServers())
          {
            servers.add(rsh);
          }
        }
      }

      // Sends to all connected LDAP servers
      for (DataServerHandler destinationHandler : connectedDSs.values())
      {
        // Don't loop on the sender
        if (destinationHandler == senderHandler)
        {
          continue;
        }
        servers.add(destinationHandler);
      }
    } else
    {
      // Destination is one server
      DataServerHandler destinationHandler =
        connectedDSs.get(msg.getDestination());
      if (destinationHandler != null)
      {
        servers.add(destinationHandler);
      } else
      {
        // the targeted server is NOT connected
        // Let's search for the replication server that MAY
        // have the targeted server connected.
        if (senderHandler.isDataServer())
        {
          for (ReplicationServerHandler rsHandler : connectedRSs.values())
          {
            // Send to all replication servers with a least one remote
            // server connected
            if (rsHandler.isRemoteLDAPServer(msg.getDestination()))
            {
              servers.add(rsHandler);
            }
          }
        }
      }
    }
    return servers;
  }

  /**
   * Processes a message coming from one server in the topology and potentially
   * forwards it to one or all other servers.
   *
   * @param msg
   *          The message received and to be processed.
   * @param msgEmitter
   *          The server handler of the server that emitted the message.
   */
  public void process(RoutableMsg msg, ServerHandler msgEmitter)
  {
    // Test the message for which a ReplicationServer is expected
    // to be the destination
    if (!(msg instanceof InitializeRequestMsg) &&
        !(msg instanceof InitializeTargetMsg) &&
        !(msg instanceof InitializeRcvAckMsg) &&
        !(msg instanceof EntryMsg) &&
        !(msg instanceof DoneMsg) &&
        (msg.getDestination() == this.localReplicationServer.getServerId()))
    {
      if (msg instanceof ErrorMsg)
      {
        ErrorMsg errorMsg = (ErrorMsg) msg;
        logError(ERR_ERROR_MSG_RECEIVED.get(errorMsg.getDetails()));
      } else if (msg instanceof MonitorRequestMsg)
      {
        replyWithTopologyMonitorMsg(msg, msgEmitter);
      } else if (msg instanceof MonitorMsg)
      {
        MonitorMsg monitorMsg = (MonitorMsg) msg;
        domainMonitor.receiveMonitorDataResponse(monitorMsg,
            msgEmitter.getServerId());
      } else
      {
        replyWithUnroutableMsgType(msgEmitter, msg);
      }
      return;
    }

    List<ServerHandler> servers = getDestinationServers(msg, msgEmitter);
    if (!servers.isEmpty())
    {
      forwardMsgToAllServers(msg, servers, msgEmitter);
    }
    else
    {
      replyWithUnreachablePeerMsg(msgEmitter, msg);
    }
  }

  private void replyWithTopologyMonitorMsg(RoutableMsg msg,
      ServerHandler msgEmitter)
  {
    /*
     * If the request comes from a Directory Server we need to build the full
     * list of all servers in the topology and send back a MonitorMsg with the
     * full list of all the servers in the topology.
     */
    if (msgEmitter.isDataServer())
    {
      // Monitoring information requested by a DS
      try
      {
        MonitorMsg monitorMsg = createGlobalTopologyMonitorMsg(
            msg.getDestination(), msg.getSenderID(),
            domainMonitor.getMonitorData());
        msgEmitter.send(monitorMsg);
      }
      catch (IOException e)
      {
        // the connection was closed.
      }
    }
    else
    {
      // Monitoring information requested by a RS
      MonitorMsg monitorMsg = createLocalTopologyMonitorMsg(
          msg.getDestination(), msg.getSenderID());

      if (monitorMsg != null)
      {
        try
        {
          msgEmitter.send(monitorMsg);
        }
        catch (IOException e)
        {
          // We log the error. The requestor will detect a timeout or
          // any other failure on the connection.
          logError(ERR_CHANGELOG_ERROR_SENDING_MSG.get(Integer.toString(msg
              .getDestination())));
        }
      }
    }
  }

  private void replyWithUnroutableMsgType(ServerHandler msgEmitter,
      RoutableMsg msg)
  {
    String msgClassname = msg.getClass().getCanonicalName();
    logError(NOTE_ERR_ROUTING_TO_SERVER.get(msgClassname));

    MessageBuilder mb = new MessageBuilder();
    mb.append(NOTE_ERR_ROUTING_TO_SERVER.get(msgClassname));
    mb.append("serverID:").append(msg.getDestination());
    ErrorMsg errMsg = new ErrorMsg(msg.getSenderID(), mb.toMessage());
    try
    {
      msgEmitter.send(errMsg);
    }
    catch (IOException ignored)
    {
      // an error happened on the sender session trying to recover
      // from an error on the receiver session.
      // Not much more we can do at this point.
    }
  }

  private void replyWithUnreachablePeerMsg(ServerHandler msgEmitter,
      RoutableMsg msg)
  {
    MessageBuilder mb = new MessageBuilder();
    mb.append(ERR_NO_REACHABLE_PEER_IN_THE_DOMAIN.get(
        this.baseDn, Integer.toString(msg.getDestination())));
    mb.append(" In Replication Server=").append(
      this.localReplicationServer.getMonitorInstanceName());
    mb.append(" unroutable message =").append(msg.getClass().getSimpleName());
    mb.append(" Details:routing table is empty");
    final Message message = mb.toMessage();
    logError(message);

    ErrorMsg errMsg = new ErrorMsg(this.localReplicationServer.getServerId(),
        msg.getSenderID(), message);
    try
    {
      msgEmitter.send(errMsg);
    }
    catch (IOException ignored)
    {
      // TODO Handle error properly (sender timeout in addition)
      /*
       * An error happened trying to send an error msg to this server.
       * Log an error and close the connection to this server.
       */
      MessageBuilder mb2 = new MessageBuilder();
      mb2.append(ERR_CHANGELOG_ERROR_SENDING_ERROR.get(this.toString()));
      mb2.append(" ");
      mb2.append(stackTraceToSingleLineString(ignored));
      logError(mb2.toMessage());
      stopServer(msgEmitter, false);
    }
  }

  private void forwardMsgToAllServers(RoutableMsg msg,
      List<ServerHandler> servers, ServerHandler msgEmitter)
  {
    for (ServerHandler targetHandler : servers)
    {
      try
      {
        targetHandler.send(msg);
      } catch (IOException ioe)
      {
        /*
         * An error happened trying to send a routable message to its
         * destination server.
         * Send back an error to the originator of the message.
         */
        MessageBuilder mb = new MessageBuilder();
        mb.append(ERR_NO_REACHABLE_PEER_IN_THE_DOMAIN.get(
            this.baseDn, Integer.toString(msg.getDestination())));
        mb.append(" unroutable message =" + msg.getClass().getSimpleName());
        mb.append(" Details: " + ioe.getLocalizedMessage());
        final Message message = mb.toMessage();
        logError(message);

        ErrorMsg errMsg = new ErrorMsg(msg.getSenderID(), message);
        try
        {
          msgEmitter.send(errMsg);
        } catch (IOException ioe1)
        {
          // an error happened on the sender session trying to recover
          // from an error on the receiver session.
          // We don't have much solution left beside closing the sessions.
          stopServer(msgEmitter, false);
          stopServer(targetHandler, false);
        }
      // TODO Handle error properly (sender timeout in addition)
      }
    }
  }

  /**
   * Creates a new monitor message including monitoring information for the
   * whole topology.
   *
   * @param sender
   *          The sender of this message.
   * @param destination
   *          The destination of this message.
   * @return The newly created and filled MonitorMsg. Null if a problem occurred
   *         during message creation.
   * @throws InterruptedException
   *           if this thread is interrupted while waiting for a response
   */
  public MonitorMsg createGlobalTopologyMonitorMsg(int sender, int destination)
      throws InterruptedException
  {
    return createGlobalTopologyMonitorMsg(sender, destination,
        domainMonitor.recomputeMonitorData());
  }

  private MonitorMsg createGlobalTopologyMonitorMsg(int sender,
      int destination, ReplicationDomainMonitorData monitorData)
  {
    final MonitorMsg returnMsg = new MonitorMsg(sender, destination);
    returnMsg.setReplServerDbState(getDbServerState());

    // Add the server state for each DS and RS currently in the topology.
    for (int replicaId : toIterable(monitorData.ldapIterator()))
    {
      returnMsg.setServerState(replicaId,
          monitorData.getLDAPServerState(replicaId),
          monitorData.getApproxFirstMissingDate(replicaId), true);
    }

    for (int replicaId : toIterable(monitorData.rsIterator()))
    {
      returnMsg.setServerState(replicaId,
          monitorData.getRSStates(replicaId),
          monitorData.getRSApproxFirstMissingDate(replicaId), false);
    }

    return returnMsg;
  }



  /**
   * Creates a new monitor message including monitoring information for the
   * topology directly connected to this RS. This includes information for: -
   * local RS - all direct DSs - all direct RSs
   *
   * @param sender
   *          The sender of this message.
   * @param destination
   *          The destination of this message.
   * @return The newly created and filled MonitorMsg. Null if the current thread
   *         was interrupted while attempting to get the domain lock.
   */
  public MonitorMsg createLocalTopologyMonitorMsg(int sender, int destination)
  {
    try
    {
      // Lock domain as we need to go through connected servers list
      lock();
    }
    catch (InterruptedException e)
    {
      return null;
    }

    try
    {
      final MonitorMsg monitorMsg = new MonitorMsg(sender, destination);
      monitorMsg.setReplServerDbState(getDbServerState());

      // Add the server state for each connected DS and RS.
      for (DataServerHandler dsHandler : this.connectedDSs.values())
      {
        monitorMsg.setServerState(dsHandler.getServerId(), dsHandler
            .getServerState(), dsHandler.getApproxFirstMissingDate(), true);
      }

      for (ReplicationServerHandler rsHandler : this.connectedRSs.values())
      {
        monitorMsg.setServerState(rsHandler.getServerId(), rsHandler
            .getServerState(), rsHandler.getApproxFirstMissingDate(), false);
      }

      return monitorMsg;
    }
    finally
    {
      release();
    }
  }

  /**
   * Shutdown this ReplicationServerDomain.
   */
  public void shutdown()
  {
    DirectoryServer.deregisterMonitorProvider(this);

    // Terminate the assured timer
    assuredTimeoutTimer.cancel();

    stopAllServers(true);

    shutdownDbHandlers();
  }

  /** Shutdown all the dbHandlers. */
  private void shutdownDbHandlers()
  {
    synchronized (sourceDbHandlers)
    {
      for (DbHandler dbHandler : sourceDbHandlers.values())
      {
        dbHandler.shutdown();
      }
      sourceDbHandlers.clear();
    }
  }

  /**
   * Returns the ServerState describing the last change from this replica.
   *
   * @return The ServerState describing the last change from this replica.
   */
  public ServerState getDbServerState()
  {
    ServerState serverState = new ServerState();
    for (DbHandler db : sourceDbHandlers.values())
    {
      serverState.update(db.getLastChange());
    }
    return serverState;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    return "ReplicationServerDomain " + baseDn;
  }

  /**
   * Send a TopologyMsg to all the connected directory servers in order to let
   * them know the topology (every known DSs and RSs).
   *
   * @param notThisOne
   *          If not null, the topology message will not be sent to this DS.
   */
  private void sendTopoInfoToAllDSsExcept(DataServerHandler notThisOne)
  {
    for (DataServerHandler dsHandler : connectedDSs.values())
    {
      if (dsHandler != notThisOne)
      // All except the supplied one
      {
        for (int i=1; i<=2; i++)
        {
          if (!dsHandler.shuttingDown()
              && dsHandler.getStatus() != ServerStatus.NOT_CONNECTED_STATUS)
          {
            TopologyMsg topoMsg =
                createTopologyMsgForDS(dsHandler.getServerId());
            try
            {
              dsHandler.sendTopoInfo(topoMsg);
              break;
            }
            catch (IOException e)
            {
              if (i == 2)
              {
                Message message =
                    ERR_EXCEPTION_SENDING_TOPO_INFO
                        .get(baseDn, "directory", Integer.toString(dsHandler
                            .getServerId()), e.getMessage());
                logError(message);
              }
            }
          }
          sleep(100);
        }
      }
    }
  }

  /**
   * Send a TopologyMsg to all the connected replication servers
   * in order to let them know our connected LDAP servers.
   */
  private void sendTopoInfoToAllRSs()
  {
    TopologyMsg topoMsg = createTopologyMsgForRS();
    for (ReplicationServerHandler rsHandler : connectedRSs.values())
    {
      for (int i=1; i<=2; i++)
      {
        if (!rsHandler.shuttingDown()
            && rsHandler.getStatus() != ServerStatus.NOT_CONNECTED_STATUS)
        {
          try
          {
            rsHandler.sendTopoInfo(topoMsg);
            break;
          }
          catch (IOException e)
          {
            if (i == 2)
            {
              Message message = ERR_EXCEPTION_SENDING_TOPO_INFO.get(
                  baseDn, "replication",
                  Integer.toString(rsHandler.getServerId()), e.getMessage());
              logError(message);
            }
          }
        }
        sleep(100);
      }
    }
  }

  /**
   * Creates a TopologyMsg filled with information to be sent to a remote RS.
   * We send remote RS the info of every DS that are directly connected to us
   * plus our own info as RS.
   * @return A suitable TopologyMsg PDU to be sent to a peer RS
   */
  public TopologyMsg createTopologyMsgForRS()
  {
    List<DSInfo> dsInfos = new ArrayList<DSInfo>();
    for (DataServerHandler dsHandler : connectedDSs.values())
    {
      dsInfos.add(dsHandler.toDSInfo());
    }

    // Create info for the local RS
    List<RSInfo> rsInfos = new ArrayList<RSInfo>();
    rsInfos.add(toRSInfo(localReplicationServer, generationId));

    return new TopologyMsg(dsInfos, rsInfos);
  }

  /**
   * Creates a TopologyMsg filled with information to be sent to a DS.
   * We send remote DS the info of every known DS and RS in the topology (our
   * directly connected DSs plus the DSs connected to other RSs) except himself.
   * Also put info related to local RS.
   *
   * @param destDsId The id of the DS the TopologyMsg PDU is to be sent to and
   * that we must not include in the DS list.
   * @return A suitable TopologyMsg PDU to be sent to a peer DS
   */
  public TopologyMsg createTopologyMsgForDS(int destDsId)
  {
    // Go through every DSs (except recipient of msg)
    List<DSInfo> dsInfos = new ArrayList<DSInfo>();
    for (DataServerHandler dsHandler : connectedDSs.values())
    {
      if (dsHandler.getServerId() == destDsId)
      {
        continue;
      }
      dsInfos.add(dsHandler.toDSInfo());
    }


    List<RSInfo> rsInfos = new ArrayList<RSInfo>();
    // Add our own info (local RS)
    rsInfos.add(toRSInfo(localReplicationServer, generationId));

    // Go through every peer RSs (and get their connected DSs), also add info
    // for RSs
    for (ReplicationServerHandler rsHandler : connectedRSs.values())
    {
      rsInfos.add(rsHandler.toRSInfo());

      rsHandler.addDSInfos(dsInfos);
    }

    return new TopologyMsg(dsInfos, rsInfos);
  }

  private RSInfo toRSInfo(ReplicationServer rs, long generationId)
  {
    return new RSInfo(rs.getServerId(), rs.getServerURL(), generationId,
        rs.getGroupId(), rs.getWeight());
  }

  /**
   * Get the generationId associated to this domain.
   *
   * @return The generationId
   */
  public long getGenerationId()
  {
    return generationId;
  }

  /**
   * Get the generationId saved status.
   *
   * @return The generationId saved status.
   */
  public boolean getGenerationIdSavedStatus()
  {
    return generationIdSavedStatus;
  }


  /**
   * Initialize the value of the generationID for this ReplicationServerDomain.
   * This method is intended to be used for initialization at startup and
   * simply stores the new value without any additional processing.
   * For example it does not clear the change-log DBs
   *
   * @param generationId The new value of generationId.
   */
  public void initGenerationID(long generationId)
  {
    synchronized (generationIDLock)
    {
      this.generationId = generationId;
      this.generationIdSavedStatus = true;
    }
  }

  /**
   * Sets the provided value as the new in memory generationId.
   * Also clear the changelog databases.
   *
   * @param generationId The new value of generationId.
   * @param savedStatus  The saved status of the generationId.
   * @return The old generation id
   */
  public long changeGenerationId(long generationId, boolean savedStatus)
  {
    synchronized (generationIDLock)
    {
      long oldGenerationId = this.generationId;

      if (this.generationId != generationId)
      {
        clearDbs();

        this.generationId = generationId;
        this.generationIdSavedStatus = savedStatus;
      }
      return oldGenerationId;
    }
  }

  /**
   * Resets the generationID.
   *
   * @param senderHandler The handler associated to the server
   *        that requested to reset the generationId.
   * @param genIdMsg The reset generation ID msg received.
   */
  public void resetGenerationId(ServerHandler senderHandler,
    ResetGenerationIdMsg genIdMsg)
  {
    if (debugEnabled())
    {
      TRACER.debugInfo("In " + this + " Receiving ResetGenerationIdMsg from "
          + senderHandler.getServerId() + " for baseDn " + baseDn + ":\n"
          + genIdMsg);
    }

    try
    {
      // Acquire lock on domain (see more details in comment of start() method
      // of ServerHandler)
      lock();
    }
    catch (InterruptedException ex)
    {
      // We can't deal with this here, so re-interrupt thread so that it is
      // caught during subsequent IO.
      Thread.currentThread().interrupt();
      return;
    }

    try
    {
      long newGenId = genIdMsg.getGenerationId();

      if (newGenId != this.generationId)
      {
        changeGenerationId(newGenId, false);
      }
      else
      {
        // Order to take a gen id we already have, just ignore
        if (debugEnabled())
        {
          TRACER.debugInfo("In " + this
              + " Reset generation id requested for baseDn " + baseDn
              + " but generation id was already " + this.generationId + ":\n"
              + genIdMsg);
        }
      }

      // If we are the first replication server warned,
      // then forwards the reset message to the remote replication servers
      for (ServerHandler rsHandler : connectedRSs.values())
      {
        try
        {
          // After we'll have sent the message , the remote RS will adopt
          // the new genId
          rsHandler.setGenerationId(newGenId);
          if (senderHandler.isDataServer())
          {
            rsHandler.send(genIdMsg);
          }
        } catch (IOException e)
        {
          logError(ERR_EXCEPTION_FORWARDING_RESET_GEN_ID.get(baseDn,
              e.getMessage()));
        }
      }

      // Change status of the connected DSs according to the requested new
      // reference generation id
      for (DataServerHandler dsHandler : connectedDSs.values())
      {
        try
        {
          dsHandler.changeStatusForResetGenId(newGenId);
        } catch (IOException e)
        {
          logError(ERR_EXCEPTION_CHANGING_STATUS_AFTER_RESET_GEN_ID.get(baseDn,
              Integer.toString(dsHandler.getServerId()),
              e.getMessage()));
        }
      }

      // Update every peers (RS/DS) with potential topology changes (status
      // change). Rather than doing that each time a DS has a status change
      // (consecutive to reset gen id message), we prefer advertising once for
      // all after changes (less packet sent), here at the end of the reset msg
      // treatment.
      sendTopoInfoToAll();

      logError(NOTE_RESET_GENERATION_ID.get(baseDn, newGenId));
    }
    catch(Exception e)
    {
      logError(Message.raw(Category.SYNC, Severity.NOTICE,
          stackTraceToSingleLineString(e)));
    }
    finally
    {
      release();
    }
  }

  /**
   * Process message of a remote server changing his status.
   * @param senderHandler The handler associated to the server
   *        that changed his status.
   * @param csMsg The message containing the new status
   */
  public void processNewStatus(DataServerHandler senderHandler,
    ChangeStatusMsg csMsg)
  {
    if (debugEnabled())
    {
      debug("receiving ChangeStatusMsg from " + senderHandler.getServerId()
          + ":\n" + csMsg);
    }

    try
    {
      // Acquire lock on domain (see more details in comment of start() method
      // of ServerHandler)
      lock();
    }
    catch (InterruptedException ex)
    {
      // We can't deal with this here, so re-interrupt thread so that it is
      // caught during subsequent IO.
      Thread.currentThread().interrupt();
      return;
    }

    try
    {
      ServerStatus newStatus = senderHandler.processNewStatus(csMsg);
      if (newStatus == ServerStatus.INVALID_STATUS)
      {
        // Already logged an error in processNewStatus()
        // just return not to forward a bad status to topology
        return;
      }

      sendTopoInfoToAllExcept(senderHandler);

      Message message = NOTE_DIRECTORY_SERVER_CHANGED_STATUS.get(
          senderHandler.getServerId(), baseDn, newStatus.toString());
      logError(message);
    }
    catch(Exception e)
    {
      logError(Message.raw(Category.SYNC, Severity.NOTICE,
          stackTraceToSingleLineString(e)));
    }
    finally
    {
      release();
    }
  }

  /**
   * Change the status of a directory server according to the event generated
   * from the status analyzer.
   * @param dsHandler The handler of the directory server to update
   * @param event The event to be used for new status computation
   * @return True if we have been interrupted (must stop), false otherwise
   */
  public boolean changeStatus(DataServerHandler dsHandler,
      StatusMachineEvent event)
  {
    try
    {
      // Acquire lock on domain (see ServerHandler#start() for more details)
      lock();
    }
    catch (InterruptedException ex)
    {
      // We have been interrupted for dying, from stopStatusAnalyzer
      // to prevent deadlock in this situation:
      // RS is being shutdown, and stopServer will call stopStatusAnalyzer.
      // Domain lock is taken by shutdown thread while status analyzer thread
      // is willing to change the status of a server at the same time so is
      // waiting for the domain lock at the same time. As shutdown thread is
      // waiting for analyzer thread death, a deadlock occurs. So we force
      // interruption of the status analyzer thread death after 2 seconds if
      // it has not finished (see StatusAnalyzer.waitForShutdown). This allows
      // to have the analyzer thread taking the domain lock only when the
      // status of a DS has to be changed. See more comments in run method of
      // StatusAnalyzer.
      if (debugEnabled())
      {
        TRACER.debugInfo("Status analyzer for domain " + baseDn
            + " has been interrupted when"
            + " trying to acquire domain lock for changing the status of DS "
            + dsHandler.getServerId());
      }
      return true;
    }

    try
    {
      ServerStatus newStatus = ServerStatus.INVALID_STATUS;
      ServerStatus oldStatus = dsHandler.getStatus();
      try
      {
        newStatus = dsHandler.changeStatus(event);
      }
      catch (IOException e)
      {
        logError(ERR_EXCEPTION_CHANGING_STATUS_FROM_STATUS_ANALYZER
            .get(baseDn,
                Integer.toString(dsHandler.getServerId()),
                e.getMessage()));
      }

      if (newStatus == ServerStatus.INVALID_STATUS || newStatus == oldStatus)
      {
        // Change was impossible or already occurred (see StatusAnalyzer
        // comments)
        return false;
      }

      sendTopoInfoToAllExcept(dsHandler);
    }
    catch (Exception e)
    {
      logError(Message.raw(Category.SYNC, Severity.NOTICE,
          stackTraceToSingleLineString(e)));
    }
    finally
    {
      release();
    }

    return false;
  }

  /**
   * Update every peers (RS/DS) with topology changes.
   */
  public void sendTopoInfoToAll()
  {
    sendTopoInfoToAllExcept(null);
  }

  /**
   * Update every peers (RS/DS) with topology changes but one DS.
   *
   * @param dsHandler
   *          if not null, the topology message will not be sent to this DS
   */
  private void sendTopoInfoToAllExcept(DataServerHandler dsHandler)
  {
    sendTopoInfoToAllDSsExcept(dsHandler);
    sendTopoInfoToAllRSs();
  }

  /**
   * Clears the Db associated with that domain.
   */
  public void clearDbs()
  {
    // Reset the localchange and state db for the current domain
    synchronized (sourceDbHandlers)
    {
      for (DbHandler dbHandler : sourceDbHandlers.values())
      {
        try
        {
          dbHandler.clear();
        } catch (Exception e)
        {
          // TODO: i18n
          MessageBuilder mb = new MessageBuilder();
          mb.append(ERR_ERROR_CLEARING_DB.get(dbHandler.toString(),
              e.getMessage() + " " + stackTraceToSingleLineString(e)));
          logError(mb.toMessage());
        }
      }
      shutdownDbHandlers();
    }
    try
    {
      localReplicationServer.clearGenerationId(baseDn);
    }
    catch (Exception e)
    {
      // TODO: i18n
      logError(Message.raw("Exception caught while clearing generationId:"
          + e.getLocalizedMessage()));
    }
  }

  /**
   * Returns whether the provided server is in degraded
   * state due to the fact that the peer server has an invalid
   * generationId for this domain.
   *
   * @param serverId The serverId for which we want to know the
   *                 the state.
   * @return Whether it is degraded or not.
   */
  public boolean isDegradedDueToGenerationId(int serverId)
  {
    if (debugEnabled())
    {
      debug("isDegraded serverId=" + serverId + " given local generation Id="
          + this.generationId);
    }

    ServerHandler sHandler = connectedRSs.get(serverId);
    if (sHandler == null)
    {
      sHandler = connectedDSs.get(serverId);
      if (sHandler == null)
      {
        return false;
      }
    }

    if (debugEnabled())
    {
      debug("Compute degradation of serverId=" + serverId
          + " LS server generation Id=" + sHandler.getGenerationId());
    }
    return sHandler.getGenerationId() != this.generationId;
  }

  /**
   * Process topology information received from a peer RS.
   * @param topoMsg The just received topo message from remote RS
   * @param rsHandler The handler that received the message.
   * @param allowResetGenId True for allowing to reset the generation id (
   * when called after initial handshake)
   * @throws IOException If an error occurred.
   * @throws DirectoryException If an error occurred.
   */
  public void receiveTopoInfoFromRS(TopologyMsg topoMsg,
      ReplicationServerHandler rsHandler, boolean allowResetGenId)
      throws IOException, DirectoryException
  {
    if (debugEnabled())
    {
      debug("receiving TopologyMsg from serverId=" + rsHandler.getServerId()
          + ":\n" + topoMsg);
    }

    try
    {
      // Acquire lock on domain (see more details in comment of start() method
      // of ServerHandler)
      lock();
    }
    catch (InterruptedException ex)
    {
      // We can't deal with this here, so re-interrupt thread so that it is
      // caught during subsequent IO.
      Thread.currentThread().interrupt();
      return;
    }

    try
    {
      // Store DS connected to remote RS & update information about the peer RS
      rsHandler.processTopoInfoFromRS(topoMsg);

      // Handle generation id
      if (allowResetGenId)
      {
        resetGenerationIdIfPossible();
        setGenerationIdIfUnset(rsHandler.getGenerationId());
      }

      if (isDifferentGenerationId(rsHandler.getGenerationId()))
      {
        Message message = WARN_BAD_GENERATION_ID_FROM_RS.get(
            rsHandler.getServerId(),
            rsHandler.session.getReadableRemoteAddress(),
            rsHandler.getGenerationId(),
            baseDn, getLocalRSServerId(), generationId);
        logError(message);

        ErrorMsg errorMsg = new ErrorMsg(getLocalRSServerId(),
            rsHandler.getServerId(), message);
        rsHandler.send(errorMsg);
      }

      /*
       * Sends the currently known topology information to every connected
       * DS we have.
       */
      sendTopoInfoToAllDSsExcept(null);
    }
    catch(Exception e)
    {
      logError(Message.raw(Category.SYNC, Severity.NOTICE,
          stackTraceToSingleLineString(e)));
    }
    finally
    {
      release();
    }
  }

  private void setGenerationIdIfUnset(long generationId)
  {
    if (this.generationId < 0)
    {
      this.generationId = generationId;
    }
  }

  /**
   * Returns the latest monitor data available for this replication server
   * domain.
   *
   * @return The latest monitor data available for this replication server
   *         domain, which is never {@code null}.
   */
  ReplicationDomainMonitorData getDomainMonitorData()
  {
    return domainMonitor.getMonitorData();
  }

  /**
   * Set the purge delay on all the db Handlers for this Domain
   * of Replication.
   *
   * @param delay The new purge delay to use.
   */
  public void setPurgeDelay(long delay)
  {
    for (DbHandler dbHandler : sourceDbHandlers.values())
    {
      dbHandler.setPurgeDelay(delay);
    }
  }

  /**
   * Get the map of connected DSs.
   * @return The map of connected DSs
   */
  public Map<Integer, DataServerHandler> getConnectedDSs()
  {
    return Collections.unmodifiableMap(connectedDSs);
  }

  /**
   * Get the map of connected RSs.
   * @return The map of connected RSs
   */
  public Map<Integer, ReplicationServerHandler> getConnectedRSs()
  {
    return Collections.unmodifiableMap(connectedRSs);
  }


  /**
   * A synchronization mechanism is created to insure exclusive access to the
   * domain. The goal is to have a consistent view of the topology by locking
   * the structures holding the topology view of the domain:
   * {@link #connectedDSs} and {@link #connectedRSs}. When a connection is
   * established with a peer DS or RS, the lock should be taken before updating
   * these structures, then released. The same mechanism should be used when
   * updating any data related to the view of the topology: for instance if the
   * status of a DS is changed, the lock should be taken before updating the
   * matching server handler and sending the topology messages to peers and
   * released after.... This allows every member of the topology to have a
   * consistent view of the topology and to be sure it will not miss some
   * information.
   * <p>
   * So the locking system must be called (not exhaustive list):
   * <ul>
   * <li>when connection established with a DS or RS</li>
   * <li>when connection ended with a DS or RS</li>
   * <li>when receiving a TopologyMsg and updating structures</li>
   * <li>when creating and sending a TopologyMsg</li>
   * <li>when a DS status is changing (ChangeStatusMsg received or sent)...</li>
   * </ul>
   */
  private final ReentrantLock lock = new ReentrantLock();

  /**
   * This lock is used to protect the generationId variable.
   */
  private final Object generationIDLock = new Object();

  /**
   * Tests if the current thread has the lock on this domain.
   * @return True if the current thread has the lock.
   */
  public boolean hasLock()
  {
    return lock.getHoldCount() > 0;
  }

  /**
   * Takes the lock on this domain (blocking until lock can be acquired) or
   * calling thread is interrupted.
   * @throws java.lang.InterruptedException If interrupted.
   */
  public void lock() throws InterruptedException
  {
    lock.lockInterruptibly();
  }

  /**
   * Releases the lock on this domain.
   */
  public void release()
  {
    lock.unlock();
  }

  /**
   * Tries to acquire the lock on the domain within a given amount of time.
   * @param timeout The amount of milliseconds to wait for acquiring the lock.
   * @return True if the lock was acquired, false if timeout occurred.
   * @throws java.lang.InterruptedException When call was interrupted.
   */
  public boolean tryLock(long timeout) throws InterruptedException
  {
    return lock.tryLock(timeout, TimeUnit.MILLISECONDS);
  }

  /**
   * Starts the status analyzer for the domain if not already started.
   */
  private void startStatusAnalyzer()
  {
    int degradedStatusThreshold =
        localReplicationServer.getDegradedStatusThreshold();
    if (degradedStatusThreshold > 0) // 0 means no status analyzer
    {
      final StatusAnalyzer thread =
          new StatusAnalyzer(this, degradedStatusThreshold);
      if (statusAnalyzer.compareAndSet(null, thread))
      {
        thread.start();
      }
    }
  }

  /**
   * Stops the status analyzer for the domain.
   */
  private void stopStatusAnalyzer()
  {
    final StatusAnalyzer thread = statusAnalyzer.get();
    if (statusAnalyzer.compareAndSet(thread, null))
    {
      thread.shutdown();
      thread.waitForShutdown();
    }
  }

  /**
   * Starts the monitoring publisher for the domain if not already started.
   */
  private void startMonitoringPublisher()
  {
    long period = localReplicationServer.getMonitoringPublisherPeriod();
    if (period > 0) // 0 means no monitoring publisher
    {
      final MonitoringPublisher thread = new MonitoringPublisher(this, period);
      if (monitoringPublisher.compareAndSet(null, thread))
      {
        thread.start();
      }
    }
  }

  /**
   * Stops the monitoring publisher for the domain.
   */
  private void stopMonitoringPublisher()
  {
    final MonitoringPublisher thread = monitoringPublisher.get();
    if (monitoringPublisher.compareAndSet(thread, null))
    {
      thread.shutdown();
      thread.waitForShutdown();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void initializeMonitorProvider(MonitorProviderCfg configuraiton)
  {
    // Nothing to do for now
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getMonitorInstanceName()
  {
    return "Replication server RS(" + localReplicationServer.getServerId()
        + ") " + localReplicationServer.getServerURL() + ",cn="
        + baseDn.replace(',', '_').replace('=', '_') + ",cn=Replication";
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Attribute> getMonitorData()
  {
    // publish the server id and the port number.
    List<Attribute> attributes = new ArrayList<Attribute>();
    attributes.add(Attributes.create("replication-server-id",
        String.valueOf(localReplicationServer.getServerId())));
    attributes.add(Attributes.create("replication-server-port",
        String.valueOf(localReplicationServer.getReplicationPort())));
    attributes.add(Attributes.create("domain-name", baseDn));
    attributes.add(Attributes.create("generation-id",
        baseDn + " " + generationId));

    // Missing changes
    long missingChanges = getDomainMonitorData().getMissingChangesRS(
        localReplicationServer.getServerId());
    attributes.add(Attributes.create("missing-changes",
        String.valueOf(missingChanges)));

    return attributes;
  }

  /**
   * Register in the domain an handler that subscribes to changes.
   * @param mHandler the provided subscribing handler.
   */
  public void registerHandler(MessageHandler mHandler)
  {
    this.otherHandlers.add(mHandler);
  }

  /**
   * Unregister from the domain an handler.
   * @param mHandler the provided unsubscribing handler.
   * @return Whether this handler has been unregistered with success.
   */
  public boolean unRegisterHandler(MessageHandler mHandler)
  {
    return this.otherHandlers.remove(mHandler);
  }

  /**
   * Return the state that contain for each server the time of eligibility.
   * @return the state.
   */
  public ServerState getChangeTimeHeartbeatState()
  {
    if (ctHeartbeatState == null)
    {
      ctHeartbeatState = getDbServerState().duplicate();
    }
    return ctHeartbeatState;
  }

  /**
   * Computes the eligible server state for the domain.
   *
   * <pre>
   *     s1               s2          s3
   *     --               --          --
   *                                 csn31
   *     csn15
   *
   *  ----------------------------------------- eligibleCSN
   *     csn14
   *                     csn26
   *     csn13
   * </pre>
   *
   * The eligibleState is : s1;csn14 / s2;csn26 / s3;csn31
   *
   * @param eligibleCSN
   *          The provided eligible CSN.
   * @return The computed eligible server state.
   */
  public ServerState getEligibleState(CSN eligibleCSN)
  {
    ServerState dbState = getDbServerState();

    // The result is initialized from the dbState.
    // From it, we don't want to keep the changes newer than eligibleCSN.
    ServerState result = dbState.duplicate();

    if (eligibleCSN != null)
    {
      for (int serverId : dbState)
      {
        DbHandler h = sourceDbHandlers.get(serverId);
        CSN mostRecentDbCSN = dbState.getCSN(serverId);
        try {
          // Is the most recent change in the Db newer than eligible CSN ?
          // if yes (like csn15 in the example above, then we have to go back
          // to the Db and look for the change older than eligible CSN (csn14)
          if (eligibleCSN.olderOrEqual(mostRecentDbCSN))
          {
            // let's try to seek the first change <= eligibleCSN
            ReplicaDBCursor cursor = null;
            try {
              cursor = h.generateCursorFrom(eligibleCSN);
              if (cursor != null && cursor.getChange() != null) {
                CSN newCSN = cursor.getChange().getCSN();
                result.update(newCSN);
              }
            } catch (ChangelogException e) {
              // there's no change older than eligibleCSN (case of s3/csn31)
              result.update(new CSN(0, 0, serverId));
            } finally {
              close(cursor);
            }
          } else {
            // for this serverId, all changes in the ChangelogDb are holder
            // than eligibleCSN, the most recent in the db is our guy.
            result.update(mostRecentDbCSN);
          }
        } catch (Exception e) {
          logError(ERR_WRITER_UNEXPECTED_EXCEPTION
              .get(stackTraceToSingleLineString(e)));
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }
    }

    if (debugEnabled())
    {
      TRACER
          .debugInfo("In " + this + " getEligibleState() result is " + result);
    }
    return result;
  }

  /**
   * Returns the start state of the domain, made of the first (oldest)
   * change stored for each serverId.
   * Note: Because the replication changelogdb trimming always keep one change
   * whatever its date, the change contained in the returned state can be very
   * old.
   * @return the start state of the domain.
   */
  public ServerState getStartState()
  {
    ServerState domainStartState = new ServerState();
    for (DbHandler dbHandler : sourceDbHandlers.values())
    {
      domainStartState.update(dbHandler.getFirstChange());
    }
    return domainStartState;
  }

  /**
   * Returns the eligible CSN for that domain - relies on the
   * ChangeTimeHeartbeat state.
   * <p>
   * For each DS, take the oldest CSN from the changetime heartbeat state and
   * from the changelog db last CSN. Can be null.
   *
   * @return the eligible CSN.
   */
  public CSN getEligibleCSN()
  {
    CSN eligibleCSN = null;

    for (DbHandler db : sourceDbHandlers.values())
    {
      // Consider this producer (DS/db).
      int serverId = db.getServerId();

      // Should it be considered for eligibility ?
      CSN heartbeatLastCSN =
        getChangeTimeHeartbeatState().getCSN(serverId);

      // If the most recent UpdateMsg or CLHeartbeatMsg received is very old
      // then the domain is considered down and not considered for eligibility
      /*
      if ((heartbeatLastDN != null) &&
          (TimeThread.getTime()- heartbeatLastDN.getTime() > 5000))
      {
        if (debugEnabled())
          TRACER.debugInfo("In " + this.getName() +
            " Server " + sid
            + " is not considered for eligibility ... potentially down");
        continue;
      }
      */

      if (!isServerConnected(serverId))
      {
        if (debugEnabled())
        {
          debug("serverId=" + serverId
              + " is not considered for eligibility ... potentially down");
        }
        continue;
      }

      CSN changelogLastCSN = db.getLastChange();
      if (changelogLastCSN != null
          && (eligibleCSN == null || changelogLastCSN.newer(eligibleCSN)))
      {
        eligibleCSN = changelogLastCSN;
      }
      if (heartbeatLastCSN != null
          && (eligibleCSN == null || heartbeatLastCSN.newer(eligibleCSN)))
      {
        eligibleCSN = heartbeatLastCSN;
      }
    }

    if (debugEnabled())
    {
      debug("getEligibleCSN() returns result =" + eligibleCSN);
    }
    return eligibleCSN;
  }

  private boolean isServerConnected(int serverId)
  {
    if (connectedDSs.containsKey(serverId))
    {
      return true;
    }

    // not directly connected
    for (ReplicationServerHandler rsHandler : connectedRSs.values())
    {
      if (rsHandler.isRemoteLDAPServer(serverId))
      {
        return true;
      }
    }
    return false;
  }


  /**
   * Processes a ChangeTimeHeartbeatMsg received, by storing the CSN (timestamp)
   * value received, and forwarding the message to the other RSes.
   * @param senderHandler The handler for the server that sent the heartbeat.
   * @param msg The message to process.
   */
  public void processChangeTimeHeartbeatMsg(ServerHandler senderHandler,
      ChangeTimeHeartbeatMsg msg )
  {
    try
    {
      // Acquire lock on domain (see more details in comment of start() method
      // of ServerHandler)
      lock();
    }
    catch (InterruptedException ex)
    {
      // We can't deal with this here, so re-interrupt thread so that it is
      // caught during subsequent IO.
      Thread.currentThread().interrupt();
      return;
    }

    try
    {
      storeReceivedCTHeartbeat(msg.getCSN());
      if (senderHandler.isDataServer())
      {
        // If we are the first replication server warned,
        // then forwards the message to the remote replication servers
        for (ReplicationServerHandler rsHandler : connectedRSs.values())
        {
          try
          {
            if (rsHandler.getProtocolVersion() >= REPLICATION_PROTOCOL_V3)
            {
              rsHandler.send(msg);
            }
          }
          catch (IOException e)
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
            logError(ERR_CHANGELOG_ERROR_SENDING_MSG
                .get("Replication Server "
                    + localReplicationServer.getReplicationPort() + " "
                    + baseDn + " " + localReplicationServer.getServerId()));
            stopServer(rsHandler, false);
          }
        }
      }
    }
    finally
    {
      release();
    }
  }

  /**
   * Store a change time value received from a data server.
   * @param csn The provided change time.
   */
  public void storeReceivedCTHeartbeat(CSN csn)
  {
    // TODO:May be we can spare processing by only storing CSN (timestamp)
    // instead of a server state.
    getChangeTimeHeartbeatState().update(csn);
  }

  /**
   * This methods count the changes, server by server :
   * - from a serverState start point
   * - to (inclusive) an end point (the provided endCSN).
   * @param startState The provided start server state.
   * @param endCSN The provided end CSN.
   * @return The number of changes between startState and endCSN.
   */
  public long getEligibleCount(ServerState startState, CSN endCSN)
  {
    long res = 0;

    for (int serverId : getDbServerState())
    {
      CSN startCSN = startState.getCSN(serverId);
      long serverIdRes = getCount(serverId, startCSN, endCSN);

      // The startPoint is excluded when counting the ECL eligible changes
      if (startCSN != null && serverIdRes > 0)
      {
        serverIdRes--;
      }

      res += serverIdRes;
    }
    return res;
  }

  /**
   * This methods count the changes, server by server:
   * - from a start CSN
   * - to (inclusive) an end point (the provided endCSN).
   * @param startCSN The provided start CSN.
   * @param endCSN The provided end CSN.
   * @return The number of changes between startTime and endCSN.
   */
  public long getEligibleCount(CSN startCSN, CSN endCSN)
  {
    long res = 0;
    for (int serverId : getDbServerState()) {
      CSN lStartCSN =
          new CSN(startCSN.getTime(), startCSN.getSeqnum(), serverId);
      res += getCount(serverId, lStartCSN, endCSN);
    }
    return res;
  }

  /**
   * Get the latest (more recent) trim date of the changelog dbs associated
   * to this domain.
   * @return The latest trim date.
   */
  public long getLatestDomainTrimDate()
  {
    long latest = 0;
    for (DbHandler db : sourceDbHandlers.values())
    {
      if (latest == 0 || latest < db.getLatestTrimDate())
      {
        latest = db.getLatestTrimDate();
      }
    }
    return latest;
  }

  /**
   * Return the monitor instance name of the ReplicationServer that created the
   * current instance.
   *
   * @return the monitor instance name of the ReplicationServer that created the
   *         current instance.
   */
  String getLocalRSMonitorInstanceName()
  {
    return this.localReplicationServer.getMonitorInstanceName();
  }

  /**
   * Return the serverId of the ReplicationServer that created the current
   * instance.
   *
   * @return the serverId of the ReplicationServer that created the current
   *         instance.
   */
  int getLocalRSServerId()
  {
    return this.localReplicationServer.getServerId();
  }

  /**
   * Update the status analyzer with the new threshold value.
   *
   * @param degradedStatusThreshold
   *          The new threshold value.
   */
  void updateDegradedStatusThreshold(int degradedStatusThreshold)
  {
    if (degradedStatusThreshold == 0)
    {
      // Requested to stop analyzers
      stopStatusAnalyzer();
      return;
    }

    final StatusAnalyzer saThread = statusAnalyzer.get();
    if (saThread != null) // it is running
    {
      saThread.setDegradedStatusThreshold(degradedStatusThreshold);
    }
    else if (connectedDSs.size() > 0)
    {
      // Requested to start analyzers with provided threshold value
      startStatusAnalyzer();
    }
  }

  /**
   * Update the monitoring publisher with the new period value.
   *
   * @param period
   *          The new period value.
   */
  void updateMonitoringPeriod(long period)
  {
    if (period == 0)
    {
      // Requested to stop monitoring publishers
      stopMonitoringPublisher();
      return;
    }

    final MonitoringPublisher mpThread = monitoringPublisher.get();
    if (mpThread != null) // it is running
    {
      mpThread.setPeriod(period);
    }
    else if (connectedDSs.size() > 0 || connectedRSs.size() > 0)
    {
      // Requested to start monitoring publishers with provided period value
      startMonitoringPublisher();
    }
  }

  /**
   * Registers a DS handler into this domain and notifies the domain about the
   * new DS.
   *
   * @param dsHandler
   *          The Directory Server Handler to register
   */
  public void register(DataServerHandler dsHandler)
  {
    startStatusAnalyzer();
    startMonitoringPublisher();

    // connected with new DS: store handler.
    connectedDSs.put(dsHandler.getServerId(), dsHandler);

    // Tell peer RSs and DSs a new DS just connected to us
    // No need to re-send TopologyMsg to this just new DS
    sendTopoInfoToAllExcept(dsHandler);
  }

  /**
   * Registers the RS handler into this domain and notifies the domain.
   *
   * @param rsHandler
   *          The Replication Server Handler to register
   */
  public void register(ReplicationServerHandler rsHandler)
  {
    startMonitoringPublisher();

    // connected with new RS (either outgoing or incoming
    // connection): store handler.
    connectedRSs.put(rsHandler.getServerId(), rsHandler);
  }

  private void debug(String message)
  {
    TRACER.debugInfo("In RS serverId=" + localReplicationServer.getServerId()
        + " for baseDn=" + baseDn + " and port="
        + localReplicationServer.getReplicationPort() + ": " + message);
  }
}
