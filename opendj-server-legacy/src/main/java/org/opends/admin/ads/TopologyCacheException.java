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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.admin.ads;

import javax.naming.NamingException;

import org.opends.admin.ads.util.ApplicationTrustManager;
import org.opends.server.types.OpenDsException;

/**
 * This class represents the Exception that can occur while reading server
 * configuration through the TopologyCache class.
 */
public class TopologyCacheException extends OpenDsException {

  private static final long serialVersionUID = 1709535837273360382L;
  private final Type type;
  private final String ldapUrl;
  private final ApplicationTrustManager trustManager;

  /** Error type. */
  public enum Type
  {
    /** Error reading the ADS. */
    GENERIC_READING_ADS,
    /** Creating connection to a particular server. */
    GENERIC_CREATING_CONNECTION,
    /** Error reading the configuration of a particular server. */
    GENERIC_READING_SERVER,
    /** The DN provided in the DirContext of ADS is not of a global administrator. */
    NOT_GLOBAL_ADMINISTRATOR,
    /** Not enough permissions to read the server configuration. */
    NO_PERMISSIONS,
    /** Timeout reading the configuration of a particular server. */
    TIMEOUT,
    /** Unexpected error. */
    BUG
  }

  /**
   * Constructor for the exception that must be generated when an
   * ADSContextException occurs.
   * @param ace the exception which is the cause of this exception.
   */
  TopologyCacheException(ADSContextException ace)
  {
    super(ace);
    type = Type.GENERIC_READING_ADS;
    ldapUrl = null;
    trustManager = null;
  }

  /**
  * Constructor for a generic Exception.
  * @param type the type of this exception.
  * @param t the cause of this exception.
  */
  public TopologyCacheException(Type type, Throwable t)
  {
    super(t);
    this.type = type;
    this.ldapUrl = null;
    this.trustManager = null;
  }

  /**
   * Constructor for the exception that must be generated when a
   * NamingException occurs.
   * @param type the type of this exception.
   * @param ne the NamingException that generated this exception.
   * @param trustManager the ApplicationTrustManager used when the
   * NamingException occurred.
   * @param ldapUrl the LDAP URL of the server we where connected to (or trying
   * to connect) when the NamingException was generated.
   */
  public TopologyCacheException(Type type, NamingException ne,
      ApplicationTrustManager trustManager, String ldapUrl)
  {
    super(ne);
    this.type = type;
    this.ldapUrl = ldapUrl;
    this.trustManager = trustManager;
  }

  /**
   * Returns the type of this exception.
   * @return the type of this exception.
   */
  public Type getType()
  {
    return type;
  }

  /**
   * Returns the LDAP URL of the server we where connected to (or trying
   * to connect) when this exception was generated.
   * @return the LDAP URL of the server we where connected to (or trying
   * to connect) when this exception was generated.
   */
  public String getLdapUrl()
  {
    return ldapUrl;
  }

  /**
   * Returns the host port representation of the server we where connected to
   * (or trying to connect) when this exception was generated.
   * @return the host port representation of the server we where connected to
   * (or trying to connect) when this exception was generated.
   */
  public String getHostPort()
  {
    int index = ldapUrl.indexOf("//");
    return ldapUrl.substring(index + 2);
  }

  /**
   * Returns the ApplicationTrustManager that we were using when this exception
   * was generated.
   * @return the ApplicationTrustManager that we were using when this exception
   * was generated.
   */
  public ApplicationTrustManager getTrustManager()
  {
    return trustManager;
  }
}
