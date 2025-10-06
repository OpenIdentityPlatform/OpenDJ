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
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 * Portions Copyright 2025 Wren Security.
 */
package org.opends.server.replication.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import net.jcip.annotations.GuardedBy;
import net.jcip.annotations.Immutable;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.server.config.server.ReplicationDomainCfg;
import org.forgerock.util.Utils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.DSInfo;
import org.opends.server.replication.common.RSInfo;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.common.ServerStatus;
import org.opends.server.replication.plugin.MultimasterReplication;
import org.opends.server.replication.protocol.ChangeStatusMsg;
import org.opends.server.replication.protocol.MonitorMsg;
import org.opends.server.replication.protocol.MonitorRequestMsg;
import org.opends.server.replication.protocol.ProtocolVersion;
import org.opends.server.replication.protocol.ReplServerStartDSMsg;
import org.opends.server.replication.protocol.ReplServerStartMsg;
import org.opends.server.replication.protocol.ReplSessionSecurity;
import org.opends.server.replication.protocol.ReplicationMsg;
import org.opends.server.replication.protocol.ServerStartMsg;
import org.opends.server.replication.protocol.Session;
import org.opends.server.replication.protocol.StartMsg;
import org.opends.server.replication.protocol.StartSessionMsg;
import org.opends.server.replication.protocol.StopMsg;
import org.opends.server.replication.protocol.TopologyMsg;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.protocol.WindowMsg;
import org.opends.server.replication.protocol.WindowProbeMsg;
import org.opends.server.types.HostPort;

import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.replication.protocol.ProtocolVersion.*;
import static org.opends.server.replication.server.ReplicationServer.*;
import static org.opends.server.util.StaticUtils.*;

/** The broker for Multi-master Replication. */
public class ReplicationBroker
{
  /**
   * Immutable class containing information about whether the broker is
   * connected to an RS and data associated to this connected RS.
   */
  @Immutable
  private static final class ConnectedRS
  {
    private static final ConnectedRS NO_CONNECTED_RS = new ConnectedRS(NO_CONNECTED_SERVER);

    /** The info of the RS we are connected to. */
    private final ReplicationServerInfo rsInfo;
    /** Contains a connected session to the RS if any exist, null otherwise. */
    private final Session session;
    private final HostPort replicationServer;

    private ConnectedRS(HostPort replicationServer)
    {
      this.rsInfo = null;
      this.session = null;
      this.replicationServer = replicationServer;
    }

    private ConnectedRS(ReplicationServerInfo rsInfo, Session session)
    {
      this.rsInfo = rsInfo;
      this.session = session;
      this.replicationServer = session != null
          ? session.getRemoteAddress()
          : NO_CONNECTED_SERVER;
    }

    private static ConnectedRS stopped()
    {
      return NO_CONNECTED_RS;
    }

    private static ConnectedRS noConnectedRS()
    {
      return NO_CONNECTED_RS;
    }

    public int getServerId()
    {
      return rsInfo != null ? rsInfo.getServerId() : -1;
    }

    private byte getGroupId()
    {
      return rsInfo != null ? rsInfo.getGroupId() : -1;
    }

    private boolean isConnected()
    {
      return session != null;
    }

    @Override
    public String toString()
    {
      final StringBuilder sb = new StringBuilder();
      toString(sb);
      return sb.toString();
    }

    public void toString(StringBuilder sb)
    {
      sb.append("connected=").append(isConnected()).append(", ");
      if (!isConnected())
      {
        sb.append("no connectedRS");
      }
      else
      {
        sb.append("connectedRS(serverId=").append(rsInfo.getServerId())
          .append(", serverUrl=").append(rsInfo.getServerURL())
          .append(", groupId=").append(rsInfo.getGroupId())
          .append(")");
      }
    }
  }

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();
  private volatile boolean shutdown;
  private final Object startStopLock = new Object();
  private volatile ReplicationDomainCfg config;
  /** String reported under CSN=monitor when there is no connected RS. */
  static final HostPort NO_CONNECTED_SERVER = new HostPort(null, 0);
  private final ServerState state;
  private Semaphore sendWindow;
  private int maxSendWindow;
  private int rcvWindow = 100;
  private int halfRcvWindow = rcvWindow / 2;
  private int timeout;
  private final ReplSessionSecurity replSessionSecurity;
  /**
   * The RS this DS is currently connected to.
   * <p>
   * Always use {@link #setConnectedRS(ConnectedRS)} to set a new
   * connected RS.
   */
  // @NotNull // for the reference
  private final AtomicReference<ConnectedRS> connectedRS = new AtomicReference<>(ConnectedRS.noConnectedRS());
  /** Our replication domain. */
  private final ReplicationDomain domain;
  /**
   * This object is used as a conditional event to be notified about
   * the reception of monitor information from the Replication Server.
   */
  private final AtomicBoolean monitorResponse = new AtomicBoolean(false);
  /**
   * A Map containing the ServerStates of all the replicas in the topology
   * as seen by the ReplicationServer the last time it was polled or the last
   * time it published monitoring information.
   */
  private Map<Integer, ServerState> replicaStates = new HashMap<>();
  /** A thread to monitor heartbeats on the session. */
  private HeartbeatMonitor heartbeatMonitor;
  /** The number of times the connection was lost. */
  private int numLostConnections;
  /**
   * When the broker cannot connect to any replication server
   * it log an error and keeps continuing every second.
   * This boolean is set when the first failure happens and is used
   * to avoid repeating the error message for further failure to connect
   * and to know that it is necessary to print a new message when the broker
   * finally succeed to connect.
   */
  private volatile boolean connectionError;
  private final Object connectPhaseLock = new Object();
  /** The thread that publishes messages to the RS containing the current change time of this DS. */
  private CTHeartbeatPublisherThread ctHeartbeatPublisherThread;
  /* Properties for the last topology info received from the network. */
  /** Contains the last known state of the replication topology. */
  private final AtomicReference<Topology> topology = new AtomicReference<>(new Topology());
  @GuardedBy("this")
  private volatile int updateDoneCount;
  private volatile boolean connectRequiresRecovery;

  /**
   * This integer defines when the best replication server checking algorithm
   * should be engaged.
   * Every time a monitoring message (each monitoring publisher period) is
   * received, it is incremented. When it reaches 2, we run the checking
   * algorithm to see if we must reconnect to another best replication server.
   * Then we reset the value to 0. But when a topology message is received, the
   * integer is reset to 0. This ensures that we wait at least one monitoring
   * publisher period before running the algorithm, but also that we wait at
   * least for a monitoring period after the last received topology message
   * (topology stabilization).
   */
  private int mustRunBestServerCheckingAlgorithm;

  /**
   * The monitor provider for this replication domain.
   * <p>
   * The name of the monitor includes the local address and must therefore be
   * re-registered every time the session is re-established or destroyed. The
   * monitor provider can only be created (i.e. non-null) if there is a
   * replication domain, which is not the case in unit tests.
   */
  private final ReplicationMonitor monitor;

  /**
   * Creates a new ReplicationServer Broker for a particular ReplicationDomain.
   *
   * @param replicationDomain The replication domain that is creating us.
   * @param state The ServerState that should be used by this broker
   *        when negotiating the session with the replicationServer.
   * @param config The configuration to use.
   * @param replSessionSecurity The session security configuration.
   */
  public ReplicationBroker(ReplicationDomain replicationDomain,
      ServerState state, ReplicationDomainCfg config,
      ReplSessionSecurity replSessionSecurity)
  {
    this.domain = replicationDomain;
    this.state = state;
    this.config = config;
    this.replSessionSecurity = replSessionSecurity;
    this.rcvWindow = getMaxRcvWindow();
    this.halfRcvWindow = rcvWindow / 2;
    this.shutdown = true;

    /*
     * Only create a monitor if there is a replication domain (this is not the
     * case in some unit tests).
     */
    this.monitor = replicationDomain != null ? new ReplicationMonitor(
        replicationDomain) : null;
    registerReplicationMonitor();
  }

  /** Start the ReplicationBroker. */
  public void start()
  {
    synchronized (startStopLock)
    {
      if (!shutdown)
      {
        return;
      }
      shutdown = false;
      this.rcvWindow = getMaxRcvWindow();
      connectAsDataServer();
    }
  }

  /**
   * Gets the group id of the RS we are connected to.
   * @return The group id of the RS we are connected to
   */
  public byte getRsGroupId()
  {
    return connectedRS.get().getGroupId();
  }

  /**
   * Gets the server id of the RS we are connected to.
   * @return The server id of the RS we are connected to
   */
  public int getRsServerId()
  {
    return connectedRS.get().getServerId();
  }

  /**
   * Gets the server id.
   * @return The server id
   */
  public int getServerId()
  {
    return config.getServerId();
  }

  private DN getBaseDN()
  {
    return config.getBaseDN();
  }

  private Set<String> getReplicationServerUrls()
  {
    return config.getReplicationServer();
  }

  private byte getGroupId()
  {
    return (byte) config.getGroupId();
  }

  /**
   * Gets the server id.
   * @return The server id
   */
  private long getGenerationID()
  {
    return domain.getGenerationID();
  }

  /**
   * Set the generation id - for test purpose.
   * @param generationID The generation id
   */
  public void setGenerationID(long generationID)
  {
    domain.setGenerationID(generationID);
  }

  /**
   * Compares 2 replication servers addresses and returns true if they both
   * represent the same replication server instance.
   * @param rs1Url Replication server 1 address
   * @param rs2Url Replication server 2 address
   * @return True if both replication server addresses represent the same
   * replication server instance, false otherwise.
   */
  private static boolean isSameReplicationServerUrl(String rs1Url,
      String rs2Url)
  {
    try
    {
      final HostPort hp1 = HostPort.valueOf(rs1Url);
      final HostPort hp2 = HostPort.valueOf(rs2Url);
      return hp1.isEquivalentTo(hp2);
    }
    catch (RuntimeException ex)
    {
      // Not a RS url or not a valid port number: should not happen
      return false;
    }
  }

  /**
   * Bag class for keeping info we get from a replication server in order to
   * compute the best one to connect to. This is in fact a wrapper to a
   * ReplServerStartMsg (V3) or a ReplServerStartDSMsg (V4). This can also be
   * updated with a info coming from received topology messages or monitoring
   * messages.
   */
  static class ReplicationServerInfo
  {
    private RSInfo rsInfo;
    private final short protocolVersion;
    private final DN baseDN;
    private final int windowSize;
    /** @NotNull */
    private final ServerState serverState;
    private final boolean sslEncryption;
    private final int degradedStatusThreshold;
    /** Keeps the 0 value if created with a ReplServerStartMsg. */
    private int connectedDSNumber;
    /** @NotNull */
    private Set<Integer> connectedDSs;
    /** Is this RS locally configured? (the RS is recognized as a usable server). */
    private boolean locallyConfigured = true;

    /**
     * Create a new instance of ReplicationServerInfo wrapping the passed
     * message.
     * @param msg LocalizableMessage to wrap.
     * @param newServerURL Override serverURL.
     * @return The new instance wrapping the passed message.
     * @throws IllegalArgumentException If the passed message has an unexpected
     *                                  type.
     */
    private static ReplicationServerInfo newInstance(
      ReplicationMsg msg, String newServerURL) throws IllegalArgumentException
    {
      final ReplicationServerInfo rsInfo = newInstance(msg);
      rsInfo.setServerURL(newServerURL);
      return rsInfo;
    }

    /**
     * Create a new instance of ReplicationServerInfo wrapping the passed
     * message.
     * @param msg LocalizableMessage to wrap.
     * @return The new instance wrapping the passed message.
     * @throws IllegalArgumentException If the passed message has an unexpected
     *                                  type.
     */
    static ReplicationServerInfo newInstance(ReplicationMsg msg)
        throws IllegalArgumentException
    {
      if (msg instanceof ReplServerStartMsg)
      {
        // RS uses protocol V3 or lower
        return new ReplicationServerInfo((ReplServerStartMsg) msg);
      }
      else if (msg instanceof ReplServerStartDSMsg)
      {
        // RS uses protocol V4 or higher
        return new ReplicationServerInfo((ReplServerStartDSMsg) msg);
      }

      // Unsupported message type: should not happen
      throw new IllegalArgumentException("Unexpected PDU type: "
          + msg.getClass().getName() + ":\n" + msg);
    }

    /**
     * Constructs a ReplicationServerInfo object wrapping a
     * {@link ReplServerStartMsg}.
     *
     * @param msg
     *          The {@link ReplServerStartMsg} this object will wrap.
     */
    private ReplicationServerInfo(ReplServerStartMsg msg)
    {
      this.protocolVersion = msg.getVersion();
      this.rsInfo = new RSInfo(msg.getServerId(), msg.getServerURL(),
          msg.getGenerationId(), msg.getGroupId(), 1);
      this.baseDN = msg.getBaseDN();
      this.windowSize = msg.getWindowSize();
      final ServerState ss = msg.getServerState();
      this.serverState = ss != null ? ss : new ServerState();
      this.sslEncryption = msg.getSSLEncryption();
      this.degradedStatusThreshold = msg.getDegradedStatusThreshold();
    }

    /**
     * Constructs a ReplicationServerInfo object wrapping a
     * {@link ReplServerStartDSMsg}.
     *
     * @param msg
     *          The {@link ReplServerStartDSMsg} this object will wrap.
     */
    private ReplicationServerInfo(ReplServerStartDSMsg msg)
    {
      this.rsInfo = new RSInfo(msg.getServerId(), msg.getServerURL(),
          msg.getGenerationId(), msg.getGroupId(), msg.getWeight());
      this.protocolVersion = msg.getVersion();
      this.baseDN = msg.getBaseDN();
      this.windowSize = msg.getWindowSize();
      final ServerState ss = msg.getServerState();
      this.serverState = ss != null ? ss : new ServerState();
      this.sslEncryption = msg.getSSLEncryption();
      this.degradedStatusThreshold = msg.getDegradedStatusThreshold();
      this.connectedDSNumber = msg.getConnectedDSNumber();
    }

    /**
     * Constructs a new replication server info with the passed RSInfo internal
     * values and the passed connected DSs.
     *
     * @param rsInfo
     *          The RSinfo to use for the update
     * @param connectedDSs
     *          The new connected DSs
     */
    ReplicationServerInfo(RSInfo rsInfo, Set<Integer> connectedDSs)
    {
      this.rsInfo =
          new RSInfo(rsInfo.getId(), rsInfo.getServerUrl(), rsInfo
              .getGenerationId(), rsInfo.getGroupId(), rsInfo.getWeight());
      this.protocolVersion = 0;
      this.baseDN = null;
      this.windowSize = 0;
      this.connectedDSs = connectedDSs;
      this.connectedDSNumber = connectedDSs.size();
      this.sslEncryption = false;
      this.degradedStatusThreshold = -1;
      this.serverState = new ServerState();
    }

    /**
     * Get the server state.
     * @return The server state
     */
    public ServerState getServerState()
    {
      return serverState;
    }

    /**
     * Get the group id.
     * @return The group id
     */
    public byte getGroupId()
    {
      return rsInfo.getGroupId();
    }

    /**
     * Get the server protocol version.
     * @return the protocolVersion
     */
    public short getProtocolVersion()
    {
      return protocolVersion;
    }

    /**
     * Get the generation id.
     * @return the generationId
     */
    public long getGenerationId()
    {
      return rsInfo.getGenerationId();
    }

    /**
     * Get the server id.
     * @return the serverId
     */
    public int getServerId()
    {
      return rsInfo.getId();
    }

    /**
     * Get the server URL.
     * @return the serverURL
     */
    public String getServerURL()
    {
      return rsInfo.getServerUrl();
    }

    /**
     * Get the base DN.
     *
     * @return the base DN
     */
    public DN getBaseDN()
    {
      return baseDN;
    }

    /**
     * Get the window size.
     * @return the windowSize
     */
    public int getWindowSize()
    {
      return windowSize;
    }

    /**
     * Get the ssl encryption.
     * @return the sslEncryption
     */
    public boolean isSslEncryption()
    {
      return sslEncryption;
    }

    /**
     * Get the degraded status threshold.
     * @return the degradedStatusThreshold
     */
    public int getDegradedStatusThreshold()
    {
      return degradedStatusThreshold;
    }

    /**
     * Get the weight.
     * @return the weight. Null if this object is a wrapper for
     * a ReplServerStartMsg.
     */
    public int getWeight()
    {
      return rsInfo.getWeight();
    }

    /**
     * Get the connected DS number.
     * @return the connectedDSNumber. Null if this object is a wrapper for
     * a ReplServerStartMsg.
     */
    public int getConnectedDSNumber()
    {
      return connectedDSNumber;
    }

    /**
     * Converts the object to a RSInfo object.
     * @return The RSInfo object matching this object.
     */
    RSInfo toRSInfo()
    {
      return rsInfo;
    }

    /**
     * Updates replication server info with the passed RSInfo internal values
     * and the passed connected DSs.
     * @param rsInfo The RSinfo to use for the update
     * @param connectedDSs The new connected DSs
     */
    private void update(RSInfo rsInfo, Set<Integer> connectedDSs)
    {
      this.rsInfo = new RSInfo(this.rsInfo.getId(), this.rsInfo.getServerUrl(),
          rsInfo.getGenerationId(), rsInfo.getGroupId(), rsInfo.getWeight());
      this.connectedDSs = connectedDSs;
      this.connectedDSNumber = connectedDSs.size();
    }

    private void setServerURL(String newServerURL)
    {
      rsInfo = new RSInfo(rsInfo.getId(), newServerURL,
          rsInfo.getGenerationId(), rsInfo.getGroupId(), rsInfo.getWeight());
    }

    /**
     * Updates replication server info with the passed server state.
     * @param serverState The ServerState to use for the update
     */
    private void update(ServerState serverState)
    {
      this.serverState.update(serverState);
    }

    /**
     * Get the getConnectedDSs.
     * @return the getConnectedDSs
     */
    public Set<Integer> getConnectedDSs()
    {
      return connectedDSs;
    }

    /**
     * Gets the locally configured status for this RS.
     * @return the locallyConfigured
     */
    public boolean isLocallyConfigured()
    {
      return locallyConfigured;
    }

    /**
     * Sets the locally configured status for this RS.
     * @param locallyConfigured the locallyConfigured to set
     */
    public void setLocallyConfigured(boolean locallyConfigured)
    {
      this.locallyConfigured = locallyConfigured;
    }

    /**
     * Returns a string representation of this object.
     * @return A string representation of this object.
     */
    @Override
    public String toString()
    {
      return "ReplServerInfo Url:" + getServerURL()
          + " ServerId:" + getServerId()
          + " GroupId:" + getGroupId()
          + " connectedDSs:" + connectedDSs;
    }
  }

  /**
   * Contacts all replication servers to get information from them and being
   * able to choose the more suitable.
   * @return the collected information.
   */
  private Map<Integer, ReplicationServerInfo> collectReplicationServersInfo()
  {
    final Map<Integer, ReplicationServerInfo> rsInfos = new ConcurrentSkipListMap<>();

    for (String serverUrl : getReplicationServerUrls())
    {
      // Connect to server + get and store info about it
      final ConnectedRS rs = performPhaseOneHandshake(serverUrl, false);
      final ReplicationServerInfo rsInfo = rs.rsInfo;
      if (rsInfo != null)
      {
        rsInfos.put(rsInfo.getServerId(), rsInfo);
      }
    }

    return rsInfos;
  }

  /**
   * Connect to a ReplicationServer.
   *
   * Handshake sequences between a DS and a RS is divided into 2 logical
   * consecutive phases (phase 1 and phase 2). DS always initiates connection
   * and always sends first message:
   *
   * DS<->RS:
   * -------
   *
   * phase 1:
   * DS --- ServerStartMsg ---> RS
   * DS <--- ReplServerStartDSMsg --- RS
   * phase 2:
   * DS --- StartSessionMsg ---> RS
   * DS <--- TopologyMsg --- RS
   *
   * Before performing a full handshake sequence, DS searches for best suitable
   * RS by making only phase 1 handshake to every RS he knows then closing
   * connection. This allows to gather information on available RSs and then
   * decide with which RS the full handshake (phase 1 then phase 2) will be
   * finally performed.
   *
   * @throws NumberFormatException address was invalid
   */
  private void connectAsDataServer()
  {
    /*
     * If a first connect or a connection failure occur, we go through here.
     * force status machine to NOT_CONNECTED_STATUS so that monitoring can see
     * that we are not connected.
     */
    domain.toNotConnectedStatus();

    /* Stop any existing heartbeat monitor and changeTime publisher from a previous session. */
    stopRSHeartBeatMonitoring();
    stopChangeTimeHeartBeatPublishing();
    mustRunBestServerCheckingAlgorithm = 0;

    synchronized (connectPhaseLock)
    {
      final int serverId = getServerId();
      final DN baseDN = getBaseDN();

      /*
       * Connect to each replication server and get their ServerState then find
       * out which one is the best to connect to.
       */
      if (logger.isTraceEnabled())
      {
        debugInfo("phase 1 : will perform PhaseOneH with each RS in order to elect the preferred one");
      }

      // Get info from every available replication servers
      Map<Integer, ReplicationServerInfo> rsInfos =
          collectReplicationServersInfo();
      computeNewTopology(toRSInfos(rsInfos));

      if (rsInfos.isEmpty())
      {
        setConnectedRS(ConnectedRS.noConnectedRS());
      }
      else
      {
        // At least one server answered, find the best one.
        RSEvaluations evals = computeBestReplicationServer(true, -1, state,
            rsInfos, serverId, getGroupId(), getGenerationID());

        // Best found, now initialize connection to this one (handshake phase 1)
        if (logger.isTraceEnabled())
        {
          debugInfo("phase 2 : will perform PhaseOneH with the preferred RS=" + evals.getBestRS());
        }

        final ConnectedRS electedRS = performPhaseOneHandshake(
            evals.getBestRS().getServerURL(), true);
        final ReplicationServerInfo electedRsInfo = electedRS.rsInfo;
        if (electedRsInfo != null)
        {
          /*
          Update replication server info with potentially more up to date
          data (server state for instance may have changed)
          */
          rsInfos.put(electedRsInfo.getServerId(), electedRsInfo);

          // Handshake phase 1 exchange went well

          // Compute in which status we are starting the session to tell the RS
          final ServerStatus initStatus = computeInitialServerStatus(
              electedRsInfo.getGenerationId(), electedRsInfo.getServerState(),
              electedRsInfo.getDegradedStatusThreshold(), getGenerationID());

          // Perform session start (handshake phase 2)
          final TopologyMsg topologyMsg =
              performPhaseTwoHandshake(electedRS, initStatus);

          if (topologyMsg != null) // Handshake phase 2 exchange went well
          {
            connectToReplicationServer(electedRS, initStatus, topologyMsg);
          } // Could perform handshake phase 2 with best
        } // Could perform handshake phase 1 with best
      }

      // connectedRS has been updated by calls above, reload it
      final ConnectedRS rs = connectedRS.get();
      if (rs.isConnected())
      {
        connectPhaseLock.notify();

        final long rsGenId = rs.rsInfo.getGenerationId();
        final int rsServerId = rs.rsInfo.getServerId();
        if (rsGenId == getGenerationID() || rsGenId == -1)
        {
          logger.info(NOTE_NOW_FOUND_SAME_GENERATION_CHANGELOG, serverId, rsServerId, baseDN,
              rs.replicationServer, getGenerationID());
        }
        else
        {
          logger.warn(WARN_NOW_FOUND_BAD_GENERATION_CHANGELOG, serverId, rsServerId, baseDN,
              rs.replicationServer, getGenerationID(), rsGenId);
        }
      }
      else
      {
         // This server could not find any replicationServer.
         // It's going to start in degraded mode. Log a message.
        if (!connectionError)
        {
          connectionError = true;
          connectPhaseLock.notify();

          if (!rsInfos.isEmpty())
          {
            logger.warn(WARN_COULD_NOT_FIND_CHANGELOG, serverId, baseDN,
                Utils.joinAsString(", ", rsInfos.keySet()));
          }
          else
          {
            logger.warn(WARN_NO_AVAILABLE_CHANGELOGS, serverId, baseDN);
          }
        }
      }
    }
  }

  private void computeNewTopology(List<RSInfo> newRSInfos)
  {
    final int rsServerId = getRsServerId();

    Topology oldTopo;
    Topology newTopo;
    do
    {
      oldTopo = topology.get();
      newTopo = new Topology(oldTopo.replicaInfos, newRSInfos, getServerId(),
          rsServerId, getReplicationServerUrls(), oldTopo.rsInfos);
    }
    while (!topology.compareAndSet(oldTopo, newTopo));

    if (logger.isTraceEnabled())
    {
      debugInfo(topologyChange(rsServerId, oldTopo, newTopo));
    }
  }

  private StringBuilder topologyChange(int rsServerId, Topology oldTopo,
      Topology newTopo)
  {
    final StringBuilder sb = new StringBuilder();
    sb.append("rsServerId=").append(rsServerId);
    if (newTopo.equals(oldTopo))
    {
      sb.append(", unchangedTopology=").append(newTopo);
    }
    else
    {
      sb.append(", oldTopology=").append(oldTopo);
      sb.append(", newTopology=").append(newTopo);
    }
    return sb;
  }

  /**
   * Connects to a replication server.
   *
   * @param rs
   *          the Replication Server to connect to
   * @param initStatus
   *          The status to enter the state machine with
   * @param topologyMsg
   *          the message containing the topology information
   */
  private void connectToReplicationServer(ConnectedRS rs,
      ServerStatus initStatus, TopologyMsg topologyMsg)
  {
    final DN baseDN = getBaseDN();
    final ReplicationServerInfo rsInfo = rs.rsInfo;

    boolean connectCompleted = false;
    try
    {
      maxSendWindow = rsInfo.getWindowSize();

      receiveTopo(topologyMsg, rs.getServerId());

      /*
      Log a message to let the administrator know that the failure was resolved.
      Wake up all the thread that were waiting on the window
      on the previous connection.
      */
      connectionError = false;
      if (sendWindow != null)
      {
        /*
         * Fix (hack) for OPENDJ-401: we want to ensure that no threads holding
         * this semaphore will get blocked when they acquire it. However, we
         * also need to make sure that we don't overflow the semaphore by
         * releasing too many permits.
         */
        final int MAX_PERMITS = Integer.MAX_VALUE >>> 2;
        if (sendWindow.availablePermits() < MAX_PERMITS)
        {
          /*
           * At least 2^29 acquisitions would need to occur for this to be
           * insufficient. In addition, at least 2^30 releases would need to
           * occur for this to potentially overflow. Hopefully this is unlikely
           * to happen.
           */
          sendWindow.release(MAX_PERMITS);
        }
      }
      sendWindow = new Semaphore(maxSendWindow);
      rcvWindow = getMaxRcvWindow();

      domain.sessionInitiated(initStatus, rsInfo.getServerState());

      final byte groupId = getGroupId();
      if (rs.getGroupId() != groupId)
      {
        /*
        Connected to replication server with wrong group id:
        warn user and start heartbeat monitor to recover when a server
        with the right group id shows up.
        */
        logger.warn(WARN_CONNECTED_TO_SERVER_WITH_WRONG_GROUP_ID,
            groupId, rs.getServerId(), rsInfo.getServerURL(), rs.getGroupId(), baseDN, getServerId());
      }
      startRSHeartBeatMonitoring(rs);
      if (rsInfo.getProtocolVersion() >=
        ProtocolVersion.REPLICATION_PROTOCOL_V3)
      {
        startChangeTimeHeartBeatPublishing(rs);
      }
      connectCompleted = true;
    }
    catch (Exception e)
    {
      logger.error(ERR_COMPUTING_FAKE_OPS, baseDN, rsInfo.getServerURL(),
          e.getLocalizedMessage() + " " + stackTraceToSingleLineString(e));
    }
    finally
    {
      if (!connectCompleted)
      {
        setConnectedRS(ConnectedRS.noConnectedRS());
      }
    }
  }

  /**
   * Determines the status we are starting with according to our state and the
   * RS state.
   *
   * @param rsGenId The generation id of the RS
   * @param rsState The server state of the RS
   * @param degradedStatusThreshold The degraded status threshold of the RS
   * @param dsGenId The local generation id
   * @return The initial status
   */
  private ServerStatus computeInitialServerStatus(long rsGenId,
    ServerState rsState, int degradedStatusThreshold, long dsGenId)
  {
    if (rsGenId == -1)
    {
      // RS has no generation id
      return ServerStatus.NORMAL_STATUS;
    }
    else if (rsGenId != dsGenId)
    {
      // DS and RS do not have same generation id
      return ServerStatus.BAD_GEN_ID_STATUS;
    }
    else
    {
      /*
      DS and RS have same generation id

      Determine if we are late or not to replay changes. RS uses a
      threshold value for pending changes to be replayed by a DS to
      determine if the DS is in normal status or in degraded status.
      Let's compare the local and remote server state using  this threshold
      value to determine if we are late or not
      */

      int nChanges = ServerState.diffChanges(rsState, state);
      if (logger.isTraceEnabled())
      {
        debugInfo("computed " + nChanges + " changes late.");
      }

      /*
      Check status to know if it is relevant to change the status. Do not
      take RSD lock to test. If we attempt to change the status whereas
      we are in a status that do not allows that, this will be noticed by
      the changeStatusFromStatusAnalyzer method. This allows to take the
      lock roughly only when needed versus every sleep time timeout.
      */
      if (degradedStatusThreshold > 0 && nChanges >= degradedStatusThreshold)
      {
        return ServerStatus.DEGRADED_STATUS;
      }
      // degradedStatusThreshold value of '0' means no degrading system used
      // (no threshold): force normal status
      return ServerStatus.NORMAL_STATUS;
    }
  }

  /**
   * Connect to the provided server performing the first phase handshake (start
   * messages exchange) and return the reply message from the replication
   * server, wrapped in a ReplicationServerInfo object.
   *
   * @param serverURL
   *          Server to connect to.
   * @param keepSession
   *          Do we keep session opened or not after handshake. Use true if want
   *          to perform handshake phase 2 with the same session and keep the
   *          session to create as the current one.
   * @return The answer from the server . Null if could not get an answer.
   */
  private ConnectedRS performPhaseOneHandshake(String serverURL, boolean keepSession)
  {
    Session newSession = null;
    Socket socket = null;
    boolean hasConnected = false;
    LocalizableMessage errorMessage = null;

    try
    {
      // Open a socket connection to the next candidate.
      socket = new Socket();
      socket.setReuseAddress(true);
      socket.setReceiveBufferSize(1000000);
      socket.setTcpNoDelay(true);
      if (config.getSourceAddress() != null)
      {
        InetSocketAddress local = new InetSocketAddress(config.getSourceAddress(), 0);
        socket.bind(local);
      }
      int timeoutMS = MultimasterReplication.getConnectionTimeoutMS();
      socket.connect(HostPort.valueOf(serverURL).toInetSocketAddress(), timeoutMS);
      newSession = replSessionSecurity.createClientSession(socket, timeoutMS);
      boolean isSslEncryption = replSessionSecurity.isSslEncryption();

      // Send our ServerStartMsg.
      final HostPort hp = new HostPort(
          socket.getLocalAddress().getHostName(), socket.getLocalPort());
      final String url = hp.toString();
      final StartMsg serverStartMsg = new ServerStartMsg(getServerId(), url, getBaseDN(),
          getMaxRcvWindow(), config.getHeartbeatInterval(), state,
          getGenerationID(), isSslEncryption, getGroupId());
      newSession.publish(serverStartMsg);

      // Read the ReplServerStartMsg or ReplServerStartDSMsg that should
      // come back.
      ReplicationMsg msg = newSession.receive();
      if (logger.isTraceEnabled())
      {
        debugInfo("RB HANDSHAKE SENT:\n" + serverStartMsg + "\nAND RECEIVED:\n"
            + msg);
      }

      // Wrap received message in a server info object
      final ReplicationServerInfo replServerInfo =
          ReplicationServerInfo.newInstance(msg, serverURL);

      // Sanity check
      final DN repDN = replServerInfo.getBaseDN();
      if (!getBaseDN().equals(repDN))
      {
        errorMessage = ERR_DS_DN_DOES_NOT_MATCH.get(repDN, getBaseDN());
        return setConnectedRS(ConnectedRS.noConnectedRS());
      }

      /*
       * We have sent our own protocol version to the replication server. The
       * replication server will use the same one (or an older one if it is an
       * old replication server).
       */
      newSession.setProtocolVersion(
          getCompatibleVersion(replServerInfo.getProtocolVersion()));

      if (!isSslEncryption)
      {
        newSession.stopEncryption();
      }

      hasConnected = true;

      if (keepSession)
      {
        // cannot store it yet,
        // only store after a successful phase two handshake
        return new ConnectedRS(replServerInfo, newSession);
      }
      return new ConnectedRS(replServerInfo, null);
    }
    catch (ConnectException e)
    {
      logger.traceException(e);
      errorMessage = WARN_NO_CHANGELOG_SERVER_LISTENING.get(getServerId(), serverURL, getBaseDN());
    }
    catch (SocketTimeoutException e)
    {
      logger.traceException(e);
      errorMessage = WARN_TIMEOUT_CONNECTING_TO_RS.get(getServerId(), serverURL, getBaseDN());
    }
    catch (Exception e)
    {
      logger.traceException(e);
      errorMessage = WARN_EXCEPTION_STARTING_SESSION_PHASE.get(
          getServerId(), serverURL, getBaseDN(), stackTraceToSingleLineString(e));
    }
    finally
    {
      if (!hasConnected || !keepSession)
      {
        close(newSession);
        close(socket);
      }

      if (!hasConnected && errorMessage != null && !connectionError)
      {
        // There was no server waiting on this host:port
        // Log a notice and will try the next replicationServer in the list
        if (keepSession) // Log error message only for final connection
        {
          // log the error message only once to avoid overflowing the error log
          logger.error(errorMessage);
        }

        logger.trace(errorMessage);
      }
    }
    return setConnectedRS(ConnectedRS.noConnectedRS());
  }

  /**
   * Performs the second phase handshake (send StartSessionMsg and receive
   * TopologyMsg messages exchange) and return the reply message from the
   * replication server.
   *
   * @param electedRS Server we are connecting with.
   * @param initStatus The status we are starting with
   * @return The ReplServerStartMsg the server replied. Null if could not
   *         get an answer.
   */
  private TopologyMsg performPhaseTwoHandshake(ConnectedRS electedRS,
    ServerStatus initStatus)
  {
    try
    {
      // Send our StartSessionMsg.
      final StartSessionMsg startSessionMsg;
      startSessionMsg = new StartSessionMsg(
          initStatus,
          domain.getRefUrls(),
          domain.isAssured(),
          domain.getAssuredMode(),
          domain.getAssuredSdLevel());
      startSessionMsg.setEclIncludes(
          domain.getEclIncludes(domain.getServerId()),
          domain.getEclIncludesForDeletes(domain.getServerId()));
      final Session session = electedRS.session;
      session.publish(startSessionMsg);

      // Read the TopologyMsg that should come back.
      final TopologyMsg topologyMsg = (TopologyMsg) session.receive();

      if (logger.isTraceEnabled())
      {
        debugInfo("RB HANDSHAKE SENT:\n" + startSessionMsg
            + "\nAND RECEIVED:\n" + topologyMsg);
      }

      // Alright set the timeout to the desired value
      session.setSoTimeout(timeout);
      setConnectedRS(electedRS);
      return topologyMsg;
    }
    catch (Exception e)
    {
      logger.error(WARN_EXCEPTION_STARTING_SESSION_PHASE,
          getServerId(), electedRS.rsInfo.getServerURL(), getBaseDN(), stackTraceToSingleLineString(e));

      setConnectedRS(ConnectedRS.noConnectedRS());
      return null;
    }
  }

  /**
   * Class holding evaluation results for electing the best replication server
   * for the local directory server.
   */
  static class RSEvaluations
  {
    private final int localServerId;
    private Map<Integer, ReplicationServerInfo> bestRSs;
    private final Map<Integer, LocalizableMessage> rsEvals = new HashMap<>();

    /**
     * Ctor.
     *
     * @param localServerId
     *          the serverId for the local directory server
     * @param rsInfos
     *          a Map of serverId => {@link ReplicationServerInfo} with all the
     *          candidate replication servers
     */
    RSEvaluations(int localServerId,
        Map<Integer, ReplicationServerInfo> rsInfos)
    {
      this.localServerId = localServerId;
      this.bestRSs = rsInfos;
    }

    private boolean keepBest(LocalEvaluation eval)
    {
      if (eval.hasAcceptedAny())
      {
        bestRSs = eval.getAccepted();
        rsEvals.putAll(eval.getRejected());
        return true;
      }
      return false;
    }

    /**
     * Sets the elected best replication server, rejecting all the other
     * replication servers with the supplied evaluation.
     *
     * @param bestRsId
     *          the serverId of the elected replication server
     * @param rejectedRSsEval
     *          the evaluation for all the rejected replication servers
     */
    private void setBestRS(int bestRsId, LocalizableMessage rejectedRSsEval)
    {
      for (Iterator<Entry<Integer, ReplicationServerInfo>> it =
          this.bestRSs.entrySet().iterator(); it.hasNext();)
      {
        final Entry<Integer, ReplicationServerInfo> entry = it.next();
        final Integer rsId = entry.getKey();
        final ReplicationServerInfo rsInfo = entry.getValue();
        if (rsInfo.getServerId() != bestRsId)
        {
          it.remove();
        }
        rsEvals.put(rsId, rejectedRSsEval);
      }
    }

    private void discardAll(LocalizableMessage eval)
    {
      for (Integer rsId : bestRSs.keySet())
      {
        rsEvals.put(rsId, eval);
      }
    }

    private boolean foundBestRS()
    {
      return bestRSs.size() == 1;
    }

    /**
     * Returns the {@link ReplicationServerInfo} for the best replication
     * server.
     *
     * @return the {@link ReplicationServerInfo} for the best replication server
     */
    ReplicationServerInfo getBestRS()
    {
      if (foundBestRS())
      {
        return bestRSs.values().iterator().next();
      }
      return null;
    }

    /**
     * Returns the evaluations for all the candidate replication servers.
     *
     * @return a Map of serverId => LocalizableMessage containing the evaluation for each
     *         candidate replication servers.
     */
    Map<Integer, LocalizableMessage> getEvaluations()
    {
      if (foundBestRS())
      {
        final Integer bestRSServerId = getBestRS().getServerId();
        if (rsEvals.get(bestRSServerId) == null)
        {
          final LocalizableMessage eval = NOTE_BEST_RS.get(bestRSServerId, localServerId);
          rsEvals.put(bestRSServerId, eval);
        }
      }
      return Collections.unmodifiableMap(rsEvals);
    }

    /**
     * Returns the evaluation for the supplied replication server Id.
     * <p>
     * Note: "unknown RS" message is returned if the supplied replication server
     * was not part of the candidate replication servers.
     *
     * @param rsServerId
     *          the supplied replication server Id
     * @return the evaluation {@link LocalizableMessage} for the supplied replication
     *         server Id
     */
    private LocalizableMessage getEvaluation(int rsServerId)
    {
      final LocalizableMessage evaluation = getEvaluations().get(rsServerId);
      if (evaluation != null)
      {
        return evaluation;
      }
      return NOTE_UNKNOWN_RS.get(rsServerId, localServerId);
    }

    @Override
    public String toString()
    {
      return "Current best replication server Ids: " + bestRSs.keySet()
          + ", Evaluation of connected replication servers"
          + " (ServerId => Evaluation): " + rsEvals.keySet()
          + ", Any replication server not appearing here"
          + " could not be contacted.";
    }
  }

  /** Evaluation local to one filter. */
  private static class LocalEvaluation
  {
    private final Map<Integer, ReplicationServerInfo> accepted = new HashMap<>();
    private final Map<ReplicationServerInfo, LocalizableMessage> rsEvals = new HashMap<>();

    private void accept(Integer rsId, ReplicationServerInfo rsInfo)
    {
      // forget previous eval, including undoing reject
      this.rsEvals.remove(rsInfo);
      this.accepted.put(rsId, rsInfo);
    }

    private void reject(ReplicationServerInfo rsInfo, LocalizableMessage reason)
    {
      this.accepted.remove(rsInfo.getServerId()); // undo accept
      this.rsEvals.put(rsInfo, reason);
    }

    private Map<Integer, ReplicationServerInfo> getAccepted()
    {
      return accepted;
    }

    private ReplicationServerInfo[] getAcceptedRSInfos()
    {
      return accepted.values().toArray(
          new ReplicationServerInfo[accepted.size()]);
    }

    public Map<Integer, LocalizableMessage> getRejected()
    {
      final Map<Integer, LocalizableMessage> result = new HashMap<>();
      for (Entry<ReplicationServerInfo, LocalizableMessage> entry : rsEvals.entrySet())
      {
        result.put(entry.getKey().getServerId(), entry.getValue());
      }
      return result;
    }

    private boolean hasAcceptedAny()
    {
      return !accepted.isEmpty();
    }
  }

  /**
   * Returns the replication server that best fits our need so that we can
   * connect to it or determine if we must disconnect from current one to
   * re-connect to best server.
   * <p>
   * Note: this method is static for test purpose (access from unit tests)
   *
   * @param firstConnection True if we run this method for the very first
   * connection of the broker. False if we run this method to determine if the
   * replication server we are currently connected to is still the best or not.
   * @param rsServerId The id of the replication server we are currently
   * connected to. Only used when firstConnection is false.
   * @param myState The local server state.
   * @param rsInfos The list of available replication servers and their
   * associated information (choice will be made among them).
   * @param localServerId The server id for the suffix we are working for.
   * @param groupId The groupId we prefer being connected to if possible
   * @param generationId The generation id we are using
   * @return The computed best replication server. If the returned value is
   * null, the best replication server is undetermined but the local server must
   * disconnect (so the best replication server is another one than the current
   * one). Null can only be returned when firstConnection is false.
   */
  static RSEvaluations computeBestReplicationServer(
      boolean firstConnection, int rsServerId, ServerState myState,
      Map<Integer, ReplicationServerInfo> rsInfos, int localServerId,
      byte groupId, long generationId)
  {
    final RSEvaluations evals = new RSEvaluations(localServerId, rsInfos);
    // Shortcut, if only one server, this is the best
    if (evals.foundBestRS())
    {
      return evals;
    }

    /**
     * Apply some filtering criteria to determine the best servers list from
     * the available ones. The ordered list of criteria is (from more important
     * to less important):
     * - replication server has the same group id as the local DS one
     * - replication server has the same generation id as the local DS one
     * - replication server is up to date regarding changes generated by the
     *   local DS
     * - replication server in the same VM as local DS one
     */
    /*
    The list of best replication servers is filtered with each criteria. At
    each criteria, the list is replaced with the filtered one if there
    are some servers from the filtering, otherwise, the list is left as is
    and the new filtering for the next criteria is applied and so on.

    Use only servers locally configured: those are servers declared in
    the local configuration. When the current method is called, for
    sure, at least one server from the list is locally configured
    */
    filterServersLocallyConfigured(evals, localServerId);
    // Some servers with same group id ?
    filterServersWithSameGroupId(evals, localServerId, groupId);
    // Some servers with same generation id ?
    final boolean rssWithSameGenerationIdExist =
        filterServersWithSameGenerationId(evals, localServerId, generationId);
    if (rssWithSameGenerationIdExist)
    {
      // If some servers with the right generation id this is useful to
      // run the local DS change criteria
      filterServersWithAllLocalDSChanges(evals, myState, localServerId);
    }
    // Some servers in the local VM or local host?
    filterServersOnSameHost(evals, localServerId);

    if (evals.foundBestRS())
    {
      return evals;
    }

    /* Now apply the choice based on the weight to the best servers list */
    if (firstConnection)
    {
      // We are not connected to a server yet
      computeBestServerForWeight(evals, -1, -1);
    }
    else
    {
      /*
       * We are already connected to a RS: compute the best RS as far as the
       * weights is concerned. If this is another one, some DS must disconnect.
       */
      computeBestServerForWeight(evals, rsServerId, localServerId);
    }
    return evals;
  }

  /**
   * Creates a new list that contains only replication servers that are locally
   * configured.
   * @param evals The evaluation object
   */
  private static void filterServersLocallyConfigured(RSEvaluations evals,
      int localServerId)
  {
    final LocalEvaluation eval = new LocalEvaluation();
    for (Entry<Integer, ReplicationServerInfo> entry : evals.bestRSs.entrySet())
    {
      final Integer rsId = entry.getKey();
      final ReplicationServerInfo rsInfo = entry.getValue();
      if (rsInfo.isLocallyConfigured())
      {
        eval.accept(rsId, rsInfo);
      }
      else
      {
        eval.reject(rsInfo,
            NOTE_RS_NOT_LOCALLY_CONFIGURED.get(rsId, localServerId));
      }
    }
    evals.keepBest(eval);
  }

  /**
   * Creates a new list that contains only replication servers that have the
   * passed group id, from a passed replication server list.
   * @param evals The evaluation object
   * @param groupId The group id that must match
   */
  private static void filterServersWithSameGroupId(RSEvaluations evals,
      int localServerId, byte groupId)
  {
    final LocalEvaluation eval = new LocalEvaluation();
    for (Entry<Integer, ReplicationServerInfo> entry : evals.bestRSs.entrySet())
    {
      final Integer rsId = entry.getKey();
      final ReplicationServerInfo rsInfo = entry.getValue();
      if (rsInfo.getGroupId() == groupId)
      {
        eval.accept(rsId, rsInfo);
      }
      else
      {
        eval.reject(rsInfo, NOTE_RS_HAS_DIFFERENT_GROUP_ID_THAN_DS.get(
            rsId, rsInfo.getGroupId(), localServerId, groupId));
      }
    }
    evals.keepBest(eval);
  }

  /**
   * Creates a new list that contains only replication servers that have the
   * provided generation id, from a provided replication server list.
   * When the selected replication servers have no change (empty serverState)
   * then the 'empty'(generationId==-1) replication servers are also included
   * in the result list.
   *
   * @param evals The evaluation object
   * @param generationId The generation id that must match
   * @return whether some replication server passed the filter
   */
  private static boolean filterServersWithSameGenerationId(
      RSEvaluations evals, long localServerId, long generationId)
  {
    final Map<Integer, ReplicationServerInfo> bestServers = evals.bestRSs;
    final LocalEvaluation eval = new LocalEvaluation();
    boolean emptyState = true;

    for (Entry<Integer, ReplicationServerInfo> entry : bestServers.entrySet())
    {
      final Integer rsId = entry.getKey();
      final ReplicationServerInfo rsInfo = entry.getValue();
      if (rsInfo.getGenerationId() == generationId)
      {
        eval.accept(rsId, rsInfo);
        if (!rsInfo.serverState.isEmpty())
        {
          emptyState = false;
        }
      }
      else if (rsInfo.getGenerationId() == -1)
      {
        eval.reject(rsInfo, NOTE_RS_HAS_NO_GENERATION_ID.get(rsId,
            generationId, localServerId));
      }
      else
      {
        eval.reject(rsInfo, NOTE_RS_HAS_DIFFERENT_GENERATION_ID_THAN_DS.get(
            rsId, rsInfo.getGenerationId(), localServerId, generationId));
      }
    }

    if (emptyState)
    {
      // If the RS with a generationId have all an empty state,
      // then the 'empty'(genId=-1) RSes are also candidate
      for (Entry<Integer, ReplicationServerInfo> entry : bestServers.entrySet())
      {
        ReplicationServerInfo rsInfo = entry.getValue();
        if (rsInfo.getGenerationId() == -1)
        {
          // will undo the reject of previously rejected RSs
          eval.accept(entry.getKey(), rsInfo);
        }
      }
    }

    return evals.keepBest(eval);
  }

  /**
   * Creates a new list that contains only replication servers that have the
   * latest changes from the passed DS, from a passed replication server list.
   * @param evals The evaluation object
   * @param localState The state of the local DS
   * @param localServerId The server id to consider for the changes
   */
  private static void filterServersWithAllLocalDSChanges(
      RSEvaluations evals, ServerState localState, int localServerId)
  {
    // Extract the CSN of the latest change generated by the local server
    final CSN localCSN = getCSN(localState, localServerId);

    /**
     * Find replication servers that are up to date (or more up to date than us,
     * if for instance we failed and restarted, having sent some changes to the
     * RS but without having time to store our own state) regarding our own
     * server id. If some servers are more up to date, prefer this list but take
     * only the latest CSN.
     */
    final LocalEvaluation mostUpToDateEval = new LocalEvaluation();
    boolean foundRSMoreUpToDateThanLocalDS = false;
    CSN latestRsCSN = null;
    for (Entry<Integer, ReplicationServerInfo> entry : evals.bestRSs.entrySet())
    {
      final Integer rsId = entry.getKey();
      final ReplicationServerInfo rsInfo = entry.getValue();
      final CSN rsCSN = getCSN(rsInfo.getServerState(), localServerId);

      // Has this replication server the latest local change ?
      if (rsCSN.isOlderThan(localCSN))
      {
        mostUpToDateEval.reject(rsInfo, NOTE_RS_LATER_THAN_LOCAL_DS.get(
            rsId, rsCSN.toStringUI(), localServerId, localCSN.toStringUI()));
      }
      else if (rsCSN.equals(localCSN))
      {
        // This replication server has exactly the latest change from the
        // local server
        if (!foundRSMoreUpToDateThanLocalDS)
        {
          mostUpToDateEval.accept(rsId, rsInfo);
        }
        else
        {
          mostUpToDateEval.reject(rsInfo,
            NOTE_RS_LATER_THAN_ANOTHER_RS_MORE_UP_TO_DATE_THAN_LOCAL_DS.get(
              rsId, rsCSN.toStringUI(), localServerId, localCSN.toStringUI()));
        }
      }
      else if (rsCSN.isNewerThan(localCSN))
      {
        // This replication server is even more up to date than the local server
        if (latestRsCSN == null)
        {
          foundRSMoreUpToDateThanLocalDS = true;
          // all previous results are now outdated, reject them all
          rejectAllWithRSIsLaterThanBestRS(mostUpToDateEval, localServerId,
              localCSN);
          // Initialize the latest CSN
          latestRsCSN = rsCSN;
        }

        if (rsCSN.equals(latestRsCSN))
        {
          mostUpToDateEval.accept(rsId, rsInfo);
        }
        else if (rsCSN.isNewerThan(latestRsCSN))
        {
          // This RS is even more up to date, reject all previously accepted RSs
          // and store this new RS
          rejectAllWithRSIsLaterThanBestRS(mostUpToDateEval, localServerId,
              localCSN);
          mostUpToDateEval.accept(rsId, rsInfo);
          latestRsCSN = rsCSN;
        }
        else
        {
          mostUpToDateEval.reject(rsInfo,
            NOTE_RS_LATER_THAN_ANOTHER_RS_MORE_UP_TO_DATE_THAN_LOCAL_DS.get(
              rsId, rsCSN.toStringUI(), localServerId, localCSN.toStringUI()));
        }
      }
    }
    evals.keepBest(mostUpToDateEval);
  }

  private static CSN getCSN(ServerState state, int serverId)
  {
    final CSN csn = state.getCSN(serverId);
    if (csn != null)
    {
      return csn;
    }
    return new CSN(0, 0, serverId);
  }

  private static void rejectAllWithRSIsLaterThanBestRS(
      final LocalEvaluation eval, int localServerId, CSN localCSN)
  {
    for (ReplicationServerInfo rsInfo : eval.getAcceptedRSInfos())
    {
      final String rsCSN =
          getCSN(rsInfo.getServerState(), localServerId).toStringUI();
      final LocalizableMessage reason =
          NOTE_RS_LATER_THAN_ANOTHER_RS_MORE_UP_TO_DATE_THAN_LOCAL_DS.get(
            rsInfo.getServerId(), rsCSN, localServerId, localCSN.toStringUI());
      eval.reject(rsInfo, reason);
    }
  }

  /**
   * Creates a new list that contains only replication servers that are on the
   * same host as the local DS, from a passed replication server list. This
   * method will gives priority to any replication server which is in the same
   * VM as this DS.
   *
   * @param evals The evaluation object
   */
  private static void filterServersOnSameHost(RSEvaluations evals,
      int localServerId)
  {
    /*
     * Initially look for all servers on the same host. If we find one in the
     * same VM, then narrow the search.
     */
    boolean foundRSInSameVM = false;
    final LocalEvaluation eval = new LocalEvaluation();
    for (Entry<Integer, ReplicationServerInfo> entry : evals.bestRSs.entrySet())
    {
      final Integer rsId = entry.getKey();
      final ReplicationServerInfo rsInfo = entry.getValue();
      final HostPort hp = HostPort.valueOf(rsInfo.getServerURL());
      if (hp.isLocalAddress())
      {
        if (isLocalReplicationServerPort(hp.getPort()))
        {
          if (!foundRSInSameVM)
          {
            // An RS in the same VM will always have priority.
            // Narrow the search to only include servers in this VM.
            rejectAllWithRSOnDifferentVMThanDS(eval, localServerId);
            foundRSInSameVM = true;
          }
          eval.accept(rsId, rsInfo);
        }
        else if (!foundRSInSameVM)
        {
          // OK, accept RSs on the same machine because we have not found an RS
          // in the same VM yet
          eval.accept(rsId, rsInfo);
        }
        else
        {
          // Skip: we have found some RSs in the same VM, but this RS is not.
          eval.reject(rsInfo, NOTE_RS_ON_DIFFERENT_VM_THAN_DS.get(rsId,
              localServerId));
        }
      }
      else
      {
        eval.reject(rsInfo, NOTE_RS_ON_DIFFERENT_HOST_THAN_DS.get(rsId,
            localServerId));
      }
    }
    evals.keepBest(eval);
  }

  private static void rejectAllWithRSOnDifferentVMThanDS(LocalEvaluation eval,
      int localServerId)
  {
    for (ReplicationServerInfo rsInfo : eval.getAcceptedRSInfos())
    {
      eval.reject(rsInfo, NOTE_RS_ON_DIFFERENT_VM_THAN_DS.get(
          rsInfo.getServerId(), localServerId));
    }
  }

  /**
   * Computes the best replication server the local server should be connected
   * to so that the load is correctly spread across the topology, following the
   * weights guidance.
   * Warning: This method is expected to be called with at least 2 servers in
   * bestServers
   * Note: this method is static for test purpose (access from unit tests)
   * @param evals The evaluation object
   * @param currentRsServerId The replication server the local server is
   *        currently connected to. -1 if the local server is not yet connected
   *        to any replication server.
   * @param localServerId The server id of the local server. This is not used
   *        when it is not connected to a replication server
   *        (currentRsServerId = -1)
   */
  static void computeBestServerForWeight(RSEvaluations evals,
      int currentRsServerId, int localServerId)
  {
    final Map<Integer, ReplicationServerInfo> bestServers = evals.bestRSs;
    /*
     * - Compute the load goal of each RS, deducing it from the weights affected
     * to them.
     * - Compute the current load of each RS, deducing it from the DSs
     * currently connected to them.
     * - Compute the differences between the load goals and the current loads of
     * the RSs.
     */
    // Sum of the weights
    int sumOfWeights = 0;
    // Sum of the connected DSs
    int sumOfConnectedDSs = 0;
    for (ReplicationServerInfo rsInfo : bestServers.values())
    {
      sumOfWeights += rsInfo.getWeight();
      sumOfConnectedDSs += rsInfo.getConnectedDSNumber();
    }

    // Distance (difference) of the current loads to the load goals of each RS:
    // key:server id, value: distance
    Map<Integer, BigDecimal> loadDistances = new HashMap<>();
    // Precision for the operations (number of digits after the dot)
    final MathContext mathContext = new MathContext(32, RoundingMode.HALF_UP);
    for (Entry<Integer, ReplicationServerInfo> entry : bestServers.entrySet())
    {
      final Integer rsId = entry.getKey();
      final ReplicationServerInfo rsInfo = entry.getValue();

      //  load goal = rs weight / sum of weights
      BigDecimal loadGoalBd = BigDecimal.valueOf(rsInfo.getWeight()).divide(
          BigDecimal.valueOf(sumOfWeights), mathContext);
      BigDecimal currentLoadBd = BigDecimal.ZERO;
      if (sumOfConnectedDSs != 0)
      {
        // current load = number of connected DSs / total number of DSs
        int connectedDSs = rsInfo.getConnectedDSNumber();
        currentLoadBd = BigDecimal.valueOf(connectedDSs).divide(
            BigDecimal.valueOf(sumOfConnectedDSs), mathContext);
      }
      // load distance = load goal - current load
      BigDecimal loadDistanceBd =
        loadGoalBd.subtract(currentLoadBd, mathContext);
      loadDistances.put(rsId, loadDistanceBd);
    }

    if (currentRsServerId == -1)
    {
      // The local server is not connected yet, find best server to connect to,
      // taking the weights into account.
      computeBestServerWhenNotConnected(evals, loadDistances, localServerId);
    }
    else
    {
      // The local server is currently connected to a RS, let's see if it must
      // disconnect or not, taking the weights into account.
      computeBestServerWhenConnected(evals, loadDistances, localServerId,
          currentRsServerId, sumOfWeights, sumOfConnectedDSs);
    }
  }

  private static void computeBestServerWhenNotConnected(RSEvaluations evals,
      Map<Integer, BigDecimal> loadDistances, int localServerId)
  {
    final Map<Integer, ReplicationServerInfo> bestServers = evals.bestRSs;
    /*
     * Find the server with the current highest distance to its load goal and
     * choose it. Make an exception if every server is correctly balanced,
     * that is every current load distances are equal to 0, in that case,
     * choose the server with the highest weight
     */
    int bestRsId = 0; // If all server equal, return the first one
    float highestDistance = Float.NEGATIVE_INFINITY;
    boolean allRsWithZeroDistance = true;
    int highestWeightRsId = -1;
    int highestWeight = -1;
    for (Entry<Integer, ReplicationServerInfo> entry : bestServers.entrySet())
    {
      Integer rsId = entry.getKey();
      float loadDistance = loadDistances.get(rsId).floatValue();
      if (loadDistance > highestDistance)
      {
        // This server is far more from its balance point
        bestRsId = rsId;
        highestDistance = loadDistance;
      }
      if (loadDistance != 0)
      {
        allRsWithZeroDistance = false;
      }
      int weight = entry.getValue().getWeight();
      if (weight > highestWeight)
      {
        // This server has a higher weight
        highestWeightRsId = rsId;
        highestWeight = weight;
      }
    }
    // All servers with a 0 distance ?
    if (allRsWithZeroDistance)
    {
      // Choose server with the highest weight
      bestRsId = highestWeightRsId;
    }
    evals.setBestRS(bestRsId, NOTE_BIGGEST_WEIGHT_RS.get(localServerId,
        bestRsId));
  }

  private static void computeBestServerWhenConnected(RSEvaluations evals,
      Map<Integer, BigDecimal> loadDistances, int localServerId,
      int currentRsServerId, int sumOfWeights, int sumOfConnectedDSs)
  {
    final Map<Integer, ReplicationServerInfo> bestServers = evals.bestRSs;
    final MathContext mathContext = new MathContext(32, RoundingMode.HALF_UP);
    float currentLoadDistance =
      loadDistances.get(currentRsServerId).floatValue();
    if (currentLoadDistance < 0)
    {
      /*
      Too much DSs connected to the current RS, compared with its load
      goal:
      Determine the potential number of DSs to disconnect from the current
      RS and see if the local DS is part of them: the DSs that must
      disconnect are those with the lowest server id.
      Compute the sum of the distances of the load goals of the other RSs
      */
      BigDecimal sumOfLoadDistancesOfOtherRSsBd = BigDecimal.ZERO;
      for (Integer rsId : bestServers.keySet())
      {
        if (rsId != currentRsServerId)
        {
          sumOfLoadDistancesOfOtherRSsBd = sumOfLoadDistancesOfOtherRSsBd.add(
            loadDistances.get(rsId), mathContext);
        }
      }

      if (sumOfLoadDistancesOfOtherRSsBd.floatValue() > 0)
      {
        /*
        The average distance of the other RSs shows a lack of DSs.
        Compute the number of DSs to disconnect from the current RS,
        rounding to the nearest integer number. Do only this if there is
        no risk of yoyo effect: when the exact balance cannot be
        established due to the current number of DSs connected, do not
        disconnect a DS. A simple example where the balance cannot be
        reached is:
        - RS1 has weight 1 and 2 DSs
        - RS2 has weight 1 and 1 DS
        => disconnecting a DS from RS1 to reconnect it to RS2 would have no
        sense as this would lead to the reverse situation. In that case,
        the perfect balance cannot be reached and we must stick to the
        current situation, otherwise the DS would keep move between the 2
        RSs
        */
        float notRoundedOverloadingDSsNumber = sumOfLoadDistancesOfOtherRSsBd.
          multiply(BigDecimal.valueOf(sumOfConnectedDSs), mathContext)
              .floatValue();
        int overloadingDSsNumber = Math.round(notRoundedOverloadingDSsNumber);

        // Avoid yoyo effect
        if (overloadingDSsNumber == 1)
        {
          // What would be the new load distance for the current RS if
          // we disconnect some DSs ?
          ReplicationServerInfo currentReplicationServerInfo =
            bestServers.get(currentRsServerId);

          int currentRsWeight = currentReplicationServerInfo.getWeight();
          BigDecimal currentRsWeightBd = BigDecimal.valueOf(currentRsWeight);
          BigDecimal sumOfWeightsBd = BigDecimal.valueOf(sumOfWeights);
          BigDecimal currentRsLoadGoalBd =
            currentRsWeightBd.divide(sumOfWeightsBd, mathContext);
          BigDecimal potentialCurrentRsNewLoadBd = BigDecimal.ZERO;
          if (sumOfConnectedDSs != 0)
          {
            int connectedDSs = currentReplicationServerInfo.
              getConnectedDSNumber();
            BigDecimal potentialNewConnectedDSsBd =
                BigDecimal.valueOf(connectedDSs - 1);
            BigDecimal sumOfConnectedDSsBd =
                BigDecimal.valueOf(sumOfConnectedDSs);
            potentialCurrentRsNewLoadBd =
              potentialNewConnectedDSsBd.divide(sumOfConnectedDSsBd,
                mathContext);
          }
          BigDecimal potentialCurrentRsNewLoadDistanceBd =
            currentRsLoadGoalBd.subtract(potentialCurrentRsNewLoadBd,
              mathContext);

          // What would be the new load distance for the other RSs ?
          BigDecimal additionalDsLoadBd =
              BigDecimal.ONE.divide(
                  BigDecimal.valueOf(sumOfConnectedDSs), mathContext);
          BigDecimal potentialNewSumOfLoadDistancesOfOtherRSsBd =
            sumOfLoadDistancesOfOtherRSsBd.subtract(additionalDsLoadBd,
                  mathContext);

          /*
          Now compare both values: we must not disconnect the DS if this
          is for going in a situation where the load distance of the other
          RSs is the opposite of the future load distance of the local RS
          or we would evaluate that we should disconnect just after being
          arrived on the new RS. But we should disconnect if we reach the
          perfect balance (both values are 0).
          */
          if (mustAvoidYoyoEffect(potentialCurrentRsNewLoadDistanceBd,
              potentialNewSumOfLoadDistancesOfOtherRSsBd))
          {
            // Avoid the yoyo effect, and keep the local DS connected to its
            // current RS
            evals.setBestRS(currentRsServerId,
                NOTE_AVOID_YOYO_EFFECT.get(localServerId, currentRsServerId));
            return;
          }
        }

        ReplicationServerInfo currentRsInfo =
            bestServers.get(currentRsServerId);
        if (isServerOverloadingRS(localServerId, currentRsInfo,
            overloadingDSsNumber))
        {
          // The local server is part of the DSs to disconnect
          evals.discardAll(NOTE_DISCONNECT_DS_FROM_OVERLOADED_RS.get(
              localServerId, currentRsServerId));
        }
        else
        {
          // The local server is not part of the servers to disconnect from the
          // current RS.
          evals.setBestRS(currentRsServerId,
              NOTE_DO_NOT_DISCONNECT_DS_FROM_OVERLOADED_RS.get(localServerId,
                  currentRsServerId));
        }
      } else {
        // The average distance of the other RSs does not show a lack of DSs:
        // no need to disconnect any DS from the current RS.
        evals.setBestRS(currentRsServerId,
            NOTE_NO_NEED_TO_REBALANCE_DSS_BETWEEN_RSS.get(localServerId,
                currentRsServerId));
      }
    } else {
      // The RS load goal is reached or there are not enough DSs connected to
      // it to reach it: do not disconnect from this RS and return rsInfo for
      // this RS
      evals.setBestRS(currentRsServerId,
          NOTE_DO_NOT_DISCONNECT_DS_FROM_ACCEPTABLE_LOAD_RS.get(localServerId,
              currentRsServerId));
    }
  }

  private static boolean mustAvoidYoyoEffect(BigDecimal rsNewLoadDistance,
      BigDecimal otherRSsNewSumOfLoadDistances)
  {
    final MathContext roundCtx = new MathContext(6, RoundingMode.DOWN);
    final BigDecimal rsLoadDistance = rsNewLoadDistance.round(roundCtx);
    final BigDecimal otherRSsSumOfLoadDistances =
        otherRSsNewSumOfLoadDistances.round(roundCtx);

    return rsLoadDistance.compareTo(BigDecimal.ZERO) != 0
        && rsLoadDistance.compareTo(otherRSsSumOfLoadDistances.negate()) == 0;
  }

  /**
   * Returns whether the local DS is overloading the RS.
   * <p>
   * There are an "overloadingDSsNumber" of DS overloading the RS. The list of
   * DSs connected to this RS is ordered by serverId to use a consistent
   * ordering across all nodes in the topology. The serverIds which index in the
   * List are lower than "overloadingDSsNumber" will be evicted first.
   * <p>
   * This ordering is unfair since nodes with the lower serverIds will be
   * evicted more often than nodes with higher serverIds. However, it is a
   * consistent and reliable ordering applicable anywhere in the topology.
   */
  private static boolean isServerOverloadingRS(int localServerId,
      ReplicationServerInfo currentRsInfo, int overloadingDSsNumber)
  {
    List<Integer> serversConnectedToCurrentRS = new ArrayList<>(currentRsInfo.getConnectedDSs());
    Collections.sort(serversConnectedToCurrentRS);

    final int idx = serversConnectedToCurrentRS.indexOf(localServerId);
    return idx != -1 && idx < overloadingDSsNumber;
  }

  /** Start the heartbeat monitor thread. */
  private void startRSHeartBeatMonitoring(ConnectedRS rs)
  {
    final long heartbeatInterval = config.getHeartbeatInterval();
    if (heartbeatInterval > 0)
    {
      heartbeatMonitor = new HeartbeatMonitor(getServerId(), rs.getServerId(),
          getBaseDN().toString(), rs.session, heartbeatInterval);
      heartbeatMonitor.start();
    }
  }

  /** Stop the heartbeat monitor thread. */
  private synchronized void stopRSHeartBeatMonitoring()
  {
    if (heartbeatMonitor != null)
    {
      heartbeatMonitor.shutdown();
      heartbeatMonitor = null;
    }
  }

  /**
   * Restart the ReplicationBroker.
   * @param infiniteTry the socket which failed
   */
  public void reStart(boolean infiniteTry)
  {
    reStart(connectedRS.get().session, infiniteTry);
  }

  /**
   * Restart the ReplicationServer broker after a failure.
   *
   * @param failingSession the socket which failed
   * @param infiniteTry the socket which failed
   */
  private void reStart(Session failingSession, boolean infiniteTry)
  {
    if (failingSession != null)
    {
      failingSession.close();
      numLostConnections++;
    }

    ConnectedRS rs = connectedRS.get();
    if (failingSession == rs.session && !rs.equals(ConnectedRS.noConnectedRS()))
    {
      rs = setConnectedRS(ConnectedRS.noConnectedRS());
    }

    while (true)
    {
      // Synchronize inside the loop in order to allow shutdown.
      synchronized (startStopLock)
      {
        if (rs.isConnected() || shutdown)
        {
          break;
        }

        try
        {
          connectAsDataServer();
          rs = connectedRS.get();
        }
        catch (Exception e)
        {
          logger.error(NOTE_EXCEPTION_RESTARTING_SESSION,
              getBaseDN(), e.getLocalizedMessage() + " " + stackTraceToSingleLineString(e));
        }

        if (rs.isConnected() || !infiniteTry)
        {
          break;
        }
      }
      try
      {
          Thread.sleep(500);
      }
      catch (InterruptedException ignored)
      {
        // ignore
      }
    }

    if (logger.isTraceEnabled())
    {
      debugInfo("end restart : connected=" + rs.isConnected() + " with RS("
          + rs.getServerId() + ") genId=" + getGenerationID());
    }
  }

  /**
   * Publish a message to the other servers.
   * @param msg the message to publish
   */
  public void publish(ReplicationMsg msg)
  {
    publish(msg, false, true);
  }

  /**
   * Publish a message to the other servers.
   * @param msg            The message to publish.
   * @param retryOnFailure Whether reconnect should automatically be done.
   * @return               Whether publish succeeded.
   */
  boolean publish(ReplicationMsg msg, boolean retryOnFailure)
  {
    return publish(msg, false, retryOnFailure);
  }

  /**
   * Publish a recovery message to the other servers.
   * @param msg the message to publish
   */
  public void publishRecovery(ReplicationMsg msg)
  {
    publish(msg, true, true);
  }

  /**
   * Publish a message to the other servers.
   * @param msg the message to publish
   * @param recoveryMsg the message is a recovery LocalizableMessage
   * @param retryOnFailure whether retry should be done on failure
   * @return whether the message was successfully sent.
   */
  private boolean publish(ReplicationMsg msg, boolean recoveryMsg,
      boolean retryOnFailure)
  {
    boolean done = false;

    while (!done && !shutdown)
    {
      if (connectionError)
      {
        /*
        It was not possible to connect to any replication server.
        Since the operation was already processed, we have no other
        choice than to return without sending the ReplicationMsg
        and relying on the resend procedure of the connect phase to
        fix the problem when we finally connect.
        */

        if (logger.isTraceEnabled())
        {
          debugInfo("publish(): Publishing a message is not possible due to"
              + " existing connection error.");
        }

        return false;
      }

      try
      {
        /*
        save the session at the time when we acquire the
        sendwindow credit so that we can make sure later
        that the session did not change in between.
        This is necessary to make sure that we don't publish a message
        on a session with a credit that was acquired from a previous
        session.
        */
        Session currentSession;
        Semaphore currentWindowSemaphore;
        synchronized (connectPhaseLock)
        {
          currentSession = connectedRS.get().session;
          currentWindowSemaphore = sendWindow;
        }

        /*
        If the Replication domain has decided that there is a need to
        recover some changes then it is not allowed to send this
        change but it will be the responsibility of the recovery thread to
        do it.
        */
        if (!recoveryMsg & connectRequiresRecovery)
        {
          return false;
        }

        boolean credit;
        if (msg instanceof UpdateMsg)
        {
          /*
          Acquiring the window credit must be done outside of the
          connectPhaseLock because it can be blocking and we don't
          want to hold off reconnection in case the connection dropped.
          */
          credit =
            currentWindowSemaphore.tryAcquire(500, TimeUnit.MILLISECONDS);
        }
        else
        {
          credit = true;
        }

        if (credit)
        {
          synchronized (connectPhaseLock)
          {
            /*
            session may have been set to null in the connection phase
            when restarting the broker for example.
            Check the session. If it has changed, some disconnection or
            reconnection happened and we need to restart from scratch.
            */
            final Session session = connectedRS.get().session;
            if (session != null && session == currentSession)
            {
              session.publish(msg);
              done = true;
            }
          }
        }
        if (!credit && currentWindowSemaphore.availablePermits() == 0)
        {
          synchronized (connectPhaseLock)
          {
            /*
            the window is still closed.
            Send a WindowProbeMsg message to wake up the receiver in case the
            window update message was lost somehow...
            then loop to check again if connection was closed.
            */
            Session session = connectedRS.get().session;
            if (session != null)
            {
              session.publish(new WindowProbeMsg());
            }
          }
        }
      }
      catch (IOException e)
      {
        if (logger.isTraceEnabled())
        {
          debugInfo("publish(): IOException caught: "
              + stackTraceToSingleLineString(e));
        }
        if (!retryOnFailure)
        {
          return false;
        }

        // The receive threads should handle reconnection or
        // mark this broker in error. Just retry.
        synchronized (connectPhaseLock)
        {
          try
          {
            connectPhaseLock.wait(100);
          }
          catch (InterruptedException ignored)
          {
            if (logger.isTraceEnabled())
            {
              debugInfo("publish(): InterruptedException caught 1: "
                  + stackTraceToSingleLineString(ignored));
            }
          }
        }
      }
      catch (InterruptedException ignored)
      {
        // just loop.
        if (logger.isTraceEnabled())
        {
          debugInfo("publish(): InterruptedException caught 2: "
              + stackTraceToSingleLineString(ignored));
        }
      }
    }
    return true;
  }

  /**
   * Receive a message.
   * This method is not thread-safe and should either always be
   * called in a single thread or protected by a locking mechanism
   * before being called. This is a wrapper to the method with a boolean version
   * so that we do not have to modify existing tests.
   *
   * @return the received message
   * @throws SocketTimeoutException if the timeout set by setSoTimeout
   *         has expired
   */
  public ReplicationMsg receive() throws SocketTimeoutException
  {
    return receive(false, true, false);
  }

  /**
   * Receive a message.
   * This method is not thread-safe and should either always be
   * called in a single thread or protected by a locking mechanism
   * before being called.
   *
   * @param reconnectToTheBestRS Whether broker will automatically switch
   *                             to the best suitable RS.
   * @param reconnectOnFailure   Whether broker will automatically reconnect
   *                             on failure.
   * @param returnOnTopoChange   Whether broker should return TopologyMsg
   *                             received.
   * @return the received message
   *
   * @throws SocketTimeoutException if the timeout set by setSoTimeout
   *         has expired
   */
  ReplicationMsg receive(boolean reconnectToTheBestRS,
      boolean reconnectOnFailure, boolean returnOnTopoChange)
    throws SocketTimeoutException
  {
    while (!shutdown)
    {
      ConnectedRS rs = connectedRS.get();
      if (!rs.isConnected())
      {
        if (reconnectOnFailure)
        {
          // infinite try to reconnect
          reStart(null, true);
          continue;
        }
        else
        {
          // Must be shutting down.
          break;
        }
      }

      final int serverId = getServerId();
      final DN baseDN = getBaseDN();
      final int previousRsServerID = rs.getServerId();
      try
      {
        ReplicationMsg msg = rs.session.receive();
        if (msg instanceof UpdateMsg)
        {
          synchronized (this)
          {
            rcvWindow--;
          }
        }
        if (msg instanceof WindowMsg)
        {
          final WindowMsg windowMsg = (WindowMsg) msg;
          sendWindow.release(windowMsg.getNumAck());
        }
        else if (msg instanceof TopologyMsg)
        {
          final TopologyMsg topoMsg = (TopologyMsg) msg;
          receiveTopo(topoMsg, getRsServerId());
          if (reconnectToTheBestRS)
          {
            // Reset wait time before next computation of best server
            mustRunBestServerCheckingAlgorithm = 0;
          }

          // Caller wants to check what's changed
          if (returnOnTopoChange)
          {
            return msg;
          }
        }
        else if (msg instanceof StopMsg)
        {
          // RS performs a proper disconnection
          logger.warn(WARN_REPLICATION_SERVER_PROPERLY_DISCONNECTED, previousRsServerID, rs.replicationServer,
              serverId, baseDN);

          // Try to find a suitable RS
          reStart(rs.session, true);
        }
        else if (msg instanceof MonitorMsg)
        {
          // This is the response to a MonitorRequest that was sent earlier or
          // the regular message of the monitoring publisher of the RS.
          MonitorMsg monitorMsg = (MonitorMsg) msg;

          // Extract and store replicas ServerStates
          final Map<Integer, ServerState> newReplicaStates = new HashMap<>();
          for (int srvId : toIterable(monitorMsg.ldapIterator()))
          {
            newReplicaStates.put(srvId, monitorMsg.getLDAPServerState(srvId));
          }
          replicaStates = newReplicaStates;

          // Notify the sender that the response was received.
          synchronized (monitorResponse)
          {
            monitorResponse.set(true);
            monitorResponse.notify();
          }

          // Update the replication servers ServerStates with new received info
          Map<Integer, ReplicationServerInfo> rsInfos = topology.get().rsInfos;
          for (int srvId : toIterable(monitorMsg.rsIterator()))
          {
            final ReplicationServerInfo rsInfo = rsInfos.get(srvId);
            if (rsInfo != null)
            {
              rsInfo.update(monitorMsg.getRSServerState(srvId));
            }
          }

          /*
          Now if it is allowed, compute the best replication server to see if
          it is still the one we are currently connected to. If not,
          disconnect properly and let the connection algorithm re-connect to
          best replication server
          */
          if (reconnectToTheBestRS)
          {
            mustRunBestServerCheckingAlgorithm++;
            if (mustRunBestServerCheckingAlgorithm == 2)
            {
              // Stable topology (no topo msg since few seconds): proceed with
              // best server checking.
              final RSEvaluations evals = computeBestReplicationServer(
                  false, previousRsServerID, state,
                  rsInfos, serverId, getGroupId(), getGenerationID());
              final ReplicationServerInfo bestServerInfo = evals.getBestRS();
              if (previousRsServerID != -1
                  && (bestServerInfo == null
                      || bestServerInfo.getServerId() != previousRsServerID))
              {
                // The best replication server is no more the one we are
                // currently using. Disconnect properly then reconnect.
                LocalizableMessage message;
                if (bestServerInfo == null)
                {
                  message = NOTE_LOAD_BALANCE_REPLICATION_SERVER.get(
                      serverId, previousRsServerID, rs.replicationServer, baseDN);
                }
                else
                {
                  final int bestRsServerId = bestServerInfo.getServerId();
                  message = NOTE_NEW_BEST_REPLICATION_SERVER.get(
                      serverId, previousRsServerID, rs.replicationServer, bestRsServerId, baseDN,
                      evals.getEvaluation(previousRsServerID),
                      evals.getEvaluation(bestRsServerId));
                }
                logger.info(message);
                if (logger.isTraceEnabled())
                {
                  debugInfo("best replication servers evaluation results: " + evals);
                }
                reStart(true);
              }

              // Reset wait time before next computation of best server
              mustRunBestServerCheckingAlgorithm = 0;
            }
          }
        }
        else
        {
          return msg;
        }
      }
      catch (SocketTimeoutException e)
      {
        throw e;
      }
      catch (Exception e)
      {
        logger.traceException(e);

        if (!shutdown)
        {
          if (rs.session == null || !rs.session.closeInitiated())
          {
            // We did not initiate the close on our side, log an error message.
            logger.error(WARN_REPLICATION_SERVER_BADLY_DISCONNECTED,
                serverId, baseDN, previousRsServerID, rs.replicationServer);
          }

          if (!reconnectOnFailure)
          {
            break; // does not seem necessary to explicitly disconnect ..
          }

          reStart(rs.session, true);
        }
      }
    }
    return null;
  }

  /**
   * Gets the States of all the Replicas currently in the Topology. When this
   * method is called, a Monitoring message will be sent to the Replication
   * Server to which this domain is currently connected so that it computes a
   * table containing information about all Directory Servers in the topology.
   * This Computation involves communications will all the servers currently
   * connected and
   *
   * @return The States of all Replicas in the topology (except us)
   */
  public Map<Integer, ServerState> getReplicaStates()
  {
    monitorResponse.set(false);

    // publish Monitor Request LocalizableMessage to the Replication Server
    publish(new MonitorRequestMsg(getServerId(), getRsServerId()));

    // wait for Response up to 10 seconds.
    try
    {
      synchronized (monitorResponse)
      {
        if (!monitorResponse.get())
        {
          monitorResponse.wait(10000);
        }
      }
    } catch (InterruptedException e)
    {
      Thread.currentThread().interrupt();
    }
    return replicaStates;
  }

  /**
   * This method allows to do the necessary computing for the window
   * management after treatment by the worker threads.
   *
   * This should be called once the replay thread have done their job
   * and the window can be open again.
   */
  public synchronized void updateWindowAfterReplay()
  {
    try
    {
      updateDoneCount++;
      final Session session = connectedRS.get().session;
      if (updateDoneCount >= halfRcvWindow && session != null)
      {
        session.publish(new WindowMsg(updateDoneCount));
        rcvWindow += updateDoneCount;
        updateDoneCount = 0;
      }
    } catch (IOException e)
    {
      // Any error on the socket will be handled by the thread calling receive()
      // just ignore.
    }
  }

  /** Stop the server. */
  public void stop()
  {
    if (logger.isTraceEnabled() && !shutdown)
    {
      debugInfo("is stopping and will close the connection to RS(" + getRsServerId() + ")");
    }

    synchronized (startStopLock)
    {
      if (shutdown)
      {
        return;
      }
      domain.publishReplicaOfflineMsg();
      shutdown = true;
      setConnectedRS(ConnectedRS.stopped());
      stopRSHeartBeatMonitoring();
      stopChangeTimeHeartBeatPublishing();
      deregisterReplicationMonitor();
    }
  }

  /**
   * Set a timeout value.
   * With this option set to a non-zero value, calls to the receive() method
   * block for only this amount of time after which a
   * java.net.SocketTimeoutException is raised.
   * The Broker is valid and usable even after such an Exception is raised.
   *
   * @param timeout the specified timeout, in milliseconds.
   * @throws SocketException if there is an error in the underlying protocol,
   *         such as a TCP error.
   */
  public void setSoTimeout(int timeout) throws SocketException
  {
    this.timeout = timeout;
    final Session session = connectedRS.get().session;
    if (session != null)
    {
      session.setSoTimeout(timeout);
    }
  }

  /**
   * Get the host and port of the replicationServer to which this broker is currently connected.
   *
   * @return the host and port of the replicationServer to which this domain is currently connected.
   */
  public HostPort getReplicationServer()
  {
    return connectedRS.get().replicationServer;
  }

  /**
   * Get the maximum receive window size.
   *
   * @return The maximum receive window size.
   */
  public int getMaxRcvWindow()
  {
    return config.getWindowSize();
  }

  /**
   * Get the current receive window size.
   *
   * @return The current receive window size.
   */
  public int getCurrentRcvWindow()
  {
    return rcvWindow;
  }

  /**
   * Get the maximum send window size.
   *
   * @return The maximum send window size.
   */
  public int getMaxSendWindow()
  {
    return maxSendWindow;
  }

  /**
   * Get the current send window size.
   *
   * @return The current send window size.
   */
  public int getCurrentSendWindow()
  {
    if (isConnected())
    {
      return sendWindow.availablePermits();
    }
    return 0;
  }

  /**
   * Get the number of times the connection was lost.
   * @return The number of times the connection was lost.
   */
  public int getNumLostConnections()
  {
    return numLostConnections;
  }

  /**
   * Change some configuration parameters.
   *
   * @param newConfig  The new config to use.
   * @return                    A boolean indicating if the changes
   *                            requires to restart the service.
   */
  boolean changeConfig(ReplicationDomainCfg newConfig)
  {
    // These parameters needs to be renegotiated with the ReplicationServer
    // so if they have changed, that requires restarting the session with
    // the ReplicationServer.
    // A new session is necessary only when information regarding
    // the connection is modified
    boolean needToRestartSession =
        !newConfig.getReplicationServer().equals(config.getReplicationServer())
        || newConfig.getWindowSize() != config.getWindowSize()
        || newConfig.getHeartbeatInterval() != config.getHeartbeatInterval()
        || newConfig.getGroupId() != config.getGroupId();

    this.config = newConfig;
    this.rcvWindow = newConfig.getWindowSize();
    this.halfRcvWindow = this.rcvWindow / 2;

    return needToRestartSession;
  }

  /**
   * Get the version of the replication protocol.
   * @return The version of the replication protocol.
   */
  public short getProtocolVersion()
  {
    final Session session = connectedRS.get().session;
    return session != null ? session.getProtocolVersion() : ProtocolVersion.getCurrentVersion();
  }

  /**
   * Check if the broker is connected to a ReplicationServer and therefore
   * ready to received and send Replication Messages.
   *
   * @return true if the server is connected, false if not.
   */
  public boolean isConnected()
  {
    return connectedRS.get().isConnected();
  }

  /**
   * Determine whether the connection to the replication server is encrypted.
   * @return true if the connection is encrypted, false otherwise.
   */
  public boolean isSessionEncrypted()
  {
    final Session session = connectedRS.get().session;
    return session != null ? session.isEncrypted() : false;
  }

  /**
   * Signals the RS we just entered a new status.
   * @param newStatus The status the local DS just entered
   */
  public void signalStatusChange(ServerStatus newStatus)
  {
    try
    {
      connectedRS.get().session.publish(
          new ChangeStatusMsg(ServerStatus.INVALID_STATUS, newStatus));
    } catch (IOException ex)
    {
      logger.error(ERR_EXCEPTION_SENDING_CS, getBaseDN(), getServerId(),
          ex.getLocalizedMessage() + " " + stackTraceToSingleLineString(ex));
    }
  }

  /**
   * Gets the info for DSs in the topology (except us).
   * @return The info for DSs in the topology (except us)
   */
  public Map<Integer, DSInfo> getReplicaInfos()
  {
    return topology.get().replicaInfos;
  }

  /**
   * Gets the info for RSs in the topology (except the one we are connected
   * to).
   * @return The info for RSs in the topology (except the one we are connected
   * to)
   */
  public List<RSInfo> getRsInfos()
  {
    return toRSInfos(topology.get().rsInfos);
  }

  private List<RSInfo> toRSInfos(Map<Integer, ReplicationServerInfo> rsInfos)
  {
    final List<RSInfo> result = new ArrayList<>();
    for (ReplicationServerInfo rsInfo : rsInfos.values())
    {
      result.add(rsInfo.toRSInfo());
    }
    return result;
  }

  /**
   * Processes an incoming TopologyMsg.
   * Updates the structures for the local view of the topology.
   *
   * @param topoMsg
   *          The topology information received from RS.
   * @param rsServerId
   *          the serverId to use for the connectedDS
   */
  private void receiveTopo(TopologyMsg topoMsg, int rsServerId)
  {
    final Topology newTopo = computeNewTopology(topoMsg, rsServerId);
    for (DSInfo dsInfo : newTopo.replicaInfos.values())
    {
      domain.setEclIncludes(dsInfo.getDsId(), dsInfo.getEclIncludes(), dsInfo
          .getEclIncludesForDeletes());
    }
  }

  private Topology computeNewTopology(TopologyMsg topoMsg, int rsServerId)
  {
    Topology oldTopo;
    Topology newTopo;
    do
    {
      oldTopo = topology.get();
      newTopo = new Topology(topoMsg, getServerId(), rsServerId,
              getReplicationServerUrls(), oldTopo.rsInfos);
    }
    while (!topology.compareAndSet(oldTopo, newTopo));

    if (logger.isTraceEnabled())
    {
      final StringBuilder sb = topologyChange(rsServerId, oldTopo, newTopo);
      sb.append(" received TopologyMsg=").append(topoMsg);
      debugInfo(sb);
    }
    return newTopo;
  }

  /** Contains the last known state of the replication topology. */
  static final class Topology
  {
    /** The RS's serverId that this DS was connected to when this topology state was computed. */
    private final int rsServerId;
    /**
     * Info for other DSs.
     * <p>
     * Warning: does not contain info for us (for our server id)
     */
    final Map<Integer, DSInfo> replicaInfos;
    /**
     * The map of replication server info initialized at connection time and
     * regularly updated. This is used to decide to which best suitable
     * replication server one wants to connect. Key: replication server id
     * Value: replication server info for the matching replication server id
     */
    final Map<Integer, ReplicationServerInfo> rsInfos;

    private Topology()
    {
      this.rsServerId = -1;
      this.replicaInfos = Collections.emptyMap();
      this.rsInfos = Collections.emptyMap();
    }

    /**
     * Constructor to use when only the RSInfos need to be recomputed.
     *
     * @param dsInfosToKeep
     *          the DSInfos that will be stored as is
     * @param newRSInfos
     *          the new RSInfos from which to compute the new topology
     * @param dsServerId
     *          the DS serverId
     * @param rsServerId
     *          the current connected RS serverId
     * @param configuredReplicationServerUrls
     *          the configured replication server URLs
     * @param previousRsInfos
     *          the RSInfos computed in the previous Topology object
     */
    Topology(Map<Integer, DSInfo> dsInfosToKeep, List<RSInfo> newRSInfos,
        int dsServerId, int rsServerId,
        Set<String> configuredReplicationServerUrls,
        Map<Integer, ReplicationServerInfo> previousRsInfos)
    {
      this.rsServerId = rsServerId;
      this.replicaInfos = dsInfosToKeep == null
          ? Collections.<Integer, DSInfo>emptyMap() : dsInfosToKeep;
      this.rsInfos = computeRSInfos(dsServerId, newRSInfos,
          previousRsInfos, configuredReplicationServerUrls);
    }

    /**
     * Constructor to use when a new TopologyMsg has been received.
     *
     * @param topoMsg
     *          the topology message containing the new DSInfos and RSInfos from
     *          which to compute the new topology
     * @param dsServerId
     *          the DS serverId
     * @param rsServerId
     *          the current connected RS serverId
     * @param configuredReplicationServerUrls
     *          the configured replication server URLs
     * @param previousRsInfos
     *          the RSInfos computed in the previous Topology object
     */
    Topology(TopologyMsg topoMsg, int dsServerId,
        int rsServerId, Set<String> configuredReplicationServerUrls,
        Map<Integer, ReplicationServerInfo> previousRsInfos)
    {
      this.rsServerId = rsServerId;
      this.replicaInfos = removeThisDs(topoMsg.getReplicaInfos(), dsServerId);
      this.rsInfos = computeRSInfos(dsServerId, topoMsg.getRsInfos(),
          previousRsInfos, configuredReplicationServerUrls);
    }

    private Map<Integer, DSInfo> removeThisDs(Map<Integer, DSInfo> dsInfos,
        int dsServerId)
    {
      final Map<Integer, DSInfo> copy = new HashMap<>(dsInfos);
      copy.remove(dsServerId);
      return Collections.unmodifiableMap(copy);
    }

    private Map<Integer, ReplicationServerInfo> computeRSInfos(
        int dsServerId, List<RSInfo> newRsInfos,
        Map<Integer, ReplicationServerInfo> previousRsInfos,
        Set<String> configuredReplicationServerUrls)
    {
      final Map<Integer, ReplicationServerInfo> results = new HashMap<>(previousRsInfos);

      // Update replication server info list with the received topology info
      final Set<Integer> rssToKeep = new HashSet<>();
      for (RSInfo newRSInfo : newRsInfos)
      {
        final int rsId = newRSInfo.getId();
        rssToKeep.add(rsId); // Mark this server as still existing
        Set<Integer> connectedDSs =
            computeDSsConnectedTo(rsId, dsServerId);
        ReplicationServerInfo rsInfo = results.get(rsId);
        if (rsInfo == null)
        {
          // New replication server, create info for it add it to the list
          rsInfo = new ReplicationServerInfo(newRSInfo, connectedDSs);
          setLocallyConfiguredFlag(rsInfo, configuredReplicationServerUrls);
          results.put(rsId, rsInfo);
        }
        else
        {
          // Update the existing info for the replication server
          rsInfo.update(newRSInfo, connectedDSs);
        }
      }

      // Remove any replication server that may have disappeared from the
      // topology
      results.keySet().retainAll(rssToKeep);

      return Collections.unmodifiableMap(results);
    }

    /** Computes the list of DSs connected to a particular RS. */
    private Set<Integer> computeDSsConnectedTo(int rsId, int dsServerId)
    {
      final Set<Integer> connectedDSs = new HashSet<>();
      if (rsServerId == rsId)
      {
        /*
         * If we are computing connected DSs for the RS we are connected to, we
         * should count the local DS as the DSInfo of the local DS is not sent
         * by the replication server in the topology message. We must count
         * ourselves as a connected server.
         */
        connectedDSs.add(dsServerId);
      }

      for (DSInfo dsInfo : replicaInfos.values())
      {
        if (dsInfo.getRsId() == rsId)
        {
          connectedDSs.add(dsInfo.getDsId());
        }
      }

      return connectedDSs;
    }

    /**
     * Sets the locally configured flag for the passed ReplicationServerInfo
     * object, analyzing the local configuration.
     *
     * @param rsInfo
     *          the Replication server to check and update
     * @param configuredReplicationServerUrls
     */
    private void setLocallyConfiguredFlag(ReplicationServerInfo rsInfo,
        Set<String> configuredReplicationServerUrls)
    {
      // Determine if the passed ReplicationServerInfo has a URL that is present
      // in the locally configured replication servers
      String rsUrl = rsInfo.getServerURL();
      if (rsUrl == null)
      {
        // The ReplicationServerInfo has been generated from a server with
        // no URL in TopologyMsg (i.e: with replication protocol version < 4):
        // ignore this server as we do not know how to connect to it
        rsInfo.setLocallyConfigured(false);
        return;
      }
      for (String serverUrl : configuredReplicationServerUrls)
      {
        if (isSameReplicationServerUrl(serverUrl, rsUrl))
        {
          // This RS is locally configured, mark this
          rsInfo.setLocallyConfigured(true);
          rsInfo.setServerURL(serverUrl);
          return;
        }
      }
      rsInfo.setLocallyConfigured(false);
    }

    @Override
    public boolean equals(Object obj)
    {
      if (this == obj)
      {
        return true;
      }
      if (obj == null || getClass() != obj.getClass())
      {
        return false;
      }
      final Topology other = (Topology) obj;
      return rsServerId == other.rsServerId
          && Objects.equals(replicaInfos, other.replicaInfos)
          && Objects.equals(rsInfos, other.rsInfos)
          && urlsEqual1(replicaInfos, other.replicaInfos)
          && urlsEqual2(rsInfos, other.rsInfos);
    }

    private boolean urlsEqual1(Map<Integer, DSInfo> replicaInfos1,
        Map<Integer, DSInfo> replicaInfos2)
    {
      for (Entry<Integer, DSInfo> entry : replicaInfos1.entrySet())
      {
        DSInfo dsInfo = replicaInfos2.get(entry.getKey());
        if (!Objects.equals(entry.getValue().getDsUrl(), dsInfo.getDsUrl()))
        {
          return false;
        }
      }
      return true;
    }

    private boolean urlsEqual2(Map<Integer, ReplicationServerInfo> rsInfos1,
        Map<Integer, ReplicationServerInfo> rsInfos2)
    {
      for (Entry<Integer, ReplicationServerInfo> entry : rsInfos1.entrySet())
      {
        ReplicationServerInfo rsInfo = rsInfos2.get(entry.getKey());
        if (!Objects.equals(entry.getValue().getServerURL(), rsInfo.getServerURL()))
        {
          return false;
        }
      }
      return true;
    }

    @Override
    public int hashCode()
    {
      final int prime = 31;
      int result = 1;
      result = prime * result + rsServerId;
      result = prime * result
          + (replicaInfos == null ? 0 : replicaInfos.hashCode());
      result = prime * result + (rsInfos == null ? 0 : rsInfos.hashCode());
      return result;
    }

    @Override
    public String toString()
    {
      return getClass().getSimpleName()
          + " rsServerId=" + rsServerId
          + ", replicaInfos=" + replicaInfos.values()
          + ", rsInfos=" + rsInfos.values();
    }
  }

  /**
   * Check if the broker could not find any Replication Server and therefore
   * connection attempt failed.
   *
   * @return true if the server could not connect to any Replication Server.
   */
  boolean hasConnectionError()
  {
    return connectionError;
  }

  /** Starts publishing to the RS the current timestamp used in this server. */
  private void startChangeTimeHeartBeatPublishing(ConnectedRS rs)
  {
    // Start a CSN heartbeat thread.
    long changeTimeHeartbeatInterval = config.getChangetimeHeartbeatInterval();
    if (changeTimeHeartbeatInterval > 0)
    {
      final String threadName = "Replica DS(" + getServerId()
              + ") change time heartbeat publisher for domain \"" + getBaseDN()
              + "\" to RS(" + rs.getServerId() + ") at " + rs.replicationServer;

      ctHeartbeatPublisherThread = new CTHeartbeatPublisherThread(
          threadName, rs.session, changeTimeHeartbeatInterval, getServerId());
      ctHeartbeatPublisherThread.start();
    }
    else if (logger.isTraceEnabled())
    {
      debugInfo("is not configured to send CSN heartbeat interval");
    }
  }

  /** Stops publishing to the RS the current timestamp used in this server. */
  private synchronized void stopChangeTimeHeartBeatPublishing()
  {
    if (ctHeartbeatPublisherThread != null)
    {
      ctHeartbeatPublisherThread.shutdown();
      ctHeartbeatPublisherThread = null;
    }
  }

  /**
   * Set the connectRequiresRecovery to the provided value.
   * This flag is used to indicate if a recovery of Update is necessary
   * after a reconnection to a RS.
   * It is the responsibility of the ReplicationDomain to set it during the
   * sessionInitiated phase.
   *
   * @param b the new value of the connectRequiresRecovery.
   */
  public void setRecoveryRequired(boolean b)
  {
    connectRequiresRecovery = b;
  }

  /**
   * Returns whether the broker is shutting down.
   * @return whether the broker is shutting down.
   */
  boolean shuttingDown()
  {
    return shutdown;
  }

  /**
   * Returns the local address of this replication domain, or the empty string
   * if it is not yet connected.
   *
   * @return The local address.
   */
  HostPort getLocalUrl()
  {
    final Session session = connectedRS.get().session;
    return session != null ? session.getLocalUrl() : new HostPort(null, 0);
  }

  /**
   * Returns the replication monitor instance name associated with this broker.
   *
   * @return The replication monitor instance name.
   */
  String getReplicationMonitorInstanceName()
  {
    // Only invoked by replication domain so always non-null.
    return monitor.getMonitorInstanceName();
  }

  /**
   * Updates the currently connected replication server reference.
   * <p>
   * If the new replication server differs from the previous one, this method will:
   * <ul>
   * <li>deregister the replication monitor associated with the old server,
   * <li>close the old replication session,
   * <li>and register a replication monitor for the new server.
   *
   * @param newRS The new {@link ConnectedRS} instance to set as the currently connected replication server.
   * @return The newly set {@link ConnectedRS} instance.
   */
  private ConnectedRS setConnectedRS(final ConnectedRS newRS)
  {
    ConnectedRS oldRS = connectedRS.get();
    if (!oldRS.equals(newRS))
    {
      // monitor name is changing, deregister before registering again
      deregisterReplicationMonitor();
      if (oldRS.isConnected())
      {
        oldRS.session.close();
      }
      connectedRS.set(newRS);
      registerReplicationMonitor();
    }
    return newRS;
  }

  /**
   * Must be invoked each time the session changes because, the monitor name is
   * dynamically created with the session name, while monitor registration is
   * static.
   *
   * @see #monitor
   */
  private void registerReplicationMonitor()
  {
    // The monitor should not be registered if this is a unit test
    // because the replication domain is null.
    if (monitor != null)
    {
      DirectoryServer.registerMonitorProvider(monitor);
    }
  }

  private void deregisterReplicationMonitor()
  {
    // The monitor should not be deregistered if this is a unit test
    // because the replication domain is null.
    if (monitor != null)
    {
      DirectoryServer.deregisterMonitorProvider(monitor);
    }
  }

  @Override
  public String toString()
  {
    final StringBuilder sb = new StringBuilder();
    sb.append(getClass().getSimpleName())
      .append(" \"").append(getBaseDN()).append(" ")
      .append(getServerId()).append("\",")
      .append(" groupId=").append(getGroupId())
      .append(", genId=").append(getGenerationID())
      .append(", ");
    connectedRS.get().toString(sb);
    return sb.toString();
  }

  private void debugInfo(CharSequence message)
  {
    logger.trace(getClass().getSimpleName() + " for baseDN=" + getBaseDN()
        + " and serverId=" + getServerId() + ": " + message);
  }
}
