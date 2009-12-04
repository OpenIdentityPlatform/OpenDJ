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

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.opends.sdk.requests.*;
import org.opends.sdk.responses.BindResult;
import org.opends.sdk.responses.CompareResult;
import org.opends.sdk.responses.GenericExtendedResult;
import org.opends.sdk.responses.Result;

import com.sun.opends.sdk.util.Validator;

/**
 * An heart beat connection factory can be used to create
 * connections that sends a periodic search request to a Directory Server.
 */
public class HeartBeatConnectionFactory
    extends AbstractConnectionFactory<
    HeartBeatConnectionFactory.HeartBeatAsynchronousConnection> {
  private final SearchRequest heartBeat;
  private final int interval;
  private final List<HeartBeatAsynchronousConnection> activeConnections;
  private final ConnectionFactory<?> parentFactory;

  private boolean stopRequested;

  public HeartBeatConnectionFactory(
      ConnectionFactory<?> parentFactory,
      int interval) {
    this(parentFactory, Requests.newSearchRequest("", SearchScope.BASE_OBJECT,
        "(objectClass=*)", "1.1"), interval);
  }

  public HeartBeatConnectionFactory(
      ConnectionFactory<?> parentFactory,
      SearchRequest heartBeat, int interval) {
    Validator.ensureNotNull(parentFactory, heartBeat);
    this.heartBeat = heartBeat;
    this.interval = interval;
    this.activeConnections = new LinkedList<HeartBeatAsynchronousConnection>();
    this.parentFactory = parentFactory;

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        stopRequested = true;
      }
    });

    new HeartBeatThread().start();
  }

  /**
   * An asynchronous connection that sends heart beats and supports all
   * operations.
   */
  public final class HeartBeatAsynchronousConnection
      implements AsynchronousConnection, ConnectionEventListener,
      ResultHandler<Result, Void> {
    private final AsynchronousConnection connection;

    public HeartBeatAsynchronousConnection(AsynchronousConnection connection) {
      this.connection = connection;
    }

    public void abandon(AbandonRequest request)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException {
      connection.abandon(request);
    }

    public <P> ResultFuture<Result> add(
        AddRequest request,
        ResultHandler<Result, P> handler, P p)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException {
      return connection.add(request, handler, p);
    }

    public <P> ResultFuture<BindResult> bind(
        BindRequest request, ResultHandler<? super BindResult, P> handler, P p)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException {
      return connection.bind(request, handler, p);
    }

    public void close() {
      synchronized (activeConnections) {
        connection.removeConnectionEventListener(this);
        activeConnections.remove(this);
      }
      connection.close();
    }

    public void close(UnbindRequest request, String reason)
        throws NullPointerException {
      synchronized (activeConnections) {
        connection.removeConnectionEventListener(this);
        activeConnections.remove(this);
      }
      connection.close(request, reason);
    }

    public <P> ResultFuture<CompareResult> compare(
        CompareRequest request, ResultHandler<? super CompareResult, P> handler,
        P p) throws UnsupportedOperationException, IllegalStateException,
        NullPointerException {
      return connection.compare(request, handler, p);
    }

    public <P> ResultFuture<Result> delete(
        DeleteRequest request,
        ResultHandler<Result, P> handler,
        P p)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException {
      return connection.delete(request, handler, p);
    }

    public <R extends Result, P> ResultFuture<R> extendedRequest(
        ExtendedRequest<R> request, ResultHandler<? super R, P> handler, P p)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException {
      return connection.extendedRequest(request, handler, p);
    }

    public <P> ResultFuture<Result> modify(
        ModifyRequest request,
        ResultHandler<Result, P> handler,
        P p)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException {
      return connection.modify(request, handler, p);
    }

    public <P> ResultFuture<Result> modifyDN(
        ModifyDNRequest request,
        ResultHandler<Result, P> handler,
        P p)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException {
      return connection.modifyDN(request, handler, p);
    }

    public <P> ResultFuture<Result> search(
        SearchRequest request, ResultHandler<Result, P> resultHandler,
        SearchResultHandler<P> searchResultHandler, P p)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException {
      return connection.search(request, resultHandler, searchResultHandler, p);
    }

    public void addConnectionEventListener(ConnectionEventListener listener)
        throws IllegalStateException, NullPointerException {
      connection.addConnectionEventListener(listener);
    }

    public void removeConnectionEventListener(ConnectionEventListener listener)
        throws NullPointerException {
      connection.removeConnectionEventListener(listener);
    }

    /**
     * {@inheritDoc}
     */
    public boolean isClosed()
    {
      return connection.isClosed();
    }

    public void connectionReceivedUnsolicitedNotification(
        GenericExtendedResult notification) {
      // Do nothing
    }

    public void connectionErrorOccurred(
        boolean isDisconnectNotification,
        ErrorResultException error) {
      synchronized (activeConnections) {
        connection.removeConnectionEventListener(this);
        activeConnections.remove(this);
      }
    }

    public void handleErrorResult(Void aVoid, ErrorResultException error) {
      // TODO: I18N
      if(error instanceof OperationTimeoutException)
      {
        close(Requests.newUnbindRequest(), "Heart beat timed out");
      }
    }

    public void handleResult(Void aVoid, Result result) {
      // Do nothing
    }

    private void sendHeartBeat() {
      search(heartBeat, this, null, null);
    }
  }

  private final class HeartBeatThread extends Thread {
    private HeartBeatThread() {
      super("Heart Beat Thread");
    }

    public void run() {
      while (!stopRequested) {
        synchronized (activeConnections) {
          for (HeartBeatAsynchronousConnection connection : activeConnections) {
            connection.sendHeartBeat();
          }
        }
        try {
          sleep(interval);
        } catch (InterruptedException e) {
          // Ignore
        }
      }
    }
  }

  private final class ConnectionFutureImpl<P> implements
      ConnectionFuture<HeartBeatAsynchronousConnection>,
      ConnectionResultHandler<AsynchronousConnection, Void> {
    private volatile HeartBeatAsynchronousConnection heartBeatConnection;

    private volatile ErrorResultException exception;

    private volatile ConnectionFuture<?> connectFuture;

    private final CountDownLatch latch = new CountDownLatch(1);

    private final
    ConnectionResultHandler<? super HeartBeatAsynchronousConnection, P> handler;

    private final P p;

    private boolean cancelled;


    private ConnectionFutureImpl(
        ConnectionResultHandler<
            ? super HeartBeatAsynchronousConnection, P> handler,
        P p) {
      this.handler = handler;
      this.p = p;
    }


    public boolean cancel(boolean mayInterruptIfRunning) {
      cancelled = connectFuture.cancel(mayInterruptIfRunning);
      if (cancelled) {
        latch.countDown();
      }
      return cancelled;
    }


    public HeartBeatAsynchronousConnection get()
        throws InterruptedException, ErrorResultException {
      latch.await();
      if (cancelled) {
        throw new CancellationException();
      }
      if (exception != null) {
        throw exception;
      }
      return heartBeatConnection;
    }


    public HeartBeatAsynchronousConnection get(
        long timeout,
        TimeUnit unit) throws InterruptedException, TimeoutException,
        ErrorResultException {
      latch.await(timeout, unit);
      if (cancelled) {
        throw new CancellationException();
      }
      if (exception != null) {
        throw exception;
      }
      return heartBeatConnection;
    }


    public boolean isCancelled() {
      return cancelled;
    }


    public boolean isDone() {
      return latch.getCount() == 0;
    }


    public void handleConnection(
        Void v,
        AsynchronousConnection connection) {
      heartBeatConnection = new HeartBeatAsynchronousConnection(connection);
      synchronized (activeConnections) {
        connection.addConnectionEventListener(heartBeatConnection);
        activeConnections.add(heartBeatConnection);
      }
      if (handler != null) {
        handler.handleConnection(p, heartBeatConnection);
      }
      latch.countDown();
    }


    public void handleConnectionError(Void v, ErrorResultException error) {
      exception = error;
      if (handler != null) {
        handler.handleConnectionError(p, error);
      }
      latch.countDown();
    }
  }

  public <P> ConnectionFuture<? extends HeartBeatAsynchronousConnection>
  getAsynchronousConnection(
      ConnectionResultHandler<? super
          HeartBeatAsynchronousConnection, P> pConnectionResultHandler, P p) {
    ConnectionFutureImpl<P> future =
        new ConnectionFutureImpl<P>(pConnectionResultHandler, p);
    future.connectFuture =
        parentFactory.getAsynchronousConnection(future, null);
    return future;
  }
}
