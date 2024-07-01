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

/** This class represents an ACI's targetcontrol keyword. */
public class TargetControl {
  /** HashSet of OID strings parsed from the decode. */
  private final Set<String> controlOIDS;
  /** Enumeration representing the targetcontrol operator. */
  private final EnumTargetOperator op;

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
    boolean ret = isApplicable(matchCtx.getControlOID());
    if (EnumTargetOperator.NOT_EQUALITY.equals(op))
    {
      return !ret;
    }
    return ret;
  }

  private boolean isApplicable(String matchControlOID)
  {
    for (String oid : controlOIDS)
    {
      if (oid.equals("*") || matchControlOID.equals(oid))
      {
        return true;
      }
    }
    return false;
  }
}
