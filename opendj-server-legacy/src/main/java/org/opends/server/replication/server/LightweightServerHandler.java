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
package org.opends.server.replication.server;

import java.util.Date;
import java.util.Set;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.api.MonitorData;
import org.forgerock.opendj.server.config.server.MonitorProviderCfg;
import org.opends.server.api.MonitorProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.replication.common.DSInfo;
import org.opends.server.replication.common.ServerState;
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
  /** The tracer object for the debug logger. */
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private final ReplicationServerHandler replServerHandler;

  /** All the information for this DS. */
  private final DSInfo dsInfo;

  /**
   * Creates a new LightweightServerHandler with the provided serverId,
   * connected to the remote Replication Server represented by
   * replServerHandler.
   *
   * @param replServerHandler
   *          The server handler of the RS this remote DS is connected to
   * @param dsInfo
   *          all the info for the represented DS
   */
  public LightweightServerHandler(ReplicationServerHandler replServerHandler,
      DSInfo dsInfo)
  {
    this.replServerHandler = replServerHandler;
    this.dsInfo = dsInfo;

    if (logger.isTraceEnabled())
    {
      debugInfo("()");
    }
  }

  /**
   * Creates a DSInfo structure representing this remote DS.
   * @return The DSInfo structure representing this remote DS
   */
  public DSInfo toDSInfo()
  {
    return dsInfo;
  }

  /**
   * Get the serverID associated with this LDAP server / directory server.
   *
   * @return The serverId.
   */
  public int getServerId()
  {
    return dsInfo.getDsId();
  }

  /**
   * Start this server handler processing.
   */
  public void startHandler()
  {
    if (logger.isTraceEnabled())
    {
      debugInfo("start");
    }
    DirectoryServer.deregisterMonitorProvider(this);
    DirectoryServer.registerMonitorProvider(this);
  }

  /**
   * Stop this server handler processing.
   */
  public void stopHandler()
  {
    if (logger.isTraceEnabled())
    {
      debugInfo("stop");
    }
    DirectoryServer.deregisterMonitorProvider(this);
  }

  private void debugInfo(String message)
  {
    final ReplicationServerDomain domain = replServerHandler.getDomain();
    logger.trace("In " + domain.getLocalRSMonitorInstanceName()
        + " LWSH for remote server " + getServerId() + " connected to:"
        + replServerHandler.getMonitorInstanceName() + " " + message);
  }

  /** {@inheritDoc} */
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
    return "Connected directory server DS(" + dsInfo.getDsId() + ") "
        + dsInfo.getDsUrl()
        + ",cn=" + replServerHandler.getMonitorInstanceName();
  }

  @Override
  public MonitorData getMonitorData()
  {
    MonitorData attributes = new MonitorData(8);

    final int serverId = dsInfo.getDsId();
    final ReplicationServerDomain domain = replServerHandler.getDomain();
    attributes.add("server-id", serverId);
    attributes.add("domain-name", domain.getBaseDN());
    attributes.add("connected-to", replServerHandler.getMonitorInstanceName());

    // Retrieves the topology counters
    final ReplicationDomainMonitorData md = domain.getDomainMonitorData();
    ServerState remoteState = md.getLDAPServerState(serverId);
    if (remoteState == null)
    {
      remoteState = new ServerState();
    }

    Set<String> serverState = remoteState.toStringSet();
    if (serverState.isEmpty())
    {
      attributes.add("server-state", "unknown");
    }
    else
    {
      attributes.add("server-state", serverState);
    }

    // Oldest missing update
    long approxFirstMissingDate = md.getApproxFirstMissingDate(serverId);
    if (approxFirstMissingDate > 0)
    {
      attributes.add("approx-older-change-not-synchronized", new Date(approxFirstMissingDate));
      attributes.add("approx-older-change-not-synchronized-millis", approxFirstMissingDate);
    }
    attributes.add("missing-changes", md.getMissingChanges(serverId));
    attributes.add("approximate-delay", md.getApproxDelay(serverId));

    return attributes;
  }
}
