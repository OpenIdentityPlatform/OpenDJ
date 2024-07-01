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
package org.opends.server.types;

/**
 * This enumeration defines a policy that indicates how the server
 * should deal with SSL/TLS-based client connections.  It is used to
 * determine whether the server should request that clients provide
 * their own certificates, and whether to accept client connections
 * in which the client did not provide a certificate.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public enum SSLClientAuthPolicy
{
  /** Indicates that the server will not request a certificate from the client. */
  DISABLED("Disabled"),
  /**
   * Indicates that the server will request a certificate from the
   * client but will not require that one be provided.
   */
  OPTIONAL("Optional"),
  /**
   * Indicates that the server will request a certificate from the
   * client and will reject any connection attempt in which the client
   * did not provide one.
   */
  REQUIRED("Required");

  /** The human-readable name for this policy. */
  private String policyName;

  /**
   * Creates a new SSL client auth policy with the provided name.
   *
   * @param  policyName  The human-readable name for this policy.
   */
  private SSLClientAuthPolicy(String policyName)
  {
    this.policyName = policyName;
  }

  /**
   * Retrieves the SSL client authentication policy for the specified
   * name.
   *
   * @param  policyName  The name of the SSL client authentication
   *                     policy to retrieve.
   *
   * @return  The requested SSL client authentication policy, or
   *          <CODE>null</CODE> if the provided value is not the name
   *          of a valid client authentication policy.
   */
  public static SSLClientAuthPolicy policyForName(String policyName)
  {
    String lowerName = policyName.toLowerCase();
    if (lowerName.equals("disabled") || lowerName.equals("off") ||
        lowerName.equals("never"))
    {
      return SSLClientAuthPolicy.DISABLED;
    }
    else if (lowerName.equals("optional") ||
             lowerName.equals("allowed"))
    {
      return SSLClientAuthPolicy.OPTIONAL;
    }
    else if (lowerName.equals("required") ||
             lowerName.equals("on") ||
             lowerName.equals("always"))
    {
      return SSLClientAuthPolicy.REQUIRED;
    }
    else
    {
      return null;
    }
  }

  /**
   * Retrieves the human-readable name for this SSL client auth
   * policy.
   *
   * @return  The human-readable name for this SSL client auth policy.
   */
  @Override
  public String toString()
  {
    return policyName;
  }
}
