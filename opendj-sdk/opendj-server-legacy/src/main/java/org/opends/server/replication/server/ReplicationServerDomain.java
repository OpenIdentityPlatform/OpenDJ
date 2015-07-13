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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.replication.server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.admin.std.server.MonitorProviderCfg;
import org.opends.server.api.MonitorProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.DSInfo;
import org.opends.server.replication.common.RSInfo;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.common.ServerStatus;
import org.opends.server.replication.common.StatusMachineEvent;
import org.opends.server.replication.protocol.AckMsg;
import org.opends.server.replication.protocol.ChangeStatusMsg;
import org.opends.server.replication.protocol.ChangeTimeHeartbeatMsg;
import org.opends.server.replication.protocol.ErrorMsg;
import org.opends.server.replication.protocol.MonitorMsg;
import org.opends.server.replication.protocol.MonitorRequestMsg;
import org.opends.server.replication.protocol.ReplicaOfflineMsg;
import org.opends.server.replication.protocol.ResetGenerationIdMsg;
import org.opends.server.replication.protocol.RoutableMsg;
import org.opends.server.replication.protocol.TopologyMsg;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.DBCursor;
import org.opends.server.replication.server.changelog.api.DBCursor.CursorOptions;
import org.opends.server.replication.server.changelog.api.ReplicationDomainDB;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.HostPort;

import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.replication.common.ServerStatus.*;
import static org.opends.server.replication.common.StatusMachineEvent.*;
import static org.opends.server.replication.protocol.ProtocolVersion.*;
import static org.opends.server.replication.server.changelog.api.DBCursor.KeyMatchingStrategy.*;
import static org.opends.server.replication.server.changelog.api.DBCursor.PositionStrategy.*;
import static org.opends.server.util.CollectionUtils.*;
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
  private final DN baseDN;

  /**
   * Periodically verifies whether the connected DSs are late and publishes any
   * pending status messages.
   */
  private final StatusAnalyzer statusAnalyzer;

  /**
   * The monitoring publisher that periodically sends monitoring messages to the
   * topology. Using an AtomicReference to avoid leaking references to costly
   * threads.
   */
  private final AtomicReference<MonitoringPublisher> monitoringPublisher = new AtomicReference<>();
  /** Maintains monitor data for the current domain. */
  private final ReplicationDomainMonitor domainMonitor = new ReplicationDomainMonitor(this);

  /**
   * The following map contains one balanced tree for each replica ID to which
   * we are currently publishing the first update in the balanced tree is the
   * next change that we must push to this particular server.
   */
  private final Map<Integer, DataServerHandler> connectedDSs = new ConcurrentHashMap<>();

  /**
   * This map contains one ServerHandler for each replication servers with which
   * we are connected (so normally all the replication servers) the first update
   * in the balanced tree is the next change that we must push to this
   * particular server.
   */
  private final Map<Integer, ReplicationServerHandler> connectedRSs = new ConcurrentHashMap<>();

  private final ReplicationDomainDB domainDB;
  /** The ReplicationServer that created the current instance. */
  private final ReplicationServer localReplicationServer;

  /**
   * The generationId of the current replication domain. The generationId is
   * computed by hashing the first 1000 entries in the DB.
   */
  private volatile long generationId = -1;
  /**
   * JNR, this is legacy code, hard to follow logic. I think what this field
   * tries to say is: "is the generationId in use anywhere?", i.e. is there a
   * replication topology in place? As soon as an answer to any of these
   * question comes true, then it is set to true.
   * <p>
   * It looks like the only use of this field is to prevent the
   * {@link #generationId} from being reset by
   * {@link #resetGenerationIdIfPossible()}.
   */
  private volatile boolean generationIdSavedStatus;

  /** The tracer object for the debug logger. */
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

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
  private final Map<CSN, ExpectedAcksInfo> waitingAcks = new ConcurrentHashMap<>();

  /**
   * The timer used to run the timeout code (timer tasks) for the assured update
   * messages we are waiting acks for.
   */
  private final Timer assuredTimeoutTimer;
  /**
   * Counter used to purge the timer tasks references in assuredTimeoutTimer,
   * every n number of treated assured messages.
   */
  private int assuredTimeoutTimerPurgeCounter;



  /**
   * Stores pending status messages such as DS change time heartbeats for future
   * forwarding to the rest of the topology. This class is required in order to
   * decouple inbound IO processing from outbound IO processing and avoid
   * potential inter-process deadlocks. In particular, the {@code ServerReader}
   * thread must not send messages.
   */
  private static class PendingStatusMessages
  {
    private final Map<Integer, ChangeTimeHeartbeatMsg> pendingHeartbeats = new HashMap<>(1);
    private final Map<Integer, MonitorMsg> pendingDSMonitorMsgs = new HashMap<>(1);
    private final Map<Integer, MonitorMsg> pendingRSMonitorMsgs = new HashMap<>(1);
    private boolean sendRSTopologyMsg;
    private boolean sendDSTopologyMsg;
    private int excludedDSForTopologyMsg = -1;

    /**
     * Enqueues a TopologyMsg for all the connected directory servers in order
     * to let them know the topology (every known DSs and RSs).
     *
     * @param excludedDS
     *          If not null, the topology message will not be sent to this DS.
     */
    private void enqueueTopoInfoToAllDSsExcept(DataServerHandler excludedDS)
    {
      int excludedServerId = excludedDS != null ? excludedDS.getServerId() : -1;
      if (sendDSTopologyMsg)
      {
        if (excludedServerId != excludedDSForTopologyMsg)
        {
          excludedDSForTopologyMsg = -1;
        }
      }
      else
      {
        sendDSTopologyMsg = true;
        excludedDSForTopologyMsg = excludedServerId;
      }
    }

    /**
     * Enqueues a TopologyMsg for all the connected replication servers in order
     * to let them know our connected LDAP servers.
     */
    private void enqueueTopoInfoToAllRSs()
    {
      sendRSTopologyMsg = true;
    }

    /**
     * Enqueues a ChangeTimeHeartbeatMsg received from a DS for forwarding to
     * all other RS instances.
     *
     * @param msg
     *          The heartbeat message.
     */
    private void enqueueChangeTimeHeartbeatMsg(ChangeTimeHeartbeatMsg msg)
    {
      pendingHeartbeats.put(msg.getCSN().getServerId(), msg);
    }

    private void enqueueDSMonitorMsg(int dsServerId, MonitorMsg msg)
    {
      pendingDSMonitorMsgs.put(dsServerId, msg);
    }

    private void enqueueRSMonitorMsg(int rsServerId, MonitorMsg msg)
    {
      pendingRSMonitorMsgs.put(rsServerId, msg);
    }

    /** {@inheritDoc} */
    @Override
    public String toString()
    {
      return getClass().getSimpleName()
          + " pendingHeartbeats=" + pendingHeartbeats
          + ", pendingDSMonitorMsgs=" + pendingDSMonitorMsgs
          + ", pendingRSMonitorMsgs=" + pendingRSMonitorMsgs
          + ", sendRSTopologyMsg=" + sendRSTopologyMsg
          + ", sendDSTopologyMsg=" + sendDSTopologyMsg
          + ", excludedDSForTopologyMsg=" + excludedDSForTopologyMsg;
    }
  }

  private final Object pendingStatusMessagesLock = new Object();

  /** @GuardedBy("pendingStatusMessagesLock") */
  private PendingStatusMessages pendingStatusMessages = new PendingStatusMessages();

  /**
   * Creates a new ReplicationServerDomain associated to the baseDN.
   *
   * @param baseDN
   *          The baseDN associated to the ReplicationServerDomain.
   * @param localReplicationServer
   *          the ReplicationServer that created this instance.
   */
  public ReplicationServerDomain(DN baseDN,
      ReplicationServer localReplicationServer)
  {
    this.baseDN = baseDN;
    this.localReplicationServer = localReplicationServer;
    this.assuredTimeoutTimer = new Timer("Replication server RS("
        + localReplicationServer.getServerId()
        + ") assured timer for domain \"" + baseDN + "\"", true);
    this.domainDB =
        localReplicationServer.getChangelogDB().getReplicationDomainDB();
    this.statusAnalyzer = new StatusAnalyzer(this);
    this.statusAnalyzer.start();
    DirectoryServer.registerMonitorProvider(this);
  }

  /**
   * Add an update that has been received to the list of
   * updates that must be forwarded to all other servers.
   *
   * @param updateMsg  The update that has been received.
   * @param sourceHandler The ServerHandler for the server from which the
   *        update was received
   * @throws IOException When an IO exception happens during the update
   *         processing.
   */
  public void put(UpdateMsg updateMsg, ServerHandler sourceHandler) throws IOException
  {
    sourceHandler.updateServerState(updateMsg);
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
    final PreparedAssuredInfo preparedAssuredInfo = getPreparedAssuredInfo(updateMsg, sourceHandler);

    if (!publishUpdateMsg(updateMsg))
    {
      return;
    }

    final List<Integer> assuredServers = getAssuredServers(updateMsg, preparedAssuredInfo);

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
    final NotAssuredUpdateMsg notAssuredUpdateMsg =
        preparedAssuredInfo != null ? new NotAssuredUpdateMsg(updateMsg) : null;

    // Push the message to the replication servers
    if (sourceHandler.isDataServer())
    {
      for (ReplicationServerHandler rsHandler : connectedRSs.values())
      {
        /**
         * Ignore updates to RS with bad gen id
         * (no system managed status for a RS)
         */
        if (!isDifferentGenerationId(rsHandler, updateMsg))
        {
          addUpdate(rsHandler, updateMsg, notAssuredUpdateMsg, assuredServers);
        }
      }
    }

    // Push the message to the LDAP servers
    for (DataServerHandler dsHandler : connectedDSs.values())
    {
      // Do not forward the change to the server that just sent it
      if (dsHandler != sourceHandler
          && !isUpdateMsgFiltered(updateMsg, dsHandler))
      {
        addUpdate(dsHandler, updateMsg, notAssuredUpdateMsg, assuredServers);
      }
    }
  }

  private boolean isDifferentGenerationId(ReplicationServerHandler rsHandler,
      UpdateMsg updateMsg)
  {
    final boolean isDifferent = isDifferentGenerationId(rsHandler.getGenerationId());
    if (isDifferent && logger.isTraceEnabled())
    {
      debug("updateMsg " + updateMsg.getCSN()
          + " will not be sent to replication server "
          + rsHandler.getServerId() + " with generation id "
          + rsHandler.getGenerationId() + " different from local "
          + "generation id " + generationId);
    }
    return isDifferent;
  }

  /**
   * Ignore updates to DS in bad BAD_GENID_STATUS or FULL_UPDATE_STATUS.
   * <p>
   * The RSD lock should not be taken here as it is acceptable to have a delay
   * between the time the server has a wrong status and the fact we detect it:
   * the updates that succeed to pass during this time will have no impact on
   * remote server. But it is interesting to not saturate uselessly the network
   * if the updates are not necessary so this check to stop sending updates is
   * interesting anyway. Not taking the RSD lock allows to have better
   * performances in normal mode (most of the time).
   */
  private boolean isUpdateMsgFiltered(UpdateMsg updateMsg, DataServerHandler dsHandler)
  {
    final ServerStatus dsStatus = dsHandler.getStatus();
    if (dsStatus == ServerStatus.BAD_GEN_ID_STATUS)
    {
      if (logger.isTraceEnabled())
      {
        debug("updateMsg " + updateMsg.getCSN()
            + " will not be sent to directory server "
            + dsHandler.getServerId() + " with generation id "
            + dsHandler.getGenerationId() + " different from local "
            + "generation id " + generationId);
      }
      return true;
    }
    else if (dsStatus == ServerStatus.FULL_UPDATE_STATUS)
    {
      if (logger.isTraceEnabled())
      {
        debug("updateMsg " + updateMsg.getCSN()
            + " will not be sent to directory server "
            + dsHandler.getServerId() + " as it is in full update");
      }
      return true;
    }
    return false;
  }

  private PreparedAssuredInfo getPreparedAssuredInfo(UpdateMsg updateMsg,
      ServerHandler sourceHandler) throws IOException
  {
    // Assured feature is supported starting from replication protocol V2
    if (!updateMsg.isAssured()
        || sourceHandler.getProtocolVersion() < REPLICATION_PROTOCOL_V2)
    {
      return null;
    }

    // According to assured sub-mode, prepare structures to keep track of
    // the acks we are interested in.
    switch (updateMsg.getAssuredMode())
    {
    case SAFE_DATA_MODE:
      sourceHandler.incrementAssuredSdReceivedUpdates();
      return processSafeDataUpdateMsg(updateMsg, sourceHandler);

    case SAFE_READ_MODE:
      sourceHandler.incrementAssuredSrReceivedUpdates();
      return processSafeReadUpdateMsg(updateMsg, sourceHandler);

    default:
      // Unknown assured mode: should never happen
      logger.error(ERR_RS_UNKNOWN_ASSURED_MODE,
          localReplicationServer.getServerId(), updateMsg.getAssuredMode(), baseDN, updateMsg);
      return null;
    }
  }

  private List<Integer> getAssuredServers(UpdateMsg updateMsg, PreparedAssuredInfo preparedAssuredInfo)
  {
    List<Integer> expectedServers = null;
    if (preparedAssuredInfo != null && preparedAssuredInfo.expectedServers != null)
    {
      expectedServers = preparedAssuredInfo.expectedServers;
      // Store the expected acks info into the global map.
      // The code for processing reception of acks for this update will update
      // info kept in this object and if enough acks received, it will send
      // back the final ack to the requester and remove the object from this map
      // OR
      // The following timer will time out and send an timeout ack to the
      // requester if the acks are not received in time. The timer will also
      // remove the object from this map.
      final CSN csn = updateMsg.getCSN();
      waitingAcks.put(csn, preparedAssuredInfo.expectedAcksInfo);

      // Arm timer for this assured update message (wait for acks until it times out)
      final AssuredTimeoutTask assuredTimeoutTask = new AssuredTimeoutTask(csn);
      assuredTimeoutTimer.schedule(assuredTimeoutTask, localReplicationServer.getAssuredTimeout());
      // Purge timer every 100 treated messages
      assuredTimeoutTimerPurgeCounter++;
      if ((assuredTimeoutTimerPurgeCounter % 100) == 0)
      {
        assuredTimeoutTimer.purge();
      }
    }

    return expectedServers != null ? expectedServers : Collections.<Integer> emptyList();
  }

  private boolean publishUpdateMsg(UpdateMsg updateMsg)
  {
    try
    {
      if (updateMsg instanceof ReplicaOfflineMsg)
      {
        final ReplicaOfflineMsg offlineMsg = (ReplicaOfflineMsg) updateMsg;
        this.domainDB.notifyReplicaOffline(baseDN, offlineMsg.getCSN());
        return true;
      }

      if (this.domainDB.publishUpdateMsg(baseDN, updateMsg))
      {
        /*
         * JNR: Matt and I had a hard time figuring out where to put this
         * synchronized block. We elected to put it here, but without a strong
         * conviction.
         */
        synchronized (generationIDLock)
        {
          /*
           * JNR: I think the generationIdSavedStatus is set to true because
           * method above created a ReplicaDB which assumes the generationId was
           * communicated to another server. Hence setting true on this field
           * prevent the generationId from being reset.
           */
          generationIdSavedStatus = true;
        }
      }
      return true;
    }
    catch (ChangelogException e)
    {
      /*
       * Because of database problem we can't save any more changes from at
       * least one LDAP server. This replicationServer therefore can't do it's
       * job properly anymore and needs to close all its connections and
       * shutdown itself.
       */
      logger.error(ERR_CHANGELOG_SHUTDOWN_DATABASE_ERROR, stackTraceToSingleLineString(e));
      localReplicationServer.shutdown();
      return false;
    }
  }

  private void addUpdate(ServerHandler sHandler, UpdateMsg updateMsg,
      NotAssuredUpdateMsg notAssuredUpdateMsg, List<Integer> assuredServers)
  {
    // Assured mode: post an assured or not assured matching update message
    // according to what has been computed for the destination server
    if (notAssuredUpdateMsg != null
        && !assuredServers.contains(sHandler.getServerId()))
    {
      sHandler.add(notAssuredUpdateMsg);
    }
    else
    {
      sHandler.add(updateMsg);
    }
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
    List<Integer> expectedServers = new ArrayList<>();
    List<Integer> wrongStatusServers = new ArrayList<>();

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
    if (!expectedServers.isEmpty())
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
      logger.error(ERR_UNKNOWN_ASSURED_SAFE_DATA_LEVEL,
          localReplicationServer.getServerId(), safeDataLevel, baseDN, update);
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

    List<Integer> expectedServers = new ArrayList<>();
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
  void processAck(AckMsg ack, ServerHandler ackingServer)
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
            LocalizableMessageBuilder mb = new LocalizableMessageBuilder();
            mb.append(ERR_RS_ERROR_SENDING_ACK.get(
                localReplicationServer.getServerId(), origServer.getServerId(), csn, baseDN));
            mb.append(" ");
            mb.append(stackTraceToSingleLineString(e));
            logger.error(mb.toMessage());
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
          if (logger.isTraceEnabled())
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
            LocalizableMessageBuilder mb = new LocalizableMessageBuilder();
            mb.append(ERR_RS_ERROR_SENDING_ACK.get(
                localReplicationServer.getServerId(), origServer.getServerId(), csn, baseDN));
            mb.append(" ");
            mb.append(stackTraceToSingleLineString(e));
            logger.error(mb.toMessage());
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
   * @param serversToDisconnect
   *          the replication servers addresses for which we want to stop
   *          operations
   */
  public void stopReplicationServers(Collection<HostPort> serversToDisconnect)
  {
    for (ReplicationServerHandler rsHandler : connectedRSs.values())
    {
      if (serversToDisconnect.contains(
            HostPort.valueOf(rsHandler.getServerAddressURL())))
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
      logger.error(ERR_DUPLICATE_SERVER_ID, localReplicationServer.getMonitorInstanceName(),
          connectedDSs.get(dsHandler.getServerId()), dsHandler, dsHandler.getServerId());
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
    if (logger.isTraceEnabled())
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
          if (logger.isTraceEnabled())
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
        }
        else if (connectedDSs.containsKey(sHandler.getServerId()))
        {
          unregisterServerHandler(sHandler, shutdown, true);
        }
      }
      catch(Exception e)
      {
        logger.error(LocalizableMessage.raw(stackTraceToSingleLineString(e)));
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

  private void unregisterServerHandler(ServerHandler sHandler, boolean shutdown,
      boolean isDirectoryServer)
  {
    unregisterServerHandler(sHandler);
    sHandler.shutdown();

    resetGenerationIdIfPossible();
    if (!shutdown)
    {
      synchronized (pendingStatusMessagesLock)
      {
        if (isDirectoryServer)
        {
          // Update the remote replication servers with our list
          // of connected LDAP servers
          pendingStatusMessages.enqueueTopoInfoToAllRSs();
        }
        // Warn our DSs that a RS or DS has quit (does not use this
        // handler as already removed from list)
        pendingStatusMessages.enqueueTopoInfoToAllDSsExcept(null);
      }
      statusAnalyzer.notifyPendingStatusMessage();
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
    if (logger.isTraceEnabled())
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
          if (logger.isTraceEnabled())
          {
            debug("mayResetGenerationId skip RS " + rsHandler
                + " that has different genId");
          }
        }
        else if (rsHandler.hasRemoteLDAPServers())
        {
          ldapServersConnectedInTheTopology = true;

          if (logger.isTraceEnabled())
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

      if (logger.isTraceEnabled())
      {
        debug("has ldap servers connected to it - will not reset generationId");
      }
    }

    if (!ldapServersConnectedInTheTopology
        && !generationIdSavedStatus
        && generationId != -1)
    {
      changeGenerationId(-1);
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
    LocalizableMessage message = ERR_DUPLICATE_REPLICATION_SERVER_ID.get(
        localReplicationServer.getMonitorInstanceName(),
        oldRsHandler.getServerAddressURL(), rsHandler.getServerAddressURL(),
        rsHandler.getServerId());
    throw new DirectoryException(ResultCode.OTHER, message);
  }

  /**
   * Creates and returns a cursor across this replication domain.
   * <p>
   * Client code must call {@link DBCursor#next()} to advance the cursor to the
   * next available record.
   * <p>
   * When the cursor is not used anymore, client code MUST call the
   * {@link DBCursor#close()} method to free the resources and locks used by the
   * cursor.
   *
   * @param startAfterServerState
   *          Starting point for the replicaDB cursors. If null, start from the
   *          oldest CSN
   * @return a non null {@link DBCursor} going from oldest to newest CSN
   * @throws ChangelogException
   *           If a database problem happened
   * @see ReplicationDomainDB#getCursorFrom(DN, ServerState, CursorOptions)
   */
  public DBCursor<UpdateMsg> getCursorFrom(ServerState startAfterServerState)
      throws ChangelogException
  {
    CursorOptions options = new CursorOptions(GREATER_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY);
    return domainDB.getCursorFrom(baseDN, startAfterServerState, options);
  }

  /**
   * Get the baseDN.
   *
   * @return Returns the baseDN.
   */
  public DN getBaseDN()
  {
    return baseDN;
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
    List<ServerHandler> servers = new ArrayList<>();

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
   * @param sender
   *          The server handler of the server that sent the message.
   */
  void process(RoutableMsg msg, ServerHandler sender)
  {
    if (msg.getDestination() == localReplicationServer.getServerId())
    {
      // Handle routable messages targeted at this RS.
      if (msg instanceof ErrorMsg)
      {
        ErrorMsg errorMsg = (ErrorMsg) msg;
        logger.error(ERR_ERROR_MSG_RECEIVED, errorMsg.getDetails());
      }
      else
      {
        replyWithUnroutableMsgType(sender, msg);
      }
    }
    else
    {
      // Forward message not destined for this RS.
      List<ServerHandler> servers = getDestinationServers(msg, sender);
      if (!servers.isEmpty())
      {
        forwardMsgToAllServers(msg, servers, sender);
      }
      else
      {
        replyWithUnreachablePeerMsg(sender, msg);
      }
    }
  }

  /**
   * Responds to a monitor request message.
   *
   * @param msg
   *          The monitor request message.
   * @param sender
   *          The DS/RS which sent the monitor request.
   */
  void processMonitorRequestMsg(MonitorRequestMsg msg, ServerHandler sender)
  {
    enqueueMonitorMsg(msg, sender);
  }

  /**
   * Responds to a monitor message.
   *
   * @param msg
   *          The monitor message
   * @param sender
   *          The DS/RS which sent the monitor.
   */
  void processMonitorMsg(MonitorMsg msg, ServerHandler sender)
  {
    domainMonitor.receiveMonitorDataResponse(msg, sender.getServerId());
  }

  private void replyWithUnroutableMsgType(ServerHandler msgEmitter,
      RoutableMsg msg)
  {
    String msgClassname = msg.getClass().getCanonicalName();
    logger.info(NOTE_ERR_ROUTING_TO_SERVER, msgClassname);

    LocalizableMessageBuilder mb = new LocalizableMessageBuilder();
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
    LocalizableMessageBuilder mb = new LocalizableMessageBuilder();
    mb.append(ERR_NO_REACHABLE_PEER_IN_THE_DOMAIN.get(baseDN, msg.getDestination()));
    mb.append(" In Replication Server=").append(
      this.localReplicationServer.getMonitorInstanceName());
    mb.append(" unroutable message =").append(msg.getClass().getSimpleName());
    mb.append(" Details:routing table is empty");
    final LocalizableMessage message = mb.toMessage();
    logger.error(message);

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
      logger.error(ERR_CHANGELOG_ERROR_SENDING_ERROR, this, ignored);
      stopServer(msgEmitter, false);
    }
  }

  private void forwardMsgToAllServers(RoutableMsg msg,
      List<ServerHandler> servers, ServerHandler sender)
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
        LocalizableMessageBuilder mb = new LocalizableMessageBuilder();
        mb.append(ERR_NO_REACHABLE_PEER_IN_THE_DOMAIN.get(baseDN, msg.getDestination()));
        mb.append(" unroutable message =").append(msg.getClass().getSimpleName());
        mb.append(" Details: ").append(ioe.getLocalizedMessage());
        final LocalizableMessage message = mb.toMessage();
        logger.error(message);

        ErrorMsg errMsg = new ErrorMsg(msg.getSenderID(), message);
        try
        {
          sender.send(errMsg);
        } catch (IOException ioe1)
        {
          // an error happened on the sender session trying to recover
          // from an error on the receiver session.
          // We don't have much solution left beside closing the sessions.
          stopServer(sender, false);
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
    returnMsg.setReplServerDbState(getLatestServerState());

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
  private MonitorMsg createLocalTopologyMonitorMsg(int sender, int destination)
  {
    final MonitorMsg monitorMsg = new MonitorMsg(sender, destination);
    monitorMsg.setReplServerDbState(getLatestServerState());

    // Add the server state for each connected DS and RS.
    for (DataServerHandler dsHandler : this.connectedDSs.values())
    {
      monitorMsg.setServerState(dsHandler.getServerId(),
          dsHandler.getServerState(), dsHandler.getApproxFirstMissingDate(),
          true);
    }

    for (ReplicationServerHandler rsHandler : this.connectedRSs.values())
    {
      monitorMsg.setServerState(rsHandler.getServerId(),
          rsHandler.getServerState(), rsHandler.getApproxFirstMissingDate(),
          false);
    }
    return monitorMsg;
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
    statusAnalyzer.shutdown();
  }

  /**
   * Returns the latest most current ServerState describing the newest CSNs for
   * each server in this domain.
   *
   * @return The ServerState describing the newest CSNs for each server in in
   *         this domain.
   */
  public ServerState getLatestServerState()
  {
    return domainDB.getDomainNewestCSNs(baseDN);
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    return "ReplicationServerDomain " + baseDN;
  }



  /**
   * Creates a TopologyMsg filled with information to be sent to a remote RS.
   * We send remote RS the info of every DS that are directly connected to us
   * plus our own info as RS.
   * @return A suitable TopologyMsg PDU to be sent to a peer RS
   */
  public TopologyMsg createTopologyMsgForRS()
  {
    List<DSInfo> dsInfos = new ArrayList<>();
    for (DataServerHandler dsHandler : connectedDSs.values())
    {
      dsInfos.add(dsHandler.toDSInfo());
    }

    // Create info for the local RS
    List<RSInfo> rsInfos = newArrayList(toRSInfo(localReplicationServer, generationId));

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
    List<DSInfo> dsInfos = new ArrayList<>();
    for (DataServerHandler dsHandler : connectedDSs.values())
    {
      if (dsHandler.getServerId() == destDsId)
      {
        continue;
      }
      dsInfos.add(dsHandler.toDSInfo());
    }


    List<RSInfo> rsInfos = new ArrayList<>();
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
   * @return The old generation id
   */
  public long changeGenerationId(long generationId)
  {
    synchronized (generationIDLock)
    {
      long oldGenerationId = this.generationId;

      if (this.generationId != generationId)
      {
        clearDbs();

        this.generationId = generationId;
        this.generationIdSavedStatus = false;
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
    if (logger.isTraceEnabled())
    {
      debug("Receiving ResetGenerationIdMsg from "
          + senderHandler.getServerId() + ":\n" + genIdMsg);
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
      final long newGenId = genIdMsg.getGenerationId();
      if (newGenId != this.generationId)
      {
        changeGenerationId(newGenId);
      }
      else
      {
        // Order to take a gen id we already have, just ignore
        if (logger.isTraceEnabled())
        {
          debug("Reset generation id requested but generationId was already "
              + this.generationId + ":\n" + genIdMsg);
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
          logger.error(ERR_EXCEPTION_FORWARDING_RESET_GEN_ID, baseDN, e.getMessage());
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
          logger.error(ERR_EXCEPTION_CHANGING_STATUS_AFTER_RESET_GEN_ID, baseDN,
              dsHandler.getServerId(), e.getMessage());
        }
      }

      // Update every peers (RS/DS) with potential topology changes (status
      // change). Rather than doing that each time a DS has a status change
      // (consecutive to reset gen id message), we prefer advertising once for
      // all after changes (less packet sent), here at the end of the reset msg
      // treatment.
      sendTopoInfoToAll();

      logger.info(NOTE_RESET_GENERATION_ID, baseDN, newGenId);
    }
    catch(Exception e)
    {
      logger.error(LocalizableMessage.raw(stackTraceToSingleLineString(e)));
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
    if (logger.isTraceEnabled())
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

      enqueueTopoInfoToAllExcept(senderHandler);

      logger.info(NOTE_DIRECTORY_SERVER_CHANGED_STATUS,
          senderHandler.getServerId(), baseDN, newStatus);
    }
    catch(Exception e)
    {
      logger.error(LocalizableMessage.raw(stackTraceToSingleLineString(e)));
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
  private boolean changeStatus(DataServerHandler dsHandler,
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
      if (logger.isTraceEnabled())
      {
        logger.trace("Status analyzer for domain " + baseDN
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
        logger.error(ERR_EXCEPTION_CHANGING_STATUS_FROM_STATUS_ANALYZER,
            baseDN, dsHandler.getServerId(), e.getMessage());
      }

      if (newStatus == ServerStatus.INVALID_STATUS || newStatus == oldStatus)
      {
        // Change was impossible or already occurred (see StatusAnalyzer
        // comments)
        return false;
      }

      enqueueTopoInfoToAllExcept(dsHandler);
    }
    catch (Exception e)
    {
      logger.error(LocalizableMessage.raw(stackTraceToSingleLineString(e)));
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
    enqueueTopoInfoToAllExcept(null);
  }

  /**
   * Update every peers (RS/DS) with topology changes but one DS.
   *
   * @param dsHandler
   *          if not null, the topology message will not be sent to this DS
   */
  private void enqueueTopoInfoToAllExcept(DataServerHandler dsHandler)
  {
    synchronized (pendingStatusMessagesLock)
    {
      pendingStatusMessages.enqueueTopoInfoToAllDSsExcept(dsHandler);
      pendingStatusMessages.enqueueTopoInfoToAllRSs();
    }
    statusAnalyzer.notifyPendingStatusMessage();
  }

  /**
   * Clears the Db associated with that domain.
   */
  private void clearDbs()
  {
    try
    {
      domainDB.removeDomain(baseDN);
    }
    catch (ChangelogException e)
    {
      logger.error(ERR_ERROR_CLEARING_DB, baseDN, e.getMessage(), e);
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
    if (logger.isTraceEnabled())
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

    if (logger.isTraceEnabled())
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
    if (logger.isTraceEnabled())
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
        LocalizableMessage message = WARN_BAD_GENERATION_ID_FROM_RS.get(rsHandler.getServerId(),
            rsHandler.session.getReadableRemoteAddress(), rsHandler.getGenerationId(),
            baseDN, getLocalRSServerId(), generationId);
        logger.warn(message);

        ErrorMsg errorMsg = new ErrorMsg(getLocalRSServerId(),
            rsHandler.getServerId(), message);
        rsHandler.send(errorMsg);
      }

      /*
       * Sends the currently known topology information to every connected
       * DS we have.
       */
      synchronized (pendingStatusMessagesLock)
      {
        pendingStatusMessages.enqueueTopoInfoToAllDSsExcept(null);
      }
      statusAnalyzer.notifyPendingStatusMessage();
    }
    catch(Exception e)
    {
      logger.error(LocalizableMessage.raw(stackTraceToSingleLineString(e)));
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
    if (thread != null && monitoringPublisher.compareAndSet(thread, null))
    {
      thread.shutdown();
      thread.waitForShutdown();
    }
  }

  /** {@inheritDoc} */
  @Override
  public void initializeMonitorProvider(MonitorProviderCfg configuraiton)
  {
    // Nothing to do for now
  }

  /** {@inheritDoc} */
  @Override
  public String getMonitorInstanceName()
  {
    return "Replication server RS(" + localReplicationServer.getServerId()
        + ") " + localReplicationServer.getServerURL() + ",cn="
        + baseDN.toString().replace(',', '_').replace('=', '_')
        + ",cn=Replication";
  }

  /** {@inheritDoc} */
  @Override
  public List<Attribute> getMonitorData()
  {
    // publish the server id and the port number.
    List<Attribute> attributes = new ArrayList<>();
    attributes.add(Attributes.create("replication-server-id",
        String.valueOf(localReplicationServer.getServerId())));
    attributes.add(Attributes.create("replication-server-port",
        String.valueOf(localReplicationServer.getReplicationPort())));
    attributes.add(Attributes.create("domain-name",
        baseDN.toString()));
    attributes.add(Attributes.create("generation-id",
        baseDN + " " + generationId));

    // Missing changes
    long missingChanges = getDomainMonitorData().getMissingChangesRS(
        localReplicationServer.getServerId());
    attributes.add(Attributes.create("missing-changes",
        String.valueOf(missingChanges)));

    return attributes;
  }

  /**
   * Returns the oldest known state for the domain, made of the oldest CSN
   * stored for each serverId.
   * <p>
   * Note: Because the replication changelogDB trimming always keep one change
   * whatever its date, the CSN contained in the returned state can be very old.
   *
   * @return the start state of the domain.
   */
  public ServerState getOldestState()
  {
    return domainDB.getDomainOldestCSNs(baseDN);
  }

  private void sendTopologyMsg(String type, ServerHandler handler, TopologyMsg msg)
  {
    for (int i = 1; i <= 2; i++)
    {
      if (!handler.shuttingDown()
          && handler.getStatus() != ServerStatus.NOT_CONNECTED_STATUS)
      {
        try
        {
          handler.sendTopoInfo(msg);
          break;
        }
        catch (IOException e)
        {
          if (i == 2)
          {
            logger.error(ERR_EXCEPTION_SENDING_TOPO_INFO,
                baseDN, type, handler.getServerId(), e.getMessage());
          }
        }
      }
      sleep(100);
    }
  }



  /**
   * Processes a ChangeTimeHeartbeatMsg received, by storing the CSN (timestamp)
   * value received, and forwarding the message to the other RSes.
   * @param senderHandler The handler for the server that sent the heartbeat.
   * @param msg The message to process.
   * @throws DirectoryException
   *           if a problem occurs
   */
  void processChangeTimeHeartbeatMsg(ServerHandler senderHandler,
      ChangeTimeHeartbeatMsg msg) throws DirectoryException
  {
    try
    {
      domainDB.replicaHeartbeat(baseDN, msg.getCSN());
    }
    catch (ChangelogException e)
    {
      throw new DirectoryException(ResultCode.OPERATIONS_ERROR, e
          .getMessageObject(), e);
    }

    if (senderHandler.isDataServer())
    {
      /*
       * If we are the first replication server warned, then forward the message
       * to the remote replication servers.
       */
      synchronized (pendingStatusMessagesLock)
      {
        pendingStatusMessages.enqueueChangeTimeHeartbeatMsg(msg);
      }
      statusAnalyzer.notifyPendingStatusMessage();
    }
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
    startMonitoringPublisher();

    // connected with new DS: store handler.
    connectedDSs.put(dsHandler.getServerId(), dsHandler);

    // Tell peer RSs and DSs a new DS just connected to us
    // No need to re-send TopologyMsg to this just new DS
    enqueueTopoInfoToAllExcept(dsHandler);
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
    logger.trace("In ReplicationServerDomain serverId="
        + localReplicationServer.getServerId() + " for baseDN=" + baseDN
        + " and port=" + localReplicationServer.getReplicationPort()
        + ": " + message);
  }



  /**
   * Go through each connected DS, get the number of pending changes we have for
   * it and change status accordingly if threshold value is crossed/uncrossed.
   */
  void checkDSDegradedStatus()
  {
    final int degradedStatusThreshold = localReplicationServer
        .getDegradedStatusThreshold();
    // Threshold value = 0 means no status analyzer (no degrading system)
    // we should not have that as the status analyzer thread should not be
    // created if this is the case, but for sanity purpose, we add this
    // test
    if (degradedStatusThreshold > 0)
    {
      for (DataServerHandler serverHandler : connectedDSs.values())
      {
        // Get number of pending changes for this server
        final int nChanges = serverHandler.getRcvMsgQueueSize();
        if (logger.isTraceEnabled())
        {
          logger.trace("In RS " + getLocalRSServerId() + ", for baseDN="
              + getBaseDN() + ": " + "Status analyzer: DS "
              + serverHandler.getServerId() + " has " + nChanges
              + " message(s) in writer queue.");
        }

        // Check status to know if it is relevant to change the status. Do not
        // take RSD lock to test. If we attempt to change the status whereas
        // the current status does allow it, this will be noticed by
        // the changeStatusFromStatusAnalyzer() method. This allows to take the
        // lock roughly only when needed versus every sleep time timeout.
        if (nChanges >= degradedStatusThreshold)
        {
          if (serverHandler.getStatus() == NORMAL_STATUS
              && changeStatus(serverHandler, TO_DEGRADED_STATUS_EVENT))
          {
            break; // Interrupted.
          }
        }
        else
        {
          if (serverHandler.getStatus() == DEGRADED_STATUS
              && changeStatus(serverHandler, TO_NORMAL_STATUS_EVENT))
          {
            break; // Interrupted.
          }
        }
      }
    }
  }



  /**
   * Sends any enqueued status messages to the rest of the topology.
   */
  void sendPendingStatusMessages()
  {
    /*
     * Take a snapshot of pending status notifications in order to avoid holding
     * the broadcast lock for too long. In addition, clear the notifications so
     * that they are not resent the next time.
     */
    final PendingStatusMessages savedState;
    synchronized (pendingStatusMessagesLock)
    {
      savedState = pendingStatusMessages;
      pendingStatusMessages = new PendingStatusMessages();
    }
    sendPendingChangeTimeHeartbeatMsgs(savedState);
    sendPendingTopologyMsgs(savedState);
    sendPendingMonitorMsgs(savedState);
  }



  private void sendPendingMonitorMsgs(final PendingStatusMessages pendingMsgs)
  {
    for (Entry<Integer, MonitorMsg> msg : pendingMsgs.pendingDSMonitorMsgs
        .entrySet())
    {
      ServerHandler ds = connectedDSs.get(msg.getKey());
      if (ds != null)
      {
        try
        {
          ds.send(msg.getValue());
        }
        catch (IOException e)
        {
          // Ignore: connection closed.
        }
      }
    }
    for (Entry<Integer, MonitorMsg> msg : pendingMsgs.pendingRSMonitorMsgs
        .entrySet())
    {
      ServerHandler rs = connectedRSs.get(msg.getKey());
      if (rs != null)
      {
        try
        {
          rs.send(msg.getValue());
        }
        catch (IOException e)
        {
          // We log the error. The requestor will detect a timeout or
          // any other failure on the connection.

          // FIXME: why do we log for RSs but not DSs?
          logger.traceException(e);
          logger.error(ERR_CHANGELOG_ERROR_SENDING_MSG, msg.getValue().getDestination());
        }
      }
    }
  }



  private void sendPendingChangeTimeHeartbeatMsgs(PendingStatusMessages pendingMsgs)
  {
    for (ChangeTimeHeartbeatMsg pendingHeartbeat : pendingMsgs.pendingHeartbeats.values())
    {
      for (ReplicationServerHandler rsHandler : connectedRSs.values())
      {
        try
        {
          if (rsHandler.getProtocolVersion() >= REPLICATION_PROTOCOL_V3)
          {
            rsHandler.send(pendingHeartbeat);
          }
        }
        catch (IOException e)
        {
          logger.traceException(e);
          logger.error(ERR_CHANGELOG_ERROR_SENDING_MSG, "Replication Server "
              + localReplicationServer.getReplicationPort() + " " + baseDN
              + " " + localReplicationServer.getServerId());
          stopServer(rsHandler, false);
        }
      }
    }
  }



  private void sendPendingTopologyMsgs(PendingStatusMessages pendingMsgs)
  {
    if (pendingMsgs.sendDSTopologyMsg)
    {
      for (ServerHandler handler : connectedDSs.values())
      {
        if (handler.getServerId() != pendingMsgs.excludedDSForTopologyMsg)
        {
          final TopologyMsg topoMsg = createTopologyMsgForDS(handler
              .getServerId());
          sendTopologyMsg("directory", handler, topoMsg);
        }
      }
    }

    if (pendingMsgs.sendRSTopologyMsg && !connectedRSs.isEmpty())
    {
      final TopologyMsg topoMsg = createTopologyMsgForRS();
      for (ServerHandler handler : connectedRSs.values())
      {
        sendTopologyMsg("replication", handler, topoMsg);
      }
    }
  }



  private void enqueueMonitorMsg(MonitorRequestMsg msg, ServerHandler sender)
  {
    /*
     * If the request comes from a Directory Server we need to build the full
     * list of all servers in the topology and send back a MonitorMsg with the
     * full list of all the servers in the topology.
     */
    if (sender.isDataServer())
    {
      MonitorMsg monitorMsg = createGlobalTopologyMonitorMsg(
          msg.getDestination(), msg.getSenderID(),
          domainMonitor.getMonitorData());
      synchronized (pendingStatusMessagesLock)
      {
        pendingStatusMessages.enqueueDSMonitorMsg(sender.getServerId(),
            monitorMsg);
      }
    }
    else
    {
      MonitorMsg monitorMsg = createLocalTopologyMonitorMsg(
          msg.getDestination(), msg.getSenderID());
      synchronized (pendingStatusMessagesLock)
      {
        pendingStatusMessages.enqueueRSMonitorMsg(sender.getServerId(),
            monitorMsg);
      }
    }
    statusAnalyzer.notifyPendingStatusMessage();
  }
}
