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

import static org.forgerock.opendj.rest2ldap.Rest2ldapMessages.*;
import static org.forgerock.json.JsonValue.*;
import static org.forgerock.opendj.ldap.Filter.alwaysFalse;
import static org.forgerock.opendj.ldap.Filter.alwaysTrue;
import static org.forgerock.opendj.rest2ldap.Utils.isNullOrEmpty;
import static org.forgerock.opendj.rest2ldap.Utils.newBadRequestException;
import static org.forgerock.opendj.rest2ldap.Utils.toFilter;
import static org.forgerock.opendj.rest2ldap.Utils.toLowerCase;
import static org.forgerock.util.promise.Promises.newResultPromise;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.forgerock.json.JsonPointer;
import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.PatchOperation;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.Modification;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.Promise;

/**
 * An property mapper which maps a single JSON attribute to a fixed value.
 */
final class JsonConstantPropertyMapper extends PropertyMapper {
    private final JsonValue value;

    JsonConstantPropertyMapper(final Object value) {
        this.value = new JsonValue(value);
    }

    @Override
    boolean isRequired() {
        return false;
    }

    @Override
    boolean isMultiValued() {
        return false;
    }

    @Override
    public String toString() {
        return "constant(" + value + ")";
    }

    @Override
    Promise<List<Attribute>, ResourceException> create(final Context context,
                                                       final Resource resource, final JsonPointer path,
                                                       final JsonValue v) {
        if (!isNullOrEmpty(v) && !v.getObject().equals(value.getObject())) {
            return newBadRequestException(ERR_CREATION_READ_ONLY_FIELD.get(path)).asPromise();
        } else {
            return newResultPromise(Collections.<Attribute> emptyList());
        }
    }

    @Override
    void getLdapAttributes(final JsonPointer path, final JsonPointer subPath, final Set<String> ldapAttributes) {
        // Nothing to do.
    }

    @Override
    Promise<Filter, ResourceException> getLdapFilter(final Context context, final Resource resource,
                                                     final JsonPointer path, final JsonPointer subPath,
                                                     final FilterType type, final String operator,
                                                     final Object valueAssertion) {
        return newResultPromise(getLdapFilter0(subPath, type, valueAssertion));
    }

    private Filter getLdapFilter0(final JsonPointer subPath, final FilterType type, final Object valueAssertion) {
        final JsonValue subValue = value.get(subPath);
        if (subValue == null) {
            return alwaysFalse();
        } else if (type == FilterType.PRESENT) {
            return alwaysTrue();
        } else if (value.isString() && valueAssertion instanceof String) {
            final String v1 = toLowerCase(value.asString());
            final String v2 = toLowerCase((String) valueAssertion);
            switch (type) {
            case CONTAINS:
                return toFilter(v1.contains(v2));
            case STARTS_WITH:
                return toFilter(v1.startsWith(v2));
            default:
                return compare(type, v1, v2);
            }
        } else if (value.isNumber() && valueAssertion instanceof Number) {
            final Double v1 = value.asDouble();
            final Double v2 = ((Number) valueAssertion).doubleValue();
            return compare(type, v1, v2);
        } else if (value.isBoolean() && valueAssertion instanceof Boolean) {
            final Boolean v1 = value.asBoolean();
            final Boolean v2 = (Boolean) valueAssertion;
            return compare(type, v1, v2);
        } else {
            // This property mapper is a candidate but it does not match.
            return alwaysFalse();
        }
    }

    @Override
    Promise<List<Modification>, ResourceException> patch(final Context context, final Resource resource,
                                                         final JsonPointer path, final PatchOperation operation) {
        return newBadRequestException(ERR_PATCH_READ_ONLY_FIELD.get(path)).asPromise();
    }

    @Override
    Promise<JsonValue, ResourceException> read(final Context context, final Resource resource,
                                               final JsonPointer path, final Entry e) {
        return newResultPromise(value.copy());
    }

    @Override
    Promise<List<Modification>, ResourceException> update(final Context context, final Resource resource,
                                                          final JsonPointer path, final Entry e, final JsonValue v) {
        if (!isNullOrEmpty(v) && !v.getObject().equals(value.getObject())) {
            return newBadRequestException(ERR_MODIFY_READ_ONLY_FIELD.get("update", path)).asPromise();
        } else {
            return newResultPromise(Collections.<Modification>emptyList());
        }
    }

    private <T extends Comparable<T>> Filter compare(final FilterType type, final T v1, final T v2) {
        switch (type) {
        case EQUAL_TO:
            return toFilter(v1.equals(v2));
        case GREATER_THAN:
            return toFilter(v1.compareTo(v2) > 0);
        case GREATER_THAN_OR_EQUAL_TO:
            return toFilter(v1.compareTo(v2) >= 0);
        case LESS_THAN:
            return toFilter(v1.compareTo(v2) < 0);
        case LESS_THAN_OR_EQUAL_TO:
            return toFilter(v1.compareTo(v2) <= 0);
        default:
            return alwaysFalse(); // Not supported.
        }
    }

    @Override
    JsonValue toJsonSchema() {
        return toJsonSchema(value);
    }

    private static JsonValue toJsonSchema(JsonValue value) {
        if (value.isMap()) {
            final JsonValue jsonSchema = json(object(field("type", "object")));
            final JsonValue jsonProps = json(object());
            for (String key : value.keys()) {
                jsonProps.put(key, toJsonSchema(value.get(key)));
            }
            jsonSchema.put("properties", jsonProps.getObject());
            return jsonSchema;
        } else if (value.isCollection()) {
            final JsonValue jsonSchema = json(object(field("type", "array")));
            final JsonValue firstItem = value.get(value.keys().iterator().next());
            // assume all items have the same schema
            JsonValue firstItemJson = toJsonSchema(firstItem);
            jsonSchema.put("items", firstItemJson != null ? firstItemJson.getObject() : null);
            if (value.getObject() instanceof Set) {
                jsonSchema.put("uniqueItems", true);
            }
            return jsonSchema;
        } else if (value.isBoolean()) {
            return json(object(field("type", "boolean"),
                               field("default", value)));
        } else if (value.isString()) {
            return json(object(field("type", "string"),
                               field("default", value)));
        } else if (value.isNumber()) {
            return json(object(field("type", "number"),
                               field("default", value)));
        } else if (value.isNull()) {
            return json(object(field("type", "null")));
        } else {
            throw new IllegalStateException("Unsupported json value: " + value);
        }
    }
}
