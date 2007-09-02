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
 * Test the DistinguishedNameEqualityMatchingRule.
 */
public class DistinguishedNameEqualityMatchingRuleTest extends
    EqualityMatchingRuleTest
{

  /**
   * {@inheritDoc}
   */
  @Override
  @DataProvider(name="equalityMatchingRuleInvalidValues")
  public Object[][] createEqualityMatchingRuleInvalidValues()
  {
    return new Object[][] {
        
    };
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @DataProvider(name="equalitymatchingrules")
  public Object[][] createEqualityMatchingRuleTest()
  {
    /*
     *  DistinguishedNameEqualityMatchingRule rely on DN for the
     *  DN with correct syntax, just do a few such tests and then
     *  do more tests with invalid DNs
     */
    
    return new Object[][] {
        // valid DNs
        { "", "" , true},
        { "   ", "", true },
        { "dc=com", "dc=com" , true},
        { "DC=COM", "dc=com" , true},
        { "dc = com", "dc=com" , true},
        { " dc = com ", "dc=com" , true},
        { "dc=example,dc=com", "dc=example,dc=com" , true},
        { "dc=example, dc=com", "dc=example,dc=com", true },
        { "dc=example ,dc=com", "dc=example,dc=com", true },
    };
  }
 
  /**
   * {@inheritDoc}
   */
  @Override
  @DataProvider(name="warnmodeEqualityMatchingRule")
  public Object[][] createWarnmodeEqualityMatchingRuleTest()
  {
    return new Object[][] {
        // invalid DNs
        { "example", "example", true },
        { " example ", " example ", true },
        { "invalid dn", "invalid   dn", true },
    };
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected EqualityMatchingRule getRule()
  {
    return new DistinguishedNameEqualityMatchingRule();
  }

}
