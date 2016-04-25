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
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.authorization.dseecompat;

import static org.opends.messages.AccessControlMessages.*;

import java.util.Set;

/** This class represents an ACI's extop keyword rule. */
public class ExtOp {
  /** Set of OID strings parsed from the decode. */
  private final Set<String> extOpOIDs;
  /** Enumeration representing the extop operator. */
  private final EnumTargetOperator op;

  /**
   * Creates a class that can be used to evaluate a extop rule.
   *
   * @param op The operator of the extop expression (=, !=).
   * @param extOpOIDs  Set of extended operation OIDS to use in the evaluation
   *                  (wild-card '*' allowed).
   */
  private ExtOp(EnumTargetOperator op, Set<String> extOpOIDs)
  {
    this.extOpOIDs=extOpOIDs;
    this.op=op;
  }

  /**
   *  Decode an extop expression string.
   *
   * @param operator  An enumeration representing the operator type.
   * @param expr A string representing the extop expression.
   * @return  A class representing the extop expression that can be
   *          used to evaluate an ACI.
   *
   * @throws AciException If the specified expression string is invalid.
   */
  public static ExtOp decode(EnumTargetOperator operator, String expr)
          throws AciException {
    Set<String> extOpOIDs = Aci.decodeOID(expr,
                  WARN_ACI_SYNTAX_INVALID_TARGEXTOP_EXPRESSION.get(expr));
    return new ExtOp(operator, extOpOIDs);
  }

   /**
   * Check if a extop is applicable based on the provided target match
   * context.
   *
   * @param matchCtx The target match context to use in the check.
   * @return True if the extop is applicable based on the context.
   */
  public boolean isApplicable(AciTargetMatchContext matchCtx) {
    if(matchCtx.getExtOpOID() == null)
    {
      return false;
    }
    boolean ret = isApplicable(matchCtx.getExtOpOID());
    if (EnumTargetOperator.NOT_EQUALITY.equals(op))
    {
      return !ret;
    }
    return ret;
  }

  private boolean isApplicable(String matchOID)
  {
    for(String oid : extOpOIDs)
    {
      if ("*".equals(oid) || matchOID.equals(oid))
      {
        return true;
      }
    }
    return false;
  }
}
