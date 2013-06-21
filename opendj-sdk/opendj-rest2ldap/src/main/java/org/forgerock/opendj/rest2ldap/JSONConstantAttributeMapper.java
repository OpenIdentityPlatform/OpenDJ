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
import static org.forgerock.opendj.ldap.Filter.alwaysTrue;
import static org.forgerock.opendj.rest2ldap.Utils.i18n;
import static org.forgerock.opendj.rest2ldap.Utils.isNullOrEmpty;
import static org.forgerock.opendj.rest2ldap.Utils.toFilter;
import static org.forgerock.opendj.rest2ldap.Utils.toLowerCase;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.forgerock.json.fluent.JsonPointer;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.resource.BadRequestException;
import org.forgerock.json.resource.PatchOperation;
import org.forgerock.json.resource.ResultHandler;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.Modification;

/**
 * An attribute mapper which maps a single JSON attribute to a fixed value.
 */
final class JSONConstantAttributeMapper extends AttributeMapper {
    private final JsonValue value;

    JSONConstantAttributeMapper(final Object value) {
        this.value = new JsonValue(value);
    }

    @Override
    public String toString() {
        return "constant(" + value.toString() + ")";
    }

    @Override
    void create(final Context c, final JsonPointer path, final JsonValue v,
            final ResultHandler<List<Attribute>> h) {
        if (!isNullOrEmpty(v) && !v.getObject().equals(value.getObject())) {
            h.handleError(new BadRequestException(i18n(
                    "The request cannot be processed because it attempts to create "
                            + "the read-only field '%s'", path)));
        } else {
            h.handleResult(Collections.<Attribute> emptyList());
        }
    }

    @Override
    void getLDAPAttributes(final Context c, final JsonPointer path, final JsonPointer subPath,
            final Set<String> ldapAttributes) {
        // Nothing to do.
    }

    @Override
    void getLDAPFilter(final Context c, final JsonPointer path, final JsonPointer subPath,
            final FilterType type, final String operator, final Object valueAssertion,
            final ResultHandler<Filter> h) {
        final Filter filter;
        final JsonValue subValue = value.get(subPath);
        if (subValue == null) {
            filter = alwaysFalse();
        } else if (type == FilterType.PRESENT) {
            filter = alwaysTrue();
        } else if (value.isString() && valueAssertion instanceof String) {
            final String v1 = toLowerCase(value.asString());
            final String v2 = toLowerCase((String) valueAssertion);
            switch (type) {
            case CONTAINS:
                filter = toFilter(v1.contains(v2));
                break;
            case STARTS_WITH:
                filter = toFilter(v1.startsWith(v2));
                break;
            default:
                filter = compare(c, type, v1, v2);
                break;
            }
        } else if (value.isNumber() && valueAssertion instanceof Number) {
            final Double v1 = value.asDouble();
            final Double v2 = ((Number) valueAssertion).doubleValue();
            filter = compare(c, type, v1, v2);
        } else if (value.isBoolean() && valueAssertion instanceof Boolean) {
            final Boolean v1 = value.asBoolean();
            final Boolean v2 = (Boolean) valueAssertion;
            filter = compare(c, type, v1, v2);
        } else {
            // This attribute mapper is a candidate but it does not match.
            filter = alwaysFalse();
        }
        h.handleResult(filter);
    }

    @Override
    void patch(final Context c, final JsonPointer path, final PatchOperation operation,
            final ResultHandler<List<Modification>> h) {
        h.handleError(new BadRequestException(i18n(
                "The request cannot be processed because it attempts to patch "
                        + "the read-only field '%s'", path)));
    }

    @Override
    void read(final Context c, final JsonPointer path, final Entry e,
            final ResultHandler<JsonValue> h) {
        h.handleResult(value.copy());
    }

    @Override
    void update(final Context c, final JsonPointer path, final Entry e, final JsonValue v,
            final ResultHandler<List<Modification>> h) {
        if (!isNullOrEmpty(v) && !v.getObject().equals(value.getObject())) {
            h.handleError(new BadRequestException(i18n(
                    "The request cannot be processed because it attempts to modify "
                            + "the read-only field '%s'", path)));
        } else {
            h.handleResult(Collections.<Modification> emptyList());
        }
    }

    private <T extends Comparable<T>> Filter compare(final Context c, final FilterType type,
            final T v1, final T v2) {
        final Filter filter;
        switch (type) {
        case EQUAL_TO:
            filter = toFilter(v1.equals(v2));
            break;
        case GREATER_THAN:
            filter = toFilter(v1.compareTo(v2) > 0);
            break;
        case GREATER_THAN_OR_EQUAL_TO:
            filter = toFilter(v1.compareTo(v2) >= 0);
            break;
        case LESS_THAN:
            filter = toFilter(v1.compareTo(v2) < 0);
            break;
        case LESS_THAN_OR_EQUAL_TO:
            filter = toFilter(v1.compareTo(v2) <= 0);
            break;
        default:
            filter = alwaysFalse(); // Not supported.
            break;
        }
        return filter;
    }

}
