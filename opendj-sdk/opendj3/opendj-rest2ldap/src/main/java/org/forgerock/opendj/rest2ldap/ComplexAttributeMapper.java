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

import static org.forgerock.opendj.rest2ldap.Utils.toLowerCase;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.forgerock.json.fluent.JsonPointer;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.ResultHandler;
import org.forgerock.json.resource.ServerContext;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.Entry;

/**
 * An attribute mapper which maps a single JSON attribute to the result of
 * another attribute mapper.
 */
public class ComplexAttributeMapper implements AttributeMapper {

    private final String jsonAttributeName;
    private final AttributeMapper mapper;
    private final String normalizedJsonAttributeName;

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
    public ComplexAttributeMapper(final String jsonAttributeName, final AttributeMapper mapper) {
        this.jsonAttributeName = jsonAttributeName;
        this.mapper = mapper;
        this.normalizedJsonAttributeName = toLowerCase(jsonAttributeName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void getLDAPAttributes(final JsonPointer jsonAttribute, final Set<String> ldapAttributes) {
        if (attributeMatchesPointer(jsonAttribute)) {
            final JsonPointer relativePointer = jsonAttribute.relativePointer();
            mapper.getLDAPAttributes(relativePointer, ldapAttributes);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void toJSON(final ServerContext c, final Entry e,
            final ResultHandler<Map<String, Object>> h) {
        final ResultHandler<Map<String, Object>> wrapper = new ResultHandler<Map<String, Object>>() {

            @Override
            public void handleError(final ResourceException e) {
                h.handleError(e);
            }

            @Override
            public void handleResult(final Map<String, Object> result) {
                final Map<String, Object> complexResult = Collections.singletonMap(
                        jsonAttributeName, (Object) result);
                h.handleResult(complexResult);
            }
        };
        mapper.toJSON(c, e, wrapper);
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
