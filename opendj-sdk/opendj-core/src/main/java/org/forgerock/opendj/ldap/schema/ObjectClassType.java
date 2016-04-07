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
 * Copyright 2009 Sun Microsystems, Inc.
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
