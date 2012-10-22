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

import static org.forgerock.opendj.rest2ldap.Utils.attributeToJson;
import static org.forgerock.opendj.rest2ldap.Utils.getAttributeName;
import static org.forgerock.opendj.rest2ldap.Utils.toLowerCase;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.forgerock.json.fluent.JsonPointer;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.resource.ResultHandler;
import org.forgerock.json.resource.ServerContext;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.Entry;

/**
 * An attribute mapper that directly maps a configurable selection of attributes
 * to and from LDAP without any transformation.
 */
public final class DefaultAttributeMapper implements AttributeMapper {

    private final Map<String, String> excludedAttributes = new LinkedHashMap<String, String>();
    // All user attributes by default.
    private final Map<String, String> includedAttributes = new LinkedHashMap<String, String>();

    /**
     * Creates a new default attribute mapper which will map all user attributes
     * to JSON by default.
     */
    public DefaultAttributeMapper() {
        // No implementation required.
    }

    /**
     * Excludes one or more LDAP attributes from this mapping.
     *
     * @param attributes
     *            The attributes to be excluded.
     * @return This attribute mapper.
     */
    public DefaultAttributeMapper excludeAttribute(final String... attributes) {
        for (final String attribute : attributes) {
            excludedAttributes.put(toLowerCase(attribute), attribute);
        }
        return this;
    }

    @Override
    public void getLDAPAttributes(final JsonPointer jsonAttribute, final Set<String> ldapAttributes) {
        switch (jsonAttribute.size()) {
        case 0:
            // Requested everything.
            if (!includedAttributes.isEmpty()) {
                ldapAttributes.addAll(includedAttributes.values());
            } else {
                // All user attributes.
                ldapAttributes.add("*");
            }
            break;
        default:
            final String name = jsonAttribute.get(0);
            if (isIncludedAttribute(name)) {
                ldapAttributes.add(name);
            }
            break;
        }
    }

    /**
     * Includes one or more LDAP attributes in this mapping.
     *
     * @param attributes
     *            The attributes to be included.
     * @return This attribute mapper.
     */
    public DefaultAttributeMapper includeAttribute(final String... attributes) {
        for (final String attribute : attributes) {
            includedAttributes.put(toLowerCase(attribute), attribute);
        }
        return this;
    }

    @Override
    public void toJSON(final ServerContext c, final Entry e,
            final ResultHandler<Map<String, Object>> h) {
        final Map<String, Object> result = new LinkedHashMap<String, Object>(e.getAttributeCount());
        for (final Attribute a : e.getAllAttributes()) {
            final String name = getAttributeName(a);
            if (isIncludedAttribute(name)) {
                result.put(name, attributeToJson(a));
            }
        }
        h.handleResult(result);
    }

    @Override
    public void toLDAP(final ServerContext c, final JsonValue v,
            final ResultHandler<List<Attribute>> h) {
        // TODO:
    }

    private boolean isIncludedAttribute(final String name) {
        final String lowerName = toLowerCase(name);

        // Ignore the requested attribute if it has been excluded.
        if (excludedAttributes.containsKey(lowerName)) {
            return false;
        }

        // Include all attributes by default.
        if (includedAttributes.isEmpty() || includedAttributes.containsKey(lowerName)) {
            return true;
        }

        return false;
    }
}
