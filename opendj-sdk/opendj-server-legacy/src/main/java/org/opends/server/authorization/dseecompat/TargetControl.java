/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS
 */
package org.opends.server.authorization.dseecompat;

import static org.opends.messages.AccessControlMessages.*;

import java.util.HashSet;
import java.util.Set;

/**
 * This class represents an ACI's targetcontrol keyword.
 */
public class TargetControl {

  /** HashSet of OID strings parsed from the decode. */
  private Set<String> controlOIDS = new HashSet<>();
  /** Enumeration representing the targetcontrol operator. */
  private EnumTargetOperator op = EnumTargetOperator.EQUALITY;

  /**
   * Creates a class that can be used to evaluate a targetcontrol.
   *
   * @param op The operator of the targetcontrol expression (=, !=).
   * @param controlOIDS  Set of control OIDS to use in the evaluation (may
   *                     contain wild-card '*').
   */
  private TargetControl(EnumTargetOperator op, Set<String> controlOIDS)
  {
    this.controlOIDS=controlOIDS;
    this.op=op;
  }

  /**
   *  Decode an targetcontrol expression string.
   *
   * @param operator  An enumeration representing the operator type.
   * @param expr A string representing the targetcontrol expression.
   * @return  A class representing the targetcontrol expression that can be
   *          used to evaluate an ACI.
   *
   * @throws AciException If the specified expression string is invalid.
   */
  public static TargetControl decode(EnumTargetOperator operator, String expr)
          throws AciException {
    Set<String> controlOIDs = Aci.decodeOID(expr,
                  WARN_ACI_SYNTAX_INVALID_TARGETCONTROL_EXPRESSION.get(expr));
    return new TargetControl(operator, controlOIDs);
  }

  /**
   * Check if a targetcontrol is applicable based on the provided target match
   * context.
   *
   * @param matchCtx The target match context to use in the check.
   * @return True if the targetcontrol is applicable based on the context.
   */
  public boolean isApplicable(AciTargetMatchContext matchCtx) {
    if(matchCtx.getControlOID() == null)
    {
      return false;
    }
    boolean ret = false;
    for(String oid : controlOIDS)
    {
      if(oid.equals("*") || matchCtx.getControlOID().equals(oid)) {
        ret=true;
        break;
      }
    }
    if(op.equals(EnumTargetOperator.NOT_EQUALITY))
    {
      ret = !ret;
    }
    return ret;
  }
}
