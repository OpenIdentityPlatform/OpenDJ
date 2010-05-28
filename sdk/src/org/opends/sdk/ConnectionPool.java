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
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;

import org.opends.sdk.requests.*;
import org.opends.sdk.responses.*;
import org.opends.sdk.schema.Schema;

import com.sun.opends.sdk.util.AbstractFutureResult;
import com.sun.opends.sdk.util.CompletedFutureResult;
import com.sun.opends.sdk.util.StaticUtils;



/**
 * A simple connection pool implementation.
 */
final class ConnectionPool extends AbstractConnectionFactory
{
  // Future used for waiting for pooled connections to become available.
  private static final class FuturePooledConnection extends
      AbstractFutureResult<AsynchronousConnection>
  {
    private FuturePooledConnection(
        final ResultHandler<? super AsynchronousConnection> handler)
    {
      super(handler);
    }



    /**
     * {@inheritDoc}
     */
    public int getRequestID()
    {
      return -1;
    }

  }



  private final class PooledConnectionWapper implements AsynchronousConnection,
      ConnectionEventListener
  {
    private final AsynchronousConnection connection;

    private volatile boolean isClosed;



    private PooledConnectionWapper(final AsynchronousConnection connection)
    {
      this.connection = connection;
      this.connection.addConnectionEventListener(this);
    }



    public FutureResult<Void> abandon(final AbandonRequest request)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      if (isClosed())
      {
        throw new IllegalStateException();
      }
      return connection.abandon(request);
    }



    public FutureResult<Result> add(final AddRequest request,
        final ResultHandler<Result> handler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      if (isClosed())
      {
        throw new IllegalStateException();
      }
      return connection.add(request, handler);
    }



    public FutureResult<Result> add(final AddRequest request,
        final ResultHandler<Result> resultHandler,
        final IntermediateResponseHandler intermediateResponseHandler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      if (isClosed())
      {
        throw new IllegalStateException();
      }
      return connection
          .add(request, resultHandler, intermediateResponseHandler);
    }



    public void addConnectionEventListener(
        final ConnectionEventListener listener) throws IllegalStateException,
        NullPointerException
    {
      if (isClosed())
      {
        throw new IllegalStateException();
      }
    }



    public FutureResult<BindResult> bind(final BindRequest request,
        final ResultHandler<? super BindResult> handler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      if (isClosed())
      {
        throw new IllegalStateException();
      }
      return connection.bind(request, handler);
    }



    public FutureResult<BindResult> bind(final BindRequest request,
        final ResultHandler<? super BindResult> resultHandler,
        final IntermediateResponseHandler intermediateResponseHandler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      if (isClosed())
      {
        throw new IllegalStateException();
      }
      return connection.bind(request, resultHandler,
          intermediateResponseHandler);
    }



    public void close()
    {
      synchronized (pool)
      {
        if (isClosed)
        {
          return;
        }
        isClosed = true;

        // Don't put invalid connections back in the pool.
        if (connection.isValid())
        {
          releaseConnection(connection);
          return;
        }
      }

      // Connection is no longer valid. Close outside of lock
      connection.removeConnectionEventListener(this);
      connection.close();
      if (StaticUtils.DEBUG_LOG.isLoggable(Level.WARNING))
      {
        StaticUtils.DEBUG_LOG.warning(String.format(
            "Dead connection released and closed. "
                + "numConnections: %d, poolSize: %d, " + "pendingFutures: %d",
            numConnections, pool.size(), pendingFutures.size()));
      }

      if (StaticUtils.DEBUG_LOG.isLoggable(Level.WARNING))
      {
        StaticUtils.DEBUG_LOG.warning(String.format(
            "Reconnect attempt starting. "
                + "numConnections: %d, poolSize: %d, " + "pendingFutures: %d",
            numConnections, pool.size(), pendingFutures.size()));
      }
      connectionFactory.getAsynchronousConnection(new ReconnectHandler());
    }



    public void close(final UnbindRequest request, final String reason)
        throws NullPointerException
    {
      close();
    }



    public FutureResult<CompareResult> compare(final CompareRequest request,
        final ResultHandler<? super CompareResult> handler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      if (isClosed())
      {
        throw new IllegalStateException();
      }
      return connection.compare(request, handler);
    }



    public FutureResult<CompareResult> compare(final CompareRequest request,
        final ResultHandler<? super CompareResult> resultHandler,
        final IntermediateResponseHandler intermediateResponseHandler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      if (isClosed())
      {
        throw new IllegalStateException();
      }
      return connection.compare(request, resultHandler,
          intermediateResponseHandler);
    }



    public void connectionClosed()
    {
      // Ignore - we intercept close via the close method.
    }



    public void connectionErrorOccurred(final boolean isDisconnectNotification,
        final ErrorResultException error)
    {
      // Remove this connection from the pool if its in there. If not,
      // just ignore and wait for the user to close and we can deal with it
      // there.
      if (pool.remove(this))
      {
        numConnections--;
        connection.removeConnectionEventListener(this);

        // FIXME: should still close the connection, but we need to be
        // careful that users of the pooled connection get a sensible
        // error if they continue to use it (i.e. not an NPE or ISE).

        if (StaticUtils.DEBUG_LOG.isLoggable(Level.WARNING))
        {
          StaticUtils.DEBUG_LOG.warning(String.format(
              "Connection error occured and removed from pool: "
                  + error.getMessage()
                  + " numConnections: %d, poolSize: %d, pendingFutures: %d",
              numConnections, pool.size(), pendingFutures.size()));
        }
      }
    }



    public void connectionReceivedUnsolicitedNotification(
        final ExtendedResult notification)
    {
      // Ignore
    }



    public FutureResult<Result> delete(final DeleteRequest request,
        final ResultHandler<Result> handler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      if (isClosed())
      {
        throw new IllegalStateException();
      }
      return connection.delete(request, handler);
    }



    public FutureResult<Result> delete(final DeleteRequest request,
        final ResultHandler<Result> resultHandler,
        final IntermediateResponseHandler intermediateResponseHandler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      if (isClosed())
      {
        throw new IllegalStateException();
      }
      return connection.delete(request, resultHandler,
          intermediateResponseHandler);
    }



    public <R extends ExtendedResult> FutureResult<R> extendedRequest(
        final ExtendedRequest<R> request, final ResultHandler<? super R> handler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      if (isClosed())
      {
        throw new IllegalStateException();
      }
      return connection.extendedRequest(request, handler);
    }



    public <R extends ExtendedResult> FutureResult<R> extendedRequest(
        final ExtendedRequest<R> request,
        final ResultHandler<? super R> resultHandler,
        final IntermediateResponseHandler intermediateResponseHandler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      if (isClosed())
      {
        throw new IllegalStateException();
      }
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
      return isClosed;
    }



    public boolean isValid()
    {
      return !isClosed && connection.isValid();
    }



    public FutureResult<Result> modify(final ModifyRequest request,
        final ResultHandler<Result> handler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      if (isClosed())
      {
        throw new IllegalStateException();
      }
      return connection.modify(request, handler);
    }



    public FutureResult<Result> modify(final ModifyRequest request,
        final ResultHandler<Result> resultHandler,
        final IntermediateResponseHandler intermediateResponseHandler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      if (isClosed())
      {
        throw new IllegalStateException();
      }
      return connection.modify(request, resultHandler,
          intermediateResponseHandler);
    }



    public FutureResult<Result> modifyDN(final ModifyDNRequest request,
        final ResultHandler<Result> handler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      if (isClosed())
      {
        throw new IllegalStateException();
      }
      return connection.modifyDN(request, handler);
    }



    public FutureResult<Result> modifyDN(final ModifyDNRequest request,
        final ResultHandler<Result> resultHandler,
        final IntermediateResponseHandler intermediateResponseHandler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      if (isClosed())
      {
        throw new IllegalStateException();
      }
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
      if (isClosed())
      {
        throw new IllegalStateException();
      }
      return connection.readEntry(name, attributeDescriptions, resultHandler);
    }



    /**
     * {@inheritDoc}
     */
    public FutureResult<RootDSE> readRootDSE(
        final ResultHandler<RootDSE> handler)
        throws UnsupportedOperationException, IllegalStateException
    {
      if (isClosed())
      {
        throw new IllegalStateException();
      }
      return connection.readRootDSE(handler);
    }



    /**
     * {@inheritDoc}
     */
    public FutureResult<Schema> readSchema(final DN name,
        final ResultHandler<Schema> handler)
        throws UnsupportedOperationException, IllegalStateException
    {
      if (isClosed())
      {
        throw new IllegalStateException();
      }
      return connection.readSchema(name, handler);
    }



    /**
     * {@inheritDoc}
     */
    public FutureResult<Schema> readSchemaForEntry(final DN name,
        final ResultHandler<Schema> handler)
        throws UnsupportedOperationException, IllegalStateException
    {
      if (isClosed())
      {
        throw new IllegalStateException();
      }
      return connection.readSchemaForEntry(name, handler);
    }



    public void removeConnectionEventListener(
        final ConnectionEventListener listener) throws NullPointerException
    {
      if (isClosed())
      {
        throw new IllegalStateException();
      }
    }



    public FutureResult<Result> search(final SearchRequest request,
        final ResultHandler<Result> resultHandler,
        final SearchResultHandler searchResulthandler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      if (isClosed())
      {
        throw new IllegalStateException();
      }
      return connection.search(request, resultHandler, searchResulthandler);
    }



    public FutureResult<Result> search(final SearchRequest request,
        final ResultHandler<Result> resultHandler,
        final SearchResultHandler searchResulthandler,
        final IntermediateResponseHandler intermediateResponseHandler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      if (isClosed())
      {
        throw new IllegalStateException();
      }
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
      if (isClosed())
      {
        throw new IllegalStateException();
      }
      return connection.searchSingleEntry(request, resultHandler);
    }
  }



  private class ReconnectHandler implements
      ResultHandler<AsynchronousConnection>
  {
    public void handleErrorResult(final ErrorResultException error)
    {
      // The reconnect failed. Fail the connect attempt.
      numConnections--;
      // The reconnect failed. The underlying connection factory
      // probably went
      // down. Just fail all pending futures
      synchronized (pool)
      {
        while (!pendingFutures.isEmpty())
        {
          pendingFutures.poll().handleErrorResult(error);
        }
      }
      if (StaticUtils.DEBUG_LOG.isLoggable(Level.WARNING))
      {
        StaticUtils.DEBUG_LOG.warning(String.format(
            "Reconnect failed. Failed all pending futures: "
                + error.getMessage()
                + " numConnections: %d, poolSize: %d, pendingFutures: %d",
            numConnections, pool.size(), pendingFutures.size()));
      }

    }



    public void handleResult(final AsynchronousConnection connection)
    {
      if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINE))
      {
        StaticUtils.DEBUG_LOG.finest(String.format("Reconnect succeeded. "
            + " numConnections: %d, poolSize: %d, pendingFutures: %d",
            numConnections, pool.size(), pendingFutures.size()));
      }
      synchronized (pool)
      {
        releaseConnection(connection);
      }
    }
  }



  private final ConnectionFactory connectionFactory;

  private volatile int numConnections;

  private final int poolSize;

  // FIXME: should use a better collection than this - CLQ?
  private final Queue<AsynchronousConnection> pool;

  private final ConcurrentLinkedQueue<FuturePooledConnection> pendingFutures;



  /**
   * Creates a new connection pool which will maintain {@code poolSize}
   * connections created using the provided connection factory.
   *
   * @param connectionFactory
   *          The connection factory to use for creating new connections.
   * @param poolSize
   *          The maximum size of the connection pool.
   */
  ConnectionPool(final ConnectionFactory connectionFactory, final int poolSize)
  {
    this.connectionFactory = connectionFactory;
    this.poolSize = poolSize;
    this.pool = new ConcurrentLinkedQueue<AsynchronousConnection>();
    this.pendingFutures = new ConcurrentLinkedQueue<FuturePooledConnection>();
  }



  @Override
  public synchronized FutureResult<AsynchronousConnection> getAsynchronousConnection(
      final ResultHandler<AsynchronousConnection> handler)
  {
    // This entire method is synchronized to ensure new connects are
    // done synchronously to avoid the "pending connect" case.
    AsynchronousConnection conn;
    synchronized (pool)
    {
      // Check to see if we have a connection in the pool
      conn = pool.poll();
      if (conn == null)
      {
        // Pool was empty. Maybe a new connection if pool size is not
        // reached
        if (numConnections >= poolSize)
        {
          // We reached max # of conns so wait for a connection to
          // become available.
          final FuturePooledConnection future = new FuturePooledConnection(
              handler);
          pendingFutures.add(future);

          if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINE))
          {
            StaticUtils.DEBUG_LOG.finest(String.format(
                "No connections available. Wait-listed"
                    + "numConnections: %d, poolSize: %d, pendingFutures: %d",
                numConnections, pool.size(), pendingFutures.size()));
          }

          return future;
        }
      }
    }

    if (conn == null)
    {
      try
      {
        // We can create a new connection.
        conn = connectionFactory.getAsynchronousConnection(null).get();
        numConnections++;
        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINE))
        {
          StaticUtils.DEBUG_LOG.finest(String.format(
              "New connection established and aquired. "
                  + "numConnections: %d, poolSize: %d, pendingFutures: %d",
              numConnections, pool.size(), pendingFutures.size()));
        }
      }
      catch (final ErrorResultException e)
      {
        if (handler != null)
        {
          handler.handleErrorResult(e);
        }
        return new CompletedFutureResult<AsynchronousConnection>(e);
      }
      catch (final InterruptedException e)
      {
        final ErrorResultException error = new ErrorResultException(Responses
            .newResult(ResultCode.CLIENT_SIDE_LOCAL_ERROR).setCause(e));
        if (handler != null)
        {
          handler.handleErrorResult(error);
        }
        return new CompletedFutureResult<AsynchronousConnection>(error);
      }
    }
    else
    {
      if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINE))
      {
        StaticUtils.DEBUG_LOG.finest(String.format(
            "Connection aquired from pool. "
                + "numConnections: %d, poolSize: %d, pendingFutures: %d",
            numConnections, pool.size(), pendingFutures.size()));
      }
    }

    final PooledConnectionWapper pooledConnection = new PooledConnectionWapper(
        conn);
    if (handler != null)
    {
      handler.handleResult(pooledConnection);
    }
    return new CompletedFutureResult<AsynchronousConnection>(pooledConnection);

  }



  private void releaseConnection(final AsynchronousConnection connection)
  {
    // See if there waiters pending.
    for (;;)
    {
      final PooledConnectionWapper pooledConnection = new PooledConnectionWapper(
          connection);
      final FuturePooledConnection future = pendingFutures.poll();

      if (future == null)
      {
        // No waiters - so drop out and add connection to pool.
        break;
      }

      future.handleResult(pooledConnection);

      if (!future.isCancelled())
      {
        // The future was not cancelled and the connection was
        // accepted.
        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINE))
        {
          StaticUtils.DEBUG_LOG.finest(String.format(
              "Connection released and directly "
                  + "given to waiter. numConnections: %d, poolSize: %d, "
                  + "pendingFutures: %d", numConnections, pool.size(),
              pendingFutures.size()));
        }
        return;
      }
    }

    // No waiters. Put back in pool.
    pool.offer(connection);
    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINE))
    {
      StaticUtils.DEBUG_LOG.finest(String.format(
          "Connection released to pool. numConnections: %d, "
              + "poolSize: %d, pendingFutures: %d", numConnections,
          pool.size(), pendingFutures.size()));
    }
  }
}
