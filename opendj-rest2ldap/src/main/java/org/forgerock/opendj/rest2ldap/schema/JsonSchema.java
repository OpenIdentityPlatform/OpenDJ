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
 */
package org.forgerock.opendj.rest2ldap.schema;

import static com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_COMMENTS;
import static com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_SINGLE_QUOTES;
import static com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS;
import static java.util.Collections.emptyList;
import static org.forgerock.opendj.ldap.schema.Schema.getCoreSchema;
import static org.forgerock.opendj.rest2ldap.Rest2ldapMessages.ERR_JSON_IO_ERROR;
import static org.forgerock.opendj.rest2ldap.Rest2ldapMessages.ERR_JSON_PARSE_ERROR;
import static org.forgerock.opendj.rest2ldap.schema.JsonSchema.ValidationPolicy.LENIENT;
import static org.forgerock.opendj.rest2ldap.schema.JsonSchema.ValidationPolicy.STRICT;
import static org.forgerock.util.Options.defaultOptions;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.forgerock.opendj.ldap.schema.MatchingRuleImpl;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.forgerock.opendj.ldap.schema.Syntax;
import org.forgerock.util.Function;
import org.forgerock.util.Option;
import org.forgerock.util.Options;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Utility methods for obtaining JSON syntaxes and matching rules. See the package documentation for more detail.
 */
public final class JsonSchema {
    /** JSON value validation policies. */
    public enum ValidationPolicy {
        /** JSON validation policy requiring strict conformance to RFC 7159. */
        STRICT(new ObjectMapper()),
        /**
         * JSON validation policy requiring conformance to RFC 7159 with the following exceptions: 1) comments are
         * allowed, 2) single quotes may be used instead of double quotes, and 3) unquoted control characters are
         * allowed in strings.
         */
        LENIENT(new ObjectMapper().enable(ALLOW_COMMENTS)
                                  .enable(ALLOW_SINGLE_QUOTES)
                                  .enable(ALLOW_UNQUOTED_CONTROL_CHARS)),
        /** JSON validation policy which does not perform any validation. */
        DISABLED(null);

        private final ObjectMapper objectMapper;

        ValidationPolicy(final ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        final JsonFactory getJsonFactory() {
            return objectMapper.getFactory();
        }

        final ObjectMapper getObjectMapper() {
            return objectMapper;
        }
    }

    /**
     * Schema option controlling syntax validation for JSON based attributes. By default this compatibility option
     * is set to {@link ValidationPolicy#STRICT}.
     */
    public static final Option<ValidationPolicy> VALIDATION_POLICY = Option.withDefault(STRICT);
    /**
     * Matching rule option controlling whether JSON string comparisons should be case-sensitive. By default this
     * compatibility option is set to {@code false} meaning that case will be ignored.
     * <p>
     * This option must be provided when constructing a JSON matching rule using {@link
     * #newJsonQueryEqualityMatchingRuleImpl}, and cannot be overridden at the schema level.
     */
    public static final Option<Boolean> CASE_SENSITIVE_STRINGS = Option.withDefault(false);
    /**
     * Matching rule option controlling whether JSON string comparisons should ignore white-space. By default this
     * compatibility option is set to {@code true} meaning that leading and trailing white-space will be ignored and
     * intermediate white-space will be reduced to a single white-space character.
     * <p>
     * This option must be provided when constructing a JSON matching rule using {@link
     * #newJsonQueryEqualityMatchingRuleImpl}, and cannot be overridden at the schema level.
     */
    public static final Option<Boolean> IGNORE_WHITE_SPACE = Option.withDefault(true);
    /**
     * Matching rule option controlling which JSON fields should be indexed by the matching rule. By default all
     * fields will be indexed. To restrict the set of indexed fields specify a list whose values are wild-card
     * patterns for matching against JSON pointers. Patterns are JSON pointers where "*" represents zero or more
     * characters in a single path element, and "**" represents any number of path elements. For example:
     *
     * <table valign="top">
     *     <tr><th>Pattern</th><th>Matches</th><th>Doesn't match</th></tr>
     *     <tr><td>/aaa/bbb/ccc</td><td>/aaa/bbb/ccc</td><td>/aaa/bbb/ccc/ddd<br/>/aaa/bbb/cccc</td></tr>
     *     <tr><td>/aaa/b&#x002A;/ccc</td><td>/aaa/bbb/ccc<br/>/aaa/bxx/ccc</td><td>/aaa/xxx/ccc<br/>/aaa/bbb</td></tr>
     *     <tr><td>/aaa/&#x002A;&#x002A;/ddd</td><td>/aaa/ddd<br/>/aaa/xxx/yyy/ddd</td><td>/aaa/bbb/ccc</td></tr>
     *     <tr><td>/aaa/&#x002A;&#x002A;</td><td>/aaa<br/>/aaa/bbb<br/>/aaa/bbb/ccc<br/></td><td>/aa</td></tr>
     * </table>
     */
    @SuppressWarnings("unchecked")
    public static final Option<Collection<String>> INDEXED_FIELD_PATTERNS =
            (Option) Option.of(Collection.class, emptyList());
    /** The OID of the JSON attribute syntax. */
    static final String SYNTAX_JSON_OID = "1.3.6.1.4.1.36733.2.1.3.1";
    /** The description of the JSON attribute syntax. */
    static final String SYNTAX_JSON_DESCRIPTION = "Json";
    /** The OID of the JSON query attribute syntax. */
    static final String SYNTAX_JSON_QUERY_OID = "1.3.6.1.4.1.36733.2.1.3.2";
    /** The description of the JSON query attribute syntax. */
    static final String SYNTAX_JSON_QUERY_DESCRIPTION = "Json Query";
    /** The OID of the case insensitive JSON query equality matching rule. */
    static final String EMR_CASE_IGNORE_JSON_QUERY_OID = "1.3.6.1.4.1.36733.2.1.4.1";
    /** The name of the case insensitive JSON query equality matching rule. */
    static final String EMR_CASE_IGNORE_JSON_QUERY_NAME = "caseIgnoreJsonQueryMatch";
    /** The OID of the case sensitive JSON query equality matching rule. */
    static final String EMR_CASE_EXACT_JSON_QUERY_OID = "1.3.6.1.4.1.36733.2.1.4.2";
    /** The name of the case sensitive JSON query equality matching rule. */
    static final String EMR_CASE_EXACT_JSON_QUERY_NAME = "caseExactJsonQueryMatch";
    private static final Syntax JSON_SYNTAX;
    private static final Syntax JSON_QUERY_SYNTAX;
    private static final MatchingRule CASE_IGNORE_JSON_QUERY_MATCHING_RULE;
    private static final MatchingRule CASE_EXACT_JSON_QUERY_MATCHING_RULE;
    private static final Function<ByteString, Object, LocalizedIllegalArgumentException> BYTESTRING_TO_JSON =
            new Function<ByteString, Object, LocalizedIllegalArgumentException>() {
                @Override
                public Object apply(final ByteString value) {
                    try (final InputStream inputStream = value.asReader().asInputStream()) {
                        return LENIENT.getObjectMapper().readValue(inputStream, Object.class);
                    } catch (final IOException e) {
                        throw new LocalizedIllegalArgumentException(jsonParsingException(e));
                    }
                }
            };

    static LocalizableMessage jsonParsingException(final IOException e) {
        if (e instanceof JsonProcessingException) {
            final JsonProcessingException jpe = (JsonProcessingException) e;
            if (jpe.getLocation() != null) {
                return ERR_JSON_PARSE_ERROR.get(jpe.getLocation().getLineNr(),
                                                jpe.getLocation().getColumnNr(),
                                                jpe.getOriginalMessage());
            }
        }
        return ERR_JSON_IO_ERROR.get(e.getMessage());
    }

    private static final Function<Object, ByteString, JsonProcessingException> JSON_TO_BYTESTRING =
            new Function<Object, ByteString, JsonProcessingException>() {
                @Override
                public ByteString apply(final Object value) throws JsonProcessingException {
                    return ByteString.wrap(LENIENT.getObjectMapper().writeValueAsBytes(value));
                }
            };

    static {
        final Schema schema = addJsonSyntaxesAndMatchingRulesToSchema(new SchemaBuilder(getCoreSchema())).toSchema();
        JSON_SYNTAX = schema.getSyntax(SYNTAX_JSON_OID);
        JSON_QUERY_SYNTAX = schema.getSyntax(SYNTAX_JSON_QUERY_OID);
        CASE_IGNORE_JSON_QUERY_MATCHING_RULE = schema.getMatchingRule(EMR_CASE_IGNORE_JSON_QUERY_OID);
        CASE_EXACT_JSON_QUERY_MATCHING_RULE = schema.getMatchingRule(EMR_CASE_EXACT_JSON_QUERY_OID);
    }

    /**
     * Returns the JSON attribute syntax having the OID 1.3.6.1.4.1.36733.2.1.3.1. Attribute values of this syntax
     * must be valid JSON. Use the {@link #VALIDATION_POLICY} schema option to control the degree of syntax
     * enforcement. By default JSON attributes will support equality matching using the
     * {@link #getCaseIgnoreJsonQueryMatchingRule() jsonQueryMatch} matching rule, although this may be overridden
     * when defining individual attribute types.
     *
     * @return The JSON attribute syntax having the OID 1.3.6.1.4.1.36733.2.1.3.1.
     */
    public static Syntax getJsonSyntax() {
        return JSON_SYNTAX;
    }

    /**
     * Returns the JSON Query attribute syntax having the OID 1.3.6.1.4.1.36733.2.1.3.2. Attribute values of this
     * syntax must be valid CREST JSON {@link org.forgerock.util.query.QueryFilter query filter} strings as
     * defined in {@link org.forgerock.util.query.QueryFilterParser}.
     *
     * @return The JSON Query attribute syntax having the OID 1.3.6.1.4.1.36733.2.1.3.2.
     */
    public static Syntax getJsonQuerySyntax() {
        return JSON_QUERY_SYNTAX;
    }

    /**
     * Returns the {@code jsonQueryMatch} matching rule having the OID 1.3.6.1.4.1.36733.2.1.4.1. The
     * matching rule's assertion syntax is a {@link #getJsonQuerySyntax() CREST JSON query filter}. This matching
     * rule will ignore case when comparing JSON strings as well as ignoring white-space. In addition, all JSON
     * fields will be indexed if indexing is enabled.
     *
     * @return The @code jsonQueryMatch} matching rule having the OID 1.3.6.1.4.1.36733.2.1.4.1.
     */
    public static MatchingRule getCaseIgnoreJsonQueryMatchingRule() {
        return CASE_IGNORE_JSON_QUERY_MATCHING_RULE;
    }

    /**
     * Returns the {@code jsonQueryMatch} matching rule having the OID 1.3.6.1.4.1.36733.2.1.4.2. The
     * matching rule's assertion syntax is a {@link #getJsonQuerySyntax() CREST JSON query filter}. This matching
     * rule will ignore case when comparing JSON strings as well as ignoring white-space. In addition, all JSON
     * fields will be indexed if indexing is enabled.
     *
     * @return The @code jsonQueryMatch} matching rule having the OID 1.3.6.1.4.1.36733.2.1.4.2.
     */
    public static MatchingRule getCaseExactJsonQueryMatchingRule() {
        return CASE_EXACT_JSON_QUERY_MATCHING_RULE;
    }

    /**
     * Creates a new custom JSON query equality matching rule implementation with the provided matching rule name and
     * options. This method should be used when creating custom JSON matching rules whose behavior differs from
     * {@link #getCaseIgnoreJsonQueryMatchingRule()}.
     *
     * @param matchingRuleName
     *         The name of the matching rule. This will be used as the index ID in attribute indexes so it must not
     *         collide with other indexes identifiers.
     * @param options
     *         The options controlling the behavior of the matching rule.
     * @return The new custom JSON query equality matching rule implementation.
     * @see #CASE_SENSITIVE_STRINGS
     * @see #IGNORE_WHITE_SPACE
     */
    public static MatchingRuleImpl newJsonQueryEqualityMatchingRuleImpl(final String matchingRuleName,
                                                                        final Options options) {
        return new JsonQueryEqualityMatchingRuleImpl(matchingRuleName, options);
    }

    /**
     * Adds the syntaxes and matching rules required by for JSON attribute support to the provided schema builder.
     *
     * @param builder
     *         The schema builder to which the schema elements should be added.
     * @return The schema builder.
     */
    public static SchemaBuilder addJsonSyntaxesAndMatchingRulesToSchema(final SchemaBuilder builder) {
        builder.buildSyntax(SYNTAX_JSON_OID)
               .description(SYNTAX_JSON_DESCRIPTION)
               .implementation(new JsonSyntaxImpl())
               .extraProperties("X-ORIGIN", "OpenDJ Directory Server")
               .addToSchema();

        builder.buildSyntax(SYNTAX_JSON_QUERY_OID)
               .description(SYNTAX_JSON_QUERY_DESCRIPTION)
               .implementation(new JsonQuerySyntaxImpl())
               .extraProperties("X-ORIGIN", "OpenDJ Directory Server")
               .addToSchema();

        final JsonQueryEqualityMatchingRuleImpl caseIgnoreImpl = new JsonQueryEqualityMatchingRuleImpl(
                EMR_CASE_IGNORE_JSON_QUERY_NAME,
                defaultOptions().set(CASE_SENSITIVE_STRINGS, false).set(IGNORE_WHITE_SPACE, true));
        builder.buildMatchingRule(EMR_CASE_IGNORE_JSON_QUERY_OID)
               .names(EMR_CASE_IGNORE_JSON_QUERY_NAME)
               .syntaxOID(SYNTAX_JSON_QUERY_OID)
               .extraProperties("X-ORIGIN", "OpenDJ Directory Server")
               .implementation(caseIgnoreImpl)
               .addToSchema();

        final JsonQueryEqualityMatchingRuleImpl caseExactImpl = new JsonQueryEqualityMatchingRuleImpl(
                EMR_CASE_EXACT_JSON_QUERY_NAME,
                defaultOptions().set(CASE_SENSITIVE_STRINGS, true).set(IGNORE_WHITE_SPACE, true));
        builder.buildMatchingRule(EMR_CASE_EXACT_JSON_QUERY_OID)
               .names(EMR_CASE_EXACT_JSON_QUERY_NAME)
               .syntaxOID(SYNTAX_JSON_QUERY_OID)
               .extraProperties("X-ORIGIN", "OpenDJ Directory Server")
               .implementation(caseExactImpl)
               .addToSchema();

        return builder;
    }

    /**
     * Returns a function which parses {@code JSON} values. Invalid values will result in a
     * {@code LocalizedIllegalArgumentException}.
     *
     * @return A function which parses {@code JSON} values.
     */
    public static Function<ByteString, Object, LocalizedIllegalArgumentException> byteStringToJson() {
        return BYTESTRING_TO_JSON;
    }

    /**
     * Returns a function which converts a JSON {@code Object} to a {@code ByteString}.
     *
     * @return A function which converts a JSON {@code Object} to a {@code ByteString}.
     */
    public static Function<Object, ByteString, JsonProcessingException> jsonToByteString() {
        return JSON_TO_BYTESTRING;
    }

    private JsonSchema() {
        // Prevent instantiation.
    }
}
