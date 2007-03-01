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
 *  This class provides an enumeration of the valid ACI target operators.
 */
public enum EnumTargetOperator {
    /**
    * This enumeration is returned when the target operator is  "=".
     */
    EQUALITY        ("="),
    /**
    * This enumeration is returned when the target operator is  "!=".
     */
    NOT_EQUALITY    ("!=");

    /**
     * The target operator name.
     */
    private final String operator;

    /**
     * Create an enumeration of the provided operator name.
     * @param operator The operator name to create.
     */
    EnumTargetOperator(String operator){
        this.operator = operator;
    }

    /**
     * Checks if the provided operator name is equal to the enumeration.
     * @param op The operator name to check for.
     * @return  True if the operator name is equal to the enumeration.
     */
    public boolean isOperator(String op){
        return op.equalsIgnoreCase(operator);
    }

    /**
     * Creates an enumeration of the specified operator type name.
     * @param op The operator type name to create.
     * @return  Return an enumeration of the operator type name or null if the
     * name is invalid.
     */
    public static EnumTargetOperator createOperator(String op){
        if (op != null){
            for (EnumTargetOperator t : EnumTargetOperator.values()){
                if (t.isOperator(op)){
                    return t;
                }
            }
        }
        return null;
    }
}
