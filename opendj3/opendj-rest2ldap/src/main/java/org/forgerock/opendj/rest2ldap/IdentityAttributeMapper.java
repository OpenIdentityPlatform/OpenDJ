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

import static org.forgerock.opendj.rest2ldap.Utils.getAttributeName;
import static org.forgerock.opendj.rest2ldap.Utils.toLowerCase;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.forgerock.json.fluent.JsonPointer;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.ResultHandler;
import org.forgerock.resource.provider.Context;

/**
 *
 */
public final class IdentityAttributeMapper implements AttributeMapper {

    // All user attributes by default.
    private final Map<String, String> includedAttributes = new LinkedHashMap<String, String>();
    private final Map<String, String> excludedAttributes = new LinkedHashMap<String, String>();

    public IdentityAttributeMapper() {
        // No implementation required.
    }

    public IdentityAttributeMapper includeAttribute(String... attributes) {
        for (String attribute : attributes) {
            includedAttributes.put(toLowerCase(attribute), attribute);
        }
        return this;
    }

    public IdentityAttributeMapper excludeAttribute(String... attributes) {
        for (String attribute : attributes) {
            excludedAttributes.put(toLowerCase(attribute), attribute);
        }
        return this;
    }

    public Collection<String> getAllLDAPAttributes() {
        if (!includedAttributes.isEmpty()) {
            return includedAttributes.values();
        } else {
            // All user attributes.
            return Collections.emptySet();
        }
    }

    public void getLDAPAttributesFor(JsonPointer resourceAttribute, Set<String> ldapAttributes) {
        String name = resourceAttribute.leaf();
        if (name != null) {
            if (isIncludedAttribute(name)) {
                ldapAttributes.add(name);
            } else {
                // FIXME: log something or return a ResourceException?
            }
        }
    }

    public void toJson(Context c, Entry e, ResultHandler<Map<String, Object>> h) {
        Map<String, Object> result = new LinkedHashMap<String, Object>(e.getAttributeCount());
        for (Attribute a : e.getAllAttributes()) {
            String name = getAttributeName(a);
            if (isIncludedAttribute(name)) {
                result.put(name, Utils.attributeToJson(a));
            }
        }
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

    public void toLDAP(Context c, JsonValue v, ResultHandler<List<Attribute>> h) {
        // TODO:
    }
}
