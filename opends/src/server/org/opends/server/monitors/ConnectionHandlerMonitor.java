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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.monitors;



import static org.opends.server.util.ServerConstants.*;

import java.util.LinkedList;
import java.util.List;

import org.opends.server.admin.std.server.ConnectionHandlerCfg;
import org.opends.server.admin.std.server.MonitorProviderCfg;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ConnectionHandler;
import org.opends.server.api.MonitorProvider;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeBuilder;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.Attributes;
import org.opends.server.types.DirectoryConfig;
import org.opends.server.types.HostPort;
import org.opends.server.types.ObjectClass;



/**
 * This class implements a monitor provider that will report generic information
 * for an enabled Directory Server connection handler, including its protocol,
 * listeners, and established connections.
 */
public class ConnectionHandlerMonitor
       extends MonitorProvider<MonitorProviderCfg>
{
  // The attribute type that will be used to report the established connections.
  private AttributeType connectionsType;

  // The attribute type that will be used to report the listeners.
  private AttributeType listenerType;

  // The attribute type that will be used to report the number of established
  // client connections.
  private AttributeType numConnectionsType;

  // The attribute type that will be used to report the protocol.
  private AttributeType protocolType;

  // The connection handler with which this monitor is associated.
  private ConnectionHandler<?> connectionHandler;

  // The name for this monitor.
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
    super(connectionHandler.getConnectionHandlerName());

    this.connectionHandler = connectionHandler;
  }



  /**
   * {@inheritDoc}
   */
  public void initializeMonitorProvider(MonitorProviderCfg configuration)
  {
    monitorName = connectionHandler.getConnectionHandlerName();

    connectionsType =
         DirectoryConfig.getAttributeType(ATTR_MONITOR_CONNHANDLER_CONNECTION,
                                          true);

    listenerType =
         DirectoryConfig.getAttributeType(ATTR_MONITOR_CONNHANDLER_LISTENER,
                                          true);

    numConnectionsType =
         DirectoryConfig.getAttributeType(
              ATTR_MONITOR_CONNHANDLER_NUMCONNECTIONS, true);

    protocolType =
         DirectoryConfig.getAttributeType(ATTR_MONITOR_CONNHANDLER_PROTOCOL,
                                          true);
  }



  /**
   * {@inheritDoc}
   */
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
  public ObjectClass getMonitorObjectClass()
  {
    return DirectoryConfig.getObjectClass(OC_MONITOR_CONNHANDLER, true);
  }



  /**
   * {@inheritDoc}
   */
  public long getUpdateInterval()
  {
    // We don't need do anything on a periodic basis.
    return 0;
  }



  /**
   * {@inheritDoc}
   */
  public void updateMonitorData()
  {
    // No implementaiton is required.
  }



  /**
   * {@inheritDoc}
   */
  public List<Attribute> getMonitorData()
  {
    LinkedList<Attribute> attrs = new LinkedList<Attribute>();

    int numConnections = 0;
    LinkedList<ClientConnection> conns = new LinkedList<ClientConnection>(
        connectionHandler.getClientConnections());
    LinkedList<HostPort> listeners = new LinkedList<HostPort>(connectionHandler
        .getListeners());

    attrs.add(Attributes.create(protocolType, connectionHandler
        .getProtocol()));

    if (!listeners.isEmpty())
    {
      AttributeBuilder builder = new AttributeBuilder(listenerType);
      for (HostPort hp : listeners)
      {
        builder.add(new AttributeValue(listenerType, hp.toString()));
      }
      attrs.add(builder.toAttribute());
    }

    if (!conns.isEmpty())
    {
      AttributeBuilder builder = new AttributeBuilder(connectionsType);
      for (ClientConnection c : conns)
      {
        numConnections++;
        builder.add(new AttributeValue(connectionsType, c
            .getMonitorSummary()));
      }
      attrs.add(builder.toAttribute());
    }

    attrs.add(Attributes.create(numConnectionsType, String
        .valueOf(numConnections)));

    return attrs;
  }
}

