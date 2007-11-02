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

package org.opends.guitools.statuspanel;

/** Policy to follow to choose the protocol to be used. */
public enum ConnectionProtocolPolicy
{
  /**
   * Force to use Start TLS.
   */
  USE_STARTTLS,
  /**
   * Force to use LDAP.
   */
  USE_LDAP,
  /**
   * Force to use LDAPs.
   */
  USE_LDAPS,
  /**
   * Use the most secure available (LDAPs, StartTLS and finally LDAP).
   */
  USE_MOST_SECURE_AVAILABLE,
  /**
   * Use the less secure available (LDAP, and then LDAPs).
   */
  USE_LESS_SECURE_AVAILABLE;

  /**
   * Returns the ConnectionPolicy to be used with the parameters provided
   * by the user.
   * @param useSSL whether the user asked to use SSL or not.
   * @param useStartTLS whether the user asked to use Start TLS or not.
   * @return the ConnectionPolicy to be used with the parameters provided
   * by the user.
   */
  public static ConnectionProtocolPolicy getConnectionPolicy(boolean useSSL,
      boolean useStartTLS)
  {
    ConnectionProtocolPolicy policy;
    if (useStartTLS)
    {
      policy = ConnectionProtocolPolicy.USE_STARTTLS;
    }
    else if (useSSL)
    {
      policy = ConnectionProtocolPolicy.USE_LDAPS;
    }
    else
    {
      policy = ConnectionProtocolPolicy.USE_LESS_SECURE_AVAILABLE;
    }
    return policy;
  }
}
