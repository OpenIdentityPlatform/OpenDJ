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

package org.opends.sdk.requests;



import org.opends.sdk.ByteString;
import org.opends.sdk.ResultCode;
import org.opends.sdk.controls.Control;
import org.opends.sdk.extensions.ExtendedOperation;
import org.opends.sdk.responses.GenericExtendedResult;



/**
 * A generic Extended request which should be used for unsupported
 * extended operations. Servers list the names of Extended requests they
 * recognize in the {@code supportedExtension} attribute in the root
 * DSE. Where the name is not recognized, the server returns
 * {@link ResultCode#PROTOCOL_ERROR} (the server may return this error
 * in other cases).
 */
public interface GenericExtendedRequest extends
    ExtendedRequest<GenericExtendedResult>
{
  /**
   * Adds the provided control to this request.
   * 
   * @param control
   *          The control to be added to this request.
   * @return This request.
   * @throws UnsupportedOperationException
   *           If this request does not permit controls to be added.
   * @throws NullPointerException
   *           If {@code control} was {@code null}.
   */
  GenericExtendedRequest addControl(Control control)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * Removes all the controls included with this request.
   * 
   * @return This request.
   * @throws UnsupportedOperationException
   *           If this request does not permit controls to be removed.
   */
  GenericExtendedRequest clearControls()
      throws UnsupportedOperationException;



  /**
   * Returns the first control contained in this request having the
   * specified OID.
   * 
   * @param oid
   *          The OID of the control to be returned.
   * @return The control, or {@code null} if the control is not included
   *         with this request.
   * @throws NullPointerException
   *           If {@code oid} was {@code null}.
   */
  Control getControl(String oid) throws NullPointerException;



  /**
   * Returns an {@code Iterable} containing the controls included with
   * this request. The returned {@code Iterable} may be used to remove
   * controls if permitted by this request.
   * 
   * @return An {@code Iterable} containing the controls.
   */
  Iterable<Control> getControls();



  /**
   * {@inheritDoc}
   */
  ExtendedOperation<GenericExtendedRequest, GenericExtendedResult> getExtendedOperation();



  /**
   * {@inheritDoc}
   */
  String getRequestName();



  /**
   * {@inheritDoc}
   */
  ByteString getRequestValue();



  /**
   * Indicates whether or not this request has any controls.
   * 
   * @return {@code true} if this request has any controls, otherwise
   *         {@code false}.
   */
  boolean hasControls();



  /**
   * Removes the first control contained in this request having the
   * specified OID.
   * 
   * @param oid
   *          The OID of the control to be removed.
   * @return The removed control, or {@code null} if the control is not
   *         included with this request.
   * @throws UnsupportedOperationException
   *           If this request does not permit controls to be removed.
   * @throws NullPointerException
   *           If {@code oid} was {@code null}.
   */
  Control removeControl(String oid)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * Sets the dotted-decimal representation of the unique OID
   * corresponding to this generic extended request.
   * 
   * @param oid
   *          The dotted-decimal representation of the unique OID.
   * @return This generic extended request.
   * @throws UnsupportedOperationException
   *           If this generic extended request does not permit the
   *           request name to be set.
   * @throws NullPointerException
   *           If {@code oid} was {@code null}.
   */
  GenericExtendedRequest setRequestName(String oid)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * Sets the content of this generic extended request in a form defined
   * by the extended request.
   * 
   * @param bytes
   *          The content of this generic extended request in a form
   *          defined by the extended request, or {@code null} if there
   *          is no content.
   * @return This generic extended request.
   * @throws UnsupportedOperationException
   *           If this generic extended request does not permit the
   *           request value to be set.
   */
  GenericExtendedRequest setRequestValue(ByteString bytes)
      throws UnsupportedOperationException;

}
