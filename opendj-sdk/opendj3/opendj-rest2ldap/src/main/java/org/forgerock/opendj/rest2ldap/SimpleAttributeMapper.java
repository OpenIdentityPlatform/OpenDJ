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
 * Copyright 2012 ForgeRock AS.
 */
package org.forgerock.opendj.rest2ldap;

import static org.forgerock.opendj.rest2ldap.Utils.byteStringToJson;
import static org.forgerock.opendj.rest2ldap.Utils.toLowerCase;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.forgerock.json.fluent.JsonPointer;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.resource.ResultHandler;
import org.forgerock.json.resource.ServerContext;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.Function;
import org.forgerock.opendj.ldap.Functions;

/**
 * An attribute mapper which maps a single JSON attribute to a single LDAP
 * attribute.
 */
public class SimpleAttributeMapper implements AttributeMapper {

    private Function<ByteString, ?, Void> decoder = null;
    private Object defaultValue = null;
    private boolean forceSingleValued = false;

    // private boolean isReadOnly = false;
    private final String jsonAttributeName;
    private final String ldapAttributeName;
    private final String normalizedJsonAttributeName;

    /**
     * Creates a new simple attribute mapper which maps a single LDAP attribute
     * to an entry.
     *
     * @param attributeName
     *            The name of the simple JSON and LDAP attribute.
     */
    public SimpleAttributeMapper(final String attributeName) {
        this(attributeName, attributeName);
    }

    /**
     * Creates a new simple attribute mapper which maps a single LDAP attribute
     * to an entry.
     *
     * @param jsonAttributeName
     *            The name of the simple JSON attribute.
     * @param ldapAttributeName
     *            The name of the LDAP attribute.
     */
    public SimpleAttributeMapper(final String jsonAttributeName, final String ldapAttributeName) {
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
        this.defaultValue = defaultValue;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void getLDAPAttributes(final JsonPointer jsonAttribute, final Set<String> ldapAttributes) {
        if (attributeMatchesPointer(jsonAttribute)) {
            ldapAttributes.add(ldapAttributeName);
        }
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

    /**
     * {@inheritDoc}
     */
    @Override
    public void toJSON(final ServerContext c, final Entry e,
            final ResultHandler<Map<String, Object>> h) {
        final Attribute a = e.getAttribute(ldapAttributeName);
        if (a != null) {
            final Function<ByteString, ?, Void> f = decoder == null ? Functions.fixedFunction(
                    byteStringToJson(), a) : decoder;
            final Object value;
            if (forceSingleValued || a.getAttributeDescription().getAttributeType().isSingleValue()) {
                value = a.parse().as(f, defaultValue);
            } else {
                value = a.parse().asSetOf(f, defaultValue);
            }
            h.handleResult(Collections.singletonMap(jsonAttributeName, value));
        } else {
            h.handleResult(null);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void toLDAP(final ServerContext c, final JsonValue v,
            final ResultHandler<List<Attribute>> h) {
        // TODO Auto-generated method stub

    }

    private boolean attributeMatchesPointer(final JsonPointer resourceAttribute) {
        return resourceAttribute.isEmpty()
                || toLowerCase(resourceAttribute.get(0)).equals(normalizedJsonAttributeName);
    }

}
