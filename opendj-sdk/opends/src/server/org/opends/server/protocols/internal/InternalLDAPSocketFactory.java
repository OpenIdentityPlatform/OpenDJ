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
package org.opends.server.protocols.internal;



import java.net.InetAddress;
import java.net.Socket;
import javax.net.SocketFactory;



/**
 * This class provides an implementation of a
 * {@code javax.net.SocketFactory} object that can be used to create
 * internal LDAP sockets.  This socket factory can be used with some
 * common LDAP SDKs (e.g., JNDI) in order to allow that SDK to be used
 * to perform internal operations within OpenDS with minimal changes
 * needed from what is required to perform external LDAP
 * communication.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public final class InternalLDAPSocketFactory
       extends SocketFactory
{
  /**
   * Creates a new instance of this internal LDAP socket factory.
   */
  public InternalLDAPSocketFactory()
  {
    // No implementation is required.
  }



  /**
   * Retrieves the default socket factory that should be used.  Note
   * that this method must be present for the implementation to work
   * properly.  Even though the superclass declares the same static
   * method and static methods are not generally overridden, that is
   * not the case here because the method is invoked through
   * reflection, and the superclass returns a bogus socket factory.
   *
   * @return  The default socket factory that should be used.
   */
  public static SocketFactory getDefault()
  {
    return new InternalLDAPSocketFactory();
  }



  /**
   * Creates a new internal LDAP socket.  The provided arguments will
   * be ignored, as they are not needed by this implementation.
   *
   * @param  host  The remote address to which the socket should be
   *               connected.
   * @param  port  The remote port to which the socket should be
   *               connected.
   *
   * @return  The created internal LDAP socket.
   */
  @Override()
  public Socket createSocket(InetAddress host, int port)
  {
    return new InternalLDAPSocket();
  }



  /**
   * Creates a new internal LDAP socket.  The provided arguments will
   * be ignored, as they are not needed by this implementation.
   *
   * @param  host  The remote address to which the socket should be
   *               connected.
   * @param  port  The remote port to which the socket should be
   *               connected.
   *
   * @return  The created internal LDAP socket.
   */
  @Override()
  public Socket createSocket(String host, int port)
  {
    return new InternalLDAPSocket();
  }



  /**
   * Creates a new internal LDAP socket.  The provided arguments will
   * be ignored, as they are not needed by this implementation.
   *
   * @param  host        The remote address to which the socket should
   *                     be connected.
   * @param  port        The remote port to which the socket should be
   *                     connected.
   * @param  clientHost  The local address to which the socket should
   *                     be bound.
   * @param  clientPort  The local port to which the socket should be
   *                     bound.
   *
   * @return  The created internal LDAP socket.
   */
  @Override()
  public Socket createSocket(InetAddress host, int port,
                             InetAddress clientHost, int clientPort)
  {
    return new InternalLDAPSocket();
  }



  /**
   * Creates a new internal LDAP socket.  The provided arguments will
   * be ignored, as they are not needed by this implementation.
   *
   * @param  host        The remote address to which the socket should
   *                     be connected.
   * @param  port        The remote port to which the socket should be
   *                     connected.
   * @param  clientHost  The local address to which the socket should
   *                     be bound.
   * @param  clientPort  The local port to which the socket should be
   *                     bound.
   *
   * @return  The created internal LDAP socket.
   */
  @Override()
  public Socket createSocket(String host, int port,
                             InetAddress clientHost, int clientPort)
  {
    return new InternalLDAPSocket();
  }



  /**
   * Retrieves a string representation of this internal LDAP socket
   * factory.
   *
   * @return  A string representation of this internal LDAP socket
   *          factory.
   */
  @Override()
  public String toString()
  {
    return "InternalLDAPSocketFactory";
  }
}

