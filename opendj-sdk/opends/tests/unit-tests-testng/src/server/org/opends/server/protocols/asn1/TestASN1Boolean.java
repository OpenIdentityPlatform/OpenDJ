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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.protocols.asn1;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;

import org.testng.annotations.Test;

/**
 * This class defines a set of tests for the
 * org.opends.server.protocols.asn1.ASN1Boolean class.
 */
public class TestASN1Boolean extends ASN1TestCase {
  /**
   * Tests the <CODE>booleanValue</CODE> method.
   */
  @Test
  public void testBooleanValue() {
    assertTrue(new ASN1Boolean(true).booleanValue());

    assertFalse(new ASN1Boolean(false).booleanValue());
  }

  /**
   * Tests the <CODE>setValue</CODE> method that takes a boolean
   * argument.
   */
  @Test(dependsOnMethods = { "testBooleanValue" })
  public void testSetBooleanValue() {
    ASN1Boolean element = new ASN1Boolean(true); // Create a new true
    // element.

    element.setValue(true); // Test setting the same value.
    assertTrue(element.booleanValue());

    element.setValue(false); // Test setting the opposite value.
    assertFalse(element.booleanValue());

    element = new ASN1Boolean(false); // Create a new false element.

    element.setValue(false); // Test setting the same value.
    assertFalse(element.booleanValue());

    element.setValue(true); // Test setting the opposite value.
    assertTrue(element.booleanValue());
  }

  /**
   * Tests the <CODE>setValue</CODE> method that takes a byte array
   * argument.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dependsOnMethods = { "testBooleanValue" })
  public void testSetByteValue() throws Exception {
    ASN1Boolean element = new ASN1Boolean(true);

    element.setValue(new byte[] { 0x00 });
    assertFalse(element.booleanValue());

    // Test setting the value to all other possible byte
    // representations, which
    // should evaluate to "true".
    for (int i = 1; i < 256; i++) {
      byte byteValue = (byte) (i & 0xFF);

      element.setValue(new byte[] { byteValue });
      assertTrue(element.booleanValue());
    }
  }

  /**
   * Tests the <CODE>decodeAsBoolean</CODE> method that takes an ASN.1
   * element argument.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dependsOnMethods = { "testBooleanValue" })
  public void testDecodeElementAsBoolean() throws Exception {
    // Test a boolean element of "true".
    ASN1Boolean trueBoolean = new ASN1Boolean(true);
    assertTrue(ASN1Boolean.decodeAsBoolean(trueBoolean).booleanValue());

    // Test a boolean element of "false".
    ASN1Boolean falseBoolean = new ASN1Boolean(false);
    assertFalse(ASN1Boolean.decodeAsBoolean(falseBoolean).booleanValue());

    // Test the valid generic elements that may be used.
    for (int i = 0; i < 256; i++) {
      byte byteValue = (byte) (i & 0xFF);
      boolean compareValue = (i != 0);

      ASN1Element element = new ASN1Element((byte) 0x00,
          new byte[] { byteValue });

      assertEquals(compareValue, ASN1Boolean.decodeAsBoolean(element)
          .booleanValue());
    }
  }

  /**
   * Tests the <CODE>decodeAsBoolean</CODE> method that takes a byte
   * array argument.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dependsOnMethods = { "testBooleanValue" })
  public void testDecodeBytesAsBoolean() throws Exception {
    byte[] encodedElement = new byte[] { 0x00, 0x01, 0x00 };

    // Test all possible byte values.
    for (int i = 0; i < 256; i++) {
      boolean compareValue = (i != 0);

      // Set the value.
      encodedElement[2] = (byte) (i & 0xFF);

      // Test with the standard Boolean type.
      encodedElement[0] = (byte) 0x01;
      assertEquals(compareValue, ASN1Boolean
          .decodeAsBoolean(encodedElement).booleanValue());
    }
  }
}
