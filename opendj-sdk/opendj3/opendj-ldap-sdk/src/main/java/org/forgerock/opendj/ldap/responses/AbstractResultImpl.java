/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opendj3/legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opendj3/legal-notices/CDDLv1_0.txt.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions copyright 2012 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.responses;



import java.util.LinkedList;
import java.util.List;

import org.forgerock.opendj.ldap.ResultCode;

import com.forgerock.opendj.util.Validator;



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
   * Creates a new modifiable result implementation using the provided result
   * code.
   *
   * @param resultCode
   *          The result code.
   * @throws NullPointerException
   *           If {@code resultCode} was {@code null}.
   */
  AbstractResultImpl(final ResultCode resultCode)
  {
    this.resultCode = resultCode;
  }



  /**
   * Creates a new modifiable result that is an exact copy of the provided
   * result.
   *
   * @param result
   *          The result to be copied.
   * @throws NullPointerException
   *           If {@code result} was {@code null}.
   */
  AbstractResultImpl(Result result)
  {
    super(result);
    this.cause = result.getCause();
    this.diagnosticMessage = result.getDiagnosticMessage();
    this.matchedDN = result.getMatchedDN();
    this.referralURIs.addAll(result.getReferralURIs());
    this.resultCode = result.getResultCode();
  }



  /**
   * {@inheritDoc}
   */
  public final S addReferralURI(final String uri)
  {
    Validator.ensureNotNull(uri);

    referralURIs.add(uri);
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
  public final List<String> getReferralURIs()
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
  public final S setCause(final Throwable cause)
  {
    this.cause = cause;
    return getThis();
  }



  /**
   * {@inheritDoc}
   */
  public final S setDiagnosticMessage(final String message)
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
  public final S setMatchedDN(final String dn)
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
  public final S setResultCode(final ResultCode resultCode)
  {
    Validator.ensureNotNull(resultCode);

    this.resultCode = resultCode;
    return getThis();
  }

}
