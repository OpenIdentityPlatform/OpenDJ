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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.protocols.jmx;

import org.opends.server.api.ClientConnection;


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
  private ClientConnection clientConnection;


  /**
   * Default Constructor.
   *
   * @param clientConnection
   *           The  representation of this credential is a Jmx Client connection
   * that will handle incoming requests.
   */
  public Credential(ClientConnection clientConnection)
  {
    this.clientConnection = clientConnection;
  }

  /**
   * Returns the associated Client connection.
   *
   * @return the associated ClientConnection object. Can be null
   */
  public ClientConnection getClientConnection()
  {
    return clientConnection;
  }
}
