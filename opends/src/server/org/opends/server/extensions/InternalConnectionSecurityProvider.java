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
package org.opends.server.extensions;



import java.nio.channels.SocketChannel;

import org.opends.server.api.ClientConnection;
import org.opends.server.api.ConnectionSecurityProvider;
import org.opends.server.types.DirectoryException;




/**
 * This provides an implementation of a connection security provider that is
 * intended to be used for internal client connections.  It is exactly the same
 * as the null connection security provider in that it doesn't actually protect
 * anything, but the <CODE>isSecure</CODE> method always returns
 * <CODE>true</CODE> because it is inherently secure by being an internal
 * connection.
 */
public class InternalConnectionSecurityProvider
       extends NullConnectionSecurityProvider
{



  /**
   * Creates a new instance of this internal connection security provider.
   */
  public InternalConnectionSecurityProvider()
  {
    super();
  }



  /**
   * Creates a new instance of this internal connection security provider with
   * the provided information.
   *
   * @param  clientConnection  The client connection for this security provider
   *                           instance.
   * @param  socketChannel     The socket channel for this security provider
   *                           instance.
   */
  protected InternalConnectionSecurityProvider(
                 ClientConnection clientConnection, SocketChannel socketChannel)
  {
    super(clientConnection, socketChannel);
  }



  /**
   * {@inheritDoc}
   */
  public String getSecurityMechanismName()
  {

    return "INTERNAL";
  }



  /**
   * {@inheritDoc}
   */
  public boolean isSecure()
  {

    // Internal connections are inherently secure.
    return true;
  }



  /**
   * Creates a new instance of this connection security provider that will be
   * used to encode and decode all communication on the provided client
   * connection.
   *
   * @param  clientConnection  The client connection with which this security
   *                           provider will be associated.
   * @param  socketChannel     The socket channel that may be used to
   *                           communicate with the client.
   *
   * @return  The created connection security provider instance.
   *
   * @throws  DirectoryException  If a problem occurs while creating a new
   *                              instance of this security provider for the
   *                              given client connection.
   */
  public ConnectionSecurityProvider newInstance(ClientConnection
                                                      clientConnection,
                                                SocketChannel socketChannel)
         throws DirectoryException
  {

    return new InternalConnectionSecurityProvider(clientConnection,
                                                  socketChannel);
  }
}

