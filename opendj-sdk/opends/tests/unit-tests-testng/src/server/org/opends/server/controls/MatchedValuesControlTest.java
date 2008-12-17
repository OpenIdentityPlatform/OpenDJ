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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.controls;

import java.util.ArrayList;
import java.util.List;

import org.opends.server.api.MatchingRuleFactory;
import org.opends.server.api.MatchingRule;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.schema.BooleanEqualityMatchingRuleFactory;
import org.opends.server.schema.DistinguishedNameEqualityMatchingRuleFactory;
import org.opends.server.schema.IntegerEqualityMatchingRuleFactory;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.ByteString;
import org.opends.server.types.LDAPException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Test MatchedValuesControl
 */
public class MatchedValuesControlTest
    extends ControlsTestCase
{

  /**
   * Check "enum" values
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
   * Test createEqualityFilter
   */
  @Test(dataProvider = "equalityFilterData")
  public void checkCreateEqualityFilter(String type, String value)
      throws Exception
  {
    MatchedValuesFilter mvf;
    //
    // ( String rawAttributeType, ASN1OctetStringrawAssertionValue)
    //
    // Check null, null
    try
    {
      mvf = MatchedValuesFilter.createEqualityFilter((String) null,
          (ASN1OctetString) null);
      assertTrue(false, "Expected NullPointerException");
    }
    catch (NullPointerException e)
    {
      // excepted behavior
    }
    catch (AssertionError e)
    {
      // excepted behavior
    }

    // Check type, null
    try
    {
      mvf = MatchedValuesFilter.createEqualityFilter(type,
          (ASN1OctetString) null);
      assertTrue(false, "Expected NullPointerException");
    }
    catch (NullPointerException e)
    {
      // excepted behavior
    }
    catch (AssertionError e)
    {
      // excepted behavior
    }

    mvf = MatchedValuesFilter.createEqualityFilter(type, new ASN1OctetString(
        value));
    assertNotNull(mvf);
    assertEquals(mvf.getRawAttributeType(), type);
    assertEquals(mvf.getRawAssertionValue(), new ASN1OctetString(value));
    assertEquals(mvf.getMatchType(), MatchedValuesFilter.EQUALITY_MATCH_TYPE);
    checkEncodeDecode(mvf);

    // Check null, value
    try
    {
      mvf = MatchedValuesFilter.createEqualityFilter((String) null,
          new ASN1OctetString(value));
      assertTrue(false, "Expected NullPointerException");
    }
    catch (NullPointerException e)
    {
      // excepted behavior
    }
    catch (AssertionError e)
    {
      // excepted behavior
    }

    //
    // ( AttributeType attributeType, AttributeValue assertionValue
    //
    AttributeType attType = DirectoryServer.getAttributeType(type);
    AttributeValue attVal = null;
    if (attType != null)
    {
      attVal = new AttributeValue(attType, value);
    }

    // Check null, null
    try
    {
      mvf = MatchedValuesFilter.createEqualityFilter((AttributeType) null,
          (AttributeValue) null);
      assertTrue(false, "Expected NullPointerException");
    }
    catch (NullPointerException e)
    {
      // excepted behavior
    }
    catch (AssertionError e)
    {
      // excepted behavior
    }

    // Check type, null
    try
    {
      mvf = MatchedValuesFilter.createEqualityFilter(attType,
          (AttributeValue) null);
      assertTrue(false, "Expected NullPointerException");
    }
    catch (NullPointerException e)
    {
      // excepted behavior
    }
    catch (AssertionError e)
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
      mvf = MatchedValuesFilter.createEqualityFilter((AttributeType) null,
          attVal);
      assertTrue(false, "Expected NullPointerException");
    }
    catch (NullPointerException e)
    {
      // excepted behavior
    }
    catch (AssertionError e)
    {
      // excepted behavior
    }
  }

  @DataProvider(name = "substringsFilterData")
  public Object[][] createSubstringsFilterData()
  {
    ArrayList<String> l = new ArrayList<String>(3) ;
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
   * Test createEqualityFilter
   */
  @Test(dataProvider = "substringsFilterData")
  public void checkCreateSubstringsFilter(String type, String subInitial,
      List<String> subAny, String subFinal) throws Exception
  {

    // input parameter
    String             rawAttTypeTest = type;
    AttributeType         attTypeTest = DirectoryServer.getAttributeType(type);
    ASN1OctetString       subInitialTest = new ASN1OctetString(subInitial);
    List<ByteString> subAnyTest =
      new ArrayList<ByteString>(subAny.size());
    for (String s : subAny)
    {
      subAnyTest.add(new ASN1OctetString(s));
    }
    ByteString subFinalTest = new ASN1OctetString(subFinal);

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
        if (exceptionExpected)
        {
          assertTrue(false, "Expected NullPointerException");
        }
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

          if (subAnyTestCurrent == null)
          {
            assertNull(mvf.getSubAnyElements());
          }
          else
          {
            ret = mvf.getSubAnyElements();
            assertNotNull(ret);
            assertEquals(subAnyTestCurrent.size(), ret.size());
            for (ByteString r : ret)
            {
              assertTrue(subAnyTestCurrent.contains(r));
            }
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
        if ( ! exceptionExpected)
        {
          assertTrue(false, "Exception not excepted: " + t.getMessage());
        }
      }
    }
  }

  /**
   * Test GreaterOrEqualFilter
   */
  @Test(dataProvider = "equalityFilterData")
  public void checkGreaterOrEqualFilter(String type, String value)
      throws Exception
  {
    MatchedValuesFilter mvf;
    //
    // ( String rawAttributeType, ASN1OctetStringrawAssertionValue)
    //
    // Check null, null
    try
    {

      mvf = MatchedValuesFilter.createGreaterOrEqualFilter((String) null,
          (ASN1OctetString) null);
      assertTrue(false, "Expected NullPointerException");
    }
    catch (NullPointerException e)
    {
      // excepted behavior
    }
    catch (AssertionError e)
    {
      // excepted behavior
    }

    // Check type, null
    try
    {
      mvf = MatchedValuesFilter.createGreaterOrEqualFilter(type,
          (ASN1OctetString) null);
      assertTrue(false, "Expected NullPointerException");
    }
    catch (NullPointerException e)
    {
      // excepted behavior
    }
    catch (AssertionError e)
    {
      // excepted behavior
    }

    // Check type, value
    // As we provide dummy type and value string, attType and attVal
    // could
    // be null.
    if ((type != null) && (value != null))
    {
      mvf = MatchedValuesFilter.createGreaterOrEqualFilter(type,
          new ASN1OctetString(value));
      assertNotNull(mvf);
      assertEquals(mvf.getRawAttributeType(), type);
      assertEquals(mvf.getRawAssertionValue(), new ASN1OctetString(value));
      assertEquals(mvf.getMatchType(),
          MatchedValuesFilter.GREATER_OR_EQUAL_TYPE);
    }

    // Check null, value
    try
    {
      mvf = MatchedValuesFilter.createGreaterOrEqualFilter((String) null,
          new ASN1OctetString(value));
      assertTrue(false, "Expected NullPointerException");
    }
    catch (NullPointerException e)
    {
      // excepted behavior
    }
    catch (AssertionError e)
    {
      // excepted behavior
    }

    //
    // ( AttributeType attributeType, AttributeValue assertionValue
    //
    AttributeType attType = DirectoryServer.getAttributeType(type);
    AttributeValue attVal = null;
    if (attType != null)
    {
      attVal = new AttributeValue(attType, value);
    }

    // Check null, null
    try
    {
      mvf = MatchedValuesFilter.createGreaterOrEqualFilter(
          (AttributeType) null, (AttributeValue) null);
      assertTrue(false, "Expected NullPointerException");
    }
    catch (NullPointerException e)
    {
      // excepted behavior
    }
    catch (AssertionError e)
    {
      // excepted behavior
    }

    // Check type, null
    try
    {
      mvf = MatchedValuesFilter.createGreaterOrEqualFilter(attType,
          (AttributeValue) null);
      assertTrue(false, "Expected NullPointerException");
    }
    catch (NullPointerException e)
    {
      // excepted behavior
    }
    catch (AssertionError e)
    {
      // excepted behavior
    }

    // Check type, value
    if ((attType != null) && (attVal != null))
    {
      mvf = MatchedValuesFilter.createGreaterOrEqualFilter(attType, attVal);
      assertNotNull(mvf);
      assertEquals(mvf.getAttributeType(), attType);
      assertEquals(mvf.getAssertionValue(), attVal);
      assertEquals(mvf.getMatchType(),
          MatchedValuesFilter.GREATER_OR_EQUAL_TYPE);
    }

    // Check null, value
    try
    {
      mvf = MatchedValuesFilter.createGreaterOrEqualFilter(
          (AttributeType) null, attVal);
      assertTrue(false, "Expected NullPointerException");
    }
    catch (NullPointerException e)
    {
      // excepted behavior
    }
    catch (AssertionError e)
    {
      // excepted behavior
    }
  }

  /**
   * Test LessOrEqualFilter
   */
  @Test(dataProvider = "equalityFilterData")
  public void checkLessOrEqualFilter(String type, String value)
      throws Exception
  {
    //
    // ( String rawAttributeType, ASN1OctetStringrawAssertionValue)
    //
    // Check null, null
    MatchedValuesFilter mvf;
    try
    {
      mvf = MatchedValuesFilter.createLessOrEqualFilter((String) null,
          (ASN1OctetString) null);
    }
    catch (NullPointerException e)
    {
      // excepted behavior
    }
    catch (AssertionError e)
    {
      // excepted behavior
    }

    // Check type, null
    try
    {
      mvf = MatchedValuesFilter.createLessOrEqualFilter(type,
          (ASN1OctetString) null);
    }
    catch (NullPointerException e)
    {
      // excepted behavior
    }
    catch (AssertionError e)
    {
      // excepted behavior
    }

    // Check type, value
    mvf = MatchedValuesFilter.createLessOrEqualFilter(type,
        new ASN1OctetString(value));
    assertNotNull(mvf);
    assertEquals(mvf.getRawAttributeType(), type);
    assertEquals(mvf.getRawAssertionValue(), new ASN1OctetString(value));
    assertEquals(mvf.getMatchType(), MatchedValuesFilter.LESS_OR_EQUAL_TYPE);

    // Check null, value
    try
    {
      mvf = MatchedValuesFilter.createLessOrEqualFilter((String) null,
          new ASN1OctetString(value));
    }
    catch (NullPointerException e)
    {
      // excepted behavior
    }
    catch (AssertionError e)
    {
      // excepted behavior
    }
    ;

    //
    // ( AttributeType attributeType, AttributeValue assertionValue
    //
    AttributeType attType = DirectoryServer.getAttributeType(type);
    AttributeValue attVal = null ;
    if (attType != null)
    {
      new AttributeValue(attType, value);
    }

    // Check null, null
    try
    {
      mvf = MatchedValuesFilter.createLessOrEqualFilter((AttributeType) null,
          (AttributeValue) null);
    }
    catch (NullPointerException e)
    {
      // excepted behavior
    }
    catch (AssertionError e)
    {
      // excepted behavior
    }

    // Check type, null
    try
    {
      mvf = MatchedValuesFilter.createLessOrEqualFilter(attType,
          (AttributeValue) null);
    }
    catch (NullPointerException e)
    {
      // excepted behavior
    }
    catch (AssertionError e)
    {
      // excepted behavior
    }

    // Check type, value
    // As we provide dummy type and value string, attType and attVal
    // could
    // be null.
    if ((attType != null) && (attVal != null))
    {
      mvf = MatchedValuesFilter.createLessOrEqualFilter(attType, attVal);
      assertNotNull(mvf);
      assertEquals(mvf.getAttributeType(), attType);
      assertEquals(mvf.getAssertionValue(), attVal);
      assertEquals(mvf.getMatchType(), MatchedValuesFilter.LESS_OR_EQUAL_TYPE);
    }

    // Check null, value
    try
    {
      mvf = MatchedValuesFilter.createLessOrEqualFilter((AttributeType) null,
          attVal);
    }
    catch (NullPointerException e)
    {
      // excepted behavior
    }
    catch (AssertionError e)
    {
      // excepted behavior
    }
  }

  /**
   * Test PresentFilter
   */
  @Test(dataProvider = "equalityFilterData")
  public void checkPresentFilter(
      String type, String value) throws Exception
  {
    //
    // ( String rawAttributeType)
    //
    // Check null
    MatchedValuesFilter mvf = null;
    try
    {
      mvf = MatchedValuesFilter.createPresentFilter((String) null);
      assertTrue(false, "Expected NullPointerException");
    }
    catch (NullPointerException e)
    {
      // excepted behavior
    }
    catch (AssertionError e)
    {
      // excepted behavior
    }

    // Check type
    mvf = MatchedValuesFilter.createPresentFilter(type);
    assertNotNull(mvf);
    assertEquals(mvf.getRawAttributeType(), type);
    assertEquals(mvf.getMatchType(), MatchedValuesFilter.PRESENT_TYPE);

    //
    // ( AttributeType attributeType
    //
    AttributeType attType = DirectoryServer.getAttributeType(type);

    // Check null
    try
    {
      mvf = MatchedValuesFilter.createPresentFilter((AttributeType) null);
      assertTrue(false, "Expected NullPointerException");
    }
    catch (NullPointerException e)
    {
      // excepted behavior
    }
    catch (AssertionError e)
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
   * Test ApproximateFilter
   */
  @Test(dataProvider = "equalityFilterData")
  public void checkApproximateFilter(String type, String value)
      throws Exception
  {
    MatchedValuesFilter mvf;
    //
    // ( String rawAttributeType, ASN1OctetStringrawAssertionValue)
    //
    // Check null, null
    try
    {
      mvf = MatchedValuesFilter.createApproximateFilter((String) null,
          (ASN1OctetString) null);
      assertTrue(false, "Expected NullPointerException");
    }
    catch (NullPointerException e)
    {
      // expected behavior
    }
    catch (AssertionError e)
    {
      // excepted behavior
    }

    // Check type, null
    try
    {
      mvf = MatchedValuesFilter.createApproximateFilter(type,
          (ASN1OctetString) null);
      assertTrue(false, "Expected NullPointerException");
    }
    catch (NullPointerException e)
    {
      // expected behavior
    }
    catch (AssertionError e)
    {
      // excepted behavior
    }

    // Check type, value
    mvf = MatchedValuesFilter.createApproximateFilter(type,
        new ASN1OctetString(value));
    assertNotNull(mvf);
    assertEquals(mvf.getRawAttributeType(), type);
    assertEquals(mvf.getRawAssertionValue(), new ASN1OctetString(value));
    assertEquals(mvf.getMatchType(), MatchedValuesFilter.APPROXIMATE_MATCH_TYPE);

    // Check null, value
    try
    {
      mvf = MatchedValuesFilter.createApproximateFilter((String) null,
          new ASN1OctetString(value));
      assertTrue(false, "Expected NullPointerException");
    }
    catch (NullPointerException e)
    {
      // expected behavior
    }
    catch (AssertionError e)
    {
      // excepted behavior
    }

    //
    // ( AttributeType attributeType, AttributeValue assertionValue
    //
    AttributeType attType = DirectoryServer.getAttributeType(type);
    AttributeValue attVal = null ;
    if (attType != null)
    {
      attVal = new AttributeValue(attType, value);
    }

    // Check null, null
    try
    {
      mvf = MatchedValuesFilter.createApproximateFilter((AttributeType) null,
          (AttributeValue) null);
      assertTrue(false, "Expected NullPointerException");
    }
    catch (NullPointerException e)
    {
      // expected behavior
    }
    catch (AssertionError e)
    {
      // excepted behavior
    }

    // Check type, null
    try
    {
      mvf = MatchedValuesFilter.createApproximateFilter(attType,
          (AttributeValue) null);
      assertTrue(false, "Expected NullPointerException");
    }
    catch (NullPointerException e)
    {
      // expected behavior
    }
    catch (AssertionError e)
    {
      // excepted behavior
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

    // Check null, value
    try
    {
      mvf = MatchedValuesFilter.createApproximateFilter((AttributeType) null,
          attVal);
      assertTrue(false, "Expected NullPointerException");
    }
    catch (NullPointerException e)
    {
      // expected behavior
    }
    catch (AssertionError e)
    {
      // excepted behavior
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
   * Test ExtensibleMatchFilter
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
    ASN1OctetString rawAttValueTest = (attTypeTest == null) ? null : new ASN1OctetString(value);
    AttributeValue     attValueTest = (attTypeTest == null) ? null : new AttributeValue(attTypeTest, value);
    //
    // parameter used for the test.
    String          rawAttTypeTestCurrent;
    AttributeType      attTypeTestCurrent ;
    String          rawMatchingRuleidTestCurrent ;
    MatchingRule        matchingRuleidTestCurrent ;
    ASN1OctetString rawAttValueTestCurrent;
    AttributeValue     attValueTestCurrent;


    for (int i= 0 ; i <= 7 ; i++)
    {
      rawAttTypeTestCurrent = null;
      rawMatchingRuleidTestCurrent = null;
      matchingRuleidTestCurrent = null ;
      rawAttValueTestCurrent = null;
      attTypeTestCurrent = null;
      attValueTestCurrent = null ;

      if ((i & 0x4) != 0) attTypeTestCurrent = attTypeTest;
      if ((i & 0x4) != 0) rawAttTypeTestCurrent = rawAttTypeTest;
      if ((i & 0x2) != 0) rawMatchingRuleidTestCurrent = matchingRuleIdTest;
      if ((i & 0x2) != 0) matchingRuleidTestCurrent = matchingRule ;
      if ((i & 0x1) != 0) rawAttValueTestCurrent = rawAttValueTest;
      if ((i & 0x1) != 0) attValueTestCurrent = attValueTest;

      boolean exceptionExpected = (attTypeTestCurrent == null)
          || (attValueTestCurrent == null) || (matchingRuleidTestCurrent == null);

      MatchedValuesFilter mvf = null;
      try
      {
        // Create filter with raw value
        mvf = MatchedValuesFilter.createExtensibleMatchFilter(rawAttTypeTestCurrent,
            rawMatchingRuleidTestCurrent, rawAttValueTestCurrent);
        if ( exceptionExpected)
        {
          assertTrue(false, "Expected NullPointerException");
        }
        else
        {
          assertNotNull(mvf);
          assertEquals(mvf.getMatchType(),
              MatchedValuesFilter.EXTENSIBLE_MATCH_TYPE);
          assertEquals(rawMatchingRuleidTestCurrent, mvf.getMatchingRuleID());
          assertEquals(rawAttValueTestCurrent, mvf.getRawAssertionValue());

          mvf = MatchedValuesFilter.createExtensibleMatchFilter(
              attTypeTestCurrent, matchingRuleidTestCurrent, attValueTestCurrent);
          assertNotNull(mvf);
          assertEquals(mvf.getMatchType(),
              MatchedValuesFilter.EXTENSIBLE_MATCH_TYPE);
          assertEquals(matchingRuleidTestCurrent, mvf.getMatchingRule());
          assertEquals(attValueTestCurrent, mvf.getAssertionValue());
        }
      }
      catch (Throwable t)
      {
        if ( ! exceptionExpected)
        {
          assertTrue(false, "Exception not excepted" + t.getMessage());
        }
      }
    }
  }

  /**
   * Check encode/decode method
   */
  private void checkEncodeDecode(MatchedValuesFilter mvf)
  {
    ASN1Element asn1Elt = mvf.encode() ;
    try
    {
      MatchedValuesFilter newMvf = MatchedValuesFilter.decode(asn1Elt) ;
      assertEquals(newMvf.toString(), mvf.toString());
    }
    catch (LDAPException e)
    {
      assertTrue(false, "Unexpected LDAPException ; msg=" + e.getMessage());
    }

  }
}
