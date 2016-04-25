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

/** This class provides an enumeration of the two access types (allow, deny). */
enum EnumAccessType {
    /** Allow access type. */
    ALLOW   ("allow"),
    /** Deny access type. */
    DENY    ("deny");

    /** The access type string. */
    private final String accessType;

    /**
     * Constructor that sets the accessType string.
     * @param accessType The access type string to set.
     */
    EnumAccessType (String accessType){
        this.accessType = accessType ;
    }

    /**
     * Checks if the access type is equal to the string
     * representation passed in.
     * @param type The string representation of the access type.
     * @return True if the access types are equal.
     */
    public boolean isAccessType(String type){
        return type.equalsIgnoreCase(accessType);
    }

    /*
     * TODO Make this method and all other Enum decode methods more efficient.
     *
     * Using the Enum.values() method is documented to be potentially slow.
     * If we ever expect to use the decode() method in a performance-critical
     * manner, then we should make it more efficient.  The same thing applies
     * to all of the other enumeration types defined in the package.
     */
    /**
     * Decodes an access type enumeration from a string passed into the method.
     * @param type The string representation of the access type.
     * @return   Return an EnumAccessType matching the string representation,
     * or null if the string is not valid.
     */
    public static EnumAccessType decode(String type){
        if (type != null){
            for (EnumAccessType t : EnumAccessType.values()) {
                if (t.isAccessType(type)){
                    return t;
                }
            }
        }
        return null;
    }
}
