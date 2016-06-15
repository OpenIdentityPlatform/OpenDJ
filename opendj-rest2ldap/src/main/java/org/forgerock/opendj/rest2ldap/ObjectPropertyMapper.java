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
 * Copyright 2012-2016 ForgeRock AS.
 */
package org.forgerock.opendj.rest2ldap;

import static org.forgerock.opendj.rest2ldap.Rest2Ldap.simple;
import static org.forgerock.opendj.rest2ldap.Rest2ldapMessages.*;
import static org.forgerock.json.resource.PatchOperation.operation;
import static org.forgerock.opendj.ldap.Filter.alwaysFalse;
import static org.forgerock.opendj.rest2ldap.Rest2Ldap.asResourceException;
import static org.forgerock.opendj.rest2ldap.Utils.newBadRequestException;
import static org.forgerock.opendj.rest2ldap.Utils.toLowerCase;
import static org.forgerock.util.Utils.joinAsString;
import static org.forgerock.util.promise.Promises.newResultPromise;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.forgerock.json.JsonPointer;
import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.PatchOperation;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.Modification;
import org.forgerock.util.Function;
import org.forgerock.util.Pair;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;

/** An property mapper which maps JSON objects to LDAP attributes. */
public final class ObjectPropertyMapper extends PropertyMapper {
    private static final class Mapping {
        private final PropertyMapper mapper;
        private final String name;

        private Mapping(final String name, final PropertyMapper mapper) {
            this.name = name;
            this.mapper = mapper;
        }

        @Override
        public String toString() {
            return name + " -> " + mapper;
        }
    }

    private final Map<String, Mapping> mappings = new LinkedHashMap<>();

    private boolean includeAllUserAttributesByDefault = false;
    private final Set<String> excludedDefaultUserAttributes = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    ObjectPropertyMapper() {
        // Nothing to do.
    }

    /**
     * Creates an explicit mapping for a property contained in the JSON object. When user attributes are
     * {@link #includeAllUserAttributesByDefault included} by default, be careful to {@link
     * #excludedDefaultUserAttributes exclude} any attributes which have explicit mappings defined using this method,
     * otherwise they will be duplicated in the JSON representation.
     *
     * @param name
     *            The name of the JSON property to be mapped.
     * @param mapper
     *            The property mapper responsible for mapping the JSON attribute to LDAP attribute(s).
     * @return A reference to this property mapper.
     */
    public ObjectPropertyMapper property(final String name, final PropertyMapper mapper) {
        mappings.put(toLowerCase(name), new Mapping(name, mapper));
        return this;
    }

    /**
     * Specifies whether all LDAP user attributes should be mapped by default using the default schema based mapping
     * rules. Individual attributes can be excluded using {@link #excludedDefaultUserAttributes} in order to prevent
     * attributes with explicit mappings being mapped twice.
     *
     * @param include {@code true} if all LDAP user attributes be mapped by default.
     * @return A reference to this property mapper.
     */
    public ObjectPropertyMapper includeAllUserAttributesByDefault(final boolean include) {
        this.includeAllUserAttributesByDefault = include;
        return this;
    }

    /**
     * Specifies zero or more user attributes which will be excluded from the default user attribute mappings when
     * enabled using {@link #includeAllUserAttributesByDefault}. Attributes which have explicit mappings should be
     * excluded in order to prevent duplication.
     *
     * @param attributeNames The list of attributes to be excluded.
     * @return A reference to this property mapper.
     */
    public ObjectPropertyMapper excludedDefaultUserAttributes(final String... attributeNames) {
        return excludedDefaultUserAttributes(Arrays.asList(attributeNames));
    }

    /**
     * Specifies zero or more user attributes which will be excluded from the default user attribute mappings when
     * enabled using {@link #includeAllUserAttributesByDefault}. Attributes which have explicit mappings should be
     * excluded in order to prevent duplication.
     *
     * @param attributeNames The list of attributes to be excluded.
     * @return A reference to this property mapper.
     */
    public ObjectPropertyMapper excludedDefaultUserAttributes(final Collection<String> attributeNames) {
        excludedDefaultUserAttributes.addAll(attributeNames);
        return this;
    }

    @Override
    public String toString() {
        return "object(" + joinAsString(", ", mappings.values()) + ")";
    }

    @Override
    Promise<List<Attribute>, ResourceException> create(final Connection connection,
                                                       final Resource resource, final JsonPointer path,
                                                       final JsonValue v) {
        try {
            // First check that the JSON value is an object and that the fields it contains are known by this mapper.
            final Map<String, Mapping> missingMappings = validateJsonValue(path, v);

            // Accumulate the results of the subordinate mappings.
            final List<Promise<List<Attribute>, ResourceException>> promises = new ArrayList<>();

            // Invoke mappings for which there are values provided.
            if (v != null && !v.isNull()) {
                for (final Map.Entry<String, Object> me : v.asMap().entrySet()) {
                    final Mapping mapping = getMapping(me.getKey());
                    final JsonValue subValue = new JsonValue(me.getValue());
                    promises.add(mapping.mapper.create(connection, resource, path.child(me.getKey()),
                                                       subValue));
                }
            }

            // Invoke mappings for which there were no values provided.
            for (final Mapping mapping : missingMappings.values()) {
                promises.add(mapping.mapper.create(connection, resource, path.child(mapping.name), null));
            }

            return Promises.when(promises)
                           .then(this.<Attribute> accumulateResults());
        } catch (final Exception e) {
            return asResourceException(e).asPromise();
        }
    }

    @Override
    void getLdapAttributes(final JsonPointer path, final JsonPointer subPath, final Set<String> ldapAttributes) {
        if (subPath.isEmpty()) {
            // Request all subordinate mappings.
            if (includeAllUserAttributesByDefault) {
                ldapAttributes.add("*");
                // Continue because there may be explicit mappings for operational attributes.
            }
            for (final Mapping mapping : mappings.values()) {
                mapping.mapper.getLdapAttributes(path.child(mapping.name), subPath, ldapAttributes);
            }
        } else {
            // Request single subordinate mapping.
            final Mapping mapping = getMappingOrNull(subPath);
            if (mapping != null) {
                mapping.mapper.getLdapAttributes(path.child(subPath.get(0)), subPath.relativePointer(), ldapAttributes);
            }
        }
    }

    @Override
    Promise<Filter, ResourceException> getLdapFilter(final Connection connection, final Resource resource,
                                                     final JsonPointer path, final JsonPointer subPath,
                                                     final FilterType type, final String operator,
                                                     final Object valueAssertion) {
        final Mapping mapping = getMappingOrNull(subPath);
        if (mapping != null) {
            return mapping.mapper.getLdapFilter(connection,
                                                resource,
                                                path.child(subPath.get(0)),
                                                subPath.relativePointer(),
                                                type,
                                                operator,
                                                valueAssertion);
        } else {
            /*
             * Either the filter targeted the entire object (i.e. it was "/"),
             * or it targeted an unrecognized attribute within the object.
             * Either way, the filter will never match.
             */
            return newResultPromise(alwaysFalse());
        }
    }

    @Override
    Promise<List<Modification>, ResourceException> patch(final Connection connection, final Resource resource,
                                                         final JsonPointer path, final PatchOperation operation) {
        try {
            final JsonPointer field = operation.getField();
            final JsonValue v = operation.getValue();

            if (field.isEmpty()) {
                /*
                 * The patch operation applies to this object. We'll handle this
                 * by allowing the JSON value to be a partial object and
                 * add/remove/replace only the provided values.
                 */
                validateJsonValue(path, v);

                // Accumulate the results of the subordinate mappings.
                final List<Promise<List<Modification>, ResourceException>> promises = new ArrayList<>();

                // Invoke mappings for which there are values provided.
                if (!v.isNull()) {
                    for (final Map.Entry<String, Object> me : v.asMap().entrySet()) {
                        final Mapping mapping = getMapping(me.getKey());
                        final JsonValue subValue = new JsonValue(me.getValue());
                        final PatchOperation subOperation =
                                operation(operation.getOperation(), field /* empty */, subValue);
                        promises.add(mapping.mapper.patch(connection, resource, path.child(me.getKey()), subOperation));
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
                final Mapping mapping = getMappingOrNull(fieldName);
                if (mapping == null) {
                    throw newBadRequestException(ERR_UNRECOGNIZED_FIELD.get(path.child(fieldName)));
                }
                final PatchOperation subOperation =
                        operation(operation.getOperation(), field.relativePointer(), v);
                return mapping.mapper.patch(connection, resource, path.child(fieldName), subOperation);
            }
        } catch (final Exception e) {
            return asResourceException(e).asPromise();
        }
    }

    @Override
    Promise<JsonValue, ResourceException> read(final Connection connection, final Resource resource,
                                               final JsonPointer path, final Entry e) {
        /*
         * Use an accumulator which will aggregate the results from the
         * subordinate mappers into a single list. On completion, the
         * accumulator combines the results into a single JSON map object.
         */
        final List<Promise<Pair<String, JsonValue>, ResourceException>> promises =
                new ArrayList<>(mappings.size());

        for (final Mapping mapping : mappings.values()) {
            promises.add(mapping.mapper.read(connection, resource, path.child(mapping.name), e)
                                       .then(toProperty(mapping.name)));
        }

        if (includeAllUserAttributesByDefault) {
            // Map all user attributes using a default simple mapping. It would be nice if we could automatically
            // detect which attributes have been mapped already using explicit mappings, but it would require us to
            // track which attributes have been accessed in the entry. Instead, we'll rely on the user to exclude
            // attributes which have explicit mappings.
            for (final Attribute attribute : e.getAllAttributes()) {
                // Don't include operational attributes. They must have explicit mappings.
                if (attribute.getAttributeDescription().getAttributeType().isOperational()) {
                    continue;
                }
                // Filter out excluded attributes.
                final String attributeName = attribute.getAttributeDescriptionAsString();
                if (!excludedDefaultUserAttributes.isEmpty() && excludedDefaultUserAttributes.contains(attributeName)) {
                    continue;
                }
                // This attribute needs to be mapped.
                final SimplePropertyMapper mapper = simple(attribute.getAttributeDescription());
                promises.add(mapper.read(connection, resource, path.child(attributeName), e)
                                   .then(toProperty(attributeName)));
            }
        }

        return Promises.when(promises)
                       .then(new Function<List<Pair<String, JsonValue>>, JsonValue, ResourceException>() {
                           @Override
                           public JsonValue apply(final List<Pair<String, JsonValue>> value) {
                               if (value.isEmpty()) {
                                   // No subordinate attributes, so omit the entire JSON object from the resource.
                                   return null;
                               } else {
                                   // Combine the sub-attributes into a single JSON object.
                                   final Map<String, Object> result = new LinkedHashMap<>(value.size());
                                   for (final Pair<String, JsonValue> e : value) {
                                       if (e != null) {
                                           result.put(e.getFirst(), e.getSecond().getObject());
                                       }
                                   }
                                   return new JsonValue(result);
                               }
                           }
                       });
    }

    private Function<JsonValue, Pair<String, JsonValue>, ResourceException> toProperty(final String name) {
        return new Function<JsonValue, Pair<String, JsonValue>, ResourceException>() {
            @Override
            public Pair<String, JsonValue> apply(final JsonValue value) {
                return value != null ? Pair.of(name, value) : null;
            }
        };
    }

    @Override
    Promise<List<Modification>, ResourceException> update(final Connection connection, final Resource resource,
                                                          final JsonPointer path, final Entry e, final JsonValue v) {
        try {
            // First check that the JSON value is an object and that the fields it contains are known by this mapper.
            final Map<String, Mapping> missingMappings = validateJsonValue(path, v);

            // Accumulate the results of the subordinate mappings.
            final List<Promise<List<Modification>, ResourceException>> promises = new ArrayList<>();

            // Invoke mappings for which there are values provided.
            if (v != null && !v.isNull()) {
                for (final Map.Entry<String, Object> me : v.asMap().entrySet()) {
                    final Mapping mapping = getMapping(me.getKey());
                    final JsonValue subValue = new JsonValue(me.getValue());
                    promises.add(mapping.mapper.update(connection, resource, path.child(me.getKey()), e, subValue));
                }
            }

            // Invoke mappings for which there were no values provided.
            for (final Mapping mapping : missingMappings.values()) {
                promises.add(mapping.mapper.update(connection, resource, path.child(mapping.name), e, null));
            }

            return Promises.when(promises)
                           .then(this.<Modification> accumulateResults());
        } catch (final Exception ex) {
            return asResourceException(ex).asPromise();
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
    private Map<String, Mapping> validateJsonValue(final JsonPointer path, final JsonValue v) throws ResourceException {
        final Map<String, Mapping> missingMappings = new LinkedHashMap<>(mappings);
        if (v != null && !v.isNull()) {
            if (v.isMap()) {
                for (final String attribute : v.asMap().keySet()) {
                    if (missingMappings.remove(toLowerCase(attribute)) == null
                            && !isIncludedDefaultUserAttribute(attribute)) {
                        throw newBadRequestException(ERR_UNRECOGNIZED_FIELD.get(path.child(attribute)));
                    }
                }
            } else {
                throw newBadRequestException(ERR_FIELD_WRONG_TYPE.get(path));
            }
        }
        return missingMappings;
    }

    private Mapping getMappingOrNull(final JsonPointer jsonAttribute) {
        return jsonAttribute.isEmpty() ? null : getMappingOrNull(jsonAttribute.get(0));
    }

    private Mapping getMappingOrNull(final String jsonAttribute) {
        final Mapping mapping = mappings.get(toLowerCase(jsonAttribute));
        if (mapping != null) {
            return mapping;
        }
        if (isIncludedDefaultUserAttribute(jsonAttribute)) {
            return new Mapping(jsonAttribute, simple(jsonAttribute));
        }
        return null;
    }

    private Mapping getMapping(final String jsonAttribute) {
        final Mapping mappingOrNull = getMappingOrNull(jsonAttribute);
        if (mappingOrNull != null) {
            return mappingOrNull;
        }
        throw new IllegalStateException("Unexpected null mapping for jsonAttribute: " + jsonAttribute);
    }

    private boolean isIncludedDefaultUserAttribute(final String attributeName) {
        return includeAllUserAttributesByDefault
                && (excludedDefaultUserAttributes.isEmpty() || !excludedDefaultUserAttributes.contains(attributeName));
    }
}
