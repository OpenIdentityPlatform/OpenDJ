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

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.schema.AbstractSubstringMatchingRuleImplTest.FakeIndexQueryFactory;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.forgerock.util.time.TimeService;

import static org.fest.assertions.Assertions.*;
import static org.forgerock.opendj.ldap.schema.AbstractSubstringMatchingRuleImplTest.*;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.*;

@SuppressWarnings("javadoc")
@Test
public class PartialDateAndTimeMatchingRuleTestCase extends MatchingRuleTest {

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
            { "20140630101080Z" },
        };
    }

    @DataProvider(name = "matchingRuleInvalidAssertionValues")
    public Object[][] createMatchingRuleInvalidAssertionValues() {
        return new Object[][] {
            { " " },
            { "bla" },
            // invalid time unit values
            { "-1Y03M11D12h48m32s" },
            { "0Y03M11D12h48m32s" },
            { "2014Y-1M11D12h48m32s" },
            { "2014Y0M11D12h48m32s" },
            { "2014Y13M11D12h48m32s" },
            { "2014Y03M-1D12h48m32s" },
            { "2014Y03M0D12h48m32s" },
            { "2014Y13M32D12h48m32s" },
            { "2014Y03M11D-1h48m32s" },
            { "2014Y03M11D24h48m32s" },
            { "2014Y03M11D12h-1m32s" },
            { "2014Y03M11D12h60m32s" },
            { "2014Y03M11D12h48m-1s" },
            { "2014Y03M11D12h48m61s" },
            // duplicate each time unit
            { "1Y2014Y03M11D12h" },
            { "2014Y1M03M11D12h" },
            { "2014Y03M1D11D12h" },
            { "2014Y03M11D1h12h" },
            { "2014Y03M11D12h1m48m" },
            { "2014Y03M11D12h48m1s32s" },
            // February and non leap years
            { "2014Y02M29D" },
            { "1800Y02M29D" },
            { "2000Y02M30D" },
            { "2000Y02M31D" },
            // 31st of months
            { "2012Y04M31D" },
            { "2012Y06M31D" },
            { "2012Y09M31D" },
            { "2012Y11M31D" },
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

        final SimpleDateFormat partialTimeUpToSeconds = new SimpleDateFormat("yyyy'Y'MM'M'dd'D'HH'h'mm'm'ss's'");
        final SimpleDateFormat generalizedTimeUpToSeconds = new SimpleDateFormat("yyyyMMddHHmmss'Z'");
        final SimpleDateFormat partialTimeUpToMinutes = new SimpleDateFormat("yyyy'Y'MM'M'dd'D'HH'h'mm'm'");
        final SimpleDateFormat generalizedTimeUpToMinutes = new SimpleDateFormat("yyyyMMddHHmm'Z'");
        final SimpleDateFormat partialTimeUpToHours = new SimpleDateFormat("yyyy'Y'MM'M'dd'D'HH'h'");
        final SimpleDateFormat generalizedTimeUpToHours = new SimpleDateFormat("yyyyMMddHH'Z'");

        return new Object[][] {
            // expect 3 values : attribute value, assertion value, result

            // use now date and one month ahead dates
            { generalizedTimeUpToSeconds.format(nowDate), partialTimeUpToSeconds.format(nowDate),
                ConditionResult.TRUE },
            { generalizedTimeUpToSeconds.format(oneMonthAheadDate), partialTimeUpToSeconds.format(oneMonthAheadDate),
                ConditionResult.TRUE },
            { generalizedTimeUpToMinutes.format(nowDate), partialTimeUpToMinutes.format(nowDate),
                ConditionResult.TRUE },
            { generalizedTimeUpToMinutes.format(oneMonthAheadDate), partialTimeUpToMinutes.format(oneMonthAheadDate),
                ConditionResult.TRUE },
            { generalizedTimeUpToHours.format(nowDate), partialTimeUpToHours.format(nowDate),
                ConditionResult.TRUE },
            { generalizedTimeUpToHours.format(oneMonthAheadDate), partialTimeUpToHours.format(oneMonthAheadDate),
                ConditionResult.TRUE },
            // 29th of months and leap years
            { "20120329120000Z", "2012Y03M29D", ConditionResult.TRUE },
            { "20120229120000Z", "2012Y02M29D", ConditionResult.TRUE },
            { "20000229120000Z", "2000Y02M29D", ConditionResult.TRUE },
            // Generalized time implementation does not allow leap seconds
            // because Java does not support them. Apparently, it never will support them:
            // @see http://bugs.java.com/bugdatabase/view_bug.do?bug_id=4272347
            // leap seconds are allowed, even though no formula exists to validate them
            { "20120630235959Z", "2012Y06M30D23h59m60s", ConditionResult.FALSE },
            // no match
            { "20111231235930Z", "2012Y12M31D23h59m30s", ConditionResult.FALSE },
            { "20121031235930Z", "2012Y12M31D23h59m30s", ConditionResult.FALSE },
            { "20121230235930Z", "2012Y12M31D23h59m30s", ConditionResult.FALSE },
            { "20121231225930Z", "2012Y12M31D23h59m30s", ConditionResult.FALSE },
            { "20121231235830Z", "2012Y12M31D23h59m30s", ConditionResult.FALSE },
            { "20121231235829Z", "2012Y12M31D23h59m30s", ConditionResult.FALSE },
            // 30th of months
            { "19820930120000Z", "1982Y09M30D", ConditionResult.TRUE },
            // 31st of months
            { "20120131120000Z", "2012Y01M31D", ConditionResult.TRUE },
            { "20120331120000Z", "2012Y03M31D", ConditionResult.TRUE },
            { "20120531120000Z", "2012Y05M31D", ConditionResult.TRUE },
            { "20120731120000Z", "2012Y07M31D", ConditionResult.TRUE },
            { "20120831120000Z", "2012Y08M31D", ConditionResult.TRUE },
            { "20121031120000Z", "2012Y10M31D", ConditionResult.TRUE },
            { "20121231120000Z", "2012Y12M31D", ConditionResult.TRUE },
            // Only single time units
            { "20121231123000Z", "2012Y", ConditionResult.TRUE },
            { "20121231123000Z", "2012Y12M", ConditionResult.TRUE },
            { "20121231123000Z", "2012Y31D", ConditionResult.TRUE },
            { "20121231123000Z", "2012Y12h", ConditionResult.TRUE },
            { "20121231123000Z", "2012Y30m", ConditionResult.TRUE },
            { "20121231123000Z", "2012Y0s", ConditionResult.TRUE },
        };
    }

    /** {@inheritDoc} */
    @Override
    protected MatchingRule getRule() {
        return CoreSchema.getInstance().getMatchingRule(SchemaConstants.MR_PARTIAL_DATE_AND_TIME_OID);
    }

    @Test
    public void testCreateIndexQuery() throws Exception {
        Assertion assertion = getRule().getAssertion(ByteString.valueOfUtf8("2012Y"));

        String indexQuery = assertion.createIndexQuery(new FakeIndexQueryFactory(newIndexingOptions(), false));
        assertThat(indexQuery).matches(
                "intersect\\[exactMatch\\(" + MR_PARTIAL_DATE_AND_TIME_NAME + ", value=='[0-9A-Z ]*'\\)\\]");
    }
}
