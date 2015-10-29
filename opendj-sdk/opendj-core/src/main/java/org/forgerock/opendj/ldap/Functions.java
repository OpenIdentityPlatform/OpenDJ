/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions copyright 2012-2015 ForgeRock AS.
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
                public String apply(final ByteString value) {
                    return value.toString();
                }
            };

    private static final Function<Object, Object, NeverThrowsException> IDENTITY =
            new Function<Object, Object, NeverThrowsException>() {
                public Object apply(final Object value) {
                    return value;
                }
            };

    private static final Function<String, String, NeverThrowsException> NORMALIZE_STRING =
            new Function<String, String, NeverThrowsException>() {
                public String apply(final String value) {
                    return StaticUtils.toLowerCase(value).trim();
                }
            };

    private static final Function<Object, ByteString, NeverThrowsException> OBJECT_TO_BYTESTRING =
            new Function<Object, ByteString, NeverThrowsException>() {
                public ByteString apply(final Object value) {
                    return ByteString.valueOfObject(value);
                }
            };

    private static final Function<String, Boolean, NeverThrowsException> STRING_TO_BOOLEAN =
            new Function<String, Boolean, NeverThrowsException>() {
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

    private static final Function<String, GeneralizedTime, NeverThrowsException> STRING_TO_GENERALIZED_TIME =
            new Function<String, GeneralizedTime, NeverThrowsException>() {
                public GeneralizedTime apply(final String value) {
                    return GeneralizedTime.valueOf(value);
                }
            };

    private static final Function<String, Integer, NeverThrowsException> STRING_TO_INTEGER =
            new Function<String, Integer, NeverThrowsException>() {
                public Integer apply(final String value) {
                    try {
                        return Integer.valueOf(value);
                    } catch (final NumberFormatException e) {
                        final LocalizableMessage message = FUNCTIONS_TO_INTEGER_FAIL.get(value);
                        throw new LocalizedIllegalArgumentException(message);
                    }
                }
            };

    private static final Function<String, Long, NeverThrowsException> STRING_TO_LONG =
            new Function<String, Long, NeverThrowsException>() {
                public Long apply(final String value) {
                    try {
                        return Long.valueOf(value);
                    } catch (final NumberFormatException e) {
                        final LocalizableMessage message = FUNCTIONS_TO_LONG_FAIL.get(value);
                        throw new LocalizedIllegalArgumentException(message);
                    }
                }
            };

    private static final Function<ByteString, Boolean, NeverThrowsException> BYTESTRING_TO_BOOLEAN = compose(
            byteStringToString(), STRING_TO_BOOLEAN);

    private static final Function<ByteString, GeneralizedTime, NeverThrowsException> BYTESTRING_TO_GENERALIZED_TIME =
            compose(byteStringToString(), STRING_TO_GENERALIZED_TIME);

    private static final Function<ByteString, Integer, NeverThrowsException> BYTESTRING_TO_INTEGER = compose(
            byteStringToString(), STRING_TO_INTEGER);

    private static final Function<ByteString, Long, NeverThrowsException> BYTESTRING_TO_LONG = compose(
            byteStringToString(), STRING_TO_LONG);

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
     * @param first
     *            The first function which will consume the input.
     * @param second
     *            The second function which will produce the result.
     * @return The composition.
     */
    public static <M, X, N> Function<M, N, NeverThrowsException> compose(
            final Function<M, X, NeverThrowsException> first, final Function<X, N, NeverThrowsException> second) {
        return new Function<M, N, NeverThrowsException>() {
            public N apply(final M value) {
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
    public static Function<String, AttributeDescription, NeverThrowsException> stringToAttributeDescription() {
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
    public static Function<String, AttributeDescription, NeverThrowsException> stringToAttributeDescription(
            final Schema schema) {
        return new Function<String, AttributeDescription, NeverThrowsException>() {
            public AttributeDescription apply(final String value) {
                return AttributeDescription.valueOf(value, schema);
            }
        };
    }

    /**
     * Returns a function which parses {@code Boolean} values. The function will
     * accept the values {@code 0}, {@code false}, {@code no}, {@code off},
     * {@code 1}, {@code true}, {@code yes}, {@code on}. All other values will
     * result in a {@code NumberFormatException}.
     *
     * @return A function which parses {@code Boolean} values.
     */
    public static Function<String, Boolean, NeverThrowsException> stringToBoolean() {
        return STRING_TO_BOOLEAN;
    }

    /**
     * Returns a function which parses {@code DN}s using the default schema.
     * Invalid values will result in a {@code LocalizedIllegalArgumentException}
     * .
     *
     * @return A function which parses {@code DN}s.
     */
    public static Function<String, DN, NeverThrowsException> stringToDN() {
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
    public static Function<String, DN, NeverThrowsException> stringToDN(final Schema schema) {
        return new Function<String, DN, NeverThrowsException>() {
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
    public static Function<String, GeneralizedTime, NeverThrowsException> stringToGeneralizedTime() {
        return STRING_TO_GENERALIZED_TIME;
    }

    /**
     * Returns a function which parses {@code Integer} string values. Invalid
     * values will result in a {@code LocalizedIllegalArgumentException}.
     *
     * @return A function which parses {@code Integer} string values.
     */
    public static Function<String, Integer, NeverThrowsException> stringToInteger() {
        return STRING_TO_INTEGER;
    }

    /**
     * Returns a function which parses {@code Long} string values. Invalid
     * values will result in a {@code LocalizedIllegalArgumentException}.
     *
     * @return A function which parses {@code Long} string values.
     */
    public static Function<String, Long, NeverThrowsException> stringToLong() {
        return STRING_TO_LONG;
    }

    /**
     * Returns a function which parses {@code AttributeDescription}s using the
     * default schema. Invalid values will result in a
     * {@code LocalizedIllegalArgumentException}.
     *
     * @return A function which parses {@code AttributeDescription}s.
     */
    public static Function<ByteString, AttributeDescription, NeverThrowsException> byteStringToAttributeDescription() {
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
    public static Function<ByteString, AttributeDescription, NeverThrowsException> byteStringToAttributeDescription(
            final Schema schema) {
        return compose(byteStringToString(), new Function<String, AttributeDescription, NeverThrowsException>() {
            public AttributeDescription apply(final String value) {
                return AttributeDescription.valueOf(value, schema);
            }
        });
    }

    /**
     * Returns a function which parses {@code Boolean} values. The function will
     * accept the values {@code 0}, {@code false}, {@code no}, {@code off},
     * {@code 1}, {@code true}, {@code yes}, {@code on}. All other values will
     * result in a {@code NumberFormatException}.
     *
     * @return A function which parses {@code Boolean} values.
     */
    public static Function<ByteString, Boolean, NeverThrowsException> byteStringToBoolean() {
        return BYTESTRING_TO_BOOLEAN;
    }

    /**
     * Returns a function which parses {@code DN}s using the default schema.
     * Invalid values will result in a {@code LocalizedIllegalArgumentException}
     * .
     *
     * @return A function which parses {@code DN}s.
     */
    public static Function<ByteString, DN, NeverThrowsException> byteStringToDN() {
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
    public static Function<ByteString, DN, NeverThrowsException> byteStringToDN(final Schema schema) {
        return compose(byteStringToString(), new Function<String, DN, NeverThrowsException>() {
            public DN apply(final String value) {
                return DN.valueOf(value, schema);
            }
        });
    }

    /**
     * Returns a function which parses generalized time strings. Invalid values
     * will result in a {@code LocalizedIllegalArgumentException}.
     *
     * @return A function which parses generalized time strings.
     */
    public static Function<ByteString, GeneralizedTime, NeverThrowsException> byteStringToGeneralizedTime() {
        return BYTESTRING_TO_GENERALIZED_TIME;
    }

    /**
     * Returns a function which parses {@code Integer} string values. Invalid
     * values will result in a {@code LocalizedIllegalArgumentException}.
     *
     * @return A function which parses {@code Integer} string values.
     */
    public static Function<ByteString, Integer, NeverThrowsException> byteStringToInteger() {
        return BYTESTRING_TO_INTEGER;
    }

    /**
     * Returns a function which parses {@code Long} string values. Invalid
     * values will result in a {@code LocalizedIllegalArgumentException}.
     *
     * @return A function which parses {@code Long} string values.
     */
    public static Function<ByteString, Long, NeverThrowsException> byteStringToLong() {
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
