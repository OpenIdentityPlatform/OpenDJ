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
 *      Copyright 2014-2015 ForgeRock AS
 */
package org.forgerock.opendj.ldap.schema;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.TreeSet;

import org.fest.assertions.Assertions;
import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.spi.IndexQueryFactory;
import org.forgerock.opendj.ldap.spi.Indexer;
import org.forgerock.opendj.ldap.spi.IndexingOptions;
import org.forgerock.util.Utils;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.forgerock.opendj.ldap.ByteString.*;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

/**
 * Tests all generic code of AbstractSubstringMatchingRuleImpl.
 */
@SuppressWarnings("javadoc")
public class AbstractSubstringMatchingRuleImplTest extends AbstractSchemaTestCase {

    private static class FakeSubstringMatchingRuleImpl extends AbstractSubstringMatchingRuleImpl {

        FakeSubstringMatchingRuleImpl() {
            super(SMR_CASE_EXACT_OID, EMR_CASE_EXACT_OID);
        }

        /** {@inheritDoc} */
        @Override
        public ByteString normalizeAttributeValue(Schema schema, ByteSequence value) throws DecodeException {
            return value.toByteString();
        }

    }

    static class FakeIndexQueryFactory implements IndexQueryFactory<String> {

        private final IndexingOptions options;
        private final boolean normalizedValuesAreReadable;

        public FakeIndexQueryFactory(IndexingOptions options) {
            this(options, true);
        }

        public FakeIndexQueryFactory(IndexingOptions options, boolean normalizedValuesAreReadable) {
            this.options = options;
            this.normalizedValuesAreReadable = normalizedValuesAreReadable;
        }

        @Override
        public String createExactMatchQuery(String indexID, ByteSequence key) {
            String keyValue = normalizedValuesAreReadable ? key.toString() : key.toByteString().toHexString();
            return "exactMatch(" + indexID + ", value=='" + keyValue + "')";
        }

        @Override
        public String createMatchAllQuery() {
            return "matchAll()";
        }

        @Override
        public String createRangeMatchQuery(String indexID, ByteSequence lower, ByteSequence upper,
                boolean lowerIncluded, boolean upperIncluded) {
            final StringBuilder sb = new StringBuilder("rangeMatch");
            sb.append("(");
            sb.append(indexID);
            sb.append(", '");
            if (normalizedValuesAreReadable) {
                sb.append(lower);
            } else if (!lower.isEmpty()) {
                sb.append(lower.toByteString().toHexString());
            }
            sb.append("' <");
            if (lowerIncluded) {
                sb.append("=");
            }
            sb.append(" value <");
            if (upperIncluded) {
                sb.append("=");
            }
            sb.append(" '");
            if (normalizedValuesAreReadable) {
                sb.append(upper);
            } else if (!upper.isEmpty()) {
                sb.append(upper.toByteString().toHexString());
            }
            sb.append("')");
            return sb.toString();
        }

        @Override
        public String createIntersectionQuery(Collection<String> subqueries) {
            return "intersect[" + Utils.joinAsString(", ", subqueries) + "]";
        }

        @Override
        public String createUnionQuery(Collection<String> subqueries) {
            return "union[" + Utils.joinAsString(", ", subqueries) + "]";
        }

        @Override
        public IndexingOptions getIndexingOptions() {
            return options;
        }

    }

    private MatchingRuleImpl getRule() {
        return new FakeSubstringMatchingRuleImpl();
    }

    static IndexingOptions newIndexingOptions() {
        final IndexingOptions options = mock(IndexingOptions.class);
        when(options.substringKeySize()).thenReturn(3);
        return options;
    }

    @DataProvider
    public Object[][] invalidAssertions() {
        return new Object[][] {
            { "" },
            { "abc" },
            { "**" },
            { "\\g" },
            { "\\0" },
            { "\\00" },
            { "\\0g" },
            { gen() },
        };
    }

    @Test(dataProvider = "invalidAssertions", expectedExceptions = { DecodeException.class })
    public void testInvalidAssertion(String assertionValue) throws Exception {
        getRule().getAssertion(null, valueOfUtf8(assertionValue));
    }

    @DataProvider
    public Object[][] validAssertions() {
        return new Object[][] {
            { "this is a string", "*", ConditionResult.TRUE },
            { "this is a string", "that*", ConditionResult.FALSE },
            { "this is a string", "*that", ConditionResult.FALSE },
            { "this is a string", "this*is*a*string", ConditionResult.TRUE },
            { "this is a string", "this*my*string", ConditionResult.FALSE },
            { "this is a string", "string*a*is*this", ConditionResult.FALSE },
            { "this is a string", "string*a*is*this", ConditionResult.FALSE },
            { "this is a string", "*\\00", ConditionResult.FALSE },
            { "this is a string", gen() + "*", ConditionResult.FALSE },
            // initial substring longer than value
            { "tt", "this*", ConditionResult.FALSE },
            // final substring longer than value
            { "tt", "*this", ConditionResult.FALSE },
        };
    }

    private String gen() {
        final char[] array = new char[] {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };
        final StringBuilder sb = new StringBuilder();
        for (char c : array) {
            sb.append("\\").append(c).append(c);
        }
        return sb.toString();
    }

    @Test(dataProvider = "validAssertions")
    public void testValidAssertions(String attrValue, String assertionValue, ConditionResult expected)
            throws Exception {
        final MatchingRuleImpl rule = getRule();
        final ByteString normValue = rule.normalizeAttributeValue(null, valueOfUtf8(attrValue));
        Assertion assertion = rule.getAssertion(null, valueOfUtf8(assertionValue));
        assertEquals(assertion.matches(normValue), expected);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSubstringCreateIndexQueryForFinalWithMultipleSubqueries() throws Exception {
        Assertion assertion = getRule().getSubstringAssertion(
            null, null, Collections.EMPTY_LIST, valueOfUtf8("this"));

        assertEquals(
            assertion.createIndexQuery(new FakeIndexQueryFactory(newIndexingOptions())),
            "intersect["
                    + "exactMatch(" + SMR_CASE_EXACT_OID + ", value=='his'), "
                    + "exactMatch(" + SMR_CASE_EXACT_OID + ", value=='thi')"
                    + "]");
    }

    @Test
    public void testSubstringCreateIndexQueryForAllNoSubqueries() throws Exception {
        Assertion assertion = getRule().getSubstringAssertion(
            null, valueOfUtf8("abc"), Arrays.asList(toByteStrings("def", "ghi")), valueOfUtf8("jkl"));

        assertEquals(
            assertion.createIndexQuery(new FakeIndexQueryFactory(newIndexingOptions())),
            "intersect["
                    + "rangeMatch(" + EMR_CASE_EXACT_OID + ", 'abc' <= value < 'abd'), "
                    + "exactMatch(" + SMR_CASE_EXACT_OID + ", value=='def'), "
                    + "exactMatch(" + SMR_CASE_EXACT_OID + ", value=='ghi'), "
                    + "exactMatch(" + SMR_CASE_EXACT_OID + ", value=='jkl'), "
                    + "exactMatch(" + SMR_CASE_EXACT_OID + ", value=='abc')"
                    + "]");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSubstringCreateIndexQueryWithInitial() throws Exception {
        Assertion assertion = getRule().getSubstringAssertion(
            null, valueOfUtf8("aa"), Collections.EMPTY_LIST, null);

        assertEquals(
            assertion.createIndexQuery(new FakeIndexQueryFactory(newIndexingOptions())),
            "intersect["
                    + "rangeMatch(" + EMR_CASE_EXACT_OID + ", 'aa' <= value < 'ab'), "
                    + "rangeMatch(" + SMR_CASE_EXACT_OID + ", 'aa' <= value < 'ab')"
                    + "]");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSubstringCreateIndexQueryWithInitialOverflowsInRange() throws Exception {
        ByteString lower = wrap(new byte[] { 'a', (byte) 0XFF });
        Assertion assertion = getRule().getSubstringAssertion(
            null, lower, Collections.EMPTY_LIST, null);

        assertEquals(
            assertion.createIndexQuery(new FakeIndexQueryFactory(newIndexingOptions())),
            // 0x00 is the nul byte, a.k.a. string terminator
            // so everything after it is not part of the string
            "intersect["
                    + "rangeMatch(" + EMR_CASE_EXACT_OID + ", '" + lower + "' <= value < 'b\u0000'), "
                    + "rangeMatch(" + SMR_CASE_EXACT_OID + ", '" + lower + "' <= value < 'b\u0000')"
                    + "]");
    }

    @Test
    public void testIndexer() throws Exception {
        final IndexingOptions options = newIndexingOptions();
        final Indexer indexer = getRule().createIndexers(options).iterator().next();
        Assertions.assertThat(indexer.getIndexID()).isEqualTo(SMR_CASE_EXACT_OID + ":" + options.substringKeySize());

        final TreeSet<ByteString> keys = new TreeSet<>();
        indexer.createKeys(Schema.getCoreSchema(), valueOfUtf8("ABCDE"), keys);
        Assertions.assertThat(keys).containsOnly((Object[]) toByteStrings("ABC", "BCD", "CDE", "DE", "E"));
    }

    private ByteString[] toByteStrings(String... strings) {
        final ByteString[] results = new ByteString[strings.length];
        for (int i = 0; i < strings.length; i++) {
            results[i] = valueOfUtf8(strings[i]);
        }
        return results;
    }
}
