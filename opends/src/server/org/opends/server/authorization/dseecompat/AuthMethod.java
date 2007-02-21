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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.server.authorization.dseecompat;

import static org.opends.server.authorization.dseecompat.AciMessages.*;
import static org.opends.server.messages.MessageHandler.getMessage;

/**
 * The AuthMethod class represents an authmethod bind rule keyword expression.
 */
public class AuthMethod implements KeywordBindRule {
    private EnumAuthMethod authMethod=null;
    private EnumBindRuleType type=null;

    /**
     * Create a class representing an authmethod bind rule keyword from the
     * provided method and bind rule type.
     * @param method An enumeration representing the method of the expression.
     * @param type An enumeration representing the type of the expression.
     */
    private AuthMethod(EnumAuthMethod method, EnumBindRuleType type) {
        this.authMethod=method;
        this.type=type;
    }

    /**
     * Decode a string representing a authmethod bind rule.
     * @param expr  The string representing the bind rule.
     * @param type An enumeration representing the bind rule type.
     * @return  An keyword bind rule class that can be used to evaluate the
     * bind rule.
     * @throws AciException If the expression string is invalid.
     */
    public static KeywordBindRule decode(String expr, EnumBindRuleType type)
    throws AciException  {
        EnumAuthMethod method=EnumAuthMethod.createAuthmethod(expr);
        if (method == null)
        {
            int msgID = MSGID_ACI_SYNTAX_INVALID_AUTHMETHOD_EXPRESSION;
            String message = getMessage(msgID, expr);
            throw new AciException(msgID, message);
        }
        return new AuthMethod(method, type);
    }

    /*
     * TODO Evaluate if AUTHMETHOD_NONE processing is correct. This was fixed
     * prior to Neil's review. Verify in a unit test.
     *
     * I'm not sure that the evaluate() method handles AUTHMETHOD_NONE
     * correctly. My understanding is that it should only match in cases
     * in which no authentication has been performed, but you have it
     * always matching.
     */
    /**
     * Evaluate authmethod bind rule using the provided evaluation context.
     * @param evalCtx  An evaluation context to use.
     * @return  An enumeration evaluation result.
     */
    public EnumEvalResult evaluate(AciEvalContext evalCtx) {
        EnumEvalResult matched=EnumEvalResult.FALSE;
        if(authMethod==EnumAuthMethod.AUTHMETHOD_NONE) {
            matched=EnumEvalResult.TRUE;
        } else if(authMethod==EnumAuthMethod.AUTHMETHOD_SIMPLE) {
            if(evalCtx.getAuthenticationMethod(false)
                    == EnumAuthMethod.AUTHMETHOD_SIMPLE){
                matched=EnumEvalResult.TRUE;
            }
        } else if(authMethod == EnumAuthMethod.AUTHMETHOD_SSL) {
            /*
             * TODO Verfiy that SSL authemethod is correctly handled in a
             * unit test.
             * I'm not sure that the evaluate() method correctly handles
             * SASL EXTERNAL in all cases.  My understanding is that in
             * DS 5/6, an authmethod of SSL is the same as an authmethod of
             * SASL EXTERNAL.  If that's true, then you don't properly handle
             * that condition.
             */
            if(authMethod == evalCtx.getAuthenticationMethod(true))
                    matched=EnumEvalResult.TRUE;
        } else {
            if(authMethod ==evalCtx.getAuthenticationMethod(false))
                matched=EnumEvalResult.TRUE;
        }
        return matched.getRet(type, false);
    }
}
