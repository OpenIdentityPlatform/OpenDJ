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
 *      Copyright 2014 ForgeRock AS
 */
package org.opends.server.schema;

import java.text.SimpleDateFormat;
import java.util.*;

import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.DecodeException;
import org.opends.server.admin.std.server.MatchingRuleCfg;
import org.opends.server.api.MatchingRule;
import org.opends.server.util.TimeThread;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("javadoc")
public class TimeBasedMatchingRuleFactoryTest extends SchemaTestCase
{

  private static final String LESS_THAN_RELATIVE_TIME = "relativeTimeLTOrderingMatch";
  private static final String GREATER_THAN_RELATIVE_TIME = "relativeTimeGTOrderingMatch";
  private static final String PARTIAL_DATE_AND_TIME = "partialDateAndTimeMatchingRule";

  @DataProvider
  public Iterator<Object[]> validAssertionValuesDataProvider()
  {
    final SimpleDateFormat generalizedTimeFormatter = new SimpleDateFormat("yyyyMMddHHmmss'Z'");
    final Calendar cal = TimeThread.getCalendar();
    final Date nowDate = cal.getTime();
    final String nowGT = generalizedTimeFormatter.format(nowDate);
    cal.add(Calendar.MONTH, 1);
    final Date oneMonthAheadDate = cal.getTime();
    final String oneMonthAheadGT = generalizedTimeFormatter.format(oneMonthAheadDate);

    final Collection<Object[]> results = new LinkedList<Object[]>(Arrays.asList(new Object[][] {
          { LESS_THAN_RELATIVE_TIME,    /* now + */"1"/* s */, oneMonthAheadGT, ConditionResult.FALSE },
          { GREATER_THAN_RELATIVE_TIME, /* now + */"1"/* s */, oneMonthAheadGT, ConditionResult.TRUE },
          { LESS_THAN_RELATIVE_TIME,    /* now */"+1s", oneMonthAheadGT, ConditionResult.FALSE },
          { GREATER_THAN_RELATIVE_TIME, /* now */"+1s", oneMonthAheadGT, ConditionResult.TRUE },
          { LESS_THAN_RELATIVE_TIME,    /* now */"+1m", oneMonthAheadGT, ConditionResult.FALSE },
          { GREATER_THAN_RELATIVE_TIME, /* now */"+1m", oneMonthAheadGT, ConditionResult.TRUE },
          { LESS_THAN_RELATIVE_TIME,    /* now */"+1h", oneMonthAheadGT, ConditionResult.FALSE },
          { GREATER_THAN_RELATIVE_TIME, /* now */"+1h", oneMonthAheadGT, ConditionResult.TRUE },
          { LESS_THAN_RELATIVE_TIME,    /* now */"-30d", nowGT, ConditionResult.FALSE },
          { GREATER_THAN_RELATIVE_TIME, /* now */"-30d", nowGT, ConditionResult.TRUE },
          { LESS_THAN_RELATIVE_TIME,    /* now */"-30w", nowGT, ConditionResult.FALSE },
          { GREATER_THAN_RELATIVE_TIME, /* now */"-30w", nowGT, ConditionResult.TRUE },
          // 29th of months and leap years
          { PARTIAL_DATE_AND_TIME, "2012Y03M29D", "20120329120000Z", ConditionResult.TRUE },
          { PARTIAL_DATE_AND_TIME, "2012Y02M29D", "20120229120000Z", ConditionResult.TRUE },
          { PARTIAL_DATE_AND_TIME, "2000Y02M29D", "20000229120000Z", ConditionResult.TRUE },
          // Generalized time implementation does not allow leap seconds
          // because Java does not support them. Apparently, it never will support them:
          // @see http://bugs.java.com/bugdatabase/view_bug.do?bug_id=4272347
          // leap seconds are allowed, even though no formula exists to validate them
          { PARTIAL_DATE_AND_TIME, "2012Y06M30D23h59m60s", "20120630235959Z", ConditionResult.FALSE },
          // no match
          { PARTIAL_DATE_AND_TIME, "2012Y12M31D23h59m30s", "20111231235930Z", ConditionResult.FALSE },
          { PARTIAL_DATE_AND_TIME, "2012Y12M31D23h59m30s", "20121031235930Z", ConditionResult.FALSE },
          { PARTIAL_DATE_AND_TIME, "2012Y12M31D23h59m30s", "20121230235930Z", ConditionResult.FALSE },
          { PARTIAL_DATE_AND_TIME, "2012Y12M31D23h59m30s", "20121231225930Z", ConditionResult.FALSE },
          { PARTIAL_DATE_AND_TIME, "2012Y12M31D23h59m30s", "20121231235830Z", ConditionResult.FALSE },
          { PARTIAL_DATE_AND_TIME, "2012Y12M31D23h59m30s", "20121231235829Z", ConditionResult.FALSE },
          // 30th of months
          { PARTIAL_DATE_AND_TIME, "1982Y09M30D", "19820930120000Z", ConditionResult.TRUE },
          // 31st of months
          { PARTIAL_DATE_AND_TIME, "2012Y01M31D", "20120131120000Z", ConditionResult.TRUE },
          { PARTIAL_DATE_AND_TIME, "2012Y03M31D", "20120331120000Z", ConditionResult.TRUE },
          { PARTIAL_DATE_AND_TIME, "2012Y05M31D", "20120531120000Z", ConditionResult.TRUE },
          { PARTIAL_DATE_AND_TIME, "2012Y07M31D", "20120731120000Z", ConditionResult.TRUE },
          { PARTIAL_DATE_AND_TIME, "2012Y08M31D", "20120831120000Z", ConditionResult.TRUE },
          { PARTIAL_DATE_AND_TIME, "2012Y10M31D", "20121031120000Z", ConditionResult.TRUE },
          { PARTIAL_DATE_AND_TIME, "2012Y12M31D", "20121231120000Z", ConditionResult.TRUE },
          // Only single time units
          { PARTIAL_DATE_AND_TIME, "2012Y", "20121231123000Z", ConditionResult.TRUE },
          { PARTIAL_DATE_AND_TIME, "2012Y12M", "20121231123000Z", ConditionResult.TRUE },
          { PARTIAL_DATE_AND_TIME, "2012Y31D", "20121231123000Z", ConditionResult.TRUE },
          { PARTIAL_DATE_AND_TIME, "2012Y12h", "20121231123000Z", ConditionResult.TRUE },
          { PARTIAL_DATE_AND_TIME, "2012Y30m", "20121231123000Z", ConditionResult.TRUE },
          { PARTIAL_DATE_AND_TIME, "2012Y0s", "20121231123000Z", ConditionResult.TRUE },
    }));
    addPartialDateAndTimeData(results, nowDate, oneMonthAheadDate);
    return results.iterator();
  }

  private void addPartialDateAndTimeData(Collection<Object[]> results, Date... dates)
  {
    final SimpleDateFormat ptFormatterUpToSeconds = new SimpleDateFormat("yyyy'Y'MM'M'dd'D'HH'h'mm'm'ss's'");
    final SimpleDateFormat ptFormatterUpToMinutes = new SimpleDateFormat("yyyy'Y'MM'M'dd'D'HH'h'mm'm'");
    final SimpleDateFormat ptFormatterUpToHours = new SimpleDateFormat("yyyy'Y'MM'M'dd'D'HH'h'");
    final SimpleDateFormat gtFormatterUpToSeconds = new SimpleDateFormat("yyyyMMddHHmmss'Z'");
    final SimpleDateFormat gtFormatterUpToMinutes = new SimpleDateFormat("yyyyMMddHHmm'Z'");
    final SimpleDateFormat gtFormatterUpToHours = new SimpleDateFormat("yyyyMMddHH'Z'");

    for (Date date : dates)
    {
      String ptUpToSeconds = ptFormatterUpToSeconds.format(date);
      String gtUpToSeconds = gtFormatterUpToSeconds.format(date);
      results.add(new Object[] { PARTIAL_DATE_AND_TIME, ptUpToSeconds, gtUpToSeconds, ConditionResult.TRUE });
      String ptUpToMinutes = ptFormatterUpToMinutes.format(date);
      String gtUpToMinutes = gtFormatterUpToMinutes.format(date);
      results.add(new Object[] { PARTIAL_DATE_AND_TIME, ptUpToMinutes, gtUpToMinutes, ConditionResult.TRUE });
      String ptUpToHours = ptFormatterUpToHours.format(date);
      String gtUpToHours = gtFormatterUpToHours.format(date);
      results.add(new Object[] { PARTIAL_DATE_AND_TIME, ptUpToHours, gtUpToHours, ConditionResult.TRUE });
    }
  }

  @DataProvider
  public Object[][] invalidAssertionValuesDataProvider()
  {
    final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyyMMddHH");
    final Calendar cal = TimeThread.getCalendar();
    final String now = dateFormatter.format(cal.getTime()) + "Z";

    return new Object[][] {
      // { LESS_THAN_RELATIVE_TIME, "", now, null },
      { LESS_THAN_RELATIVE_TIME, "bla", now, null },
      { LESS_THAN_RELATIVE_TIME, /* now */"-30b", now, null },
      { LESS_THAN_RELATIVE_TIME, /* now */"-30ms", now, null },

      // { PARTIAL_DATE_AND_TIME, "", now, null },
      { PARTIAL_DATE_AND_TIME, "bla", now, null },
      // invalid time unit values
      { PARTIAL_DATE_AND_TIME, "-1Y03M11D12h48m32s", now, null },
      { PARTIAL_DATE_AND_TIME, "0Y03M11D12h48m32s", now, null },
      { PARTIAL_DATE_AND_TIME, "2014Y-1M11D12h48m32s", now, null },
      { PARTIAL_DATE_AND_TIME, "2014Y0M11D12h48m32s", now, null },
      { PARTIAL_DATE_AND_TIME, "2014Y13M11D12h48m32s", now, null },
      { PARTIAL_DATE_AND_TIME, "2014Y03M-1D12h48m32s", now, null },
      { PARTIAL_DATE_AND_TIME, "2014Y03M0D12h48m32s", now, null },
      { PARTIAL_DATE_AND_TIME, "2014Y13M32D12h48m32s", now, null },
      { PARTIAL_DATE_AND_TIME, "2014Y03M11D-1h48m32s", now, null },
      { PARTIAL_DATE_AND_TIME, "2014Y03M11D24h48m32s", now, null },
      { PARTIAL_DATE_AND_TIME, "2014Y03M11D12h-1m32s", now, null },
      { PARTIAL_DATE_AND_TIME, "2014Y03M11D12h60m32s", now, null },
      { PARTIAL_DATE_AND_TIME, "2014Y03M11D12h48m-1s", now, null },
      { PARTIAL_DATE_AND_TIME, "2014Y03M11D12h48m61s", now, null },
      // duplicate each time unit
      { PARTIAL_DATE_AND_TIME, "1Y2014Y03M11D12h", now, null },
      { PARTIAL_DATE_AND_TIME, "2014Y1M03M11D12h", now, null },
      { PARTIAL_DATE_AND_TIME, "2014Y03M1D11D12h", now, null },
      { PARTIAL_DATE_AND_TIME, "2014Y03M11D1h12h", now, null },
      { PARTIAL_DATE_AND_TIME, "2014Y03M11D12h1m48m", now, null },
      { PARTIAL_DATE_AND_TIME, "2014Y03M11D12h48m1s32s", now, null },
      // February and non leap years
      { PARTIAL_DATE_AND_TIME, "2014Y02M29D", now, null },
      { PARTIAL_DATE_AND_TIME, "1800Y02M29D", now, null },
      { PARTIAL_DATE_AND_TIME, "2000Y02M30D", now, null },
      { PARTIAL_DATE_AND_TIME, "2000Y02M31D", now, null },
      // 31st of months
      { PARTIAL_DATE_AND_TIME, "2012Y04M31D", now, null },
      { PARTIAL_DATE_AND_TIME, "2012Y06M31D", now, null },
      { PARTIAL_DATE_AND_TIME, "2012Y09M31D", now, null },
      { PARTIAL_DATE_AND_TIME, "2012Y11M31D", now, null },
    };
  }

  @Test(dataProvider = "validAssertionValuesDataProvider")
  public void testValidAssertionValues(String matchingRuleName,
      String assertionValue, String attributeValue,
      ConditionResult expectedResult) throws Exception
  {
    final MatchingRule rule = getMatchingRule(matchingRuleName);

    Assertion assertion = rule.getAssertion(ByteString.valueOf(assertionValue));
    ByteString normalizedAttributeValue =
        rule.normalizeAttributeValue(ByteString.valueOf(attributeValue));
    assertThat(assertion.matches(normalizedAttributeValue)).isEqualTo(expectedResult);
  }

  @Test(dataProvider = "invalidAssertionValuesDataProvider",
      expectedExceptions = DecodeException.class)
  public void testInvalidAssertionValues(String matchingRuleName,
      String assertionValue, String attributeValue,
      ConditionResult expectedResult) throws Exception
  {
    testValidAssertionValues(matchingRuleName, assertionValue, attributeValue, expectedResult);
  }

  private MatchingRule getMatchingRule(String matchingRuleName) throws Exception
  {
    final Collection<MatchingRule> mRules = getMatchingRules();
    assertThat(mRules).hasSize(3);
    for (MatchingRule mRule : mRules)
    {
      if (mRule.getNameOrOID().equals(matchingRuleName))
      {
        return mRule;
      }
    }
    fail("Could not find a matching rule named '" + matchingRuleName + "'");
    return null;
  }

  private Collection<MatchingRule> getMatchingRules() throws Exception
  {
    final TimeBasedMatchingRuleFactory factory =
        new TimeBasedMatchingRuleFactory();
    final MatchingRuleCfg cfg = mock(MatchingRuleCfg.class);
    factory.initializeMatchingRule(cfg);
    final Collection<MatchingRule> mRules = factory.getMatchingRules();
    verifyNoMoreInteractions(cfg);
    return mRules;
  }
}
