/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2008 Sun Microsystems, Inc.
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
    nullMemberVariablesAfterTest2();

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

    clearSuccessfulTestParams2();

    Assert.assertEquals(successfulOnly, new String[]{null, null});
    Assert.assertEquals(successfulAndFailed, new String[]{"", ""});
    Assert.assertEquals(failedOnly, new String[]{"", ""});
  }
}
