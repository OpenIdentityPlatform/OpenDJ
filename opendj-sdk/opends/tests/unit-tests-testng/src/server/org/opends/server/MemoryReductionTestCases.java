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
package org.opends.server;

import org.testng.annotations.Test;
import org.testng.Assert;

/**
 * This tests some of the hacks we have in place in DirectoryServerTestCase
 * to reduce the memory that we consume during the tests.
 */
public class MemoryReductionTestCases extends DirectoryServerTestCase {
  private Object normalMemberObj = "should be nulled";
  private String normalMemberStr = "should be nulled";
  private final String finalStr = "shouldn't be nulled";
  private static String staticStr = "shouldn't be nulled";
  private int justAnInt = 1; // Shouldn't be affected.

  /**
   * Test that nullMemberVariablesAfterTest can actually null out
   * member variables.
   */
  @Test
  public void testNullMemberVariablesAfterTest() {
    nullMemberVariablesAfterTest();

    Assert.assertNull(normalMemberStr);
    Assert.assertNull(normalMemberObj);
    Assert.assertNotNull(finalStr);
    Assert.assertNotNull(staticStr);
    Assert.assertEquals(justAnInt, 1);
  }


  @Test
  public void testClearedTestParameters() {
    String[] successfulOnly = {"", ""};
    String[] successfulAndFailed = {"", ""};
    String[] failedOnly = {"", ""};

    addParamsFromSuccessfulTests(successfulOnly);
    addParamsFromSuccessfulTests(successfulAndFailed);
    addParamsFromFailedTest(successfulAndFailed);
    addParamsFromFailedTest(failedOnly);

    clearSuccessfulTestParams();

    Assert.assertEquals(successfulOnly, new String[]{null, null});
    Assert.assertEquals(successfulAndFailed, new String[]{"", ""});
    Assert.assertEquals(failedOnly, new String[]{"", ""});
  }
}
