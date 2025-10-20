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
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.replication.service;

import static org.forgerock.opendj.ldap.ResultCode.*;
import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.replication.common.AssuredMode.*;
import static org.opends.server.replication.common.StatusMachine.*;
import static org.opends.server.util.CollectionUtils.*;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import net.jcip.annotations.Immutable;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.server.config.meta.ReplicationDomainCfgDefn.AssuredType;
import org.forgerock.opendj.server.config.server.ReplicationDomainCfg;
import org.opends.server.api.DirectoryThread;
import org.opends.server.api.MonitorData;
import org.opends.server.backends.task.Task;
import org.opends.server.replication.common.AssuredMode;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.CSNGenerator;
import org.opends.server.replication.common.DSInfo;
import org.opends.server.replication.common.RSInfo;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.common.ServerStatus;
import org.opends.server.replication.common.StatusMachine;
import org.opends.server.replication.common.StatusMachineEvent;
import org.opends.server.replication.protocol.AckMsg;
import org.opends.server.replication.protocol.ChangeStatusMsg;
import org.opends.server.replication.protocol.DoneMsg;
import org.opends.server.replication.protocol.EntryMsg;
import org.opends.server.replication.protocol.ErrorMsg;
import org.opends.server.replication.protocol.HeartbeatMsg;
import org.opends.server.replication.protocol.InitializeRcvAckMsg;
import org.opends.server.replication.protocol.InitializeRequestMsg;
import org.opends.server.replication.protocol.InitializeTargetMsg;
import org.opends.server.replication.protocol.ProtocolVersion;
import org.opends.server.replication.protocol.ReplSessionSecurity;
import org.opends.server.replication.protocol.ReplicationMsg;
import org.opends.server.replication.protocol.ResetGenerationIdMsg;
import org.opends.server.replication.protocol.RoutableMsg;
import org.opends.server.replication.protocol.TopologyMsg;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.tasks.InitializeTargetTask;
import org.opends.server.tasks.InitializeTask;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.HostPort;

/**
 * This class should be used as a base for Replication implementations.
 * <p>
 * It is intended that developer in need of a replication mechanism
 * subclass this class with their own implementation.
 * <p>
 *   The startup phase of the ReplicationDomain subclass,
 *   should read the list of replication servers from the configuration,
 *   instantiate a {@link ServerState} then start the publish service
 *   by calling {@link #startPublishService()}.
 *   At this point it can start calling the {@link #publish(UpdateMsg)}
 *   method if needed.
 * <p>
 *   When the startup phase reach the point when the subclass is ready
 *   to handle updates the Replication Domain implementation should call the
 *   {@link #startListenService()} method.
 *   At this point a Listener thread is created on the Replication Service
 *   and which can start receiving updates.
 * <p>
 *   When updates are received the Replication Service calls the
 *   {@link #processUpdate(UpdateMsg)} method.
 *   ReplicationDomain implementation should implement the appropriate code
 *   for replaying the update on the local repository.
 *   When fully done the subclass must call the
 *   {@link #processUpdateDone(UpdateMsg, String)} method.
 *   This allows to process the update asynchronously if necessary.
 *
 * <p>
 *   To propagate changes to other replica, a ReplicationDomain implementation
 *   must use the {@link #publish(UpdateMsg)} method.
 * <p>
 *   If the Full Initialization process is needed then implementation
 *   for {@code importBackend(InputStream)} and
 *   {@code exportBackend(OutputStream)} must be
 *   provided.
 * <p>
 *   Full Initialization of a replica can be triggered by LDAP clients
 *   by creating InitializeTasks or InitializeTargetTask.
 *   Full initialization can also be triggered from the ReplicationDomain
 *   implementation using methods {@link #initializeRemote(int, Task)}
 *   or {@link #initializeFromRemote(int, Task)}.
 * <p>
 *   At shutdown time, the {@link #disableService()} method should be called to
 *   cleanly stop the replication service.
 */
public abstract class ReplicationDomain
{
  /** Contains all the attributes included for the ECL (External Changelog). */
  @Immutable
  private static final class ECLIncludes
  {
    final Map<Integer, Set<String>> includedAttrsByServer;
    final Set<String> includedAttrsAllServers;

    final Map<Integer, Set<String>> includedAttrsForDeletesByServer;
    final Set<String> includedAttrsForDeletesAllServers;

    private ECLIncludes(
        Map<Integer, Set<String>> includedAttrsByServer,
        Set<String> includedAttrsAllServers,
        Map<Integer, Set<String>> includedAttrsForDeletesByServer,
        Set<String> includedAttrsForDeletesAllServers)
    {
      this.includedAttrsByServer = includedAttrsByServer;
      this.includedAttrsAllServers = includedAttrsAllServers;

      this.includedAttrsForDeletesByServer = includedAttrsForDeletesByServer;
      this.includedAttrsForDeletesAllServers =includedAttrsForDeletesAllServers;
    }

    @SuppressWarnings("unchecked")
    public ECLIncludes()
    {
      this(Collections.EMPTY_MAP, Collections.EMPTY_SET, Collections.EMPTY_MAP,
          Collections.EMPTY_SET);
    }

    /**
     * Add attributes to be included in the ECL.
     *
     * @param serverId
     *          Server where these attributes are configured.
     * @param includeAttributes
     *          Attributes to be included with all change records, may include
     *          wild-cards.
     * @param includeAttributesForDeletes
     *          Additional attributes to be included with delete change records,
     *          may include wild-cards.
     * @return a new {@link ECLIncludes} object if included attributes have
     *         changed, or the current object otherwise.
     */
    public ECLIncludes addIncludedAttributes(int serverId,
        Set<String> includeAttributes, Set<String> includeAttributesForDeletes)
    {
      boolean configurationChanged = false;

      Set<String> s1 = new HashSet<>(includeAttributes);

      // Combine all+delete attributes.
      Set<String> s2 = new HashSet<>(s1);
      s2.addAll(includeAttributesForDeletes);

      Map<Integer,Set<String>> eclIncludesByServer = this.includedAttrsByServer;
      if (!s1.equals(this.includedAttrsByServer.get(serverId)))
      {
        configurationChanged = true;
        eclIncludesByServer = new HashMap<>(this.includedAttrsByServer);
        eclIncludesByServer.put(serverId, Collections.unmodifiableSet(s1));
      }

      Map<Integer, Set<String>> eclIncludesForDeletesByServer = this.includedAttrsForDeletesByServer;
      if (!s2.equals(this.includedAttrsForDeletesByServer.get(serverId)))
      {
        configurationChanged = true;
        eclIncludesForDeletesByServer = new HashMap<>(this.includedAttrsForDeletesByServer);
        eclIncludesForDeletesByServer.put(serverId, Collections.unmodifiableSet(s2));
      }

      if (!configurationChanged)
      {
        return this;
      }

      // and rebuild the global list to be ready for usage
      Set<String> eclIncludesAllServer = new HashSet<>();
      for (Set<String> attributes : eclIncludesByServer.values())
      {
        eclIncludesAllServer.addAll(attributes);
      }

      Set<String> eclIncludesForDeletesAllServer = new HashSet<>();
      for (Set<String> attributes : eclIncludesForDeletesByServer.values())
      {
        eclIncludesForDeletesAllServer.addAll(attributes);
      }
      return new ECLIncludes(eclIncludesByServer,
          Collections.unmodifiableSet(eclIncludesAllServer),
          eclIncludesForDeletesByServer,
          Collections.unmodifiableSet(eclIncludesForDeletesAllServer));
    }
  }

  /** Current status for this replicated domain. */
  private ServerStatus status = ServerStatus.NOT_CONNECTED_STATUS;
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The configuration of the replication domain. */
  protected volatile ReplicationDomainCfg config;
  /**
   * The assured configuration of the replication domain. It is a duplicate of
   * {@link #config} because of its update model.
   *
   * @see #readAssuredConfig(ReplicationDomainCfg, boolean)
   */
  private volatile ReplicationDomainCfg assuredConfig;

  /**
   * The ReplicationBroker that is used by this ReplicationDomain to
   * connect to the ReplicationService.
   */
  protected ReplicationBroker broker;

  /**
   * This Map is used to store all outgoing assured messages in order
   * to be able to correlate all the coming back acks to the original
   * operation.
   */
  private final Map<CSN, UpdateMsg> waitingAckMsgs = new ConcurrentHashMap<>();
  /**
   * The context related to an import or export being processed
   * Null when none is being processed.
   */
  private final AtomicReference<ImportExportContext> importExportContext = new AtomicReference<>();

  /**
   * The Thread waiting for incoming update messages for this domain and pushing
   * them to the global incoming update message queue for later processing by
   * replay threads.
   */
  private volatile DirectoryThread listenerThread;

  /** A set of counters used for Monitoring. */
  private AtomicInteger numProcessedUpdates = new AtomicInteger(0);
  private AtomicInteger numRcvdUpdates = new AtomicInteger(0);
  private AtomicInteger numSentUpdates = new AtomicInteger(0);

  /** Assured replication monitoring counters. */

  /** Number of updates sent in Assured Mode, Safe Read. */
  private AtomicInteger assuredSrSentUpdates = new AtomicInteger(0);
  /**
   * Number of updates sent in Assured Mode, Safe Read, that have been
   * successfully acknowledged.
   */
  private AtomicInteger assuredSrAcknowledgedUpdates = new AtomicInteger(0);
  /**
   * Number of updates sent in Assured Mode, Safe Read, that have not been
   * successfully acknowledged (either because of timeout, wrong status or error
   * at replay).
   */
  private AtomicInteger assuredSrNotAcknowledgedUpdates = new AtomicInteger(0);
  /**
   * Number of updates sent in Assured Mode, Safe Read, that have not been
   * successfully acknowledged because of timeout.
   */
  private AtomicInteger assuredSrTimeoutUpdates = new AtomicInteger(0);
  /**
   * Number of updates sent in Assured Mode, Safe Read, that have not been
   * successfully acknowledged because of wrong status.
   */
  private AtomicInteger assuredSrWrongStatusUpdates = new AtomicInteger(0);
  /**
   * Number of updates sent in Assured Mode, Safe Read, that have not been
   * successfully acknowledged because of replay error.
   */
  private AtomicInteger assuredSrReplayErrorUpdates = new AtomicInteger(0);
  /**
   * Multiple values allowed: number of updates sent in Assured Mode, Safe Read,
   * that have not been successfully acknowledged (either because of timeout,
   * wrong status or error at replay) for a particular server (DS or RS).
   * <p>
   * String format: &lt;server id&gt;:&lt;number of failed updates&gt;
   */
  private final Map<Integer, Integer> assuredSrServerNotAcknowledgedUpdates = new HashMap<>();
  /** Number of updates received in Assured Mode, Safe Read request. */
  private AtomicInteger assuredSrReceivedUpdates = new AtomicInteger(0);
  /**
   * Number of updates received in Assured Mode, Safe Read request that we have
   * acked without errors.
   */
  private AtomicInteger assuredSrReceivedUpdatesAcked = new AtomicInteger(0);
  /**
   * Number of updates received in Assured Mode, Safe Read request that we have
   * acked with errors.
   */
  private AtomicInteger assuredSrReceivedUpdatesNotAcked = new AtomicInteger(0);
  /** Number of updates sent in Assured Mode, Safe Data. */
  private AtomicInteger assuredSdSentUpdates = new AtomicInteger(0);
  /**
   * Number of updates sent in Assured Mode, Safe Data, that have been
   * successfully acknowledged.
   */
  private AtomicInteger assuredSdAcknowledgedUpdates = new AtomicInteger(0);
  /**
   * Number of updates sent in Assured Mode, Safe Data, that have not been
   * successfully acknowledged because of timeout.
   */
  private AtomicInteger assuredSdTimeoutUpdates = new AtomicInteger(0);
  /**
   * Multiple values allowed: number of updates sent in Assured Mode, Safe Data,
   * that have not been successfully acknowledged because of timeout for a
   * particular RS.
   * <p>
   * String format: &lt;server id&gt;:&lt;number of failed updates&gt;
   */
  private final Map<Integer, Integer> assuredSdServerTimeoutUpdates = new HashMap<>();

  /* Status related monitoring fields */

  /**
   * Indicates the date when the status changed. This may be used to indicate
   * the date the session with the current replication server started (when
   * status is NORMAL for instance). All the above assured monitoring fields
   * are also reset each time the status is changed
   */
  private Date lastStatusChangeDate = new Date();

  /** The state maintained by the Concrete Class. */
  private final ServerState state;

  /** The generator that will be used to generate {@link CSN} for this domain. */
  private final CSNGenerator generator;

  private final AtomicReference<ECLIncludes> eclIncludes = new AtomicReference<>(new ECLIncludes());

  /**
   * An object used to protect the initialization of the underlying broker
   * session of this ReplicationDomain.
   */
  private final Object sessionLock = new Object();

  /**
   * The generationId for this replication domain. It is made of a hash of the
   * 1000 first entries for this domain.
   */
  protected volatile long generationId;

  /**
   * Thread pool for export thread tasks
   */
  private final ExecutorService exportThreadPool = Executors.newCachedThreadPool();

  /**
   * Returns the {@link CSNGenerator} that will be used to
   * generate {@link CSN} for this domain.
   *
   * @return The {@link CSNGenerator} that will be used to
   *         generate {@link CSN} for this domain.
   */
  public CSNGenerator getGenerator()
  {
    return generator;
  }

  /**
   * Creates a ReplicationDomain with the provided parameters.
   *
   * @param config
   *          The configuration object for this ReplicationDomain
   * @param generationId
   *          the generation of this ReplicationDomain
   */
  public ReplicationDomain(ReplicationDomainCfg config, long generationId)
  {
    this(config, generationId, new ServerState());
  }

  /**
   * Creates a ReplicationDomain with the provided parameters. (for unit test
   * purpose only)
   *
   * @param config
   *          The configuration object for this ReplicationDomain
   * @param generationId
   *          the generation of this ReplicationDomain
   * @param serverState
   *          The serverState to use
   */
  public ReplicationDomain(ReplicationDomainCfg config, long generationId,
      ServerState serverState)
  {
    this.config = config;
    this.assuredConfig = config;
    this.generationId = generationId;
    this.state = serverState;
    this.generator = new CSNGenerator(getServerId(), state);
  }

  /**
   * Set the initial status of the domain and perform necessary initializations.
   * This method will be called by the Broker each time the ReplicationBroker
   * establish a new session to a Replication Server.
   *
   * Implementations may override this method when they need to perform
   * additional computing after session establishment.
   * The default implementation should be sufficient for ReplicationDomains
   * that don't need to perform additional computing.
   *
   * @param initStatus              The status to enter the state machine with.
   * @param rsState                 The ServerState of the ReplicationServer
   *                                with which the session was established.
   */
  public void sessionInitiated(ServerStatus initStatus, ServerState rsState)
  {
    // Sanity check: is it a valid initial status?
    if (!isValidInitialStatus(initStatus))
    {
      logger.error(ERR_DS_INVALID_INIT_STATUS, initStatus, getBaseDN(), getServerId());
    }
    else
    {
      status = initStatus;
    }
    generator.adjust(state);
    generator.adjust(rsState);
  }

  /**
   * Processes an incoming ChangeStatusMsg. Compute new status according to
   * given order. Then update domain for being compliant with new status
   * definition.
   * @param csMsg The received status message
   */
  private void receiveChangeStatus(ChangeStatusMsg csMsg)
  {
    if (logger.isTraceEnabled())
    {
      logger.trace("Replication domain " + getBaseDN() +
        " received change status message:\n" + csMsg);
    }

    ServerStatus reqStatus = csMsg.getRequestedStatus();

    // Translate requested status to a state machine event
    StatusMachineEvent event = StatusMachineEvent.statusToEvent(reqStatus);
    if (event == StatusMachineEvent.INVALID_EVENT)
    {
      logger.error(ERR_DS_INVALID_REQUESTED_STATUS, reqStatus, getBaseDN(), getServerId());
      return;
    }

    // Set the new status to the requested one
    setNewStatus(event);
  }

  /** Called when first connection or disconnection detected. */
  void toNotConnectedStatus()
  {
    // Go into not connected status
    setNewStatus(StatusMachineEvent.TO_NOT_CONNECTED_STATUS_EVENT);
  }

  /**
   * Perform whatever actions are needed to apply properties for being
   * compliant with new status. Must be called in synchronized section for
   * status. The new status is already set in status variable.
   */
  private void updateDomainForNewStatus()
  {
    switch (status)
    {
      case FULL_UPDATE_STATUS:
        // Signal RS we just entered the full update status
        broker.signalStatusChange(status);
        break;
      case NOT_CONNECTED_STATUS:
      case NORMAL_STATUS:
      case DEGRADED_STATUS:
      case BAD_GEN_ID_STATUS:
        break;
      default:
        if (logger.isTraceEnabled())
        {
          logger.trace("updateDomainForNewStatus: unexpected status: " + status);
        }
    }
  }

  /**
   * Gets the status for this domain.
   * @return The status for this domain.
   */
  public ServerStatus getStatus()
  {
    return status;
  }

  /**
   * Returns the base DN of this ReplicationDomain. All Replication Domain using
   * this baseDN will be connected through the Replication Service.
   *
   * @return The base DN of this ReplicationDomain
   */
  public DN getBaseDN()
  {
    return config.getBaseDN();
  }

  /**
   * Get the server ID. The identifier of this Replication Domain inside the
   * Replication Service. Each Domain must use a unique ServerID.
   *
   * @return The server ID.
   */
  public int getServerId()
  {
    return config.getServerId();
  }

  /**
   * Window size used during initialization .. between - the
   * initializer/exporter DS that listens/waits acknowledges and that slows down
   * data msg publishing based on the slowest server - and each
   * initialized/importer DS that publishes acknowledges each WINDOW/2 data msg
   * received.
   *
   * @return the initWindow
   */
  private int getInitWindow()
  {
    return config.getInitializationWindowSize();
  }

  /**
   * Tells if assured replication is enabled for this domain.
   * @return True if assured replication is enabled for this domain.
   */
  public boolean isAssured()
  {
    return AssuredType.SAFE_DATA.equals(assuredConfig.getAssuredType())
        || AssuredType.SAFE_READ.equals(assuredConfig.getAssuredType());
  }

  /**
   * Gives the mode for the assured replication of the domain. Only used when
   * assured is true).
   *
   * @return The mode for the assured replication of the domain.
   */
  public AssuredMode getAssuredMode()
  {
    switch (assuredConfig.getAssuredType())
    {
    case SAFE_DATA:
    case NOT_ASSURED: // The assured mode will be ignored in that case anyway
      return AssuredMode.SAFE_DATA_MODE;
    case SAFE_READ:
      return AssuredMode.SAFE_READ_MODE;
    }
    return null; // should never happen
  }

  /**
   * Gives the assured Safe Data level of the replication of the domain. (used
   * when assuredMode is SAFE_DATA).
   *
   * @return The assured level of the replication of the domain.
   */
  public byte getAssuredSdLevel()
  {
    return (byte) assuredConfig.getAssuredSdLevel();
  }

  /**
   * Gives the assured timeout of the replication of the domain (in ms).
   * @return The assured timeout of the replication of the domain.
   */
  public long getAssuredTimeout()
  {
    return assuredConfig.getAssuredTimeout();
  }

  /**
   * Gets the group id for this domain.
   * @return The group id for this domain.
   */
  public byte getGroupId()
  {
    return (byte) config.getGroupId();
  }

  /**
   * Gets the referrals URLs this domain publishes. Referrals urls to be
   * published to other servers of the topology.
   * <p>
   * TODO: fill that with all currently opened urls if no urls configured
   *
   * @return The referrals URLs this domain publishes.
   */
  public Set<String> getRefUrls()
  {
    return config.getReferralsUrl();
  }

  /**
   * Gets the info for Replicas in the topology (except us).
   * @return The info for Replicas in the topology (except us)
   */
  public Map<Integer, DSInfo> getReplicaInfos()
  {
    return broker.getReplicaInfos();
  }

  /**
   * Returns information about the DS server related to the provided serverId.
   * based on the TopologyMsg we received when the remote replica connected or
   * disconnected. Return null when no server with the provided serverId is
   * connected.
   *
   * @param  dsId The provided serverId of the remote replica
   * @return the info related to this remote server if it is connected,
   *                  null is the server is NOT connected.
   */
  private DSInfo getConnectedRemoteDS(int dsId)
  {
    return getReplicaInfos().get(dsId);
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
    return broker.getReplicaStates();
  }

  /**
   * Gets the info for RSs in the topology (except the one we are connected
   * to).
   * @return The info for RSs in the topology (except the one we are connected
   * to)
   */
  public List<RSInfo> getRsInfos()
  {
    return broker.getRsInfos();
  }

  /**
   * Gets the server ID of the Replication Server to which the domain
   * is currently connected.
   *
   * @return The server ID of the Replication Server to which the domain
   *         is currently connected.
   */
  public int getRsServerId()
  {
    return broker.getRsServerId();
  }

  /** Increment the number of processed updates. */
  private void incProcessedUpdates()
  {
    numProcessedUpdates.incrementAndGet();
  }

  /**
   * Get the number of updates replayed by the replication.
   *
   * @return The number of updates replayed by the replication
   */
  int getNumProcessedUpdates()
  {
    if (numProcessedUpdates != null)
    {
      return numProcessedUpdates.get();
    }
    return 0;
  }

  /**
   * Get the number of updates received by the replication plugin.
   *
   * @return the number of updates received
   */
  int getNumRcvdUpdates()
  {
    if (numRcvdUpdates != null)
    {
      return numRcvdUpdates.get();
    }
    return 0;
  }

  /**
   * Get the number of updates sent by the replication plugin.
   *
   * @return the number of updates sent
   */
  int getNumSentUpdates()
  {
    if (numSentUpdates != null)
    {
      return numSentUpdates.get();
    }
    return 0;
  }

  /**
   * Receives an update message from the replicationServer.
   * The other types of messages are processed in an opaque way for the caller.
   * Also responsible for updating the list of pending changes
   * @return the received message - null if none
   */
  private UpdateMsg receive()
  {
    UpdateMsg update = null;

    while (update == null)
    {
      InitializeRequestMsg initReqMsg = null;
      ReplicationMsg msg;
      try
      {
        msg = broker.receive(true, true, false);
        if (msg == null)
        {
          // The server is in the shutdown process
          return null;
        }

        if (logger.isTraceEnabled() && !(msg instanceof HeartbeatMsg))
        {
          logger.trace("LocalizableMessage received <" + msg + ">");
        }

        if (msg instanceof AckMsg)
        {
          AckMsg ack = (AckMsg) msg;
          receiveAck(ack);
        }
        else if (msg instanceof InitializeRequestMsg)
        {
          // Another server requests us to provide entries
          // for a total update
          initReqMsg = (InitializeRequestMsg)msg;
        }
        else if (msg instanceof InitializeTargetMsg)
        {
          // Another server is exporting its entries to us
          InitializeTargetMsg initTargetMsg = (InitializeTargetMsg) msg;

          /*
          This must be done while we are still holding the broker lock
          because we are now going to receive a bunch of entries from the
          remote server and we want the import thread to catch them and
          not the ListenerThread.
          */
          initialize(initTargetMsg, initTargetMsg.getSenderID());
        }
        else if (msg instanceof ErrorMsg)
        {
          ErrorMsg errorMsg = (ErrorMsg)msg;
          ImportExportContext ieCtx = importExportContext.get();
          if (ieCtx != null)
          {
            /*
            This is an error termination for the 2 following cases :
            - either during an export
            - or before an import really started
            For example, when we publish a request and the
            replicationServer did not find the import source.

            A remote error during the import will be received in the
            receiveEntryBytes() method.
            */
            if (logger.isTraceEnabled())
            {
              logger.trace(
                  "[IE] processErrorMsg:" + getServerId() +
                  " baseDN: " + getBaseDN() +
                  " Error Msg received: " + errorMsg);
            }

            if (errorMsg.getCreationTime() > ieCtx.startTime)
            {
              // consider only ErrorMsg that relate to the current import/export
              processErrorMsg(errorMsg, ieCtx);
            }
            else
            {
              /*
              Simply log - happen when the ErrorMsg relates to a previous
              attempt of initialization while we have started a new one
              on this side.
              */
              logger.error(ERR_ERROR_MSG_RECEIVED, errorMsg.getDetails());
            }
          }
          else
          {
            // Simply log - happen if import/export has been terminated
            // on our side before receiving this ErrorMsg.
            logger.error(ERR_ERROR_MSG_RECEIVED, errorMsg.getDetails());
          }
        }
        else if (msg instanceof ChangeStatusMsg)
        {
          ChangeStatusMsg csMsg = (ChangeStatusMsg)msg;
          receiveChangeStatus(csMsg);
        }
        else if (msg instanceof UpdateMsg)
        {
          update = (UpdateMsg) msg;
          // If the replica is reset to an older state (server died, reset from a backup of day-1),
          // then its generator state must be adjusted back to what it was before.
          // Scary: what happens if the DS starts accepting updates
          // before the recovery is finished?
          generator.adjust(update.getCSN());
        }
        else if (msg instanceof InitializeRcvAckMsg)
        {
          ImportExportContext ieCtx = importExportContext.get();
          if (ieCtx != null)
          {
            InitializeRcvAckMsg ackMsg = (InitializeRcvAckMsg) msg;
            ieCtx.setAckVal(ackMsg.getSenderID(), ackMsg.getNumAck());
          }
          // Trash this msg When no input/export is running/should never happen
        }
      }
      catch (SocketTimeoutException e)
      {
        // just retry
      }
      /*
      Test if we have received and export request message and
      if that's the case handle it now.
      This must be done outside of the portion of code protected
      by the broker lock so that we keep receiving update
      when we are doing and export and so that a possible
      closure of the socket happening when we are publishing the
      entries to the remote can be handled by the other
      replay thread when they call this method and therefore the
      broker.receive() method.
      */
      if (initReqMsg != null)
      {
        // Do this work in a thread to allow replay thread continue working
        ExportThread exportThread = new ExportThread(
            initReqMsg.getSenderID(), initReqMsg.getInitWindow());
        exportThreadPool.execute(() -> {
          Thread.currentThread().setName(exportThread.getName());
          exportThread.run();
        });
      }
    }

    numRcvdUpdates.incrementAndGet();
    if (update.isAssured()
        && broker.getRsGroupId() == getGroupId()
        && update.getAssuredMode() == AssuredMode.SAFE_READ_MODE)
    {
      assuredSrReceivedUpdates.incrementAndGet();
    }
    return update;
  }

  /**
   * Updates the passed monitoring list of errors received for assured messages
   * (safe data or safe read, depending of the passed list to update) for a
   * particular server in the list. This increments the counter of error for the
   * passed server, or creates an initial value of 1 error for it if the server
   * is not yet present in the map.
   * @param errorsByServer map of number of errors per serverID
   * @param sid the ID of the server which produced an error
   */
  private void updateAssuredErrorsByServer(Map<Integer,Integer> errorsByServer,
    Integer sid)
  {
    synchronized (errorsByServer)
    {
      Integer serverErrCount = errorsByServer.get(sid);
      if (serverErrCount == null)
      {
        // Server not present in list, create an entry with an
        // initial number of errors set to 1
        errorsByServer.put(sid, 1);
      } else
      {
        // Server already present in list, just increment number of
        // errors for the server
        int val = serverErrCount;
        val++;
        errorsByServer.put(sid, val);
      }
    }
  }

  /**
   * Do the necessary processing when an AckMsg is received.
   *
   * @param ack The AckMsg that was received.
   */
  private void receiveAck(AckMsg ack)
  {
    CSN csn = ack.getCSN();

    // Remove the message for pending ack list (this may already make the thread
    // that is waiting for the ack be aware of its reception)
    UpdateMsg update = waitingAckMsgs.remove(csn);

    // Signal waiting thread ack has been received
    if (update != null)
    {
      synchronized (update)
      {
        update.notify();
      }

      // Analyze status of embedded in the ack to see if everything went well
      boolean hasTimeout = ack.hasTimeout();
      boolean hasReplayErrors = ack.hasReplayError();
      boolean hasWrongStatus = ack.hasWrongStatus();

      AssuredMode updateAssuredMode = update.getAssuredMode();

      if (hasTimeout || hasReplayErrors || hasWrongStatus)
      {
        // Some problems detected: message did not correctly reach every requested servers.
        logger.info(NOTE_DS_RECEIVED_ACK_ERROR, getBaseDN(), getServerId(), update, ack.errorsToString());

        List<Integer> failedServers = ack.getFailedServers();

        // Increment assured replication monitoring counters
        switch (updateAssuredMode)
        {
          case SAFE_READ_MODE:
            assuredSrNotAcknowledgedUpdates.incrementAndGet();
            if (hasTimeout)
            {
              assuredSrTimeoutUpdates.incrementAndGet();
            }
            if (hasReplayErrors)
            {
              assuredSrReplayErrorUpdates.incrementAndGet();
            }
            if (hasWrongStatus)
            {
              assuredSrWrongStatusUpdates.incrementAndGet();
            }
            if (failedServers != null) // This should always be the case !
            {
              for(Integer sid : failedServers)
              {
                updateAssuredErrorsByServer(
                  assuredSrServerNotAcknowledgedUpdates, sid);
              }
            }
            break;
          case SAFE_DATA_MODE:
            // The only possible cause of ack error in safe data mode is timeout
            if (hasTimeout) // So should always be the case
            {
              assuredSdTimeoutUpdates.incrementAndGet();
            }
            if (failedServers != null) // This should always be the case !
            {
              for(Integer sid : failedServers)
              {
                updateAssuredErrorsByServer(
                  assuredSdServerTimeoutUpdates, sid);
              }
            }
            break;
          default:
          // Should not happen
        }
      } else
      {
        // Update has been acknowledged without errors
        // Increment assured replication monitoring counters
        switch (updateAssuredMode)
        {
          case SAFE_READ_MODE:
            assuredSrAcknowledgedUpdates.incrementAndGet();
            break;
          case SAFE_DATA_MODE:
            assuredSdAcknowledgedUpdates.incrementAndGet();
            break;
          default:
          // Should not happen
        }
      }
    }
  }


  /*
   * After this point the code is related to the Total Update.
   */

  /**
   * This thread is launched when we want to export data to another server.
   *
   * When a task is created locally (so this local server is the initiator)
   * of the export (Example: dsreplication initialize-all),
   * this thread is NOT used but the task thread is running the export instead).
   */
  private class ExportThread extends DirectoryThread
  {
    /** Id of server that will be initialized. */
    private final int serverIdToInitialize;
    private final int initWindow;

    /**
     * Constructor for the ExportThread.
     *
     * @param serverIdToInitialize
     *          serverId of server that will receive entries
     * @param initWindow
     *          The value of the initialization window for flow control between
     *          the importer and the exporter.
     */
    public ExportThread(int serverIdToInitialize, int initWindow)
    {
      super("Export thread from serverId=" + getServerId() + " to serverId="
          + serverIdToInitialize);
      this.serverIdToInitialize = serverIdToInitialize;
      this.initWindow = initWindow;
    }

    @Override
    public void run()
    {
      if (logger.isTraceEnabled())
      {
        logger.trace("[IE] starting " + getName());
      }
      try
      {
        initializeRemote(serverIdToInitialize, serverIdToInitialize, null,
            initWindow);
      } catch (DirectoryException de)
      {
        /*
        An error message has been sent to the peer
        This server is not the initiator of the export so there is
        nothing more to do locally.
        */
        if (logger.isTraceEnabled()) {
          logger.trace(LocalizableMessage.raw("[IE] got exception" + getName()), de);
        }
      }

      if (logger.isTraceEnabled())
      {
        logger.trace("[IE] ending " + getName());
      }
    }
  }

  /** This class contains the context related to an import or export launched on the domain. */
  protected static final class ImportExportContext
  {
    /** The private task that initiated the operation. */
    private Task initializeTask;
    /** The destination in the case of an export. */
    private int exportTarget = RoutableMsg.UNKNOWN_SERVER;
    /** The source in the case of an import. */
    private int importSource = RoutableMsg.UNKNOWN_SERVER;

    /** The total entry count expected to be processed. */
    private long entryCount;
    /** The count for the entry not yet processed. */
    private long entryLeftCount;

    /** Exception raised during the initialization. */
    private DirectoryException exception;

    /** Whether the context is related to an import or an export. */
    private final boolean importInProgress;

    /** Current counter of messages exchanged during the initialization. */
    private int msgCnt;

    /**
     * Number of connections lost when we start the initialization. Will help
     * counting connections lost during initialization,
     */
    private int initNumLostConnections;

    /** Request message sent when this server has the initializeFromRemote task. */
    private InitializeRequestMsg initReqMsgSent;

    /**
     * Start time of the initialization process. ErrorMsg timestamped before
     * this startTime will be ignored.
     */
    private final long startTime;

    /** List for replicas (DS) connected to the topology when initialization started. */
    private final Set<Integer> startList = new HashSet<>(0);

    /**
     * List for replicas (DS) with a failure (disconnected from the topology)
     * since the initialization started.
     */
    private final Set<Integer> failureList = new HashSet<>(0);

    /**
     * Flow control during initialization: Map of remote serverId to number of messages received.
     */
    private final Map<Integer, Integer> ackVals = new HashMap<>();
    /** ServerId of the slowest server (the one with the smallest non null counter). */
    private int slowestServerId = -1;

    private short exporterProtocolVersion = -1;

    /** Window used during this initialization. */
    private int initWindow;

    /** Number of attempt already done for this initialization. */
    private short attemptCnt;

    /**
     * Creates a new IEContext.
     *
     * @param importInProgress true if the IEContext will be used
     *                         for and import, false if the IEContext
     *                         will be used for and export.
     */
    private ImportExportContext(boolean importInProgress)
    {
      this.importInProgress = importInProgress;
      this.startTime = System.currentTimeMillis();
      this.attemptCnt = 0;
    }

    /**
     * Returns a boolean indicating if a total update import is currently in
     * Progress.
     *
     * @return A boolean indicating if a total update import is currently in
     *         Progress.
     */
    boolean importInProgress()
    {
      return importInProgress;
    }

    /**
     * Returns the total number of entries to be processed when a total update
     * is in progress.
     *
     * @return The total number of entries to be processed when a total update
     *         is in progress.
     */
    long getTotalEntryCount()
    {
      return entryCount;
    }

    /**
     * Returns the number of entries still to be processed when a total update
     * is in progress.
     *
     * @return The number of entries still to be processed when a total update
     *         is in progress.
     */
    long getLeftEntryCount()
    {
      return entryLeftCount;
    }

    /**
     * Initializes the import/export counters with the provider value.
     * @param total Total number of entries to be processed.
     * @throws DirectoryException if an error occurred.
     */
    private void initializeCounters(long total) throws DirectoryException
    {
      entryCount = total;
      entryLeftCount = total;

      if (initializeTask instanceof InitializeTask)
      {
        final InitializeTask task = (InitializeTask) initializeTask;
        task.setTotal(entryCount);
        task.setLeft(entryCount);
      }
      else if (initializeTask instanceof InitializeTargetTask)
      {
        final InitializeTargetTask task = (InitializeTargetTask) initializeTask;
        task.setTotal(entryCount);
        task.setLeft(entryCount);
      }
    }

    /**
     * Update the counters of the task for each entry processed during
     * an import or export.
     *
     * @param  entriesDone The number of entries that were processed
     *                     since the last time this method was called.
     *
     * @throws DirectoryException if an error occurred.
     */
    private void updateCounters(int entriesDone) throws DirectoryException
    {
      entryLeftCount -= entriesDone;

      if (initializeTask != null)
      {
        if (initializeTask instanceof InitializeTask)
        {
          ((InitializeTask)initializeTask).setLeft(entryLeftCount);
        }
        else if (initializeTask instanceof InitializeTargetTask)
        {
          ((InitializeTargetTask)initializeTask).setLeft(entryLeftCount);
        }
      }
    }

    @Override
    public String toString()
    {
      return "[Entry count=" + this.entryCount +
             ", Entry left count=" + this.entryLeftCount + "]";
    }

    /**
     * Gets the server id of the exporting server.
     * @return the server id of the exporting server.
     */
    public int getExportTarget()
    {
      return exportTarget;
    }

    /**
     * Gets the server id of the importing server.
     * @return the server id of the importing server.
     */
    public int getImportSource()
    {
      return importSource;
    }

    /**
     * Get the exception that occurred during the import/export.
     * @return the exception that occurred during the import/export.
     */
    public DirectoryException getException()
    {
      return exception;
    }

    /**
     * Set the exception that occurred during the import/export.
     * @param exception the exception that occurred during the import/export.
     */
    public void setException(DirectoryException exception)
    {
      this.exception = exception;
    }

    /**
     * Only sets the exception that occurred during the import/export if none
     * was already set on this object.
     *
     * @param exception the exception that occurred during the import/export.
     */
    public void setExceptionIfNoneSet(DirectoryException exception)
    {
      if (exception == null)
      {
        this.exception = exception;
      }
    }

    /**
     * Set the id of the EntryMsg acknowledged from a receiver (importer)server.
     * (updated via the listener thread)
     * @param serverId serverId of the acknowledger/receiver/importer server.
     * @param numAck   id of the message received.
     */
    private void setAckVal(int serverId, int numAck)
    {
      if (logger.isTraceEnabled())
      {
        logger.trace("[IE] setAckVal[" + serverId + "]=" + numAck);
      }

      ackVals.put(serverId, numAck);

      // Recompute the server with the minAck returned,means the slowest server.
      slowestServerId = serverId;
      int minMsgReceived = ackVals.get(serverId);
      for (Entry<Integer, Integer> mapEntry : ackVals.entrySet())
      {
        int nbMsgReceived = mapEntry.getValue();
        if (nbMsgReceived < minMsgReceived)
        {
          slowestServerId = mapEntry.getKey();
          minMsgReceived = nbMsgReceived;
        }
      }
    }

    /**
     * Returns the serverId of the server that acknowledged the smallest
     * EntryMsg id.
     * @return serverId of the server with latest acknowledge.
     *                  0 when no ack has been received yet.
     */
    public int getSlowestServer()
    {
      if (logger.isTraceEnabled())
      {
        logger.trace("[IE] getSlowestServer" + slowestServerId
            + " " + this.ackVals.get(slowestServerId));
      }

      return this.slowestServerId;
    }
  }

  /**
   * Verifies that the given string represents a valid source
   * from which this server can be initialized.
   *
   * @param targetString The string representing the source
   * @return The source as a integer value
   * @throws DirectoryException if the string is not valid
   */
  public int decodeTarget(String targetString) throws DirectoryException
  {
    if ("all".equalsIgnoreCase(targetString))
    {
      return RoutableMsg.ALL_SERVERS;
    }

    // So should be a serverID
    try
    {
      int target = Integer.decode(targetString);
      if (target >= 0)
      {
        // FIXME Could we check now that it is a know server in the domain ?
        // JNR: Yes please
      }
      return target;
    }
    catch (Exception e)
    {
      throw new DirectoryException(ResultCode.OTHER, ERR_INVALID_EXPORT_TARGET.get(), e);
    }
  }

  /**
   * Initializes a remote server from this server.
   * <p>
   * The {@code exportBackend(OutputStream)} will therefore be called
   * on this server, and the {@code importBackend(InputStream)}
   * will be called on the remote server.
   * <p>
   * The InputStream and OutputStream given as a parameter to those
   * methods will be connected through the replication protocol.
   *
   * @param target   The server-id of the server that should be initialized.
   *                 The target can be discovered using the
   *                 {@link #getReplicaInfos()} method.
   * @param initTask The task that triggers this initialization and that should
   *                 be updated with its progress.
   *
   * @throws DirectoryException If it was not possible to publish the
   *                            Initialization message to the Topology.
   */
  public void initializeRemote(int target, Task initTask) throws DirectoryException
  {
    initializeRemote(target, getServerId(), initTask, getInitWindow());
  }

  /**
   * Process the initialization of some other server or servers in the topology
   * specified by the target argument when this initialization specifying the
   * server that requests the initialization.
   *
   * @param serverToInitialize The target server that should be initialized.
   * @param serverRunningTheTask The server that initiated the export. It can
   * be the serverID of this server, or the serverID of a remote server.
   * @param initTask The task in this server that triggers this initialization
   * and that should be updated with its progress. Null when the export is done
   * following a request coming from a remote server (task is remote).
   * @param initWindow The value of the initialization window for flow control
   * between the importer and the exporter.
   *
   * @exception DirectoryException When an error occurs. No exception raised
   * means success.
   */
  protected void initializeRemote(int serverToInitialize,
      int serverRunningTheTask, Task initTask, int initWindow)
  throws DirectoryException
  {
    final ImportExportContext ieCtx = acquireIEContext(false);

    /*
    We manage the list of servers to initialize in order :
    - to test at the end that all expected servers have reconnected
    after their import and with the right genId
    - to update the task with the server(s) where this test failed
    */

    Map<Integer, DSInfo> replicaInfos = getReplicaInfos();
    if (serverToInitialize == RoutableMsg.ALL_SERVERS)
    {
      if (replicaInfos.isEmpty())
      {
        throw new DirectoryException(UNWILLING_TO_PERFORM,
            ERR_FULL_UPDATE_NO_REMOTES.get(getBaseDN(), getServerId()));
      }

      logger.info(NOTE_FULL_UPDATE_ENGAGED_FOR_REMOTE_START_ALL,
          countEntries(), getBaseDN(), getServerId());

      ieCtx.startList.addAll(replicaInfos.keySet());

      for (DSInfo dsi : replicaInfos.values())
      {
        if (dsi.getProtocolVersion()>= ProtocolVersion.REPLICATION_PROTOCOL_V4)
        {
          ieCtx.setAckVal(dsi.getDsId(), 0);
        }
      }
    }
    else
    {
      DSInfo dsi = getDsInfoOrNull(replicaInfos.values(), serverToInitialize);
      if (dsi == null)
      {
        throw new DirectoryException(UNWILLING_TO_PERFORM,
            ERR_FULL_UPDATE_MISSING_REMOTE.get(getBaseDN(), getServerId(), serverToInitialize));
      }

      logger.info(NOTE_FULL_UPDATE_ENGAGED_FOR_REMOTE_START, countEntries(),
          getBaseDN(), getServerId(), serverToInitialize);

      ieCtx.startList.add(serverToInitialize);
      ieCtx.setAckVal(dsi.getDsId(), 0);
    }

    DirectoryException exportRootException = null;

    // loop for the case where the exporter is the initiator
    int attempt = 0;
    boolean done = false;
    while (!done && ++attempt < 2) // attempt loop
    {
      try
      {
        ieCtx.exportTarget = serverToInitialize;
        if (initTask != null)
        {
          ieCtx.initializeTask = initTask;
        }
        ieCtx.initializeCounters(countEntries());
        ieCtx.msgCnt = 0;
        ieCtx.initNumLostConnections = broker.getNumLostConnections();
        ieCtx.initWindow = initWindow;

        // Send start message to the peer
        InitializeTargetMsg initTargetMsg = new InitializeTargetMsg(
            getBaseDN(), getServerId(), serverToInitialize,
            serverRunningTheTask, ieCtx.entryCount, initWindow);

        broker.publish(initTargetMsg);

        // Wait for all servers to be ok
        waitForRemoteStartOfInit(ieCtx);

        // Servers that left in the list are those for which we could not test
        // that they have been successfully initialized.
        if (!ieCtx.failureList.isEmpty())
        {
          throw new DirectoryException(
              ResultCode.OTHER,
              ERR_INIT_NO_SUCCESS_START_FROM_SERVERS.get(getBaseDN(), ieCtx.failureList));
        }

        exportBackend(new BufferedOutputStream(new ReplOutputStream(this)));

        // Notify the peer of the success
        broker.publish(
            new DoneMsg(getServerId(), initTargetMsg.getDestination()));
      }
      catch(DirectoryException exportException)
      {
        // Give priority to the first exception raised - stored in the context
        final DirectoryException ieEx = ieCtx.exception;
        exportRootException = ieEx != null ? ieEx : exportException;
      }

      if (logger.isTraceEnabled())
      {
        logger.trace("[IE] In " + broker.getReplicationMonitorInstanceName()
            + " export ends with connected=" + broker.isConnected()
            + " exportRootException=" + exportRootException);
      }

      if (exportRootException != null)
      {
        try
        {
          /*
          Handling the errors during export

          Note: we could have lost the connection and another thread
          the listener one) has already managed to reconnect.
          So we MUST rely on the test broker.isConnected()
          ONLY to do 'wait to be reconnected by another thread'
          (if not yet reconnected already).
          */
          if (!broker.isConnected())
          {
            // We are still disconnected, so we wait for the listener thread
            // to reconnect - wait 10s
            if (logger.isTraceEnabled())
            {
              logger.trace("[IE] Exporter wait for reconnection by the listener thread");
            }
            int att=0;
            while (!broker.shuttingDown()
                && !broker.isConnected()
                && ++att < 100)
            {
              try { Thread.sleep(100); }
              catch(Exception e){ /* do nothing */ }
            }
          }

          if (initTask != null
              && broker.isConnected()
              && serverToInitialize != RoutableMsg.ALL_SERVERS)
          {
            /*
            NewAttempt case : In the case where
            - it's not an InitializeAll
            - AND the previous export attempt failed
            - AND we are (now) connected
            - and we own the task and this task is not an InitializeAll
            Let's :
            - sleep to let time to the other peer to reconnect if needed
            - and launch another attempt
            */
            try { Thread.sleep(1000); }
            catch(Exception e){ /* do nothing */ }

            logger.info(NOTE_RESENDING_INIT_TARGET, exportRootException.getLocalizedMessage());
            continue;
          }

          broker.publish(new ErrorMsg(
              serverToInitialize, exportRootException.getMessageObject()));
        }
        catch(Exception e)
        {
          // Ignore the failure raised while proceeding the root failure
        }
      }

      // We are always done for this export ...
      // ... except in the NewAttempt case (see above)
      done = true;

    } // attempt loop

    // Wait for all servers to be ok, and build the failure list
    waitForRemoteEndOfInit(ieCtx);

    // Servers that left in the list are those for which we could not test
    // that they have been successfully initialized.
    if (!ieCtx.failureList.isEmpty() && exportRootException == null)
    {
      exportRootException = new DirectoryException(ResultCode.OTHER,
              ERR_INIT_NO_SUCCESS_END_FROM_SERVERS.get(getGenerationID(), ieCtx.failureList));
    }

    // Don't forget to release IEcontext acquired at beginning.
    releaseIEContext(); // FIXME should not this be in a finally?

    final String cause = exportRootException == null ? ""
        : exportRootException.getLocalizedMessage();
    if (serverToInitialize == RoutableMsg.ALL_SERVERS)
    {
      logger.info(NOTE_FULL_UPDATE_ENGAGED_FOR_REMOTE_END_ALL,
          getBaseDN(), getServerId(), cause);
    }
    else
    {
      logger.info(NOTE_FULL_UPDATE_ENGAGED_FOR_REMOTE_END,
          getBaseDN(), getServerId(), serverToInitialize, cause);
    }

    if (exportRootException != null)
    {
      throw exportRootException;
    }
  }

  private DSInfo getDsInfoOrNull(Collection<DSInfo> replicaInfos, int serverToInitialize)
  {
    for (DSInfo dsi : replicaInfos)
    {
      if (dsi.getDsId() == serverToInitialize
          && dsi.getProtocolVersion() >= ProtocolVersion.REPLICATION_PROTOCOL_V4)
      {
        return dsi;
      }
    }
    return null;
  }

  /**
   * For all remote servers in the start list:
   * - wait it has finished the import and present the expected generationID,
   * - build the failureList.
   */
  private void waitForRemoteStartOfInit(ImportExportContext ieCtx)
  {
    final Set<Integer> replicasWeAreWaitingFor = new HashSet<>(ieCtx.startList);

    if (logger.isTraceEnabled())
    {
      logger.trace("[IE] wait for start replicasWeAreWaitingFor=" + replicasWeAreWaitingFor);
    }

    int waitResultAttempt = 0;
    boolean done;
    do
    {
      done = true;
      for (DSInfo dsi : getReplicaInfos().values())
      {
        if (logger.isTraceEnabled())
        {
          logger.trace(
            "[IE] wait for start dsId " + dsi.getDsId()
            + " " + dsi.getStatus()
            + " " + dsi.getGenerationId()
            + " " + getGenerationID());
        }
        if (ieCtx.startList.contains(dsi.getDsId()))
        {
          if (dsi.getStatus() != ServerStatus.FULL_UPDATE_STATUS)
          {
            // this one is still not doing the Full Update ... retry later
            done = false;
            try { Thread.sleep(100);
            }
            catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
            waitResultAttempt++;
            break;
          }
          else
          {
            // this one is ok
            replicasWeAreWaitingFor.remove(dsi.getDsId());
          }
        }
      }
    }
    while (!done && waitResultAttempt < 1200 && !broker.shuttingDown());

    ieCtx.failureList.addAll(replicasWeAreWaitingFor);

    if (logger.isTraceEnabled())
    {
      logger.trace("[IE] wait for start ends with " + ieCtx.failureList);
    }
  }

  /**
   * For all remote servers in the start list:
   * - wait it has finished the import and present the expected generationID,
   * - build the failureList.
   */
  private void waitForRemoteEndOfInit(ImportExportContext ieCtx)
  {
    final Set<Integer> replicasWeAreWaitingFor = new HashSet<>(ieCtx.startList);

    if (logger.isTraceEnabled())
    {
      logger.trace("[IE] wait for end replicasWeAreWaitingFor=" + replicasWeAreWaitingFor);
    }

    /*
    In case some new servers appear during the init, we want them to be
    considered in the processing of sorting the successfully initialized
    and the others
    */
    replicasWeAreWaitingFor.addAll(getReplicaInfos().keySet());

    boolean done;
    do
    {
      done = true;
      int reconnectMaxDelayInSec = 10;
      int reconnectWait = 0;
      Iterator<Integer> it = replicasWeAreWaitingFor.iterator();
      while (it.hasNext())
      {
        int serverId = it.next();
        if (ieCtx.failureList.contains(serverId))
        {
          /* this server has already been in error during initialization don't wait for it */
          continue;
        }

        DSInfo dsInfo = getConnectedRemoteDS(serverId);
        if (dsInfo == null)
        {
          /*
          this server is disconnected
          may be for a long time if it crashed or had been stopped
          may be just the time to reconnect after import : should be short
          */
          if (++reconnectWait<reconnectMaxDelayInSec)
          {
            // let's still wait to give a chance to this server to reconnect
            done = false;
          }
          // Else we left enough time to the servers to reconnect
        }
        else
        {
          // this server is connected
          if (dsInfo.getStatus() == ServerStatus.FULL_UPDATE_STATUS)
          {
            // this one is still doing the Full Update ... retry later
            done = false;
            break;
          }

          if (dsInfo.getGenerationId() == getGenerationID())
          { // and with the expected generationId
            // We're done with this server
            it.remove();
          }
        }
      }

      // loop and wait
      if (!done)
      {
        try { Thread.sleep(1000); }
        catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        } // 1sec
      }
    }
    while (!done && !broker.shuttingDown()); // infinite wait

    ieCtx.failureList.addAll(replicasWeAreWaitingFor);

    if (logger.isTraceEnabled())
    {
      logger.trace("[IE] wait for end ends with " + ieCtx.failureList);
    }
  }

  /**
   * Get the ServerState maintained by the Concrete class.
   *
   * @return the ServerState maintained by the Concrete class.
   */
  public ServerState getServerState()
  {
    return state;
  }

  /**
   * Acquire and initialize the import/export context, verifying no other
   * import/export is in progress.
   */
  private ImportExportContext acquireIEContext(boolean importInProgress)
      throws DirectoryException
  {
    final ImportExportContext ieCtx = new ImportExportContext(importInProgress);
    if (!importExportContext.compareAndSet(null, ieCtx))
    {
      // Rejects 2 simultaneous exports
      LocalizableMessage message = ERR_SIMULTANEOUS_IMPORT_EXPORT_REJECTED.get();
      throw new DirectoryException(ResultCode.OTHER, message);
    }
    return ieCtx;
  }

  private void releaseIEContext()
  {
    importExportContext.set(null);
  }

  /**
   * Processes an error message received while an export is
   * on going, or an import will start.
   *
   * @param errorMsg The error message received.
   */
  private void processErrorMsg(ErrorMsg errorMsg, ImportExportContext ieCtx)
  {
    //Exporting must not be stopped on the first error, if we run initialize-all
    if (ieCtx != null && ieCtx.exportTarget != RoutableMsg.ALL_SERVERS)
    {
      // The ErrorMsg is received while we have started an initialization
      ieCtx.setExceptionIfNoneSet(new DirectoryException(
          ResultCode.OTHER, errorMsg.getDetails()));

      /*
       * This can happen :
       * - on the first InitReqMsg sent when source in not known for example
       * - on the next attempt when source crashed and did not reconnect
       *   even after the nextInitAttemptDelay
       * During the import, the ErrorMsg will be received by receiveEntryBytes
       */
      if (ieCtx.initializeTask instanceof InitializeTask)
      {
        // Update the task that initiated the import
        ((InitializeTask) ieCtx.initializeTask)
            .updateTaskCompletionState(ieCtx.getException());

        releaseIEContext();
      }
    }
  }

  /**
   * Receives bytes related to an entry in the context of an import to
   * initialize the domain (called by ReplLDIFInputStream).
   *
   * @return The bytes. Null when the Done or Err message has been received
   */
  protected byte[] receiveEntryBytes()
  {
    ReplicationMsg msg;
    while (true)
    {
      ImportExportContext ieCtx = importExportContext.get();
      try
      {
        // In the context of the total update, we don't want any automatic
        // re-connection done transparently by the broker because of a better
        // RS or because of a connection failure.
        // We want to be notified of topology change in order to track a
        // potential disconnection of the exporter.
        msg = broker.receive(false, false, true);

        if (logger.isTraceEnabled())
        {
          logger.trace("[IE] In "
              + broker.getReplicationMonitorInstanceName()
              + ", receiveEntryBytes " + msg);
        }

        if (msg == null)
        {
          if (broker.shuttingDown())
          {
            // The server is in the shutdown process
            return null;
          }
          else
          {
            // Handle connection issues
            ieCtx.setExceptionIfNoneSet(new DirectoryException(
                ResultCode.OTHER, ERR_INIT_RS_DISCONNECTION_DURING_IMPORT
                    .get(broker.getReplicationServer())));
            return null;
          }
        }

        // Check good ordering of msg received
        if (msg instanceof EntryMsg)
        {
          EntryMsg entryMsg = (EntryMsg)msg;
          byte[] entryBytes = entryMsg.getEntryBytes();
          ieCtx.updateCounters(countEntryLimits(entryBytes));

          if (ieCtx.exporterProtocolVersion >=
            ProtocolVersion.REPLICATION_PROTOCOL_V4)
          {
            // check the msgCnt of the msg received to check ordering
            if (++ieCtx.msgCnt != entryMsg.getMsgId())
            {
              ieCtx.setExceptionIfNoneSet(new DirectoryException(
                  ResultCode.OTHER, ERR_INIT_BAD_MSG_ID_SEQ_DURING_IMPORT.get(ieCtx.msgCnt, entryMsg.getMsgId())));
              return null;
            }

            // send the ack of flow control mgmt
            if ((ieCtx.msgCnt % (ieCtx.initWindow/2)) == 0)
            {
              final InitializeRcvAckMsg amsg = new InitializeRcvAckMsg(
                  getServerId(), entryMsg.getSenderID(), ieCtx.msgCnt);
              broker.publish(amsg, false);
              if (logger.isTraceEnabled())
              {
                logger.trace("[IE] In "
                    + broker.getReplicationMonitorInstanceName()
                    + ", publish InitializeRcvAckMsg" + amsg);
              }
            }
          }
          return entryBytes;
        }
        else if (msg instanceof DoneMsg)
        {
          /*
          This is the normal termination of the import
          No error is stored and the import is ended by returning null
          */
          return null;
        }
        else if (msg instanceof ErrorMsg)
        {
          /*
          This is an error termination during the import
          The error is stored and the import is ended by returning null
          */
          if (ieCtx.getException() == null)
          {
            ErrorMsg errMsg = (ErrorMsg)msg;
            if (errMsg.getCreationTime() > ieCtx.startTime)
            {
              ieCtx.setException(
                  new DirectoryException(ResultCode.OTHER,errMsg.getDetails()));
              return null;
            }
          }
        }
        else
        {
          // Other messages received during an import are trashed except
          // the topologyMsg.
          if (msg instanceof TopologyMsg
              && getConnectedRemoteDS(ieCtx.importSource) == null)
          {
            LocalizableMessage errMsg = ERR_INIT_EXPORTER_DISCONNECTION.get(
                getBaseDN(), getServerId(), ieCtx.importSource);
            ieCtx.setExceptionIfNoneSet(new DirectoryException(ResultCode.OTHER, errMsg));
            return null;
          }
        }
      }
      catch(Exception e)
      {
        ieCtx.setExceptionIfNoneSet(new DirectoryException(
            ResultCode.OTHER,
            ERR_INIT_IMPORT_FAILURE.get(e.getLocalizedMessage())));
      }
    }
  }

  /**
   * Count the number of entries in the provided byte[].
   * This is based on the hypothesis that the entries are separated
   * by a "\n\n" String.
   *
   * @param   entryBytes the set of bytes containing one or more entries.
   * @return  The number of entries in the provided byte[].
   */
  private int countEntryLimits(byte[] entryBytes)
  {
    return countEntryLimits(entryBytes, 0, entryBytes.length);
  }

  /**
   * Count the number of entries in the provided byte[].
   * This is based on the hypothesis that the entries are separated
   * by a "\n\n" String.
   *
   * @param   entryBytes the set of bytes containing one or more entries.
   * @return  The number of entries in the provided byte[].
   */
  private int countEntryLimits(byte[] entryBytes, int pos, int length)
  {
    int entryCount = 0;
    int count = 0;
    while (count<=length-2)
    {
      if (entryBytes[pos+count] == '\n' && entryBytes[pos+count+1] == '\n')
      {
        entryCount++;
        count++;
      }
      count++;
    }
    return entryCount;
  }

  /**
   * Exports an entry in LDIF format.
   *
   * @param lDIFEntry The entry to be exported in byte[] form.
   * @param pos       The starting Position in the array.
   * @param length    Number of array elements to be copied.
   *
   * @throws IOException when an error occurred.
   */
  void exportLDIFEntry(byte[] lDIFEntry, int pos, int length)
      throws IOException
  {
    if (logger.isTraceEnabled())
    {
      logger.trace("[IE] Entering exportLDIFEntry entry=" + Arrays.toString(lDIFEntry));
    }

    // build the message
    ImportExportContext ieCtx = importExportContext.get();
    EntryMsg entryMessage = new EntryMsg(
        getServerId(), ieCtx.getExportTarget(), lDIFEntry, pos, length,
        ++ieCtx.msgCnt);

    // Waiting the slowest loop
    while (!broker.shuttingDown())
    {
      /*
      If an error was raised - like receiving an ErrorMsg from a remote
      server that have been stored by the listener thread in the ieContext,
      we just abandon the export by throwing an exception.
      */
      if (ieCtx.getException() != null)
      {
        throw new IOException(ieCtx.getException().getMessage());
      }

      int slowestServerId = ieCtx.getSlowestServer();
      if (getConnectedRemoteDS(slowestServerId) == null)
      {
        ieCtx.setException(new DirectoryException(ResultCode.OTHER,
            ERR_INIT_HEARTBEAT_LOST_DURING_EXPORT.get(ieCtx.getSlowestServer())));

        throw new IOException("IOException with nested DirectoryException",
            ieCtx.getException());
      }

      int ourLastExportedCnt = ieCtx.msgCnt;
      int slowestCnt = ieCtx.ackVals.get(slowestServerId);

      if (logger.isTraceEnabled())
      {
        logger.trace("[IE] Entering exportLDIFEntry waiting " +
            " our=" + ourLastExportedCnt + " slowest=" + slowestCnt);
      }

      if (ourLastExportedCnt - slowestCnt > ieCtx.initWindow)
      {
        if (logger.isTraceEnabled())
        {
          logger.trace("[IE] Entering exportLDIFEntry waiting");
        }

        // our export is too far beyond the slowest importer - let's wait
        try { Thread.sleep(100); }
        catch(Exception e) { /* do nothing */ }

        // process any connection error
        if (broker.hasConnectionError()
          || broker.getNumLostConnections() != ieCtx.initNumLostConnections)
        {
          // publish failed - store the error in the ieContext ...
          DirectoryException de = new DirectoryException(ResultCode.OTHER,
              ERR_INIT_RS_DISCONNECTION_DURING_EXPORT.get(broker.getRsServerId()));
          ieCtx.setExceptionIfNoneSet(de);
          // .. and abandon the export by throwing an exception.
          throw new IOException(de.getMessage());
        }
      }
      else
      {
        if (logger.isTraceEnabled())
        {
          logger.trace("[IE] slowest got to us => stop waiting");
        }
        break;
      }
    } // Waiting the slowest loop

    if (logger.isTraceEnabled())
    {
      logger.trace("[IE] Entering exportLDIFEntry pub entry=" + Arrays.toString(lDIFEntry));
    }

    boolean sent = broker.publish(entryMessage, false);

    // process any publish error
    if (!sent
        || broker.hasConnectionError()
        || broker.getNumLostConnections() != ieCtx.initNumLostConnections)
    {
      // publish failed - store the error in the ieContext ...
      DirectoryException de = new DirectoryException(ResultCode.OTHER,
          ERR_INIT_RS_DISCONNECTION_DURING_EXPORT.get(broker.getRsServerId()));
      ieCtx.setExceptionIfNoneSet(de);
      // .. and abandon the export by throwing an exception.
      throw new IOException(de.getMessage());
    }

    // publish succeeded
    try
    {
      ieCtx.updateCounters(countEntryLimits(lDIFEntry, pos, length));
    }
    catch (DirectoryException de)
    {
      ieCtx.setExceptionIfNoneSet(de);
      // .. and abandon the export by throwing an exception.
      throw new IOException(de.getMessage());
    }
  }

  /**
   * Initializes asynchronously this domain from a remote source server.
   * Before returning from this call, for the provided task :
   * - the progressing counters are updated during the initialization using
   *   setTotal() and setLeft().
   * - the end of the initialization using updateTaskCompletionState().
   * <p>
   * When this method is called, a request for initialization is sent to the
   * remote source server requesting initialization.
   * <p>
   *
   * @param source   The server-id of the source from which to initialize.
   *                 The source can be discovered using the
   *                 {@link #getReplicaInfos()} method.
   *
   * @param initTask The task that launched the initialization
   *                 and should be updated of its progress.
   *
   * @throws DirectoryException If it was not possible to publish the
   *                            Initialization message to the Topology.
   *                            The task state is updated.
   */
  public void initializeFromRemote(int source, Task initTask)
  throws DirectoryException
  {
    if (logger.isTraceEnabled())
    {
      logger.trace("[IE] Entering initializeFromRemote for " + this);
    }

    LocalizableMessage errMsg = !broker.isConnected()
        ? ERR_INITIALIZATION_FAILED_NOCONN.get(getBaseDN())
        : null;

    /*
    We must not test here whether the remote source is connected to
    the topology by testing if it stands in the replicas list since.
    In the case of a re-attempt of initialization, the listener thread is
    running this method directly coming from initialize() method and did
    not processed any topology message in between the failure and the
    new attempt.
    */
    try
    {
      /*
      We must immediately acquire a context to store the task inside
      The context will be used when we (the listener thread) will receive
      the InitializeTargetMsg, process the import, and at the end
      update the task.
      */

      final ImportExportContext ieCtx = acquireIEContext(true);
      ieCtx.initializeTask = initTask;
      ieCtx.attemptCnt = 0;
      ieCtx.initReqMsgSent = new InitializeRequestMsg(
          getBaseDN(), getServerId(), source, getInitWindow());
      broker.publish(ieCtx.initReqMsgSent);

      /*
      The normal success processing is now to receive InitTargetMsg then
      entries from the remote server.
      The error cases are :
      - either local error immediately caught below
      - a remote error we will receive as an ErrorMsg
      */
    }
    catch(DirectoryException de)
    {
      errMsg = de.getMessageObject();
    }
    catch(Exception e)
    {
      // Should not happen
      errMsg = LocalizableMessage.raw(e.getLocalizedMessage());
      logger.error(errMsg);
    }

    // When error, update the task and raise the error to the caller
    if (errMsg != null)
    {
      // No need to call here updateTaskCompletionState - will be done
      // by the caller
      releaseIEContext();
      throw new DirectoryException(ResultCode.OTHER, errMsg);
    }
  }

  /**
   * Processes an InitializeTargetMsg received from a remote server
   * meaning processes an initialization from the entries expected to be
   * received now.
   *
   * @param initTargetMsgReceived The message received from the remote server.
   *
   * @param requesterServerId The serverId of the server that requested the
   *                          initialization meaning the server where the
   *                          task has initially been created (this server,
   *                          or the remote server).
   */
  private void initialize(InitializeTargetMsg initTargetMsgReceived, int requesterServerId)
  {
    if (logger.isTraceEnabled())
    {
      logger.trace("[IE] Entering initialize - domain=" + this);
    }

    InitializeTask initFromTask = null;
    int source = initTargetMsgReceived.getSenderID();
    ImportExportContext ieCtx = importExportContext.get();
    try
    {
      // Log starting
      logger.info(NOTE_FULL_UPDATE_ENGAGED_FROM_REMOTE_START, getBaseDN(),
          initTargetMsgReceived.getSenderID(), getServerId());

      // Go into full update status
      setNewStatus(StatusMachineEvent.TO_FULL_UPDATE_STATUS_EVENT);

      // Acquire an import context if no already done (and initialize).
      if (initTargetMsgReceived.getInitiatorID() != getServerId())
      {
        /*
        The initTargetMsgReceived is for an import initiated by the remote server.
        Test and set if no import already in progress
        */
        ieCtx = acquireIEContext(true);
      }

      // Initialize stuff
      ieCtx.importSource = source;
      ieCtx.initializeCounters(initTargetMsgReceived.getEntryCount());
      ieCtx.initWindow = initTargetMsgReceived.getInitWindow();
      ieCtx.exporterProtocolVersion = getProtocolVersion(source);
      initFromTask = (InitializeTask) ieCtx.initializeTask;

      // Launch the import
      importBackend(new ReplInputStream(this));
    }
    catch (DirectoryException e)
    {
      /*
      Store the exception raised. It will be considered if no other exception
      has been previously stored in  the context
      */
      ieCtx.setExceptionIfNoneSet(e);
    }
    finally
    {
      if (logger.isTraceEnabled())
      {
        logger.trace("[IE] Domain=" + this
          + " ends import with exception=" + ieCtx.getException()
          + " connected=" + broker.isConnected());
      }

      /*
      It is necessary to restart (reconnect to RS) for different reasons
      - when everything went well, reconnect in order to exchange
      new state, new generation ID
      - when we have connection failure, reconnect to retry a new import
      right here, right now
      we never want retryOnFailure if we fails reconnecting in the restart.
      */
      broker.reStart(false);

      if (ieCtx.getException() != null
          && broker.isConnected()
          && initFromTask != null
          && ++ieCtx.attemptCnt < 2)
      {
          /* Worth a new attempt since initFromTask is in this server, connection is ok */
          try
          {
            /*
            Wait for the exporter to stabilize - eventually reconnect as
            well if it was connected to the same RS than the one we lost ...
            */
            Thread.sleep(1000);

            /* Restart the whole import protocol exchange by sending again the request */
            logger.info(NOTE_RESENDING_INIT_FROM_REMOTE_REQUEST,
                ieCtx.getException().getLocalizedMessage());

            broker.publish(ieCtx.initReqMsgSent);

            ieCtx.initializeCounters(0);
            ieCtx.exception = null;
            ieCtx.msgCnt = 0;

            // Processing of the received initTargetMsgReceived is done
            // let's wait for the next one
            return;
          }
          catch(Exception e)
          {
            /*
            An error occurs when sending a new request for a new import.
            This error is not stored, preferring to keep the initial one.
            */
            logger.error(ERR_SENDING_NEW_ATTEMPT_INIT_REQUEST,
                e.getLocalizedMessage(), ieCtx.getException().getLocalizedMessage());
          }
      }

      // ===================
      // No new attempt case

      if (logger.isTraceEnabled())
      {
        logger.trace("[IE] Domain=" + this
          + " ends initialization with exception=" + ieCtx.getException()
          + " connected=" + broker.isConnected()
          + " task=" + initFromTask
          + " attempt=" + ieCtx.attemptCnt);
      }

      try
      {
        if (broker.isConnected() && ieCtx.getException() != null)
        {
          // Let's notify the exporter
          ErrorMsg errorMsg = new ErrorMsg(requesterServerId,
              ieCtx.getException().getMessageObject());
          broker.publish(errorMsg);
        }
        /*
        Update the task that initiated the import must be the last thing.
        Particularly, broker.restart() after import success must be done
        before some other operations/tasks to be launched,
        like resetting the generation ID.
        */
        if (initFromTask != null)
        {
          initFromTask.updateTaskCompletionState(ieCtx.getException());
        }
      }
      finally
      {
        String errorMsg = ieCtx.getException() != null ? ieCtx.getException().getLocalizedMessage() : "";
        logger.info(NOTE_FULL_UPDATE_ENGAGED_FROM_REMOTE_END,
            getBaseDN(), initTargetMsgReceived.getSenderID(), getServerId(), errorMsg);
        releaseIEContext();
      } // finally
    } // finally
  }

  /**
   * Return the protocol version of the DS related to the provided serverId.
   * Returns -1 when the protocol version is not known.
   * @param dsServerId The provided serverId.
   * @return The protocol version.
   */
  private short getProtocolVersion(int dsServerId)
  {
    final DSInfo dsInfo = getReplicaInfos().get(dsServerId);
    if (dsInfo != null)
    {
      return dsInfo.getProtocolVersion();
    }
    return -1;
  }

  /**
   * Sets the status to a new value depending of the passed status machine
   * event.
   * @param event The event that may make the status be changed
   */
  protected void signalNewStatus(StatusMachineEvent event)
  {
    setNewStatus(event);
    broker.signalStatusChange(status);
  }

  private void setNewStatus(StatusMachineEvent event)
  {
    ServerStatus newStatus = StatusMachine.computeNewStatus(status, event);
    if (newStatus == ServerStatus.INVALID_STATUS)
    {
      logger.error(ERR_DS_CANNOT_CHANGE_STATUS, getBaseDN(), getServerId(), status, event);
      return;
    }

    if (newStatus != status)
    {
      // Reset status date
      lastStatusChangeDate = new Date();
      // Reset monitoring counters if reconnection
      if (newStatus == ServerStatus.NOT_CONNECTED_STATUS)
      {
        resetMonitoringCounters();
      }

      status = newStatus;
      if (logger.isTraceEnabled())
      {
        logger.trace("Replication domain " + getBaseDN()
            + " new status is: " + status);
      }

      // Perform whatever actions are needed to apply properties for being
      // compliant with new status
      updateDomainForNewStatus();
    }
  }

  /**
   * Returns a boolean indicating if an import or export is currently
   * processed.
   *
   * @return The status
   */
  public boolean ieRunning()
  {
    return importExportContext.get() != null;
  }

  /**
   * Check the value of the Replication Servers generation ID.
   *
   * @param generationID        The expected value of the generation ID.
   *
   * @throws DirectoryException When the generation ID of the Replication
   *                            Servers is not the expected value.
   */
  private void checkGenerationID(long generationID) throws DirectoryException
  {
    boolean allSet = true;

    for (int i = 0; i< 50; i++)
    {
      allSet = true;
      for (RSInfo rsInfo : getRsInfos())
      {
        // the 'empty' RSes (generationId==-1) are considered as good citizens
        if (rsInfo.getGenerationId() != -1L &&
            rsInfo.getGenerationId() != generationID)
        {
          try
          {
            Thread.sleep(i*100);
          } catch (InterruptedException e)
          {
            Thread.currentThread().interrupt();
          }
          allSet = false;
          break;
        }
      }
      if (allSet)
      {
        break;
      }
    }
    if (!allSet)
    {
      LocalizableMessage message = ERR_RESET_GENERATION_ID_FAILED.get(getBaseDN());
      throw new DirectoryException(ResultCode.OTHER, message);
    }
  }

  /**
   * Reset the Replication Log.
   * Calling this method will remove all the Replication information that
   * was kept on all the Replication Servers currently connected in the
   * topology.
   *
   * @throws DirectoryException If this ReplicationDomain is not currently
   *                           connected to a Replication Server or it
   *                           was not possible to contact it.
   */
  void resetReplicationLog() throws DirectoryException
  {
    // Reset the Generation ID to -1 to clean the ReplicationServers.
    resetGenerationId(-1L);

    // check that at least one ReplicationServer did change its generation-id
    checkGenerationID(-1L);

    // Reconnect to the Replication Server so that it adopts our GenerationID.
    restartService();

    // wait for the domain to reconnect.
    int count = 0;
    while (!isConnected() && count < 10)
    {
      try
      {
        Thread.sleep(100);
      } catch (InterruptedException e)
      {
        Thread.currentThread().interrupt();
      }
    }

    resetGenerationId(getGenerationID());

    // check that at least one ReplicationServer did change its generation-id
    checkGenerationID(getGenerationID());
  }

  /**
   * Reset the generationId of this domain in the whole topology.
   * A message is sent to the Replication Servers for them to reset
   * their change dbs.
   *
   * @param generationIdNewValue  The new value of the generation Id.
   * @throws DirectoryException   When an error occurs
   */
  public void resetGenerationId(Long generationIdNewValue)
      throws DirectoryException
  {
    if (logger.isTraceEnabled())
    {
      logger.trace("Server id " + getServerId() + " and domain "
          + getBaseDN() + " resetGenerationId " + generationIdNewValue);
    }

    ResetGenerationIdMsg genIdMessage =
        new ResetGenerationIdMsg(getGenId(generationIdNewValue));

    if (!isConnected())
    {
      LocalizableMessage message = ERR_RESET_GENERATION_CONN_ERR_ID.get(getBaseDN(),
          getServerId(), genIdMessage.getGenerationId());
      throw new DirectoryException(ResultCode.OTHER, message);
    }
    broker.publish(genIdMessage);

    // check that at least one ReplicationServer did change its generation-id
    checkGenerationID(getGenId(generationIdNewValue));
  }

  private long getGenId(Long generationIdNewValue)
  {
    if (generationIdNewValue != null)
    {
      return generationIdNewValue;
    }
    return getGenerationID();
  }


  /*
   ******** End of The total Update code *********
   */

  /*
   ******* Start of Monitoring Code **********
   */

  /**
   * Get the maximum receive window size.
   *
   * @return The maximum receive window size.
   */
  int getMaxRcvWindow()
  {
    if (broker != null)
    {
      return broker.getMaxRcvWindow();
    }
    return 0;
  }

  /**
   * Get the current receive window size.
   *
   * @return The current receive window size.
   */
  int getCurrentRcvWindow()
  {
    if (broker != null)
    {
      return broker.getCurrentRcvWindow();
    }
    return 0;
  }

  /**
   * Get the maximum send window size.
   *
   * @return The maximum send window size.
   */
  int getMaxSendWindow()
  {
    if (broker != null)
    {
      return broker.getMaxSendWindow();
    }
    return 0;
  }

  /**
   * Get the current send window size.
   *
   * @return The current send window size.
   */
  int getCurrentSendWindow()
  {
    if (broker != null)
    {
      return broker.getCurrentSendWindow();
    }
    return 0;
  }

  /**
   * Get the number of times the replication connection was lost.
   * @return The number of times the replication connection was lost.
   */
  int getNumLostConnections()
  {
    if (broker != null)
    {
      return broker.getNumLostConnections();
    }
    return 0;
  }

  /**
   * Determine whether the connection to the replication server is encrypted.
   * @return true if the connection is encrypted, false otherwise.
   */
  boolean isSessionEncrypted()
  {
    return broker != null && broker.isSessionEncrypted();
  }

  /**
   * Check if the domain is connected to a ReplicationServer.
   *
   * @return true if the server is connected, false if not.
   */
  public boolean isConnected()
  {
    return broker != null && broker.isConnected();
  }

  /**
   * Check if the domain has a connection error.
   * A Connection error happens when the broker could not be created
   * or when the broker could not find any ReplicationServer to connect to.
   *
   * @return true if the domain has a connection error.
   */
  public boolean hasConnectionError()
  {
    return broker == null || broker.hasConnectionError();
  }

  /**
   * Get the name of the replicationServer to which this domain is currently
   * connected.
   *
   * @return the name of the replicationServer to which this domain
   *         is currently connected.
   */
  public HostPort getReplicationServer()
  {
    if (broker != null)
    {
      return broker.getReplicationServer();
    }
    return ReplicationBroker.NO_CONNECTED_SERVER;
  }

  /**
   * Gets the number of updates sent in assured safe read mode.
   * @return The number of updates sent in assured safe read mode.
   */
  public int getAssuredSrSentUpdates()
  {
    return assuredSrSentUpdates.get();
  }

  /**
   * Gets the number of updates sent in assured safe read mode that have been
   * acknowledged without errors.
   * @return The number of updates sent in assured safe read mode that have been
   * acknowledged without errors.
   */
  public int getAssuredSrAcknowledgedUpdates()
  {
    return assuredSrAcknowledgedUpdates.get();
  }

  /**
   * Gets the number of updates sent in assured safe read mode that have not
   * been acknowledged.
   * @return The number of updates sent in assured safe read mode that have not
   * been acknowledged.
   */
  public int getAssuredSrNotAcknowledgedUpdates()
  {
    return assuredSrNotAcknowledgedUpdates.get();
  }

  /**
   * Gets the number of updates sent in assured safe read mode that have not
   * been acknowledged due to timeout error.
   * @return The number of updates sent in assured safe read mode that have not
   * been acknowledged due to timeout error.
   */
  public int getAssuredSrTimeoutUpdates()
  {
    return assuredSrTimeoutUpdates.get();
  }

  /**
   * Gets the number of updates sent in assured safe read mode that have not
   * been acknowledged due to wrong status error.
   * @return The number of updates sent in assured safe read mode that have not
   * been acknowledged due to wrong status error.
   */
  public int getAssuredSrWrongStatusUpdates()
  {
    return assuredSrWrongStatusUpdates.get();
  }

  /**
   * Gets the number of updates sent in assured safe read mode that have not
   * been acknowledged due to replay error.
   * @return The number of updates sent in assured safe read mode that have not
   * been acknowledged due to replay error.
   */
  public int getAssuredSrReplayErrorUpdates()
  {
    return assuredSrReplayErrorUpdates.get();
  }

  /**
   * Gets the number of updates sent in assured safe read mode that have not
   * been acknowledged per server.
   * @return A copy of the map that contains the number of updates sent in
   * assured safe read mode that have not been acknowledged per server.
   */
  public Map<Integer, Integer> getAssuredSrServerNotAcknowledgedUpdates()
  {
    synchronized(assuredSrServerNotAcknowledgedUpdates)
    {
      return new HashMap<>(assuredSrServerNotAcknowledgedUpdates);
    }
  }

  /**
   * Gets the number of updates received in assured safe read mode request.
   * @return The number of updates received in assured safe read mode request.
   */
  public int getAssuredSrReceivedUpdates()
  {
    return assuredSrReceivedUpdates.get();
  }

  /**
   * Gets the number of updates received in assured safe read mode that we acked
   * without error (no replay error).
   * @return The number of updates received in assured safe read mode that we
   * acked without error (no replay error).
   */
  public int getAssuredSrReceivedUpdatesAcked()
  {
    return this.assuredSrReceivedUpdatesAcked.get();
  }

  /**
   * Gets the number of updates received in assured safe read mode that we did
   * not ack due to error (replay error).
   * @return The number of updates received in assured safe read mode that we
   * did not ack due to error (replay error).
   */
  public int getAssuredSrReceivedUpdatesNotAcked()
  {
    return this.assuredSrReceivedUpdatesNotAcked.get();
  }

  /**
   * Gets the number of updates sent in assured safe data mode.
   * @return The number of updates sent in assured safe data mode.
   */
  public int getAssuredSdSentUpdates()
  {
    return assuredSdSentUpdates.get();
  }

  /**
   * Gets the number of updates sent in assured safe data mode that have been
   * acknowledged without errors.
   * @return The number of updates sent in assured safe data mode that have been
   * acknowledged without errors.
   */
  public int getAssuredSdAcknowledgedUpdates()
  {
    return assuredSdAcknowledgedUpdates.get();
  }

  /**
   * Gets the number of updates sent in assured safe data mode that have not
   * been acknowledged due to timeout error.
   * @return The number of updates sent in assured safe data mode that have not
   * been acknowledged due to timeout error.
   */
  public int getAssuredSdTimeoutUpdates()
  {
    return assuredSdTimeoutUpdates.get();
  }

  /**
   * Gets the number of updates sent in assured safe data mode that have not
   * been acknowledged due to timeout error per server.
   * @return A copy of the map that contains the number of updates sent in
   * assured safe data mode that have not been acknowledged due to timeout
   * error per server.
   */
  public Map<Integer, Integer> getAssuredSdServerTimeoutUpdates()
  {
    synchronized(assuredSdServerTimeoutUpdates)
    {
      return new HashMap<>(assuredSdServerTimeoutUpdates);
    }
  }

  /**
   * Gets the date of the last status change.
   * @return The date of the last status change.
   */
  public Date getLastStatusChangeDate()
  {
    return lastStatusChangeDate;
  }

  /** Resets the values of the monitoring counters. */
  private void resetMonitoringCounters()
  {
    numProcessedUpdates = new AtomicInteger(0);
    numRcvdUpdates = new AtomicInteger(0);
    numSentUpdates = new AtomicInteger(0);

    assuredSrSentUpdates = new AtomicInteger(0);
    assuredSrAcknowledgedUpdates = new AtomicInteger(0);
    assuredSrNotAcknowledgedUpdates = new AtomicInteger(0);
    assuredSrTimeoutUpdates = new AtomicInteger(0);
    assuredSrWrongStatusUpdates = new AtomicInteger(0);
    assuredSrReplayErrorUpdates = new AtomicInteger(0);
    synchronized (assuredSrServerNotAcknowledgedUpdates)
    {
      assuredSrServerNotAcknowledgedUpdates.clear();
    }
    assuredSrReceivedUpdates = new AtomicInteger(0);
    assuredSrReceivedUpdatesAcked = new AtomicInteger(0);
    assuredSrReceivedUpdatesNotAcked = new AtomicInteger(0);
    assuredSdSentUpdates = new AtomicInteger(0);
    assuredSdAcknowledgedUpdates = new AtomicInteger(0);
    assuredSdTimeoutUpdates = new AtomicInteger(0);
    synchronized (assuredSdServerTimeoutUpdates)
    {
      assuredSdServerTimeoutUpdates.clear();
    }
  }

  /*
   ********** End of Monitoring Code **************
   */

  /**
   * Start the publish mechanism of the Replication Service. After this method
   * has been called, the publish service can be used by calling the
   * {@link #publish(UpdateMsg)} method.
   *
   * @throws ConfigException
   *           If the DirectoryServer configuration was incorrect.
   */
  public void startPublishService() throws ConfigException
  {
    synchronized (sessionLock)
    {
      if (broker == null)
      {
        // create the broker object used to publish and receive changes
        broker = new ReplicationBroker(
            this, state, config, new ReplSessionSecurity());
        broker.start();
      }
    }
  }

  /**
   * Starts the receiver side of the Replication Service.
   * <p>
   * After this method has been called, the Replication Service will start
   * calling the {@link #processUpdate(UpdateMsg)}.
   * <p>
   * This method must be called once and must be called after the
   * {@link #startPublishService()}.
   */
  public void startListenService()
  {
    synchronized (sessionLock)
    {
      if (listenerThread != null)
      {
        return;
      }

      final String threadName = "Replica DS(" + getServerId() + ") listener for domain \"" + getBaseDN() + "\"";

      listenerThread = new DirectoryThread(new Runnable()
      {
        @Override
        public void run()
        {
          if (logger.isTraceEnabled())
          {
            logger.trace("Replication Listener thread starting.");
          }

          // Loop processing any incoming update messages.
          while (!listenerThread.isShutdownInitiated())
          {
            final UpdateMsg updateMsg = receive();
            if (updateMsg == null)
            {
              // The server is shutting down.
              listenerThread.initiateShutdown();
            }
            else if (processUpdate(updateMsg)
                && updateMsg.contributesToDomainState())
            {
              /*
               * Warning: in synchronous mode, no way to tell the replay of an
               * update went wrong Just put null in processUpdateDone so that if
               * assured replication is used the ack is sent without error at
               * replay flag.
               */
              processUpdateDone(updateMsg, null);
              state.update(updateMsg.getCSN());
            }
          }

          if (logger.isTraceEnabled())
          {
            logger.trace("Replication Listener thread stopping.");
          }
        }
      }, threadName);

      listenerThread.start();
    }
  }

  /**
   * Temporarily disable the Replication Service.
   * The Replication Service can be enabled again using
   * {@link #enableService()}.
   * <p>
   * It can be useful to disable the Replication Service when the
   * repository where the replicated information is stored becomes
   * temporarily unavailable and replicated updates can therefore not
   * be replayed during a while. This method is not MT safe.
   */
  public void disableService()
  {
    synchronized (sessionLock)
    {
      /*
       * Stop the broker first in order to prevent the listener from reconnecting - see OPENDJ-457.
       */
      if (broker != null)
      {
        broker.stop();
      }
      try {
        exportThreadPool.shutdown();
        boolean timedOut = exportThreadPool.awaitTermination(100, TimeUnit.SECONDS);
        logger.info(LocalizableMessage.raw("export pool termination timed out: " + timedOut));
      } catch (InterruptedException e) {
        // Give up waiting.
      }

      // Stop the listener thread
      if (listenerThread != null)
      {
        listenerThread.initiateShutdown();
        try
        {
          listenerThread.join();
        }
        catch (InterruptedException e)
        {
          // Give up waiting.
        }
        listenerThread = null;
      }
    }
  }

  /**
   * Returns {@code true} if the listener thread is shutting down or has
   * shutdown.
   *
   * @return {@code true} if the listener thread is shutting down or has
   *         shutdown.
   */
  protected final boolean isListenerShuttingDown()
  {
    final DirectoryThread tmp = listenerThread;
    return tmp == null || tmp.isShutdownInitiated();
  }

  /**
   * Restart the Replication service after a {@link #disableService()}.
   * <p>
   * The Replication Service will restart from the point indicated by the
   * {@link ServerState} that was given as a parameter to the
   * {@link #startPublishService()} at startup time.
   * <p>
   * If some data have changed in the repository during the period of time when
   * the Replication Service was disabled, this {@link ServerState} should
   * therefore be updated by the Replication Domain subclass before calling this
   * method. This method is not MT safe.
   */
  public void enableService()
  {
    synchronized (sessionLock)
    {
      broker.start();
      startListenService();
    }
  }

  /**
   * Change some ReplicationDomain parameters.
   *
   * @param config
   *          The new configuration that this domain should now use.
   */
  protected void changeConfig(ReplicationDomainCfg config)
  {
    if (broker != null && broker.changeConfig(config))
    {
      restartService();
    }
  }

  /**
   * Applies a configuration change to the attributes which should be included
   * in the ECL.
   *
   * @param includeAttributes
   *          attributes to be included with all change records.
   * @param includeAttributesForDeletes
   *          additional attributes to be included with delete change records.
   */
  public void changeConfig(Set<String> includeAttributes,
      Set<String> includeAttributesForDeletes)
  {
    final boolean attrsModified = setEclIncludes(
        getServerId(), includeAttributes, includeAttributesForDeletes);
    if (attrsModified && broker != null)
    {
      restartService();
    }
  }

  private void restartService()
  {
    disableService();
    enableService();
  }

  /**
   * This method should trigger an export of the replicated data.
   * to the provided outputStream.
   * When finished the outputStream should be flushed and closed.
   *
   * @param output               The OutputStream where the export should
   *                             be produced.
   * @throws DirectoryException  When needed.
   */
  protected abstract void exportBackend(OutputStream output)
           throws DirectoryException;

  /**
   * This method should trigger an import of the replicated data.
   *
   * @param input                The InputStream from which
   *                             the import should be reading entries.
   *
   * @throws DirectoryException  When needed.
   */
  protected abstract void importBackend(InputStream input)
           throws DirectoryException;

  /**
   * This method should return the total number of objects in the
   * replicated domain.
   * This count will be used for reporting.
   *
   * @throws DirectoryException when needed.
   *
   * @return The number of objects in the replication domain.
   */
  public abstract long countEntries() throws DirectoryException;

  /**
   * This method should handle the processing of {@link UpdateMsg} receive from
   * remote replication entities.
   * <p>
   * This method will be called by a single thread and should therefore should
   * not be blocking.
   *
   * @param updateMsg
   *          The {@link UpdateMsg} that was received.
   * @return A boolean indicating if the processing is completed at return time.
   *         If <code> true </code> is returned, no further processing is
   *         necessary. If <code> false </code> is returned, the subclass should
   *         call the method {@link #processUpdateDone(UpdateMsg, String)} and
   *         update the ServerState When this processing is complete.
   */
  public abstract boolean processUpdate(UpdateMsg updateMsg);

  /**
   * This method must be called after each call to
   * {@link #processUpdate(UpdateMsg)} when the processing of the
   * update is completed.
   * <p>
   * It is useful for implementation needing to process the update in an
   * asynchronous way or using several threads, but must be called even by
   * implementation doing it in a synchronous, single-threaded way.
   *
   * @param msg
   *          The UpdateMsg whose processing was completed.
   * @param replayErrorMsg
   *          if not null, this means an error occurred during the replay of
   *          this update, and this is the matching human readable message
   *          describing the problem.
   */
  protected void processUpdateDone(UpdateMsg msg, String replayErrorMsg)
  {
    broker.updateWindowAfterReplay();

    /*
    Send an ack if it was requested and the group id is the same of the RS
    one. Only Safe Read mode makes sense in DS for returning an ack.
    */
    // Assured feature is supported starting from replication protocol V2
    if (msg.isAssured()
      && broker.getProtocolVersion() >= ProtocolVersion.REPLICATION_PROTOCOL_V2)
    {
      if (msg.getAssuredMode() == AssuredMode.SAFE_READ_MODE)
      {
        if (broker.getRsGroupId() == getGroupId())
        {
          // Send the ack
          AckMsg ackMsg = new AckMsg(msg.getCSN());
          if (replayErrorMsg != null)
          {
            // Mark the error in the ack
            //   -> replay error occurred
            ackMsg.setHasReplayError(true);
            //   -> replay error occurred in our server
            ackMsg.setFailedServers(newArrayList(getServerId()));
          }
          broker.publish(ackMsg);
          if (replayErrorMsg != null)
          {
            assuredSrReceivedUpdatesNotAcked.incrementAndGet();
          }
          else
          {
            assuredSrReceivedUpdatesAcked.incrementAndGet();
          }
        }
      }
      else if (getAssuredMode() != AssuredMode.SAFE_DATA_MODE)
      {
        logger.error(ERR_DS_UNKNOWN_ASSURED_MODE, getServerId(), msg.getAssuredMode(), getBaseDN(), msg);
      }
        // Nothing to do in Assured safe data mode, only RS ack updates.
    }

    incProcessedUpdates();
  }

  /**
   * Prepare a message if it is to be sent in assured mode.
   * If the assured mode is enabled, this method should be called before
   * publish(UpdateMsg msg) method. This will configure the update accordingly
   * before it is sent and will prepare the mechanism that will block until the
   * matching ack is received. To wait for the ack after publish call, use
   * the waitForAckIfAssuredEnabled() method.
   * The expected typical usage in a service inheriting from this class is
   * the following sequence:
   * UpdateMsg msg = xxx;
   * prepareWaitForAckIfAssuredEnabled(msg);
   * publish(msg);
   * waitForAckIfAssuredEnabled(msg);
   *
   * Note: prepareWaitForAckIfAssuredEnabled and waitForAckIfAssuredEnabled have
   * no effect if assured replication is disabled.
   * Note: this mechanism should not be used if using publish(byte[] msg)
   * version as usage of these methods is already hidden inside.
   *
   * @param msg The update message to be sent soon.
   */
  protected void prepareWaitForAckIfAssuredEnabled(UpdateMsg msg)
  {
    /*
     * If assured configured, set message accordingly to request an ack in the
     * right assured mode.
     * No ack requested for a RS with a different group id.
     * Assured replication supported for the same locality,
     * i.e: a topology working in the same geographical location).
     * If we are connected to a RS which is not in our locality,
     * no need to ask for an ack.
     */
    if (needsAck())
    {
      msg.setAssured(true);
      msg.setAssuredMode(getAssuredMode());
      if (getAssuredMode() == AssuredMode.SAFE_DATA_MODE)
      {
        msg.setSafeDataLevel(getAssuredSdLevel());
      }

      // Add the assured message to the list of update that are waiting for acks
      waitingAckMsgs.put(msg.getCSN(), msg);
    }
  }

  private boolean needsAck()
  {
    return isAssured() && broker.getRsGroupId() == getGroupId();
  }

  /**
   * Wait for the processing of an assured message after it has been sent, if
   * assured replication is configured, otherwise, do nothing.
   * The prepareWaitForAckIfAssuredEnabled method should have been called
   * before, see its comment for the full picture.
   *
   * @param msg The UpdateMsg for which we are waiting for an ack.
   * @throws TimeoutException When the configured timeout occurs waiting for the
   * ack.
   */
  protected void waitForAckIfAssuredEnabled(UpdateMsg msg)
    throws TimeoutException
  {
    if (needsAck())
    {
      // Increment assured replication monitoring counters
      switch (getAssuredMode())
      {
        case SAFE_READ_MODE:
          assuredSrSentUpdates.incrementAndGet();
          break;
        case SAFE_DATA_MODE:
          assuredSdSentUpdates.incrementAndGet();
          break;
        default:
        // Should not happen
      }
    } else
    {
      // Not assured or bad group id, return immediately
      return;
    }

    // Wait for the ack to be received, timing out if necessary
    long startTime = System.currentTimeMillis();
    synchronized (msg)
    {
      CSN csn = msg.getCSN();
      while (waitingAckMsgs.containsKey(csn))
      {
        try
        {
          /*
          WARNING: this timeout may be difficult to optimize: too low, it
          may use too much CPU, too high, it may penalize performance...
          */
          msg.wait(10);
        } catch (InterruptedException e)
        {
          if (logger.isTraceEnabled())
          {
            logger.trace("waitForAck method interrupted for replication " +
              "baseDN: " + getBaseDN());
          }
          break;
        }
        // Timeout ?
        if (System.currentTimeMillis() - startTime >= getAssuredTimeout())
        {
          /*
          Timeout occurred, be sure that ack is not being received and if so,
          remove the update from the wait list, log the timeout error and
          also update assured monitoring counters
          */
          final UpdateMsg update = waitingAckMsgs.remove(csn);
          if (update == null)
          {
            // Ack received just before timeout limit: we can exit
            break;
          }

          // No luck, this is a real timeout
          // Increment assured replication monitoring counters
          switch (msg.getAssuredMode())
          {
          case SAFE_READ_MODE:
            assuredSrNotAcknowledgedUpdates.incrementAndGet();
            assuredSrTimeoutUpdates.incrementAndGet();
            // Increment number of errors for our RS
            updateAssuredErrorsByServer(assuredSrServerNotAcknowledgedUpdates,
                broker.getRsServerId());
            break;
          case SAFE_DATA_MODE:
            assuredSdTimeoutUpdates.incrementAndGet();
            // Increment number of errors for our RS
            updateAssuredErrorsByServer(assuredSdServerTimeoutUpdates,
                broker.getRsServerId());
            break;
          default:
            // Should not happen
          }

          throw new TimeoutException("No ack received for message csn: " + csn
              + " and replication domain: " + getBaseDN() + " after "
              + getAssuredTimeout() + " ms.");
        }
      }
    }
  }

  /**
   * Publish an {@link UpdateMsg} to the Replication Service.
   * <p>
   * The Replication Service will handle the delivery of this {@link UpdateMsg}
   * to all the participants of this Replication Domain. These members will be
   * receive this {@link UpdateMsg} through a call of the
   * {@link #processUpdate(UpdateMsg)} message.
   *
   * @param msg The UpdateMsg that should be published.
   */
  public void publish(UpdateMsg msg)
  {
    broker.publish(msg);
    if (msg.contributesToDomainState())
    {
      state.update(msg.getCSN());
    }
    numSentUpdates.incrementAndGet();
  }

  /**
   * Publishes a replica offline message if all pending changes for current
   * replica have been sent out.
   */
  public void publishReplicaOfflineMsg()
  {
    // Here to be overridden
  }

  /**
   * This method should return the generationID to use for this
   * ReplicationDomain.
   * This method can be called at any time after the ReplicationDomain
   * has been started.
   *
   * @return The GenerationID.
   */
  public long getGenerationID()
  {
    return generationId;
  }

  /**
   * Sets the generationId for this replication domain.
   *
   * @param generationId
   *          the generationId to set
   */
  public void setGenerationID(long generationId)
  {
    this.generationId = generationId;
  }

  /**
   * Subclasses should use this method to add additional monitoring information
   * in the ReplicationDomain.
   *
   * @param monitorData where to additional monitoring attributes
   */
  public void addAdditionalMonitoring(MonitorData monitorData)
  {
  }

  /**
   * Returns the Import/Export context associated to this ReplicationDomain.
   *
   * @return the Import/Export context associated to this ReplicationDomain
   */
  protected ImportExportContext getImportExportContext()
  {
    return importExportContext.get();
  }

  /**
   * Returns the local address of this replication domain, or the empty string
   * if it is not yet connected.
   *
   * @return The local address.
   */
  HostPort getLocalUrl()
  {
    final ReplicationBroker tmp = broker;
    return tmp != null ? tmp.getLocalUrl() : ReplicationBroker.NO_CONNECTED_SERVER;
  }

  /**
   * Set the attributes configured on a server to be included in the ECL.
   *
   * @param serverId
   *          Server where these attributes are configured.
   * @param includeAttributes
   *          Attributes to be included with all change records, may include
   *          wild-cards.
   * @param includeAttributesForDeletes
   *          Additional attributes to be included with delete change records,
   *          may include wild-cards.
   * @return {@code true} if the set of attributes was modified.
   */
  public boolean setEclIncludes(int serverId,
      Set<String> includeAttributes,
      Set<String> includeAttributesForDeletes)
  {
    ECLIncludes current;
    ECLIncludes updated;
    do
    {
      current = this.eclIncludes.get();
      updated = current.addIncludedAttributes(
          serverId, includeAttributes, includeAttributesForDeletes);
    }
    while (!this.eclIncludes.compareAndSet(current, updated));
    return current != updated;
  }

  /**
   * Get the attributes to include in each change for the ECL.
   *
   * @return The attributes to include in each change for the ECL.
   */
  public Set<String> getEclIncludes()
  {
    return eclIncludes.get().includedAttrsAllServers;
  }

  /**
   * Get the attributes to include in each delete change for the ECL.
   *
   * @return The attributes to include in each delete change for the ECL.
   */
  public Set<String> getEclIncludesForDeletes()
  {
    return eclIncludes.get().includedAttrsForDeletesAllServers;
  }

  /**
   * Get the attributes to include in each change for the ECL for a given
   * serverId.
   *
   * @param serverId
   *          The serverId for which we want the include attributes.
   * @return The attributes.
   */
  Set<String> getEclIncludes(int serverId)
  {
    return eclIncludes.get().includedAttrsByServer.get(serverId);
  }

  /**
   * Get the attributes to include in each change for the ECL for a given
   * serverId.
   *
   * @param serverId
   *          The serverId for which we want the include attributes.
   * @return The attributes.
   */
  Set<String> getEclIncludesForDeletes(int serverId)
  {
    return eclIncludes.get().includedAttrsForDeletesByServer.get(serverId);
  }

  /**
   * Returns the CSN of the last Change that was fully processed by this
   * ReplicationDomain.
   *
   * @return The CSN of the last Change that was fully processed by this
   *         ReplicationDomain.
   */
  public CSN getLastLocalChange()
  {
    return state.getCSN(getServerId());
  }

  /**
   * Gets and stores the assured replication configuration parameters. Returns a
   * boolean indicating if the passed configuration has changed compared to
   * previous values and the changes require a reconnection.
   *
   * @param config
   *          The configuration object
   * @param allowReconnection
   *          Tells if one must reconnect if significant changes occurred
   */
  protected void readAssuredConfig(ReplicationDomainCfg config,
      boolean allowReconnection)
  {
    // Disconnect if required: changing configuration values before
    // disconnection would make assured replication used immediately and
    // disconnection could cause some timeouts error.
    if (needReconnection(config) && allowReconnection)
    {
      disableService();

      assuredConfig = config;

      enableService();
    }
  }

  private boolean needReconnection(ReplicationDomainCfg cfg)
  {
    final AssuredMode assuredMode = getAssuredMode();
    switch (cfg.getAssuredType())
    {
    case NOT_ASSURED:
      if (isAssured())
      {
        return true;
      }
      break;
    case SAFE_DATA:
      if (!isAssured() || assuredMode == SAFE_READ_MODE)
      {
        return true;
      }
      break;
    case SAFE_READ:
      if (!isAssured() || assuredMode == SAFE_DATA_MODE)
      {
        return true;
      }
      break;
    }

    return isAssured()
        && assuredMode == SAFE_DATA_MODE
        && cfg.getAssuredSdLevel() != getAssuredSdLevel();
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + " " + getBaseDN() + " " + getServerId();
  }
}
