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



import org.opends.sdk.DN;
import org.opends.sdk.ResultCode;
import org.opends.sdk.controls.Control;
import org.opends.sdk.util.ByteString;
import org.opends.sdk.util.LocalizedIllegalArgumentException;



/**
 * A generic Bind request which should be used for unsupported
 * authentication methods. Servers that do not support a choice supplied
 * by a client return a Bind response with the result code set to
 * {@link ResultCode#AUTH_METHOD_NOT_SUPPORTED}.
 */
public interface GenericBindRequest extends BindRequest
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
  GenericBindRequest addControl(Control control)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * Removes all the controls included with this request.
   * 
   * @return This request.
   * @throws UnsupportedOperationException
   *           If this request does not permit controls to be removed.
   */
  GenericBindRequest clearControls()
      throws UnsupportedOperationException;



  /**
   * Returns the authentication mechanism identifier for this generic
   * bind request. Note that value {@code 0} is reserved for simple
   * authentication, {@code 1} and {@code 2} are reserved but unused,
   * and {@code 3} is reserved for SASL authentication.
   * 
   * @return The authentication mechanism identifier.
   */
  byte getAuthenticationType();



  /**
   * Returns the authentication information for this generic bind
   * request in a form defined by the authentication mechanism.
   * 
   * @return The authentication information.
   */
  ByteString getAuthenticationValue();



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
  DN getName();



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
   * Sets the authentication mechanism identifier for this generic bind
   * request. Note that value {@code 0} is reserved for simple
   * authentication, {@code 1} and {@code 2} are reserved but unused,
   * and {@code 3} is reserved for SASL authentication.
   * 
   * @param type
   *          The authentication mechanism identifier for this generic
   *          bind request.
   * @return This generic bind request.
   * @throws UnsupportedOperationException
   *           If this generic bind request does not permit the
   *           authentication type to be set.
   */
  GenericBindRequest setAuthenticationType(byte type)
      throws UnsupportedOperationException;



  /**
   * Sets the authentication information for this generic bind request
   * in a form defined by the authentication mechanism.
   * 
   * @param bytes
   *          The authentication information for this generic bind
   *          request in a form defined by the authentication mechanism.
   * @return This generic bind request.
   * @throws UnsupportedOperationException
   *           If this generic bind request does not permit the
   *           authentication bytes to be set.
   * @throws NullPointerException
   *           If {@code bytes} was {@code null}.
   */
  GenericBindRequest setAuthenticationValue(ByteString bytes)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * Sets the distinguished name of the Directory object that the client
   * wishes to bind as. The distinguished name may be empty (but never
   * {@code null} when used for of anonymous binds, or when using SASL
   * authentication. The server shall not dereference any aliases in
   * locating the named object.
   * 
   * @param dn
   *          The distinguished name of the Directory object that the
   *          client wishes to bind as.
   * @return This bind request.
   * @throws UnsupportedOperationException
   *           If this bind request does not permit the distinguished
   *           name to be set.
   * @throws NullPointerException
   *           If {@code dn} was {@code null}.
   */
  GenericBindRequest setName(DN dn)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * Sets the distinguished name of the Directory object that the client
   * wishes to bind as. The distinguished name may be empty (but never
   * {@code null} when used for of anonymous binds, or when using SASL
   * authentication. The server shall not dereference any aliases in
   * locating the named object.
   * 
   * @param dn
   *          The distinguished name of the Directory object that the
   *          client wishes to bind as.
   * @return This bind request.
   * @throws LocalizedIllegalArgumentException
   *           If {@code dn} could not be decoded using the default
   *           schema.
   * @throws UnsupportedOperationException
   *           If this bind request does not permit the distinguished
   *           name to be set.
   * @throws NullPointerException
   *           If {@code dn} was {@code null}.
   */
  GenericBindRequest setName(String dn)
      throws LocalizedIllegalArgumentException,
      UnsupportedOperationException, NullPointerException;

}
