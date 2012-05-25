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

import static org.forgerock.opendj.rest2ldap.Utils.toLowerCase;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.forgerock.json.fluent.JsonPointer;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.resource.exception.ResourceException;
import org.forgerock.resource.provider.Context;

/**
 *
 */
public class ComplexAttributeMapper implements AttributeMapper {

    private final String normalizedJsonAttributeName;
    private final String jsonAttributeName;
    private final AttributeMapper mapper;

    /**
     * Creates a new complex attribute mapper which will wrap the results of the
     * provided mapper as a complex JSON object.
     *
     * @param jsonAttributeName
     *            The name of the complex attribute.
     * @param mapper
     *            The mapper which should be used to provide the contents of the
     *            complex attribute.
     */
    public ComplexAttributeMapper(String jsonAttributeName, AttributeMapper mapper) {
        this.jsonAttributeName = jsonAttributeName;
        this.mapper = mapper;
        this.normalizedJsonAttributeName = toLowerCase(jsonAttributeName);
    }

    /**
     * {@inheritDoc}
     */
    public void getLDAPAttributes(JsonPointer jsonAttribute, Set<String> ldapAttributes) {
        if (attributeMatchesPointer(jsonAttribute)) {
            JsonPointer relativePointer = jsonAttribute.relativePointer();
            mapper.getLDAPAttributes(relativePointer, ldapAttributes);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void toJson(Context c, Entry e,
            final AttributeMapperCompletionHandler<Map<String, Object>> h) {
        AttributeMapperCompletionHandler<Map<String, Object>> wrapper =
                new AttributeMapperCompletionHandler<Map<String, Object>>() {

                    public void onSuccess(Map<String, Object> result) {
                        Map<String, Object> complexResult =
                                Collections.singletonMap(jsonAttributeName, (Object) result);
                        h.onSuccess(complexResult);
                    }

                    public void onFailure(ResourceException e) {
                        h.onFailure(e);
                    }
                };
        mapper.toJson(c, e, wrapper);
    }

    /**
     * {@inheritDoc}
     */
    public void toLDAP(Context c, JsonValue v, AttributeMapperCompletionHandler<List<Attribute>> h) {
        // TODO Auto-generated method stub

    }

    private boolean attributeMatchesPointer(JsonPointer resourceAttribute) {
        return resourceAttribute.isEmpty()
                || toLowerCase(resourceAttribute.get(0)).equals(normalizedJsonAttributeName);
    }

}
