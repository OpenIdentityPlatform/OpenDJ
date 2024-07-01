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

/** This class provides an enumeration of the allowed bind rule keyword types. */
enum EnumBindRuleKeyword {
    /** The enumeration type when the bind rule has specified keyword of userdn. */
    USERDN     ("userdn"),
    /** The enumeration type when the bind rule has specified keyword of groupdn. */
    GROUPDN    ("groupdn"),
    /** The enumeration type when the bind rule has specified keyword of roledn. */
    ROLEDN     ("roledn"),
    /** The enumeration type when the bind rule has specified keyword of ip. */
    IP         ("ip"),
    /** The enumeration type when the bind rule has specified keyword of dns. */
    DNS        ("dns"),
    /** The enumeration type when the bind rule has specified keyword of dayofweek. */
    DAYOFWEEK  ("dayofweek"),
    /** The enumeration type when the bind rule has specified keyword of timeofday. */
    TIMEOFDAY  ("timeofday"),
    /** The enumeration type when the bind rule has specified keyword of userattr. */
    USERATTR ("userattr"),
    /** The enumeration type when the bind rule has specified keyword of authmethod. */
    AUTHMETHOD ("authmethod"),
    /** The enumeration type when the bind rule has specified keyword of ssf. */
    SSF("ssf");

    /** The keyword name. */
    private final String keyword;

    /**
     * Creates a new enumeration type for the specified keyword.
     * @param keyword The keyword name.
     */
    EnumBindRuleKeyword(String keyword){
        this.keyword = keyword;
    }

    /**
     * Checks to see if the keyword string is equal to the enumeration.
     * @param keywordStr   The keyword name to check equality for.
     * @return  True if the keyword is equal to the specified name.
     */
    public boolean isBindRuleKeyword(String keywordStr){
        return keywordStr.equalsIgnoreCase(this.keyword);
    }

    /**
     * Create a new enumeration type for the specified keyword name.
     * @param keywordStr The name of the enumeration to create.
     * @return A new enumeration type for the name or null if the name is
     * not valid.
     */
    public static EnumBindRuleKeyword createBindRuleKeyword(String keywordStr){
        if (keywordStr != null){
            for (EnumBindRuleKeyword t : EnumBindRuleKeyword.values()){
                if (t.isBindRuleKeyword(keywordStr)){
                    return t;
                }
            }
        }
        return null;
    }
}
