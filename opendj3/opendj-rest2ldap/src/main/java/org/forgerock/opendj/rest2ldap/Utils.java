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

import static javax.xml.bind.DatatypeConverter.parseDateTime;
import static javax.xml.bind.DatatypeConverter.printDateTime;
import static org.forgerock.opendj.ldap.Functions.byteStringToBoolean;
import static org.forgerock.opendj.ldap.Functions.byteStringToGeneralizedTime;
import static org.forgerock.opendj.ldap.Functions.byteStringToLong;
import static org.forgerock.opendj.ldap.Functions.byteStringToString;
import static org.forgerock.opendj.ldap.Functions.fixedFunction;
import static org.forgerock.opendj.ldap.schema.CoreSchema.getBooleanSyntax;
import static org.forgerock.opendj.ldap.schema.CoreSchema.getGeneralizedTimeSyntax;
import static org.forgerock.opendj.ldap.schema.CoreSchema.getIntegerSyntax;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.ResultHandler;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.Function;
import org.forgerock.opendj.ldap.GeneralizedTime;
import org.forgerock.opendj.ldap.LinkedAttribute;
import org.forgerock.opendj.ldap.schema.Syntax;

/**
 * Internal utility methods.
 */
final class Utils {
    /**
     * Implementation class for {@link #accumulate}.
     *
     * @param <V>
     *            The type of result.
     */
    private static final class AccumulatingResultHandler<V> implements ResultHandler<V> {
        private final ResultHandler<List<V>> handler;
        private final AtomicInteger latch;
        private final List<V> results;

        private AccumulatingResultHandler(final int size, final ResultHandler<List<V>> handler) {
            this.latch = new AtomicInteger(size);
            this.results = new ArrayList<V>(size);
            this.handler = handler;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleError(final ResourceException e) {
            // Ensure that handler is only invoked once.
            if (latch.getAndSet(0) > 0) {
                handler.handleError(e);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleResult(final V result) {
            synchronized (results) {
                results.add(result);
            }
            if (latch.decrementAndGet() == 0) {
                handler.handleResult(results);
            }
        }

    }

    private static final Function<ByteString, String, Void> BYTESTRING_TO_BASE64 =
            new Function<ByteString, String, Void>() {
                @Override
                public String apply(ByteString value, Void p) {
                    return value.toBase64String();
                }
            };

    private static final Function<Object, ByteString, Void> BASE64_TO_BYTESTRING =
            new Function<Object, ByteString, Void>() {
                @Override
                public ByteString apply(Object value, Void p) {
                    return ByteString.valueOfBase64(String.valueOf(value));
                }
            };

    private static final Function<ByteString, Object, AttributeDescription> BYTESTRING_TO_JSON =
            new Function<ByteString, Object, AttributeDescription>() {
                @Override
                public Object apply(final ByteString value, final AttributeDescription ad) {
                    final Syntax syntax = ad.getAttributeType().getSyntax();
                    if (syntax.equals(getBooleanSyntax())) {
                        return byteStringToBoolean().apply(value, null);
                    } else if (syntax.equals(getIntegerSyntax())) {
                        return byteStringToLong().apply(value, null);
                    } else if (syntax.equals(getGeneralizedTimeSyntax())) {
                        return printDateTime(byteStringToGeneralizedTime().apply(value, null)
                                .toCalendar());
                    } else {
                        return byteStringToString().apply(value, null);
                    }
                }
            };

    private static final Function<Object, ByteString, AttributeDescription> JSON_TO_BYTESTRING =
            new Function<Object, ByteString, AttributeDescription>() {
                @Override
                public ByteString apply(final Object value, final AttributeDescription ad) {
                    if (isJSONPrimitive(value)) {
                        final Syntax syntax = ad.getAttributeType().getSyntax();
                        if (syntax.equals(getGeneralizedTimeSyntax())) {
                            return ByteString.valueOf(GeneralizedTime.valueOf(parseDateTime(value
                                    .toString())));
                        } else {
                            return ByteString.valueOf(value);
                        }
                    } else {
                        throw new IllegalArgumentException("Unrecognized type of JSON value: "
                                + value.getClass().getName());
                    }
                }
            };

    static <V> ResultHandler<V> accumulate(final int size, final ResultHandler<List<V>> handler) {
        return new AccumulatingResultHandler<V>(size, handler);
    }

    static Object attributeToJson(final Attribute a) {
        final Function<ByteString, Object, Void> f =
                fixedFunction(BYTESTRING_TO_JSON, a.getAttributeDescription());
        final boolean isSingleValued =
                a.getAttributeDescription().getAttributeType().isSingleValue();
        return isSingleValued ? a.parse().as(f) : asList(a.parse().asSetOf(f));
    }

    static Function<ByteString, Object, AttributeDescription> byteStringToJson() {
        return BYTESTRING_TO_JSON;
    }

    static Function<ByteString, String, Void> byteStringToBase64() {
        return BYTESTRING_TO_BASE64;
    }

    static Function<Object, ByteString, Void> base64ToByteString() {
        return BASE64_TO_BYTESTRING;
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

    static boolean isJSONPrimitive(final Object value) {
        return value instanceof String || value instanceof Boolean || value instanceof Number;
    }

    static Attribute jsonToAttribute(final Object value, final AttributeDescription ad) {
        return jsonToAttribute(value, ad, fixedFunction(jsonToByteString(), ad));
    }

    static Attribute jsonToAttribute(final Object value, final AttributeDescription ad,
            final Function<Object, ByteString, Void> f) {
        if (isJSONPrimitive(value)) {
            return new LinkedAttribute(ad, f.apply(value, null));
        } else if (value instanceof Collection<?>) {
            final Attribute a = new LinkedAttribute(ad);
            for (final Object o : (Collection<?>) value) {
                a.add(f.apply(o, null));
            }
            return a;
        } else {
            throw new IllegalArgumentException("Unrecognized type of JSON value: "
                    + value.getClass().getName());
        }
    }

    static Function<Object, ByteString, AttributeDescription> jsonToByteString() {
        return JSON_TO_BYTESTRING;
    }

    static Filter toFilter(final boolean value) {
        return value ? Filter.alwaysTrue() : Filter.alwaysFalse();
    }

    static Filter toFilter(final Context c, final FilterType type, final String ldapAttribute,
            final Object valueAssertion) {
        final String v = String.valueOf(valueAssertion);
        final Filter filter;
        switch (type) {
        case CONTAINS:
            filter = Filter.substrings(ldapAttribute, null, Collections.singleton(v), null);
            break;
        case STARTS_WITH:
            filter = Filter.substrings(ldapAttribute, v, null, null);
            break;
        case EQUAL_TO:
            filter = Filter.equality(ldapAttribute, v);
            break;
        case GREATER_THAN:
            filter = Filter.greaterThan(ldapAttribute, v);
            break;
        case GREATER_THAN_OR_EQUAL_TO:
            filter = Filter.greaterOrEqual(ldapAttribute, v);
            break;
        case LESS_THAN:
            filter = Filter.lessThan(ldapAttribute, v);
            break;
        case LESS_THAN_OR_EQUAL_TO:
            filter = Filter.lessOrEqual(ldapAttribute, v);
            break;
        case PRESENT:
            filter = Filter.present(ldapAttribute);
            break;
        case EXTENDED:
        default:
            filter = toFilter(false); // Not supported.
            break;
        }
        return filter;
    }

    static String toLowerCase(final String s) {
        return s != null ? s.toLowerCase(Locale.ENGLISH) : null;
    }

    static <M, N> ResultHandler<M> transform(final Function<M, N, Void> f,
            final ResultHandler<N> handler) {
        return new ResultHandler<M>() {
            @Override
            public void handleError(final ResourceException error) {
                handler.handleError(error);
            }

            @Override
            public void handleResult(final M result) {
                handler.handleResult(f.apply(result, null));
            }
        };
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
