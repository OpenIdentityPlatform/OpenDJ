/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opendj3/legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opendj3/legal-notices/CDDLv1_0.txt.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions copyright 2011 ForgeRock AS
 */

package com.forgerock.opendj.ldap;



import static org.forgerock.opendj.ldap.ErrorResultException.newErrorResult;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.concurrent.ExecutionException;

import javax.net.ssl.SSLEngine;

import org.forgerock.opendj.ldap.*;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.StartTLSExtendedRequest;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.Result;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.EmptyCompletionHandler;
import org.glassfish.grizzly.filterchain.DefaultFilterChain;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;

import com.forgerock.opendj.util.CompletedFutureResult;
import com.forgerock.opendj.util.FutureResultTransformer;
import com.forgerock.opendj.util.RecursiveFutureResult;



/**
 * LDAP connection factory implementation.
 */
public final class LDAPConnectionFactoryImpl implements ConnectionFactory
{

  @SuppressWarnings("rawtypes")
  private final class FutureResultImpl implements
      CompletionHandler<org.glassfish.grizzly.Connection>
  {
    private final FutureResultTransformer<Result, Connection> futureStartTLSResult;
    private final RecursiveFutureResult<LDAPConnection, ExtendedResult> futureConnectionResult;
    private LDAPConnection connection;



    private FutureResultImpl(final ResultHandler<? super Connection> handler)
    {
      this.futureStartTLSResult = new FutureResultTransformer<Result, Connection>(
          handler)
      {

        @Override
        protected ErrorResultException transformErrorResult(
            final ErrorResultException errorResult)
        {
          // Ensure that the connection is closed.
          try
          {
            if (connection != null)
            {
              connection.close();
            }
          }
          catch (final Exception e)
          {
            // Ignore.
          }
          return errorResult;
        }



        @Override
        protected LDAPConnection transformResult(final Result result)
            throws ErrorResultException
        {
          return connection;
        }

      };

      this.futureConnectionResult = new RecursiveFutureResult<LDAPConnection, ExtendedResult>(
          futureStartTLSResult)
      {

        @Override
        protected FutureResult<? extends ExtendedResult> chainResult(
            final LDAPConnection innerResult,
            final ResultHandler<? super ExtendedResult> handler)
            throws ErrorResultException
        {
          connection = innerResult;

          if (options.getSSLContext() != null && options.useStartTLS())
          {
            final StartTLSExtendedRequest startTLS = Requests
                .newStartTLSExtendedRequest(options.getSSLContext());
            startTLS.addEnabledCipherSuite(options.getEnabledCipherSuites()
                .toArray(new String[options.getEnabledCipherSuites().size()]));
            startTLS.addEnabledProtocol(options.getEnabledProtocols().toArray(
                new String[options.getEnabledProtocols().size()]));
            return connection.extendedRequestAsync(startTLS, null, handler);
          }

          if (options.getSSLContext() != null)
          {
            try
            {
              connection.startTLS(options.getSSLContext(),
                  options.getEnabledProtocols(),
                  options.getEnabledCipherSuites(),
                  new EmptyCompletionHandler<SSLEngine>()
                  {
                    @Override
                    public void completed(final SSLEngine result)
                    {
                      handler.handleResult(null);
                    }



                    @Override
                    public void failed(final Throwable throwable)
                    {
                      handler.handleErrorResult(newErrorResult(
                          ResultCode.CLIENT_SIDE_CONNECT_ERROR,
                          throwable.getMessage(), throwable));
                    }
                  });
              return null;
            }
            catch (final IOException ioe)
            {
              throw newErrorResult(ResultCode.CLIENT_SIDE_CONNECT_ERROR,
                  ioe.getMessage(), ioe);
            }
          }
          handler.handleResult(null);
          return new CompletedFutureResult<ExtendedResult>(
              (ExtendedResult) null);
        }

      };

      futureStartTLSResult.setFutureResult(futureConnectionResult);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void cancelled()
    {
      // Ignore this.
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void completed(final org.glassfish.grizzly.Connection connection)
    {
      futureConnectionResult.handleResult(adaptConnection(connection));
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void failed(final Throwable throwable)
    {
      futureConnectionResult
          .handleErrorResult(adaptConnectionException(throwable));
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void updated(final org.glassfish.grizzly.Connection connection)
    {
      // Ignore this.
    }

  }



  private final SocketAddress socketAddress;
  private final TCPNIOTransport transport;
  private final FilterChain defaultFilterChain;
  private final LDAPClientFilter clientFilter;
  private final LDAPOptions options;



  /**
   * Creates a new LDAP connection factory implementation which can be used to
   * create connections to the Directory Server at the provided host and port
   * address using provided connection options.
   *
   * @param address
   *          The address of the Directory Server to connect to.
   * @param options
   *          The LDAP connection options to use when creating connections.
   */
  public LDAPConnectionFactoryImpl(final SocketAddress address,
      final LDAPOptions options)
  {
    if (options.getTCPNIOTransport() == null)
    {
      this.transport = DefaultTCPNIOTransport.getInstance();
    }
    else
    {
      this.transport = options.getTCPNIOTransport();
    }
    this.socketAddress = address;
    this.options = new LDAPOptions(options);
    this.clientFilter = new LDAPClientFilter(new LDAPReader(
        this.options.getDecodeOptions()), 0);
    this.defaultFilterChain = new DefaultFilterChain();
    this.defaultFilterChain.add(new TransportFilter());
    this.defaultFilterChain.add(clientFilter);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public Connection getConnection() throws ErrorResultException,
      InterruptedException
  {
    return getConnectionAsync(null).get();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public FutureResult<Connection> getConnectionAsync(
      final ResultHandler<? super Connection> handler)
  {
    final FutureResultImpl future = new FutureResultImpl(handler);

    try
    {
      future.futureConnectionResult.setFutureResult(transport.connect(
          socketAddress, future));
      return future.futureStartTLSResult;
    }
    catch (final IOException e)
    {
      final ErrorResultException result = adaptConnectionException(e);
      return new CompletedFutureResult<Connection>(result);
    }
  }



  /**
   * Returns the address of the Directory Server.
   *
   * @return The address of the Directory Server.
   */
  public SocketAddress getSocketAddress()
  {
    return socketAddress;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    final StringBuilder builder = new StringBuilder();
    builder.append("LDAPConnectionFactory(");
    builder.append(getSocketAddress().toString());
    builder.append(')');
    return builder.toString();
  }



  private LDAPConnection adaptConnection(
      final org.glassfish.grizzly.Connection<?> connection)
  {
    // Test shows that its much faster with non block writes but risk
    // running out of memory if the server is slow.
    connection.configureBlocking(true);
    connection.setProcessor(defaultFilterChain);

    final LDAPConnection ldapConnection = new LDAPConnection(connection,
        options);
    clientFilter.registerConnection(connection, ldapConnection);
    return ldapConnection;
  }



  private ErrorResultException adaptConnectionException(Throwable t)
  {
    if (t instanceof ExecutionException)
    {
      t = t.getCause();
    }

    return newErrorResult(ResultCode.CLIENT_SIDE_CONNECT_ERROR, t.getMessage(),
        t);
  }
}
