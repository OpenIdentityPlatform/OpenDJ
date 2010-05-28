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
 *      Copyright 2010 Sun Microsystems, Inc.
 */

package org.opends.sdk.responses;



import org.opends.sdk.DecodeException;
import org.opends.sdk.DecodeOptions;
import org.opends.sdk.ResultCode;



/**
 * A factory interface for decoding a generic extended result as an extended
 * result of specific type.
 *
 * @param <S>
 *          The type of result.
 */
public interface ExtendedResultDecoder<S extends ExtendedResult>
{
  /**
   * Adapts the provided error result parameters to an extended operation
   * result. This method is called when a generic failure occurs, such as a
   * connection failure, and the error result needs to be converted to a {@code
   * Result} of type {@code S}.
   *
   * @param resultCode
   *          The result code.
   * @param matchedDN
   *          The matched DN, which may be empty if none was provided.
   * @param diagnosticMessage
   *          The diagnostic message, which may be empty if none was provided.
   * @return The decoded extended operation error result.
   * @throws NullPointerException
   *           If {@code resultCode}, {@code matchedDN}, or {@code
   *           diagnosticMessage} were {@code null}.
   */
  S adaptExtendedErrorResult(ResultCode resultCode, String matchedDN,
      String diagnosticMessage) throws NullPointerException;



  /**
   * Decodes the provided extended operation result as a {@code Result} of type
   * {@code S}. This method is called when an extended result is received from
   * the server. The result may indicate success or failure of the extended
   * request.
   *
   * @param result
   *          The extended operation result to be decoded.
   * @param options
   *          The set of decode options which should be used when decoding the
   *          extended operation result.
   * @return The decoded extended operation result.
   * @throws DecodeException
   *           If the provided extended operation result could not be decoded.
   *           For example, if the request name was wrong, or if the request
   *           value was invalid.
   */
  S decodeExtendedResult(ExtendedResult result, DecodeOptions options)
      throws DecodeException;

}
