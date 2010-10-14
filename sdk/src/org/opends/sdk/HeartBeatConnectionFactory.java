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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 */

package org.opends.sdk;



import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.opends.sdk.requests.*;
import org.opends.sdk.responses.*;
import org.opends.sdk.schema.Schema;

import com.sun.opends.sdk.util.FutureResultTransformer;



/**
 * An heart beat connection factory can be used to create connections that sends
 * a periodic search request to a Directory Server.
 */
final class HeartBeatConnectionFactory extends AbstractConnectionFactory
{
  /**
   * An asynchronous connection that sends heart beats and supports all
   * operations.
   */
  private final class AsynchronousConnectionImpl implements
      AsynchronousConnection, ConnectionEventListener, SearchResultHandler
  {
    private final AsynchronousConnection connection;

    private long lastSuccessfulPing;

    private FutureResult<Result> lastPingFuture;



    private AsynchronousConnectionImpl(final AsynchronousConnection connection)
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
        final ResultHandler<? super Result> handler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      return connection.add(request, handler);
    }



    public FutureResult<Result> add(final AddRequest request,
        final ResultHandler<? super Result> resultHandler,
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



    public FutureResult<BindResult> bind(final BindRequest request,
        final ResultHandler<? super BindResult> handler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      return connection.bind(request, handler);
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
      synchronized (activeConnections)
      {
        connection.removeConnectionEventListener(this);
        activeConnections.remove(this);
      }
      connection.close();
    }



    public void close(final UnbindRequest request, final String reason)
        throws NullPointerException
    {
      synchronized (activeConnections)
      {
        connection.removeConnectionEventListener(this);
        activeConnections.remove(this);
      }
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



    public void handleConnectionClosed()
    {
      // Ignore - we intercept close through the close method.
    }



    public void handleConnectionError(final boolean isDisconnectNotification,
        final ErrorResultException error)
    {
      synchronized (activeConnections)
      {
        connection.removeConnectionEventListener(this);
        activeConnections.remove(this);
      }
    }



    public void handleUnsolicitedNotification(final ExtendedResult notification)
    {
      // Do nothing
    }



    public FutureResult<Result> delete(final DeleteRequest request,
        final ResultHandler<? super Result> handler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      return connection.delete(request, handler);
    }



    public FutureResult<Result> delete(final DeleteRequest request,
        final ResultHandler<? super Result> resultHandler,
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
    public boolean handleEntry(SearchResultEntry entry)
    {
      // Ignore.
      return true;
    }



    public void handleErrorResult(final ErrorResultException error)
    {
      connection.close(Requests.newUnbindRequest(), "Heartbeat retured error: "
          + error);
    }



    /**
     * {@inheritDoc}
     */
    public boolean handleReference(SearchResultReference reference)
    {
      // Ignore.
      return true;
    }



    public void handleResult(final Result result)
    {
      lastSuccessfulPing = System.currentTimeMillis();
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
      return connection.isValid()
          && (lastSuccessfulPing <= 0 || System.currentTimeMillis()
              - lastSuccessfulPing < unit.toMillis(timeout) * 2);
    }



    public FutureResult<Result> modify(final ModifyRequest request,
        final ResultHandler<? super Result> handler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      return connection.modify(request, handler);
    }



    public FutureResult<Result> modify(final ModifyRequest request,
        final ResultHandler<? super Result> resultHandler,
        final IntermediateResponseHandler intermediateResponseHandler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      return connection.modify(request, resultHandler,
          intermediateResponseHandler);
    }



    public FutureResult<Result> modifyDN(final ModifyDNRequest request,
        final ResultHandler<? super Result> handler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      return connection.modifyDN(request, handler);
    }



    public FutureResult<Result> modifyDN(final ModifyDNRequest request,
        final ResultHandler<? super Result> resultHandler,
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
        final ResultHandler<? super RootDSE> handler)
        throws UnsupportedOperationException, IllegalStateException
    {
      return connection.readRootDSE(handler);
    }



    /**
     * {@inheritDoc}
     */
    public FutureResult<Schema> readSchema(final DN name,
        final ResultHandler<? super Schema> handler)
        throws UnsupportedOperationException, IllegalStateException
    {
      return connection.readSchema(name, handler);
    }



    /**
     * {@inheritDoc}
     */
    public FutureResult<Schema> readSchemaForEntry(final DN name,
        final ResultHandler<? super Schema> handler)
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
        final SearchResultHandler handler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      return connection.search(request, handler);
    }



    public FutureResult<Result> search(final SearchRequest request,
        final SearchResultHandler resultHandler,
        final IntermediateResponseHandler intermediateResponseHandler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      return connection.search(request, resultHandler,
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



    /**
     * {@inheritDoc}
     */
    public String toString()
    {
      StringBuilder builder = new StringBuilder();
      builder.append("HeartBeatConnection(");
      builder.append(connection);
      builder.append(')');
      return builder.toString();
    }
  }



  private final class FutureResultImpl extends
      FutureResultTransformer<AsynchronousConnection, AsynchronousConnection>
      implements ResultHandler<AsynchronousConnection>
  {

    private FutureResultImpl(
        final ResultHandler<? super AsynchronousConnection> handler)
    {
      super(handler);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    protected AsynchronousConnection transformResult(
        final AsynchronousConnection connection) throws ErrorResultException
    {
      final AsynchronousConnectionImpl heartBeatConnection = new AsynchronousConnectionImpl(
          connection);
      synchronized (activeConnections)
      {
        connection.addConnectionEventListener(heartBeatConnection);
        activeConnections.add(heartBeatConnection);
      }
      return heartBeatConnection;
    }

  }



  private final class HeartBeatThread extends Thread
  {
    private HeartBeatThread()
    {
      super("Heart Beat Thread");
      this.setDaemon(true);
    }



    @Override
    public void run()
    {
      long startTime;
      while (true)
      {
        startTime = System.currentTimeMillis();
        synchronized (activeConnections)
        {
          for (final AsynchronousConnectionImpl connection : activeConnections)
          {
            if (connection.lastPingFuture == null
                || connection.lastPingFuture.isDone())
            {
              connection.lastPingFuture = connection.search(heartBeat,
                  connection, null);
            }
          }
        }
        try
        {
          final long sleepTime = unit.toMillis(timeout)
              - (System.currentTimeMillis() - startTime);
          if (sleepTime > 0)
          {
            sleep(sleepTime);
          }
        }
        catch (final InterruptedException e)
        {
          // Ignore
        }
      }
    }
  }



  private final SearchRequest heartBeat;

  private final long timeout;

  // FIXME: use a single global scheduler?

  private final TimeUnit unit;

  private final List<AsynchronousConnectionImpl> activeConnections;

  private final ConnectionFactory parentFactory;

  private static final SearchRequest DEFAULT_SEARCH = Requests
      .newSearchRequest("", SearchScope.BASE_OBJECT, "(objectClass=*)", "1.1");



  /**
   * Creates a new heart-beat connection factory which will create connections
   * using the provided connection factory and periodically ping any created
   * connections in order to detect that they are still alive.
   *
   * @param connectionFactory
   *          The connection factory to use for creating connections.
   * @param timeout
   *          The time to wait between keepalive pings.
   * @param unit
   *          The time unit of the timeout argument.
   */
  HeartBeatConnectionFactory(final ConnectionFactory connectionFactory,
      final long timeout, final TimeUnit unit)
  {
    this(connectionFactory, timeout, unit, DEFAULT_SEARCH);
  }



  /**
   * Creates a new heart-beat connection factory which will create connections
   * using the provided connection factory and periodically ping any created
   * connections using the specified search request in order to detect that they
   * are still alive.
   *
   * @param connectionFactory
   *          The connection factory to use for creating connections.
   * @param timeout
   *          The time to wait between keepalive pings.
   * @param unit
   *          The time unit of the timeout argument.
   * @param heartBeat
   *          The search request to use when pinging connections.
   */
  HeartBeatConnectionFactory(final ConnectionFactory connectionFactory,
      final long timeout, final TimeUnit unit, final SearchRequest heartBeat)
  {
    this.heartBeat = heartBeat;
    this.timeout = timeout;
    this.unit = unit;
    this.activeConnections = new LinkedList<AsynchronousConnectionImpl>();
    this.parentFactory = connectionFactory;

    new HeartBeatThread().start();
  }



  @Override
  public FutureResult<AsynchronousConnection> getAsynchronousConnection(
      final ResultHandler<? super AsynchronousConnection> handler)
  {
    final FutureResultImpl future = new FutureResultImpl(handler);
    future.setFutureResult(parentFactory.getAsynchronousConnection(future));
    return future;
  }



  /**
   * {@inheritDoc}
   */
  public String toString()
  {
    final StringBuilder builder = new StringBuilder();
    builder.append("HeartBeatConnectionFactory(");
    builder.append(String.valueOf(parentFactory));
    builder.append(')');
    return builder.toString();
  }
}
