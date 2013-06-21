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

import java.util.Comparator;
import java.util.List;

import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.DecodeException;

/**
 * This class implements a default equality or approximate matching rule that
 * matches normalized values in byte order.
 */
abstract class AbstractMatchingRuleImpl implements MatchingRuleImpl {
    static class DefaultEqualityAssertion implements Assertion {
        ByteSequence normalizedAssertionValue;

        protected DefaultEqualityAssertion(final ByteSequence normalizedAssertionValue) {
            this.normalizedAssertionValue = normalizedAssertionValue;
        }

        public ConditionResult matches(final ByteSequence attributeValue) {
            return normalizedAssertionValue.equals(attributeValue) ? ConditionResult.TRUE
                    : ConditionResult.FALSE;
        }
    }

    private static final Assertion UNDEFINED_ASSERTION = new Assertion() {
        public ConditionResult matches(final ByteSequence attributeValue) {
            return ConditionResult.UNDEFINED;
        }
    };

    private static final Comparator<ByteSequence> DEFAULT_COMPARATOR =
            new Comparator<ByteSequence>() {
                public int compare(final ByteSequence o1, final ByteSequence o2) {
                    return o1.compareTo(o2);
                }
            };

    AbstractMatchingRuleImpl() {
        // Nothing to do.
    }

    public Comparator<ByteSequence> comparator(final Schema schema) {
        return DEFAULT_COMPARATOR;
    }

    public Assertion getAssertion(final Schema schema, final ByteSequence value)
            throws DecodeException {
        return new DefaultEqualityAssertion(normalizeAttributeValue(schema, value));
    }

    public Assertion getAssertion(final Schema schema, final ByteSequence subInitial,
            final List<? extends ByteSequence> subAnyElements, final ByteSequence subFinal)
            throws DecodeException {
        return UNDEFINED_ASSERTION;
    }

    public Assertion getGreaterOrEqualAssertion(final Schema schema, final ByteSequence value)
            throws DecodeException {
        return UNDEFINED_ASSERTION;
    }

    public Assertion getLessOrEqualAssertion(final Schema schema, final ByteSequence value)
            throws DecodeException {
        return UNDEFINED_ASSERTION;
    }
}
