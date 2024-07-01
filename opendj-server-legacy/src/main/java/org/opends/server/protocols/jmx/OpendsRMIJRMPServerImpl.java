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
 * Portions Copyright 2015 ForgeRock AS.
 */
package org.opends.server.protocols.jmx;

import java.io.IOException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.util.Map;
import java.util.Set;

import javax.management.remote.rmi.RMIJRMPServerImpl;
import javax.management.remote.rmi.RMIConnection;
import javax.security.auth.Subject;

/**
 * An <code>OpendsRMIJRMPServerImpl</code> object that is exported
 * through JRMP and that creates client connections as RMI objects exported
 * through JRMP.
 */
public class OpendsRMIJRMPServerImpl
    extends RMIJRMPServerImpl
{
  /**
   * Creates a new RMIServer object that will be exported on the given port
   * using the given socket factories.
   *
   * @param port
   *        the port on which this object and the RMIConnectionImpl objects
   *        it creates will be exported. Can be zero, to indicate any
   *        available port
   * @param csf
   *        the client socket factory for the created RMI objects. Can be
   *        null.
   * @param ssf
   *        the server socket factory for the created RMI objects. Can be
   *        null.
   * @param env
   *        the environment map. Can be null.
   * @throws IOException
   *         if the RMIServer object cannot be created.
   */
  public OpendsRMIJRMPServerImpl(int port, RMIClientSocketFactory csf,
      RMIServerSocketFactory ssf, Map<String, ?> env) throws IOException
  {
    super(port, csf, ssf, env);
  }

  /** {@inheritDoc} */
  @Override
  protected RMIConnection makeClient(String connectionId, Subject subject)
      throws IOException
  {
    if (subject != null)
    {
      Set<Credential> privateCreds = subject
          .getPrivateCredentials(Credential.class);
      JmxClientConnection jmxClientConnection =
       (JmxClientConnection)
       privateCreds.iterator().next().getClientConnection();
      jmxClientConnection.jmxConnectionID = connectionId;
    }

    return super.makeClient(connectionId, subject);
  }

}
