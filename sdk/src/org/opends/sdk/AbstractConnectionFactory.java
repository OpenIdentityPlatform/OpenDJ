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
 * This class provides a skeletal implementation of the {@code
 * ConnectionFactory} interface, to minimize the effort required to implement
 * this interface.
 */
public abstract class AbstractConnectionFactory implements ConnectionFactory
{
  /**
   * Creates a new abstract connection factory.
   */
  protected AbstractConnectionFactory()
  {
    // Nothing to do.
  }



  /**
   * {@inheritDoc}
   */
  public abstract FutureResult<AsynchronousConnection> getAsynchronousConnection(
      ResultHandler<AsynchronousConnection> handler);



  /**
   * {@inheritDoc}
   * <p>
   * The default implementation is to convert the asynchronous connection
   * returned from {@code blockingGetAsynchronousConnection()} to a synchronous
   * connection using a {@link SynchronousConnection} as per the following code:
   *
   * <pre>
   * return new SynchronousConnection(blockingGetAsynchronousConnection());
   * </pre>
   *
   * Implementations should override this method if they wish to return a
   * different type of synchronous connection.
   *
   * @return A connection to the Directory Server associated with this
   *         connection factory.
   * @throws ErrorResultException
   *           If the connection request failed for some reason.
   * @throws InterruptedException
   *           If the current thread was interrupted while waiting.
   */
  public Connection getConnection() throws ErrorResultException,
      InterruptedException
  {
    return getAsynchronousConnection(null).get().getSynchronousConnection();
  }
}
