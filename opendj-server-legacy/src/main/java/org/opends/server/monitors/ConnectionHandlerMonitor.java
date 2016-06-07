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
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.monitors;

import static org.opends.server.util.ServerConstants.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;

import org.forgerock.opendj.server.config.server.ConnectionHandlerCfg;
import org.forgerock.opendj.server.config.server.MonitorProviderCfg;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ConnectionHandler;
import org.opends.server.api.MonitorData;
import org.opends.server.api.MonitorProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.HostPort;
import org.forgerock.opendj.ldap.schema.ObjectClass;

/**
 * This class implements a monitor provider that will report generic information
 * for an enabled Directory Server connection handler, including its protocol,
 * listeners, and established connections.
 */
public class ConnectionHandlerMonitor
       extends MonitorProvider<MonitorProviderCfg>
{
  /** The connection handler with which this monitor is associated. */
  private ConnectionHandler<?> connectionHandler;

  /** The name for this monitor. */
  private String monitorName;



  /**
   * Creates a new instance of this connection handler monitor provider that
   * will work with the provided connection handler.  Most of the initialization
   * should be handled in the {@code initializeMonitorProvider} method.
   *
   * @param  connectionHandler  The connection handler with which this monitor
   *                            is associated.
   */
  public ConnectionHandlerMonitor(
       ConnectionHandler<? extends ConnectionHandlerCfg> connectionHandler)
  {
    this.connectionHandler = connectionHandler;
  }



  @Override
  public void initializeMonitorProvider(MonitorProviderCfg configuration)
  {
    monitorName = connectionHandler.getConnectionHandlerName();
  }



  /** {@inheritDoc} */
  @Override
  public String getMonitorInstanceName()
  {
    return monitorName;
  }



  /**
   * Retrieves the objectclass that should be included in the monitor entry
   * created from this monitor provider.
   *
   * @return  The objectclass that should be included in the monitor entry
   *          created from this monitor provider.
   */
  @Override
  public ObjectClass getMonitorObjectClass()
  {
    return DirectoryServer.getSchema().getObjectClass(OC_MONITOR_CONNHANDLER);
  }



  @Override
  public MonitorData getMonitorData()
  {
    LinkedList<ClientConnection> conns = new LinkedList<>(connectionHandler.getClientConnections());
    LinkedList<HostPort> listeners = new LinkedList<>(connectionHandler.getListeners());

    final MonitorData attrs = new MonitorData(5);
    attrs.add(ATTR_MONITOR_CONFIG_DN, connectionHandler.getComponentEntryDN());
    attrs.add(ATTR_MONITOR_CONNHANDLER_PROTOCOL, connectionHandler.getProtocol());

    if (!listeners.isEmpty())
    {
      attrs.add(ATTR_MONITOR_CONNHANDLER_LISTENER, listeners);
    }

    attrs.add(ATTR_MONITOR_CONNHANDLER_NUMCONNECTIONS, conns.size());
    if (!conns.isEmpty())
    {
      Collection<String> connectionSummaries = new ArrayList<>();
      for (ClientConnection c : conns)
      {
        connectionSummaries.add(c.getMonitorSummary());
      }
      attrs.add(ATTR_MONITOR_CONNHANDLER_CONNECTION, connectionSummaries);
    }

    return attrs;
  }
}
