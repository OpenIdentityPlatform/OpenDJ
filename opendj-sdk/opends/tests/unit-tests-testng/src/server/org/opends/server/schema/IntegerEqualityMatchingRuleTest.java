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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.schema;

import org.opends.server.api.EqualityMatchingRule;
import org.testng.annotations.DataProvider;

/**
 * Test the IntegerEqualityMatchingRule.
 */
public class IntegerEqualityMatchingRuleTest extends EqualityMatchingRuleTest
{
  /**
   * {@inheritDoc}
   */
  @Override
  @DataProvider(name="equalityMatchingRuleInvalidValues")
  public Object[][] createEqualityMatchingRuleInvalidValues()
  {
    return new Object[][] {
        {"01"},
        {"00"},
        {"-01"},
        {"1-2"},
        {"b2"},
        {"-"},
        {""},
    };
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @DataProvider(name="equalitymatchingrules")
  public Object[][] createEqualityMatchingRuleTest()
  {
    return new Object[][] {  
        {"1234567890",  "1234567890",   true},
        {"-1",          "-1",           true},
        {"-9876543210", "-9876543210", true},
        {"1",           "-1",          false},
    };
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected EqualityMatchingRule getRule()
  {
    return new IntegerEqualityMatchingRule();
  }

}
