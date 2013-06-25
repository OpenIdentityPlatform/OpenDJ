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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;



/**
 * This class implements an enumeration that may be used to indicate if/how a
 * client's certificate should be validated against the corresponding user entry
 * in the Directory Server.
 */
public enum CertificateValidationPolicy
{
  /**
   * Indicates that the server should always attempt to validate the client
   * certificate against the version in the corresponding user's entry.  If no
   * certificates exist in the user's entry, then the validation will fail.
   */
  ALWAYS("always"),



  /**
   * Indicates that the server should not attempt to validate the client
   * certificate against the version in the corresponding user's entry.
   */
  NEVER("never"),



  /**
   * Indicates that the server should attempt to validate the client certificate
   * against the version in the corresponding user's entry if there are any
   * certificates in that user's entry.  If the user's entry does not contain
   * any certificates, then no validation will be attempted.
   */
  IFPRESENT("ifpresent");



  // The human-readable name for this policy.
  private String policyName;



  /**
   * Creates a new certificate validation policy with the provided name.
   *
   * @param  policyName  The human-readable name for this policy.
   */
  private CertificateValidationPolicy(String policyName)
  {
    this.policyName = policyName;
  }



  /**
   * Retrieves the certificate validation policy for the specified name.
   *
   * @param  policyName  The name of the policy to retrieve.
   *
   * @return  The requested certificate validation policy, or <CODE>null</CODE>
   *          if the provided value is not the name of a valid policy.
   */
  public static CertificateValidationPolicy policyForName(String policyName)
  {
    String lowerName = policyName.toLowerCase();
    if (lowerName.equals("always"))
    {
      return CertificateValidationPolicy.ALWAYS;
    }
    else if (lowerName.equals("never"))
    {
      return CertificateValidationPolicy.NEVER;
    }
    else if (lowerName.equals("ifpresent"))
    {
      return CertificateValidationPolicy.IFPRESENT;
    }
    else
    {
      return null;
    }
  }



  /**
   * Retrieves the human-readable name for this certificate validation policy.
   *
   * @return  The human-readable name for this certificate validation policy.
   */
  public String toString()
  {
    return policyName;
  }
}

