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
package com.sun.opends.sdk.tools;



import java.security.cert.CertificateException;



/**
 * The certificate's subject DN's value and the host name we tried to
 * connect to do not match.
 */
@SuppressWarnings("serial")
final class HostnameMismatchCertificateException extends
    CertificateException
{
  private String expectedHostname;

  private String certificateHostname;



  HostnameMismatchCertificateException(String expectedHostname,
      String certificateHostname)
  {
    this.expectedHostname = expectedHostname;
    this.certificateHostname = certificateHostname;
  }



  HostnameMismatchCertificateException(String msg,
      String expectedHostname, String certificateHostname)
  {
    super(msg);
    this.expectedHostname = expectedHostname;
    this.certificateHostname = certificateHostname;
  }



  String getExpectedHostname()
  {
    return expectedHostname;
  }



  void setExpectedHostname(String expectedHostname)
  {
    this.expectedHostname = expectedHostname;
  }



  String getCertificateHostname()
  {
    return certificateHostname;
  }



  void setCertificateHostname(String certificateHostname)
  {
    this.certificateHostname = certificateHostname;
  }
}
