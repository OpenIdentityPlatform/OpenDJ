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

import static org.forgerock.opendj.rest2ldap.Utils.accumulate;
import static org.forgerock.opendj.rest2ldap.Utils.transform;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.forgerock.json.fluent.JsonPointer;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.resource.ResultHandler;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.Function;

/**
 * An attribute mapper which combines the results of a set of subordinate
 * attribute mappers into a single JSON object.
 */
final class CompositeAttributeMapper extends AttributeMapper {
    private final List<AttributeMapper> attributeMappers;

    /**
     * Creates a new composite attribute mapper.
     */
    CompositeAttributeMapper(final Collection<AttributeMapper> mappers) {
        this.attributeMappers = new ArrayList<AttributeMapper>(mappers);
    }

    @Override
    void getLDAPAttributes(final Context c, final JsonPointer jsonAttribute,
            final Set<String> ldapAttributes) {
        for (final AttributeMapper attribute : attributeMappers) {
            attribute.getLDAPAttributes(c, jsonAttribute, ldapAttributes);
        }
    }

    @Override
    void getLDAPFilter(final Context c, final FilterType type, final JsonPointer jsonAttribute,
            final String operator, final Object valueAssertion, final ResultHandler<Filter> h) {
        final ResultHandler<Filter> handler =
                accumulate(attributeMappers.size(), transform(
                        new Function<List<Filter>, Filter, Void>() {
                            @Override
                            public Filter apply(final List<Filter> value, final Void p) {
                                // Remove unmapped filters and combine using logical-OR.
                                final Iterator<Filter> i = value.iterator();
                                while (i.hasNext()) {
                                    final Filter f = i.next();
                                    if (f == null) {
                                        // No mapping so remove.
                                        i.remove();
                                    } else if (f == c.getConfig().falseFilter()) {
                                        return c.getConfig().falseFilter();
                                    } else if (f == c.getConfig().trueFilter()) {
                                        return c.getConfig().trueFilter();
                                    }
                                }
                                switch (value.size()) {
                                case 0:
                                    // No mappings found.
                                    return null;
                                case 1:
                                    return value.get(0);
                                default:
                                    return Filter.or(value);
                                }
                            }
                        }, h));
        for (final AttributeMapper subMapper : attributeMappers) {
            subMapper.getLDAPFilter(c, type, jsonAttribute, operator, valueAssertion, handler);
        }
    }

    @Override
    void toJSON(final Context c, final Entry e, final ResultHandler<Map<String, Object>> h) {
        final ResultHandler<Map<String, Object>> handler =
                accumulate(attributeMappers.size(), transform(
                        new Function<List<Map<String, Object>>, Map<String, Object>, Void>() {
                            @Override
                            public Map<String, Object> apply(final List<Map<String, Object>> value,
                                    final Void p) {
                                switch (value.size()) {
                                case 0:
                                    return Collections.<String, Object> emptyMap();
                                case 1:
                                    return value.get(0) != null ? value.get(0) : Collections
                                            .<String, Object> emptyMap();
                                default:
                                    return mergeJsonValues(value,
                                            new LinkedHashMap<String, Object>());
                                }
                            }
                        }, h));
        for (final AttributeMapper mapper : attributeMappers) {
            mapper.toJSON(c, e, handler);
        }
    }

    @Override
    void toLDAP(final Context c, final JsonValue v, final ResultHandler<List<Attribute>> h) {
        final ResultHandler<List<Attribute>> handler =
                accumulate(attributeMappers.size(), transform(
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
                                    List<Attribute> attributes =
                                            new ArrayList<Attribute>(value.size());
                                    for (List<Attribute> a : value) {
                                        attributes.addAll(a);
                                    }
                                    return attributes;
                                }
                            }
                        }, h));
        for (final AttributeMapper mapper : attributeMappers) {
            mapper.toLDAP(c, v, handler);
        }
    }

    @SuppressWarnings("unchecked")
    private void mergeJsonValue(final Map<String, Object> srcValue,
            final Map<String, Object> dstValue) {
        for (final Map.Entry<String, Object> record : srcValue.entrySet()) {
            final String key = record.getKey();
            final Object newValue = record.getValue();
            Object existingValue = dstValue.get(key);
            if (existingValue == null) {
                // Value is new, so just add it.
                dstValue.put(key, newValue);
            } else if ((existingValue instanceof Map) && (newValue instanceof Map)) {
                // Merge two maps - create a new Map, in case the existing one
                // is unmodifiable.
                existingValue =
                        new LinkedHashMap<String, Object>((Map<String, Object>) existingValue);
                mergeJsonValue((Map<String, Object>) newValue, (Map<String, Object>) existingValue);
            } else if ((existingValue instanceof List) && (newValue instanceof List)) {
                // Merge two lists- create a new List, in case the existing one
                // is unmodifiable.
                final List<Object> tmp = new ArrayList<Object>((List<Object>) existingValue);
                tmp.addAll((List<Object>) newValue);
                existingValue = tmp;
            }

            // Replace the existing value.
            dstValue.put(key, newValue);
        }
    }

    private Map<String, Object> mergeJsonValues(final List<Map<String, Object>> srcValues,
            final Map<String, Object> dstValue) {
        for (final Map<String, Object> value : srcValues) {
            if (value != null) {
                mergeJsonValue(value, dstValue);
            }
        }
        return dstValue;
    }
}
