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



import java.util.LinkedList;
import java.util.List;

import org.opends.sdk.ResultCode;

import com.sun.opends.sdk.util.Validator;



/**
 * Modifiable result implementation.
 * 
 * @param <S>
 *          The type of result.
 */
abstract class AbstractResultImpl<S extends Result> extends
    AbstractResponseImpl<S> implements Result
{
  // For local errors caused by internal exceptions.
  private Throwable cause = null;

  private String diagnosticMessage = "";

  private String matchedDN = "";

  private final List<String> referralURIs = new LinkedList<String>();

  private ResultCode resultCode;



  /**
   * Creates a new modifiable result implementation using the provided
   * result code.
   * 
   * @param resultCode
   *          The result code.
   * @throws NullPointerException
   *           If {@code resultCode} was {@code null}.
   */
  AbstractResultImpl(ResultCode resultCode) throws NullPointerException
  {
    this.resultCode = resultCode;
  }



  /**
   * {@inheritDoc}
   */
  public final S addReferralURI(String uri) throws NullPointerException
  {
    Validator.ensureNotNull(uri);

    referralURIs.add(uri);
    return getThis();
  }



  /**
   * {@inheritDoc}
   */
  public final S clearReferralURIs()
  {
    referralURIs.clear();
    return getThis();
  }



  /**
   * {@inheritDoc}
   */
  public final Throwable getCause()
  {
    return cause;
  }



  /**
   * {@inheritDoc}
   */
  public final String getDiagnosticMessage()
  {
    return diagnosticMessage;
  }



  /**
   * {@inheritDoc}
   */
  public final String getMatchedDN()
  {
    return matchedDN;
  }



  /**
   * {@inheritDoc}
   */
  public final Iterable<String> getReferralURIs()
  {
    return referralURIs;
  }



  /**
   * {@inheritDoc}
   */
  public final ResultCode getResultCode()
  {
    return resultCode;
  }



  /**
   * {@inheritDoc}
   */
  public final boolean hasReferralURIs()
  {
    return !referralURIs.isEmpty();
  }



  /**
   * {@inheritDoc}
   */
  public final boolean isReferral()
  {
    final ResultCode code = getResultCode();
    return code.equals(ResultCode.REFERRAL);
  }



  /**
   * {@inheritDoc}
   */
  public final boolean isSuccess()
  {
    final ResultCode code = getResultCode();
    return !code.isExceptional();
  }



  /**
   * {@inheritDoc}
   */
  public final S setCause(Throwable cause)
  {
    this.cause = cause;
    return getThis();
  }



  /**
   * {@inheritDoc}
   */
  public final S setDiagnosticMessage(String message)
  {
    if (message == null)
    {
      this.diagnosticMessage = "";
    }
    else
    {
      this.diagnosticMessage = message;
    }

    return getThis();
  }



  /**
   * {@inheritDoc}
   */
  public final S setMatchedDN(String dn)
  {
    if (dn == null)
    {
      this.matchedDN = "";
    }
    else
    {
      this.matchedDN = dn;
    }

    return getThis();
  }



  /**
   * {@inheritDoc}
   */
  public final S setResultCode(ResultCode resultCode)
      throws NullPointerException
  {
    Validator.ensureNotNull(resultCode);

    this.resultCode = resultCode;
    return getThis();
  }

}
