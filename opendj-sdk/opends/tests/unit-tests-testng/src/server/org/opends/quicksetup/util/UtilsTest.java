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

package org.opends.quicksetup.util;

import static org.testng.Assert.*;
import org.testng.annotations.*;
import org.opends.quicksetup.Constants;
import org.opends.server.DirectoryServerTestCase;

/**
 * Utils Tester.
 */
@Test(sequential=true)
public class UtilsTest extends DirectoryServerTestCase {

  @DataProvider(name = "breakHtmlStringData")
  public Object[][] breakHtmlStringData() {
    // Test data should not contain words longer that
    // the maximum line length value.
    return new Object[][]{
            {"Hi my name is Bingo", 5,
                    "Hi my<br>name<br>is<br>Bingo"},
            {"Hi<br>my name is Bingo", 5,
                    "Hi<br>my<br>name<br>is<br>Bingo"},
            {"Hi<br>my<br>name<br>is<br>Bingo", 5,
                    "Hi<br>my<br>name<br>is<br>Bingo"},
    };
  }

  @Test(dataProvider = "breakHtmlStringData")
  public void testBreakHtmlString(String s, int maxll, String expectedValue) {
    assertTrue(Utils.breakHtmlString(s, maxll).equals(expectedValue));
  }

  @DataProvider(name = "stripHtmlData")
  public Object[][] stripHtmlData() {
    return new Object[][]{
            {"Hi <i>Ho</i>", "Hi Ho"},
            {"Hi <b>Ho</b>", "Hi Ho"},
            {"Hi<br> Ho", "Hi Ho"},
            {"Hi<br/> Ho", "Hi Ho"},
            {"Hi<input value=\"abc\"/> Ho", "Hi Ho"},
            {"Hi<input value=\"abc\"></input> Ho", "Hi Ho"},
            // {"Hi<tag attr=\"1 > 0\"> Ho", "Hi Ho"}, // broken case
            // {"Hi <your name here>", "Hi <your name here>"} // broken case
    };
  }

  @Test(enabled = false, dataProvider = "stripHtmlData")
  public void testStripHtml(String html, String expectedResult) {
    assertTrue(expectedResult.equals(Utils.stripHtml(html)));
  }

  @DataProvider(name = "containsHtmlData")
  public Object[][] containsHtmlData() {
    return new Object[][]{
            {"Hi <i>Ho</i>", true},
            // {"Hello <your name here>", false}, // broken case
    };
  }

  @Test(enabled = false, dataProvider = "containsHtmlData")
  public void testContainsHtml(String s, boolean expectedResult) {
    assertTrue(expectedResult == Utils.containsHtml(s));
  }

}
