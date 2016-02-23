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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2015 ForgeRock AS.
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
  @Test(expectedExceptions = PropertyException.class)
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
  @Test(expectedExceptions = PropertyException.class)
  public void testDecodeValue2() {
    StringPropertyDefinition d = getDefinition(true, "^[a-z]+$");
    d.decodeValue("abc123");
  }



  /** Create a string property definition. */
  private StringPropertyDefinition getDefinition(
      boolean isCaseInsensitive, String pattern) {
    StringPropertyDefinition.Builder builder = StringPropertyDefinition
        .createBuilder(RootCfgDefn.getInstance(), "test-property");
    builder.setCaseInsensitive(isCaseInsensitive);
    builder.setPattern(pattern, "STRING");
    return builder.getInstance();
  }
}
