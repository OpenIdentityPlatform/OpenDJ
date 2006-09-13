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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.schema;

import org.opends.server.TestCaseUtils;
import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.core.DirectoryException;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.ByteString;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Test The equality matching rules and the equality matching rule api.
 */
public class EqualityMatchingRuleTest extends SchemaTestCase
{
  @DataProvider(name="equalitymatchingrules")
  public Object[][] createEqualityMatchingRuleTest()
  {
    return new Object[][] {
        {"BooleanEqualityMatchingRule", "TRUE", "true", true},
        {"BooleanEqualityMatchingRule", "YES", "true", true},
        {"BooleanEqualityMatchingRule", "ON", "true", true},
        {"BooleanEqualityMatchingRule", "1", "true", true},
        {"BooleanEqualityMatchingRule", "FALSE", "false", true},
        {"BooleanEqualityMatchingRule", "NO", "false", true},
        {"BooleanEqualityMatchingRule", "OFF", "false", true},
        {"BooleanEqualityMatchingRule", "0", "false", true},
        {"BooleanEqualityMatchingRule", "TRUE", "false", false},

        {"CaseIgnoreEqualityMatchingRule", " string ", "string", true},
        {"CaseIgnoreEqualityMatchingRule", "string ", "string", true},
        {"CaseIgnoreEqualityMatchingRule", " string", "string", true},
        {"CaseIgnoreEqualityMatchingRule", "    ", " ", true},
        {"CaseIgnoreEqualityMatchingRule", "Z", "z", true},
        {"CaseIgnoreEqualityMatchingRule",
                            "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890",
                            "abcdefghijklmnopqrstuvwxyz1234567890", true},

        {"IntegerEqualityMatchingRule", "1234567890", "1234567890", true},
        {"IntegerEqualityMatchingRule", "-1", "-1", true},
        {"IntegerEqualityMatchingRule", "-9876543210", "-9876543210", true},
        {"IntegerEqualityMatchingRule", "1", "-1", false},

        {"GeneralizedTimeEqualityMatchingRule","2006090613Z",
                                               "20060906130000.000Z", true},
        {"GeneralizedTimeEqualityMatchingRule","200609061350Z",
                                               "20060906135000.000Z", true},
        {"GeneralizedTimeEqualityMatchingRule","200609061351Z",
                                               "20060906135000.000Z", false},
        {"GeneralizedTimeEqualityMatchingRule","20060906135030Z",
                                               "20060906135030.000Z", true},
        {"GeneralizedTimeEqualityMatchingRule","20060906135030.3Z",
                                               "20060906135030.300Z", true},
        {"GeneralizedTimeEqualityMatchingRule","20060906135030.30Z",
                                               "20060906135030.300Z", true},
        {"GeneralizedTimeEqualityMatchingRule","20060906135030.Z",
                                               "20060906135030.000Z", true},
        {"GeneralizedTimeEqualityMatchingRule","20060906135030.0118Z",
                                               "20060906135030.012Z", true},
        {"GeneralizedTimeEqualityMatchingRule","20060906135030+01",
                                               "20060906125030.000Z", true},
        {"GeneralizedTimeEqualityMatchingRule","20060906135030+0101",
                                               "20060906124930.000Z", true},
                                               
        {"UUIDEqualityMatchingRule",
                 "12345678-9ABC-DEF0-1234-1234567890ab",
                 "12345678-9abc-def0-1234-1234567890ab", true},
        {"UUIDEqualityMatchingRule",
                  "12345678-9abc-def0-1234-1234567890ab",
                  "12345678-9abc-def0-1234-1234567890ab", true},
        {"UUIDEqualityMatchingRule",
                  "02345678-9abc-def0-1234-1234567890ab",
                  "12345678-9abc-def0-1234-1234567890ab", false},
                  
        {"BitStringEqualityMatchingRule", "\'0\'B", "\'0\'B", true},
        {"BitStringEqualityMatchingRule", "\'1\'B", "\'1\'B", true},
        {"BitStringEqualityMatchingRule", "\'0\'B", "\'1\'B", false},
        
       
    };

  }

  /**
   * Test the normalization and the comparison of valid values.
   */
  @Test(dataProvider= "equalitymatchingrules")
  public void EqualityMatchingRules(String ruleClassName, String value1,
                             String value2, Boolean result) throws Exception
  {
    // load the mathing rule code
    Class rule = Class.forName("org.opends.server.schema."+ruleClassName);

    // Make sure that the specified class can be instantiated as a task.
    EqualityMatchingRule ruleInstance =
      (EqualityMatchingRule) rule.newInstance();

    // we should call initializeMatchingRule but they all seem empty at the
    // moment.
    // ruleInstance.initializeMatchingRule(configEntry);

    // normalize the 2 provided values and check that they are equals
    ByteString normalizedValue1 =
      ruleInstance.normalizeValue(new ASN1OctetString(value1));
    ByteString normalizedValue2 =
      ruleInstance.normalizeValue(new ASN1OctetString(value2));

    Boolean liveResult = ruleInstance.areEqual(normalizedValue1,
        normalizedValue2);
    assertEquals(result, liveResult);
  }

  @DataProvider(name="equalityMathchingRuleInvalidValues")
  public Object[][] createEqualityMathchingRuleInvalidValues()
  {
    return new Object[][] {
        {"IntegerEqualityMatchingRule", "01"},
        {"IntegerEqualityMatchingRule", "00"},
        {"IntegerEqualityMatchingRule", "-01"},
        {"IntegerEqualityMatchingRule", "1-2"},
        {"IntegerEqualityMatchingRule", "b2"},
        {"IntegerEqualityMatchingRule", "-"},
        {"IntegerEqualityMatchingRule", ""},

        {"GeneralizedTimeEqualityMatchingRule","2006september061Z"},
        {"GeneralizedTimeEqualityMatchingRule","2006"},
        {"GeneralizedTimeEqualityMatchingRule","200609061Z"},
        {"GeneralizedTimeEqualityMatchingRule","20060906135Z"},
        {"GeneralizedTimeEqualityMatchingRule","200609061350G"},
        {"GeneralizedTimeEqualityMatchingRule","2006090613mmZ"},
        {"GeneralizedTimeEqualityMatchingRule","20060906135030.011"},
        {"GeneralizedTimeEqualityMatchingRule","20060906135030Zx"},
        
        {"UUIDEqualityMatchingRule", "G2345678-9abc-def0-1234-1234567890ab"},
        {"UUIDEqualityMatchingRule", "g2345678-9abc-def0-1234-1234567890ab"},
        {"UUIDEqualityMatchingRule", "12345678/9abc/def0/1234/1234567890ab"},
        {"UUIDEqualityMatchingRule", "12345678-9abc-def0-1234-1234567890a"},
        
        {"BitStringEqualityMatchingRule", "\'a\'B"},
        {"BitStringEqualityMatchingRule", "0"},
        {"BitStringEqualityMatchingRule", "010101"},
        {"BitStringEqualityMatchingRule", "\'10101"},
        {"BitStringEqualityMatchingRule", "\'1010\'A"},
        
    };
  }

  /**
   * Test that invalid values are rejected.
   */
  @Test(dataProvider= "equalityMathchingRuleInvalidValues")
  public void EqualityMatchingRulesInvalidValues(String ruleClassName,
      String value) throws Exception
    {

    // load the matching rule code
    Class rule = Class.forName("org.opends.server.schema."+ruleClassName);

    // Make sure that the specified class can be instantiated as a task.
    EqualityMatchingRule ruleInstance =
      (EqualityMatchingRule) rule.newInstance();

    // we should call initializeMatchingRule but they all seem empty at the
    // moment.
    // ruleInstance.initializeMatchingRule(configEntry);

    // normalize the 2 provided values
    boolean success = false;
    try
    {
      ruleInstance.normalizeValue(new ASN1OctetString(value));
    } catch (DirectoryException e) {
      success = true;
    }
    // if we get there with false value for  success then the tested
    // matching rule did not raised the Exception.

    assertTrue(success);
  }
  /**
   * Set up the environment for performing the tests in this suite.
   *
   * @throws Exception
   *           If the environment could not be set up.
   */
  @BeforeClass
  public void setUp() throws Exception {
    // This test suite depends on having the schema available.
    TestCaseUtils.startServer();
  }

}
