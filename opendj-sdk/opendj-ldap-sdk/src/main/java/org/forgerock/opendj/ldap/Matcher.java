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
 *      Portions copyright 2012 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

import static com.forgerock.opendj.util.StaticUtils.DEBUG_LOG;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.forgerock.opendj.ldap.schema.MatchingRuleUse;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.UnknownSchemaElementException;

import com.forgerock.opendj.util.StaticUtils;

/**
 * An interface for determining whether entries match a {@code Filter}.
 */
public final class Matcher {
    private static final class AndMatcherImpl extends MatcherImpl {
        private final List<MatcherImpl> subMatchers;

        private AndMatcherImpl(final List<MatcherImpl> subMatchers) {
            this.subMatchers = subMatchers;
        }

        @Override
        public ConditionResult matches(final Entry entry) {
            ConditionResult r = ConditionResult.TRUE;
            for (final MatcherImpl m : subMatchers) {
                final ConditionResult p = m.matches(entry);
                if (p == ConditionResult.FALSE) {
                    return p;
                }
                r = ConditionResult.and(r, p);
            }
            return r;
        }
    }

    private static final class AssertionMatcherImpl extends MatcherImpl {
        private final Assertion assertion;

        private final AttributeDescription attributeDescription;

        private final boolean dnAttributes;

        private final MatchingRule rule;

        private final MatchingRuleUse ruleUse;

        private AssertionMatcherImpl(final AttributeDescription attributeDescription,
                final MatchingRule rule, final MatchingRuleUse ruleUse, final Assertion assertion,
                final boolean dnAttributes) {
            this.attributeDescription = attributeDescription;
            this.rule = rule;
            this.ruleUse = ruleUse;
            this.assertion = assertion;
            this.dnAttributes = dnAttributes;
        }

        @Override
        public ConditionResult matches(final Entry entry) {
            ConditionResult r = ConditionResult.FALSE;
            if (attributeDescription != null) {
                // If the matchingRule field is absent, the type field will be
                // present and the default equality matching rule is used,
                // and an equality match is performed for that type.

                // If the type field is present and the matchingRule is present,
                // the matchValue is compared against the specified attribute
                // type and its subtypes.
                final ConditionResult p =
                        Matcher.matches(entry.getAttribute(attributeDescription), rule, assertion);
                if (p == ConditionResult.TRUE) {
                    return p;
                }
                r = ConditionResult.or(r, p);
            } else {
                // If the type field is absent and the matchingRule is present,
                // the matchValue is compared against all attributes in an entry
                // that support that matchingRule.
                for (final Attribute a : entry.getAllAttributes()) {
                    if (ruleUse.hasAttribute(a.getAttributeDescription().getAttributeType())) {
                        final ConditionResult p = Matcher.matches(a, rule, assertion);
                        if (p == ConditionResult.TRUE) {
                            return p;
                        }
                        r = ConditionResult.or(r, p);
                    }
                }
            }

            if (dnAttributes) {
                // If the dnAttributes field is set to TRUE, the match is
                // additionally applied against all the AttributeValueAssertions
                // in an entry's distinguished name, and it evaluates to TRUE if
                // there is at least one attribute or subtype in the
                // distinguished name for which the filter item evaluates to
                // TRUE.
                final DN dn = entry.getName();
                for (final RDN rdn : dn) {
                    for (final AVA ava : rdn) {
                        if (ruleUse.hasAttribute(ava.getAttributeType())) {
                            final ConditionResult p =
                                    Matcher.matches(ava.getAttributeValue(), rule, assertion);
                            if (p == ConditionResult.TRUE) {
                                return p;
                            }
                            r = ConditionResult.or(r, p);
                        }
                    }
                }
            }
            return r;
        }
    }

    private static class FalseMatcherImpl extends MatcherImpl {
        @Override
        public ConditionResult matches(final Entry entry) {
            return ConditionResult.FALSE;
        }
    }

    private static abstract class MatcherImpl {
        public abstract ConditionResult matches(Entry entry);
    }

    private static final class NotMatcherImpl extends MatcherImpl {
        private final MatcherImpl subFilter;

        private NotMatcherImpl(final MatcherImpl subFilter) {
            this.subFilter = subFilter;
        }

        @Override
        public ConditionResult matches(final Entry entry) {
            return ConditionResult.not(subFilter.matches(entry));
        }
    }

    private static final class OrMatcherImpl extends MatcherImpl {
        private final List<MatcherImpl> subMatchers;

        private OrMatcherImpl(final List<MatcherImpl> subMatchers) {
            this.subMatchers = subMatchers;
        }

        @Override
        public ConditionResult matches(final Entry entry) {
            ConditionResult r = ConditionResult.FALSE;
            for (final MatcherImpl m : subMatchers) {
                final ConditionResult p = m.matches(entry);
                if (p == ConditionResult.TRUE) {
                    return p;
                }
                r = ConditionResult.or(r, p);
            }
            return r;
        }
    }

    private static final class PresentMatcherImpl extends MatcherImpl {
        private final AttributeDescription attribute;

        private PresentMatcherImpl(final AttributeDescription attribute) {
            this.attribute = attribute;
        }

        @Override
        public ConditionResult matches(final Entry entry) {
            return entry.getAttribute(attribute) == null ? ConditionResult.FALSE
                    : ConditionResult.TRUE;
        }
    }

    private static class TrueMatcherImpl extends MatcherImpl {
        @Override
        public ConditionResult matches(final Entry entry) {
            return ConditionResult.TRUE;
        }
    }

    private static class UndefinedMatcherImpl extends MatcherImpl {
        @Override
        public ConditionResult matches(final Entry entry) {
            return ConditionResult.UNDEFINED;
        }
    }

    /**
     * A visitor which is used to transform a filter into a matcher.
     */
    private static final class Visitor implements FilterVisitor<MatcherImpl, Schema> {
        public MatcherImpl visitAndFilter(final Schema schema, final List<Filter> subFilters) {
            if (subFilters.isEmpty()) {
                if (DEBUG_LOG.isLoggable(Level.FINER)) {
                    DEBUG_LOG.finer("Empty add filter component. " + "Will always return TRUE");
                }
                return TRUE;
            }

            final List<MatcherImpl> subMatchers = new ArrayList<MatcherImpl>(subFilters.size());
            for (final Filter f : subFilters) {
                subMatchers.add(f.accept(this, schema));
            }
            return new AndMatcherImpl(subMatchers);
        }

        public MatcherImpl visitApproxMatchFilter(final Schema schema,
                final String attributeDescription, final ByteString assertionValue) {
            AttributeDescription ad;
            MatchingRule rule;
            Assertion assertion;

            try {
                ad = AttributeDescription.valueOf(attributeDescription, schema);
            } catch (final LocalizedIllegalArgumentException e) {
                if (DEBUG_LOG.isLoggable(Level.WARNING)) {
                    DEBUG_LOG.warning("Attribute description " + attributeDescription
                            + " is not recognized: " + e.getMessage());
                }
                return UNDEFINED;
            }

            if ((rule = ad.getAttributeType().getApproximateMatchingRule()) == null) {
                if (DEBUG_LOG.isLoggable(Level.WARNING)) {
                    DEBUG_LOG.warning("The attribute type " + attributeDescription
                            + " does not define an approximate matching rule");
                }
                return UNDEFINED;
            }

            try {
                assertion = rule.getAssertion(assertionValue);
            } catch (final DecodeException de) {
                if (DEBUG_LOG.isLoggable(Level.WARNING)) {
                    DEBUG_LOG.warning("The assertion value " + assertionValue + " is invalid: "
                            + de.getMessage());
                }
                return UNDEFINED;
            }
            return new AssertionMatcherImpl(ad, rule, null, assertion, false);
        }

        public MatcherImpl visitEqualityMatchFilter(final Schema schema,
                final String attributeDescription, final ByteString assertionValue) {
            AttributeDescription ad;
            MatchingRule rule;
            Assertion assertion;

            try {
                ad = AttributeDescription.valueOf(attributeDescription, schema);
            } catch (final LocalizedIllegalArgumentException e) {
                if (DEBUG_LOG.isLoggable(Level.WARNING)) {
                    DEBUG_LOG.warning("Attribute description " + attributeDescription
                            + " is not recognized: " + e.getMessage());
                }
                return UNDEFINED;
            }

            if ((rule = ad.getAttributeType().getEqualityMatchingRule()) == null) {
                if (DEBUG_LOG.isLoggable(Level.WARNING)) {
                    DEBUG_LOG.warning("The attribute type " + attributeDescription
                            + " does not define an equality matching rule");
                }
                return UNDEFINED;
            }

            try {
                assertion = rule.getAssertion(assertionValue);
            } catch (final DecodeException de) {
                if (DEBUG_LOG.isLoggable(Level.WARNING)) {
                    DEBUG_LOG.warning("The assertion value " + assertionValue + " is invalid: "
                            + de.getMessage());
                }
                return UNDEFINED;
            }
            return new AssertionMatcherImpl(ad, rule, null, assertion, false);
        }

        public MatcherImpl visitExtensibleMatchFilter(final Schema schema,
                final String matchingRule, final String attributeDescription,
                final ByteString assertionValue, final boolean dnAttributes) {
            AttributeDescription ad = null;
            MatchingRule rule = null;
            MatchingRuleUse ruleUse = null;
            Assertion assertion;

            if (matchingRule != null) {
                try {
                    rule = schema.getMatchingRule(matchingRule);
                } catch (final UnknownSchemaElementException e) {
                    if (DEBUG_LOG.isLoggable(Level.WARNING)) {
                        DEBUG_LOG.warning("Matching rule " + matchingRule + " is not recognized: "
                                + e.getMessage());
                    }
                    return UNDEFINED;
                }
            }

            if (attributeDescription != null) {
                try {
                    ad = AttributeDescription.valueOf(attributeDescription, schema);
                } catch (final LocalizedIllegalArgumentException e) {
                    if (DEBUG_LOG.isLoggable(Level.WARNING)) {
                        DEBUG_LOG.warning("Attribute description " + attributeDescription
                                + " is not recognized: " + e.getMessage());
                    }
                    return UNDEFINED;
                }

                if (rule == null) {
                    if ((rule = ad.getAttributeType().getEqualityMatchingRule()) == null) {
                        if (DEBUG_LOG.isLoggable(Level.WARNING)) {
                            DEBUG_LOG.warning("The attribute type " + attributeDescription
                                    + " does not define an equality matching rule");
                        }
                        return UNDEFINED;
                    }
                } else {
                    try {
                        ruleUse = schema.getMatchingRuleUse(rule);
                    } catch (final UnknownSchemaElementException e) {
                        if (DEBUG_LOG.isLoggable(Level.WARNING)) {
                            DEBUG_LOG.warning("No matching rule use is defined for "
                                    + "matching rule " + matchingRule);
                            return UNDEFINED;
                        }
                    }
                    if (!ruleUse.hasAttribute(ad.getAttributeType())) {
                        if (DEBUG_LOG.isLoggable(Level.WARNING)) {
                            DEBUG_LOG.warning("The matching rule " + matchingRule
                                    + " is not valid for attribute type " + attributeDescription);
                        }
                        return UNDEFINED;
                    }
                }
            } else {
                try {
                    ruleUse = schema.getMatchingRuleUse(rule);
                } catch (final UnknownSchemaElementException e) {
                    if (DEBUG_LOG.isLoggable(Level.WARNING)) {
                        DEBUG_LOG.warning("No matching rule use is defined for " + "matching rule "
                                + matchingRule);
                    }
                    return UNDEFINED;
                }
            }

            try {
                assertion = rule.getAssertion(assertionValue);
            } catch (final DecodeException de) {
                if (DEBUG_LOG.isLoggable(Level.WARNING)) {
                    DEBUG_LOG.warning("The assertion value " + assertionValue + " is invalid: "
                            + de.getMessage());
                }
                return UNDEFINED;
            }
            return new AssertionMatcherImpl(ad, rule, ruleUse, assertion, dnAttributes);
        }

        public MatcherImpl visitGreaterOrEqualFilter(final Schema schema,
                final String attributeDescription, final ByteString assertionValue) {
            AttributeDescription ad;
            MatchingRule rule;
            Assertion assertion;

            try {
                ad = AttributeDescription.valueOf(attributeDescription, schema);
            } catch (final LocalizedIllegalArgumentException e) {
                if (DEBUG_LOG.isLoggable(Level.WARNING)) {
                    DEBUG_LOG.warning("Attribute description " + attributeDescription
                            + " is not recognized: " + e.getMessage());
                }
                return UNDEFINED;
            }

            if ((rule = ad.getAttributeType().getOrderingMatchingRule()) == null) {
                if (DEBUG_LOG.isLoggable(Level.WARNING)) {
                    DEBUG_LOG.warning("The attribute type " + attributeDescription
                            + " does not define an ordering matching rule");
                }
                return UNDEFINED;
            }

            try {
                assertion = rule.getGreaterOrEqualAssertion(assertionValue);
            } catch (final DecodeException de) {
                if (DEBUG_LOG.isLoggable(Level.WARNING)) {
                    DEBUG_LOG.warning("The assertion value " + assertionValue + " is invalid: "
                            + de.getMessage());
                }
                return UNDEFINED;
            }
            return new AssertionMatcherImpl(ad, rule, null, assertion, false);
        }

        public MatcherImpl visitLessOrEqualFilter(final Schema schema,
                final String attributeDescription, final ByteString assertionValue) {
            AttributeDescription ad;
            MatchingRule rule;
            Assertion assertion;

            try {
                ad = AttributeDescription.valueOf(attributeDescription, schema);
            } catch (final LocalizedIllegalArgumentException e) {
                if (DEBUG_LOG.isLoggable(Level.WARNING)) {
                    DEBUG_LOG.warning("Attribute description " + attributeDescription
                            + " is not recognized: " + e.getMessage());
                }
                return UNDEFINED;
            }

            if ((rule = ad.getAttributeType().getOrderingMatchingRule()) == null) {
                if (DEBUG_LOG.isLoggable(Level.WARNING)) {
                    DEBUG_LOG.warning("The attribute type " + attributeDescription
                            + " does not define an ordering matching rule");
                }
                return UNDEFINED;
            }

            try {
                assertion = rule.getLessOrEqualAssertion(assertionValue);
            } catch (final DecodeException de) {
                if (DEBUG_LOG.isLoggable(Level.WARNING)) {
                    DEBUG_LOG.warning("The assertion value " + assertionValue + " is invalid: "
                            + de.getMessage());
                }
                return UNDEFINED;
            }
            return new AssertionMatcherImpl(ad, rule, null, assertion, false);
        }

        public MatcherImpl visitNotFilter(final Schema schema, final Filter subFilter) {
            final MatcherImpl subMatcher = subFilter.accept(this, schema);
            return new NotMatcherImpl(subMatcher);
        }

        public MatcherImpl visitOrFilter(final Schema schema, final List<Filter> subFilters) {
            if (subFilters.isEmpty()) {
                if (DEBUG_LOG.isLoggable(Level.FINER)) {
                    DEBUG_LOG.finer("Empty or filter component. " + "Will always return FALSE");
                }
                return FALSE;
            }

            final List<MatcherImpl> subMatchers = new ArrayList<MatcherImpl>(subFilters.size());
            for (final Filter f : subFilters) {
                subMatchers.add(f.accept(this, schema));
            }
            return new OrMatcherImpl(subMatchers);
        }

        public MatcherImpl visitPresentFilter(final Schema schema, final String attributeDescription) {
            AttributeDescription ad;
            try {
                ad = AttributeDescription.valueOf(attributeDescription, schema);
            } catch (final LocalizedIllegalArgumentException e) {
                if (DEBUG_LOG.isLoggable(Level.WARNING)) {
                    DEBUG_LOG.warning("Attribute description " + attributeDescription
                            + " is not recognized: " + e.getMessage());
                }
                return UNDEFINED;
            }

            return new PresentMatcherImpl(ad);
        }

        public MatcherImpl visitSubstringsFilter(final Schema schema,
                final String attributeDescription, final ByteString initialSubstring,
                final List<ByteString> anySubstrings, final ByteString finalSubstring) {
            AttributeDescription ad;
            MatchingRule rule;
            Assertion assertion;

            try {
                ad = AttributeDescription.valueOf(attributeDescription, schema);
            } catch (final LocalizedIllegalArgumentException e) {
                if (DEBUG_LOG.isLoggable(Level.WARNING)) {
                    DEBUG_LOG.warning("Attribute description " + attributeDescription
                            + " is not recognized: " + e.getMessage());
                }
                return UNDEFINED;
            }

            if ((rule = ad.getAttributeType().getSubstringMatchingRule()) == null) {
                if (DEBUG_LOG.isLoggable(Level.WARNING)) {
                    DEBUG_LOG.warning("The attribute type " + attributeDescription
                            + " does not define an substring matching rule");
                }
                return UNDEFINED;
            }

            try {
                assertion = rule.getAssertion(initialSubstring, anySubstrings, finalSubstring);
            } catch (final DecodeException de) {
                if (DEBUG_LOG.isLoggable(Level.WARNING)) {
                    DEBUG_LOG.warning("The substring assertion values contain an invalid value: "
                            + de.getMessage());
                }
                return UNDEFINED;
            }
            return new AssertionMatcherImpl(ad, rule, null, assertion, false);
        }

        public MatcherImpl visitUnrecognizedFilter(final Schema schema, final byte filterTag,
                final ByteString filterBytes) {
            if (DEBUG_LOG.isLoggable(Level.WARNING)) {
                DEBUG_LOG.warning("The type of filtering requested with tag "
                        + StaticUtils.byteToHex(filterTag) + " is not implemented");
            }
            return UNDEFINED;
        }
    }

    private static final MatcherImpl FALSE = new FalseMatcherImpl();

    private static final MatcherImpl TRUE = new TrueMatcherImpl();

    private static final MatcherImpl UNDEFINED = new UndefinedMatcherImpl();

    private static final FilterVisitor<MatcherImpl, Schema> VISITOR = new Visitor();

    private static ConditionResult matches(final Attribute a, final MatchingRule rule,
            final Assertion assertion) {

        ConditionResult r = ConditionResult.FALSE;
        if (a != null) {
            for (final ByteString v : a) {
                switch (matches(v, rule, assertion)) {
                case TRUE:
                    return ConditionResult.TRUE;
                case FALSE:
                    continue;
                case UNDEFINED:
                    r = ConditionResult.UNDEFINED;
                }
            }
        }
        return r;
    }

    private static ConditionResult matches(final ByteString v, final MatchingRule rule,
            final Assertion assertion) {
        try {
            final ByteString normalizedValue = rule.normalizeAttributeValue(v);
            return assertion.matches(normalizedValue);
        } catch (final DecodeException de) {
            if (DEBUG_LOG.isLoggable(Level.WARNING)) {
                DEBUG_LOG.warning("The attribute value " + v.toString() + " is "
                        + "invalid for matching rule " + rule.getNameOrOID()
                        + ". Possible schema error? : " + de.getMessage());
            }
            return ConditionResult.UNDEFINED;
        }
    }

    private final MatcherImpl impl;

    Matcher(final Filter filter, final Schema schema) {
        this.impl = filter.accept(VISITOR, schema);
    }

    /**
     * Indicates whether this filter {@code Matcher} matches the provided
     * {@code Entry}.
     *
     * @param entry
     *            The entry to be matched.
     * @return The result of matching the provided {@code Entry} against this
     *         filter {@code Matcher}.
     */
    public ConditionResult matches(final Entry entry) {
        return impl.matches(entry);
    }
}
