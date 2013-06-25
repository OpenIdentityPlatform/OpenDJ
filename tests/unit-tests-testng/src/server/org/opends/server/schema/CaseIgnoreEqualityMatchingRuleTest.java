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

import org.opends.server.api.EqualityMatchingRule;
import org.testng.annotations.DataProvider;

/**
 * Test the CaseIgnoreEqualityMatchingRule.
 */
public class CaseIgnoreEqualityMatchingRuleTest extends
    EqualityMatchingRuleTest
{

  /**
   * {@inheritDoc}
   */
  @Override
  @DataProvider(name="equalityMatchingRuleInvalidValues")
  public Object[][] createEqualityMatchingRuleInvalidValues()
  {
    return new Object[][] {};
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @DataProvider(name="equalitymatchingrules")
  public Object[][] createEqualityMatchingRuleTest()
  {
    return new Object[][] {
        {" string ", "string", true},
        {"string ",  "string", true},
        {" string",  "string", true},
        {"    ",     " ",      true},
        {"Z",        "z",      true},
        {"ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890",
         "abcdefghijklmnopqrstuvwxyz1234567890", true},
         {"foo\u0020bar\u0020\u0020","foo bar",true},
         {"test\u00AD\u200D","test",true},
         {"foo\u070Fbar" ,"foobar",true},
          //Case-folding data below.
          {"foo\u0149bar","foo\u02BC\u006Ebar",true},
          {"foo\u017Bbar", "foo\u017Cbar",true},

    };
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected EqualityMatchingRule getRule()
  {
    return new CaseIgnoreEqualityMatchingRule();
  }

}
