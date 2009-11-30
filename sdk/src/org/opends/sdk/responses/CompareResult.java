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
 * compare the following below this CDDL HEADER, with the fields enclosed
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
import org.opends.sdk.controls.Control;



/**
 * An Compare result indicates the final status of an Compare operation.
 * <p>
 * If the attribute value assertion in the Compare request matched a
 * value of the attribute or sub-type according to the attribute's
 * equality matching rule then the result code is set to
 * {@link ResultCode#COMPARE_TRUE} and can be determined by invoking the
 * {@link #matched} method.
 */
public interface CompareResult extends Result
{
  /**
   * {@inheritDoc}
   */
  CompareResult addControl(Control control)
      throws UnsupportedOperationException, NullPointerException;



  CompareResult addReferralURI(String uri)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * {@inheritDoc}
   */
  CompareResult clearControls() throws UnsupportedOperationException;



  CompareResult clearReferralURIs()
      throws UnsupportedOperationException;



  Throwable getCause();



  /**
   * {@inheritDoc}
   */
  Control getControl(String oid) throws NullPointerException;



  /**
   * {@inheritDoc}
   */
  Iterable<Control> getControls();



  String getDiagnosticMessage();



  String getMatchedDN();



  Iterable<String> getReferralURIs();



  ResultCode getResultCode();



  /**
   * {@inheritDoc}
   */
  boolean hasControls();



  boolean hasReferralURIs();



  boolean isReferral();



  boolean isSuccess();



  /**
   * Indicates whether or not the attribute value assertion in the
   * Compare request matched a value of the attribute or sub-type
   * according to the attribute's equality matching rule.
   * <p>
   * Specifically, this method returns {@code true} if the result code
   * is equal to {@link ResultCode#COMPARE_TRUE}.
   * 
   * @return {@code true} if the attribute value assertion matched,
   *         otherwise {@code false}.
   */
  boolean matched();



  /**
   * {@inheritDoc}
   */
  Control removeControl(String oid)
      throws UnsupportedOperationException, NullPointerException;



  CompareResult setCause(Throwable cause)
      throws UnsupportedOperationException;



  CompareResult setDiagnosticMessage(String message)
      throws UnsupportedOperationException;



  CompareResult setMatchedDN(String dn)
      throws UnsupportedOperationException;



  CompareResult setResultCode(ResultCode resultCode)
      throws UnsupportedOperationException, NullPointerException;

}
