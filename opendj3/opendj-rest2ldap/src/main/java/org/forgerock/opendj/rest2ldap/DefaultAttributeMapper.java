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

import static org.forgerock.opendj.rest2ldap.Utils.attributeToJson;
import static org.forgerock.opendj.rest2ldap.Utils.getAttributeName;
import static org.forgerock.opendj.rest2ldap.Utils.toLowerCase;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.forgerock.json.fluent.JsonPointer;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.resource.provider.Context;

/**
 *
 */
public final class DefaultAttributeMapper implements AttributeMapper {

    // All user attributes by default.
    private final Map<String, String> includedAttributes = new LinkedHashMap<String, String>();
    private final Map<String, String> excludedAttributes = new LinkedHashMap<String, String>();

    public DefaultAttributeMapper() {
        // No implementation required.
    }

    public DefaultAttributeMapper includeAttribute(String... attributes) {
        for (String attribute : attributes) {
            includedAttributes.put(toLowerCase(attribute), attribute);
        }
        return this;
    }

    public DefaultAttributeMapper excludeAttribute(String... attributes) {
        for (String attribute : attributes) {
            excludedAttributes.put(toLowerCase(attribute), attribute);
        }
        return this;
    }

    public void getLDAPAttributes(Set<String> ldapAttributes) {
        if (!includedAttributes.isEmpty()) {
            ldapAttributes.addAll(includedAttributes.values());
        } else {
            // All user attributes.
            ldapAttributes.add("*");
        }
    }

    public void getLDAPAttributes(JsonPointer jsonAttribute, Set<String> ldapAttributes) {
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
            String name = jsonAttribute.get(0);
            if (isIncludedAttribute(name)) {
                ldapAttributes.add(name);
            }
            break;
        }
    }

    public void toJson(Context c, Entry e, AttributeMapperCompletionHandler<Map<String, Object>> h) {
        Map<String, Object> result = new LinkedHashMap<String, Object>(e.getAttributeCount());
        for (Attribute a : e.getAllAttributes()) {
            String name = getAttributeName(a);
            if (isIncludedAttribute(name)) {
                result.put(name, attributeToJson(a));
            }
        }
        h.onSuccess(result);
    }

    private boolean isIncludedAttribute(String name) {
        String lowerName = toLowerCase(name);

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

    public void toLDAP(Context c, JsonValue v, AttributeMapperCompletionHandler<List<Attribute>> h) {
        // TODO:
    }
}
