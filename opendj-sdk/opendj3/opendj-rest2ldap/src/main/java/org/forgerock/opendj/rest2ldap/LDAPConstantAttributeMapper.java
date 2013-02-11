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

import static java.util.Collections.singletonList;
import static org.forgerock.opendj.ldap.Attributes.singletonAttribute;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.forgerock.json.fluent.JsonPointer;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.resource.ResultHandler;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.LinkedAttribute;

/**
 * An attribute mapper which maps a single LDAP attribute to a fixed value.
 */
final class LDAPConstantAttributeMapper extends AttributeMapper {
    private final List<Attribute> attributes;

    LDAPConstantAttributeMapper(final AttributeDescription attributeName,
            final Object... attributeValues) {
        if (attributeValues.length == 1) {
            attributes = singletonList(singletonAttribute(attributeName, attributeValues[0]));
        } else {
            Attribute attribute = new LinkedAttribute(attributeName);
            for (Object o : attributeValues) {
                attribute.add(o);
            }
            attributes = singletonList(attribute);
        }
    }

    @Override
    void getLDAPAttributes(final Context c, final JsonPointer jsonAttribute,
            final Set<String> ldapAttributes) {
        // Nothing to do.
    }

    @Override
    void getLDAPFilter(final Context c, final FilterType type, final JsonPointer jsonAttribute,
            final String operator, final Object valueAssertion, final ResultHandler<Filter> h) {
        // This attribute mapper cannot handle the provided filter component.
        h.handleResult(null);
    }

    @Override
    void toJSON(final Context c, final Entry e, final ResultHandler<Map<String, Object>> h) {
        h.handleResult(Collections.<String, Object> emptyMap());
    }

    @Override
    void toLDAP(final Context c, final JsonValue v, final ResultHandler<List<Attribute>> h) {
        h.handleResult(attributes);
    }

}
