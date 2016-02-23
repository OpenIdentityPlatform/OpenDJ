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
 * Copyright 2008-2009 Sun Microsystems, Inc.
 * Portions Copyright 2015 ForgeRock AS.
 */

package org.opends.server.tools.dsreplication;

import org.opends.server.types.HostPort;

/**
 * This class is used to store the information provided by the user to
 * initialize replication.  It is required because when we are in interactive
 * mode the ReplicationCliArgumentParser is not enough.
 */
public class SourceDestinationServerUserData extends ReplicationUserData
{
  private String hostNameSource;
  private int portSource;
  private String hostNameDestination;
  private int portDestination;

  /**
   * Returns the host name of the source server.
   * @return the host name of the source server.
   */
  public String getHostNameSource()
  {
    return hostNameSource;
  }

  /**
   * Sets the host name of the source server.
   * @param hostNameSource the host name of the source server.
   */
  public void setHostNameSource(String hostNameSource)
  {
    this.hostNameSource = hostNameSource;
  }

  /**
   * Returns the port of the source server.
   * @return the port of the source server.
   */
  public int getPortSource()
  {
    return portSource;
  }

  /**
   * Sets the port of the source server.
   * @param portSource the port of the source server.
   */
  public void setPortSource(int portSource)
  {
    this.portSource = portSource;
  }

  /**
   * Returns the host name of the destination server.
   * @return the host name of the destination server.
   */
  public String getHostNameDestination()
  {
    return new HostPort(hostNameDestination, portDestination).toString();
  }

  /**
   * Returns a host:port string for the source server.
   * @return a host:port string for the source server
   */
  public String getSourceHostPort()
  {
    return new HostPort(hostNameSource, portSource).toString();
  }

  /**
   * Returns a host:port string for the destination server.
   * @return a host:port string for the destination server
   */
  public String getDestinationHostPort()
  {
    return hostNameDestination + ":" + portDestination;
  }

  /**
   * Sets the host name of the destination server.
   * @param hostNameDestination the host name of the destination server.
   */
  public void setHostNameDestination(String hostNameDestination)
  {
    this.hostNameDestination = hostNameDestination;
  }

  /**
   * Returns the port of the destination server.
   * @return the port of the destination server.
   */
  public int getPortDestination()
  {
    return portDestination;
  }

  /**
   * Sets the port of the destination server.
   * @param portDestination the port of the destination server.
   */
  public void setPortDestination(int portDestination)
  {
    this.portDestination = portDestination;
  }

  /**
   * Returns a {@link HostPort} representing the source server.
   * @return a {@link HostPort} representing the source server
   */
  public HostPort getSource()
  {
    return new HostPort(hostNameSource, portSource);
  }

  /**
   * Returns a {@link HostPort} representing the destination server.
   * @return a {@link HostPort} representing the destination server
   */
  public HostPort getDestination()
  {
    return new HostPort(hostNameDestination, portDestination);
  }
}
