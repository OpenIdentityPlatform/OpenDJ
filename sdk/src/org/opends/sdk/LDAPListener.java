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

package org.opends.sdk;



import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import com.sun.opends.sdk.ldap.LDAPListenerImpl;
import com.sun.opends.sdk.util.Validator;



/**
 * An LDAP server connection listener which waits for LDAP connection requests
 * to come in over the network and binds them to a {@link ServerConnection}.
 * <p>
 * The following code illustrates how to create a simple LDAP server:
 *
 * <pre>
 * class MyClientConnection implements ServerConnection&lt;Integer&gt;
 * {
 *   private final LDAPClientContext clientContext;
 *
 *
 *
 *   private MyClientConnection(LDAPClientContext clientContext)
 *   {
 *     this.clientContext = clientContext;
 *   }
 *
 *
 *
 *   public void add(Integer context, AddRequest request,
 *       ResultHandler&lt;Result&gt; handler,
 *       IntermediateResponseHandler intermediateResponseHandler)
 *       throws UnsupportedOperationException
 *   {
 *     // ...
 *   }
 *
 *   // ...
 *
 * }
 *
 *
 *
 * class MyServer implements ServerConnectionFactory&lt;LDAPClientContext, Integer&gt;
 * {
 *   public ServerConnection&lt;Integer&gt; accept(LDAPClientContext context)
 *   {
 *     System.out.println(&quot;Connection from: &quot; + context.getPeerAddress());
 *     return new MyClientConnection(context);
 *   }
 * }
 *
 *
 *
 * public static void main(String[] args) throws Exception
 * {
 *   LDAPListener listener = new LDAPListener(1389, new MyServer());
 *
 *   // ...
 *
 *   listener.close();
 * }
 * </pre>
 */
public final class LDAPListener implements Closeable
{
  // We implement the factory using the pimpl idiom in order have
  // cleaner Javadoc which does not expose implementation methods.

  private final LDAPListenerImpl impl;



  /**
   * Creates a new LDAP listener implementation which will listen for LDAP
   * client connections at the provided address.
   *
   * @param port
   *          The port to listen on.
   * @param factory
   *          The server connection factory which will be used to create server
   *          connections.
   * @throws IOException
   *           If an error occurred while trying to listen on the provided
   *           address.
   * @throws NullPointerException
   *           If {code factory} was {@code null}.
   */
  public LDAPListener(final int port,
      final ServerConnectionFactory<LDAPClientContext, Integer> factory)
      throws IOException, NullPointerException
  {
    this(port, factory, new LDAPListenerOptions());
  }



  /**
   * Creates a new LDAP listener implementation which will listen for LDAP
   * client connections at the provided address.
   *
   * @param port
   *          The port to listen on.
   * @param factory
   *          The server connection factory which will be used to create server
   *          connections.
   * @param options
   *          The LDAP listener options.
   * @throws IOException
   *           If an error occurred while trying to listen on the provided
   *           address.
   * @throws NullPointerException
   *           If {code factory} or {@code options} was {@code null}.
   */
  public LDAPListener(final int port,
      final ServerConnectionFactory<LDAPClientContext, Integer> factory,
      final LDAPListenerOptions options) throws IOException,
      NullPointerException
  {
    Validator.ensureNotNull(factory, options);
    final SocketAddress address = new InetSocketAddress(port);
    this.impl = new LDAPListenerImpl(address, factory, options);
  }



  /**
   * Creates a new LDAP listener implementation which will listen for LDAP
   * client connections at the provided address.
   *
   * @param port
   *          The port to listen on.
   * @param host
   *          The address to listen on.
   * @param factory
   *          The server connection factory which will be used to create server
   *          connections.
   * @throws IOException
   *           If an error occurred while trying to listen on the provided
   *           address.
   * @throws NullPointerException
   *           If {@code host} or {code factory} was {@code null}.
   */
  public LDAPListener(final int port, final String host,
      final ServerConnectionFactory<LDAPClientContext, Integer> factory)
      throws IOException, NullPointerException
  {
    this(port, host, factory, new LDAPListenerOptions());
  }



  /**
   * Creates a new LDAP listener implementation which will listen for LDAP
   * client connections at the provided address.
   *
   * @param port
   *          The port to listen on.
   * @param host
   *          The address to listen on.
   * @param factory
   *          The server connection factory which will be used to create server
   *          connections.
   * @param options
   *          The LDAP listener options.
   * @throws IOException
   *           If an error occurred while trying to listen on the provided
   *           address.
   * @throws NullPointerException
   *           If {@code host}, {code factory}, or {@code options} was {@code
   *           null}.
   */
  public LDAPListener(final int port, final String host,
      final ServerConnectionFactory<LDAPClientContext, Integer> factory,
      final LDAPListenerOptions options) throws IOException,
      NullPointerException
  {
    Validator.ensureNotNull(host, factory, options);
    final SocketAddress address = new InetSocketAddress(host, port);
    this.impl = new LDAPListenerImpl(address, factory, options);
  }



  /**
   * Creates a new LDAP listener implementation which will listen for LDAP
   * client connections at the provided address.
   *
   * @param address
   *          The address to listen on.
   * @param factory
   *          The server connection factory which will be used to create server
   *          connections.
   * @throws IOException
   *           If an error occurred while trying to listen on the provided
   *           address.
   * @throws NullPointerException
   *           If {@code address} or {code factory} was {@code null}.
   */
  public LDAPListener(final SocketAddress address,
      final ServerConnectionFactory<LDAPClientContext, Integer> factory)
      throws IOException, NullPointerException
  {
    this(address, factory, new LDAPListenerOptions());
  }



  /**
   * Creates a new LDAP listener implementation which will listen for LDAP
   * client connections at the provided address.
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
   * @throws NullPointerException
   *           If {@code address}, {code factory}, or {@code options} was
   *           {@code null}.
   */
  public LDAPListener(final SocketAddress address,
      final ServerConnectionFactory<LDAPClientContext, Integer> factory,
      final LDAPListenerOptions options) throws IOException,
      NullPointerException
  {
    Validator.ensureNotNull(address, factory, options);
    this.impl = new LDAPListenerImpl(address, factory, options);
  }



  /**
   * Closes this LDAP connection listener.
   *
   * @throws IOException
   *           If an IO error occurred while closing this listener.
   */
  public void close() throws IOException
  {
    impl.close();
  }

}
