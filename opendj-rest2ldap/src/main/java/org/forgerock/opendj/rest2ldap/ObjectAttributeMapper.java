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
 * Copyright 2012-2015 ForgeRock AS.
 */
package org.forgerock.opendj.rest2ldap;

import static org.forgerock.json.resource.PatchOperation.operation;
import static org.forgerock.opendj.ldap.Filter.alwaysFalse;
import static org.forgerock.opendj.rest2ldap.Rest2LDAP.asResourceException;
import static org.forgerock.opendj.rest2ldap.Utils.i18n;
import static org.forgerock.opendj.rest2ldap.Utils.toLowerCase;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.forgerock.json.JsonPointer;
import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.BadRequestException;
import org.forgerock.json.resource.PatchOperation;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.Modification;
import org.forgerock.util.Function;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;

/** An attribute mapper which maps JSON objects to LDAP attributes. */
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

    private final Map<String, Mapping> mappings = new LinkedHashMap<>();

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
        return "object(" + mappings.values() + ")";
    }

    @Override
    Promise<List<Attribute>, ResourceException> create(
            final RequestState requestState, final JsonPointer path, final JsonValue v) {
        try {
            /*
             * First check that the JSON value is an object and that the fields
             * it contains are known by this mapper.
             */
            final Map<String, Mapping> missingMappings = checkMapping(path, v);

            // Accumulate the results of the subordinate mappings.
            final List<Promise<List<Attribute>, ResourceException>> promises = new ArrayList<>();

            // Invoke mappings for which there are values provided.
            if (v != null && !v.isNull()) {
                for (final Map.Entry<String, Object> me : v.asMap().entrySet()) {
                    final Mapping mapping = getMapping(me.getKey());
                    final JsonValue subValue = new JsonValue(me.getValue());
                    promises.add(mapping.mapper.create(requestState, path.child(me.getKey()), subValue));
                }
            }

            // Invoke mappings for which there were no values provided.
            for (final Mapping mapping : missingMappings.values()) {
                promises.add(mapping.mapper.create(requestState, path.child(mapping.name), null));
            }

            return Promises.when(promises)
                           .then(this.<Attribute> accumulateResults());
        } catch (final Exception e) {
            return Promises.newExceptionPromise(asResourceException(e));
        }
    }

    @Override
    void getLDAPAttributes(final RequestState requestState, final JsonPointer path, final JsonPointer subPath,
            final Set<String> ldapAttributes) {
        if (subPath.isEmpty()) {
            // Request all subordinate mappings.
            for (final Mapping mapping : mappings.values()) {
                mapping.mapper.getLDAPAttributes(requestState, path.child(mapping.name), subPath, ldapAttributes);
            }
        } else {
            // Request single subordinate mapping.
            final Mapping mapping = getMapping(subPath);
            if (mapping != null) {
                mapping.mapper.getLDAPAttributes(
                        requestState, path.child(subPath.get(0)), subPath.relativePointer(), ldapAttributes);
            }
        }
    }

    @Override
    Promise<Filter, ResourceException> getLDAPFilter(final RequestState requestState, final JsonPointer path,
            final JsonPointer subPath, final FilterType type, final String operator, final Object valueAssertion) {
        final Mapping mapping = getMapping(subPath);
        if (mapping != null) {
            return mapping.mapper.getLDAPFilter(requestState, path.child(subPath.get(0)),
                    subPath.relativePointer(), type, operator, valueAssertion);
        } else {
            /*
             * Either the filter targeted the entire object (i.e. it was "/"),
             * or it targeted an unrecognized attribute within the object.
             * Either way, the filter will never match.
             */
            return Promises.newResultPromise(alwaysFalse());
        }
    }

    @Override
    Promise<List<Modification>, ResourceException> patch(
            final RequestState requestState, final JsonPointer path, final PatchOperation operation) {
        try {
            final JsonPointer field = operation.getField();
            final JsonValue v = operation.getValue();

            if (field.isEmpty()) {
                /*
                 * The patch operation applies to this object. We'll handle this
                 * by allowing the JSON value to be a partial object and
                 * add/remove/replace only the provided values.
                 */
                checkMapping(path, v);

                // Accumulate the results of the subordinate mappings.
                final List<Promise<List<Modification>, ResourceException>> promises = new ArrayList<>();

                // Invoke mappings for which there are values provided.
                if (!v.isNull()) {
                    for (final Map.Entry<String, Object> me : v.asMap().entrySet()) {
                        final Mapping mapping = getMapping(me.getKey());
                        final JsonValue subValue = new JsonValue(me.getValue());
                        final PatchOperation subOperation =
                                operation(operation.getOperation(), field /* empty */, subValue);
                        promises.add(mapping.mapper.patch(requestState, path.child(me.getKey()), subOperation));
                    }
                }

                return Promises.when(promises)
                               .then(this.<Modification> accumulateResults());
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
                return mapping.mapper.patch(requestState, path.child(fieldName), subOperation);
            }
        } catch (final Exception ex) {
            return Promises.newExceptionPromise(asResourceException(ex));
        }
    }

    @Override
    Promise<JsonValue, ResourceException> read(final RequestState requestState, final JsonPointer path, final Entry e) {
        /*
         * Use an accumulator which will aggregate the results from the
         * subordinate mappers into a single list. On completion, the
         * accumulator combines the results into a single JSON map object.
         */
        final List<Promise<Map.Entry<String, JsonValue>, ResourceException>> promises =
                new ArrayList<>(mappings.size());

        for (final Mapping mapping : mappings.values()) {
            promises.add(mapping.mapper.read(requestState, path.child(mapping.name), e)
                    .then(new Function<JsonValue, Map.Entry<String, JsonValue>, ResourceException>() {
                        @Override
                        public Map.Entry<String, JsonValue> apply(final JsonValue value) {
                            return value != null ? new SimpleImmutableEntry<String, JsonValue>(mapping.name, value)
                                                 : null;
                        }
                    }));
        }

        return Promises.when(promises)
                .then(new Function<List<Map.Entry<String, JsonValue>>, JsonValue, ResourceException>() {
                    @Override
                    public JsonValue apply(final List<Map.Entry<String, JsonValue>> value) {
                        if (value.isEmpty()) {
                            /*
                             * No subordinate attributes, so omit the entire
                             * JSON object from the resource.
                             */
                            return null;
                        } else {
                            // Combine the sub-attributes into a single JSON object.
                            final Map<String, Object> result = new LinkedHashMap<>(value.size());
                            for (final Map.Entry<String, JsonValue> e : value) {
                                if (e != null) {
                                    result.put(e.getKey(), e.getValue().getObject());
                                }
                            }
                            return new JsonValue(result);
                        }
                    }
                });
    }

    @Override
    Promise<List<Modification>, ResourceException> update(
            final RequestState requestState, final JsonPointer path, final Entry e, final JsonValue v) {
        try {
            // First check that the JSON value is an object and that the fields
            // it contains are known by this mapper.
            final Map<String, Mapping> missingMappings = checkMapping(path, v);

            // Accumulate the results of the subordinate mappings.
            final List<Promise<List<Modification>, ResourceException>> promises = new ArrayList<>();

            // Invoke mappings for which there are values provided.
            if (v != null && !v.isNull()) {
                for (final Map.Entry<String, Object> me : v.asMap().entrySet()) {
                    final Mapping mapping = getMapping(me.getKey());
                    final JsonValue subValue = new JsonValue(me.getValue());
                    promises.add(mapping.mapper.update(requestState, path.child(me.getKey()), e, subValue));
                }
            }

            // Invoke mappings for which there were no values provided.
            for (final Mapping mapping : missingMappings.values()) {
                promises.add(mapping.mapper.update(requestState, path.child(mapping.name), e, null));
            }

            return Promises.when(promises)
                           .then(this.<Modification> accumulateResults());
        } catch (final Exception ex) {
            return Promises.newExceptionPromise(asResourceException(ex));
        }
    }

    private <T> Function<List<List<T>>, List<T>, ResourceException> accumulateResults() {
        return new Function<List<List<T>>, List<T>, ResourceException>() {
            @Override
            public List<T> apply(final List<List<T>> value) {
                switch (value.size()) {
                case 0:
                    return Collections.emptyList();
                case 1:
                    return value.get(0);
                default:
                    final List<T> attributes = new ArrayList<>(value.size());
                    for (final List<T> a : value) {
                        attributes.addAll(a);
                    }
                    return attributes;
                }
            }
        };
    }

    /** Fail immediately if the JSON value has the wrong type or contains unknown attributes. */
    private Map<String, Mapping> checkMapping(final JsonPointer path, final JsonValue v)
            throws ResourceException {
        final Map<String, Mapping> missingMappings = new LinkedHashMap<>(mappings);
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
