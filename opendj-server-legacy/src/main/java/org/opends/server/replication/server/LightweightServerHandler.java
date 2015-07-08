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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.replication.server;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.opends.server.admin.std.server.MonitorProviderCfg;
import org.opends.server.api.MonitorProvider;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.replication.common.DSInfo;
import org.opends.server.replication.common.ServerState;
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

  /**
   * Retrieves a set of attributes containing monitor data that should be
   * returned to the client if the corresponding monitor entry is requested.
   *
   * @return  A set of attributes containing monitor data that should be
   *          returned to the client if the corresponding monitor entry is
   *          requested.
   */
  @Override
  public List<Attribute> getMonitorData()
  {
    List<Attribute> attributes = new ArrayList<>();

    final int serverId = dsInfo.getDsId();
    final ReplicationServerDomain domain = replServerHandler.getDomain();
    attributes.add(Attributes.create("server-id", String.valueOf(serverId)));
    attributes.add(Attributes.create("domain-name",
        domain.getBaseDN().toString()));
    attributes.add(Attributes.create("connected-to",
        replServerHandler.getMonitorInstanceName()));

    // Retrieves the topology counters
    final ReplicationDomainMonitorData md = domain.getDomainMonitorData();
    ServerState remoteState = md.getLDAPServerState(serverId);
    if (remoteState == null)
    {
      remoteState = new ServerState();
    }

    // get the Server State
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
