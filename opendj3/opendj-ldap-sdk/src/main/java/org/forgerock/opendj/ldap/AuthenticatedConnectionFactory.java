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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 */

package org.forgerock.opendj.ldap;



import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.responses.BindResult;

import com.forgerock.opendj.util.AbstractConnectionFactory;
import com.forgerock.opendj.util.AsynchronousConnectionDecorator;
import com.forgerock.opendj.util.FutureResultTransformer;
import com.forgerock.opendj.util.RecursiveFutureResult;



/**
 * An authenticated connection factory can be used to create pre-authenticated
 * connections to a Directory Server.
 * <p>
 * The connections returned by an authenticated connection factory support all
 * operations with the exception of Bind requests. Attempts to perform a Bind
 * will result in an {@code UnsupportedOperationException}.
 * <p>
 * If the Bind request fails for some reason (e.g. invalid credentials), then
 * the connection attempt will fail and an {@code ErrorResultException} will be
 * thrown.
 */
final class AuthenticatedConnectionFactory extends AbstractConnectionFactory
{

  /**
   * An authenticated asynchronous connection supports all operations except
   * Bind operations.
   */
  public static final class AuthenticatedAsynchronousConnection extends
      AsynchronousConnectionDecorator
  {

    private AuthenticatedAsynchronousConnection(
        final AsynchronousConnection connection)
    {
      super(connection);
    }



    /**
     * Bind operations are not supported by pre-authenticated connections. This
     * method will always throw {@code UnsupportedOperationException}.
     */
    public FutureResult<BindResult> bind(final BindRequest request,
        final ResultHandler<? super BindResult> handler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      throw new UnsupportedOperationException();
    }



    /**
     * Bind operations are not supported by pre-authenticated connections. This
     * method will always throw {@code UnsupportedOperationException}.
     */
    public FutureResult<BindResult> bind(final BindRequest request,
        final ResultHandler<? super BindResult> resultHandler,
        final IntermediateResponseHandler intermediateResponseHandler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      throw new UnsupportedOperationException();
    }



    /**
     * {@inheritDoc}
     */
    public String toString()
    {
      StringBuilder builder = new StringBuilder();
      builder.append("AuthenticatedConnection(");
      builder.append(connection);
      builder.append(')');
      return builder.toString();
    }

  }



  private static final class FutureResultImpl
  {
    private final FutureResultTransformer<BindResult, AsynchronousConnection> futureBindResult;

    private final RecursiveFutureResult<AsynchronousConnection, BindResult> futureConnectionResult;

    private final BindRequest bindRequest;

    private AsynchronousConnection connection;



    private FutureResultImpl(final BindRequest request,
        final ResultHandler<? super AsynchronousConnection> handler)
    {
      this.bindRequest = request;
      this.futureBindResult = new FutureResultTransformer<BindResult, AsynchronousConnection>(
          handler)
      {

        @Override
        protected ErrorResultException transformErrorResult(
            final ErrorResultException errorResult)
        {
          // Ensure that the connection is closed.
          try
          {
            connection.close();
            connection = null;
          }
          catch (final Exception e)
          {
            // Ignore.
          }
          return errorResult;
        }



        @Override
        protected AsynchronousConnection transformResult(final BindResult result)
            throws ErrorResultException
        {
          return new AuthenticatedAsynchronousConnection(connection);
        }

      };
      this.futureConnectionResult = new RecursiveFutureResult<AsynchronousConnection, BindResult>(
          futureBindResult)
      {

        @Override
        protected FutureResult<? extends BindResult> chainResult(
            final AsynchronousConnection innerResult,
            final ResultHandler<? super BindResult> handler)
            throws ErrorResultException
        {
          connection = innerResult;
          return connection.bind(bindRequest, handler);
        }
      };
      futureBindResult.setFutureResult(futureConnectionResult);
    }

  }



  private final BindRequest request;

  private final ConnectionFactory parentFactory;



  /**
   * Creates a new authenticated connection factory which will obtain
   * connections using the provided connection factory and immediately perform
   * the provided Bind request.
   *
   * @param factory
   *          The connection factory to use for connecting to the Directory
   *          Server.
   * @param request
   *          The Bind request to use for authentication.
   */
  AuthenticatedConnectionFactory(final ConnectionFactory factory,
      final BindRequest request)
  {
    this.parentFactory = factory;

    // FIXME: should do a defensive copy.
    this.request = request;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public FutureResult<AsynchronousConnection> getAsynchronousConnection(
      final ResultHandler<? super AsynchronousConnection> handler)
  {
    final FutureResultImpl future = new FutureResultImpl(request, handler);
    future.futureConnectionResult.setFutureResult(parentFactory
        .getAsynchronousConnection(future.futureConnectionResult));
    return future.futureBindResult;
  }



  /**
   * {@inheritDoc}
   */
  public String toString()
  {
    final StringBuilder builder = new StringBuilder();
    builder.append("AuthenticatedConnectionFactory(");
    builder.append(String.valueOf(parentFactory));
    builder.append(')');
    return builder.toString();
  }

}
