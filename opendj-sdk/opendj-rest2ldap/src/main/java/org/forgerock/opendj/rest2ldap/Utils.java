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

import static javax.xml.bind.DatatypeConverter.parseDateTime;
import static javax.xml.bind.DatatypeConverter.printDateTime;
import static org.forgerock.opendj.ldap.Filter.alwaysFalse;
import static org.forgerock.opendj.ldap.Functions.byteStringToBoolean;
import static org.forgerock.opendj.ldap.Functions.byteStringToGeneralizedTime;
import static org.forgerock.opendj.ldap.Functions.byteStringToLong;
import static org.forgerock.opendj.ldap.Functions.byteStringToString;
import static org.forgerock.opendj.ldap.schema.CoreSchema.getBooleanSyntax;
import static org.forgerock.opendj.ldap.schema.CoreSchema.getGeneralizedTimeSyntax;
import static org.forgerock.opendj.ldap.schema.CoreSchema.getIntegerSyntax;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.forgerock.json.JsonValue;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.GeneralizedTime;
import org.forgerock.opendj.ldap.LinkedAttribute;
import org.forgerock.opendj.ldap.schema.Syntax;
import org.forgerock.util.Function;
import org.forgerock.util.promise.NeverThrowsException;

/**
 * Internal utility methods.
 */
final class Utils {

    private static final Function<Object, ByteString, NeverThrowsException> BASE64_TO_BYTESTRING =
            new Function<Object, ByteString, NeverThrowsException>() {
                @Override
                public ByteString apply(final Object value) {
                    return ByteString.valueOfBase64(String.valueOf(value));
                }
            };

    private static final Function<ByteString, String, NeverThrowsException> BYTESTRING_TO_BASE64 =
            new Function<ByteString, String, NeverThrowsException>() {
                @Override
                public String apply(final ByteString value) {
                    return value.toBase64String();
                }
            };


    static Object attributeToJson(final Attribute a) {
        final Function<ByteString, Object, NeverThrowsException> f = byteStringToJson(a.getAttributeDescription());
        final boolean isSingleValued = a.getAttributeDescription().getAttributeType().isSingleValue();
        return isSingleValued ? a.parse().as(f) : asList(a.parse().asSetOf(f));
    }

    static Function<Object, ByteString, NeverThrowsException> base64ToByteString() {
        return BASE64_TO_BYTESTRING;
    }

    static Function<ByteString, String, NeverThrowsException> byteStringToBase64() {
        return BYTESTRING_TO_BASE64;
    }

    static Function<ByteString, Object, NeverThrowsException> byteStringToJson(final AttributeDescription ad) {
        return new Function<ByteString, Object, NeverThrowsException>() {
            @Override
            public Object apply(final ByteString value) {
                final Syntax syntax = ad.getAttributeType().getSyntax();
                if (syntax.equals(getBooleanSyntax())) {
                    return byteStringToBoolean().apply(value);
                } else if (syntax.equals(getIntegerSyntax())) {
                    return byteStringToLong().apply(value);
                } else if (syntax.equals(getGeneralizedTimeSyntax())) {
                    return printDateTime(byteStringToGeneralizedTime().apply(value)
                            .toCalendar());
                } else {
                    return byteStringToString().apply(value);
                }
            }
        };
    }

    static <T> T ensureNotNull(final T object) {
        if (object == null) {
            throw new NullPointerException();
        }
        return object;
    }

    static <T> T ensureNotNull(final T object, final String message) {
        if (object == null) {
            throw new NullPointerException(message);
        }
        return object;
    }

    static String getAttributeName(final Attribute a) {
        return a.getAttributeDescription().withoutOption("binary").toString();
    }

    /**
     * Stub formatter for i18n strings.
     *
     * @param format
     *            The format string.
     * @param args
     *            The string arguments.
     * @return The formatted string.
     */
    static String i18n(final String format, final Object... args) {
        return String.format(format, args);
    }

    static boolean isJSONPrimitive(final Object value) {
        return value instanceof String || value instanceof Boolean || value instanceof Number;
    }

    static boolean isNullOrEmpty(final JsonValue v) {
        return v == null || v.isNull() || (v.isList() && v.size() == 0);
    }

    static Attribute jsonToAttribute(final Object value, final AttributeDescription ad) {
        return jsonToAttribute(value, ad, jsonToByteString(ad));
    }

    static Attribute jsonToAttribute(final Object value, final AttributeDescription ad,
            final Function<Object, ByteString, NeverThrowsException> f) {
        if (isJSONPrimitive(value)) {
            return new LinkedAttribute(ad, f.apply(value));
        } else if (value instanceof Collection<?>) {
            final Attribute a = new LinkedAttribute(ad);
            for (final Object o : (Collection<?>) value) {
                a.add(f.apply(o));
            }
            return a;
        } else {
            throw new IllegalArgumentException("Unrecognized type of JSON value: "
                    + value.getClass().getName());
        }
    }

    static Function<Object, ByteString, NeverThrowsException> jsonToByteString(final AttributeDescription ad) {
        return new Function<Object, ByteString, NeverThrowsException>() {
            @Override
            public ByteString apply(final Object value) {
                if (isJSONPrimitive(value)) {
                    final Syntax syntax = ad.getAttributeType().getSyntax();
                    if (syntax.equals(getGeneralizedTimeSyntax())) {
                        return ByteString.valueOfObject(GeneralizedTime.valueOf(parseDateTime(value.toString())));
                    } else {
                        return ByteString.valueOfObject(value);
                    }
                } else {
                    throw new IllegalArgumentException("Unrecognized type of JSON value: "
                            + value.getClass().getName());
                }
            }
        };
    }

    static Filter toFilter(final boolean value) {
        return value ? Filter.alwaysTrue() : Filter.alwaysFalse();
    }

    static Filter toFilter(final FilterType type, final String ldapAttribute, final ByteString valueAssertion) {
        switch (type) {
        case CONTAINS:
            return Filter.substrings(ldapAttribute, null, Collections.singleton(valueAssertion), null);
        case STARTS_WITH:
            return Filter.substrings(ldapAttribute, valueAssertion, null, null);
        case EQUAL_TO:
            return Filter.equality(ldapAttribute, valueAssertion);
        case GREATER_THAN:
            return Filter.greaterThan(ldapAttribute, valueAssertion);
        case GREATER_THAN_OR_EQUAL_TO:
            return Filter.greaterOrEqual(ldapAttribute, valueAssertion);
        case LESS_THAN:
            return Filter.lessThan(ldapAttribute, valueAssertion);
        case LESS_THAN_OR_EQUAL_TO:
            return Filter.lessOrEqual(ldapAttribute, valueAssertion);
        case PRESENT:
            return Filter.present(ldapAttribute);
        case EXTENDED:
        default:
            return alwaysFalse(); // Not supported.
        }
    }

    static String toLowerCase(final String s) {
        return s != null ? s.toLowerCase(Locale.ENGLISH) : null;
    }

    private static <T> List<T> asList(final Collection<T> c) {
        if (c instanceof List) {
            return (List<T>) c;
        }
        return new ArrayList<>(c);
    }

    /** Prevent instantiation. */
    private Utils() {
        // No implementation required.
    }

}
