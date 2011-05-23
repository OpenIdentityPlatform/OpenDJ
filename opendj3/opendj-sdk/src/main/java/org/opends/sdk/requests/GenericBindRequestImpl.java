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
 *      Copyright 2010 Sun Microsystems, Inc.
 */

package org.opends.sdk.requests;



import org.opends.sdk.ByteString;
import org.opends.sdk.ErrorResultException;

import com.forgerock.opendj.util.Validator;



/**
 * Generic bind request implementation.
 */
final class GenericBindRequestImpl extends
    AbstractBindRequest<GenericBindRequest> implements GenericBindRequest
{
  private final BindClient bindClient;

  private String name;

  private ByteString authenticationValue;

  private byte authenticationType;



  /**
   * Creates a new generic bind request using a generic bind client.
   */
  GenericBindRequestImpl(final String name, final byte authenticationType,
      final ByteString authenticationValue)
  {
    this.name = name;
    this.authenticationType = authenticationType;
    this.authenticationValue = authenticationValue;
    this.bindClient = null; // Create a new bind client each time.
  }



  /**
   * Creates a new generic bind request using the provided bind client.
   * <p>
   * This is intended for use by other bind client implementations in this
   * package.
   */
  GenericBindRequestImpl(final String name, final byte authenticationType,
      final ByteString authenticationValue, final BindClient bindClient)
  {
    this.name = name;
    this.authenticationType = authenticationType;
    this.authenticationValue = authenticationValue;
    this.bindClient = bindClient; // Always return same bind client.
  }



  /**
   * Creates a new generic bind request that is an exact copy of the
   * provided request.
   *
   * @param genericBindRequest
   *          The generic bind request to be copied.
   * @throws NullPointerException
   *           If {@code genericBindRequest} was {@code null} .
   */
  GenericBindRequestImpl(
      final GenericBindRequest genericBindRequest)
      throws NullPointerException
  {
    super(genericBindRequest);
    this.name = genericBindRequest.getName();
    this.authenticationType = genericBindRequest.getAuthenticationType();
    this.authenticationValue = genericBindRequest.getAuthenticationValue();
    this.bindClient = null; // Create a new bind client each time.
  }



  public BindClient createBindClient(final String serverName)
      throws ErrorResultException
  {
    if (bindClient == null)
    {
      return new BindClientImpl(this)
          .setNextAuthenticationValue(authenticationValue);
    }
    else
    {
      return bindClient;
    }
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
  @Override
  public String getName()
  {
    return name;
  }



  /**
   * {@inheritDoc}
   */
  public GenericBindRequest setAuthenticationType(final byte type)
      throws UnsupportedOperationException
  {
    this.authenticationType = type;
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public GenericBindRequest setAuthenticationValue(final ByteString bytes)
      throws UnsupportedOperationException, NullPointerException
  {
    Validator.ensureNotNull(bytes);
    this.authenticationValue = bytes;
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public GenericBindRequest setName(final String name)
      throws UnsupportedOperationException, NullPointerException
  {
    Validator.ensureNotNull(name);
    this.name = name;
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
