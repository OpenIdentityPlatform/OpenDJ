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

//
// J2SE
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate ;

/**
 * When a remote client (dsconfig for instance) wants to establish a
 * remote connection with opends server through a secure connection,
 * and if the certificate is not known, the SSL handcheck fails and
 * this exception is thrown. This allows to get the certificate chain
 * which is unknown.
 */
public class OpendsCertificateException extends CertificateException
{

  /**
   * The serial version UUID.
   */
  private static final long serialVersionUID = 1151044344529478436L;


  // ------------------
  // Private certificate chain
  // ------------------
  private X509Certificate[] chain;

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
