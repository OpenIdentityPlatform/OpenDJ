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

/**
 * A connection factory provides an interface for obtaining a connection
 * to a Directory Server. Connection factories can be used to wrap other
 * connection factories in order to provide enhanced capabilities in a
 * manner which is transparent to the application. For example:
 * <ul>
 * <li>Connection pooling
 * <li>Load balancing
 * <li>Keep alive
 * <li>Transactional connections
 * <li>Connections to LDIF files
 * <li>Data transformations
 * <li>Logging connections
 * <li>Read-only connections
 * <li>Pre-authenticated connections
 * <li>Recording connections, with primitive roll-back functionality
 * </ul>
 * An application typically obtains a connection from a connection
 * factory, performs one or more operations, and then closes the
 * connection. Applications should aim to close connections as soon as
 * possible in order to avoid resource contention.
 *
 * @param <C>
 *          The type of asynchronous connection returned by this
 *          connection factory.
 */
public interface ConnectionFactory<C extends AsynchronousConnection>
{
  /**
   * Returns a connection to the Directory Server associated with this
   * connection factory. The connection returned by this method can be
   * used immediately.
   *
   * @return A connection to the Directory Server associated with this
   *         connection factory.
   * @throws ErrorResultException
   *           If the connection request failed for some reason.
   */
  Connection getConnection() throws ErrorResultException;



  /**
   * Initiates an asynchronous connection request to the Directory
   * Server associated with this connection factory. The returned
   * {@code ConnectionFuture} can be used to retrieve the completed
   * asynchronous connection. Alternatively, if a {@code
   * ConnectionResultHandler} is provided, the handler will be notified
   * when the connection is available and ready for use.
   *
   * @param handler
   *          The completion handler, or {@code null} if no handler is
   *          to be used.
   * @return A future which can be used to retrieve the asynchronous
   *         connection.
   */
  ConnectionFuture<? extends C> getAsynchronousConnection(
      ConnectionResultHandler<? super C> handler);
}
