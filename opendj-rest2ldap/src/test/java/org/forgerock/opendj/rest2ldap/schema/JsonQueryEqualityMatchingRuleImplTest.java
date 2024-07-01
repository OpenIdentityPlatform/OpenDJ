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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.forgerock.opendj.ldap.Attributes.singletonAttribute;
import static org.forgerock.opendj.ldap.ConditionResult.FALSE;
import static org.forgerock.opendj.ldap.ConditionResult.TRUE;
import static org.forgerock.opendj.ldap.ConditionResult.UNDEFINED;
import static org.forgerock.opendj.ldap.ConditionResult.or;
import static org.forgerock.opendj.ldap.schema.Schema.getDefaultSchema;
import static org.forgerock.opendj.rest2ldap.schema.JsonQueryEqualityMatchingRuleImpl.compileWildCardPattern;
import static org.forgerock.opendj.rest2ldap.schema.JsonSchema.*;
import static org.forgerock.opendj.rest2ldap.schema.JsonSchema.ValidationPolicy.LENIENT;
import static org.forgerock.util.Options.defaultOptions;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.forgerock.json.JsonPointer;
import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.LinkedHashMapEntry;
import org.forgerock.opendj.ldap.Matcher;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.forgerock.opendj.ldap.spi.IndexQueryFactory;
import org.forgerock.opendj.ldap.spi.Indexer;
import org.forgerock.opendj.ldap.spi.IndexingOptions;
import org.forgerock.testng.ForgeRockTestCase;
import org.forgerock.util.Options;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
@Test
public class JsonQueryEqualityMatchingRuleImplTest extends ForgeRockTestCase {
    private final MatchingRule matchingRule = JsonSchema.getCaseIgnoreJsonQueryMatchingRule();
    private final JsonQueryEqualityMatchingRuleImpl matchingRuleImpl =
            new JsonQueryEqualityMatchingRuleImpl(EMR_CASE_IGNORE_JSON_QUERY_NAME, defaultOptions());
    // @formatter:off
    private final Schema schema = addJsonSyntaxesAndMatchingRulesToSchema(new SchemaBuilder(getDefaultSchema()))
            .setOption(VALIDATION_POLICY, LENIENT)
            .addAttributeType("( 9.9.9 NAME 'json' "
                                      + "EQUALITY caseIgnoreJsonQueryMatch "
                                      + "SYNTAX " + SYNTAX_JSON_OID + " )", true)
            .toSchema();
    // @formatter:on

    @DataProvider
    public static Object[][] validJson() {
        // @formatter:off
        return new Object[][] {
            { "null", "null" },
            { "false", "false" },
            { "true", "true" },
            { "123", "123" },
            { "123.456", "123.456" },
            { "'string'", "'string'" },
            { "   '  HeLlo  WoRlD  '   ", "'hello world'" },
            { "[]", "[]" },
            { " [ 1, 2, 3 ] ", "[1,2,3]" },
            // Sort keys
            { " { 'c' : 1, 'a' : 2, 'b' : 3 } ", "{'a':2,'b':3,'c':1}" },
            { "{'a':1,'A':2}", "{'A':2,'a':1}" },
            // Case-sensitive keys
            { "{'a':1,'a':2}", "{'a':2}" },
            // Filter duplicate keys
            // Nested objects
            { "{'c':3,'b':[1,'One',1],'a':'XYZ'}", "{'a':'xyz','b':[1,'one',1],'c':3}" },
            { "[1,2,{'c':3,'b':[1,'One',1],'a':'XYZ'}]", "[1,2,{'a':'xyz','b':[1,'one',1],'c':3}]" }
        };
        // @formatter:on
    }

    @Test(dataProvider = "validJson")
    public void testNormalizeAttributeValueWithValidJson(String json, String normalizedJson) throws Exception {
        final ByteString expected = ByteString.valueOfUtf8(normalizedJson.replaceAll("'", "\""));
        final ByteString normalizeAttributeValue = matchingRule.normalizeAttributeValue(ByteString.valueOfUtf8(json));
        assertThat(normalizeAttributeValue).isEqualTo(expected);
    }

    @DataProvider
    public static Object[][] invalidJson() {
        // @formatter:off
        return new Object[][] {
            { "" },
            { "x" },
            { "'string" },
            { "string'" },
            { "'a' 'b'" },
            { "000" },
            { "3." },
            { ".1" },
            { "1 2 3" },
            { "[1" },
            { "1]" },
            { "[1, 2," },
            { "[1, 2, 3], 4]" },
            { "[1, [2, 3, 4]" },
            { "{'k1':'v1'" },
            { "'k1':'v1'}" },
            { "{'k1':'v1','k2':{'k3':'v3','k4':'v4','k5':'v5','k6':'v6'}" },
            { "{'k1':'v1','k2':{'k3':'v3','k4':'v4','k5':'v5'}},'k6':'v6'}" }
        };
        // @formatter:on
    }

    @Test(dataProvider = "invalidJson", expectedExceptions = DecodeException.class)
    public void testNormalizeAttributeValueWithInvalidJson(String json) throws Exception {
        matchingRule.normalizeAttributeValue(ByteString.valueOfUtf8(json));
    }

    @DataProvider
    public Object[][] indexKeys() {
        // @formatter:off

        // json, keys...
        return new Object[][] {
            { "null",  keys(key("/", null)) },
            { "false", keys(key("/", false)) },
            { "true", keys(key("/", true)) },
            { "123", keys(key("/", 123)) },
            { "123.456", keys(key("/", 123.456)) },
            { "'string'", keys(key("/", "string")) },
            { "   '  HeLlo  WoRlD  '   ", keys(key("/", "hello world")) },
            { "[]", keys() },
            { " [ 1, 2, 3 ] ",
              keys(key("/", 1),
                   key("/", 2),
                   key("/", 3)) },
            { "{'ak1':'av1','ak2':{'bk1':'bv1','bk2':[1,2,3],'bk3':{'ck1':'cv1','ck2':'cv2'}},'ak3':'av3'}",
              keys(key("/ak1", "av1"),
                   key("/ak2/bk1", "bv1"),
                   key("/ak2/bk2", 1),
                   key("/ak2/bk2", 2),
                   key("/ak2/bk2", 3),
                   key("/ak2/bk3/ck1", "cv1"),
                   key("/ak2/bk3/ck2", "cv2"),
                   key("/ak3", "av3")),
            }
        };
        // @formatter:on
    }

    @Test(dataProvider = "indexKeys")
    public void testCreateIndexers(String json, ByteString[] expectedKeys) throws Exception {
        final Collection<? extends Indexer> indexers = matchingRule.createIndexers(mock(IndexingOptions.class));
        assertThat(indexers).hasSize(1);
        final Indexer indexer = indexers.iterator().next();
        final ArrayList<ByteString> keys = new ArrayList<>();
        indexer.createKeys(null, ByteString.valueOfUtf8(json), keys);
        assertThat(keys).containsOnly(expectedKeys);
    }

    private ByteString[] keys(final ByteString... keys) {
        return keys;
    }

    private ByteString key(final String jsonPointer, final Object value) {
        return matchingRuleImpl.createIndexKey(jsonPointer, value);
    }

    /** JSON object that jsonQueryAssertions will match against. */
    // @formatter:off
    private final String json = "{"
            + "'null':null,"
            + "'true':true,"
            + "'false':false,"
            + "'intpos':123,"
            + "'intneg':-123,"
            + "'doublepos':12.3,"
            + "'doubleneg':-12.3,"
            + "'string':'  HELLO  world  ',"
            + "'array':['One',['Two','Three'],{'key':'value'},'Four'],"
            + "'object':{'nested':{'k1':'v1','k2':[1,2,3],'k3':'v3'},'tail':'tail','999':'999'}"
            + "}";
    // @formatter:on

    @DataProvider
    public static Object[][] jsonQueryAssertions() {
        // @formatter:off
        return new Object[][] {
            // Test field addressing.
            { "true", TRUE, false },
            { "false", FALSE, false },
            { "garbage", UNDEFINED, false },
            { "/missing pr", FALSE, false },
            { "/null pr", TRUE, false },
            { "/missing eq 123", FALSE, false },
            { "/null eq 123", FALSE, false },
            { "/true eq true", TRUE, false },
            { "/true eq false", FALSE, false },
            { "/true eq 123", FALSE, false },
            { "/false eq true", FALSE, false },
            { "/false eq false", TRUE, false },
            { "/intpos eq 123", TRUE, false },
            { "/intpos eq -123", FALSE, false },
            { "/intpos eq true", FALSE, false },
            { "/intneg eq -123", TRUE, false },
            { "/intneg eq 123", FALSE, false },
            { "/intneg eq true", FALSE, false },
            { "/doublepos eq 12.3", TRUE, false },
            { "/doublepos eq -12.3", FALSE, false },
            { "/doublepos eq true", FALSE, false },
            { "/doubleneg eq -12.3", TRUE, false },
            { "/doubleneg eq 12.3", FALSE, false },
            { "/doubleneg eq true", FALSE, false },
            { "/string eq 'hello world'", TRUE, false },
            { "/string eq ' HELLO  WORLD '", TRUE, false },
            { "/string eq 'hello mars'", FALSE, false },
            { "/string eq 123", FALSE, false },
            { "/array eq 'one'", TRUE, false },
            { "/array eq 'two'", FALSE, true },
            { "/array eq 'four'", TRUE, false },
            { "/array/0 eq 'one'", TRUE, false },
            { "/array/0 eq 'four'", FALSE, true },
            { "/array/10 eq 'one'", FALSE, true },
            { "/array/2/key eq 'value'", TRUE, false },
            { "/object eq 'value'", FALSE, false },
            { "/object/nested/k1 eq 'v1'", TRUE, false },
            { "/object/tail eq 'tail'", TRUE, false },
            { "/object/999 eq '999'", TRUE, false },
            // Integer comparisons.
            { "/intpos lt 1000", TRUE, false },
            { "/intpos lt 123", FALSE, false },
            { "/intpos lt -1000", FALSE, false },
            { "/intneg lt 1000", TRUE, false },
            { "/intneg lt -123", FALSE, false },
            { "/intneg lt -1000", FALSE, false },
            { "/intpos le 1000", TRUE, false },
            { "/intpos le 123", TRUE, false },
            { "/intpos le -1000", FALSE, false },
            { "/intneg le 1000", TRUE, false },
            { "/intneg le -123", TRUE, false },
            { "/intneg le -1000", FALSE, false },
            { "/intpos gt 1000", FALSE, false },
            { "/intpos gt 123", FALSE, false },
            { "/intpos gt -1000", TRUE, false },
            { "/intneg gt 1000", FALSE, false },
            { "/intneg gt -123", FALSE, false },
            { "/intneg gt -1000", TRUE, false },
            { "/intpos ge 1000", FALSE, false },
            { "/intpos ge 123", TRUE, false },
            { "/intpos ge -1000", TRUE, false },
            { "/intneg ge 1000", FALSE, false },
            { "/intneg ge -123", TRUE, false },
            { "/intneg ge -1000", TRUE, false },
            // Double comparisons.
            { "/doublepos lt 100.0", TRUE, false },
            { "/doublepos lt 12.3", FALSE, false },
            { "/doublepos lt -100.0", FALSE, false },
            { "/doubleneg lt 100.0", TRUE, false },
            { "/doubleneg lt -12.3", FALSE, false },
            { "/doubleneg lt -100.0", FALSE, false },
            { "/doublepos le 100.0", TRUE, false },
            { "/doublepos le 12.3", TRUE, false },
            { "/doublepos le -100.0", FALSE, false },
            { "/doubleneg le 100.0", TRUE, false },
            { "/doubleneg le -12.3", TRUE, false },
            { "/doubleneg le -100.0", FALSE, false },
            { "/doublepos gt 100.0", FALSE, false },
            { "/doublepos gt 12.3", FALSE, false },
            { "/doublepos gt -100.0", TRUE, false },
            { "/doubleneg gt 100.0", FALSE, false },
            { "/doubleneg gt -12.3", FALSE, false },
            { "/doubleneg gt -100.0", TRUE, false },
            { "/doublepos ge 100.0", FALSE, false },
            { "/doublepos ge 12.3", TRUE, false },
            { "/doublepos ge -100.0", TRUE, false },
            { "/doubleneg ge 100.0", FALSE, false },
            { "/doubleneg ge -12.3", TRUE, false },
            { "/doubleneg ge -100.0", TRUE, false },
            // String comparisons.
            { "/string lt 'zzz'", TRUE, false },
            { "/string lt ' Hello  World '", FALSE, false },
            { "/string lt 'aaa'", FALSE, false },
            { "/string le 'zzz'", TRUE, false },
            { "/string le ' Hello  World '", TRUE, false },
            { "/string le 'aaa'", FALSE, false },
            { "/string gt 'zzz'", FALSE, false },
            { "/string gt ' Hello  World '", FALSE, false },
            { "/string gt 'aaa'", TRUE, false },
            { "/string ge 'zzz'", FALSE, false },
            { "/string ge ' Hello  World '", TRUE, false },
            { "/string ge 'aaa'", TRUE, false },
            { "/string sw '  HELLO'", TRUE, false },
            { "/string sw 'mars'", FALSE, false },
            { "/string co '  LO  '", TRUE, false },
            { "/string co 'mars'", FALSE, true },
            // Test AND operator.
            { "false and false", FALSE, false },
            { "false and true", FALSE, false },
            { "true and false", FALSE, false },
            { "true and true", TRUE, false },
            // Test OR operator.
            { "false or false", FALSE, false },
            { "false or true", TRUE, false },
            { "true or false", TRUE, false },
            { "true or true", TRUE, false },
            // Test NOT operator.
            // { "!false", TRUE, false }, // FIXME: bug in QueryFilterParser?
            // { "!true", FALSE, true }, // FIXME: bug in QueryFilterParser?
            { "!(false)", TRUE, true },
            { "!(true)", FALSE, true },
        };
        // @formatter:on
    }

    @Test(dataProvider = "jsonQueryAssertions")
    public void testAssertionMatching(final String query, final ConditionResult expected,
                                      final boolean expectFalsePositives) throws Exception {
        final Matcher matcher = Filter.equality("json", query).matcher(schema);
        assertThat(matcher.matches(jsonEntry(schema))).isEqualTo(expected);
    }

    /**
     * Tests index generation and querying. The previous test evaluates a JSON filter directly against a JSON value
     * and checks that the returned filter result matches the expected result. This test, however, evaluates the
     * filter against a pseudo index containing index keys for the JSON value. If the set of keys remaining at the
     * end of the index query is non-empty then the "entry" is candidate. Remember that indexes are allowed to return
     * false positives, so there is not a direct correlation between the expected filter result and the final key
     * set's content: if the filter is expected to match then the key set must be non-empty if the filter is expected
     * not to match then the key set should be empty, but MAY be non-empty in rare cases. The third test parameter
     * "expectFalsePositives" indicates whether a false positive is expected.
     */
    @Test(dataProvider = "jsonQueryAssertions")
    public void testAssertionCreateIndexQuery(final String query, final ConditionResult expected,
                                              final boolean expectFalsePositives) throws Exception {
        if (expected == UNDEFINED) {
            try {
                matchingRuleImpl.getAssertion(schema, ByteString.valueOfUtf8(query));
                fail("Unexpectedly succeeded when parsing invalid assertion");
            } catch (DecodeException e) {
                // Expected.
            }
            return;
        }
        testAssertionIndexQuery(query, expected, expectFalsePositives, matchingRuleImpl, schema);
    }

    @DataProvider
    public static Object[][] jsonQueryAssertionsPartialIndexing() {
        // @formatter:off
        return new Object[][] {
            // Indexed.
            { "/intpos eq 123", TRUE, false },
            { "/intpos eq -123", FALSE, false },
            // Not indexed.
            { "/doublepos eq 12.3", TRUE, true },
            { "/doublepos eq -12.3", FALSE, true },
            // Indexed.
            { "/string eq 'hello world'", TRUE, false },
            { "/string eq 'hello mars'", FALSE, false },
            // Indexed.
            { "/array eq 'one'", TRUE, false },
            { "/array eq 'two'", FALSE, true },
            { "/array/0 eq 'one'", TRUE, false },
            { "/array/0 eq 'four'", FALSE, true },
            { "/array/10 eq 'one'", FALSE, true },
            // Not indexed.
            { "/array/2/key eq 'value'", TRUE, true },
            { "/array/2/key eq 'nomatch'", FALSE, true },
            // Indexed.
            { "/object/nested/k1 eq 'v1'", TRUE, false },
            { "/object/nested/k1 eq 'v2'", FALSE, false },
            // Not indexed.
            { "/object/tail eq 'tail'", TRUE, true },
            { "/object/tail eq 'head'", FALSE, true },
            { "/object/999 eq '999'", TRUE, true },
            { "/object/999 eq '666'", FALSE, true },
        };
        // @formatter:on
    }

    @Test(dataProvider = "jsonQueryAssertionsPartialIndexing")
    public void testAssertionCreateIndexQueryPartialIndexing(final String query, final ConditionResult expected,
                                                     final boolean expectFalsePositives) throws Exception {
        final Options options = defaultOptions()
                .set(INDEXED_FIELD_PATTERNS, Arrays.asList("intpos", "/string", "/array", "/object/nested/**"));
        final JsonQueryEqualityMatchingRuleImpl matchingRuleImpl =
                new JsonQueryEqualityMatchingRuleImpl(EMR_CASE_IGNORE_JSON_QUERY_NAME, options);
        final SchemaBuilder builder = new SchemaBuilder(getDefaultSchema());

        addJsonSyntaxesAndMatchingRulesToSchema(builder);
        builder.setOption(VALIDATION_POLICY, LENIENT)
               .buildMatchingRule("9.9.9")
               .names("partialIndexingTestMatch")
               .syntaxOID(SYNTAX_JSON_QUERY_OID)
               .implementation(matchingRuleImpl)
               .addToSchema()
               .addAttributeType("( 9.9.9 NAME 'json' EQUALITY partialIndexingTestMatch "
                                         + "SYNTAX " + SYNTAX_JSON_OID + " )", true);
        final Schema schema = builder.toSchema();
        testAssertionIndexQuery(query, expected, expectFalsePositives, matchingRuleImpl, schema);
    }

    private void testAssertionIndexQuery(final String query, final ConditionResult expected,
                                         final boolean expectFalsePositives,
                                         final JsonQueryEqualityMatchingRuleImpl impl, final Schema schema)
            throws DecodeException {
        // The assertion is expected to be valid so create an index query. The generated query depends on the
        // assertion, so there's not much that we can verify here.
        final Indexer indexer = impl.createIndexers(null).iterator().next();
        final TreeSet<ByteString> keys = new TreeSet<>();
        indexer.createKeys(schema, ByteString.valueOfUtf8(json), keys);

        final Assertion assertion = impl.getAssertion(schema, ByteString.valueOfUtf8(query));
        final IndexQueryFactory<Set<ByteString>> factory = new MockIndexQueryFactory(keys);
        final Set<ByteString> matchedKeys = assertion.createIndexQuery(factory);
        final ConditionResult isPotentialMatch = ConditionResult.valueOf(!matchedKeys.isEmpty());

        assertThat(isPotentialMatch).isEqualTo(or(expected, ConditionResult.valueOf(expectFalsePositives)));
    }

    private static class MockIndexQueryFactory implements IndexQueryFactory<Set<ByteString>> {
        private final TreeSet<ByteString> keys;

        private MockIndexQueryFactory(final TreeSet<ByteString> keys) {
            this.keys = keys;
        }

        @Override
        public Set<ByteString> createExactMatchQuery(final String indexID, final ByteSequence key) {
            assertThat(indexID).isEqualTo(EMR_CASE_IGNORE_JSON_QUERY_NAME);
            final ByteString keyByteString = key.toByteString();
            if (keys.contains(keyByteString)) {
                return Collections.singleton(keyByteString);
            }
            return Collections.emptySet();
        }

        @Override
        public Set<ByteString> createMatchAllQuery() {
            return keys;
        }

        @Override
        public Set<ByteString> createRangeMatchQuery(final String indexID, final ByteSequence lower,
                                                     final ByteSequence upper, final boolean lowerIncluded,
                                                     final boolean upperIncluded) {
            assertThat(indexID).isEqualTo(EMR_CASE_IGNORE_JSON_QUERY_NAME);
            if (lower.isEmpty()) {
                return keys.headSet(upper.toByteString(), upperIncluded);
            } else if (upper.isEmpty()) {
                return keys.tailSet(lower.toByteString(), lowerIncluded);
            } else {
                return keys.subSet(lower.toByteString(), lowerIncluded, upper.toByteString(), upperIncluded);
            }
        }

        @Override
        public Set<ByteString> createIntersectionQuery(final Collection<Set<ByteString>> subqueries) {
            final TreeSet<ByteString> result = new TreeSet<>(keys);
            for (Set<ByteString> subquery : subqueries) {
                result.retainAll(subquery);
            }
            return result;
        }

        @Override
        public Set<ByteString> createUnionQuery(final Collection<Set<ByteString>> subqueries) {
            final TreeSet<ByteString> result = new TreeSet<>();
            for (Set<ByteString> subquery : subqueries) {
                result.addAll(subquery);
            }
            return result;
        }

        @Override
        public IndexingOptions getIndexingOptions() {
            return null;
        }
    }

    @Test
    public void testGetGreaterOrEqualAssertion() throws Exception {
        assertThat(matchingRule.getGreaterOrEqualAssertion(null)).isSameAs(Assertion.UNDEFINED_ASSERTION);
    }

    @Test
    public void testGetLessOrEqualAssertion() throws Exception {
        assertThat(matchingRule.getLessOrEqualAssertion(null)).isSameAs(Assertion.UNDEFINED_ASSERTION);
    }

    @Test
    public void testGetSubstringAssertion() throws Exception {
        assertThat(matchingRule.getSubstringAssertion(null, null, null)).isSameAs(Assertion.UNDEFINED_ASSERTION);
    }

    @DataProvider
    public static Object[][] patterns() {
        // @formatter:off
        return new Object[][] {
            { "", "/", true },
            { "/", "/", true },
            { "/", "/a", false },
            { "/a", "/a", true },
            { "/a", "/aa", false},
            { "/a", "/a/a", false},
            { "/a/b", "/a/b", true },
            { "/a/b", "/a/bb", false },
            { "/a/*/c", "/a/b/c", true },
            { "/a/*/c", "/a/bbb/c", true },
            { "/a/*/c", "/a/b/b/c", false },
            { "/a/*/c", "/a/c", false },
            { "/a/**/c", "/a/b/c", true },
            { "/a/**/c", "/a/bbb/c", true },
            { "/a/**/c", "/a/b/b/c", true },
            { "/a/**/c", "/a/b/b/c/c", true },
            { "/a/**/c", "/a/b/b/c/cc", false },
            { "/a/**/c", "/a/c", true },
            { "/a/*", "/a", false },
            { "/a/*", "/a/b", true },
            { "/a/*", "/a/b/c", false },
            { "/a/**", "/a", true },
            { "/a/**", "/a/b", true },
            { "/a/**", "/a/b", true },
            { "/a/**", "/a/b/c", true },
            { "/**/a", "/a", true },
            { "/**/a", "/b", false },
            { "/**/a", "/b/a", true },
            { "/**/a", "/c/b/a", true },
            { "/**/a", "/c/b/a/a", true },
        };
        // @formatter:on
    }

    @Test(dataProvider = "patterns")
    public void testIndexedFieldPattens(String pattern, String field, boolean isMatchExpected) throws Exception {
        final Pattern regex = compileWildCardPattern(pattern);
        final String normalizedJsonPointer = new JsonPointer(field).toString();
        assertThat(regex.matcher(normalizedJsonPointer).matches()).isEqualTo(isMatchExpected);
    }

    private Entry jsonEntry(final Schema schema) {
        // @formatter:off
        final Entry entry = new LinkedHashMapEntry("dn: cn=test",
                                                   "objectClass: top",
                                                   "objectClass: extensibleObject",
                                                   "cn: test");
        // @formatter:on
        final AttributeDescription jsonAttributeDescription = AttributeDescription.valueOf("json", schema);
        entry.addAttribute(singletonAttribute(jsonAttributeDescription, json));
        return entry;
    }
}
