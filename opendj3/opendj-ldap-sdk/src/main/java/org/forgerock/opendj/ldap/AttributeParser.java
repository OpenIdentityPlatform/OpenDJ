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
 *      Copyright 2012 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.NoSuchElementException;
import java.util.Set;

import org.forgerock.opendj.ldap.schema.Schema;

import com.forgerock.opendj.util.Base64;
import com.forgerock.opendj.util.Collections2;
import com.forgerock.opendj.util.Function;
import com.forgerock.opendj.util.Functions;

/**
 * A fluent API for parsing attributes as different types of value. An attribute
 * parser is obtained from an entry using the method
 * {@link Entry#parseAttribute} or from an attribute using
 * {@link Attribute#parse()}.
 * <p>
 * Methods throw an {@code IllegalArgumentException} when a value cannot be
 * parsed (e.g. because its syntax is invalid). Methods which return a
 * {@code Set} always return a modifiable non-{@code null} result.
 * <p>
 * Examples:
 *
 * <pre>
 * Entry entry = ...;
 *
 * Calendar timestamp = entry.parseAttribute("createTimestamp").asCalendar();
 * boolean isEnabled = entry.parseAttribute("enabled").asBoolean(false);
 *
 * Entry group = ...;
 * Schema schema = ...;
 *
 * Set&lt;DN&gt; members = group.parseAttribute("member").usingSchema(schema).asSetOfDN();
 * </pre>
 *
 * @see Entry#parseAttribute
 * @see Attribute#parse()
 */
public final class AttributeParser {
    // TODO: enums, filters, rdns?

    private static final AttributeParser NULL_INSTANCE = new AttributeParser(null);

    /**
     * Returns an attribute parser for the provided attribute. {@code null}
     * attributes are permitted and will be treated as if an empty attribute was
     * provided.
     *
     * @param attribute
     *            The attribute to be parsed, which may be {@code null}.
     * @return The attribute parser.
     */
    public static AttributeParser parseAttribute(final Attribute attribute) {
        return isEmpty(attribute) ? NULL_INSTANCE : new AttributeParser(attribute);
    }

    private static boolean isEmpty(final Attribute attribute) {
        return (attribute == null) || attribute.isEmpty();
    }

    private final Attribute attribute;
    private Schema schema;

    private AttributeParser(final Attribute attribute) {
        this.attribute = attribute;
    }

    /**
     * Returns the first value decoded as an {@code AttributeDescription} using
     * the schema associated with this parser, or {@code null} if the attribute
     * does not contain any values.
     *
     * @return The first value decoded as an {@code AttributeDescription}.
     */
    public AttributeDescription asAttributeDescription() {
        return asAttributeDescription(null);
    }

    /**
     * Returns the first value decoded as an {@code AttributeDescription} using
     * the schema associated with this parser, or {@code defaultValue} if the
     * attribute does not contain any values.
     *
     * @param defaultValue
     *            The default value to return if the attribute is empty.
     * @return The first value decoded as an {@code AttributeDescription}.
     */
    public AttributeDescription asAttributeDescription(final AttributeDescription defaultValue) {
        return parseSingleValue(Functions.valueToAttributeDescription(getSchema()), defaultValue);
    }

    /**
     * Returns the first value encoded as base64, or {@code null} if the
     * attribute does not contain any values.
     *
     * @return The first value encoded as base64.
     */
    public String asBase64() {
        return asBase64(null);
    }

    /**
     * Returns the first value encoded as base64, or {@code defaultValue} if the
     * attribute does not contain any values.
     *
     * @param defaultValue
     *            The default value to return if the attribute is empty.
     * @return The first value encoded as base64.
     */
    public String asBase64(final ByteString defaultValue) {
        return parseSingleValue(Functions.valueToBase64(), Base64.encode(defaultValue));
    }

    /**
     * Returns the first value decoded as a boolean, or {@code null} if the
     * attribute does not contain any values.
     *
     * @return The first value decoded as a boolean.
     */
    public Boolean asBoolean() {
        return isEmpty(attribute) ? null : asBoolean(false /* ignored */);
    }

    /**
     * Returns the first value decoded as an {@code Boolean}, or
     * {@code defaultValue} if the attribute does not contain any values.
     *
     * @param defaultValue
     *            The default value to return if the attribute is empty.
     * @return The first value decoded as an {@code Boolean}.
     */
    public boolean asBoolean(final boolean defaultValue) {
        return parseSingleValue(Functions.valueToBoolean(), defaultValue);
    }

    /**
     * Returns the first value, or {@code null} if the attribute does not
     * contain any values.
     *
     * @return The first value.
     */
    public ByteString asByteString() {
        return asByteString(null);
    }

    /**
     * Returns the first value, or {@code defaultValue} if the attribute does
     * not contain any values.
     *
     * @param defaultValue
     *            The default value to return if the attribute is empty.
     * @return The first value.
     */
    public ByteString asByteString(final ByteString defaultValue) {
        return parseSingleValue(Functions.<ByteString> identityFunction(), defaultValue);
    }

    /**
     * Returns the first value decoded as a {@code Calendar} using the
     * generalized time syntax, or {@code null} if the attribute does not
     * contain any values.
     *
     * @return The first value decoded as a {@code Calendar}.
     */
    public Calendar asCalendar() {
        return asCalendar(null);
    }

    /**
     * Returns the first value decoded as an {@code Calendar} using the
     * generalized time syntax, or {@code defaultValue} if the attribute does
     * not contain any values.
     *
     * @param defaultValue
     *            The default value to return if the attribute is empty.
     * @return The first value decoded as an {@code Calendar}.
     */
    public Calendar asCalendar(final Calendar defaultValue) {
        return parseSingleValue(Functions.valueToCalendar(), defaultValue);
    }

    /**
     * Returns the first value decoded as a {@code DN} using the schema
     * associated with this parser, or {@code null} if the attribute does not
     * contain any values.
     *
     * @return The first value decoded as a {@code DN}.
     */
    public DN asDN() {
        return asDN(null);
    }

    /**
     * Returns the first value decoded as a {@code DN} using the schema
     * associated with this parser, or {@code defaultValue} if the attribute
     * does not contain any values.
     *
     * @param defaultValue
     *            The default value to return if the attribute is empty.
     * @return The first value decoded as a {@code DN}.
     */
    public DN asDN(final DN defaultValue) {
        return parseSingleValue(Functions.valueToDN(getSchema()), defaultValue);
    }

    /**
     * Returns the first value decoded as an {@code Integer}, or {@code null} if
     * the attribute does not contain any values.
     *
     * @return The first value decoded as an {@code Integer}.
     */
    public Integer asInteger() {
        return isEmpty(attribute) ? null : asInteger(0 /* ignored */);
    }

    /**
     * Returns the first value decoded as an {@code Integer}, or
     * {@code defaultValue} if the attribute does not contain any values.
     *
     * @param defaultValue
     *            The default value to return if the attribute is empty.
     * @return The first value decoded as an {@code Integer}.
     */
    public int asInteger(final int defaultValue) {
        return parseSingleValue(Functions.valueToInteger(), defaultValue);
    }

    /**
     * Returns the first value decoded as a {@code Long}, or {@code null} if the
     * attribute does not contain any values.
     *
     * @return The first value decoded as a {@code Long}.
     */
    public Long asLong() {
        return isEmpty(attribute) ? null : asLong(0L /* ignored */);
    }

    /**
     * Returns the first value decoded as a {@code Long}, or
     * {@code defaultValue} if the attribute does not contain any values.
     *
     * @param defaultValue
     *            The default value to return if the attribute is empty.
     * @return The first value decoded as a {@code Long}.
     */
    public long asLong(final long defaultValue) {
        return parseSingleValue(Functions.valueToLong(), defaultValue);
    }

    /**
     * Returns the values decoded as a set of {@code AttributeDescription}s
     * using the schema associated with this parser, or {@code defaultValues} if
     * the attribute does not contain any values.
     *
     * @param defaultValues
     *            The default values to return if the attribute is empty.
     * @return The values decoded as a set of {@code AttributeDescription}s.
     */
    public Set<AttributeDescription> asSetOfAttributeDescription(
            final AttributeDescription... defaultValues) {
        return asSetOfAttributeDescription(Arrays.asList(defaultValues));
    }

    /**
     * Returns the values decoded as a set of {@code AttributeDescription}s
     * using the schema associated with this parser, or {@code defaultValues} if
     * the attribute does not contain any values.
     *
     * @param defaultValues
     *            The default values to return if the attribute is empty.
     * @return The values decoded as a set of {@code AttributeDescription}s.
     */
    public Set<AttributeDescription> asSetOfAttributeDescription(
            final Collection<AttributeDescription> defaultValues) {
        return parseMultipleValues(Functions.valueToAttributeDescription(), defaultValues);
    }

    /**
     * Returns the values contained in the attribute encoded as base64, or
     * {@code defaultValues} if the attribute does not contain any values.
     *
     * @param defaultValues
     *            The default values to return if the attribute is empty.
     * @return The values contained in the attribute encoded as base64.
     */
    public Set<String> asSetOfBase64(final Collection<ByteString> defaultValues) {
        return parseMultipleValues(Functions.valueToString(), Collections2.transformedCollection(
                defaultValues, Functions.valueToBase64(), null));
    }

    /**
     * Returns the values contained in the attribute encoded as base64, or
     * {@code defaultValues} if the attribute does not contain any values.
     *
     * @param defaultValues
     *            The default values to return if the attribute is empty.
     * @return The values contained in the attribute encoded as base64.
     */
    public Set<String> asSetOfBase64(final String... defaultValues) {
        return asSetOfString(Arrays.asList(defaultValues));
    }

    /**
     * Returns the values decoded as a set of {@code Boolean}s, or
     * {@code defaultValues} if the attribute does not contain any values.
     *
     * @param defaultValues
     *            The default values to return if the attribute is empty.
     * @return The values decoded as a set of {@code Boolean}s.
     */
    public Set<Boolean> asSetOfBoolean(final Boolean... defaultValues) {
        return asSetOfBoolean(Arrays.asList(defaultValues));
    }

    /**
     * Returns the values decoded as a set of {@code Boolean}s, or
     * {@code defaultValues} if the attribute does not contain any values.
     *
     * @param defaultValues
     *            The default values to return if the attribute is empty.
     * @return The values decoded as a set of {@code Boolean}s.
     */
    public Set<Boolean> asSetOfBoolean(final Collection<Boolean> defaultValues) {
        return parseMultipleValues(Functions.valueToBoolean(), defaultValues);
    }

    /**
     * Returns the values contained in the attribute, or {@code defaultValues}
     * if the attribute does not contain any values.
     *
     * @param defaultValues
     *            The default values to return if the attribute is empty.
     * @return The values contained in the attribute.
     */
    public Set<ByteString> asSetOfByteString(final ByteString... defaultValues) {
        return asSetOfByteString(Arrays.asList(defaultValues));
    }

    /**
     * Returns the values contained in the attribute, or {@code defaultValues}
     * if the attribute does not contain any values.
     *
     * @param defaultValues
     *            The default values to return if the attribute is empty.
     * @return The values contained in the attribute.
     */
    public Set<ByteString> asSetOfByteString(final Collection<ByteString> defaultValues) {
        return parseMultipleValues(Functions.<ByteString> identityFunction(), defaultValues);
    }

    /**
     * Returns the values decoded as a set of {@code Calendar}s using the
     * generalized time syntax, or {@code defaultValues} if the attribute does
     * not contain any values.
     *
     * @param defaultValues
     *            The default values to return if the attribute is empty.
     * @return The values decoded as a set of {@code Calendar}s.
     */
    public Set<Calendar> asSetOfCalendar(final Calendar... defaultValues) {
        return asSetOfCalendar(Arrays.asList(defaultValues));
    }

    /**
     * Returns the values decoded as a set of {@code Calendar}s using the
     * generalized time syntax, or {@code defaultValues} if the attribute does
     * not contain any values.
     *
     * @param defaultValues
     *            The default values to return if the attribute is empty.
     * @return The values decoded as a set of {@code Calendar}s.
     */
    public Set<Calendar> asSetOfCalendar(final Collection<Calendar> defaultValues) {
        return parseMultipleValues(Functions.valueToCalendar(), defaultValues);
    }

    /**
     * Returns the values decoded as a set of {@code DN}s using the schema
     * associated with this parser, or {@code defaultValues} if the attribute
     * does not contain any values.
     *
     * @param defaultValues
     *            The default values to return if the attribute is empty.
     * @return The values decoded as a set of {@code DN}s.
     */
    public Set<DN> asSetOfDN(final Collection<DN> defaultValues) {
        return parseMultipleValues(Functions.valueToDN(), defaultValues);
    }

    /**
     * Returns the values decoded as a set of {@code DN}s using the schema
     * associated with this parser, or {@code defaultValues} if the attribute
     * does not contain any values.
     *
     * @param defaultValues
     *            The default values to return if the attribute is empty.
     * @return The values decoded as a set of {@code DN}s.
     */
    public Set<DN> asSetOfDN(final DN... defaultValues) {
        return asSetOfDN(Arrays.asList(defaultValues));
    }

    /**
     * Returns the values decoded as a set of {@code Integer}s, or
     * {@code defaultValues} if the attribute does not contain any values.
     *
     * @param defaultValues
     *            The default values to return if the attribute is empty.
     * @return The values decoded as a set of {@code Integer}s.
     */
    public Set<Integer> asSetOfInteger(final Collection<Integer> defaultValues) {
        return parseMultipleValues(Functions.valueToInteger(), defaultValues);
    }

    /**
     * Returns the values decoded as a set of {@code Integer}s, or
     * {@code defaultValues} if the attribute does not contain any values.
     *
     * @param defaultValues
     *            The default values to return if the attribute is empty.
     * @return The values decoded as a set of {@code Integer}s.
     */
    public Set<Integer> asSetOfInteger(final Integer... defaultValues) {
        return asSetOfInteger(Arrays.asList(defaultValues));
    }

    /**
     * Returns the values decoded as a set of {@code Long}s, or
     * {@code defaultValues} if the attribute does not contain any values.
     *
     * @param defaultValues
     *            The default values to return if the attribute is empty.
     * @return The values decoded as a set of {@code Long}s.
     */
    public Set<Long> asSetOfLong(final Collection<Long> defaultValues) {
        return parseMultipleValues(Functions.valueToLong(), defaultValues);
    }

    /**
     * Returns the values decoded as a set of {@code Long}s, or
     * {@code defaultValues} if the attribute does not contain any values.
     *
     * @param defaultValues
     *            The default values to return if the attribute is empty.
     * @return The values decoded as a set of {@code Long}s.
     */
    public Set<Long> asSetOfLong(final Long... defaultValues) {
        return asSetOfLong(Arrays.asList(defaultValues));
    }

    /**
     * Returns the values decoded as a set of {@code String}s, or
     * {@code defaultValues} if the attribute does not contain any values.
     *
     * @param defaultValues
     *            The default values to return if the attribute is empty.
     * @return The values decoded as a set of {@code String}s.
     */
    public Set<String> asSetOfString(final Collection<String> defaultValues) {
        return parseMultipleValues(Functions.valueToString(), defaultValues);
    }

    /**
     * Returns the values decoded as a set of {@code String}s, or
     * {@code defaultValues} if the attribute does not contain any values.
     *
     * @param defaultValues
     *            The default values to return if the attribute is empty.
     * @return The values decoded as a set of {@code String}s.
     */
    public Set<String> asSetOfString(final String... defaultValues) {
        return asSetOfString(Arrays.asList(defaultValues));
    }

    /**
     * Returns the first value decoded as a {@code String}, or {@code null} if
     * the attribute does not contain any values.
     *
     * @return The first value decoded as a {@code String}.
     */
    public String asString() {
        return asString(null);
    }

    /**
     * Returns the first value decoded as a {@code String}, or
     * {@code defaultValue} if the attribute does not contain any values.
     *
     * @param defaultValue
     *            The default value to return if the attribute is empty.
     * @return The first value decoded as a {@code String}.
     */
    public String asString(final String defaultValue) {
        return parseSingleValue(Functions.valueToString(), defaultValue);
    }

    /**
     * Throws a {@code NoSuchElementException} if the attribute referenced by
     * this parser is {@code null} or empty.
     *
     * @return A reference to this attribute parser.
     * @throws NoSuchElementException
     *             If the attribute referenced by this parser is {@code null} or
     *             empty.
     */
    public AttributeParser requireValue() {
        if (isEmpty(attribute)) {
            throw new NoSuchElementException();
        } else {
            return this;
        }
    }

    /**
     * Sets the {@code Schema} which will be used when parsing schema sensitive
     * values such as DNs and attribute descriptions.
     *
     * @param schema
     *            The {@code Schema} which will be used when parsing schema
     *            sensitive values.
     * @return This attribute parser.
     */
    public AttributeParser usingSchema(final Schema schema) {
        // Avoid modifying the null instance: a schema will not be needed
        // anyway.
        if (this != NULL_INSTANCE) {
            this.schema = schema;
        }
        return this;
    }

    private Schema getSchema() {
        return schema == null ? Schema.getDefaultSchema() : schema;
    }

    private <T> Set<T> parseMultipleValues(final Function<ByteString, T, ?> f,
            final Collection<? extends T> defaultValues) {
        if (!isEmpty(attribute)) {
            final LinkedHashSet<T> result = new LinkedHashSet<T>(attribute.size());
            for (final ByteString b : attribute) {
                result.add(f.apply(b, null));
            }
            return result;
        } else if (defaultValues != null) {
            return new LinkedHashSet<T>(defaultValues);
        } else {
            return new LinkedHashSet<T>(0);
        }
    }

    private <T> T parseSingleValue(final Function<ByteString, T, ?> f, final T defaultValue) {
        if (!isEmpty(attribute)) {
            return f.apply(attribute.firstValue(), null);
        } else {
            return defaultValue;
        }
    }
}
