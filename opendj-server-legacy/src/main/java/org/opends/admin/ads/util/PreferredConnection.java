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
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.admin.ads.util;

import static org.opends.admin.ads.util.PreferredConnection.Type.*;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

import org.opends.server.types.HostPort;

/**
 * A simple class that is used to be able to specify which URL and connection
 * type to use when we connect to a server.
 */
public class PreferredConnection
{
  /** The type of the connection. */
  public enum Type
  {
    /** LDAP connection. */
    LDAP,
    /** LDAPS connection. */
    LDAPS,
    /** Start TLS connection. */
    START_TLS
  }

  private final HostPort hostPort;
  private final Type type;

  /**
   * The constructor of the PreferredConnection.
   * @param hostPort the host and port to connect to the server.
   * @param type the type of connection to be used to connect (required to
   * differentiate StartTLS and regular LDAP).
   */
  public PreferredConnection(HostPort hostPort, Type type)
  {
    this.hostPort = hostPort;
    this.type = type;
  }

  /**
   * Returns the LDAP URL to be used.
   * @return the LDAP URL to be used.
   */
  public String getLDAPURL()
  {
    return (type == LDAPS ? "ldaps" : "ldap") + "://" + hostPort;
  }

  /**
   * Returns the host name and port number.
   *
   * @return the hostPort
   */
  public HostPort getHostPort()
  {
    return hostPort;
  }

  /**
   * Returns the type of the connection.
   *
   * @return the type of the connection.
   */
  public Type getType()
  {
    return type;
  }

  @Override
  public int hashCode()
  {
    return Objects.hash(type, hostPort);
  }

  @Override
  public boolean equals(Object o)
  {
    if (this == o)
    {
      return true;
    }
    if (o instanceof PreferredConnection)
    {
      PreferredConnection p = (PreferredConnection)o;
      return Objects.equals(type, p.type)
          && Objects.equals(hostPort, p.hostPort);
    }
    return false;
  }

  @Override
  public String toString()
  {
    return getLDAPURL();
  }

  /**
   * Commodity method that returns a PreferredConnection object with the
   * information on a given connection.
   * @param conn the connection we retrieve the information from.
   * @return a preferred connection object.
   */
  private static PreferredConnection getPreferredConnection(ConnectionWrapper conn)
  {
    return new PreferredConnection(conn.getHostPort(), conn.getConnectionType());
  }

  /**
   * Commodity method that generates a list of preferred connection (of just
   * one) with the information on a given connection.
   * @param conn the connection we retrieve the information from.
   * @return a list containing the preferred connection object.
   */
  public static Set<PreferredConnection> getPreferredConnections(ConnectionWrapper conn)
  {
    return Collections.singleton(getPreferredConnection(conn));
  }
}
