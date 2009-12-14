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
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.opends.sdk.requests.*;
import org.opends.sdk.responses.BindResult;
import org.opends.sdk.responses.CompareResult;
import org.opends.sdk.responses.Result;
import org.opends.sdk.responses.SearchResultEntry;
import org.opends.sdk.schema.Schema;

import com.sun.opends.sdk.util.Validator;



/**
 * An authenticated connection factory can be used to create
 * pre-authenticated connections to a Directory Server.
 * <p>
 * The connections returned by an authenticated connection factory
 * support all operations with the exception of Bind requests. Attempts
 * to perform a Bind will result in an {@code
 * UnsupportedOperationException}.
 * <p>
 * In addition, the returned connections support retrieval of the
 * {@code BindResult} returned from the initial Bind request, or last
 * rebind.
 * <p>
 * Support for connection re-authentication is provided through the
 * {@link #setRebindAllowed} method which, if set to {@code true},
 * causes subsequent connections created using the factory to support
 * the {@code rebind} method.
 * <p>
 * If the Bind request fails for some reason (e.g. invalid credentials),
 * then the connection attempt will fail and an {@code
 * ErrorResultException} will be thrown.
 */
public final class AuthenticatedConnectionFactory
    implements
    ConnectionFactory<AuthenticatedConnectionFactory.AuthenticatedAsynchronousConnection>
{
  // We implement the factory using the pimpl idiom in order have
  // cleaner Javadoc which does not expose implementation methods from
  // AbstractConnectionFactory.

  private static final class Impl
      extends
      AbstractConnectionFactory<AuthenticatedConnectionFactory.AuthenticatedAsynchronousConnection>
      implements
      ConnectionFactory<AuthenticatedConnectionFactory.AuthenticatedAsynchronousConnection>
  {
    private final BindRequest request;

    private final ConnectionFactory<?> parentFactory;

    private boolean allowRebinds = false;



    private Impl(ConnectionFactory<?> factory, BindRequest request)
        throws NullPointerException
    {
      Validator.ensureNotNull(factory, request);
      this.parentFactory = factory;

      // FIXME: should do a defensive copy.
      this.request = request;
    }



    /**
     * {@inheritDoc}
     */
    public ConnectionFuture<AuthenticatedAsynchronousConnection> getAsynchronousConnection(
        ConnectionResultHandler<? super AuthenticatedAsynchronousConnection> handler)
    {
      // TODO: bug here? if allowRebind= false then bind will never
      // happen
      ConnectionFutureImpl future = new ConnectionFutureImpl(
          allowRebinds ? request : null, handler);
      future.connectFuture = parentFactory
          .getAsynchronousConnection(future);
      return future;
    }



    /**
     * {@inheritDoc}
     */
    public AuthenticatedConnection getConnection()
        throws ErrorResultException
    {
      return new AuthenticatedConnection(
          blockingGetAsynchronousConnection());
    }

  }



  private final Impl impl;



  /**
   * An authenticated synchronous connection supports all operations
   * except Bind operations.
   */
  public static final class AuthenticatedConnection extends
      SynchronousConnection
  {
    private final AuthenticatedAsynchronousConnection connection;



    private AuthenticatedConnection(
        AuthenticatedAsynchronousConnection connection)
    {
      super(connection);
      this.connection = connection;
    }



    /**
     * Bind operations are not supported by pre-authenticated
     * connections. This method will always throw {@code
     * UnsupportedOperationException}.
     */
    public BindResult bind(BindRequest request)
        throws UnsupportedOperationException
    {
      throw new UnsupportedOperationException();
    }



    /**
     * Bind operations are not supported by pre-authenticated
     * connections. This method will always throw {@code
     * UnsupportedOperationException}.
     */
    public BindResult bind(String name, String password)
        throws UnsupportedOperationException
    {
      throw new UnsupportedOperationException();
    }



    /**
     * Re-authenticates to the Directory Server using the bind request
     * associated with this connection. If re-authentication fails for
     * some reason then this connection will be automatically closed.
     *
     * @return The result of the operation.
     * @throws ErrorResultException
     *           If the result code indicates that the request failed
     *           for some reason.
     * @throws InterruptedException
     *           If the current thread was interrupted while waiting.
     * @throws UnsupportedOperationException
     *           If this connection does not support rebind operations.
     * @throws IllegalStateException
     *           If this connection has already been closed, i.e. if
     *           {@code isClosed() == true}.
     */
    public BindResult rebind() throws ErrorResultException,
        InterruptedException, UnsupportedOperationException,
        IllegalStateException
    {

      if (connection.request == null)
      {
        throw new UnsupportedOperationException();
      }
      return super.bind(connection.request);
    }



    /**
     * Returns an unmodifiable view of the Bind result which was
     * returned from the server after authentication.
     *
     * @return The Bind result which was returned from the server after
     *         authentication.
     */
    public BindResult getAuthenticatedBindResult()
    {
      return connection.getAuthenticatedBindResult();
    }
  }



  /**
   * An authenticated asynchronous connection supports all operations
   * except Bind operations.
   */
  public static final class AuthenticatedAsynchronousConnection
      implements AsynchronousConnection
  {

    private final BindRequest request;

    private volatile BindResult result;

    private final AsynchronousConnection connection;



    private AuthenticatedAsynchronousConnection(
        AsynchronousConnection connection, BindRequest request,
        BindResult result)
    {
      this.connection = connection;
      this.request = request;
      this.result = result;
    }



    /**
     * Returns an unmodifiable view of the Bind result which was
     * returned from the server after authentication.
     *
     * @return The Bind result which was returned from the server after
     *         authentication.
     */
    public BindResult getAuthenticatedBindResult()
    {
      return result;
    }



    public void abandon(AbandonRequest request)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      connection.abandon(request);
    }



    public ResultFuture<Result> add(AddRequest request,
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
    public ResultFuture<BindResult> bind(BindRequest request,
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



    public ResultFuture<CompareResult> compare(CompareRequest request,
        ResultHandler<? super CompareResult> handler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      return connection.compare(request, handler);
    }



    public ResultFuture<Result> delete(DeleteRequest request,
        ResultHandler<Result> handler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      return connection.delete(request, handler);
    }



    public <R extends Result> ResultFuture<R> extendedRequest(
        ExtendedRequest<R> request, ResultHandler<? super R> handler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      return connection.extendedRequest(request, handler);
    }



    public ResultFuture<Result> modify(ModifyRequest request,
        ResultHandler<Result> handler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      return connection.modify(request, handler);
    }



    public ResultFuture<Result> modifyDN(ModifyDNRequest request,
        ResultHandler<Result> handler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      return connection.modifyDN(request, handler);
    }



    /**
     * Re-authenticates to the Directory Server using the bind request
     * associated with this connection. If re-authentication fails for
     * some reason then this connection will be automatically closed.
     *
     * @param handler
     *          A result handler which can be used to asynchronously
     *          process the operation result when it is received, may be
     *          {@code null}.
     * @return A future representing the result of the operation.
     * @throws UnsupportedOperationException
     *           If this connection does not support rebind operations.
     * @throws IllegalStateException
     *           If this connection has already been closed, i.e. if
     *           {@code isClosed() == true}.
     */
    public ResultFuture<BindResult> rebind(
        ResultHandler<? super BindResult> handler)
        throws UnsupportedOperationException, IllegalStateException
    {
      if (request == null)
      {
        throw new UnsupportedOperationException();
      }

      // Wrap the client handler so that we can update the connection
      // state.
      final ResultHandler<? super BindResult> clientHandler = handler;

      ResultHandler<BindResult> handlerWrapper = new ResultHandler<BindResult>()
      {

        public void handleErrorResult(ErrorResultException error)
        {
          // This connection is now unauthenticated so prevent
          // further use.
          connection.close();

          if (clientHandler != null)
          {
            clientHandler.handleErrorResult(error);
          }
        }



        public void handleResult(BindResult result)
        {
          // Save the result.
          AuthenticatedAsynchronousConnection.this.result = result;

          if (clientHandler != null)
          {
            clientHandler.handleResult(result);
          }
        }

      };

      return connection.bind(request, handlerWrapper);
    }



    public void removeConnectionEventListener(
        ConnectionEventListener listener) throws NullPointerException
    {
      connection.removeConnectionEventListener(listener);
    }



    public ResultFuture<Result> search(SearchRequest request,
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
    public boolean isClosed()
    {
      return connection.isClosed();
    }



    /**
     * {@inheritDoc}
     */
    public ResultFuture<RootDSE> readRootDSE(
        ResultHandler<RootDSE> handler)
        throws UnsupportedOperationException, IllegalStateException
    {
      return connection.readRootDSE(handler);
    }



    /**
     * {@inheritDoc}
     */
    public ResultFuture<SearchResultEntry> readEntry(DN name,
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
    public ResultFuture<SearchResultEntry> searchSingleEntry(
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
    public ResultFuture<Schema> readSchemaForEntry(DN name,
        ResultHandler<Schema> handler)
        throws UnsupportedOperationException, IllegalStateException
    {
      return connection.readSchemaForEntry(name, handler);
    }



    /**
     * {@inheritDoc}
     */
    public ResultFuture<Schema> readSchema(DN name,
        ResultHandler<Schema> handler)
        throws UnsupportedOperationException, IllegalStateException
    {
      return connection.readSchema(name, handler);
    }

  }



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
   * @throws NullPointerException
   *           If {@code factory} or {@code request} was {@code null}.
   */
  public AuthenticatedConnectionFactory(ConnectionFactory<?> factory,
      BindRequest request) throws NullPointerException
  {
    impl = new Impl(factory, request);
  }



  private static final class ConnectionFutureImpl implements
      ConnectionFuture<AuthenticatedAsynchronousConnection>,
      ConnectionResultHandler<AsynchronousConnection>,
      ResultHandler<BindResult>
  {
    private volatile AuthenticatedAsynchronousConnection authenticatedConnection;

    private volatile AsynchronousConnection connection;

    private volatile ErrorResultException exception;

    private volatile ConnectionFuture<?> connectFuture;

    private volatile ResultFuture<BindResult> bindFuture;

    private final CountDownLatch latch = new CountDownLatch(1);

    private final ConnectionResultHandler<? super AuthenticatedAsynchronousConnection> handler;

    private boolean cancelled;

    private final BindRequest request;



    private ConnectionFutureImpl(
        BindRequest request,
        ConnectionResultHandler<? super AuthenticatedAsynchronousConnection> handler)
    {
      this.request = request;
      this.handler = handler;
    }



    public boolean cancel(boolean mayInterruptIfRunning)
    {
      cancelled = connectFuture.cancel(mayInterruptIfRunning)
          || bindFuture != null
          && bindFuture.cancel(mayInterruptIfRunning);
      if (cancelled)
      {
        latch.countDown();
      }
      return cancelled;
    }



    public AuthenticatedAsynchronousConnection get()
        throws InterruptedException, ErrorResultException
    {
      latch.await();
      if (cancelled)
      {
        throw new CancellationException();
      }
      if (exception != null)
      {
        throw exception;
      }
      return authenticatedConnection;
    }



    public AuthenticatedAsynchronousConnection get(long timeout,
        TimeUnit unit) throws InterruptedException, TimeoutException,
        ErrorResultException
    {
      latch.await(timeout, unit);
      if (cancelled)
      {
        throw new CancellationException();
      }
      if (exception != null)
      {
        throw exception;
      }
      return authenticatedConnection;
    }



    public boolean isCancelled()
    {
      return cancelled;
    }



    public boolean isDone()
    {
      return latch.getCount() == 0;
    }



    public void handleConnection(AsynchronousConnection connection)
    {
      this.connection = connection;
      this.bindFuture = this.connection.bind(request, this);
    }



    public void handleConnectionError(ErrorResultException error)
    {
      exception = error;
      latch.countDown();
    }



    public void handleResult(BindResult result)
    {
      // FIXME: should make the result unmodifiable.
      authenticatedConnection = new AuthenticatedAsynchronousConnection(
          connection, request, result);
      latch.countDown();
      if (handler != null)
      {
        handler.handleConnection(authenticatedConnection);
      }
    }



    public void handleErrorResult(ErrorResultException error)
    {
      // Ensure that the connection is closed.
      try
      {
        connection.close();
      }
      catch (Exception e)
      {
        // Ignore.
      }

      exception = error;
      latch.countDown();
      if (handler != null)
      {
        handler.handleConnectionError(exception);
      }
    }
  }



  /**
   * Specifies whether or not rebind requests are to be supported by
   * connections created by this authenticated connection factory.
   * <p>
   * Rebind requests are invoked using the connection's {@code rebind}
   * method which will throw an {@code UnsupportedOperationException} if
   * rebinds are not supported (the default).
   *
   * @param allowRebinds
   *          {@code true} if the {@code rebind} operation is to be
   *          supported, otherwise {@code false}.
   * @return A reference to this connection factory.
   */
  public AuthenticatedConnectionFactory setRebindAllowed(
      boolean allowRebinds)
  {
    impl.allowRebinds = allowRebinds;
    return this;
  }



  /**
   * Indicates whether or not rebind requests are to be supported by
   * connections created by this authenticated connection factory.
   * <p>
   * Rebind requests are invoked using the connection's {@code rebind}
   * method which will throw an {@code UnsupportedOperationException} if
   * rebinds are not supported (the default).
   *
   * @return allowRebinds {@code true} if the {@code rebind} operation
   *         is to be supported, otherwise {@code false}.
   */
  public boolean isRebindAllowed()
  {
    return impl.allowRebinds;
  }



  public ConnectionFuture<AuthenticatedAsynchronousConnection> getAsynchronousConnection(
      ConnectionResultHandler<? super AuthenticatedAsynchronousConnection> handler)
  {
    return impl.getAsynchronousConnection(handler);
  }



  public AuthenticatedConnection getConnection()
      throws ErrorResultException
  {
    return impl.getConnection();
  }

}
