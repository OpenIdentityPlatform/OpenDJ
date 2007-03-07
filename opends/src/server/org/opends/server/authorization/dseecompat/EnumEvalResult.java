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

/**
 * This class provides an enumeration of evaluation results returned by
 * the bind rule evaluation methods.
 */
public enum EnumEvalResult {

    /**
     * This enumeration is returned when the result of the evaluation is TRUE.
     */
    TRUE(0),
    /**
     * This enumeration is returned when the result of the evaluation is FALSE.
     */
    FALSE(1),
    /**
     * This enumeration is returned when the result of the evaluation is FAIL.
     * This should only be returned when a system failure occurred.
     */
    FAIL(2),
    /**
     * This is an internal enumeration used during evaluation of bind rule when
     * internal processing of the evaluation is undefined. It is never returned
     * back as a result of the evaluation.
     */
    ERR(3);

    /**
     * Create a new enumeration type for the specified result value.
     * @param v The value of the result.
     */
    EnumEvalResult(int v) {
    }

    /**
     * The method tries to determine if the result was undefined, and if so
     * it returns an FAIL enumeration. If the result was not undefined (the
     * common case for all of the bind rule evaluations), then the bind rule
     * type is examined to see if the result needs to be flipped (type equals
     * NOT_EQUAL_BINDRULE_TYPE).
     * @param type The bind rule type enumeration of the bind rule.
     * @param undefined  A flag that signals the the result was undefined.
     * @return An enumeration containing the correct result after processing
     * the undefined field and the bind rule type enumeration.
     */
    public EnumEvalResult getRet(EnumBindRuleType type, boolean undefined) {
        EnumEvalResult ret=this;
        if(this.equals(EnumEvalResult.TRUE) || !undefined) {
            if(type.equals(EnumBindRuleType.NOT_EQUAL_BINDRULE_TYPE))
                if(this.equals(EnumEvalResult.TRUE))
                    ret=EnumEvalResult.FALSE;
                else
                    ret=EnumEvalResult.TRUE;
        } else
            ret=EnumEvalResult.FAIL;
        return ret;
    }

    /**
     * This method is used to possibly negate the result of a simple bind rule
     * evaluation. If the boolean is true than the result is negated.
     * @param v The enumeration result of the simple bind rule evaluation.
     * @param n If true the result should be negated (TRUE->FALSE, FALSE->TRUE).
     * @return  A possibly negated enumeration result.
     */
    public  static EnumEvalResult negateIfNeeded(EnumEvalResult v, boolean n) {
        if(n) {
            if(v.equals(EnumEvalResult.TRUE))
                v=EnumEvalResult.FALSE;
            else
                v=EnumEvalResult.TRUE;
        }
        return v;
    }

    /**
     * Helper method that converts this enumeration to a boolean. Usually the
     * FAIL enumeration has been handled before this is called.
     * @return True if the enumeration is TRUE, else false.
     */
    public boolean getBoolVal() {
        return this == EnumEvalResult.TRUE;
    }
}
