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
 * Copyright 2012-2016 ForgeRock AS.
 */
package org.forgerock.opendj.ldap;

import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.util.Function;

import static com.forgerock.opendj.ldap.CoreMessages.ERR_ATTRIBUTE_PARSER_MISSING_ATTRIBUTE;
import static com.forgerock.opendj.util.Collections2.*;
import static java.util.Arrays.asList;
import static org.forgerock.opendj.ldap.Functions.*;

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
        return new AttributeParser(attribute);
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
     *         The type of the value to be decoded.
     * @param <E>
     *         The type of exception thrown by the function.
     * @param f
     *         The function which should be used to decode the value.
     * @return The first value decoded as a {@code T}.
     * @throws E
     *         If an error occurred when parsing the attribute.
     */
    public <T, E extends Exception> T as(final Function<ByteString, ? extends T, E> f) throws E {
        return as(f, null);
    }

    /**
     * Returns the first value decoded as a {@code T} using the provided
     * {@link Function}, or {@code defaultValue} if the attribute does not
     * contain any values.
     *
     * @param <T>
     *            The type of the value to be decoded.
     * @param <E>
     *            The type of exception thrown by the function.
     * @param f
     *            The function which should be used to decode the value.
     * @param defaultValue
     *            The default value to return if the attribute is empty.
     * @return The first value decoded as a {@code T}.
     * @throws E
     *         If an error occurred when parsing the attribute.
     */
    public <T, E extends Exception> T as(final Function<ByteString, ? extends T, E> f, final T defaultValue) throws E {
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
        return as(byteStringToAttributeDescription(getSchema()), defaultValue);
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
        return as(byteStringToBoolean(), defaultValue);
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
     * Returns the first value decoded as a {@code X509Certificate}, or {@code null} if the attribute does not
     * contain any values.
     *
     * @return The first value decoded as a {@code X509Certificate}.
     */
    public X509Certificate asCertificate() {
        return asCertificate(null);
    }

    /**
     * Returns the first value decoded as a {@code X509Certificate}, or {@code defaultValue} if the attribute
     * does not contain any values.
     *
     * @param defaultValue
     *            The default value to return if the attribute is empty.
     * @return The first value decoded as a {@code X509Certificate}.
     */
    public X509Certificate asCertificate(final X509Certificate defaultValue) {
        return as(byteStringToCertificate(), defaultValue);
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
        return as(byteStringToDN(getSchema()), defaultValue);
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
        return as(byteStringToGeneralizedTime(), defaultValue);
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
        return as(byteStringToInteger(), defaultValue);
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
        return as(byteStringToLong(), defaultValue);
    }

    /**
     * Returns the values decoded as a set of {@code T}s using the provided
     * {@link Function}, or {@code defaultValues} if the attribute does not
     * contain any values.
     *
     * @param <T>
     *            The type of the values to be decoded.
     * @param <E>
     *            The type of exception thrown by the function.
     * @param f
     *            The function which should be used to decode values.
     * @param defaultValues
     *            The default values to return if the attribute is empty.
     * @return The values decoded as a set of {@code T}s.
     * @throws E
     *         If an error occurred when parsing the attribute.
     */
    public <T, E extends Exception> Set<T> asSetOf(final Function<ByteString, ? extends T, E> f,
            final Collection<? extends T> defaultValues) throws E {
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
     * @param <E>
     *            The type of exception thrown by the function.
     * @param f
     *            The function which should be used to decode values.
     * @param defaultValues
     *            The default values to return if the attribute is empty.
     * @return The values decoded as a set of {@code T}s.
     * @throws E
     *         If an error occurred when parsing the attribute.
     */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public final <T, E extends Exception> Set<T> asSetOf(final Function<ByteString, ? extends T, E> f,
            final T... defaultValues) throws E {
        return asSetOf(f, asList(defaultValues));
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
        return asSetOfAttributeDescription(asList(defaultValues));
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
    public Set<AttributeDescription> asSetOfAttributeDescription(final Collection<AttributeDescription> defaultValues) {
        return asSetOf(byteStringToAttributeDescription(), defaultValues);
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
        return asSetOfAttributeDescription(transformedCollection(asList(defaultValues),
                                                                 stringToAttributeDescription(getSchema()), null));
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
        return asSetOfBoolean(asList(defaultValues));
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
        return asSetOf(byteStringToBoolean(), defaultValues);
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
        return asSetOfByteString(asList(defaultValues));
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
     * Returns the values decoded as a set of {@code X509Certificate}s, or an empty set if the attribute does not
     * contain any values.
     *
     * @return The values decoded as a set of {@code X509Certificate}s.
     */
    public Set<X509Certificate> asSetOfCertificate() {
        return asSetOf(byteStringToCertificate());
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
        return asSetOf(byteStringToDN(), defaultValues);
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
        return asSetOfDN(asList(defaultValues));
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
        return asSetOfDN(transformedCollection(asList(defaultValues), stringToDN(getSchema()), null));
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
    public Set<GeneralizedTime> asSetOfGeneralizedTime(final Collection<GeneralizedTime> defaultValues) {
        return asSetOf(byteStringToGeneralizedTime(), defaultValues);
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
        return asSetOfGeneralizedTime(asList(defaultValues));
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
        return asSetOf(byteStringToInteger(), defaultValues);
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
        return asSetOfInteger(asList(defaultValues));
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
        return asSetOf(byteStringToLong(), defaultValues);
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
        return asSetOfLong(asList(defaultValues));
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
        return asSetOf(byteStringToString(), defaultValues);
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
        return asSetOfString(asList(defaultValues));
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
        return as(byteStringToString(), defaultValue);
    }

    /**
     * Throws a {@code LocalizedIllegalArgumentException} if the attribute referenced by this parser is {@code null} or
     * empty.
     *
     * @return A reference to this attribute parser.
     * @throws LocalizedIllegalArgumentException
     *         If the attribute referenced by this parser is {@code null} or empty.
     */
    public AttributeParser requireValue() {
        if (isEmpty(attribute)) {
            final String attributeName = attribute.getAttributeDescriptionAsString();
            throw new LocalizedIllegalArgumentException(ERR_ATTRIBUTE_PARSER_MISSING_ATTRIBUTE.get(attributeName));
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
        this.schema = schema;
        return this;
    }

    private Schema getSchema() {
        return schema == null ? Schema.getDefaultSchema() : schema;
    }
}
