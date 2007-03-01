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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.types;



/**
 * This enumeration defines the set of possible outcomes that can
 * result from processing a cancel request.  This is based on the
 * specification contained in RFC 3909.
 */
public enum CancelResult
{
  /**
   * The cancel result that indicates that the target operation was
   * canceled successfully and in a manner that should have no
   * permanent effects on the server or the data it contains.
   */
  CANCELED(ResultCode.CANCELED),



  /**
   * The cancel result that indicates that the target operation could
   * not be found, which may mean that it either does not exist or has
   * already completed.
   */
  NO_SUCH_OPERATION(ResultCode.NO_SUCH_OPERATION),



  /**
   * The cancel result that indicates that processing on the target
   * operation had already progressed to a point in which it was too
   * late to be able to cancel.
   */
  TOO_LATE(ResultCode.TOO_LATE),



  /**
   * The cancel result that indicates that the operation exists but
   * cannot be canceled for some reason (e.g., it is an abandon, bind,
   * cancel, or unbind operation, or if it is one that would impact
   * the security of the underlying connection).
   */
  CANNOT_CANCEL(ResultCode.CANNOT_CANCEL);



  // The result code associated with this cancel result.
  private final ResultCode resultCode;



  /**
   * Creates a new cancel result with the provided result code.
   *
   * @param  resultCode  The result code associated with this cancel
   *                     result.
   */
  private CancelResult(ResultCode resultCode)
  {
    this.resultCode = resultCode;
  }



  /**
   * Retrieves the result code associated with this cancel result.
   *
   * @return  The result code associated with this cancel result.
   */
  public final ResultCode getResultCode()
  {
    return resultCode;
  }



  /**
   * Retrieves a string representation of this cancel result.
   *
   * @return  A string representation of this cancel result.
   */
  public final String toString()
  {
    return String.valueOf(resultCode);
  }
}

