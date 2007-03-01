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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.protocols.jmx;


/**
 * Represents a Ldap credential used for JMX connection authentication.
 * <p>
 * In JMX Connection, one and only one instance of this class is expected
 * to be found in each of the <code>publicCredentials</code> and
 * <code>privateCredentials</code> sets of
 * {@link javax.security.auth.Subject}. Those are the Jmx Client connection
 * that will handle incoming requests.
 */
public class Credential
{
  /**
   * The Client connection to be used.
   */
  private JmxClientConnection jmxClientConnection;


  /**
   * Default Constructor.
   *
   * @param jmxClientConnection
   *           The  representation of this credential is a Jmx Client connection
   * that will handle incoming requests.
   */
  public Credential(JmxClientConnection jmxClientConnection)
  {
    this.jmxClientConnection = jmxClientConnection;
  }

  /**
   * Returns the associated Client connection.
   *
   * @return the associated JmxClientConnection object. Can be null
   */
  public JmxClientConnection getClientConnection()
  {
    return jmxClientConnection;
  }
}
