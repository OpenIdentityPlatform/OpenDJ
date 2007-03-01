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

import org.opends.server.api.SubstringMatchingRule;
import org.testng.annotations.DataProvider;

/**
 * Test the NumericStringSubstringMatchingRule.
 */
public class NumericStringSubstringMatchingRuleTest extends
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
       // disabled becasue of issue 730
       // {"123456789", new String[] {"123", "234", "567", "789"}, true },
       // {"123456789", new String[] {"123", "234"}, true },
       // {"123456789", new String[] {"567", "234"}, true },
        {"123456789", new String[] {"123", "456"}, true },
        {"123456789", new String[] {"123"}, true },
        {"123456789", new String[] {"456"}, true },
        {"123456789", new String[] {"789"}, true },
        {"123456789", new String[] {"123456789"}, true },
        {"123456789", new String[] {"1234567890"}, false },
        {"123456789", new String[] {"9"}, true },
        {"123456789", new String[] {"1"}, true },
        {"123456789", new String[] {"0"}, false },
        {"123456789", new String[] {"    "}, true },
        {"123456789", new String[] {"0123"}, false },
    };
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SubstringMatchingRule getRule()
  {
    return new NumericStringSubstringMatchingRule();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @DataProvider(name="substringInitialMatchData")
  public Object[][] createSubstringInitialMatchData()
  {
    return new Object[][] {
        {"123456789",  "12345678",   true },
        {"123456789",  "2345678",    false },
        {"123456789",  "1234",       true },
        {"123456789",  "1",          true },
        {"123456789",  "678",        false },
        {"123456789",  "2",          false },
        {"123456789",  " ",          true },
        {"123456789",  "123456789",  true },
        {"123456789",  "1234567890", false },
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
        {"123456789",  "123456789", true },
        {"123456789",  "456789", true },
        {"123456789",  "567", false },
        {"123456789",  "123", false },
        {"123456789",  " ", true },
        {"123456789",  "0789", false },
    };
  }
}
