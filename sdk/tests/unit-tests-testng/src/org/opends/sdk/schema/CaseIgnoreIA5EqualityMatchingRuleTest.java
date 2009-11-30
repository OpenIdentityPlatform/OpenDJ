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
 *      Copyright 2009 Sun Microsystems, Inc.
 */
package org.opends.sdk.schema;



import static org.opends.server.schema.SchemaConstants.EMR_CASE_IGNORE_IA5_OID;

import org.opends.sdk.ConditionResult;
import org.testng.annotations.DataProvider;



/**
 * Test the CaseExactIA5EqualityMatchingRule.
 */
public class CaseIgnoreIA5EqualityMatchingRuleTest extends
    MatchingRuleTest
{

  /**
   * {@inheritDoc}
   */
  @Override
  @DataProvider(name = "matchingRuleInvalidAttributeValues")
  public Object[][] createMatchingRuleInvalidAttributeValues()
  {
    return new Object[][] { { "12345678\uFFFD" }, };
  }



  /**
   * {@inheritDoc}
   */
  @Override
  @DataProvider(name = "matchingrules")
  public Object[][] createMatchingRuleTest()
  {
    return new Object[][] {
        { "12345678", "12345678", ConditionResult.TRUE },
        { "ABC45678", "ABC45678", ConditionResult.TRUE },
        { "ABC45678", "abc45678", ConditionResult.TRUE }, };
  }



  /**
   * {@inheritDoc}
   */
  @Override
  protected MatchingRule getRule()
  {
    return Schema.getCoreSchema().getMatchingRule(
        EMR_CASE_IGNORE_IA5_OID);
  }

}
