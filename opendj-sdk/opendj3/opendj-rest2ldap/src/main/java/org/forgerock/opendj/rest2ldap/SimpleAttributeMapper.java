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
 * information: "Portions Copyrighted [year] [name of copyright owner]".
 *
 * Copyright 2012 ForgeRock AS. All rights reserved.
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
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.Function;
import org.forgerock.opendj.ldap.Functions;
import org.forgerock.resource.provider.Context;

/**
 *
 */
public class SimpleAttributeMapper implements AttributeMapper {

    private final String ldapAttributeName;
    private final String jsonAttributeName;
    private final String normalizedJsonAttributeName;

    private boolean forceSingleValued = false;
    private Object defaultValue = null;
    private boolean isReadOnly = false;
    private Function<ByteString, ?, Void> decoder = null;

    /**
     * Creates a new simple attribute mapper which maps a single LDAP attribute
     * to an entry.
     *
     * @param attributeName
     *            The name of the simple JSON and LDAP attribute.
     */
    public SimpleAttributeMapper(String attributeName) {
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
    public SimpleAttributeMapper(String jsonAttributeName, String ldapAttributeName) {
        this.jsonAttributeName = jsonAttributeName;
        this.ldapAttributeName = ldapAttributeName;
        this.normalizedJsonAttributeName = toLowerCase(jsonAttributeName);
    }

    public SimpleAttributeMapper withDefaultValue(Object defaultValue) {
        this.defaultValue = defaultValue;
        return this;
    }

    public SimpleAttributeMapper isReadOnly(boolean readOnly) {
        this.isReadOnly = readOnly;
        return this;
    }

    public SimpleAttributeMapper forceSingleValued(boolean singleValued) {
        this.forceSingleValued = singleValued;
        return this;
    }

    public SimpleAttributeMapper withDecoder(Function<ByteString, ?, Void> f) {
        this.decoder = f;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public void getLDAPAttributes(JsonPointer jsonAttribute, Set<String> ldapAttributes) {
        if (attributeMatchesPointer(jsonAttribute)) {
            ldapAttributes.add(ldapAttributeName);
        }
    }

    private boolean attributeMatchesPointer(JsonPointer resourceAttribute) {
        return resourceAttribute.isEmpty()
                || toLowerCase(resourceAttribute.get(0)).equals(normalizedJsonAttributeName);
    }

    /**
     * {@inheritDoc}
     */
    public void toJson(Context c, Entry e,
            final AttributeMapperCompletionHandler<Map<String, Object>> h) {
        Attribute a = e.getAttribute(ldapAttributeName);
        if (a != null) {
            Function<ByteString, ?, Void> f =
                    decoder == null ? Functions.fixedFunction(byteStringToJson(), a) : decoder;
            final Object value;
            if (forceSingleValued || a.getAttributeDescription().getAttributeType().isSingleValue()) {
                value = a.parse().as(f, defaultValue);
            } else {
                value = a.parse().asSetOf(f, defaultValue);
            }
            Map<String, Object> result = Collections.singletonMap(jsonAttributeName, value);
            h.onSuccess(result);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void toLDAP(Context c, JsonValue v, AttributeMapperCompletionHandler<List<Attribute>> h) {
        // TODO Auto-generated method stub

    }

}
