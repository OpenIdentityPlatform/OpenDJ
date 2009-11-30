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
 * generic extended the following below this CDDL HEADER, with the fields enclosed
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
import org.opends.sdk.util.ByteString;



/**
 * A Generic Extended result indicates the final status of an Generic
 * Extended operation.
 */
public interface GenericExtendedResult extends ExtendedResult
{
  /**
   * {@inheritDoc}
   */
  GenericExtendedResult addControl(Control control)
      throws UnsupportedOperationException, NullPointerException;



  GenericExtendedResult addReferralURI(String uri)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * {@inheritDoc}
   */
  GenericExtendedResult clearControls()
      throws UnsupportedOperationException;



  GenericExtendedResult clearReferralURIs()
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



  /**
   * {@inheritDoc}
   */
  String getResponseName();



  /**
   * {@inheritDoc}
   */
  ByteString getResponseValue();



  ResultCode getResultCode();



  /**
   * {@inheritDoc}
   */
  boolean hasControls();



  boolean hasReferralURIs();



  boolean isReferral();



  boolean isSuccess();



  /**
   * {@inheritDoc}
   */
  Control removeControl(String oid)
      throws UnsupportedOperationException, NullPointerException;



  GenericExtendedResult setCause(Throwable cause)
      throws UnsupportedOperationException;



  GenericExtendedResult setDiagnosticMessage(String message)
      throws UnsupportedOperationException;



  GenericExtendedResult setMatchedDN(String dn)
      throws UnsupportedOperationException;



  /**
   * Sets the dotted-decimal representation of the unique OID
   * corresponding to this generic extended result.
   * 
   * @param oid
   *          The dotted-decimal representation of the unique OID, or
   *          {@code null} if there is no response name.
   * @return This generic extended result.
   * @throws UnsupportedOperationException
   *           If this generic extended result does not permit the
   *           response name to be set.
   */
  GenericExtendedResult setResponseName(String oid)
      throws UnsupportedOperationException;



  /**
   * Sets the content of this generic extended result in a form defined
   * by the extended result.
   * 
   * @param bytes
   *          The content of this generic extended result in a form
   *          defined by the extended result, or {@code null} if there
   *          is no content.
   * @return This generic extended result.
   * @throws UnsupportedOperationException
   *           If this generic extended result does not permit the
   *           response value to be set.
   */
  GenericExtendedResult setResponseValue(ByteString bytes)
      throws UnsupportedOperationException;



  GenericExtendedResult setResultCode(ResultCode resultCode)
      throws UnsupportedOperationException, NullPointerException;

}
