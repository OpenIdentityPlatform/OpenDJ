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



import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;

import org.opends.server.admin.std.server.ConnectionHandlerCfg;
import org.opends.server.admin.std.server.MonitorProviderCfg;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ConnectionHandler;
import org.opends.server.api.MonitorProvider;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.ByteStringFactory;
import org.opends.server.types.DirectoryConfig;
import org.opends.server.types.HostPort;
import org.opends.server.types.ObjectClass;

import static org.opends.server.util.ServerConstants.*;



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
    LinkedList<ClientConnection> conns =
         new LinkedList<ClientConnection>(
                  connectionHandler.getClientConnections());
    LinkedList<HostPort> listeners =
         new LinkedList<HostPort>(connectionHandler.getListeners());

    LinkedHashSet<AttributeValue> values = new LinkedHashSet<AttributeValue>();
    values.add(new AttributeValue(protocolType,
         ByteStringFactory.create(connectionHandler.getProtocol())));
    attrs.add(new Attribute(protocolType, ATTR_MONITOR_CONNHANDLER_PROTOCOL,
                            values));


    if (! listeners.isEmpty())
    {
      values = new LinkedHashSet<AttributeValue>();
      for (HostPort hp : listeners)
      {
        values.add(new AttributeValue(listenerType,
                                      ByteStringFactory.create(hp.toString())));
      }
      attrs.add(new Attribute(listenerType, ATTR_MONITOR_CONNHANDLER_LISTENER,
                              values));
    }

    if (! conns.isEmpty())
    {
      values = new LinkedHashSet<AttributeValue>();
      for (ClientConnection c : conns)
      {
        numConnections++;
        values.add(new AttributeValue(connectionsType,
             ByteStringFactory.create(c.getMonitorSummary())));
      }
      attrs.add(new Attribute(connectionsType,
                              ATTR_MONITOR_CONNHANDLER_CONNECTION, values));
    }

    values = new LinkedHashSet<AttributeValue>();
    values.add(new AttributeValue(numConnectionsType,
         ByteStringFactory.create(String.valueOf(numConnections))));
    attrs.add(new Attribute(numConnectionsType,
                            ATTR_MONITOR_CONNHANDLER_NUMCONNECTIONS, values));

    return attrs;
  }
}

