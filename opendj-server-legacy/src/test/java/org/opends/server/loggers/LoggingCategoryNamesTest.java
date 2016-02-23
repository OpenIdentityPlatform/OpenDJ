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
 * Copyright 2014-2015 ForgeRock AS.
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
