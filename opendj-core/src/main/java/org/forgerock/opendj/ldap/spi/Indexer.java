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
 *      Portions copyright 2014-2015 ForgeRock AS
 */
package org.forgerock.opendj.ldap.spi;

import java.util.Collection;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.schema.Schema;

/**
 * This class is registered with a Backend and it provides callbacks
 * for indexing attribute values. An index implementation will use
 * this interface to create the keys for an attribute value.
 */
public interface Indexer {

    /**
     * Returns an index identifier associated with this indexer. An identifier
     * should be selected based on the matching rule type. A unique identifier
     * will map to a unique index database in the backend implementation. If
     * multiple matching rules need to share the index database, the
     * corresponding indexers should always use the same identifier.
     *
     * @return index ID A String containing the ID associated with this indexer.
     */
    String getIndexID();

    /**
     * Generates the set of index keys for an attribute.
     *
     * @param schema
     *          The schema in which the associated matching rule is defined.
     * @param value
     *          The attribute value for which keys are required.
     * @param keys
     *          A collection where to add the created keys.
     * @throws DecodeException if an error occurs while normalizing the value
     */
    void createKeys(Schema schema, ByteSequence value, Collection<ByteString> keys) throws DecodeException;

    /**
     * Returns a human readable representation of the key.
     * Does a best effort conversion from an index key to a string that can be printed, as
     * used by the diagnostic tools, which are the only users of the method.
     * It is not necessary for the resulting string to exactly match the value it was
     * generated from.
     *
     * @param key the byte string for the index key.
     * @return a human readable representation of the key
     */
    String keyToHumanReadableString(ByteSequence key);
}
