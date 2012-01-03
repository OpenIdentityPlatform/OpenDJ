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



import java.util.List;

import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.controls.ControlDecoder;



/**
 * An Compare result indicates the final status of an Compare operation.
 * <p>
 * If the attribute value assertion in the Compare request matched a value of
 * the attribute or sub-type according to the attribute's equality matching rule
 * then the result code is set to {@link ResultCode#COMPARE_TRUE} and can be
 * determined by invoking the {@link #matched} method.
 */
public interface CompareResult extends Result
{
  /**
   * {@inheritDoc}
   */
  CompareResult addControl(Control control);



  /**
   * {@inheritDoc}
   */
  CompareResult addReferralURI(String uri);



  /**
   * {@inheritDoc}
   */
  Throwable getCause();



  /**
   * {@inheritDoc}
   */
  <C extends Control> C getControl(ControlDecoder<C> decoder,
      DecodeOptions options) throws DecodeException;



  /**
   * {@inheritDoc}
   */
  List<Control> getControls();



  /**
   * {@inheritDoc}
   */
  String getDiagnosticMessage();



  /**
   * {@inheritDoc}
   */
  String getMatchedDN();



  /**
   * {@inheritDoc}
   */
  List<String> getReferralURIs();



  /**
   * {@inheritDoc}
   */
  ResultCode getResultCode();



  /**
   * {@inheritDoc}
   */
  boolean isReferral();



  /**
   * {@inheritDoc}
   */
  boolean isSuccess();



  /**
   * Indicates whether or not the attribute value assertion in the Compare
   * request matched a value of the attribute or sub-type according to the
   * attribute's equality matching rule.
   * <p>
   * Specifically, this method returns {@code true} if the result code is equal
   * to {@link ResultCode#COMPARE_TRUE}.
   *
   * @return {@code true} if the attribute value assertion matched, otherwise
   *         {@code false}.
   */
  boolean matched();



  /**
   * {@inheritDoc}
   */
  CompareResult setCause(Throwable cause);



  /**
   * {@inheritDoc}
   */
  CompareResult setDiagnosticMessage(String message);



  /**
   * {@inheritDoc}
   */
  CompareResult setMatchedDN(String dn);



  /**
   * {@inheritDoc}
   */
  CompareResult setResultCode(ResultCode resultCode);

}
