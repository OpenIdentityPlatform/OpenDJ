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
 *      Portions copyright 2011-2012 ForgeRock AS
 */

package org.forgerock.opendj.ldap.requests;



import static com.forgerock.opendj.ldap.LDAPConstants.*;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ErrorResultException;

import com.forgerock.opendj.util.StaticUtils;
import com.forgerock.opendj.util.Validator;



/**
 * Simple bind request implementation.
 */
final class SimpleBindRequestImpl extends
    AbstractBindRequest<SimpleBindRequest> implements SimpleBindRequest
{
  private byte[] password = new byte[0];

  private String name = "".intern();



  /**
   * Creates a new simple bind request having the provided name and password
   * suitable for name/password authentication.
   *
   * @param name
   *          The name of the Directory object that the client wishes to bind
   *          as, which may be empty.
   * @param password
   *          The password of the Directory object that the client wishes to
   *          bind as, which may be empty indicating that an unauthenticated
   *          bind is to be performed.
   * @throws NullPointerException
   *           If {@code name} or {@code password} was {@code null}.
   */
  SimpleBindRequestImpl(final String name, final byte[] password)
  {
    this.name = name;
    this.password = password;
  }



  /**
   * Creates a new simple bind request that is an exact copy of the
   * provided request.
   *
   * @param simpleBindRequest
   *          The simple bind request to be copied.
   * @throws NullPointerException
   *           If {@code simpleBindRequest} was {@code null} .
   */
  SimpleBindRequestImpl(final SimpleBindRequest simpleBindRequest)
  {
    super(simpleBindRequest);
    this.name = simpleBindRequest.getName();
    this.password = StaticUtils.copyOfBytes(simpleBindRequest.getPassword());
  }



  public BindClient createBindClient(final String serverName)
      throws ErrorResultException
  {
    return new BindClientImpl(this).setNextAuthenticationValue(password);
  }



  public byte getAuthenticationType()
  {
    return TYPE_AUTHENTICATION_SIMPLE;
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
  public byte[] getPassword()
  {
    return password;
  }



  /**
   * {@inheritDoc}
   */
  public SimpleBindRequest setName(final String name)
  {
    Validator.ensureNotNull(name);
    this.name = name;
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public SimpleBindRequest setPassword(final byte[] password)
  {
    Validator.ensureNotNull(password);
    this.password = password;
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public SimpleBindRequest setPassword(final char[] password)
  {
    Validator.ensureNotNull(password);
    this.password = StaticUtils.getBytes(password);
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
    builder.append(ByteString.wrap(getPassword()));
    builder.append(", controls=");
    builder.append(getControls());
    builder.append(")");
    return builder.toString();
  }
}
