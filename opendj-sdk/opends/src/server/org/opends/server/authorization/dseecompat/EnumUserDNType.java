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
public enum EnumUserDNType {

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
        /**
         * The enumeration type when the "userdn" URL has the value of:
         *  "ldap:///all".
         */
        ALL(2),
        /**
         * The enumeration type when the "userdn" URL has the value of:
         *  "ldap:///parent".
         */
        PARENT(3),
        /**
         * The enumeration type when the "userdn" URL has the value of:
         *  "ldap:///self".
         */
        SELF(4),
        /**
         * The enumeration type when the "userdn" URL has the value of:
         *  "ldap:///anyone".
         */
        ANYONE(5),
        /**
         * The enumeration type when the "userdn" URL is contains a DN (suffix),
         * a scope and a filter.
         */
        URL(6);

        /**
         * Constructor taking an integer value.
         * @param v Integer value.
         */
        EnumUserDNType(int v) {}
}
