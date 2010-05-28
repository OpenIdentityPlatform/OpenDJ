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
 *      Copyright 2010 Sun Microsystems, Inc.
 */

package com.sun.opends.sdk.ldap;



import java.io.Closeable;
import java.io.IOException;
import java.net.SocketAddress;

import javax.net.ssl.SSLContext;

import org.opends.sdk.DecodeOptions;
import org.opends.sdk.LDAPClientContext;
import org.opends.sdk.LDAPListenerOptions;
import org.opends.sdk.ServerConnectionFactory;

import com.sun.grizzly.filterchain.DefaultFilterChain;
import com.sun.grizzly.filterchain.FilterChain;
import com.sun.grizzly.filterchain.TransportFilter;
import com.sun.grizzly.nio.transport.TCPNIOServerConnection;
import com.sun.grizzly.nio.transport.TCPNIOTransport;
import com.sun.grizzly.ssl.SSLEngineConfigurator;
import com.sun.grizzly.ssl.SSLFilter;



/**
 * LDAP listener implementation.
 */
public final class LDAPListenerImpl implements Closeable
{
  private final TCPNIOTransport transport;
  private final FilterChain defaultFilterChain;
  private final ServerConnectionFactory<LDAPClientContext, Integer> connectionFactory;
  private final TCPNIOServerConnection serverConnection;



  /**
   * Creates a new LDAP listener implementation which will listen for LDAP
   * client connections using the provided address and connection options.
   *
   * @param address
   *          The address to listen on.
   * @param factory
   *          The server connection factory which will be used to create server
   *          connections.
   * @param options
   *          The LDAP listener options.
   * @throws IOException
   *           If an error occurred while trying to listen on the provided
   *           address.
   */
  public LDAPListenerImpl(final SocketAddress address,
      final ServerConnectionFactory<LDAPClientContext, Integer> factory,
      final LDAPListenerOptions options) throws IOException
  {
    TCPNIOTransport tmpTransport = null;
    if (options instanceof GrizzlyLDAPListenerOptions)
    {
      tmpTransport = ((GrizzlyLDAPListenerOptions) options)
          .getTCPNIOTransport();
    }
    if (tmpTransport == null)
    {
      tmpTransport = GlobalTransportFactory.getInstance().createTCPTransport();
    }
    this.transport = tmpTransport;
    this.connectionFactory = factory;
    this.defaultFilterChain = new DefaultFilterChain();
    this.defaultFilterChain.add(new TransportFilter());

    if (options.getSSLContext() != null)
    {
      final SSLContext sslContext = options.getSSLContext();
      SSLEngineConfigurator sslEngineConfigurator;

      sslEngineConfigurator = new SSLEngineConfigurator(sslContext, false,
          false, false);
      this.defaultFilterChain.add(new SSLFilter(sslEngineConfigurator, null));
    }

    this.defaultFilterChain.add(new LDAPServerFilter(this, new LDAPReader(
        new DecodeOptions(options.getDecodeOptions())), 0));

    this.serverConnection = transport.bind(address, options.getBacklog());
    this.serverConnection.setProcessor(defaultFilterChain);
  }



  /**
   * {@inheritDoc}
   */
  public void close() throws IOException
  {
    transport.unbind(serverConnection);
  }



  ServerConnectionFactory<LDAPClientContext, Integer> getConnectionFactory()
  {
    return connectionFactory;
  }



  FilterChain getDefaultFilterChain()
  {
    return defaultFilterChain;
  }
}
