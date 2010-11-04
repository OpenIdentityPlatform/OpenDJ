/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2010 ForgeRock AS.
 */


package org.opends.server.schema;

import java.util.Calendar;
import java.util.GregorianCalendar;
import static org.testng.Assert.*;

import java.util.List;

import java.util.TimeZone;
import org.opends.server.TestCaseUtils;
import org.opends.server.api.MatchingRule;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.types.ByteString;
import org.opends.server.types.ConditionResult;
import org.opends.server.types.DN;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchScope;
import org.opends.server.util.TimeThread;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.opends.server.schema.GeneralizedTimeSyntax.*;
import static org.opends.server.schema.SchemaConstants.*;

/**
 * This class tests various time-based matching rules.
 */
public final class TimeBasedMatchingRuleTest
        extends SchemaTestCase
{
  //User DNs to be used in tests.
  private DN user1;

  private DN user2 ;

  private DN user3;

  private DN user4;

  private DN user5;

  private DN user6;
  private DN user7;
  private DN user8;

 private final static String TIME_ATTR = "test-time";
 private final static String DATE_ATTR = "test-date";


  /**
   * Ensures that the Directory Server is running before executing the
   * testcases.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass()
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();

    TestCaseUtils.initializeTestBackend(true);

    user1 = DN.decode("cn=user1,dc=example,dc=com");
    user2 = DN.decode("cn=user2,dc=example,dc=com");
    user3 = DN.decode("cn=user3,dc=example,dc=com");
    user4 = DN.decode("cn=user4,dc=example,dc=com");
    user5 = DN.decode("cn=user5,dc=example,dc=com");
    user6 = DN.decode("cn=user6,dc=example,dc=com");
    user7 = DN.decode("cn=user7,dc=example,dc=com");
    user8 = DN.decode("cn=user!,dc=example,dc=com");

    /**
    Extend the schema and add an attribute which is baseed on
    generalizedTimeSyntax. Since all the existing attributes based
    on that syntax are read-only, let us create a new attribute and
    add it.*/
   int resultCode = TestCaseUtils.applyModifications(true,
    "dn: cn=schema",
    "changetype: modify",
    "add: attributeTypes",
    "attributeTypes: ( test-time-oid NAME 'test-time' DESC 'Test time attribute'  EQUALITY   " +
    "generalizedTimeMatch ORDERING  generalizedTimeOrderingMatch SYNTAX 1.3.6.1.4.1.1466.115.121.1.24 SINGLE-VALUE )",
    "attributeTypes: ( test-date-oid NAME 'test-date' DESC 'Test date attribute'  EQUALITY   " +
    "generalizedTimeMatch ORDERING  generalizedTimeOrderingMatch SYNTAX 1.3.6.1.4.1.1466.115.121.1.24 SINGLE-VALUE )",
    "-",
    "add: objectclasses",
    "objectclasses: ( testoc-oid NAME 'testOC' SUP top AUXILIARY MUST test-time)",
    "objectclasses: ( testoc2-oid NAME 'testOC2' SUP top AUXILIARY MUST test-date)"
    );
    assertTrue(resultCode == 0);
  }



  /**
   * Test to search using the less-than relative time matching rule for expired events.
   */
  @Test()
  public void testRTLessThanExpiredEvents() throws Exception
  {
    try
    {
      populateEntries();
      InternalClientConnection conn =
           InternalClientConnection.getRootConnection();

      InternalSearchOperation searchOperation =
           new InternalSearchOperation(
                conn,
                InternalClientConnection.nextOperationID(),
                InternalClientConnection.nextMessageID(),
                null,
                ByteString.valueOf("dc=example,dc=com"),
                SearchScope.WHOLE_SUBTREE,
                DereferencePolicy.NEVER_DEREF_ALIASES,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                false,
                LDAPFilter.decode(TIME_ATTR+":"+EXT_OMR_RELATIVE_TIME_LT_OID+":=-60m"), //
                null, null);

      searchOperation.run();
      assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
      List<SearchResultEntry> entries = searchOperation.getSearchEntries();
      assertTrue(dnFoundInEntryList(entries,user1,user2));
    }
    finally
    {
      TestCaseUtils.clearJEBackend(false, "userRoot", "dc=example,dc=com");
    }
  }



  /**
   * Test to search using the less-than relative time matching rule for future events.
   */
  @Test()
  public void testRTLessThanFutureEvents() throws Exception
  {
    try
    {
      populateEntries();
      InternalClientConnection conn =
           InternalClientConnection.getRootConnection();

      InternalSearchOperation searchOperation =
           new InternalSearchOperation(
                conn,
                InternalClientConnection.nextOperationID(),
                InternalClientConnection.nextMessageID(),
                null,
                ByteString.valueOf("dc=example,dc=com"),
                SearchScope.WHOLE_SUBTREE,
                DereferencePolicy.NEVER_DEREF_ALIASES,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                false,
                LDAPFilter.decode(TIME_ATTR+":"+EXT_OMR_RELATIVE_TIME_LT_OID+":=1d"),
                null, null);

      searchOperation.run();
      assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
      List<SearchResultEntry> entries = searchOperation.getSearchEntries();
      assertTrue(entries.size() == 4 && dnFoundInEntryList(entries,user1,user2,user3,user5));
    }
    finally
    {
      TestCaseUtils.clearJEBackend(false, "userRoot", "dc=example,dc=com");
    }
  }



    /**
   * Test to search using the greater-than relative time matching rule for expired events.
   */
  @Test()
  public void testRTGreaterThanExpiredEvents() throws Exception
  {
    try
    {
      populateEntries();
      InternalClientConnection conn =
           InternalClientConnection.getRootConnection();

      InternalSearchOperation searchOperation =
           new InternalSearchOperation(
                conn,
                InternalClientConnection.nextOperationID(),
                InternalClientConnection.nextMessageID(),
                null,
                ByteString.valueOf("dc=example,dc=com"),
                SearchScope.WHOLE_SUBTREE,
                DereferencePolicy.NEVER_DEREF_ALIASES,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                false,
                LDAPFilter.decode(TIME_ATTR+":"+EXT_OMR_RELATIVE_TIME_GT_OID+":=-1h"),
                null, null);

      searchOperation.run();
      assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
      List<SearchResultEntry> entries = searchOperation.getSearchEntries();
      assertTrue(entries.size()==3 && dnFoundInEntryList(entries,user3,user4,user5));
    }
    finally
    {
      TestCaseUtils.clearJEBackend(false, "userRoot", "dc=example,dc=com");
    }
  }



    /**
   * Test to search using the greater-than relative time matching rule for future events.
   */
  @Test()
  public void testRTGreaterThanFutureEvents() throws Exception
  {
    try
    {
      populateEntries();
      InternalClientConnection conn =
           InternalClientConnection.getRootConnection();

      InternalSearchOperation searchOperation =
           new InternalSearchOperation(
                conn,
                InternalClientConnection.nextOperationID(),
                InternalClientConnection.nextMessageID(),
                null,
                ByteString.valueOf("dc=example,dc=com"),
                SearchScope.WHOLE_SUBTREE,
                DereferencePolicy.NEVER_DEREF_ALIASES,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                false,
                LDAPFilter.decode(TIME_ATTR+":"+EXT_OMR_RELATIVE_TIME_GT_OID+":=0s"),
                null, null);

      searchOperation.run();
      assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
      List<SearchResultEntry> entries = searchOperation.getSearchEntries();
      assertTrue(entries.size()==2 && dnFoundInEntryList(entries,user3,user4));
    }
    finally
    {
      TestCaseUtils.clearJEBackend(false, "userRoot", "dc=example,dc=com");
    }
  }



   /**
    * Test to search using the partial date and time matching rule
    * for an assertion value.
    * Dates for this test are hardcoded to avoid test failures depending
    * on when the tests are launched.
    */
  @Test()
  public void testPartialDateNTimeMatchingRuleUsingSearch() throws Exception
  {
    try
    {
      populateEntries();
      InternalClientConnection conn =
           InternalClientConnection.getRootConnection();
      String assertion = "01D11M";
      InternalSearchOperation searchOperation =
           new InternalSearchOperation(
                conn,
                InternalClientConnection.nextOperationID(),
                InternalClientConnection.nextMessageID(),
                null,
                ByteString.valueOf("dc=example,dc=com"),
                SearchScope.WHOLE_SUBTREE,
                DereferencePolicy.NEVER_DEREF_ALIASES,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                false,
                LDAPFilter.decode(DATE_ATTR+":"+EXT_PARTIAL_DATE_TIME_OID+":="+assertion),
                null,null);

      searchOperation.run();
      assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
      List<SearchResultEntry> entries = searchOperation.getSearchEntries();
      assertTrue(entries.size()==1 && dnFoundInEntryList(entries,user6));
    }
    finally
    {
      TestCaseUtils.clearJEBackend(false, "userRoot", "dc=example,dc=com");
    }
  }


  /**
   * Test to match the attribute and the assertion values using a partial date and time
   * matching rule.
   */
  @Test(dataProvider="partialDateTimeValues")
  public void testPartialDateNTimeMatch(long attributeValue,String assertionValue) throws Exception
  {
    MatchingRule partialTimeRule = DirectoryServer.getMatchingRule(
            EXT_PARTIAL_DATE_TIME_NAME.toLowerCase());
    ByteString str = partialTimeRule.normalizeAssertionValue(ByteString.valueOf(assertionValue));
    assertTrue(partialTimeRule.valuesMatch(ByteString.valueOf(attributeValue), str) ==
            ConditionResult.TRUE);
  }



  /**
   * Tests the assertion syntax of the relative time matching rules.
   */
  @Test(dataProvider= "relativeTimeValues")
  public void testRelativeTimeMatchingRuleAssertionSyntax(String assertion,boolean isValid)
  {
    MatchingRule relativeTimeLTRule =
            DirectoryServer.getOrderingMatchingRule(
            EXT_OMR_RELATIVE_TIME_LT_ALT_NAME.toLowerCase());
    boolean exception = false;
    try
    {
      relativeTimeLTRule.normalizeAssertionValue(ByteString.valueOf(assertion));
    }
    catch(DirectoryException e)
    {
      //invalid values will throw an exception.
      exception = true;
      assertTrue(!isValid);
    }
    if(!isValid)
    {
      //An invalid value can't get away without throwing exception.
      assertTrue(exception);
    }
  }



  /**
   * Tests the assertion syntax of the partial date and time matching rules.
   */
  @Test(dataProvider= "partialDateTimeSyntaxes")
  public void testPartialDateTimeMatchingRuleAssertionSyntax(String assertion,boolean isValid)
  {
    MatchingRule partialDTRule =
            DirectoryServer.getMatchingRule(EXT_PARTIAL_DATE_TIME_OID);
    boolean exception = false;
    try
    {
      partialDTRule.normalizeAssertionValue(ByteString.valueOf(assertion));
    }
    catch(DirectoryException e)
    {
      //invalid values will throw an exception.
      exception = true;
      assertTrue(!isValid);
    }
    if(!isValid)
    {
      assertTrue(exception);
    }
  }



  /**
   * Generates data for testing relative time matching rule assertion syntax.
   */
  @DataProvider(name="relativeTimeValues")
  private Object[][] createRelativeTimeValues()
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


  /**
   * Generates the data for testing partial time date and time values.
   */
  @DataProvider(name="partialDateTimeValues")
  private Object[][] createPartialDateTimeValues()
  {
    GregorianCalendar c = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
    c.setLenient(false);
    c.clear();
    c.set(Calendar.HOUR_OF_DAY,23);
    c.set(Calendar.MINUTE,0);
    c.set(Calendar.SECOND,0);
    long time1 = c.getTimeInMillis();
    c.set(Calendar.HOUR_OF_DAY,00);
    c.set(Calendar.MINUTE,59);
    c.set(Calendar.SECOND,59);
    long time2 = c.getTimeInMillis();

    return new Object[][] {
      {time1,"0s"},
      {time1,"0m"},
      {time1,"23h"},
      {time2,"59m59s"},
      {time2,"0h59m59s"}
    };
  }



  /**
   * Generates data for testing partial date and time assertion syntax.
   */
  @DataProvider(name="partialDateTimeSyntaxes")
  private Object[][] createPartialDateTimeSyntaxes()
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



//validate if the args are found in the entries list.
  private boolean dnFoundInEntryList( List<SearchResultEntry> entries,DN ... dns)
  {
    for(DN dn: dns)
    {
      boolean found = false;
      for(SearchResultEntry entry: entries)
      {
        System.out.println("dn from the current entry is " + entry.getDN());
        if(entry.getDN().equals(dn))
        {
          found = true;
        }
      }
      if(!found)
      {
        return false;
      }
    }
    return true;
  }


  //Creates the entries.
  private void populateEntries() throws Exception
  {
    //Get the current time from the TimeThread. Using the current time from new
    // calendar may fail if the time thread using a stale time.
    long currentTime = TimeThread.getTime();

    TestCaseUtils.clearJEBackend(true, "userRoot", "dc=example,dc=com");
    TestCaseUtils.addEntries(
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
