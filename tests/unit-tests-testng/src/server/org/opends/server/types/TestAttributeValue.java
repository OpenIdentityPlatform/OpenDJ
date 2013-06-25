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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 */
package org.opends.server.types;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.testng.Assert;
import org.opends.server.core.DirectoryServer;

/**
 * Test case for AttributeValues
 */
public class TestAttributeValue extends TypesTestCase
{
  /**
   * Create test data for testing the
   * {@link AttributeValue#hashCode()} method.
   *
   * @return Returns the array of test data.
   */
  @DataProvider(name = "generateHashCodeTestData")
  public Object[][] createHashCodeTestData() {
    return new Object[][] { { "one", "one", true },
        { "one", "ONE", true }, { "one", "  oNe  ", true },
        { "one two", " one  two  ", true },
        { "one two", "onetwo", false }, { "one", "two", false } };
  }



  /**
   * Check that the
   * {@link AttributeValue#hashCode()} method
   * works as expected.
   *
   * @param value1
   *          The first test value.
   * @param value2
   *          The second test value.
   * @param result
   *          The expected result.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "generateHashCodeTestData")
  public void testHashCodeTestData(String value1,
      String value2, boolean result) throws Exception {
    AttributeType type = DirectoryServer.getDefaultAttributeType("test");

    AttributeValue av1 = AttributeValues.create(type, value1);
    AttributeValue av2 = AttributeValues.create(type, value2);

    int h1 = av1.hashCode();
    int h2 = av2.hashCode();

    Assert.assertEquals(h1 == h2, result);
  }



  /**
   * Check that the {@link AttributeValue#getNormalizedValue()} method
   * works as expected.
   *
   * @param value1
   *          The first test value.
   * @param value2
   *          The second test value.
   * @param result
   *          The expected result.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "generateHashCodeTestData")
  public void testGetNormalizedValue(String value1, String value2,
      boolean result) throws Exception {
    AttributeType type = DirectoryServer.getDefaultAttributeType("test");

    AttributeValue av1 = AttributeValues.create(type, value1);
    AttributeValue av2 = AttributeValues.create(type, value2);

    ByteString r1 = av1.getNormalizedValue();
    ByteString r2 = av2.getNormalizedValue();

    Assert.assertEquals(r1.equals(r2), result);
  }
}
