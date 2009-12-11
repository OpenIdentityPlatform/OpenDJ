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
 * A completion handler for consuming the result of an asynchronous
 * operation.
 * <p>
 * {@link Connection} objects allow a result completion handler to be
 * specified when sending operation requests to a Directory Server. The
 * {@link #handleResult} method is invoked when the operation completes
 * successfully. The {@link #handleErrorResult} method is invoked if the
 * operation fails.
 * <p>
 * Implementations of these methods should complete in a timely manner
 * so as to avoid keeping the invoking thread from dispatching to other
 * completion handlers.
 *
 * @param <S>
 *          The type of result handled by this result handler.
 * @param <P>
 *          The type of the additional parameter to this handler's
 *          methods. Use {@link java.lang.Void} for visitors that do not
 *          need an additional parameter.
 */
public interface ResultHandler<S, P>
{
  /**
   * Invoked when the asynchronous operation has failed.
   *
   * @param p
   *          A handler specified parameter.
   * @param error
   *          The error result exception indicating why the asynchronous
   *          operation has failed.
   */
  void handleErrorResult(P p, ErrorResultException error);



  /**
   * Invoked when the asynchronous operation has completed successfully.
   *
   * @param p
   *          A handler specified parameter.
   * @param result
   *          The result of the asynchronous operation.
   */
  void handleResult(P p, S result);
}
