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
 *      Copyright 2014 ForgeRock AS
 */
package org.opends.server.util;

import java.lang.reflect.InvocationTargetException;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.*;

@SuppressWarnings("javadoc")
public class StaticUtilsTest extends UtilTestCase
{

  @DataProvider
  public Object[][] stackTraceToSingleLineString()
  {
    final String message = "This is a tremendous error message";
    final String noMessageStackTrace = "Exception (" + getClass().getSimpleName() + ".java:";
    final String messageStackTrace = "Exception: " + message + " (" + getClass().getSimpleName() + ".java:";
    return new Object[][] {
      { null, "" },
      { new Exception(), noMessageStackTrace },
      { new Exception(message), messageStackTrace },
      { new Exception(new Exception()), "Exception: java.lang.Exception (" + getClass().getSimpleName() + ".java:" },
      { new Exception(message, new Exception()), messageStackTrace },
      { new InvocationTargetException(null), "InvocationTargetException (" + getClass().getSimpleName() + ".java:" },
      { new InvocationTargetException(new Exception()), noMessageStackTrace },
      { new InvocationTargetException(new Exception(message)), messageStackTrace },
    };
  }

  @Test(dataProvider = "stackTraceToSingleLineString")
  public void stackTraceToSingleLineString(Throwable t, String startOfOutput)
  {
    assertThat(StaticUtils.stackTraceToSingleLineString(t)).startsWith(startOfOutput);
  }

  @DataProvider
  public Object[][] stackTraceToSingleLineStringInDebugBuild()
  {
    final String message = "This is a tremendous error message";
    final String messageStackTrace = "java.lang.Exception: " + message + " / " + getClass().getSimpleName() + ".java:";
    return new Object[][] {
      { null, "" },
      { new Exception(), "java.lang.Exception / " + getClass().getSimpleName() + ".java:" },
      { new Exception(message), messageStackTrace },
      { new Exception(new Exception()), "java.lang.Exception: java.lang.Exception / " + getClass().getSimpleName() + ".java:" },
      { new Exception(message, new Exception()), messageStackTrace },
    };
  }

  @Test(dataProvider = "stackTraceToSingleLineStringInDebugBuild")
  public void stackTraceToSingleLineStringInDebugBuild(Throwable t, String startOfOutput)
  {
    final boolean orig = DynamicConstants.DEBUG_BUILD;
    DynamicConstants.DEBUG_BUILD = true;
    try
    {
      assertThat(StaticUtils.stackTraceToSingleLineString(t)).startsWith(startOfOutput);
    }
    finally
    {
      DynamicConstants.DEBUG_BUILD = orig;
    }
  }

}
