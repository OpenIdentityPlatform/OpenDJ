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

import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.opends.sdk.ByteString;

/**
 * Unmodifiable CRAM-MD5 SASL bind request implementation.
 */
final class UnmodifiableCRAMMD5SASLBindRequestImpl extends
    AbstractUnmodifiableSASLBindRequest<CRAMMD5SASLBindRequest> implements
    CRAMMD5SASLBindRequest
{
  UnmodifiableCRAMMD5SASLBindRequestImpl(CRAMMD5SASLBindRequest impl) {
    super(impl);
  }

  @Override
  public String getAuthenticationID() {
    return impl.getAuthenticationID();
  }

  @Override
  public ByteString getPassword() {
    return impl.getPassword();
  }

  @Override
  public CRAMMD5SASLBindRequest setAuthenticationID(String authenticationID)
      throws LocalizedIllegalArgumentException, NullPointerException {
    throw new UnsupportedOperationException();
  }

  @Override
  public CRAMMD5SASLBindRequest setPassword(ByteString password)
      throws UnsupportedOperationException, NullPointerException {
    throw new UnsupportedOperationException();
  }

  @Override
  public CRAMMD5SASLBindRequest setPassword(char[] password)
      throws UnsupportedOperationException, NullPointerException {
    throw new UnsupportedOperationException();
  }
}
