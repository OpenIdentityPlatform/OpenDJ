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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 */


package org.opends.server.schema;

import org.opends.server.api.EqualityMatchingRule;
import static org.testng.Assert.*;

import java.util.List;

import org.opends.server.TestCaseUtils;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.types.ByteString;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchScope;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.testng.annotations.DataProvider;


/**
 * This Test Class tests various matching rules for their compability
 * against RFC 4517,4518 and 3454.
 */
public final class StringPrepProfileTestCase
        extends SchemaTestCase
{
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
  }


  //Adds an entry for test.
  private void addEntry() throws Exception
  {
    TestCaseUtils.clearJEBackend(true, "userRoot", "dc=example,dc=com");
    TestCaseUtils.addEntries(
      "dn: cn=Jos\u00E9,dc=example,   dc=com",
            "objectClass: person",
            "cn: Jos\u00E9", // the value is precomposed.
            "sn: This\u00AD\u180D\uFE00\u0085\u0085is\u202A\u2028a\u0020test"
            );
  }



  /**
   * Tests the stringprep algorithm by adding an entry containing precomposed
   * DN and searching for it using the filter containing a combining sequence
   * and acute accent.
   */
  @Test()
  public void testUnicodeSearch() throws Exception
  {
    try
    {
      addEntry();

      InternalClientConnection conn =
           InternalClientConnection.getRootConnection();

      InternalSearchOperation searchOperation =
        new InternalSearchOperation(
        conn,
        InternalClientConnection.nextOperationID(),
        InternalClientConnection.nextMessageID(),
        null,
        ByteString.valueOf("dc=  example,dc=com"),
        SearchScope.WHOLE_SUBTREE,
        DereferencePolicy.NEVER_DEREF_ALIASES,
        Integer.MAX_VALUE,
        Integer.MAX_VALUE,
        false,
        //The filter uses a combining sequence e and acute accent.
        LDAPFilter.decode("&(cn=Jos\u0065\u0301)(sn=This is a test)"),
        null, null);

      searchOperation.run();
      assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
      List<SearchResultEntry> entries = searchOperation.getSearchEntries();
      //No results expected for jdk 5.
      //This will succeed only on all JREs >=1.6 or sun's jvm 5.
      assertTrue(entries.size()==1);
    }
    finally
    {
      TestCaseUtils.clearJEBackend(true, "userRoot", "dc=example,dc=com");
    }
  }



  /**
   * Tests the stringprep preparation sans any case folding. This is applicable
   * to case exact matching rules only.
   */
  @Test(dataProvider= "exactRuleData")
  public void testStringPrepNoCaseFold(String value1,
                             String value2, Boolean result) throws Exception
  {
    //Take any caseExact matching rule.
    EqualityMatchingRule rule = new CaseExactIA5EqualityMatchingRule();
    ByteString normalizedValue1 =
      rule.normalizeValue(ByteString.valueOf(value1));
    ByteString normalizedValue2 =
      rule.normalizeValue(ByteString.valueOf(value2));

    Boolean liveResult = rule.areEqual(normalizedValue1, normalizedValue2);
    assertEquals(result, liveResult);
  }

  
  //Generates data for case exact matching rules.
  @DataProvider(name="exactRuleData")
  private Object[][] createExactRuleData()
  {
    return new Object[][] {
      {"12345678", "12345678", true},
      {"ABC45678", "ABC45678", true},
      {"ABC45678", "abc45678", false},
      {"\u0020foo\u0020bar\u0020\u0020","foo bar",true},
      {"test\u00AD\u200D","test",true},
      {"foo\u000Bbar","foo\u0020bar",true},
      {"foo\u070Fbar" ,"foobar",true},
    };
  }


   /**
   * Tests the stringprep preparation with case folding. This is applicable
   * to case ignore matching rules only.
   */
  @Test(dataProvider= "caseFoldRuleData")
  public void testStringPrepWithCaseFold(String value1,
                             String value2, Boolean result) throws Exception
  {
    //Take any caseExact matching rule.
    EqualityMatchingRule rule = new CaseIgnoreEqualityMatchingRule();
    ByteString normalizedValue1 =
      rule.normalizeValue(ByteString.valueOf(value1));
    ByteString normalizedValue2 =
      rule.normalizeValue(ByteString.valueOf(value2));

    Boolean liveResult = rule.areEqual(normalizedValue1, normalizedValue2);
    assertEquals(result, liveResult);
  }


  //Generates data for case ignore matching rules.
  @DataProvider(name="caseFoldRuleData")
  private Object[][] createIgnoreRuleData()
  {
    return new Object[][] {
      {"12345678", "12345678", true},
      {"ABC45678", "abc45678", true},
      {"\u0020foo\u0020bar\u0020\u0020","foo bar",true},
      {"test\u00AD\u200D","test",true},
      {"foo\u000Bbar","foo\u0020bar",true},
      {"foo\u070Fbar" ,"foobar",true},
      {"foo\u0149bar","foo\u02BC\u006Ebar",true},
      {"foo\u017Bbar", "foo\u017Cbar",true},
      {"foo\u017BBAR", "foo\u017Cbar",true},
    };
  }
}
