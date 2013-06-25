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
 *      Portions Copyright 2011-2013 ForgeRock AS
 */
package org.opends.server.replication.service;

import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import static org.opends.server.replication.protocol.ProtocolVersion.*;
import static org.opends.server.replication.server.ReplicationServer.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.common.DSInfo;
import org.opends.server.replication.common.MutableBoolean;
import org.opends.server.replication.common.RSInfo;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.common.ServerStatus;
import org.opends.server.replication.plugin.MultimasterReplication;
import org.opends.server.replication.protocol.*;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.util.ServerConstants;

/**
 * The broker for Multi-master Replication.
 */
public class ReplicationBroker
{

  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();
  private volatile boolean shutdown = false;
  private final Object startStopLock = new Object();
  /**
   * Replication server URLs under this format: "<code>hostname:port</code>".
   */
  private volatile Collection<String> replicationServerUrls;
  private volatile boolean connected = false;
  /**
   * String reported under cn=monitor when there is no connected RS.
   */
  public final static String NO_CONNECTED_SERVER = "Not connected";
  private volatile String replicationServer = NO_CONNECTED_SERVER;
  private volatile Session session = null;
  private final ServerState state;
  private final String baseDn;
  private final int serverId;
  private Semaphore sendWindow;
  private int maxSendWindow;
  private int rcvWindow = 100;
  private int halfRcvWindow = rcvWindow / 2;
  private int maxRcvWindow = rcvWindow;
  private int timeout = 0;
  private short protocolVersion;
  private ReplSessionSecurity replSessionSecurity;
  /** My group id. */
  private byte groupId = -1;
  /** The group id of the RS we are connected to. */
  private byte rsGroupId = -1;
  /** The server id of the RS we are connected to. */
  private Integer rsServerId = -1;
  /** The server URL of the RS we are connected to. */
  private String rsServerUrl = null;
  /** Our replication domain. */
  private ReplicationDomain domain = null;
  /**
   * This object is used as a conditional event to be notified about
   * the reception of monitor information from the Replication Server.
   */
  private final MutableBoolean monitorResponse = new MutableBoolean(false);
  /**
   * A Map containing the ServerStates of all the replicas in the topology
   * as seen by the ReplicationServer the last time it was polled or the last
   * time it published monitoring information.
   */
  private HashMap<Integer, ServerState> replicaStates =
    new HashMap<Integer, ServerState>();
  /**
   * The expected duration in milliseconds between heartbeats received
   * from the replication server.  Zero means heartbeats are off.
   */
  private long heartbeatInterval = 0;
  /**
   * A thread to monitor heartbeats on the session.
   */
  private HeartbeatMonitor heartbeatMonitor = null;
  /**
   * The number of times the connection was lost.
   */
  private int numLostConnections = 0;
  /**
   * When the broker cannot connect to any replication server
   * it log an error and keeps continuing every second.
   * This boolean is set when the first failure happens and is used
   * to avoid repeating the error message for further failure to connect
   * and to know that it is necessary to print a new message when the broker
   * finally succeed to connect.
   */
  private volatile boolean connectionError = false;
  private final Object connectPhaseLock = new Object();
  /**
   * The thread that publishes messages to the RS containing the current
   * change time of this DS.
   */
  private CTHeartbeatPublisherThread ctHeartbeatPublisherThread = null;
  /**
   * The expected period in milliseconds between these messages are sent
   * to the replication server. Zero means heartbeats are off.
   */
  private long changeTimeHeartbeatSendInterval = 0;
  /*
   * Properties for the last topology info received from the network.
   */
  // Info for other DSs.
  // Warning: does not contain info for us (for our server id)
  private volatile List<DSInfo> dsList = new ArrayList<DSInfo>();
  private volatile long generationID;
  private volatile int updateDoneCount = 0;
  private volatile boolean connectRequiresRecovery = false;

  /**
   * The map of replication server info initialized at connection time and
   * regularly updated. This is used to decide to which best suitable
   * replication server one wants to connect. Key: replication server id Value:
   * replication server info for the matching replication server id
   */
  private volatile Map<Integer, ReplicationServerInfo> replicationServerInfos
    = null;

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
  private int mustRunBestServerCheckingAlgorithm = 0;

  /**
   * The monitor provider for this replication domain. The name of the monitor
   * includes the local address and must therefore be re-registered every time
   * the session is re-established or destroyed. The monitor provider can only
   * be created (i.e. non-null) if there is a replication domain, which is not
   * the case in unit tests.
   */
  private final ReplicationMonitor monitor;

  /**
   * Creates a new ReplicationServer Broker for a particular ReplicationDomain.
   *
   * @param replicationDomain The replication domain that is creating us.
   * @param state The ServerState that should be used by this broker
   *        when negotiating the session with the replicationServer.
   * @param baseDn The base DN that should be used by this broker
   *        when negotiating the session with the replicationServer.
   * @param serverID2 The server ID that should be used by this broker
   *        when negotiating the session with the replicationServer.
   * @param window The size of the send and receive window to use.
   * @param generationId The generationId for the server associated to the
   * provided serverId and for the domain associated to the provided baseDN.
   * @param heartbeatInterval The interval (in ms) between heartbeats requested
   *        from the replicationServer, or zero if no heartbeats are requested.
   * @param replSessionSecurity The session security configuration.
   * @param groupId The group id of our domain.
   * @param changeTimeHeartbeatInterval The interval (in ms) between Change
   *        time  heartbeats are sent to the RS,
   *        or zero if no CN heartbeat should be sent.
   */
  public ReplicationBroker(ReplicationDomain replicationDomain,
    ServerState state, String baseDn, int serverID2, int window,
    long generationId, long heartbeatInterval,
    ReplSessionSecurity replSessionSecurity, byte groupId,
    long changeTimeHeartbeatInterval)
  {
    this.domain = replicationDomain;
    this.baseDn = baseDn;
    this.serverId = serverID2;
    this.state = state;
    this.protocolVersion = ProtocolVersion.getCurrentVersion();
    this.replSessionSecurity = replSessionSecurity;
    this.groupId = groupId;
    this.generationID = generationId;
    this.heartbeatInterval = heartbeatInterval;
    this.rcvWindow = window;
    this.maxRcvWindow = window;
    this.halfRcvWindow = window / 2;
    this.changeTimeHeartbeatSendInterval = changeTimeHeartbeatInterval;

    /*
     * Only create a monitor if there is a replication domain (this is not the
     * case in some unit tests).
     */
    this.monitor = replicationDomain != null ? new ReplicationMonitor(
        replicationDomain) : null;
    registerReplicationMonitor();
  }

  /**
   * Start the ReplicationBroker.
   */
  public void start()
  {
    synchronized (startStopLock)
    {
      shutdown = false;
      this.rcvWindow = this.maxRcvWindow;
      this.connect();
    }
  }

  /**
   * Start the ReplicationBroker.
   *
   * @param replicationServers list of servers used
   */
  public void start(Collection<String> replicationServers)
  {
    synchronized (startStopLock)
    {
      // Open Socket to the ReplicationServer Send the Start message
      shutdown = false;
      this.replicationServerUrls = replicationServers;

      if (this.replicationServerUrls.size() < 1)
      {
        Message message = NOTE_NEED_MORE_THAN_ONE_CHANGELOG_SERVER.get();
        logError(message);
      }

      this.rcvWindow = this.maxRcvWindow;
      this.connect();
    }
  }

  /**
   * Gets the group id of the RS we are connected to.
   * @return The group id of the RS we are connected to
   */
  public byte getRsGroupId()
  {
    return rsGroupId;
  }

  /**
   * Gets the server id of the RS we are connected to.
   * @return The server id of the RS we are connected to
   */
  public Integer getRsServerId()
  {
    return rsServerId;
  }

  /**
   * Gets the server id.
   * @return The server id
   */
  public int getServerId()
  {
    return serverId;
  }

  /**
   * Gets the server id.
   * @return The server id
   */
  private long getGenerationID()
  {
    if (domain != null)
    {
      // Update the generation id
      generationID = domain.getGenerationID();
    }
    return generationID;
  }

  /**
   * Set the generation id - for test purpose.
   * @param generationID The generation id
   */
  public void setGenerationID(long generationID)
  {
    this.generationID = generationID;
  }

  /**
   * Gets the server url of the RS we are connected to.
   * @return The server url of the RS we are connected to
   */
  public String getRsServerUrl()
  {
    return rsServerUrl;
  }

  /**
   * Sets the locally configured flag for the passed ReplicationServerInfo
   * object, analyzing the local configuration.
   * @param replicationServerInfo the Replication server to check and update
   */
  private void updateRSInfoLocallyConfiguredStatus(
    ReplicationServerInfo replicationServerInfo)
  {
    // Determine if the passed ReplicationServerInfo has a URL that is present
    // in the locally configured replication servers
    String rsUrl = replicationServerInfo.getServerURL();
    if (rsUrl == null)
    {
      // The ReplicationServerInfo has been generated from a server with
      // no URL in TopologyMsg (i.e: with replication protocol version < 4):
      // ignore this server as we do not know how to connect to it
      replicationServerInfo.setLocallyConfigured(false);
      return;
    }
    for (String serverUrl : replicationServerUrls)
    {
      if (isSameReplicationServerUrl(serverUrl, rsUrl))
      {
        // This RS is locally configured, mark this
        replicationServerInfo.setLocallyConfigured(true);
        replicationServerInfo.serverURL = serverUrl;
        return;
      }
    }
    replicationServerInfo.setLocallyConfigured(false);
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
    // Get and compare ports of RS1 and RS2
    int separator1 = rs1Url.lastIndexOf(':');
    if (separator1 < 0)
    {
      // Not a RS url: should not happen
      return false;
    }
    int rs1Port = Integer.parseInt(rs1Url.substring(separator1 + 1));

    int separator2 = rs2Url.lastIndexOf(':');
    if (separator2 < 0)
    {
      // Not a RS url: should not happen
      return false;
    }
    int rs2Port = Integer.parseInt(rs2Url.substring(separator2 + 1));

    if (rs1Port != rs2Port)
    {
      return false;
    }

    // Get and compare addresses of RS1 and RS2
    final String rs1 = rs1Url.substring(0, separator1);
    final InetAddress[] rs1Addresses;
    try
    {
      // Normalize local address to null.
      rs1Addresses = isLocalAddress(rs1) ? null : InetAddress.getAllByName(rs1);
    }
    catch (UnknownHostException ex)
    {
      // Unknown RS: should not happen
      return false;
    }

    final String rs2 = rs2Url.substring(0, separator2);
    final InetAddress[] rs2Addresses;
    try
    {
      // Normalize local address to null.
      rs2Addresses = isLocalAddress(rs2) ? null : InetAddress.getAllByName(rs2);
    }
    catch (UnknownHostException ex)
    {
      // Unknown RS: should not happen
      return false;
    }

    // Now compare addresses, if at least one match, this is the same server.
    if (rs1Addresses == null && rs2Addresses == null)
    {
      // Both local addresses.
      return true;
    }
    else if (rs1Addresses == null || rs2Addresses == null)
    {
      // One local address and one non-local.
      return false;
    }
    else
    {
      // Both non-local addresses: check for overlap.
      for (InetAddress inetAddress1 : rs1Addresses)
      {
        for (InetAddress inetAddress2 : rs2Addresses)
        {
          if (inetAddress2.equals(inetAddress1))
          {
            return true;
          }
        }
      }
    }
    return false;
  }

  /**
   * Bag class for keeping info we get from a replication server in order to
   * compute the best one to connect to. This is in fact a wrapper to a
   * ReplServerStartMsg (V3) or a ReplServerStartDSMsg (V4). This can also be
   * updated with a info coming from received topology messages or monitoring
   * messages.
   */
  public static class ReplicationServerInfo
  {
    private short protocolVersion;
    private long generationId;
    private byte groupId = -1;
    private int serverId;
    // Received server URL
    private String serverURL;
    private String baseDn = null;
    private int windowSize;
    private ServerState serverState = null;
    private boolean sslEncryption;
    private int degradedStatusThreshold = -1;
    // Keeps the 1 value if created with a ReplServerStartMsg
    private int weight = 1;
    // Keeps the 0 value if created with a ReplServerStartMsg
    private int connectedDSNumber = 0;
    private List<Integer> connectedDSs = null;
    // Is this RS locally configured ? (the RS is recognized as a usable server)
    private boolean locallyConfigured = true;

    /**
     * Create a new instance of ReplicationServerInfo wrapping the passed
     * message.
     * @param msg Message to wrap.
     * @param server Override serverURL.
     * @return The new instance wrapping the passed message.
     * @throws IllegalArgumentException If the passed message has an unexpected
     *                                  type.
     */
    public static ReplicationServerInfo newInstance(
      ReplicationMsg msg, String server) throws IllegalArgumentException
    {
      ReplicationServerInfo rsInfo = newInstance(msg);
      rsInfo.serverURL = server;
      return rsInfo;
    }

    /**
     * Create a new instance of ReplicationServerInfo wrapping the passed
     * message.
     * @param msg Message to wrap.
     * @return The new instance wrapping the passed message.
     * @throws IllegalArgumentException If the passed message has an unexpected
     *                                  type.
     */
    public static ReplicationServerInfo newInstance(
      ReplicationMsg msg) throws IllegalArgumentException
    {
      if (msg instanceof ReplServerStartMsg)
      {
        // This is a ReplServerStartMsg (RS uses protocol V3 or under)
        ReplServerStartMsg replServerStartMsg = (ReplServerStartMsg) msg;
        return new ReplicationServerInfo(replServerStartMsg);
      } else if (msg instanceof ReplServerStartDSMsg)
      {
        // This is a ReplServerStartDSMsg (RS uses protocol V4 or higher)
        ReplServerStartDSMsg replServerStartDSMsg = (ReplServerStartDSMsg) msg;
        return new ReplicationServerInfo(replServerStartDSMsg);
      }

      // Unsupported message type: should not happen
      throw new IllegalArgumentException("Unexpected PDU type: " +
        msg.getClass().getName() + " :\n" + msg.toString());
    }

    /**
     * Constructs a ReplicationServerInfo object wrapping a
     * {@link ReplServerStartMsg}.
     *
     * @param replServerStartMsg
     *          The {@link ReplServerStartMsg} this object will wrap.
     */
    private ReplicationServerInfo(ReplServerStartMsg replServerStartMsg)
    {
      this.protocolVersion = replServerStartMsg.getVersion();
      this.generationId = replServerStartMsg.getGenerationId();
      this.groupId = replServerStartMsg.getGroupId();
      this.serverId = replServerStartMsg.getServerId();
      this.serverURL = replServerStartMsg.getServerURL();
      this.baseDn = replServerStartMsg.getBaseDn();
      this.windowSize = replServerStartMsg.getWindowSize();
      this.serverState = replServerStartMsg.getServerState();
      this.sslEncryption = replServerStartMsg.getSSLEncryption();
      this.degradedStatusThreshold =
        replServerStartMsg.getDegradedStatusThreshold();
    }

    /**
     * Constructs a ReplicationServerInfo object wrapping a
     * {@link ReplServerStartDSMsg}.
     *
     * @param replServerStartDSMsg
     *          The {@link ReplServerStartDSMsg} this object will wrap.
     */
    private ReplicationServerInfo(ReplServerStartDSMsg replServerStartDSMsg)
    {
      this.protocolVersion = replServerStartDSMsg.getVersion();
      this.generationId = replServerStartDSMsg.getGenerationId();
      this.groupId = replServerStartDSMsg.getGroupId();
      this.serverId = replServerStartDSMsg.getServerId();
      this.serverURL = replServerStartDSMsg.getServerURL();
      this.baseDn = replServerStartDSMsg.getBaseDn();
      this.windowSize = replServerStartDSMsg.getWindowSize();
      this.serverState = replServerStartDSMsg.getServerState();
      this.sslEncryption = replServerStartDSMsg.getSSLEncryption();
      this.degradedStatusThreshold =
        replServerStartDSMsg.getDegradedStatusThreshold();
      this.weight = replServerStartDSMsg.getWeight();
      this.connectedDSNumber = replServerStartDSMsg.getConnectedDSNumber();
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
     * get the group id.
     * @return The group id
     */
    public byte getGroupId()
    {
      return groupId;
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
      return generationId;
    }

    /**
     * Get the server id.
     * @return the serverId
     */
    public int getServerId()
    {
      return serverId;
    }

    /**
     * Get the server URL.
     * @return the serverURL
     */
    public String getServerURL()
    {
      return serverURL;
    }

    /**
     * Get the base dn.
     * @return the baseDn
     */
    public String getBaseDn()
    {
      return baseDn;
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
      return weight;
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
     * Constructs a new replication server info with the passed RSInfo
     * internal values and the passed connected DSs.
     * @param rsInfo The RSinfo to use for the update
     * @param connectedDSs The new connected DSs
     */
    public ReplicationServerInfo(RSInfo rsInfo, List<Integer> connectedDSs)
    {
      this.serverId = rsInfo.getId();
      this.serverURL = rsInfo.getServerUrl();
      this.generationId = rsInfo.getGenerationId();
      this.groupId = rsInfo.getGroupId();
      this.weight = rsInfo.getWeight();
      this.connectedDSs = connectedDSs;
      this.connectedDSNumber = connectedDSs.size();
      this.serverState = new ServerState();
    }

    /**
     * Converts the object to a RSInfo object.
     * @return The RSInfo object matching this object.
     */
    public RSInfo toRSInfo()
    {
      return new RSInfo(serverId, serverURL, generationId, groupId, weight);
    }

    /**
     * Updates replication server info with the passed RSInfo internal values
     * and the passed connected DSs.
     * @param rsInfo The RSinfo to use for the update
     * @param connectedDSs The new connected DSs
     */
    public void update(RSInfo rsInfo, List<Integer> connectedDSs)
    {
      this.generationId = rsInfo.getGenerationId();
      this.groupId = rsInfo.getGroupId();
      this.weight = rsInfo.getWeight();
      this.connectedDSs = connectedDSs;
      this.connectedDSNumber = connectedDSs.size();
    }

    /**
     * Updates replication server info with the passed server state.
     * @param serverState The ServerState to use for the update
     */
    public void update(ServerState serverState)
    {
      if (this.serverState != null)
      {
        this.serverState.update(serverState);
      } else
      {
        this.serverState = serverState;
      }
    }

    /**
     * Get the getConnectedDSs.
     * @return the getConnectedDSs
     */
    public List<Integer> getConnectedDSs()
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
      return "Url:" + this.serverURL + " ServerId:" + this.serverId
          + " GroupId:" + this.groupId;
    }
  }

  private void connect()
  {
    if (this.baseDn.compareToIgnoreCase(
      ServerConstants.DN_EXTERNAL_CHANGELOG_ROOT) == 0)
    {
      connectAsECL();
    } else
    {
      connectAsDataServer();
    }
  }

  /**
   * Contacts all replication servers to get information from them and being
   * able to choose the more suitable.
   * @return the collected information.
   */
  private Map<Integer, ReplicationServerInfo> collectReplicationServersInfo()
  {
    Map<Integer, ReplicationServerInfo> rsInfos =
      new ConcurrentHashMap<Integer, ReplicationServerInfo>();

    for (String serverUrl : replicationServerUrls)
    {
      // Connect to server and get info about it
      ReplicationServerInfo replicationServerInfo =
        performPhaseOneHandshake(serverUrl, false, false);

      // Store server info in list
      if (replicationServerInfo != null)
      {
        rsInfos.put(replicationServerInfo.getServerId(), replicationServerInfo);
      }
    }

    return rsInfos;
  }

  /**
   * Special aspects of connecting as ECL (External Change Log) compared to
   * connecting as data server are :
   * <ul>
   * <li>1 single RS configured</li>
   * <li>so no choice of the preferred RS</li>
   * <li>?? Heartbeat</li>
   * <li>Start handshake is :
   *
   * <pre>
   *    Broker ---> StartECLMsg       ---> RS
   *          <---- ReplServerStartMsg ---
   *           ---> StartSessionECLMsg --> RS
   * </pre>
   *
   * </li>
   * </ul>
   */
  private void connectAsECL()
  {
    // FIXME:ECL List of RS to connect is for now limited to one RS only
    String bestServer = this.replicationServerUrls.iterator().next();

    if (performPhaseOneHandshake(bestServer, true, true) != null)
    {
      performECLPhaseTwoHandshake(bestServer);
    }
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
    May have created a broker with null replication domain for
    unit test purpose.
    */
    if (domain != null)
    {
      /*
      If a first connect or a connection failure occur, we go through here.
      force status machine to NOT_CONNECTED_STATUS so that monitoring can
      see that we are not connected.
      */
      domain.toNotConnectedStatus();
    }

    /*
    Stop any existing heartbeat monitor and changeTime publisher
    from a previous session.
    */
    stopRSHeartBeatMonitoring();
    stopChangeTimeHeartBeatPublishing();
    mustRunBestServerCheckingAlgorithm = 0;

    synchronized (connectPhaseLock)
    {
      /*
       * Connect to each replication server and get their ServerState then find
       * out which one is the best to connect to.
       */
      if (debugEnabled())
        TRACER.debugInfo("serverId: " + serverId
          + " phase 1 : will perform PhaseOneH with each RS in "
          + " order to elect the preferred one");

      // Get info from every available replication servers
      replicationServerInfos = collectReplicationServersInfo();

      ReplicationServerInfo electedRsInfo = null;

      if (replicationServerInfos.size() > 0)
      {
        // At least one server answered, find the best one.
        electedRsInfo = computeBestReplicationServer(true, -1, state,
          replicationServerInfos, serverId, groupId, getGenerationID());

        // Best found, now initialize connection to this one (handshake phase 1)
        if (debugEnabled())
          TRACER.debugInfo("serverId: " + serverId
            + " phase 2 : will perform PhaseOneH with the preferred RS="
            + electedRsInfo);
        electedRsInfo = performPhaseOneHandshake(
          electedRsInfo.getServerURL(), true, false);

        if (electedRsInfo != null)
        {
          /*
          Update replication server info with potentially more up to date
          data (server state for instance may have changed)
          */
          replicationServerInfos
              .put(electedRsInfo.getServerId(), electedRsInfo);

          // Handshake phase 1 exchange went well

          // Compute in which status we are starting the session to tell the RS
          ServerStatus initStatus =
            computeInitialServerStatus(electedRsInfo.getGenerationId(),
            electedRsInfo.getServerState(),
            electedRsInfo.getDegradedStatusThreshold(),
            getGenerationID());

          // Perform session start (handshake phase 2)
          TopologyMsg topologyMsg = performPhaseTwoHandshake(
            electedRsInfo.getServerURL(), initStatus);

          if (topologyMsg != null) // Handshake phase 2 exchange went well
          {
            connectToReplicationServer(electedRsInfo, initStatus, topologyMsg);
          } // Could perform handshake phase 2 with best

        } // Could perform handshake phase 1 with best

      } // Reached some servers

      // connected is set by connectToReplicationServer()
      // and electedRsInfo isn't null then. Check anyway
      if (electedRsInfo != null && connected)
      {
        connectPhaseLock.notify();

        if ((electedRsInfo.getGenerationId() == getGenerationID())
            || (electedRsInfo.getGenerationId() == -1))
        {
          Message message = NOTE_NOW_FOUND_SAME_GENERATION_CHANGELOG
              .get(serverId, rsServerId, baseDn,
                  session.getReadableRemoteAddress(),
                  getGenerationID());
          logError(message);
        } else
        {
          Message message = WARN_NOW_FOUND_BAD_GENERATION_CHANGELOG
              .get(serverId, rsServerId, baseDn,
                  session.getReadableRemoteAddress(),
                  getGenerationID(),
                  electedRsInfo.getGenerationId());
          logError(message);
        }
      } else
      {
        /*
         * This server could not find any replicationServer. It's going to start
         * in degraded mode. Log a message.
         */
        connected = false;
        replicationServer = NO_CONNECTED_SERVER;

        if (!connectionError)
        {
          connectionError = true;
          connectPhaseLock.notify();

          if (replicationServerInfos.size() > 0)
          {
            Message message = WARN_COULD_NOT_FIND_CHANGELOG.get(
                serverId,
                baseDn,
                collectionToString(replicationServerInfos.keySet(),
                    ", "));
            logError(message);
          }
          else
          {
            Message message = WARN_NO_AVAILABLE_CHANGELOGS.get(
                serverId, baseDn);
            logError(message);
          }
        }
      }
    }
  }

  /**
   * Connects to a replication server.
   *
   * @param rsInfo
   *          the Replication Server to connect to
   * @param initStatus
   *          The status to enter the state machine with
   * @param topologyMsg
   *          the message containing the topology information
   */
  private void connectToReplicationServer(ReplicationServerInfo rsInfo,
      ServerStatus initStatus, TopologyMsg topologyMsg)
  {
    try
    {
      replicationServer = session.getReadableRemoteAddress();
      maxSendWindow = rsInfo.getWindowSize();
      rsGroupId = rsInfo.getGroupId();
      rsServerId = rsInfo.getServerId();
      rsServerUrl = rsInfo.getServerURL();

      receiveTopo(topologyMsg);

      /*
      Log a message to let the administrator know that the failure
      was resolved.
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
        final int MAX_PERMITS = (Integer.MAX_VALUE >>> 2);
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
      rcvWindow = maxRcvWindow;
      connected = true;

      /*
      May have created a broker with null replication domain for
      unit test purpose.
      */
      if (domain != null)
      {
        domain.sessionInitiated(initStatus, rsInfo.getServerState(), rsInfo
            .getGenerationId(), session);
      }

      if (getRsGroupId() != groupId)
      {
        /*
        Connected to replication server with wrong group id:
        warn user and start heartbeat monitor to recover when a server
        with the right group id shows up.
        */
        Message message =
            WARN_CONNECTED_TO_SERVER_WITH_WRONG_GROUP_ID.get(Byte
                .toString(groupId), Integer.toString(rsServerId), rsInfo
                .getServerURL(), Byte.toString(getRsGroupId()), baseDn, Integer
                .toString(serverId));
        logError(message);
      }
      startRSHeartBeatMonitoring();
      if (rsInfo.getProtocolVersion() >=
        ProtocolVersion.REPLICATION_PROTOCOL_V3)
      {
        startChangeTimeHeartBeatPublishing();
      }
    }
    catch (Exception e)
    {
      Message message =
          ERR_COMPUTING_FAKE_OPS.get(baseDn, rsInfo.getServerURL(), e
              .getLocalizedMessage()
              + stackTraceToSingleLineString(e));
      logError(message);
    }
    finally
    {
      if (!connected)
      {
        setSession(null);
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
  public ServerStatus computeInitialServerStatus(long rsGenId,
    ServerState rsState, int degradedStatusThreshold, long dsGenId)
  {
    if (rsGenId == -1)
    {
      // RS has no generation id
      return ServerStatus.NORMAL_STATUS;
    } else
    {
      if (rsGenId == dsGenId)
      {
        /*
        DS and RS have same generation id

        Determine if we are late or not to replay changes. RS uses a
        threshold value for pending changes to be replayed by a DS to
        determine if the DS is in normal status or in degraded status.
        Let's compare the local and remote server state using  this threshold
        value to determine if we are late or not
        */

        ServerStatus initStatus;
        int nChanges = ServerState.diffChanges(rsState, state);

        if (debugEnabled())
        {
          TRACER.debugInfo("RB for dn " + baseDn
            + " and with server id " + Integer.toString(serverId)
            + " computed " + Integer.toString(nChanges) + " changes late.");
        }

        /*
        Check status to know if it is relevant to change the status. Do not
        take RSD lock to test. If we attempt to change the status whereas
        we are in a status that do not allows that, this will be noticed by
        the changeStatusFromStatusAnalyzer method. This allows to take the
        lock roughly only when needed versus every sleep time timeout.
        */
        if (degradedStatusThreshold > 0)
        {
          if (nChanges >= degradedStatusThreshold)
          {
            initStatus = ServerStatus.DEGRADED_STATUS;
          } else
          {
            initStatus = ServerStatus.NORMAL_STATUS;
          }
        } else
        {
          /*
          0 threshold value means no degrading system used (no threshold):
          force normal status
          */
          initStatus = ServerStatus.NORMAL_STATUS;
        }

        return initStatus;
      } else
      {
        // DS and RS do not have same generation id
        return ServerStatus.BAD_GEN_ID_STATUS;
      }
    }
  }



  /**
   * Connect to the provided server performing the first phase handshake (start
   * messages exchange) and return the reply message from the replication
   * server, wrapped in a ReplicationServerInfo object.
   *
   * @param server
   *          Server to connect to.
   * @param keepConnection
   *          Do we keep session opened or not after handshake. Use true if want
   *          to perform handshake phase 2 with the same session and keep the
   *          session to create as the current one.
   * @param isECL
   *          Indicates whether or not the an ECL handshake is to be performed.
   * @return The answer from the server . Null if could not get an answer.
   */
  private ReplicationServerInfo performPhaseOneHandshake(
      String server, boolean keepConnection, boolean isECL)
  {
    int separator = server.lastIndexOf(':');
    String port = server.substring(separator + 1);
    String hostname = server.substring(0, separator);

    Session localSession = null;
    Socket socket = null;
    boolean hasConnected = false;
    Message errorMessage = null;

    try
    {
      /*
       * Open a socket connection to the next candidate.
       */
      int intPort = Integer.parseInt(port);
      InetSocketAddress serverAddr = new InetSocketAddress(
          InetAddress.getByName(hostname), intPort);
      socket = new Socket();
      socket.setReceiveBufferSize(1000000);
      socket.setTcpNoDelay(true);
      int timeoutMS = MultimasterReplication.getConnectionTimeoutMS();
      socket.connect(serverAddr, timeoutMS);
      localSession = replSessionSecurity.createClientSession(socket, timeoutMS);
      boolean isSslEncryption = replSessionSecurity
          .isSslEncryption(server);

      // Send our ServerStartMsg.
      String url = socket.getLocalAddress().getHostName() + ":"
          + socket.getLocalPort();
      StartMsg serverStartMsg;
      if (!isECL)
      {
        serverStartMsg = new ServerStartMsg(serverId, url, baseDn,
            maxRcvWindow, heartbeatInterval, state,
            this.getGenerationID(), isSslEncryption, groupId);
      }
      else
      {
        serverStartMsg = new ServerStartECLMsg(url, 0, 0, 0, 0,
            maxRcvWindow, heartbeatInterval, state,
            this.getGenerationID(), isSslEncryption, groupId);
      }
      localSession.publish(serverStartMsg);

      // Read the ReplServerStartMsg or ReplServerStartDSMsg that should
      // come back.
      ReplicationMsg msg = localSession.receive();
      if (debugEnabled())
      {
        TRACER.debugInfo("In RB for " + baseDn + "\nRB HANDSHAKE SENT:\n"
          + serverStartMsg.toString() + "\nAND RECEIVED:\n"
          + msg.toString());
      }

      // Wrap received message in a server info object
      ReplicationServerInfo replServerInfo = ReplicationServerInfo
          .newInstance(msg, server);

      // Sanity check
      String repDn = replServerInfo.getBaseDn();
      if (!(this.baseDn.equals(repDn)))
      {
        errorMessage = ERR_DS_DN_DOES_NOT_MATCH.get(repDn,
            this.baseDn);
        return null;
      }

      /*
       * We have sent our own protocol version to the replication server. The
       * replication server will use the same one (or an older one if it is an
       * old replication server).
       */
      final short localProtocolVersion = getCompatibleVersion(replServerInfo
          .getProtocolVersion());
      if (keepConnection)
      {
        protocolVersion = localProtocolVersion;
      }
      localSession.setProtocolVersion(localProtocolVersion);

      if (!isSslEncryption)
      {
        localSession.stopEncryption();
      }

      hasConnected = true;

      // If this connection as the one to use for sending and receiving
      // updates, store it.
      if (keepConnection)
      {
        setSession(localSession);
      }

      return replServerInfo;
    }
    catch (ConnectException e)
    {
      errorMessage = WARN_NO_CHANGELOG_SERVER_LISTENING.get(serverId,
          server, baseDn);
      return null;
    }
    catch (SocketTimeoutException e)
    {
      errorMessage = WARN_TIMEOUT_CONNECTING_TO_RS.get(serverId,
          server, baseDn);
      return null;
    }
    catch (Exception e)
    {
      errorMessage = WARN_EXCEPTION_STARTING_SESSION_PHASE.get(serverId,
          server, baseDn, stackTraceToSingleLineString(e));
      return null;
    }
    finally
    {
      if (!hasConnected || !keepConnection)
      {
        if (localSession != null)
        {
          localSession.close();
        }

        if (socket != null)
        {
          try
          {
            socket.close();
          }
          catch (IOException e)
          {
            // Ignore.
          }
        }
      }

      if (!hasConnected && errorMessage != null)
      {
        // There was no server waiting on this host:port Log a notice and try
        // the next replicationServer in the list
        if (!connectionError)
        {
          if (keepConnection) // Log error message only for final connection
          {
            // the error message is only logged once to avoid overflowing
            // the error log
            logError(errorMessage);
          }

          if (debugEnabled())
          {
            TRACER.debugInfo(errorMessage.toString());
          }
        }
      }
    }
  }



  /**
   * Performs the second phase handshake for External Change Log (send
   * StartSessionMsg and receive TopologyMsg messages exchange) and return the
   * reply message from the replication server.
   *
   * @param server Server we are connecting with.
   * @return The ReplServerStartMsg the server replied. Null if could not
   *         get an answer.
   */
  private TopologyMsg performECLPhaseTwoHandshake(String server)
  {
    TopologyMsg topologyMsg = null;

    try
    {
      // Send our Start Session
      StartECLSessionMsg startECLSessionMsg = new StartECLSessionMsg();
      startECLSessionMsg.setOperationId("-1");
      session.publish(startECLSessionMsg);

      /* FIXME:ECL In the handshake phase two, should RS send back a topo msg ?
       * Read the TopologyMsg that should come back.
      topologyMsg = (TopologyMsg) session.receive();
       */
      if (debugEnabled())
      {
        TRACER.debugInfo("In RB for " + baseDn
          + "\nRB HANDSHAKE SENT:\n" + startECLSessionMsg.toString());
      }

      // Alright set the timeout to the desired value
      session.setSoTimeout(timeout);
      connected = true;

    } catch (Exception e)
    {
      Message message = WARN_EXCEPTION_STARTING_SESSION_PHASE.get(serverId,
          server, baseDn, stackTraceToSingleLineString(e));
      logError(message);

      setSession(null);

      // Be sure to return null.
      topologyMsg = null;
    }
    return topologyMsg;
  }

  /**
   * Performs the second phase handshake (send StartSessionMsg and receive
   * TopologyMsg messages exchange) and return the reply message from the
   * replication server.
   *
   * @param server Server we are connecting with.
   * @param initStatus The status we are starting with
   * @return The ReplServerStartMsg the server replied. Null if could not
   *         get an answer.
   */
  private TopologyMsg performPhaseTwoHandshake(String server,
    ServerStatus initStatus)
  {
    TopologyMsg topologyMsg;

    try
    {
      /*
       * Send our StartSessionMsg.
       */
      StartSessionMsg startSessionMsg;
      // May have created a broker with null replication domain for
      // unit test purpose.
      if (domain != null)
      {
        startSessionMsg =
          new StartSessionMsg(
          initStatus,
          domain.getRefUrls(),
          domain.isAssured(),
          domain.getAssuredMode(),
          domain.getAssuredSdLevel());
        startSessionMsg.setEclIncludes(
            domain.getEclIncludes(domain.getServerId()),
            domain.getEclIncludesForDeletes(domain.getServerId()));
      }
      else
      {
        startSessionMsg =
          new StartSessionMsg(initStatus, new ArrayList<String>());
      }
      session.publish(startSessionMsg);

      /*
       * Read the TopologyMsg that should come back.
       */
      topologyMsg = (TopologyMsg) session.receive();

      if (debugEnabled())
      {
        TRACER.debugInfo("In RB for " + baseDn
          + "\nRB HANDSHAKE SENT:\n" + startSessionMsg.toString()
          + "\nAND RECEIVED:\n" + topologyMsg.toString());
      }

      // Alright set the timeout to the desired value
      session.setSoTimeout(timeout);

    } catch (Exception e)
    {
      Message message = WARN_EXCEPTION_STARTING_SESSION_PHASE.get(serverId,
          server, baseDn, stackTraceToSingleLineString(e));
      logError(message);

      setSession(null);

      // Be sure to return null.
      topologyMsg = null;
    }
    return topologyMsg;
  }

  /**
   * Returns the replication server that best fits our need so that we can
   * connect to it or determine if we must disconnect from current one to
   * re-connect to best server.
   *
   * Note: this method is static for test purpose (access from unit tests)
   *
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
  public static ReplicationServerInfo computeBestReplicationServer(
      boolean firstConnection, int rsServerId, ServerState myState,
      Map<Integer, ReplicationServerInfo> rsInfos, int localServerId,
      byte groupId, long generationId)
  {

    // Shortcut, if only one server, this is the best
    if (rsInfos.size() == 1)
    {
      return rsInfos.values().iterator().next();
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
    Map<Integer, ReplicationServerInfo> bestServers = rsInfos;
    /*
    The list of best replication servers is filtered with each criteria. At
    each criteria, the list is replaced with the filtered one if there
    are some servers from the filtering, otherwise, the list is left as is
    and the new filtering for the next criteria is applied and so on.

    Use only servers locally configured: those are servers declared in
    the local configuration. When the current method is called, for
    sure, at least one server from the list is locally configured
    */
    bestServers =
        keepBest(filterServersLocallyConfigured(bestServers), bestServers);
    // Some servers with same group id ?
    bestServers =
        keepBest(filterServersWithSameGroupId(bestServers, groupId),
            bestServers);
    // Some servers with same generation id ?
    Map<Integer, ReplicationServerInfo> sameGenerationId =
        filterServersWithSameGenerationId(bestServers, generationId);
    if (sameGenerationId.size() > 0)
    {
      // If some servers with the right generation id this is useful to
      // run the local DS change criteria
      bestServers =
          keepBest(filterServersWithAllLocalDSChanges(sameGenerationId,
              myState, localServerId), sameGenerationId);
    }
    // Some servers in the local VM or local host?
    bestServers = keepBest(filterServersOnSameHost(bestServers), bestServers);

    /**
     * Now apply the choice base on the weight to the best servers list
     */
    if (bestServers.size() > 1)
    {
      if (firstConnection)
      {
        // We are not connected to a server yet
        return computeBestServerForWeight(bestServers, -1, -1);
      } else
      {
        /*
        We are already connected to a RS: compute the best RS as far as the
        weights is concerned. If this is another one, some DS must
        disconnect.
        */
        return computeBestServerForWeight(bestServers, rsServerId,
          localServerId);
      }
    } else
    {
      return bestServers.values().iterator().next();
    }
  }

  /**
   * If the filtered Map is not empty then it is returned, else return the
   * original unfiltered Map.
   *
   * @return the best fit Map between the filtered Map and the original
   * unfiltered Map.
   */
  private static <K, V> Map<K, V> keepBest(Map<K, V> filteredMap,
      Map<K, V> unfilteredMap)
  {
    if (!filteredMap.isEmpty())
    {
      return filteredMap;
    }
    return unfilteredMap;
  }

  /**
   * Creates a new list that contains only replication servers that are locally
   * configured.
   * @param bestServers The list of replication servers to filter
   * @return The sub list of replication servers locally configured
   */
  private static Map<Integer, ReplicationServerInfo>
    filterServersLocallyConfigured(Map<Integer,
    ReplicationServerInfo> bestServers)
  {
    Map<Integer, ReplicationServerInfo> result =
      new HashMap<Integer, ReplicationServerInfo>();

    for (Integer rsId : bestServers.keySet())
    {
      ReplicationServerInfo replicationServerInfo = bestServers.get(rsId);
      if (replicationServerInfo.isLocallyConfigured())
      {
        result.put(rsId, replicationServerInfo);
      }
    }
    return result;
  }

  /**
   * Creates a new list that contains only replication servers that have the
   * passed group id, from a passed replication server list.
   * @param bestServers The list of replication servers to filter
   * @param groupId The group id that must match
   * @return The sub list of replication servers matching the requested group id
   * (which may be empty)
   */
  private static Map<Integer, ReplicationServerInfo>
    filterServersWithSameGroupId(Map<Integer,
    ReplicationServerInfo> bestServers, byte groupId)
  {
    Map<Integer, ReplicationServerInfo> result =
      new HashMap<Integer, ReplicationServerInfo>();

    for (Integer rsId : bestServers.keySet())
    {
      ReplicationServerInfo replicationServerInfo = bestServers.get(rsId);
      if (replicationServerInfo.getGroupId() == groupId)
      {
        result.put(rsId, replicationServerInfo);
      }
    }
    return result;
  }

  /**
   * Creates a new list that contains only replication servers that have the
   * provided generation id, from a provided replication server list.
   * When the selected replication servers have no change (empty serverState)
   * then the 'empty'(generationId==-1) replication servers are also included
   * in the result list.
   *
   * @param bestServers  The list of replication servers to filter
   * @param generationId The generation id that must match
   * @return The sub list of replication servers matching the requested
   * generation id (which may be empty)
   */
  private static Map<Integer, ReplicationServerInfo>
    filterServersWithSameGenerationId(Map<Integer,
    ReplicationServerInfo> bestServers, long generationId)
  {
    Map<Integer, ReplicationServerInfo> result =
      new HashMap<Integer, ReplicationServerInfo>();
    boolean emptyState = true;

    for (Integer rsId : bestServers.keySet())
    {
      ReplicationServerInfo replicationServerInfo = bestServers.get(rsId);
      if (replicationServerInfo.getGenerationId() == generationId)
      {
        result.put(rsId, replicationServerInfo);
        if (!replicationServerInfo.serverState.isEmpty())
          emptyState = false;
      }
    }

    if (emptyState)
    {
      // If the RS with a generationId have all an empty state,
      // then the 'empty'(genId=-1) RSes are also candidate
      for (Integer rsId : bestServers.keySet())
      {
        ReplicationServerInfo replicationServerInfo = bestServers.get(rsId);
        if (replicationServerInfo.getGenerationId() == -1)
        {
          result.put(rsId, replicationServerInfo);
        }
      }
    }
    return result;
  }

  /**
   * Creates a new list that contains only replication servers that have the
   * latest changes from the passed DS, from a passed replication server list.
   * @param bestServers The list of replication servers to filter
   * @param localState The state of the local DS
   * @param localServerId The server id to consider for the changes
   * @return The sub list of replication servers that have the latest changes
   * from the passed DS (which may be empty)
   */
  private static Map<Integer, ReplicationServerInfo>
    filterServersWithAllLocalDSChanges(Map<Integer,
    ReplicationServerInfo> bestServers, ServerState localState,
    int localServerId)
  {
    Map<Integer, ReplicationServerInfo> upToDateServers =
      new HashMap<Integer, ReplicationServerInfo>();
    Map<Integer, ReplicationServerInfo> moreUpToDateServers =
      new HashMap<Integer, ReplicationServerInfo>();

    // Extract the change number of the latest change generated by the local
    // server
    ChangeNumber myChangeNumber = localState.getMaxChangeNumber(localServerId);
    if (myChangeNumber == null)
    {
      myChangeNumber = new ChangeNumber(0, 0, localServerId);
    }

    /**
     * Find replication servers who are up to date (or more up to date than us,
     * if for instance we failed and restarted, having sent some changes to the
     * RS but without having time to store our own state) regarding our own
     * server id. If some servers more up to date, prefer this list but take
     * only the latest change number.
     */
    ChangeNumber latestRsChangeNumber = null;
    for (Integer rsId : bestServers.keySet())
    {
      ReplicationServerInfo replicationServerInfo = bestServers.get(rsId);
      ServerState rsState = replicationServerInfo.getServerState();
      ChangeNumber rsChangeNumber = rsState.getMaxChangeNumber(localServerId);
      if (rsChangeNumber == null)
      {
        rsChangeNumber = new ChangeNumber(0, 0, localServerId);
      }

      // Has this replication server the latest local change ?
      if (myChangeNumber.olderOrEqual(rsChangeNumber))
      {
        if (myChangeNumber.equals(rsChangeNumber))
        {
          // This replication server has exactly the latest change from the
          // local server
          upToDateServers.put(rsId, replicationServerInfo);
        } else
        {
          // This replication server is even more up to date than the local
          // server
          if (latestRsChangeNumber == null)
          {
            // Initialize the latest change number
            latestRsChangeNumber = rsChangeNumber;
          }
          if (rsChangeNumber.newerOrEquals(latestRsChangeNumber))
          {
            if (rsChangeNumber.equals(latestRsChangeNumber))
            {
              moreUpToDateServers.put(rsId, replicationServerInfo);
            } else
            {
              // This RS is even more up to date, clear the list and store this
              // new RS
              moreUpToDateServers.clear();
              moreUpToDateServers.put(rsId, replicationServerInfo);
              latestRsChangeNumber = rsChangeNumber;
            }
          }
        }
      }
    }
    if (moreUpToDateServers.size() > 0)
    {
      // Prefer servers more up to date than local server
      return moreUpToDateServers;
    } else
    {
      return upToDateServers;
    }
  }



  /**
   * Creates a new list that contains only replication servers that are on the
   * same host as the local DS, from a passed replication server list. This
   * method will gives priority to any replication server which is in the same
   * VM as this DS.
   *
   * @param bestServers
   *          The list of replication servers to filter
   * @return The sub list of replication servers being on the same host as the
   *         local DS (which may be empty)
   */
  private static Map<Integer, ReplicationServerInfo> filterServersOnSameHost(
      Map<Integer, ReplicationServerInfo> bestServers)
  {
    /*
     * Initially look for all servers on the same host. If we find one in the
     * same VM, then narrow the search.
     */
    boolean filterServersInSameVM = false;
    Map<Integer, ReplicationServerInfo> result =
        new HashMap<Integer, ReplicationServerInfo>();
    for (Integer rsId : bestServers.keySet())
    {
      ReplicationServerInfo replicationServerInfo = bestServers.get(rsId);
      String server = replicationServerInfo.getServerURL();
      int separator = server.lastIndexOf(':');
      if (separator > 0)
      {
        String hostname = server.substring(0, separator);
        if (isLocalAddress(hostname))
        {
          int port = Integer.parseInt(server.substring(separator + 1));
          if (isLocalReplicationServerPort(port))
          {
            // An RS in the same VM will always have priority.
            if (!filterServersInSameVM)
            {
              // Narrow the search to only include servers in this VM.
              result.clear();
              filterServersInSameVM = true;
            }
            result.put(rsId, replicationServerInfo);
          }
          else if (!filterServersInSameVM)
          {
            result.put(rsId, replicationServerInfo);
          }
          else
          {
            // Skip: we have found some RSs in the same VM, but this RS is not.
          }
        }
      }
    }
    return result;
  }

  /**
   * Computes the best replication server the local server should be connected
   * to so that the load is correctly spread across the topology, following the
   * weights guidance.
   * Warning: This method is expected to be called with at least 2 servers in
   * bestServers
   * Note: this method is static for test purpose (access from unit tests)
   * @param bestServers The list of replication servers to consider
   * @param currentRsServerId The replication server the local server is
   *        currently connected to. -1 if the local server is not yet connected
   *        to any replication server.
   * @param localServerId The server id of the local server. This is not used
   *        when it is not connected to a replication server
   *        (currentRsServerId = -1)
   * @return The replication server the local server should be connected to
   * as far as the weight is concerned. This may be the currently used one if
   * the weight is correctly spread. If the returned value is null, the best
   * replication server is undetermined but the local server must disconnect
   * (so the best replication server is another one than the current one).
   */
  public static ReplicationServerInfo computeBestServerForWeight(
    Map<Integer, ReplicationServerInfo> bestServers, int currentRsServerId,
    int localServerId)
  {
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
    for (ReplicationServerInfo replicationServerInfo : bestServers.values())
    {
      sumOfWeights += replicationServerInfo.getWeight();
      sumOfConnectedDSs += replicationServerInfo.getConnectedDSNumber();
    }
    // Distance (difference) of the current loads to the load goals of each RS:
    // key:server id, value: distance
    Map<Integer, BigDecimal> loadDistances = new HashMap<Integer, BigDecimal>();
    // Precision for the operations (number of digits after the dot)
    MathContext mathContext = new MathContext(32, RoundingMode.HALF_UP);
    for (Integer rsId : bestServers.keySet())
    {
      ReplicationServerInfo replicationServerInfo = bestServers.get(rsId);

      int rsWeight = replicationServerInfo.getWeight();
      //  load goal = rs weight / sum of weights
      BigDecimal loadGoalBd = BigDecimal.valueOf(rsWeight).divide(
          BigDecimal.valueOf(sumOfWeights), mathContext);
      BigDecimal currentLoadBd = BigDecimal.ZERO;
      if (sumOfConnectedDSs != 0)
      {
        // current load = number of connected DSs / total number of DSs
        int connectedDSs = replicationServerInfo.getConnectedDSNumber();
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
      // The local server is not connected yet

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
      for (Integer rsId : bestServers.keySet())
      {
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
        int weight = bestServers.get(rsId).getWeight();
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
      return bestServers.get(bestRsId);
    } else
    {
      // The local server is currently connected to a RS, let's see if it must
      // disconnect or not, taking the weights into account.

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
            Now compare both values: we must no disconnect the DS if this
            is for going in a situation where the load distance of the other
            RSs is the opposite of the future load distance of the local RS
            or we would evaluate that we should disconnect just after being
            arrived on the new RS. But we should disconnect if we reach the
            perfect balance (both values are 0).
            */
            MathContext roundMc =
              new MathContext(6, RoundingMode.DOWN);
            BigDecimal potentialCurrentRsNewLoadDistanceBdRounded =
              potentialCurrentRsNewLoadDistanceBd.round(roundMc);
            BigDecimal potentialNewSumOfLoadDistancesOfOtherRSsBdRounded =
              potentialNewSumOfLoadDistancesOfOtherRSsBd.round(roundMc);

            if ((potentialCurrentRsNewLoadDistanceBdRounded.compareTo(
              BigDecimal.ZERO) != 0)
              && (potentialCurrentRsNewLoadDistanceBdRounded.equals(
              potentialNewSumOfLoadDistancesOfOtherRSsBdRounded.negate())))
            {
              // Avoid the yoyo effect, and keep the local DS connected to its
              // current RS
              return bestServers.get(currentRsServerId);
            }
          }

          // Prepare a sorted list (from lowest to highest) or DS server ids
          // connected to the current RS
          ReplicationServerInfo currentRsInfo =
            bestServers.get(currentRsServerId);
          List<Integer> serversConnectedToCurrentRS =
            currentRsInfo.getConnectedDSs();
          List<Integer> sortedServers = new ArrayList<Integer>(
            serversConnectedToCurrentRS);
          Collections.sort(sortedServers);

          // Go through the list of DSs to disconnect and see if the local
          // server is part of them.
          int index = 0;
          while (overloadingDSsNumber > 0)
          {
            int severToDisconnectId = sortedServers.get(index);
            if (severToDisconnectId == localServerId)
            {
              // The local server is part of the DSs to disconnect
              return null;
            }
            overloadingDSsNumber--;
            index++;
          }

          // The local server is not part of the servers to disconnect from the
          // current RS.
          return bestServers.get(currentRsServerId);
        } else
        {
          // The average distance of the other RSs does not show a lack of DSs:
          // no need to disconnect any DS from the current RS.
          return bestServers.get(currentRsServerId);
        }
      } else
      {
        // The RS load goal is reached or there are not enough DSs connected to
        // it to reach it: do not disconnect from this RS and return rsInfo for
        // this RS
        return bestServers.get(currentRsServerId);
      }
    }
  }

  /**
   * Start the heartbeat monitor thread.
   */
  private void startRSHeartBeatMonitoring()
  {
    // Start a heartbeat monitor thread.
    if (heartbeatInterval > 0)
    {
      heartbeatMonitor = new HeartbeatMonitor(getServerId(),
          getRsServerId(), baseDn, session, heartbeatInterval);
      heartbeatMonitor.start();
    }
  }

  /**
   * Stop the heartbeat monitor thread.
   */
  synchronized void stopRSHeartBeatMonitoring()
  {
    if (heartbeatMonitor != null)
    {
      heartbeatMonitor.shutdown();
      heartbeatMonitor = null;
    }
  }

  /**
   * restart the ReplicationBroker.
   * @param infiniteTry the socket which failed
   */
  public void reStart(boolean infiniteTry)
  {
    reStart(session, infiniteTry);
  }

  /**
   * Restart the ReplicationServer broker after a failure.
   *
   * @param failingSession the socket which failed
   * @param infiniteTry the socket which failed
   */
  public void reStart(Session failingSession, boolean infiniteTry)
  {
    if (failingSession != null)
    {
      failingSession.close();
      numLostConnections++;
    }

    if (failingSession == session)
    {
      connected = false;
      rsGroupId = -1;
      rsServerId = -1;
      rsServerUrl = null;
      setSession(null);
    }

    while (true)
    {
      // Synchronize inside the loop in order to allow shutdown.
      synchronized (startStopLock)
      {
        if (connected || shutdown)
        {
          break;
        }

        try
        {
          connect();
        }
        catch (Exception e)
        {
          MessageBuilder mb = new MessageBuilder();
          mb.append(NOTE_EXCEPTION_RESTARTING_SESSION.get(baseDn,
              e.getLocalizedMessage()));
          mb.append(stackTraceToSingleLineString(e));
          logError(mb.toMessage());
        }

        if (connected || !infiniteTry)
        {
          break;
        }

      }
      try
      {
          Thread.sleep(500);
      }
      catch (InterruptedException e)
      {
        // ignore
      }
    }

    if (debugEnabled())
    {
      TRACER.debugInfo(this + " end restart : connected=" + connected
        + " with RSid=" + this.getRsServerId() + " genid=" + this.generationID);
    }
  }

  /**
   * Publish a message to the other servers.
   * @param msg the message to publish
   */
  public void publish(ReplicationMsg msg)
  {
    _publish(msg, false, true);
  }

  /**
   * Publish a message to the other servers.
   * @param msg            The message to publish.
   * @param retryOnFailure Whether reconnect should automatically be done.
   * @return               Whether publish succeeded.
   */
  public boolean publish(ReplicationMsg msg, boolean retryOnFailure)
  {
    return _publish(msg, false, retryOnFailure);
  }

  /**
   * Publish a recovery message to the other servers.
   * @param msg the message to publish
   */
  public void publishRecovery(ReplicationMsg msg)
  {
    _publish(msg, true, true);
  }

  /**
   * Publish a message to the other servers.
   * @param msg the message to publish
   * @param recoveryMsg the message is a recovery Message
   * @param retryOnFailure whether retry should be done on failure
   * @return whether the message was successfully sent.
   */
  boolean _publish(ReplicationMsg msg, boolean recoveryMsg,
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

        if (debugEnabled())
        {
          TRACER.debugInfo("ReplicationBroker.publish() Publishing a "
            + "message is not possible due to existing connection error.");
        }

        return false;
      }

      try
      {
        boolean credit;
        Session current_session;
        Semaphore currentWindowSemaphore;

        /*
        save the session at the time when we acquire the
        sendwindow credit so that we can make sure later
        that the session did not change in between.
        This is necessary to make sure that we don't publish a message
        on a session with a credit that was acquired from a previous
        session.
        */
        synchronized (connectPhaseLock)
        {
          current_session = session;
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

        if (msg instanceof UpdateMsg)
        {
          /*
          Acquiring the window credit must be done outside of the
          connectPhaseLock because it can be blocking and we don't
          want to hold off reconnection in case the connection dropped.
          */
          credit =
            currentWindowSemaphore.tryAcquire(500, TimeUnit.MILLISECONDS);
        } else
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

            if ((session != null) &&
                (session == current_session))
            {
              session.publish(msg);
              done = true;
            }
          }
        }
        if ((!credit) && (currentWindowSemaphore.availablePermits() == 0))
        {
          synchronized (connectPhaseLock)
          {
            /*
            the window is still closed.
            Send a WindowProbeMsg message to wake up the receiver in case the
            window update message was lost somehow...
            then loop to check again if connection was closed.
            */
            if (session != null) {
              session.publish(new WindowProbeMsg());
            }
          }
        }
      } catch (IOException e)
      {
        if (!retryOnFailure)
          return false;

        // The receive threads should handle reconnection or
        // mark this broker in error. Just retry.
        synchronized (connectPhaseLock)
        {
          try
          {
            connectPhaseLock.wait(100);
          } catch (InterruptedException e1)
          {
            // ignore
            if (debugEnabled())
            {
              TRACER.debugInfo("ReplicationBroker.publish() "
                + "Interrupted exception raised : " + e.getLocalizedMessage());
            }
          }
        }
      } catch (InterruptedException e)
      {
        // just loop.
        if (debugEnabled())
        {
          TRACER.debugInfo("ReplicationBroker.publish() "
            + "Interrupted exception raised." + e.getLocalizedMessage());
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
  public ReplicationMsg receive(boolean reconnectToTheBestRS,
      boolean reconnectOnFailure, boolean returnOnTopoChange)
    throws SocketTimeoutException
  {
    while (!shutdown)
    {
      if (reconnectOnFailure && !connected)
      {
        // infinite try to reconnect
        reStart(null, true);
      }

      // Save session information for later in case we need it for log messages
      // after the session has been closed and/or failed.
      final Session savedSession = session;
      if (savedSession == null)
      {
        // Must be shutting down.
        break;
      }

      final int replicationServerID = rsServerId;
      try
      {
        ReplicationMsg msg = savedSession.receive();
        if (msg instanceof UpdateMsg)
        {
          synchronized (this)
          {
            rcvWindow--;
          }
        }
        if (msg instanceof WindowMsg)
        {
          WindowMsg windowMsg = (WindowMsg) msg;
          sendWindow.release(windowMsg.getNumAck());
        }
        else if (msg instanceof TopologyMsg)
        {
          TopologyMsg topoMsg = (TopologyMsg) msg;
          receiveTopo(topoMsg);
          if (reconnectToTheBestRS)
          {
            // Reset wait time before next computation of best server
            mustRunBestServerCheckingAlgorithm = 0;
          }

          // Caller wants to check what's changed
          if (returnOnTopoChange)
            return msg;

        }
        else if (msg instanceof StopMsg)
        {
          /*
           * RS performs a proper disconnection
           */
          Message message = WARN_REPLICATION_SERVER_PROPERLY_DISCONNECTED
              .get(replicationServerID,
                  savedSession.getReadableRemoteAddress(),
              serverId, baseDn);
          logError(message);

          // Try to find a suitable RS
          this.reStart(savedSession, true);
        }
        else if (msg instanceof MonitorMsg)
        {
          // This is the response to a MonitorRequest that was sent earlier or
          // the regular message of the monitoring publisher of the RS.
          MonitorMsg monitorMsg = (MonitorMsg) msg;

          // Extract and store replicas ServerStates
          replicaStates = new HashMap<Integer, ServerState>();
          for (int srvId : toIterable(monitorMsg.ldapIterator()))
          {
            replicaStates.put(srvId, monitorMsg.getLDAPServerState(srvId));
          }

          // Notify the sender that the response was received.
          synchronized (monitorResponse)
          {
            monitorResponse.set(true);
            monitorResponse.notify();
          }

          // Update the replication servers ServerStates with new received info
          for (int srvId : toIterable(monitorMsg.rsIterator()))
          {
            ReplicationServerInfo rsInfo = replicationServerInfos.get(srvId);
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
              ReplicationServerInfo bestServerInfo =
                computeBestReplicationServer(false, rsServerId, state,
                replicationServerInfos, serverId, groupId,
                generationID);

              if ((rsServerId != -1) && ((bestServerInfo == null) ||
                (bestServerInfo.getServerId() != rsServerId)))
              {
                // The best replication server is no more the one we are
                // currently using. Disconnect properly then reconnect.
                Message message;
                if (bestServerInfo == null)
                {
                  message = NOTE_LOAD_BALANCE_REPLICATION_SERVER.get(
                      serverId, replicationServerID,
                      savedSession.getReadableRemoteAddress(),
                      baseDn);
                }
                else
                {
                  message = NOTE_NEW_BEST_REPLICATION_SERVER.get(
                      serverId, replicationServerID,
                      savedSession.getReadableRemoteAddress(),
                      bestServerInfo.getServerId(), baseDn);
                }
                logError(message);
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
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        if (!shutdown)
        {
          final Session tmpSession = session;
          if (tmpSession == null || !tmpSession.closeInitiated())
          {
            /*
             * We did not initiate the close on our side, log an error message.
             */
            Message message = WARN_REPLICATION_SERVER_BADLY_DISCONNECTED
                .get(serverId, baseDn, replicationServerID,
                    savedSession.getReadableRemoteAddress());
            logError(message);
          }

          if (reconnectOnFailure)
          {
            reStart(savedSession, true);
          }
          else
          {
            break; // does not seem necessary to explicitly disconnect ..
          }
        }
      }
    } // while !shutdown
    return null;
  }

  /**
   * Gets the States of all the Replicas currently in the
   * Topology.
   * When this method is called, a Monitoring message will be sent
   * to the Replication Server to which this domain is currently connected
   * so that it computes a table containing information about
   * all Directory Servers in the topology.
   * This Computation involves communications will all the servers
   * currently connected and
   *
   * @return The States of all Replicas in the topology (except us)
   */
  public Map<Integer, ServerState> getReplicaStates()
  {
    monitorResponse.set(false);

    // publish Monitor Request Message to the Replication Server
    publish(new MonitorRequestMsg(serverId, getRsServerId()));

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
      if ((updateDoneCount >= halfRcvWindow) && (session != null))
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

  /**
   * stop the server.
   */
  public void stop()
  {
    if (debugEnabled())
      TRACER.debugInfo("ReplicationBroker " + serverId + " is stopping and will"
        + " close the connection to replication server " + rsServerId + " for"
        + " domain " + baseDn);

    synchronized (startStopLock)
    {
      shutdown = true;
      connected = false;
      stopRSHeartBeatMonitoring();
      stopChangeTimeHeartBeatPublishing();
      replicationServer = "stopped";
      rsGroupId = -1;
      rsServerId = -1;
      rsServerUrl = null;
      setSession(null);
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
    if (session != null)
    {
      session.setSoTimeout(timeout);
    }
  }

  /**
   * Get the name of the replicationServer to which this broker is currently
   * connected.
   *
   * @return the name of the replicationServer to which this domain
   *         is currently connected.
   */
  public String getReplicationServer()
  {
    return replicationServer;
  }

  /**
   * Get the maximum receive window size.
   *
   * @return The maximum receive window size.
   */
  public int getMaxRcvWindow()
  {
    return maxRcvWindow;
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
    if (connected)
    {
      return sendWindow.availablePermits();
    } else
    {
      return 0;
    }
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
   * @param replicationServers  The new list of replication servers.
   * @param window              The max window size.
   * @param heartbeatInterval   The heartBeat interval.
   *
   * @return                    A boolean indicating if the changes
   *                            requires to restart the service.
   * @param groupId            The new group id to use
   */
  public boolean changeConfig(
    Collection<String> replicationServers, int window, long heartbeatInterval,
    byte groupId)
  {
    // These parameters needs to be renegotiated with the ReplicationServer
    // so if they have changed, that requires restarting the session with
    // the ReplicationServer.
    Boolean needToRestartSession = false;

    // A new session is necessary only when information regarding
    // the connection is modified
    if (this.replicationServerUrls == null
        || replicationServers.size() != this.replicationServerUrls.size()
        || !replicationServers.containsAll(this.replicationServerUrls)
        || window != this.maxRcvWindow
        || heartbeatInterval != this.heartbeatInterval
        || groupId != this.groupId)
    {
      needToRestartSession = true;
    }

    this.replicationServerUrls = replicationServers;
    this.rcvWindow = window;
    this.maxRcvWindow = window;
    this.halfRcvWindow = window / 2;
    this.heartbeatInterval = heartbeatInterval;
    this.groupId = groupId;

    return needToRestartSession;
  }

  /**
   * Get the version of the replication protocol.
   * @return The version of the replication protocol.
   */
  public short getProtocolVersion()
  {
    return protocolVersion;
  }

  /**
   * Check if the broker is connected to a ReplicationServer and therefore
   * ready to received and send Replication Messages.
   *
   * @return true if the server is connected, false if not.
   */
  public boolean isConnected()
  {
    return connected;
  }

  /**
   * Determine whether the connection to the replication server is encrypted.
   * @return true if the connection is encrypted, false otherwise.
   */
  public boolean isSessionEncrypted()
  {
    final Session tmp = session;
    return tmp != null ? tmp.isEncrypted() : false;
  }

  /**
   * Signals the RS we just entered a new status.
   * @param newStatus The status the local DS just entered
   */
  public void signalStatusChange(ServerStatus newStatus)
  {
    try
    {
      ChangeStatusMsg csMsg = new ChangeStatusMsg(ServerStatus.INVALID_STATUS,
        newStatus);
      session.publish(csMsg);
    } catch (IOException ex)
    {
      Message message = ERR_EXCEPTION_SENDING_CS.get(
        baseDn,
        Integer.toString(serverId),
        ex.getLocalizedMessage() + stackTraceToSingleLineString(ex));
      logError(message);
    }
  }

  /**
   * Sets the group id of the broker.
   * @param groupId The new group id.
   */
  public void setGroupId(byte groupId)
  {
    this.groupId = groupId;
  }

  /**
   * Gets the info for DSs in the topology (except us).
   * @return The info for DSs in the topology (except us)
   */
  public List<DSInfo> getDsList()
  {
    return dsList;
  }

  /**
   * Gets the info for RSs in the topology (except the one we are connected
   * to).
   * @return The info for RSs in the topology (except the one we are connected
   * to)
   */
  public List<RSInfo> getRsList()
  {
    List<RSInfo> result = new ArrayList<RSInfo>();

    for (ReplicationServerInfo replicationServerInfo :
      replicationServerInfos.values())
    {
      result.add(replicationServerInfo.toRSInfo());
    }
    return result;
  }

  /**
   * Computes the list of DSs connected to a particular RS.
   * @param rsId The RS id of the server one wants to know the connected DSs
   * @param dsList The list of DSinfo from which to compute things
   * @return The list of connected DSs to the server rsId
   */
  private List<Integer> computeConnectedDSs(int rsId, List<DSInfo> dsList)
  {
    List<Integer> connectedDSs = new ArrayList<Integer>();

    if (rsServerId == rsId)
    {
      /*
      If we are computing connected DSs for the RS we are connected
      to, we should count the local DS as the DSInfo of the local DS is not
      sent by the replication server in the topology message. We must count
      ourselves as a connected server.
      */
      connectedDSs.add(serverId);
    }

    for (DSInfo dsInfo : dsList)
    {
      if (dsInfo.getRsId() == rsId)
        connectedDSs.add(dsInfo.getDsId());
    }

    return connectedDSs;
  }

  /**
   * Processes an incoming TopologyMsg.
   * Updates the structures for the local view of the topology.
   *
   * @param topoMsg The topology information received from RS.
   */
  public void receiveTopo(TopologyMsg topoMsg)
  {
    if (debugEnabled())
      TRACER.debugInfo(this + " receive TopologyMsg=" + topoMsg);

    // Store new DS list
    dsList = topoMsg.getDsList();

    // Update replication server info list with the received topology
    // information
    List<Integer> rsToKeepList = new ArrayList<Integer>();
    for (RSInfo rsInfo : topoMsg.getRsList())
    {
      int rsId = rsInfo.getId();
      rsToKeepList.add(rsId); // Mark this server as still existing
      List<Integer> connectedDSs = computeConnectedDSs(rsId, dsList);
      ReplicationServerInfo replicationServerInfo =
        replicationServerInfos.get(rsId);
      if (replicationServerInfo == null)
      {
        // New replication server, create info for it add it to the list
        replicationServerInfo =
          new ReplicationServerInfo(rsInfo, connectedDSs);
        // Set the locally configured flag for this new RS only if it is
        // configured
        updateRSInfoLocallyConfiguredStatus(replicationServerInfo);
        replicationServerInfos.put(rsId, replicationServerInfo);
      } else
      {
        // Update the existing info for the replication server
        replicationServerInfo.update(rsInfo, connectedDSs);
      }
    }

    /**
     * Now remove any replication server that may have disappeared from the
     * topology.
     */
    Iterator<Entry<Integer, ReplicationServerInfo>> rsInfoIt =
      replicationServerInfos.entrySet().iterator();
    while (rsInfoIt.hasNext())
    {
      Entry<Integer, ReplicationServerInfo> rsInfoEntry = rsInfoIt.next();
      if (!rsToKeepList.contains(rsInfoEntry.getKey()))
      {
        // This replication server has quit the topology, remove it from the
        // list
        rsInfoIt.remove();
      }
    }
    if (domain != null)
    {
      for (DSInfo info : dsList)
      {
        domain.setEclIncludes(info.getDsId(), info.getEclIncludes(),
            info.getEclIncludesForDeletes());
      }
    }
  }

  /**
   * Check if the broker could not find any Replication Server and therefore
   * connection attempt failed.
   *
   * @return true if the server could not connect to any Replication Server.
   */
  public boolean hasConnectionError()
  {
    return connectionError;
  }

  /**
   * Starts publishing to the RS the current timestamp used in this server.
   */
  public void startChangeTimeHeartBeatPublishing()
  {
    // Start a CN heartbeat thread.
    if (changeTimeHeartbeatSendInterval > 0)
    {
      String threadName = "Replica DS("
          + this.getServerId()
          + ") change time heartbeat publisher for domain \""
          + this.baseDn + "\" to RS(" + this.getRsServerId()
          + ") at " + session.getReadableRemoteAddress();

      ctHeartbeatPublisherThread = new CTHeartbeatPublisherThread(
          threadName, session, changeTimeHeartbeatSendInterval,
          serverId);
      ctHeartbeatPublisherThread.start();
    } else
    {
      if (debugEnabled())
        TRACER.debugInfo(this
          + " is not configured to send CN heartbeat interval");
    }
  }

  /**
   * Stops publishing to the RS the current timestamp used in this server.
   */
  public synchronized void stopChangeTimeHeartBeatPublishing()
  {
    if (ctHeartbeatPublisherThread != null)
    {
      ctHeartbeatPublisherThread.shutdown();
      ctHeartbeatPublisherThread = null;
    }
  }

  /**
   * Set a new change time heartbeat interval to this broker.
   * @param changeTimeHeartbeatInterval The new interval (in ms).
   */
  public void setChangeTimeHeartbeatInterval(int changeTimeHeartbeatInterval)
  {
    stopChangeTimeHeartBeatPublishing();
    this.changeTimeHeartbeatSendInterval = changeTimeHeartbeatInterval;
    startChangeTimeHeartBeatPublishing();
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
  public boolean shuttingDown()
  {
    return shutdown;
  }

  /**
   * Returns the local address of this replication domain, or the empty string
   * if it is not yet connected.
   *
   * @return The local address.
   */
  String getLocalUrl()
  {
    final Session tmp = session;
    return tmp != null ? tmp.getLocalUrl() : "";
  }

  /**
   * Returns the replication monitor associated with this broker.
   *
   * @return The replication monitor.
   */
  ReplicationMonitor getReplicationMonitor()
  {
    // Only invoked by replication domain so always non-null.
    return monitor;
  }

  private void setSession(final Session newSession)
  {
    // De-register the monitor with the old name.
    deregisterReplicationMonitor();

    final Session oldSession = session;
    if (oldSession != null)
    {
      oldSession.close();
    }
    session = newSession;

    // Re-register the monitor with the new name.
    registerReplicationMonitor();
  }

  private void registerReplicationMonitor()
  {
    /*
     * The monitor should not be registered if this is a unit test because the
     * replication domain is null.
     */
    if (monitor != null)
    {
      DirectoryServer.registerMonitorProvider(monitor);
    }
  }

  private void deregisterReplicationMonitor()
  {
    /*
     * The monitor should not be deregistered if this is a unit test because the
     * replication domain is null.
     */
    if (monitor != null)
    {
      DirectoryServer.deregisterMonitorProvider(monitor);
    }
  }
}
