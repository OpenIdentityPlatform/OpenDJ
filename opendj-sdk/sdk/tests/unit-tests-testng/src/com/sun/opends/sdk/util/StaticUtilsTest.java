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

package com.sun.opends.sdk.util;



import org.opends.sdk.OpenDSTestCase;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;



/**
 * Test {@code StaticUtils}.
 */
@Test(groups = { "precommit", "types", "sdk" }, sequential = true)
public final class StaticUtilsTest extends OpenDSTestCase
{
  @DataProvider(name = "dataForToLowerCase")
  public Object[][] dataForToLowerCase()
  {
    // Value, toLowerCase or null if identity
    return new Object[][] {
        { "", null },
        { " ", null },
        { "   ", null },
        { "12345", null },
        {
            "abcdefghijklmnopqrstuvwxyz1234567890`~!@#$%^&*()_-+={}|[]\\:\";'<>?,./",
            null },
        {
            "Aabcdefghijklmnopqrstuvwxyz1234567890`~!@#$%^&*()_-+={}|[]\\:\";'<>?,./",
            "aabcdefghijklmnopqrstuvwxyz1234567890`~!@#$%^&*()_-+={}|[]\\:\";'<>?,./" },
        {
            "abcdefghijklmnopqrstuvwxyz1234567890`~!@#$%^&*()_-+={}|[]\\:\";'<>?,./A",
            "abcdefghijklmnopqrstuvwxyz1234567890`~!@#$%^&*()_-+={}|[]\\:\";'<>?,./a" },
        { "ABCDEFGHIJKLMNOPQRSTUVWXYZ", "abcdefghijklmnopqrstuvwxyz" },
        { "\u00c7edilla", "\u00e7edilla" },
        { "ced\u00cdlla", "ced\u00edlla" }, };
  }



  @Test(dataProvider = "dataForToLowerCase")
  public void testToLowerCaseString(String s, String expected)
  {
    String actual = StaticUtils.toLowerCase(s);
    if (expected == null)
    {
      Assert.assertSame(actual, s);
    }
    else
    {
      Assert.assertEquals(actual, expected);
    }
  }



  @Test(dataProvider = "dataForToLowerCase")
  public void testToLowerCaseStringBuilder(String s, String expected)
  {
    StringBuilder builder = new StringBuilder();
    String actual = StaticUtils.toLowerCase(s, builder).toString();
    if (expected == null)
    {
      Assert.assertEquals(actual, s);
    }
    else
    {
      Assert.assertEquals(actual, expected);
    }
  }
}
