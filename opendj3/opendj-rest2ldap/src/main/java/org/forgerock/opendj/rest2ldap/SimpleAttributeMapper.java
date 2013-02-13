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
 * Copyright 2012-2013 ForgeRock AS.
 */
package org.forgerock.opendj.rest2ldap;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.forgerock.opendj.ldap.Functions.fixedFunction;
import static org.forgerock.opendj.rest2ldap.Utils.byteStringToJson;
import static org.forgerock.opendj.rest2ldap.Utils.jsonToAttribute;
import static org.forgerock.opendj.rest2ldap.Utils.jsonToByteString;
import static org.forgerock.opendj.rest2ldap.Utils.toFilter;
import static org.forgerock.opendj.rest2ldap.WritabilityPolicy.READ_ONLY;
import static org.forgerock.opendj.rest2ldap.WritabilityPolicy.READ_WRITE;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.forgerock.json.fluent.JsonPointer;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.resource.BadRequestException;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.ResultHandler;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.Function;
import org.forgerock.opendj.ldap.LinkedAttribute;

/**
 * An attribute mapper which provides a simple mapping from a JSON value to a
 * single LDAP attribute.
 */
public final class SimpleAttributeMapper extends AttributeMapper {
    private Function<ByteString, ?, Void> decoder = null;
    private Object defaultJSONValue = null;
    private Collection<Object> defaultJSONValues = Collections.emptySet();
    private ByteString defaultLDAPValue = null;
    private Function<Object, ByteString, Void> encoder = null;
    private boolean isIgnoreUpdates = true;
    private boolean isRequired = false;
    private boolean isSingleValued = false;
    private final AttributeDescription ldapAttributeName;
    private WritabilityPolicy writabilityPolicy = READ_WRITE;

    SimpleAttributeMapper(final AttributeDescription ldapAttributeName) {
        this.ldapAttributeName = ldapAttributeName;
    }

    /**
     * Sets the decoder which will be used for converting LDAP attribute values
     * to JSON values.
     *
     * @param f
     *            The function to use for decoding LDAP attribute values.
     * @return This attribute mapper.
     */
    public SimpleAttributeMapper decoder(final Function<ByteString, ?, Void> f) {
        this.decoder = f;
        return this;
    }

    /**
     * Sets the default JSON value which should be substituted when the LDAP
     * attribute is not found in the LDAP entry.
     *
     * @param defaultValue
     *            The default JSON value.
     * @return This attribute mapper.
     */
    public SimpleAttributeMapper defaultJSONValue(final Object defaultValue) {
        this.defaultJSONValue = defaultValue;
        this.defaultJSONValues = defaultValue != null ? singleton(defaultValue) : emptySet();
        return this;
    }

    /**
     * Sets the default LDAP value which should be substituted when the JSON
     * attribute is not found in the JSON value.
     *
     * @param defaultValue
     *            The default LDAP value.
     * @return This attribute mapper.
     */
    public SimpleAttributeMapper defaultLDAPValue(final Object defaultValue) {
        this.defaultLDAPValue = defaultValue != null ? ByteString.valueOf(defaultValue) : null;
        return this;
    }

    /**
     * Sets the encoder which will be used for converting JSON values to LDAP
     * attribute values.
     *
     * @param f
     *            The function to use for encoding LDAP attribute values.
     * @return This attribute mapper.
     */
    public SimpleAttributeMapper encoder(final Function<Object, ByteString, Void> f) {
        this.encoder = f;
        return this;
    }

    /**
     * Indicates whether or not an attempt to update the LDAP attribute should
     * be ignored when the update is incompatible with the writability policy.
     * The default is {@code true}.
     *
     * @param ignore
     *            {@code true} an attempt to update the LDAP attribute should be
     *            ignored.
     * @return This attribute mapper.
     */
    public SimpleAttributeMapper ignoreUpdates(final boolean ignore) {
        this.isIgnoreUpdates = ignore;
        return this;
    }

    /**
     * Indicates that the LDAP attribute is mandatory and must be provided
     * during create requests. The default is {@code false}.
     *
     * @param isRequired
     *            {@code true} if the LDAP attribute is mandatory and must be
     *            provided during create requests.
     * @return This attribute mapper.
     */
    public SimpleAttributeMapper required(final boolean isRequired) {
        this.isRequired = isRequired;
        return this;
    }

    /**
     * Forces a multi-valued LDAP attribute to be represented as a single-valued
     * JSON value, rather than an array of values. The default is {@code false}.
     *
     * @param isSingleValued
     *            {@code true} if the LDAP attribute should be treated as a
     *            single-valued attribute.
     * @return This attribute mapper.
     */
    public SimpleAttributeMapper singleValued(final boolean isSingleValued) {
        this.isSingleValued = isSingleValued;
        return this;
    }

    /**
     * Indicates whether or not the LDAP attribute supports updates. The default
     * is {@link WritabilityPolicy#READ_WRITE}.
     *
     * @param policy
     *            The writability policy.
     * @return This attribute mapper.
     */
    public SimpleAttributeMapper writability(final WritabilityPolicy policy) {
        this.writabilityPolicy = policy;
        return this;
    }

    @Override
    void getLDAPAttributes(final Context c, final JsonPointer jsonAttribute,
            final Set<String> ldapAttributes) {
        ldapAttributes.add(ldapAttributeName.toString());
    }

    @Override
    void getLDAPFilter(final Context c, final FilterType type, final JsonPointer jsonAttribute,
            final String operator, final Object valueAssertion, final ResultHandler<Filter> h) {
        if (jsonAttribute.isEmpty()) {
            h.handleResult(toFilter(c, type, ldapAttributeName.toString(), valueAssertion));
        } else {
            // This attribute mapper does not support partial filtering.
            h.handleResult(c.getConfig().falseFilter());
        }
    }

    @Override
    void toJSON(final Context c, final Entry e, final ResultHandler<JsonValue> h) {
        final Function<ByteString, ?, Void> f =
                decoder == null ? fixedFunction(byteStringToJson(), ldapAttributeName) : decoder;
        final Object value;
        if (isSingleValued || ldapAttributeName.getAttributeType().isSingleValue()) {
            value = e.parseAttribute(ldapAttributeName).as(f, defaultJSONValue);
        } else {
            value = e.parseAttribute(ldapAttributeName).asSetOf(f, defaultJSONValues);
        }
        h.handleResult(new JsonValue(value));
    }

    @Override
    void toLDAP(final Context c, final JsonValue v, final ResultHandler<List<Attribute>> h) {
        try {
            final List<Attribute> result;
            if (v == null || v.isNull()) {
                if (isRequired()) {
                    // FIXME: improve error message.
                    throw new BadRequestException("no value provided");
                } else if (defaultLDAPValue != null) {
                    result =
                            singletonList((Attribute) new LinkedAttribute(ldapAttributeName,
                                    defaultLDAPValue));
                } else {
                    result = emptyList();
                }
            } else if (v.isList() && isSingleValued()) {
                // FIXME: improve error message.
                throw new BadRequestException("expected single value, but got multiple values");
            } else if (isCreate()) {
                if (isIgnoreUpdates) {
                    result = emptyList();
                } else {
                    // FIXME: improve error message.
                    throw new BadRequestException("attempted to create a read-only value");
                }
            } else {
                final Object value = v.getObject();
                if (value != null) {
                    final Function<Object, ByteString, Void> f =
                            encoder != null ? encoder : fixedFunction(jsonToByteString(),
                                    ldapAttributeName);
                    result = singletonList(jsonToAttribute(value, ldapAttributeName, f));
                } else if (defaultLDAPValue != null) {
                    result =
                            singletonList((Attribute) new LinkedAttribute(ldapAttributeName,
                                    defaultLDAPValue));
                } else {
                    result = emptyList();
                }
            }
            h.handleResult(result);
        } catch (final ResourceException e) {
            h.handleError(e);
        } catch (final Exception e) {
            // FIXME: improve error message.
            h.handleError(new BadRequestException(e.getMessage()));
        }
    }

    private boolean isCreate() {
        return writabilityPolicy != READ_ONLY
                && ldapAttributeName.getAttributeType().isNoUserModification();
    }

    private boolean isRequired() {
        return isRequired && defaultJSONValue == null;
    }

    private boolean isSingleValued() {
        return isSingleValued || ldapAttributeName.getAttributeType().isSingleValue();
    }

}
