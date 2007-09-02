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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.server.admin;



import static org.testng.Assert.assertEquals;

import org.opends.server.admin.std.meta.RootCfgDefn;
import org.opends.server.DirectoryServerTestCase;
import org.testng.annotations.Test;



/**
 * StringPropertyDefinition Tester.
 */
public class StringPropertyDefinitionTest extends DirectoryServerTestCase {

  /**
   * Tests validateValue() with valid data and no pattern.
   */
  @Test
  public void testValidateValue1() {
    StringPropertyDefinition d = getDefinition(true, null);
    d.validateValue("abc");
  }



  /**
   * Tests validateValue() with valid data and a pattern.
   */
  @Test
  public void testValidateValue2() {
    StringPropertyDefinition d = getDefinition(true, "^[a-z]+$");
    d.validateValue("abc");
  }



  /**
   * Tests validateValue() with invalid data and a pattern.
   */
  @Test(expectedExceptions = IllegalPropertyValueException.class)
  public void testValidateValue3() {
    StringPropertyDefinition d = getDefinition(true, "^[a-z]+$");
    d.validateValue("abc123");
  }



  /**
   * Tests decodeValue() with valid data and a pattern.
   */
  @Test
  public void testDecodeValue1() {
    StringPropertyDefinition d = getDefinition(true, "^[a-z]+$");
    assertEquals(d.decodeValue("abc"), "abc");
  }



  /**
   * Tests decodeValue() with invalid data and a pattern.
   */
  @Test(expectedExceptions = IllegalPropertyValueStringException.class)
  public void testDecodeValue2() {
    StringPropertyDefinition d = getDefinition(true, "^[a-z]+$");
    d.decodeValue("abc123");
  }



  // Create a string property definition.
  private StringPropertyDefinition getDefinition(
      boolean isCaseInsensitive, String pattern) {
    StringPropertyDefinition.Builder builder = StringPropertyDefinition
        .createBuilder(RootCfgDefn.getInstance(), "test-property");
    builder.setCaseInsensitive(isCaseInsensitive);
    builder.setPattern(pattern, "STRING");
    return builder.getInstance();
  }
}
