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
 * This class implements an enumeration that may be used for
 * configuration items that may have three possible values:  accept,
 * reject, or warn.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public enum AcceptRejectWarn
{
  /** Indicates that elements meeting the associated criteria should be accepted. */
  ACCEPT("accept"),
  /** Indicates that elements meeting the associated criteria should be rejected. */
  REJECT("reject"),
  /**
   * Indicates that a warning should be logged if an element meets the
   * associated criteria.  Whether it will be accepted or rejected
   * after the log warning is dependent on the scenario in which this
   * enumeration is used.
   */
  WARN("warn");

  /** The human-readable name for this policy. */
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
  @Override
  public String toString()
  {
    return policyName;
  }
}
