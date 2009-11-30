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



import java.util.concurrent.ExecutionException;

import org.opends.sdk.responses.Result;



/**
 * Thrown when the result code returned in a Result indicates that the
 * Request was unsuccessful.
 */
@SuppressWarnings("serial")
public class ErrorResultException extends ExecutionException
{
  private final Result result;



  /**
   * Wraps the provided result in an appropriate error result exception.
   * The type of error result exception used depends on the underlying
   * result code.
   *
   * @param result
   *          The result whose result code indicates a failure.
   * @return The error result exception wrapping the provided result.
   * @throws IllegalArgumentException
   *           If the provided result does not represent a failure.
   * @throws NullPointerException
   *           If {@code result} was {@code null}.
   */
  public static ErrorResultException wrap(Result result)
      throws IllegalArgumentException, NullPointerException
  {
    if (!result.getResultCode().isExceptional())
    {
      throw new IllegalArgumentException(
          "Attempted to wrap a successful result: " + result);
    }

    // TODO: choose type of exception based on result code (e.g.
    // referral).
    return new ErrorResultException(result);
  }



  /**
   * Creates a new error result exception using the provided result.
   *
   * @param result
   *          The error result.
   */
  ErrorResultException(Result result)
  {
    super(result.getResultCode() + ": " + result.getDiagnosticMessage());
    this.result = result;
  }



  /**
   * Returns the error result which caused this exception to be thrown.
   * The type of result returned corresponds to the expected result type
   * of the original request.
   *
   * @return The error result which caused this exception to be thrown.
   */
  public Result getResult()
  {
    return result;
  }
}
