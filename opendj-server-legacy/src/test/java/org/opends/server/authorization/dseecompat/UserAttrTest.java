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
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.authorization.dseecompat;

import static org.assertj.core.api.Assertions.*;

import java.lang.reflect.Field;

import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.types.DirectoryException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Verifies that {@link UserAttr#decode(String, EnumBindRuleType)} splits a userattr expression at its first
 * octothorpe: the attribute name cannot contain one, the value after it can (issue #1153).
 */
@SuppressWarnings("javadoc")
public class UserAttrTest extends DirectoryServerTestCase
{
  @BeforeClass
  public void setUp() throws Exception
  {
    TestCaseUtils.startFakeServer();
  }

  @AfterClass
  public void tearDown() throws DirectoryException
  {
    TestCaseUtils.shutdownFakeServer();
  }

  @DataProvider
  public Object[][] valueExpressions()
  {
    return new Object[][] {
      { "departmentNumber#a1", "departmentNumber", "a1" },
      { "departmentNumber#a#1", "departmentNumber", "a#1" },
      { "departmentNumber##", "departmentNumber", "#" },
    };
  }

  @Test(dataProvider = "valueExpressions")
  public void decodeSplitsAtTheFirstOctothorpe(String expression, String attrName, String value) throws Exception
  {
    final KeywordBindRule rule = UserAttr.decode(expression, EnumBindRuleType.EQUAL_BINDRULE_TYPE);

    assertThat(field(rule, "attrStr")).isEqualTo(attrName);
    assertThat(field(rule, "attrVal")).isEqualTo(value);
  }

  @DataProvider
  public Object[][] invalidExpressions()
  {
    return new Object[][] { { "departmentNumber" }, { "departmentNumber#" } };
  }

  @Test(dataProvider = "invalidExpressions", expectedExceptions = AciException.class)
  public void decodeRejectsAnExpressionWithoutValue(String expression) throws Exception
  {
    UserAttr.decode(expression, EnumBindRuleType.EQUAL_BINDRULE_TYPE);
  }

  private static Object field(Object object, String name) throws Exception
  {
    final Field field = UserAttr.class.getDeclaredField(name);
    field.setAccessible(true);
    return field.get(object);
  }
}
