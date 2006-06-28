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
import static org.testng.AssertJUnit.assertTrue;

import java.util.ArrayList;

import org.testng.annotations.Configuration;
import org.testng.annotations.Test;

/**
 * This class defines a set of tests for the
 * org.opends.server.protocols.asn1.ASN1Sequence class.
 */
public class TestASN1Sequence extends ASN1TestCase {
  // The sets of pre-encoded ASN.1 elements that will be included in the
  // test
  // case.
  private ArrayList<ArrayList<ASN1Element>> testElementSets;

  // The set of pre-encoded element sets that will be used in the test
  // cases.
  private ArrayList<byte[]> testEncodedElementSets;

  /**
   * Performs any necessary initialization for this test case.
   */
  @Configuration(beforeTestMethod = true)
  public void setUp() {
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

    // Sets containing from 1 to 10 zero-length elements.
    for (int i = 1; i <= 10; i++) {
      ArrayList<ASN1Element> elements = new ArrayList<ASN1Element>(i);

      for (int j = 0; j < i; j++) {
        elements.add(new ASN1Element((byte) 0x00));
      }
      testElementSets.add(elements);
      testEncodedElementSets.add(new byte[i * 2]);
    }

    // Sets containing from 1 to 10 1-byte-length elements.
    for (int i = 1; i <= 10; i++) {
      ArrayList<ASN1Element> elements = new ArrayList<ASN1Element>(i);

      byte[] encodedElements = new byte[i * 3];
      for (int j = 0; j < i; j++) {
        elements.add(new ASN1Element((byte) 0x00, new byte[1]));
        encodedElements[(j * 3) + 1] = (byte) 0x01;
      }

      testElementSets.add(elements);
      testEncodedElementSets.add(encodedElements);
    }

    // Sets containing from 1 to 10 127-byte-length elements.
    for (int i = 1; i <= 10; i++) {
      ArrayList<ASN1Element> elements = new ArrayList<ASN1Element>(i);

      byte[] encodedElements = new byte[i * 129];
      for (int j = 0; j < i; j++) {
        elements.add(new ASN1Element((byte) 0x00, new byte[127]));
        encodedElements[(j * 129) + 1] = (byte) 0x7F;
      }

      testElementSets.add(elements);
      testEncodedElementSets.add(encodedElements);
    }

    // Sets containing from 1 to 10 128-byte-length elements.
    for (int i = 1; i <= 10; i++) {
      ArrayList<ASN1Element> elements = new ArrayList<ASN1Element>(i);

      byte[] encodedElements = new byte[i * 131];
      for (int j = 0; j < i; j++) {
        elements.add(new ASN1Element((byte) 0x00, new byte[128]));
        encodedElements[(j * 131) + 1] = (byte) 0x81;
        encodedElements[(j * 131) + 2] = (byte) 0x80;
      }

      testElementSets.add(elements);
      testEncodedElementSets.add(encodedElements);
    }
  }

  /**
   * Tests the <CODE>elements</CODE> method.
   */
  @Test()
  public void testElements() {
    int numElementSets = testElementSets.size();
    for (int i = 0; i < numElementSets; i++) {
      ArrayList<ASN1Element> elementSet = testElementSets.get(i);

      ArrayList<ASN1Element> compareList;
      if (elementSet == null) {
        compareList = new ArrayList<ASN1Element>(0);
      } else {
        compareList = elementSet;
      }

      ASN1Sequence element = new ASN1Sequence(elementSet);

      assertTrue(listsAreEqual(compareList, element.elements()));
    }
  }

  /**
   * Tests the <CODE>setElements</CODE> method.
   */
  @Test()
  public void testSetElements() {
    ASN1Sequence element = new ASN1Sequence();

    int numElementSets = testElementSets.size();
    for (int i = 0; i < numElementSets; i++) {
      ArrayList<ASN1Element> elementSet = testElementSets.get(i);

      ArrayList<ASN1Element> compareList;
      if (elementSet == null) {
        compareList = new ArrayList<ASN1Element>(0);
      } else {
        compareList = elementSet;
      }

      element.setElements(elementSet);

      assertTrue(listsAreEqual(compareList, element.elements()));
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
    ASN1Sequence element = new ASN1Sequence();

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

      element.setValue(encodedElementSet);

      assertTrue(listsAreEqual(compareList, element.elements()));
    }
  }

  /**
   * Tests the <CODE>decodeAsSequence</CODE> method that takes an
   * ASN.1 element argument.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test()
  public void testDecodeElementAsSequence() throws Exception {
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

      assertTrue(listsAreEqual(compareList, ASN1Sequence.decodeAsSequence(
          element).elements()));
    }
  }

  /**
   * Tests the <CODE>decodeAsSequence</CODE> method that takes a byte
   * array argument.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test()
  public void testDecodeBytesAsSequence() throws Exception {
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

      byte[] encodedLength = ASN1Element
          .encodeLength(encodedElementSet.length);
      byte[] encodedElement = new byte[1 + encodedLength.length
          + encodedElementSet.length];

      encodedElement[0] = 0x00;
      System.arraycopy(encodedLength, 0, encodedElement, 1,
          encodedLength.length);
      System.arraycopy(encodedElementSet, 0, encodedElement,
          1 + encodedLength.length, encodedElementSet.length);

      assertTrue(listsAreEqual(compareList, ASN1Sequence.decodeAsSequence(
          encodedElement).elements()));
    }
  }
}
