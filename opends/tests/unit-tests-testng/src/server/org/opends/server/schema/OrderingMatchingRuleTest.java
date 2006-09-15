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

import static org.testng.Assert.assertTrue;

import org.opends.server.api.OrderingMatchingRule;
import org.opends.server.core.DirectoryException;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.ByteString;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test The Ordering matching rules and the Ordering matching rule api.
 */
public class OrderingMatchingRuleTest extends SchemaTestCase
{
  @DataProvider(name="Orderingmatchingrules")
  public Object[][] createOrderingMatchingRuleTest()
  {
    return new Object[][] {
        {"GeneralizedTimeOrderingMatchingRule", "20060912180130Z",
          "20060912180130Z", 0},
        {"GeneralizedTimeOrderingMatchingRule", "20060912180130Z",
          "20060912180129Z", 1},
        {"GeneralizedTimeOrderingMatchingRule", "20060912180129Z",
          "20060912180130Z", -1},
         
        // following test is currently disabled because it does not work  
        // {"NumericStringOrderingMatchingRule", "1", "09", -1},
        // see issue 638
        {"NumericStringOrderingMatchingRule", "1", "999999999999999999999", -1},
        {"NumericStringOrderingMatchingRule", "1", "9", -1},
        {"NumericStringOrderingMatchingRule", "1", " 1 ", 0},
        {"NumericStringOrderingMatchingRule", "0", "1", -1},
        {"NumericStringOrderingMatchingRule", "1", "0", 1},
      
        {"IntegerOrderingMatchingRule", "1", "0", 1},
        {"IntegerOrderingMatchingRule", "1", "1", 0},
        {"IntegerOrderingMatchingRule", "45", "54", -1},
        {"IntegerOrderingMatchingRule", "-63", "63", -1},
        {"IntegerOrderingMatchingRule", "-63", "0", -1},
        {"IntegerOrderingMatchingRule", "63", "0", 1},
        {"IntegerOrderingMatchingRule", "0", "-63", 1},
        {"IntegerOrderingMatchingRule", "987654321987654321987654321",
                                        "987654321987654321987654322", -1},
    };
  }

  /**
   * Test the comparison of valid values.
   */
  @Test(dataProvider= "Orderingmatchingrules")
  public void OrderingMatchingRules(String ruleClassName, String value1,
      String value2, int result) throws Exception
  {
    // load the mathing rule code
    Class rule = Class.forName("org.opends.server.schema."+ruleClassName);

    // Make sure that the specified class can be instantiated as a task.
    OrderingMatchingRule ruleInstance =
      (OrderingMatchingRule) rule.newInstance();

    // we should call initializeMatchingRule but they all seem empty at the
    // moment.
    // ruleInstance.initializeMatchingRule(configEntry);

    ByteString normalizedValue1 =
      ruleInstance.normalizeValue(new ASN1OctetString(value1));
    ByteString normalizedValue2 =
      ruleInstance.normalizeValue(new ASN1OctetString(value2));
    int res = ruleInstance.compareValues(normalizedValue1, normalizedValue2);
    if (result == 0)
      assertTrue(res == 0);
    else if (result > 0)
      assertTrue(res > 0);
    else
      assertTrue(res < 0);
  }

  @DataProvider(name="OrderingMatchingRuleInvalidValues")
  public Object[][] createOrderingMatchingRuleInvalidValues()
  {
    return new Object[][] {
        {"GeneralizedTimeOrderingMatchingRule", "20060912180130"},
        {"GeneralizedTimeOrderingMatchingRule","2006123123595aZ"},
        {"GeneralizedTimeOrderingMatchingRule","200a1231235959Z"},
        {"GeneralizedTimeOrderingMatchingRule","2006j231235959Z"},
        {"GeneralizedTimeOrderingMatchingRule","20061231#35959Z"},
        {"GeneralizedTimeOrderingMatchingRule","2006"},
        
        {"NumericStringOrderingMatchingRule", "jfhslur"},
        {"NumericStringOrderingMatchingRule", "123AB"},
        
        {"IntegerOrderingMatchingRule", " 63 "},
        {"IntegerOrderingMatchingRule", "- 63"},
        {"IntegerOrderingMatchingRule", "+63"},
        {"IntegerOrderingMatchingRule", "AB"},
        {"IntegerOrderingMatchingRule", "0xAB"},
    };
  }

  /**
   * Test that invalid values are rejected.
   */
  @Test(dataProvider= "OrderingMatchingRuleInvalidValues")
  public void OrderingMatchingRulesInvalidValues(String ruleClassName,
      String value) throws Exception
  {

    // load the matching rule code
    Class rule = Class.forName("org.opends.server.schema."+ruleClassName);

    // Make sure that the specified class can be instantiated as a task.
    OrderingMatchingRule ruleInstance =
      (OrderingMatchingRule) rule.newInstance();

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
}
