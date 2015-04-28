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
 *      Portions copyright 2012-2015 ForgeRock AS.
 */
package org.forgerock.opendj.ldap;

import static com.forgerock.opendj.ldap.CoreMessages.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.util.Reject;

/**
 * A search result sort key as defined in RFC 2891 is used to specify how search
 * result entries should be ordered. Sort keys are used with the server side
 * sort request control
 * {@link org.forgerock.opendj.ldap.controls.ServerSideSortRequestControl}, but
 * could also be used for performing client side sorting as well.
 * <p>
 * The following example illustrates how a single sort key may be used to sort
 * entries as they are returned from a search operation using the {@code cn}
 * attribute as the sort key:
 *
 * <pre>
 * Connection connection = ...;
 * SearchRequest request = ...;
 *
 * Comparator&lt;Entry> comparator = SortKey.comparator("cn");
 * Set&lt;SearchResultEntry>; results = new TreeSet&lt;SearchResultEntry>(comparator);
 *
 * connection.search(request, results);
 * </pre>
 *
 * A sort key includes an attribute description and a boolean value that
 * indicates whether the sort should be ascending or descending. It may also
 * contain a specific ordering matching rule that should be used for the sorting
 * process, although if none is provided it will use the default ordering
 * matching rule for the attribute type.
 *
 * @see <a href="http://tools.ietf.org/html/rfc2891">RFC 2891 - LDAP Control
 *      Extension for Server Side Sorting of Search Results </a>
 */
public final class SortKey {
    private static final class CompositeEntryComparator implements Comparator<Entry> {
        private final List<Comparator<Entry>> comparators;

        private CompositeEntryComparator(final List<Comparator<Entry>> comparators) {
            this.comparators = comparators;
        }

        public int compare(final Entry entry1, final Entry entry2) {
            for (final Comparator<Entry> comparator : comparators) {
                final int result = comparator.compare(entry1, entry2);
                if (result != 0) {
                    return result;
                }
            }
            return 0;
        }

    }

    /**
     * A comparator which can be used to compare entries using a sort key.
     */
    private static final class EntryComparator implements Comparator<Entry> {
        private final AttributeDescription attributeDescription;
        private final MatchingRule matchingRule;
        private final boolean isReverseOrder;

        private EntryComparator(final AttributeDescription attributeDescription,
                final MatchingRule matchingRule, final boolean isReverseOrder) {
            this.attributeDescription = attributeDescription;
            this.matchingRule = matchingRule;
            this.isReverseOrder = isReverseOrder;
        }

        /**
         * We must use the lowest available value in both entries and missing
         * attributes sort last.
         */
        public int compare(final Entry entry1, final Entry entry2) {
            // Find and normalize the lowest value attribute in each entry.
            final ByteString normalizedValue1 = lowestValueOf(entry1);
            final ByteString normalizedValue2 = lowestValueOf(entry2);

            // Entries with missing attributes always sort after (regardless of
            // order).
            if (normalizedValue1 == null) {
                return normalizedValue2 != null ? 1 : 0;
            } else if (normalizedValue2 == null) {
                return -1;
            } else if (isReverseOrder) {
                return normalizedValue2.compareTo(normalizedValue1);
            } else {
                return normalizedValue1.compareTo(normalizedValue2);
            }
        }

        private ByteString lowestValueOf(final Entry entry) {
            ByteString normalizedValue = null;
            for (final Attribute attribute : entry.getAllAttributes(attributeDescription)) {
                for (final ByteString value : attribute) {
                    try {
                        final ByteString tmp = matchingRule.normalizeAttributeValue(value);
                        if (normalizedValue == null || tmp.compareTo(normalizedValue) < 0) {
                            normalizedValue = tmp;
                        }
                    } catch (final DecodeException ignored) {
                        // Ignore the error - treat the value as missing.
                    }
                }
            }
            return normalizedValue;
        }

    }

    /**
     * Returns a {@code Comparator} which can be used to compare entries using
     * the provided list of sort keys. The sort keys will be decoded using the
     * default schema.
     *
     * @param keys
     *            The list of sort keys.
     * @return The {@code Comparator}.
     * @throws LocalizedIllegalArgumentException
     *             If one of the sort keys could not be converted to a
     *             comparator.
     * @throws IllegalArgumentException
     *             If {@code keys} was empty.
     * @throws NullPointerException
     *             If {@code keys} was {@code null}.
     */
    public static Comparator<Entry> comparator(final Collection<SortKey> keys) {
        return comparator(Schema.getDefaultSchema(), keys);
    }

    /**
     * Returns a {@code Comparator} which can be used to compare entries using
     * the provided list of sort keys. The sort keys will be decoded using the
     * provided schema.
     *
     * @param schema
     *            The schema which should be used for decoding the sort keys.
     * @param keys
     *            The list of sort keys.
     * @return The {@code Comparator}.
     * @throws LocalizedIllegalArgumentException
     *             If one of the sort keys could not be converted to a
     *             comparator.
     * @throws IllegalArgumentException
     *             If {@code keys} was empty.
     * @throws NullPointerException
     *             If {@code schema} or {@code keys} was {@code null}.
     */
    public static Comparator<Entry> comparator(final Schema schema, final Collection<SortKey> keys) {
        Reject.ifNull(schema, keys);
        Reject.ifFalse(!keys.isEmpty(), "keys must not be empty");

        final List<Comparator<Entry>> comparators = new ArrayList<>(keys.size());
        for (final SortKey key : keys) {
            comparators.add(key.comparator(schema));
        }
        return new CompositeEntryComparator(comparators);
    }

    /**
     * Returns a {@code Comparator} which can be used to compare entries using
     * the provided list of sort keys. The sort keys will be decoded using the
     * provided schema.
     *
     * @param schema
     *            The schema which should be used for decoding the sort keys.
     * @param keys
     *            The list of sort keys.
     * @return The {@code Comparator}.
     * @throws LocalizedIllegalArgumentException
     *             If one of the sort keys could not be converted to a
     *             comparator.
     * @throws IllegalArgumentException
     *             If {@code keys} was empty.
     * @throws NullPointerException
     *             If {@code schema} or {@code keys} was {@code null}.
     */
    public static Comparator<Entry> comparator(final Schema schema, final SortKey... keys) {
        return comparator(schema, Arrays.asList(keys));
    }

    /**
     * Returns a {@code Comparator} which can be used to compare entries using
     * the provided list of sort keys. The sort keys will be decoded using the
     * default schema.
     *
     * @param keys
     *            The list of sort keys.
     * @return The {@code Comparator}.
     * @throws LocalizedIllegalArgumentException
     *             If one of the sort keys could not be converted to a
     *             comparator.
     * @throws IllegalArgumentException
     *             If {@code keys} was empty.
     * @throws NullPointerException
     *             If {@code keys} was {@code null}.
     */
    public static Comparator<Entry> comparator(final SortKey... keys) {
        return comparator(Schema.getDefaultSchema(), keys);
    }

    /**
     * Returns a {@code Comparator} which can be used to compare entries using
     * the provided string representation of a list of sort keys. The sort keys
     * will be decoded using the default schema. The string representation is
     * comprised of a comma separate list of sort keys as defined in
     * {@link #valueOf(String)}. There must be at least one sort key present in
     * the string representation.
     *
     * @param sortKeys
     *            The list of sort keys.
     * @return The {@code Comparator}.
     * @throws LocalizedIllegalArgumentException
     *             If {@code sortKeys} is not a valid string representation of a
     *             list of sort keys, or if one of the sort keys could not be
     *             converted to a comparator.
     * @throws NullPointerException
     *             If {@code sortKeys} was {@code null}.
     */
    public static Comparator<Entry> comparator(final String sortKeys) {
        Reject.ifNull(sortKeys);

        final List<Comparator<Entry>> comparators = new LinkedList<>();
        final StringTokenizer tokenizer = new StringTokenizer(sortKeys, ",");
        while (tokenizer.hasMoreTokens()) {
            final String token = tokenizer.nextToken().trim();
            comparators.add(valueOf(token).comparator());
        }
        if (comparators.isEmpty()) {
            final LocalizableMessage message = ERR_SORT_KEY_NO_SORT_KEYS.get(sortKeys);
            throw new LocalizedIllegalArgumentException(message);
        }
        return new CompositeEntryComparator(comparators);
    }

    /**
     * Parses the provided string representation of a sort key as a
     * {@code SortKey}. The string representation has the following ABNF (see
     * RFC 4512 for definitions of the elements below):
     *
     * <pre>
     *   SortKey = [ PLUS / HYPHEN ]    ; order specifier
     *             attributedescription ; attribute description
     *             [ COLON oid ]        ; ordering matching rule OID
     * </pre>
     *
     * Examples:
     *
     * <pre>
     *   cn                           ; case ignore ascending sort on "cn"
     *   -cn                          ; case ignore descending sort on "cn"
     *   +cn;lang-fr                  ; case ignore ascending sort on "cn;lang-fr"
     *   -cn;lang-fr:caseExactMatch   ; case exact ascending sort on "cn;lang-fr"
     * </pre>
     *
     * @param sortKey
     *            The string representation of a sort key.
     * @return The parsed {@code SortKey}.
     * @throws LocalizedIllegalArgumentException
     *             If {@code sortKey} is not a valid string representation of a
     *             sort key.
     * @throws NullPointerException
     *             If {@code sortKey} was {@code null}.
     */
    public static SortKey valueOf(String sortKey) {
        Reject.ifNull(sortKey);

        boolean reverseOrder = false;
        if (sortKey.startsWith("-")) {
            reverseOrder = true;
            sortKey = sortKey.substring(1);
        } else if (sortKey.startsWith("+")) {
            sortKey = sortKey.substring(1);
        }

        final int colonPos = sortKey.indexOf(':');
        if (colonPos < 0) {
            if (sortKey.length() == 0) {
                final LocalizableMessage message = ERR_SORT_KEY_NO_ATTR_NAME.get(sortKey);
                throw new LocalizedIllegalArgumentException(message);
            }

            return new SortKey(sortKey, reverseOrder, null);
        } else if (colonPos == 0) {
            final LocalizableMessage message = ERR_SORT_KEY_NO_ATTR_NAME.get(sortKey);
            throw new LocalizedIllegalArgumentException(message);
        } else if (colonPos == (sortKey.length() - 1)) {
            final LocalizableMessage message = ERR_SORT_KEY_NO_MATCHING_RULE.get(sortKey);
            throw new LocalizedIllegalArgumentException(message);
        } else {
            final String attrName = sortKey.substring(0, colonPos);
            final String ruleID = sortKey.substring(colonPos + 1);

            return new SortKey(attrName, reverseOrder, ruleID);
        }
    }

    private final String attributeDescription;

    private final String orderingMatchingRule;

    private final boolean isReverseOrder;

    /**
     * Creates a new sort key using the provided attribute description. The
     * returned sort key will compare attributes in the order specified using
     * the named ordering matching rule.
     *
     * @param attributeDescription
     *            The name of the attribute to be sorted using this sort key.
     * @param isReverseOrder
     *            {@code true} if this sort key should be evaluated in reverse
     *            (descending) order.
     * @param orderingMatchingRule
     *            The name or OID of the ordering matching rule, which should be
     *            used when comparing attributes using this sort key, or
     *            {@code null} if the default ordering matching rule associated
     *            with the attribute should be used.
     * @throws NullPointerException
     *             If {@code AttributeDescription} was {@code null}.
     */
    public SortKey(final AttributeDescription attributeDescription, final boolean isReverseOrder,
            final MatchingRule orderingMatchingRule) {
        Reject.ifNull(attributeDescription);
        this.attributeDescription = attributeDescription.toString();
        this.orderingMatchingRule =
                orderingMatchingRule != null ? orderingMatchingRule.getNameOrOID() : null;
        this.isReverseOrder = isReverseOrder;
    }

    /**
     * Creates a new sort key using the provided attribute description. The
     * returned sort key will compare attributes in ascending order using the
     * default ordering matching rule associated with the attribute.
     *
     * @param attributeDescription
     *            The name of the attribute to be sorted using this sort key.
     * @throws NullPointerException
     *             If {@code AttributeDescription} was {@code null}.
     */
    public SortKey(final String attributeDescription) {
        this(attributeDescription, false, null);
    }

    /**
     * Creates a new sort key using the provided attribute description. The
     * returned sort key will compare attributes in the order specified using
     * the default ordering matching rule associated with the attribute.
     *
     * @param attributeDescription
     *            The name of the attribute to be sorted using this sort key.
     * @param isReverseOrder
     *            {@code true} if this sort key should be evaluated in reverse
     *            (descending) order.
     * @throws NullPointerException
     *             If {@code AttributeDescription} was {@code null}.
     */
    public SortKey(final String attributeDescription, final boolean isReverseOrder) {
        this(attributeDescription, isReverseOrder, null);
    }

    /**
     * Creates a new sort key using the provided attribute description. The
     * returned sort key will compare attributes in the order specified using
     * the named ordering matching rule.
     *
     * @param attributeDescription
     *            The name of the attribute to be sorted using this sort key.
     * @param isReverseOrder
     *            {@code true} if this sort key should be evaluated in reverse
     *            (descending) order.
     * @param orderingMatchingRule
     *            The name or OID of the ordering matching rule, which should be
     *            used when comparing attributes using this sort key, or
     *            {@code null} if the default ordering matching rule associated
     *            with the attribute should be used.
     * @throws NullPointerException
     *             If {@code AttributeDescription} was {@code null}.
     */
    public SortKey(final String attributeDescription, final boolean isReverseOrder,
            final String orderingMatchingRule) {
        Reject.ifNull(attributeDescription);
        this.attributeDescription = attributeDescription;
        this.orderingMatchingRule = orderingMatchingRule;
        this.isReverseOrder = isReverseOrder;
    }

    /**
     * Returns a {@code Comparator} which can be used to compare entries using
     * this sort key. The attribute description and matching rule, if present,
     * will be decoded using the default schema.
     *
     * @return The {@code Comparator}.
     * @throws LocalizedIllegalArgumentException
     *             If attributeDescription is not a valid LDAP string
     *             representation of an attribute description, or if no ordering
     *             matching rule was found.
     */
    public Comparator<Entry> comparator() {
        return comparator(Schema.getDefaultSchema());
    }

    /**
     * Returns a {@code Comparator} which can be used to compare entries using
     * this sort key. The attribute description and matching rule, if present,
     * will be decoded using the provided schema.
     *
     * @param schema
     *            The schema which should be used for decoding the attribute
     *            description and matching rule.
     * @return The {@code Comparator}.
     * @throws LocalizedIllegalArgumentException
     *             If attributeDescription is not a valid LDAP string
     *             representation of an attribute description, or if no ordering
     *             matching rule was found.
     * @throws NullPointerException
     *             If {@code schema} was {@code null}.
     */
    public Comparator<Entry> comparator(final Schema schema) {
        Reject.ifNull(schema);

        final AttributeDescription ad = AttributeDescription.valueOf(attributeDescription, schema);

        MatchingRule mrule;
        if (orderingMatchingRule != null) {
            // FIXME: need to check that the matching rule is a matching rule
            // and can
            // be used with the attribute.
            mrule = schema.getMatchingRule(orderingMatchingRule);

            if (mrule == null) {
                // Specified ordering matching rule not found.
                final LocalizableMessage message =
                        ERR_SORT_KEY_MRULE_NOT_FOUND.get(toString(), orderingMatchingRule);
                throw new LocalizedIllegalArgumentException(message);
            }
        } else {
            mrule = ad.getAttributeType().getOrderingMatchingRule();

            if (mrule == null) {
                // No default ordering matching rule found.
                final LocalizableMessage message =
                        ERR_SORT_KEY_DEFAULT_MRULE_NOT_FOUND.get(toString(), attributeDescription);
                throw new LocalizedIllegalArgumentException(message);
            }
        }

        return new EntryComparator(ad, mrule, isReverseOrder);
    }

    /**
     * Returns the name of the attribute to be sorted using this sort key.
     *
     * @return The name of the attribute to be sorted using this sort key.
     */
    public String getAttributeDescription() {
        return attributeDescription;
    }

    /**
     * Returns the name or OID of the ordering matching rule, if specified,
     * which should be used when comparing attributes using this sort key.
     *
     * @return The name or OID of the ordering matching rule, if specified,
     *         which should be used when comparing attributes using this sort
     *         key, or {@code null} if the default ordering matching rule
     *         associated with the attribute should be used.
     */
    public String getOrderingMatchingRule() {
        return orderingMatchingRule;
    }

    /**
     * Returns {@code true} if this sort key should be evaluated in reverse
     * (descending) order. More specifically, comparisons performed using the
     * ordering matching rule associated with this sort key will have their
     * results inverted.
     *
     * @return {@code true} if this sort key should be evaluated in reverse
     *         (descending) order.
     */
    public boolean isReverseOrder() {
        return isReverseOrder;
    }

    /**
     * Returns a string representation of this sort key using the format defined
     * in {@link #valueOf(String)}.
     *
     * @return A string representation of this sort key.
     */
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        if (isReverseOrder) {
            builder.append('-');
        }
        builder.append(attributeDescription);
        if (orderingMatchingRule != null) {
            builder.append(':');
            builder.append(orderingMatchingRule);
        }
        return builder.toString();
    }

}
