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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2016 ForgeRock AS.
 *
 */
package org.forgerock.opendj.rest2ldap;

import static java.util.Collections.singletonList;
import static org.forgerock.opendj.ldap.Filter.alwaysFalse;
import static org.forgerock.opendj.ldap.Filter.alwaysTrue;
import static org.forgerock.opendj.rest2ldap.Rest2ldapMessages.ERR_ILLEGAL_FILTER_ASSERTION_VALUE;
import static org.forgerock.opendj.rest2ldap.Rest2ldapMessages.ERR_MODIFY_READ_ONLY_FIELD;
import static org.forgerock.opendj.rest2ldap.Rest2ldapMessages.ERR_PATCH_READ_ONLY_FIELD;
import static org.forgerock.opendj.rest2ldap.Utils.isNullOrEmpty;
import static org.forgerock.opendj.rest2ldap.Utils.newBadRequestException;
import static org.forgerock.util.promise.Promises.newResultPromise;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.forgerock.json.JsonPointer;
import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.PatchOperation;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.Modification;
import org.forgerock.util.promise.Promise;

/**
 * A property mapper which maps a single JSON property containing the resource type to the resource's object classes.
 */
final class ResourceTypePropertyMapper extends PropertyMapper {
    static final ResourceTypePropertyMapper INSTANCE = new ResourceTypePropertyMapper();

    private ResourceTypePropertyMapper() { }

    @Override
    public String toString() {
        return "type()";
    }

    @Override
    Promise<List<Attribute>, ResourceException> create(final Connection connection,
                                                       final Resource resource, final JsonPointer path,
                                                       final JsonValue v) {
        return newResultPromise(singletonList(resource.getObjectClassAttribute()));
    }

    @Override
    void getLdapAttributes(final JsonPointer path, final JsonPointer subPath, final Set<String> ldapAttributes) {
        ldapAttributes.add("objectClass");
    }

    @Override
    Promise<Filter, ResourceException> getLdapFilter(final Connection connection, final Resource resource,
                                                     final JsonPointer path, final JsonPointer subPath,
                                                     final FilterType type, final String operator,
                                                     final Object valueAssertion) {
        if (subPath.isEmpty()) {
            switch (type) {
            case PRESENT:
                return newResultPromise(alwaysTrue());
            case EQUAL_TO:
                if (valueAssertion instanceof String) {
                    final Resource subType = resource.resolveSubTypeFromString((String) valueAssertion);
                    if (subType == null) {
                        return newResultPromise(alwaysFalse());
                    }
                    final List<Filter> subFilters = new ArrayList<>();
                    for (final ByteString objectClass : subType.getObjectClassAttribute()) {
                        subFilters.add(Filter.equality("objectClass", objectClass));
                    }
                    return newResultPromise(Filter.and(subFilters));
                }
                return newBadRequestException(ERR_ILLEGAL_FILTER_ASSERTION_VALUE.get(valueAssertion, path)).asPromise();
            default:
                return newResultPromise(alwaysFalse()); // Not supported.
            }
        } else {
            // This property mapper does not support partial filtering.
            return newResultPromise(alwaysFalse());
        }
    }

    @Override
    Promise<List<Modification>, ResourceException> patch(final Connection connection, final Resource resource,
                                                         final JsonPointer path, final PatchOperation operation) {
        return newBadRequestException(ERR_PATCH_READ_ONLY_FIELD.get(path)).asPromise();
    }

    @Override
    Promise<JsonValue, ResourceException> read(final Connection connection, final Resource resource,
                                               final JsonPointer path, final Entry e) {
        return newResultPromise(new JsonValue(resource.getResourceId()));
    }

    @Override
    Promise<List<Modification>, ResourceException> update(final Connection connection, final Resource resource,
                                                          final JsonPointer path, final Entry e, final JsonValue v) {
        if (!isNullOrEmpty(v) && !v.getObject().equals(resource.getResourceId())) {
            return newBadRequestException(ERR_MODIFY_READ_ONLY_FIELD.get("update", path)).asPromise();
        } else {
            return newResultPromise(Collections.<Modification>emptyList());
        }
    }
}
