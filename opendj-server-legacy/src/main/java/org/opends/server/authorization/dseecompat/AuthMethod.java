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
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.core.DirectoryServer;

/**
 * The AuthMethod class represents an authmethod bind rule keyword expression.
 */
public class AuthMethod implements KeywordBindRule {

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();


    /**
     * Enumeration representing the authentication method.
     */
    private EnumAuthMethod authMethod;

    /**
     * The SASL mechanism if the authentication method is SASL.
     */
    private String saslMech;

    /**
     * Enumeration representing the bind rule operation type.
     */
    private EnumBindRuleType type;

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
          logger.info(NOTE_ACI_SYNTAX_DUBIOUS_AUTHMETHOD_SASL_MECHANISM, saslMech);
        }
        return new AuthMethod(EnumAuthMethod.AUTHMETHOD_SASL, saslMech, type);
      }

      LocalizableMessage message = WARN_ACI_SYNTAX_INVALID_AUTHMETHOD_EXPRESSION.get(expr);
      throw new AciException(message);
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

    /** {@inheritDoc} */
    @Override
    public String toString()
    {
      final StringBuilder sb = new StringBuilder();
      toString(sb);
      return sb.toString();
    }

    /** {@inheritDoc} */
    @Override
    public final void toString(StringBuilder buffer)
    {
      buffer.append(super.toString());
    }

}
