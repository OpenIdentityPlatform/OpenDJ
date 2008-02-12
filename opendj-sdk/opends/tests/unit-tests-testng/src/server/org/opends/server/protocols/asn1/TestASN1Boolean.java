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



import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.*;



/**
 * This class defines a set of tests for the
 * org.opends.server.protocols.asn1.ASN1Boolean class.
 */
public class TestASN1Boolean
       extends ASN1TestCase
{
  /**
   * Retrieves the set of boolean values that may be used for testing.
   *
   * @return  The set of boolean values that may be used for testing.
   */
  @DataProvider(name = "booleanValues")
  public Object[][] getBooleanValues()
  {
    return new Object[][]
    {
      new Object[] { false },
      new Object[] { true }
    };
  }



  /**
   * Tests the first constructor, which takes a single boolean argument.
   *
   * @param  b  The boolean value to use in the test.
   */
  @Test(dataProvider = "booleanValues")
  public void testConstructor1(boolean b)
  {
    new ASN1Boolean(b);
  }



  /**
   * Tests the second constructor, which takes byte and boolean arguments.
   *
   * @param  b  The boolean value to use in the test.
   */
  @Test(dataProvider = "booleanValues")
  public void testConstructor2(boolean b)
  {
    new ASN1Boolean((byte) 0x50, b);
  }



  /**
   * Tests the <CODE>booleanValue</CODE> method.
   *
   * @param  b  The boolean value to use in the test.
   */
  @Test(dataProvider = "booleanValues")
  public void testBooleanValue(boolean b)
  {
    assertEquals(new ASN1Boolean(b).booleanValue(), b);
  }



  /**
   * Tests the <CODE>setValue</CODE> method that takes a boolean argument.
   *
   * @param  b  The boolean value to use in the test.
   */
  @Test(dataProvider = "booleanValues")
  public void testSetBooleanValue(boolean b)
  {
    ASN1Boolean booleanElement = new ASN1Boolean(!b);
    booleanElement.setValue(b);
    assertEquals(booleanElement.booleanValue(), b);
  }



  /**
   * Retrieves the set of byte array values that may be used for testing.
   *
   * @return  The set of byte array values that may be used for testing.
   */
  @DataProvider(name = "byteValues")
  public Object[][] getByteValues()
  {
    Object[][] array = new Object[256][1];
    for (int i=0; i < 256; i++)
    {
      array[i] = new Object[] { new byte[] { (byte) (i & 0xFF) } };
    }

    return array;
  }



  /**
   * Tests the <CODE>setValue</CODE> method that takes a byte array argument
   * with valid values.
   *
   * @param  b  The byte array to use in the test.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "byteValues")
  public void testSetValidByteValue(byte[] b)
         throws Exception
  {
    ASN1Boolean booleanElement = new ASN1Boolean(false);
    booleanElement.setValue(b);
    assertEquals(booleanElement.booleanValue(), (b[0] != 0x00));
  }



  /**
   * Tests the <CODE>setValue</CODE> method that takes a byte array argument
   * with a null value.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testSetNullByteValue()
         throws Exception
  {
    ASN1Boolean booleanElement = new ASN1Boolean(false);
    byte[] b = null;
    booleanElement.setValue(b);
  }



  /**
   * Tests the <CODE>setValue</CODE> method that takes a byte array argument
   * with an empty array value.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testSetZeroByteValue()
         throws Exception
  {
    ASN1Boolean booleanElement = new ASN1Boolean(false);
    byte[] b = new byte[0];
    booleanElement.setValue(b);
  }



  /**
   * Tests the <CODE>setValue</CODE> method that takes a byte array argument
   * with a multi-byte array value.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testSetMultiByteValue()
         throws Exception
  {
    ASN1Boolean booleanElement = new ASN1Boolean(false);
    byte[] b = new byte[2];
    booleanElement.setValue(b);
  }



  /**
   * Tests the <CODE>decodeAsBoolean</CODE> method that takes an ASN1Element
   * argument with valid elements.
   *
   * @param  b  The byte array to use for the element values.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "byteValues")
  public void testDecodeValidElementAsBoolean(byte[] b)
         throws Exception
  {
    // First, try with an actual boolean element.
    ASN1Element e = new ASN1Boolean(false);
    e.setValue(b);
    ASN1Boolean booleanElement = ASN1Boolean.decodeAsBoolean(e);
    assertEquals(booleanElement.booleanValue(), (b[0] != 0x00));

    e = new ASN1Boolean((byte) 0x50, false);
    e.setValue(b);
    booleanElement = ASN1Boolean.decodeAsBoolean(e);
    assertEquals(booleanElement.booleanValue(), (b[0] != 0x00));


    // Next, test with a generic ASN.1 element.
    e = new ASN1Element(ASN1Constants.UNIVERSAL_BOOLEAN_TYPE, b);
    booleanElement = ASN1Boolean.decodeAsBoolean(e);
    assertEquals(booleanElement.booleanValue(), (b[0] != 0x00));

    e = new ASN1Element((byte) 0x50, b);
    booleanElement = ASN1Boolean.decodeAsBoolean(e);
    assertEquals(booleanElement.booleanValue(), (b[0] != 0x00));
  }



  /**
   * Tests the <CODE>decodeAsBoolean</CODE> method that takes an ASN1Element
   * argument with a null element.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeNullElementAsBoolean()
         throws Exception
  {
    ASN1Element e = null;
    ASN1Boolean.decodeAsBoolean(e);
  }



  /**
   * Tests the <CODE>decodeAsBoolean</CODE> method that takes an ASN1Element
   * argument with a zero-byte element.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeZeroByteElementAsBoolean()
         throws Exception
  {
    ASN1Element e = new ASN1Element((byte) 0x50, new byte[0]);
    ASN1Boolean.decodeAsBoolean(e);
  }



  /**
   * Tests the <CODE>decodeAsBoolean</CODE> method that takes an ASN1Element
   * argument with a multi-byte element.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeMultiByteElementAsBoolean()
         throws Exception
  {
    ASN1Element e = new ASN1Element((byte) 0x50, new byte[2]);
    ASN1Boolean.decodeAsBoolean(e);
  }



  /**
   * Tests the <CODE>decodeAsBoolean</CODE> method that takes a byte array
   * argument with valid arrays.
   *
   * @param  b  The byte array to use for the element values.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "byteValues")
  public void testDecodeValidArrayAsBoolean(byte[] b)
         throws Exception
  {
    // First, test with the standard Boolean type.
    byte[] elementArray = new byte[] { 0x01, 0x01, b[0] };
    ASN1Boolean booleanElement = ASN1Boolean.decodeAsBoolean(elementArray);
    assertEquals(booleanElement.booleanValue(), (b[0] != 0x00));


    // Next, test with a nonstandard Boolean type.
    elementArray[0] = (byte) 0x50;
    booleanElement = ASN1Boolean.decodeAsBoolean(elementArray);
    assertEquals(booleanElement.booleanValue(), (b[0] != 0x00));
  }



  /**
   * Tests the <CODE>decodeAsBoolean</CODE> method that takes a byte array
   * argument with valid arrays using extended lengths.
   *
   * @param  b  The byte array to use for the element values.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "byteValues")
  public void testDecodeValidExtendedArrayAsBoolean(byte[] b)
         throws Exception
  {
    // First, test with the standard Boolean type.
    byte[] elementArray = new byte[] { 0x01, (byte) 0x81, 0x01, b[0] };
    ASN1Boolean booleanElement = ASN1Boolean.decodeAsBoolean(elementArray);
    assertEquals(booleanElement.booleanValue(), (b[0] != 0x00));


    // Next, test with a nonstandard Boolean type.
    elementArray[0] = (byte) 0x50;
    booleanElement = ASN1Boolean.decodeAsBoolean(elementArray);
    assertEquals(booleanElement.booleanValue(), (b[0] != 0x00));
  }



  /**
   * Tests the <CODE>decodeAsBoolean</CODE> method that takes a byte array
   * argument with a null array.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeNullArrayAsBoolean()
         throws Exception
  {
    byte[] b = null;
    ASN1Boolean.decodeAsBoolean(b);
  }



  /**
   * Tests the <CODE>decodeAsBoolean</CODE> method that takes a byte array
   * argument with a short array.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeShortArrayAsBoolean()
         throws Exception
  {
    byte[] b = new byte[1];
    ASN1Boolean.decodeAsBoolean(b);
  }



  /**
   * Tests the <CODE>decodeAsBoolean</CODE> method that takes a byte array
   * argument with an array that takes too many bytes to expressthe length.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeLongLengthArrayAsBoolean()
         throws Exception
  {
    byte[] b = { 0x01, (byte) 0x85, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00 };
    ASN1Boolean.decodeAsBoolean(b);
  }



  /**
   * Tests the <CODE>decodeAsBoolean</CODE> method that takes a byte array
   * argument with an array that doesn't contain a full length.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeTruncatedLengthArrayAsBoolean()
         throws Exception
  {
    byte[] b = { 0x01, (byte) 0x82, 0x00 };
    ASN1Boolean.decodeAsBoolean(b);
  }



  /**
   * Tests the <CODE>decodeAsBoolean</CODE> method that takes a byte array
   * argument with an array that has more bytes than indicated by the length.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeLengthMismatchArrayAsBoolean()
         throws Exception
  {
    byte[] b = { 0x01, 0x01, 0x00, 0x00 };
    ASN1Boolean.decodeAsBoolean(b);
  }



  /**
   * Tests the <CODE>decodeAsBoolean</CODE> method that takes a byte array
   * argument with an array that has an invalid number of bytes in the value.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeLongValueArrayAsBoolean()
         throws Exception
  {
    byte[] b = { 0x01, 0x02, 0x00, 0x00 };
    ASN1Boolean.decodeAsBoolean(b);
  }



  /**
   * Tests the first <CODE>toString</CODE> method which takes a string builder
   * argument.
   *
   * @param  b  The byte array to use as the element value.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "byteValues")
  public void testToString1(byte[] b)
         throws Exception
  {
    ASN1Boolean booleanElement = new ASN1Boolean(false);
    booleanElement.setValue(b);
    booleanElement.toString(new StringBuilder());
  }



  /**
   * Tests the second <CODE>toString</CODE> method which takes string builder
   * and integer arguments.
   *
   * @param  b  The byte array to use as the element value.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "byteValues")
  public void testToString2(byte[] b)
         throws Exception
  {
    ASN1Boolean booleanElement = new ASN1Boolean(false);
    booleanElement.setValue(b);
    booleanElement.toString(new StringBuilder(), 1);
  }
}

