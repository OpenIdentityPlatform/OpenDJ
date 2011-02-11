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
import org.opends.sdk.LocalizedIllegalArgumentException;

/**
 * Unmodifiable plain SASL bind request implementation.
 */
final class UnmodifiablePlainSASLBindRequestImpl extends
    AbstractUnmodifiableSASLBindRequest<PlainSASLBindRequest> implements
    PlainSASLBindRequest
{
  UnmodifiablePlainSASLBindRequestImpl(PlainSASLBindRequest impl) {
    super(impl);
  }

  @Override
  public String getAuthenticationID() {
    return impl.getAuthenticationID();
  }

  @Override
  public String getAuthorizationID() {
    return impl.getAuthorizationID();
  }

  @Override
  public ByteString getPassword() {
    return impl.getPassword();
  }

  @Override
  public PlainSASLBindRequest setAuthenticationID(String authenticationID)
      throws UnsupportedOperationException, LocalizedIllegalArgumentException,
      NullPointerException {
    throw new UnsupportedOperationException();
  }

  @Override
  public PlainSASLBindRequest setAuthorizationID(String authorizationID)
      throws UnsupportedOperationException, LocalizedIllegalArgumentException {
    throw new UnsupportedOperationException();
  }

  @Override
  public PlainSASLBindRequest setPassword(ByteString password)
      throws UnsupportedOperationException, NullPointerException {
    throw new UnsupportedOperationException();
  }

  @Override
  public PlainSASLBindRequest setPassword(char[] password)
      throws UnsupportedOperationException, NullPointerException {
    throw new UnsupportedOperationException();
  }
}
