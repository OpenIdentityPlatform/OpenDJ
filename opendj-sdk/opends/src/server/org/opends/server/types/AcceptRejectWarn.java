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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.types;



/**
 * This class implements an enumeration that may be used for
 * configuration items that may have three possible values:  accept,
 * reject, or warn.
 */
public enum AcceptRejectWarn
{
  /**
   * Indicates that elements meeting the associated criteria should be
   * accepted.
   */
  ACCEPT("accept"),



  /**
   * Indicates that elements meeting the associated criteria should be
   * rejected.
   */
  REJECT("reject"),



  /**
   * Indicates that a warning should be logged if an element meets the
   * associated criteria.  Whether it will be accepted or rejected
   * after the log warning is dependent on the scenario in which this
   * enumeration is used.
   */
  WARN("warn");



  // The human-readable name for this policy.
  private String policyName;



  /**
   * Creates a new accept/reject/warn policy with the provided name.
   *
   * @param  policyName  The human-readable name for this policy.
   */
  private AcceptRejectWarn(String policyName)
  {
    this.policyName = policyName;
  }



  /**
   * Retrieves the accept/reject/warn policy for the specified name.
   *
   * @param  policyName  The name of the policy to retrieve.
   *
   * @return  The requested accept/reject/warn policy, or
   *          <CODE>null</CODE> if the provided value is not the name
   *          of a valid policy.
   */
  public static AcceptRejectWarn policyForName(String policyName)
  {
    String lowerName = policyName.toLowerCase();
    if (lowerName.equals("accept") || lowerName.equals("allow"))
    {
      return AcceptRejectWarn.ACCEPT;
    }
    else if (lowerName.equals("reject") || lowerName.equals("deny"))
    {
      return AcceptRejectWarn.REJECT;
    }
    else if (lowerName.equals("warn"))
    {
      return AcceptRejectWarn.WARN;
    }
    else
    {
      return null;
    }
  }



  /**
   * Retrieves the human-readable name for this accept/reject/warn
   * policy.
   *
   * @return  The human-readable name for this accept/reject/warn
   *          policy.
   */
  public String toString()
  {
    return policyName;
  }
}

