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
 * Simple bind request implementation.
 */
final class SimpleBindRequestImpl extends
    AbstractBindRequest<SimpleBindRequest> implements SimpleBindRequest
{
  private ByteString password = ByteString.empty();

  private DN name = DN.rootDN();



  /**
   * Creates a new simple bind request having the provided name and
   * password suitable for name/password authentication.
   * 
   * @param name
   *          The distinguished name of the Directory object that the
   *          client wishes to bind as, which may be empty.
   * @param password
   *          The password of the Directory object that the client
   *          wishes to bind as, which may be empty indicating that an
   *          unauthenticated bind is to be performed.
   * @throws NullPointerException
   *           If {@code name} or {@code password} was {@code null}.
   */
  SimpleBindRequestImpl(DN name, ByteString password)
      throws NullPointerException
  {
    this.name = name;
    this.password = password;
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
  public ByteString getPassword()
  {
    return password;
  }



  /**
   * {@inheritDoc}
   */
  public String getPasswordAsString()
  {
    return password.toString();
  }



  /**
   * {@inheritDoc}
   */
  public SimpleBindRequest setName(DN dn)
      throws UnsupportedOperationException, NullPointerException
  {
    Validator.ensureNotNull(dn);
    this.name = dn;
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public SimpleBindRequest setName(String dn)
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
  public SimpleBindRequest setPassword(ByteString password)
      throws UnsupportedOperationException, NullPointerException
  {
    Validator.ensureNotNull(password);
    this.password = password;
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public SimpleBindRequest setPassword(String password)
      throws UnsupportedOperationException, NullPointerException
  {
    Validator.ensureNotNull(password);
    this.password = ByteString.valueOf(password);
    return this;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    final StringBuilder builder = new StringBuilder();
    builder.append("SimpleBindRequest(name=");
    builder.append(getName());
    builder.append(", authentication=simple");
    builder.append(", password=");
    builder.append(getPasswordAsString());
    builder.append(", controls=");
    builder.append(getControls());
    builder.append(")");
    return builder.toString();
  }
}
