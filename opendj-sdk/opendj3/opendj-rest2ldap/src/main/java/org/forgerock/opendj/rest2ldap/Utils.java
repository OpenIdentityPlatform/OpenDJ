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
 * Copyright 2012 ForgeRock AS.
 */
package org.forgerock.opendj.rest2ldap;

import static org.forgerock.opendj.ldap.schema.CoreSchema.getBooleanSyntax;
import static org.forgerock.opendj.ldap.schema.CoreSchema.getGeneralizedTimeSyntax;
import static org.forgerock.opendj.ldap.schema.CoreSchema.getIntegerSyntax;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import javax.xml.bind.DatatypeConverter;

import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.Function;
import org.forgerock.opendj.ldap.Functions;
import org.forgerock.opendj.ldap.schema.Syntax;

/**
 * Internal utility methods.
 */
final class Utils {

    // @Checkstyle:off
    private static final Function<ByteString, Object, Attribute> BYTESTRING_TO_JSON =
            new Function<ByteString, Object, Attribute>() {
        @Override
        public Object apply(final ByteString value, final Attribute a) {
            final Syntax syntax = a.getAttributeDescription().getAttributeType().getSyntax();
            if (syntax.equals(getBooleanSyntax())) {
                return Functions.byteStringToBoolean().apply(value, null);
            } else if (syntax.equals(getIntegerSyntax())) {
                return Functions.byteStringToLong().apply(value, null);
            } else if (syntax.equals(getGeneralizedTimeSyntax())) {
                return DatatypeConverter.printDateTime(Functions.byteStringToGeneralizedTime()
                        .apply(value, null).toCalendar());
            } else if (syntax.isHumanReadable()) {
                return Functions.byteStringToString().apply(value, null);
            } else {
                // Base 64 encoded binary.
                return DatatypeConverter.printBase64Binary(value.toByteArray());
            }
        }
    };
    // @Checkstyle:on

    static Object attributeToJson(final Attribute a) {
        final Function<ByteString, Object, Void> f = Functions.fixedFunction(BYTESTRING_TO_JSON, a);
        final boolean isSingleValued = a.getAttributeDescription().getAttributeType()
                .isSingleValue();
        return isSingleValued ? a.parse().as(f) : asList(a.parse().asSetOf(f));
    }

    static Function<ByteString, Object, Attribute> byteStringToJson() {
        return BYTESTRING_TO_JSON;
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

    static String toLowerCase(final String s) {
        return s != null ? s.toLowerCase(Locale.ENGLISH) : null;
    }

    private static <T> List<T> asList(final Collection<T> c) {
        if (c instanceof List) {
            return (List<T>) c;
        } else {
            return new ArrayList<T>(c);
        }
    }

    // Prevent instantiation.
    private Utils() {
        // No implementation required.
    }

}
