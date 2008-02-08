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

import org.opends.server.types.LDAPURL;

/**
 * The UserDNTypeURL class contains the EnumUserDNType and the URL value,
 * of a "userdn" URL decoded by the UserDN.decode() method.
 */
public class UserDNTypeURL {

    /*
     * The DN type of the URL.
     */
    private EnumUserDNType dnType;

    /*
     * The URL value. Maybe a dummy value for types such as ANYONE or SELF.
     */
    private LDAPURL url;

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
