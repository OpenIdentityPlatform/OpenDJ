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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.admin.ads.util;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.X509TrustManager;

/**
 * An X509TrustManager which trusts everything.
 */
class BlindTrustManager implements X509TrustManager {

  /**
   * {@inheritDoc}
   */
  public void checkClientTrusted(X509Certificate[] chain, String authType)
  throws CertificateException
  {
  }

  /**
   * {@inheritDoc}
   */
  public void checkServerTrusted(X509Certificate[] chain, String authType)
  throws CertificateException
  {
  }

  /**
   * {@inheritDoc}
   */
  public X509Certificate[] getAcceptedIssuers()
  {
    return new X509Certificate[0];
  }
}

