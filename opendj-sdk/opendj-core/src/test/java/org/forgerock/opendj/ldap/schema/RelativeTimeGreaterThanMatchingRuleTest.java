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
 *      Copyright 2014-2015 ForgeRock AS.
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
import org.forgerock.util.time.TimeService;

import static org.fest.assertions.Assertions.*;
import static org.forgerock.opendj.ldap.schema.AbstractSubstringMatchingRuleImplTest.*;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.*;

@SuppressWarnings("javadoc")
@Test
public class RelativeTimeGreaterThanMatchingRuleTest extends MatchingRuleTest {

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    @Override
    @DataProvider(name = "matchingrules")
    public Object[][] createMatchingRuleTest() {
        final Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(TimeService.SYSTEM.now());
        final Date nowDate = calendar.getTime();
        calendar.add(Calendar.MONTH, 1);
        final Date oneMonthAheadDate = calendar.getTime();

        final String nowGT = GeneralizedTime.valueOf(nowDate).toString();
        final String oneMonthAheadGT = GeneralizedTime.valueOf(oneMonthAheadDate).toString();

        return new Object[][] {
            // attribute value, assertion value, result
            { oneMonthAheadGT, "1", ConditionResult.TRUE },
            { oneMonthAheadGT, "+1s", ConditionResult.TRUE },
            { oneMonthAheadGT, "+1h", ConditionResult.TRUE },
            { oneMonthAheadGT, "+1m", ConditionResult.TRUE },
            { nowGT, "-30d", ConditionResult.TRUE },
            { nowGT, "-30w", ConditionResult.TRUE },
            { nowGT, "-30m", ConditionResult.TRUE },
            { nowGT, "-30s", ConditionResult.TRUE },

            { oneMonthAheadGT, "6w", ConditionResult.FALSE },
            { nowGT, "1d", ConditionResult.FALSE },
            { nowGT, "10s", ConditionResult.FALSE },
            { nowGT, "+1h", ConditionResult.FALSE },
            { nowGT, "+1m", ConditionResult.FALSE },
            { nowGT, "+1w", ConditionResult.FALSE },
            // OPENDJ-2397 - dates before 1970 have negative ms.
            {"19000101010203Z", "1d", ConditionResult.FALSE},
        };
    }

    /** {@inheritDoc} */
    @Override
    protected MatchingRule getRule() {
        return CoreSchema.getInstance().getMatchingRule(SchemaConstants.OMR_RELATIVE_TIME_GREATER_THAN_OID);
    }

    @Test
    public void testCreateIndexQuery() throws Exception {
        Assertion assertion = getRule().getAssertion(ByteString.valueOfUtf8("+5m"));

        String indexQuery = assertion.createIndexQuery(new FakeIndexQueryFactory(newIndexingOptions(), false));
        assertThat(indexQuery).startsWith("rangeMatch(" + EMR_GENERALIZED_TIME_NAME + ", '")
                .endsWith("' < value < '')");
    }
}
