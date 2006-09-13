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
 * org.opends.server.protocols.asn1.ASN1OctetString class.
 */
public class TestASN1OctetString
       extends ASN1TestCase
{
  /**
   * Tests the first constructor, which doesn't take any arguments.
   */
  @Test()
  public void testConstructor1()
  {
    new ASN1OctetString();
  }



  /**
   * Create the values that can be used for testing BER types.
   *
   * @return  The values that can be used for testing BER types.
   */
  @DataProvider(name = "types")
  public Object[][] getTypes()
  {
    // Create an array with all of the valid single-byte types.  We don't
    // support multi-byte types, so this should be a comprehensive data set.
    Object[][] testTypes = new Object[0xFF][1];
    for (int i=0x00; i < 0xFF; i++)
    {
      testTypes[i] = new Object[] { (byte) (i & 0xFF) };
    }

    return testTypes;
  }



  /**
   * Tests the second constructor, which takes a byte argument.
   *
   * @param  type  The BER type to use for the test.
   */
  @Test(dataProvider = "types")
  public void testConstructor2(byte type)
  {
    ASN1OctetString os = new ASN1OctetString(type);
    assertEquals(os.getType(), type);
  }



  /**
   * Create byte arrays to use for element values.
   *
   * @return  A list of byte arrays that can be used as element values.
   */
  @DataProvider(name = "binaryValues")
  public Object[][] getBinaryValues()
  {
    // NOTE -- Don't make these arrays too big since they consume memory.
    return new Object[][]
    {
      new Object[] { null },              // The null value
      new Object[] { new byte[0x00] },    // The zero-byte value
      new Object[] { new byte[0x01] },    // The single-byte value
      new Object[] { new byte[0x7F] },    // The largest 1-byte length encoding
      new Object[] { new byte[0x80] },    // The smallest 2-byte length encoding
      new Object[] { new byte[0xFF] },    // The largest 2-byte length encoding
      new Object[] { new byte[0x0100] },  // The smallest 3-byte length encoding
      new Object[] { new byte[0xFFFF] },  // The largest 3-byte length encoding
      new Object[] { new byte[0x010000] } // The smallest 4-byte length encoding
    };
  }



  /**
   * Tests the third constructor, which takes a byte array argument.
   *
   * @param  value  The value to use for the test.
   */
  @Test(dataProvider = "binaryValues")
  public void testConstructor3(byte[] value)
  {
    ASN1OctetString os = new ASN1OctetString(value);
    if (value == null)
    {
      assertEquals(os.value(), new byte[0]);
    }
    else
    {
      assertEquals(os.value(), value);
    }
  }



  /**
   * Create strings to use for element values.
   *
   * @return  A list of strings that can be used as element values.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @DataProvider(name = "stringValues")
  public Object[][] getStringValues()
         throws Exception
  {
    return new Object[][]
    {
      new Object[] { null },
      new Object[] { "" },
      new Object[] { "\u0000" },
      new Object[] { "\t" },
      new Object[] { "\n" },
      new Object[] { "\r\n" },
      new Object[] { " " },
      new Object[] { "a" },
      new Object[] { "Test1\tTest2\tTest3" },
      new Object[] { "Test1\nTest2\nTest3" },
      new Object[] { "Test1\r\nTest2\r\nTest3" },
      new Object[] { "The Quick Brown Fox Jumps Over The Lazy Dog" },
      new Object[] { "\u00BFD\u00F3nde est\u00E1 el ba\u00F1o?" }
    };
  }



  /**
   * Tests the fourth constructor, which takes a string argument.
   *
   * @param  value  The value to use for the test.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "stringValues")
  public void testConstructor4(String value)
         throws Exception
  {
    ASN1OctetString os = new ASN1OctetString(value);
    if (value == null)
    {
      assertEquals(os.stringValue(), "");
      assertEquals(os.value(), new byte[0]);
    }
    else
    {
      assertEquals(os.stringValue(), value);
      assertEquals(os.value(), value.getBytes("UTF-8"));
    }
  }



  /**
   * Tests the fifth constructor, which takes byte and byte array arguments.
   *
   * @param  value  The value to use for the test.
   */
  @Test(dataProvider = "binaryValues")
  public void testConstructor5(byte[] value)
  {
    for (int i=0; i < 255; i++)
    {
      ASN1OctetString os = new ASN1OctetString((byte) (i & 0xFF), value);
      if (value == null)
      {
        assertEquals(os.value(), new byte[0]);
      }
      else
      {
        assertEquals(os.value(), value);
      }
    }
  }



  /**
   * Tests the sixth constructor, which takes byte and string arguments.
   *
   * @param  value  The value to use for the test.
   */
  @Test(dataProvider = "stringValues")
  public void testConstructor6(String value)
  {
    for (int i=0; i < 255; i++)
    {
      ASN1OctetString os = new ASN1OctetString((byte) (i & 0xFF), value);
      if (value == null)
      {
        assertEquals(os.stringValue(), "");
      }
      else
      {
        assertEquals(os.stringValue(), value);
      }
    }
  }



  /**
   * Tests the <CODE>stringValue</CODE> methods for the case in which the octet
   * string was created using a string representation.
   *
   * @param  value  The value to use for the test.
   */
  @Test(dataProvider = "stringValues")
  public void testStringValueFromStrings(String value)
  {
    ASN1OctetString os = new ASN1OctetString(value);
    if (value == null)
    {
      assertEquals(os.stringValue(), "");
    }
    else
    {
      assertEquals(os.stringValue(), value);
    }

    os = new ASN1OctetString(value);
    StringBuilder valueBuffer = new StringBuilder();
    os.stringValue(valueBuffer);
    if (value == null)
    {
      assertEquals(valueBuffer.toString(), "");
    }
    else
    {
      assertEquals(valueBuffer.toString(), value);
    }
  }



  /**
   * Tests the <CODE>stringValue</CODE> methods for the case in which the octet
   * string was created using a binary representation.
   *
   * @param  value  The value to use for the test.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "stringValues")
  public void testStringValueFromBytes(String value)
         throws Exception
  {
    byte[] valueBytes;
    if (value == null)
    {
      valueBytes = null;
    }
    else
    {
      valueBytes = value.getBytes("UTF-8");
    }

    ASN1OctetString os = new ASN1OctetString(valueBytes);
    if (value == null)
    {
      assertEquals(os.stringValue(), "");
    }
    else
    {
      assertEquals(os.stringValue(), value);
    }

    os = new ASN1OctetString(valueBytes);
    StringBuilder valueBuffer = new StringBuilder();
    os.stringValue(valueBuffer);
    if (value == null)
    {
      assertEquals(valueBuffer.toString(), "");
    }
    else
    {
      assertEquals(valueBuffer.toString(), value);
    }
  }



  /**
   * Tests the <CODE>setValue</CODE> method that takes a string argument.
   *
   * @param  value  The value to use for the test.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "stringValues")
  public void testSetStringValue(String value)
         throws Exception
  {
    ASN1OctetString os = new ASN1OctetString();
    os.setValue(value);
    if (value == null)
    {
      assertEquals(os.stringValue(), "");
      assertEquals(os.value(), new byte[0]);
    }
    else
    {
      assertEquals(os.stringValue(), value);
      assertEquals(os.value(), value.getBytes("UTF-8"));
    }
  }



  /**
   * Tests the <CODE>setValue</CODE> method that takes a byte array argument.
   *
   * @param  value  The value to use for the test.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "binaryValues")
  public void testSetBinaryValue(byte[] value)
         throws Exception
  {
    ASN1OctetString os = new ASN1OctetString();
    os.setValue(value);
    if (value == null)
    {
      assertEquals(os.stringValue(), "");
      assertEquals(os.value(), new byte[0]);
    }
    else
    {
      assertEquals(os.stringValue(), new String(value, "UTF-8"));
      assertEquals(os.value(), value);
    }
  }



  /**
   * Create ASN.1 elements to test decoding them as octet strings.
   *
   * @return  A list of ASN.1 elements that can be decoded as octet strings.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @DataProvider(name = "elements")
  public Object[][] getElements()
  {
    return new Object[][]
    {
      new Object[] { new ASN1OctetString() },
      new Object[] { new ASN1OctetString((byte) 0x50) },
      new Object[] { new ASN1OctetString(new byte[50]) },
      new Object[] { new ASN1OctetString("Hello") },
      new Object[] { new ASN1Element((byte) 0x50) },
      new Object[] { new ASN1Element((byte) 0x50, new byte[50]) },
      new Object[] { new ASN1Boolean(false) },
      new Object[] { new ASN1Boolean(true) },
      new Object[] { new ASN1Enumerated(0) },
      new Object[] { new ASN1Enumerated(1) },
      new Object[] { new ASN1Enumerated(127) },
      new Object[] { new ASN1Enumerated(128) },
      new Object[] { new ASN1Enumerated(255) },
      new Object[] { new ASN1Enumerated(256) },
      new Object[] { new ASN1Integer(0) },
      new Object[] { new ASN1Integer(1) },
      new Object[] { new ASN1Integer(127) },
      new Object[] { new ASN1Integer(128) },
      new Object[] { new ASN1Integer(255) },
      new Object[] { new ASN1Integer(256) },
      new Object[] { new ASN1Long(0) },
      new Object[] { new ASN1Long(1) },
      new Object[] { new ASN1Long(127) },
      new Object[] { new ASN1Long(128) },
      new Object[] { new ASN1Long(255) },
      new Object[] { new ASN1Long(256) },
      new Object[] { new ASN1Null() },
      new Object[] { new ASN1Sequence() },
      new Object[] { new ASN1Set() }
    };
  }



  /**
   * Test the <CODE>decodeAsOctetString</CODE> method that takes an ASN.1
   * element using valid elements.
   *
   * @param  element  The element to decode.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "elements")
  public void testDecodeValidElementAsOctetString(ASN1Element element)
         throws Exception
  {
    ASN1OctetString.decodeAsOctetString(element);
  }



  /**
   * Test the <CODE>decodeAsOctetString</CODE> method that takes an ASN.1
   * element using a null element.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeNullElementAsOctetString()
         throws Exception
  {
    ASN1Element e = null;
    ASN1OctetString.decodeAsOctetString(e);
  }



  /**
   * Create byte arrays with encoded ASN.1 elements to test decoding them as
   * octet strings.
   *
   * @return  A list of byte arrays with encoded ASN.1 elements that can be
   *          decoded as octet strings.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @DataProvider(name = "elementArrays")
  public Object[][] getElementArrays()
  {
    return new Object[][]
    {
      new Object[] { new byte[] { 0x04, 0x00 } },
      new Object[] { new byte[] { (byte) 0x50, 0x00 } },
      new Object[] { new byte[] { 0x04, 0x05, 0x48, 0x65, 0x6C, 0x6C, 0x6F } },
      new Object[] { new byte[] { 0x01, 0x01, 0x00 } },
      new Object[] { new byte[] { 0x01, 0x01, (byte) 0xFF } },
      new Object[] { new byte[] { 0x0A, 0x01, 0x00 } },
      new Object[] { new byte[] { 0x0A, 0x01, 0x01 } },
      new Object[] { new byte[] { 0x0A, 0x01, 0x7F } },
      new Object[] { new byte[] { 0x0A, 0x01, (byte) 0x80 } },
      new Object[] { new byte[] { 0x0A, 0x01, (byte) 0xFF } },
      new Object[] { new byte[] { 0x0A, 0x02, 0x01, 0x00 } },
      new Object[] { new byte[] { 0x02, 0x01, 0x00 } },
      new Object[] { new byte[] { 0x02, 0x01, 0x01 } },
      new Object[] { new byte[] { 0x02, 0x01, 0x7F } },
      new Object[] { new byte[] { 0x02, 0x02, 0x00, (byte) 0x80 } },
      new Object[] { new byte[] { 0x02, 0x02, 0x00, (byte) 0xFF } },
      new Object[] { new byte[] { 0x02, 0x02, 0x01, 0x00 } },
      new Object[] { new byte[] { 0x05, 0x00 } },
      new Object[] { new byte[] { 0x30, 0x00 } },
      new Object[] { new byte[] { 0x31, 0x00 } },
      new Object[] { new byte[] { 0x05, (byte) 0x81, 0x00 } },
      new Object[] { new byte[] { 0x05, (byte) 0x82, 0x00, 0x00 } },
      new Object[] { new byte[] { 0x05, (byte) 0x83, 0x00, 0x00, 0x00 } },
      new Object[] { new byte[] { 0x05, (byte) 0x84, 0x00, 0x00, 0x00, 0x00 } },
    };
  }



  /**
   * Tests the <CODE>decodeAsOctetString</CODE> method that takes a byte array
   * using a valid array.
   *
   * @param  b  The byte array to decode.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "elementArrays")
  public void testDecodeValidArrayAsOctetString(byte[] b)
         throws Exception
  {
    ASN1OctetString.decodeAsOctetString(b);
  }



  /**
   * Tests the <CODE>decodeAsOctetString</CODE> method that takes a byte array
   * using a null array.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions  = { ASN1Exception.class })
  public void testDecodeNullArrayAsOctetString()
         throws Exception
  {
    byte[] b = null;
    ASN1OctetString.decodeAsOctetString(b);
  }



  /**
   * Tests the <CODE>decodeAsOctetString</CODE> method that takes a byte array
   * using a short array.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions  = { ASN1Exception.class })
  public void testDecodeShortArrayAsOctetString()
         throws Exception
  {
    byte[] b = new byte[1];
    ASN1OctetString.decodeAsOctetString(b);
  }



  /**
   * Tests the <CODE>decodeAsOctetString</CODE> method that takes a byte array
   * using an array that indicates it takes more than four bytes to encode the
   * length.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions  = { ASN1Exception.class })
  public void testDecodeLongLengthArrayAsOctetString()
         throws Exception
  {
    byte[] b = { 0x04, (byte) 0x85, 0x00, 0x00, 0x00, 0x00, 0x00 };
    ASN1OctetString.decodeAsOctetString(b);
  }



  /**
   * Tests the <CODE>decodeAsOctetString</CODE> method that takes a byte array
   * using an array that doesn't fully contain the length.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions  = { ASN1Exception.class })
  public void testDecodeTruncatedLengthArrayAsOctetString()
         throws Exception
  {
    byte[] b = { 0x04, (byte) 0x82, 0x00 };
    ASN1OctetString.decodeAsOctetString(b);
  }



  /**
   * Tests the <CODE>decodeAsOctetString</CODE> method that takes a byte array
   * using an array whose actual length doesn't match with the decoded length.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions  = { ASN1Exception.class })
  public void testDecodeLengthMismatchArrayAsOctetString()
         throws Exception
  {
    byte[] b = { 0x04, 0x00, 0x00 };
    ASN1OctetString.decodeAsOctetString(b);
  }



  /**
   * Tests the <CODE>duplicate</CODE> method.
   *
   * @param  b  The byte array to decode as an octet string.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "elementArrays")
  public void testDuplicate(byte[] b)
         throws Exception
  {
    ASN1OctetString os1 = ASN1OctetString.decodeAsOctetString(b);
    ASN1OctetString os2 = os1.duplicate();
    assertTrue(os1.equals(os2));
    assertNotSame(os1, os2);

    os1.setValue(new byte[50]);
    assertFalse(os1.equals(os2));
  }



  /**
   * Tests the <CODE>toString</CODE> method that takes a string builder
   * argument.
   *
   * @param  b  The byte array to decode as an octet string.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "elementArrays")
  public void testToString1(byte[] b)
         throws Exception
  {
    ASN1OctetString os = ASN1OctetString.decodeAsOctetString(b);
    os.toString(new StringBuilder());
  }



  /**
   * Tests the <CODE>toString</CODE> method that takes a string builder and
   * integer arguments.
   *
   * @param  b  The byte array to decode as an octet string.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "elementArrays")
  public void testToString2(byte[] b)
         throws Exception
  {
    ASN1OctetString os = ASN1OctetString.decodeAsOctetString(b);
    os.toString(new StringBuilder(), 1);
  }



  /**
   * Tests the <CODE>toASN1OctetString</CODE> method.
   *
   * @param  b  The byte array to decode as an octet string.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "elementArrays")
  public void testToASN1OctetString(byte[] b)
         throws Exception
  {
    ASN1OctetString os1 = ASN1OctetString.decodeAsOctetString(b);
    ASN1OctetString os2 = os1.toASN1OctetString();
    assertEquals(os2.value(), os1.value());
  }
}

