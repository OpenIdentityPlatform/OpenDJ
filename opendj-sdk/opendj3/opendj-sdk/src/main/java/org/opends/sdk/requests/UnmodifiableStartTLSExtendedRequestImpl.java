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

import java.util.Collections;
import java.util.List;

import javax.net.ssl.SSLContext;

import org.opends.sdk.responses.ExtendedResult;

/**
 * Unmodifiable start TLS extended request implementation.
 */
final class UnmodifiableStartTLSExtendedRequestImpl
    extends AbstractUnmodifiableExtendedRequest
    <StartTLSExtendedRequest, ExtendedResult>
    implements StartTLSExtendedRequest
{
  UnmodifiableStartTLSExtendedRequestImpl(StartTLSExtendedRequest impl) {
    super(impl);
  }

  public SSLContext getSSLContext() {
    return impl.getSSLContext();
  }

  public StartTLSExtendedRequest addEnabledProtocol(String... protocols) {
    throw new UnsupportedOperationException();
  }

  public StartTLSExtendedRequest addEnabledCipherSuite(String... suites) {
    throw new UnsupportedOperationException();
  }

  public List<String> getEnabledProtocols() {
    return Collections.unmodifiableList(impl.getEnabledProtocols());
  }

  public List<String> getEnabledCipherSuites() {
    return Collections.unmodifiableList(impl.getEnabledCipherSuites());
  }

  public StartTLSExtendedRequest setSSLContext(SSLContext sslContext) {
    throw new UnsupportedOperationException();
  }
}
