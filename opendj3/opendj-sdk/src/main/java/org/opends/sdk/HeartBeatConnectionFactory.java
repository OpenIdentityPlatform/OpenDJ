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



import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.opends.sdk.requests.Requests;
import org.opends.sdk.requests.SearchRequest;
import org.opends.sdk.responses.ExtendedResult;
import org.opends.sdk.responses.Result;
import org.opends.sdk.responses.SearchResultEntry;
import org.opends.sdk.responses.SearchResultReference;

import com.forgerock.opendj.util.AsynchronousConnectionDecorator;
import com.forgerock.opendj.util.FutureResultTransformer;
import com.forgerock.opendj.util.StaticUtils;
import com.forgerock.opendj.util.Validator;



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
  private final class AsynchronousConnectionImpl extends
      AsynchronousConnectionDecorator implements ConnectionEventListener,
      SearchResultHandler
  {
    private long lastSuccessfulPing;

    private FutureResult<Result> lastPingFuture;



    private AsynchronousConnectionImpl(final AsynchronousConnection connection)
    {
      super(connection);
    }



    @Override
    public void handleConnectionClosed()
    {
      notifyClosed();
    }



    @Override
    public void handleConnectionError(final boolean isDisconnectNotification,
        final ErrorResultException error)
    {
      notifyClosed();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean handleEntry(final SearchResultEntry entry)
    {
      // Ignore.
      return true;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void handleErrorResult(final ErrorResultException error)
    {
      connection.close(Requests.newUnbindRequest(), "Heartbeat retured error: "
          + error);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean handleReference(final SearchResultReference reference)
    {
      // Ignore.
      return true;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void handleResult(final Result result)
    {
      lastSuccessfulPing = System.currentTimeMillis();
    }



    @Override
    public void handleUnsolicitedNotification(final ExtendedResult notification)
    {
      // Do nothing
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isValid()
    {
      return connection.isValid()
          && (lastSuccessfulPing <= 0 || System.currentTimeMillis()
              - lastSuccessfulPing < unit.toMillis(interval) * 2);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public String toString()
    {
      final StringBuilder builder = new StringBuilder();
      builder.append("HeartBeatConnection(");
      builder.append(connection);
      builder.append(')');
      return builder.toString();
    }



    private void notifyClosed()
    {
      synchronized (activeConnections)
      {
        connection.removeConnectionEventListener(this);
        activeConnections.remove(this);

        if (activeConnections.isEmpty())
        {
          // This is the last active connection, so stop the heart beat.
          heartBeatFuture.cancel(false);
        }
      }
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
        if (activeConnections.isEmpty())
        {
          // This is the first active connection, so start the heart beat.
          heartBeatFuture = scheduler.scheduleWithFixedDelay(
              new HeartBeatRunnable(), 0, interval, unit);
        }
        activeConnections.add(heartBeatConnection);
      }
      return heartBeatConnection;
    }

  }



  private final class HeartBeatRunnable implements Runnable
  {
    private HeartBeatRunnable()
    {
      // Nothing to do.
    }



    @Override
    public void run()
    {
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
    }
  }



  private final SearchRequest heartBeat;

  private final long interval;

  private final ScheduledExecutorService scheduler;

  private final TimeUnit unit;

  private final List<AsynchronousConnectionImpl> activeConnections;

  private final ConnectionFactory factory;

  private static final SearchRequest DEFAULT_SEARCH = Requests
      .newSearchRequest("", SearchScope.BASE_OBJECT, "(objectClass=*)", "1.1");

  private ScheduledFuture<?> heartBeatFuture;



  /**
   * Creates a new heart-beat connection factory which will create connections
   * using the provided connection factory and periodically ping any created
   * connections in order to detect that they are still alive every 10 seconds
   * using the default scheduler.
   *
   * @param factory
   *          The connection factory to use for creating connections.
   */
  HeartBeatConnectionFactory(final ConnectionFactory factory)
  {
    this(factory, 10, TimeUnit.SECONDS, DEFAULT_SEARCH, StaticUtils
        .getDefaultScheduler());
  }



  /**
   * Creates a new heart-beat connection factory which will create connections
   * using the provided connection factory and periodically ping any created
   * connections in order to detect that they are still alive using the
   * specified frequency and the default scheduler.
   *
   * @param factory
   *          The connection factory to use for creating connections.
   * @param interval
   *          The interval between keepalive pings.
   * @param unit
   *          The time unit for the interval between keepalive pings.
   */
  HeartBeatConnectionFactory(final ConnectionFactory factory,
      final long interval, final TimeUnit unit)
  {
    this(factory, interval, unit, DEFAULT_SEARCH, StaticUtils
        .getDefaultScheduler());
  }



  /**
   * Creates a new heart-beat connection factory which will create connections
   * using the provided connection factory and periodically ping any created
   * connections using the specified search request in order to detect that they
   * are still alive.
   *
   * @param factory
   *          The connection factory to use for creating connections.
   * @param interval
   *          The interval between keepalive pings.
   * @param unit
   *          The time unit for the interval between keepalive pings.
   * @param heartBeat
   *          The search request to use for keepalive pings.
   */
  HeartBeatConnectionFactory(final ConnectionFactory factory,
      final long interval, final TimeUnit unit, final SearchRequest heartBeat)
  {
    this(factory, interval, unit, heartBeat, StaticUtils.getDefaultScheduler());
  }



  /**
   * Creates a new heart-beat connection factory which will create connections
   * using the provided connection factory and periodically ping any created
   * connections using the specified search request in order to detect that they
   * are still alive.
   *
   * @param factory
   *          The connection factory to use for creating connections.
   * @param interval
   *          The interval between keepalive pings.
   * @param unit
   *          The time unit for the interval between keepalive pings.
   * @param heartBeat
   *          The search request to use for keepalive pings.
   * @param scheduler
   *          The scheduler which should for periodically sending keepalive
   *          pings.
   */
  HeartBeatConnectionFactory(final ConnectionFactory factory,
      final long interval, final TimeUnit unit, final SearchRequest heartBeat,
      final ScheduledExecutorService scheduler)
  {
    Validator.ensureNotNull(factory, heartBeat, unit, scheduler);
    Validator.ensureTrue(interval >= 0, "negative timeout");

    this.heartBeat = heartBeat;
    this.interval = interval;
    this.unit = unit;
    this.activeConnections = new LinkedList<AsynchronousConnectionImpl>();
    this.factory = factory;
    this.scheduler = scheduler;
  }



  @Override
  public FutureResult<AsynchronousConnection> getAsynchronousConnection(
      final ResultHandler<? super AsynchronousConnection> handler)
  {
    final FutureResultImpl future = new FutureResultImpl(handler);
    future.setFutureResult(factory.getAsynchronousConnection(future));
    return future;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    final StringBuilder builder = new StringBuilder();
    builder.append("HeartBeatConnectionFactory(");
    builder.append(String.valueOf(factory));
    builder.append(')');
    return builder.toString();
  }
}
