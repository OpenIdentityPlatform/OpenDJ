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

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.core.DirectoryServer;

/** The AuthMethod class represents an authmethod bind rule keyword expression. */
public class AuthMethod implements KeywordBindRule {
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

    /** Enumeration representing the authentication method. */
    private final EnumAuthMethod authMethod;
    /** The SASL mechanism if the authentication method is SASL. */
    private final String saslMech;
    /** Enumeration representing the bind rule operation type. */
    private final EnumBindRuleType type;

    /**
     * Create a class representing an authmethod bind rule keyword from the
     * provided method and bind rule type.
     * @param type An enumeration representing the type of the expression.
     * @param saslMech The string representation of the SASL Mechanism.
     * @param method  An Enumeration of the authentication method.
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
      if ("none".equals(lowerExpr))
      {
        return new AuthMethod(EnumAuthMethod.AUTHMETHOD_NONE, null, type);
      }
      else if ("simple".equals(lowerExpr))
      {
        return new AuthMethod(EnumAuthMethod.AUTHMETHOD_SIMPLE, null, type);
      }
      else if ("ssl".equals(lowerExpr))
      {
        return new AuthMethod(EnumAuthMethod.AUTHMETHOD_SSL, "EXTERNAL", type);
      }
      else if (expr.length() > 5 && lowerExpr.startsWith("sasl "))
      {
        String saslMech = expr.substring(5);
        if (DirectoryServer.getSASLMechanismHandler(saslMech) == null) {
          logger.info(NOTE_ACI_SYNTAX_DUBIOUS_AUTHMETHOD_SASL_MECHANISM, saslMech);
        }
        return new AuthMethod(EnumAuthMethod.AUTHMETHOD_SASL, saslMech, type);
      }

      throw new AciException(WARN_ACI_SYNTAX_INVALID_AUTHMETHOD_EXPRESSION.get(expr));
    }

    /**
     * Evaluate authmethod bind rule using the provided evaluation context.
     * @param evalCtx  An evaluation context to use.
     * @return  An enumeration evaluation result.
     */
    @Override
    public EnumEvalResult evaluate(AciEvalContext evalCtx) {
        EnumEvalResult matched =
             evalCtx.hasAuthenticationMethod(authMethod, saslMech);
        return matched.getRet(type, false);
    }

    @Override
    public String toString()
    {
      final StringBuilder sb = new StringBuilder();
      toString(sb);
      return sb.toString();
    }

    @Override
    public final void toString(StringBuilder buffer)
    {
      buffer.append(super.toString());
    }
}
