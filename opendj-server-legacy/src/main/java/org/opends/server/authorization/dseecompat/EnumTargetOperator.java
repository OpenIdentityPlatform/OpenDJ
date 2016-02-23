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
 * Portions Copyright 2015 ForgeRock AS.
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

    /** The target operator name. */
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
