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
 *      Portions Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.replication.server;

import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;

import org.opends.server.admin.std.server.MonitorProviderCfg;
import org.opends.server.api.MonitorProvider;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.replication.common.ServerState;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DN;
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

  short serverId;
  ServerHandler replServerHandler;
  ReplicationServerDomain rsDomain;
  DN baseDn;

  /**
   * Creates a new LighweightServerHandler with the provided serverid, connected
   * to the remote Replication Server represented by replServerHandler.
   *
   * @param serverId The serverId of this remote LDAP server.
   * @param replServerHandler The server handler of the Replication Server to
   * which this LDAP server is remotely connected.
   */
  public LightweightServerHandler(String serverId,
      ServerHandler replServerHandler)
  {
    super("Server Handler");
    this.serverId = Short.valueOf(serverId);
    this.replServerHandler = replServerHandler;
    this.rsDomain = replServerHandler.getDomain();
    this.baseDn = rsDomain.getBaseDn();

    if (debugEnabled())
      TRACER.debugInfo(
        "In " +
  replServerHandler.getDomain().getReplicationServer().getMonitorInstanceName()+
        " LWSH for remote server " + this.serverId +
        " connected to:" + this.replServerHandler.getMonitorInstanceName() +
        " ()");
}

  /**
   * Get the serverID associated with this LDAP server.
   * @return The serverId.
   */
  public short getServerId()
  {
    return Short.valueOf(serverId);
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
    DirectoryServer.deregisterMonitorProvider(getMonitorInstanceName());
    DirectoryServer.registerMonitorProvider(this);

  }

  /**
   * Stop this server handler processing.
   */
  public void stopHandler()
  {
    if (debugEnabled())
      TRACER.debugInfo(
      "In " +
replServerHandler.getDomain().getReplicationServer().getMonitorInstanceName() +
      " LWSH for remote server " + this.serverId +
      " connected to:" + this.replServerHandler.getMonitorInstanceName() +
          " stop");
    DirectoryServer.deregisterMonitorProvider(getMonitorInstanceName());
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
    String serverURL=""; // FIXME
    String str = baseDn.toString() + " " + serverURL + " "
       + String.valueOf(serverId);
    return "Undirect LDAP Server " + str;
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
    if (debugEnabled())
      TRACER.debugInfo(
          "In " +
          this.replServerHandler.getDomain().getReplicationServer().
          getMonitorInstanceName()+
          " LWSH for remote server " + this.serverId +
          " connected to:" + this.replServerHandler.getMonitorInstanceName() +
      " getMonitor data");


    ArrayList<Attribute> attributes = new ArrayList<Attribute>();

    attributes.add(new Attribute("server-id",
        String.valueOf(serverId)));
    attributes.add(new Attribute("base-dn",
        rsDomain.getBaseDn().toNormalizedString()));
    attributes.add(new Attribute("connected-to",
        replServerHandler.getMonitorInstanceName()));

    // Retrieves the topology counters
    try
    {
      rsDomain.retrievesRemoteMonitorData();

      // Compute the latency for the current SH
      ServerState remoteState = rsDomain.getServerState(serverId);
      if (remoteState == null)
      {
        remoteState = new ServerState();
      }

      /* get the Server State */
      final String ATTR_SERVER_STATE = "server-state";
      AttributeType type =
        DirectoryServer.getDefaultAttributeType(ATTR_SERVER_STATE);
      LinkedHashSet<AttributeValue> values =
        new LinkedHashSet<AttributeValue>();
      for (String str : remoteState.toStringSet())
      {
        values.add(new AttributeValue(type,str));
      }
      Attribute attr = new Attribute(type, ATTR_SERVER_STATE, values);
      attributes.add(attr);

      // add the latency attribute to our monitor data
      // Compute the latency for the current SH
      int missingChanges = rsDomain.getMissingChanges(remoteState);
      attributes.add(new Attribute("missing-changes",
          String.valueOf(missingChanges)));

      // Add the oldest missing update
      Long olderUpdateTime = rsDomain.getApproxFirstMissingDate(serverId);
      if (olderUpdateTime != null)
      {
        Date date = new Date(olderUpdateTime);
        attributes.add(new Attribute("approx-older-change-not-synchronized",
          date.toString()));
        attributes.add(
          new Attribute("approx-older-change-not-synchronized-millis",
          String.valueOf(olderUpdateTime)));
      }
    }
    catch(Exception e)
    {
      // We failed retrieving the remote monitor data.
      attributes.add(new Attribute("error",
        stackTraceToSingleLineString(e)));
    }
    return attributes;
  }
}
