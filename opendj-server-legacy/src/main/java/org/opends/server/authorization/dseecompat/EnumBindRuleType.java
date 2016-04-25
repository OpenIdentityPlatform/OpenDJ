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

/** This class provides an enumeration of the allowed bind rule types. */
enum EnumBindRuleType {
    /** The enumeration type when the bind rule has specified type of "=". */
    EQUAL_BINDRULE_TYPE             ("="),
    /** The enumeration type when the bind rule has specified type of "!=". */
    NOT_EQUAL_BINDRULE_TYPE         ("!="),
    /** The enumeration type when the bind rule has specified type of "<". */
    LESS_BINDRULE_TYPE              ("<"),
    /** The enumeration type when the bind rule has specified type of "<=". */
    LESS_OR_EQUAL_BINDRULE_TYPE     ("<="),
    /** The enumeration type when the bind rule has specified type of >". */
    GREATER_BINDRULE_TYPE           (">"),
    /** The enumeration type when the bind rule has specified type of ">=". */
    GREATER_OR_EQUAL_BINDRULE_TYPE  (">=");

    /** The bind rule type name. */
    private final String type;

    /**
     * Creates a new enumeration type for the specified bind rule type.
     * @param type The bind rule type name.
     */
    EnumBindRuleType(String type){
        this.type = type;
    }

    /**
     * Returns the comparison operator corresponding to this EnumBindRuleType.
     *
     * @return the string representing the comparison operator
     */
    public String getType()
    {
      return type;
    }

    /**
     * Checks to see if the type string is equal to the enumeration type
     * name.
     * @param type  The type name to check equality for.
     * @return  True if the keyword is equal to the specified name.
     */
    public boolean isBindRuleType(String type){
        return type.equals(this.type);
    }

    /**
     * Create a new enumeration type for the specified type name.
     * @param type  The name of the enumeration to create.
     * @return A new enumeration type for the name or null if the name is
     * not valid.
     */
    public static EnumBindRuleType createBindruleOperand(String type){
        if (type != null){
            for (EnumBindRuleType t : EnumBindRuleType.values()){
                if (t.isBindRuleType(type)){
                    return t;
                }
            }
        }
        return null;
    }
}
