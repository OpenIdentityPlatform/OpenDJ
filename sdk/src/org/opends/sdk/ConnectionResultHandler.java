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
 * A completion handler which is notified when an asynchronous
 * connection attempt has completed.
 * <p>
 * {@link ConnectionFactory} objects allow a connection result
 * completion handler to be specified when attempting to connect to a
 * Directory Server. The {@link #handleConnection} method is invoked
 * when the operation completes successfully. The
 * {@link #handleConnectionError} method is invoked if the operations
 * fails.
 * <p>
 * Implementations of these methods should complete in a timely manner
 * so as to avoid keeping the invoking thread from dispatching to other
 * completion handlers.
 *
 * @param <C>
 *          The type of asynchronous connection handled by this
 *          connection result handler.
 */
public interface ConnectionResultHandler<C extends AsynchronousConnection>
{
  /**
   * Invoked when the asynchronous connection has completed
   * successfully.
   *
   * @param connection
   *          The connection which can be used to interact with the
   *          Directory Server.
   */
  void handleConnection(C connection);



  /**
   * Invoked when the asynchronous connection attempt has failed.
   *
   * @param error
   *          The error result exception indicating why the asynchronous
   *          connection attempt has failed.
   */
  void handleConnectionError(ErrorResultException error);
}
