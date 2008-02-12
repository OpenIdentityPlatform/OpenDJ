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
package org.opends.server.schema;

import org.opends.server.api.EqualityMatchingRule;
import org.testng.annotations.DataProvider;

/**
 * Test the  GeneralizedTimeEqualityMatchingRule.
 */
public class GeneralizedTimeEqualityMatchingRuleTest extends
    EqualityMatchingRuleTest
{

  /**
   * {@inheritDoc}
   */
  @Override
  @DataProvider(name="equalityMatchingRuleInvalidValues")
  public Object[][] createEqualityMatchingRuleInvalidValues()
  {
    return new Object [][] {
        {"2006september061Z"},
        {"2006"},
        {"200609061Z"},
        {"20060906135Z"},
        {"200609061350G"},
        {"2006090613mmZ"},
        {"20060906135030.011"},
        {"20060906135030Zx"},
        {"20060906135030.Z"},
        {"20060906135030.aZ"},
        {"20060906135030"},
        {"20060906135030.123"},
        {"20060906135030-2500"},
        {"20060906135030-2070"},
        {"20060931135030Z"},
        {"20060229135030Z"},
        {"20060230135030Z"},
    };
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @DataProvider(name="equalitymatchingrules")
  public Object[][] createEqualityMatchingRuleTest()
  {
    return new Object [][] {
        {"2006090613Z",             "20060906130000.000Z", true},
        {"200609061350Z",           "20060906135000.000Z", true},
        {"200609061351Z",           "20060906135000.000Z", false},
        {"20060906135030Z",         "20060906135030.000Z", true},
        {"20060906135030.3Z",       "20060906135030.300Z", true},
        {"20060906135030.30Z",      "20060906135030.300Z", true},
        {"20060906135030Z",         "20060906135030.000Z", true},
        {"20060906135030.0Z",       "20060906135030.000Z", true},
        {"20060906135030.0118Z",    "20060906135030.012Z", true},
        {"20060906135030+01",       "20060906125030.000Z", true},
        {"20060906135030+0101",     "20060906124930.000Z", true},
        {"20070417055812.318-0500", "20070417105812.318Z", true},
        {"2007041705.5Z",           "20070417053000.000Z", true},
        {"200704170558.5Z",         "20070417055830.000Z", true},
    };
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected EqualityMatchingRule getRule()
  {
    return new GeneralizedTimeEqualityMatchingRule();
  }

}
