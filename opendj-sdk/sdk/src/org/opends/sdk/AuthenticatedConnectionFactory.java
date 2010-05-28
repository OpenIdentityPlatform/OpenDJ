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



import java.util.Collection;

import org.opends.sdk.requests.*;
import org.opends.sdk.responses.*;
import org.opends.sdk.schema.Schema;

import com.sun.opends.sdk.util.FutureResultTransformer;
import com.sun.opends.sdk.util.RecursiveFutureResult;



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
  public static final class AuthenticatedAsynchronousConnection implements
      AsynchronousConnection
  {

    private final AsynchronousConnection connection;



    private AuthenticatedAsynchronousConnection(
        final AsynchronousConnection connection)
    {
      this.connection = connection;
    }



    public FutureResult<Void> abandon(final AbandonRequest request)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      return connection.abandon(request);
    }



    public FutureResult<Result> add(final AddRequest request,
        final ResultHandler<Result> handler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      return connection.add(request, handler);
    }



    public FutureResult<Result> add(final AddRequest request,
        final ResultHandler<Result> resultHandler,
        final IntermediateResponseHandler intermediateResponseHandler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      return connection
          .add(request, resultHandler, intermediateResponseHandler);
    }



    public void addConnectionEventListener(
        final ConnectionEventListener listener) throws IllegalStateException,
        NullPointerException
    {
      connection.addConnectionEventListener(listener);
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



    public FutureResult<BindResult> bind(final BindRequest request,
        final ResultHandler<? super BindResult> resultHandler,
        final IntermediateResponseHandler intermediateResponseHandler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      return connection.bind(request, resultHandler,
          intermediateResponseHandler);
    }



    public void close()
    {
      connection.close();
    }



    public void close(final UnbindRequest request, final String reason)
        throws NullPointerException
    {
      connection.close(request, reason);
    }



    public FutureResult<CompareResult> compare(final CompareRequest request,
        final ResultHandler<? super CompareResult> handler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      return connection.compare(request, handler);
    }



    public FutureResult<CompareResult> compare(final CompareRequest request,
        final ResultHandler<? super CompareResult> resultHandler,
        final IntermediateResponseHandler intermediateResponseHandler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      return connection.compare(request, resultHandler,
          intermediateResponseHandler);
    }



    public FutureResult<Result> delete(final DeleteRequest request,
        final ResultHandler<Result> handler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      return connection.delete(request, handler);
    }



    public FutureResult<Result> delete(final DeleteRequest request,
        final ResultHandler<Result> resultHandler,
        final IntermediateResponseHandler intermediateResponseHandler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      return connection.delete(request, resultHandler,
          intermediateResponseHandler);
    }



    public <R extends ExtendedResult> FutureResult<R> extendedRequest(
        final ExtendedRequest<R> request, final ResultHandler<? super R> handler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      return connection.extendedRequest(request, handler);
    }



    public <R extends ExtendedResult> FutureResult<R> extendedRequest(
        final ExtendedRequest<R> request,
        final ResultHandler<? super R> resultHandler,
        final IntermediateResponseHandler intermediateResponseHandler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      return connection.extendedRequest(request, resultHandler,
          intermediateResponseHandler);
    }



    /**
     * {@inheritDoc}
     */
    public Connection getSynchronousConnection()
    {
      return new SynchronousConnection(this);
    }



    /**
     * {@inheritDoc}
     */
    public boolean isClosed()
    {
      return connection.isClosed();
    }



    /**
     * {@inheritDoc}
     */
    public boolean isValid()
    {
      return connection.isValid();
    }



    public FutureResult<Result> modify(final ModifyRequest request,
        final ResultHandler<Result> handler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      return connection.modify(request, handler);
    }



    public FutureResult<Result> modify(final ModifyRequest request,
        final ResultHandler<Result> resultHandler,
        final IntermediateResponseHandler intermediateResponseHandler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      return connection.modify(request, resultHandler,
          intermediateResponseHandler);
    }



    public FutureResult<Result> modifyDN(final ModifyDNRequest request,
        final ResultHandler<Result> handler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      return connection.modifyDN(request, handler);
    }



    public FutureResult<Result> modifyDN(final ModifyDNRequest request,
        final ResultHandler<Result> resultHandler,
        final IntermediateResponseHandler intermediateResponseHandler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      return connection.modifyDN(request, resultHandler,
          intermediateResponseHandler);
    }



    /**
     * {@inheritDoc}
     */
    public FutureResult<SearchResultEntry> readEntry(final DN name,
        final Collection<String> attributeDescriptions,
        final ResultHandler<? super SearchResultEntry> resultHandler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      return connection.readEntry(name, attributeDescriptions, resultHandler);
    }



    /**
     * {@inheritDoc}
     */
    public FutureResult<RootDSE> readRootDSE(
        final ResultHandler<RootDSE> handler)
        throws UnsupportedOperationException, IllegalStateException
    {
      return connection.readRootDSE(handler);
    }



    /**
     * {@inheritDoc}
     */
    public FutureResult<Schema> readSchema(final DN name,
        final ResultHandler<Schema> handler)
        throws UnsupportedOperationException, IllegalStateException
    {
      return connection.readSchema(name, handler);
    }



    /**
     * {@inheritDoc}
     */
    public FutureResult<Schema> readSchemaForEntry(final DN name,
        final ResultHandler<Schema> handler)
        throws UnsupportedOperationException, IllegalStateException
    {
      return connection.readSchemaForEntry(name, handler);
    }



    public void removeConnectionEventListener(
        final ConnectionEventListener listener) throws NullPointerException
    {
      connection.removeConnectionEventListener(listener);
    }



    public FutureResult<Result> search(final SearchRequest request,
        final ResultHandler<Result> resultHandler,
        final SearchResultHandler searchResulthandler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      return connection.search(request, resultHandler, searchResulthandler);
    }



    public FutureResult<Result> search(final SearchRequest request,
        final ResultHandler<Result> resultHandler,
        final SearchResultHandler searchResulthandler,
        final IntermediateResponseHandler intermediateResponseHandler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      return connection.search(request, resultHandler, searchResulthandler,
          intermediateResponseHandler);
    }



    /**
     * {@inheritDoc}
     */
    public FutureResult<SearchResultEntry> searchSingleEntry(
        final SearchRequest request,
        final ResultHandler<? super SearchResultEntry> resultHandler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      return connection.searchSingleEntry(request, resultHandler);
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
      final ResultHandler<AsynchronousConnection> handler)
  {
    final FutureResultImpl future = new FutureResultImpl(request, handler);
    future.futureConnectionResult.setFutureResult(parentFactory
        .getAsynchronousConnection(future.futureConnectionResult));
    return future.futureBindResult;
  }

}
