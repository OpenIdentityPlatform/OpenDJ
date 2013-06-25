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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 */
package org.opends.server.schema;

import org.opends.server.api.OrderingMatchingRule;
import org.testng.annotations.DataProvider;

/**
 * Test the CaseIgnoreOrderingMatchingRule.
 */
public class CaseIgnoreOrderingMatchingRuleTest extends
    OrderingMatchingRuleTest
{

  /**
   * {@inheritDoc}
   */
  @Override
  @DataProvider(name="OrderingMatchingRuleInvalidValues")
  public Object[][] createOrderingMatchingRuleInvalidValues()
  {
    return new Object[][] {
    };
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @DataProvider(name="Orderingmatchingrules")
  public Object[][] createOrderingMatchingRuleTestData()
  {
    return new Object[][] {
        {"12345678", "02345678", 1},
        {"abcdef", "bcdefa", -1},
        {"abcdef", "abcdef", 0},
        {"abcdef", "ABCDEF", 0},
        {"abcdef", "aCcdef", -1},
        {"aCcdef", "abcdef", 1},
        {"foo\u0020bar\u0020\u0020","foo bar",0},
        {"test\u00AD\u200D","test",0},
        {"foo\u070Fbar" ,"foobar",0},
        //Case-folding data below.
        {"foo\u0149bar","foo\u02BC\u006Ebar",0},
        {"foo\u017Bbar", "foo\u017Cbar",0},
        {"foo\u017Bbar", "goo\u017Cbar",-1},
        //issue# 3583
        {"a","\u00f8",-1},
    };
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected OrderingMatchingRule getRule()
  {
    return new CaseIgnoreOrderingMatchingRule();
  }
}
