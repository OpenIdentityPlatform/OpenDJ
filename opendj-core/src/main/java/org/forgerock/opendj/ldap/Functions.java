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
 * Copyright 2009-2010 Sun Microsystems, Inc.
 * Portions copyright 2012-2016 ForgeRock AS.
 */
package org.forgerock.opendj.ldap;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.util.Function;
import org.forgerock.util.promise.NeverThrowsException;

import com.forgerock.opendj.util.StaticUtils;

import static org.forgerock.opendj.ldap.schema.Schema.*;

import static com.forgerock.opendj.ldap.CoreMessages.*;

import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

/**
 * Common {@link Function} implementations which may be used when parsing
 * attributes.
 *
 * @see Entry#parseAttribute
 * @see Attribute#parse
 * @see AttributeParser
 */
public final class Functions {

    private static final Function<ByteString, String, NeverThrowsException> BYTESTRING_TO_STRING =
            new Function<ByteString, String, NeverThrowsException>() {
                @Override
                public String apply(final ByteString value) {
                    return value.toString();
                }
            };

    private static final Function<Object, Object, NeverThrowsException> IDENTITY =
            new Function<Object, Object, NeverThrowsException>() {
                @Override
                public Object apply(final Object value) {
                    return value;
                }
            };

    private static final Function<String, String, NeverThrowsException> NORMALIZE_STRING =
            new Function<String, String, NeverThrowsException>() {
                @Override
                public String apply(final String value) {
                    return StaticUtils.toLowerCase(value).trim();
                }
            };

    private static final Function<Object, ByteString, NeverThrowsException> OBJECT_TO_BYTESTRING =
            new Function<Object, ByteString, NeverThrowsException>() {
                @Override
                public ByteString apply(final Object value) {
                    return ByteString.valueOfObject(value);
                }
            };

    private static final Function<String, Boolean, LocalizedIllegalArgumentException> STRING_TO_BOOLEAN =
            new Function<String, Boolean, LocalizedIllegalArgumentException>() {
                @Override
                public Boolean apply(final String value) {
                    final String valueString = StaticUtils.toLowerCase(value);
                    if ("true".equals(valueString) || "yes".equals(valueString)
                            || "on".equals(valueString) || "1".equals(valueString)) {
                        return Boolean.TRUE;
                    } else if ("false".equals(valueString) || "no".equals(valueString)
                            || "off".equals(valueString) || "0".equals(valueString)) {
                        return Boolean.FALSE;
                    } else {
                        throw new LocalizedIllegalArgumentException(
                                WARN_ATTR_SYNTAX_ILLEGAL_BOOLEAN.get(valueString));
                    }
                }
            };

    private static final Function<String, GeneralizedTime, LocalizedIllegalArgumentException> STRING_TO_GTIME =
            new Function<String, GeneralizedTime, LocalizedIllegalArgumentException>() {
                @Override
                public GeneralizedTime apply(final String value) {
                    return GeneralizedTime.valueOf(value);
                }
            };

    private static final Function<String, Integer, LocalizedIllegalArgumentException> STRING_TO_INTEGER =
            new Function<String, Integer, LocalizedIllegalArgumentException>() {
                @Override
                public Integer apply(final String value) {
                    try {
                        return Integer.valueOf(value);
                    } catch (final NumberFormatException e) {
                        final LocalizableMessage message = FUNCTIONS_TO_INTEGER_FAIL.get(value);
                        throw new LocalizedIllegalArgumentException(message);
                    }
                }
            };

    private static final Function<String, Long, LocalizedIllegalArgumentException> STRING_TO_LONG =
            new Function<String, Long, LocalizedIllegalArgumentException>() {
                @Override
                public Long apply(final String value) {
                    try {
                        return Long.valueOf(value);
                    } catch (final NumberFormatException e) {
                        final LocalizableMessage message = FUNCTIONS_TO_LONG_FAIL.get(value);
                        throw new LocalizedIllegalArgumentException(message);
                    }
                }
            };

    private static final Function<ByteString, Boolean, LocalizedIllegalArgumentException> BYTESTRING_TO_BOOLEAN =
            compose(byteStringToString(), STRING_TO_BOOLEAN);

    private static final Function<ByteString, X509Certificate, LocalizedIllegalArgumentException> BYTESTRING_TO_CERT =
            new Function<ByteString, X509Certificate, LocalizedIllegalArgumentException>() {
                @Override
                public X509Certificate apply(final ByteString value) {
                    try {
                        final CertificateFactory factory = CertificateFactory.getInstance("X.509");
                        return (X509Certificate) factory.generateCertificate(value.asReader().asInputStream());
                    } catch (CertificateException e) {
                        final String head = value.subSequence(0, Math.min(value.length(), 8)).toHexString();
                        throw new LocalizedIllegalArgumentException(FUNCTIONS_TO_CERT_FAIL.get(head), e);
                    }
                }
            };

    private static final Function<ByteString, GeneralizedTime, LocalizedIllegalArgumentException> BYTESTRING_TO_GTIME =
            compose(byteStringToString(), STRING_TO_GTIME);

    private static final Function<ByteString, Integer, LocalizedIllegalArgumentException> BYTESTRING_TO_INTEGER =
            compose(byteStringToString(), STRING_TO_INTEGER);

    private static final Function<ByteString, Long, LocalizedIllegalArgumentException> BYTESTRING_TO_LONG =
            compose(byteStringToString(), STRING_TO_LONG);

    /**
     * Creates a function that returns constant value for any input.
     *
     * @param <M>
     *            The type of input values transformed by this function.
     * @param <N>
     *            The type of output values returned by this function.
     * @param constant
     *            The constant value for the function to return
     * @return A function that always returns constant value.
     */
    public static <M, N> Function<M, N, NeverThrowsException> returns(final N constant) {
        return new Function<M, N, NeverThrowsException>() {
            @Override
            public N apply(M value) {
                return constant;
            }
        };
    }

    /**
     * Returns the composition of two functions. The result of the first
     * function will be passed to the second.
     *
     * @param <M>
     *            The type of input values transformed by this function.
     * @param <N>
     *            The type of output values returned by this function.
     * @param <X>
     *            The type of intermediate values passed between the two
     *            functions.
     * @param <E>
     *            The type of exception thrown by the {@code second} function.
     * @param first
     *            The first function which will consume the input.
     * @param second
     *            The second function which will produce the result.
     * @return The composition.
     */
    public static <M, X, N, E extends Exception> Function<M, N, E> compose(
            final Function<M, X, NeverThrowsException> first, final Function<X, N, E> second) {
        return new Function<M, N, E>() {
            @Override
            public N apply(final M value) throws E {
                return second.apply(first.apply(value));
            }
        };
    }

    /**
     * Returns a function which always returns the value that it was provided
     * with.
     *
     * @param <M>
     *            The type of values transformed by this function.
     * @return A function which always returns the value that it was provided
     *         with.
     */
    @SuppressWarnings("unchecked")
    public static <M> Function<M, M, NeverThrowsException> identityFunction() {
        return (Function<M, M, NeverThrowsException>) IDENTITY;
    }

    /**
     * Returns a function which converts a {@code String} to lower case using
     * {@link StaticUtils#toLowerCase} and then trims it.
     *
     * @return A function which converts a {@code String} to lower case using
     *         {@link StaticUtils#toLowerCase} and then trims it.
     */
    public static Function<String, String, NeverThrowsException> normalizeString() {
        return NORMALIZE_STRING;
    }

    /**
     * Returns a function which converts an {@code Object} to a
     * {@code ByteString} using the {@link ByteString#valueOfObject(Object)} method.
     *
     * @return A function which converts an {@code Object} to a
     *         {@code ByteString} .
     */
    public static Function<Object, ByteString, NeverThrowsException> objectToByteString() {
        return OBJECT_TO_BYTESTRING;
    }

    /**
     * Returns a function which parses {@code AttributeDescription}s using the
     * default schema. Invalid values will result in a
     * {@code LocalizedIllegalArgumentException}.
     *
     * @return A function which parses {@code AttributeDescription}s.
     */
    public static Function<String, AttributeDescription, LocalizedIllegalArgumentException>
    stringToAttributeDescription() {
        return stringToAttributeDescription(getDefaultSchema());
    }

    /**
     * Returns a function which parses {@code AttributeDescription}s using the
     * provided schema. Invalid values will result in a
     * {@code LocalizedIllegalArgumentException}.
     *
     * @param schema
     *            The schema to use for decoding attribute descriptions.
     * @return A function which parses {@code AttributeDescription}s.
     */
    public static Function<String, AttributeDescription, LocalizedIllegalArgumentException>
    stringToAttributeDescription(final Schema schema) {
        return new Function<String, AttributeDescription, LocalizedIllegalArgumentException>() {
            @Override
            public AttributeDescription apply(final String value) {
                return AttributeDescription.valueOf(value, schema);
            }
        };
    }

    /**
     * Returns a function which parses {@code Boolean} values. The function will
     * accept the values {@code 0}, {@code false}, {@code no}, {@code off},
     * {@code 1}, {@code true}, {@code yes}, {@code on}. All other values will
     * result in a {@code LocalizedIllegalArgumentException}.
     *
     * @return A function which parses {@code Boolean} values.
     */
    public static Function<String, Boolean, LocalizedIllegalArgumentException> stringToBoolean() {
        return STRING_TO_BOOLEAN;
    }

    /**
     * Returns a function which parses {@code DN}s using the default schema.
     * Invalid values will result in a {@code LocalizedIllegalArgumentException}
     * .
     *
     * @return A function which parses {@code DN}s.
     */
    public static Function<String, DN, LocalizedIllegalArgumentException> stringToDN() {
        return stringToDN(getDefaultSchema());
    }

    /**
     * Returns a function which parses {@code DN}s using the provided schema.
     * Invalid values will result in a {@code LocalizedIllegalArgumentException}
     * .
     *
     * @param schema
     *            The schema to use for decoding DNs.
     * @return A function which parses {@code DN}s.
     */
    public static Function<String, DN, LocalizedIllegalArgumentException> stringToDN(final Schema schema) {
        return new Function<String, DN, LocalizedIllegalArgumentException>() {
            @Override
            public DN apply(final String value) {
                return DN.valueOf(value, schema);
            }
        };
    }

    /**
     * Returns a function which parses generalized time strings. Invalid values
     * will result in a {@code LocalizedIllegalArgumentException}.
     *
     * @return A function which parses generalized time strings.
     */
    public static Function<String, GeneralizedTime, LocalizedIllegalArgumentException> stringToGeneralizedTime() {
        return STRING_TO_GTIME;
    }

    /**
     * Returns a function which parses {@code Integer} string values. Invalid
     * values will result in a {@code LocalizedIllegalArgumentException}.
     *
     * @return A function which parses {@code Integer} string values.
     */
    public static Function<String, Integer, LocalizedIllegalArgumentException> stringToInteger() {
        return STRING_TO_INTEGER;
    }

    /**
     * Returns a function which parses {@code Long} string values. Invalid
     * values will result in a {@code LocalizedIllegalArgumentException}.
     *
     * @return A function which parses {@code Long} string values.
     */
    public static Function<String, Long, LocalizedIllegalArgumentException> stringToLong() {
        return STRING_TO_LONG;
    }

    /**
     * Returns a function which parses {@code AttributeDescription}s using the
     * default schema. Invalid values will result in a
     * {@code LocalizedIllegalArgumentException}.
     *
     * @return A function which parses {@code AttributeDescription}s.
     */
    public static Function<ByteString, AttributeDescription, LocalizedIllegalArgumentException>
    byteStringToAttributeDescription() {
        return byteStringToAttributeDescription(getDefaultSchema());
    }

    /**
     * Returns a function which parses {@code AttributeDescription}s using the
     * provided schema. Invalid values will result in a
     * {@code LocalizedIllegalArgumentException}.
     *
     * @param schema
     *            The schema to use for decoding attribute descriptions.
     * @return A function which parses {@code AttributeDescription}s.
     */
    public static Function<ByteString, AttributeDescription, LocalizedIllegalArgumentException>
    byteStringToAttributeDescription(final Schema schema) {
        return compose(byteStringToString(), stringToAttributeDescription(schema));
    }

    /**
     * Returns a function which parses {@code Boolean} values. The function will
     * accept the values {@code 0}, {@code false}, {@code no}, {@code off},
     * {@code 1}, {@code true}, {@code yes}, {@code on}. All other values will
     * result in a {@code LocalizedIllegalArgumentException}.
     *
     * @return A function which parses {@code Boolean} values.
     */
    public static Function<ByteString, Boolean, LocalizedIllegalArgumentException> byteStringToBoolean() {
        return BYTESTRING_TO_BOOLEAN;
    }

    /**
     * Returns a function which parses {@code DN}s using the default schema.
     * Invalid values will result in a {@code LocalizedIllegalArgumentException}
     * .
     *
     * @return A function which parses {@code DN}s.
     */
    public static Function<ByteString, DN, LocalizedIllegalArgumentException> byteStringToDN() {
        return byteStringToDN(getDefaultSchema());
    }

    /**
     * Returns a function which parses {@code DN}s using the provided schema.
     * Invalid values will result in a {@code LocalizedIllegalArgumentException}
     * .
     *
     * @param schema
     *            The schema to use for decoding DNs.
     * @return A function which parses {@code DN}s.
     */
    public static Function<ByteString, DN, LocalizedIllegalArgumentException> byteStringToDN(final Schema schema) {
        return compose(byteStringToString(), stringToDN(schema));
    }

    /**
     * Returns a function which parses {@code X509Certificate} values. Invalid values will
     * result in a {@code LocalizedIllegalArgumentException}.
     *
     * @return A function which parses {@code X509Certificate} values.
     */
    public static Function<ByteString, X509Certificate, LocalizedIllegalArgumentException> byteStringToCertificate() {
        return BYTESTRING_TO_CERT;
    }

    /**
     * Returns a function which parses generalized time strings. Invalid values
     * will result in a {@code LocalizedIllegalArgumentException}.
     *
     * @return A function which parses generalized time strings.
     */
    public static Function<ByteString, GeneralizedTime, LocalizedIllegalArgumentException>
    byteStringToGeneralizedTime() {
        return BYTESTRING_TO_GTIME;
    }

    /**
     * Returns a function which parses {@code Integer} string values. Invalid
     * values will result in a {@code LocalizedIllegalArgumentException}.
     *
     * @return A function which parses {@code Integer} string values.
     */
    public static Function<ByteString, Integer, LocalizedIllegalArgumentException> byteStringToInteger() {
        return BYTESTRING_TO_INTEGER;
    }

    /**
     * Returns a function which parses {@code Long} string values. Invalid
     * values will result in a {@code LocalizedIllegalArgumentException}.
     *
     * @return A function which parses {@code Long} string values.
     */
    public static Function<ByteString, Long, LocalizedIllegalArgumentException> byteStringToLong() {
        return BYTESTRING_TO_LONG;
    }

    /**
     * Returns a function which parses a {@code ByteString} as a UTF-8 encoded
     * {@code String}.
     *
     * @return A function which parses the string representation of a
     *         {@code ByteString} as a UTF-8 encoded {@code String}.
     */
    public static Function<ByteString, String, NeverThrowsException> byteStringToString() {
        return BYTESTRING_TO_STRING;
    }

    /** Prevent instantiation. */
    private Functions() {
        // Do nothing.
    }

}
