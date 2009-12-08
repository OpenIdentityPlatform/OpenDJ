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

import java.util.Stack;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

import org.opends.sdk.requests.*;
import org.opends.sdk.responses.BindResult;
import org.opends.sdk.responses.CompareResult;
import org.opends.sdk.responses.GenericExtendedResult;
import org.opends.sdk.responses.Result;

import com.sun.opends.sdk.util.StaticUtils;

/**
 * A simple connection pool implementation.
 */
public class ConnectionPool
    extends AbstractConnectionFactory<AsynchronousConnection> {
  private final ConnectionFactory<?> connectionFactory;
  private volatile int numConnections;
  private final int poolSize;

  // FIXME: should use a better collection than this - CLQ?
  private final Stack<AsynchronousConnection> pool;

  private final ConcurrentLinkedQueue<PendingConnectionFuture<?>> pendingFutures;
  private final Object lock = new Object();

  private final class PooledConnectionWapper
      implements AsynchronousConnection, ConnectionEventListener {
    private AsynchronousConnection connection;

    private PooledConnectionWapper(AsynchronousConnection connection) {
      this.connection = connection;
      this.connection.addConnectionEventListener(this);
    }

    public void abandon(AbandonRequest request)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException {
      if (connection == null) {
        throw new IllegalStateException();
      }
      connection.abandon(request);
    }

    public <P> ResultFuture<Result> add(
        AddRequest request,
        ResultHandler<Result, P> handler, P p)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException {
      if (connection == null) {
        throw new IllegalStateException();
      }
      return connection.add(request, handler, p);
    }

    public <P> ResultFuture<BindResult> bind(
        BindRequest request, ResultHandler<? super BindResult, P> handler, P p)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException {
      if (connection == null) {
        throw new IllegalStateException();
      }
      return connection.bind(request, handler, p);
    }

    public void close() {
      synchronized (lock) {
        try {
          // Don't put closed connections back in the pool.
          if (connection.isClosed()) {
            if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINE))
            {
              StaticUtils.DEBUG_LOG.finest(String
                  .format("Dead connection released to pool. " +
                  "numConnections: %d, poolSize: %d, pendingFutures: %d",
                  numConnections, pool.size(), pendingFutures.size()));
            }
            return;
          }

          // See if there waiters pending
          PendingConnectionFuture<?> future = pendingFutures.poll();
          if (future != null) {
            PooledConnectionWapper pooledConnection =
                new PooledConnectionWapper(connection);
            future.connection(pooledConnection);
            if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINE))
            {
              StaticUtils.DEBUG_LOG.finest(String
                  .format("Connection released to pool and directly " +
                  "given to waiter. numConnections: %d, poolSize: %d, " +
                  "pendingFutures: %d", numConnections, pool.size(),
                  pendingFutures.size()));
            }
            return;
          }

          // No waiters. Put back in pool.
          pool.push(connection);
          if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINE))
          {
            StaticUtils.DEBUG_LOG.finest(String
                .format("Connection released to pool. " +
                "numConnections: %d, poolSize: %d, pendingFutures: %d",
                numConnections, pool.size(), pendingFutures.size()));
          }
        }
        finally {
          // Null out the underlying connection to prevent further use.
          connection = null;
        }
      }
    }

    public void close(UnbindRequest request, String reason)
        throws NullPointerException {
      close();
    }

    public <P> ResultFuture<CompareResult> compare(
        CompareRequest request, ResultHandler<? super CompareResult, P> handler,
        P p) throws UnsupportedOperationException, IllegalStateException,
        NullPointerException {
      if (connection == null) {
        throw new IllegalStateException();
      }
      return connection.compare(request, handler, p);
    }

    public <P> ResultFuture<Result> delete(
        DeleteRequest request, ResultHandler<Result, P> handler, P p)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException {
      if (connection == null) {
        throw new IllegalStateException();
      }
      return connection.delete(request, handler, p);
    }

    public <R extends Result, P> ResultFuture<R> extendedRequest(
        ExtendedRequest<R> request, ResultHandler<? super R, P> handler, P p)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException {
      if (connection == null) {
        throw new IllegalStateException();
      }
      return connection.extendedRequest(request, handler, p);
    }

    public <P> ResultFuture<Result> modify(
        ModifyRequest request, ResultHandler<Result, P> handler, P p)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException {
      if (connection == null) {
        throw new IllegalStateException();
      }
      return connection.modify(request, handler, p);
    }

    public <P> ResultFuture<Result> modifyDN(
        ModifyDNRequest request, ResultHandler<Result, P> handler, P p)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException {
      if (connection == null) {
        throw new IllegalStateException();
      }
      return connection.modifyDN(request, handler, p);
    }

    public <P> ResultFuture<Result> search(
        SearchRequest request, ResultHandler<Result, P> resultHandler,
        SearchResultHandler<P> searchResulthandler, P p)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException {
      if (connection == null) {
        throw new IllegalStateException();
      }
      return connection.search(request, resultHandler, searchResulthandler, p);
    }

    public void addConnectionEventListener(ConnectionEventListener listener)
        throws IllegalStateException, NullPointerException {
      if (connection == null) {
        throw new IllegalStateException();
      }
    }

    public void removeConnectionEventListener(ConnectionEventListener listener)
        throws NullPointerException {
      if (connection == null) {
        throw new IllegalStateException();
      }
    }

    /**
     * {@inheritDoc}
     */
    public boolean isClosed()
    {
      return connection == null;
    }

    public void connectionReceivedUnsolicitedNotification(
        GenericExtendedResult notification) {
      // Ignore
    }

    public void connectionErrorOccurred(
        boolean isDisconnectNotification, ErrorResultException error) {
      synchronized (lock) {
        // Remove this connection from the pool if its in there
        pool.remove(this);
        numConnections--;
        connection.removeConnectionEventListener(this);

        // FIXME: should still close the connection, but we need to be
        // careful that users of the pooled connection get a sensible
        // error if they continue to use it (i.e. not an NPE or ISE).

        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINE))
        {
          StaticUtils.DEBUG_LOG.finest(String
              .format("Connection error occured: " + error.getMessage() +
              " numConnections: %d, poolSize: %d, pendingFutures: %d",
              numConnections, pool.size(), pendingFutures.size()));
        }
      }
    }
  }

  private static final class CompletedConnectionFuture
      implements ConnectionFuture<AsynchronousConnection> {
    private final PooledConnectionWapper connection;

    private CompletedConnectionFuture(PooledConnectionWapper connection) {
      this.connection = connection;
    }

    public boolean cancel(boolean mayInterruptIfRunning) {
      return false;
    }

    public AsynchronousConnection get()
        throws InterruptedException, ErrorResultException {
      return connection;
    }

    public AsynchronousConnection get(long timeout, TimeUnit unit)
        throws InterruptedException, TimeoutException, ErrorResultException {
      return connection;
    }

    public boolean isCancelled() {
      return false;
    }

    public boolean isDone() {
      return true;
    }
  }

  private final class PendingConnectionFuture<P>
      implements ConnectionFuture<AsynchronousConnection> {
    private volatile boolean isCancelled;
    private volatile PooledConnectionWapper connection;
    private volatile ErrorResultException err;
    private final ConnectionResultHandler<? super AsynchronousConnection, P>
        handler;
    private final P p;
    private final CountDownLatch latch = new CountDownLatch(1);

    private PendingConnectionFuture(
        P p,
        ConnectionResultHandler<? super AsynchronousConnection, P> handler) {
      this.handler = handler;
      this.p = p;
    }

    public synchronized boolean cancel(boolean mayInterruptIfRunning) {
      return pendingFutures.remove(this) && (isCancelled = true);
    }

    public AsynchronousConnection get()
        throws InterruptedException, ErrorResultException {
      latch.await();
      if (err != null) {
        throw err;
      }
      return connection;
    }

    public AsynchronousConnection get(long timeout, TimeUnit unit)
        throws InterruptedException, TimeoutException, ErrorResultException {
      latch.await(timeout, unit);
      if (err != null) {
        throw err;
      }
      return connection;
    }

    public synchronized boolean isCancelled() {
      return isCancelled;
    }

    public boolean isDone() {
      return latch.getCount() == 0;
    }

    private void connection(PooledConnectionWapper connection) {
      this.connection = connection;
      if (handler != null) {
        handler.handleConnection(p, connection);
      }
      latch.countDown();
    }

    private void error(ErrorResultException e) {
      this.err = e;
      if (handler != null) {
        handler.handleConnectionError(p, e);
      }
      latch.countDown();
    }
  }



  /**
   * Creates a new connection pool which will maintain {@code poolSize}
   * connections created using the provided connection factory.
   *
   * @param connectionFactory
   *          The connection factory to use for creating new
   *          connections.
   * @param poolSize
   *          The maximum size of the connection pool.
   */
  public ConnectionPool(ConnectionFactory<?> connectionFactory, int poolSize) {
    this.connectionFactory = connectionFactory;
    this.poolSize = poolSize;
    this.pool = new Stack<AsynchronousConnection>();
    this.pendingFutures = new ConcurrentLinkedQueue<PendingConnectionFuture<?>>();
  }

  private final class WrapConnectionResultHandler
      implements ConnectionResultHandler<AsynchronousConnection, Void> {
    private final PendingConnectionFuture<?> future;

    private WrapConnectionResultHandler(PendingConnectionFuture<?> future) {
      this.future = future;
    }

    public void handleConnection(
        java.lang.Void p,
        AsynchronousConnection connection) {
      PooledConnectionWapper pooledConnection =
          new PooledConnectionWapper(connection);
      future.connection(pooledConnection);
    }

    public void handleConnectionError(
        java.lang.Void p,
        ErrorResultException error) {
      future.error(error);
    }
  }

  public <P> ConnectionFuture<AsynchronousConnection> getAsynchronousConnection(
      ConnectionResultHandler<? super AsynchronousConnection, P> handler, P p) {
    synchronized (lock) {
      // Check to see if we have a connection in the pool


      if (!pool.isEmpty()) {
        AsynchronousConnection conn = pool.pop();
        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINE))
        {
          StaticUtils.DEBUG_LOG.finest(String
              .format("Connection aquired from pool. " +
              "numConnections: %d, poolSize: %d, pendingFutures: %d",
              numConnections, pool.size(), pendingFutures.size()));
        }
        PooledConnectionWapper pooledConnection =
            new PooledConnectionWapper(conn);
        if (handler != null) {
          handler.handleConnection(p, pooledConnection);
        }
        return new CompletedConnectionFuture(pooledConnection);
      }

      PendingConnectionFuture<P> pendingFuture =
          new PendingConnectionFuture<P>(p, handler);
      // Pool was empty. Maybe a new connection if pool size is not reached
      if (numConnections < poolSize) {
        numConnections++;
        WrapConnectionResultHandler wrapHandler =
            new WrapConnectionResultHandler(pendingFuture);
        connectionFactory.getAsynchronousConnection(wrapHandler, null);
        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINE))
        {
          StaticUtils.DEBUG_LOG.finest(String
              .format("New connection established and aquired. " +
              "numConnections: %d, poolSize: %d, pendingFutures: %d",
              numConnections, pool.size(), pendingFutures.size()));
        }
      } else {
        // Have to wait
        pendingFutures.add(pendingFuture);
        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINE))
        {
          StaticUtils.DEBUG_LOG.finest(String
              .format("No connections available. Wait-listed" +
              "numConnections: %d, poolSize: %d, pendingFutures: %d",
              numConnections, pool.size(), pendingFutures.size()));
        }
      }

      return pendingFuture;
    }
  }
}
