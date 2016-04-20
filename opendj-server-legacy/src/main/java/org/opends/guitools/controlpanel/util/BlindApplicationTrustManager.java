/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2009 Sun Microsystems, Inc.
 * Portions Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.util;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import org.opends.admin.ads.util.ApplicationTrustManager;

/** An application trust manager that accepts all the certificates. */
public class BlindApplicationTrustManager extends ApplicationTrustManager
{
  /** Default constructor. */
  public BlindApplicationTrustManager()
  {
    super(null);
  }

  @Override
  public void checkClientTrusted(X509Certificate[] chain, String authType)
  throws CertificateException
  {
    // no-op
  }

  @Override
  public void checkServerTrusted(X509Certificate[] chain, String authType)
  throws CertificateException
  {
    // no-op
  }

  @Override
  public X509Certificate[] getAcceptedIssuers()
  {
    return new X509Certificate[0];
  }

  @Override
  public BlindApplicationTrustManager createCopy()
  {
    return new BlindApplicationTrustManager();
  }
}
