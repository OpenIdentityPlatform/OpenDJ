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
import org.opends.sdk.DN;
import org.opends.sdk.LocalizedIllegalArgumentException;

import com.sun.opends.sdk.util.Validator;



/**
 * Generic bind request implementation.
 */
final class GenericBindRequestImpl extends
    AbstractBindRequest<GenericBindRequest> implements
    GenericBindRequest
{

  private DN name;

  private ByteString authenticationValue;

  private byte authenticationType;



  /**
   * Creates a new generic bind request using the provided distinguished
   * name, authentication type, and authentication information.
   * 
   * @param name
   *          The distinguished name of the Directory object that the
   *          client wishes to bind as (may be empty).
   * @param authenticationType
   *          The authentication mechanism identifier for this generic
   *          bind request.
   * @param authenticationValue
   *          The authentication information for this generic bind
   *          request in a form defined by the authentication mechanism.
   * @throws NullPointerException
   *           If {@code name}, {@code authenticationType}, or {@code
   *           authenticationValue} was {@code null}.
   */
  GenericBindRequestImpl(DN name, byte authenticationType,
      ByteString authenticationValue) throws NullPointerException
  {
    this.name = name;
    this.authenticationType = authenticationType;
    this.authenticationValue = authenticationValue;
  }



  /**
   * {@inheritDoc}
   */
  public byte getAuthenticationType()
  {
    return authenticationType;
  }



  /**
   * {@inheritDoc}
   */
  public ByteString getAuthenticationValue()
  {
    return authenticationValue;
  }



  /**
   * {@inheritDoc}
   */
  public DN getName()
  {
    return name;
  }



  /**
   * {@inheritDoc}
   */
  public GenericBindRequest setAuthenticationType(byte type)
      throws UnsupportedOperationException
  {
    this.authenticationType = type;
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public GenericBindRequest setAuthenticationValue(ByteString bytes)
      throws UnsupportedOperationException, NullPointerException
  {
    Validator.ensureNotNull(bytes);
    this.authenticationValue = bytes;
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public GenericBindRequest setName(DN dn)
      throws UnsupportedOperationException, NullPointerException
  {
    Validator.ensureNotNull(dn);
    this.name = dn;
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public GenericBindRequest setName(String dn)
      throws LocalizedIllegalArgumentException,
      UnsupportedOperationException, NullPointerException
  {
    Validator.ensureNotNull(dn);
    this.name = DN.valueOf(dn);
    return this;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    final StringBuilder builder = new StringBuilder();
    builder.append("GenericBindRequest(name=");
    builder.append(getName());
    builder.append(", authenticationType=");
    builder.append(getAuthenticationType());
    builder.append(", authenticationValue=");
    builder.append(getAuthenticationValue());
    builder.append(", controls=");
    builder.append(getControls());
    builder.append(")");
    return builder.toString();
  }
}
