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
 * Copyright 2010 Sun Microsystems, Inc.
 * Portions copyright 2011-2016 ForgeRock AS.
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
        @Override
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
