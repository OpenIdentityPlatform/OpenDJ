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

import static org.opends.server.util.StaticUtils.listsAreEqual;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;

import org.testng.annotations.Configuration;
import org.testng.annotations.Test;

/**
 * This class defines a set of tests for the
 * org.opends.server.protocols.asn1.ASN1Element class.
 */
public class TestASN1Element extends ASN1TestCase {
  // The sets of pre-encoded ASN.1 elements that will be included in the
  // test
  // case.
  private ArrayList<ArrayList<ASN1Element>> testElementSets;

  // The set of pre-encoded element sets that will be used in the test
  // cases.
  private ArrayList<byte[]> testEncodedElementSets;

  // The set of pre-encoded integer values that will be included in the
  // test
  // cases.
  private ArrayList<byte[]> testEncodedIntegers;

  // The set of pre-encoded lengths that will be included in the test
  // cases.
  private ArrayList<byte[]> testEncodedLengths;

  // The set of BER types that will be included in the test cases.
  private ArrayList<Byte> testTypes;

  // The set of element values that will be included in the test cases.
  private ArrayList<byte[]> testValues;

  // The set of integer values that will be included in the test cases.
  private ArrayList<Integer> testIntegers;

  // The set of lengths that will be included in the test cases.
  private ArrayList<Integer> testLengths;

  /**
   * Performs any necessary initialization for this test case.
   */
  @Configuration(beforeTestClass = true)
  public void setUp() {
    // Initialize the set of types. It will encapsulate the entire range
    // of
    // possible byte values.
    testTypes = new ArrayList<Byte>();
    for (int i = 0; i < 0xFF; i++) {
      testTypes.add((byte) (i & 0xFF));
    }

    // Initialize the set of values. Don't make these too big since they
    // consume memory.
    testValues = new ArrayList<byte[]>();
    testValues.add(null); // The null value.
    testValues.add(new byte[0x00]); // The zero-byte value.
    testValues.add(new byte[0x01]); // The single-byte value.
    testValues.add(new byte[0x7F]); // The largest 1-byte length
    // encoding.
    testValues.add(new byte[0x80]);
    testValues.add(new byte[0xFF]); // The largest 2-byte length
    // encoding.
    testValues.add(new byte[0x0100]);
    testValues.add(new byte[0xFFFF]); // The largest 3-byte length
    // encoding.
    testValues.add(new byte[0x010000]);

    // Initialize the set of element lengths and their pre-encoded
    // representations. Don't make these too big since we will create
    // arrays
    // with these lengths during testing.
    testLengths = new ArrayList<Integer>();
    testEncodedLengths = new ArrayList<byte[]>();

    testLengths.add(0x00); // The zero-byte length.
    testEncodedLengths.add(new byte[] { (byte) 0x00 });

    testLengths.add(0x01); // A common 1-byte length.
    testEncodedLengths.add(new byte[] { (byte) 0x01 });

    testLengths.add(0x7F); // The largest 1-byte length encoding.
    testEncodedLengths.add(new byte[] { (byte) 0x7F });

    testLengths.add(0x80); // The smallest length that must use 2
    // bytes.
    testEncodedLengths.add(new byte[] { (byte) 0x81, (byte) 0x80 });

    testLengths.add(0xFF); // The largest length that may use 2 bytes.
    testEncodedLengths.add(new byte[] { (byte) 0x81, (byte) 0xFF });

    testLengths.add(0x0100); // The smallest length that must use 3
    // bytes.
    testEncodedLengths.add(new byte[] { (byte) 0x82, (byte) 0x01,
        (byte) 0x00 });

    testLengths.add(0xFFFF); // The largest length that may use 3
    // bytes.
    testEncodedLengths.add(new byte[] { (byte) 0x82, (byte) 0xFF,
        (byte) 0xFF });

    testLengths.add(0x010000); // The smallest length that must use 4
    // bytes.
    testEncodedLengths.add(new byte[] { (byte) 0x83, (byte) 0x01,
        (byte) 0x00, (byte) 0x00 });

    // Initialize the set of integer values and their pre-encoded
    // representations. These can get big since they will not be used to
    // create
    // arrays. Also, there is no need to test negative values since LDAP
    // doesn't make use of them.
    testIntegers = new ArrayList<Integer>();
    testEncodedIntegers = new ArrayList<byte[]>();

    testIntegers.add(0x00); // A common 1-byte encoding.
    testEncodedIntegers.add(new byte[] { (byte) 0x00 });

    testIntegers.add(0x7F); // The largest 1-byte encoding.
    testEncodedIntegers.add(new byte[] { (byte) 0x7F });

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

    // Initialize the sets of ASN.1 elements that will be used in
    // testing the
    // group encode/decode operations.
    testElementSets = new ArrayList<ArrayList<ASN1Element>>();
    testEncodedElementSets = new ArrayList<byte[]>();

    testElementSets.add(null); // The null set.
    testEncodedElementSets.add(new byte[0]);

    testElementSets.add(new ArrayList<ASN1Element>(0)); // The empty
    // set.
    testEncodedElementSets.add(new byte[0]);

    // Sets containing from 1 to 10 elements.
    for (int i = 1; i <= 10; i++) {
      ArrayList<ASN1Element> elements = new ArrayList<ASN1Element>(i);

      for (int j = 0; j < i; j++) {
        elements.add(new ASN1Element((byte) 0x00));
      }
      testElementSets.add(elements);
      testEncodedElementSets.add(new byte[i * 2]);
    }
  }

  /**
   * Tests the <CODE>getType</CODE> method.
   */
  @Test()
  public void testGetType() {
    for (byte type : testTypes) {
      ASN1Element element = new ASN1Element(type);

      assertEquals(type, element.getType());

      for (byte[] value : testValues) {
        element = new ASN1Element(type, value);

        assertEquals(type, element.getType());
      }
    }
  }

  /**
   * Tests the <CODE>setType</CODE> method.
   */
  @Test()
  public void testSetType() {
    ASN1Element element = new ASN1Element((byte) 0x00);
    for (byte type : testTypes) {
      element.setType(type);

      assertEquals(type, element.getType());
    }
  }

  /**
   * Tests the <CODE>isUniversal</CODE> method.
   */
  @Test()
  public void testIsUniversal() {
    ASN1Element element = new ASN1Element((byte) 0x00);
    for (byte type : testTypes) {
      element.setType(type);
      boolean isUniversal = (((byte) (type & 0xC0)) == ((byte) 0x00));

      assertEquals(isUniversal, element.isUniversal());
    }
  }

  /**
   * Tests the <CODE>isApplicationSpecific</CODE> method.
   */
  @Test()
  public void testIsApplicationSpecific() {
    ASN1Element element = new ASN1Element((byte) 0x00);
    for (byte type : testTypes) {
      element.setType(type);
      boolean isApplicationSpecific = (((byte) (type & 0xC0)) == ((byte) 0x40));

      assertEquals(isApplicationSpecific, element.isApplicationSpecific());
    }
  }

  /**
   * Tests the <CODE>isContextSpecific</CODE> method.
   */
  @Test()
  public void testIsContextSpecific() {
    ASN1Element element = new ASN1Element((byte) 0x00);
    for (byte type : testTypes) {
      element.setType(type);
      boolean isContextSpecific = (((byte) (type & 0xC0)) == ((byte) 0x80));

      assertEquals(isContextSpecific, element.isContextSpecific());
    }
  }

  /**
   * Tests the <CODE>isPrivate</CODE> method.
   */
  @Test()
  public void testIsPrivate() {
    ASN1Element element = new ASN1Element((byte) 0x00);
    for (byte type : testTypes) {
      element.setType(type);
      boolean isPrivate = (((byte) (type & 0xC0)) == ((byte) 0xC0));

      assertEquals(isPrivate, element.isPrivate());
    }
  }

  /**
   * Tests the <CODE>isPrimitive</CODE> method.
   */
  @Test()
  public void testIsPrimitive() {
    ASN1Element element = new ASN1Element((byte) 0x00);
    for (byte type : testTypes) {
      element.setType(type);
      boolean isPrimitive = (((byte) (type & 0x20)) == ((byte) 0x00));

      assertEquals(isPrimitive, element.isPrimitive());
    }
  }

  /**
   * Tests the <CODE>isConstructed</CODE> method.
   */
  @Test()
  public void testIsConstructed() {
    ASN1Element element = new ASN1Element((byte) 0x00);
    for (byte type : testTypes) {
      element.setType(type);
      boolean isConstructed = (((byte) (type & 0x20)) == ((byte) 0x20));

      assertEquals(isConstructed, element.isConstructed());
    }
  }

  /**
   * Tests the <CODE>getValue</CODE> method.
   */
  @Test()
  public void testGetValue() {
    for (byte type : testTypes) {
      ASN1Element element = new ASN1Element(type);

      assertTrue(Arrays.equals(new byte[0], element.value()));

      for (byte[] value : testValues) {
        element = new ASN1Element(type, value);

        if (value == null) {
          assertTrue(Arrays.equals(new byte[0], element.value()));
        } else {
          assertTrue(Arrays.equals(value, element.value()));
        }
      }
    }
  }

  /**
   * Tests the <CODE>setValue</CODE> method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test()
  public void testSetValue() throws Exception {
    ASN1Element element = new ASN1Element((byte) 0x00);

    for (byte[] value : testValues) {
      element.setValue(value);
      if (value == null) {
        assertTrue(Arrays.equals(new byte[0], element.value()));
      } else {
        assertTrue(Arrays.equals(value, element.value()));
      }
    }
  }

  /**
   * Tests the <CODE>encodeLength</CODE> method.
   */
  @Test()
  public void testEncodeLength() {
    int numLengths = testLengths.size();
    for (int i = 0; i < numLengths; i++) {
      int length = testLengths.get(i);
      byte[] encodedLength = testEncodedLengths.get(i);

      assertTrue(Arrays.equals(encodedLength, ASN1Element
          .encodeLength(length)));
    }
  }

  /**
   * Tests the <CODE>encode</CODE> and <CODE>decode</CODE> methods.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test()
  public void testEncodeAndDecode() throws Exception {
    for (byte type : testTypes) {
      for (byte[] value : testValues) {
        int length;
        byte[] encodedLength;
        if (value == null) {
          length = 0;
          encodedLength = new byte[] { (byte) 0x00 };
        } else {
          length = value.length;
          encodedLength = ASN1Element.encodeLength(length);
        }

        byte[] encodedElement = new byte[1 + length + encodedLength.length];
        encodedElement[0] = type;
        System.arraycopy(encodedLength, 0, encodedElement, 1,
            encodedLength.length);
        if (value != null) {
          System.arraycopy(value, 0, encodedElement,
              1 + encodedLength.length, length);
        }

        ASN1Element element = new ASN1Element(type, value);

        assertTrue(Arrays.equals(encodedElement, element.encode()));

        assertTrue(element.equals(ASN1Element.decode(encodedElement)));
      }

      int numLengths = testLengths.size();
      for (int i = 0; i < numLengths; i++) {
        int length = testLengths.get(i);
        byte[] encodedLength = testEncodedLengths.get(i);
        byte[] value = new byte[length];

        byte[] encodedElement = new byte[1 + length + encodedLength.length];
        encodedElement[0] = type;
        System.arraycopy(encodedLength, 0, encodedElement, 1,
            encodedLength.length);

        ASN1Element element = new ASN1Element(type, value);

        assertTrue(Arrays.equals(encodedElement, element.encode()));

        assertTrue(element.equals(ASN1Element.decode(encodedElement)));
      }
    }
  }

  /**
   * Tests the <CODE>encodeValue</CODE> method with a single boolean
   * argument.
   */
  @Test()
  public void testEncodeBooleanValue() {
    byte[] encodedFalse = new byte[] { (byte) 0x00 };
    byte[] encodedTrue = new byte[] { (byte) 0xFF };

    assertTrue(Arrays.equals(encodedFalse, ASN1Element.encodeValue(false)));

    assertTrue(Arrays.equals(encodedTrue, ASN1Element.encodeValue(true)));
  }

  /**
   * Tests the <CODE>encodeValue</CODE> method with a single int
   * argument.
   */
  @Test()
  public void testEncodeIntValue() {
    int numIntValues = testIntegers.size();
    for (int i = 0; i < numIntValues; i++) {
      int intValue = testIntegers.get(i);
      byte[] encodedInt = testEncodedIntegers.get(i);

      assertTrue(Arrays.equals(encodedInt, ASN1Element
          .encodeValue(intValue)));
    }
  }

  /**
   * Tests the <CODE>encodeValue</CODE> method with a set of ASN.1
   * elements.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test()
  public void testEncodeAndDecodeElements() throws Exception {
    int numElementSets = testElementSets.size();
    for (int i = 0; i < numElementSets; i++) {
      ArrayList<ASN1Element> elementSet = testElementSets.get(i);
      byte[] encodedElementSet = testEncodedElementSets.get(i);

      assertTrue(Arrays.equals(encodedElementSet, ASN1Element
          .encodeValue(elementSet)));

      ArrayList<ASN1Element> decodedElementSet;
      decodedElementSet = ASN1Element.decodeElements(encodedElementSet);

      ArrayList<ASN1Element> compareSet;
      if (elementSet == null) {
        compareSet = new ArrayList<ASN1Element>(0);
      } else {
        compareSet = elementSet;
      }

      assertTrue(listsAreEqual(compareSet, decodedElementSet));
    }
  }

  /**
   * Tests the <CODE>decodeAsBoolean</CODE> method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test()
  public void testDecodeAsBoolean() throws Exception {
    for (int i = 0; i < 256; i++) {
      byte[] valueByte = new byte[] { (byte) i };
      ASN1Element element = new ASN1Element((byte) 0x00, valueByte);
      boolean booleanValue = (i != 0);

      assertEquals(booleanValue, element.decodeAsBoolean().booleanValue());

      element = new ASN1Element((byte) 0x01, valueByte);

      assertEquals(booleanValue, element.decodeAsBoolean().booleanValue());
    }
  }

  /**
   * Tests the <CODE>decodeAsEnumerated</CODE> method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test()
  public void testDecodeAsEnumerated() throws Exception {
    int numIntValues = testIntegers.size();
    for (int i = 0; i < numIntValues; i++) {
      int intValue = testIntegers.get(i);
      byte[] encodedInt = testEncodedIntegers.get(i);

      ASN1Element element = new ASN1Element((byte) 0x00, encodedInt);

      assertEquals(intValue, element.decodeAsEnumerated().intValue());

      element = new ASN1Element((byte) 0x0A, encodedInt);

      assertEquals(intValue, element.decodeAsEnumerated().intValue());
    }
  }

  /**
   * Tests the <CODE>decodeAsInteger</CODE> method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test()
  public void testDecodeAsInteger() throws Exception {
    int numIntValues = testIntegers.size();
    for (int i = 0; i < numIntValues; i++) {
      int intValue = testIntegers.get(i);
      byte[] encodedInt = testEncodedIntegers.get(i);

      ASN1Element element = new ASN1Element((byte) 0x00, encodedInt);

      assertEquals(intValue, element.decodeAsInteger().intValue());

      element = new ASN1Element((byte) 0x02, encodedInt);

      assertEquals(intValue, element.decodeAsInteger().intValue());
    }
  }

  /**
   * Tests the <CODE>decodeAsNull</CODE> method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test()
  public void testDecodeAsNull() throws Exception {
    for (byte type : testTypes) {
      ASN1Element element = new ASN1Element(type);
      ASN1Null nullElement = new ASN1Null(type);

      assertEquals(nullElement, element.decodeAsNull());
    }
  }

  /**
   * Tests the <CODE>decodeAsOctetString</CODE> method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test()
  public void testDecodeAsOctetString() throws Exception {
    for (byte[] value : testValues) {
      ASN1Element element = new ASN1Element((byte) 0x00, value);

      byte[] compareValue;
      if (value == null) {
        compareValue = new byte[0];
      } else {
        compareValue = value;
      }

      assertTrue(Arrays.equals(compareValue, element.decodeAsOctetString()
          .value()));

      element = new ASN1Element((byte) 0x04, value);

      assertTrue(Arrays.equals(compareValue, element.decodeAsOctetString()
          .value()));
    }
  }

  /**
   * Tests the <CODE>decodeAsSequence</CODE> method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test()
  public void testDecodeAsSequence() throws Exception {
    int numElementSets = testElementSets.size();
    for (int i = 0; i < numElementSets; i++) {
      ArrayList<ASN1Element> elementSet = testElementSets.get(i);
      byte[] encodedElementSet = testEncodedElementSets.get(i);

      ArrayList<ASN1Element> compareList;
      if (elementSet == null) {
        compareList = new ArrayList<ASN1Element>(0);
      } else {
        compareList = elementSet;
      }

      ASN1Element element = new ASN1Element((byte) 0x00, encodedElementSet);

      assertTrue(listsAreEqual(compareList, element.decodeAsSequence()
          .elements()));

      element = new ASN1Element((byte) 0x30, encodedElementSet);

      assertTrue(listsAreEqual(compareList, element.decodeAsSequence()
          .elements()));
    }
  }

  /**
   * Tests the <CODE>decodeAsSet</CODE> method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test()
  public void testDecodeAsSet() throws Exception {
    int numElementSets = testElementSets.size();
    for (int i = 0; i < numElementSets; i++) {
      ArrayList<ASN1Element> elementSet = testElementSets.get(i);
      byte[] encodedElementSet = testEncodedElementSets.get(i);

      ArrayList<ASN1Element> compareList;
      if (elementSet == null) {
        compareList = new ArrayList<ASN1Element>(0);
      } else {
        compareList = elementSet;
      }

      ASN1Element element = new ASN1Element((byte) 0x00, encodedElementSet);

      assertTrue(listsAreEqual(compareList, element.decodeAsSet()
          .elements()));

      element = new ASN1Element((byte) 0x31, encodedElementSet);

      assertTrue(listsAreEqual(compareList, element.decodeAsSet()
          .elements()));
    }
  }

  /**
   * Tests the <CODE>equals</CODE> and <CODE>hashCode</CODE>
   * methods.
   */
  @Test()
  public void testEqualsAndHashCode() {
    // Perform simple tests for two basic elements that should be the
    // same, one
    // that should differ in type, one that should differ in value, and
    // one that
    // should differ in both.
    ASN1Element e1 = new ASN1Element((byte) 0x00);
    ASN1Element e2 = new ASN1Element((byte) 0x00, new byte[0]);
    ASN1Element e3 = new ASN1Element((byte) 0x01);
    ASN1Element e4 = new ASN1Element((byte) 0x00,
        new byte[] { (byte) 0x00 });
    ASN1Element e5 = new ASN1Element((byte) 0x01,
        new byte[] { (byte) 0x00 });
    ASN1Element e6 = new ASN1Element((byte) 0x00,
        new byte[] { (byte) 0x01 });

    assertTrue(e1.equals(e2)); // Basic equality test.

    assertTrue(e2.equals(e1)); // Reflexive equality test.

    assertFalse(e1.equals(e3)); // Difference in type.

    assertFalse(e1.equals(e4)); // Difference in value.

    assertFalse(e1.equals(e5)); // Differences in type and value.

    assertFalse(e4.equals(e6)); // Difference in values with the same
    // length.

    // Make sure that equal elements have equal hash codes.
    assertEquals(e1.hashCode(), e2.hashCode()); // Hash code equality
    // test.

    // Test equals against a null element.
    assertFalse(e1.equals(null));

    // Test boolean elements against equivalent generic elements.
    ASN1Element trueElement = new ASN1Element((byte) 0x01,
        new byte[] { (byte) 0xFF });
    ASN1Element falseElement = new ASN1Element((byte) 0x01,
        new byte[] { (byte) 0x00 });
    ASN1Boolean trueBoolean = new ASN1Boolean(true);
    ASN1Boolean falseBoolean = new ASN1Boolean(false);

    assertTrue(trueElement.equals(trueBoolean));

    assertTrue(trueBoolean.equals(trueElement));

    assertEquals(trueElement.hashCode(), trueBoolean.hashCode());

    assertTrue(falseElement.equals(falseBoolean));

    assertTrue(falseBoolean.equals(falseElement));

    assertEquals(falseElement.hashCode(), falseBoolean.hashCode());

    // Test integer elements against equivalent generic elements.
    int numIntegers = testIntegers.size();
    for (int i = 0; i < numIntegers; i++) {
      int intValue = testIntegers.get(i);
      byte[] encodedIntValue = testEncodedIntegers.get(i);

      ASN1Element genericElement = new ASN1Element((byte) 0x02,
          encodedIntValue);
      ASN1Integer integerElement = new ASN1Integer(intValue);

      assertTrue(genericElement.equals(integerElement));

      assertTrue(integerElement.equals(genericElement)); // Reflexive
      // test.

      // Test for matching hash codes.
      assertEquals(genericElement.hashCode(), integerElement.hashCode());
    }
  }
}
