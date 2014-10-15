/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package org.forgerock.opendj.ldap.schema;

/**
 * This enumeration defines the set of possible objectclass types that may be
 * used, as defined in RFC 2252.
 *
 * @see <a href="http://tools.ietf.org/html/rfc2252">RFC 2252 - Lightweight
 *      Directory Access Protocol (v3): Attribute Syntax Definitions</a>
 */
public enum ObjectClassType {
    /**
     * The objectclass type that to use for classes declared "abstract".
     */
    ABSTRACT("ABSTRACT"),

    /**
     * The objectclass type that to use for classes declared "structural".
     */
    STRUCTURAL("STRUCTURAL"),

    /**
     * The objectclass type that to use for classes declared "auxiliary".
     */
    AUXILIARY("AUXILIARY");

    /** The string representation of this objectclass type. */
    private final String typeString;

    /**
     * Creates a new objectclass type with the provided string representation.
     *
     * @param typeString
     *            The string representation for this objectclass type.
     */
    private ObjectClassType(final String typeString) {
        this.typeString = typeString;
    }

    /**
     * Retrieves a string representation of this objectclass type.
     *
     * @return A string representation of this objectclass type.
     */
    @Override
    public String toString() {
        return typeString;
    }
}
