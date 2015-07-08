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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2014 Manuel Gaupp
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.controls;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import org.opends.server.api.MatchingRuleFactory;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.opends.server.core.DirectoryServer;
import org.opends.server.schema.BooleanEqualityMatchingRuleFactory;
import org.opends.server.schema.DistinguishedNameEqualityMatchingRuleFactory;
import org.opends.server.schema.IntegerEqualityMatchingRuleFactory;
import org.opends.server.types.*;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.io.ASN1;
import org.opends.server.util.Base64;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Test MatchedValuesControl.
 */
public class MatchedValuesControlTest
    extends ControlsTestCase
{

  /**
   * Check "enum" values.
   */
  @Test
  public void checkFilterValues() throws Exception
  {
    assertEquals(MatchedValuesFilter.EQUALITY_MATCH_TYPE, (byte) 0xA3);
    assertEquals(MatchedValuesFilter.SUBSTRINGS_TYPE ,(byte)0xA4);
    assertEquals(MatchedValuesFilter.GREATER_OR_EQUAL_TYPE,(byte)0xA5);
    assertEquals(MatchedValuesFilter.LESS_OR_EQUAL_TYPE,(byte)0xA6);
    assertEquals(MatchedValuesFilter.PRESENT_TYPE ,(byte)0x87);
    assertEquals(MatchedValuesFilter.APPROXIMATE_MATCH_TYPE,(byte)0xA8);
    assertEquals(MatchedValuesFilter.EXTENSIBLE_MATCH_TYPE,(byte)0xA9);
  }

  @DataProvider(name = "equalityFilterData")
  public Object[][] createEqualityFilterData()
  {
    return new Object[][]
    {
    { "description", "description" },
    { "objectclass", "top" },
    { "faketype", "fakevalue" }, };
  }

  /**
   * Test createEqualityFilter.
   */
  @Test(dataProvider = "equalityFilterData")
  public void checkCreateEqualityFilter(String type, String value)
      throws Exception
  {
    try
    {
      MatchedValuesFilter.createEqualityFilter((String) null, (ByteString) null);
      fail("Expected NullPointerException");
    }
    catch (NullPointerException e)
    {
      // excepted behavior
    }

    try
    {
      MatchedValuesFilter.createEqualityFilter(type, (ByteString) null);
      fail("Expected NullPointerException");
    }
    catch (NullPointerException e)
    {
      // excepted behavior
    }

    MatchedValuesFilter mvf = MatchedValuesFilter.createEqualityFilter(type, ByteString.valueOf(value));
    assertNotNull(mvf);
    assertEquals(mvf.getRawAttributeType(), type);
    assertEquals(mvf.getRawAssertionValue(), ByteString.valueOf(value));
    assertEquals(mvf.getMatchType(), MatchedValuesFilter.EQUALITY_MATCH_TYPE);
    checkEncodeDecode(mvf);

    try
    {
      MatchedValuesFilter.createEqualityFilter((String) null, ByteString.valueOf(value));
      fail("Expected NullPointerException");
    }
    catch (NullPointerException e)
    {
      // excepted behavior
    }


    AttributeType attType = DirectoryServer.getAttributeType(type);
    ByteString attVal = null;
    if (attType != null)
    {
      attVal = ByteString.valueOf(value);
    }

    try
    {
      MatchedValuesFilter.createEqualityFilter((AttributeType) null, null);
      fail("Expected NullPointerException");
    }
    catch (NullPointerException e)
    {
      // excepted behavior
    }

    try
    {
      MatchedValuesFilter.createEqualityFilter(attType, null);
      fail("Expected NullPointerException");
    }
    catch (NullPointerException e)
    {
      // excepted behavior
    }

    // Check type, value
    // As we provide dummy type and value string, attType and attVal
    // could
    // be null.
    if ((attType != null) && (attVal != null))
    {
      mvf = MatchedValuesFilter.createEqualityFilter(attType, attVal);
      assertNotNull(mvf);
      assertEquals(mvf.getAttributeType(), attType);
      assertEquals(mvf.getAssertionValue(), attVal);
      assertEquals(mvf.getMatchType(), MatchedValuesFilter.EQUALITY_MATCH_TYPE);
      checkEncodeDecode(mvf);
    }

    // Check null, value
    try
    {
      MatchedValuesFilter.createEqualityFilter((AttributeType) null, attVal);
      fail("Expected NullPointerException");
    }
    catch (NullPointerException e)
    {
      // excepted behavior
    }
  }

  @DataProvider(name = "substringsFilterData")
  public Object[][] createSubstringsFilterData()
  {
    ArrayList<String> l = new ArrayList<>(3) ;
    l.add("subAny") ;
    l.add("o") ;
    l.add("fakesubAny");

    return new Object[][]
    {
    { "description", "subInitial" ,l, "subFinal" },
    { "objectclass", "t", l,"p" },
    { "fakeobjecttype", "fakesubInitial" , l,"fakesubFinal"}, };
  }


  /**
   * Test createEqualityFilter.
   */
  @Test(dataProvider = "substringsFilterData")
  public void checkCreateSubstringsFilter(String type, String subInitial,
      List<String> subAny, String subFinal) throws Exception
  {

    // input parameter
    String             rawAttTypeTest = type;
    AttributeType         attTypeTest = DirectoryServer.getAttributeType(type);
    ByteString            subInitialTest = ByteString.valueOf(subInitial);
    List<ByteString> subAnyTest = new ArrayList<>(subAny.size());
    for (String s : subAny)
    {
      subAnyTest.add(ByteString.valueOf(s));
    }
    ByteString subFinalTest = ByteString.valueOf(subFinal);

    // test parameter
    AttributeType    attTypeCurrent;
    String           rawAttTypeTestCurrent;
    ByteString       subInitialTestCurrent;
    List<ByteString> subAnyTestCurrent;
    ByteString       subFinalTestCurrent;

    for (int i = 0; i <= 15; i++)
    {
      attTypeCurrent        = null;
      rawAttTypeTestCurrent = null;
      subInitialTestCurrent = null;
      subAnyTestCurrent     = null;
      subFinalTestCurrent   = null;
      if ((i & 0x8) != 0) attTypeCurrent        = attTypeTest;
      if ((i & 0x8) != 0) rawAttTypeTestCurrent = rawAttTypeTest;
      if ((i & 0x4) != 0) subInitialTestCurrent = subInitialTest;
      if ((i & 0x2) != 0) subAnyTestCurrent     = subAnyTest;
      if ((i & 0x1) != 0) subFinalTestCurrent   = subFinalTest;

      if (attTypeCurrent == null)
      {
        rawAttTypeTestCurrent = null ;
      }
      boolean exceptionExpected = (attTypeCurrent == null);

      try
      {
        MatchedValuesFilter mvf = MatchedValuesFilter.createSubstringsFilter(
            rawAttTypeTestCurrent, subInitialTestCurrent, subAnyTestCurrent,
            subFinalTestCurrent);
        assertFalse(exceptionExpected, "Expected NullPointerException");
        assertNotNull(mvf);
        assertEquals(mvf.getMatchType(), MatchedValuesFilter.SUBSTRINGS_TYPE);
        assertEquals(rawAttTypeTestCurrent, mvf.getRawAttributeType());

        if (subInitialTestCurrent == null)
        {
          assertNull(mvf.getSubInitialElement());
        }
        else
        {
          assertEquals(subInitialTestCurrent, mvf.getSubInitialElement());
        }

        if (subAnyTestCurrent == null)
        {
          assertNull(mvf.getSubAnyElements());
        }
        else
        {
          List<ByteString> ret = mvf.getSubAnyElements();
          assertNotNull(ret);
          assertEquals(subAnyTestCurrent.size(), ret.size());
          for (ByteString r : ret)
          {
            assertTrue(subAnyTestCurrent.contains(r));
          }
          if (subFinalTestCurrent == null)
          {
            assertNull(mvf.getSubFinalElement());
          }
          else
          {
            assertEquals(subFinalTestCurrent, mvf.getSubFinalElement());
          }

          mvf = MatchedValuesFilter.createSubstringsFilter(attTypeCurrent,
              subInitialTestCurrent, subAnyTestCurrent, subFinalTestCurrent);
          assertNotNull(mvf);
          assertEquals(mvf.getMatchType(), MatchedValuesFilter.SUBSTRINGS_TYPE);

          assertEquals(attTypeCurrent, mvf.getAttributeType());
          if (subInitialTestCurrent == null)
          {
            assertNull(mvf.getSubInitialElement());
          }
          else
          {
            assertEquals(subInitialTestCurrent, mvf.getSubInitialElement());
          }

          ret = mvf.getSubAnyElements();
          assertNotNull(ret);
          assertEquals(subAnyTestCurrent.size(), ret.size());
          for (ByteString r : ret)
          {
            assertTrue(subAnyTestCurrent.contains(r));
          }

          if (subFinalTestCurrent == null)
          {
            assertNull(mvf.getSubFinalElement());
          }
          else
          {
            assertEquals(subFinalTestCurrent, mvf.getSubFinalElement());
          }
        }
      }
      catch (Throwable t)
      {
        assertTrue(exceptionExpected, "Exception not excepted: " + t.getMessage());
      }
    }
  }

  /**
   * Test GreaterOrEqualFilter.
   */
  @Test(dataProvider = "equalityFilterData")
  public void checkGreaterOrEqualFilter(String type, String value)
      throws Exception
  {
    try
    {
      MatchedValuesFilter.createGreaterOrEqualFilter((String) null, (ByteString) null);
      fail("Expected NullPointerException");
    }
    catch (NullPointerException e)
    {
      // excepted behavior
    }

    try
    {
      MatchedValuesFilter.createGreaterOrEqualFilter(type, (ByteString) null);
      fail("Expected NullPointerException");
    }
    catch (NullPointerException e)
    {
      // excepted behavior
    }

    // Check type, value
    // As we provide dummy type and value string, attType and attVal could
    // be null.
    if ((type != null) && (value != null))
    {
      MatchedValuesFilter mvf;
      mvf = MatchedValuesFilter.createGreaterOrEqualFilter(type,
          ByteString.valueOf(value));
      assertNotNull(mvf);
      assertEquals(mvf.getRawAttributeType(), type);
      assertEquals(mvf.getRawAssertionValue(), ByteString.valueOf(value));
      assertEquals(mvf.getMatchType(),
          MatchedValuesFilter.GREATER_OR_EQUAL_TYPE);
    }

    try
    {
      MatchedValuesFilter.createGreaterOrEqualFilter((String) null, ByteString.valueOf(value));
      fail("Expected NullPointerException");
    }
    catch (NullPointerException e)
    {
      // excepted behavior
    }

    // ( AttributeType attributeType, ByteString assertionValue
    AttributeType attType = DirectoryServer.getAttributeType(type);
    ByteString attVal = null;
    if (attType != null)
    {
      attVal = ByteString.valueOf(value);
    }

    try
    {
      MatchedValuesFilter.createGreaterOrEqualFilter((AttributeType) null, null);
      fail("Expected NullPointerException");
    }
    catch (NullPointerException e)
    {
      // excepted behavior
    }

    try
    {
      MatchedValuesFilter.createGreaterOrEqualFilter(attType, null);
      fail("Expected NullPointerException");
    }
    catch (NullPointerException e)
    {
      // excepted behavior
    }

    // Check type, value
    if ((attType != null) && (attVal != null))
    {
      MatchedValuesFilter mvf;
      mvf = MatchedValuesFilter.createGreaterOrEqualFilter(attType, attVal);
      assertNotNull(mvf);
      assertEquals(mvf.getAttributeType(), attType);
      assertEquals(mvf.getAssertionValue(), attVal);
      assertEquals(mvf.getMatchType(),
          MatchedValuesFilter.GREATER_OR_EQUAL_TYPE);
    }

    try
    {
      MatchedValuesFilter.createGreaterOrEqualFilter((AttributeType) null, attVal);
      fail("Expected NullPointerException");
    }
    catch (NullPointerException e)
    {
      // excepted behavior
    }
  }

  /**
   * Test LessOrEqualFilter.
   */
  @Test(dataProvider = "equalityFilterData")
  public void checkLessOrEqualFilter(String type, String value)
      throws Exception
  {
    try
    {
      MatchedValuesFilter.createLessOrEqualFilter((String) null, (ByteString) null);
      fail("Expected NullPointerException");
    }
    catch (NullPointerException e)
    {
      // excepted behavior
    }

    try
    {
      MatchedValuesFilter.createLessOrEqualFilter(type, (ByteString) null);
    }
    catch (NullPointerException e)
    {
      // excepted behavior
    }

    // Check type, value
    MatchedValuesFilter mvf;
    mvf = MatchedValuesFilter.createLessOrEqualFilter(type,
        ByteString.valueOf(value));
    assertNotNull(mvf);
    assertEquals(mvf.getRawAttributeType(), type);
    assertEquals(mvf.getRawAssertionValue(), ByteString.valueOf(value));
    assertEquals(mvf.getMatchType(), MatchedValuesFilter.LESS_OR_EQUAL_TYPE);

    try
    {
      mvf = MatchedValuesFilter.createLessOrEqualFilter((String) null,
          ByteString.valueOf(value));
    }
    catch (NullPointerException e)
    {
      // excepted behavior
    }


    AttributeType attType = DirectoryServer.getAttributeType(type);
    ByteString attVal = null ;
    if (attType != null)
    {
      attVal = ByteString.valueOf(value);
    }

    try
    {
      MatchedValuesFilter.createLessOrEqualFilter((AttributeType) null, null);
      fail("Expected NullPointerException");
    }
    catch (NullPointerException e)
    {
      // excepted behavior
    }

    try
    {
      MatchedValuesFilter.createLessOrEqualFilter(attType, null);
      fail("Expected NullPointerException");
    }
    catch (NullPointerException e)
    {
      // excepted behavior
    }

    // Check type, value
    // As we provide dummy type and value string, attType and attVal could
    // be null.
    if ((attType != null) && (attVal != null))
    {
      mvf = MatchedValuesFilter.createLessOrEqualFilter(attType, attVal);
      assertNotNull(mvf);
      assertEquals(mvf.getAttributeType(), attType);
      assertEquals(mvf.getAssertionValue(), attVal);
      assertEquals(mvf.getMatchType(), MatchedValuesFilter.LESS_OR_EQUAL_TYPE);
    }

    try
    {
      MatchedValuesFilter.createLessOrEqualFilter((AttributeType) null, attVal);
      fail("Expected NullPointerException");
    }
    catch (NullPointerException e)
    {
      // excepted behavior
    }
  }

  /**
   * Test PresentFilter.
   */
  @Test(dataProvider = "equalityFilterData")
  public void checkPresentFilter(
      String type, String value) throws Exception
  {
    // ( String rawAttributeType)
    // Check null
    MatchedValuesFilter mvf = null;
    try
    {
      mvf = MatchedValuesFilter.createPresentFilter((String) null);
      fail("Expected NullPointerException");
    }
    catch (NullPointerException e)
    {
      // excepted behavior
    }

    // Check type
    mvf = MatchedValuesFilter.createPresentFilter(type);
    assertNotNull(mvf);
    assertEquals(mvf.getRawAttributeType(), type);
    assertEquals(mvf.getMatchType(), MatchedValuesFilter.PRESENT_TYPE);

    // ( AttributeType attributeType
    AttributeType attType = DirectoryServer.getAttributeType(type);

    try
    {
      MatchedValuesFilter.createPresentFilter((AttributeType) null);
      fail("Expected NullPointerException");
    }
    catch (NullPointerException e)
    {
      // excepted behavior
    }

    // Check type,
    if (attType != null)
    {
      mvf = MatchedValuesFilter.createPresentFilter(attType);
      assertNotNull(mvf);
      assertEquals(mvf.getAttributeType(), attType);
      assertEquals(mvf.getMatchType(), MatchedValuesFilter.PRESENT_TYPE);
    }
  }

  /**
   * Test ApproximateFilter.
   */
  @Test(dataProvider = "equalityFilterData")
  public void checkApproximateFilter(String type, String value)
      throws Exception
  {
    try
    {
      MatchedValuesFilter.createApproximateFilter((String) null, (ByteString) null);
      fail("Expected NullPointerException");
    }
    catch (NullPointerException e)
    {
      // expected behavior
    }

    try
    {
      MatchedValuesFilter.createApproximateFilter(type, (ByteString) null);
      fail("Expected NullPointerException");
    }
    catch (NullPointerException e)
    {
      // expected behavior
    }

    // Check type, value
    MatchedValuesFilter mvf;
    mvf = MatchedValuesFilter.createApproximateFilter(type,
        ByteString.valueOf(value));
    assertNotNull(mvf);
    assertEquals(mvf.getRawAttributeType(), type);
    assertEquals(mvf.getRawAssertionValue(), ByteString.valueOf(value));
    assertEquals(mvf.getMatchType(), MatchedValuesFilter.APPROXIMATE_MATCH_TYPE);

    try
    {
      MatchedValuesFilter.createApproximateFilter((String) null, ByteString.valueOf(value));
      fail("Expected NullPointerException");
    }
    catch (NullPointerException e)
    {
      // expected behavior
    }

    // ( AttributeType attributeType, ByteString assertionValue
    AttributeType attType = DirectoryServer.getAttributeType(type);
    ByteString attVal = null ;
    if (attType != null)
    {
      attVal = ByteString.valueOf(value);
    }

    try
    {
      MatchedValuesFilter.createApproximateFilter((AttributeType) null, null);
      fail("Expected NullPointerException");
    }
    catch (NullPointerException e)
    {
      // expected behavior
    }

    try
    {
      MatchedValuesFilter.createApproximateFilter(attType, null);
      fail("Expected NullPointerException");
    }
    catch (NullPointerException e)
    {
      // expected behavior
    }

    // Check type, value
    // As we provide dummy type and value string, attType and attVal could
    // be null.
    if ((attType != null) && (attVal != null))
    {
      mvf = MatchedValuesFilter.createApproximateFilter(attType, attVal);
      assertNotNull(mvf);
      assertEquals(mvf.getAttributeType(), attType);
      assertEquals(mvf.getAssertionValue(), attVal);
      assertEquals(mvf.getMatchType(),
          MatchedValuesFilter.APPROXIMATE_MATCH_TYPE);
    }

    try
    {
      MatchedValuesFilter.createApproximateFilter((AttributeType) null, attVal);
      fail("Expected NullPointerException");
    }
    catch (NullPointerException e)
    {
      // expected behavior
    }
  }

  @DataProvider(name = "extensibleMatchFilterData")
  public Object[][] createExtensibleMatchFilterData() throws Exception
  {
    MatchingRuleFactory<?> factory = new BooleanEqualityMatchingRuleFactory();
    factory.initializeMatchingRule(null);
    MatchingRule booleanEquality = factory.getMatchingRules().iterator().next();
    factory = new IntegerEqualityMatchingRuleFactory();
    factory.initializeMatchingRule(null);
    MatchingRule integerEquality = factory.getMatchingRules().iterator().next();
    factory = new DistinguishedNameEqualityMatchingRuleFactory();
    factory.initializeMatchingRule(null);
    MatchingRule distinguishedEquality = factory.getMatchingRules().iterator().next();

    return new Object[][]
    {
    { "description", booleanEquality, "description" },
    { "objectclass", integerEquality ,"top" },
    { "fakeobjecttype", distinguishedEquality, "fakevalue" }, };
  }

  /**
   * Test ExtensibleMatchFilter.
   */
  @Test(dataProvider = "extensibleMatchFilterData")
  public void checkExtensibleMatchFilter(
      String type, MatchingRule matchingRule, String value)
      throws Exception
  {

    // input value
    String          rawAttTypeTest = type ;
    AttributeType      attTypeTest = DirectoryServer.getAttributeType(type) ;
    String             matchingRuleIdTest = matchingRule.getOID() ;
    ByteString    attValueTest = (attTypeTest == null) ? null : ByteString.valueOf(value);
    // parameter used for the test.
    String          rawAttTypeTestCurrent;
    AttributeType      attTypeTestCurrent ;
    String          rawMatchingRuleidTestCurrent ;
    MatchingRule        matchingRuleidTestCurrent ;
    ByteString     attValueTestCurrent;


    for (int i= 0 ; i <= 7 ; i++)
    {
      rawAttTypeTestCurrent = null;
      rawMatchingRuleidTestCurrent = null;
      matchingRuleidTestCurrent = null ;
      attTypeTestCurrent = null;
      attValueTestCurrent = null ;

      if ((i & 0x4) != 0) attTypeTestCurrent = attTypeTest;
      if ((i & 0x4) != 0) rawAttTypeTestCurrent = rawAttTypeTest;
      if ((i & 0x2) != 0) rawMatchingRuleidTestCurrent = matchingRuleIdTest;
      if ((i & 0x2) != 0) matchingRuleidTestCurrent = matchingRule ;
      if ((i & 0x1) != 0) attValueTestCurrent = attValueTest;

      boolean exceptionExpected = (attTypeTestCurrent == null)
          || (attValueTestCurrent == null) || (matchingRuleidTestCurrent == null);

      MatchedValuesFilter mvf = null;
      try
      {
        // Create filter with raw value
        mvf = MatchedValuesFilter.createExtensibleMatchFilter(rawAttTypeTestCurrent,
            rawMatchingRuleidTestCurrent, attValueTestCurrent);
        assertFalse(exceptionExpected, "Expected NullPointerException");
        assertNotNull(mvf);
        assertEquals(mvf.getMatchType(), MatchedValuesFilter.EXTENSIBLE_MATCH_TYPE);
        assertEquals(rawMatchingRuleidTestCurrent, mvf.getMatchingRuleID());
        assertEquals(attValueTestCurrent, mvf.getRawAssertionValue());

        mvf = MatchedValuesFilter.createExtensibleMatchFilter(
            attTypeTestCurrent, matchingRuleidTestCurrent, attValueTestCurrent);
        assertNotNull(mvf);
        assertEquals(mvf.getMatchType(), MatchedValuesFilter.EXTENSIBLE_MATCH_TYPE);
        assertEquals(matchingRuleidTestCurrent, mvf.getMatchingRule());
        assertEquals(attValueTestCurrent, mvf.getAssertionValue());
      }
      catch (Throwable t)
      {
        assertTrue(exceptionExpected, "Exception not excepted" + t.getMessage());
      }
    }
  }

  /**
   * Check encode/decode method.
   */
  private void checkEncodeDecode(MatchedValuesFilter mvf) throws Exception
  {
    ByteStringBuilder bsb = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(bsb);
    {
      mvf.encode(writer);
      MatchedValuesFilter newMvf = MatchedValuesFilter.decode(ASN1.getReader(bsb));
      assertEquals(newMvf.toString(), mvf.toString());
    }
  }

  @DataProvider(name = "differentNormalization")
  public Object[][] differentNormalizationData() throws ParseException
  {
    final String BASE64_CERT_VALUE =
      "MIICpTCCAg6gAwIBAgIJALeoA6I3ZC/cMA0GCSqGSIb3DQEBBQUAMFYxCzAJBgNV" +
      "BAYTAlVTMRMwEQYDVQQHEwpDdXBlcnRpb25lMRwwGgYDVQQLExNQcm9kdWN0IERl" +
      "dmVsb3BtZW50MRQwEgYDVQQDEwtCYWJzIEplbnNlbjAeFw0xMjA1MDIxNjM0MzVa" +
      "Fw0xMjEyMjExNjM0MzVaMFYxCzAJBgNVBAYTAlVTMRMwEQYDVQQHEwpDdXBlcnRp" +
      "b25lMRwwGgYDVQQLExNQcm9kdWN0IERldmVsb3BtZW50MRQwEgYDVQQDEwtCYWJz" +
      "IEplbnNlbjCBnzANBgkqhkiG9w0BAQEFAAOBjQAwgYkCgYEApysa0c9qc8FB8gIJ" +
      "8zAb1pbJ4HzC7iRlVGhRJjFORkGhyvU4P5o2wL0iz/uko6rL9/pFhIlIMbwbV8sm" +
      "mKeNUPitwiKOjoFDmtimcZ4bx5UTAYLbbHMpEdwSpMC5iF2UioM7qdiwpAfZBd6Z" +
      "69vqNxuUJ6tP+hxtr/aSgMH2i8ECAwEAAaN7MHkwCQYDVR0TBAIwADAsBglghkgB" +
      "hvhCAQ0EHxYdT3BlblNTTCBHZW5lcmF0ZWQgQ2VydGlmaWNhdGUwHQYDVR0OBBYE" +
      "FLlZD3aKDa8jdhzoByOFMAJDs2osMB8GA1UdIwQYMBaAFLlZD3aKDa8jdhzoByOF" +
      "MAJDs2osMA0GCSqGSIb3DQEBBQUAA4GBAE5vccY8Ydd7by2bbwiDKgQqVyoKrkUg" +
      "6CD0WRmc2pBeYX2z94/PWO5L3Fx+eIZh2wTxScF+FdRWJzLbUaBuClrxuy0Y5ifj" +
      "axuJ8LFNbZtsp1ldW3i84+F5+SYT+xI67ZcoAtwx/VFVI9s5I/Gkmu9f9nxjPpK7" +
      "1AIUXiE3Qcck";
    final String CERT_EXACT_ASSERTION =
      "{ serialNumber 13233831500277100508, issuer rdnSequence:\""+
      "CN=Babs Jensen,OU=Product Development,L=Cupertione,C=US\" }";
    return new Object[][]{
      {"userCertificate", ByteString.wrap(Base64.decode(BASE64_CERT_VALUE)),
        CERT_EXACT_ASSERTION}};
  }

  @Test(dataProvider = "differentNormalization")
  public void testDifferentNormalization(String type, ByteString value,
                                         String assertion)
  {
    AttributeType attrType = DirectoryServer.getAttributeType("usercertificate");
    MatchedValuesFilter mvf = MatchedValuesFilter.createEqualityFilter(type, ByteString.valueOf(assertion));
    assertTrue(mvf.valueMatches(attrType, value));
  }
}
