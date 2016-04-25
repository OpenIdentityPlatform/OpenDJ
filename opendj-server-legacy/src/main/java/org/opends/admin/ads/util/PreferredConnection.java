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

import java.util.Collections;
import java.util.Set;

import javax.naming.ldap.InitialLdapContext;

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

  private final String ldapUrl;
  private final Type type;

  /**
   * The constructor of the PreferredConnection.
   * @param ldapUrl the LDAP URL to connect to the server.
   * @param type the type of connection to be used to connect (required to
   * differentiate StartTLS and regular LDAP).
   */
  public PreferredConnection(String ldapUrl, Type type)
  {
    this.ldapUrl = ldapUrl;
    this.type = type;
  }

  /**
   * Returns the LDAP URL to be used.
   * @return the LDAP URL to be used.
   */
  public String getLDAPURL()
  {
    return ldapUrl;
  }

  /**
   * Returns the type of the connection.
   * @return the type of the connection.
   */
  public Type getType()
  {
    return type;
  }

  @Override
  public int hashCode()
  {
    return (type+ldapUrl.toLowerCase()).hashCode();
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
      return type == p.getType()
          && ldapUrl.equalsIgnoreCase(p.getLDAPURL());
    }
    return false;
  }

  /**
   * Commodity method that returns a PreferredConnection object with the
   * information on a given InitialLdapContext.
   * @param conn the connection we retrieve the information from.
   * @return a preferred connection object.
   */
  private static PreferredConnection getPreferredConnection(ConnectionWrapper conn)
  {
    InitialLdapContext ctx = conn.getLdapContext();
    String ldapUrl = ConnectionUtils.getLdapUrl(ctx);
    PreferredConnection.Type type;
    if (ConnectionUtils.isStartTLS(ctx))
    {
      type = PreferredConnection.Type.START_TLS;
    }
    else if (ConnectionUtils.isSSL(ctx))
    {
      type = PreferredConnection.Type.LDAPS;
    }
    else
    {
      type = PreferredConnection.Type.LDAP;
    }
    return new PreferredConnection(ldapUrl, type);
  }

  /**
   * Commodity method that generates a list of preferred connection (of just
   * one) with the information on a given InitialLdapContext.
   * @param conn the connection we retrieve the information from.
   * @return a list containing the preferred connection object.
   */
  public static Set<PreferredConnection> getPreferredConnections(ConnectionWrapper conn)
  {
    return Collections.singleton(getPreferredConnection(conn));
  }
}
