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



import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.*;



/**
 * This class defines a set of tests for the
 * org.opends.server.protocols.asn1.ASN1Integer class.
 */
public class TestASN1Integer
       extends ASN1TestCase
{
  /**
   * Retrieves the set of int values that should be used for testing.
   *
   * @return  The set of int values that should be used for testing.
   */
  @DataProvider(name = "intValues")
  public Object[][] getIntValues()
  {
    return new Object[][]
    {
      new Object[] { 0x00000000 },
      new Object[] { 0x00000001 },
      new Object[] { 0x0000000F },
      new Object[] { 0x00000010 },
      new Object[] { 0x0000007F },
      new Object[] { 0x00000080 },
      new Object[] { 0x000000FF },
      new Object[] { 0x00000100 },
      new Object[] { 0x00000FFF },
      new Object[] { 0x00001000 },
      new Object[] { 0x0000FFFF },
      new Object[] { 0x00010000 },
      new Object[] { 0x000FFFFF },
      new Object[] { 0x00100000 },
      new Object[] { 0x00FFFFFF },
      new Object[] { 0x01000000 },
      new Object[] { 0x0FFFFFFF },
      new Object[] { 0x10000000 },
      new Object[] { 0x7FFFFFFF },
      new Object[] { -0x00000001 },
      new Object[] { -0x0000000F },
      new Object[] { -0x00000010 },
      new Object[] { -0x0000007F },
      new Object[] { -0x00000080 },
      new Object[] { -0x000000FF },
      new Object[] { -0x00000100 },
      new Object[] { -0x00000FFF },
      new Object[] { -0x00001000 },
      new Object[] { -0x0000FFFF },
      new Object[] { -0x00010000 },
      new Object[] { -0x000FFFFF },
      new Object[] { -0x00100000 },
      new Object[] { -0x00FFFFFF },
      new Object[] { -0x01000000 },
      new Object[] { -0x0FFFFFFF },
      new Object[] { -0x10000000 },
      new Object[] { -0x7FFFFFFF },
      new Object[] { 0x80000000 }
    };
  }



  /**
   * Tests the first constructor, which takes a single integer argument.
   *
   * @param  i  The integer value to use to create the element.
   */
  @Test(dataProvider = "intValues")
  public void testConstructor1(int i)
  {
    new ASN1Integer(i);
  }



  /**
   * Tests the second constructor, which takes byte and integer arguments.
   *
   * @param  i  The integer value to use to create the element.
   */
  @Test(dataProvider = "intValues")
  public void testConstructor2(int i)
  {
    new ASN1Integer((byte) 0x50, i);
  }



  /**
   * Tests the <CODE>intValue</CODE> method.
   *
   * @param  i  The integer value to use for the test.
   */
  @Test(dataProvider = "intValues")
  public void testIntValue(int i)
  {
    assertEquals(i, new ASN1Integer(i).intValue());
  }



  /**
   * Tests the <CODE>setValue</CODE> method that takes an int argument.
   *
   * @param  i  The integer value to use for the test.
   */
  @Test(dataProvider = "intValues")
  public void testSetIntValue(int i)
  {
    ASN1Integer integerElement = new ASN1Integer(0);
    integerElement.setValue(i);
    assertEquals(i, integerElement.intValue());
  }



  /**
   * Tests the <CODE>setValue</CODE> method that takes a byte array argument
   * with a valid array.
   *
   * @param  i  The integer value to use for the test.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "intValues")
  public void testSetByteValue(int i)
         throws Exception
  {
    ASN1Integer integerElement = new ASN1Integer(0);

    byte[] encoding;
    if ((i & 0x7F) == i)
    {
      encoding = new byte[1];
      encoding[0] = (byte) (i & 0xFF);
    }
    else if ((i & 0x7FFF) == i)
    {
      encoding = new byte[2];
      encoding[0] = (byte) ((i >> 8) & 0xFF);
      encoding[1] = (byte) (i & 0xFF);
    }
    else if ((i & 0x7FFFFF) == i)
    {
      encoding = new byte[3];
      encoding[0] = (byte) ((i >> 16) & 0xFF);
      encoding[1] = (byte) ((i >> 8) & 0xFF);
      encoding[2] = (byte) (i & 0xFF);
    }
    else
    {
      encoding = new byte[4];
      encoding[0] = (byte) ((i >> 24) & 0xFF);
      encoding[1] = (byte) ((i >> 16) & 0xFF);
      encoding[2] = (byte) ((i >> 8) & 0xFF);
      encoding[3] = (byte) (i & 0xFF);
    }

    integerElement.setValue(encoding);
    assertEquals(i, integerElement.intValue());
  }



  /**
   * Tests the <CODE>setValue</CODE> method that takes a byte array argument
   * with a null array.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testSetByteValueNull()
         throws Exception
  {
    ASN1Integer integerElement = new ASN1Integer(0);

    byte[] b = null;
    integerElement.setValue(b);
  }



  /**
   * Tests the <CODE>setValue</CODE> method that takes a byte array argument
   * with an empty array.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testSetByteValueEmptyArray()
         throws Exception
  {
    ASN1Integer integerElement = new ASN1Integer(0);

    byte[] b = new byte[0];
    integerElement.setValue(b);
  }



  /**
   * Tests the <CODE>setValue</CODE> method that takes a byte array argument
   * with a long array.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testSetByteValueLongArray()
         throws Exception
  {
    ASN1Integer integerElement = new ASN1Integer(0);

    byte[] b = new byte[5];
    integerElement.setValue(b);
  }



  /**
   * Tests the <CODE>decodeAsInteger</CODE> method that takes an ASN1Element
   * arguent using a valid value.
   *
   * @param i  The integer value to use in the test.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "intValues")
  public void testDecodeValidElementAsInteger(int i)
         throws Exception
  {
    // First, make sure that we can decode an integer element as an integer.
    ASN1Element e = new ASN1Integer(i);
    ASN1Integer integerElement = ASN1Integer.decodeAsInteger(e);
    assertEquals(i, integerElement.intValue());

    e = new ASN1Integer((byte) 0x50, i);
    integerElement = ASN1Integer.decodeAsInteger(e);
    assertEquals(i, integerElement.intValue());


    // Next, make sure that we can decode a generic element as an integer.
    byte[] encoding;
    if ((i & 0x7F) == i)
    {
      encoding = new byte[1];
      encoding[0] = (byte) (i & 0xFF);
    }
    else if ((i & 0x7FFF) == i)
    {
      encoding = new byte[2];
      encoding[0] = (byte) ((i >> 8) & 0xFF);
      encoding[1] = (byte) (i & 0xFF);
    }
    else if ((i & 0x7FFFFF) == i)
    {
      encoding = new byte[3];
      encoding[0] = (byte) ((i >> 16) & 0xFF);
      encoding[1] = (byte) ((i >> 8) & 0xFF);
      encoding[2] = (byte) (i & 0xFF);
    }
    else
    {
      encoding = new byte[4];
      encoding[0] = (byte) ((i >> 24) & 0xFF);
      encoding[1] = (byte) ((i >> 16) & 0xFF);
      encoding[2] = (byte) ((i >> 8) & 0xFF);
      encoding[3] = (byte) (i & 0xFF);
    }

    e = new ASN1Element(ASN1Constants.UNIVERSAL_INTEGER_TYPE, encoding);
    integerElement = ASN1Integer.decodeAsInteger(e);
    assertEquals(i, integerElement.intValue());

    e = new ASN1Element((byte) 0x50, encoding);
    integerElement = ASN1Integer.decodeAsInteger(e);
    assertEquals(i, integerElement.intValue());
  }



  /**
   * Tests the <CODE>decodeAsInteger</CODE> method that takes an ASN1Element
   * arguent using a valid value.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeNullElementAsInteger()
         throws Exception
  {
    ASN1Element e = null;
    ASN1Integer.decodeAsInteger(e);
  }



  /**
   * Tests the <CODE>decodeAsInteger</CODE> method that takes an ASN1Element
   * arguent a zero-length element.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeZeroLengthElementAsInteger()
         throws Exception
  {
    ASN1Element e = new ASN1Element(ASN1Constants.UNIVERSAL_INTEGER_TYPE);
    ASN1Integer.decodeAsInteger(e);
  }



  /**
   * Tests the <CODE>decodeAsInteger</CODE> method that takes an ASN1Element
   * arguent a long value element.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeLongValueElementAsInteger()
         throws Exception
  {
    ASN1Element e = new ASN1Element(ASN1Constants.UNIVERSAL_INTEGER_TYPE,
                                    new byte[5]);
    ASN1Integer.decodeAsInteger(e);
  }



  /**
   * Tests the <CODE>decodeAsInteger</CODE> method that takes a byte array with
   * a valid array.
   *
   * @param i  The integer value to use in the test.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "intValues")
  public void testDecodeValidArrayAsInteger(int i)
         throws Exception
  {
    byte[] encoding;
    if ((i & 0x7F) == i)
    {
      encoding = new byte[1];
      encoding[0] = (byte) (i & 0xFF);
    }
    else if ((i & 0x7FFF) == i)
    {
      encoding = new byte[2];
      encoding[0] = (byte) ((i >> 8) & 0xFF);
      encoding[1] = (byte) (i & 0xFF);
    }
    else if ((i & 0x7FFFFF) == i)
    {
      encoding = new byte[3];
      encoding[0] = (byte) ((i >> 16) & 0xFF);
      encoding[1] = (byte) ((i >> 8) & 0xFF);
      encoding[2] = (byte) (i & 0xFF);
    }
    else
    {
      encoding = new byte[4];
      encoding[0] = (byte) ((i >> 24) & 0xFF);
      encoding[1] = (byte) ((i >> 16) & 0xFF);
      encoding[2] = (byte) ((i >> 8) & 0xFF);
      encoding[3] = (byte) (i & 0xFF);
    }

    byte[] encodedElement = new byte[2 + encoding.length];
    encodedElement[0] = ASN1Constants.UNIVERSAL_INTEGER_TYPE;
    encodedElement[1] = (byte) encoding.length;
    System.arraycopy(encoding, 0, encodedElement, 2, encoding.length);

    ASN1Integer integerElement = ASN1Integer.decodeAsInteger(encodedElement);
    assertEquals(i, integerElement.intValue());
  }



  /**
   * Tests the <CODE>decodeAsInteger</CODE> method that takes a byte array with
   * a valid extended length array.
   *
   * @param i  The integer value to use in the test.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "intValues")
  public void testDecodeValidExtendedLengthArrayAsInteger(int i)
         throws Exception
  {
    byte[] encoding;
    if ((i & 0x7F) == i)
    {
      encoding = new byte[1];
      encoding[0] = (byte) (i & 0xFF);
    }
    else if ((i & 0x7FFF) == i)
    {
      encoding = new byte[2];
      encoding[0] = (byte) ((i >> 8) & 0xFF);
      encoding[1] = (byte) (i & 0xFF);
    }
    else if ((i & 0x7FFFFF) == i)
    {
      encoding = new byte[3];
      encoding[0] = (byte) ((i >> 16) & 0xFF);
      encoding[1] = (byte) ((i >> 8) & 0xFF);
      encoding[2] = (byte) (i & 0xFF);
    }
    else
    {
      encoding = new byte[4];
      encoding[0] = (byte) ((i >> 24) & 0xFF);
      encoding[1] = (byte) ((i >> 16) & 0xFF);
      encoding[2] = (byte) ((i >> 8) & 0xFF);
      encoding[3] = (byte) (i & 0xFF);
    }

    byte[] encodedElement = new byte[3 + encoding.length];
    encodedElement[0] = ASN1Constants.UNIVERSAL_INTEGER_TYPE;
    encodedElement[1] = (byte) 0x81;
    encodedElement[2] = (byte) encoding.length;
    System.arraycopy(encoding, 0, encodedElement, 3, encoding.length);

    ASN1Integer integerElement = ASN1Integer.decodeAsInteger(encodedElement);
    assertEquals(i, integerElement.intValue());
  }



  /**
   * Tests the <CODE>decodeAsInteger</CODE> method that takes a byte array with
   * a null array.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeNullArrayAsInteger()
         throws Exception
  {
    byte[] b = null;
    ASN1Integer.decodeAsInteger(b);
  }



  /**
   * Tests the <CODE>decodeAsInteger</CODE> method that takes a byte array with
   * a short array.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeShortArrayAsInteger()
         throws Exception
  {
    byte[] b = new byte[0];
    ASN1Integer.decodeAsInteger(b);
  }



  /**
   * Tests the <CODE>decodeAsInteger</CODE> method that takes a byte array with
   * a long length array.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeLongLengthArrayAsInteger()
         throws Exception
  {
    byte[] b = { 0x02, (byte) 0x85, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00 };
    ASN1Integer.decodeAsInteger(b);
  }



  /**
   * Tests the <CODE>decodeAsInteger</CODE> method that takes a byte array with
   * a truncated length array.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeTruncatedLengthArrayAsInteger()
         throws Exception
  {
    byte[] b = { 0x02, (byte) 0x82, 0x00 };
    ASN1Integer.decodeAsInteger(b);
  }



  /**
   * Tests the <CODE>decodeAsInteger</CODE> method that takes a byte array with
   * a length mismatch.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeLengthMismatchArrayAsInteger()
         throws Exception
  {
    byte[] b = { 0x02, (byte) 0x81, 0x01 };
    ASN1Integer.decodeAsInteger(b);
  }



  /**
   * Tests the <CODE>decodeAsInteger</CODE> method that takes a byte array with
   * a value too long for an integer.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeLongIntLengthArrayAsInteger()
         throws Exception
  {
    byte[] b = { 0x02, 0x05, 0x00, 0x00, 0x00, 0x00, 0x00 };
    ASN1Integer.decodeAsInteger(b);
  }



  /**
   * Tests the first <CODE>toString</CODE> method that takes a string builder
   * argument.
   *
   * @param  i  The integer value to use in the test.
   */
  @Test(dataProvider = "intValues")
  public void testToString1(int i)
  {
    new ASN1Integer(i).toString(new StringBuilder());
  }



  /**
   * Tests the second <CODE>toString</CODE> method that takes string builder and
   * integer arguments.
   *
   * @param  i  The integer value to use in the test.
   */
  @Test(dataProvider = "intValues")
  public void testToString2(int i)
  {
    new ASN1Integer(i).toString(new StringBuilder(), 1);
  }
}

