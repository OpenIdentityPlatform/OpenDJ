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
import static java.util.Collections.singletonMap;
import static org.forgerock.opendj.ldap.Functions.fixedFunction;
import static org.forgerock.opendj.rest2ldap.Utils.byteStringToJson;
import static org.forgerock.opendj.rest2ldap.Utils.jsonToAttribute;
import static org.forgerock.opendj.rest2ldap.Utils.jsonToByteString;
import static org.forgerock.opendj.rest2ldap.Utils.toFilter;
import static org.forgerock.opendj.rest2ldap.Utils.toLowerCase;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.forgerock.json.fluent.JsonPointer;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.resource.BadRequestException;
import org.forgerock.json.resource.ResultHandler;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.Function;
import org.forgerock.opendj.ldap.LinkedAttribute;

/**
 * An attribute mapper which maps a single JSON attribute to a single LDAP
 * attribute.
 */
public final class SimpleAttributeMapper extends AttributeMapper {

    private Function<ByteString, ?, Void> decoder = null;
    private Object defaultJSONValue = null;
    private Collection<Object> defaultJSONValues = Collections.emptySet();
    private ByteString defaultLDAPValue = null;
    private Function<Object, ByteString, Void> encoder = null;
    private boolean forceSingleValued = false;

    // private boolean isReadOnly = false;
    private final String jsonAttributeName;
    private final AttributeDescription ldapAttributeName;
    private final String normalizedJsonAttributeName;

    /**
     * Creates a new simple attribute mapper which maps a single LDAP attribute
     * to an entry.
     *
     * @param jsonAttributeName
     *            The name of the simple JSON attribute.
     * @param ldapAttributeName
     *            The name of the LDAP attribute.
     */
    SimpleAttributeMapper(final String jsonAttributeName,
            final AttributeDescription ldapAttributeName) {
        this.jsonAttributeName = jsonAttributeName;
        this.ldapAttributeName = ldapAttributeName;
        this.normalizedJsonAttributeName = toLowerCase(jsonAttributeName);
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
     * Prevents the LDAP attribute from being updated.
     *
     * @param readOnly
     *            {@code true} if the LDAP attribute is read-only.
     * @return This attribute mapper.
     */
    public SimpleAttributeMapper readOnly(final boolean readOnly) {
        // TODO: enforcement policy: ignore, warn, or reject.
        // this.isReadOnly = readOnly;
        return this;
    }

    /**
     * Forces a multi-valued LDAP attribute to be represented as a single-valued
     * JSON value, rather than an array of values.
     *
     * @param singleValued
     *            {@code true} if the LDAP attribute should be treated as a
     *            single-valued attribute.
     * @return This attribute mapper.
     */
    public SimpleAttributeMapper singleValued(final boolean singleValued) {
        this.forceSingleValued = singleValued;
        return this;
    }

    @Override
    void getLDAPAttributes(final Context c, final JsonPointer jsonAttribute,
            final Set<String> ldapAttributes) {
        if (jsonAttribute.isEmpty() || matches(jsonAttribute)) {
            ldapAttributes.add(ldapAttributeName.toString());
        }
    }

    @Override
    void getLDAPFilter(final Context c, final FilterType type, final JsonPointer jsonAttribute,
            final String operator, final Object valueAssertion, final ResultHandler<Filter> h) {
        if (matches(jsonAttribute)) {
            h.handleResult(toFilter(c, type, ldapAttributeName.toString(), valueAssertion));
        } else {
            // This attribute mapper cannot handle the provided filter component.
            h.handleResult(null);
        }
    }

    @Override
    void toJSON(final Context c, final Entry e, final ResultHandler<Map<String, Object>> h) {
        final Function<ByteString, ?, Void> f =
                decoder == null ? fixedFunction(byteStringToJson(), ldapAttributeName) : decoder;
        final Object value;
        if (forceSingleValued || ldapAttributeName.getAttributeType().isSingleValue()) {
            value = e.parseAttribute(ldapAttributeName).as(f, defaultJSONValue);
        } else {
            value = e.parseAttribute(ldapAttributeName).asSetOf(f, defaultJSONValues);
        }
        h.handleResult(singletonMap(jsonAttributeName, value));
    }

    @Override
    void toLDAP(final Context c, final JsonValue v, final ResultHandler<List<Attribute>> h) {
        if (v.isMap()) {
            final Object value = v.get(jsonAttributeName).getObject();
            try {
                final List<Attribute> result;
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
                h.handleResult(result);
            } catch (final Exception e) {
                // FIXME: improve error message.
                h.handleError(new BadRequestException("The field " + jsonAttributeName
                        + " is invalid"));
                return;
            }
        } else {
            h.handleResult(Collections.<Attribute> emptyList());
        }
    }

    private boolean matches(final JsonPointer jsonAttribute) {
        return !jsonAttribute.isEmpty()
                && toLowerCase(jsonAttribute.get(0)).equals(normalizedJsonAttributeName);
    }

}
