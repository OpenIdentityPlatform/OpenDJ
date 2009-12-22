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
import org.opends.sdk.responses.BindResult;
import org.opends.sdk.responses.CompareResult;
import org.opends.sdk.responses.Result;
import org.opends.sdk.responses.SearchResultEntry;
import org.opends.sdk.schema.Schema;

import com.sun.opends.sdk.util.FutureResultTransformer;
import com.sun.opends.sdk.util.RecursiveFutureResult;



/**
 * An authenticated connection factory can be used to create
 * pre-authenticated connections to a Directory Server.
 * <p>
 * The connections returned by an authenticated connection factory
 * support all operations with the exception of Bind requests. Attempts
 * to perform a Bind will result in an {@code
 * UnsupportedOperationException}.
 * <p>
 * If the Bind request fails for some reason (e.g. invalid credentials),
 * then the connection attempt will fail and an {@code
 * ErrorResultException} will be thrown.
 */
final class AuthenticatedConnectionFactory extends
    AbstractConnectionFactory<AsynchronousConnection>
{

  private final BindRequest request;

  private final ConnectionFactory<?> parentFactory;



  /**
   * Creates a new authenticated connection factory which will obtain
   * connections using the provided connection factory and immediately
   * perform the provided Bind request.
   *
   * @param factory
   *          The connection factory to use for connecting to the
   *          Directory Server.
   * @param request
   *          The Bind request to use for authentication.
   */
  AuthenticatedConnectionFactory(ConnectionFactory<?> factory,
      BindRequest request) throws NullPointerException
  {
    this.parentFactory = factory;

    // FIXME: should do a defensive copy.
    this.request = request;
  }



  /**
   * {@inheritDoc}
   */
  public FutureResult<AsynchronousConnection> getAsynchronousConnection(
      ResultHandler<? super AsynchronousConnection> handler)
  {
    FutureResultImpl future = new FutureResultImpl(request, handler);
    future.futureConnectionResult.setFutureResult(parentFactory
        .getAsynchronousConnection(future.futureConnectionResult));
    return future.futureBindResult;
  }



  /**
   * An authenticated asynchronous connection supports all operations
   * except Bind operations.
   */
  public static final class AuthenticatedAsynchronousConnection
      implements AsynchronousConnection
  {

    private final AsynchronousConnection connection;



    private AuthenticatedAsynchronousConnection(
        AsynchronousConnection connection)
    {
      this.connection = connection;
    }



    public void abandon(AbandonRequest request)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      connection.abandon(request);
    }



    public FutureResult<Result> add(AddRequest request,
        ResultHandler<Result> handler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      return connection.add(request, handler);
    }



    public void addConnectionEventListener(
        ConnectionEventListener listener) throws IllegalStateException,
        NullPointerException
    {
      connection.addConnectionEventListener(listener);
    }



    /**
     * Bind operations are not supported by pre-authenticated
     * connections. This method will always throw {@code
     * UnsupportedOperationException}.
     */
    public FutureResult<BindResult> bind(BindRequest request,
        ResultHandler<? super BindResult> handler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      throw new UnsupportedOperationException();
    }



    public void close()
    {
      connection.close();
    }



    public void close(UnbindRequest request, String reason)
        throws NullPointerException
    {
      connection.close(request, reason);
    }



    public FutureResult<CompareResult> compare(CompareRequest request,
        ResultHandler<? super CompareResult> handler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      return connection.compare(request, handler);
    }



    public FutureResult<Result> delete(DeleteRequest request,
        ResultHandler<Result> handler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      return connection.delete(request, handler);
    }



    public <R extends Result> FutureResult<R> extendedRequest(
        ExtendedRequest<R> request, ResultHandler<? super R> handler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      return connection.extendedRequest(request, handler);
    }



    public FutureResult<Result> modify(ModifyRequest request,
        ResultHandler<Result> handler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      return connection.modify(request, handler);
    }



    public FutureResult<Result> modifyDN(ModifyDNRequest request,
        ResultHandler<Result> handler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      return connection.modifyDN(request, handler);
    }



    public void removeConnectionEventListener(
        ConnectionEventListener listener) throws NullPointerException
    {
      connection.removeConnectionEventListener(listener);
    }



    public FutureResult<Result> search(SearchRequest request,
        ResultHandler<Result> resultHandler,
        SearchResultHandler searchResulthandler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      return connection.search(request, resultHandler,
          searchResulthandler);
    }



    /**
     * {@inheritDoc}
     */
    public boolean isValid() {
      return connection.isValid();
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
    public FutureResult<RootDSE> readRootDSE(
        ResultHandler<RootDSE> handler)
        throws UnsupportedOperationException, IllegalStateException
    {
      return connection.readRootDSE(handler);
    }



    /**
     * {@inheritDoc}
     */
    public FutureResult<SearchResultEntry> readEntry(DN name,
        Collection<String> attributeDescriptions,
        ResultHandler<? super SearchResultEntry> resultHandler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      return connection.readEntry(name, attributeDescriptions,
          resultHandler);
    }



    /**
     * {@inheritDoc}
     */
    public FutureResult<SearchResultEntry> searchSingleEntry(
        SearchRequest request,
        ResultHandler<? super SearchResultEntry> resultHandler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      return connection.searchSingleEntry(request, resultHandler);
    }



    /**
     * {@inheritDoc}
     */
    public FutureResult<Schema> readSchemaForEntry(DN name,
        ResultHandler<Schema> handler)
        throws UnsupportedOperationException, IllegalStateException
    {
      return connection.readSchemaForEntry(name, handler);
    }



    /**
     * {@inheritDoc}
     */
    public FutureResult<Schema> readSchema(DN name,
        ResultHandler<Schema> handler)
        throws UnsupportedOperationException, IllegalStateException
    {
      return connection.readSchema(name, handler);
    }

  }



  private static final class FutureResultImpl
  {
    private final FutureResultTransformer<BindResult, AsynchronousConnection> futureBindResult;

    private final RecursiveFutureResult<AsynchronousConnection, BindResult> futureConnectionResult;

    private final BindRequest bindRequest;

    private AsynchronousConnection connection;



    private FutureResultImpl(BindRequest request,
        ResultHandler<? super AsynchronousConnection> handler)
    {
      this.bindRequest = request;
      this.futureBindResult = new FutureResultTransformer<BindResult, AsynchronousConnection>(
          handler)
      {

        protected ErrorResultException transformErrorResult(
            ErrorResultException errorResult)
        {
          // Ensure that the connection is closed.
          try
          {
            connection.close();
            connection = null;
          }
          catch (Exception e)
          {
            // Ignore.
          }
          return errorResult;
        }



        protected AsynchronousConnection transformResult(
            BindResult result) throws ErrorResultException
        {
          // FIXME: should make the result unmodifiable.
          return new AuthenticatedAsynchronousConnection(connection);
        }

      };
      this.futureConnectionResult = new RecursiveFutureResult<AsynchronousConnection, BindResult>(
          futureBindResult)
      {

        protected FutureResult<? extends BindResult> chainResult(
            AsynchronousConnection innerResult,
            ResultHandler<? super BindResult> handler)
            throws ErrorResultException
        {
          connection = innerResult;
          return connection.bind(bindRequest, handler);
        }
      };
      futureBindResult.setFutureResult(futureConnectionResult);
    }

  }

}
