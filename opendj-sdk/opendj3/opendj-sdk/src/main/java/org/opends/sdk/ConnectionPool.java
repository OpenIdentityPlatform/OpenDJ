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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

import org.opends.sdk.requests.*;
import org.opends.sdk.responses.*;
import org.opends.sdk.schema.Schema;

import com.forgerock.opendj.util.AsynchronousFutureResult;
import com.forgerock.opendj.util.CompletedFutureResult;
import com.forgerock.opendj.util.StaticUtils;
import com.forgerock.opendj.util.Validator;



/**
 * A simple connection pool implementation.
 */
final class ConnectionPool extends AbstractConnectionFactory
{

  /**
   * This result handler is invoked when an attempt to add a new connection to
   * the pool completes.
   */
  private final class ConnectionResultHandler implements
      ResultHandler<AsynchronousConnection>
  {
    /**
     * {@inheritDoc}
     */
    @Override
    public void handleErrorResult(final ErrorResultException error)
    {
      // Connection attempt failed, so decrease the pool size.
      currentPoolSize.release();

      if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINE))
      {
        StaticUtils.DEBUG_LOG.fine(String.format(
            "Connection attempt failed: " + error.getMessage()
                + " currentPoolSize=%d, poolSize=%d",
            poolSize - currentPoolSize.availablePermits(), poolSize));
      }

      QueueElement holder;
      synchronized (queue)
      {
        if (queue.isEmpty() || !queue.getFirst().isWaitingFuture())
        {
          // No waiting futures.
          return;
        }
        else
        {
          holder = queue.removeFirst();
        }
      }

      // There was waiting future, so close it.
      holder.getWaitingFuture().handleErrorResult(error);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void handleResult(final AsynchronousConnection connection)
    {
      if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINE))
      {
        StaticUtils.DEBUG_LOG.fine(String.format(
            "Connection attempt succeeded: "
                + " currentPoolSize=%d, poolSize=%d",
                poolSize - currentPoolSize.availablePermits(), poolSize));
      }

      publishConnection(connection);
    }
  }



  /**
   * A pooled connection is passed to the client. It wraps an underlying
   * "pooled" connection obtained from the underlying factory and lasts until
   * the client application closes this connection. More specifically, pooled
   * connections are not actually stored in the internal queue.
   */
  private final class PooledConnection implements AsynchronousConnection
  {
    // Connection event listeners registed against this pooled connection should
    // have the same life time as the pooled connection.
    private final List<ConnectionEventListener> listeners =
      new CopyOnWriteArrayList<ConnectionEventListener>();

    private final AsynchronousConnection connection;

    private final AtomicBoolean isClosed = new AtomicBoolean(false);



    PooledConnection(final AsynchronousConnection connection)
    {
      this.connection = connection;
    }



    /**
     * {@inheritDoc}
     */
    @Override
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



    /**
     * {@inheritDoc}
     */
    @Override
    public FutureResult<Result> add(final AddRequest request,
        final ResultHandler<? super Result> handler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      if (isClosed())
      {
        throw new IllegalStateException();
      }
      return connection.add(request, handler);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public FutureResult<Result> add(final AddRequest request,
        final ResultHandler<? super Result> resultHandler,
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



    /**
     * {@inheritDoc}
     */
    @Override
    public void addConnectionEventListener(
        final ConnectionEventListener listener) throws IllegalStateException,
        NullPointerException
    {
      Validator.ensureNotNull(listener);
      if (isClosed())
      {
        throw new IllegalStateException();
      }
      listeners.add(listener);
    }



    /**
     * {@inheritDoc}
     */
    @Override
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



    /**
     * {@inheritDoc}
     */
    @Override
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



    /**
     * {@inheritDoc}
     */
    @Override
    public void close()
    {
      if (!isClosed.compareAndSet(false, true))
      {
        // Already closed.
        return;
      }

      // Don't put invalid connections back in the pool.
      if (connection.isValid())
      {
        publishConnection(connection);
      }
      else
      {
        // The connection may have been disconnected by the remote server, but
        // the server may still be available. In order to avoid leaving pending
        // futures hanging indefinitely, we should try to reconnect immediately.

        // Close the dead connection.
        connection.close();

        // Try to get a new connection to replace it.
        factory.getAsynchronousConnection(connectionResultHandler);

        if (StaticUtils.DEBUG_LOG.isLoggable(Level.WARNING))
        {
          StaticUtils.DEBUG_LOG.warning(String.format(
              "Connection no longer valid. "
                  + "currentPoolSize=%d, poolSize=%d",
                  poolSize - currentPoolSize.availablePermits(), poolSize));
        }
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void close(final UnbindRequest request, final String reason)
        throws NullPointerException
    {
      close();
    }



    /**
     * {@inheritDoc}
     */
    @Override
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



    /**
     * {@inheritDoc}
     */
    @Override
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



    /**
     * {@inheritDoc}
     */
    @Override
    public FutureResult<Result> delete(final DeleteRequest request,
        final ResultHandler<? super Result> handler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      if (isClosed())
      {
        throw new IllegalStateException();
      }
      return connection.delete(request, handler);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public FutureResult<Result> delete(final DeleteRequest request,
        final ResultHandler<? super Result> resultHandler,
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



    /**
     * {@inheritDoc}
     */
    @Override
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



    /**
     * {@inheritDoc}
     */
    @Override
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
    @Override
    public Connection getSynchronousConnection()
    {
      return new SynchronousConnection(this);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isClosed()
    {
      return isClosed.get();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isValid()
    {
      return connection.isValid() && !isClosed();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public FutureResult<Result> modify(final ModifyRequest request,
        final ResultHandler<? super Result> handler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      if (isClosed())
      {
        throw new IllegalStateException();
      }
      return connection.modify(request, handler);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public FutureResult<Result> modify(final ModifyRequest request,
        final ResultHandler<? super Result> resultHandler,
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



    /**
     * {@inheritDoc}
     */
    @Override
    public FutureResult<Result> modifyDN(final ModifyDNRequest request,
        final ResultHandler<? super Result> handler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      if (isClosed())
      {
        throw new IllegalStateException();
      }
      return connection.modifyDN(request, handler);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public FutureResult<Result> modifyDN(final ModifyDNRequest request,
        final ResultHandler<? super Result> resultHandler,
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
    @Override
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
    @Override
    public FutureResult<RootDSE> readRootDSE(
        final ResultHandler<? super RootDSE> handler)
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
    @Override
    public FutureResult<Schema> readSchema(final DN name,
        final ResultHandler<? super Schema> handler)
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
    @Override
    public FutureResult<Schema> readSchemaForEntry(final DN name,
        final ResultHandler<? super Schema> handler)
        throws UnsupportedOperationException, IllegalStateException
    {
      if (isClosed())
      {
        throw new IllegalStateException();
      }
      return connection.readSchemaForEntry(name, handler);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void removeConnectionEventListener(
        final ConnectionEventListener listener) throws NullPointerException
    {
      Validator.ensureNotNull(listener);
      if (isClosed())
      {
        throw new IllegalStateException();
      }
      listeners.remove(listener);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public FutureResult<Result> search(final SearchRequest request,
        final SearchResultHandler handler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      if (isClosed())
      {
        throw new IllegalStateException();
      }
      return connection.search(request, handler);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public FutureResult<Result> search(final SearchRequest request,
        final SearchResultHandler resultHandler,
        final IntermediateResponseHandler intermediateResponseHandler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      if (isClosed())
      {
        throw new IllegalStateException();
      }
      return connection.search(request, resultHandler,
          intermediateResponseHandler);
    }



    /**
     * {@inheritDoc}
     */
    @Override
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



    /**
     * {@inheritDoc}
     */
    @Override
    public String toString()
    {
      final StringBuilder builder = new StringBuilder();
      builder.append("PooledConnection(");
      builder.append(connection);
      builder.append(')');
      return builder.toString();
    }
  }



  /**
   * A queue element is either a pending connection request future awaiting an
   * {@code AsynchronousConnection} or it is an unused
   * {@code AsynchronousConnection} awaiting a connection request.
   */
  private static final class QueueElement
  {
    private final Object value;



    QueueElement(final AsynchronousConnection connection)
    {
      this.value = connection;
    }



    QueueElement(final ResultHandler<? super AsynchronousConnection> handler)
    {
      this.value = new AsynchronousFutureResult<AsynchronousConnection>(handler);
    }



    AsynchronousConnection getWaitingConnection()
    {
      if (value instanceof AsynchronousConnection)
      {
        return (AsynchronousConnection) value;
      }
      else
      {
        throw new IllegalStateException();
      }
    }



    @SuppressWarnings("unchecked")
    AsynchronousFutureResult<AsynchronousConnection> getWaitingFuture()
    {
      if (value instanceof AsynchronousFutureResult)
      {
        return (AsynchronousFutureResult<AsynchronousConnection>) value;
      }
      else
      {
        throw new IllegalStateException();
      }
    }



    boolean isWaitingFuture()
    {
      return value instanceof AsynchronousFutureResult;
    }



    public String toString()
    {
      return String.valueOf(value);
    }
  }



  // Guarded by queue.
  private final LinkedList<QueueElement> queue = new LinkedList<QueueElement>();

  private final ConnectionFactory factory;

  private final int poolSize;

  private final Semaphore currentPoolSize;

  private final ResultHandler<AsynchronousConnection> connectionResultHandler =
    new ConnectionResultHandler();



  /**
   * Creates a new connection pool which will maintain {@code poolSize}
   * connections created using the provided connection factory.
   *
   * @param factory
   *          The connection factory to use for creating new connections.
   * @param poolSize
   *          The maximum size of the connection pool.
   */
  ConnectionPool(final ConnectionFactory factory, final int poolSize)
  {
    this.factory = factory;
    this.poolSize = poolSize;
    this.currentPoolSize = new Semaphore(poolSize);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public FutureResult<AsynchronousConnection> getAsynchronousConnection(
      final ResultHandler<? super AsynchronousConnection> handler)
  {
    QueueElement holder;
    synchronized (queue)
    {
      if (queue.isEmpty() || queue.getFirst().isWaitingFuture())
      {
        holder = new QueueElement(handler);
        queue.add(holder);
      }
      else
      {
        holder = queue.removeFirst();
      }
    }

    if (!holder.isWaitingFuture())
    {
      // There was a completed connection attempt.
      final AsynchronousConnection connection = holder.getWaitingConnection();
      final PooledConnection pooledConnection = new PooledConnection(connection);
      if (handler != null)
      {
        handler.handleResult(pooledConnection);
      }
      return new CompletedFutureResult<AsynchronousConnection>(pooledConnection);
    }
    else
    {
      // Grow the pool if needed.
      final FutureResult<AsynchronousConnection> future = holder
          .getWaitingFuture();
      if (!future.isDone() && currentPoolSize.tryAcquire())
      {
        factory.getAsynchronousConnection(connectionResultHandler);
      }
      return future;
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    final StringBuilder builder = new StringBuilder();
    builder.append("ConnectionPool(");
    builder.append(String.valueOf(factory));
    builder.append(',');
    builder.append(poolSize);
    builder.append(')');
    return builder.toString();
  }



  private void publishConnection(final AsynchronousConnection connection)
  {
    QueueElement holder;
    synchronized (queue)
    {
      if (queue.isEmpty() || !queue.getFirst().isWaitingFuture())
      {
        holder = new QueueElement(connection);
        queue.add(holder);
        return;
      }
      else
      {
        holder = queue.removeFirst();
      }
    }

    // There was waiting future, so close it.
    final PooledConnection pooledConnection = new PooledConnection(connection);
    holder.getWaitingFuture().handleResult(pooledConnection);
  }
}
