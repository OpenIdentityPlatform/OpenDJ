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
 * This enumeration defines the set of possible attribute usage values that may
 * apply to an attribute type, as defined in RFC 2252.
 *
 * @see <a href="http://tools.ietf.org/html/rfc2252">RFC 2252 - Lightweight
 *      Directory Access Protocol (v3): Attribute Syntax Definitions</a>
 */
public enum AttributeUsage {
    /**
     * The attribute usage intended for user-defined attribute types.
     */
    USER_APPLICATIONS("userApplications", false),

    /**
     * The attribute usage intended for standard operational attributes.
     */
    DIRECTORY_OPERATION("directoryOperation", true),

    /**
     * The attribute usage intended for non-standard operational attributes
     * shared among multiple DSAs.
     */
    DISTRIBUTED_OPERATION("distributedOperation", true),

    /**
     * The attribute usage intended for non-standard operational attributes used
     * by a single DSA.
     */
    DSA_OPERATION("dSAOperation", true);

    /** The string representation of this attribute usage. */
    private final String usageString;

    /**
     * Flag indicating whether or not the usage should be categorized as
     * operational.
     */
    private final boolean isOperational;

    /**
     * Creates a new attribute usage with the provided string representation.
     *
     * @param usageString
     *            The string representation of this attribute usage.
     * @param isOperational
     *            <code>true</code> if attributes having this attribute usage
     *            are operational, or <code>false</code> otherwise.
     */
    private AttributeUsage(final String usageString, final boolean isOperational) {
        this.usageString = usageString;
        this.isOperational = isOperational;
    }

    /**
     * Determine whether or not attributes having this attribute usage are
     * operational.
     *
     * @return Returns <code>true</code> if attributes having this attribute
     *         usage are operational, or <code>false</code> otherwise.
     */
    public boolean isOperational() {
        return isOperational;
    }

    /**
     * Retrieves a string representation of this attribute usage.
     *
     * @return A string representation of this attribute usage.
     */
    @Override
    public String toString() {
        return usageString;
    }
}
