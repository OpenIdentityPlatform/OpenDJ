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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2015-2016 ForgeRock AS.
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



  /** The human-readable name for this policy. */
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
  @Override
  public String toString()
  {
    return policyName;
  }
}

