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

/**
 * This class provides an enumeration of the allowed bind rule booelan types.
 */
public enum EnumBooleanTypes {

    /**
     * The enumeration type when the bind rule has specified boolean type of
     * "AND".
     */
    AND_BOOLEAN_TYPE               ("and"),
    /**
     * The enumeration type when the bind rule has specified boolean type of
     * "OR".
     */
    OR_BOOLEAN_TYPE                ("or"),
    /**
     * The enumeration type when the bind rule has specified boolean type of
     * "NOT".
     */
    NOT_BOOLEAN_TYPE                ("not");

    /*
    * The bind rule boolean type name.
     */
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
