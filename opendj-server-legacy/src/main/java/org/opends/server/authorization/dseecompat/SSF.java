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

import org.forgerock.i18n.LocalizableMessage;
import static org.opends.messages.AccessControlMessages.*;

/**
 * The class represents the ssf keyword in a bind rule.SSF stands for
 * security strength factor.
 */
public class SSF implements KeywordBindRule {

    /** Enumeration representing the bind rule operation type. */
    private EnumBindRuleType type;

    private static final int MAX_KEY_BITS=1024;
    private int ssf;

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
        if ((valueAsInt <= 0) || (valueAsInt > MAX_KEY_BITS)) {
            LocalizableMessage message = WARN_ACI_SYNTAX_INVALID_SSF_RANGE.get(expr);
            throw new AciException(message);
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
    public EnumEvalResult evaluate(AciEvalContext evalCtx) {
        int currentSSF = evalCtx.getCurrentSSF();
        EnumEvalResult matched = getMatched(currentSSF);
        return matched.getRet(type, false);
    }

    private EnumEvalResult getMatched(int currentSSF) {
      switch (type) {
      case EQUAL_BINDRULE_TYPE:
      case NOT_EQUAL_BINDRULE_TYPE:
          if (currentSSF == ssf) {
            return EnumEvalResult.TRUE;
          }
          break;

      case LESS_OR_EQUAL_BINDRULE_TYPE:
          if (currentSSF <= ssf) {
            return EnumEvalResult.TRUE;
          }
          break;

      case LESS_BINDRULE_TYPE:
          if (currentSSF < ssf) {
            return EnumEvalResult.TRUE;
          }
          break;

      case GREATER_OR_EQUAL_BINDRULE_TYPE:
          if (currentSSF >= ssf) {
            return EnumEvalResult.TRUE;
          }
          break;

      case GREATER_BINDRULE_TYPE:
          if (currentSSF > ssf) {
            return EnumEvalResult.TRUE;
          }
      }
      return EnumEvalResult.FALSE;
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