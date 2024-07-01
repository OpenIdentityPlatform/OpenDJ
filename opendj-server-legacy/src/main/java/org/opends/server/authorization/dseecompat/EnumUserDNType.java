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
 * Portions Copyright 2016 ForgeRock AS.
 */
package org.opends.server.authorization.dseecompat;

/**
 * Enumeration that represents the type an "userdn" keyword DN can have.
 * The issues is the syntax allows invalid URLs such as "ldap:///anyone"
 * and "ldap:///self".  The strategy is to use this class to hold
 * the type and another class UserDNTypeURL to hold both this type and URL.
 *
 * If the URL is an invalid URL, then a dummy URL is saved.
 * For types such as URL, DN and DNPATTERN, the actual URL is saved and can
 * be retrieved by the UserDN.evaluate() method when needed. The dummy URL is
 * ignored in the UserDN.evaluate() method for types such as: ALL, PARENT,
 * SELF and ANYONE.
 */
enum EnumUserDNType {
        /**
         * The enumeration type when the "userdn" URL contains only a DN (no
         * filter or scope) and that DN has no pattern.
         */
        DN(0),
        /**
         * The enumeration type when the "userdn" URL contains only a DN (no
         * filter or scope) and that DN has a substring pattern.
         */
        DNPATTERN(1),
        /** The enumeration type when the "userdn" URL has the value of: "ldap:///all". */
        ALL(2),
        /** The enumeration type when the "userdn" URL has the value of: "ldap:///parent". */
        PARENT(3),
        /** The enumeration type when the "userdn" URL has the value of: "ldap:///self". */
        SELF(4),
        /** The enumeration type when the "userdn" URL has the value of: "ldap:///anyone". */
        ANYONE(5),
        /** The enumeration type when the "userdn" URL is contains a DN (suffix), a scope and a filter. */
        URL(6);

        /**
         * Constructor taking an integer value.
         * @param v Integer value.
         */
        EnumUserDNType(int v) {}
}
