/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.types;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ResultCode;


/**
 * This enumeration defines the set of possible outcomes that can
 * result from processing a cancel request.  This is based on the
 * specification contained in RFC 3909.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public class CancelResult
{
  /** The result code associated with this cancel result. */
  private final ResultCode resultCode;

  /**
   * A human-readable response that the server
   * provided for the result of the cancellation.
   */
  private final LocalizableMessage responseMessage;

  /**
   * Creates a new cancel result with the provided result code.
   *
   * @param  resultCode  The result code associated with this cancel
   *                     result.
   *
   * @param  responseMessage A human-readable response that the
   *                         server provided for the result
   *                         of the cancellation.
   */
  public CancelResult(ResultCode resultCode, LocalizableMessage responseMessage)
  {
    this.resultCode = resultCode;
    this.responseMessage = responseMessage;
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
   * Retrieves the human-readable response that the server provided
   * for the result of the cancellation.  The caller may alter the
   * contents of this buffer.
   *
   * @return  The buffer that is used to hold a human-readable
   *          response that the server provided for the result of this
   *          cancellation.
   */
  public LocalizableMessage getResponseMessage()
  {
    return responseMessage;
  }

  /**
   * Retrieves a string representation of this cancel result.
   *
   * @return  A string representation of this cancel result.
   */
  @Override
  public final String toString()
  {
    return String.valueOf(resultCode);
  }
}

