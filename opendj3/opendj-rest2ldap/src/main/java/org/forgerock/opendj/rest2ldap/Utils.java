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
import static org.forgerock.opendj.ldap.Filter.alwaysFalse;
import static org.forgerock.opendj.ldap.Functions.byteStringToBoolean;
import static org.forgerock.opendj.ldap.Functions.byteStringToGeneralizedTime;
import static org.forgerock.opendj.ldap.Functions.byteStringToLong;
import static org.forgerock.opendj.ldap.Functions.byteStringToString;
import static org.forgerock.opendj.ldap.Functions.fixedFunction;
import static org.forgerock.opendj.ldap.schema.CoreSchema.getBooleanSyntax;
import static org.forgerock.opendj.ldap.schema.CoreSchema.getGeneralizedTimeSyntax;
import static org.forgerock.opendj.ldap.schema.CoreSchema.getIntegerSyntax;
import static org.forgerock.opendj.rest2ldap.Rest2LDAP.asResourceException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import org.forgerock.json.fluent.JsonValue;
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
        private ResourceException exception; // Guarded by latch.
        private final ResultHandler<List<V>> handler;
        private final AtomicInteger latch;
        private final List<V> results;

        private AccumulatingResultHandler(final int size, final ResultHandler<List<V>> handler) {
            if (size <= 0) {
                throw new IllegalStateException();
            }
            this.latch = new AtomicInteger(size);
            this.results = new ArrayList<V>(size);
            this.handler = handler;
        }

        @Override
        public void handleError(final ResourceException e) {
            exception = e;
            latch(); // Volatile write publishes exception.
        }

        @Override
        public void handleResult(final V result) {
            if (result != null) {
                synchronized (results) {
                    results.add(result);
                }
            }
            latch();
        }

        private void latch() {
            /*
             * Invoke the handler once all results have been received. Avoid
             * failing-fast when an error occurs because some in-flight tasks
             * may depend on resources (e.g. connections) which are
             * automatically closed on completion.
             */
            if (latch.decrementAndGet() == 0) {
                if (exception != null) {
                    handler.handleError(exception);
                } else {
                    handler.handleResult(results);
                }
            }
        }
    }

    private static final Function<Object, ByteString, Void> BASE64_TO_BYTESTRING =
            new Function<Object, ByteString, Void>() {
                @Override
                public ByteString apply(final Object value, final Void p) {
                    return ByteString.valueOfBase64(String.valueOf(value));
                }
            };

    private static final Function<ByteString, String, Void> BYTESTRING_TO_BASE64 =
            new Function<ByteString, String, Void>() {
                @Override
                public String apply(final ByteString value, final Void p) {
                    return value.toBase64String();
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

    /**
     * Returns a result handler which can be used to collect the results of
     * {@code size} asynchronous operations. Once all results have been received
     * {@code handler} will be invoked with a list containing the results.
     * Accumulation ignores {@code null} results, so the result list may be
     * smaller than {@code size}. The returned result handler does not
     * fail-fast: it will wait until all results have been received even if an
     * error has been detected. This ensures that asynchronous operations can
     * use resources such as connections which are automatically released
     * (closed) upon completion of the final operation.
     *
     * @param <V>
     *            The type of result to be collected.
     * @param size
     *            The number of expected results.
     * @param handler
     *            The result handler to be invoked when all results have been
     *            received.
     * @return A result handler which can be used to collect the results of
     *         {@code size} asynchronous operations.
     */
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

    static Function<Object, ByteString, Void> base64ToByteString() {
        return BASE64_TO_BYTESTRING;
    }

    static Function<ByteString, String, Void> byteStringToBase64() {
        return BYTESTRING_TO_BASE64;
    }

    static Function<ByteString, Object, AttributeDescription> byteStringToJson() {
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
            final ByteString valueAssertion) {
        final Filter filter;
        switch (type) {
        case CONTAINS:
            filter =
                    Filter.substrings(ldapAttribute, null, Collections.singleton(valueAssertion),
                            null);
            break;
        case STARTS_WITH:
            filter = Filter.substrings(ldapAttribute, valueAssertion, null, null);
            break;
        case EQUAL_TO:
            filter = Filter.equality(ldapAttribute, valueAssertion);
            break;
        case GREATER_THAN:
            filter = Filter.greaterThan(ldapAttribute, valueAssertion);
            break;
        case GREATER_THAN_OR_EQUAL_TO:
            filter = Filter.greaterOrEqual(ldapAttribute, valueAssertion);
            break;
        case LESS_THAN:
            filter = Filter.lessThan(ldapAttribute, valueAssertion);
            break;
        case LESS_THAN_OR_EQUAL_TO:
            filter = Filter.lessOrEqual(ldapAttribute, valueAssertion);
            break;
        case PRESENT:
            filter = Filter.present(ldapAttribute);
            break;
        case EXTENDED:
        default:
            filter = alwaysFalse(); // Not supported.
            break;
        }
        return filter;
    }

    static String toLowerCase(final String s) {
        return s != null ? s.toLowerCase(Locale.ENGLISH) : null;
    }

    /**
     * Returns a result handler which accepts results of type {@code M}, applies
     * the function {@code f} in order to convert the result to an object of
     * type {@code N}, and subsequently invokes {@code handler}. If an
     * unexpected error occurs while performing the transformation, the
     * exception is converted to a {@code ResourceException} before invoking
     * {@code handler.handleError()}.
     *
     * @param <M>
     *            The type of result expected by the returned handler.
     * @param <N>
     *            The type of result expected by {@code handler}.
     * @param f
     *            A function which converts the result of type {@code M} to type
     *            {@code N}.
     * @param handler
     *            A result handler which accepts results of type {@code N}.
     * @return A result handler which accepts results of type {@code M}.
     */
    static <M, N> ResultHandler<M> transform(final Function<M, N, Void> f,
            final ResultHandler<N> handler) {
        return new ResultHandler<M>() {
            @Override
            public void handleError(final ResourceException error) {
                handler.handleError(error);
            }

            @Override
            public void handleResult(final M result) {
                try {
                    handler.handleResult(f.apply(result, null));
                } catch (final Throwable t) {
                    handler.handleError(asResourceException(t));
                }
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
