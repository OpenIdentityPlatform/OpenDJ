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

import static org.forgerock.opendj.ldap.Filter.alwaysFalse;
import static org.forgerock.opendj.rest2ldap.Utils.accumulate;
import static org.forgerock.opendj.rest2ldap.Utils.toLowerCase;
import static org.forgerock.opendj.rest2ldap.Utils.transform;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.forgerock.json.fluent.JsonPointer;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.resource.BadRequestException;
import org.forgerock.json.resource.ResultHandler;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.Function;

/**
 * An attribute mapper which maps JSON objects to LDAP attributes.
 */
public final class ObjectAttributeMapper extends AttributeMapper {

    private static final class Mapping {
        private final AttributeMapper mapper;
        private final String name;

        private Mapping(final String name, final AttributeMapper mapper) {
            this.name = name;
            this.mapper = mapper;
        }
    }

    private final Map<String, Mapping> mappings = new LinkedHashMap<String, Mapping>();

    ObjectAttributeMapper() {
        // Nothing to do.
    }

    /**
     * Creates a mapping for an attribute contained in the JSON object.
     *
     * @param name
     *            The name of the JSON attribute to be mapped.
     * @param mapper
     *            The attribute mapper responsible for mapping the JSON
     *            attribute to LDAP attribute(s).
     * @return A reference to this attribute mapper.
     */
    public ObjectAttributeMapper attribute(final String name, final AttributeMapper mapper) {
        mappings.put(toLowerCase(name), new Mapping(name, mapper));
        return this;
    }

    @Override
    void getLDAPAttributes(final Context c, final JsonPointer jsonAttribute,
            final Set<String> ldapAttributes) {
        if (jsonAttribute.isEmpty()) {
            // Request all subordinate mappings.
            for (final Mapping mapping : mappings.values()) {
                mapping.mapper.getLDAPAttributes(c, jsonAttribute, ldapAttributes);
            }
        } else {
            // Request single subordinate mapping.
            final Mapping mapping = getMapping(jsonAttribute);
            if (mapping != null) {
                final JsonPointer relativePointer = jsonAttribute.relativePointer();
                mapping.mapper.getLDAPAttributes(c, relativePointer, ldapAttributes);
            }
        }
    }

    @Override
    void getLDAPFilter(final Context c, final FilterType type, final JsonPointer jsonAttribute,
            final String operator, final Object valueAssertion, final ResultHandler<Filter> h) {
        final Mapping mapping = getMapping(jsonAttribute);
        if (mapping != null) {
            final JsonPointer relativePointer = jsonAttribute.relativePointer();
            mapping.mapper.getLDAPFilter(c, type, relativePointer, operator, valueAssertion, h);
        } else {
            // Either the filter targeted the entire object (i.e. it was "/"), or it targeted
            // an unrecognized attribute within the object. Either way, the filter will
            // never match.
            h.handleResult(alwaysFalse());
        }
    }

    boolean isEmpty() {
        return mappings.isEmpty();
    }

    @Override
    void toJSON(final Context c, final Entry e, final ResultHandler<JsonValue> h) {
        // Use an accumulator which will aggregate the results from the subordinate mappers into
        // a single list. On completion, the accumulator combines the results into a single JSON
        // map object.
        final ResultHandler<Map.Entry<String, JsonValue>> handler =
                accumulate(mappings.size(), transform(
                        new Function<List<Map.Entry<String, JsonValue>>, JsonValue, Void>() {
                            @Override
                            public JsonValue apply(final List<Map.Entry<String, JsonValue>> value,
                                    final Void p) {
                                if (value.isEmpty()) {
                                    // No subordinate attributes, so omit the entire JSON object
                                    // from the resource.
                                    return null;
                                } else {
                                    // Combine the sub-attributes into a single JSON object.
                                    final Map<String, Object> result =
                                            new LinkedHashMap<String, Object>(value.size());
                                    for (final Map.Entry<String, JsonValue> e : value) {
                                        result.put(e.getKey(), e.getValue().getObject());
                                    }
                                    return new JsonValue(result);
                                }
                            }
                        }, h));

        for (final Mapping mapping : mappings.values()) {
            mapping.mapper.toJSON(c, e, transform(
                    new Function<JsonValue, Map.Entry<String, JsonValue>, Void>() {
                        @Override
                        public Map.Entry<String, JsonValue> apply(final JsonValue value,
                                final Void p) {
                            return new SimpleImmutableEntry<String, JsonValue>(mapping.name, value);
                        }
                    }, handler));
        }
    }

    @Override
    void toLDAP(final Context c, final JsonValue v, final ResultHandler<List<Attribute>> h) {
        // Fail immediately if the JSON value has the wrong type or contains unknown attributes.
        final Map<String, Mapping> missingMappings = new LinkedHashMap<String, Mapping>(mappings);
        if (v != null && !v.isNull()) {
            if (v.isMap()) {
                for (final String attribute : v.asMap().keySet()) {
                    if (missingMappings.remove(toLowerCase(attribute)) == null) {
                        h.handleError(new BadRequestException("unrecognized attribute '"
                                + attribute + "'"));
                        return;
                    }
                }
            } else {
                h.handleError(new BadRequestException("JSON object expected"));
                return;
            }
        }

        // Accumulate the results of the subordinate mappings.
        final ResultHandler<List<Attribute>> handler =
                accumulate(mappings.size(), transform(
                        new Function<List<List<Attribute>>, List<Attribute>, Void>() {
                            @Override
                            public List<Attribute> apply(final List<List<Attribute>> value,
                                    final Void p) {
                                switch (value.size()) {
                                case 0:
                                    return Collections.emptyList();
                                case 1:
                                    return value.get(0) != null ? value.get(0) : Collections
                                            .<Attribute> emptyList();
                                default:
                                    final List<Attribute> attributes =
                                            new ArrayList<Attribute>(value.size());
                                    for (final List<Attribute> a : value) {
                                        attributes.addAll(a);
                                    }
                                    return attributes;
                                }
                            }
                        }, h));

        // Invoke mappings for which there are values provided.
        if (v != null && !v.isNull()) {
            for (final Map.Entry<String, Object> e : v.asMap().entrySet()) {
                final Mapping mapping = getMapping(e.getKey());
                final JsonValue subValue = new JsonValue(e.getValue());
                mapping.mapper.toLDAP(c, subValue, handler);
            }
        }

        // Invoke mappings for which there were no values provided.
        for (final Mapping mapping : missingMappings.values()) {
            mapping.mapper.toLDAP(c, null, handler);
        }
    }

    private Mapping getMapping(final JsonPointer jsonAttribute) {
        return jsonAttribute.isEmpty() ? null : getMapping(jsonAttribute.get(0));
    }

    private Mapping getMapping(final String jsonAttribute) {
        return mappings.get(toLowerCase(jsonAttribute));
    }

}
