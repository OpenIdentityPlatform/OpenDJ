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

import org.testng.annotations.Test;

/**
 * This class defines a set of tests for the
 * org.opends.server.protocols.asn1.ASN1Null class.
 */
public class TestASN1Null extends ASN1TestCase {
  /**
   * Tests the <CODE>setValue</CODE> method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test()
  public void testSetValue() throws Exception {
    ASN1Null element = new ASN1Null();

    // Test with a null array.
    element.setValue(null);

    // Test with an empty array.
    element.setValue(new byte[0]);
  }

  /**
   * Tests the <CODE>decodeAsNull</CODE> method that takes an ASN.1
   * element argument.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test()
  public void testDecodeElementAsNull() throws Exception {
    // Test with a type of 0x00.
    ASN1Element element = new ASN1Element((byte) 0x00);

    ASN1Null.decodeAsNull(element);

    // Test with a type of 0x05.
    element = new ASN1Element((byte) 0x05);
    ASN1Null.decodeAsNull(element);
  }

  /**
   * Tests the <CODE>decodeAsNull</CODE> method that takes a byte
   * array argument.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test()
  public void testDecodeBytesAsNull() throws Exception {
    byte[] encodedElement = new byte[] { (byte) 0x00, (byte) 0x00 };

    // Test with all possible type representations.
    for (int i = 0; i < 256; i++) {
      byte type = (byte) (i & 0xFF);
      encodedElement[0] = type;

      ASN1Null.decodeAsNull(encodedElement);
    }
  }
}
