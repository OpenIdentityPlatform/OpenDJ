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
 * Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.rest2ldap;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.opendj.rest2ldap.Rest2Ldap.asResourceException;
import static org.forgerock.opendj.rest2ldap.Rest2ldapMessages.ERR_ENCODING_VALUES_FOR_FIELD;
import static org.forgerock.opendj.rest2ldap.Rest2ldapMessages.ERR_PATCH_JSON_INTERNAL_PROPERTY;
import static org.forgerock.opendj.rest2ldap.Utils.jsonToAttribute;
import static org.forgerock.opendj.rest2ldap.Utils.newBadRequestException;
import static org.forgerock.opendj.rest2ldap.Utils.newNotSupportedException;
import static org.forgerock.opendj.rest2ldap.schema.JsonSchema.byteStringToJson;
import static org.forgerock.opendj.rest2ldap.schema.JsonSchema.jsonToByteString;
import static org.forgerock.util.promise.Promises.newResultPromise;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.forgerock.json.JsonPointer;
import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.PatchOperation;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.Modification;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.query.QueryFilter;

/** A property mapper which provides a mapping from a JSON value to an LDAP attribute having the JSON syntax. */
public final class JsonPropertyMapper extends AbstractLdapPropertyMapper<JsonPropertyMapper> {
    /**
     * The default JSON schema for this property. According to json-schema.org the {} schema allows anything.
     * However, the OpenAPI transformer seems to expect at least a "type" field to be present.
     */
    private static final JsonValue ANY_SCHEMA = new JsonValue(object(field("type", "object")));
    private JsonValue jsonSchema = ANY_SCHEMA;

    JsonPropertyMapper(final AttributeDescription ldapAttributeName) {
        super(ldapAttributeName);
    }

    /**
     * Sets the default JSON value which should be substituted when the LDAP attribute is not found in the LDAP entry.
     *
     * @param defaultValue
     *         The default JSON value.
     * @return This property mapper.
     */
    public JsonPropertyMapper defaultJsonValue(final Object defaultValue) {
        this.defaultJsonValues = defaultValue != null ? singletonList(defaultValue) : emptyList();
        return this;
    }

    /**
     * Sets the default JSON values which should be substituted when the LDAP attribute is not found in the LDAP entry.
     *
     * @param defaultValues
     *         The default JSON values.
     * @return This property mapper.
     */
    public JsonPropertyMapper defaultJsonValues(final Collection<?> defaultValues) {
        this.defaultJsonValues = defaultValues != null ? new ArrayList<>(defaultValues) : emptyList();
        return this;
    }

    /**
     * Sets the JSON schema corresponding to this simple property mapper. If not {@code null},
     * it will be returned by {@link #toJsonSchema()}, otherwise a default JSON schema will be
     * automatically generated with the information available in this property mapper.
     *
     * @param jsonSchema
     *         the JSON schema corresponding to this simple property mapper. Can be {@code null}
     * @return This property mapper.
     */
    public JsonPropertyMapper jsonSchema(JsonValue jsonSchema) {
        this.jsonSchema = jsonSchema != null ? jsonSchema : ANY_SCHEMA;
        return this;
    }

    @Override
    public String toString() {
        return "json(" + ldapAttributeName + ")";
    }

    @Override
    Promise<Filter, ResourceException> getLdapFilter(final Context context, final Resource resource,
                                                     final JsonPointer path, final JsonPointer subPath,
                                                     final FilterType type, final String operator,
                                                     final Object valueAssertion) {
        final QueryFilter<JsonPointer> queryFilter = toQueryFilter(type, subPath, operator, valueAssertion);
        return newResultPromise(Filter.equality(ldapAttributeName.toString(), queryFilter));
    }

    private QueryFilter<JsonPointer> toQueryFilter(final FilterType type, final JsonPointer subPath,
                                                   final String operator, final Object valueAssertion) {
        switch (type) {
        case CONTAINS:
            return QueryFilter.contains(subPath, valueAssertion);
        case STARTS_WITH:
            return QueryFilter.startsWith(subPath, valueAssertion);
        case EQUAL_TO:
            return QueryFilter.equalTo(subPath, valueAssertion);
        case GREATER_THAN:
            return QueryFilter.greaterThan(subPath, valueAssertion);
        case GREATER_THAN_OR_EQUAL_TO:
            return QueryFilter.greaterThanOrEqualTo(subPath, valueAssertion);
        case LESS_THAN:
            return QueryFilter.lessThan(subPath, valueAssertion);
        case LESS_THAN_OR_EQUAL_TO:
            return QueryFilter.lessThanOrEqualTo(subPath, valueAssertion);
        case PRESENT:
            return QueryFilter.present(subPath);
        case EXTENDED:
            return QueryFilter.extendedMatch(subPath, operator, valueAssertion);
        default:
            return QueryFilter.alwaysFalse();
        }
    }

    @Override
    Promise<Attribute, ResourceException> getNewLdapAttributes(final Context context, final Resource resource,
                                                               final JsonPointer path, final List<Object> newValues) {
        try {
            return newResultPromise(jsonToAttribute(newValues, ldapAttributeName, jsonToByteString()));
        } catch (final Exception e) {
            return newBadRequestException(ERR_ENCODING_VALUES_FOR_FIELD.get(path, e.getMessage())).asPromise();
        }
    }

    @Override
    JsonPropertyMapper getThis() {
        return this;
    }

    /** Intercept attempts to patch internal fields and reject these as unsupported rather than unrecognized. */
    @Override
    Promise<List<Modification>, ResourceException> patch(final Context context, final Resource resource,
                                                         final JsonPointer path, final PatchOperation operation) {
        final JsonPointer field = operation.getField();
        if (field.isEmpty() || field.size() == 1 && field.get(0).equals("-")) {
            return super.patch(context, resource, path, operation);
        }
        return newNotSupportedException(ERR_PATCH_JSON_INTERNAL_PROPERTY.get(field, path, path)).asPromise();
    }

    @SuppressWarnings("fallthrough")
    @Override
    Promise<JsonValue, ResourceException> read(final Context context, final Resource resource, final JsonPointer path,
                                               final Entry e) {
        try {
            final Set<Object> s = e.parseAttribute(ldapAttributeName).asSetOf(byteStringToJson(), defaultJsonValues);
            switch (s.size()) {
            case 0:
                return newResultPromise(null);
            case 1:
                if (attributeIsSingleValued()) {
                    return newResultPromise(new JsonValue(s.iterator().next()));
                }
                // Fall-though: unexpectedly got multiple values. It's probably best to just return them.
            default:
                return newResultPromise(new JsonValue(new ArrayList<>(s)));
            }
        } catch (final Exception ex) {
            // The LDAP attribute could not be decoded.
            return asResourceException(ex).asPromise();
        }
    }

    @Override
    JsonValue toJsonSchema() {
        return jsonSchema;
    }
}
