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
 * Portions Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.server.authorization.dseecompat;

/** This class provides an enumeration of the allowed bind rule boolean types. */
enum EnumBooleanTypes {
    /** The enumeration type when the bind rule has specified boolean type of "AND". */
    AND_BOOLEAN_TYPE               ("and"),
    /** The enumeration type when the bind rule has specified boolean type of "OR". */
    OR_BOOLEAN_TYPE                ("or"),
    /** The enumeration type when the bind rule has specified boolean type of "NOT". */
    NOT_BOOLEAN_TYPE                ("not");

    /** The bind rule boolean type name. */
    private final String booleanType;

    /**
     * Creates a new enumeration type for the specified bind rule boolean type.
     * @param booleanType  The boolean type name.
     */
    EnumBooleanTypes(String booleanType){
        this.booleanType = booleanType;
    }

    /**
     * Checks to see if the boolean type string is equal to the enumeration type
     * name.
     * @param booleanType  The type name to check equality for.
     * @return  True if the keyword is equal to the specified name.
     */
    public boolean isBindRuleBooleanOperand(String booleanType){
        return booleanType.equalsIgnoreCase(this.booleanType);
    }

    /**
     * Create a new enumeration type for the specified boolean type name.
     * @param booleanType  The name of the enumeration to create.
     * @return A new enumeration type for the name or null if the name is
     * not valid.
     */
    public static
    EnumBooleanTypes createBindruleOperand(String booleanType) {
        if (booleanType != null){
          for (EnumBooleanTypes t : EnumBooleanTypes.values()) {
                if (t.isBindRuleBooleanOperand(booleanType)) {
                    return t;
                }
            }
        }
        return null;
    }
}
