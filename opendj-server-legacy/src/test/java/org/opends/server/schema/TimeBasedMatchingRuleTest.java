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
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2009-2010 Sun Microsystems, Inc.
 * Portions Copyright 2010-2016 ForgeRock AS.
 */
package org.opends.server.schema;

import static org.assertj.core.api.Assertions.*;
import static org.opends.server.schema.GeneralizedTimeSyntax.format;
import static org.opends.server.schema.SchemaConstants.*;
import static org.testng.Assert.*;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Attribute;
import org.opends.server.types.Entry;
import org.opends.server.types.FilterType;
import org.opends.server.types.SearchFilter;
import org.opends.server.util.TimeThread;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** This class tests various time-based matching rules. */
@SuppressWarnings("javadoc")
public final class TimeBasedMatchingRuleTest
        extends SchemaTestCase
{
  /** User DNs to be used in tests. */
  private DN user1;
  private DN user2;
  private DN user3;
  private DN user4;
  private DN user5;

  private static final String TIME_ATTR = "test-time";
  private static final String DATE_ATTR = "test-date";

  private static final String TEST_TIME_DEF = "( test-time-oid NAME 'test-time' DESC 'Test time attribute'  EQUALITY "
      + "generalizedTimeMatch ORDERING  generalizedTimeOrderingMatch "
      + "SYNTAX 1.3.6.1.4.1.1466.115.121.1.24 SINGLE-VALUE )";
  private static final String TEST_DATE_DEF = "( test-date-oid NAME 'test-date' DESC 'Test date attribute'  EQUALITY "
      + "generalizedTimeMatch ORDERING  generalizedTimeOrderingMatch "
      + "SYNTAX 1.3.6.1.4.1.1466.115.121.1.24 SINGLE-VALUE )";
  private static final String TEST_OC_DEF = "( testoc-oid NAME 'testOC' SUP top AUXILIARY MUST test-time)";
  private static final String TEST_OC2_DEF = "( testoc2-oid NAME 'testOC2' SUP top AUXILIARY MUST test-date)";


  /**
   * Ensures that the Directory Server is running before executing the
   * testcases.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass
  public void startServer()
         throws Exception
  {
    user1 = DN.valueOf("cn=user1,dc=example,dc=com");
    user2 = DN.valueOf("cn=user2,dc=example,dc=com");
    user3 = DN.valueOf("cn=user3,dc=example,dc=com");
    user4 = DN.valueOf("cn=user4,dc=example,dc=com");
    user5 = DN.valueOf("cn=user5,dc=example,dc=com");

    /*
    Extend the schema and add an attribute which is based on
    generalizedTimeSyntax. Since all the existing attributes based
    on that syntax are read-only, let us create a new attribute and
    add it.*/
   int resultCode = TestCaseUtils.applyModifications(true,
    "dn: cn=schema",
    "changetype: modify",
    "add: attributeTypes",
    "attributeTypes: " + TEST_TIME_DEF,
    "attributeTypes: " + TEST_DATE_DEF,
    "-",
    "add: objectclasses",
    "objectclasses: " + TEST_OC_DEF,
    "objectclasses: " + TEST_OC2_DEF
    );
    assertEquals(0, resultCode);
  }

  @AfterClass
  public void cleanup() throws Exception
  {
    int resultCode = TestCaseUtils.applyModifications(true,
        "dn: cn=schema",
        "changetype: modify",
        "delete: objectclasses",
        "objectclasses: " + TEST_OC_DEF,
        "objectclasses: " + TEST_OC2_DEF,
        "-",
        "delete: attributetypes",
        "attributeTypes: " + TEST_TIME_DEF,
        "attributeTypes: " + TEST_DATE_DEF
      );
      assertThat(resultCode).isEqualTo(0);
  }

  @DataProvider
  public Object[][] relativeTime()
  {
    return new Object[][] {
      // relativeTime less than expired events
      { TIME_ATTR + ":" + EXT_OMR_RELATIVE_TIME_LT_OID + ":=-60m", new DN[] { user1, user2, } },
      // relativeTime less than future events
      { TIME_ATTR + ":" + EXT_OMR_RELATIVE_TIME_LT_OID + ":=1d", new DN[] { user1, user2, user3, user5, } },
      // relativeTime greater than expired events
      { TIME_ATTR + ":" + EXT_OMR_RELATIVE_TIME_GT_OID + ":=-1h", new DN[] { user3, user4, user5, } },
      // relativeTime greater than future events
      { TIME_ATTR + ":" + EXT_OMR_RELATIVE_TIME_GT_OID + ":=0s", new DN[] { user3, user4, } },
    };
  }

  @Test(dataProvider = "relativeTime")
  public void testRelativeTimeUsingAssertion(String filterString, DN[] expectedDNs) throws Exception
  {
    SearchFilter filter = SearchFilter.createFilterFromString(filterString);
    assertThat(getMatchingEntryDNs(filter)).containsOnly(expectedDNs);
  }

  private Collection<DN> getMatchingEntryDNs(SearchFilter filter) throws Exception
  {
    AttributeType attrType = filter.getAttributeType();
    MatchingRule rule = DirectoryServer.getSchema().getMatchingRule(filter.getMatchingRuleID());
    Assertion assertion = rule.getAssertion(filter.getAssertionValue());

    Collection<DN> results = new ArrayList<>();
    for (Entry entry : makeEntries())
    {
      Attribute attribute = entry.getExactAttribute(AttributeDescription.create(attrType));
      if (attribute != null)
      {
        ByteString attrValue = rule.normalizeAttributeValue(attribute.iterator().next());
        if (assertion.matches(attrValue).toBoolean())
        {
          results.add(entry.getName());
        }
      }
    }
    return results;
  }

  /** Test to search using the relative time matching rule with index. */
  @Test(dataProvider = "relativeTime")
  public void testRelativeTimeWithIndex(String filterString, DN[] expectedDNs) throws Exception
  {
    FakeEntryIndex index = new FakeEntryIndex(TIME_ATTR);
    index.addAll(makeEntries());
    Collection<Entry> entries = index.evaluateFilter(filterString);
    assertThat(toNames(entries)).containsOnly(expectedDNs);
  }

  private List<DN> toNames(Collection<? extends Entry> entries)
  {
    List<DN> results = new ArrayList<>();
    for (Entry entry : entries)
    {
      results.add(entry.getName());
    }
    return results;
  }

  /**
   * Test to match the attribute and the assertion values using a partial date and time
   * matching rule.
   */
  @Test(dataProvider="partialDateTimeValues")
  public void testPartialDateNTimeMatch(long timeInMillis, String generalizedTime, String assertionValue)
      throws Exception
  {
    MatchingRule partialTimeRule = DirectoryServer.getSchema().getMatchingRule(EXT_PARTIAL_DATE_TIME_NAME);
    Assertion assertion = partialTimeRule.getAssertion(ByteString.valueOfUtf8(assertionValue));
    assertEquals(assertion.matches(ByteString.valueOfLong(timeInMillis)), ConditionResult.TRUE);
  }

  @Test(dataProvider="partialDateTimeValues")
  public void testPartialDateNTimeMatchViaIndex(long timeInMillis, String generalizedTime, String assertionValue)
      throws Exception
  {
    ByteString attrValue = ByteString.valueOfUtf8(generalizedTime);
    ByteString assertValue = ByteString.valueOfUtf8(assertionValue);

    FakeByteStringIndex fakeIndex = new FakeByteStringIndex(EXT_PARTIAL_DATE_TIME_NAME);
    fakeIndex.add(attrValue);
    Set<ByteString> attrValues = fakeIndex.evaluateAssertionValue(assertValue, FilterType.EXTENSIBLE_MATCH);
    assertThat(attrValues).containsOnly(attrValue);
  }



  /** Tests the assertion syntax of the relative time matching rules. */
  @Test(dataProvider= "relativeTimeValues")
  public void testRelativeTimeMatchingRuleAssertionSyntax(String assertion,boolean isValid)
  {
    MatchingRule relativeTimeLTRule = DirectoryServer.getSchema().getMatchingRule(EXT_OMR_RELATIVE_TIME_LT_ALT_NAME);
    try
    {
      relativeTimeLTRule.getAssertion(ByteString.valueOfUtf8(assertion));
      // An invalid value can't get away without throwing exception.
      assertTrue(isValid);
    }
    catch (DecodeException e)
    {
      //invalid values will throw an exception.
      assertFalse(isValid);
    }
  }



  /** Tests the assertion syntax of the partial date and time matching rules. */
  @Test(dataProvider= "partialDateTimeSyntaxes")
  public void testPartialDateTimeMatchingRuleAssertionSyntax(String assertion,boolean isValid)
  {
    MatchingRule partialDTRule = DirectoryServer.getSchema().getMatchingRule(EXT_PARTIAL_DATE_TIME_OID);
    try
    {
      partialDTRule.getAssertion(ByteString.valueOfUtf8(assertion));
      assertTrue(isValid);
    }
    catch (DecodeException e)
    {
      //invalid values will throw an exception.
      assertFalse(isValid);
    }
  }



  /** Generates data for testing relative time matching rule assertion syntax. */
  @DataProvider
  public Object[][] relativeTimeValues()
  {
    return new Object[][] {
      {"1s",true},
      {"1s0d",false},
      {"-1d",true},
      {"2h",true},
      {"+2w",true},
      {"0",true},
      {"0s",true},
      {"0d",true},
      {"xyz",false},
      {"12w-2d",false},
      {"1s2s",false},
      {"1d4s5d",false}
    };
  }


  /** Generates the data for testing partial time date and time values. */
  @DataProvider
  public Object[][] partialDateTimeValues()
  {
    SimpleDateFormat sdf = new SimpleDateFormat("YYYYMMddHHmmssZ");
    GregorianCalendar c = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
    c.setLenient(false);
    c.clear();
    sdf.setCalendar(c);
    c.set(Calendar.HOUR_OF_DAY,23);
    c.set(Calendar.MINUTE,0);
    c.set(Calendar.SECOND,0);
    long time1 = c.getTimeInMillis();
    String format1 = sdf.format(c.getTime());
    c.set(Calendar.HOUR_OF_DAY,00);
    c.set(Calendar.MINUTE,59);
    c.set(Calendar.SECOND,59);
    long time2 = c.getTimeInMillis();
    String format2 = sdf.format(c.getTime());

    return new Object[][] {
      { time1, format1, "0s" },
      { time1, format1, "0m" },
      { time1, format1, "23h" },
      { time2, format2, "59m59s" },
      { time2, format2, "0h59m59s" },
      { time2, format2, "01D01M" },
    };
  }



  /** Generates data for testing partial date and time assertion syntax. */
  @DataProvider
  public Object[][] partialDateTimeSyntaxes()
  {
   //Get the current time.
   GregorianCalendar cal =
           new GregorianCalendar(TimeZone.getTimeZone("UTC"));
   cal.setLenient(false);

    //Get the date today.
    int second = cal.get(Calendar.SECOND);
    int minute = cal.get(Calendar.MINUTE);
    int hour = cal.get(Calendar.HOUR);
    int date = cal.get(Calendar.DATE);
    int month = cal.get(Calendar.MONTH) + 1;
    int year = cal.get(Calendar.YEAR);

    return new Object[][] {
      {"20MM30DD1978YY",false},
      {"02MM29DD2009YY",false},
      {"02MM31DD2010YY",false},
      {"-1s",false},
      {"02M29D2008Y",true},
      {"DDYY",false},
      {"02D",true},
      {"12M",true},
      {"1978Y",true},
      {"0MM",false},
      {"20MM03DD10MM",false},
      {"00s12m13h",true},
      {"00s12m14h1M3D1978Y",true},
      {"1s",true},
      {"12m",true},
      {"23h",true},
      {"61s",false},
      {"60m",false},
      {"24h",false},
      {second+"s",true},
      {minute+"m",true},
      {hour+"h",true},
      {date+"D",true},
      {month+"M",true},
      {year+"Y",true},
      {month+"M"+date+"D",true},
      {year+"Y"+date+"D",true},
      {month+"M"+year+"Y"+date+"D",true}
    };
  }

  private List<Entry> makeEntries() throws Exception
  {
    // Get the current time from the TimeThread. Using the current time from new
    // calendar may fail if the time thread using a stale time.
    long currentTime = TimeThread.getTime();

    return TestCaseUtils.makeEntries(
      "dn: cn=user1,dc=example,dc=com",
      "objectclass: person",
      "objectclass: testoc",
      "cn: user1",
      "sn: user1",
      TIME_ATTR + ": "+ format(currentTime-4000*1000), //more than 1 hour old.
      "",
      "dn: cn=user2,dc=example,dc=com",
      "objectclass: person",
      "objectclass: testoc",
      "cn: user2",
      "sn: user2",
      TIME_ATTR + ": " + format(currentTime-25*3600*1000), //more than  a day old.
      "",
      "dn: cn=user3,dc=example,dc=com",
      "objectclass: person",
      "objectclass: testoc",
      "cn: user3",
      "sn: user3",
      TIME_ATTR + ": " + format(currentTime+4000*1000),  //more than 1 hour in advance.
      "",
      "dn: cn=user4,dc=example,dc=com",
      "objectclass: person",
      "objectclass: testoc",
      "cn: user4",
      "sn: user4",
      TIME_ATTR + ": " + format(currentTime+25*3600*1000),  // more than 1 day in advance
      "",
      "dn: cn=user5,dc=example,dc=com",
      "objectclass: person",
      "objectclass: testoc",
      "cn: user5",
      "sn: user5",
      TIME_ATTR + ": " + format(currentTime), // now.
      "",
      "dn: cn=user6,dc=example,dc=com",
      "objectclass: person",
      "objectclass: testoc2",
      "cn: user6",
      "sn: user6",
      DATE_ATTR + ": 19651101000000Z", // Nov 1st 1965
      "",
      "dn: cn=user7,dc=example,dc=com",
      "objectclass: person",
      "objectclass: testoc2",
      "cn: user7",
      "sn: user7",
      DATE_ATTR + ": 20101104000000Z", // Nov 4th 2010
      "",
      "dn: cn=user8,dc=example,dc=com",
      "objectclass: person",
      "objectclass: testoc2",
      "cn: user8",
      "sn: user8",
      DATE_ATTR + ": 20000101000000Z" // Jan 1st 2000
    );
  }
}
