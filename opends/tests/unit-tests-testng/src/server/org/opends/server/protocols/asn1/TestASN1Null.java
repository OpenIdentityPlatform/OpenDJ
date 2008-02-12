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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.protocols.asn1;



import org.testng.annotations.Test;



/**
 * This class defines a set of tests for the
 * org.opends.server.protocols.asn1.ASN1Null class.
 */
public class TestASN1Null
       extends ASN1TestCase
{
  /**
   * Tests the first constructor, which doesn't take any arguments.
   */
  @Test()
  public void testConstructor1()
  {
    new ASN1Null();
  }



  /**
   * Tests the second constructor, which takes a single byte argument.
   */
  @Test()
  public void testConstructor2()
  {
    for (int i=0; i < 254; i++)
    {
      new ASN1Null((byte) (i & 0xFF));
    }
  }



  /**
   * Tests the <CODE>setValue</CODE> method with a null argument.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSetNullValue()
         throws Exception
  {
    ASN1Null n = new ASN1Null();
    n.setValue(null);
  }



  /**
   * Tests the <CODE>setValue</CODE> method with an empty byte array argument.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSetEmptyValue()
         throws Exception
  {
    ASN1Null n = new ASN1Null();
    n.setValue(new byte[0]);
  }



  /**
   * Tests the <CODE>setValue</CODE> method with a non-empty byte array
   * argument.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testSetNonEmptyValue()
         throws Exception
  {
    ASN1Null n = new ASN1Null();
    n.setValue(new byte[1]);
  }



  /**
   * Tests the <CODE>decodeAsNull</CODE> method that takes an ASN1Element
   * argument with a null argument.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeNullElementAsNull()
         throws Exception
  {
    ASN1Element e = null;
    ASN1Null.decodeAsNull(e);
  }



  /**
   * Tests the <CODE>decodeAsNull</CODE> method that takes an ASN1Element
   * argument with an element with a zero-length value.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDecodeZeroLengthElementAsNull()
         throws Exception
  {
    ASN1Element e = new ASN1OctetString(new byte[0]);
    ASN1Null.decodeAsNull(e);
  }



  /**
   * Tests the <CODE>decodeAsNull</CODE> method that takes an ASN1Element
   * argument with an element with a nonzero-length value.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeNonZeroLengthElementAsNull()
         throws Exception
  {
    ASN1Element e = new ASN1OctetString(new byte[1]);
    ASN1Null.decodeAsNull(e);
  }



  /**
   * Tests the <CODE>decodeAsNull</CODE> method that takes a byte array argument
   * with a null array.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeNullArrayAsNull()
         throws Exception
  {
    byte[] b = null;
    ASN1Null.decodeAsNull(b);
  }



  /**
   * Tests the <CODE>decodeAsNull</CODE> method that takes a byte array argument
   * with a short array.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeShortArrayAsNull()
         throws Exception
  {
    byte[] b = new byte[1];
    ASN1Null.decodeAsNull(b);
  }



  /**
   * Tests the <CODE>decodeAsNull</CODE> method that takes a byte array argument
   * with an array with a long length.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeLongLengthArrayAsNull()
         throws Exception
  {
    byte[] b = new byte[] { 0x05, (byte) 0x85, 0x00, 0x00, 0x00, 0x00, 0x00 };
    ASN1Null.decodeAsNull(b);
  }



  /**
   * Tests the <CODE>decodeAsNull</CODE> method that takes a byte array argument
   * with an array with a truncated length.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeTruncatedLengthArrayAsNull()
         throws Exception
  {
    byte[] b = new byte[] { 0x05, (byte) 0x82, 0x00 };
    ASN1Null.decodeAsNull(b);
  }



  /**
   * Tests the <CODE>decodeAsNull</CODE> method that takes a byte array argument
   * with an array with a length mismatch.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeLengthMismatchArrayAsNull()
         throws Exception
  {
    byte[] b = new byte[] { 0x05, 0x00, 0x00 };
    ASN1Null.decodeAsNull(b);
  }



  /**
   * Tests the <CODE>decodeAsNull</CODE> method that takes a byte array argument
   * with an arry with a nonzero length.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeNonZeroLengthArrayAsNull()
         throws Exception
  {
    byte[] b = new byte[] { 0x05, 0x01, 0x00 };
    ASN1Null.decodeAsNull(b);
  }



  /**
   * Tests the <CODE>decodeAsNull</CODE> method that takes a byte array argument
   * with an arry with a zero length.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDecodeZeroLengthArrayAsNull()
         throws Exception
  {
    byte[] b = new byte[] { 0x05, 0x00 };
    ASN1Null.decodeAsNull(b);
  }



  /**
   * Tests the <CODE>decodeAsNull</CODE> method that takes a byte array argument
   * with an arry with a zero length that takes multiple bytes to encode.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDecodeExtendedZeroLengthArrayAsNull()
         throws Exception
  {
    byte[] b = new byte[] { 0x05, (byte) 0x81, 0x00 };
    ASN1Null.decodeAsNull(b);
  }



  /**
   * Tests the <CODE>toString</CODE> method that takes a string builder
   * argument.
   */
  @Test()
  public void testToString1()
  {
    new ASN1Null().toString(new StringBuilder());
  }



  /**
   * Tests the <CODE>toString</CODE> method that takes string builder and
   * integer arguments.
   */
  @Test()
  public void testToString2()
  {
    new ASN1Null().toString(new StringBuilder(), 1);
  }
}

