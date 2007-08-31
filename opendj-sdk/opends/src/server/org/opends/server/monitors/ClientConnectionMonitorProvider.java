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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.monitors;



import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.TreeMap;

import org.opends.server.admin.std.server.ConnectionHandlerCfg;
import org.opends.server.admin.std.server.ClientConnectionMonitorProviderCfg;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ConnectionHandler;
import org.opends.server.api.MonitorProvider;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.InitializationException;



/**
 * This class defines a Directory Server monitor provider that can be used to
 * obtain information about the client connections established to the server.
 * Note that the information reported is obtained with little or no locking, so
 * it may not be entirely consistent, especially for active connections.
 */
public class ClientConnectionMonitorProvider
       extends MonitorProvider<ClientConnectionMonitorProviderCfg>
{
  /**
   * Creates an instance of this monitor provider.
   */
  public ClientConnectionMonitorProvider()
  {
    super("Client Connection Monitor Provider");

    // No initialization should be performed here.
  }



  /**
   * {@inheritDoc}
   */
  public void initializeMonitorProvider(
                   ClientConnectionMonitorProviderCfg configuration)
         throws ConfigException, InitializationException
  {
    // No initialization is required.
  }



  /**
   * Retrieves the name of this monitor provider.  It should be unique among all
   * monitor providers, including all instances of the same monitor provider.
   *
   * @return  The name of this monitor provider.
   */
  public String getMonitorInstanceName()
  {
    return "Client Connections";
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
  public long getUpdateInterval()
  {
    // This monitor does not need to run periodically.
    return 0;
  }



  /**
   * Performs any processing periodic processing that may be desired to update
   * the information associated with this monitor.  Note that best-effort
   * attempts will be made to ensure that calls to this method come
   * <CODE>getUpdateInterval()</CODE> milliseconds apart, but no guarantees will
   * be made.
   */
  public void updateMonitorData()
  {
    // This monitor does not need to run periodically.
    return;
  }



  /**
   * Retrieves a set of attributes containing monitor data that should be
   * returned to the client if the corresponding monitor entry is requested.
   *
   * @return  A set of attributes containing monitor data that should be
   *          returned to the client if the corresponding monitor entry is
   *          requested.
   */
  public ArrayList<Attribute> getMonitorData()
  {
    // Get information about all the available connections.
    ArrayList<Collection<ClientConnection>> connCollections =
         new ArrayList<Collection<ClientConnection>>();
    for (ConnectionHandler handler : DirectoryServer.getConnectionHandlers())
    {
      ConnectionHandler<? extends ConnectionHandlerCfg> connHandler =
           (ConnectionHandler<? extends ConnectionHandlerCfg>) handler;
      connCollections.add(connHandler.getClientConnections());
    }


    // Re-order the connections by connection ID.
    TreeMap<Long,ClientConnection> connMap =
         new TreeMap<Long,ClientConnection>();
    for (Collection<ClientConnection> collection : connCollections)
    {
      for (ClientConnection conn : collection)
      {
        connMap.put(conn.getConnectionID(), conn);
      }
    }


    // Iterate through all the client connections and create a one-line summary
    // of each.
    AttributeType attrType =
         DirectoryServer.getDefaultAttributeType("connection");
    LinkedHashSet<AttributeValue> values =
         new LinkedHashSet<AttributeValue>(connMap.size());
    for (ClientConnection conn : connMap.values())
    {
      values.add(new AttributeValue(attrType, conn.getMonitorSummary()));
    }


    ArrayList<Attribute> attrs = new ArrayList<Attribute>(1);
    attrs.add(new Attribute(attrType, "connection", values));
    return attrs;
  }
}

