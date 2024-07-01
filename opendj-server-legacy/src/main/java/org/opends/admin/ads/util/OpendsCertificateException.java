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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.admin.ads.util;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * When a remote client (dsconfig for instance) wants to establish a
 * remote connection with opends server through a secure connection,
 * and if the certificate is not known, the SSL handcheck fails and
 * this exception is thrown. This allows to get the certificate chain
 * which is unknown.
 */
public class OpendsCertificateException extends CertificateException
{
  /** The serial version UUID. */
  private static final long serialVersionUID = 1151044344529478436L;

  /** Private certificate chain. */
  private final X509Certificate[] chain;

  // ------------------
  // Constructor
  // ------------------

  /**
   * Build a new OpendsCertificationException object.
   *
   * @param chain the certificate chain which is unknown and has caused
   *        the SSL handcheck failure.
   */
  public OpendsCertificateException(X509Certificate[] chain)
  {
    super();
    this.chain = chain;
  }

  /**
   * Build a new OpendsCertificationException object.
   *
   * @param msg the detail message string of this exception.
   *
   * @param chain the certificate chain which is unknown and has caused
   *        the SSL handcheck failure.
   */
  public OpendsCertificateException(String msg, X509Certificate[] chain)
  {
    super(msg);
    this.chain = chain;
  }

  /**
   * Build a new OpendsCertificationException object.
   *
   * @param chain the certificate chain which is unknown and has caused
   *        the SSL handcheck failure.
   * @param cause the cause
   */
  public OpendsCertificateException(X509Certificate[] chain, CertificateException cause)
  {
    super(cause);
    this.chain = chain;
  }

  /**
   * Return the certificate chain which is unknown and has caused
   * the SSL handcheck failure.
   *
   * @return the certificate chain which is unknown and has caused
   *        the SSL handcheck failure.
   */
  public X509Certificate[] getChain()
  {
    return chain;
  }
}
