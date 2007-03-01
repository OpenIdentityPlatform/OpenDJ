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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.protocols.asn1;



import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.*;



/**
 * This class defines a set of tests for the
 * org.opends.server.protocols.asn1.ASN1Long class.
 */
public class TestASN1Long
       extends ASN1TestCase
{
  /**
   * Retrieves the set of long values that should be used for testing.
   *
   * @return  The set of long values that should be used for testing.
   */
  @DataProvider(name = "longValues")
  public Object[][] getLongValues()
  {
    return new Object[][]
    {
      new Object[] { 0x0000000000000000L },
      new Object[] { 0x0000000000000001L },
      new Object[] { 0x000000000000007FL },
      new Object[] { 0x0000000000000080L },
      new Object[] { 0x00000000000000FFL },
      new Object[] { 0x0000000000000100L },
      new Object[] { 0x000000000000FFFFL },
      new Object[] { 0x0000000000010000L },
      new Object[] { 0x0000000000FFFFFFL },
      new Object[] { 0x0000000001000000L },
      new Object[] { 0x00000000FFFFFFFFL },
      new Object[] { 0x0000000100000000L },
      new Object[] { 0x000000FFFFFFFFFFL },
      new Object[] { 0x0000010000000000L },
      new Object[] { 0x0000FFFFFFFFFFFFL },
      new Object[] { 0x0001000000000000L },
      new Object[] { 0x00FFFFFFFFFFFFFFL },
      new Object[] { 0x0100000000000000L },
      new Object[] { 0x7FFFFFFFFFFFFFFFL },
      new Object[] { -0x0000000000000001L },
      new Object[] { -0x000000000000007FL },
      new Object[] { -0x0000000000000080L },
      new Object[] { -0x00000000000000FFL },
      new Object[] { -0x0000000000000100L },
      new Object[] { -0x000000000000FFFFL },
      new Object[] { -0x0000000000010000L },
      new Object[] { -0x0000000000FFFFFFL },
      new Object[] { -0x0000000001000000L },
      new Object[] { -0x00000000FFFFFFFFL },
      new Object[] { -0x0000000100000000L },
      new Object[] { -0x000000FFFFFFFFFFL },
      new Object[] { -0x0000010000000000L },
      new Object[] { -0x0000FFFFFFFFFFFFL },
      new Object[] { -0x0001000000000000L },
      new Object[] { -0x00FFFFFFFFFFFFFFL },
      new Object[] { -0x0100000000000000L },
      new Object[] { -0x7FFFFFFFFFFFFFFFL },
      new Object[] { 0x8000000000000000L }
    };
  }



  /**
   * Tests the first constructor, which takes a single long argument.
   *
   * @param  l  The long value to use to create the element.
   */
  @Test(dataProvider = "longValues")
  public void testConstructor1(long l)
  {
    new ASN1Long(l);
  }



  /**
   * Tests the second constructor, which takes byte and long arguments.
   *
   * @param  l  The long value to use to create the element.
   */
  @Test(dataProvider = "longValues")
  public void testConstructor2(long l)
  {
    new ASN1Long((byte) 0x50, l);
  }



  /**
   * Tests the <CODE>longValue</CODE> method.
   *
   * @param  l  The long value to use for the test.
   */
  @Test(dataProvider = "longValues")
  public void testLongValue(long l)
  {
    assertEquals(new ASN1Long(l).longValue(), l);
  }



  /**
   * Tests the <CODE>setValue</CODE> method that takes a long argument.
   *
   * @param  l  The long value to use for the test.
   */
  @Test(dataProvider = "longValues")
  public void testSetLongValue(long l)
  {
    ASN1Long longElement = new ASN1Long(0);
    longElement.setValue(l);
    assertEquals(longElement.longValue(), l);
  }



  /**
   * Tests the <CODE>setValue</CODE> method that takes a byte array argument
   * with a valid array.
   *
   * @param  l  The long value to use for the test.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "longValues")
  public void testSetByteValue(long l)
         throws Exception
  {
    ASN1Long longElement = new ASN1Long(0);

    byte[] encoding;
    if ((l & 0x7FL) == l)
    {
      encoding = new byte[1];
      encoding[0] = (byte) (l & 0xFF);
    }
    else if ((l & 0x7FFFL) == l)
    {
      encoding = new byte[2];
      encoding[0] = (byte) ((l >> 8) & 0xFF);
      encoding[1] = (byte) (l & 0xFF);
    }
    else if ((l & 0x7FFFFFL) == l)
    {
      encoding = new byte[3];
      encoding[0] = (byte) ((l >> 16) & 0xFF);
      encoding[1] = (byte) ((l >> 8) & 0xFF);
      encoding[2] = (byte) (l & 0xFF);
    }
    else if ((l & 0x7FFFFFFFL) == l)
    {
      encoding = new byte[4];
      encoding[0] = (byte) ((l >> 24) & 0xFF);
      encoding[1] = (byte) ((l >> 16) & 0xFF);
      encoding[2] = (byte) ((l >> 8) & 0xFF);
      encoding[3] = (byte) (l & 0xFF);
    }
    else if ((l & 0x7FFFFFFFFFL) == l)
    {
      encoding = new byte[5];
      encoding[0] = (byte) ((l >> 32) & 0xFF);
      encoding[1] = (byte) ((l >> 24) & 0xFF);
      encoding[2] = (byte) ((l >> 16) & 0xFF);
      encoding[3] = (byte) ((l >> 8) & 0xFF);
      encoding[4] = (byte) (l & 0xFF);
    }
    else if ((l & 0x7FFFFFFFFFFFL) == l)
    {
      encoding = new byte[6];
      encoding[0] = (byte) ((l >> 40) & 0xFF);
      encoding[1] = (byte) ((l >> 32) & 0xFF);
      encoding[2] = (byte) ((l >> 24) & 0xFF);
      encoding[3] = (byte) ((l >> 16) & 0xFF);
      encoding[4] = (byte) ((l >> 8) & 0xFF);
      encoding[5] = (byte) (l & 0xFF);
    }
    else if ((l & 0x7FFFFFFFFFFFFFL) == l)
    {
      encoding = new byte[7];
      encoding[0] = (byte) ((l >> 48) & 0xFF);
      encoding[1] = (byte) ((l >> 40) & 0xFF);
      encoding[2] = (byte) ((l >> 32) & 0xFF);
      encoding[3] = (byte) ((l >> 24) & 0xFF);
      encoding[4] = (byte) ((l >> 16) & 0xFF);
      encoding[5] = (byte) ((l >> 8) & 0xFF);
      encoding[6] = (byte) (l & 0xFF);
    }
    else
    {
      encoding = new byte[8];
      encoding[0] = (byte) ((l >> 56) & 0xFF);
      encoding[1] = (byte) ((l >> 48) & 0xFF);
      encoding[2] = (byte) ((l >> 40) & 0xFF);
      encoding[3] = (byte) ((l >> 32) & 0xFF);
      encoding[4] = (byte) ((l >> 24) & 0xFF);
      encoding[5] = (byte) ((l >> 16) & 0xFF);
      encoding[6] = (byte) ((l >> 8) & 0xFF);
      encoding[7] = (byte) (l & 0xFF);
    }

    longElement.setValue(encoding);
    assertEquals(longElement.longValue(), l);
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
    ASN1Long longElement = new ASN1Long(0);

    byte[] b = null;
    longElement.setValue(b);
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
    ASN1Long longElement = new ASN1Long(0);

    byte[] b = new byte[0];
    longElement.setValue(b);
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
    ASN1Long longElement = new ASN1Long(0);

    byte[] b = new byte[9];
    longElement.setValue(b);
  }



  /**
   * Tests the <CODE>decodeAsLong</CODE> method that takes an ASN1Element
   * arguent using a valid value.
   *
   * @param  l  The long value to use in the test.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "longValues")
  public void testDecodeValidElementAsLong(long l)
         throws Exception
  {
    // First, make sure that we can decode a long element as a long.
    ASN1Element e = new ASN1Long(l);
    ASN1Long longElement = ASN1Long.decodeAsLong(e);
    assertEquals(longElement.longValue(), l);

    e = new ASN1Long((byte) 0x50, l);
    longElement = ASN1Long.decodeAsLong(e);
    assertEquals(longElement.longValue(), l);


    // Next, make sure that we can decode a generic element as a long.
    byte[] encoding;
    if ((l & 0x7FL) == l)
    {
      encoding = new byte[1];
      encoding[0] = (byte) (l & 0xFF);
    }
    else if ((l & 0x7FFFL) == l)
    {
      encoding = new byte[2];
      encoding[0] = (byte) ((l >> 8) & 0xFF);
      encoding[1] = (byte) (l & 0xFF);
    }
    else if ((l & 0x7FFFFFL) == l)
    {
      encoding = new byte[3];
      encoding[0] = (byte) ((l >> 16) & 0xFF);
      encoding[1] = (byte) ((l >> 8) & 0xFF);
      encoding[2] = (byte) (l & 0xFF);
    }
    else if ((l & 0x7FFFFFFFL) == l)
    {
      encoding = new byte[4];
      encoding[0] = (byte) ((l >> 24) & 0xFF);
      encoding[1] = (byte) ((l >> 16) & 0xFF);
      encoding[2] = (byte) ((l >> 8) & 0xFF);
      encoding[3] = (byte) (l & 0xFF);
    }
    else if ((l & 0x7FFFFFFFFFL) == l)
    {
      encoding = new byte[5];
      encoding[0] = (byte) ((l >> 32) & 0xFF);
      encoding[1] = (byte) ((l >> 24) & 0xFF);
      encoding[2] = (byte) ((l >> 16) & 0xFF);
      encoding[3] = (byte) ((l >> 8) & 0xFF);
      encoding[4] = (byte) (l & 0xFF);
    }
    else if ((l & 0x7FFFFFFFFFFFL) == l)
    {
      encoding = new byte[6];
      encoding[0] = (byte) ((l >> 40) & 0xFF);
      encoding[1] = (byte) ((l >> 32) & 0xFF);
      encoding[2] = (byte) ((l >> 24) & 0xFF);
      encoding[3] = (byte) ((l >> 16) & 0xFF);
      encoding[4] = (byte) ((l >> 8) & 0xFF);
      encoding[5] = (byte) (l & 0xFF);
    }
    else if ((l & 0x7FFFFFFFFFFFFFL) == l)
    {
      encoding = new byte[7];
      encoding[0] = (byte) ((l >> 48) & 0xFF);
      encoding[1] = (byte) ((l >> 40) & 0xFF);
      encoding[2] = (byte) ((l >> 32) & 0xFF);
      encoding[3] = (byte) ((l >> 24) & 0xFF);
      encoding[4] = (byte) ((l >> 16) & 0xFF);
      encoding[5] = (byte) ((l >> 8) & 0xFF);
      encoding[6] = (byte) (l & 0xFF);
    }
    else
    {
      encoding = new byte[8];
      encoding[0] = (byte) ((l >> 56) & 0xFF);
      encoding[1] = (byte) ((l >> 48) & 0xFF);
      encoding[2] = (byte) ((l >> 40) & 0xFF);
      encoding[3] = (byte) ((l >> 32) & 0xFF);
      encoding[4] = (byte) ((l >> 24) & 0xFF);
      encoding[5] = (byte) ((l >> 16) & 0xFF);
      encoding[6] = (byte) ((l >> 8) & 0xFF);
      encoding[7] = (byte) (l & 0xFF);
    }

    e = new ASN1Element(ASN1Constants.UNIVERSAL_INTEGER_TYPE, encoding);
    longElement = ASN1Long.decodeAsLong(e);
    assertEquals(longElement.longValue(), l);

    e = new ASN1Element((byte) 0x50, encoding);
    longElement = ASN1Long.decodeAsLong(e);
    assertEquals(longElement.longValue(), l);
  }



  /**
   * Tests the <CODE>decodeAsLong</CODE> method that takes an ASN1Element
   * arguent using a valid value.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeNullElementAsLong()
         throws Exception
  {
    ASN1Element e = null;
    ASN1Long.decodeAsLong(e);
  }



  /**
   * Tests the <CODE>decodeAsLong</CODE> method that takes an ASN1Element
   * arguent a zero-length element.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeZeroLengthElementAsLong()
         throws Exception
  {
    ASN1Element e = new ASN1Element(ASN1Constants.UNIVERSAL_INTEGER_TYPE);
    ASN1Long.decodeAsLong(e);
  }



  /**
   * Tests the <CODE>decodeAsLong</CODE> method that takes an ASN1Element
   * arguent a long value element.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeLongValueElementAsLong()
         throws Exception
  {
    ASN1Element e = new ASN1Element(ASN1Constants.UNIVERSAL_INTEGER_TYPE,
                                    new byte[9]);
    ASN1Long.decodeAsLong(e);
  }



  /**
   * Tests the <CODE>decodeAsLong</CODE> method that takes a byte array with
   * a valid array.
   *
   * @param  l  The long value to use in the test.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "longValues")
  public void testDecodeValidArrayAsLong(long l)
         throws Exception
  {
    byte[] encoding;
    if ((l & 0x7FL) == l)
    {
      encoding = new byte[1];
      encoding[0] = (byte) (l & 0xFF);
    }
    else if ((l & 0x7FFFL) == l)
    {
      encoding = new byte[2];
      encoding[0] = (byte) ((l >> 8) & 0xFF);
      encoding[1] = (byte) (l & 0xFF);
    }
    else if ((l & 0x7FFFFFL) == l)
    {
      encoding = new byte[3];
      encoding[0] = (byte) ((l >> 16) & 0xFF);
      encoding[1] = (byte) ((l >> 8) & 0xFF);
      encoding[2] = (byte) (l & 0xFF);
    }
    else if ((l & 0x7FFFFFFFL) == l)
    {
      encoding = new byte[4];
      encoding[0] = (byte) ((l >> 24) & 0xFF);
      encoding[1] = (byte) ((l >> 16) & 0xFF);
      encoding[2] = (byte) ((l >> 8) & 0xFF);
      encoding[3] = (byte) (l & 0xFF);
    }
    else if ((l & 0x7FFFFFFFFFL) == l)
    {
      encoding = new byte[5];
      encoding[0] = (byte) ((l >> 32) & 0xFF);
      encoding[1] = (byte) ((l >> 24) & 0xFF);
      encoding[2] = (byte) ((l >> 16) & 0xFF);
      encoding[3] = (byte) ((l >> 8) & 0xFF);
      encoding[4] = (byte) (l & 0xFF);
    }
    else if ((l & 0x7FFFFFFFFFFFL) == l)
    {
      encoding = new byte[6];
      encoding[0] = (byte) ((l >> 40) & 0xFF);
      encoding[1] = (byte) ((l >> 32) & 0xFF);
      encoding[2] = (byte) ((l >> 24) & 0xFF);
      encoding[3] = (byte) ((l >> 16) & 0xFF);
      encoding[4] = (byte) ((l >> 8) & 0xFF);
      encoding[5] = (byte) (l & 0xFF);
    }
    else if ((l & 0x7FFFFFFFFFFFFFL) == l)
    {
      encoding = new byte[7];
      encoding[0] = (byte) ((l >> 48) & 0xFF);
      encoding[1] = (byte) ((l >> 40) & 0xFF);
      encoding[2] = (byte) ((l >> 32) & 0xFF);
      encoding[3] = (byte) ((l >> 24) & 0xFF);
      encoding[4] = (byte) ((l >> 16) & 0xFF);
      encoding[5] = (byte) ((l >> 8) & 0xFF);
      encoding[6] = (byte) (l & 0xFF);
    }
    else
    {
      encoding = new byte[8];
      encoding[0] = (byte) ((l >> 56) & 0xFF);
      encoding[1] = (byte) ((l >> 48) & 0xFF);
      encoding[2] = (byte) ((l >> 40) & 0xFF);
      encoding[3] = (byte) ((l >> 32) & 0xFF);
      encoding[4] = (byte) ((l >> 24) & 0xFF);
      encoding[5] = (byte) ((l >> 16) & 0xFF);
      encoding[6] = (byte) ((l >> 8) & 0xFF);
      encoding[7] = (byte) (l & 0xFF);
    }

    byte[] encodedElement = new byte[2 + encoding.length];
    encodedElement[0] = ASN1Constants.UNIVERSAL_INTEGER_TYPE;
    encodedElement[1] = (byte) encoding.length;
    System.arraycopy(encoding, 0, encodedElement, 2, encoding.length);

    ASN1Long longElement = ASN1Long.decodeAsLong(encodedElement);
    assertEquals(longElement.longValue(), l);
  }



  /**
   * Tests the <CODE>decodeAsLong</CODE> method that takes a byte array with
   * a valid extended length array.
   *
   * @param  l  The long value to use in the test.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "longValues")
  public void testDecodeValidExtendedLengthArrayAsLong(long l)
         throws Exception
  {
    byte[] encoding;
    if ((l & 0x7FL) == l)
    {
      encoding = new byte[1];
      encoding[0] = (byte) (l & 0xFF);
    }
    else if ((l & 0x7FFFL) == l)
    {
      encoding = new byte[2];
      encoding[0] = (byte) ((l >> 8) & 0xFF);
      encoding[1] = (byte) (l & 0xFF);
    }
    else if ((l & 0x7FFFFFL) == l)
    {
      encoding = new byte[3];
      encoding[0] = (byte) ((l >> 16) & 0xFF);
      encoding[1] = (byte) ((l >> 8) & 0xFF);
      encoding[2] = (byte) (l & 0xFF);
    }
    else if ((l & 0x7FFFFFFFL) == l)
    {
      encoding = new byte[4];
      encoding[0] = (byte) ((l >> 24) & 0xFF);
      encoding[1] = (byte) ((l >> 16) & 0xFF);
      encoding[2] = (byte) ((l >> 8) & 0xFF);
      encoding[3] = (byte) (l & 0xFF);
    }
    else if ((l & 0x7FFFFFFFFFL) == l)
    {
      encoding = new byte[5];
      encoding[0] = (byte) ((l >> 32) & 0xFF);
      encoding[1] = (byte) ((l >> 24) & 0xFF);
      encoding[2] = (byte) ((l >> 16) & 0xFF);
      encoding[3] = (byte) ((l >> 8) & 0xFF);
      encoding[4] = (byte) (l & 0xFF);
    }
    else if ((l & 0x7FFFFFFFFFFFL) == l)
    {
      encoding = new byte[6];
      encoding[0] = (byte) ((l >> 40) & 0xFF);
      encoding[1] = (byte) ((l >> 32) & 0xFF);
      encoding[2] = (byte) ((l >> 24) & 0xFF);
      encoding[3] = (byte) ((l >> 16) & 0xFF);
      encoding[4] = (byte) ((l >> 8) & 0xFF);
      encoding[5] = (byte) (l & 0xFF);
    }
    else if ((l & 0x7FFFFFFFFFFFFFL) == l)
    {
      encoding = new byte[7];
      encoding[0] = (byte) ((l >> 48) & 0xFF);
      encoding[1] = (byte) ((l >> 40) & 0xFF);
      encoding[2] = (byte) ((l >> 32) & 0xFF);
      encoding[3] = (byte) ((l >> 24) & 0xFF);
      encoding[4] = (byte) ((l >> 16) & 0xFF);
      encoding[5] = (byte) ((l >> 8) & 0xFF);
      encoding[6] = (byte) (l & 0xFF);
    }
    else
    {
      encoding = new byte[8];
      encoding[0] = (byte) ((l >> 56) & 0xFF);
      encoding[1] = (byte) ((l >> 48) & 0xFF);
      encoding[2] = (byte) ((l >> 40) & 0xFF);
      encoding[3] = (byte) ((l >> 32) & 0xFF);
      encoding[4] = (byte) ((l >> 24) & 0xFF);
      encoding[5] = (byte) ((l >> 16) & 0xFF);
      encoding[6] = (byte) ((l >> 8) & 0xFF);
      encoding[7] = (byte) (l & 0xFF);
    }

    byte[] encodedElement = new byte[3 + encoding.length];
    encodedElement[0] = ASN1Constants.UNIVERSAL_INTEGER_TYPE;
    encodedElement[1] = (byte) 0x81;
    encodedElement[2] = (byte) encoding.length;
    System.arraycopy(encoding, 0, encodedElement, 3, encoding.length);

    ASN1Long longElement = ASN1Long.decodeAsLong(encodedElement);
    assertEquals(longElement.longValue(), l);
  }



  /**
   * Tests the <CODE>decodeAsLong</CODE> method that takes a byte array with
   * a null array.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeNullArrayAsLong()
         throws Exception
  {
    byte[] b = null;
    ASN1Long.decodeAsLong(b);
  }



  /**
   * Tests the <CODE>decodeAsLong</CODE> method that takes a byte array with
   * a short array.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeShortArrayAsLong()
         throws Exception
  {
    byte[] b = new byte[0];
    ASN1Long.decodeAsLong(b);
  }



  /**
   * Tests the <CODE>decodeAsLong</CODE> method that takes a byte array with
   * a long length array.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeLongLengthArrayAsLong()
         throws Exception
  {
    byte[] b = { 0x02, (byte) 0x85, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00 };
    ASN1Long.decodeAsLong(b);
  }



  /**
   * Tests the <CODE>decodeAsLong</CODE> method that takes a byte array with
   * a truncated length array.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeTruncatedLengthArrayAsLong()
         throws Exception
  {
    byte[] b = { 0x02, (byte) 0x82, 0x00 };
    ASN1Long.decodeAsLong(b);
  }



  /**
   * Tests the <CODE>decodeAsLong</CODE> method that takes a byte array with
   * a length mismatch.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeLengthMismatchArrayAsLong()
         throws Exception
  {
    byte[] b = { 0x02, (byte) 0x81, 0x01 };
    ASN1Long.decodeAsLong(b);
  }



  /**
   * Tests the <CODE>decodeAsLong</CODE> method that takes a byte array with
   * a value too long for a long.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeLongIntLengthArrayAsLong()
         throws Exception
  {
    byte[] b = { 0x02, 0x09, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                 0x00 };
    ASN1Long.decodeAsLong(b);
  }



  /**
   * Tests the first <CODE>toString</CODE> method that takes a string builder
   * argument.
   *
   * @param  l  The long value to use in the test.
   */
  @Test(dataProvider = "longValues")
  public void testToString1(long l)
  {
    new ASN1Long(l).toString(new StringBuilder());
  }



  /**
   * Tests the second <CODE>toString</CODE> method that takes string builder and
   * integer arguments.
   *
   * @param  l  The long value to use in the test.
   */
  @Test(dataProvider = "longValues")
  public void testToString2(long l)
  {
    new ASN1Long(l).toString(new StringBuilder(), 1);
  }
}

