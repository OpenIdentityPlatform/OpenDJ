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

import static com.fasterxml.jackson.core.JsonToken.END_ARRAY;
import static com.fasterxml.jackson.core.JsonToken.END_OBJECT;
import static com.forgerock.opendj.util.StringPrepProfile.prepareUnicode;
import static org.forgerock.opendj.ldap.Assertion.UNDEFINED_ASSERTION;
import static org.forgerock.opendj.ldap.schema.CoreSchema.getIntegerMatchingRule;
import static org.forgerock.opendj.rest2ldap.Rest2ldapMessages.*;
import static org.forgerock.opendj.rest2ldap.schema.JsonSchema.CASE_SENSITIVE_STRINGS;
import static org.forgerock.opendj.rest2ldap.schema.JsonSchema.IGNORE_WHITE_SPACE;
import static org.forgerock.opendj.rest2ldap.schema.JsonSchema.INDEXED_FIELD_PATTERNS;
import static org.forgerock.opendj.rest2ldap.schema.JsonSchema.ValidationPolicy.LENIENT;
import static org.forgerock.opendj.rest2ldap.schema.JsonSchema.jsonParsingException;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.forgerock.json.JsonPointer;
import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.QueryFilters;
import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.schema.MatchingRuleImpl;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.spi.IndexQueryFactory;
import org.forgerock.opendj.ldap.spi.Indexer;
import org.forgerock.opendj.ldap.spi.IndexingOptions;
import org.forgerock.util.Options;
import org.forgerock.util.query.QueryFilter;
import org.forgerock.util.query.QueryFilterVisitor;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

/**
 * This class implements the jsonQueryMatch equality matching rule whose assertion syntax is a
 * CREST {@link QueryFilter} and whose string syntax is defined in {@link org.forgerock.util.query.QueryFilterParser}.
 */
final class JsonQueryEqualityMatchingRuleImpl implements MatchingRuleImpl {
    // Separator bytes used when encoding JSON keys. Null sorts before false, sorts before true, etc.
    // Package private for testing.
    private static final int KEY_FIELD_START = 0;
    private static final int KEY_FIELD_END = 1;
    private static final int KEY_TYPE_NULL = 0;
    private static final int KEY_TYPE_FALSE = 1;
    private static final int KEY_TYPE_TRUE = 2;
    private static final int KEY_TYPE_NUMBER = 3;
    private static final int KEY_TYPE_STRING = 4;
    private final String indexID;
    private final boolean ignoreWhiteSpaceInStrings;
    private final boolean caseSensitiveStrings;
    private final List<Pattern> indexedFieldPatterns;
    private final QueryFilterVisitor<ConditionResult, JsonValue, JsonPointer> matcher = new Matcher();
    private final List<? extends Indexer> indexers = Collections.singletonList(new IndexerImpl());

    JsonQueryEqualityMatchingRuleImpl(final String indexID, Options options) {
        this.indexID = indexID;
        this.ignoreWhiteSpaceInStrings = options.get(IGNORE_WHITE_SPACE);
        this.caseSensitiveStrings = options.get(CASE_SENSITIVE_STRINGS);
        this.indexedFieldPatterns = compileWildCardPatterns(options.get(INDEXED_FIELD_PATTERNS));
    }

    private static List<Pattern> compileWildCardPatterns(final Collection<String> wildCardPatterns) {
        final List<Pattern> regexes = new ArrayList<>();
        for (final String wildCardPattern : wildCardPatterns) {
            regexes.add(compileWildCardPattern(wildCardPattern));
        }
        return regexes;
    }

    /**
     * Compiles a wild-card pattern into a regex taking care to normalize percent encoded characters, etc. This
     * method is package private for testing.
     */
    static Pattern compileWildCardPattern(final String wildCardPattern) {
        // Make the pattern easier to parse: replace multi-char sequences with a single char in order to avoid
        // having to maintain state during subsequent parsing phase.
        final char slashStarStar = '\u0000';
        final char starStar = '\u0001';
        final char star = '\u0002';
        final String normalizedPattern = new JsonPointer(wildCardPattern).toString()
                                                                         .replaceAll("/\\*\\*", "" + slashStarStar)
                                                                         .replaceAll("\\*\\*", "" + starStar)
                                                                         .replaceAll("\\*", "" + star);
        final StringBuilder builder = new StringBuilder();
        int elementStart = 0;
        for (int i = 0; i < normalizedPattern.length(); i++) {
            final char c = normalizedPattern.charAt(i);
            if (c <= star) {
                if (elementStart < i) {
                    // Escape and add literal substring.
                    builder.append(Pattern.quote(normalizedPattern.substring(elementStart, i)));
                }
                switch (c) {
                case slashStarStar:
                    builder.append("(/.*)?");
                    break;
                case starStar:
                    builder.append(".*");
                    break;
                case star:
                    builder.append("[^/]*");
                    break;
                }
                elementStart = i + 1;
            }
        }
        if (elementStart < normalizedPattern.length()) {
            // Escape and add remaining literal substring.
            builder.append(Pattern.quote(normalizedPattern.substring(elementStart)));
        }
        return Pattern.compile(builder.toString());
    }

    @Override
    public Assertion getAssertion(final Schema schema, final ByteSequence assertionValue) throws DecodeException {
        final QueryFilter<JsonPointer> queryFilter;
        try {
            queryFilter = QueryFilters.parse(assertionValue.toString());
        } catch (Exception e) {
            throw DecodeException.error(ERR_JSON_QUERY_PARSE_ERROR.get(assertionValue));
        }

        return new Assertion() {
            @Override
            public ConditionResult matches(final ByteSequence normalizedAttributeValue) {
                try (final InputStream inputStream = normalizedAttributeValue.asReader().asInputStream()) {
                    final Object object = LENIENT.getObjectMapper().readValue(inputStream, Object.class);
                    final JsonValue jsonValue = new JsonValue(object);
                    return queryFilter.accept(matcher, jsonValue);
                } catch (IOException e) {
                    // It may be that syntax validation was disabled when the attribute was created.
                    return ConditionResult.FALSE;
                }
            }

            @Override
            public <T> T createIndexQuery(final IndexQueryFactory<T> factory) throws DecodeException {
                return queryFilter.accept(new IndexQueryBuilder<T>(), factory);
            }
        };
    }

    @Override
    public Assertion getSubstringAssertion(final Schema schema, final ByteSequence subInitial,
                                           final List<? extends ByteSequence> subAnyElements,
                                           final ByteSequence subFinal) throws DecodeException {
        return UNDEFINED_ASSERTION;
    }

    @Override
    public Assertion getGreaterOrEqualAssertion(final Schema schema, final ByteSequence value) throws DecodeException {
        return UNDEFINED_ASSERTION;
    }

    @Override
    public Assertion getLessOrEqualAssertion(final Schema schema, final ByteSequence value) throws DecodeException {
        return UNDEFINED_ASSERTION;
    }

    @Override
    public ByteString normalizeAttributeValue(final Schema schema, final ByteSequence value) throws DecodeException {
        // The normalized representation is still valid JSON so that it can be reparsed during assertion matching.
        try (final InputStream inputStream = value.asReader().asInputStream();
             final JsonParser parser = LENIENT.getJsonFactory().createParser(inputStream)) {
            JsonToken jsonToken = parser.nextToken();
            if (jsonToken == null) {
                throw DecodeException.error(ERR_JSON_EMPTY_CONTENT.get());
            }

            final ByteStringBuilder normalizedValue = new ByteStringBuilder(value.length());
            normalizeJsonValue(parser, jsonToken, normalizedValue);
            if (parser.nextToken() != null) {
                throw DecodeException.error(ERR_JSON_TRAILING_CONTENT.get());
            }
            return normalizedValue.toByteString();
        } catch (DecodeException e) {
            throw e;
        } catch (IOException e) {
            throw DecodeException.error(jsonParsingException(e));
        }
    }

    private void normalizeJsonValue(final JsonParser parser, JsonToken jsonToken, final ByteStringBuilder builder)
            throws IOException {
        switch (jsonToken) {
        case START_OBJECT:
            final TreeMap<String, ByteSequence> normalizedObject = new TreeMap<>();
            while (parser.nextToken() != END_OBJECT) {
                final String key = parser.getCurrentName();
                final ByteStringBuilder value = new ByteStringBuilder();
                normalizeJsonValue(parser, parser.nextToken(), value);
                normalizedObject.put(key, value);
            }
            builder.appendByte('{');
            boolean isFirstField = true;
            for (Map.Entry<String, ByteSequence> keyValuePair : normalizedObject.entrySet()) {
                if (!isFirstField) {
                    builder.appendByte(',');
                }
                builder.appendByte('"');
                builder.appendUtf8(keyValuePair.getKey());
                builder.appendByte('"');
                builder.appendByte(':');
                builder.appendBytes(keyValuePair.getValue());
                isFirstField = false;
            }
            builder.appendByte('}');
            break;
        case START_ARRAY:
            builder.appendByte('[');
            boolean isFirstElement = true;
            while ((jsonToken = parser.nextToken()) != END_ARRAY) {
                if (!isFirstElement) {
                    builder.appendByte(',');
                }
                normalizeJsonValue(parser, jsonToken, builder);
                isFirstElement = false;
            }
            builder.appendByte(']');
            break;
        case VALUE_STRING:
            builder.appendByte('"');
            builder.appendUtf8(normalizeString(parser.getText()));
            builder.appendByte('"');
            break;
        case VALUE_NUMBER_INT:
        case VALUE_NUMBER_FLOAT:
            builder.appendUtf8(parser.getNumberValue().toString());
            break;
        case VALUE_TRUE:
        case VALUE_FALSE:
        case VALUE_NULL:
            builder.appendUtf8(parser.getText());
            break;
        case END_OBJECT:
        case END_ARRAY:
        case FIELD_NAME:
        case NOT_AVAILABLE:
        case VALUE_EMBEDDED_OBJECT:
            // Should not happen.
            throw new IllegalStateException();
        }
    }

    /** Normalize strings in a similar manner to LDAP's directory string matching rules. */
    private String normalizeString(final String string) {
        final StringBuilder builder = new StringBuilder(string.length());
        prepareUnicode(builder, ByteString.valueOfUtf8(string), ignoreWhiteSpaceInStrings, !caseSensitiveStrings);
        if (builder.length() == 0 && string.length() > 0) {
            return " ";
        }
        return builder.toString();
    }

    @Override
    public Collection<? extends Indexer> createIndexers(final IndexingOptions options) {
        return indexers;
    }

    private class IndexerImpl implements Indexer {
        @Override
        public String getIndexID() {
            return indexID;
        }

        @Override
        public void createKeys(final Schema schema, final ByteSequence value, final Collection<ByteString> keys)
                throws DecodeException {
            try (final InputStream inputStream = value.asReader().asInputStream();
                 final JsonParser parser = LENIENT.getJsonFactory().createParser(inputStream)) {
                JsonToken jsonToken = parser.nextToken();
                if (jsonToken == null) {
                    throw DecodeException.error(ERR_JSON_EMPTY_CONTENT.get());
                }

                JsonPointer parentJsonPointer = new JsonPointer();
                JsonPointer jsonPointer = new JsonPointer();
                String normalizedJsonPointer = normalizeJsonPointer(jsonPointer);
                final ByteStringBuilder builder = new ByteStringBuilder();
                int depth = 0;
                do {
                    switch (jsonToken) {
                    case START_OBJECT:
                        parentJsonPointer = jsonPointer;
                        depth++;
                        break;
                    case START_ARRAY:
                        // Ignore array indices and instead treat elements as if they were multiple values for the
                        // current pointer.
                        depth++;
                        break;
                    case END_OBJECT:
                        jsonPointer = parentJsonPointer;
                        normalizedJsonPointer = normalizeJsonPointer(jsonPointer);
                        parentJsonPointer = parentJsonPointer.parent();
                        depth--;
                        break;
                    case END_ARRAY:
                        depth--;
                        break;
                    case FIELD_NAME:
                        // Normalize for the pathological case where a field name happens to be a number.
                        jsonPointer = parentJsonPointer.child(parser.getCurrentName());
                        normalizedJsonPointer = normalizeJsonPointer(jsonPointer);
                        break;
                    case VALUE_NULL:
                        if (isFieldIndexed(normalizedJsonPointer)) {
                            createFieldStartIndexKey(normalizedJsonPointer, builder);
                            keys.add(createNullIndexKey(builder));
                        }
                        break;
                    case VALUE_FALSE:
                        if (isFieldIndexed(normalizedJsonPointer)) {
                            createFieldStartIndexKey(normalizedJsonPointer, builder);
                            keys.add(createBooleanIndexKey(builder, false));
                        }
                        break;
                    case VALUE_TRUE:
                        if (isFieldIndexed(normalizedJsonPointer)) {
                            createFieldStartIndexKey(normalizedJsonPointer, builder);
                            keys.add(createBooleanIndexKey(builder, true));
                        }
                        break;
                    case VALUE_NUMBER_INT:
                    case VALUE_NUMBER_FLOAT:
                        if (isFieldIndexed(normalizedJsonPointer)) {
                            createFieldStartIndexKey(normalizedJsonPointer, builder);
                            keys.add(createNumberIndexKey(builder, parser.getDecimalValue()));
                        }
                        break;
                    case VALUE_STRING:
                        if (isFieldIndexed(normalizedJsonPointer)) {
                            createFieldStartIndexKey(normalizedJsonPointer, builder);
                            keys.add(createStringIndexKey(builder, parser.getText()));
                        }
                        break;
                    case NOT_AVAILABLE:
                    case VALUE_EMBEDDED_OBJECT:
                        // Should not happen.
                        throw new IllegalStateException();
                    }
                    builder.setLength(0);
                    jsonToken = parser.nextToken();
                } while (depth > 0);

                if (parser.nextToken() != null) {
                    throw DecodeException.error(ERR_JSON_TRAILING_CONTENT.get());
                }
            } catch (DecodeException e) {
                throw e;
            } catch (IOException e) {
                throw DecodeException.error(jsonParsingException(e));
            }
        }

        @Override
        public String keyToHumanReadableString(final ByteSequence key) {
            return key.toByteString().toASCIIString();
        }
    }

    private boolean isFieldIndexed(final String normalizedJsonPointer) {
        // Default behavior is that all fields are indexed.
        if (indexedFieldPatterns.isEmpty()) {
            return true;
        }
        // The field is indexed if it matches any of the configured patterns.
        for (Pattern indexedFieldPattern : indexedFieldPatterns) {
            if (indexedFieldPattern.matcher(normalizedJsonPointer).matches()) {
                return true;
            }
        }
        return false;
    }

    private ByteString createFieldStartIndexKey(final String normalizedJsonPointer) {
        final ByteStringBuilder builder = new ByteStringBuilder(normalizedJsonPointer.length() + 1);
        createFieldStartIndexKey(normalizedJsonPointer, builder);
        return builder.toByteString();
    }

    private void createFieldStartIndexKey(final String normalizedJsonPointer, final ByteStringBuilder builder) {
        builder.appendUtf8(normalizedJsonPointer);
        builder.appendByte(KEY_FIELD_START);
    }

    private ByteString createFieldEndIndexKey(final String normalizedJsonPointer) {
        final ByteStringBuilder builder = new ByteStringBuilder();
        builder.appendUtf8(normalizedJsonPointer);
        builder.appendByte(KEY_FIELD_END);
        return builder.toByteString();
    }

    // Package private for testing.
    ByteString createIndexKey(final String normalizedJsonPointer, final Object value) {
        final ByteString fieldKey = createFieldStartIndexKey(normalizedJsonPointer);
        return createIndexKey(fieldKey, value).toByteString();
    }

    private ByteSequence createIndexKey(final ByteString fieldKey, final Object value) {
        final ByteStringBuilder builder = new ByteStringBuilder(fieldKey);
        if (value == null) {
            return createNullIndexKey(builder);
        } else if (value instanceof Number) {
            final Double doubleValue = ((Number) value).doubleValue();
            return createNumberIndexKey(builder, BigDecimal.valueOf(doubleValue));
        } else if (value instanceof Boolean) {
            final Boolean booleanValue = (Boolean) value;
            return createBooleanIndexKey(builder, booleanValue);
        } else { // String or something unexpected in which case convert it to a string.
            final String stringValue = normalizeString(value.toString());
            return createStringIndexKey(builder, stringValue);
        }
    }

    private ByteString createStringIndexKey(final ByteStringBuilder builder, final String string) {
        builder.appendByte(KEY_TYPE_STRING);
        builder.appendUtf8(normalizeString(string));
        return builder.toByteString();
    }

    private ByteString createNumberIndexKey(final ByteStringBuilder builder, final BigDecimal number) {
        // Re-use the integer matching rule in order to have a natural sort order. To do this we need
        // to first convert floating point numbers to an integer. We multiply by 10^6 in order to
        // preserve 6 decimal places of accuracy.
        builder.appendByte(KEY_TYPE_NUMBER);
        final ByteString micros = ByteString.valueOfObject(number.movePointRight(6).toBigInteger());
        try {
            builder.appendBytes(getIntegerMatchingRule().normalizeAttributeValue(micros));
        } catch (DecodeException e) {
            throw new RuntimeException(e); // Shouldn't happen since we know the value is valid.
        }
        return builder.toByteString();
    }

    private ByteString createBooleanIndexKey(final ByteStringBuilder builder, final boolean b) {
        builder.appendByte(b ? KEY_TYPE_TRUE : KEY_TYPE_FALSE);
        return builder.toByteString();
    }

    private ByteString createNullIndexKey(final ByteStringBuilder builder) {
        builder.appendByte(KEY_TYPE_NULL);
        return builder.toByteString();
    }

    /**
     * We need to strip out numeric JSON pointer elements in order to cope with our lack of wild-card support when
     * querying JSON arrays. Given the following JSON value:
     * <pre>
     *     {
     *         "array": [ "value1", "value2", "value3" ],
     *         "string": "value4",
     *         "123": "legal!"
     *     }
     * </pre>
     * We want to be able to perform queries against multi-valued fields without having to know the index of the
     * element that we are looking for. For example, the wild-card filter {@code /array/* eq 'value2'} should match the
     * above object because one of the array elements matches 'value2'. Unfortunately, there is no explicit wild-card
     * support for JSON pointers, so we support it implicitly instead. Thus the filter {@code /array eq 'value2'}
     * matches. We need to support explicit indexing though as well, so {@code /array/2 eq 'value2'} should match as
     * well. This makes indexing a bit trickier, since we effectively need to index each value twice, once with the
     * array index and once without.
     * <p/>
     * Indexes can return false positives, so a simple solution is to remove any JSON pointer tokens that look like
     * array indices. This even works in the rare case where object keys are numbers. The above object yields the
     * following keys:
     * <pre>
     *     array KEY_FIELD_START KEY_TYPE_STRING value1
     *     array KEY_FIELD_START KEY_TYPE_STRING value2
     *     array KEY_FIELD_START KEY_TYPE_STRING value3
     *     string KEY_FIELD_START KEY_TYPE_STRING value4
     *     &lt;empty&gt; KEY_FIELD_START KEY_TYPE_STRING legal!
     * </pre>
     */
    private String normalizeJsonPointer(final JsonPointer jsonPointer) {
        // Ensure that returned string has same encoding as JsonPointer.toString().
        for (int i = 0; i < jsonPointer.size(); i++) {
            final String token = jsonPointer.get(i);
            if (isArrayIndex(token)) {
                final ArrayList<String> tokens = new ArrayList<>(jsonPointer.size());
                for (int j = 0; j < jsonPointer.size(); j++) {
                    final String tokenj = jsonPointer.get(j);
                    if (j == i || (j > i && isArrayIndex(tokenj))) {
                        continue;
                    }
                    tokens.add(tokenj);
                }
                return new JsonPointer(tokens.toArray(new String[0])).toString();
            }
        }
        return jsonPointer.toString();
    }

    private boolean isArrayIndex(final String token) {
        final int length = token.length();
        if (length == 0) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            final char c = token.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    private class IndexQueryBuilder<T> implements QueryFilterVisitor<T, IndexQueryFactory<T>, JsonPointer> {
        @Override
        public T visitAndFilter(final IndexQueryFactory<T> indexQueryFactory,
                                final List<QueryFilter<JsonPointer>> subFilters) {
            final List<T> subQueries = new ArrayList<>(subFilters.size());
            for (QueryFilter<JsonPointer> subFilter : subFilters) {
                subQueries.add(subFilter.accept(this, indexQueryFactory));
            }
            return indexQueryFactory.createIntersectionQuery(subQueries);
        }

        @Override
        public T visitBooleanLiteralFilter(final IndexQueryFactory<T> indexQueryFactory, final boolean value) {
            return value ? indexQueryFactory.createMatchAllQuery()
                         : indexQueryFactory.createUnionQuery(Collections.<T>emptySet());
        }

        @Override
        public T visitContainsFilter(final IndexQueryFactory<T> indexQueryFactory, final JsonPointer field,
                                     final Object valueAssertion) {
            // Not supported yet, but we can at least narrow down the set of candidates to those entries containing
            // the requested field.
            return visitPresentFilter(indexQueryFactory, field);
        }

        @Override
        public T visitEqualsFilter(final IndexQueryFactory<T> indexQueryFactory, final JsonPointer field,
                                   final Object valueAssertion) {
            final String normalizedJsonPointer = normalizeJsonPointer(field);
            if (!isFieldIndexed(normalizedJsonPointer)) {
                return indexQueryFactory.createMatchAllQuery();
            }
            final ByteString fieldKey = createFieldStartIndexKey(normalizedJsonPointer);
            final ByteSequence key = createIndexKey(fieldKey, valueAssertion);
            return indexQueryFactory.createExactMatchQuery(indexID, key);
        }

        @Override
        public T visitExtendedMatchFilter(final IndexQueryFactory<T> indexQueryFactory, final JsonPointer field,
                                          final String operator, final Object valueAssertion) {
            // Not supported, so the filter does not match any entries.
            return indexQueryFactory.createUnionQuery(Collections.<T>emptySet());
        }

        @Override
        public T visitGreaterThanFilter(final IndexQueryFactory<T> indexQueryFactory, final JsonPointer field,
                                        final Object valueAssertion) {
            final String normalizedJsonPointer = normalizeJsonPointer(field);
            if (!isFieldIndexed(normalizedJsonPointer)) {
                return indexQueryFactory.createMatchAllQuery();
            }
            final ByteString fieldKey = createFieldStartIndexKey(normalizedJsonPointer);
            final ByteSequence startKey = createIndexKey(fieldKey, valueAssertion);
            final ByteString endKey = createFieldEndIndexKey(normalizedJsonPointer);
            return indexQueryFactory.createRangeMatchQuery(indexID, startKey, endKey, false, false);
        }

        @Override
        public T visitGreaterThanOrEqualToFilter(final IndexQueryFactory<T> indexQueryFactory, final JsonPointer field,
                                                 final Object valueAssertion) {
            final String normalizedJsonPointer = normalizeJsonPointer(field);
            if (!isFieldIndexed(normalizedJsonPointer)) {
                return indexQueryFactory.createMatchAllQuery();
            }
            final ByteString fieldKey = createFieldStartIndexKey(normalizedJsonPointer);
            final ByteSequence startKey = createIndexKey(fieldKey, valueAssertion);
            final ByteString endKey = createFieldEndIndexKey(normalizedJsonPointer);
            return indexQueryFactory.createRangeMatchQuery(indexID, startKey, endKey, true, false);
        }

        @Override
        public T visitLessThanFilter(final IndexQueryFactory<T> indexQueryFactory, final JsonPointer field,
                                     final Object valueAssertion) {
            final String normalizedJsonPointer = normalizeJsonPointer(field);
            if (!isFieldIndexed(normalizedJsonPointer)) {
                return indexQueryFactory.createMatchAllQuery();
            }
            final ByteString startKey = createFieldStartIndexKey(normalizedJsonPointer);
            final ByteSequence endKey = createIndexKey(startKey, valueAssertion);
            return indexQueryFactory.createRangeMatchQuery(indexID, startKey, endKey, false, false);
        }

        @Override
        public T visitLessThanOrEqualToFilter(final IndexQueryFactory<T> indexQueryFactory, final JsonPointer field,
                                              final Object valueAssertion) {
            final String normalizedJsonPointer = normalizeJsonPointer(field);
            if (!isFieldIndexed(normalizedJsonPointer)) {
                return indexQueryFactory.createMatchAllQuery();
            }
            final ByteString startKey = createFieldStartIndexKey(normalizedJsonPointer);
            final ByteSequence endKey = createIndexKey(startKey, valueAssertion);
            return indexQueryFactory.createRangeMatchQuery(indexID, startKey, endKey, false, true);
        }

        @Override
        public T visitNotFilter(final IndexQueryFactory<T> indexQueryFactory,
                                final QueryFilter<JsonPointer> subFilter) {
            // It's not possible to generate a query for a NOT filter so just consider all entries as candidates.
            return indexQueryFactory.createMatchAllQuery();
        }

        @Override
        public T visitOrFilter(final IndexQueryFactory<T> indexQueryFactory,
                               final List<QueryFilter<JsonPointer>> subFilters) {
            final List<T> subQueries = new ArrayList<>(subFilters.size());
            for (QueryFilter<JsonPointer> subFilter : subFilters) {
                subQueries.add(subFilter.accept(this, indexQueryFactory));
            }
            return indexQueryFactory.createUnionQuery(subQueries);
        }

        @Override
        public T visitPresentFilter(final IndexQueryFactory<T> indexQueryFactory, final JsonPointer field) {
            final String normalizedJsonPointer = normalizeJsonPointer(field);
            if (!isFieldIndexed(normalizedJsonPointer)) {
                return indexQueryFactory.createMatchAllQuery();
            }
            final ByteString startKey = createFieldStartIndexKey(normalizedJsonPointer);
            final ByteString endKey = createFieldEndIndexKey(normalizedJsonPointer);
            return indexQueryFactory.createRangeMatchQuery(indexID, startKey, endKey, true, false);
        }

        @Override
        public T visitStartsWithFilter(final IndexQueryFactory<T> indexQueryFactory, final JsonPointer field,
                                       final Object valueAssertion) {
            final String normalizedJsonPointer = normalizeJsonPointer(field);
            if (!isFieldIndexed(normalizedJsonPointer)) {
                return indexQueryFactory.createMatchAllQuery();
            }
            // These assertions make sense for string values, but don't make much sense for other primitive types.
            if (valueAssertion instanceof String) {
                return visitGreaterThanOrEqualToFilter(indexQueryFactory, field, valueAssertion);
            }
            // Best effort: 'true' starts with 'true' and '123' starts with '123', etc.
            return visitEqualsFilter(indexQueryFactory, field, valueAssertion);
        }
    }

    private final class Matcher implements QueryFilterVisitor<ConditionResult, JsonValue, JsonPointer> {
        @Override
        public ConditionResult visitAndFilter(final JsonValue jsonValue,
                                              final List<QueryFilter<JsonPointer>> subFilters) {
            ConditionResult r = ConditionResult.TRUE;
            for (final QueryFilter<JsonPointer> subFilter : subFilters) {
                final ConditionResult p = subFilter.accept(this, jsonValue);
                if (p == ConditionResult.FALSE) {
                    return p;
                }
                r = ConditionResult.and(r, p);
            }
            return r;
        }

        @Override
        public ConditionResult visitBooleanLiteralFilter(final JsonValue jsonValue, final boolean value) {
            return ConditionResult.valueOf(value);
        }

        @Override
        public ConditionResult visitContainsFilter(final JsonValue jsonValue, final JsonPointer field,
                                                   final Object valueAssertion) {
            return visitComparisonFilter(jsonValue, field, valueAssertion, FilterType.CONTAINS);
        }

        @Override
        public ConditionResult visitEqualsFilter(final JsonValue jsonValue, final JsonPointer field,
                                                 final Object valueAssertion) {
            return visitComparisonFilter(jsonValue, field, valueAssertion, FilterType.EQUALS);
        }

        @Override
        public ConditionResult visitExtendedMatchFilter(final JsonValue jsonValue, final JsonPointer field,
                                                        final String operator, final Object valueAssertion) {
            return ConditionResult.UNDEFINED; // Not supported.
        }

        @Override
        public ConditionResult visitGreaterThanFilter(final JsonValue jsonValue, final JsonPointer field,
                                                      final Object valueAssertion) {
            return visitComparisonFilter(jsonValue, field, valueAssertion, FilterType.GREATER_THAN);
        }

        @Override
        public ConditionResult visitGreaterThanOrEqualToFilter(final JsonValue jsonValue, final JsonPointer field,
                                                               final Object valueAssertion) {
            return visitComparisonFilter(jsonValue, field, valueAssertion, FilterType.GREATER_THAN_OR_EQUAL_TO);
        }

        @Override
        public ConditionResult visitLessThanFilter(final JsonValue jsonValue, final JsonPointer field,
                                                   final Object valueAssertion) {
            return visitComparisonFilter(jsonValue, field, valueAssertion, FilterType.LESS_THAN);
        }

        @Override
        public ConditionResult visitLessThanOrEqualToFilter(final JsonValue jsonValue, final JsonPointer field,
                                                            final Object valueAssertion) {
            return visitComparisonFilter(jsonValue, field, valueAssertion, FilterType.LESS_THAN_OR_EQUAL_TO);
        }

        @Override
        public ConditionResult visitNotFilter(final JsonValue jsonValue, final QueryFilter<JsonPointer> subFilter) {
            return ConditionResult.not(subFilter.accept(this, jsonValue));
        }

        @Override
        public ConditionResult visitOrFilter(final JsonValue jsonValue,
                                             final List<QueryFilter<JsonPointer>> subFilters) {
            ConditionResult r = ConditionResult.FALSE;
            for (final QueryFilter<JsonPointer> subFilter : subFilters) {
                final ConditionResult p = subFilter.accept(this, jsonValue);
                if (p == ConditionResult.TRUE) {
                    return p;
                }
                r = ConditionResult.or(r, p);
            }
            return r;
        }

        @Override
        public ConditionResult visitPresentFilter(final JsonValue jsonValue, final JsonPointer field) {
            return ConditionResult.valueOf(jsonValue.get(field) != null);
        }

        @Override
        public ConditionResult visitStartsWithFilter(final JsonValue jsonValue, final JsonPointer field,
                                                     final Object valueAssertion) {
            return visitComparisonFilter(jsonValue, field, valueAssertion, FilterType.STARTS_WITH);
        }

        private ConditionResult visitComparisonFilter(final JsonValue jsonValue, final JsonPointer field,
                                                      final Object valueAssertion, final FilterType equals) {
            final JsonValue jsonValueField = jsonValue.get(field);
            if (jsonValueField == null || jsonValueField.isMap()) {
                return ConditionResult.FALSE;
            }
            if (jsonValueField.isList()) {
                for (Object listElement : jsonValueField.asList()) {
                    if (compare(equals, valueAssertion, listElement)) {
                        return ConditionResult.TRUE;
                    }
                }
                return ConditionResult.FALSE;
            } else {
                return ConditionResult.valueOf(compare(equals, valueAssertion, jsonValueField.getObject()));
            }
        }

        private boolean compare(final FilterType type, final Object assertion, final Object value) {
            if (assertion instanceof String && value instanceof String) {
                final String stringAssertion = normalizeString((String) assertion);
                final String stringValue = normalizeString((String) value);
                switch (type) {
                case CONTAINS:
                    return stringValue.contains(stringAssertion);
                case STARTS_WITH:
                    return stringValue.startsWith(stringAssertion);
                default:
                    return compare0(type, stringAssertion, stringValue);
                }
            } else if (assertion instanceof Number && value instanceof Number) {
                final Double doubleAssertion = ((Number) assertion).doubleValue();
                final Double doubleValue = ((Number) value).doubleValue();
                return compare0(type, doubleAssertion, doubleValue);
            } else if (assertion instanceof Boolean && value instanceof Boolean) {
                final Boolean booleanAssertion = (Boolean) assertion;
                final Boolean booleanValue = (Boolean) value;
                return compare0(type, booleanAssertion, booleanValue);
            } else {
                return false;
            }
        }

        private <T extends Comparable<T>> boolean compare0(final FilterType type, final T assertion, final T value) {
            switch (type) {
            case EQUALS:
            case CONTAINS:
            case STARTS_WITH:
                return value.equals(assertion);
            case GREATER_THAN:
                return value.compareTo(assertion) > 0;
            case GREATER_THAN_OR_EQUAL_TO:
                return value.compareTo(assertion) >= 0;
            case LESS_THAN:
                return value.compareTo(assertion) < 0;
            case LESS_THAN_OR_EQUAL_TO:
                return value.compareTo(assertion) <= 0;
            }
            return false;
        }
    }

    private enum FilterType {
        EQUALS,
        CONTAINS,
        GREATER_THAN,
        GREATER_THAN_OR_EQUAL_TO,
        LESS_THAN,
        LESS_THAN_OR_EQUAL_TO,
        STARTS_WITH
    }
}
