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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.forgerock.json.fluent.JsonPointer;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.resource.exception.ResourceException;
import org.forgerock.resource.provider.Context;

/**
 *
 */
public final class CompositeAttributeMapper implements AttributeMapper {
    private final List<AttributeMapper> attributeMappers = new LinkedList<AttributeMapper>();

    /**
     * Creates a new composite attribute mapper.
     *
     */
    public CompositeAttributeMapper() {
        // No implementation required.
    }

    public CompositeAttributeMapper addMapper(AttributeMapper mapper) {
        attributeMappers.add(mapper);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public void getLDAPAttributes(JsonPointer jsonAttribute, Set<String> ldapAttributes) {
        for (AttributeMapper attribute : attributeMappers) {
            attribute.getLDAPAttributes(jsonAttribute, ldapAttributes);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void toJson(Context c, Entry e,
            final AttributeMapperCompletionHandler<Map<String, Object>> h) {
        AttributeMapperCompletionHandler<Map<String, Object>> resultAccumulater =
                new AttributeMapperCompletionHandler<Map<String, Object>>() {
                    private final AtomicInteger latch = new AtomicInteger(attributeMappers.size());
                    private final List<Map<String, Object>> results =
                            new ArrayList<Map<String, Object>>(latch.get());

                    public void onFailure(ResourceException e) {
                        // Ensure that handler is only invoked once.
                        if (latch.getAndSet(0) > 0) {
                            h.onFailure(e);
                        }
                    }

                    public void onSuccess(Map<String, Object> result) {
                        synchronized (this) {
                            results.add(result);
                        }
                        if (latch.decrementAndGet() == 0) {
                            final Map<String, Object> mergeResult;
                            switch (results.size()) {
                            case 0:
                                mergeResult = Collections.<String, Object> emptyMap();
                                break;
                            case 1:
                                mergeResult = results.get(0);
                                break;
                            default:
                                mergeResult = new LinkedHashMap<String, Object>();
                                mergeJsonValues(results, mergeResult);
                                break;
                            }
                            h.onSuccess(mergeResult);
                        }
                    }
                };

        for (AttributeMapper mapper : attributeMappers) {
            mapper.toJson(c, e, resultAccumulater);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void toLDAP(Context c, JsonValue v, AttributeMapperCompletionHandler<List<Attribute>> h) {
        // TODO Auto-generated method stub

    }

    /**
     * Merge the provided list of JSON values into a single value.
     *
     * @param srcValues
     *            The source values.
     * @param dstValue
     *            The destination value, into which which the values should be
     *            merged.
     */
    private void mergeJsonValues(List<Map<String, Object>> srcValues, Map<String, Object> dstValue) {
        for (Map<String, Object> value : srcValues) {
            mergeJsonValue(value, dstValue);
        }
    }

    /**
     * Merge one JSON value into another.
     *
     * @param srcValue
     *            The source value.
     * @param dstValue
     *            The destination value, into which which the value should be
     *            merged.
     */
    @SuppressWarnings("unchecked")
    private void mergeJsonValue(Map<String, Object> srcValue, Map<String, Object> dstValue) {
        for (Map.Entry<String, Object> record : srcValue.entrySet()) {
            String key = record.getKey();
            Object newValue = record.getValue();
            Object existingValue = dstValue.get(key);
            if (existingValue == null) {
                // Value is new, so just add it.
                dstValue.put(key, newValue);
            } else if (existingValue instanceof Map && newValue instanceof Map) {
                // Merge two maps - create a new Map, in case the existing one
                // is unmodifiable.
                existingValue =
                        new LinkedHashMap<String, Object>((Map<String, Object>) existingValue);
                mergeJsonValue((Map<String, Object>) newValue, (Map<String, Object>) existingValue);
            } else if (existingValue instanceof List && newValue instanceof List) {
                // Merge two lists- create a new List, in case the existing one
                // is unmodifiable.
                List<Object> tmp = new ArrayList<Object>((List<Object>) existingValue);
                tmp.addAll((List<Object>) newValue);
                existingValue = tmp;
            }

            // Replace the existing value.
            dstValue.put(key, newValue);
        }
    }
}
