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

import java.util.ArrayList;
import java.util.Collection;
import java.util.TreeMap;

import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.server.config.server.ClientConnectionMonitorProviderCfg;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ConnectionHandler;
import org.opends.server.api.MonitorData;
import org.opends.server.api.MonitorProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.InitializationException;

/**
 * This class defines a Directory Server monitor provider that can be
 * used to obtain information about the client connections established
 * to the server. Note that the information reported is obtained with
 * little or no locking, so it may not be entirely consistent,
 * especially for active connections.
 */
public class ClientConnectionMonitorProvider extends
    MonitorProvider<ClientConnectionMonitorProviderCfg>
{

  /**
   * The connection handler associated with this monitor, or null if all
   * connection handlers should be monitored.
   */
  private final ConnectionHandler<?> handler;



  /**
   * Creates an instance of this monitor provider.
   */
  public ClientConnectionMonitorProvider()
  {
    // This will monitor all connection handlers.
    this.handler = null;
  }



  /**
   * Creates an instance of this monitor provider.
   *
   * @param handler
   *          to which the monitor provider is associated.
   */
  public ClientConnectionMonitorProvider(ConnectionHandler handler)
  {
    this.handler = handler;
  }



  /** {@inheritDoc} */
  @Override
  public void initializeMonitorProvider(
      ClientConnectionMonitorProviderCfg configuration)
      throws ConfigException, InitializationException
  {
    // No initialization is required.
  }



  /**
   * Retrieves the name of this monitor provider. It should be unique
   * among all monitor providers, including all instances of the same
   * monitor provider.
   *
   * @return The name of this monitor provider.
   */
  @Override
  public String getMonitorInstanceName()
  {
    if (handler == null)
    {
      return "Client Connections";
    }
    else
    {
      // Client connections of a connection handler
      return "Client Connections" + ",cn="
          + handler.getConnectionHandlerName();
    }
  }

  @Override
  public MonitorData getMonitorData()
  {
    // Re-order the connections by connection ID.
    TreeMap<Long, ClientConnection> connMap = new TreeMap<>();

    if (handler != null)
    {
      for (ClientConnection conn : handler.getClientConnections())
      {
        connMap.put(conn.getConnectionID(), conn);
      }
    }
    else
    {
      // Get information about all the available connections.
      for (ConnectionHandler<?> hdl : DirectoryServer.getConnectionHandlers())
      {
        // FIXME: connections from different handlers could have the same ID.
        for (ClientConnection conn : hdl.getClientConnections())
        {
          connMap.put(conn.getConnectionID(), conn);
        }
      }
    }

    Collection<String> connectionSummaries = new ArrayList<>(connMap.values().size());
    for (ClientConnection conn : connMap.values())
    {
      connectionSummaries.add(conn.getMonitorSummary());
    }
    MonitorData result = new MonitorData(1);
    result.add("connection", connectionSummaries);
    return result;
  }
}
