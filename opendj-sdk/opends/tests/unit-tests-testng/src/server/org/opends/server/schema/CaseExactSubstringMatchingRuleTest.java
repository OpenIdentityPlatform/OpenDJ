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

import org.opends.server.api.SubstringMatchingRule;
import org.testng.annotations.DataProvider;

/**
 * Test the CaseExactSubstringMatchingRule class.
 */
public class CaseExactSubstringMatchingRuleTest extends
    SubstringMatchingRuleTest
{

  /**
   * {@inheritDoc}
   */
  @Override
  @DataProvider(name="substringMiddleMatchData")
  public Object[][] createSubstringMiddleMatchData()
  {
    return new Object[][] {
        {"this is a value", new String[] {"this"}, true },
        {"this is a value", new String[] {"is"}, true },
        {"this is a value", new String[] {"a"}, true },
        {"this is a value", new String[] {"value"}, true },
        {"this is a value", new String[] {" "}, true },
        {"this is a value", new String[] {"this", "is", "a", "value"}, true },
         // The matching rule requires ordered non overlapping substrings.
         // Issue #730 was not valid.
        {"this is a value", new String[] {"value", "this"}, false },
        {"this is a value", new String[] {"this", "this is"}, false },
        {"this is a value", new String[] {"his is", "a val",}, true },
        {"this is a value", new String[] {"not",}, false },
        {"this is a value", new String[] {"THIS",}, false },
        {"this is a value", new String[] {"this", "not"}, false },
        {"this is a value", new String[] {"    "}, true },
    };
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected SubstringMatchingRule getRule()
  {
    return new CaseExactSubstringMatchingRule();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @DataProvider(name="substringInitialMatchData")
  public Object[][] createSubstringInitialMatchData()
  {
    return new Object[][] {
        {"this is a value",  "this",  true },
        {"this is a value",  "th",    true },
        {"this is a value",  "t",     true },
        {"this is a value",  "is",    false },
        {"this is a value",  "a",     false },
        {"this is a value",  "value", false },
        {"this is a value",  " ",     false },
        {"this is a value",  "NOT",   false },
        {"this is a value",  "THIS",  false },
    };
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @DataProvider(name="substringFinalMatchData")
  public Object[][] createSubstringFinalMatchData()
  {
    return new Object[][] {
        {"this is a value", "value", true },
        {"this is a value", "alue", true },
        {"this is a value", "ue", true },
        {"this is a value", "e", true },
        {"this is a value", "valu", false },
        {"this is a value",  "this", false },
        {"this is a value", " ", false },
        {"this is a value", "VALUE", false },
        {"this is a VALUE", "value", false },
        {"end with space    ", " ", false },
        {"end with space    ", "space", true },
    };
  }

}
