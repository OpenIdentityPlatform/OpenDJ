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
 *      Portions copyright 2012 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

import static org.forgerock.opendj.ldap.CoreMessages.FUNCTIONS_TO_INTEGER_FAIL;
import static org.forgerock.opendj.ldap.CoreMessages.FUNCTIONS_TO_LONG_FAIL;
import static org.forgerock.opendj.ldap.CoreMessages.WARN_ATTR_SYNTAX_ILLEGAL_BOOLEAN;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.schema.Schema;

import com.forgerock.opendj.util.StaticUtils;

/**
 * Common {@link Function} implementations which may be used when parsing
 * attributes.
 *
 * @see Entry#parseAttribute
 * @see Attribute#parse
 * @see AttributeParser
 */
public final class Functions {

    private static final class FixedFunction<M, N, P> implements Function<M, N, Void> {
        private final Function<M, N, P> function;

        private final P parameter;

        private FixedFunction(final Function<M, N, P> function, final P p) {
            this.function = function;
            this.parameter = p;
        }

        /**
         * {@inheritDoc}
         */
        public N apply(final M value, final Void p) {
            return function.apply(value, parameter);
        }

    }

    private static final Function<ByteString, String, Void> BYTESTRING_TO_STRING =
            new Function<ByteString, String, Void>() {
                public String apply(final ByteString value, final Void p) {
                    return value.toString();
                }
            };

    private static final Function<Object, Object, Void> IDENTITY =
            new Function<Object, Object, Void>() {
                public Object apply(final Object value, final Void p) {
                    return value;
                }
            };

    private static final Function<String, String, Void> NORMALIZE_STRING =
            new Function<String, String, Void>() {
                public String apply(final String value, final Void p) {
                    return StaticUtils.toLowerCase(value).trim();
                }
            };

    private static final Function<Object, ByteString, Void> OBJECT_TO_BYTESTRING =
            new Function<Object, ByteString, Void>() {
                public ByteString apply(final Object value, final Void p) {
                    return ByteString.valueOf(value);
                }
            };

    private static final Function<String, AttributeDescription, Schema> STRING_TO_ATTRIBUTE_DESCRIPTION =
            new Function<String, AttributeDescription, Schema>() {
                public AttributeDescription apply(final String value, final Schema p) {
                    return AttributeDescription.valueOf(value, p);
                }
            };

    private static final Function<String, Boolean, Void> STRING_TO_BOOLEAN =
            new Function<String, Boolean, Void>() {
                public Boolean apply(final String value, final Void p) {
                    final String valueString = StaticUtils.toLowerCase(value);

                    if (valueString.equals("true") || valueString.equals("yes")
                            || valueString.equals("on") || valueString.equals("1")) {
                        return Boolean.TRUE;
                    } else if (valueString.equals("false") || valueString.equals("no")
                            || valueString.equals("off") || valueString.equals("0")) {
                        return Boolean.FALSE;
                    } else {
                        final LocalizableMessage message =
                                WARN_ATTR_SYNTAX_ILLEGAL_BOOLEAN.get(valueString);
                        throw new LocalizedIllegalArgumentException(message);
                    }
                }
            };

    private static final Function<String, DN, Schema> STRING_TO_DN =
            new Function<String, DN, Schema>() {
                public DN apply(final String value, final Schema p) {
                    return DN.valueOf(value, p);
                }
            };

    private static final Function<String, GeneralizedTime, Void> STRING_TO_GENERALIZED_TIME =
            new Function<String, GeneralizedTime, Void>() {
                public GeneralizedTime apply(final String value, final Void p) {
                    return GeneralizedTime.valueOf(value);
                }
            };

    private static final Function<String, Integer, Void> STRING_TO_INTEGER =
            new Function<String, Integer, Void>() {
                public Integer apply(final String value, final Void p) {
                    try {
                        return Integer.valueOf(value);
                    } catch (final NumberFormatException e) {
                        final LocalizableMessage message = FUNCTIONS_TO_INTEGER_FAIL.get(value);
                        throw new LocalizedIllegalArgumentException(message);
                    }
                }
            };

    private static final Function<String, Long, Void> STRING_TO_LONG =
            new Function<String, Long, Void>() {
                public Long apply(final String value, final Void p) {
                    try {
                        return Long.valueOf(value);
                    } catch (final NumberFormatException e) {
                        final LocalizableMessage message = FUNCTIONS_TO_LONG_FAIL.get(value);
                        throw new LocalizedIllegalArgumentException(message);
                    }
                }
            };

    private static final Function<ByteString, AttributeDescription, Schema> BYTESTRING_TO_ATTRIBUTE_DESCRIPTION =
            composeSecondP(byteStringToString(), STRING_TO_ATTRIBUTE_DESCRIPTION);

    private static final Function<ByteString, Boolean, Void> BYTESTRING_TO_BOOLEAN = compose(
            byteStringToString(), STRING_TO_BOOLEAN);

    private static final Function<ByteString, DN, Schema> BYTESTRING_TO_DN = composeSecondP(
            byteStringToString(), STRING_TO_DN);

    private static final Function<ByteString, GeneralizedTime, Void> BYTESTRING_TO_GENERALIZED_TIME =
            compose(byteStringToString(), STRING_TO_GENERALIZED_TIME);

    private static final Function<ByteString, Integer, Void> BYTESTRING_TO_INTEGER = compose(
            byteStringToString(), STRING_TO_INTEGER);

    private static final Function<ByteString, Long, Void> BYTESTRING_TO_LONG = compose(
            byteStringToString(), STRING_TO_LONG);

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
    public static <M, X, N> Function<M, N, Void> compose(final Function<M, X, Void> first,
            final Function<X, N, Void> second) {
        return new Function<M, N, Void>() {
            public N apply(final M value, final Void p) {
                final X tmp = first.apply(value, p);
                return second.apply(tmp, p);
            };
        };
    }

    /**
     * Returns the composition of two functions. The result of the first
     * function will be passed to the second. The first function will be passed
     * an additional parameter.
     *
     * @param <M>
     *            The type of input values transformed by this function.
     * @param <N>
     *            The type of output values returned by this function.
     * @param <X>
     *            The type of intermediate values passed between the two
     *            functions.
     * @param <P>
     *            The type of the additional parameter to the first function's
     *            {@code apply} method. Use {@link java.lang.Void} for functions
     *            that do not need an additional parameter.
     * @param first
     *            The first function which will consume the input.
     * @param second
     *            The second function which will produce the result.
     * @return The composition.
     */
    public static <M, X, N, P> Function<M, N, P> composeFirstP(final Function<M, X, P> first,
            final Function<X, N, Void> second) {
        return new Function<M, N, P>() {
            public N apply(final M value, final P p) {
                final X tmp = first.apply(value, p);
                return second.apply(tmp, null);
            };
        };
    }

    /**
     * Returns the composition of two functions. The result of the first
     * function will be passed to the second. The second function will be passed
     * an additional parameter.
     *
     * @param <M>
     *            The type of input values transformed by this function.
     * @param <N>
     *            The type of output values returned by this function.
     * @param <X>
     *            The type of intermediate values passed between the two
     *            functions.
     * @param <P>
     *            The type of the additional parameter to the second function's
     *            {@code apply} method. Use {@link java.lang.Void} for functions
     *            that do not need an additional parameter.
     * @param first
     *            The first function which will consume the input.
     * @param second
     *            The second function which will produce the result.
     * @return The composition.
     */
    public static <M, X, N, P> Function<M, N, P> composeSecondP(final Function<M, X, Void> first,
            final Function<X, N, P> second) {
        return new Function<M, N, P>() {
            public N apply(final M value, final P p) {
                final X tmp = first.apply(value, null);
                return second.apply(tmp, p);
            };
        };
    }

    /**
     * Returns a function which which always invokes {@code function} with
     * {@code p}.
     *
     * @param <M>
     *            The type of input values transformed by this function.
     * @param <N>
     *            The type of output values return by this function.
     * @param <P>
     *            The type of the additional parameter to this function's
     *            {@code apply} method. Use {@link java.lang.Void} for functions
     *            that do not need an additional parameter.
     * @param function
     *            The function to wrap.
     * @param p
     *            The parameter which will always be passed to {@code function}.
     * @return A function which which always invokes {@code function} with
     *         {@code p}.
     */
    public static <M, N, P> Function<M, N, Void> fixedFunction(final Function<M, N, P> function,
            final P p) {
        return new FixedFunction<M, N, P>(function, p);
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
    public static <M> Function<M, M, Void> identityFunction() {
        return (Function<M, M, Void>) IDENTITY;
    }

    /**
     * Returns a function which converts a {@code String} to lower case using
     * {@link StaticUtils#toLowerCase} and then trims it.
     *
     * @return A function which converts a {@code String} to lower case using
     *         {@link StaticUtils#toLowerCase} and then trims it.
     */
    public static Function<String, String, Void> normalizeString() {
        return NORMALIZE_STRING;
    }

    /**
     * Returns a function which converts an {@code Object} to a
     * {@code ByteString} using the {@link ByteString#valueOf(Object)} method.
     *
     * @return A function which converts an {@code Object} to a
     *         {@code ByteString} .
     */
    public static Function<Object, ByteString, Void> objectToByteString() {
        return OBJECT_TO_BYTESTRING;
    }

    /**
     * Returns a function which parses {@code AttributeDescription}s using the
     * default schema. Invalid values will result in a
     * {@code LocalizedIllegalArgumentException}.
     *
     * @return A function which parses {@code AttributeDescription}s.
     */
    public static Function<String, AttributeDescription, Void> stringToAttributeDescription() {
        return fixedFunction(STRING_TO_ATTRIBUTE_DESCRIPTION, Schema.getDefaultSchema());
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
    public static Function<String, AttributeDescription, Void> stringToAttributeDescription(
            final Schema schema) {
        return fixedFunction(STRING_TO_ATTRIBUTE_DESCRIPTION, schema);
    }

    /**
     * Returns a function which parses {@code Boolean} values. The function will
     * accept the values {@code 0}, {@code false}, {@code no}, {@code off},
     * {@code 1}, {@code true}, {@code yes}, {@code on}. All other values will
     * result in a {@code NumberFormatException}.
     *
     * @return A function which parses {@code Boolean} values.
     */
    public static Function<String, Boolean, Void> stringToBoolean() {
        return STRING_TO_BOOLEAN;
    }

    /**
     * Returns a function which parses {@code DN}s using the default schema.
     * Invalid values will result in a {@code LocalizedIllegalArgumentException}
     * .
     *
     * @return A function which parses {@code DN}s.
     */
    public static Function<String, DN, Void> stringToDN() {
        return fixedFunction(STRING_TO_DN, Schema.getDefaultSchema());
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
    public static Function<String, DN, Void> stringToDN(final Schema schema) {
        return fixedFunction(STRING_TO_DN, schema);
    }

    /**
     * Returns a function which parses generalized time strings. Invalid values
     * will result in a {@code LocalizedIllegalArgumentException}.
     *
     * @return A function which parses generalized time strings.
     */
    public static Function<String, GeneralizedTime, Void> stringToGeneralizedTime() {
        return STRING_TO_GENERALIZED_TIME;
    }

    /**
     * Returns a function which parses {@code Integer} string values. Invalid
     * values will result in a {@code LocalizedIllegalArgumentException}.
     *
     * @return A function which parses {@code Integer} string values.
     */
    public static Function<String, Integer, Void> stringToInteger() {
        return STRING_TO_INTEGER;
    }

    /**
     * Returns a function which parses {@code Long} string values. Invalid
     * values will result in a {@code LocalizedIllegalArgumentException}.
     *
     * @return A function which parses {@code Long} string values.
     */
    public static Function<String, Long, Void> stringToLong() {
        return STRING_TO_LONG;
    }

    /**
     * Returns a function which parses {@code AttributeDescription}s using the
     * default schema. Invalid values will result in a
     * {@code LocalizedIllegalArgumentException}.
     *
     * @return A function which parses {@code AttributeDescription}s.
     */
    public static Function<ByteString, AttributeDescription, Void> byteStringToAttributeDescription() {
        return fixedFunction(BYTESTRING_TO_ATTRIBUTE_DESCRIPTION, Schema.getDefaultSchema());
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
    public static Function<ByteString, AttributeDescription, Void> byteStringToAttributeDescription(
            final Schema schema) {
        return fixedFunction(BYTESTRING_TO_ATTRIBUTE_DESCRIPTION, schema);
    }

    /**
     * Returns a function which parses {@code Boolean} values. The function will
     * accept the values {@code 0}, {@code false}, {@code no}, {@code off},
     * {@code 1}, {@code true}, {@code yes}, {@code on}. All other values will
     * result in a {@code NumberFormatException}.
     *
     * @return A function which parses {@code Boolean} values.
     */
    public static Function<ByteString, Boolean, Void> byteStringToBoolean() {
        return BYTESTRING_TO_BOOLEAN;
    }

    /**
     * Returns a function which parses {@code DN}s using the default schema.
     * Invalid values will result in a {@code LocalizedIllegalArgumentException}
     * .
     *
     * @return A function which parses {@code DN}s.
     */
    public static Function<ByteString, DN, Void> byteStringToDN() {
        return fixedFunction(BYTESTRING_TO_DN, Schema.getDefaultSchema());
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
    public static Function<ByteString, DN, Void> byteStringToDN(final Schema schema) {
        return fixedFunction(BYTESTRING_TO_DN, schema);
    }

    /**
     * Returns a function which parses generalized time strings. Invalid values
     * will result in a {@code LocalizedIllegalArgumentException}.
     *
     * @return A function which parses generalized time strings.
     */
    public static Function<ByteString, GeneralizedTime, Void> byteStringToGeneralizedTime() {
        return BYTESTRING_TO_GENERALIZED_TIME;
    }

    /**
     * Returns a function which parses {@code Integer} string values. Invalid
     * values will result in a {@code LocalizedIllegalArgumentException}.
     *
     * @return A function which parses {@code Integer} string values.
     */
    public static Function<ByteString, Integer, Void> byteStringToInteger() {
        return BYTESTRING_TO_INTEGER;
    }

    /**
     * Returns a function which parses {@code Long} string values. Invalid
     * values will result in a {@code LocalizedIllegalArgumentException}.
     *
     * @return A function which parses {@code Long} string values.
     */
    public static Function<ByteString, Long, Void> byteStringToLong() {
        return BYTESTRING_TO_LONG;
    }

    /**
     * Returns a function which parses a {@code ByteString} as a UTF-8 encoded
     * {@code String}.
     *
     * @return A function which parses the string representation of a
     *         {@code ByteString} as a UTF-8 encoded {@code String}.
     */
    public static Function<ByteString, String, Void> byteStringToString() {
        return BYTESTRING_TO_STRING;
    }

    // Prevent instantiation
    private Functions() {
        // Do nothing.
    }

}
