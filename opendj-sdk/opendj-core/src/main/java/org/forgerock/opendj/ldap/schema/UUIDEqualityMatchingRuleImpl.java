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
package org.forgerock.opendj.ldap.schema;

import static com.forgerock.opendj.ldap.CoreMessages.WARN_ATTR_SYNTAX_UUID_EXPECTED_DASH;
import static com.forgerock.opendj.ldap.CoreMessages.WARN_ATTR_SYNTAX_UUID_EXPECTED_HEX;
import static com.forgerock.opendj.ldap.CoreMessages.WARN_ATTR_SYNTAX_UUID_INVALID_LENGTH;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.EMR_UUID_NAME;

import java.util.Collection;
import java.util.Collections;

import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteSequenceReader;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.spi.IndexQueryFactory;
import org.forgerock.opendj.ldap.spi.Indexer;
import org.forgerock.opendj.ldap.spi.IndexingOptions;

/**
 * This class defines the uuidMatch matching rule defined in RFC 4530. It will
 * be used as the default equality matching rule for the UUID syntax.
 */
final class UUIDEqualityMatchingRuleImpl extends AbstractMatchingRuleImpl {
    /**
     * An optimized hash based indexer which stores a 4 byte hash of the UUID. Mix the bytes using XOR similar
     * to UUID.hashCode(): 128 down to 64 then 32.
     */
    private static final Collection<? extends Indexer> INDEXERS = Collections.singleton(new Indexer() {
        @Override
        public void createKeys(Schema schema, ByteSequence value, Collection<ByteString> keys) throws DecodeException {
            keys.add(hash(normalize(value)));
        }

        @Override
        public String keyToHumanReadableString(ByteSequence key) {
            return key.toByteString().toHexString();
        }

        @Override
        public String getIndexID() {
            return EMR_UUID_NAME;
        }
    });

    UUIDEqualityMatchingRuleImpl() {
        // Nothing to do.
    }

    @Override
    public ByteString normalizeAttributeValue(final Schema schema, final ByteSequence value) throws DecodeException {
        return normalize(value).toByteString();
    }

    @Override
    public Assertion getAssertion(final Schema schema, final ByteSequence assertionValue) throws DecodeException {
        final ByteString normalizedAssertionValue = normalizeAttributeValue(schema, assertionValue);
        return new Assertion() {
            @Override
            public ConditionResult matches(final ByteSequence normalizedAttributeValue) {
                return ConditionResult.valueOf(normalizedAssertionValue.equals(normalizedAttributeValue));
            }

            @Override
            public <T> T createIndexQuery(final IndexQueryFactory<T> factory) throws DecodeException {
                return factory.createExactMatchQuery(EMR_UUID_NAME, hash(normalizedAssertionValue));
            }
        };
    }

    @Override
    public Collection<? extends Indexer> createIndexers(final IndexingOptions options) {
        return INDEXERS;
    }

    // Package private so that it can be reused by ordering matching rule.
    static ByteSequence normalize(final ByteSequence value) throws DecodeException {
        if (value.length() != 36) {
            throw DecodeException.error(WARN_ATTR_SYNTAX_UUID_INVALID_LENGTH.get(value, value.length()));
        }
        final ByteStringBuilder builder = new ByteStringBuilder(16);
        for (int i = 0; i < 36; i++) {
            // The 9th, 14th, 19th, and 24th characters must be dashes. All others must be hex.
            switch (i) {
            case 8:
            case 13:
            case 18:
            case 23:
                final char c = (char) value.byteAt(i);
                if (c != '-') {
                    throw DecodeException.error(WARN_ATTR_SYNTAX_UUID_EXPECTED_DASH.get(value, i, c));
                }
                break;
            default:
                final int high4Bits = decodeHexByte(value, i++);
                final int low4Bits = decodeHexByte(value, i);
                builder.appendByte((high4Bits << 4) | low4Bits);
                break;
            }
        }
        return builder;
    }

    private static int decodeHexByte(final ByteSequence value, final int i) throws DecodeException {
        final char c = (char) value.byteAt(i);
        switch (c) {
        case '0':
        case '1':
        case '2':
        case '3':
        case '4':
        case '5':
        case '6':
        case '7':
        case '8':
        case '9':
            return c - '0';
        case 'a':
        case 'b':
        case 'c':
        case 'd':
        case 'e':
        case 'f':
            return c - 'a' + 10;
        case 'A':
        case 'B':
        case 'C':
        case 'D':
        case 'E':
        case 'F':
            return c - 'A' + 10;
        default:
            throw DecodeException.error(WARN_ATTR_SYNTAX_UUID_EXPECTED_HEX.get(value, i, c));
        }
    }

    private static ByteString hash(final ByteSequence normalizeAttributeValue) {
        final ByteSequenceReader uuid128Bytes = normalizeAttributeValue.asReader();
        final long uuidHigh64 = uuid128Bytes.readLong();
        final long uuidLow64 = uuid128Bytes.readLong();
        final long uuid64 = uuidHigh64 ^ uuidLow64;
        final int hash32 = ((int) (uuid64 >> 32)) ^ (int) uuid64;
        return ByteString.valueOfInt(hash32);
    }
}
