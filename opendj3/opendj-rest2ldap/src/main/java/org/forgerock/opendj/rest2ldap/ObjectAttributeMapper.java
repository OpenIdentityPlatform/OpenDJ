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

import static org.forgerock.json.resource.PatchOperation.operation;
import static org.forgerock.opendj.ldap.Filter.alwaysFalse;
import static org.forgerock.opendj.rest2ldap.Rest2LDAP.asResourceException;
import static org.forgerock.opendj.rest2ldap.Utils.accumulate;
import static org.forgerock.opendj.rest2ldap.Utils.i18n;
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
import org.forgerock.json.resource.PatchOperation;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.ResultHandler;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.Function;
import org.forgerock.opendj.ldap.Modification;

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

        @Override
        public String toString() {
            return name + " -> " + mapper;
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
    public String toString() {
        return "object(" + mappings.values().toString() + ")";
    }

    @Override
    void create(final Context c, final JsonPointer path, final JsonValue v,
            final ResultHandler<List<Attribute>> h) {
        try {
            /*
             * First check that the JSON value is an object and that the fields
             * it contains are known by this mapper.
             */
            final Map<String, Mapping> missingMappings = checkMapping(path, v);

            // Accumulate the results of the subordinate mappings.
            final ResultHandler<List<Attribute>> handler = accumulator(h);

            // Invoke mappings for which there are values provided.
            if (v != null && !v.isNull()) {
                for (final Map.Entry<String, Object> me : v.asMap().entrySet()) {
                    final Mapping mapping = getMapping(me.getKey());
                    final JsonValue subValue = new JsonValue(me.getValue());
                    mapping.mapper.create(c, path.child(me.getKey()), subValue, handler);
                }
            }

            // Invoke mappings for which there were no values provided.
            for (final Mapping mapping : missingMappings.values()) {
                mapping.mapper.create(c, path.child(mapping.name), null, handler);
            }
        } catch (final Exception e) {
            h.handleError(asResourceException(e));
        }
    }

    @Override
    void getLDAPAttributes(final Context c, final JsonPointer path, final JsonPointer subPath,
            final Set<String> ldapAttributes) {
        if (subPath.isEmpty()) {
            // Request all subordinate mappings.
            for (final Mapping mapping : mappings.values()) {
                mapping.mapper.getLDAPAttributes(c, path.child(mapping.name), subPath,
                        ldapAttributes);
            }
        } else {
            // Request single subordinate mapping.
            final Mapping mapping = getMapping(subPath);
            if (mapping != null) {
                mapping.mapper.getLDAPAttributes(c, path.child(subPath.get(0)), subPath
                        .relativePointer(), ldapAttributes);
            }
        }
    }

    @Override
    void getLDAPFilter(final Context c, final JsonPointer path, final JsonPointer subPath,
            final FilterType type, final String operator, final Object valueAssertion,
            final ResultHandler<Filter> h) {
        final Mapping mapping = getMapping(subPath);
        if (mapping != null) {
            mapping.mapper.getLDAPFilter(c, path.child(subPath.get(0)), subPath.relativePointer(),
                    type, operator, valueAssertion, h);
        } else {
            /*
             * Either the filter targeted the entire object (i.e. it was "/"),
             * or it targeted an unrecognized attribute within the object.
             * Either way, the filter will never match.
             */
            h.handleResult(alwaysFalse());
        }
    }

    @Override
    void patch(final Context c, final JsonPointer path, final PatchOperation operation,
            final ResultHandler<List<Modification>> h) {
        try {
            final JsonPointer field = operation.getField();
            final JsonValue v = operation.getValue();

            if (field.isEmpty()) {
                /*
                 * The patch operation applies to this object. We'll handle this
                 * by allowing the JSON value to be a partial object and
                 * add/remove/replace only the provided values.
                 */
                final Map<String, Mapping> missingMappings = checkMapping(path, v);

                // Accumulate the results of the subordinate mappings.
                final ResultHandler<List<Modification>> handler =
                        accumulator(mappings.size() - missingMappings.size(), h);

                // Invoke mappings for which there are values provided.
                if (!v.isNull()) {
                    for (final Map.Entry<String, Object> me : v.asMap().entrySet()) {
                        final Mapping mapping = getMapping(me.getKey());
                        final JsonValue subValue = new JsonValue(me.getValue());
                        final PatchOperation subOperation =
                                operation(operation.getOperation(), field /* empty */, subValue);
                        mapping.mapper.patch(c, path.child(me.getKey()), subOperation, handler);
                    }
                }
            } else {
                /*
                 * The patch operation targets a subordinate field. Create a new
                 * patch operation targeting the field and forward it to the
                 * appropriate mapper.
                 */
                final String fieldName = field.get(0);
                final Mapping mapping = getMapping(fieldName);
                if (mapping == null) {
                    throw new BadRequestException(i18n(
                            "The request cannot be processed because it included "
                                    + "an unrecognized field '%s'", path.child(fieldName)));
                }
                final PatchOperation subOperation =
                        operation(operation.getOperation(), field.relativePointer(), v);
                mapping.mapper.patch(c, path.child(fieldName), subOperation, h);
            }
        } catch (final Exception ex) {
            h.handleError(asResourceException(ex));
        }
    }

    @Override
    void read(final Context c, final JsonPointer path, final Entry e,
            final ResultHandler<JsonValue> h) {
        /*
         * Use an accumulator which will aggregate the results from the
         * subordinate mappers into a single list. On completion, the
         * accumulator combines the results into a single JSON map object.
         */
        final ResultHandler<Map.Entry<String, JsonValue>> handler =
                accumulate(mappings.size(), transform(
                        new Function<List<Map.Entry<String, JsonValue>>, JsonValue, Void>() {
                            @Override
                            public JsonValue apply(final List<Map.Entry<String, JsonValue>> value,
                                    final Void p) {
                                if (value.isEmpty()) {
                                    /*
                                     * No subordinate attributes, so omit the
                                     * entire JSON object from the resource.
                                     */
                                    return null;
                                } else {
                                    /*
                                     * Combine the sub-attributes into a single
                                     * JSON object.
                                     */
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
            mapping.mapper.read(c, path.child(mapping.name), e, transform(
                    new Function<JsonValue, Map.Entry<String, JsonValue>, Void>() {
                        @Override
                        public Map.Entry<String, JsonValue> apply(final JsonValue value,
                                final Void p) {
                            return value != null ? new SimpleImmutableEntry<String, JsonValue>(
                                    mapping.name, value) : null;
                        }
                    }, handler));
        }
    }

    @Override
    void update(final Context c, final JsonPointer path, final Entry e, final JsonValue v,
            final ResultHandler<List<Modification>> h) {
        try {
            /*
             * First check that the JSON value is an object and that the fields
             * it contains are known by this mapper.
             */
            final Map<String, Mapping> missingMappings = checkMapping(path, v);

            // Accumulate the results of the subordinate mappings.
            final ResultHandler<List<Modification>> handler = accumulator(h);

            // Invoke mappings for which there are values provided.
            if (v != null && !v.isNull()) {
                for (final Map.Entry<String, Object> me : v.asMap().entrySet()) {
                    final Mapping mapping = getMapping(me.getKey());
                    final JsonValue subValue = new JsonValue(me.getValue());
                    mapping.mapper.update(c, path.child(me.getKey()), e, subValue, handler);
                }
            }

            // Invoke mappings for which there were no values provided.
            for (final Mapping mapping : missingMappings.values()) {
                mapping.mapper.update(c, path.child(mapping.name), e, null, handler);
            }
        } catch (final Exception ex) {
            h.handleError(asResourceException(ex));
        }
    }

    private <T> ResultHandler<List<T>> accumulator(final ResultHandler<List<T>> h) {
        return accumulator(mappings.size(), h);
    }

    private <T> ResultHandler<List<T>> accumulator(final int size, final ResultHandler<List<T>> h) {
        return accumulate(size, transform(new Function<List<List<T>>, List<T>, Void>() {
            @Override
            public List<T> apply(final List<List<T>> value, final Void p) {
                switch (value.size()) {
                case 0:
                    return Collections.emptyList();
                case 1:
                    return value.get(0);
                default:
                    final List<T> attributes = new ArrayList<T>(value.size());
                    for (final List<T> a : value) {
                        attributes.addAll(a);
                    }
                    return attributes;
                }
            }
        }, h));
    }

    /*
     * Fail immediately if the JSON value has the wrong type or contains unknown
     * attributes.
     */
    private Map<String, Mapping> checkMapping(final JsonPointer path, final JsonValue v)
            throws ResourceException {
        final Map<String, Mapping> missingMappings = new LinkedHashMap<String, Mapping>(mappings);
        if (v != null && !v.isNull()) {
            if (v.isMap()) {
                for (final String attribute : v.asMap().keySet()) {
                    if (missingMappings.remove(toLowerCase(attribute)) == null) {
                        throw new BadRequestException(i18n(
                                "The request cannot be processed because it included "
                                        + "an unrecognized field '%s'", path.child(attribute)));
                    }
                }
            } else {
                throw new BadRequestException(i18n(
                        "The request cannot be processed because it included "
                                + "the field '%s' whose value is the wrong type: "
                                + "an object is expected", path));
            }
        }
        return missingMappings;
    }

    private Mapping getMapping(final JsonPointer jsonAttribute) {
        return jsonAttribute.isEmpty() ? null : getMapping(jsonAttribute.get(0));
    }

    private Mapping getMapping(final String jsonAttribute) {
        return mappings.get(toLowerCase(jsonAttribute));
    }

}
