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
 * Portions Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.quicksetup.util;

import static org.testng.Assert.*;

import org.opends.server.DirectoryServerTestCase;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Utils Tester. */
@Test(sequential=true)
@SuppressWarnings("javadoc")
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
    assertEquals(Utils.breakHtmlString(s, maxll), expectedValue);
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
    assertEquals(expectedResult, Utils.containsHtml(s));
  }
}
