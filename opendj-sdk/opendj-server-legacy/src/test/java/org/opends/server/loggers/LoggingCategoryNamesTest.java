/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2014-2015 ForgeRock AS.
 */
package org.opends.server.loggers;

import org.opends.server.DirectoryServerTestCase;
import org.opends.server.core.DirectoryServer;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

@SuppressWarnings("javadoc")
public class LoggingCategoryNamesTest extends DirectoryServerTestCase
{
  private static final String CORE_PACKAGE = DirectoryServer.class.getPackage().getName();
  private static final String CORE_PACKAGE_NAME = "CORE";

  @DataProvider
  public Object[][] matchingNames()
  {
    return new Object[][] {
      { CORE_PACKAGE },
      { CORE_PACKAGE + ".SomeClass" },
      { CORE_PACKAGE + ".pack1.SomeClass"},
      { CORE_PACKAGE + ".pack1.pack2.SomeClass" },
    };
  }

  @DataProvider
  public Object[][] nonMatchingNames()
  {
    return new Object[][] {
      { CORE_PACKAGE.substring(0, CORE_PACKAGE.length()-1) },
      { CORE_PACKAGE.substring(0, CORE_PACKAGE.length()-1) + ".SomeClass" },
      { CORE_PACKAGE.substring(0, CORE_PACKAGE.length()-1) + ".pack1.SomeClass"},
      { "org.opends.server" },
      { "" },
      { "anyvalue" },
    };
  }

  @Test(dataProvider = "matchingNames")
  public void testMatching(String classname) throws Exception
  {
    assertEquals(LoggingCategoryNames.getCategoryName(classname), CORE_PACKAGE_NAME);
  }

  @Test(dataProvider = "nonMatchingNames")
  public void testNonMatching(String classname) throws Exception
  {
    assertEquals(LoggingCategoryNames.getCategoryName(classname), classname);
  }

  @Test
  public void testSubPackages() throws Exception
  {
    assertEquals(LoggingCategoryNames.getCategoryName("org.opends.server.backends.jeb.foo"), "JEB");
    assertEquals(LoggingCategoryNames.getCategoryName("org.opends.server.backends.jeb"), "JEB");
    assertEquals(LoggingCategoryNames.getCategoryName("org.opends.server.backends"), "BACKEND");
    assertEquals(LoggingCategoryNames.getCategoryName("org.opends.server"), "org.opends.server");
  }
}
