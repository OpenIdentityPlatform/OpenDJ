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
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.server.authorization.dseecompat;

/**
 * This class provides an enumeration of the reasons why an ACI evaluation
 * returned a result from the AciHandler's testApplicableLists call. This class
 * is used by a geteffectiverights aclRightsInfo attribute search to build
 * a summary string.
 */

public enum EnumEvalReason {

  /**
   * There are aren't any allow ACIs.
   */
  NO_ALLOW_ACIS(0),

  /**
   * An deny ACI either evaluated to FAIL or to TRUE.
   */
  EVALUATED_DENY_ACI(1),

  /**
   * An allow  evaluated to true.
   */
  EVALUATED_ALLOW_ACI(2),

  /**
   * None of the allow and deny ACIs evaluated to true.
   */
  NO_MATCHED_ALLOWS_ACIS(3),

  /**
   * No specific reason could be determined.
   */
  NO_REASON(4),

  /**
   * The authorization DN has bypass-acl privileges.
   */
  SKIP_ACI(5);

  /**
   * Create a new enumeration type for the specified result value.
   * @param v The value of the result.
   */
  EnumEvalReason(int v) {}
}
