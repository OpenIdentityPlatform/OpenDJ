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
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions copyright 2011 ForgeRock AS
 */

package org.forgerock.opendj.ldap;

import org.forgerock.opendj.ldap.schema.Schema;

/**
 * Schema resolvers are included with a set of {@code DecodeOptions} in order to
 * allow application to control how {@code Schema} instances are selected when
 * decoding requests and responses.
 * <p>
 * Implementations must be thread safe. More specifically, any schema caching
 * performed by the implementation must be capable of handling multiple
 * concurrent schema requests.
 *
 * @see Schema
 * @see DecodeOptions
 */
public interface SchemaResolver {
    /**
     * A schema resolver which always returns the current default schema as
     * returned by {@link Schema#getDefaultSchema()}.
     */
    SchemaResolver DEFAULT = new SchemaResolver() {

        public Schema resolveSchema(String dn) {
            return Schema.getDefaultSchema();
        }
    };

    /**
     * Finds the appropriate schema for use with the provided distinguished
     * name.
     * <p>
     * Schema resolution must always succeed regardless of any errors that
     * occur.
     *
     * @param dn
     *            The string representation of a distinguished name associated
     *            with an entry whose schema is to be located.
     * @return The appropriate schema for use with the provided distinguished
     *         name.
     */
    Schema resolveSchema(String dn);
}
