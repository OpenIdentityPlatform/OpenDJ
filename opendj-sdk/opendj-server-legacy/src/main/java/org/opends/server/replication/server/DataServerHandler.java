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

import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.replication.common.ServerStatus.*;
import static org.opends.server.replication.common.StatusMachine.*;
import static org.opends.server.replication.protocol.ProtocolVersion.*;

import java.io.IOException;
import java.util.*;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.replication.common.*;
import org.opends.server.replication.protocol.*;
import org.opends.server.types.*;

/**
 * This class defines a server handler, which handles all interaction with a
 * peer server (RS or DS).
 */
public class DataServerHandler extends ServerHandler
{

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * Temporary generationId received in handshake/phase1, and used after
   * handshake/phase2.
   */
  private long tmpGenerationId;

  /** Status of this DS (only used if this server handler represents a DS). */
  private ServerStatus status = ServerStatus.INVALID_STATUS;

  /** Referrals URLs this DS is exporting. */
  private List<String> refUrls = new ArrayList<>();
  /** Assured replication enabled on DS or not. */
  private boolean assuredFlag;
  /** DS assured mode (relevant if assured replication enabled). */
  private AssuredMode assuredMode = AssuredMode.SAFE_DATA_MODE;
  /** DS safe data level (relevant if assured mode is safe data). */
  private byte safeDataLevel = -1;
  private Set<String> eclIncludes = new HashSet<>();
  private Set<String> eclIncludesForDeletes = new HashSet<>();

  /**
   * Creates a new data server handler.
   * @param session The session opened with the remote data server.
   * @param queueSize The queue size.
   * @param replicationServer The hosting RS.
   * @param rcvWindowSize The receiving window size.
   */
  public DataServerHandler(
      Session session,
      int queueSize,
      ReplicationServer replicationServer,
      int rcvWindowSize)
  {
    super(session, queueSize, replicationServer, rcvWindowSize);
  }

  /**
   * Order the peer DS server to change his status or close the connection
   * according to the requested new generation id.
   * @param newGenId The new generation id to take into account
   * @throws IOException If IO error occurred.
   */
  public void changeStatusForResetGenId(long newGenId) throws IOException
  {
    StatusMachineEvent event = getStatusMachineEvent(newGenId);
    if (event == null)
    {
      return;
    }

    if (event == StatusMachineEvent.TO_BAD_GEN_ID_STATUS_EVENT
        && status == ServerStatus.FULL_UPDATE_STATUS)
    {
      // Prevent useless error message (full update status cannot lead to bad gen status)
      logger.info(NOTE_BAD_GEN_ID_IN_FULL_UPDATE, replicationServer.getServerId(),
          getBaseDN(), serverId, generationId, newGenId);
      return;
    }

    changeStatus(event, "for reset gen id");
  }

  private StatusMachineEvent getStatusMachineEvent(long newGenId)
  {
    if (newGenId == -1)
    {
      // The generation id is being made invalid, let's put the DS
      // into BAD_GEN_ID_STATUS
      return StatusMachineEvent.TO_BAD_GEN_ID_STATUS_EVENT;
    }
    if (newGenId != generationId)
    {
      // This server has a bad generation id compared to new reference one,
      // let's put it into BAD_GEN_ID_STATUS
      return StatusMachineEvent.TO_BAD_GEN_ID_STATUS_EVENT;
    }

    if (status != ServerStatus.BAD_GEN_ID_STATUS)
    {
      if (logger.isTraceEnabled())
      {
        logger.trace("In RS " + replicationServer.getServerId()
            + ", DS " + getServerId() + " for baseDN=" + getBaseDN()
            + " has already generation id " + newGenId
            + " so no ChangeStatusMsg sent to him.");
      }
      return null;
    }

    // This server has the good new reference generation id.
    // Close connection with him to force his reconnection: DS will
    // reconnect in NORMAL_STATUS or DEGRADED_STATUS.

    if (logger.isTraceEnabled())
    {
      logger.trace("In RS " + replicationServer.getServerId()
          + ", closing connection to DS " + getServerId() + " for baseDN=" + getBaseDN()
          + " to force reconnection as new local generationId"
          + " and remote one match and DS is in bad gen id: " + newGenId);
    }

    // Connection closure must not be done calling RSD.stopHandler() as it
    // would rewait the RSD lock that we already must have entering this
    // method. This would lead to a reentrant lock which we do not want.
    // So simply close the session, this will make the hang up appear
    // after the reader thread that took the RSD lock releases it.
    if (session != null
        // V4 protocol introduced a StopMsg to properly close the
        // connection between servers
        && getProtocolVersion() >= ProtocolVersion.REPLICATION_PROTOCOL_V4)
    {
      try
      {
        session.publish(new StopMsg());
      }
      catch (IOException ioe)
      {
        // Anyway, going to close session, so nothing to do
      }
    }

    // NOT_CONNECTED_STATUS is the last one in RS session life: handler
    // will soon disappear after this method call...
    status = ServerStatus.NOT_CONNECTED_STATUS;
    return null;
  }

  /**
   * Change the status according to the event.
   *
   * @param event
   *          The event to be used for new status computation
   * @return The new status of the DS
   * @throws IOException
   *           When raised by the underlying session
   */
  public ServerStatus changeStatus(StatusMachineEvent event) throws IOException
  {
    return changeStatus(event, "from status analyzer");
  }

  private ServerStatus changeStatus(StatusMachineEvent event, String origin)
      throws IOException
  {
    // Check state machine allows this new status (Sanity check)
    ServerStatus newStatus = StatusMachine.computeNewStatus(status, event);
    if (newStatus == ServerStatus.INVALID_STATUS)
    {
      logger.error(ERR_RS_CANNOT_CHANGE_STATUS, getBaseDN(), serverId, status, event);
      // Only change allowed is from NORMAL_STATUS to DEGRADED_STATUS and vice
      // versa. We may be trying to change the status while another status has
      // just been entered: e.g a full update has just been engaged.
      // In that case, just ignore attempt to change the status
      return newStatus;
    }

    // Send message requesting to change the DS status
    ChangeStatusMsg csMsg = new ChangeStatusMsg(newStatus, INVALID_STATUS);

    if (logger.isTraceEnabled())
    {
      logger.trace("In RS " + replicationServer.getServerId()
          + " Sending change status " + origin + " to " + getServerId()
          + " for baseDN=" + getBaseDN() + ":\n" + csMsg);
    }

    session.publish(csMsg);

    status = newStatus;

    return newStatus;
  }

  /** {@inheritDoc} */
  @Override
  public List<Attribute> getMonitorData()
  {
    // Get the generic ones
    List<Attribute> attributes = super.getMonitorData();

    // Add the specific DS ones
    attributes.add(Attributes.create("replica", serverURL));
    attributes.add(Attributes.create("connected-to",
        this.replicationServer.getMonitorInstanceName()));

    ReplicationDomainMonitorData md =
        replicationServerDomain.getDomainMonitorData();

    // Oldest missing update
    long approxFirstMissingDate = md.getApproxFirstMissingDate(serverId);
    if (approxFirstMissingDate > 0)
    {
      Date date = new Date(approxFirstMissingDate);
      attributes.add(Attributes.create(
          "approx-older-change-not-synchronized", date.toString()));
      attributes.add(Attributes.create(
          "approx-older-change-not-synchronized-millis", String
          .valueOf(approxFirstMissingDate)));
    }

    // Missing changes
    attributes.add(Attributes.create("missing-changes",
        String.valueOf(md.getMissingChanges(serverId))));

    // Replication delay
    attributes.add(Attributes.create("approximate-delay",
        String.valueOf(md.getApproxDelay(serverId))));

    /* get the Server State */
    AttributeBuilder builder = new AttributeBuilder("server-state");
    ServerState state = md.getLDAPServerState(serverId);
    if (state != null)
    {
      for (String str : state.toStringSet())
      {
        builder.add(str);
      }
      attributes.add(builder.toAttribute());
    }

    return attributes;
  }

  /** {@inheritDoc} */
  @Override
  public String getMonitorInstanceName()
  {
    return "Connected directory server DS(" + serverId + ") " + serverURL
        + ",cn=" + replicationServerDomain.getMonitorInstanceName();
  }

  /**
   * Gets the status of the connected DS.
   * @return The status of the connected DS.
   */
  @Override
  public ServerStatus getStatus()
  {
    return status;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isDataServer()
  {
    return true;
  }

  /**
   * Process message of a remote server changing his status.
   * @param csMsg The message containing the new status
   * @return The new server status of the DS
   */
  public ServerStatus processNewStatus(ChangeStatusMsg csMsg)
  {
    // Get the status the DS just entered
    ServerStatus reqStatus = csMsg.getNewStatus();
    // Translate new status to a state machine event
    StatusMachineEvent event = StatusMachineEvent.statusToEvent(reqStatus);
    if (event == StatusMachineEvent.INVALID_EVENT)
    {
      logger.error(ERR_RS_INVALID_NEW_STATUS, reqStatus, getBaseDN(), serverId);
      return ServerStatus.INVALID_STATUS;
    }

    // Check state machine allows this new status
    ServerStatus newStatus = StatusMachine.computeNewStatus(status, event);
    if (newStatus == ServerStatus.INVALID_STATUS)
    {
      logger.error(ERR_RS_CANNOT_CHANGE_STATUS, getBaseDN(), serverId, status, event);
      return ServerStatus.INVALID_STATUS;
    }

    status = newStatus;
    return status;
  }

  /**
   * Processes a start message received from a remote data server.
   * @param serverStartMsg The provided start message received.
   * @return flag specifying whether the remote server requests encryption.
   * @throws DirectoryException raised when an error occurs.
   */
  public boolean processStartFromRemote(ServerStartMsg serverStartMsg)
  throws DirectoryException
  {
    session
        .setProtocolVersion(getCompatibleVersion(serverStartMsg.getVersion()));
    tmpGenerationId = serverStartMsg.getGenerationId();
    serverId = serverStartMsg.getServerId();
    serverURL = serverStartMsg.getServerURL();
    groupId = serverStartMsg.getGroupId();
    heartbeatInterval = serverStartMsg.getHeartbeatInterval();

    // generic stuff
    setBaseDNAndDomain(serverStartMsg.getBaseDN(), true);
    setInitialServerState(serverStartMsg.getServerState());
    setSendWindowSize(serverStartMsg.getWindowSize());

    if (heartbeatInterval < 0)
    {
      heartbeatInterval = 0;
    }
    return serverStartMsg.getSSLEncryption();
  }

  /** Send our own TopologyMsg to DS. */
  private TopologyMsg sendTopoToRemoteDS() throws IOException
  {
    TopologyMsg outTopoMsg = replicationServerDomain
        .createTopologyMsgForDS(this.serverId);
    sendTopoInfo(outTopoMsg);
    return outTopoMsg;
  }

  /**
   * Starts the handler from a remote ServerStart message received from
   * the remote data server.
   * @param inServerStartMsg The provided ServerStart message received.
   */
  public void startFromRemoteDS(ServerStartMsg inServerStartMsg)
  {
    try
    {
      // initializations
      localGenerationId = -1;
      oldGenerationId = -100;

      // processes the ServerStart message received
      boolean sessionInitiatorSSLEncryption =
        processStartFromRemote(inServerStartMsg);

      /**
       * Hack to be sure that if a server disconnects and reconnect, we
       * let the reader thread see the closure and cleanup any reference
       * to old connection. This must be done before taking the domain lock so
       * that the reader thread has a chance to stop the handler.
       *
       * TODO: This hack should be removed and disconnection/reconnection
       * properly dealt with.
       */
      if (replicationServerDomain.getConnectedDSs()
          .containsKey(inServerStartMsg.getServerId()))
      {
        try {
          Thread.sleep(100);
        }
        catch(Exception e){
          abortStart(null);
          return;
        }
      }

      lockDomainNoTimeout();

      localGenerationId = replicationServerDomain.getGenerationId();
      oldGenerationId = localGenerationId;

      if (replicationServerDomain.isAlreadyConnectedToDS(this))
      {
        abortStart(null);
        return;
      }

      try
      {
        StartMsg outStartMsg = sendStartToRemote();

        logStartHandshakeRCVandSND(inServerStartMsg, outStartMsg);

        // The session initiator decides whether to use SSL.
        // Until here session is encrypted then it depends on the negotiation
        if (!sessionInitiatorSSLEncryption)
        {
          session.stopEncryption();
        }

        // wait and process StartSessionMsg from remote RS
        StartSessionMsg inStartSessionMsg =
          waitAndProcessStartSessionFromRemoteDS();
        if (inStartSessionMsg == null)
        {
          // DS wants to properly close the connection (DS sent a StopMsg)
          logStopReceived();
          abortStart(null);
          return;
        }

        // Send our own TopologyMsg to remote DS
        TopologyMsg outTopoMsg = sendTopoToRemoteDS();

        logStartSessionHandshake(inStartSessionMsg, outTopoMsg);
      }
      catch(IOException e)
      {
        LocalizableMessage errMessage = ERR_DS_DISCONNECTED_DURING_HANDSHAKE.get(
            inServerStartMsg.getServerId(), replicationServer.getServerId());
        throw new DirectoryException(ResultCode.OTHER, errMessage);
      }
      catch (Exception e)
      {
        // We do not need to support DS V1 connection, we just accept RS V1
        // connection:
        // We just trash the message, log the event for debug purpose and close
        // the connection
        throw new DirectoryException(ResultCode.OTHER, null, null);
      }

      replicationServerDomain.register(this);

      logger.debug(INFO_REPLICATION_SERVER_CONNECTION_FROM_DS, getReplicationServerId(), getServerId(),
              replicationServerDomain.getBaseDN(), session.getReadableRemoteAddress());

      super.finalizeStart();
    }
    catch(DirectoryException de)
    {
      abortStart(de.getMessageObject());
    }
    catch(Exception e)
    {
      abortStart(null);
    }
    finally
    {
      releaseDomainLock();
    }
  }

  /**
   * Sends a start message to the remote DS.
   *
   * @return The StartMsg sent.
   * @throws IOException
   *           When an exception occurs.
   */
  private StartMsg sendStartToRemote() throws IOException
  {
    final StartMsg startMsg;

    // Before V4 protocol, we sent a ReplServerStartMsg
    if (getProtocolVersion() < ProtocolVersion.REPLICATION_PROTOCOL_V4)
    {
      // Peer DS uses protocol < V4 : send it a ReplServerStartMsg
      startMsg = createReplServerStartMsg();
    }
    else
    {
      // Peer DS uses protocol V4 : send it a ReplServerStartDSMsg
      startMsg = new ReplServerStartDSMsg(getReplicationServerId(),
          getReplicationServerURL(), getBaseDN(), maxRcvWindow,
          replicationServerDomain.getLatestServerState(),
          localGenerationId, sslEncryption, getLocalGroupId(),
          replicationServer.getDegradedStatusThreshold(),
          replicationServer.getWeight(),
          replicationServerDomain.getConnectedDSs().size());
    }

    send(startMsg);
    return startMsg;
  }

  /**
   * Creates a DSInfo structure representing this remote DS.
   * @return The DSInfo structure representing this remote DS
   */
  public DSInfo toDSInfo()
  {
    return new DSInfo(serverId, serverURL, getReplicationServerId(),
        generationId, status, assuredFlag, assuredMode, safeDataLevel, groupId,
        refUrls, eclIncludes, eclIncludesForDeletes, getProtocolVersion());
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    if (serverId != 0)
    {
      return "Replica DS(" + serverId + ") for domain \""
          + replicationServerDomain.getBaseDN() + "\"";
    }
    return "Unknown server";
  }

  /**
   * Wait receiving the StartSessionMsg from the remote DS and process it, or
   * receiving a StopMsg to properly stop the handshake procedure.
   * @return the startSessionMsg received or null DS sent a stop message to
   *         not finish the handshake.
   * @throws Exception
   */
  private StartSessionMsg waitAndProcessStartSessionFromRemoteDS()
      throws Exception
  {
    ReplicationMsg msg = session.receive();

    if (msg instanceof StopMsg)
    {
      // DS wants to stop handshake (was just for handshake phase one for RS
      // choice). Return null to make the session be terminated.
      return null;
    } else if (!(msg instanceof StartSessionMsg))
    {
      LocalizableMessage message = LocalizableMessage.raw(
          "Protocol error: StartSessionMsg required." + msg + " received.");
      abortStart(message);
      return null;
    }

    // Process StartSessionMsg sent by remote DS
    StartSessionMsg startSessionMsg = (StartSessionMsg) msg;

    this.status = startSessionMsg.getStatus();
    // Sanity check: is it a valid initial status?
    if (!isValidInitialStatus(this.status))
    {
      throw new DirectoryException(ResultCode.OTHER,
          ERR_RS_INVALID_INIT_STATUS.get( this.status, getBaseDN(), serverId));
    }

    this.refUrls = startSessionMsg.getReferralsURLs();
    this.assuredFlag = startSessionMsg.isAssured();
    this.assuredMode = startSessionMsg.getAssuredMode();
    this.safeDataLevel = startSessionMsg.getSafeDataLevel();
    this.eclIncludes = startSessionMsg.getEclIncludes();
    this.eclIncludesForDeletes = startSessionMsg.getEclIncludesForDeletes();

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
    generationId = tmpGenerationId;
    if (localGenerationId > 0)
    {
      if (generationId != localGenerationId)
      {
        logger.warn(WARN_BAD_GENERATION_ID_FROM_DS, serverId, session.getReadableRemoteAddress(),
            generationId, getBaseDN(), getReplicationServerId(), localGenerationId);
      }
    }
    else
    {
      // We are an empty ReplicationServer
      if (generationId > 0 && !getServerState().isEmpty())
      {
        // If the LDAP server has already sent changes
        // it is not expected to connect to an empty RS
        logger.warn(WARN_BAD_GENERATION_ID_FROM_DS, serverId, session.getReadableRemoteAddress(),
            generationId, getBaseDN(), getReplicationServerId(), localGenerationId);
      }
      else
      {
        // The local RS is not initialized - take the one received
        // WARNING: Must be done before computing topo message to send
        // to peer server as topo message must embed valid generation id
        // for our server
        oldGenerationId = replicationServerDomain.changeGenerationId(generationId);
      }
    }
    return startSessionMsg;
  }

  /**
   * Process message of a remote server changing his status.
   * @param csMsg The message containing the new status
   */
  public void receiveNewStatus(ChangeStatusMsg csMsg)
  {
    replicationServerDomain.processNewStatus(this, csMsg);
  }
}
