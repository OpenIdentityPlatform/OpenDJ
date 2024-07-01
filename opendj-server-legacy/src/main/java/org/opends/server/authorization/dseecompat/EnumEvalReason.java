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
