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

import org.opends.messages.Message;
import static org.opends.messages.AccessControlMessages.*;

/**
 * The class represents the ssf keyword in a bind rule.SSF stands for
 * security strength factor.
 *
 */
public class SSF implements KeywordBindRule {

    /*
     *  Enumeration representing the bind rule operation type.
     */
    private EnumBindRuleType type=null;

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
    static SSF
    decode(String expr, EnumBindRuleType type) throws AciException  {
        int valueAsInt = 0;
        try {
            valueAsInt = Integer.parseInt(expr);
        } catch (NumberFormatException nfe) {
            Message message =
                 WARN_ACI_SYNTAX_INVALID_SSF_FORMAT.get(expr, nfe.getMessage());
            throw new AciException(message);
        }
        if ((valueAsInt <= 0) || (valueAsInt > MAX_KEY_BITS)) {
            Message message = WARN_ACI_SYNTAX_INVALID_SSF_RANGE.get(expr);
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
        EnumEvalResult matched=EnumEvalResult.FALSE;
        int currentSSF = evalCtx.getCurrentSSF();
        switch (type) {
        case EQUAL_BINDRULE_TYPE:
        case NOT_EQUAL_BINDRULE_TYPE:
            if (currentSSF == ssf)
                matched=EnumEvalResult.TRUE;
            break;

        case LESS_OR_EQUAL_BINDRULE_TYPE:
            if (currentSSF <= ssf)
                matched=EnumEvalResult.TRUE;
            break;

        case LESS_BINDRULE_TYPE:
            if (currentSSF < ssf)
                matched=EnumEvalResult.TRUE;
            break;

        case GREATER_OR_EQUAL_BINDRULE_TYPE:
            if (currentSSF >= ssf)
                matched=EnumEvalResult.TRUE;
            break;

        case GREATER_BINDRULE_TYPE:
            if (currentSSF > ssf)
                matched=EnumEvalResult.TRUE;
        }
        return matched.getRet(type, false);
    }
}