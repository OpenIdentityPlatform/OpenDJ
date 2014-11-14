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
 *      Portions copyright 2014 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.schema;

import static com.forgerock.opendj.ldap.CoreMessages.WARN_ATTR_SYNTAX_ILLEGAL_INTEGER;

import java.math.BigInteger;
import java.util.Comparator;

import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.spi.IndexQueryFactory;

/**
 * This class defines the integerOrderingMatch matching rule defined in X.520
 * and referenced in RFC 4519. The implementation of this matching rule is
 * intentionally aligned with the ordering matching rule so that they could
 * potentially share the same index.
 */
final class IntegerOrderingMatchingRuleImpl extends AbstractOrderingMatchingRuleImpl {
    static final Comparator<ByteSequence> COMPARATOR = new Comparator<ByteSequence>() {
        @Override
        public int compare(final ByteSequence o1, final ByteSequence o2) {
            final BigInteger i1 = decodeNormalizedValue(o1);
            final BigInteger i2 = decodeNormalizedValue(o2);
            return i1.compareTo(i2);
        }
    };

    static ByteString normalizeValueAndEncode(final ByteSequence value) throws DecodeException {
        return encodeNormalizedValue(normalizeValue(value));
    }

    private static BigInteger normalizeValue(final ByteSequence value) throws DecodeException {
        try {
            return new BigInteger(value.toString());
        } catch (final Exception e) {
            throw DecodeException.error(WARN_ATTR_SYNTAX_ILLEGAL_INTEGER.get(value));
        }
    }

    private static BigInteger decodeNormalizedValue(final ByteSequence normalizedValue) {
        return new BigInteger(normalizedValue.toByteArray());
    }

    private static ByteString encodeNormalizedValue(final BigInteger normalizedValue) {
        return ByteString.wrap(normalizedValue.toByteArray());
    }

    @Override
    public Comparator<ByteSequence> comparator(final Schema schema) {
        return COMPARATOR;
    }

    @Override
    public Assertion getAssertion(final Schema schema, final ByteSequence value)
            throws DecodeException {
        final BigInteger normAssertion = normalizeValue(value);
        return new Assertion() {
            @Override
            public <T> T createIndexQuery(final IndexQueryFactory<T> factory)
                    throws DecodeException {
                return factory.createRangeMatchQuery(getIndexId(), ByteString.empty(),
                        encodeNormalizedValue(normAssertion), false, false);
            }

            @Override
            public ConditionResult matches(final ByteSequence normValue) {
                return ConditionResult.valueOf(decodeNormalizedValue(normValue).compareTo(
                        normAssertion) < 0);
            }
        };
    }

    @Override
    public Assertion getGreaterOrEqualAssertion(final Schema schema, final ByteSequence value)
            throws DecodeException {
        final BigInteger normAssertion = normalizeValue(value);
        return new Assertion() {
            @Override
            public <T> T createIndexQuery(final IndexQueryFactory<T> factory)
                    throws DecodeException {
                return factory.createRangeMatchQuery(getIndexId(), encodeNormalizedValue(normAssertion), ByteString
                        .empty(), true, false);
            }

            @Override
            public ConditionResult matches(final ByteSequence normValue) {
                return ConditionResult.valueOf(decodeNormalizedValue(normValue).compareTo(
                        normAssertion) >= 0);
            }
        };
    }

    @Override
    public Assertion getLessOrEqualAssertion(final Schema schema, final ByteSequence value)
            throws DecodeException {
        final BigInteger normAssertion = normalizeValue(value);
        return new Assertion() {
            @Override
            public <T> T createIndexQuery(final IndexQueryFactory<T> factory)
                    throws DecodeException {
                return factory.createRangeMatchQuery(getIndexId(), ByteString.empty(),
                        encodeNormalizedValue(normAssertion), false, true);
            }

            @Override
            public ConditionResult matches(final ByteSequence normValue) {
                return ConditionResult.valueOf(decodeNormalizedValue(normValue).compareTo(
                        normAssertion) <= 0);
            }
        };
    }

    @Override
    public ByteString normalizeAttributeValue(final Schema schema, final ByteSequence value)
            throws DecodeException {
        return normalizeValueAndEncode(value);
    }
}
