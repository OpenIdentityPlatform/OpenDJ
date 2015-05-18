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
 *      Copyright 2014-2015 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.schema;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.spi.IndexQueryFactory;
import org.forgerock.opendj.ldap.spi.Indexer;
import org.forgerock.opendj.ldap.spi.IndexingOptions;

/**
 * Implementations of collation matching rules. Each matching rule is created
 * from a locale (eg, "en-US" or "en-GB").
 * <p>
 * The PRIMARY strength is used for collation, which means that only primary
 * differences are considered significant for comparison. It usually means that
 * spaces, case, and accent are not significant, although it is language dependant.
 * <p>
 * For a given locale, two indexes are used: a shared one (for equality and
 * ordering rules) and a substring one (for substring rule).
 */
final class CollationMatchingRulesImpl {

    private static final String INDEX_ID_SHARED = "shared";
    private static final String INDEX_ID_SUBSTRING = "substring";

    private CollationMatchingRulesImpl() {
        // This class is not instanciable
    }

    /**
     * Creates a collation equality matching Rule.
     *
     * @param locale
     *            the locale to use for this rule
     * @return the matching rule implementation
     */
    static MatchingRuleImpl collationEqualityMatchingRule(Locale locale) {
        return new CollationEqualityMatchingRuleImpl(locale);
    }

    /**
     * Creates a collation substring matching Rule.
     *
     * @param locale
     *            the locale to use for this rule
     * @return the matching rule implementation
     */
    static MatchingRuleImpl collationSubstringMatchingRule(Locale locale) {
        return new CollationSubstringMatchingRuleImpl(locale);
    }

    /**
     * Creates a collation less than matching Rule.
     *
     * @param locale
     *            the locale to use for this rule
     * @return the matching rule implementation
     */
    static MatchingRuleImpl collationLessThanMatchingRule(Locale locale) {
        return new CollationLessThanMatchingRuleImpl(locale);
    }

    /**
     * Creates a collation less than or equal matching Rule.
     *
     * @param locale
     *            the locale to use for this rule
     * @return the matching rule implementation
     */
    static MatchingRuleImpl collationLessThanOrEqualMatchingRule(Locale locale) {
        return new CollationLessThanOrEqualToMatchingRuleImpl(locale);
    }

    /**
     * Creates a collation greater than matching Rule.
     *
     * @param locale
     *            the locale to use for this rule
     * @return the matching rule implementation
     */
    static MatchingRuleImpl collationGreaterThanMatchingRule(Locale locale) {
        return new CollationGreaterThanMatchingRuleImpl(locale);
    }

    /**
     * Creates a collation greater than or equal matching Rule.
     *
     * @param locale
     *            the locale to use for this rule
     * @return the matching rule implementation
     */
    static MatchingRuleImpl collationGreaterThanOrEqualToMatchingRule(Locale locale) {
        return new CollationGreaterThanOrEqualToMatchingRuleImpl(locale);
    }

    /**
     * Defines the base for collation matching rules.
     */
    private static abstract class AbstractCollationMatchingRuleImpl extends AbstractMatchingRuleImpl {
        private final Locale locale;
        final Collator collator;
        final String indexName;
        final Indexer indexer;

        /**
         * Creates the collation matching rule with the provided locale.
         *
         * @param locale
         *            Locale associated with this rule.
         */
        AbstractCollationMatchingRuleImpl(Locale locale) {
            this.locale = locale;
            this.collator = createCollator(locale);
            this.indexName = getPrefixIndexName() + "." + INDEX_ID_SHARED;
            this.indexer = new DefaultIndexer(indexName);
        }

        private Collator createCollator(Locale locale) {
            Collator collator = Collator.getInstance(locale);
            collator.setStrength(Collator.PRIMARY);
            collator.setDecomposition(Collator.FULL_DECOMPOSITION);
            return collator;
        }

        /**
         * Returns the prefix name of the index database for this matching rule. An
         * index name for this rule will be based upon the Locale. This will
         * ensure that multiple collation matching rules corresponding to the
         * same Locale can share the same index database.
         *
         * @return The prefix name of the index for this matching rule.
         */
        String getPrefixIndexName() {
            String language = locale.getLanguage();
            String country = locale.getCountry();
            String variant = locale.getVariant();

            StringBuilder builder = new StringBuilder(language);
            if (country != null && country.length() > 0) {
                builder.append("_");
                builder.append(country);
            }
            if (variant != null && variant.length() > 0) {
                builder.append("_");
                builder.append(variant);
            }
            return builder.toString();
        }

        /** {@inheritDoc} */
        @Override
        public Collection<? extends Indexer> createIndexers(IndexingOptions options) {
            return Collections.singletonList(indexer);
        }

        /** {@inheritDoc} */
        @Override
        public ByteString normalizeAttributeValue(final Schema schema, final ByteSequence value)
                throws DecodeException {
            try {
                final byte[] byteArray = collator.getCollationKey(value.toString()).toByteArray();
                // Last 4 bytes are 0s when collator strength is set to PRIMARY, so skip them
                return ByteString.wrap(byteArray).subSequence(0, byteArray.length - 4);
            } catch (final LocalizedIllegalArgumentException e) {
                throw DecodeException.error(e.getMessageObject());
            }
        }
    }

    /**
     * Defines the collation equality matching rule.
     */
    private static final class CollationEqualityMatchingRuleImpl extends AbstractCollationMatchingRuleImpl {

        /**
         * Creates the matching rule with the provided locale.
         *
         * @param locale
         *          Locale associated with this rule.
         */
        CollationEqualityMatchingRuleImpl(Locale locale) {
            super(locale);
        }

        @Override
        public Assertion getAssertion(final Schema schema, final ByteSequence assertionValue)
                throws DecodeException {
            return named(indexName, normalizeAttributeValue(schema, assertionValue));
        }

    }

    /**
     * Defines the collation substring matching rule.
     */
    private static final class CollationSubstringMatchingRuleImpl extends AbstractCollationMatchingRuleImpl {

        private final AbstractSubstringMatchingRuleImpl substringMatchingRule;

        /**
         * Creates the matching rule with the provided locale.
         *
         * @param locale
         *          Locale associated with this rule.
         */
        CollationSubstringMatchingRuleImpl(Locale locale) {
            super(locale);
            substringMatchingRule = new AbstractSubstringMatchingRuleImpl(
                    getPrefixIndexName() + "." + INDEX_ID_SUBSTRING, indexName) {
                @Override
                public ByteString normalizeAttributeValue(Schema schema, ByteSequence value)
                        throws DecodeException {
                    return CollationSubstringMatchingRuleImpl.this.normalizeAttributeValue(schema, value);
                }
            };
        }

        @Override
        public Assertion getAssertion(final Schema schema, final ByteSequence assertionValue) throws DecodeException {
            return substringMatchingRule.getAssertion(schema, assertionValue);
        }

        /** {@inheritDoc} */
        @Override
        public Assertion getSubstringAssertion(Schema schema, ByteSequence subInitial,
                List<? extends ByteSequence> subAnyElements, ByteSequence subFinal) throws DecodeException {
            return substringMatchingRule.getSubstringAssertion(schema, subInitial, subAnyElements, subFinal);
        }

        /** {@inheritDoc} */
        @Override
        public final Collection<? extends Indexer> createIndexers(IndexingOptions options) {
            final Collection<Indexer> indexers = new ArrayList<>(substringMatchingRule.createIndexers(options));
            indexers.add(indexer);
            return indexers;
        }
    }

    /**
     * Defines the collation ordering matching rule.
     */
    private static abstract class CollationOrderingMatchingRuleImpl extends AbstractCollationMatchingRuleImpl {

        final AbstractOrderingMatchingRuleImpl orderingMatchingRule;

        /**
         * Creates the matching rule with the provided locale.
         *
         * @param locale
         *          Locale associated with this rule.
         */
        CollationOrderingMatchingRuleImpl(Locale locale) {
            super(locale);

            orderingMatchingRule = new AbstractOrderingMatchingRuleImpl(indexName) {
                @Override
                public ByteString normalizeAttributeValue(Schema schema, ByteSequence value) throws DecodeException {
                    return CollationOrderingMatchingRuleImpl.this.normalizeAttributeValue(schema, value);
                }
            };
        }
    }

    /**
     * Defines the collation less than matching rule.
     */
    private static final class CollationLessThanMatchingRuleImpl extends CollationOrderingMatchingRuleImpl {

        CollationLessThanMatchingRuleImpl(Locale locale) {
            super(locale);
        }

        /** {@inheritDoc} */
        @Override
        public Assertion getAssertion(Schema schema, ByteSequence assertionValue) throws DecodeException {
            return orderingMatchingRule.getAssertion(schema, assertionValue);
        }
    }

    /**
     * Defines the collation less than or equal matching rule.
     */
    private static final class CollationLessThanOrEqualToMatchingRuleImpl extends CollationOrderingMatchingRuleImpl {

        CollationLessThanOrEqualToMatchingRuleImpl(Locale locale) {
            super(locale);
        }

        /** {@inheritDoc} */
        @Override
        public Assertion getAssertion(Schema schema, ByteSequence assertionValue) throws DecodeException {
            return orderingMatchingRule.getLessOrEqualAssertion(schema, assertionValue);
        }
    }

    /**
     * Defines the collation greater than matching rule.
     */
    private static final class CollationGreaterThanMatchingRuleImpl extends CollationOrderingMatchingRuleImpl {

        CollationGreaterThanMatchingRuleImpl(Locale locale) {
            super(locale);
        }

        /** {@inheritDoc} */
        @Override
        public Assertion getAssertion(Schema schema, ByteSequence assertionValue)
                throws DecodeException {
            final ByteString normAssertion = normalizeAttributeValue(schema, assertionValue);
            return new Assertion() {
                @Override
                public ConditionResult matches(final ByteSequence attributeValue) {
                    return ConditionResult.valueOf(attributeValue.compareTo(normAssertion) > 0);
                }

                @Override
                public <T> T createIndexQuery(IndexQueryFactory<T> factory) throws DecodeException {
                    return factory.createRangeMatchQuery(indexName, normAssertion,
                            ByteString.empty(), false, false);
                }
            };
        }
    }

    /**
     * Defines the collation greater than or equal matching rule.
     */
    private static final class CollationGreaterThanOrEqualToMatchingRuleImpl
        extends CollationOrderingMatchingRuleImpl {

        CollationGreaterThanOrEqualToMatchingRuleImpl(Locale locale) {
            super(locale);
        }

        /** {@inheritDoc} */
        @Override
        public Assertion getAssertion(Schema schema, ByteSequence assertionValue) throws DecodeException {
            return orderingMatchingRule.getGreaterOrEqualAssertion(schema, assertionValue);
        }
    }
}
