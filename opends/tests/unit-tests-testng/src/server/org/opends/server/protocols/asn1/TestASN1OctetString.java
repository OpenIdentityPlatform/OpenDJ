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

import static org.opends.server.util.StaticUtils.getBytes;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;

import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;

/**
 * This class defines a set of tests for the
 * org.opends.server.protocols.asn1.ASN1OctetString class.
 */
public class TestASN1OctetString extends ASN1TestCase {
  // The set of binary values that should be used in test cases.
  private ArrayList<byte[]> testByteValues;

  // The set of encoded versions of the provided string values.
  private ArrayList<byte[]> testEncodedStrings;

  // The set of string values that should be used in test cases.
  private ArrayList<String> testStrings;

  /**
   * Performs any necessary initialization for this test case.
   */
  @BeforeClass
  public void setUp() {
    // Initialize the set of binary values. Don't make these too big
    // since they
    // consume memory.
    testByteValues = new ArrayList<byte[]>();
    testByteValues.add(null); // The null value.
    testByteValues.add(new byte[0x00]); // The zero-byte value.
    testByteValues.add(new byte[0x01]); // The single-byte value.
    testByteValues.add(new byte[0x7F]); // The largest 1-byte length
    // encoding.
    testByteValues.add(new byte[0x80]);
    testByteValues.add(new byte[0xFF]); // The largest 2-byte length
    // encoding.
    testByteValues.add(new byte[0x0100]);
    testByteValues.add(new byte[0xFFFF]); // The largest 3-byte length
    // encoding.
    testByteValues.add(new byte[0x010000]);

    // Initialize the set of string values.
    testStrings = new ArrayList<String>();
    testEncodedStrings = new ArrayList<byte[]>();

    testStrings.add(null);
    testEncodedStrings.add(new byte[0]);

    testStrings.add("");
    testEncodedStrings.add(new byte[0]);

    String lastString = "";
    for (int i = 0; i <= 256; i++) {
      String newString = lastString + "a";
      testStrings.add(newString);
      testEncodedStrings.add(getBytes(newString));

      lastString = newString;
    }
  }

  /**
   * Tests the <CODE>stringValue</CODE> method.
   */
  @Test()
  public void testStringValue() {
    for (String s : testStrings) {
      if (s == null) {
        assertEquals("", new ASN1OctetString(s).stringValue());
      } else {
        assertEquals(s, new ASN1OctetString(s).stringValue());
      }
    }
  }

  /**
   * Tests the <CODE>setValue</CODE> method that takes a string
   * argument.
   */
  @Test()
  public void testSetStringValue() {
    ASN1OctetString element = new ASN1OctetString();

    int numStrings = testStrings.size();
    for (int i = 0; i < numStrings; i++) {
      String s = testStrings.get(i);
      byte[] b = testEncodedStrings.get(i);

      String compareValue;
      if (s == null) {
        compareValue = "";
      } else {
        compareValue = s;
      }

      element.setValue(s);
      assertEquals(compareValue, element.stringValue());

      assertTrue(Arrays.equals(b, element.value()));
    }
  }

  /**
   * Tests the <CODE>setValue</CODE> method that takes a byte array
   * argument.
   */
  @Test()
  public void testSetByteValue() {
    ASN1OctetString element = new ASN1OctetString();

    // Test the binary representations.
    for (byte[] value : testByteValues) {
      byte[] compareValue;
      if (value == null) {
        compareValue = new byte[0];
      } else {
        compareValue = value;
      }

      element.setValue(value);

      assertTrue(Arrays.equals(compareValue, element.value()));
    }

    // Test the string representations.
    int numStrings = testStrings.size();
    for (int i = 0; i < numStrings; i++) {
      String s = testStrings.get(i);
      byte[] b = testEncodedStrings.get(i);

      String compareString;
      if (s == null) {
        compareString = "";
      } else {
        compareString = s;
      }

      element.setValue(b);

      assertEquals(compareString, element.stringValue());

      assertTrue(Arrays.equals(b, element.value()));
    }
  }

  /**
   * Tests the <CODE>decodeAsOctetString</CODE> method that takes an
   * ASN.1 element argument.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test()
  public void testDecodeElementAsOctetString() throws Exception {
    // Run tests with the binary values.
    for (byte[] value : testByteValues) {
      ASN1Element element = new ASN1Element((byte) 0x00, value);

      byte[] compareValue;
      if (value == null) {
        compareValue = new byte[0];
      } else {
        compareValue = value;
      }

      assertTrue(Arrays.equals(compareValue, ASN1OctetString
          .decodeAsOctetString(element).value()));
    }

    // Run tests with the string values.
    int numStrings = testStrings.size();
    for (int i = 0; i < numStrings; i++) {
      String s = testStrings.get(i);
      byte[] b = testEncodedStrings.get(i);

      String compareString;
      if (s == null) {
        compareString = "";
      } else {
        compareString = s;
      }

      ASN1Element element = new ASN1Element((byte) 0x00, b);

      assertEquals(compareString, ASN1OctetString.decodeAsOctetString(
          element).stringValue());
    }
  }

  /**
   * Tests the <CODE>decodeAsOctetString</CODE> method that takes a
   * byte array argument.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test()
  public void testDecodeBytesAsOctetString() throws Exception {
    // Run tests with the binary values.
    for (byte[] value : testByteValues) {
      byte[] encodedLength;
      byte[] encodedElement;
      if (value == null) {
        encodedLength = ASN1Element.encodeLength(0);
        encodedElement = new byte[1 + encodedLength.length];
      } else {
        encodedLength = ASN1Element.encodeLength(value.length);
        encodedElement = new byte[1 + encodedLength.length + value.length];
      }

      encodedElement[0] = (byte) 0x00;
      System.arraycopy(encodedLength, 0, encodedElement, 1,
          encodedLength.length);

      if (value != null) {
        System.arraycopy(value, 0, encodedElement,
            1 + encodedLength.length, value.length);
      }

      byte[] compareValue;
      if (value == null) {
        compareValue = new byte[0];
      } else {
        compareValue = value;
      }

      assertTrue(Arrays.equals(compareValue, ASN1OctetString
          .decodeAsOctetString(encodedElement).value()));
    }

    // Run tests with the string values.
    int numStrings = testStrings.size();
    for (int i = 0; i < numStrings; i++) {
      String s = testStrings.get(i);
      byte[] b = testEncodedStrings.get(i);

      String compareString;
      if (s == null) {
        compareString = "";
      } else {
        compareString = s;
      }

      byte[] encodedLength = ASN1Element.encodeLength(b.length);
      byte[] encodedElement = new byte[1 + encodedLength.length + b.length];

      encodedElement[0] = (byte) 0x00;
      System.arraycopy(encodedLength, 0, encodedElement, 1,
          encodedLength.length);
      System.arraycopy(b, 0, encodedElement, 1 + encodedLength.length,
          b.length);

      assertEquals(compareString, ASN1OctetString.decodeAsOctetString(
          encodedElement).stringValue());
    }
  }

  /**
   * Tests the <CODE>duplicate</CODE> method.
   */
  @Test()
  public void testDuplicate() {
    // Run tests with the binary values.
    for (byte[] value : testByteValues) {
      ASN1OctetString os1 = new ASN1OctetString(value);
      ASN1OctetString os2 = os1.duplicate();
      assertEquals(os1, os2);

      assertEquals(os1.hashCode(), os2.hashCode());
    }

    // Run tests with the string values.
    int numStrings = testStrings.size();
    for (int i = 0; i < numStrings; i++) {
      String s = testStrings.get(i);

      String compareString;
      if (s == null) {
        compareString = "";
      } else {
        compareString = s;
      }

      ASN1OctetString os1 = new ASN1OctetString(s);
      ASN1OctetString os2 = os1.duplicate();

      assertEquals(os1, os2);

      assertEquals(compareString, os2.stringValue());

      assertEquals(os1.hashCode(), os2.hashCode());
    }
  }
}
