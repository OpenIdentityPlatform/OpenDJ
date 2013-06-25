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

import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
import org.opends.server.types.*;
import org.opends.server.util.TimeThread;

import com.sleepycat.je.DatabaseException;

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
 *
 * it runs a thread that is responsible for saving the messages
 * received to the disk and for trimming them
 * Decision to trim can be based on disk space or age of the message
 */
public class ReplicationServerDomain extends MonitorProvider<MonitorProviderCfg>
{
  private final String baseDn;
  // The Status analyzer that periodically verifies if the connected DSs are
  // late or not
  private StatusAnalyzer statusAnalyzer = null;

  // The monitoring publisher that periodically sends monitoring messages to the
  // topology
  private MonitoringPublisher monitoringPublisher = null;

  /*
   * The following map contains one balanced tree for each replica ID
   * to which we are currently publishing
   * the first update in the balanced tree is the next change that we
   * must push to this particular server
   *
   * We add new TreeSet in the HashMap when a new server register
   * to this replication server.
   *
   */
  private final Map<Integer, DataServerHandler> directoryServers =
    new ConcurrentHashMap<Integer, DataServerHandler>();

  /*
   * This map contains one ServerHandler for each replication servers
   * with which we are connected (so normally all the replication servers)
   * the first update in the balanced tree is the next change that we
   * must push to this particular server
   *
   * We add new TreeSet in the HashMap when a new replication server register
   * to this replication server.
   */
  private final Map<Integer, ReplicationServerHandler> replicationServers =
    new ConcurrentHashMap<Integer, ReplicationServerHandler>();

  private final ConcurrentLinkedQueue<MessageHandler> otherHandlers =
    new ConcurrentLinkedQueue<MessageHandler>();

  /*
   * This map contains the List of updates received from each
   * LDAP server
   */
  private final Map<Integer, DbHandler> sourceDbHandlers =
    new ConcurrentHashMap<Integer, DbHandler>();
  private ReplicationServer replicationServer;

  // GenerationId management
  private volatile long generationId = -1;
  private boolean generationIdSavedStatus = false;

  // The tracer object for the debug logger.
  private static final DebugTracer TRACER = getTracer();

  // Monitor data management
  /**
   * The monitor data consolidated over the topology.
   */
  private volatile MonitorData monitorData = new MonitorData();

  // This lock guards against multiple concurrent monitor data recalculation.
  private final Object pendingMonitorLock = new Object();

  // Guarded by pendingMonitorLock.
  private long monitorDataLastBuildDate = 0;

  // The set of replication servers which are already known to be slow to send
  // monitor data.
  //
  // Guarded by pendingMonitorLock.
  private final Set<Integer> monitorDataLateServers = new HashSet<Integer>();

  // This lock serializes updates to the pending monitor data.
  private final Object pendingMonitorDataLock = new Object();

  // Monitor data which is currently being calculated.
  //
  // Guarded by pendingMonitorDataLock.
  private MonitorData pendingMonitorData;

  // A set containing the IDs of servers from which we are currently expecting
  // monitor responses. When a response is received from a server we remove the
  // ID from this table, and count down the latch if the ID was in the table.
  //
  // Guarded by pendingMonitorDataLock.
  private final Set<Integer> pendingMonitorDataServerIDs =
    new HashSet<Integer>();

  // This latch is non-null and is used in order to count incoming responses as
  // they arrive. Since incoming response may arrive at any time, even when
  // there is no pending monitor request, access to the latch must be guarded.
  //
  // Guarded by pendingMonitorDataLock.
  private CountDownLatch pendingMonitorDataLatch = null;

  // TODO: Remote monitor data cache lifetime is 500ms/should be configurable
  private final long monitorDataLifeTime = 500;



  /**
   * The needed info for each received assured update message we are waiting
   * acks for.
   * Key: a change number matching a received update message which requested
   * assured mode usage (either safe read or safe data mode)
   * Value: The object holding every info needed about the already received acks
   * as well as the acks to be received.
   * For more details, see ExpectedAcksInfo and its sub classes javadoc.
   */
  private final ConcurrentHashMap<ChangeNumber, ExpectedAcksInfo> waitingAcks =
    new ConcurrentHashMap<ChangeNumber, ExpectedAcksInfo>();

  // The timer used to run the timeout code (timer tasks) for the assured update
  // messages we are waiting acks for.
  private Timer assuredTimeoutTimer = null;
  // Counter used to purge the timer tasks references in assuredTimeoutTimer,
  // every n number of treated assured messages
  private int assuredTimeoutTimerPurgeCounter = 0;

  private ServerState ctHeartbeatState = null;

  /**
   * Creates a new ReplicationServerDomain associated to the DN baseDn.
   *
   * @param baseDn The baseDn associated to the ReplicationServerDomain.
   * @param replicationServer the ReplicationServer that created this
   *                          replicationServer cache.
   */
  public ReplicationServerDomain(
      String baseDn, ReplicationServer replicationServer)
  {
    this.baseDn = baseDn;
    this.replicationServer = replicationServer;
    this.assuredTimeoutTimer = new Timer("Replication server RS("
        + replicationServer.getServerId()
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

    ChangeNumber cn = update.getChangeNumber();
    int id = cn.getServerId();
    sourceHandler.updateServerState(update);
    sourceHandler.incrementInCount();

    if (generationId < 0)
    {
      generationId = sourceHandler.getGenerationId();
    }

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
      if (sourceHandler.getProtocolVersion() >=
        ProtocolVersion.REPLICATION_PROTOCOL_V2)
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
            Integer.toString(replicationServer.getServerId()),
            assuredMode.toString(), baseDn, update.toString());
          logError(errorMsg);
          assuredMessage = false;
        }
      } else
      {
        assuredMessage = false;
      }
    }

    // look for the dbHandler that is responsible for the LDAP server which
    // generated the change.
    DbHandler dbHandler;
    synchronized (sourceDbHandlers)
    {
      dbHandler = sourceDbHandlers.get(id);
      if (dbHandler == null)
      {
        try
        {
          dbHandler = replicationServer.newDbHandler(id, baseDn);
          generationIdSavedStatus = true;
        } catch (DatabaseException e)
        {
          /*
           * Because of database problem we can't save any more changes
           * from at least one LDAP server.
           * This replicationServer therefore can't do it's job properly anymore
           * and needs to close all its connections and shutdown itself.
           */
          MessageBuilder mb = new MessageBuilder();
          mb.append(ERR_CHANGELOG_SHUTDOWN_DATABASE_ERROR.get());
          mb.append(stackTraceToSingleLineString(e));
          logError(mb.toMessage());
          replicationServer.shutdown();
          return;
        }
        sourceDbHandlers.put(id, dbHandler);
      }
    }

    // Publish the messages to the source handler
    dbHandler.add(update);

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
        waitingAcks.put(cn, preparedAssuredInfo.expectedAcksInfo);

        // Arm timer for this assured update message (wait for acks until it
        // times out)
        AssuredTimeoutTask assuredTimeoutTask = new AssuredTimeoutTask(cn);
        assuredTimeoutTimer.schedule(assuredTimeoutTask,
          replicationServer.getAssuredTimeout());
        // Purge timer every 100 treated messages
        assuredTimeoutTimerPurgeCounter++;
        if ((assuredTimeoutTimerPurgeCounter % 100) == 0)
          assuredTimeoutTimer.purge();
      }
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

    /*
     * Push the message to the replication servers
     */
    if (sourceHandler.isDataServer())
    {
      for (ReplicationServerHandler handler : replicationServers.values())
      {
        /**
         * Ignore updates to RS with bad gen id
         * (no system managed status for a RS)
         */
        if ( (generationId>0) && (generationId != handler.getGenerationId()) )
        {
          if (debugEnabled())
            TRACER.debugInfo("In " + "Replication Server " +
              replicationServer.getReplicationPort() + " " +
              baseDn + " " + replicationServer.getServerId() +
              " for dn " + baseDn + ", update " +
              update.getChangeNumber().toString() +
              " will not be sent to replication server " +
              Integer.toString(handler.getServerId()) + " with generation id " +
              Long.toString(handler.getGenerationId()) +
              " different from local " +
              "generation id " + Long.toString(generationId));

          continue;
        }

        if (assuredMessage)
        {
          // Assured mode: post an assured or not assured matching update
          // message according to what has been computed for the destination
          // server
          if ((expectedServers != null) && expectedServers.contains(handler.
            getServerId()))
          {
            handler.add(update, sourceHandler);
          } else
          {
            if (notAssuredUpdate == null)
            {
              notAssuredUpdate = new NotAssuredUpdateMsg(update);
            }
            handler.add(notAssuredUpdate, sourceHandler);
          }
        } else
        {
          handler.add(update, sourceHandler);
        }
      }
    }

    /*
     * Push the message to the LDAP servers
     */
    for (DataServerHandler handler : directoryServers.values())
    {
      // Don't forward the change to the server that just sent it
      if (handler == sourceHandler)
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
      ServerStatus dsStatus = handler.getStatus();
      if ( (dsStatus == ServerStatus.BAD_GEN_ID_STATUS) ||
        (dsStatus == ServerStatus.FULL_UPDATE_STATUS) )
      {
        if (debugEnabled())
        {
          if (dsStatus == ServerStatus.BAD_GEN_ID_STATUS)
            TRACER.debugInfo("In " + this +
              " for dn " + baseDn + ", update " +
              update.getChangeNumber().toString() +
              " will not be sent to directory server " +
              Integer.toString(handler.getServerId()) + " with generation id " +
              Long.toString(handler.getGenerationId()) +
              " different from local " +
              "generation id " + Long.toString(generationId));
          if (dsStatus == ServerStatus.FULL_UPDATE_STATUS)
            TRACER.debugInfo("In RS " +
              replicationServer.getServerId() +
              " for dn " + baseDn + ", update " +
              update.getChangeNumber().toString() +
              " will not be sent to directory server " +
              Integer.toString(handler.getServerId()) +
              " as it is in full update");
        }

        continue;
      }

      if (assuredMessage)
      {
        // Assured mode: post an assured or not assured matching update
        // message according to what has been computed for the destination
        // server
        if ((expectedServers != null) && expectedServers.contains(handler.
          getServerId()))
        {
          handler.add(update, sourceHandler);
        } else
        {
          if (notAssuredUpdate == null)
          {
            notAssuredUpdate = new NotAssuredUpdateMsg(update);
          }
          handler.add(notAssuredUpdate, sourceHandler);
        }
      } else
      {
        handler.add(update, sourceHandler);
      }
    }

    // Push the message to the other subscribing handlers
    for (MessageHandler handler : otherHandlers) {
      handler.add(update, sourceHandler);
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
       *
       */
      public List<Integer> expectedServers = null;

      /**
       * The constructed ExpectedAcksInfo object to be used when acks will be
       * received. Null if expectedServers is null.
       */
      public ExpectedAcksInfo expectedAcksInfo = null;
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
    ChangeNumber cn = update.getChangeNumber();
    byte groupId = replicationServer.getGroupId();
    byte sourceGroupId = sourceHandler.getGroupId();
    List<Integer> expectedServers = new ArrayList<Integer>();
    List<Integer> wrongStatusServers = new ArrayList<Integer>();

    if (sourceGroupId == groupId)
      // Assured feature does not cross different group ids
    {
      if (sourceHandler.isDataServer())
      {
        // Look for RS eligible for assured
        for (ReplicationServerHandler handler : replicationServers.values())
        {
          if (handler.getGroupId() == groupId)
            // No ack expected from a RS with different group id
          {
            if ((generationId > 0) &&
              (generationId == handler.getGenerationId()))
              // No ack expected from a RS with bad gen id
            {
              expectedServers.add(handler.getServerId());
            }
          }
        }
      }

      // Look for DS eligible for assured
      for (DataServerHandler handler : directoryServers.values())
      {
        // Don't forward the change to the server that just sent it
        if (handler == sourceHandler)
        {
          continue;
        }
        if (handler.getGroupId() == groupId)
          // No ack expected from a DS with different group id
        {
          ServerStatus serverStatus = handler.getStatus();
          if (serverStatus == ServerStatus.NORMAL_STATUS)
          {
            expectedServers.add(handler.getServerId());
          } else
            // No ack expected from a DS with wrong status
          {
            if (serverStatus == ServerStatus.DEGRADED_STATUS)
            {
              wrongStatusServers.add(handler.getServerId());
            }
            /**
             * else
             * BAD_GEN_ID_STATUS or FULL_UPDATE_STATUS:
             * We do not want this to be reported as an error to the update
             * maker -> no pollution or potential misunderstanding when
             * reading logs or monitoring and it was just administration (for
             * instance new server is being configured in topo: it goes in bad
             * gen then then full full update).
             */
          }
        }
      }
    }

    // Return computed structures
    PreparedAssuredInfo preparedAssuredInfo = new PreparedAssuredInfo();
    if (expectedServers.size() > 0)
    {
      // Some other acks to wait for
      preparedAssuredInfo.expectedAcksInfo = new SafeReadExpectedAcksInfo(cn,
        sourceHandler, expectedServers, wrongStatusServers);
      preparedAssuredInfo.expectedServers = expectedServers;
    }

    if (preparedAssuredInfo.expectedServers == null)
    {
      // No eligible servers found, send the ack immediately
      sourceHandler.send(new AckMsg(cn));
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
    ChangeNumber cn = update.getChangeNumber();
    boolean interestedInAcks = false;
    byte safeDataLevel = update.getSafeDataLevel();
    byte groupId = replicationServer.getGroupId();
    byte sourceGroupId = sourceHandler.getGroupId();
    if (safeDataLevel < (byte) 1)
    {
      // Should never happen
      Message errorMsg = ERR_UNKNOWN_ASSURED_SAFE_DATA_LEVEL.get(
        Integer.toString(replicationServer.getServerId()),
        Byte.toString(safeDataLevel), baseDn, update.toString());
      logError(errorMsg);
    } else if (sourceGroupId != groupId)
    {
      // Assured feature does not cross different group IDS
    } else
    {
      if ((generationId > 0) &&
        (generationId == sourceHandler.getGenerationId()))
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
            sourceHandler.send(new AckMsg(cn));
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
            sourceHandler.send(new AckMsg(cn));
          }
        }
      }
    }

    List<Integer> expectedServers = new ArrayList<Integer>();
    if (interestedInAcks)
    {
      if (sourceHandler.isDataServer())
      {
        // Look for RS eligible for assured
        for (ReplicationServerHandler handler : replicationServers.values())
        {
          if (handler.getGroupId() == groupId)
            // No ack expected from a RS with different group id
          {
            if ((generationId > 0) &&
              (generationId == handler.getGenerationId()))
              // No ack expected from a RS with bad gen id
            {
              expectedServers.add(handler.getServerId());
            }
          }
        }
      }
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
        byte finalSdl = ((nExpectedServers >= neededAdditionalServers) ?
          (byte)sdl : // Keep level as it was
          (byte)(nExpectedServers+1)); // Change level to match what's available
        preparedAssuredInfo.expectedAcksInfo = new SafeDataExpectedAcksInfo(cn,
          sourceHandler, finalSdl, expectedServers);
        preparedAssuredInfo.expectedServers = expectedServers;
      } else
      {
        // level > 1 and source is a DS but no eligible servers found, send the
        // ack immediately
        sourceHandler.send(new AckMsg(cn));
      }
    }

    return preparedAssuredInfo;
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
    ChangeNumber cn = ack.getChangeNumber();
    ExpectedAcksInfo expectedAcksInfo = waitingAcks.get(cn);

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
          waitingAcks.remove(cn);
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
              Integer.toString(replicationServer.getServerId()),
              Integer.toString(origServer.getServerId()),
              cn.toString(), baseDn));
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
    /* Else the timeout occurred for the update matching this change number
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
    private ChangeNumber cn = null;

    /**
     * Constructor for the timer task.
     * @param cn The changenumber of the assured update we are waiting acks for
     */
    public AssuredTimeoutTask(ChangeNumber cn)
    {
      this.cn = cn;
    }

    /**
     * Run when the assured timeout for an assured update message we are waiting
     * acks for occurs.
     */
    public void run()
    {
      ExpectedAcksInfo expectedAcksInfo = waitingAcks.get(cn);

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
          waitingAcks.remove(cn);
          // Create the timeout ack and send him to the server the assured
          // update message came from
          AckMsg finalAck = expectedAcksInfo.createAck(true);
          ServerHandler origServer = expectedAcksInfo.getRequesterServer();
          if (debugEnabled())
            TRACER.debugInfo(
              "In RS " + Integer.toString(replicationServer.getServerId()) +
              " for " + baseDn +
              ", sending timeout for assured update with change " + " number " +
              cn.toString() + " to server id " +
              Integer.toString(origServer.getServerId()));
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
                Integer.toString(replicationServer.getServerId()),
                Integer.toString(origServer.getServerId()),
                cn.toString(), baseDn));
            mb.append(stackTraceToSingleLineString(e));
            logError(mb.toMessage());
            stopServer(origServer, false);
          }
          // Increment assured counters
          boolean safeRead =
              (expectedAcksInfo instanceof SafeReadExpectedAcksInfo);
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
            ServerHandler expectedServerInTimeout =
              directoryServers.get(serverId);
            if (expectedServerInTimeout != null)
            {
              // Was a DS
              if (safeRead)
              {
                expectedServerInTimeout.incrementAssuredSrSentUpdatesTimeout();
              } else
              {
                // No SD update sent to a DS (meaningless)
              }
            } else
            {
              expectedServerInTimeout =
                replicationServers.get(serverId);
              if (expectedServerInTimeout != null)
              {
                // Was a RS
                if (safeRead)
                {
                  expectedServerInTimeout.
                    incrementAssuredSrSentUpdatesTimeout();
                } else
                {
                  expectedServerInTimeout.
                    incrementAssuredSdSentUpdatesTimeout();
                }
              }
              /* else server disappeared ? Let's forget about it. */
            }
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
   * @param replServers the replication servers for which
   * we want to stop operations
   */
  public void stopReplicationServers(Collection<String> replServers)
  {
    for (ReplicationServerHandler handler : replicationServers.values())
    {
      if (replServers.contains(handler.getServerAddressURL()))
        stopServer(handler, false);
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
    // Close session with other replication servers
    for (ReplicationServerHandler serverHandler : replicationServers.values())
    {
      stopServer(serverHandler, shutdown);
    }

    // Close session with other LDAP servers
    for (DataServerHandler serverHandler : directoryServers.values())
    {
      stopServer(serverHandler, shutdown);
    }
  }

  /**
   * Checks that a DS is not connected with same id.
   *
   * @param handler the DS we want to check
   * @return true if this is not a duplicate server
   */
  public boolean checkForDuplicateDS(DataServerHandler handler)
  {
    if (directoryServers.containsKey(handler.getServerId()))
    {
      // looks like two connected LDAP servers have the same serverId
      Message message = ERR_DUPLICATE_SERVER_ID.get(
          replicationServer.getMonitorInstanceName(),
          directoryServers.get(handler.getServerId()).toString(),
          handler.toString(), handler.getServerId());
      logError(message);
      return false;
    }
    return true;
  }

  /**
   * Stop operations with a given server.
   *
   * @param handler the server for which we want to stop operations.
   * @param shutdown A boolean indicating if the stop is due to a
   *                 shutdown condition.
   */
  public void stopServer(ServerHandler handler, boolean shutdown)
  {
      if (debugEnabled())
        TRACER.debugInfo(
            "In " + this.replicationServer.getMonitorInstanceName() +
            " domain=" + this + " stopServer() on the server handler " +
            handler.getMonitorInstanceName());
    /*
     * We must prevent deadlock on replication server domain lock, when for
     * instance this code is called from dying ServerReader but also dying
     * ServerWriter at the same time, or from a thread that wants to shut down
     * the handler. So use a thread safe flag to know if the job must be done
     * or not (is already being processed or not).
     */
    if (!handler.engageShutdown())
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
        if ( (directoryServers.size() + replicationServers.size() )== 1)
        {
          if (debugEnabled())
            TRACER.debugInfo("In " +
              replicationServer.getMonitorInstanceName() +
              " remote server " + handler.getMonitorInstanceName() + " is " +
              "the last RS/DS to be stopped: stopping monitoring publisher");
          stopMonitoringPublisher();
        }

        if (handler.isReplicationServer())
        {
          if (replicationServers.containsKey(handler.getServerId()))
          {
            unregisterServerHandler(handler);
            handler.shutdown();

            // Check if generation id has to be reset
            mayResetGenerationId();
            if (!shutdown)
            {
              // Warn our DSs that a RS or DS has quit (does not use this
              // handler as already removed from list)
              buildAndSendTopoInfoToDSs(null);
            }
          }
        } else if (directoryServers.containsKey(handler.getServerId()))
        {
          // If this is the last DS for the domain,
          // shutdown the status analyzer
          if (directoryServers.size() == 1)
          {
            if (debugEnabled())
              TRACER.debugInfo("In " +
                replicationServer.getMonitorInstanceName() +
                " remote server " + handler.getMonitorInstanceName() +
                " is the last DS to be stopped: stopping status analyzer");
            stopStatusAnalyzer();
          }

          unregisterServerHandler(handler);
          handler.shutdown();

          // Check if generation id has to be reset
          mayResetGenerationId();
          if (!shutdown)
          {
            // Update the remote replication servers with our list
            // of connected LDAP servers
            buildAndSendTopoInfoToRSs();
            // Warn our DSs that a RS or DS has quit (does not use this
            // handler as already removed from list)
            buildAndSendTopoInfoToDSs(null);
          }
        } else if (otherHandlers.contains(handler))
        {
          unRegisterHandler(handler);
          handler.shutdown();
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

  /**
   * Stop the handler.
   * @param handler The handler to stop.
   */
  public void stopServer(MessageHandler handler)
  {
    if (debugEnabled())
      TRACER.debugInfo(
          "In " + this.replicationServer.getMonitorInstanceName()
          + " domain=" + this + " stopServer() on the message handler "
          + handler.getMonitorInstanceName());
    /*
     * We must prevent deadlock on replication server domain lock, when for
     * instance this code is called from dying ServerReader but also dying
     * ServerWriter at the same time, or from a thread that wants to shut down
     * the handler. So use a thread safe flag to know if the job must be done
     * or not (is already being processed or not).
     */
    if (!handler.engageShutdown())
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
        if (otherHandlers.contains(handler))
        {
          unRegisterHandler(handler);
          handler.shutdown();
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
   * @param handler the provided handler to unregister.
   */
  private void unregisterServerHandler(ServerHandler handler)
  {
    if (handler.isReplicationServer())
    {
      replicationServers.remove(handler.getServerId());
    }
    else
    {
      directoryServers.remove(handler.getServerId());
    }
  }

  /**
   * This method resets the generationId for this domain if there is no LDAP
   * server currently connected in the whole topology on this domain and
   * if the generationId has never been saved.
   *
   * - test emtpyness of directoryServers list
   * - traverse replicationServers list and test for each if DS are connected
   * So it strongly relies on the directoryServers list
   */
  private void mayResetGenerationId()
  {
    if (debugEnabled())
      TRACER.debugInfo(
        "In RS " + this.replicationServer.getMonitorInstanceName() +
        " for " + baseDn + " " +
        " mayResetGenerationId generationIdSavedStatus=" +
        generationIdSavedStatus);

    // If there is no more any LDAP server connected to this domain in the
    // topology and the generationId has never been saved, then we can reset
    // it and the next LDAP server to connect will become the new reference.
    boolean lDAPServersConnectedInTheTopology = false;
    if (directoryServers.isEmpty())
    {
      for (ReplicationServerHandler rsh : replicationServers.values())
      {
        if (generationId != rsh.getGenerationId())
        {
          if (debugEnabled())
            TRACER.debugInfo(
              "In RS " + this.replicationServer.getMonitorInstanceName() +
              " for " + baseDn + " " +
              " mayResetGenerationId skip RS" + rsh.getMonitorInstanceName() +
              " that has different genId");
        } else
        {
          if (rsh.hasRemoteLDAPServers())
          {
            lDAPServersConnectedInTheTopology = true;

            if (debugEnabled())
              TRACER.debugInfo(
                "In RS " + this.replicationServer.getMonitorInstanceName() +
                " for " + baseDn + " " +
                " mayResetGenerationId RS" + rsh.getMonitorInstanceName() +
                " has servers connected to it - will not reset generationId");
            break;
          }
        }
      }
    } else
    {
      lDAPServersConnectedInTheTopology = true;
      if (debugEnabled())
        TRACER.debugInfo(
          "In RS " + this.replicationServer.getMonitorInstanceName() +
          " for " + baseDn + " " +
          " has servers connected to it - will not reset generationId");
    }

    if ((!lDAPServersConnectedInTheTopology) &&
      (!this.generationIdSavedStatus) &&
      (generationId != -1))
    {
      changeGenerationId(-1, false);
    }
  }

  /**
   * Checks that a remote RS is not already connected to this hosting RS.
   * @param handler The handler for the remote RS.
   * @return flag specifying whether the remote RS is already connected.
   * @throws DirectoryException when a problem occurs.
   */
  public boolean checkForDuplicateRS(ReplicationServerHandler handler)
  throws DirectoryException
  {
    ReplicationServerHandler oldHandler =
      replicationServers.get(handler.getServerId());
    if ((oldHandler != null))
    {
      if (oldHandler.getServerAddressURL().equals(
        handler.getServerAddressURL()))
      {
        // this is the same server, this means that our ServerStart messages
        // have been sent at about the same time and 2 connections
        // have been established.
        // Silently drop this connection.
        return false;
      }
      else
      {
        // looks like two replication servers have the same serverId
        // log an error message and drop this connection.
        Message message = ERR_DUPLICATE_REPLICATION_SERVER_ID.get(
          replicationServer.getMonitorInstanceName(), oldHandler.
          getServerAddressURL(), handler.getServerAddressURL(),
          handler.getServerId());
        throw new DirectoryException(ResultCode.OTHER, message);
      }
    }
    return true;
  }

  /**
   * Get the next update that need to be sent to a given LDAP server.
   * This call is blocking when no update is available or when dependencies
   * do not allow to send the next available change
   *
   * @param  handler  The server handler for the target directory server.
   *
   * @return the update that must be forwarded
   */
  public UpdateMsg take(ServerHandler handler)
  {
    UpdateMsg msg;
    /*
     * Get the balanced tree that we use to sort the changes to be
     * sent to the replica from the cookie
     *
     * The next change to send is always the first one in the tree
     * So this methods simply need to check that dependencies are OK
     * and update this replicaId RUV
     *
     */
    msg = handler.take();

    return msg;
  }

  /**
   * Return a Set of String containing the lists of Replication servers
   * connected to this server.
   * @return the set of connected servers
   */
  public Set<String> getChangelogs()
  {
    LinkedHashSet<String> mySet = new LinkedHashSet<String>();

    for (ReplicationServerHandler handler : replicationServers.values())
    {
      mySet.add(handler.getServerAddressURL());
    }

    return mySet;
  }

  /**
   * Return a set containing the server that produced update and known by
   * this replicationServer from all over the topology,
   * whatever directly connected of connected to another RS.
   * @return a set containing the servers known by this replicationServer.
   */
  public Set<Integer> getServers()
  {
    return sourceDbHandlers.keySet();
  }

  /**
   * Returns as a set of String the list of LDAP servers connected to us.
   * Each string is the serverID of a connected LDAP server.
   *
   * @return The set of connected LDAP servers
   */
  public List<String> getConnectedLDAPservers()
  {
    List<String> mySet = new ArrayList<String>(0);

    for (DataServerHandler handler : directoryServers.values())
    {
      mySet.add(String.valueOf(handler.getServerId()));
    }
    return mySet;
  }

  /**
   * Creates and returns an iterator.
   * When the iterator is not used anymore, the caller MUST call the
   * ReplicationIterator.releaseCursor() method to free the resources
   * and locks used by the ReplicationIterator.
   *
   * @param serverId Identifier of the server for which the iterator is created.
   * @param changeNumber Starting point for the iterator.
   * @return the created ReplicationIterator. Null when no DB is available
   * for the provided server Id.
   */
  public ReplicationIterator getChangelogIterator(int serverId,
      ChangeNumber changeNumber)
  {
    DbHandler handler = sourceDbHandlers.get(serverId);
    if (handler == null)
      return null;

    ReplicationIterator it;
    try
    {
      it = handler.generateIterator(changeNumber);
    }
    catch (Exception e)
    {
      return null;
    }

    if (!it.next())
    {
      it.releaseCursor();
      return null;
    }

    return it;
  }

 /**
  * Count the number of changes in the replication changelog for the provided
  * serverID, between 2 provided changenumbers.
  * @param serverId Identifier of the server for which the iterator is created.
  * @param from lower limit changenumber.
  * @param to   upper limit changenumber.
  * @return the number of changes.
  *
  */
  public int getCount(int serverId,
      ChangeNumber from, ChangeNumber to)
  {
    DbHandler handler = sourceDbHandlers.get(serverId);
    if (handler == null)
      return 0;

    return handler.getCount(from, to);
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
   * @throws DatabaseException If a database error happened.
   */
  public void setDbHandler(int serverId, DbHandler dbHandler)
    throws DatabaseException
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
    List<ServerHandler> servers =
      new ArrayList<ServerHandler>();

    if (msg.getDestination() == RoutableMsg.THE_CLOSEST_SERVER)
    {
      // TODO Import from the "closest server" to be implemented
    } else if (msg.getDestination() == RoutableMsg.ALL_SERVERS)
    {
      if (!senderHandler.isReplicationServer())
      {
        // Send to all replication servers with a least one remote
        // server connected
        for (ReplicationServerHandler rsh : replicationServers.values())
        {
          if (rsh.hasRemoteLDAPServers())
          {
            servers.add(rsh);
          }
        }
      }

      // Sends to all connected LDAP servers
      for (DataServerHandler destinationHandler : directoryServers.values())
      {
        // Don't loop on the sender
        if (destinationHandler == senderHandler)
          continue;
        servers.add(destinationHandler);
      }
    } else
    {
      // Destination is one server
      DataServerHandler destinationHandler =
        directoryServers.get(msg.getDestination());
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
          for (ReplicationServerHandler h : replicationServers.values())
          {
            // Send to all replication servers with a least one remote
            // server connected
            if (h.isRemoteLDAPServer(msg.getDestination()))
            {
              servers.add(h);
            }
          }
        }
      }
    }
    return servers;
  }

  /**
   * Processes a message coming from one server in the topology
   * and potentially forwards it to one or all other servers.
   *
   * @param msg The message received and to be processed.
   * @param senderHandler The server handler of the server that emitted
   * the message.
   */
  public void process(RoutableMsg msg, ServerHandler senderHandler)
  {
    // Test the message for which a ReplicationServer is expected
    // to be the destination
    if (!(msg instanceof InitializeRequestMsg) &&
        !(msg instanceof InitializeTargetMsg) &&
        !(msg instanceof InitializeRcvAckMsg) &&
        !(msg instanceof EntryMsg) &&
        !(msg instanceof DoneMsg) &&
        (msg.getDestination() == this.replicationServer.getServerId()))
    {
      if (msg instanceof ErrorMsg)
      {
        ErrorMsg errorMsg = (ErrorMsg) msg;
        logError(ERR_ERROR_MSG_RECEIVED.get(
          errorMsg.getDetails()));
      } else if (msg instanceof MonitorRequestMsg)
      {
        // If the request comes from a Directory Server we need to
        // build the full list of all servers in the topology
        // and send back a MonitorMsg with the full list of all the servers
        // in the topology.
        if (senderHandler.isDataServer())
        {
          // Monitoring information requested by a DS
          MonitorMsg monitorMsg = createGlobalTopologyMonitorMsg(
              msg.getDestination(), msg.getSenderID(), monitorData);

          if (monitorMsg != null)
          {
            try
            {
              senderHandler.send(monitorMsg);
            }
            catch (IOException e)
            {
              // the connection was closed.
            }
          }
          return;
        } else
        {
          // Monitoring information requested by a RS
          MonitorMsg monitorMsg =
            createLocalTopologyMonitorMsg(msg.getDestination(),
            msg.getSenderID());

          if (monitorMsg != null)
          {
            try
            {
              senderHandler.send(monitorMsg);
            } catch (Exception e)
            {
              // We log the error. The requestor will detect a timeout or
              // any other failure on the connection.
              logError(ERR_CHANGELOG_ERROR_SENDING_MSG.get(
                  Integer.toString((msg.getDestination()))));
            }
          }
        }
      } else if (msg instanceof MonitorMsg)
      {
        MonitorMsg monitorMsg = (MonitorMsg) msg;
        receivesMonitorDataResponse(monitorMsg, senderHandler.getServerId());
      } else
      {
        logError(NOTE_ERR_ROUTING_TO_SERVER.get(
          msg.getClass().getCanonicalName()));

        MessageBuilder mb1 = new MessageBuilder();
        mb1.append(
            NOTE_ERR_ROUTING_TO_SERVER.get(msg.getClass().getCanonicalName()));
        mb1.append("serverID:").append(msg.getDestination());
        ErrorMsg errMsg = new ErrorMsg(msg.getSenderID(), mb1.toMessage());
        try
        {
          senderHandler.send(errMsg);
        } catch (IOException ioe1)
        {
          // an error happened on the sender session trying to recover
          // from an error on the receiver session.
          // Not much more we can do at this point.
        }
      }
      return;
    }

    List<ServerHandler> servers = getDestinationServers(msg, senderHandler);

    if (servers.isEmpty())
    {
      MessageBuilder mb = new MessageBuilder();
      mb.append(ERR_NO_REACHABLE_PEER_IN_THE_DOMAIN.get(
          this.baseDn, Integer.toString(msg.getDestination())));
      mb.append(" In Replication Server=").append(
        this.replicationServer.getMonitorInstanceName());
      mb.append(" unroutable message =").append(msg.getClass().getSimpleName());
      mb.append(" Details:routing table is empty");
      ErrorMsg errMsg = new ErrorMsg(
        this.replicationServer.getServerId(),
        msg.getSenderID(),
        mb.toMessage());
      logError(mb.toMessage());
      try
      {
        senderHandler.send(errMsg);
      } catch (IOException ioe)
      {
        // TODO Handle error properly (sender timeout in addition)
        /*
         * An error happened trying to send an error msg to this server.
         * Log an error and close the connection to this server.
         */
        MessageBuilder mb2 = new MessageBuilder();
        mb2.append(ERR_CHANGELOG_ERROR_SENDING_ERROR.get(this.toString()));
        mb2.append(stackTraceToSingleLineString(ioe));
        logError(mb2.toMessage());
        stopServer(senderHandler, false);
      }
    } else
    {
      for (ServerHandler targetHandler : servers)
      {
        try
        {
          targetHandler.send(msg);
        } catch (IOException ioe)
        {
          /*
           * An error happened trying the send a routable message
           * to its destination server.
           * Send back an error to the originator of the message.
           */
          MessageBuilder mb1 = new MessageBuilder();
          mb1.append(ERR_NO_REACHABLE_PEER_IN_THE_DOMAIN.get(
              this.baseDn, Integer.toString(msg.getDestination())));
          mb1.append(" unroutable message =" + msg.getClass().getSimpleName());
          mb1.append(" Details: " + ioe.getLocalizedMessage());
          ErrorMsg errMsg = new ErrorMsg(
            msg.getSenderID(), mb1.toMessage());
          logError(mb1.toMessage());
          try
          {
            senderHandler.send(errMsg);
          } catch (IOException ioe1)
          {
            // an error happened on the sender session trying to recover
            // from an error on the receiver session.
            // We don't have much solution left beside closing the sessions.
            stopServer(senderHandler, false);
            stopServer(targetHandler, false);
          }
        // TODO Handle error properly (sender timeout in addition)
        }
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
   * @param monitorData
   *          The domain monitor data which should be used for the message.
   * @return The newly created and filled MonitorMsg. Null if a problem occurred
   *         during message creation.
   */
  public MonitorMsg createGlobalTopologyMonitorMsg(
      int sender, int destination, MonitorData monitorData)
  {
    MonitorMsg returnMsg =
      new MonitorMsg(sender, destination);

    returnMsg.setReplServerDbState(getDbServerState());

    // Add the informations about the Replicas currently in
    // the topology.
    Iterator<Integer> it = monitorData.ldapIterator();
    while (it.hasNext())
    {
      int replicaId = it.next();
      returnMsg.setServerState(replicaId,
          monitorData.getLDAPServerState(replicaId),
          monitorData.getApproxFirstMissingDate(replicaId), true);
    }

    // Add the information about the Replication Servers
    // currently in the topology.
    it = monitorData.rsIterator();
    while (it.hasNext())
    {
      int replicaId = it.next();
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
      MonitorMsg monitorMsg = new MonitorMsg(sender, destination);

      // Populate for each connected LDAP Server
      // from the states stored in the serverHandler.
      // - the server state
      // - the older missing change
      for (DataServerHandler lsh : this.directoryServers.values())
      {
        monitorMsg.setServerState(lsh.getServerId(),
            lsh.getServerState(), lsh.getApproxFirstMissingDate(),
            true);
      }

      // Same for the connected RS
      for (ReplicationServerHandler rsh : this.replicationServers.values())
      {
        monitorMsg.setServerState(rsh.getServerId(),
            rsh.getServerState(), rsh.getApproxFirstMissingDate(),
            false);
      }

      // Populate the RS state in the msg from the DbState
      monitorMsg.setReplServerDbState(this.getDbServerState());
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

    stopDbHandlers();
  }

  /**
   * Stop the dbHandlers .
   */
  private void stopDbHandlers()
  {
    // Shutdown the dbHandlers
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
   * Send a TopologyMsg to all the connected directory servers in order to
   * let them know the topology (every known DSs and RSs).
   * @param notThisOne If not null, the topology message will not be sent to
   * this passed server.
   */
  public void buildAndSendTopoInfoToDSs(ServerHandler notThisOne)
  {
    for (DataServerHandler handler : directoryServers.values())
    {
      if ((notThisOne == null) || ((handler != notThisOne)))
        // All except passed one
      {
        for (int i=1; i<=2; i++)
        {
          if (!handler.shuttingDown())
          {
            if (handler.getStatus() != ServerStatus.NOT_CONNECTED_STATUS)
            {
              TopologyMsg topoMsg=createTopologyMsgForDS(handler.getServerId());
              try
              {
                handler.sendTopoInfo(topoMsg);
                break;
              }
              catch (IOException e)
              {
                if (i==2)
                {
                  Message message = ERR_EXCEPTION_SENDING_TOPO_INFO.get(
                      baseDn,
                      "directory",
                      Integer.toString(handler.getServerId()),
                      e.getMessage());
                  logError(message);
                }
              }
            }
          }
          try { Thread.sleep(100); } catch(Exception e) {}
        }
      }
    }
  }

  /**
   * Send a TopologyMsg to all the connected replication servers
   * in order to let them know our connected LDAP servers.
   */
  public void buildAndSendTopoInfoToRSs()
  {
    TopologyMsg topoMsg = createTopologyMsgForRS();
    for (ReplicationServerHandler handler : replicationServers.values())
    {
      for (int i=1; i<=2; i++)
      {
        if (!handler.shuttingDown())
        {
          if (handler.getStatus() != ServerStatus.NOT_CONNECTED_STATUS)
          {
            try
            {
              handler.sendTopoInfo(topoMsg);
              break;
            }
            catch (IOException e)
            {
              if (i==2)
              {
                Message message = ERR_EXCEPTION_SENDING_TOPO_INFO.get(
                    baseDn,
                    "replication",
                    Integer.toString(handler.getServerId()),
                    e.getMessage());
                logError(message);
              }
            }
          }
        }
        try { Thread.sleep(100); } catch(Exception e) {}
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

    // Go through every DSs
    for (DataServerHandler serverHandler : directoryServers.values())
    {
      dsInfos.add(serverHandler.toDSInfo());
    }

    // Create info for the local RS
    List<RSInfo> rsInfos = new ArrayList<RSInfo>();
    RSInfo localRSInfo = new RSInfo(replicationServer.getServerId(),
      replicationServer.getServerURL(), generationId,
      replicationServer.getGroupId(), replicationServer.getWeight());
    rsInfos.add(localRSInfo);

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
    List<DSInfo> dsInfos = new ArrayList<DSInfo>();
    List<RSInfo> rsInfos = new ArrayList<RSInfo>();

    // Go through every DSs (except recipient of msg)
    for (DataServerHandler serverHandler : directoryServers.values())
    {
      if (serverHandler.getServerId() == destDsId)
        continue;
      dsInfos.add(serverHandler.toDSInfo());
    }

    // Add our own info (local RS)
    RSInfo localRSInfo = new RSInfo(replicationServer.getServerId(),
      replicationServer.getServerURL(), generationId,
      replicationServer.getGroupId(), replicationServer.getWeight());
    rsInfos.add(localRSInfo);

    // Go through every peer RSs (and get their connected DSs), also add info
    // for RSs
    for (ReplicationServerHandler serverHandler : replicationServers.values())
    {
      // Put RS info
      rsInfos.add(serverHandler.toRSInfo());

      serverHandler.addDSInfos(dsInfos);
    }

    return new TopologyMsg(dsInfos, rsInfos);
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
        // we are changing of genId
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
      TRACER.debugInfo(
          "In " + this +
          " Receiving ResetGenerationIdMsg from " + senderHandler.getServerId()+
          " for baseDn " + baseDn + ":\n" + genIdMsg);

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
          TRACER.debugInfo(
              "In " + this
              + " Reset generation id requested for baseDn " + baseDn
              + " but generation id was already " + this.generationId
              + ":\n" + genIdMsg);
      }

      // If we are the first replication server warned,
      // then forwards the reset message to the remote replication servers
      for (ServerHandler rsHandler : replicationServers.values())
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
      for (DataServerHandler dsHandler : directoryServers.values())
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
      buildAndSendTopoInfoToDSs(null);
      buildAndSendTopoInfoToRSs();

      Message message = NOTE_RESET_GENERATION_ID.get(baseDn, newGenId);
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
      TRACER.debugInfo(
          "In RS " + getReplicationServer().getServerId() +
          " Receiving ChangeStatusMsg from " + senderHandler.getServerId() +
          " for baseDn " + baseDn + ":\n" + csMsg);
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

      // Update every peers (RS/DS) with topology changes
      buildAndSendTopoInfoToDSs(senderHandler);
      buildAndSendTopoInfoToRSs();

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
   * @param serverHandler The handler of the directory server to update
   * @param event The event to be used for new status computation
   * @return True if we have been interrupted (must stop), false otherwise
   */
  public boolean changeStatusFromStatusAnalyzer(
      DataServerHandler serverHandler, StatusMachineEvent event)
  {
    try
    {
      // Acquire lock on domain (see more details in comment of start() method
      // of ServerHandler)
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
        TRACER
            .debugInfo("Status analyzer for domain "
                + baseDn
                + " has been interrupted when"
                + " trying to acquire domain lock for changing the status"
                + " of DS "
                + serverHandler.getServerId());
      return true;
    }

    try
    {
      ServerStatus newStatus = ServerStatus.INVALID_STATUS;
      ServerStatus oldStatus = serverHandler.getStatus();
      try
      {
        newStatus = serverHandler
            .changeStatusFromStatusAnalyzer(event);
      }
      catch (IOException e)
      {
        logError(ERR_EXCEPTION_CHANGING_STATUS_FROM_STATUS_ANALYZER
            .get(baseDn,
                Integer.toString(serverHandler.getServerId()),
                e.getMessage()));
      }

      if ((newStatus == ServerStatus.INVALID_STATUS)
          || (newStatus == oldStatus))
      {
        // Change was impossible or already occurred (see StatusAnalyzer
        // comments)
        return false;
      }

      // Update every peers (RS/DS) with topology changes
      buildAndSendTopoInfoToDSs(serverHandler);
      buildAndSendTopoInfoToRSs();
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
            e.getMessage() + " " +
            stackTraceToSingleLineString(e)));
          logError(mb.toMessage());
        }
      }
      stopDbHandlers();
    }
    try
    {
      replicationServer.clearGenerationId(baseDn);
    } catch (Exception e)
    {
      // TODO: i18n
      logError(Message.raw(
        "Exception caught while clearing generationId:" +
        e.getLocalizedMessage()));
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
      TRACER.debugInfo(
        "In " + this.replicationServer.getMonitorInstanceName() +
        " baseDN=" + baseDn +
        " isDegraded serverId=" + serverId +
        " given local generation Id=" + this.generationId);

    ServerHandler handler = replicationServers.get(serverId);
    if (handler == null)
    {
      handler = directoryServers.get(serverId);
      if (handler == null)
      {
        return false;
      }
    }

    if (debugEnabled())
      TRACER.debugInfo(
        "In " + this.replicationServer.getMonitorInstanceName() +
        " baseDN=" + baseDn +
        " Compute degradation of serverId=" + serverId +
        " LS server generation Id=" + handler.getGenerationId());
    return (handler.getGenerationId() != this.generationId);
  }

  /**
   * Return the associated replication server.
   * @return The replication server.
   */
  public ReplicationServer getReplicationServer()
  {
    return replicationServer;
  }

  /**
   * Process topology information received from a peer RS.
   * @param topoMsg The just received topo message from remote RS
   * @param handler The handler that received the message.
   * @param allowResetGenId True for allowing to reset the generation id (
   * when called after initial handshake)
   * @throws IOException If an error occurred.
   * @throws DirectoryException If an error occurred.
   */
  public void receiveTopoInfoFromRS(TopologyMsg topoMsg,
      ReplicationServerHandler handler,
    boolean allowResetGenId)
    throws IOException, DirectoryException
  {
    if (debugEnabled())
    {
      TRACER.debugInfo(
        "In RS " + getReplicationServer().getServerId() +
        " Receiving TopologyMsg from " + handler.getServerId() +
        " for baseDn " + baseDn + ":\n" + topoMsg);
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
      /*
       * Store DS connected to remote RS & update information about the peer RS
       */
      handler.processTopoInfoFromRS(topoMsg);

      /*
       * Handle generation id
       */
      if (allowResetGenId)
      {
        // Check if generation id has to be reseted
        mayResetGenerationId();
        if (generationId < 0)
          generationId = handler.getGenerationId();
      }

      if (generationId > 0 && (generationId != handler.getGenerationId()))
      {
        Message message = WARN_BAD_GENERATION_ID_FROM_RS.get(handler
            .getServerId(), handler.session
            .getReadableRemoteAddress(), handler.getGenerationId(),
            baseDn, getReplicationServer().getServerId(),
            generationId);
        logError(message);

        ErrorMsg errorMsg = new ErrorMsg(
            getReplicationServer().getServerId(),
            handler.getServerId(),
            message);
        handler.send(errorMsg);
      }

      /*
       * Sends the currently known topology information to every connected
       * DS we have.
       */
      buildAndSendTopoInfoToDSs(null);
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

  /* =======================
   * Monitor Data generation
   * =======================
   */

  /**
   * Returns the latest monitor data available for this replication server
   * domain.
   *
   * @return The latest monitor data available for this replication server
   *         domain, which is never {@code null}.
   */
  MonitorData getDomainMonitorData()
  {
    return monitorData;
  }



  /**
   * Recomputes the monitor data for this replication server domain.
   *
   * @return The recomputed monitor data for this replication server domain.
   * @throws InterruptedException
   *           If this thread is interrupted while waiting for a response.
   */
  MonitorData computeDomainMonitorData() throws InterruptedException
  {
    // Only allow monitor recalculation at a time.
    synchronized (pendingMonitorLock)
    {
      if ((monitorDataLastBuildDate + monitorDataLifeTime) < TimeThread
          .getTime())
      {
        try
        {
          // Prevent out of band monitor responses from updating our pending
          // table until we are ready.
          synchronized (pendingMonitorDataLock)
          {
            // Clear the pending monitor data.
            pendingMonitorDataServerIDs.clear();
            pendingMonitorData = new MonitorData();

            // Initialize the monitor data.
            initializePendingMonitorData();

            // Send the monitor requests to the connected replication servers.
            for (ReplicationServerHandler rs : replicationServers.values())
            {
              // Add server ID to pending table.
              int serverId = rs.getServerId();

              MonitorRequestMsg msg = new MonitorRequestMsg(
                  this.replicationServer.getServerId(), serverId);
              try
              {
                rs.send(msg);

                // Only register this server ID if we were able to send the
                // message.
                pendingMonitorDataServerIDs.add(serverId);
              }
              catch (IOException e)
              {
                // Log a message and do a best effort from here.
                Message message = ERR_SENDING_REMOTE_MONITOR_DATA_REQUEST
                    .get(baseDn, serverId, e.getMessage());
                logError(message);
              }
            }

            // Create the pending response latch based on the number of expected
            // monitor responses.
            pendingMonitorDataLatch = new CountDownLatch(
                pendingMonitorDataServerIDs.size());
          }

          // Wait for the responses to come back.
          pendingMonitorDataLatch.await(5, TimeUnit.SECONDS);

          // Log messages for replication servers that have gone or come back.
          synchronized (pendingMonitorDataLock)
          {
            // Log servers that have come back.
            for (int serverId : monitorDataLateServers)
            {
              // Ensure that we only log once per server: don't fill the
              // error log with repeated messages.
              if (!pendingMonitorDataServerIDs.contains(serverId))
              {
                logError(NOTE_MONITOR_DATA_RECEIVED.get(baseDn,
                    serverId));
              }
            }

            // Log servers that have gone away.
            for (int serverId : pendingMonitorDataServerIDs)
            {
              // Ensure that we only log once per server: don't fill the
              // error log with repeated messages.
              if (!monitorDataLateServers.contains(serverId))
              {
                logError(WARN_MISSING_REMOTE_MONITOR_DATA.get(baseDn,
                    serverId));
              }
            }

            // Remember which servers were late this time.
            monitorDataLateServers.clear();
            monitorDataLateServers.addAll(pendingMonitorDataServerIDs);
          }

          // Store the new computed data as the reference
          synchronized (pendingMonitorDataLock)
          {
            // Now we have the expected answers or an error occurred
            pendingMonitorData.completeComputing();
            monitorData = pendingMonitorData;
            monitorDataLastBuildDate = TimeThread.getTime();
          }
        }
        finally
        {
          synchronized (pendingMonitorDataLock)
          {
            // Clear pending state.
            pendingMonitorData = null;
            pendingMonitorDataLatch = null;
            pendingMonitorDataServerIDs.clear();
          }
        }
      }
    }

    return monitorData;
  }



  /**
   * Start collecting global monitoring information for this
   * ReplicationServerDomain.
   */

  private void initializePendingMonitorData()
  {
    // Let's process our directly connected DS
    // - in the ServerHandler for a given DS1, the stored state contains :
    // - the max CN produced by DS1
    // - the last CN consumed by DS1 from DS2..n
    // - in the RSdomain/dbHandler, the built-in state contains :
    // - the max CN produced by each server
    // So for a given DS connected we can take the state and the max from
    // the DS/state.

    for (ServerHandler ds : directoryServers.values())
    {
      int serverID = ds.getServerId();

      // the state comes from the state stored in the SH
      ServerState dsState = ds.getServerState()
          .duplicate();

      // the max CN sent by that LS also comes from the SH
      ChangeNumber maxcn = dsState.getMaxChangeNumber(serverID);
      if (maxcn == null)
      {
        // This directly connected LS has never produced any change
        maxcn = new ChangeNumber(0, 0, serverID);
      }
      pendingMonitorData.setMaxCN(serverID, maxcn);
      pendingMonitorData.setLDAPServerState(serverID, dsState);
      pendingMonitorData.setFirstMissingDate(serverID,
          ds.getApproxFirstMissingDate());
    }

    // Then initialize the max CN for the LS that produced something
    // - from our own local db state
    // - whatever they are directly or indirectly connected
    ServerState dbServerState = getDbServerState();
    pendingMonitorData.setRSState(replicationServer.getServerId(),
        dbServerState);
    for (int sid : dbServerState) {
      ChangeNumber storedCN = dbServerState.getMaxChangeNumber(sid);
      pendingMonitorData.setMaxCN(sid, storedCN);
    }
  }



  /**
   * Processes a Monitor message receives from a remote Replication Server and
   * stores the data received.
   *
   * @param msg
   *          The message to be processed.
   * @param serverId
   *          server handler that is receiving the message.
   */
  private void receivesMonitorDataResponse(MonitorMsg msg,
      int serverId)
  {
    synchronized (pendingMonitorDataLock)
    {
      if (pendingMonitorData == null)
      {
        // This is a response for an earlier request whose computing is
        // already complete.
        logError(INFO_IGNORING_REMOTE_MONITOR_DATA.get(baseDn,
            msg.getSenderID()));
        return;
      }

      try
      {
        // Here is the RS state : list <serverID, lastChangeNumber>
        // For each LDAP Server, we keep the max CN across the RSes
        ServerState replServerState = msg.getReplServerDbState();
        pendingMonitorData.setMaxCNs(replServerState);

        // store the remote RS states.
        pendingMonitorData.setRSState(msg.getSenderID(),
            replServerState);

        // Store the remote LDAP servers states
        Iterator<Integer> lsidIterator = msg.ldapIterator();
        while (lsidIterator.hasNext())
        {
          int sid = lsidIterator.next();
          ServerState dsServerState = msg.getLDAPServerState(sid);
          pendingMonitorData.setMaxCNs(dsServerState);
          pendingMonitorData.setLDAPServerState(sid, dsServerState);
          pendingMonitorData.setFirstMissingDate(sid,
              msg.getLDAPApproxFirstMissingDate(sid));
        }

        // Process the latency reported by the remote RSi on its connections
        // to the other RSes
        Iterator<Integer> rsidIterator = msg.rsIterator();
        while (rsidIterator.hasNext())
        {
          int rsid = rsidIterator.next();
          if (rsid == replicationServer.getServerId())
          {
            // this is the latency of the remote RSi regarding the current RS
            // let's update the fmd of my connected LS
            for (ServerHandler connectedlsh : directoryServers
                .values())
            {
              int connectedlsid = connectedlsh.getServerId();
              Long newfmd = msg.getRSApproxFirstMissingDate(rsid);
              pendingMonitorData.setFirstMissingDate(connectedlsid,
                  newfmd);
            }
          }
          else
          {
            // this is the latency of the remote RSi regarding another RSj
            // let's update the latency of the LSes connected to RSj
            ReplicationServerHandler rsjHdr = replicationServers
                .get(rsid);
            if (rsjHdr != null)
            {
              for (int remotelsid : rsjHdr
                  .getConnectedDirectoryServerIds())
              {
                Long newfmd = msg.getRSApproxFirstMissingDate(rsid);
                pendingMonitorData.setFirstMissingDate(remotelsid,
                    newfmd);
              }
            }
          }
        }
      }
      catch (RuntimeException e)
      {
        // FIXME: do we really expect these???
        logError(ERR_PROCESSING_REMOTE_MONITOR_DATA.get(e
            .getMessage() + stackTraceToSingleLineString(e)));
      }
      finally
      {
        // Decreases the number of expected responses and potentially
        // wakes up the waiting requester thread.
        if (pendingMonitorDataServerIDs.remove(serverId))
        {
          pendingMonitorDataLatch.countDown();
        }
      }
    }
  }

  /**
   * Set the purge delay on all the db Handlers for this Domain
   * of Replication.
   *
   * @param delay The new purge delay to use.
   */
  public void setPurgeDelay(long delay)
  {
    for (DbHandler handler : sourceDbHandlers.values())
    {
      handler.setPurgeDelay(delay);
    }
  }

  /**
   * Get the map of connected DSs.
   * @return The map of connected DSs
   */
  public Map<Integer, DataServerHandler> getConnectedDSs()
  {
    return directoryServers;
  }

  /**
   * Get the map of connected RSs.
   * @return The map of connected RSs
   */
  public Map<Integer, ReplicationServerHandler> getConnectedRSs()
  {
    return replicationServers;
  }


  /**
   * A synchronization mechanism is created to insure exclusive access to the
   * domain. The goal is to have a consistent view of the topology by locking
   * the structures holding the topology view of the domain: directoryServers
   * and replicationServers. When a connection is established with a peer DS or
   * RS, the lock should be taken before updating these structures, then
   * released. The same mechanism should be used when updating any data related
   * to the view of the topology: for instance if the status of a DS is changed,
   * the lock should be taken before updating the matching server handler and
   * sending the topology messages to peers and released after.... This allows
   * every member of the topology to have a consistent view of the topology and
   * to be sure it will not miss some information.
   * So the locking system must be called (not exhaustive list):
   * - when connection established with a DS or RS
   * - when connection ended with a DS or RS
   * - when receiving a TopologyMsg and updating structures
   * - when creating and sending a TopologyMsg
   * - when a DS status is changing (ChangeStatusMsg received or sent)...
   */
  private final ReentrantLock lock = new ReentrantLock();

  /**
   * This lock is used to protect the generationid variable.
   */
  private final Object generationIDLock = new Object();

  /**
   * Tests if the current thread has the lock on this domain.
   * @return True if the current thread has the lock.
   */
  public boolean hasLock()
  {
    return (lock.getHoldCount() > 0);
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
   * Starts the status analyzer for the domain.
   */
  public void startStatusAnalyzer()
  {
    if (statusAnalyzer == null)
    {
      int degradedStatusThreshold =
        replicationServer.getDegradedStatusThreshold();
      if (degradedStatusThreshold > 0) // 0 means no status analyzer
      {
        statusAnalyzer = new StatusAnalyzer(this, degradedStatusThreshold);
        statusAnalyzer.start();
      }
    }
  }

  /**
   * Stops the status analyzer for the domain.
   */
  public void stopStatusAnalyzer()
  {
    if (statusAnalyzer != null)
    {
      statusAnalyzer.shutdown();
      statusAnalyzer.waitForShutdown();
      statusAnalyzer = null;
    }
  }

  /**
   * Tests if the status analyzer for this domain is running.
   * @return True if the status analyzer is running, false otherwise.
   */
  public boolean isRunningStatusAnalyzer()
  {
    return (statusAnalyzer != null);
  }

  /**
   * Update the status analyzer with the new threshold value.
   * @param degradedStatusThreshold The new threshold value.
   */
  public void updateStatusAnalyzer(int degradedStatusThreshold)
  {
    if (statusAnalyzer != null)
    {
      statusAnalyzer.setDegradedStatusThreshold(degradedStatusThreshold);
    }
  }

  /**
   * Starts the monitoring publisher for the domain.
   */
  public void startMonitoringPublisher()
  {
    if (monitoringPublisher == null)
    {
      long period =
        replicationServer.getMonitoringPublisherPeriod();
      if (period > 0) // 0 means no monitoring publisher
      {
        monitoringPublisher = new MonitoringPublisher(this, period);
        monitoringPublisher.start();
      }
    }
  }

  /**
   * Stops the monitoring publisher for the domain.
   */
  public void stopMonitoringPublisher()
  {
    if (monitoringPublisher != null)
    {
      monitoringPublisher.shutdown();
      monitoringPublisher.waitForShutdown();
      monitoringPublisher = null;
    }
  }

  /**
   * Tests if the monitoring publisher for this domain is running.
   * @return True if the monitoring publisher is running, false otherwise.
   */
  public boolean isRunningMonitoringPublisher()
  {
    return (monitoringPublisher != null);
  }

  /**
   * Update the monitoring publisher with the new period value.
   * @param period The new period value.
   */
  public void updateMonitoringPublisher(long period)
  {
    if (monitoringPublisher != null)
    {
      monitoringPublisher.setPeriod(period);
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
    return "Replication server RS(" + replicationServer.getServerId() + ") "
        + replicationServer.getServerURL() + ",cn="
        + baseDn.replace(',', '_').replace('=', '_') + ",cn=Replication";
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ArrayList<Attribute> getMonitorData()
  {
    /*
     * publish the server id and the port number.
     */
    ArrayList<Attribute> attributes = new ArrayList<Attribute>();
    attributes.add(Attributes.create("replication-server-id",
        String.valueOf(replicationServer.getServerId())));
    attributes.add(Attributes.create("replication-server-port",
        String.valueOf(replicationServer.getReplicationPort())));

    /*
     * Add all the base DNs that are known by this replication server.
     */
    AttributeBuilder builder = new AttributeBuilder("domain-name");
    builder.add(baseDn);
    attributes.add(builder.toAttribute());

    // Publish to monitor the generation ID by replicationServerDomain
    builder = new AttributeBuilder("generation-id");
    builder.add(baseDn + " " + generationId);
    attributes.add(builder.toAttribute());

    MonitorData md = getDomainMonitorData();

    // Missing changes
    long missingChanges = md.getMissingChangesRS(replicationServer
        .getServerId());
    attributes.add(Attributes.create("missing-changes",
        String.valueOf(missingChanges)));

    return attributes;
  }

  /**
   * Register in the domain an handler that subscribes to changes.
   * @param handler the provided subscribing handler.
   */
  public void registerHandler(MessageHandler handler)
  {
    this.otherHandlers.add(handler);
  }

  /**
   * Unregister from the domain an handler.
   * @param handler the provided unsubscribing handler.
   * @return Whether this handler has been unregistered with success.
   */
  public boolean unRegisterHandler(MessageHandler handler)
  {
    return this.otherHandlers.remove(handler);
  }

  /**
   * Return the state that contain for each server the time of eligibility.
   * @return the state.
   */
  public ServerState getChangeTimeHeartbeatState()
  {
    if (ctHeartbeatState == null)
    {
      ctHeartbeatState = this.getDbServerState().duplicate();
    }
    return ctHeartbeatState;
  }

  /**
   * Computes the eligible server state for the domain.
   *
   *     s1               s2          s3
   *     --               --          --
   *                                 cn31
   *     cn15
   *
   *  ----------------------------------------- eligibleCN
   *     cn14
   *                     cn26
   *     cn13
   *
   * The eligibleState is : s1;cn14 / s2;cn26 / s3;cn31
   *
   * @param eligibleCN              The provided eligibleCN.
   * @return The computed eligible server state.
   */
  public ServerState getEligibleState(ChangeNumber eligibleCN)
  {
    ServerState dbState = this.getDbServerState();

    // The result is initialized from the dbState.
    // From it, we don't want to keep the changes newer than eligibleCN.
    ServerState result = dbState.duplicate();

    if (eligibleCN != null)
    {
      for (int sid : dbState) {
        DbHandler h = sourceDbHandlers.get(sid);
        ChangeNumber mostRecentDbCN = dbState.getMaxChangeNumber(sid);
        try {
          // Is the most recent change in the Db newer than eligible CN ?
          // if yes (like cn15 in the example above, then we have to go back
          // to the Db and look for the change older than  eligible CN (cn14)
          if (eligibleCN.olderOrEqual(mostRecentDbCN)) {
            // let's try to seek the first change <= eligibleCN
            ReplicationIterator ri = null;
            try {
              ri = h.generateIterator(eligibleCN);
              if ((ri != null) && (ri.getChange() != null)) {
                ChangeNumber newCN = ri.getChange().getChangeNumber();
                result.update(newCN);
              }
            } catch (Exception e) {
              // there's no change older than eligibleCN (case of s3/cn31)
              result.update(new ChangeNumber(0, 0, sid));
            } finally {
              if (ri != null) {
                ri.releaseCursor();
              }
            }
          } else {
            // for this serverId, all changes in the ChangelogDb are holder
            // than eligibleCN , the most recent in the db is our guy.
            result.update(mostRecentDbCN);
          }
        } catch (Exception e) {
          Message errMessage = ERR_WRITER_UNEXPECTED_EXCEPTION.get(
              " " + stackTraceToSingleLineString(e));
          logError(errMessage);
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }
    }

    if (debugEnabled())
      TRACER.debugInfo("In " + this
        + " getEligibleState() result is " + result);
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
   * Returns the eligibleCN for that domain - relies on the ChangeTimeHeartbeat
   * state.
   * For each DS, take the oldest CN from the changetime heartbeat state
   * and from the changelog db last CN. Can be null.
   * @return the eligible CN.
   */
  public ChangeNumber getEligibleCN()
  {
    ChangeNumber eligibleCN = null;

    for (DbHandler db : sourceDbHandlers.values())
    {
      // Consider this producer (DS/db).
      int sid = db.getServerId();

      // Should it be considered for eligibility ?
      ChangeNumber heartbeatLastDN =
        getChangeTimeHeartbeatState().getMaxChangeNumber(sid);

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

      boolean sidConnected = false;
      if (directoryServers.containsKey(sid))
      {
        sidConnected = true;
      }
      else
      {
        // not directly connected
        for (ReplicationServerHandler rsh : replicationServers.values())
        {
          if (rsh.isRemoteLDAPServer(sid))
          {
            sidConnected = true;
            break;
          }
        }
      }
      if (!sidConnected)
      {
        if (debugEnabled())
          TRACER.debugInfo("In " + "Replication Server " +
            replicationServer.getReplicationPort() + " " +
            baseDn + " " + replicationServer.getServerId() +
            " Server " + sid
            + " is not considered for eligibility ... potentially down");
        continue;
      }

      ChangeNumber changelogLastCN = db.getLastChange();
      if (changelogLastCN != null)
      {
        if ((eligibleCN == null) || (changelogLastCN.newer(eligibleCN)))
        {
          eligibleCN = changelogLastCN;
        }
      }

      if ((heartbeatLastDN != null) &&
       ((eligibleCN == null) || (heartbeatLastDN.newer(eligibleCN))))
      {
        eligibleCN = heartbeatLastDN;
      }
    }

    if (debugEnabled())
      TRACER.debugInfo(
        "In " + "Replication Server " + replicationServer.getReplicationPort() +
        " " + baseDn + " " + replicationServer.getServerId() +
        " getEligibleCN() returns result =" + eligibleCN);
    return eligibleCN;
  }


  /**
   * Processes a ChangeTimeHeartbeatMsg received, by storing the CN (timestamp)
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
      storeReceivedCTHeartbeat(msg.getChangeNumber());
      if (senderHandler.isDataServer())
      {
        // If we are the first replication server warned,
        // then forwards the message to the remote replication servers
        for (ReplicationServerHandler rsHandler : replicationServers
            .values())
        {
          try
          {
            if (rsHandler.getProtocolVersion() >=
              ProtocolVersion.REPLICATION_PROTOCOL_V3)
            {
              rsHandler.send(msg);
            }
          }
          catch (IOException e)
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
            logError(ERR_CHANGELOG_ERROR_SENDING_MSG
                .get("Replication Server "
                    + replicationServer.getReplicationPort() + " "
                    + baseDn + " " + replicationServer.getServerId()));
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
   * @param cn The provided change time.
   */
  public void storeReceivedCTHeartbeat(ChangeNumber cn)
  {
    // TODO:May be we can spare processing by only storing CN (timestamp)
    // instead of a server state.
    getChangeTimeHeartbeatState().update(cn);

    /*
    if (debugEnabled())
    {
      Set<String> ss = ctHeartbeatState.toStringSet();
      String dss = "";
      for (String s : ss)
      {
        dss = dss + " \\ " + s;
      }
      TRACER.debugInfo("In " + this.getName() + " " + dss);
    }
    */
  }

  /**
   * This methods count the changes, server by server :
   * - from a serverState start point
   * - to (inclusive) an end point (the provided endCN).
   * @param startState The provided start server state.
   * @param endCN The provided end change number.
   * @return The number of changes between startState and endCN.
   */
  public long getEligibleCount(ServerState startState, ChangeNumber endCN)
  {
    long res = 0;

    // Parses the dbState of the domain , server by server
    ServerState dbState = this.getDbServerState();
    for (int sid : dbState) {
      // process one sid
      ChangeNumber startCN = null;
      if (startState.getMaxChangeNumber(sid) != null)
        startCN = startState.getMaxChangeNumber(sid);
      long sidRes = getCount(sid, startCN, endCN);

      // The startPoint is excluded when counting the ECL eligible changes
      if ((startCN != null) && (sidRes > 0))
        sidRes--;

      res += sidRes;
    }
    return res;
  }

  /**
   * This methods count the changes, server by server :
   * - from a start CN
   * - to (inclusive) an end point (the provided endCN).
   * @param startCN The provided start changeNumber.
   * @param endCN The provided end change number.
   * @return The number of changes between startTime and endCN.
   */
  public long getEligibleCount(ChangeNumber startCN, ChangeNumber endCN)
  {
    long res = 0;

    // Parses the dbState of the domain , server by server
    ServerState dbState = this.getDbServerState();
    for (int sid : dbState) {
      // process one sid
      ChangeNumber lStartCN =
          new ChangeNumber(startCN.getTime(), startCN.getSeqnum(), sid);
      res += getCount(sid, lStartCN, endCN);
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
      if ((latest==0) || (latest<db.getLatestTrimDate()))
      {
        latest = db.getLatestTrimDate();
      }
    }
    return latest;
  }
}
