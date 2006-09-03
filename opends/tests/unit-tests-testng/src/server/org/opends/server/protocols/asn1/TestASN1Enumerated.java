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
import static org.testng.AssertJUnit.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;

import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;

/**
 * This class defines a set of tests for the
 * org.opends.server.protocols.asn1.ASN1Enumerated class.
 */
public class TestASN1Enumerated extends ASN1TestCase {
  // The set of encoded values for the test integers.
  private ArrayList<byte[]> testEncodedIntegers;

  // The set of integer values to use in test cases.
  private ArrayList<Integer> testIntegers;

  /**
   * Performs any necessary initialization for this test case.
   */
  @BeforeClass
  public void setUp() {
    testIntegers = new ArrayList<Integer>();
    testEncodedIntegers = new ArrayList<byte[]>();

    // Add all values that can be encoded using a single byte.
    for (int i = 0; i < 128; i++) {
      testIntegers.add(i);
      testEncodedIntegers.add(new byte[] { (byte) (i & 0xFF) });
    }

    testIntegers.add(0x80); // The smallest 2-byte encoding.
    testEncodedIntegers.add(new byte[] { (byte) 0x00, (byte) 0x80 });

    testIntegers.add(0xFF); // A boundary case for 2-byte encoding.
    testEncodedIntegers.add(new byte[] { (byte) 0x00, (byte) 0xFF });

    testIntegers.add(0x0100); // A boundary case for 2-byte encoding.
    testEncodedIntegers.add(new byte[] { (byte) 0x01, (byte) 0x00 });

    testIntegers.add(0x7FFF); // The largest 2-byte encoding.
    testEncodedIntegers.add(new byte[] { (byte) 0x7F, (byte) 0xFF });

    testIntegers.add(0x8000); // The smallest 3-byte encoding.
    testEncodedIntegers.add(new byte[] { (byte) 0x00, (byte) 0x80,
        (byte) 0x00 });

    testIntegers.add(0xFFFF); // A boundary case for 3-byte encoding.
    testEncodedIntegers.add(new byte[] { (byte) 0x00, (byte) 0xFF,
        (byte) 0xFF });

    testIntegers.add(0x010000); // A boundary case for 3-byte encoding.
    testEncodedIntegers.add(new byte[] { (byte) 0x01, (byte) 0x00,
        (byte) 0x00 });

    testIntegers.add(0x7FFFFF); // The largest 3-byte encoding.
    testEncodedIntegers.add(new byte[] { (byte) 0x7F, (byte) 0xFF,
        (byte) 0xFF });

    testIntegers.add(0x800000); // The smallest 4-byte encoding.
    testEncodedIntegers.add(new byte[] { (byte) 0x00, (byte) 0x80,
        (byte) 0x00, (byte) 0x00 });

    testIntegers.add(0xFFFFFF); // A boundary case for 4-byte encoding.
    testEncodedIntegers.add(new byte[] { (byte) 0x00, (byte) 0xFF,
        (byte) 0xFF, (byte) 0xFF });

    testIntegers.add(0x01000000); // A boundary case for 4-byte
    // encoding.
    testEncodedIntegers.add(new byte[] { (byte) 0x01, (byte) 0x00,
        (byte) 0x00, (byte) 0x00 });

    testIntegers.add(0x7FFFFFFF); // The largest value we will allow.
    testEncodedIntegers.add(new byte[] { (byte) 0x7F, (byte) 0xFF,
        (byte) 0xFF, (byte) 0xFF });
  }

  /**
   * Tests the <CODE>intValue</CODE> method.
   */
  @Test()
  public void testIntValue() {
    for (int i : testIntegers) {
      ASN1Integer element = new ASN1Integer(i);

      assertEquals(i, element.intValue());
    }
  }

  /**
   * Tests the <CODE>setValue</CODE> method that takes an int
   * argument.
   */
  @Test()
  public void testSetIntValue() {
    ASN1Integer element = new ASN1Integer(0);

    int numIntegers = testIntegers.size();
    for (int i = 0; i < numIntegers; i++) {
      int intValue = testIntegers.get(i);
      byte[] encodedIntValue = testEncodedIntegers.get(i);

      element.setValue(intValue);
      assertEquals(intValue, element.intValue());
      assertTrue(Arrays.equals(encodedIntValue, element.value()));
    }
  }

  /**
   * Tests the <CODE>setValue</CODE> method that takes a byte array
   * argument.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test()
  public void testSetByteValue() throws Exception {
    ASN1Integer element = new ASN1Integer(0);

    int numIntegers = testIntegers.size();
    for (int i = 0; i < numIntegers; i++) {
      int intValue = testIntegers.get(i);
      byte[] encodedIntValue = testEncodedIntegers.get(i);

      element.setValue(encodedIntValue);

      assertEquals(intValue, element.intValue());
    }
  }

  /**
   * Tests the <CODE>decodeAsEnumerated</CODE> method that takes an
   * ASN.1 element argument.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test()
  public void testDecodeElementAsEnumerated() throws Exception {
    int numIntegers = testIntegers.size();
    for (int i = 0; i < numIntegers; i++) {
      int intValue = testIntegers.get(i);
      byte[] encodedIntValue = testEncodedIntegers.get(i);

      ASN1Element element = new ASN1Element((byte) 0x00, encodedIntValue);

      assertEquals(intValue, ASN1Enumerated.decodeAsEnumerated(element)
          .intValue());

      element = new ASN1Element((byte) 0x0A, encodedIntValue);

      assertEquals(intValue, ASN1Enumerated.decodeAsEnumerated(element)
          .intValue());
    }
  }

  /**
   * Tests the <CODE>decodeAsEnumerated</CODE> method that takes a
   * byte array argument.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test()
  public void testDecodeBytesAsEnumerated() throws Exception {
    int numIntegers = testIntegers.size();
    for (int i = 0; i < numIntegers; i++) {
      int intValue = testIntegers.get(i);
      byte[] encodedIntValue = testEncodedIntegers.get(i);
      byte[] encodedLength = ASN1Element
          .encodeLength(encodedIntValue.length);
      byte[] encodedElement = new byte[1 + encodedLength.length
          + encodedIntValue.length];

      encodedElement[0] = (byte) 0x00;
      System.arraycopy(encodedLength, 0, encodedElement, 1,
          encodedLength.length);
      System.arraycopy(encodedIntValue, 0, encodedElement,
          1 + encodedLength.length, encodedIntValue.length);

      assertEquals(intValue, ASN1Enumerated.decodeAsEnumerated(
          encodedElement).intValue());

      encodedElement[0] = (byte) 0x0A;
      assertEquals(intValue, ASN1Enumerated.decodeAsEnumerated(
          encodedElement).intValue());
    }
  }
}
