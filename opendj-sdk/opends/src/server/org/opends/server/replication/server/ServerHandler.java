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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 */
package org.opends.server.replication.server;

import org.opends.messages.*;

import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.replication.common.StatusMachine.*;

import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.opends.server.admin.std.server.MonitorProviderCfg;
import org.opends.server.api.MonitorProvider;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.replication.common.AssuredMode;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.common.DSInfo;
import org.opends.server.replication.common.RSInfo;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.common.ServerStatus;
import org.opends.server.replication.common.StatusMachine;
import org.opends.server.replication.common.StatusMachineEvent;
import org.opends.server.replication.protocol.*;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeBuilder;
import org.opends.server.types.Attributes;
import org.opends.server.types.InitializationException;
import org.opends.server.util.TimeThread;

/**
 * This class defines a server handler, which handles all interaction with a
 * peer server (RS or DS).
 */
public class ServerHandler extends MonitorProvider<MonitorProviderCfg>
{

  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();
  /**
   * Time during which the server will wait for existing thread to stop
   * during the shutdownWriter.
   */
  private static final int SHUTDOWN_JOIN_TIMEOUT = 30000;

  /*
   * Properties, filled if remote server is either a DS or a RS
   */
  private short serverId;
  private ProtocolSession session;
  private final MsgQueue msgQueue = new MsgQueue();
  private MsgQueue lateQueue = new MsgQueue();
  private ReplicationServerDomain replicationServerDomain = null;
  private String serverURL;

  // Number of update sent to the server
  private int outCount = 0;
  // Number of updates received from the server
  private int inCount = 0;

  // Number of updates received from the server in assured safe read mode
  private int assuredSrReceivedUpdates = 0;
  // Number of updates received from the server in assured safe read mode that
  // timed out
  private AtomicInteger assuredSrReceivedUpdatesTimeout = new AtomicInteger();
  // Number of updates sent to the server in assured safe read mode
  private int assuredSrSentUpdates = 0;
  // Number of updates sent to the server in assured safe read mode that timed
  // out
  private AtomicInteger assuredSrSentUpdatesTimeout = new AtomicInteger();
  // Number of updates received from the server in assured safe data mode
  private int assuredSdReceivedUpdates = 0;
  // Number of updates received from the server in assured safe data mode that
  // timed out
  private AtomicInteger assuredSdReceivedUpdatesTimeout = new AtomicInteger();
  // Number of updates sent to the server in assured safe data mode
  private int assuredSdSentUpdates = 0;
  // Number of updates sent to the server in assured safe data mode that timed
  // out
  private AtomicInteger assuredSdSentUpdatesTimeout = new AtomicInteger();

  private int maxReceiveQueue = 0;
  private int maxSendQueue = 0;
  private int maxReceiveDelay = 0;
  private int maxSendDelay = 0;
  private int maxQueueSize = 5000;
  private int maxQueueBytesSize = maxQueueSize * 100;
  private int restartReceiveQueue;
  private int restartSendQueue;
  private int restartReceiveDelay;
  private int restartSendDelay;
  private boolean serverIsLDAPserver;
  private boolean following = false;
  private ServerState serverState;
  private boolean activeWriter = true;
  private ServerWriter writer = null;
  private String baseDn = null;
  private int rcvWindow;
  private int rcvWindowSizeHalf;
  private int maxRcvWindow;
  private ServerReader reader;
  private Semaphore sendWindow;
  private int sendWindowSize;
  private boolean flowControl = false; // indicate that the server is
  // flow controlled and should
  // be stopped from sending messages.

  private int saturationCount = 0;
  private short replicationServerId;
  private short protocolVersion = -1;
  private long generationId = -1;

  // Group id of this remote server
  private byte groupId = (byte) -1;

  /*
   * Properties filled only if remote server is a DS
   */

  // Status of this DS (only used if this server handler represents a DS)
  private ServerStatus status = ServerStatus.INVALID_STATUS;
  // Referrals URLs this DS is exporting
  private List<String> refUrls = new ArrayList<String>();
  // Assured replication enabled on DS or not
  private boolean assuredFlag = false;
  // DS assured mode (relevant if assured replication enabled)
  private AssuredMode assuredMode = AssuredMode.SAFE_DATA_MODE;
  // DS safe data level (relevant if assured mode is safe data)
  private byte safeDataLevel = (byte) -1;

  /*
   * Properties filled only if remote server is a RS
   */
  private String serverAddressURL;
  /**
   * When this Handler is related to a remote replication server
   * this collection will contain as many elements as there are
   * LDAP servers connected to the remote replication server.
   */
  private final Map<Short, LightweightServerHandler> directoryServers =
    new ConcurrentHashMap<Short, LightweightServerHandler>();
  /**
   * The time in milliseconds between heartbeats from the replication
   * server.  Zero means heartbeats are off.
   */
  private long heartbeatInterval = 0;
  /**
   * The thread that will send heartbeats.
   */
  HeartbeatThread heartbeatThread = null;
  /**
   * Set when ServerWriter is stopping.
   */
  private boolean shutdownWriter = false;
  /**
   * Set when ServerHandler is stopping.
   */
  private AtomicBoolean shuttingDown = new AtomicBoolean(false);

  /**
   * Creates a new server handler instance with the provided socket.
   *
   * @param session The ProtocolSession used by the ServerHandler to
   *                 communicate with the remote entity.
   * @param queueSize The maximum number of update that will be kept
   *                  in memory by this ServerHandler.
   */
  public ServerHandler(ProtocolSession session, int queueSize)
  {
    super("Server Handler");
    this.session = session;
    this.maxQueueSize = queueSize;
    this.maxQueueBytesSize = queueSize * 100;
    this.protocolVersion = ProtocolVersion.getCurrentVersion();
  }

  /**
   * Creates a DSInfo structure representing this remote DS.
   * @return The DSInfo structure representing this remote DS
   */
  public DSInfo toDSInfo()
  {
    DSInfo dsInfo = new DSInfo(serverId, replicationServerId, generationId,
      status, assuredFlag, assuredMode, safeDataLevel, groupId, refUrls);

    return dsInfo;
  }

  /**
   * Creates a RSInfo structure representing this remote RS.
   * @return The RSInfo structure representing this remote RS
   */
  public RSInfo toRSInfo()
  {
    RSInfo rsInfo = new RSInfo(serverId, generationId, groupId);

    return rsInfo;
  }

  /**
   * Do the handshake with either the DS or RS and then create the reader and
   * writer thread.
   *
   * There are 2 possible handshake sequences: DS<->RS and RS<->RS. Each one are
   * divided into 2 logical consecutive phases (phase 1 and phase 2):
   *
   * DS<->RS (DS (always initiating connection) always sends first message):
   * -------
   *
   * phase 1:
   * DS --- ServerStartMsg ---> RS
   * DS <--- ReplServerStartMsg --- RS
   * phase 2:
   * DS --- StartSessionMsg ---> RS
   * DS <--- TopologyMsg --- RS
   *
   * RS<->RS (RS initiating connection always sends first message):
   * -------
   *
   * phase 1:
   * RS1 --- ReplServerStartMsg ---> RS2
   * RS1 <--- ReplServerStartMsg --- RS2
   * phase 2:
   * RS1 --- TopologyMsg ---> RS2
   * RS1 <--- TopologyMsg --- RS2
   *
   * @param baseDn baseDn of the ServerHandler when this is an outgoing conn.
   *               null if this is an incoming connection (listen).
   * @param replicationServerId The identifier of the replicationServer that
   *                            creates this server handler.
   * @param replicationServerURL The URL of the replicationServer that creates
   *                             this server handler.
   * @param windowSize the window size that this server handler must use.
   * @param sslEncryption For outgoing connections indicates whether encryption
   *                      should be used after the exchange of start messages.
   *                      Ignored for incoming connections.
   * @param replicationServer the ReplicationServer that created this server
   *                          handler.
   */
  public void start(String baseDn, short replicationServerId,
    String replicationServerURL,
    int windowSize, boolean sslEncryption,
    ReplicationServer replicationServer)
  {

    // The handshake phase must be done by blocking any access to structures
    // keeping info on connected servers, so that one can safely check for
    // pre-existence of a server, send a coherent snapshot of known topology
    // to peers, update the local view of the topology...
    //
    // For instance a kind of problem could be that while we connect with a
    // peer RS, a DS is connecting at the same time and we could publish the
    // connected DSs to the peer RS forgetting this last DS in the TopologyMsg.
    //
    // This method and every others that need to read/make changes to the
    // structures holding topology for the domain should:
    // - call ReplicationServerDomain.lock()
    // - read/modify structures
    // - call ReplicationServerDomain.release()
    //
    // More information is provided in comment of ReplicationServerDomain.lock()

    // If domain already exists, lock it until handshake is finished otherwise
    // it will be created and locked later in the method
    if (baseDn != null)
    {
      ReplicationServerDomain rsd =
        replicationServer.getReplicationServerDomain(baseDn, false);
      if (rsd != null)
      {
        try
        {
          rsd.lock();
        } catch (InterruptedException ex)
        {
          // Thread interrupted, return.
          return;
        }
      }
    }

    long oldGenerationId = -100;

    if (debugEnabled())
      TRACER.debugInfo("In " + replicationServer.getMonitorInstanceName() +
        " starts a new LS or RS " +
        ((baseDn == null) ? "incoming connection" : "outgoing connection"));

    this.replicationServerId = replicationServerId;
    rcvWindowSizeHalf = windowSize / 2;
    maxRcvWindow = windowSize;
    rcvWindow = windowSize;
    long localGenerationId = -1;
    ReplServerStartMsg outReplServerStartMsg = null;

    /**
     * This boolean prevents from logging a polluting error when connection\
     * aborted from a DS that wanted only to perform handshake phase 1 in order
     * to determine the best suitable RS:
     * 1) -> ServerStartMsg
     * 2) <- ReplServerStartMsg
     * 3) connection closure
     */
    boolean log_error_message = true;

    try
    {
      /*
       * PROCEDE WITH FIRST PHASE OF HANDSHAKE:
       * ServerStartMsg then ReplServerStartMsg (with a DS)
       * OR
       * ReplServerStartMsg then ReplServerStartMsg (with a RS)
       */

      if (baseDn != null) // Outgoing connection

      {
        // This is an outgoing connection. Publish our start message.
        this.baseDn = baseDn;

        // Get or create the ReplicationServerDomain
        replicationServerDomain =
          replicationServer.getReplicationServerDomain(baseDn, true);
        if (!replicationServerDomain.hasLock())
        {
          try
          {
            replicationServerDomain.lock();
          } catch (InterruptedException ex)
          {
            // Thread interrupted, return.
            return;
          }
        }
        localGenerationId = replicationServerDomain.getGenerationId();

        ServerState localServerState =
          replicationServerDomain.getDbServerState();
        outReplServerStartMsg = new ReplServerStartMsg(replicationServerId,
          replicationServerURL,
          baseDn, windowSize, localServerState,
          protocolVersion, localGenerationId,
          sslEncryption,
          replicationServer.getGroupId(),
          replicationServerDomain.
          getReplicationServer().getDegradedStatusThreshold());

        session.publish(outReplServerStartMsg);
      }

      // Wait and process ServerStartMsg or ReplServerStartMsg
      ReplicationMsg msg = session.receive();
      if (msg instanceof ServerStartMsg)
      {
        // The remote server is an LDAP Server.
        ServerStartMsg serverStartMsg = (ServerStartMsg) msg;

        generationId = serverStartMsg.getGenerationId();
        protocolVersion = ProtocolVersion.minWithCurrent(
          serverStartMsg.getVersion());
        serverId = serverStartMsg.getServerId();
        serverURL = serverStartMsg.getServerURL();
        this.baseDn = serverStartMsg.getBaseDn();
        this.serverState = serverStartMsg.getServerState();
        this.groupId = serverStartMsg.getGroupId();

        maxReceiveDelay = serverStartMsg.getMaxReceiveDelay();
        maxReceiveQueue = serverStartMsg.getMaxReceiveQueue();
        maxSendDelay = serverStartMsg.getMaxSendDelay();
        maxSendQueue = serverStartMsg.getMaxSendQueue();
        heartbeatInterval = serverStartMsg.getHeartbeatInterval();

        // The session initiator decides whether to use SSL.
        sslEncryption = serverStartMsg.getSSLEncryption();

        if (maxReceiveQueue > 0)
          restartReceiveQueue = (maxReceiveQueue > 1000 ? maxReceiveQueue -
            200 : maxReceiveQueue * 8 / 10);
        else
          restartReceiveQueue = 0;

        if (maxSendQueue > 0)
          restartSendQueue =
            (maxSendQueue > 1000 ? maxSendQueue - 200 : maxSendQueue * 8 /
            10);
        else
          restartSendQueue = 0;

        if (maxReceiveDelay > 0)
          restartReceiveDelay = (maxReceiveDelay > 10 ? maxReceiveDelay - 1
            : maxReceiveDelay);
        else
          restartReceiveDelay = 0;

        if (maxSendDelay > 0)
          restartSendDelay =
            (maxSendDelay > 10 ? maxSendDelay - 1 : maxSendDelay);
        else
          restartSendDelay = 0;

        if (heartbeatInterval < 0)
        {
          heartbeatInterval = 0;
        }

        serverIsLDAPserver = true;

        // Get or Create the ReplicationServerDomain
        replicationServerDomain =
          replicationServer.getReplicationServerDomain(this.baseDn, true);

        // Hack to be sure that if a server disconnects and reconnect, we
        // let the reader thread see the closure and cleanup any reference
        // to old connection
        replicationServerDomain.waitDisconnection(serverStartMsg.getServerId());

        if (!replicationServerDomain.hasLock())
        {
          try
          {
            replicationServerDomain.lock();
          } catch (InterruptedException ex)
          {
            // Thread interrupted, return.
            return;
          }
        }

        // Duplicate server ?
        if (!replicationServerDomain.checkForDuplicateDS(this))
        {
          closeSession(null);
          if ((replicationServerDomain != null) &&
            replicationServerDomain.hasLock())
            replicationServerDomain.release();
          return;
        }

        localGenerationId = replicationServerDomain.getGenerationId();

        ServerState localServerState =
          replicationServerDomain.getDbServerState();
        // This an incoming connection. Publish our start message
        ReplServerStartMsg replServerStartMsg =
          new ReplServerStartMsg(replicationServerId, replicationServerURL,
          this.baseDn, windowSize, localServerState,
          protocolVersion, localGenerationId,
          sslEncryption,
          replicationServer.getGroupId(),
          replicationServerDomain.
          getReplicationServer().getDegradedStatusThreshold());
        session.publish(replServerStartMsg);
        sendWindowSize = serverStartMsg.getWindowSize();

        /* Until here session is encrypted then it depends on the
        negotiation */
        if (!sslEncryption)
        {
          session.stopEncryption();
        }

        if (debugEnabled())
        {
          TRACER.debugInfo("In " +
            replicationServerDomain.getReplicationServer().
            getMonitorInstanceName() + ":" +
            "\nSH HANDSHAKE RECEIVED:\n" + serverStartMsg.toString() +
            "\nAND REPLIED:\n" + replServerStartMsg.toString());
        }
      } else if (msg instanceof ReplServerStartMsg)
      {
        // The remote server is a replication server
        ReplServerStartMsg inReplServerStartMsg = (ReplServerStartMsg) msg;
        protocolVersion = ProtocolVersion.minWithCurrent(
          inReplServerStartMsg.getVersion());
        generationId = inReplServerStartMsg.getGenerationId();
        serverId = inReplServerStartMsg.getServerId();
        serverURL = inReplServerStartMsg.getServerURL();
        int separator = serverURL.lastIndexOf(':');
        serverAddressURL =
          session.getRemoteAddress() + ":" + serverURL.substring(separator +
          1);
        serverIsLDAPserver = false;
        if (protocolVersion > ProtocolVersion.REPLICATION_PROTOCOL_V1)
        {
          // We support connection from a V1 RS
          // Only V2 protocol has the group id in repl server start message
          this.groupId = inReplServerStartMsg.getGroupId();
        }
        this.baseDn = inReplServerStartMsg.getBaseDn();

        if (baseDn == null) // Reply to incoming RS

        {
          // Get or create the ReplicationServerDomain
          replicationServerDomain =
            replicationServer.getReplicationServerDomain(this.baseDn, true);
          if (!replicationServerDomain.hasLock())
          {
            try
            {
              /**
               * Take the lock on the domain.
               * WARNING: Here we try to acquire the lock with a timeout. This
               * is for preventing a deadlock that may happen if there are cross
               * connection attempts (for same domain) from this replication
               * server and from a peer one:
               * Here is the scenario:
               * - RS1 connect thread takes the domain lock and starts
               * connection to RS2
               * - at the same time RS2 connect thread takes his domain lock and
               * start connection to RS2
               * - RS2 listen thread starts processing received
               * ReplServerStartMsg from RS1 and wants to acquire the lock on
               * the domain (here) but cannot as RS2 connect thread already has
               * it
               * - RS1 listen thread starts processing received
               * ReplServerStartMsg from RS2 and wants to acquire the lock on
               * the domain (here) but cannot as RS1 connect thread already has
               * it
               * => Deadlock: 4 threads are locked.
               * So to prevent that in such situation, the listen threads here
               * will both timeout trying to acquire the lock. The random time
               * for the timeout should allow on connection attempt to be
               * aborted whereas the other one should have time to finish in the
               * same time.
               * Warning: the minimum time (3s) should be big enough to allow
               * normal situation connections to terminate. The added random
               * time should represent a big enough range so that the chance to
               * have one listen thread timing out a lot before the peer one is
               * great. When the first listen thread times out, the remote
               * connect thread should release the lock and allow the peer
               * listen thread to take the lock it was waiting for and process
               * the connection attempt.
               */
              Random random = new Random();
              int randomTime = random.nextInt(6); // Random from 0 to 5
              // Wait at least 3 seconds + (0 to 5 seconds)
              long timeout = (long) (3000 + ( randomTime * 1000 ) );
              boolean noTimeout = replicationServerDomain.tryLock(timeout);
              if (!noTimeout)
              {
                // Timeout
                Message message = NOTE_TIMEOUT_WHEN_CROSS_CONNECTION.get(
                  this.baseDn,
                  Short.toString(serverId),
                  Short.toString(replicationServer.getServerId()));
                closeSession(message);
                return;
              }
            } catch (InterruptedException ex)
            {
              // Thread interrupted, return.
              return;
            }
          }
          localGenerationId = replicationServerDomain.getGenerationId();
          ServerState domServerState =
            replicationServerDomain.getDbServerState();

          // The session initiator decides whether to use SSL.
          sslEncryption = inReplServerStartMsg.getSSLEncryption();

          // Publish our start message
          outReplServerStartMsg = new ReplServerStartMsg(replicationServerId,
            replicationServerURL,
            this.baseDn, windowSize, domServerState,
            protocolVersion,
            localGenerationId,
            sslEncryption,
            replicationServer.getGroupId(),
            replicationServerDomain.
            getReplicationServer().getDegradedStatusThreshold());

          if (protocolVersion > ProtocolVersion.REPLICATION_PROTOCOL_V1)
          {
            session.publish(outReplServerStartMsg);
          } else {
            // We support connection from a V1 RS, send PDU with V1 form
            session.publish(outReplServerStartMsg,
              ProtocolVersion.REPLICATION_PROTOCOL_V1);
          }

          if (debugEnabled())
          {
            TRACER.debugInfo("In " +
              replicationServerDomain.getReplicationServer().
              getMonitorInstanceName() + ":" +
              "\nSH HANDSHAKE RECEIVED:\n" + inReplServerStartMsg.toString() +
              "\nAND REPLIED:\n" + outReplServerStartMsg.toString());
          }
        } else
        {
          // Did the remote RS answer with the DN we provided him ?
          if (!(this.baseDn.equals(baseDn)))
          {
            Message message = ERR_RS_DN_DOES_NOT_MATCH.get(
              this.baseDn.toString(),
              baseDn.toString());
            closeSession(message);
            if ((replicationServerDomain != null) &&
              replicationServerDomain.hasLock())
              replicationServerDomain.release();
            return;
          }

          if (debugEnabled())
          {
            TRACER.debugInfo("In " +
              replicationServerDomain.getReplicationServer().
              getMonitorInstanceName() + ":" +
              "\nSH HANDSHAKE SENT:\n" + outReplServerStartMsg.toString() +
              "\nAND RECEIVED:\n" + inReplServerStartMsg.toString());
          }
        }
        this.serverState = inReplServerStartMsg.getServerState();
        sendWindowSize = inReplServerStartMsg.getWindowSize();

        // Duplicate server ?
        if (!replicationServerDomain.checkForDuplicateRS(this))
        {
          closeSession(null);
          if ((replicationServerDomain != null) &&
            replicationServerDomain.hasLock())
            replicationServerDomain.release();
          return;
        }

        /* Until here session is encrypted then it depends on the
        negociation */
        if (!sslEncryption)
        {
          session.stopEncryption();
        }
      } else
      {
        // We did not recognize the message, close session as what
        // can happen after is undetermined and we do not want the server to
        // be disturbed
        closeSession(null);
        if ((replicationServerDomain != null) &&
          replicationServerDomain.hasLock())
          replicationServerDomain.release();
        return;
      }

      if (protocolVersion > ProtocolVersion.REPLICATION_PROTOCOL_V1)
      { // Only protocol version above V1 has a phase 2 handshake

        /*
         * NOW PROCEDE WITH SECOND PHASE OF HANDSHAKE:
         * TopologyMsg then TopologyMsg (with a RS)
         * OR
         * StartSessionMsg then TopologyMsg (with a DS)
         */

        TopologyMsg outTopoMsg = null;

        if (baseDn != null) // Outgoing connection to a RS

        {
          // Send our own TopologyMsg to remote RS
          outTopoMsg = replicationServerDomain.createTopologyMsgForRS();
          session.publish(outTopoMsg);
        }

        // Wait and process TopologyMsg or StartSessionMsg
        log_error_message = false;
        ReplicationMsg msg2 = session.receive();
        log_error_message = true;
        if (msg2 instanceof TopologyMsg)
        {
          // Remote RS sent his topo msg
          TopologyMsg inTopoMsg = (TopologyMsg) msg2;

          // CONNECTION WITH A RS

          // if the remote RS and the local RS have the same genID
          // then it's ok and nothing else to do
          if (generationId == localGenerationId)
          {
            if (debugEnabled())
            {
              TRACER.debugInfo("In " +
                replicationServerDomain.getReplicationServer().
                getMonitorInstanceName() + " RS with serverID=" + serverId +
                " is connected with the right generation ID");
            }
          } else
          {
            if (localGenerationId > 0)
            {
              // if the local RS is initialized
              if (generationId > 0)
              {
                // if the remote RS is initialized
                if (generationId != localGenerationId)
                {
                  // if the 2 RS have different generationID
                  if (replicationServerDomain.getGenerationIdSavedStatus())
                  {
                    // if the present RS has received changes regarding its
                    //     gen ID and so won't change without a reset
                    // then  we are just degrading the peer.
                    Message message = NOTE_BAD_GENERATION_ID_FROM_RS.get(
                      this.baseDn,
                      Short.toString(serverId),
                      Long.toString(generationId),
                      Long.toString(localGenerationId));
                    logError(message);
                  } else
                  {
                    // The present RS has never received changes regarding its
                    // gen ID.
                    //
                    // Example case:
                    // - we are in RS1
                    // - RS2 has genId2 from LS2 (genId2 <=> no data in LS2)
                    // - RS1 has genId1 from LS1 /genId1 comes from data in
                    //   suffix
                    // - we are in RS1 and we receive a START msg from RS2
                    // - Each RS keeps its genID / is degraded and when LS2
                    //   will be populated from LS1 everything will become ok.
                    //
                    // Issue:
                    // FIXME : Would it be a good idea in some cases to just
                    //         set the gen ID received from the peer RS
                    //         specially if the peer has a non null state and
                    //         we have a nul state ?
                    // replicationServerDomain.
                    // setGenerationId(generationId, false);
                    Message message = NOTE_BAD_GENERATION_ID_FROM_RS.get(
                      this.baseDn,
                      Short.toString(serverId),
                      Long.toString(generationId),
                      Long.toString(localGenerationId));
                    logError(message);
                  }
                }
              } else
              {
                // The remote RS has no genId. We don't change anything for the
                // current RS.
              }
            } else
            {
              // The local RS is not initialized - take the one received
              // WARNING: Must be done before computing topo message to send
              // to peer server as topo message must embed valid generation id
              // for our server
              oldGenerationId =
                replicationServerDomain.setGenerationId(generationId, false);
            }
          }

          if (baseDn == null) // Reply to the RS (incoming connection)

          {
            // Send our own TopologyMsg to remote RS
            outTopoMsg = replicationServerDomain.createTopologyMsgForRS();
            session.publish(outTopoMsg);

            if (debugEnabled())
            {
              TRACER.debugInfo("In " +
                replicationServerDomain.getReplicationServer().
                getMonitorInstanceName() + ":" +
                "\nSH HANDSHAKE RECEIVED:\n" + inTopoMsg.toString() +
                "\nAND REPLIED:\n" + outTopoMsg.toString());
            }
          } else
          {
            if (debugEnabled())
            {
              TRACER.debugInfo("In " +
                replicationServerDomain.getReplicationServer().
                getMonitorInstanceName() + ":" +
                "\nSH HANDSHAKE SENT:\n" + outTopoMsg.toString() +
                "\nAND RECEIVED:\n" + inTopoMsg.toString());
            }
          }

          // Alright, connected with new RS (either outgoing or incoming
          // connection): store handler.
          Map<Short, ServerHandler> connectedRSs =
            replicationServerDomain.getConnectedRSs();
          connectedRSs.put(serverId, this);

          // Process TopologyMsg sent by remote RS: store matching new info
          // (this will also warn our connected DSs of the new received info)
          replicationServerDomain.receiveTopoInfoFromRS(inTopoMsg, this, false);

        } else if (msg2 instanceof StartSessionMsg)
        {
          // CONNECTION WITH A DS

          // Process StartSessionMsg sent by remote DS
          StartSessionMsg startSessionMsg = (StartSessionMsg) msg2;

          this.status = startSessionMsg.getStatus();
          // Sanity check: is it a valid initial status?
          if (!isValidInitialStatus(this.status))
          {
            Message mesg = ERR_RS_INVALID_INIT_STATUS.get(
              this.status.toString(), this.baseDn.toString(),
              Short.toString(serverId));
            closeSession(mesg);
            if ((replicationServerDomain != null) &&
              replicationServerDomain.hasLock())
              replicationServerDomain.release();
            return;
          }
          this.refUrls = startSessionMsg.getReferralsURLs();
          this.assuredFlag = startSessionMsg.isAssured();
          this.assuredMode = startSessionMsg.getAssuredMode();
          this.safeDataLevel = startSessionMsg.getSafeDataLevel();

          /*
           * If we have already a generationID set for the domain
           * then
           *   if the connecting replica has not the same
           *   then it is degraded locally and notified by an error message
           * else
           *   we set the generationID from the one received
           *   (unsaved yet on disk . will be set with the 1rst change
           * received)
           */
          if (localGenerationId > 0)
          {
            if (generationId != localGenerationId)
            {
              Message message = NOTE_BAD_GENERATION_ID_FROM_DS.get(
                this.baseDn,
                Short.toString(serverId),
                Long.toString(generationId),
                Long.toString(localGenerationId));
              logError(message);
            }
          } else
          {
            // We are an empty Replicationserver
            if ((generationId > 0) && (!serverState.isEmpty()))
            {
              // If the LDAP server has already sent changes
              // it is not expected to connect to an empty RS
              Message message = NOTE_BAD_GENERATION_ID_FROM_DS.get(
                this.baseDn,
                Short.toString(serverId),
                Long.toString(generationId),
                Long.toString(localGenerationId));
              logError(message);
            } else
            {
              // The local RS is not initialized - take the one received
              // WARNING: Must be done before computing topo message to send
              // to peer server as topo message must embed valid generation id
              // for our server
              oldGenerationId =
                replicationServerDomain.setGenerationId(generationId, false);
            }
          }

          // Send our own TopologyMsg to DS
          outTopoMsg = replicationServerDomain.createTopologyMsgForDS(
            this.serverId);
          session.publish(outTopoMsg);

          if (debugEnabled())
          {
            TRACER.debugInfo("In " +
              replicationServerDomain.getReplicationServer().
              getMonitorInstanceName() + ":" +
              "\nSH HANDSHAKE RECEIVED:\n" + startSessionMsg.toString() +
              "\nAND REPLIED:\n" + outTopoMsg.toString());
          }

          // Alright, connected with new DS: store handler.
          Map<Short, ServerHandler> connectedDSs =
            replicationServerDomain.getConnectedDSs();
          connectedDSs.put(serverId, this);

          // Tell peer DSs a new DS just connected to us
          // No need to resend topo msg to this just new DS so not null
          // argument
          replicationServerDomain.sendTopoInfoToDSs(this);
          // Tell peer RSs a new DS just connected to us
          replicationServerDomain.sendTopoInfoToRSs();
        } else
        {
          // We did not recognize the message, close session as what
          // can happen after is undetermined and we do not want the server to
          // be disturbed
          closeSession(null);
          if ((replicationServerDomain != null) &&
            replicationServerDomain.hasLock())
            replicationServerDomain.release();
          return;
        }
      } else
      {
        // Terminate connection from a V1 RS

        // if the remote RS and the local RS have the same genID
        // then it's ok and nothing else to do
        if (generationId == localGenerationId)
        {
          if (debugEnabled())
          {
            TRACER.debugInfo("In " +
              replicationServerDomain.getReplicationServer().
              getMonitorInstanceName() + " RS V1 with serverID=" + serverId +
              " is connected with the right generation ID");
          }
        } else
        {
          if (localGenerationId > 0)
          {
            // if the local RS is initialized
            if (generationId > 0)
            {
              // if the remote RS is initialized
              if (generationId != localGenerationId)
              {
                // if the 2 RS have different generationID
                if (replicationServerDomain.getGenerationIdSavedStatus())
                {
                  // if the present RS has received changes regarding its
                  //     gen ID and so won't change without a reset
                  // then  we are just degrading the peer.
                  Message message = NOTE_BAD_GENERATION_ID_FROM_RS.get(
                    this.baseDn,
                    Short.toString(serverId),
                    Long.toString(generationId),
                    Long.toString(localGenerationId));
                  logError(message);
                } else
                {
                  // The present RS has never received changes regarding its
                  // gen ID.
                  //
                  // Example case:
                  // - we are in RS1
                  // - RS2 has genId2 from LS2 (genId2 <=> no data in LS2)
                  // - RS1 has genId1 from LS1 /genId1 comes from data in
                  //   suffix
                  // - we are in RS1 and we receive a START msg from RS2
                  // - Each RS keeps its genID / is degraded and when LS2
                  //   will be populated from LS1 everything will become ok.
                  //
                  // Issue:
                  // FIXME : Would it be a good idea in some cases to just
                  //         set the gen ID received from the peer RS
                  //         specially if the peer has a non null state and
                  //         we have a nul state ?
                  // replicationServerDomain.
                  // setGenerationId(generationId, false);
                  Message message = NOTE_BAD_GENERATION_ID_FROM_RS.get(
                    this.baseDn,
                    Short.toString(serverId),
                    Long.toString(generationId),
                    Long.toString(localGenerationId));
                  logError(message);
                }
              }
            } else
            {
              // The remote RS has no genId. We don't change anything for the
              // current RS.
            }
          } else
          {
            // The local RS is not initialized - take the one received
            oldGenerationId =
              replicationServerDomain.setGenerationId(generationId, false);
          }
        }

        // Alright, connected with new incoming V1 RS: store handler.
        Map<Short, ServerHandler> connectedRSs =
          replicationServerDomain.getConnectedRSs();
        connectedRSs.put(serverId, this);

        // Note: the supported scenario for V1->V2 upgrade is to upgrade 1 by 1
        // all the servers of the topology. We prefer not not send a TopologyMsg
        // for giving partial/false information to the V2 servers as for
        // instance we don't have the connected DS of the V1 RS...When the V1
        // RS will be upgraded in his turn, topo info will be sent and accurate.
        // That way, there is  no risk to have false/incomplete information in
        // other servers.
      }

      /*
       * FINALIZE INITIALIZATION:
       * CREATE READER AND WRITER, HEARTBEAT SYSTEM AND UPDATE MONITORING
       * SYSTEM
       */

      // Disable timeout for next communications
      session.setSoTimeout(0);
      // sendWindow MUST be created before starting the writer
      sendWindow = new Semaphore(sendWindowSize);

      writer = new ServerWriter(session, serverId,
        this, replicationServerDomain);
      reader = new ServerReader(session, serverId,
        this, replicationServerDomain);

      reader.start();
      writer.start();

      // Create a thread to send heartbeat messages.
      if (heartbeatInterval > 0)
      {
        heartbeatThread = new HeartbeatThread(
          "Replication Heartbeat to DS " + serverURL + " " + serverId +
          " for " + this.baseDn + " in RS " + replicationServerId,
          session, heartbeatInterval / 3);
        heartbeatThread.start();
      }

      // Create the status analyzer for the domain if not already started
      if (serverIsLDAPserver)
      {
        if (!replicationServerDomain.isRunningStatusAnalyzer())
        {
          if (debugEnabled())
            TRACER.debugInfo("In " + replicationServerDomain.
              getReplicationServer().
              getMonitorInstanceName() +
              " SH for remote server " + this.getMonitorInstanceName() +
              " is starting status analyzer");
          replicationServerDomain.startStatusAnalyzer();
        }
      }

      DirectoryServer.deregisterMonitorProvider(getMonitorInstanceName());
      DirectoryServer.registerMonitorProvider(this);
    } catch (NotSupportedOldVersionPDUException e)
    {
      // We do not need to support DS V1 connection, we just accept RS V1
      // connection:
      // We just trash the message, log the event for debug purpose and close
      // the connection
      if (debugEnabled())
      TRACER.debugInfo("In " + replicationServer.getMonitorInstanceName() + ":"
        + e.getMessage());
      closeSession(null);
    } catch (Exception e)
    {
      // We do not want polluting error log if error is due to normal session
      // aborted after handshake phase one from a DS that is searching for best
      // suitable RS.
      if ( log_error_message || (baseDn != null) )
      {
        // some problem happened, reject the connection
        MessageBuilder mb = new MessageBuilder();
        mb.append(ERR_REPLICATION_SERVER_CONNECTION_ERROR.get(
          this.getMonitorInstanceName()));
        mb.append(": " + stackTraceToSingleLineString(e));
        closeSession(mb.toMessage());
      } else
      {
        closeSession(null);
      }

      // If generation id of domain was changed, set it back to old value
      // We may have changed it as it was -1 and we received a value >0 from
      // peer server and the last topo message sent may have failed being
      // sent: in that case retrieve old value of generation id for
      // replication server domain
      if (oldGenerationId != -100)
      {
        replicationServerDomain.setGenerationId(oldGenerationId, false);
      }
    }

    // Release domain
    if ((replicationServerDomain != null) &&
      replicationServerDomain.hasLock())
      replicationServerDomain.release();
  }

  /*
   * Close the session logging the passed error message
   * Log nothing if message is null.
   */
  private void closeSession(Message msg)
  {
    if (msg != null)
    {
      logError(msg);
    }
    try
    {
      session.close();
    } catch (IOException ee)
    {
      // ignore
    }
  }

  /**
   * get the Server Id.
   *
   * @return the ID of the server to which this object is linked
   */
  public short getServerId()
  {
    return serverId;
  }

  /**
   * Retrieves the Address URL for this server handler.
   *
   * @return  The Address URL for this server handler,
   *          in the form of an IP address and port separated by a colon.
   */
  public String getServerAddressURL()
  {
    return serverAddressURL;
  }

  /**
   * Retrieves the URL for this server handler.
   *
   * @return  The URL for this server handler, in the form of an address and
   *          port separated by a colon.
   */
  public String getServerURL()
  {
    return serverURL;
  }

  /**
   * Increase the counter of updates sent to the server.
   */
  public void incrementOutCount()
  {
    outCount++;
  }

  /**
   * Increase the counter of update received from the server.
   */
  public void incrementInCount()
  {
    inCount++;
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
   * Get the count of updates sent to this server.
   * @return  The count of update sent to this server.
   */
  public int getOutCount()
  {
    return outCount;
  }

  /**
   * Get the number of updates received from the server in assured safe read
   * mode.
   * @return The number of updates received from the server in assured safe read
   * mode
   */
  public int getAssuredSrReceivedUpdates()
  {
    return assuredSrReceivedUpdates;
  }

  /**
   * Get the number of updates received from the server in assured safe read
   * mode that timed out.
   * @return The number of updates received from the server in assured safe read
   * mode that timed out.
   */
  public AtomicInteger getAssuredSrReceivedUpdatesTimeout()
  {
    return assuredSrReceivedUpdatesTimeout;
  }

  /**
   * Get the number of updates sent to the server in assured safe read mode.
   * @return The number of updates sent to the server in assured safe read mode
   */
  public int getAssuredSrSentUpdates()
  {
    return assuredSrSentUpdates;
  }

  /**
   * Get the number of updates sent to the server in assured safe read mode that
   * timed out.
   * @return The number of updates sent to the server in assured safe read mode
   * that timed out.
   */
  public AtomicInteger getAssuredSrSentUpdatesTimeout()
  {
    return assuredSrSentUpdatesTimeout;
  }

    /**
   * Get the number of updates received from the server in assured safe data
   * mode.
   * @return The number of updates received from the server in assured safe data
   * mode
   */
  public int getAssuredSdReceivedUpdates()
  {
    return assuredSdReceivedUpdates;
  }

  /**
   * Get the number of updates received from the server in assured safe data
   * mode that timed out.
   * @return The number of updates received from the server in assured safe data
   * mode that timed out.
   */
  public AtomicInteger getAssuredSdReceivedUpdatesTimeout()
  {
    return assuredSdReceivedUpdatesTimeout;
  }

  /**
   * Get the number of updates sent to the server in assured safe data mode.
   * @return The number of updates sent to the server in assured safe data mode
   */
  public int getAssuredSdSentUpdates()
  {
    return assuredSdSentUpdates;
  }

  /**
   * Get the number of updates sent to the server in assured safe data mode that
   * timed out.
   * @return The number of updates sent to the server in assured safe data mode
   * that timed out.
   */
  public AtomicInteger getAssuredSdSentUpdatesTimeout()
  {
    return assuredSdSentUpdatesTimeout;
  }

  /**
   * Increment the number of updates received from the server in assured safe
   * read mode.
   */
  public void incrementAssuredSrReceivedUpdates()
  {
    assuredSrReceivedUpdates++;
  }

  /**
   * Increment the number of updates received from the server in assured safe
   * read mode that timed out.
   */
  public void incrementAssuredSrReceivedUpdatesTimeout()
  {
    assuredSrReceivedUpdatesTimeout.incrementAndGet();
  }

  /**
   * Increment the number of updates sent to the server in assured safe read
   * mode.
   */
  public void incrementAssuredSrSentUpdates()
  {
    assuredSrSentUpdates++;
  }

  /**
   * Increment the number of updates sent to the server in assured safe read
   * mode that timed out.
   */
  public void incrementAssuredSrSentUpdatesTimeout()
  {
    assuredSrSentUpdatesTimeout.incrementAndGet();
  }

  /**
   * Increment the number of updates received from the server in assured safe
   * data mode.
   */
  public void incrementAssuredSdReceivedUpdates()
  {
    assuredSdReceivedUpdates++;
  }

  /**
   * Increment the number of updates received from the server in assured safe
   * data mode that timed out.
   */
  public void incrementAssuredSdReceivedUpdatesTimeout()
  {
    assuredSdReceivedUpdatesTimeout.incrementAndGet();
  }

  /**
   * Increment the number of updates sent to the server in assured safe data
   * mode.
   */
  public void incrementAssuredSdSentUpdates()
  {
    assuredSdSentUpdates++;
  }

  /**
   * Increment the number of updates sent to the server in assured safe data
   * mode that timed out.
   */
  public void incrementAssuredSdSentUpdatesTimeout()
  {
    assuredSdSentUpdatesTimeout.incrementAndGet();
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
    ServerHandler sourceHandler)
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
  public boolean restartAfterSaturation(ServerHandler source)
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
   * Check if the server associated to this ServerHandler is a replication
   * server.
   * @return true if the server associated to this ServerHandler is a
   *         replication server.
   */
  public boolean isReplicationServer()
  {
    return (!serverIsLDAPserver);
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
   * Check if the LDAP server can follow the speed of the other servers.
   * @return true when the server has all the not yet sent changes
   *         in its queue.
   */
  public boolean isFollowing()
  {
    return following;
  }

  /**
   * Set the following flag of this server.
   * @param following the value that should be set.
   */
  public void setFollowing(boolean following)
  {
    this.following = following;
  }

  /**
   * Add an update to the list of updates that must be sent to the server
   * managed by this ServerHandler.
   *
   * @param update The update that must be added to the list of updates.
   * @param sourceHandler The server that sent the update.
   */
  public void add(UpdateMsg update, ServerHandler sourceHandler)
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

  private void setSaturated(boolean value)
  {
    flowControl = value;
  }

  /**
   * Select the next update that must be sent to the server managed by this
   * ServerHandler.
   *
   * @return the next update that must be sent to the server managed by this
   *         ServerHandler.
   */
  public UpdateMsg take()
  {
    boolean interrupted = true;
    UpdateMsg msg = getnextMessage();

    /*
     * When we remove a message from the queue we need to check if another
     * server is waiting in flow control because this queue was too long.
     * This check might cause a performance penalty an therefore it
     * is not done for every message removed but only every few messages.
     */
    if (++saturationCount > 10)
    {
      saturationCount = 0;
      try
      {
        replicationServerDomain.checkAllSaturation();
      } catch (IOException e)
      {
      }
    }
    boolean acquired = false;
    do
    {
      try
      {
        acquired = sendWindow.tryAcquire((long) 500, TimeUnit.MILLISECONDS);
        interrupted = false;
      } catch (InterruptedException e)
      {
        // loop until not interrupted
      }
    } while (((interrupted) || (!acquired)) && (!shutdownWriter));
    if (msg != null)
    {
      incrementOutCount();
      if (msg.isAssured())
      {
        if (msg.getAssuredMode() == AssuredMode.SAFE_READ_MODE)
        {
          incrementAssuredSrSentUpdates();
        } else
        {
          if (!isLDAPserver())
            incrementAssuredSdSentUpdates();
        }
      }
    }
    return msg;
  }

  /**
   * Get the next update that must be sent to the server
   * from the message queue or from the database.
   *
   * @return The next update that must be sent to the server.
   */
  private UpdateMsg getnextMessage()
  {
    UpdateMsg msg;
    while (activeWriter == true)
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
            while (msgQueue.isEmpty())
            {
              msgQueue.wait(500);
              if (!activeWriter)
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
   * Get the state of this server.
   *
   * @return ServerState the state for this server..
   */
  public ServerState getServerState()
  {
    return serverState;
  }

  /**
   * Sends an ack message to the server represented by this object.
   *
   * @param ack The ack message to be sent.
   * @throws IOException In case of Exception thrown sending the ack.
   */
  public void sendAck(AckMsg ack) throws IOException
  {
    session.publish(ack);
  }

  /**
   * Check type of server handled.
   *
   * @return true if the handled server is an LDAP server.
   *         false if the handled server is a replicationServer
   */
  public boolean isLDAPserver()
  {
    return serverIsLDAPserver;
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
   * Retrieves the name of this monitor provider.  It should be unique among all
   * monitor providers, including all instances of the same monitor provider.
   *
   * @return  The name of this monitor provider.
   */
  @Override
  public String getMonitorInstanceName()
  {
    String str = serverURL + " " + String.valueOf(serverId);

    if (serverIsLDAPserver)
      return "Connected Replica " + str +
                ",cn=" + replicationServerDomain.getMonitorInstanceName();
    else
      return "Connected Replication Server " + str +
                ",cn=" + replicationServerDomain.getMonitorInstanceName();
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
    if (serverIsLDAPserver)
    {
      attributes.add(Attributes.create("replica", serverURL));
      attributes.add(Attributes.create("connected-to",
          this.replicationServerDomain.getReplicationServer()
              .getMonitorInstanceName()));

    }
    else
    {
      attributes.add(Attributes.create("Replication-Server",
          serverURL));
    }
    attributes.add(Attributes.create("server-id", String
        .valueOf(serverId)));
    attributes.add(Attributes.create("domain-name", baseDn.toString()));

    try
    {
      MonitorData md;
      md = replicationServerDomain.computeMonitorData();

      if (serverIsLDAPserver)
      {
        // Oldest missing update
        Long approxFirstMissingDate = md.getApproxFirstMissingDate(serverId);
        if ((approxFirstMissingDate != null) && (approxFirstMissingDate > 0))
        {
          Date date = new Date(approxFirstMissingDate);
          attributes.add(Attributes.create(
              "approx-older-change-not-synchronized", date.toString()));
          attributes.add(Attributes.create(
              "approx-older-change-not-synchronized-millis", String
              .valueOf(approxFirstMissingDate)));
        }

        // Missing changes
        long missingChanges = md.getMissingChanges(serverId);
        attributes.add(Attributes.create("missing-changes", String
            .valueOf(missingChanges)));

        // Replication delay
        long delay = md.getApproxDelay(serverId);
        attributes.add(Attributes.create("approximate-delay", String
            .valueOf(delay)));
      }
      else
      {
        // Missing changes
        long missingChanges = md.getMissingChangesRS(serverId);
        attributes.add(Attributes.create("missing-changes", String
            .valueOf(missingChanges)));
      }
    }
    catch (Exception e)
    {
      // TODO: improve the log
      // We failed retrieving the remote monitor data.
      attributes.add(Attributes.create("error",
          stackTraceToSingleLineString(e)));
    }

    attributes.add(
        Attributes.create("queue-size", String.valueOf(msgQueue.count())));
    attributes.add(
        Attributes.create(
            "queue-size-bytes", String.valueOf(msgQueue.bytesCount())));
    attributes.add(
        Attributes.create(
            "following", String.valueOf(following)));

    // Deprecated
    attributes.add(Attributes.create("max-waiting-changes", String
        .valueOf(maxQueueSize)));
    attributes.add(Attributes.create("sent-updates", String
        .valueOf(getOutCount())));
    attributes.add(Attributes.create("received-updates", String
        .valueOf(getInCount())));

    // Assured counters
    attributes.add(Attributes.create("assured-sr-received-updates", String
        .valueOf(getAssuredSrReceivedUpdates())));
    attributes.add(Attributes.create("assured-sr-received-updates-timeout",
      String .valueOf(getAssuredSrReceivedUpdatesTimeout())));
    attributes.add(Attributes.create("assured-sr-sent-updates", String
        .valueOf(getAssuredSrSentUpdates())));
    attributes.add(Attributes.create("assured-sr-sent-updates-timeout", String
        .valueOf(getAssuredSrSentUpdatesTimeout())));
    attributes.add(Attributes.create("assured-sd-received-updates", String
        .valueOf(getAssuredSdReceivedUpdates())));
    if (!isLDAPserver())
    {
      attributes.add(Attributes.create("assured-sd-sent-updates",
        String.valueOf(getAssuredSdSentUpdates())));
      attributes.add(Attributes.create("assured-sd-sent-updates-timeout",
        String.valueOf(getAssuredSdSentUpdatesTimeout())));
    } else
    {
      attributes.add(Attributes.create("assured-sd-received-updates-timeout",
        String.valueOf(getAssuredSdReceivedUpdatesTimeout())));
    }

    // Window stats
    attributes.add(Attributes.create("max-send-window", String
        .valueOf(sendWindowSize)));
    attributes.add(Attributes.create("current-send-window", String
        .valueOf(sendWindow.availablePermits())));
    attributes.add(Attributes.create("max-rcv-window", String
        .valueOf(maxRcvWindow)));
    attributes.add(Attributes.create("current-rcv-window", String
        .valueOf(rcvWindow)));

    /* get the Server State */
    AttributeBuilder builder = new AttributeBuilder("server-state");
    for (String str : serverState.toStringSet())
    {
      builder.add(str);
    }
    attributes.add(builder.toAttribute());

    // Encryption
    attributes.add(Attributes.create("ssl-encryption", String
        .valueOf(session.isEncrypted())));

    // Data generation
    attributes.add(Attributes.create("generation-id", String
        .valueOf(generationId)));

    return attributes;
  }

  /**
   * Shutdown This ServerHandler.
   */
  public void shutdown()
  {
    /*
     * Shutdown ServerWriter
     */
    shutdownWriter = true;
    activeWriter = false;
    synchronized (msgQueue)
    {
      /* wake up the writer thread on an empty queue so that it disappear */
      msgQueue.clear();
      msgQueue.notify();
      msgQueue.notifyAll();
    }

    /*
     * Close session to end ServerReader or ServerWriter
     */
    try
    {
      session.close();
    } catch (IOException e)
    {
      // ignore.
    }

    /*
     * Stop the remote LSHandler
     */
    synchronized (directoryServers)
    {
      for (LightweightServerHandler lsh : directoryServers.values())
      {
        lsh.stopHandler();
      }
      directoryServers.clear();
    }

    /*
     * Stop the heartbeat thread.
     */
    if (heartbeatThread != null)
    {
      heartbeatThread.shutdown();
    }

    DirectoryServer.deregisterMonitorProvider(getMonitorInstanceName());

    /*
     * Be sure to wait for ServerWriter and ServerReader death
     * It does not matter if we try to stop a thread which is us (reader
     * or writer), but we must not wait for our own thread death.
     */
    try
    {
      if ((writer != null) && (!(Thread.currentThread().equals(writer))))
      {

        writer.join(SHUTDOWN_JOIN_TIMEOUT);
      }
      if ((reader != null) && (!(Thread.currentThread().equals(reader))))
      {
        reader.join(SHUTDOWN_JOIN_TIMEOUT);
      }
    } catch (InterruptedException e)
    {
      // don't try anymore to join and return.
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    String localString;
    if (serverId != 0)
    {
      if (serverIsLDAPserver)
        localString = "Directory Server ";
      else
        localString = "Replication Server ";


      localString += serverId + " " + serverURL + " " + baseDn;
    } else
      localString = "Unknown server";

    return localString;
  }

  /**
   * Decrement the protocol window, then check if it is necessary
   * to send a WindowMsg and send it.
   *
   * @throws IOException when the session becomes unavailable.
   */
  public synchronized void decAndCheckWindow() throws IOException
  {
    rcvWindow--;
    checkWindow();
  }

  /**
   * Check the protocol window and send WindowMsg if necessary.
   *
   * @throws IOException when the session becomes unavailable.
   */
  public synchronized void checkWindow() throws IOException
  {
    if (rcvWindow < rcvWindowSizeHalf)
    {
      if (flowControl)
      {
        if (replicationServerDomain.restartAfterSaturation(this))
        {
          flowControl = false;
        }
      }
      if (!flowControl)
      {
        WindowMsg msg = new WindowMsg(rcvWindowSizeHalf);
        session.publish(msg);
        rcvWindow += rcvWindowSizeHalf;
      }
    }
  }

  /**
   * Update the send window size based on the credit specified in the
   * given window message.
   *
   * @param windowMsg The Window Message containing the information
   *                  necessary for updating the window size.
   */
  public void updateWindow(WindowMsg windowMsg)
  {
    sendWindow.release(windowMsg.getNumAck());
  }

  /**
   * Get our heartbeat interval.
   * @return Our heartbeat interval.
   */
  public long getHeartbeatInterval()
  {
    return heartbeatInterval;
  }

  /**
   * Processes a routable message.
   *
   * @param msg The message to be processed.
   */
  public void process(RoutableMsg msg)
  {
    if (debugEnabled())
      TRACER.debugInfo("In " + replicationServerDomain.getReplicationServer().
        getMonitorInstanceName() +
        " SH for remote server " + this.getMonitorInstanceName() + ":" +
        "\nprocesses received msg:\n" + msg);
    replicationServerDomain.process(msg, this);
  }

  /**
   * Sends the provided TopologyMsg to the peer server.
   *
   * @param topoMsg The TopologyMsg message to be sent.
   * @throws IOException When it occurs while sending the message,
   *
   */
  public void sendTopoInfo(TopologyMsg topoMsg)
    throws IOException
  {
    // V1 Rs do not support the TopologyMsg
    if (protocolVersion > ProtocolVersion.REPLICATION_PROTOCOL_V1)
    {
      if (debugEnabled())
        TRACER.debugInfo("In " + replicationServerDomain.getReplicationServer().
          getMonitorInstanceName() +
          " SH for remote server " + this.getMonitorInstanceName() + ":" +
          "\nsends message:\n" + topoMsg);

      session.publish(topoMsg);
    }
  }

  /**
   * Stores topology information received from a peer RS and that must be kept
   * in RS handler.
   *
   * @param topoMsg The received topology message
   */
  public void receiveTopoInfoFromRS(TopologyMsg topoMsg)
  {
    // Store info for remote RS
    List<RSInfo> rsInfos = topoMsg.getRsList();
    // List should only contain RS info for sender
    RSInfo rsInfo = rsInfos.get(0);
    generationId = rsInfo.getGenerationId();
    groupId = rsInfo.getGroupId();

    /**
     * Store info for DSs connected to the peer RS
     */
    List<DSInfo> dsInfos = topoMsg.getDsList();

    synchronized (directoryServers)
    {
      // Removes the existing structures
      for (LightweightServerHandler lsh : directoryServers.values())
      {
        lsh.stopHandler();
      }
      directoryServers.clear();

      // Creates the new structure according to the message received.
      for (DSInfo dsInfo : dsInfos)
      {
        LightweightServerHandler lsh = new LightweightServerHandler(this,
            serverId, dsInfo.getDsId(), dsInfo.getGenerationId(),
            dsInfo.getGroupId(), dsInfo.getStatus(), dsInfo.getRefUrls(),
            dsInfo.isAssured(), dsInfo.getAssuredMode(),
            dsInfo.getSafeDataLevel());
        lsh.startHandler();
        directoryServers.put(lsh.getServerId(), lsh);
      }
    }
  }

  /**
   * Process message of a remote server changing his status.
   * @param csMsg The message containing the new status
   * @return The new server status of the DS
   */
  public ServerStatus processNewStatus(ChangeStatusMsg csMsg)
  {

    // Sanity check
    if (!serverIsLDAPserver)
    {
      Message msg =
        ERR_RECEIVED_CHANGE_STATUS_NOT_FROM_DS.get(baseDn.toString(),
        Short.toString(serverId), csMsg.toString());
      logError(msg);
      return ServerStatus.INVALID_STATUS;
    }

    // Get the status the DS just entered
    ServerStatus reqStatus = csMsg.getNewStatus();
    // Translate new status to a state machine event
    StatusMachineEvent event = StatusMachineEvent.statusToEvent(reqStatus);
    if (event == StatusMachineEvent.INVALID_EVENT)
    {
      Message msg = ERR_RS_INVALID_NEW_STATUS.get(reqStatus.toString(),
        baseDn.toString(), Short.toString(serverId));
      logError(msg);
      return ServerStatus.INVALID_STATUS;
    }

    // Check state machine allows this new status
    ServerStatus newStatus = StatusMachine.computeNewStatus(status, event);
    if (newStatus == ServerStatus.INVALID_STATUS)
    {
      Message msg = ERR_RS_CANNOT_CHANGE_STATUS.get(baseDn.toString(),
        Short.toString(serverId), status.toString(), event.toString());
      logError(msg);
      return ServerStatus.INVALID_STATUS;
    }

    status = newStatus;

    return status;
  }

  /**
   * Change the status according to the event generated from the status
   * analyzer.
   * @param event The event to be used for new status computation
   * @return The new status of the DS
   * @throws IOException When raised by the underlying session
   */
  public ServerStatus changeStatusFromStatusAnalyzer(StatusMachineEvent event)
    throws IOException
  {
    // Check state machine allows this new status (Sanity check)
    ServerStatus newStatus = StatusMachine.computeNewStatus(status, event);
    if (newStatus == ServerStatus.INVALID_STATUS)
    {
      Message msg = ERR_RS_CANNOT_CHANGE_STATUS.get(baseDn.toString(),
        Short.toString(serverId), status.toString(), event.toString());
      logError(msg);
      // Status analyzer must only change from NORMAL_STATUS to DEGRADED_STATUS
      // and vice versa. We may are being trying to change the status while for
      // instance another status has just been entered: e.g a full update has
      // just been engaged. In that case, just ignore attempt to change the
      // status
      return newStatus;
    }

    // Send message requesting to change the DS status
    ChangeStatusMsg csMsg = new ChangeStatusMsg(newStatus,
      ServerStatus.INVALID_STATUS);

    if (debugEnabled())
    {
      TRACER.debugInfo(
        "In RS " +
        replicationServerDomain.getReplicationServer().getServerId() +
        " Sending change status from status analyzer to " + getServerId() +
        " for baseDn " + baseDn + ":\n" + csMsg);
    }

    session.publish(csMsg);

    status = newStatus;

    return newStatus;
  }

  /**
   * When this handler is connected to a replication server, specifies if
   * a wanted server is connected to this replication server.
   *
   * @param wantedServer The server we want to know if it is connected
   * to the replication server represented by this handler.
   * @return boolean True is the wanted server is connected to the server
   * represented by this handler.
   */
  public boolean isRemoteLDAPServer(short wantedServer)
  {
    synchronized (directoryServers)
    {
      for (LightweightServerHandler server : directoryServers.values())
      {
        if (wantedServer == server.getServerId())
        {
          return true;
        }
      }
      return false;
    }
  }

  /**
   * When the handler is connected to a replication server, specifies the
   * replication server has remote LDAP servers connected to it.
   *
   * @return boolean True is the replication server has remote LDAP servers
   * connected to it.
   */
  public boolean hasRemoteLDAPServers()
  {
    synchronized (directoryServers)
    {
      return !directoryServers.isEmpty();
    }
  }

  /**
   * Send an InitializeRequestMessage to the server connected through this
   * handler.
   *
   * @param msg The message to be processed
   * @throws IOException when raised by the underlying session
   */
  public void send(RoutableMsg msg) throws IOException
  {
    if (debugEnabled())
      TRACER.debugInfo("In " +
        replicationServerDomain.getReplicationServer().
        getMonitorInstanceName() +
        " SH for remote server " + this.getMonitorInstanceName() + ":" +
        "\nsends message:\n" + msg);
    session.publish(msg);
  }

  /**
   * Send an ErrorMsg to the peer.
   *
   * @param errorMsg The message to be sent
   * @throws IOException when raised by the underlying session
   */
  public void sendError(ErrorMsg errorMsg) throws IOException
  {
    session.publish(errorMsg);
  }

  /**
   * Process the reception of a WindowProbeMsg message.
   *
   * @param  windowProbeMsg The message to process.
   *
   * @throws IOException    When the session becomes unavailable.
   */
  public void process(WindowProbeMsg windowProbeMsg) throws IOException
  {
    if (rcvWindow > 0)
    {
      // The LDAP server believes that its window is closed
      // while it is not, this means that some problem happened in the
      // window exchange procedure !
      // lets update the LDAP server with out current window size and hope
      // that everything will work better in the futur.
      // TODO also log an error message.
      WindowMsg msg = new WindowMsg(rcvWindow);
      session.publish(msg);
    } else
    {
      // Both the LDAP server and the replication server believes that the
      // window is closed. Lets check the flowcontrol in case we
      // can now resume operations and send a windowMessage if necessary.
      checkWindow();
    }
  }

  /**
   * Returns the value of generationId for that handler.
   * @return The value of the generationId.
   */
  public long getGenerationId()
  {
    return generationId;
  }

  /**
   * Sends a message containing a generationId to a peer server.
   * The peer is expected to be a replication server.
   *
   * @param  msg         The GenerationIdMessage message to be sent.
   * @throws IOException When it occurs while sending the message,
   *
   */
  public void forwardGenerationIdToRS(ResetGenerationIdMsg msg)
    throws IOException
  {
    session.publish(msg);
  }

  /**
   * Set a new generation ID.
   *
   * @param generationId The new generation ID
   *
   */
  public void setGenerationId(long generationId)
  {
    this.generationId = generationId;
  }

  /**
   * Returns the Replication Server Domain to which belongs this server handler.
   *
   * @return The replication server domain.
   */
  public ReplicationServerDomain getDomain()
  {
    return this.replicationServerDomain;
  }

  /**
   * Return a Set containing the servers known by this replicationServer.
   * @return a set containing the servers known by this replicationServer.
   */
  public Set<Short> getConnectedDirectoryServerIds()
  {
    synchronized (directoryServers)
    {
      return directoryServers.keySet();
    }
  }

  /**
   * Order the peer DS server to change his status or close the connection
   * according to the requested new generation id.
   * @param newGenId The new generation id to take into account
   * @throws IOException If IO error occurred.
   */
  public void changeStatusForResetGenId(long newGenId)
    throws IOException
  {
    StatusMachineEvent event = null;

    if (newGenId == -1)
    {
      // The generation id is being made invalid, let's put the DS
      // into BAD_GEN_ID_STATUS
      event = StatusMachineEvent.TO_BAD_GEN_ID_STATUS_EVENT;
    } else
    {
      if (newGenId == generationId)
      {
        if (status == ServerStatus.BAD_GEN_ID_STATUS)
        {
          // This server has the good new reference generation id.
          // Close connection with him to force his reconnection: DS will
          // reconnect in NORMAL_STATUS or DEGRADED_STATUS.

          if (debugEnabled())
          {
            TRACER.debugInfo(
              "In RS " +
              replicationServerDomain.getReplicationServer().getServerId() +
              ". Closing connection to DS " + getServerId() +
              " for baseDn " + baseDn + " to force reconnection as new local" +
              " generation id and remote one match and DS is in bad gen id: " +
              newGenId);
          }

          // Connection closure must not be done calling RSD.stopHandler() as it
          // would rewait the RSD lock that we already must have entering this
          // method. This would lead to a reentrant lock which we do not want.
          // So simply close the session, this will make the hang up appear
          // after the reader thread that took the RSD lock realeases it.
          try
          {
            if (session != null)
              session.close();
          } catch (IOException e)
          {
            // ignore
          }

          // NOT_CONNECTED_STATUS is the last one in RS session life: handler
          // will soon disappear after this method call...
          status = ServerStatus.NOT_CONNECTED_STATUS;
          return;
        } else
        {
          if (debugEnabled())
          {
            TRACER.debugInfo(
              "In RS " +
              replicationServerDomain.getReplicationServer().getServerId() +
              ". DS " + getServerId() + " for baseDn " + baseDn +
              " has already generation id " + newGenId +
              " so no ChangeStatusMsg sent to him.");
          }
          return;
        }
      } else
      {
        // This server has a bad generation id compared to new reference one,
        // let's put it into BAD_GEN_ID_STATUS
        event = StatusMachineEvent.TO_BAD_GEN_ID_STATUS_EVENT;
      }
    }

    if ((event == StatusMachineEvent.TO_BAD_GEN_ID_STATUS_EVENT) &&
      (status == ServerStatus.FULL_UPDATE_STATUS))
    {
      // Prevent useless error message (full update status cannot lead to bad
      // gen status)
      Message message = NOTE_BAD_GEN_ID_IN_FULL_UPDATE.get(
        Short.toString(replicationServerDomain.
        getReplicationServer().getServerId()),
        baseDn.toString(),
        Short.toString(serverId),
        Long.toString(generationId),
        Long.toString(newGenId));
      logError(message);
      return;
    }

    ServerStatus newStatus = StatusMachine.computeNewStatus(status, event);

    if (newStatus == ServerStatus.INVALID_STATUS)
    {
      Message msg = ERR_RS_CANNOT_CHANGE_STATUS.get(baseDn.toString(),
        Short.toString(serverId), status.toString(), event.toString());
      logError(msg);
      return;
    }

    // Send message requesting to change the DS status
    ChangeStatusMsg csMsg = new ChangeStatusMsg(newStatus,
      ServerStatus.INVALID_STATUS);

    if (debugEnabled())
    {
      TRACER.debugInfo(
        "In RS " +
        replicationServerDomain.getReplicationServer().getServerId() +
        " Sending change status for reset gen id to " + getServerId() +
        " for baseDn " + baseDn + ":\n" + csMsg);
    }

    session.publish(csMsg);

    status = newStatus;
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
   * Gets the status of the connected DS.
   * @return The status of the connected DS.
   */
  public ServerStatus getStatus()
  {
    return status;
  }

  /**
   * Gets the protocol version used with this remote server.
   * @return The protocol version used with this remote server.
   */
  public short getProtocolVersion()
  {
    return protocolVersion;
  }

  /**
   * Add the DSinfos of the connected Directory Servers
   * to the List of DSInfo provided as a parameter.
   *
   * @param dsInfos The List of DSInfo that should be updated
   *                with the DSInfo for the directoryServers
   *                connected to this ServerHandler.
   */
  public void addDSInfos(List<DSInfo> dsInfos)
  {
    synchronized (directoryServers)
    {
      for (LightweightServerHandler ls : directoryServers.values())
      {
        dsInfos.add(ls.toDSInfo());
      }
    }
  }

  /**
   * Gets the group id of the server represented by this object.
   * @return The group id of the server represented by this object.
   */
  public byte getGroupId()
  {
    return groupId;
  }
}
