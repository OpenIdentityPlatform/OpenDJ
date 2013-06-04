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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions copyright 2011-2013 ForgeRock AS
 */
package org.opends.server.replication.server;

import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.opends.server.admin.std.server.MonitorProviderCfg;
import org.opends.server.api.MonitorProvider;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.replication.common.AssuredMode;
import org.opends.server.replication.common.DSInfo;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.common.ServerStatus;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeBuilder;
import org.opends.server.types.Attributes;
import org.opends.server.types.InitializationException;

/**
 * This class defines a server handler dedicated to the remote LDAP servers
 * connected to a remote Replication Server.
 * This class is necessary because we want to provide monitor entries for them
 * and because the monitor API only allows one entry by MonitorProvider instance
 * so that no other class can provide the monitor entry for these objects.
 *
 * One instance of this class is created for each instance of remote LDAP server
 * connected to a remote Replication Server.
 */
public class LightweightServerHandler
  extends MonitorProvider<MonitorProviderCfg>
{
  // The tracer object for the debug logger.
  private static final DebugTracer TRACER = getTracer();

  private ReplicationServerHandler replServerHandler;
  private ReplicationServerDomain rsDomain;
  // The id of the RS this DS is connected to
  private int replicationServerId = -1;

  // Server id of this DS
  private int serverId = -1;
  // Server URL of this DS
  private String serverUrl = null;
  // Generation id of this DS
  private long generationId = -1;
  // Group id of the DS;
  private byte groupId = (byte) -1;
  // Status of this DS
  private ServerStatus status = ServerStatus.INVALID_STATUS;
  // Referrals URLs this DS is exporting
  private List<String> refUrls = new ArrayList<String>();
  // Assured replication enabled on DS or not
  private boolean assuredFlag = false;
  // DS assured mode (relevant if assured replication enabled)
  private AssuredMode assuredMode = AssuredMode.SAFE_DATA_MODE;
  // DS safe data level (relevant if assured mode is safe data)
  private byte safeDataLevel = (byte) -1;
  // The protocol version
  private short protocolVersion = -1;

  private Set<String> eclInclude = new HashSet<String>();
  private Set<String> eclIncludeForDeletes = new HashSet<String>();

  /**
   * Creates a new LightweightServerHandler with the provided serverid,
   * connected to the remote Replication Server represented by
   * replServerHandler.
   *
   * @param replServerHandler The server handler of the RS this remote DS is
   * connected to
   * @param replicationServerId The serverId of the RS this remote DS is
   * connected to
   * @param serverId The serverId of this remote DS.
   * @param serverUrl The serverUrl of this remote DS.
   * @param generationId The generation id of this remote DS.
   * @param groupId The group id of the remote DS
   * @param status The  id of the remote DS
   * @param refUrls The exported referral URLs of the remote DS
   * @param assuredFlag The assured flag of the remote DS
   * @param assuredMode The assured mode of the remote DS
   * @param safeDataLevel The safe data level of the remote DS
   * @param eclInclude The list of entry attributes to be added to the ECL.
   * @param eclIncludeForDeletes The list of entry attributes to be added to
   *                             the ECL.
   * @param protocolVersion The protocol version supported by the remote DS.
   */
  public LightweightServerHandler(ReplicationServerHandler replServerHandler,
      int replicationServerId, int serverId, String serverUrl,
      long generationId, byte groupId, ServerStatus status,
      List<String> refUrls, boolean assuredFlag, AssuredMode assuredMode,
      byte safeDataLevel, Set<String> eclInclude,
      Set<String> eclIncludeForDeletes, short protocolVersion)
  {
    this.replServerHandler = replServerHandler;
    this.rsDomain = replServerHandler.getDomain();
    this.replicationServerId = replicationServerId;
    this.serverId = serverId;
    this.serverUrl = serverUrl;
    this.generationId = generationId;
    this.groupId = groupId;
    this.status = status;
    this.refUrls = refUrls;
    this.assuredFlag = assuredFlag;
    this.assuredMode = assuredMode;
    this.safeDataLevel = safeDataLevel;
    this.eclInclude = eclInclude;
    this.eclIncludeForDeletes = eclIncludeForDeletes;
    this.protocolVersion = protocolVersion;

    if (debugEnabled())
      TRACER.debugInfo(
        "In " +
  replServerHandler.getDomain().getReplicationServer().getMonitorInstanceName()+
        " LWSH for remote server " + this.serverId +
        " connected to:" + this.replServerHandler.getMonitorInstanceName() +
        " ()");
}

  /**
   * Creates a DSInfo structure representing this remote DS.
   * @return The DSInfo structure representing this remote DS
   */
  public DSInfo toDSInfo()
  {
    return new DSInfo(serverId, serverUrl, replicationServerId, generationId,
      status, assuredFlag, assuredMode, safeDataLevel, groupId, refUrls,
      eclInclude, eclIncludeForDeletes, protocolVersion);
  }

  /**
   * Get the serverID associated with this LDAP server.
   * @return The serverId.
   */
  public int getServerId()
  {
    return serverId;
  }

  /**
   * Stop this server handler processing.
   */
  public void startHandler()
  {
    if (debugEnabled())
      TRACER.debugInfo(
      "In " +
replServerHandler.getDomain().getReplicationServer().getMonitorInstanceName() +
      " LWSH for remote server " + this.serverId +
      " connected to:" + this.replServerHandler.getMonitorInstanceName() +
          " start");
    DirectoryServer.deregisterMonitorProvider(this);
    DirectoryServer.registerMonitorProvider(this);

  }

  /**
   * Stop this server handler processing.
   */
  public void stopHandler()
  {
    if (debugEnabled())
      TRACER.debugInfo("In "
          + replServerHandler.getDomain().getReplicationServer()
              .getMonitorInstanceName() + " LWSH for remote server "
          + this.serverId + " connected to:"
          + this.replServerHandler.getMonitorInstanceName() + " stop");
    DirectoryServer.deregisterMonitorProvider(this);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void initializeMonitorProvider(MonitorProviderCfg configuration)
                          throws ConfigException,InitializationException
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
    return "Connected directory server DS(" + serverId + ") " + serverUrl
        + ",cn=" + replServerHandler.getMonitorInstanceName();
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

    attributes.add(Attributes.create("server-id",
        String.valueOf(serverId)));
    attributes.add(Attributes.create("domain-name",
        rsDomain.getBaseDn()));
    attributes.add(Attributes.create("connected-to",
        replServerHandler.getMonitorInstanceName()));

    // Retrieves the topology counters
    MonitorData md = rsDomain.getDomainMonitorData();

    ServerState remoteState = md.getLDAPServerState(serverId);
    if (remoteState == null)
    {
      remoteState = new ServerState();
    }

    /* get the Server State */
    AttributeBuilder builder = new AttributeBuilder("server-state");
    for (String str : remoteState.toStringSet())
    {
      builder.add(str);
    }
    if (builder.size() == 0)
    {
      builder.add("unknown");
    }
    attributes.add(builder.toAttribute());

    // Oldest missing update
    Long approxFirstMissingDate=md.getApproxFirstMissingDate(serverId);
    if ((approxFirstMissingDate != null) && (approxFirstMissingDate>0))
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
    attributes.add(Attributes.create("missing-changes",
        String.valueOf(missingChanges)));

    // Replication delay
    long delay = md.getApproxDelay(serverId);
    attributes.add(Attributes.create("approximate-delay",
        String.valueOf(delay)));

    return attributes;
  }
}
