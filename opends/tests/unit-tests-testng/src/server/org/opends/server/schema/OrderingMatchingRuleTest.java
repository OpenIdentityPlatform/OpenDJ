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

import static org.testng.Assert.*;

import org.opends.server.api.OrderingMatchingRule;
import org.opends.server.core.DirectoryException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.AcceptRejectWarn;
import org.opends.server.types.ByteString;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test The Ordering matching rules and the Ordering matching rule api.
 */
public abstract class OrderingMatchingRuleTest extends SchemaTestCase
{
  /**
   * Create data for the OrderingMatchingRules test.
   *
   * @return The data for the OrderingMatchingRules test.
   */
  @DataProvider(name="Orderingmatchingrules")
  public abstract Object[][] createOrderingMatchingRuleTestData();


  /**
   * Test the comparison of valid values.
   */
  @Test(dataProvider= "Orderingmatchingrules")
  public void OrderingMatchingRules(String value1,String value2, int result)
         throws Exception
  {
    // Make sure that the specified class can be instantiated as a task.
    OrderingMatchingRule ruleInstance = getRule();

    // we should call initializeMatchingRule but they all seem empty at the
    // moment.
    // ruleInstance.initializeMatchingRule(configEntry);

    ByteString normalizedValue1 =
      ruleInstance.normalizeValue(new ASN1OctetString(value1));
    ByteString normalizedValue2 =
      ruleInstance.normalizeValue(new ASN1OctetString(value2));
    int res = ruleInstance.compareValues(normalizedValue1, normalizedValue2);
    if (result == 0)
    {
      if (res != 0)
      {
        fail(ruleInstance + ".compareValues should return 0 for values " +
            value1 + " and " + value2);
      }
    }
    else if (result > 0)
    {
      if (res <= 0)
      {
        fail(ruleInstance + ".compareValues should return a positive integer "
            + "for values : " + value1 + " and " + value2);
      }
    }
    else
    {
      if (res >= 0)
      {
        fail(ruleInstance + ".compareValues should return a negative integer "
            + "for values : " + value1 + " and " + value2);
      }
    }
  }

  /**
   * Get the Ordering matching Rules that is to be tested.
   *
   * @return The Ordering matching Rules that is to be tested.
   */
  public abstract OrderingMatchingRule getRule();


  /**
   * Create data for the OrderingMatchingRulesInvalidValues test.
   *
   * @return The data for the OrderingMatchingRulesInvalidValues test.
   */
  @DataProvider(name="OrderingMatchingRuleInvalidValues")
  public abstract Object[][] createOrderingMatchingRuleInvalidValues();


  /**
   * Test that invalid values are rejected.
   */
  @Test(dataProvider= "OrderingMatchingRuleInvalidValues")
  public void OrderingMatchingRulesInvalidValues(String value) throws Exception
  {
    // Make sure that the specified class can be instantiated as a task.
    OrderingMatchingRule ruleInstance = getRule();

    // normalize the 2 provided values
    try
    {
      ruleInstance.normalizeValue(new ASN1OctetString(value));
    } catch (DirectoryException e) {
      // that's the expected path : the matching rule has detected that
      // the value is incorrect.
      return;
    }
    // if we get there with false value for  success then the tested
    // matching rule did not raised the Exception.

    fail(ruleInstance + " did not catch that value " + value + " is invalid.");
  }

  /**
   * Test that invalid values are rejected.
   */
  @Test(dataProvider= "OrderingMatchingRuleInvalidValues")
  public void OrderingMatchingRulesInvalidValuesWarn(String value)
         throws Exception
  {
    // Make sure that the specified class can be instantiated as a task.
    OrderingMatchingRule ruleInstance = getRule();

    AcceptRejectWarn accept = DirectoryServer.getSyntaxEnforcementPolicy();
    DirectoryServer.setSyntaxEnforcementPolicy(AcceptRejectWarn.WARN);
    // normalize the 2 provided values
    try
    {
      ruleInstance.normalizeValue(new ASN1OctetString(value));
    } catch (Exception e)
    {
      fail(ruleInstance + " in warn mode should not reject value " + value + e);
      return;
    }
    finally
    {
      DirectoryServer.setSyntaxEnforcementPolicy(accept);
    }
  }

  private void dummy ()
  {
  
       Object a = new Object[][] {
        
  
  
      
  
           
  
          
  
         
      };
  
  }


  private Object dummy_invalid()
  {
    return new Object[][] {
      





        
    };
  }
}
