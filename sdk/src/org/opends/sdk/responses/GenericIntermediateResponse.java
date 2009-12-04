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



import org.opends.sdk.ByteString;
import org.opends.sdk.controls.Control;



/**
 * A Generic Intermediate response provides a mechanism for
 * communicating unrecognized or unsupported Intermediate responses to
 * the client.
 */
public interface GenericIntermediateResponse extends
    IntermediateResponse
{
  /**
   * {@inheritDoc}
   */
  GenericIntermediateResponse addControl(Control control)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * {@inheritDoc}
   */
  GenericIntermediateResponse clearControls()
      throws UnsupportedOperationException;



  /**
   * {@inheritDoc}
   */
  Control getControl(String oid) throws NullPointerException;



  /**
   * {@inheritDoc}
   */
  Iterable<Control> getControls();



  /**
   * {@inheritDoc}
   */
  String getResponseName();



  /**
   * {@inheritDoc}
   */
  ByteString getResponseValue();



  /**
   * {@inheritDoc}
   */
  boolean hasControls();



  /**
   * {@inheritDoc}
   */
  Control removeControl(String oid)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * Sets the dotted-decimal representation of the unique OID
   * corresponding to this generic intermediate response.
   * 
   * @param oid
   *          The dotted-decimal representation of the unique OID, or
   *          {@code null} if there is no response name.
   * @return This generic intermediate response.
   * @throws UnsupportedOperationException
   *           If this generic intermediate response does not permit the
   *           response name to be set.
   */
  GenericIntermediateResponse setResponseName(String oid)
      throws UnsupportedOperationException;



  /**
   * Sets the content of this generic intermediate response in a form
   * defined by the extended result.
   * 
   * @param bytes
   *          The content of this generic intermediate response in a
   *          form defined by the intermediate response, or {@code null}
   *          if there is no content.
   * @return This generic intermediate response.
   * @throws UnsupportedOperationException
   *           If this generic intermediate response does not permit the
   *           response value to be set.
   */
  GenericIntermediateResponse setResponseValue(ByteString bytes)
      throws UnsupportedOperationException;

}
