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

import org.forgerock.i18n.LocalizableMessage;

/** The class represents the ssf keyword in a bind rule.SSF stands for security strength factor. */
public class SSF implements KeywordBindRule {
    private static final int MAX_KEY_BITS=1024;

    /** Enumeration representing the bind rule operation type. */
    private final EnumBindRuleType type;
    private final int ssf;

    private SSF(int ssf, EnumBindRuleType type) {
        this.ssf = ssf;
        this.type = type;
    }

    /**
     * Create SSF instance using the specified expression string and bind rule
     * type enumeration.
     * @param expr The expression string.
     * @param type The bind rule type enumeration.
     * @return A SSF instance.
     * @throws AciException If the SSF instance cannot be created.
     */
    static SSF decode(String expr, EnumBindRuleType type) throws AciException {
        int valueAsInt = 0;
        try {
            valueAsInt = Integer.parseInt(expr);
        } catch (NumberFormatException nfe) {
            LocalizableMessage message =
                 WARN_ACI_SYNTAX_INVALID_SSF_FORMAT.get(expr, nfe.getMessage());
            throw new AciException(message);
        }
        if (valueAsInt <= 0 || valueAsInt > MAX_KEY_BITS) {
            throw new AciException(WARN_ACI_SYNTAX_INVALID_SSF_RANGE.get(expr));
        }
        return new SSF(valueAsInt, type);
    }

    /**
     * Evaluate the specified evaluation context.
     * @param evalCtx The evaluation context to evaluate.
     *
     * @return An evaluation result enumeration containing the result of the
     *         context evaluation.
     */
    @Override
    public EnumEvalResult evaluate(AciEvalContext evalCtx) {
        EnumEvalResult matched = getMatched(evalCtx.getCurrentSSF());
        return matched.getRet(type, false);
    }

    private EnumEvalResult getMatched(int currentSSF) {
      return getMatched0(currentSSF) ? EnumEvalResult.TRUE : EnumEvalResult.FALSE;
    }

    private boolean getMatched0(int currentSSF)
    {
      switch (type) {
      case EQUAL_BINDRULE_TYPE:
      case NOT_EQUAL_BINDRULE_TYPE:
          return currentSSF == ssf;
      case LESS_OR_EQUAL_BINDRULE_TYPE:
          return currentSSF <= ssf;
      case LESS_BINDRULE_TYPE:
          return currentSSF < ssf;
      case GREATER_OR_EQUAL_BINDRULE_TYPE:
          return currentSSF >= ssf;
      case GREATER_BINDRULE_TYPE:
          return currentSSF > ssf;
      default:
          return false;
      }
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
