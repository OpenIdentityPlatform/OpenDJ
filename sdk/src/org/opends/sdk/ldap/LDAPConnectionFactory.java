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
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package org.opends.sdk.ldap;



import org.opends.sdk.*;



/**
 * LDAP connection factory implementation.
 */
public final class LDAPConnectionFactory implements
    ConnectionFactory<AsynchronousConnection>
{
  // We implement the factory using the pimpl idiom in order have
  // cleaner Javadoc which does not expose implementation methods from
  // AbstractConnectionFactory.

  private final LDAPConnectionFactoryImpl impl;



  /**
   * Creates a plain LDAP connection to the Directory Server at the
   * specified host and port address.
   *
   * @param host
   *          The host name.
   * @param port
   *          The port number.
   * @return A connection to the Directory Server at the specified host
   *         and port address.
   * @throws ErrorResultException
   *           If the connection request failed for some reason.
   * @throws NullPointerException
   *           If {@code host} was {@code null}.
   */
  public static Connection connect(String host, int port)
      throws ErrorResultException, NullPointerException
  {
    return new LDAPConnectionFactory(host, port).getConnection();
  }



  /**
   * Creates a new LDAP connection factory which can be used to create
   * LDAP connections to the Directory Server at the provided host and
   * port address using default connection options.
   *
   * @param host
   *          The host name.
   * @param port
   *          The port number.
   * @throws NullPointerException
   *           If {@code host} was {@code null}.
   */
  public LDAPConnectionFactory(String host, int port)
      throws NullPointerException
  {
    this(host, port, LDAPConnectionOptions.defaultOptions());
  }



  /**
   * Creates a new LDAP connection factory which can be used to create
   * LDAP connections to the Directory Server at the provided host and
   * port address using provided connection options.
   *
   * @param host
   *          The host name.
   * @param port
   *          The port number.
   * @param options
   *          The LDAP connection options to use when creating
   *          connections.
   * @throws NullPointerException
   *           If {@code host} or {@code options} was {@code null}.
   */
  public LDAPConnectionFactory(String host, int port,
      LDAPConnectionOptions options) throws NullPointerException
  {
    this.impl = new LDAPConnectionFactoryImpl(host, port, options);
  }



  public <P> ConnectionFuture<AsynchronousConnection> getAsynchronousConnection(
      ConnectionResultHandler<? super AsynchronousConnection, P> handler,
      P p)
  {
    return impl.getAsynchronousConnection(handler, p);
  }



  public Connection getConnection() throws ErrorResultException
  {
    return impl.getConnection();
  }
}
