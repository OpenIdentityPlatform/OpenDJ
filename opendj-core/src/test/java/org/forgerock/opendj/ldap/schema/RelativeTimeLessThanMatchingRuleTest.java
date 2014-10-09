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
 *      Copyright 2014 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.schema;

import java.util.Calendar;
import java.util.Date;

import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.GeneralizedTime;
import org.forgerock.opendj.ldap.schema.AbstractSubstringMatchingRuleImplTest.FakeIndexQueryFactory;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.forgerock.opendj.util.TimeSource;

import static org.fest.assertions.Assertions.*;
import static org.forgerock.opendj.ldap.schema.AbstractSubstringMatchingRuleImplTest.*;

@SuppressWarnings("javadoc")
@Test
public class RelativeTimeLessThanMatchingRuleTest extends MatchingRuleTest {

    /**
     * {@inheritDoc}
     */
    @Override
    @DataProvider(name = "matchingRuleInvalidAttributeValues")
    public Object[][] createMatchingRuleInvalidAttributeValues() {
        return new Object[][] {
            // too short
            { "1Z" },
            // bad year
            { "201a0630Z" },
            // bad month
            { "20141330Z" },
            // bad day
            { "20140635Z" },
            // bad hour
            { "20140630351010Z" },
            // bad minute
            { "20140630108810Z" },
            // bad second
            { "20140630101088Z" },
        };
    }

    @DataProvider(name = "matchingRuleInvalidAssertionValues")
    public Object[][] createMatchingRuleInvalidAssertionValues() {
        return new Object[][] {
            { " " },
            { "bla" },
            { "-30b" },
            { "-30ms" },
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @DataProvider(name = "matchingrules")
    public Object[][] createMatchingRuleTest() {
        final Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(TimeSource.DEFAULT.currentTimeMillis());
        final Date nowDate = calendar.getTime();
        calendar.add(Calendar.MONTH, 1);
        final Date oneMonthAheadDate = calendar.getTime();

        final String nowGT = GeneralizedTime.valueOf(nowDate).toString();
        final String oneMonthAheadGT = GeneralizedTime.valueOf(oneMonthAheadDate).toString();

        return new Object[][] {
            // attribute value, assertion value, result
            { oneMonthAheadGT, "6w", ConditionResult.TRUE },
            { oneMonthAheadGT, "2400h", ConditionResult.TRUE },
            { nowGT, "1d", ConditionResult.TRUE },
            { nowGT, "10s", ConditionResult.TRUE },
            { nowGT, "+1h", ConditionResult.TRUE },
            { nowGT, "+1m", ConditionResult.TRUE },
            { nowGT, "+1w", ConditionResult.TRUE },

            { oneMonthAheadGT, "1", ConditionResult.FALSE },
            { oneMonthAheadGT, "+30s", ConditionResult.FALSE },
            { oneMonthAheadGT, "+2h", ConditionResult.FALSE },
            { oneMonthAheadGT, "+1m", ConditionResult.FALSE },
            { nowGT, "-1d", ConditionResult.FALSE },
            { nowGT, "-2w", ConditionResult.FALSE },
            { nowGT, "-10m", ConditionResult.FALSE },
            { nowGT, "-1s", ConditionResult.FALSE },
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected MatchingRule getRule() {
        // Note that oid and names are not used by the test (ie, they could be any value, test should pass anyway)
        // Only the implementation class and the provided locale are really tested here.
        String oid = "1.3.6.1.4.1.26027.1.4.6";
        Schema schema = new SchemaBuilder(Schema.getCoreSchema()).
            buildMatchingRule(oid).
                syntaxOID(SchemaConstants.SYNTAX_GENERALIZED_TIME_OID).
                names("relativeTimeLTOrderingMatch.lt").
                implementation(TimeBasedMatchingRulesImpl.relativeTimeLTOMatchingRule()).
                addToSchema().
            toSchema();
        return schema.getMatchingRule(oid);
    }

    @Test
    public void testCreateIndexQuery() throws Exception {
        Assertion assertion = getRule().getAssertion(ByteString.valueOf("+5m"));

        String indexQuery = assertion.createIndexQuery(new FakeIndexQueryFactory(newIndexingOptions(), false));
        assertThat(indexQuery).startsWith("rangeMatch(rt.ext, '' < value < '");
    }
}
