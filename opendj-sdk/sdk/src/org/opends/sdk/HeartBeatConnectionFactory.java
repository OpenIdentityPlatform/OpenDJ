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
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.opends.sdk.requests.*;
import org.opends.sdk.responses.*;
import org.opends.sdk.schema.Schema;

import com.sun.opends.sdk.util.Validator;



/**
 * An heart beat connection factory can be used to create connections
 * that sends a periodic search request to a Directory Server.
 */
public class HeartBeatConnectionFactory extends
    AbstractConnectionFactory<AsynchronousConnection>
{
  private final SearchRequest heartBeat;

  private final int interval;

  private final List<AsynchronousConnectionImpl> activeConnections;

  private final ConnectionFactory<?> parentFactory;

  private volatile boolean stopRequested;



  // FIXME: use a single global scheduler?

  // FIXME: change timeout parameters to long+TimeUnit.

  /**
   * Creates a new heart-beat connection factory which will create
   * connections using the provided connection factory and periodically
   * ping any created connections in order to detect that they are still
   * alive.
   *
   * @param connectionFactory
   *          The connection factory to use for creating connections.
   * @param interval
   *          The period between keepalive pings.
   */
  public HeartBeatConnectionFactory(
      ConnectionFactory<?> connectionFactory, int interval)
  {
    this(connectionFactory, DEFAULT_SEARCH, interval);
  }



  private static final SearchRequest DEFAULT_SEARCH = Requests
      .newSearchRequest("", SearchScope.BASE_OBJECT, "(objectClass=*)",
          "1.1");



  /**
   * Creates a new heart-beat connection factory which will create
   * connections using the provided connection factory and periodically
   * ping any created connections using the specified search request in
   * order to detect that they are still alive.
   *
   * @param connectionFactory
   *          The connection factory to use for creating connections.
   * @param heartBeat
   *          The search request to use when pinging connections.
   * @param interval
   *          The period between keepalive pings.
   */
  public HeartBeatConnectionFactory(
      ConnectionFactory<?> connectionFactory, SearchRequest heartBeat,
      int interval)
  {
    Validator.ensureNotNull(connectionFactory, heartBeat);
    this.heartBeat = heartBeat;
    this.interval = interval;
    this.activeConnections = new LinkedList<AsynchronousConnectionImpl>();
    this.parentFactory = connectionFactory;

    Runtime.getRuntime().addShutdownHook(new Thread()
    {
      @Override
      public void run()
      {
        stopRequested = true;
      }
    });

    new HeartBeatThread().start();
  }



  /**
   * An asynchronous connection that sends heart beats and supports all
   * operations.
   */
  private final class AsynchronousConnectionImpl implements
      AsynchronousConnection, ConnectionEventListener,
      ResultHandler<Result>
  {
    private final AsynchronousConnection connection;



    private AsynchronousConnectionImpl(AsynchronousConnection connection)
    {
      this.connection = connection;
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



    public ResultFuture<BindResult> bind(BindRequest request,
        ResultHandler<? super BindResult> handler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      return connection.bind(request, handler);
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



    public void close(UnbindRequest request, String reason)
        throws NullPointerException
    {
      synchronized (activeConnections)
      {
        connection.removeConnectionEventListener(this);
        activeConnections.remove(this);
      }
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



    public ResultFuture<Result> search(SearchRequest request,
        ResultHandler<Result> resultHandler,
        SearchResultHandler searchResultHandler)
        throws UnsupportedOperationException, IllegalStateException,
        NullPointerException
    {
      return connection.search(request, resultHandler,
          searchResultHandler);
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
    public ResultFuture<RootDSE> readRootDSE(
        ResultHandler<RootDSE> handler)
        throws UnsupportedOperationException, IllegalStateException
    {
      return connection.readRootDSE(handler);
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



    public void addConnectionEventListener(
        ConnectionEventListener listener) throws IllegalStateException,
        NullPointerException
    {
      connection.addConnectionEventListener(listener);
    }



    public void removeConnectionEventListener(
        ConnectionEventListener listener) throws NullPointerException
    {
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
        GenericExtendedResult notification)
    {
      // Do nothing
    }



    public void connectionErrorOccurred(
        boolean isDisconnectNotification, ErrorResultException error)
    {
      synchronized (activeConnections)
      {
        connection.removeConnectionEventListener(this);
        activeConnections.remove(this);
      }
    }



    public void handleErrorResult(ErrorResultException error)
    {
      // TODO: I18N
      if (error instanceof TimeoutResultException)
      {
        close(Requests.newUnbindRequest(), "Heart beat timed out");
      }
    }



    public void handleResult(Result result)
    {
      // Do nothing
    }



    private void sendHeartBeat()
    {
      search(heartBeat, this, null);
    }
  }



  private final class HeartBeatThread extends Thread
  {
    private HeartBeatThread()
    {
      super("Heart Beat Thread");
    }



    public void run()
    {
      while (!stopRequested)
      {
        synchronized (activeConnections)
        {
          for (AsynchronousConnectionImpl connection : activeConnections)
          {
            connection.sendHeartBeat();
          }
        }
        try
        {
          sleep(interval);
        }
        catch (InterruptedException e)
        {
          // Ignore
        }
      }
    }
  }



  private final class ConnectionFutureImpl implements
      ConnectionFuture<AsynchronousConnection>,
      ConnectionResultHandler<AsynchronousConnection>
  {
    private volatile AsynchronousConnectionImpl heartBeatConnection;

    private volatile ErrorResultException exception;

    private volatile ConnectionFuture<?> connectFuture;

    private final CountDownLatch latch = new CountDownLatch(1);

    private final ConnectionResultHandler<? super AsynchronousConnectionImpl> handler;

    private boolean cancelled;



    private ConnectionFutureImpl(
        ConnectionResultHandler<? super AsynchronousConnectionImpl> handler)
    {
      this.handler = handler;
    }



    public boolean cancel(boolean mayInterruptIfRunning)
    {
      cancelled = connectFuture.cancel(mayInterruptIfRunning);
      if (cancelled)
      {
        latch.countDown();
      }
      return cancelled;
    }



    public AsynchronousConnectionImpl get()
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
      return heartBeatConnection;
    }



    public AsynchronousConnectionImpl get(long timeout, TimeUnit unit)
        throws InterruptedException, TimeoutException,
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
      return heartBeatConnection;
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
      heartBeatConnection = new AsynchronousConnectionImpl(connection);
      synchronized (activeConnections)
      {
        connection.addConnectionEventListener(heartBeatConnection);
        activeConnections.add(heartBeatConnection);
      }
      if (handler != null)
      {
        handler.handleConnection(heartBeatConnection);
      }
      latch.countDown();
    }



    public void handleConnectionError(ErrorResultException error)
    {
      exception = error;
      if (handler != null)
      {
        handler.handleConnectionError(error);
      }
      latch.countDown();
    }
  }



  public ConnectionFuture<AsynchronousConnection> getAsynchronousConnection(
      ConnectionResultHandler<? super AsynchronousConnection> handler)
  {
    ConnectionFutureImpl future = new ConnectionFutureImpl(handler);
    future.connectFuture = parentFactory
        .getAsynchronousConnection(future);
    return future;
  }
}
