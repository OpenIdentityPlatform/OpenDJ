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
 *      Copyright 2012-2015 ForgeRock AS.
 */
package org.forgerock.opendj.ldap;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.NoSuchElementException;
import java.util.Set;

import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.util.Function;
import org.forgerock.util.promise.NeverThrowsException;

import static com.forgerock.opendj.util.Collections2.*;

/**
 * A fluent API for parsing attributes as different types of object. An
 * attribute parser is obtained from an entry using the method
 * {@link Entry#parseAttribute} or from an attribute using
 * {@link Attribute#parse}.
 * <p>
 * Methods throw an {@code IllegalArgumentException} when a value cannot be
 * parsed (e.g. because its syntax is invalid). Methods which return a
 * {@code Set} always return a modifiable non-{@code null} result, even if the
 * attribute is {@code null} or empty.
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
 * @see Attribute#parse
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
        return attribute == null || attribute.isEmpty();
    }

    private final Attribute attribute;
    private Schema schema;

    private AttributeParser(final Attribute attribute) {
        this.attribute = attribute;
    }

    /**
     * Returns the first value decoded as a {@code T} using the provided
     * {@link Function}, or {@code null} if the attribute does not contain any
     * values.
     *
     * @param <T>
     *            The type of the value to be decoded.
     * @param f
     *            The function which should be used to decode the value.
     * @return The first value decoded as a {@code T}.
     */
    public <T> T as(final Function<ByteString, ? extends T, NeverThrowsException> f) {
        return as(f, null);
    }

    /**
     * Returns the first value decoded as a {@code T} using the provided
     * {@link Function}, or {@code defaultValue} if the attribute does not
     * contain any values.
     *
     * @param <T>
     *            The type of the value to be decoded.
     * @param f
     *            The function which should be used to decode the value.
     * @param defaultValue
     *            The default value to return if the attribute is empty.
     * @return The first value decoded as a {@code T}.
     */
    public <T> T as(final Function<ByteString, ? extends T, NeverThrowsException> f, final T defaultValue) {
        if (!isEmpty(attribute)) {
            return f.apply(attribute.firstValue());
        } else {
            return defaultValue;
        }
    }

    /**
     * Returns the first value decoded as an {@code AttributeDescription} using
     * the schema associated with this parser, or {@code null} if the attribute
     * does not contain any values.
     *
     * @return The first value decoded as an {@code AttributeDescription}.
     */
    public AttributeDescription asAttributeDescription() {
        return asAttributeDescription((AttributeDescription) null);
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
        return as(Functions.byteStringToAttributeDescription(getSchema()), defaultValue);
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
    public AttributeDescription asAttributeDescription(final String defaultValue) {
        return asAttributeDescription(AttributeDescription.valueOf(defaultValue, getSchema()));
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
        return as(Functions.byteStringToBoolean(), defaultValue);
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
        return as(Functions.<ByteString> identityFunction(), defaultValue);
    }

    /**
     * Returns the first value decoded as a {@code DN} using the schema
     * associated with this parser, or {@code null} if the attribute does not
     * contain any values.
     *
     * @return The first value decoded as a {@code DN}.
     */
    public DN asDN() {
        return asDN((DN) null);
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
        return as(Functions.byteStringToDN(getSchema()), defaultValue);
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
    public DN asDN(final String defaultValue) {
        return asDN(DN.valueOf(defaultValue, getSchema()));
    }

    /**
     * Returns the first value decoded as a {@code GeneralizedTime} using the
     * generalized time syntax, or {@code null} if the attribute does not
     * contain any values.
     *
     * @return The first value decoded as a {@code GeneralizedTime}.
     */
    public GeneralizedTime asGeneralizedTime() {
        return asGeneralizedTime(null);
    }

    /**
     * Returns the first value decoded as an {@code GeneralizedTime} using the
     * generalized time syntax, or {@code defaultValue} if the attribute does
     * not contain any values.
     *
     * @param defaultValue
     *            The default value to return if the attribute is empty.
     * @return The first value decoded as an {@code GeneralizedTime}.
     */
    public GeneralizedTime asGeneralizedTime(final GeneralizedTime defaultValue) {
        return as(Functions.byteStringToGeneralizedTime(), defaultValue);
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
        return as(Functions.byteStringToInteger(), defaultValue);
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
        return as(Functions.byteStringToLong(), defaultValue);
    }

    /**
     * Returns the values decoded as a set of {@code T}s using the provided
     * {@link Function}, or {@code defaultValues} if the attribute does not
     * contain any values.
     *
     * @param <T>
     *            The type of the values to be decoded.
     * @param f
     *            The function which should be used to decode values.
     * @param defaultValues
     *            The default values to return if the attribute is empty.
     * @return The values decoded as a set of {@code T}s.
     */
    public <T> Set<T> asSetOf(final Function<ByteString, ? extends T, NeverThrowsException> f,
            final Collection<? extends T> defaultValues) {
        if (!isEmpty(attribute)) {
            final LinkedHashSet<T> result = new LinkedHashSet<>(attribute.size());
            for (final ByteString b : attribute) {
                result.add(f.apply(b));
            }
            return result;
        } else if (defaultValues != null) {
            return new LinkedHashSet<>(defaultValues);
        } else {
            return new LinkedHashSet<>(0);
        }
    }

    /**
     * Returns the values decoded as a set of {@code T}s using the provided
     * {@link Function}, or {@code defaultValues} if the attribute does not
     * contain any values.
     *
     * @param <T>
     *            The type of the values to be decoded.
     * @param f
     *            The function which should be used to decode values.
     * @param defaultValues
     *            The default values to return if the attribute is empty.
     * @return The values decoded as a set of {@code T}s.
     */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public final <T> Set<T> asSetOf(final Function<ByteString, ? extends T, NeverThrowsException> f,
            final T... defaultValues) {
        return asSetOf(f, Arrays.asList(defaultValues));
    }

    /**
     * Returns the values decoded as a set of {@code AttributeDescription}s
     * using the schema associated with this parser, or an empty set if the
     * attribute does not contain any values.
     *
     * @return The values decoded as a set of {@code AttributeDescription}s.
     */
    public Set<AttributeDescription> asSetOfAttributeDescription() {
        return asSetOfAttributeDescription(Collections.<AttributeDescription> emptySet());
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
        return asSetOf(Functions.byteStringToAttributeDescription(), defaultValues);
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
    public Set<AttributeDescription> asSetOfAttributeDescription(final String... defaultValues) {
        return asSetOfAttributeDescription(transformedCollection(Arrays.asList(defaultValues),
                Functions.stringToAttributeDescription(getSchema()), null));
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
        return asSetOf(Functions.byteStringToBoolean(), defaultValues);
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
        return asSetOf(Functions.<ByteString> identityFunction(), defaultValues);
    }

    /**
     * Returns the values decoded as a set of {@code DN}s using the schema
     * associated with this parser, or an empty set if the attribute does not
     * contain any values.
     *
     * @return The values decoded as a set of {@code DN}s.
     */
    public Set<DN> asSetOfDN() {
        return asSetOfDN(Collections.<DN> emptySet());
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
        return asSetOf(Functions.byteStringToDN(), defaultValues);
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
     * Returns the values decoded as a set of {@code DN}s using the schema
     * associated with this parser, or {@code defaultValues} if the attribute
     * does not contain any values.
     *
     * @param defaultValues
     *            The default values to return if the attribute is empty.
     * @return The values decoded as a set of {@code DN}s.
     */
    public Set<DN> asSetOfDN(final String... defaultValues) {
        return asSetOfDN(transformedCollection(Arrays.asList(defaultValues), Functions
                .stringToDN(getSchema()), null));
    }

    /**
     * Returns the values decoded as a set of {@code GeneralizedTime}s using the
     * generalized time syntax, or {@code defaultValues} if the attribute does
     * not contain any values.
     *
     * @param defaultValues
     *            The default values to return if the attribute is empty.
     * @return The values decoded as a set of {@code GeneralizedTime}s.
     */
    public Set<GeneralizedTime> asSetOfGeneralizedTime(
            final Collection<GeneralizedTime> defaultValues) {
        return asSetOf(Functions.byteStringToGeneralizedTime(), defaultValues);
    }

    /**
     * Returns the values decoded as a set of {@code GeneralizedTime}s using the
     * generalized time syntax, or {@code defaultValues} if the attribute does
     * not contain any values.
     *
     * @param defaultValues
     *            The default values to return if the attribute is empty.
     * @return The values decoded as a set of {@code GeneralizedTime}s.
     */
    public Set<GeneralizedTime> asSetOfGeneralizedTime(final GeneralizedTime... defaultValues) {
        return asSetOfGeneralizedTime(Arrays.asList(defaultValues));
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
        return asSetOf(Functions.byteStringToInteger(), defaultValues);
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
        return asSetOf(Functions.byteStringToLong(), defaultValues);
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
        return asSetOf(Functions.byteStringToString(), defaultValues);
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
        return as(Functions.byteStringToString(), defaultValue);
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
}
