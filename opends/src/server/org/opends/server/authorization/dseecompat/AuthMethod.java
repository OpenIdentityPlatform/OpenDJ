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

package org.opends.server.authorization.dseecompat;

import static org.opends.server.messages.AciMessages.*;
import static org.opends.server.messages.MessageHandler.getMessage;
import org.opends.server.core.DirectoryServer;
import static org.opends.server.loggers.Error.logError;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;

/**
 * The AuthMethod class represents an authmethod bind rule keyword expression.
 */
public class AuthMethod implements KeywordBindRule {

    /*
     * Enumeration representing the authentication method.
     */
    private EnumAuthMethod authMethod=null;

    /**
     * The SASL mechanism if the authentication method is SASL.
     */
    private String saslMech = null;

    /*
     * Enumeration representing the bind rule operation type.
     */
    private EnumBindRuleType type=null;

    /**
     * Create a class representing an authmethod bind rule keyword from the
     * provided method and bind rule type.
     * @param type An enumeration representing the type of the expression.
     */
    private AuthMethod(EnumAuthMethod method, String saslMech,
                       EnumBindRuleType type) {
        this.authMethod=method;
        this.saslMech = saslMech;
        this.type=type;
    }

    /**
     * Decode a string representing an authmethod bind rule.
     * @param expr  The string representing the bind rule.
     * @param type An enumeration representing the bind rule type.
     * @return  A keyword bind rule class that can be used to evaluate the
     * bind rule.
     * @throws AciException If the expression string is invalid.
     */
    public static KeywordBindRule decode(String expr, EnumBindRuleType type)
    throws AciException  {
      String lowerExpr = expr.toLowerCase();
      if (lowerExpr.equals("none"))
      {
        return new AuthMethod(EnumAuthMethod.AUTHMETHOD_NONE, null, type);
      }
      else if (lowerExpr.equals("simple"))
      {
        return new AuthMethod(EnumAuthMethod.AUTHMETHOD_SIMPLE, null, type);
      }
      else if (lowerExpr.equals("ssl"))
      {
        return new AuthMethod(EnumAuthMethod.AUTHMETHOD_SSL, "EXTERNAL", type);
      }
      else if (expr.length() > 5 && lowerExpr.startsWith("sasl "))
      {
        String saslMech = expr.substring(5);
        if (DirectoryServer.getSASLMechanismHandler(saslMech) == null) {
          int msgID = MSGID_ACI_SYNTAX_DUBIOUS_AUTHMETHOD_SASL_MECHANISM;
          logError(ErrorLogCategory.ACCESS_CONTROL,
                   ErrorLogSeverity.NOTICE, msgID, saslMech);
        }
        return new AuthMethod(EnumAuthMethod.AUTHMETHOD_SASL, saslMech, type);
      }

      int msgID = MSGID_ACI_SYNTAX_INVALID_AUTHMETHOD_EXPRESSION;
      String message = getMessage(msgID, expr);
      throw new AciException(msgID, message);
    }

    /**
     * Evaluate authmethod bind rule using the provided evaluation context.
     * @param evalCtx  An evaluation context to use.
     * @return  An enumeration evaluation result.
     */
    public EnumEvalResult evaluate(AciEvalContext evalCtx) {
        EnumEvalResult matched =
             evalCtx.hasAuthenticationMethod(authMethod, saslMech);
        return matched.getRet(type, false);
    }
}
