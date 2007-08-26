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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.admin.ads;
import org.opends.server.types.OpenDsException;

import javax.naming.NamingException;

import org.opends.admin.ads.util.ApplicationTrustManager;

/**
 * This class represents the Exception that can occur while reading server
 * configuration through the TopologyCache class.
 */
public class TopologyCacheException extends OpenDsException {

  private static final long serialVersionUID = 1709535837273360382L;
  private Type type;
  private String ldapUrl;
  private ApplicationTrustManager trustManager;

  /**
   * Error type.
   */
  public enum Type
  {
    /**
     * Error reading the ADS.
     */
    GENERIC_READING_ADS,
    /**
     * Creating connection to a particular server.
     */
    GENERIC_CREATING_CONNECTION,
    /**
     * Error reading the configuration of a particular server.
     */
    GENERIC_READING_SERVER,
    /**
     * The DN provided in the DirContext of ADS is not of a global
     * administrator.
     */
    NOT_GLOBAL_ADMINISTRATOR,
    /**
     * Not enough permissions to read the server configuration.
     */
    NO_PERMISSIONS,
    /**
     * Timeout reading the configuration of a particular server.
     */
    TIMEOUT,
    /**
     * Unexpected error.
     */
    BUG
  }

  /**
   * Constructor for the exception that must be generated when an
   * ADSContextException occurs.
   * @param ace the exception which is the cause of this exception.
   */
  public TopologyCacheException(ADSContextException ace)
  {
    type = Type.GENERIC_READING_ADS;
    initCause(ace);
  }

  /**
  * Constructor for a generic Exception.
  * @param type the type of this exception.
  * @param t the cause of this exception.
  */
  public TopologyCacheException(Type type, Throwable t)
  {
    this.type = type;
    initCause(t);
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
    this.type = type;
    initCause(ne);
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
    String hostPort = ldapUrl.substring(index + 2);
    return hostPort;
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
