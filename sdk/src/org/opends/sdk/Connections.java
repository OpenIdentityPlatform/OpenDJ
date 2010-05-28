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
import java.util.concurrent.TimeUnit;

import org.opends.sdk.requests.BindRequest;
import org.opends.sdk.requests.SearchRequest;

import com.sun.opends.sdk.util.Validator;



/**
 * This class contains methods for creating and manipulating connection
 * factories and connections.
 */
public final class Connections
{
  /**
   * Creates a new authenticated connection factory which will obtain
   * connections using the provided connection factory and immediately perform
   * the provided Bind request.
   * <p>
   * The connections returned by an authenticated connection factory support all
   * operations with the exception of Bind requests. Attempts to perform a Bind
   * will result in an {@code UnsupportedOperationException}.
   * <p>
   * If the Bind request fails for some reason (e.g. invalid credentials), then
   * the connection attempt will fail and an {@code ErrorResultException} will
   * be thrown.
   *
   * @param factory
   *          The connection factory to use for connecting to the Directory
   *          Server.
   * @param request
   *          The Bind request to use for authentication.
   * @return The new connection pool.
   * @throws NullPointerException
   *           If {@code factory} or {@code request} was {@code null}.
   */
  public static ConnectionFactory newAuthenticatedConnectionFactory(
      final ConnectionFactory factory, final BindRequest request)
      throws NullPointerException
  {
    Validator.ensureNotNull(factory, request);

    return new AuthenticatedConnectionFactory(factory, request);
  }



  /**
   * Creates a new connection pool which will maintain {@code poolSize}
   * connections created using the provided connection factory.
   *
   * @param factory
   *          The connection factory to use for creating new connections.
   * @param poolSize
   *          The maximum size of the connection pool.
   * @return The new connection pool.
   * @throws IllegalArgumentException
   *           If {@code poolSize} is negative.
   * @throws NullPointerException
   *           If {@code factory} was {@code null}.
   */
  public static ConnectionFactory newConnectionPool(
      final ConnectionFactory factory, final int poolSize)
      throws IllegalArgumentException, NullPointerException
  {
    Validator.ensureNotNull(factory);
    Validator.ensureTrue(poolSize >= 0, "negative pool size");
    return new ConnectionPool(factory, poolSize);
  }



  /**
   * Creates a new connection factory providing fault tolerance across multiple
   * underlying connection factories.
   * <p>
   * The returned fail-over connection factory forwards connection requests to
   * one of the provided connection factories. If the request fails for some
   * reason (for example, due to network failure), then the fail-over connection
   * factory switches to another connection faction, repeating the process until
   * the connection request succeeds, or until all the connection factories are
   * determined to be unavailable, in which case the connection request will
   * fail.
   * <p>
   * The implementation periodically attempts to connect to failed connection
   * factories in order to determine if they have become available again.
   *
   * @param factories
   *          The connection factories which will be used for fail-over.
   * @return The new fail-over connection factory.
   * @throws NullPointerException
   *           If {@code factories} was {@code null}.
   */
  public static ConnectionFactory newFailoverConnectionFactory(
      final Collection<ConnectionFactory> factories)
      throws NullPointerException
  {
    final FailoverLoadBalancingAlgorithm algorithm = new FailoverLoadBalancingAlgorithm(
        factories);
    return new LoadBalancingConnectionFactory(algorithm);
  }



  /**
   * Creates a new connection factory providing fault tolerance across multiple
   * underlying connection factories.
   * <p>
   * The returned fail-over connection factory forwards connection requests to
   * one of the provided connection factories. If the request fails for some
   * reason (for example, due to network failure), then the fail-over connection
   * factory switches to another connection faction, repeating the process until
   * the connection request succeeds, or until all the connection factories are
   * determined to be unavailable, in which case the connection request will
   * fail.
   * <p>
   * The implementation periodically attempts to connect to failed connection
   * factories in order to determine if they have become available again.
   *
   * @param factories
   *          The connection factories which will be used for fail-over.
   * @return The new fail-over connection factory.
   * @throws NullPointerException
   *           If {@code factories} was {@code null}.
   */
  public static ConnectionFactory newFailoverConnectionFactory(
      final ConnectionFactory... factories) throws NullPointerException
  {
    final FailoverLoadBalancingAlgorithm algorithm = new FailoverLoadBalancingAlgorithm(
        factories);
    return new LoadBalancingConnectionFactory(algorithm);
  }



  /**
   * Creates a new connection factory which will create connections using the
   * provided connection factory and periodically probe any created connections
   * in order to detect that they are still alive.
   *
   * @param factory
   *          The connection factory to use for creating connections.
   * @param timeout
   *          The time to wait between keep-alive probes.
   * @param unit
   *          The time unit of the timeout argument.
   * @return The new heart-beat connection factory.
   * @throws IllegalArgumentException
   *           If {@code timeout} was negative.
   * @throws NullPointerException
   *           If {@code factory} or {@code unit} was {@code null}.
   */
  public static ConnectionFactory newHeartBeatConnectionFactory(
      final ConnectionFactory factory, final long timeout, final TimeUnit unit)
      throws IllegalArgumentException, NullPointerException
  {
    Validator.ensureNotNull(factory, unit);
    Validator.ensureTrue(timeout >= 0, "negative timeout");

    return new HeartBeatConnectionFactory(factory, timeout, unit);
  }



  /**
   * Creates a new connection factory which will create connections using the
   * provided connection factory and periodically probe any created connections
   * using the specified search request in order to detect that they are still
   * alive.
   *
   * @param factory
   *          The connection factory to use for creating connections.
   * @param timeout
   *          The time to wait between keep-alive probes.
   * @param unit
   *          The time unit of the timeout argument.
   * @param heartBeat
   *          The search request to use when pinging connections.
   * @return The new heart-beat connection factory.
   * @throws IllegalArgumentException
   *           If {@code timeout} was negative.
   * @throws NullPointerException
   *           If {@code factory}, {@code unit}, or {@code heartBeat} was
   *           {@code null}.
   */
  public static ConnectionFactory newHeartBeatConnectionFactory(
      final ConnectionFactory factory, final long timeout, final TimeUnit unit,
      final SearchRequest heartBeat) throws IllegalArgumentException,
      NullPointerException
  {
    Validator.ensureNotNull(factory, unit, heartBeat);
    Validator.ensureTrue(timeout >= 0, "negative timeout");

    return new HeartBeatConnectionFactory(factory, timeout, unit, heartBeat);
  }



  /**
   * Creates a new connection factory which will create internal connections
   * using the provided server connection factory. The server connection will be
   * provided with request context whose value is unique integer associated with
   * the request.
   *
   * @param <C>
   *          The type of client context.
   * @param factory
   *          The server connection factory to use for creating connections.
   * @param clientContext
   *          The client context.
   * @return The new internal connection factory.
   * @throws NullPointerException
   *           If {@code factory} was {@code null}.
   */
  public static <C> ConnectionFactory newInternalConnectionFactory(
      final ServerConnectionFactory<C, Integer> factory, final C clientContext)
      throws NullPointerException
  {
    Validator.ensureNotNull(factory);
    return new InternalConnectionFactory<C>(factory, clientContext);
  }



  // Prevent instantiation.
  private Connections()
  {
    // Do nothing.
  }
}
