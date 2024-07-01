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

import org.opends.server.types.LDAPURL;

/**
 * The UserDNTypeURL class contains the EnumUserDNType and the URL value,
 * of a "userdn" URL decoded by the UserDN.decode() method.
 */
public class UserDNTypeURL {

    /** The DN type of the URL. */
    private final EnumUserDNType dnType;
    /** The URL value. Maybe a dummy value for types such as ANYONE or SELF. */
    private final LDAPURL url;

    /**
     * Create a class representing the "userdn" URL decoded by the
     * UserDN.decode() method.
     * @param dnType The type of the URL determined by examining the DN
     * or suffix.
     * @param url The URL itself from the ACI "userdn" string expression.
     */
    UserDNTypeURL(EnumUserDNType dnType, LDAPURL url) {
        this.url=url;
        this.dnType=dnType;
    }

    /**
     * Returns the DN type.
     * @return The DN type of the URL.
     */
    public EnumUserDNType getUserDNType() {
        return this.dnType;
    }

    /** Returns the URL.
     * @return The URL decoded by the UserDN.decode() method.
     */
    public LDAPURL getURL() {
        return this.url;
    }
}
