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

package org.opends.sdk.responses;



import org.opends.sdk.ResultCode;



/**
 * Unmodifiable result implementation.
 * 
 * @param <S>
 *          The type of result.
 */
abstract class AbstractUnmodifiableResultImpl<S extends Result> extends
    AbstractUnmodifiableResponseImpl<S> implements Result
{

  private final S impl;



  /**
   * Creates a new unmodifiable result implementation.
   * 
   * @param impl
   *          The underlying result implementation to be made
   *          unmodifiable.
   */
  AbstractUnmodifiableResultImpl(S impl)
  {
    super(impl);
    this.impl = impl;
  }



  public final S addReferralURI(String uri)
      throws UnsupportedOperationException, NullPointerException
  {
    throw new UnsupportedOperationException();
  }



  public final S clearReferralURIs()
      throws UnsupportedOperationException
  {
    throw new UnsupportedOperationException();
  }



  public final Throwable getCause()
  {
    return impl.getCause();
  }



  public final String getDiagnosticMessage()
  {
    return impl.getDiagnosticMessage();
  }



  public final String getMatchedDN()
  {
    return impl.getMatchedDN();
  }



  public final Iterable<String> getReferralURIs()
  {
    return impl.getReferralURIs();
  }



  public final ResultCode getResultCode()
  {
    return impl.getResultCode();
  }



  public final boolean hasReferralURIs()
  {
    return impl.hasReferralURIs();
  }



  public final boolean isReferral()
  {
    return impl.isReferral();
  }



  public final boolean isSuccess()
  {
    return impl.isSuccess();
  }



  public final S setCause(Throwable cause)
      throws UnsupportedOperationException
  {
    throw new UnsupportedOperationException();
  }



  public final S setDiagnosticMessage(String message)
      throws UnsupportedOperationException
  {
    throw new UnsupportedOperationException();
  }



  public final S setMatchedDN(String dn)
      throws UnsupportedOperationException
  {
    throw new UnsupportedOperationException();
  }



  public final S setResultCode(ResultCode resultCode)
      throws UnsupportedOperationException, NullPointerException
  {
    throw new UnsupportedOperationException();
  }

}
