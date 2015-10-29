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
 *      Copyright 2009-2011 Sun Microsystems, Inc.
 *      Portions copyright 2012-2015 ForgeRock AS.
 */
package org.forgerock.opendj.ldap;

import static com.forgerock.opendj.ldap.CoreMessages.*;
import static com.forgerock.opendj.util.StaticUtils.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.util.Reject;

/**
 * A search filter as defined in RFC 4511. In addition this class also provides
 * support for the absolute true and absolute false filters as defined in RFC
 * 4526.
 * <p>
 * This class provides many factory methods for creating common types of filter.
 * Applications interact with a filter using {@link FilterVisitor} which is
 * applied to a filter using the {@link #accept(FilterVisitor, Object)} method.
 * <p>
 * The RFC 4515 string representation of a filter can be generated using the
 * {@link #toString} methods and parsed using the {@link #valueOf(String)}
 * factory method.
 * <p>
 * Filters can be constructed using the various factory methods. For example,
 * the following code illustrates how to create a filter having the string
 * representation "{@code (&(cn=bjensen)(age>=21))}":
 *
 * <pre>
 * import static org.forgerock.opendj.Filter.*;
 *
 * Filter filter = and(equality("cn", "bjensen"), greaterOrEqual("age", 21));
 *
 * // Alternatively use a filter template:
 * Filter filter = Filter.format("(&(cn=%s)(age>=%s))", "bjensen", 21);
 * </pre>
 *
 * @see <a href="http://tools.ietf.org/html/rfc4511">RFC 4511 - Lightweight
 *      Directory Access Protocol (LDAP): The Protocol </a>
 * @see <a href="http://tools.ietf.org/html/rfc4515">RFC 4515 - String
 *      Representation of Search Filters </a>
 * @see <a href="http://tools.ietf.org/html/rfc4526">RFC 4526 - Absolute True
 *      and False Filters </a>
 */
public final class Filter {

    /** The asterisk character. */
    private static final byte ASTERISK = 0x2A;

    /** The backslash character. */
    private static final byte BACKSLASH = 0x5C;

    private static final class AndImpl extends Impl {
        private final List<Filter> subFilters;

        public AndImpl(final List<Filter> subFilters) {
            this.subFilters = subFilters;
        }

        @Override
        public <R, P> R accept(final FilterVisitor<R, P> v, final P p) {
            return v.visitAndFilter(p, subFilters);
        }

    }

    private static final class ApproxMatchImpl extends Impl {

        private final ByteString assertionValue;

        private final String attributeDescription;

        public ApproxMatchImpl(final String attributeDescription, final ByteString assertionValue) {
            this.attributeDescription = attributeDescription;
            this.assertionValue = assertionValue;
        }

        @Override
        public <R, P> R accept(final FilterVisitor<R, P> v, final P p) {
            return v.visitApproxMatchFilter(p, attributeDescription, assertionValue);
        }

    }

    private static final class EqualityMatchImpl extends Impl {

        private final ByteString assertionValue;

        private final String attributeDescription;

        public EqualityMatchImpl(final String attributeDescription, final ByteString assertionValue) {
            this.attributeDescription = attributeDescription;
            this.assertionValue = assertionValue;
        }

        @Override
        public <R, P> R accept(final FilterVisitor<R, P> v, final P p) {
            return v.visitEqualityMatchFilter(p, attributeDescription, assertionValue);
        }

    }

    private static final class ExtensibleMatchImpl extends Impl {
        private final String attributeDescription;

        private final boolean dnAttributes;

        private final String matchingRule;

        private final ByteString matchValue;

        public ExtensibleMatchImpl(final String matchingRule, final String attributeDescription,
                final ByteString matchValue, final boolean dnAttributes) {
            this.matchingRule = matchingRule;
            this.attributeDescription = attributeDescription;
            this.matchValue = matchValue;
            this.dnAttributes = dnAttributes;
        }

        @Override
        public <R, P> R accept(final FilterVisitor<R, P> v, final P p) {
            return v.visitExtensibleMatchFilter(p, matchingRule, attributeDescription, matchValue,
                    dnAttributes);
        }

    }

    private static final class GreaterOrEqualImpl extends Impl {

        private final ByteString assertionValue;

        private final String attributeDescription;

        public GreaterOrEqualImpl(final String attributeDescription, final ByteString assertionValue) {
            this.attributeDescription = attributeDescription;
            this.assertionValue = assertionValue;
        }

        @Override
        public <R, P> R accept(final FilterVisitor<R, P> v, final P p) {
            return v.visitGreaterOrEqualFilter(p, attributeDescription, assertionValue);
        }

    }

    private static abstract class Impl {
        protected Impl() {
            // Nothing to do.
        }

        public abstract <R, P> R accept(FilterVisitor<R, P> v, P p);
    }

    private static final class LessOrEqualImpl extends Impl {

        private final ByteString assertionValue;

        private final String attributeDescription;

        public LessOrEqualImpl(final String attributeDescription, final ByteString assertionValue) {
            this.attributeDescription = attributeDescription;
            this.assertionValue = assertionValue;
        }

        @Override
        public <R, P> R accept(final FilterVisitor<R, P> v, final P p) {
            return v.visitLessOrEqualFilter(p, attributeDescription, assertionValue);
        }

    }

    private static final class NotImpl extends Impl {
        private final Filter subFilter;

        public NotImpl(final Filter subFilter) {
            this.subFilter = subFilter;
        }

        @Override
        public <R, P> R accept(final FilterVisitor<R, P> v, final P p) {
            return v.visitNotFilter(p, subFilter);
        }

    }

    private static final class OrImpl extends Impl {
        private final List<Filter> subFilters;

        public OrImpl(final List<Filter> subFilters) {
            this.subFilters = subFilters;
        }

        @Override
        public <R, P> R accept(final FilterVisitor<R, P> v, final P p) {
            return v.visitOrFilter(p, subFilters);
        }

    }

    private static final class PresentImpl extends Impl {

        private final String attributeDescription;

        public PresentImpl(final String attributeDescription) {
            this.attributeDescription = attributeDescription;
        }

        @Override
        public <R, P> R accept(final FilterVisitor<R, P> v, final P p) {
            return v.visitPresentFilter(p, attributeDescription);
        }

    }

    private static final class SubstringsImpl extends Impl {

        private final List<ByteString> anyStrings;

        private final String attributeDescription;

        private final ByteString finalString;

        private final ByteString initialString;

        public SubstringsImpl(final String attributeDescription, final ByteString initialString,
                final List<ByteString> anyStrings, final ByteString finalString) {
            this.attributeDescription = attributeDescription;
            this.initialString = initialString;
            this.anyStrings = anyStrings;
            this.finalString = finalString;

        }

        @Override
        public <R, P> R accept(final FilterVisitor<R, P> v, final P p) {
            return v.visitSubstringsFilter(p, attributeDescription, initialString, anyStrings,
                    finalString);
        }

    }

    private static final class UnrecognizedImpl extends Impl {

        private final ByteString filterBytes;

        private final byte filterTag;

        public UnrecognizedImpl(final byte filterTag, final ByteString filterBytes) {
            this.filterTag = filterTag;
            this.filterBytes = filterBytes;
        }

        @Override
        public <R, P> R accept(final FilterVisitor<R, P> v, final P p) {
            return v.visitUnrecognizedFilter(p, filterTag, filterBytes);
        }

    }

    /** RFC 4526 - FALSE filter. */
    private static final Filter FALSE = new Filter(new OrImpl(Collections.<Filter> emptyList()));

    /** Heavily used (objectClass=*) filter. */
    private static final Filter OBJECT_CLASS_PRESENT = new Filter(new PresentImpl("objectClass"));

    private static final FilterVisitor<StringBuilder, StringBuilder> TO_STRING_VISITOR =
            new FilterVisitor<StringBuilder, StringBuilder>() {

                @Override
                public StringBuilder visitAndFilter(final StringBuilder builder,
                        final List<Filter> subFilters) {
                    builder.append("(&");
                    for (final Filter subFilter : subFilters) {
                        subFilter.accept(this, builder);
                    }
                    builder.append(')');
                    return builder;
                }

                @Override
                public StringBuilder visitApproxMatchFilter(final StringBuilder builder,
                        final String attributeDescription, final ByteString assertionValue) {
                    builder.append('(');
                    builder.append(attributeDescription);
                    builder.append("~=");
                    valueToFilterString(builder, assertionValue);
                    builder.append(')');
                    return builder;
                }

                @Override
                public StringBuilder visitEqualityMatchFilter(final StringBuilder builder,
                        final String attributeDescription, final ByteString assertionValue) {
                    builder.append('(');
                    builder.append(attributeDescription);
                    builder.append("=");
                    valueToFilterString(builder, assertionValue);
                    builder.append(')');
                    return builder;
                }

                @Override
                public StringBuilder visitExtensibleMatchFilter(final StringBuilder builder,
                        final String matchingRule, final String attributeDescription,
                        final ByteString assertionValue, final boolean dnAttributes) {
                    builder.append('(');

                    if (attributeDescription != null) {
                        builder.append(attributeDescription);
                    }

                    if (dnAttributes) {
                        builder.append(":dn");
                    }

                    if (matchingRule != null) {
                        builder.append(':');
                        builder.append(matchingRule);
                    }

                    builder.append(":=");
                    valueToFilterString(builder, assertionValue);
                    builder.append(')');
                    return builder;
                }

                @Override
                public StringBuilder visitGreaterOrEqualFilter(final StringBuilder builder,
                        final String attributeDescription, final ByteString assertionValue) {
                    builder.append('(');
                    builder.append(attributeDescription);
                    builder.append(">=");
                    valueToFilterString(builder, assertionValue);
                    builder.append(')');
                    return builder;
                }

                @Override
                public StringBuilder visitLessOrEqualFilter(final StringBuilder builder,
                        final String attributeDescription, final ByteString assertionValue) {
                    builder.append('(');
                    builder.append(attributeDescription);
                    builder.append("<=");
                    valueToFilterString(builder, assertionValue);
                    builder.append(')');
                    return builder;
                }

                @Override
                public StringBuilder visitNotFilter(final StringBuilder builder,
                        final Filter subFilter) {
                    builder.append("(!");
                    subFilter.accept(this, builder);
                    builder.append(')');
                    return builder;
                }

                @Override
                public StringBuilder visitOrFilter(final StringBuilder builder,
                        final List<Filter> subFilters) {
                    builder.append("(|");
                    for (final Filter subFilter : subFilters) {
                        subFilter.accept(this, builder);
                    }
                    builder.append(')');
                    return builder;
                }

                @Override
                public StringBuilder visitPresentFilter(final StringBuilder builder,
                        final String attributeDescription) {
                    builder.append('(');
                    builder.append(attributeDescription);
                    builder.append("=*)");
                    return builder;
                }

                @Override
                public StringBuilder visitSubstringsFilter(final StringBuilder builder,
                        final String attributeDescription, final ByteString initialSubstring,
                        final List<ByteString> anySubstrings, final ByteString finalSubstring) {
                    builder.append('(');
                    builder.append(attributeDescription);
                    builder.append("=");
                    if (initialSubstring != null) {
                        valueToFilterString(builder, initialSubstring);
                    }
                    for (final ByteString anySubstring : anySubstrings) {
                        builder.append('*');
                        valueToFilterString(builder, anySubstring);
                    }
                    builder.append('*');
                    if (finalSubstring != null) {
                        valueToFilterString(builder, finalSubstring);
                    }
                    builder.append(')');
                    return builder;
                }

                @Override
                public StringBuilder visitUnrecognizedFilter(final StringBuilder builder,
                        final byte filterTag, final ByteString filterBytes) {
                    // Fake up a representation.
                    builder.append('(');
                    builder.append(byteToHex(filterTag));
                    builder.append(':');
                    builder.append(filterBytes.toHexString());
                    builder.append(')');
                    return builder;
                }
            };

    /** RFC 4526 - TRUE filter. */
    private static final Filter TRUE = new Filter(new AndImpl(Collections.<Filter> emptyList()));

    /**
     * Returns the {@code absolute false} filter as defined in RFC 4526 which is
     * comprised of an {@code or} filter containing zero components.
     *
     * @return The absolute false filter.
     * @see <a href="http://tools.ietf.org/html/rfc4526">RFC 4526</a>
     */
    public static Filter alwaysFalse() {
        return FALSE;
    }

    /**
     * Returns the {@code absolute true} filter as defined in RFC 4526 which is
     * comprised of an {@code and} filter containing zero components.
     *
     * @return The absolute true filter.
     * @see <a href="http://tools.ietf.org/html/rfc4526">RFC 4526</a>
     */
    public static Filter alwaysTrue() {
        return TRUE;
    }

    /**
     * Creates a new {@code and} filter using the provided list of sub-filters.
     * <p>
     * Creating a new {@code and} filter with a {@code null} or empty list of
     * sub-filters is equivalent to calling {@link #alwaysTrue()}.
     *
     * @param subFilters
     *            The list of sub-filters, may be empty or {@code null}.
     * @return The newly created {@code and} filter.
     */
    public static Filter and(final Collection<Filter> subFilters) {
        if (subFilters == null || subFilters.isEmpty()) {
            // RFC 4526 - TRUE filter.
            return alwaysTrue();
        } else if (subFilters.size() == 1) {
            final Filter subFilter = subFilters.iterator().next();
            Reject.ifNull(subFilter);
            return new Filter(new AndImpl(Collections.singletonList(subFilter)));
        } else {
            final List<Filter> subFiltersList = new ArrayList<>(subFilters.size());
            for (final Filter subFilter : subFilters) {
                Reject.ifNull(subFilter);
                subFiltersList.add(subFilter);
            }
            return new Filter(new AndImpl(Collections.unmodifiableList(subFiltersList)));
        }
    }

    /**
     * Creates a new {@code and} filter using the provided list of sub-filters.
     * <p>
     * Creating a new {@code and} filter with a {@code null} or empty list of
     * sub-filters is equivalent to calling {@link #alwaysTrue()}.
     *
     * @param subFilters
     *            The list of sub-filters, may be empty or {@code null}.
     * @return The newly created {@code and} filter.
     */
    public static Filter and(final Filter... subFilters) {
        if (subFilters == null || subFilters.length == 0) {
            // RFC 4526 - TRUE filter.
            return alwaysTrue();
        } else if (subFilters.length == 1) {
            Reject.ifNull(subFilters[0]);
            return new Filter(new AndImpl(Collections.singletonList(subFilters[0])));
        } else {
            final List<Filter> subFiltersList = new ArrayList<>(subFilters.length);
            for (final Filter subFilter : subFilters) {
                Reject.ifNull(subFilter);
                subFiltersList.add(subFilter);
            }
            return new Filter(new AndImpl(Collections.unmodifiableList(subFiltersList)));
        }
    }

    /**
     * Creates a new {@code approximate match} filter using the provided
     * attribute description and assertion value.
     * <p>
     * If {@code assertionValue} is not an instance of {@code ByteString} then
     * it will be converted using the {@link ByteString#valueOfObject(Object)} method.
     *
     * @param attributeDescription
     *            The attribute description.
     * @param assertionValue
     *            The assertion value.
     * @return The newly created {@code approximate match} filter.
     */
    public static Filter approx(final String attributeDescription, final Object assertionValue) {
        Reject.ifNull(attributeDescription, assertionValue);
        return new Filter(new ApproxMatchImpl(attributeDescription, ByteString
                .valueOfObject(assertionValue)));
    }

    /**
     * Creates a new {@code equality match} filter using the provided attribute
     * description and assertion value.
     * <p>
     * If {@code assertionValue} is not an instance of {@code ByteString} then
     * it will be converted using the {@link ByteString#valueOfObject(Object)} method.
     *
     * @param attributeDescription
     *            The attribute description.
     * @param assertionValue
     *            The assertion value.
     * @return The newly created {@code equality match} filter.
     */
    public static Filter equality(final String attributeDescription, final Object assertionValue) {
        Reject.ifNull(attributeDescription, assertionValue);
        return new Filter(new EqualityMatchImpl(attributeDescription, ByteString
                .valueOfObject(assertionValue)));
    }

    /**
     * Returns the LDAP string representation of the provided filter assertion
     * value in a form suitable for substitution directly into a filter string.
     * This method may be useful in cases where a filter is to be constructed
     * from a filter template using {@code String#format(String, Object...)}.
     * The following example illustrates two approaches to constructing an
     * equality filter:
     *
     * <pre>
     * // This may contain user input.
     * String assertionValue = ...;
     *
     * // Using the equality filter constructor:
     * Filter filter = Filter.equality("cn", assertionValue);
     *
     * // Using a String template:
     * String filterTemplate = "(cn=%s)";
     * String filterString = String.format(filterTemplate,
     *                                     Filter.escapeAssertionValue(assertionValue));
     * Filter filter = Filter.valueOf(filterString);
     * </pre>
     *
     * If {@code assertionValue} is not an instance of {@code ByteString} then
     * it will be converted using the {@link ByteString#valueOfObject(Object)} method.
     * <p>
     * <b>Note:</b> assertion values do not and should not be escaped before
     * passing them to constructors like {@link #equality(String, Object)}.
     * Escaping is only required when creating filter strings.
     *
     * @param assertionValue
     *            The assertion value.
     * @return The LDAP string representation of the provided filter assertion
     *         value in a form suitable for substitution directly into a filter
     *         string.
     * @see #format(String, Object...)
     */
    public static String escapeAssertionValue(final Object assertionValue) {
        Reject.ifNull(assertionValue);
        final ByteString bytes = ByteString.valueOfObject(assertionValue);
        final StringBuilder builder = new StringBuilder(bytes.length());
        valueToFilterString(builder, bytes);
        return builder.toString();
    }

    /**
     * Creates a new {@code extensible match} filter.
     * <p>
     * If {@code assertionValue} is not an instance of {@code ByteString} then
     * it will be converted using the {@link ByteString#valueOfObject(Object)} method.
     *
     * @param matchingRule
     *            The matching rule name, may be {@code null} if
     *            {@code attributeDescription} is specified.
     * @param attributeDescription
     *            The attribute description, may be {@code null} if
     *            {@code matchingRule} is specified.
     * @param assertionValue
     *            The assertion value.
     * @param dnAttributes
     *            Indicates whether DN matching should be performed.
     * @return The newly created {@code extensible match} filter.
     */
    public static Filter extensible(final String matchingRule, final String attributeDescription,
            final Object assertionValue, final boolean dnAttributes) {
        Reject.ifFalse(matchingRule != null || attributeDescription != null,
                "matchingRule and/or attributeDescription must not be null");
        Reject.ifNull(assertionValue);
        return new Filter(new ExtensibleMatchImpl(matchingRule, attributeDescription, ByteString
                .valueOfObject(assertionValue), dnAttributes));
    }

    /**
     * Creates a new {@code greater or equal} filter using the provided
     * attribute description and assertion value.
     * <p>
     * If {@code assertionValue} is not an instance of {@code ByteString} then
     * it will be converted using the {@link ByteString#valueOfObject(Object)} method.
     *
     * @param attributeDescription
     *            The attribute description.
     * @param assertionValue
     *            The assertion value.
     * @return The newly created {@code greater or equal} filter.
     */
    public static Filter greaterOrEqual(final String attributeDescription,
            final Object assertionValue) {
        Reject.ifNull(attributeDescription, assertionValue);
        return new Filter(new GreaterOrEqualImpl(attributeDescription, ByteString
                .valueOfObject(assertionValue)));
    }

    /**
     * Creates a new {@code greater than} filter using the provided attribute
     * description and assertion value.
     * <p>
     * If {@code assertionValue} is not an instance of {@code ByteString} then
     * it will be converted using the {@link ByteString#valueOfObject(Object)} method.
     * <p>
     * <b>NOTE:</b> since LDAP does not support {@code greater than}
     * comparisons, this method returns a filter of the form
     * {@code (&(type>=value)(!(type=value)))}. An alternative is to return a
     * filter of the form {@code (!(type<=value))} , however the outer
     * {@code not} filter will often prevent directory servers from optimizing
     * the search using indexes.
     *
     * @param attributeDescription
     *            The attribute description.
     * @param assertionValue
     *            The assertion value.
     * @return The newly created {@code greater than} filter.
     */
    public static Filter greaterThan(final String attributeDescription, final Object assertionValue) {
        return and(greaterOrEqual(attributeDescription, assertionValue), not(equality(
                attributeDescription, assertionValue)));
    }

    /**
     * Creates a new {@code less or equal} filter using the provided attribute
     * description and assertion value.
     * <p>
     * If {@code assertionValue} is not an instance of {@code ByteString} then
     * it will be converted using the {@link ByteString#valueOfObject(Object)} method.
     *
     * @param attributeDescription
     *            The attribute description.
     * @param assertionValue
     *            The assertion value.
     * @return The newly created {@code less or equal} filter.
     */
    public static Filter lessOrEqual(final String attributeDescription, final Object assertionValue) {
        Reject.ifNull(attributeDescription, assertionValue);
        return new Filter(new LessOrEqualImpl(attributeDescription, ByteString
                .valueOfObject(assertionValue)));
    }

    /**
     * Creates a new {@code less than} filter using the provided attribute
     * description and assertion value.
     * <p>
     * If {@code assertionValue} is not an instance of {@code ByteString} then
     * it will be converted using the {@link ByteString#valueOfObject(Object)} method.
     * <p>
     * <b>NOTE:</b> since LDAP does not support {@code less than} comparisons,
     * this method returns a filter of the form
     * {@code (&(type<=value)(!(type=value)))}. An alternative is to return a
     * filter of the form {@code (!(type>=value))} , however the outer
     * {@code not} filter will often prevent directory servers from optimizing
     * the search using indexes.
     *
     * @param attributeDescription
     *            The attribute description.
     * @param assertionValue
     *            The assertion value.
     * @return The newly created {@code less than} filter.
     */
    public static Filter lessThan(final String attributeDescription, final Object assertionValue) {
        return and(lessOrEqual(attributeDescription, assertionValue), not(equality(
                attributeDescription, assertionValue)));
    }

    /**
     * Creates a new {@code not} filter using the provided sub-filter.
     *
     * @param subFilter
     *            The sub-filter.
     * @return The newly created {@code not} filter.
     */
    public static Filter not(final Filter subFilter) {
        Reject.ifNull(subFilter);
        return new Filter(new NotImpl(subFilter));
    }

    /**
     * Returns the {@code objectClass} presence filter {@code (objectClass=*)}.
     * <p>
     * A call to this method is equivalent to but more efficient than the
     * following code:
     *
     * <pre>
     * Filter.present(&quot;objectClass&quot;);
     * </pre>
     *
     * @return The {@code objectClass} presence filter {@code (objectClass=*)}.
     */
    public static Filter objectClassPresent() {
        return OBJECT_CLASS_PRESENT;
    }

    /**
     * Creates a new {@code or} filter using the provided list of sub-filters.
     * <p>
     * Creating a new {@code or} filter with a {@code null} or empty list of
     * sub-filters is equivalent to calling {@link #alwaysFalse()}.
     *
     * @param subFilters
     *            The list of sub-filters, may be empty or {@code null}.
     * @return The newly created {@code or} filter.
     */
    public static Filter or(final Collection<Filter> subFilters) {
        if (subFilters == null || subFilters.isEmpty()) {
            // RFC 4526 - FALSE filter.
            return alwaysFalse();
        } else if (subFilters.size() == 1) {
            final Filter subFilter = subFilters.iterator().next();
            Reject.ifNull(subFilter);
            return new Filter(new OrImpl(Collections.singletonList(subFilter)));
        } else {
            final List<Filter> subFiltersList = new ArrayList<>(subFilters.size());
            for (final Filter subFilter : subFilters) {
                Reject.ifNull(subFilter);
                subFiltersList.add(subFilter);
            }
            return new Filter(new OrImpl(Collections.unmodifiableList(subFiltersList)));
        }
    }

    /**
     * Creates a new {@code or} filter using the provided list of sub-filters.
     * <p>
     * Creating a new {@code or} filter with a {@code null} or empty list of
     * sub-filters is equivalent to calling {@link #alwaysFalse()}.
     *
     * @param subFilters
     *            The list of sub-filters, may be empty or {@code null}.
     * @return The newly created {@code or} filter.
     */
    public static Filter or(final Filter... subFilters) {
        if (subFilters == null || subFilters.length == 0) {
            // RFC 4526 - FALSE filter.
            return alwaysFalse();
        } else if (subFilters.length == 1) {
            Reject.ifNull(subFilters[0]);
            return new Filter(new OrImpl(Collections.singletonList(subFilters[0])));
        } else {
            final List<Filter> subFiltersList = new ArrayList<>(subFilters.length);
            for (final Filter subFilter : subFilters) {
                Reject.ifNull(subFilter);
                subFiltersList.add(subFilter);
            }
            return new Filter(new OrImpl(Collections.unmodifiableList(subFiltersList)));
        }
    }

    /**
     * Creates a new {@code present} filter using the provided attribute
     * description.
     *
     * @param attributeDescription
     *            The attribute description.
     * @return The newly created {@code present} filter.
     */
    public static Filter present(final String attributeDescription) {
        Reject.ifNull(attributeDescription);
        if ("objectclass".equals(toLowerCase(attributeDescription))) {
            return OBJECT_CLASS_PRESENT;
        }
        return new Filter(new PresentImpl(attributeDescription));
    }

    /**
     * Creates a new {@code substrings} filter using the provided attribute
     * description, {@code initial}, {@code final}, and {@code any} sub-strings.
     * <p>
     * Any substrings which are not instances of {@code ByteString} will be
     * converted using the {@link ByteString#valueOfObject(Object)} method.
     *
     * @param attributeDescription
     *            The attribute description.
     * @param initialSubstring
     *            The initial sub-string, may be {@code null} if either
     *            {@code finalSubstring} or {@code anySubstrings} are specified.
     * @param anySubstrings
     *            The final sub-string, may be {@code null} or empty if either
     *            {@code finalSubstring} or {@code initialSubstring} are
     *            specified.
     * @param finalSubstring
     *            The final sub-string, may be {@code null}, may be {@code null}
     *            if either {@code initialSubstring} or {@code anySubstrings}
     *            are specified.
     * @return The newly created {@code substrings} filter.
     */
    public static Filter substrings(final String attributeDescription,
            final Object initialSubstring, final Collection<?> anySubstrings,
            final Object finalSubstring) {
        Reject.ifNull(attributeDescription);
        Reject.ifFalse(initialSubstring != null
                || finalSubstring != null
                || (anySubstrings != null && anySubstrings.size() > 0),
                "at least one substring (initial, any or final) must be specified");

        List<ByteString> anySubstringList;
        if (anySubstrings == null || anySubstrings.size() == 0) {
            anySubstringList = Collections.emptyList();
        } else if (anySubstrings.size() == 1) {
            final Object anySubstring = anySubstrings.iterator().next();
            Reject.ifNull(anySubstring);
            anySubstringList = Collections.singletonList(ByteString.valueOfObject(anySubstring));
        } else {
            anySubstringList = new ArrayList<>(anySubstrings.size());
            for (final Object anySubstring : anySubstrings) {
                Reject.ifNull(anySubstring);

                anySubstringList.add(ByteString.valueOfObject(anySubstring));
            }
            anySubstringList = Collections.unmodifiableList(anySubstringList);
        }

        return new Filter(new SubstringsImpl(attributeDescription,
                initialSubstring != null ? ByteString.valueOfObject(initialSubstring) : null,
                anySubstringList, finalSubstring != null ? ByteString.valueOfObject(finalSubstring)
                        : null));
    }

    /**
     * Creates a new {@code unrecognized} filter using the provided ASN1 filter
     * tag and content. This type of filter should be used for filters which are
     * not part of the standard filter definition.
     *
     * @param filterTag
     *            The ASN.1 tag.
     * @param filterBytes
     *            The filter content.
     * @return The newly created {@code unrecognized} filter.
     */
    public static Filter unrecognized(final byte filterTag, final ByteString filterBytes) {
        Reject.ifNull(filterBytes);
        return new Filter(new UnrecognizedImpl(filterTag, filterBytes));
    }

    /**
     * Parses the provided LDAP string representation of a filter as a
     * {@code Filter}.
     *
     * @param string
     *            The LDAP string representation of a filter.
     * @return The parsed {@code Filter}.
     * @throws LocalizedIllegalArgumentException
     *             If {@code string} is not a valid LDAP string representation
     *             of a filter.
     * @see #format(String, Object...)
     */
    public static Filter valueOf(final String string) {
        Reject.ifNull(string);

        // If the filter is enclosed in a pair of single quotes it
        // is invalid (issue #1024).
        if (string.length() > 1 && string.startsWith("'") && string.endsWith("'")) {
            final LocalizableMessage message = ERR_LDAP_FILTER_ENCLOSED_IN_APOSTROPHES.get(string);
            throw new LocalizedIllegalArgumentException(message);
        }

        try {
            if (string.startsWith("(")) {
                if (string.endsWith(")")) {
                    return valueOf0(string, 1, string.length() - 1);
                } else {
                    final LocalizableMessage message =
                            ERR_LDAP_FILTER_MISMATCHED_PARENTHESES.get(string, 1, string.length());
                    throw new LocalizedIllegalArgumentException(message);
                }
            } else {
                // We tolerate the top level filter component not being
                // surrounded by parentheses.
                return valueOf0(string, 0, string.length());
            }
        } catch (final LocalizedIllegalArgumentException liae) {
            throw liae;
        } catch (final Exception e) {
            final LocalizableMessage message =
                    ERR_LDAP_FILTER_UNCAUGHT_EXCEPTION.get(string, String.valueOf(e));
            throw new LocalizedIllegalArgumentException(message);
        }
    }

    /**
     * Creates a new filter using the provided filter template and unescaped
     * assertion values. This method first escapes each of the assertion values
     * and then substitutes them into the template using
     * {@link String#format(String, Object...)}. Finally, the formatted string
     * is parsed as an LDAP filter using {@link #valueOf(String)}.
     * <p>
     * This method may be useful in cases where the structure of a filter is not
     * known at compile time, for example, it may be obtained from a
     * configuration file. Example usage:
     *
     * <pre>
     * String template = &quot;(|(cn=%s)(uid=user.%s))&quot;;
     * Filter filter = Filter.format(template, &quot;alice&quot;, 123);
     * </pre>
     *
     * Any assertion values which are not instances of {@code ByteString} will
     * be converted using the {@link ByteString#valueOfObject(Object)} method.
     *
     * @param template
     *            The filter template.
     * @param assertionValues
     *            The assertion values to be substituted into the template.
     * @return The formatted template parsed as a {@code Filter}.
     * @throws LocalizedIllegalArgumentException
     *             If the formatted template is not a valid LDAP string
     *             representation of a filter.
     * @see #escapeAssertionValue(Object)
     */
    public static Filter format(final String template, final Object... assertionValues) {
        final String[] assertionValueStrings = new String[assertionValues.length];
        for (int i = 0; i < assertionValues.length; i++) {
            assertionValueStrings[i] = escapeAssertionValue(assertionValues[i]);
        }
        final String filterString = String.format(template, (Object[]) assertionValueStrings);
        return valueOf(filterString);
    }

    /** Converts an assertion value to a substring filter. */
    private static Filter assertionValue2SubstringFilter(final String filterString,
            final String attrType, final int equalPos, final int endPos) {
        // Get a binary representation of the value.
        final byte[] valueBytes = getBytes(filterString.substring(equalPos, endPos));

        // Find the locations of all the asterisks in the value. Also, check to
        // see if there are any escaped values, since they will need special treatment.
        boolean hasEscape = false;
        final LinkedList<Integer> asteriskPositions = new LinkedList<>();
        for (int i = 0; i < valueBytes.length; i++) {
            if (valueBytes[i] == ASTERISK) {
                asteriskPositions.add(i);
            } else if (valueBytes[i] == BACKSLASH) {
                hasEscape = true;
            }
        }

        // If there were no asterisks, then this isn't a substring filter.
        if (asteriskPositions.isEmpty()) {
            final LocalizableMessage message =
                    ERR_LDAP_FILTER_SUBSTRING_NO_ASTERISKS.get(filterString, equalPos + 1, endPos);
            throw new LocalizedIllegalArgumentException(message);
        }

        // If the value starts with an asterisk, then there is no subInitial
        // component. Otherwise, parse out the subInitial.
        ByteString subInitial;
        int firstPos = asteriskPositions.removeFirst();
        if (firstPos == 0) {
            subInitial = null;
        } else if (hasEscape) {
            final ByteStringBuilder buffer = new ByteStringBuilder(firstPos);
            escapeHexChars(buffer, attrType, valueBytes, 0, firstPos, equalPos);
            subInitial = buffer.toByteString();
        } else {
            subInitial = ByteString.wrap(valueBytes, 0, firstPos);
        }

        // Next, process through the rest of the asterisks to get the subAny values.
        final ArrayList<ByteString> subAny = new ArrayList<>();
        for (final int asteriskPos : asteriskPositions) {
            final int length = asteriskPos - firstPos - 1;

            if (hasEscape) {
                final ByteStringBuilder buffer = new ByteStringBuilder(length);
                escapeHexChars(buffer, attrType, valueBytes, firstPos + 1, asteriskPos, equalPos);
                subAny.add(buffer.toByteString());
                buffer.clear();
            } else {
                subAny.add(ByteString.wrap(valueBytes, firstPos + 1, length));
            }
            firstPos = asteriskPos;
        }

        // Finally, see if there is anything after the last asterisk, which
        // would be the subFinal value.
        ByteString subFinal;
        if (firstPos == (valueBytes.length - 1)) {
            subFinal = null;
        } else {
            final int length = valueBytes.length - firstPos - 1;

            if (hasEscape) {
                final ByteStringBuilder buffer = new ByteStringBuilder(length);
                escapeHexChars(buffer, attrType, valueBytes, firstPos + 1, valueBytes.length,
                        equalPos);
                subFinal = buffer.toByteString();
            } else {
                subFinal = ByteString.wrap(valueBytes, firstPos + 1, length);
            }
        }
        return new Filter(new SubstringsImpl(attrType, subInitial, subAny, subFinal));
    }

    private static void escapeHexChars(final ByteStringBuilder valueBuffer, final String string,
            final byte[] valueBytes, final int fromIndex, final int len, final int errorIndex) {
        for (int i = fromIndex; i < len; i++) {
            if (valueBytes[i] == BACKSLASH) {
                // The next two bytes must be the hex characters that comprise
                // the binary value.
                if (i + 2 >= valueBytes.length) {
                    final LocalizableMessage message =
                            ERR_LDAP_FILTER_INVALID_ESCAPED_BYTE.get(string, errorIndex + i + 1);
                    throw new LocalizedIllegalArgumentException(message);
                }

                byte byteValue = 0;
                switch (valueBytes[++i]) {
                case 0x30: // '0'
                    break;
                case 0x31: // '1'
                    byteValue = (byte) 0x10;
                    break;
                case 0x32: // '2'
                    byteValue = (byte) 0x20;
                    break;
                case 0x33: // '3'
                    byteValue = (byte) 0x30;
                    break;
                case 0x34: // '4'
                    byteValue = (byte) 0x40;
                    break;
                case 0x35: // '5'
                    byteValue = (byte) 0x50;
                    break;
                case 0x36: // '6'
                    byteValue = (byte) 0x60;
                    break;
                case 0x37: // '7'
                    byteValue = (byte) 0x70;
                    break;
                case 0x38: // '8'
                    byteValue = (byte) 0x80;
                    break;
                case 0x39: // '9'
                    byteValue = (byte) 0x90;
                    break;
                case 0x41: // 'A'
                case 0x61: // 'a'
                    byteValue = (byte) 0xA0;
                    break;
                case 0x42: // 'B'
                case 0x62: // 'b'
                    byteValue = (byte) 0xB0;
                    break;
                case 0x43: // 'C'
                case 0x63: // 'c'
                    byteValue = (byte) 0xC0;
                    break;
                case 0x44: // 'D'
                case 0x64: // 'd'
                    byteValue = (byte) 0xD0;
                    break;
                case 0x45: // 'E'
                case 0x65: // 'e'
                    byteValue = (byte) 0xE0;
                    break;
                case 0x46: // 'F'
                case 0x66: // 'f'
                    byteValue = (byte) 0xF0;
                    break;
                default:
                    final LocalizableMessage message =
                            ERR_LDAP_FILTER_INVALID_ESCAPED_BYTE.get(string, errorIndex + i + 1);
                    throw new LocalizedIllegalArgumentException(message);
                }

                switch (valueBytes[++i]) {
                case 0x30: // '0'
                    break;
                case 0x31: // '1'
                    byteValue |= 0x01;
                    break;
                case 0x32: // '2'
                    byteValue |= 0x02;
                    break;
                case 0x33: // '3'
                    byteValue |= 0x03;
                    break;
                case 0x34: // '4'
                    byteValue |= 0x04;
                    break;
                case 0x35: // '5'
                    byteValue |= 0x05;
                    break;
                case 0x36: // '6'
                    byteValue |= 0x06;
                    break;
                case 0x37: // '7'
                    byteValue |= 0x07;
                    break;
                case 0x38: // '8'
                    byteValue |= 0x08;
                    break;
                case 0x39: // '9'
                    byteValue |= 0x09;
                    break;
                case 0x41: // 'A'
                case 0x61: // 'a'
                    byteValue |= 0x0A;
                    break;
                case 0x42: // 'B'
                case 0x62: // 'b'
                    byteValue |= 0x0B;
                    break;
                case 0x43: // 'C'
                case 0x63: // 'c'
                    byteValue |= 0x0C;
                    break;
                case 0x44: // 'D'
                case 0x64: // 'd'
                    byteValue |= 0x0D;
                    break;
                case 0x45: // 'E'
                case 0x65: // 'e'
                    byteValue |= 0x0E;
                    break;
                case 0x46: // 'F'
                case 0x66: // 'f'
                    byteValue |= 0x0F;
                    break;
                default:
                    final LocalizableMessage message =
                            ERR_LDAP_FILTER_INVALID_ESCAPED_BYTE.get(string, errorIndex + i + 1);
                    throw new LocalizedIllegalArgumentException(message);
                }

                valueBuffer.appendByte(byteValue);
            } else {
                valueBuffer.appendByte(valueBytes[i]);
            }
        }
    }

    private static Filter valueOf0(final String string, final int beginIndex /* inclusive */,
            final int endIndex /* exclusive */) {
        if (beginIndex >= endIndex) {
            final LocalizableMessage message = ERR_LDAP_FILTER_STRING_NULL.get();
            throw new LocalizedIllegalArgumentException(message);
        }

        final int index = beginIndex;
        final char c = string.charAt(index);

        if (c == '&') {
            final List<Filter> subFilters = valueOfFilterList(string, index + 1, endIndex);
            if (subFilters.isEmpty()) {
                return alwaysTrue();
            } else {
                return new Filter(new AndImpl(subFilters));
            }
        } else if (c == '|') {
            final List<Filter> subFilters = valueOfFilterList(string, index + 1, endIndex);
            if (subFilters.isEmpty()) {
                return alwaysFalse();
            } else {
                return new Filter(new OrImpl(subFilters));
            }
        } else if (c == '!') {
            if ((string.charAt(index + 1) != '(') || (string.charAt(endIndex - 1) != ')')) {
                final LocalizableMessage message =
                        ERR_LDAP_FILTER_COMPOUND_MISSING_PARENTHESES.get(string, index,
                                endIndex - 1);
                throw new LocalizedIllegalArgumentException(message);
            }
            final List<Filter> subFilters = valueOfFilterList(string, index + 1, endIndex);
            if (subFilters.size() != 1) {
                final LocalizableMessage message =
                        ERR_LDAP_FILTER_NOT_EXACTLY_ONE.get(string, index, endIndex);
                throw new LocalizedIllegalArgumentException(message);
            }
            return new Filter(new NotImpl(subFilters.get(0)));
        } else {
            // It must be a simple filter. It must have an equal sign at some
            // point, so find it.
            final int equalPos = indexOf(string, index, endIndex);
            if (equalPos <= index) {
                final LocalizableMessage message =
                        ERR_LDAP_FILTER_NO_EQUAL_SIGN.get(string, index, endIndex);
                throw new LocalizedIllegalArgumentException(message);
            }

            // Look at the character immediately before the equal sign,
            // because it may help determine the filter type.
            String attributeDescription;
            ByteString assertionValue;

            switch (string.charAt(equalPos - 1)) {
            case '~':
                attributeDescription = valueOfAttributeDescription(string, index, equalPos - 1);
                assertionValue = valueOfAssertionValue(string, equalPos + 1, endIndex);
                return new Filter(new ApproxMatchImpl(attributeDescription, assertionValue));
            case '>':
                attributeDescription = valueOfAttributeDescription(string, index, equalPos - 1);
                assertionValue = valueOfAssertionValue(string, equalPos + 1, endIndex);
                return new Filter(new GreaterOrEqualImpl(attributeDescription, assertionValue));
            case '<':
                attributeDescription = valueOfAttributeDescription(string, index, equalPos - 1);
                assertionValue = valueOfAssertionValue(string, equalPos + 1, endIndex);
                return new Filter(new LessOrEqualImpl(attributeDescription, assertionValue));
            case ':':
                return valueOfExtensibleFilter(string, index, equalPos, endIndex);
            default:
                attributeDescription = valueOfAttributeDescription(string, index, equalPos);
                return valueOfGenericFilter(string, attributeDescription, equalPos + 1, endIndex);
            }
        }
    }

    private static int indexOf(final String string, final int index, final int endIndex) {
        for (int i = index; i < endIndex; i++) {
            if (string.charAt(i) == '=') {
                return i;
            }
        }
        return -1;
    }

    private static ByteString valueOfAssertionValue(final String string, final int startIndex,
            final int endIndex) {
        final byte[] valueBytes = getBytes(string.substring(startIndex, endIndex));
        if (hasEscape(valueBytes)) {
            final ByteStringBuilder valueBuffer = new ByteStringBuilder(valueBytes.length);
            escapeHexChars(valueBuffer, string, valueBytes, 0, valueBytes.length, startIndex);
            return valueBuffer.toByteString();
        } else {
            return ByteString.wrap(valueBytes);
        }
    }

    private static boolean hasEscape(final byte[] valueBytes) {
        for (final byte valueByte : valueBytes) {
            if (valueByte == BACKSLASH) {
                return true;
            }
        }
        return false;
    }

    private static String valueOfAttributeDescription(final String string, final int startIndex,
            final int endIndex) {
        // The part of the filter string before the equal sign should be the
        // attribute type. Make sure that the characters it contains are
        // acceptable for attribute types, including those allowed by
        // attribute name exceptions (ASCII letters and digits, the dash,
        // and the underscore). We also need to allow attribute options,
        // which includes the semicolon and the equal sign.
        final String attrType = string.substring(startIndex, endIndex);
        for (int i = 0; i < attrType.length(); i++) {
            switch (attrType.charAt(i)) {
            case '-':
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
            case ';':
            case '=':
            case 'A':
            case 'B':
            case 'C':
            case 'D':
            case 'E':
            case 'F':
            case 'G':
            case 'H':
            case 'I':
            case 'J':
            case 'K':
            case 'L':
            case 'M':
            case 'N':
            case 'O':
            case 'P':
            case 'Q':
            case 'R':
            case 'S':
            case 'T':
            case 'U':
            case 'V':
            case 'W':
            case 'X':
            case 'Y':
            case 'Z':
            case '_':
            case 'a':
            case 'b':
            case 'c':
            case 'd':
            case 'e':
            case 'f':
            case 'g':
            case 'h':
            case 'i':
            case 'j':
            case 'k':
            case 'l':
            case 'm':
            case 'n':
            case 'o':
            case 'p':
            case 'q':
            case 'r':
            case 's':
            case 't':
            case 'u':
            case 'v':
            case 'w':
            case 'x':
            case 'y':
            case 'z':
                // These are all OK.
                break;

            case '.':
            case '/':
            case ':':
            case '<':
            case '>':
            case '?':
            case '@':
            case '[':
            case '\\':
            case ']':
            case '^':
            case '`':
                // These are not allowed, but they are explicitly called out
                // because they are included in the range of values between '-'
                // and 'z', and making sure all possible characters are included
                // can help make the switch statement more efficient. We'll fall
                // through to the default clause to reject them.
            default:
                final LocalizableMessage message =
                        ERR_LDAP_FILTER_INVALID_CHAR_IN_ATTR_TYPE.get(attrType, String
                                .valueOf(attrType.charAt(i)), i);
                throw new LocalizedIllegalArgumentException(message);
            }
        }

        return attrType;
    }

    private static Filter valueOfExtensibleFilter(final String string, final int startIndex,
            final int equalIndex, final int endIndex) {
        String attributeDescription = null;
        boolean dnAttributes = false;
        String matchingRule = null;

        // Look at the first character. If it is a colon, then it must be
        // followed by either the string "dn" or the matching rule ID. If it
        // is not, then must be the attribute type.
        final String lowerLeftStr = toLowerCase(string.substring(startIndex, equalIndex));
        if (string.charAt(startIndex) == ':') {
            // See if it starts with ":dn". Otherwise, it much be the matching
            // rule ID.
            if (lowerLeftStr.startsWith(":dn:")) {
                dnAttributes = true;

                if ((startIndex + 4) < (equalIndex - 1)) {
                    matchingRule = string.substring(startIndex + 4, equalIndex - 1);
                }
            } else {
                matchingRule = string.substring(startIndex + 1, equalIndex - 1);
            }
        } else {
            final int colonPos = string.indexOf(':', startIndex);
            if (colonPos < 0) {
                final LocalizableMessage message =
                        ERR_LDAP_FILTER_EXTENSIBLE_MATCH_NO_COLON.get(string, startIndex);
                throw new LocalizedIllegalArgumentException(message);
            }

            attributeDescription = string.substring(startIndex, colonPos);

            // If there is anything left, then it should be ":dn" and/or ":"
            // followed by the matching rule ID.
            if (colonPos < (equalIndex - 1)) {
                if (lowerLeftStr.startsWith(":dn:", colonPos - startIndex)) {
                    dnAttributes = true;

                    if ((colonPos + 4) < (equalIndex - 1)) {
                        matchingRule = string.substring(colonPos + 4, equalIndex - 1);
                    }
                } else {
                    matchingRule = string.substring(colonPos + 1, equalIndex - 1);
                }
            }
        }

        // Parse out the attribute value.
        final ByteString matchValue = valueOfAssertionValue(string, equalIndex + 1, endIndex);

        // Make sure that the filter has at least one of an attribute
        // description and/or a matching rule ID.
        if (attributeDescription == null && matchingRule == null) {
            final LocalizableMessage message =
                    ERR_LDAP_FILTER_EXTENSIBLE_MATCH_NO_AD_OR_MR.get(string, startIndex);
            throw new LocalizedIllegalArgumentException(message);
        }

        return new Filter(new ExtensibleMatchImpl(matchingRule, attributeDescription, matchValue,
                dnAttributes));
    }

    private static List<Filter> valueOfFilterList(final String string, final int startIndex,
            final int endIndex) {
        // If the end index is equal to the start index, then there are no
        // components.
        if (startIndex >= endIndex) {
            return Collections.emptyList();
        }

        // At least one sub-filter.
        Filter firstFilter = null;
        List<Filter> subFilters = null;

        // The first and last characters must be parentheses. If not, then
        // that's an error.
        if ((string.charAt(startIndex) != '(') || (string.charAt(endIndex - 1) != ')')) {
            final LocalizableMessage message =
                    ERR_LDAP_FILTER_COMPOUND_MISSING_PARENTHESES.get(string, startIndex, endIndex);
            throw new LocalizedIllegalArgumentException(message);
        }

        // Iterate through the characters in the value. Whenever an open
        // parenthesis is found, locate the corresponding close parenthesis
        // by counting the number of intermediate open/close parentheses.
        int pendingOpens = 0;
        int openIndex = -1;
        for (int i = startIndex; i < endIndex; i++) {
            final char c = string.charAt(i);
            if (c == '(') {
                if (openIndex < 0) {
                    openIndex = i;
                }
                pendingOpens++;
            } else if (c == ')') {
                pendingOpens--;
                if (pendingOpens == 0) {
                    final Filter subFilter = valueOf0(string, openIndex + 1, i);
                    if (subFilters != null) {
                        subFilters.add(subFilter);
                    } else if (firstFilter != null) {
                        subFilters = new LinkedList<>();
                        subFilters.add(firstFilter);
                        subFilters.add(subFilter);
                        firstFilter = null;
                    } else {
                        firstFilter = subFilter;
                    }
                    openIndex = -1;
                } else if (pendingOpens < 0) {
                    final LocalizableMessage message =
                            ERR_LDAP_FILTER_NO_CORRESPONDING_OPEN_PARENTHESIS.get(string, i);
                    throw new LocalizedIllegalArgumentException(message);
                }
            } else if (pendingOpens <= 0) {
                final LocalizableMessage message =
                        ERR_LDAP_FILTER_COMPOUND_MISSING_PARENTHESES.get(string, startIndex,
                                endIndex);
                throw new LocalizedIllegalArgumentException(message);
            }
        }

        // At this point, we have parsed the entire set of filter
        // components. The list of open parenthesis positions must be empty.
        if (pendingOpens != 0) {
            final LocalizableMessage message =
                    ERR_LDAP_FILTER_NO_CORRESPONDING_CLOSE_PARENTHESIS.get(string, openIndex);
            throw new LocalizedIllegalArgumentException(message);
        }

        if (subFilters != null) {
            return Collections.unmodifiableList(subFilters);
        } else {
            return Collections.singletonList(firstFilter);
        }
    }

    private static Filter valueOfGenericFilter(final String string,
            final String attributeDescription, final int startIndex, final int endIndex) {
        final int asteriskIdx = string.indexOf('*', startIndex);
        if (startIndex >= endIndex) {
            // Equality filter with empty assertion value.
            return new Filter(new EqualityMatchImpl(attributeDescription, ByteString.empty()));
        } else if ((endIndex - startIndex == 1) && (string.charAt(startIndex) == '*')) {
            // Single asterisk is a present filter.
            return present(attributeDescription);
        } else if (asteriskIdx > 0 && asteriskIdx <= endIndex) {
            // Substring filter.
            return assertionValue2SubstringFilter(string, attributeDescription, startIndex,
                    endIndex);
        } else {
            // equality filter.
            final ByteString assertionValue = valueOfAssertionValue(string, startIndex, endIndex);
            return new Filter(new EqualityMatchImpl(attributeDescription, assertionValue));
        }
    }

    /**
     * Appends a properly-cleaned version of the provided value to the given
     * builder so that it can be safely used in string representations of this
     * search filter. The formatting changes that may be performed will be in
     * compliance with the specification in RFC 2254.
     *
     * @param builder
     *            The builder to which the "safe" version of the value will be
     *            appended.
     * @param value
     *            The value to be appended to the builder.
     */
    private static void valueToFilterString(final StringBuilder builder, final ByteString value) {
        // Get the binary representation of the value and iterate through
        // it to see if there are any unsafe characters. If there are,
        // then escape them and replace them with a two-digit hex
        // equivalent.
        builder.ensureCapacity(builder.length() + value.length());
        for (int i = 0; i < value.length(); i++) {
            // TODO: this is a bit overkill - it will escape all non-ascii
            // chars!
            final byte b = value.byteAt(i);
            if (((b & 0x7F) != b) // Not 7-bit clean
                    || b <= 0x1F  // Below the printable character range
                    || b == 0x28  // Open parenthesis
                    || b == 0x29  // Close parenthesis
                    || b == ASTERISK
                    || b == BACKSLASH
                    || b == 0x7F  /* Delete character */) {
                builder.append('\\');
                builder.append(byteToHex(b));
            } else {
                builder.append((char) b);
            }
        }
    }

    private final Impl pimpl;

    private Filter(final Impl pimpl) {
        this.pimpl = pimpl;
    }

    /**
     * Applies a {@code FilterVisitor} to this {@code Filter}.
     *
     * @param <R>
     *            The return type of the visitor's methods.
     * @param <P>
     *            The type of the additional parameters to the visitor's
     *            methods.
     * @param v
     *            The filter visitor.
     * @param p
     *            Optional additional visitor parameter.
     * @return A result as specified by the visitor.
     */
    public <R, P> R accept(final FilterVisitor<R, P> v, final P p) {
        return pimpl.accept(v, p);
    }

    /**
     * Returns a {@code Matcher} which can be used to compare this
     * {@code Filter} against entries using the default schema.
     *
     * @return The {@code Matcher}.
     */
    public Matcher matcher() {
        return new Matcher(this, Schema.getDefaultSchema());
    }

    /**
     * Returns a {@code Matcher} which can be used to compare this
     * {@code Filter} against entries using the provided {@code Schema}.
     *
     * @param schema
     *            The schema which the {@code Matcher} should use for
     *            comparisons.
     * @return The {@code Matcher}.
     */
    public Matcher matcher(final Schema schema) {
        return new Matcher(this, schema);
    }

    /**
     * Indicates whether this {@code Filter} matches the provided {@code Entry}
     * using the default schema.
     * <p>
     * Calling this method is equivalent to the following:
     *
     * <pre>
     * matcher().matches(entry);
     * </pre>
     *
     * @param entry
     *            The entry to be matched.
     * @return The result of matching the provided {@code Entry} against this
     *         {@code Filter} using the default schema.
     */
    public ConditionResult matches(final Entry entry) {
        return matcher(Schema.getDefaultSchema()).matches(entry);
    }

    /**
     * Returns a {@code String} whose contents is the LDAP string representation
     * of this {@code Filter}.
     *
     * @return The LDAP string representation of this {@code Filter}.
     */
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        return pimpl.accept(TO_STRING_VISITOR, builder).toString();
    }

}
