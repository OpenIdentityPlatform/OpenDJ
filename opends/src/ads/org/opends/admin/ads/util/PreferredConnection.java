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
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.admin.ads.util;

import javax.naming.ldap.InitialLdapContext;

/**
 * A simple class that is used to be able to specify which URL and connection
 * type to use when we connect to a server.
 */
public class PreferredConnection
{
  private String ldapUrl;
  private Type type;
  /**
   * The type of the connection.
   */
  public enum Type
  {
    /**
     * LDAP connection.
     */
    LDAP,
    /**
     * LDAPS connection.
     */
    LDAPS,
    /**
     * Start TLS connection.
     */
    START_TLS
  }

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

  /**
   * {@inheritDoc}
   */
  public int hashCode()
  {
    return (type+ldapUrl.toLowerCase()).hashCode();
  }

  /**
   * {@inheritDoc}
   */
  public boolean equals(Object o)
  {
    boolean equals = false;
    if (this != o)
    {
      if ((o != null) &&
      (o instanceof PreferredConnection))
      {
        PreferredConnection p = (PreferredConnection)o;
        equals = type == p.getType() &&
        ldapUrl.equalsIgnoreCase(p.getLDAPURL());
      }
    }
    else
    {
      equals = true;
    }
    return equals;
  }


  /**
   * Commodity method that returns a PreferredConnection object with the
   * information on a given InitialLdapContext.
   * @param ctx the connection we retrieve the inforamtion from.
   * @return a preferred connection object.
   */
  public static PreferredConnection getPreferredConnection(
      InitialLdapContext ctx)
  {
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
    PreferredConnection cnx = new PreferredConnection(ldapUrl, type);
    return cnx;
  }
}
