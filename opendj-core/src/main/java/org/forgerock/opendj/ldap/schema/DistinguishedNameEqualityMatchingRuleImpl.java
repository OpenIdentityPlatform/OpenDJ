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
 * Copyright 2009-2010 Sun Microsystems, Inc.
 * Portions copyright 2011-2016 ForgeRock AS.
 * Portions copyright 2024-2026 3A Systems, LLC
 */
package org.forgerock.opendj.ldap.schema;

import static com.forgerock.opendj.ldap.CoreMessages.ERR_ATTR_SYNTAX_DN_MAX_DEPTH;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.*;

import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeException;

/**
 * This class defines the distinguishedNameMatch matching rule defined in X.520
 * and referenced in RFC 2252.
 */
final class DistinguishedNameEqualityMatchingRuleImpl extends AbstractEqualityMatchingRuleImpl {

    /**
     * The maximum number of nested DN-syntax attribute values that will be normalized.
     * <p>
     * Normalizing a DN-syntax attribute value requires parsing the value as a DN and
     * normalizing it, which in turn normalizes any nested DN-syntax attribute value, and
     * so on recursively. This bounds that recursion to protect against stack overflow for
     * maliciously or accidentally crafted values (see OpenDJ issue #648).
     */
    static final int MAX_NESTED_DN_DEPTH = 100;

    /**
     * The maximum size, in bytes, of a normalized DN-syntax attribute value.
     * <p>
     * Each nesting level escapes the reserved separator bytes of the level below, which
     * roughly doubles the number of reserved bytes per level. A crafted value can therefore
     * cause the normalized form to grow exponentially with the nesting depth (see OpenDJ
     * issue #648). Values whose normalized form would exceed this limit are rejected so that
     * the AVA falls back to a byte-wise comparison instead of consuming unbounded CPU/memory.
     */
    static final int MAX_NORMALIZED_VALUE_SIZE = 1 << 20;

    /** Tracks the current DN-syntax value normalization recursion depth per thread. */
    private static final ThreadLocal<int[]> CURRENT_DEPTH = new ThreadLocal<int[]>() {
        @Override
        protected int[] initialValue() {
            return new int[1];
        }
    };

    DistinguishedNameEqualityMatchingRuleImpl() {
        super(EMR_DN_NAME);
    }

    @Override
    public ByteString normalizeAttributeValue(final Schema schema, final ByteSequence value)
            throws DecodeException {
        final int[] depth = CURRENT_DEPTH.get();
        if (depth[0] >= MAX_NESTED_DN_DEPTH) {
            throw DecodeException.error(ERR_ATTR_SYNTAX_DN_MAX_DEPTH.get(value.toString(), MAX_NESTED_DN_DEPTH));
        }
        depth[0]++;
        try {
            DN dn = DN.valueOf(value.toString(), schema.asNonStrictSchema());
            final ByteString normalized = dn.toNormalizedByteString();
            if (normalized.length() > MAX_NORMALIZED_VALUE_SIZE) {
                throw DecodeException.error(ERR_ATTR_SYNTAX_DN_MAX_DEPTH.get(value.toString(), MAX_NESTED_DN_DEPTH));
            }
            return normalized;
        } catch (final LocalizedIllegalArgumentException e) {
            throw DecodeException.error(e.getMessageObject());
        } finally {
            depth[0]--;
        }
    }

    @Override
    public String keyToHumanReadableString(ByteSequence key) {
        return key.toByteString().toASCIIString();
    }
}
